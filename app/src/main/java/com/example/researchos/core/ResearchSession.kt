package com.example.researchos.core

class ResearchSession {

    private val graph = ResearchGraph()

    val entities: Collection<Entity>
        get() = graph.entities.values

    val observations: Collection<Observation>
        get() = graph.observations.values

    fun graph(): ResearchGraph = graph

    fun add(entity: Entity) {
        graph.add(entity)
    }

    fun add(observation: Observation) {
        graph.add(observation)
    }

    fun relate(
        source: ObjectRef,
        type: RelationshipType,
        target: ObjectRef
    ) {
        graph.connect(source, type, target)
    }

    fun observationsFor(entity: Entity): List<Observation> =
        graph.observationsForEntity(entity.id)

    fun clear() {
        graph.entities.clear()
        graph.observations.clear()
        graph.relationships.clear()
    }
}