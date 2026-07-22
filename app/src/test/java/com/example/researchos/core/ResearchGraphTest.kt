package com.example.researchos.core

import com.example.researchos.core.researchos.ArchitectureId
import com.example.researchos.core.researchos.ArchitectureRef
import com.example.researchos.core.researchos.Entity
import com.example.researchos.core.researchos.ExecutionRequest
import com.example.researchos.core.researchos.ExecutionResult
import com.example.researchos.core.researchos.Observation
import com.example.researchos.core.researchos.ProvenanceContext
import com.example.researchos.core.researchos.TransformationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResearchGraphTest {

    @Test
    fun canonicalExecutionResultIsRecordedWithContextRelationship() {
        val graph = ResearchGraph()
        val observation = Observation(
            phenomenon = "anthropometry.height",
            values = mapOf("height_cm" to "172"),
            provenance = ProvenanceContext(provider = "test", methodId = "height.measure", methodVersion = "1.0.0")
        )
        val request = ExecutionRequest(
            action = "height.measure",
            method = ArchitectureRef(ArchitectureId("height.measure"), "Method"),
            context = mapOf(
                "caller" to "test",
                "context_entity_type" to "participant",
                "context_entity_id" to "participant/P001",
                "subject_id" to "participant/P001"
            )
        )
        graph.record(
            ExecutionResult(
                request = request,
                status = TransformationStatus.Succeeded,
                observations = listOf(observation)
            )
        )

        assertEquals(observation, graph.asObservations[observation.id.value])
        assertTrue(graph.asEntities.containsKey("participant/P001"))
        assertTrue(graph.asRelationships.values.any {
            it.relationshipType == "context.has_observation" &&
                it.from.id.value == "participant/P001" &&
                it.to.id.value == observation.id.value
        })
    }
}
