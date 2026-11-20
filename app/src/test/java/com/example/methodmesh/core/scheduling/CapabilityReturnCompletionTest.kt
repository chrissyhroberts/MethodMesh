package com.example.methodmesh.core.scheduling

import com.example.methodmesh.core.methodmesh.TransformationStatus
import com.example.methodmesh.transport.workflow.WorkflowResultProjection
import com.example.methodmesh.core.methodmesh.ArchitectureId
import com.example.methodmesh.core.methodmesh.ArchitectureRef
import com.example.methodmesh.core.methodmesh.ExecutionRequest
import com.example.methodmesh.core.methodmesh.ExecutionResult
import org.junit.Assert.*
import org.junit.Test

class CapabilityReturnCompletionTest {
    private fun full(status: TransformationStatus): String = WorkflowResultProjection.fullJson(listOf(ExecutionResult(
        request = ExecutionRequest(action = "fixture", method = ArchitectureRef(ArchitectureId("fixture"), "Method")),
        status = status
    )))

    @Test fun `successful canonical execution can advance`() {
        assertTrue(CapabilityReturnCompletion.succeeded(true, mapOf(
            "methodmesh_status" to "Succeeded", "methodmesh_full_json" to full(TransformationStatus.Succeeded)
        )))
    }

    @Test fun `delivering a failed cancelled unsupported or requested execution cannot advance`() {
        TransformationStatus.entries.filterNot { it == TransformationStatus.Succeeded }.forEach { status ->
            assertFalse(status.name, CapabilityReturnCompletion.succeeded(true, mapOf(
                "methodmesh_status" to status.name, "methodmesh_full_json" to full(status)
            )))
        }
    }

    @Test fun `CORE uses the mandatory full sidecar when the flat status was projected out`() {
        assertTrue(CapabilityReturnCompletion.succeeded(true, mapOf("methodmesh_full_json" to full(TransformationStatus.Succeeded))))
        assertFalse(CapabilityReturnCompletion.succeeded(true, mapOf("methodmesh_full_json" to full(TransformationStatus.Failed))))
    }

    @Test fun `a conflicting malformed missing or unknown advertised status cannot advance`() {
        listOf(
            mapOf("methodmesh_status" to "Succeeded", "methodmesh_full_json" to full(TransformationStatus.Failed)),
            mapOf("methodmesh_status" to "Failed", "methodmesh_full_json" to full(TransformationStatus.Succeeded)),
            mapOf("methodmesh_full_json" to "not JSON"),
            mapOf("methodmesh_full_json" to "{}"),
            mapOf("methodmesh_status" to "unknown"),
            mapOf("methodmesh_status" to null)
        ).forEach { assertFalse(it.toString(), CapabilityReturnCompletion.succeeded(true, it)) }
    }

    @Test fun `namespaced return metadata retains success and failure semantics`() {
        assertTrue(CapabilityReturnCompletion.succeeded(true, mapOf("visit_methodmesh_full_json" to full(TransformationStatus.Succeeded))))
        assertFalse(CapabilityReturnCompletion.succeeded(true, mapOf("visit_methodmesh_full_json" to full(TransformationStatus.Failed))))
        assertFalse(CapabilityReturnCompletion.succeeded(true, mapOf("visit_methodmesh_status" to "Cancelled")))
    }

    @Test fun `transport cancellation cannot be promoted by a successful sidecar`() {
        assertFalse(CapabilityReturnCompletion.succeeded(false, mapOf("methodmesh_full_json" to full(TransformationStatus.Succeeded))))
    }

    @Test fun `legacy result code only callers retain their prior behavior`() {
        assertTrue(CapabilityReturnCompletion.succeeded(true, emptyMap()))
        assertTrue(CapabilityReturnCompletion.succeeded(true, mapOf("value" to "legacy result")))
        assertFalse(CapabilityReturnCompletion.succeeded(false, emptyMap()))
    }

    @Test fun `canonical current result takes precedence over prefixed historical metadata`() {
        assertTrue(CapabilityReturnCompletion.succeeded(true, mapOf(
            "methodmesh_full_json" to full(TransformationStatus.Succeeded),
            "previous_methodmesh_full_json" to full(TransformationStatus.Failed)
        )))
        assertFalse(CapabilityReturnCompletion.succeeded(true, mapOf(
            "methodmesh_full_json" to full(TransformationStatus.Failed),
            "previous_methodmesh_full_json" to full(TransformationStatus.Succeeded)
        )))
    }
}
