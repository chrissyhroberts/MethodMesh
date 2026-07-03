package com.example.researchos.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ResearchGraphTest {

    @Test
    fun entityCanBeConnectedToObservation() {
        val graph = ResearchGraph()

        val participant = Entity(
            id = "participant:001",
            type = "Participant",
            label = "P001"
        )

        graph.add(participant)

        val observation = Observation(
            output = MethodOutput(
                fields = mapOf(
                    "height_cm" to 172
                )
            ),
            provenance = Provenance(
                methodId = "method:height",
                methodVersion = "1.0.0"
            )
        )

        graph.add(observation)

        graph.connect(
            source = ObjectRef(participant.id, "Entity", participant.label),
            type = RelationshipType.Observes,
            target = ObjectRef(observation.id, "Observation")
        )

        val observations = graph.observationsForEntity(participant.id)

        assertEquals(1, observations.size)
        assertEquals(observation.id, observations.first().id)
    }
}