package com.example.methodmesh.core

/**
 * In-memory MethodMesh graph used by the Android prototype.
 *
 * AS1.00-native graph storing knowledge objects and transformations returned by
 * canonical execution results.
 */
class ResearchGraph {

    val asEntities: MutableMap<String, com.example.methodmesh.core.methodmesh.Entity> = mutableMapOf()
    val asAttributes: MutableMap<String, com.example.methodmesh.core.methodmesh.Attribute> = mutableMapOf()
    val asObservations: MutableMap<String, com.example.methodmesh.core.methodmesh.Observation> = mutableMapOf()
    val asRelationships: MutableMap<String, com.example.methodmesh.core.methodmesh.Relationship> = mutableMapOf()
    val asClassifications: MutableMap<String, com.example.methodmesh.core.methodmesh.Classification> = mutableMapOf()
    val asStates: MutableMap<String, com.example.methodmesh.core.methodmesh.State> = mutableMapOf()
    val transformations: MutableMap<String, com.example.methodmesh.core.methodmesh.Transformation> = mutableMapOf()
    val executionResults: MutableMap<String, com.example.methodmesh.core.methodmesh.ExecutionResult> = mutableMapOf()

    fun add(entity: com.example.methodmesh.core.methodmesh.Entity): com.example.methodmesh.core.methodmesh.Entity {
        asEntities[entity.id.value] = entity
        return entity
    }

    fun add(attribute: com.example.methodmesh.core.methodmesh.Attribute): com.example.methodmesh.core.methodmesh.Attribute {
        asAttributes[attribute.id.value] = attribute
        return attribute
    }

    fun add(observation: com.example.methodmesh.core.methodmesh.Observation): com.example.methodmesh.core.methodmesh.Observation {
        asObservations[observation.id.value] = observation
        return observation
    }

    fun add(relationship: com.example.methodmesh.core.methodmesh.Relationship): com.example.methodmesh.core.methodmesh.Relationship {
        asRelationships[relationship.id.value] = relationship
        return relationship
    }

    fun add(classification: com.example.methodmesh.core.methodmesh.Classification): com.example.methodmesh.core.methodmesh.Classification {
        asClassifications[classification.id.value] = classification
        return classification
    }

    fun add(state: com.example.methodmesh.core.methodmesh.State): com.example.methodmesh.core.methodmesh.State {
        asStates[state.id.value] = state
        return state
    }

    fun add(transformation: com.example.methodmesh.core.methodmesh.Transformation): com.example.methodmesh.core.methodmesh.Transformation {
        transformations[transformation.id.value] = transformation
        return transformation
    }

    fun record(result: com.example.methodmesh.core.methodmesh.ExecutionResult): com.example.methodmesh.core.methodmesh.ExecutionResult {
        executionResults[result.request.id.value] = result

        val invocationContext = com.example.methodmesh.core.methodmesh.InvocationContext.from(result.request.context)
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
        // to the richer graph objects created by MethodMesh. Capabilities can
        // still use their own domain-specific subjects (e.g. an NFC tag), while
        // the caller's participant/specimen/visit remains queryable as the
        // invocation context.
        if (subjectRef != null) {
            result.entities
                .filterNot { it.id.value == subjectRef.id.value }
                .forEach { entity ->
                    add(
                        com.example.methodmesh.core.methodmesh.Relationship(
                            relationshipType = "context.associated_entity",
                            from = subjectRef,
                            to = com.example.methodmesh.core.methodmesh.ArchitectureRef(entity.id, entity.objectType, entity.entityType),
                            attributes = invocationRelationshipAttributes(result)
                        )
                    )
                }
            result.observations.forEach { observation ->
                add(
                    com.example.methodmesh.core.methodmesh.Relationship(
                        relationshipType = "context.has_observation",
                        from = subjectRef,
                        to = com.example.methodmesh.core.methodmesh.ArchitectureRef(observation.id, observation.objectType, observation.phenomenon),
                        attributes = invocationRelationshipAttributes(result)
                    )
                )
            }
            result.states.forEach { state ->
                add(
                    com.example.methodmesh.core.methodmesh.Relationship(
                        relationshipType = "context.has_state",
                        from = subjectRef,
                        to = com.example.methodmesh.core.methodmesh.ArchitectureRef(state.id, state.objectType, state.stateType),
                        attributes = invocationRelationshipAttributes(result)
                    )
                )
            }
            result.transformations.forEach { transformation ->
                add(
                    com.example.methodmesh.core.methodmesh.Relationship(
                        relationshipType = "context.has_transformation",
                        from = subjectRef,
                        to = com.example.methodmesh.core.methodmesh.ArchitectureRef(transformation.id, transformation.objectType, transformation.action),
                        attributes = invocationRelationshipAttributes(result)
                    )
                )
            }
        }

        return result
    }

    private fun invocationRelationshipAttributes(result: com.example.methodmesh.core.methodmesh.ExecutionResult): Map<String, String> = buildMap {
        put("execution_id", result.request.id.value)
        put("method_id", result.request.method.id.value)
        result.request.context["caller"]?.let { put("caller", it) }
        result.request.context["visit_id"]?.let { put("visit_id", it) }
        result.request.context["form_id"]?.let { put("form_id", it) }
        result.request.context["operator_id"]?.let { put("operator_id", it) }
    }

    fun asObservationsForSubject(subjectId: String): List<com.example.methodmesh.core.methodmesh.Observation> =
        asObservations.values.filter { it.subject?.id?.value == subjectId }

    fun clear() {
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
