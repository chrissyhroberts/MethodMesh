package com.example.researchos.core

object DemoResearchGraph {

    fun create(): ResearchGraph {
        val graph = ResearchGraph()

        val participant = Entity(
            id = "participant:001",
            type = "Participant",
            label = "P001"
        )

        graph.add(participant)

        val height = Observation(
            output = MethodOutput(
                fields = mapOf("height_cm" to 172)
            ),
            provenance = Provenance(
                methodId = "method:height",
                methodVersion = "1.0.0"
            )
        )

        graph.add(height)

        graph.connect(
            source = ObjectRef(participant.id, "Entity", participant.label),
            type = RelationshipType.Observes,
            target = ObjectRef(height.id, "Observation")
        )

        return graph
    }
}