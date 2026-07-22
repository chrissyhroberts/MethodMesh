package com.example.researchos.core.researchos

/**
 * Context supplied by a caller such as ODK when it asks ResearchOS to do work.
 *
 * The caller usually owns the current form state; ResearchOS owns execution and
 * graph mutation. This object is the bridge: every observation/state/entity
 * created during a capability invocation can be associated with the study
 * entity that ODK already knows about.
 */
data class InvocationContext(
    val caller: String = "external_app",
    val entityType: String = "",
    val entityId: String = "",
    val visitId: String = "",
    val formId: String = "",
    val operatorId: String = ""
) {
    val canonicalEntityId: String
        get() = entityId.trim().let { id ->
            if (id.isBlank()) return@let ""
            if ('/' in id) id else "${entityType.trim().ifBlank { "entity" }}/$id"
        }

    val subjectRef: ArchitectureRef
        get() = ArchitectureRef(
            id = ArchitectureId(canonicalEntityId),
            type = "Entity",
            label = canonicalEntityId
        )

    fun subjectRef(): ArchitectureRef = subjectRef

    fun asMap(requestedCapability: String? = null): Map<String, String> = buildMap {
        put("caller", caller)
        if (canonicalEntityId.isNotBlank()) {
            put("context_entity_type", entityType.trim().ifBlank { "entity" })
            put("context_entity_id", canonicalEntityId)
            put("subject_id", canonicalEntityId)
        }
        if (visitId.isNotBlank()) put("visit_id", visitId)
        if (formId.isNotBlank()) put("form_id", formId)
        if (operatorId.isNotBlank()) put("operator_id", operatorId)
        if (!requestedCapability.isNullOrBlank()) put("requested_capability", requestedCapability)
    }

    fun subjectEntity(temporalContext: TemporalContext = TemporalContext()): Entity = Entity(
        id = ArchitectureId(canonicalEntityId),
        entityType = entityType.trim().ifBlank { "entity" },
        attributes = buildMap {
            put("external_id", canonicalEntityId.substringAfter('/'))
            put("source", caller)
            put("caller", caller)
            if (visitId.isNotBlank()) put("visit_id", visitId)
            if (formId.isNotBlank()) put("form_id", formId)
            if (operatorId.isNotBlank()) put("operator_id", operatorId)
        },
        temporalContext = temporalContext
    )

    companion object {
        fun from(context: Map<String, String>): InvocationContext? {
            val id = context["context_entity_id"] ?: context["subject_id"] ?: return null
            return InvocationContext(
                caller = context["caller"].orEmpty().ifBlank { "odk" },
                entityType = context["context_entity_type"].orEmpty().ifBlank { id.substringBefore('/', "entity") },
                entityId = id,
                visitId = context["visit_id"].orEmpty(),
                formId = context["form_id"].orEmpty(),
                operatorId = context["operator_id"].orEmpty()
            )
        }
    }
}

fun ExecutionResult.withInvocationContext(context: InvocationContext?): ExecutionResult {
    if (context == null || context.canonicalEntityId.isBlank()) return this

    val contextEntity = context.subjectEntity(request.temporalContext)
    val contextRef = context.subjectRef

    val updatedObservations = observations.map { observation ->
        if (observation.subject == null) observation.copy(subject = contextRef) else observation
    }
    val updatedStates = states.map { state ->
        if (state.subject.id.value.isBlank()) state.copy(subject = contextRef) else state
    }

    val observationLinks = updatedObservations.map { observation ->
        Relationship(
            relationshipType = "subject_of",
            from = contextRef,
            to = ArchitectureRef(observation.id, observation.objectType, observation.phenomenon),
            attributes = mapOf(
                "source" to "invocation_context",
                "execution_id" to request.id.value
            ),
            temporalContext = observation.temporalContext,
            spatialContext = observation.spatialContext
        )
    }

    val nonContextSubjectLinks = updatedObservations.mapNotNull { observation ->
        val subject = observation.subject ?: return@mapNotNull null
        if (subject.id.value == contextRef.id.value) return@mapNotNull null
        Relationship(
            relationshipType = "associated_with",
            from = contextRef,
            to = subject,
            attributes = mapOf(
                "via_observation_id" to observation.id.value,
                "execution_id" to request.id.value
            ),
            temporalContext = observation.temporalContext,
            spatialContext = observation.spatialContext
        )
    }

    val stateLinks = updatedStates.map { state ->
        Relationship(
            relationshipType = "has_state",
            from = contextRef,
            to = ArchitectureRef(state.id, state.objectType, state.stateType),
            attributes = mapOf(
                "source" to "invocation_context",
                "execution_id" to request.id.value
            ),
            temporalContext = state.temporalContext,
            spatialContext = state.spatialContext
        )
    }

    val allEntities = if (entities.any { it.id.value == contextEntity.id.value }) {
        entities
    } else {
        listOf(contextEntity) + entities
    }

    val existingKeys = relationships.map { Triple(it.relationshipType, it.from.id.value, it.to.id.value) }.toSet()
    val newRelationships = (observationLinks + nonContextSubjectLinks + stateLinks)
        .filterNot { Triple(it.relationshipType, it.from.id.value, it.to.id.value) in existingKeys }

    return copy(
        entities = allEntities,
        observations = updatedObservations,
        states = updatedStates,
        relationships = relationships + newRelationships,
        diagnostics = diagnostics + mapOf(
            "context_entity_id" to context.canonicalEntityId,
            "context_entity_type" to context.entityType,
            "caller" to context.caller
        )
    )
}
