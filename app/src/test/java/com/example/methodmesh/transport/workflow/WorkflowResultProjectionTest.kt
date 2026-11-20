package com.example.methodmesh.transport.workflow

import com.example.methodmesh.core.methodmesh.*
import com.example.methodmesh.core.timeassurance.*
import com.example.methodmesh.transport.ReturnMode
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WorkflowResultProjectionTest {
    private fun clock(elapsed: Long) = ClockEvidenceSnapshot(
        observedWallTimeIso = "2026-10-20T12:00:00Z",
        elapsedRealtimeMillis = elapsed,
        bootSessionId = "test-boot",
        evidenceState = ClockEvidenceState.WALL_CLOCK_ONLY,
        monotonicContinuity = MonotonicContinuity.NO_ANCHOR,
        reason = "test"
    )
    private fun result(value: String) = ExecutionResult(
        ExecutionRequest(action = "test", method = ArchitectureRef(ArchitectureId("test"), "Method")),
        TransformationStatus.Succeeded,
        observations = listOf(Observation(phenomenon = "test", values = mapOf("answer" to value), provenance = ProvenanceContext("test")))
    )

    @Test fun `single result stays exactly as committed including unknown clock evidence`() {
        val committed = result("answer")
        assertSame(committed, WorkflowResultProjection.combine(listOf(committed)))
        assertNull(WorkflowResultProjection.combine(listOf(committed)).timeAssurance)
        assertEquals(committed.request.id.value, JSONObject(WorkflowResultProjection.fullJson(listOf(committed))).getString("methodmesh_execution_id"))
    }

    @Test fun `multi step full JSON preserves each execution and failed step diagnostic`() {
        val first = result("one")
        val second = result("two").copy(status = TransformationStatus.Failed, diagnostics = mapOf("error" to "failed\tstep"))
        val combined = WorkflowResultProjection.combine(listOf(first, second))
        assertEquals(TransformationStatus.Failed, combined.status)
        assertEquals(2, combined.observations.size)
        val steps = JSONObject(WorkflowResultProjection.fullJson(listOf(first, second))).getJSONArray("workflow_steps")
        assertEquals(2, steps.length())
        assertEquals(first.request.id.value, steps.getJSONObject(0).getString("methodmesh_execution_id"))
        assertEquals(second.request.id.value, steps.getJSONObject(1).getString("methodmesh_execution_id"))
        assertEquals("failed\tstep", steps.getJSONObject(1).getString("diagnostic_error"))
        assertEquals("Failed", steps.getJSONObject(1).getString("methodmesh_status"))
    }

    @Test fun `aggregate completion uses the last committed clock and keeps individual clocks`() {
        val first = result("one").copy(timeAssurance = clock(100))
        val last = result("two").copy(timeAssurance = clock(500))
        assertSame(first.timeAssurance, WorkflowResultProjection.combine(listOf(first)).timeAssurance)
        assertSame(last.timeAssurance, WorkflowResultProjection.combine(listOf(first, last)).timeAssurance)
        val steps = JSONObject(WorkflowResultProjection.fullJson(listOf(first, last))).getJSONArray("workflow_steps")
        assertEquals(100L, steps.getJSONObject(0).getJSONObject("execution_timing").getJSONObject("completed").getLong("elapsed_realtime_ms"))
        assertEquals(500L, steps.getJSONObject(1).getJSONObject("execution_timing").getJSONObject("completed").getLong("elapsed_realtime_ms"))
    }

    @Test fun `handled cancellation returns canonical status and metadata for the current step`() {
        val request = ExternalWorkflowRequest(
            actions = listOf(ExternalActionRequest("first", "first"), ExternalActionRequest("second", "second")),
            invocationContext = InvocationContext(caller = "test", entityType = "participant", entityId = "P99"),
            returns = emptyList(), returnMode = ReturnMode.Json
        )
        val fields = WorkflowResultProjection.cancellationFields(request, 1, 1, "Cancelled\tby user")
        assertEquals("cancelled", fields["methodmesh_closeout_status"])
        assertEquals("1", fields["methodmesh_closeout_step_count"])
        assertEquals("false", fields["methodmesh_closeout_has_payload"])
        assertEquals("Cancelled", fields["methodmesh_status"])
        val json = JSONObject(fields.getValue("methodmesh_full_json")!!)
        assertEquals("second", json.getString("methodmesh_method_id"))
        assertEquals("participant/P99", json.getString("subject_id"))
        assertEquals("Cancelled\tby user", json.getString("diagnostic_error"))
        assertTrue(json.has("time_assurance"))
    }
}
