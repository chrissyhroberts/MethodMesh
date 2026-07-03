package com.example.researchos.core

/**
 * Minimal in-memory ResearchOS graph.
 *
 * This is not a database. It is a lightweight runtime structure for connecting
 * ResearchOS knowledge objects during prototyping and validation.
 */
class ResearchGraph {

    val entities: MutableMap<String, Entity> = mutableMapOf()
    val observations: MutableMap<String, Observation> = mutableMapOf()
    val relationships: MutableList<Relationship> = mutableListOf()

    fun add(entity: Entity): Entity {
        entities[entity.id] = entity
        return entity
    }

    fun add(observation: Observation): Observation {
        observations[observation.id] = observation
        return observation
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
}