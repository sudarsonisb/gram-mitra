package com.plantdiagnostic

import com.plantdiagnostic.models.*
import kotlinx.coroutines.runBlocking
import kotlin.math.min

data class ConversationEntry(
    val content: String,
    val isUser: Boolean,
    val timestamp: Int
)

class PlantDiseaseDignosticSystemGraphDirect(
    private val db: JsonAuraDBAdapter,
    private val llm: OllamaGemmaConnector
) {
    private val conversationHistory = mutableListOf<ConversationEntry>()
    private val confirmedSymptoms = mutableSetOf<String>()
    private val ruledOutSymptoms = mutableSetOf<String>()
    private val askedSymptoms = mutableSetOf<String>()
    private var possibleDiseases = listOf<DiseaseResult>()
    private var lastAskedSymptom: String? = null
    private lateinit var schema: SchemaResult

    fun initialize(): SchemaResult {
        schema = db.getSchema()
        println("🌱 Plant Disease Diagnostic System (Graph Direct) initialized")
        println("🎯 Using DIRECT graph traversal for symptom selection!")
        return schema
    }

    private fun findDiseasesBySymptoms(symptoms: Set<String>): List<DiseaseResult> {
        if (symptoms.isEmpty()) return emptyList()
        return db.jsonConnector.findDiseasesBySymptoms(symptoms.toList())
    }

    private fun findBestDifferentiatingSymptonFromGraph(): String? {
        if (possibleDiseases.isEmpty()) {
            println("❌ No possible diseases to differentiate between")
            return null
        }

        println("🔍 Finding differentiating symptoms for ${possibleDiseases.size} diseases:")
        possibleDiseases.take(3).forEach { disease ->
            println("   - ${disease.disease} (${(disease.matchPercentage * 100).toInt()}%)")
        }

        val diseaseNames = possibleDiseases.take(5).map { it.disease }

        // Get excluded symptoms
        val allExcluded = mutableSetOf<String>()
        confirmedSymptoms.forEach { allExcluded.add(it.lowercase().trim()) }
        ruledOutSymptoms.forEach { allExcluded.add(it.lowercase().trim()) }
        askedSymptoms.forEach { allExcluded.add(it.lowercase().trim()) }

        println("🚫 Excluding ${allExcluded.size} symptoms: ${allExcluded.toList()}")

        // Get differentiating symptoms from graph
        val results = db.jsonConnector.findDifferentiatingSymptoms(diseaseNames, allExcluded)

        println("📋 Graph returned ${results.size} differentiating symptoms:")
        results.take(5).forEachIndexed { i, r ->
            println("   ${i + 1}. '${r.symptom}' (helps with ${r.diseases.size} diseases: ${r.diseases.take(2)})")
        }

        if (results.isEmpty()) {
            println("❌ No differentiating symptoms found in graph")
            return null
        }

        // Score symptoms by how well they differentiate between diseases
        var bestSymptom: SymptomResult? = null
        var bestScore = 0.0

        for (symptomInfo in results) {
            val symptomName = symptomInfo.symptom
            val diseasesWithSymptom = symptomInfo.diseases.toSet()

            // Skip if already processed
            if (symptomName.lowercase().trim() in allExcluded) continue

            // Calculate differentiation score
            val numDiseasesWithSymptom = diseasesWithSymptom.size
            val numDiseasesWithoutSymptom = diseaseNames.size - numDiseasesWithSymptom

            val score = if (numDiseasesWithSymptom == 0 || numDiseasesWithoutSymptom == 0) {
                0.0 // Doesn't help differentiate
            } else {
                // Higher score for more balanced splits
                val balanceScore = min(numDiseasesWithSymptom, numDiseasesWithoutSymptom).toDouble()
                // Bonus for symptoms that appear in multiple diseases (more informative)
                val frequencyBonus = numDiseasesWithSymptom * 0.1
                balanceScore + frequencyBonus
            }

            println("   📊 '$symptomName': $numDiseasesWithSymptom have it, $numDiseasesWithoutSymptom don't → Score: ${"%.1f".format(score)}")

            if (score > bestScore) {
                bestScore = score
                bestSymptom = symptomInfo
            }
        }

        return if (bestSymptom != null) {
            println("🏆 Best symptom to ask: '${bestSymptom.symptom}' (score: ${"%.1f".format(bestScore)})")
            println("   This symptom helps differentiate: ${bestSymptom.diseases}")
            bestSymptom.symptom
        } else {
            println("❌ No good differentiating symptom found")
            null
        }
    }

    private fun getSolutionForDisease(diseaseName: String): List<SolutionResult> {
        return db.jsonConnector.getSolutionsForDisease(diseaseName)
    }

    private fun shouldDiagnose(): DiseaseResult? {
        if (confirmedSymptoms.isEmpty() || confirmedSymptoms.size < 2) return null

        val diseases = findDiseasesBySymptoms(confirmedSymptoms)
        if (diseases.isEmpty()) return null

        val top = diseases[0]
        val second = diseases.getOrNull(1)

        println("🏥 Diagnosis check - Top disease: ${top.disease} (${(top.matchPercentage * 100).toInt()}%)")
        second?.let {
            println("   Second: ${it.disease} (${(it.matchPercentage * 100).toInt()}%)")
        }

        // Single disease
        if (diseases.size == 1) {
            println("✅ Only one disease matches - definitive diagnosis")
            return top
        }

        second?.let { secondDisease ->
            val topPct = top.matchPercentage
            val secondPct = secondDisease.matchPercentage
            val gap = topPct - secondPct

            println("   Gap between top and second: ${(gap * 100).toInt()}%")

            // Significant advantage
            if (gap > 0.20 || (topPct > 0.70 && gap > 0.10)) {
                println("✅ Top disease has significant advantage - diagnosing")
                return top
            }
        }

        // High confidence with multiple symptoms
        if (top.matchCount >= 3 && top.matchPercentage >= 0.60) {
            println("✅ High confidence with multiple matching symptoms - diagnosing")
            return top
        }

        println("⏳ Not enough confidence yet - continue asking")
        return null
    }

    private fun createDiagnosisContext(disease: DiseaseResult, additionalInfo: String = ""): String {
        val solutions = getSolutionForDisease(disease.disease)

        val contextParts = mutableListOf(
            "CONFIRMED symptoms: ${confirmedSymptoms.toList()}",
            "RULED OUT symptoms: ${ruledOutSymptoms.toList()}",
            "DIAGNOSED disease: ${disease.disease} (${(disease.matchPercentage * 100).toInt()}% match)",
            "MATCHED symptoms: ${disease.matched}"
        )

        if (additionalInfo.isNotEmpty()) {
            contextParts.add(additionalInfo)
        }

        if (solutions.isNotEmpty()) {
            val solutionNames = solutions.mapNotNull { it.solutionName }
            contextParts.add("AVAILABLE SOLUTIONS: $solutionNames")
            solutions.forEach { sol ->
                if (sol.solutionDescription.isNotEmpty()) {
                    contextParts.add("SOLUTION - ${sol.solutionName}: ${sol.solutionDescription}")
                }
                if (sol.treatment.isNotEmpty()) {
                    contextParts.add("TREATMENT - ${sol.solutionName}: ${sol.treatment}")
                }
            }
        } else {
            contextParts.add("NO SPECIFIC SOLUTIONS FOUND IN GRAPH")
        }

        return contextParts.joinToString(" | ")
    }

    private fun parseResponse(userInput: String): String {
        val lower = userInput.lowercase().trim()
        return when {
            listOf("yes", "y", "yeah", "definitely", "observed").any { it in lower } -> "confirmed"
            listOf("no", "n", "nope", "not", "never", "absent").any { it in lower } -> "denied"
            else -> "new_symptom"
        }
    }

    private fun formatQuestion(symptom: String?): String {
        if (symptom.isNullOrEmpty()) {
            return "Please describe any other symptoms you observe."
        }

        val cleanSymptom = symptom.trim().lowercase()

        return when {
            listOf("soil", "ground", "environment").any { it in cleanSymptom } ->
                "Do you observe $cleanSymptom around the plant?"
            listOf("stress", "condition", "temperature").any { it in cleanSymptom } ->
                "Is the plant showing signs of $cleanSymptom?"
            cleanSymptom.startsWith("dry") || cleanSymptom.startsWith("wet") ->
                "Are there $cleanSymptom conditions affecting the plant?"
            else ->
                "Do you observe $cleanSymptom on the plant?"
        }
    }

    suspend fun processUserInput(userInput: String): String {
        val trimmedInput = userInput.trim()
        conversationHistory.add(ConversationEntry(trimmedInput, true, conversationHistory.size))

        if (conversationHistory.size == 1) { // First interaction
            println("🌱 Initial symptom: '$trimmedInput'")
            confirmedSymptoms.add(trimmedInput.lowercase())
            possibleDiseases = findDiseasesBySymptoms(confirmedSymptoms)

            println("🔍 Found ${possibleDiseases.size} possible diseases from graph:")
            possibleDiseases.take(3).forEach { disease ->
                println("   - ${disease.disease} (${(disease.matchPercentage * 100).toInt()}% match)")
            }

            // Check for immediate diagnosis
            val definitive = shouldDiagnose()
            if (definitive != null) {
                val context = createDiagnosisContext(definitive, "IMMEDIATE DIAGNOSIS")
                val response = llm.generateSummary(confirmedSymptoms.toList(), context)
                conversationHistory.add(ConversationEntry(response, false, conversationHistory.size))
                showStatus()
                return "🏆 IMMEDIATE DIAGNOSIS!\n\n$response"
            }
        } else { // Follow-up interactions
            val responseType = parseResponse(trimmedInput)
            println("📝 User response: '$trimmedInput' → $responseType")

            when (responseType) {
                "confirmed" -> {
                    lastAskedSymptom?.let { symptom ->
                        confirmedSymptoms.add(symptom.lowercase())
                        println("✅ Confirmed: '$symptom'")
                    } ?: println("⚠️ User said yes but no symptom was being tracked!")
                }
                "denied" -> {
                    lastAskedSymptom?.let { symptom ->
                        ruledOutSymptoms.add(symptom.lowercase())
                        println("❌ Ruled out: '$symptom'")
                    } ?: println("⚠️ User said no but no symptom was being tracked!")
                }
                "new_symptom" -> {
                    confirmedSymptoms.add(trimmedInput.lowercase())
                    println("➕ New symptom: '$trimmedInput'")
                }
            }

            // Update diseases based on new information
            println("\n🔄 Updating disease list...")
            possibleDiseases = findDiseasesBySymptoms(confirmedSymptoms)

            println("🎯 Updated diseases (${possibleDiseases.size}):")
            possibleDiseases.take(3).forEach { disease ->
                println("   - ${disease.disease} (${(disease.matchPercentage * 100).toInt()}% match)")
            }

            // Check for diagnosis
            val definitive = shouldDiagnose()
            if (definitive != null) {
                val context = createDiagnosisContext(definitive, "DIAGNOSIS after ${confirmedSymptoms.size} symptoms confirmed")
                val response = llm.generateSummary(confirmedSymptoms.toList(), context)
                conversationHistory.add(ConversationEntry(response, false, conversationHistory.size))
                showStatus()
                return "🏆 DIAGNOSIS COMPLETE!\n\n$response"
            }

            // Check if we have enough symptoms but need to force diagnosis
            if (confirmedSymptoms.size >= 4 && possibleDiseases.isNotEmpty()) {
                println("⏰ Enough symptoms collected - forcing diagnosis")
                val topDisease = possibleDiseases[0]
                val context = createDiagnosisContext(topDisease, "ANALYSIS COMPLETE - Sufficient symptoms")
                val response = llm.generateSummary(confirmedSymptoms.toList(), context)
                conversationHistory.add(ConversationEntry(response, false, conversationHistory.size))
                showStatus()
                return "🏆 DIAGNOSIS (Analysis Complete)!\n\n$response"
            }
        }

        // Get next symptom to ask about - DIRECT FROM GRAPH!
        println("\n🔍 Finding next symptom from graph...")
        val nextSymptom = findBestDifferentiatingSymptonFromGraph()

        return if (nextSymptom != null) {
            // Store the symptom we're asking about
            lastAskedSymptom = nextSymptom
            askedSymptoms.add(nextSymptom.lowercase())

            // Format the question
            val question = formatQuestion(nextSymptom)

            println("❓ Asking: '$question'")

            conversationHistory.add(ConversationEntry(question, false, conversationHistory.size))
            showStatus()
            question
        } else {
            // No more symptoms to ask - provide final diagnosis
            println("🏁 No more differentiating symptoms available")
            if (possibleDiseases.isNotEmpty()) {
                val topDisease = possibleDiseases[0]
                val context = createDiagnosisContext(topDisease, "NO MORE QUESTIONS - Final diagnosis")
                val response = llm.generateSummary(confirmedSymptoms.toList(), context)
                conversationHistory.add(ConversationEntry(response, false, conversationHistory.size))
                showStatus()
                "🏆 FINAL DIAGNOSIS!\n\n$response"
            } else {
                "I need more information. Please describe any other symptoms you observe."
            }
        }
    }

    private fun showStatus() {
        println("\n📊 CURRENT STATUS:")
        println("   ✅ Confirmed: ${confirmedSymptoms.toList().ifEmpty { listOf("None") }}")
        println("   ❌ Ruled out: ${ruledOutSymptoms.toList().ifEmpty { listOf("None") }}")
        println("   🎯 Diseases: ${possibleDiseases.size} possible")
        println("=" * 50)
    }

    suspend fun getDiagnosticSummary(): String {
        if (confirmedSymptoms.isEmpty()) {
            return "No symptoms confirmed yet. Please describe what you observe on the plant."
        }

        val diseases = findDiseasesBySymptoms(confirmedSymptoms)
        return if (diseases.isNotEmpty()) {
            val topDisease = diseases[0]
            val context = createDiagnosisContext(topDisease, "SUMMARY ANALYSIS")
            llm.generateSummary(confirmedSymptoms.toList(), context)
        } else {
            "No matching diseases found in graph for the confirmed symptoms."
        }
    }

    fun resetConversation() {
        conversationHistory.clear()
        confirmedSymptoms.clear()
        ruledOutSymptoms.clear()
        askedSymptoms.clear()
        possibleDiseases = emptyList()
        lastAskedSymptom = null
        println("Conversation reset.")
    }

    fun getCurrentState(): String {
        return """
📊 CURRENT DIAGNOSTIC STATE:
Confirmed: ${confirmedSymptoms.toList()}
Ruled out: ${ruledOutSymptoms.toList()}
Asked: ${askedSymptoms.toList()}
Diseases: ${possibleDiseases.size}
${possibleDiseases.take(3).joinToString("\n") { "  - ${it.disease} (${(it.matchPercentage * 100).toInt()}%)" }}
        """.trimIndent()
    }
} 