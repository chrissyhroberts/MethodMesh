package com.example.methodmesh.core

import com.example.methodmesh.core.methodmesh.ArchitectureId
import com.example.methodmesh.core.methodmesh.ArchitectureRef
import com.example.methodmesh.core.methodmesh.Entity
import com.example.methodmesh.core.methodmesh.ExecutionRequest
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.Observation
import com.example.methodmesh.core.methodmesh.ProvenanceContext
import com.example.methodmesh.core.methodmesh.TransformationStatus
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
