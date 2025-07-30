package com.plantdiagnostic

import com.plantdiagnostic.models.*
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.max

class JsonGraphConnector(private val jsonFilePath: String = "plant_disease_graph.json") {
    private val graphData: GraphData
    private val nodesById: Map<String, GraphNode>
    private val nodesByType: Map<String, List<GraphNode>>
    private val relationshipsBySource: Map<String, List<GraphRelationship>>
    private val relationshipsByTarget: Map<String, List<GraphRelationship>>
    private val relationshipsByType: Map<String, List<GraphRelationship>>

    init {
        graphData = loadGraphData()
        nodesById = graphData.nodes.associateBy { it.id }
        nodesByType = graphData.nodes.groupBy { it.type }
        relationshipsBySource = graphData.relationships.groupBy { it.source }
        relationshipsByTarget = graphData.relationships.groupBy { it.target }
        relationshipsByType = graphData.relationships.groupBy { it.type }
    }

    private fun loadGraphData(): GraphData {
        val file = File(jsonFilePath)
        return if (file.exists()) {
            try {
                val jsonString = file.readText()
                Json.decodeFromString<GraphData>(jsonString)
            } catch (e: Exception) {
                println("Error loading graph data: ${e.message}")
                GraphData()
            }
        } else {
            println("Warning: $jsonFilePath not found. Using empty graph.")
            GraphData()
        }
    }

    fun getSchema(): SchemaResult {
        return try {
            val nodeTypes = graphData.nodes.map { it.type }.distinct()
            val relationshipTypes = graphData.relationships.map { it.type }.distinct()
            val nodeCounts = nodeTypes.map { type ->
                type to nodesByType[type]?.size ?: 0
            }.sortedByDescending { it.second }

            SchemaResult(
                nodeLabels = nodeTypes,
                relationshipTypes = relationshipTypes,
                nodeCounts = nodeCounts.take(10)
            )
        } catch (e: Exception) {
            println("Error getting schema: ${e.message}")
            SchemaResult(emptyList(), emptyList(), emptyList())
        }
    }

    fun findDiseasesBySymptoms(symptoms: List<String>): List<DiseaseResult> {
        if (symptoms.isEmpty()) return emptyList()

        val symptomsLower = symptoms.map { cleanSymptomText(it.lowercase()) }
        val diseaseResults = mutableListOf<DiseaseResult>()

        // Get all disease nodes
        val diseaseNodes = listOf("Disease", "PlantDisease").flatMap { nodeType ->
            nodesByType[nodeType] ?: emptyList()
        }

        for (diseaseNode in diseaseNodes) {
            val diseaseId = diseaseNode.id
            val diseaseName = diseaseNode.name ?: ""
            val diseaseDescription = diseaseNode.description ?: ""

            // Find all symptoms connected to this disease
            val allDiseaseSymptoms = mutableSetOf<String>()
            relationshipsBySource[diseaseId]?.forEach { rel ->
                if (rel.type in listOf("HAS_SYMPTOM", "MANIFESTS_AS", "SHOWS", "EXHIBITS")) {
                    val targetNode = nodesById[rel.target]
                    if (targetNode?.type == "Symptom") {
                        targetNode.name?.let { symptomName ->
                            val cleanSymptom = cleanSymptomText(symptomName)
                            if (cleanSymptom.isNotEmpty()) {
                                allDiseaseSymptoms.add(cleanSymptom)
                            }
                        }
                    }
                }
            }

            // Find matching symptoms using fuzzy matching
            val matchedSymptoms = mutableListOf<String>()
            for (userSymptom in symptomsLower) {
                for (diseaseSymptom in allDiseaseSymptoms) {
                    if (symptomsMatch(userSymptom, diseaseSymptom)) {
                        matchedSymptoms.add(diseaseSymptom)
                        break
                    }
                }
            }

            if (matchedSymptoms.isNotEmpty()) {
                val matchCount = matchedSymptoms.size
                val totalSymptoms = max(allDiseaseSymptoms.size, 1)
                val matchPercentage = matchCount.toDouble() / totalSymptoms

                diseaseResults.add(
                    DiseaseResult(
                        disease = diseaseName,
                        description = diseaseDescription,
                        allSymptoms = allDiseaseSymptoms.toList(),
                        matched = matchedSymptoms,
                        matchCount = matchCount,
                        totalSymptoms = totalSymptoms,
                        matchPercentage = matchPercentage
                    )
                )
            }
        }

        return diseaseResults.sortedWith(
            compareByDescending<DiseaseResult> { it.matchPercentage }
                .thenByDescending { it.matchCount }
        ).take(10)
    }

    private fun cleanSymptomText(text: String): String {
        if (text.isEmpty()) return ""

        // Remove leading/trailing spaces and punctuation
        val cleaned = text.trim().trim('.', ',', ';', ':', '!', '?')

        // Replace multiple spaces with single space
        return cleaned.replace(Regex("\\s+"), " ").lowercase()
    }

    private fun symptomsMatch(userSymptom: String, diseaseSymptom: String): Boolean {
        val userSymptomClean = userSymptom.lowercase().trim()
        val diseaseSymptomClean = diseaseSymptom.lowercase().trim()

        // Exact match
        if (userSymptomClean == diseaseSymptomClean) return true

        // Substring match (either direction)
        if (userSymptomClean in diseaseSymptomClean || diseaseSymptomClean in userSymptomClean) return true

        // Word-based matching
        val userWords = userSymptomClean.split(" ").toSet()
        val diseaseWords = diseaseSymptomClean.split(" ").toSet()

        // Remove common stop words
        val stopWords = setOf("on", "the", "of", "in", "at", "with", "and", "or", "a", "an")
        val userWordsFiltered = userWords - stopWords
        val diseaseWordsFiltered = diseaseWords - stopWords

        if (userWordsFiltered.isEmpty() || diseaseWordsFiltered.isEmpty()) return false

        // Check for significant word overlap (>50%)
        val intersection = userWordsFiltered.intersect(diseaseWordsFiltered)
        val union = userWordsFiltered.union(diseaseWordsFiltered)

        if (intersection.size.toDouble() / union.size > 0.5) return true

        // Check for key symptom keywords
        val symptomKeywords = mapOf(
            "yellow" to listOf("yellowing", "chlorosis"),
            "brown" to listOf("browning", "necrosis"),
            "wilt" to listOf("wilting", "drooping"),
            "spot" to listOf("spots", "lesions", "patches"),
            "dry" to listOf("drying", "dried", "desiccation"),
            "rot" to listOf("rotting", "decay", "decomposition"),
            "stunt" to listOf("stunted", "stunting", "dwarf")
        )

        for ((keyword, synonyms) in symptomKeywords) {
            if (keyword in userSymptomClean) {
                if (synonyms.any { it in diseaseSymptomClean } || keyword in diseaseSymptomClean) {
                    return true
                }
            }
            if (synonyms.any { it in userSymptomClean }) {
                if (keyword in diseaseSymptomClean || synonyms.any { it in diseaseSymptomClean }) {
                    return true
                }
            }
        }

        return false
    }

    fun findDifferentiatingSymptoms(diseaseNames: List<String>, excludedSymptoms: Set<String> = emptySet()): List<SymptomResult> {
        if (diseaseNames.isEmpty()) return emptyList()

        val excludedLower = excludedSymptoms.map { cleanSymptomText(it.lowercase()) }.toSet()
        val symptomDiseaseMap = mutableMapOf<String, MutableSet<String>>()

        // Find diseases by name
        val diseaseNodes = listOf("Disease", "PlantDisease").flatMap { nodeType ->
            nodesByType[nodeType]?.filter { it.name in diseaseNames } ?: emptyList()
        }

        // For each disease, find its symptoms
        for (diseaseNode in diseaseNodes) {
            val diseaseId = diseaseNode.id
            val diseaseName = diseaseNode.name ?: ""

            relationshipsBySource[diseaseId]?.forEach { rel ->
                if (rel.type in listOf("HAS_SYMPTOM", "MANIFESTS_AS", "SHOWS", "EXHIBITS")) {
                    val targetNode = nodesById[rel.target]
                    if (targetNode?.type == "Symptom") {
                        targetNode.name?.let { symptomName ->
                            val cleanSymptom = cleanSymptomText(symptomName)
                            if (cleanSymptom.isNotEmpty() && cleanSymptom !in excludedLower) {
                                // Check if any excluded symptom matches this one
                                val isExcluded = excludedLower.any { excluded ->
                                    symptomsMatch(cleanSymptom, excluded)
                                }

                                if (!isExcluded) {
                                    symptomDiseaseMap.getOrPut(cleanSymptom) { mutableSetOf() }.add(diseaseName)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Convert to result format and sort
        return symptomDiseaseMap.map { (symptomName, diseases) ->
            SymptomResult(
                symptom = symptomName,
                diseases = diseases.toList(),
                count = diseases.size
            )
        }.sortedWith(
            compareByDescending<SymptomResult> { it.count }
                .thenBy { it.symptom }
        ).take(20)
    }

    fun getSolutionsForDisease(diseaseName: String): List<SolutionResult> {
        val solutions = mutableListOf<SolutionResult>()

        // Find disease node by name (exact match)
        val diseaseNode = listOf("Disease", "PlantDisease").flatMap { nodeType ->
            nodesByType[nodeType] ?: emptyList()
        }.find { it.name == diseaseName }

        if (diseaseNode == null) return solutions

        val diseaseId = diseaseNode.id

        // Find solution relationships
        relationshipsBySource[diseaseId]?.forEach { rel ->
            val relType = rel.type.lowercase()
            if ("solution" in relType || "treatment" in relType || 
                relType == "has_solution" || relType == "treated_by") {

                val targetNode = nodesById[rel.target]
                if (targetNode?.type == "Solution") {
                    val solutionName = targetNode.name ?: ""
                    val solutionDescription = targetNode.description ?: ""

                    // Try different fields for treatment information
                    val treatment = targetNode.treatment 
                        ?: targetNode.content 
                        ?: targetNode.instructions 
                        ?: solutionName // Use solution name as fallback

                    if (solutionName.isNotEmpty()) {
                        solutions.add(
                            SolutionResult(
                                solutionName = solutionName,
                                solutionDescription = solutionDescription,
                                treatment = treatment
                            )
                        )
                    }
                }
            }
        }

        return solutions
    }

    fun close() {
        // No cleanup needed for JSON file
    }
}

class JsonAuraDBAdapter(jsonFilePath: String = "plant_disease_graph.json") {
    val jsonConnector = JsonGraphConnector(jsonFilePath)

    fun close() = jsonConnector.close()
    fun getSchema() = jsonConnector.getSchema()
} 