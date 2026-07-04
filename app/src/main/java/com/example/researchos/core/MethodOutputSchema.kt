package com.example.researchos.core

import com.example.researchos.core.researchos.KnowledgeObjectType

/**
 * Declared output contract for a method.
 *
 * `fields` remains the flat transport/export schema used by the current UI and
 * ODK/intent helpers. `graphOutputs` is the canonical ResearchOS declaration:
 * it states which knowledge objects the method produces and how flat transport
 * fields map back into those objects.
 */
data class MethodOutputSchema(
    val fields: List<MethodField> = emptyList(),
    val graphOutputs: List<GraphOutput> = emptyList(),
    val transportFields: List<TransportField> = emptyList()
) {
    fun fieldIds(): Set<String> =
        fields.map { it.id }.toSet()

    fun graphFieldIds(): Set<String> =
        graphOutputs.flatMap { it.fields }.map { it.id }.toSet()
}

data class GraphOutput(
    val id: String,
    val objectType: KnowledgeObjectType,
    val phenomenon: String? = null,
    val stateType: String? = null,
    val relationshipType: String? = null,
    val entityType: String? = null,
    val subjectRole: String? = null,
    val description: String? = null,
    val fields: List<GraphField> = emptyList()
)

data class GraphField(
    val id: String,
    val path: String,
    val type: MethodFieldType,
    val requiredWhen: RequiredWhen = RequiredWhen.IfAvailable,
    val description: String? = null
)

data class TransportField(
    val id: String,
    val sourcePath: String,
    val type: MethodFieldType,
    val description: String? = null
)

enum class RequiredWhen {
    Always,
    OnSuccessfulCapture,
    IfAvailable,
    PreviewOnly,
    TransportOnly
}
