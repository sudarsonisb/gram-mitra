package com.plantdiagnostic

import kotlinx.coroutines.runBlocking

suspend fun main() {
    println("🌱 === PLANT DISEASE DIAGNOSTIC SYSTEM (GRAPH DIRECT) ===")
    println("🎯 DIRECT graph traversal - no LLM for symptom selection!")
    println("🔍 Knowledge graph drives the conversation flow!")

    // Initialize
    val db = JsonAuraDBAdapter("plant_disease_graph.json")
    val llm = OllamaGemmaConnector()

    try {
        // Check connection
        val (isConnected, message) = llm.checkConnection()
        println("Ollama Status: $message")
        if (!isConnected) {
            println("Please ensure Ollama is running with gemma3n:e4b model")
            return
        }

        // Initialize system
        val diagnosticSystem = PlantDiseaseDignosticSystemGraphDirect(db, llm)
        val schema = diagnosticSystem.initialize()

        if (schema.nodeLabels.isNotEmpty()) {
            println("Graph loaded: ${schema.nodeLabels.size} node types, ${schema.relationshipTypes.size} relationships")

            // Show some graph statistics
            schema.nodeCounts.take(3).forEach { (nodeType, count) ->
                println("   - $nodeType: $count nodes")
            }
        }

        println("\n" + "=".repeat(60))
        println("🌿 PLANT DISEASE DIAGNOSTIC ASSISTANT (GRAPH DIRECT)")
        println("💡 Graph data directly drives symptom selection!")
        println("Commands: 'summary', 'state', 'reset', 'quit'")
        println("=".repeat(60))

        // Main loop
        while (true) {
            try {
                print("\n🌱 You: ")
                val userInput = readlnOrNull()?.trim()

                if (userInput.isNullOrEmpty()) {
                    continue
                }

                when (userInput.lowercase()) {
                    "quit", "exit" -> {
                        println("👋 Goodbye!")
                        break
                    }
                    "summary" -> {
                        println("\n" + "=".repeat(40))
                        println(diagnosticSystem.getDiagnosticSummary())
                        println("=".repeat(40))
                    }
                    "state" -> {
                        println(diagnosticSystem.getCurrentState())
                    }
                    "reset" -> {
                        diagnosticSystem.resetConversation()
                    }
                    else -> {
                        val response = diagnosticSystem.processUserInput(userInput)
                        println("\nPlant Pathologist: $response")
                    }
                }

            } catch (e: Exception) {
                println("Error: ${e.message}")
                e.printStackTrace()
            }
        }

    } catch (e: Exception) {
        println("🚨 System error: ${e.message}")
        e.printStackTrace()
    } finally {
        db.close()
        llm.close()
    }
} 