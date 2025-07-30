package com.plantdiagnostic

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import io.github.cdimascio.dotenv.dotenv

@Serializable
data class OllamaRequest(
    val model: String,
    val prompt: String,
    val stream: Boolean = false,
    val options: OllamaOptions = OllamaOptions()
)

@Serializable
data class OllamaOptions(
    val temperature: Double = 0.6,
    val num_predict: Int = 500
)

@Serializable
data class OllamaResponse(
    val response: String,
    val done: Boolean = true
)

@Serializable
data class OllamaModel(
    val name: String
)

@Serializable
data class OllamaModelsResponse(
    val models: List<OllamaModel>
)

class OllamaGemmaConnector {
    private val dotenv = dotenv()
    private val baseUrl = dotenv["OLLAMA_BASE_URL"] ?: "http://localhost:11434"
    private val model = dotenv["OLLAMA_MODEL"] ?: "gemma3n:e4b"
    private val apiUrl = "$baseUrl/api/generate"
    
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    suspend fun generateSummary(symptoms: List<String>, context: String = ""): String {
        val systemPrompt = """Provide plant disease diagnosis with: 
1) Symptom analysis 
2) Disease identification 
3) Treatment recommendations 
4) Prevention measures"""

        val prompt = "$systemPrompt\n\nCONTEXT: $context\nSYMPTOMS: ${symptoms.joinToString(", ")}"

        val payload = OllamaRequest(
            model = model,
            prompt = prompt,
            stream = false,
            options = OllamaOptions(temperature = 0.6, num_predict = 500)
        )

        return try {
            val response: OllamaResponse = client.post(apiUrl) {
                contentType(ContentType.Application.Json)
                setBody(payload)
            }.body()
            response.response.trim()
        } catch (e: Exception) {
            "Error generating summary: ${e.message}"
        }
    }

    suspend fun checkConnection(): Pair<Boolean, String> {
        return try {
            val response: OllamaModelsResponse = client.get("$baseUrl/api/tags").body()
            val models = response.models.map { it.name }
            
            if (model in models) {
                true to "✅ $model available"
            } else {
                false to "❌ $model not found. Available: $models"
            }
        } catch (e: Exception) {
            false to "❌ Connection failed: ${e.message}"
        }
    }

    fun close() {
        client.close()
    }
} 