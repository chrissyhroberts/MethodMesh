package com.example.researchos.core

/**
 * In-memory ResearchOS graph used by the Android prototype.
 *
 * Legacy Entity/Observation collections are retained as a compatibility surface
 * for the current Compose UI and transport previews. The canonical runtime
 * graph is AS/ResearchOS-native and stores knowledge objects plus explicit
 * transformations returned by ExecutionResult.
 */
class ResearchGraph {

    // Legacy compatibility graph. Prefer the AS collections below for new code.
    val entities: MutableMap<String, Entity> = mutableMapOf()
    val observations: MutableMap<String, Observation> = mutableMapOf()
    val relationships: MutableList<Relationship> = mutableListOf()

    // AS/ResearchOS-native graph.
    val asEntities: MutableMap<String, com.example.researchos.core.researchos.Entity> = mutableMapOf()
    val asAttributes: MutableMap<String, com.example.researchos.core.researchos.Attribute> = mutableMapOf()
    val asObservations: MutableMap<String, com.example.researchos.core.researchos.Observation> = mutableMapOf()
    val asRelationships: MutableMap<String, com.example.researchos.core.researchos.Relationship> = mutableMapOf()
    val asClassifications: MutableMap<String, com.example.researchos.core.researchos.Classification> = mutableMapOf()
    val asStates: MutableMap<String, com.example.researchos.core.researchos.State> = mutableMapOf()
    val transformations: MutableMap<String, com.example.researchos.core.researchos.Transformation> = mutableMapOf()
    val executionResults: MutableMap<String, com.example.researchos.core.researchos.ExecutionResult> = mutableMapOf()

    fun add(entity: Entity): Entity {
        entities[entity.id] = entity
        return entity
    }

    fun add(observation: Observation): Observation {
        observations[observation.id] = observation
        return observation
    }

    fun add(entity: com.example.researchos.core.researchos.Entity): com.example.researchos.core.researchos.Entity {
        asEntities[entity.id.value] = entity
        return entity
    }

    fun add(attribute: com.example.researchos.core.researchos.Attribute): com.example.researchos.core.researchos.Attribute {
        asAttributes[attribute.id.value] = attribute
        return attribute
    }

    fun add(observation: com.example.researchos.core.researchos.Observation): com.example.researchos.core.researchos.Observation {
        asObservations[observation.id.value] = observation
        return observation
    }

    fun add(relationship: com.example.researchos.core.researchos.Relationship): com.example.researchos.core.researchos.Relationship {
        asRelationships[relationship.id.value] = relationship
        return relationship
    }

    fun add(classification: com.example.researchos.core.researchos.Classification): com.example.researchos.core.researchos.Classification {
        asClassifications[classification.id.value] = classification
        return classification
    }

    fun add(state: com.example.researchos.core.researchos.State): com.example.researchos.core.researchos.State {
        asStates[state.id.value] = state
        return state
    }

    fun add(transformation: com.example.researchos.core.researchos.Transformation): com.example.researchos.core.researchos.Transformation {
        transformations[transformation.id.value] = transformation
        return transformation
    }

    fun record(result: com.example.researchos.core.researchos.ExecutionResult): com.example.researchos.core.researchos.ExecutionResult {
        executionResults[result.request.id.value] = result

        val invocationContext = com.example.researchos.core.researchos.InvocationContext.from(result.request.context)
        val subjectRef = invocationContext?.subjectRef()
        invocationContext?.let { add(it.subjectEntity(result.request.temporalContext)) }

        result.entities.forEach { add(it) }
        result.attributes.forEach { add(it) }
        result.observations.forEach { add(it) }
        result.relationships.forEach { add(it) }
        result.classifications.forEach { add(it) }
        result.states.forEach { add(it) }
        result.transformations.forEach { add(it) }

        // Context relationships are the operational bridge from an ODK form row
        // to the richer graph objects created by ResearchOS. Capabilities can
        // still use their own domain-specific subjects (e.g. an NFC tag), while
        // the caller's participant/specimen/visit remains queryable as the
        // invocation context.
        if (subjectRef != null) {
            result.entities
                .filterNot { it.id.value == subjectRef.id.value }
                .forEach { entity ->
                    add(
                        com.example.researchos.core.researchos.Relationship(
                            relationshipType = "context.associated_entity",
                            from = subjectRef,
                            to = com.example.researchos.core.researchos.ArchitectureRef(entity.id, entity.objectType, entity.entityType),
                            attributes = invocationRelationshipAttributes(result)
                        )
                    )
                }
            result.observations.forEach { observation ->
                add(
                    com.example.researchos.core.researchos.Relationship(
                        relationshipType = "context.has_observation",
                        from = subjectRef,
                        to = com.example.researchos.core.researchos.ArchitectureRef(observation.id, observation.objectType, observation.phenomenon),
                        attributes = invocationRelationshipAttributes(result)
                    )
                )
            }
            result.states.forEach { state ->
                add(
                    com.example.researchos.core.researchos.Relationship(
                        relationshipType = "context.has_state",
                        from = subjectRef,
                        to = com.example.researchos.core.researchos.ArchitectureRef(state.id, state.objectType, state.stateType),
                        attributes = invocationRelationshipAttributes(result)
                    )
                )
            }
            result.transformations.forEach { transformation ->
                add(
                    com.example.researchos.core.researchos.Relationship(
                        relationshipType = "context.has_transformation",
                        from = subjectRef,
                        to = com.example.researchos.core.researchos.ArchitectureRef(transformation.id, transformation.objectType, transformation.action),
                        attributes = invocationRelationshipAttributes(result)
                    )
                )
            }
        }

        return result
    }

    private fun invocationRelationshipAttributes(result: com.example.researchos.core.researchos.ExecutionResult): Map<String, String> = buildMap {
        put("execution_id", result.request.id.value)
        put("method_id", result.request.method.id.value)
        result.request.context["caller"]?.let { put("caller", it) }
        result.request.context["visit_id"]?.let { put("visit_id", it) }
        result.request.context["form_id"]?.let { put("form_id", it) }
        result.request.context["operator_id"]?.let { put("operator_id", it) }
    }

    fun connect(
        source: ObjectRef,
        type: RelationshipType,
        target: ObjectRef,
        metadata: Map<String, Any?> = emptyMap()
    ): Relationship {
        val relationship = Relationship(
            source = source,
            type = type,
            target = target,
            metadata = metadata
        )

        relationships.add(relationship)
        return relationship
    }

    fun relationshipsFrom(id: String): List<Relationship> =
        relationships.filter { it.source.id == id }

    fun relationshipsTo(id: String): List<Relationship> =
        relationships.filter { it.target.id == id }

    fun observationsForEntity(entityId: String): List<Observation> {
        val observationIds = relationships
            .filter {
                it.source.id == entityId &&
                        it.type == RelationshipType.Observes
            }
            .map { it.target.id }
            .toSet()

        return observationIds.mapNotNull { observations[it] }
    }

    fun asObservationsForSubject(subjectId: String): List<com.example.researchos.core.researchos.Observation> =
        asObservations.values.filter { it.subject?.id?.value == subjectId }

    fun clear() {
        entities.clear()
        observations.clear()
        relationships.clear()
        asEntities.clear()
        asAttributes.clear()
        asObservations.clear()
        asRelationships.clear()
        asClassifications.clear()
        asStates.clear()
        transformations.clear()
        executionResults.clear()
    }
}
