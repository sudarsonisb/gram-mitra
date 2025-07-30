package com.plantdiagnostic.models

import kotlinx.serialization.Serializable

@Serializable
data class GraphNode(
    val id: String,
    val type: String,
    val name: String? = null,
    val description: String? = null,
    val treatment: String? = null,
    val content: String? = null,
    val instructions: String? = null
)

@Serializable
data class GraphRelationship(
    val source: String,
    val target: String,
    val type: String
)

@Serializable
data class GraphData(
    val nodes: List<GraphNode> = emptyList(),
    val relationships: List<GraphRelationship> = emptyList()
)

@Serializable
data class DiseaseResult(
    val disease: String,
    val description: String,
    val allSymptoms: List<String>,
    val matched: List<String>,
    val matchCount: Int,
    val totalSymptoms: Int,
    val matchPercentage: Double
)

@Serializable
data class SymptomResult(
    val symptom: String,
    val diseases: List<String>,
    val count: Int
)

@Serializable
data class SolutionResult(
    val solutionName: String,
    val solutionDescription: String,
    val treatment: String
)

@Serializable
data class SchemaResult(
    val nodeLabels: List<String>,
    val relationshipTypes: List<String>,
    val nodeCounts: List<Pair<String, Int>>
) 