package com.example.methodmesh.transport.android

import com.example.methodmesh.core.methodmesh.*
import com.example.methodmesh.transport.workflow.ConfirmedWorkflowStep
import com.example.methodmesh.transport.workflow.ExternalActionRequest
import com.example.methodmesh.transport.workflow.ExternalWorkflowProgress
import org.junit.Assert.*
import org.junit.Test

class ExternalWorkflowViewModelTest {
    @Test fun `log warnings belong only to the current confirmed prefix not abandoned attempts`() {
        val state = ExternalWorkflowViewModel()
        val result = ExecutionResult(ExecutionRequest(action = "test", method = ArchitectureRef(ArchitectureId("test"), "Method")), TransformationStatus.Succeeded)
        state.logWarnings["old-abandoned-execution"] = "old warning"
        state.logWarnings[result.request.id.value] = "current warning"
        state.progress = ExternalWorkflowProgress(confirmed = listOf(ConfirmedWorkflowStep(ExternalActionRequest("test", "test"), result)), index = 1)
        assertTrue(state.logWarning()!!.contains("current warning"))
        assertFalse(state.logWarning()!!.contains("old warning"))
        state.progress = state.progress.back()
        assertNull(state.logWarning())
    }
    @Test fun `retained workflow accepts ordinary activity recreation`() {
        val state = ExternalWorkflowViewModel()
        state.initialise(restoringActivity = false)
        val progress = state.progress.accept()
        state.progress = progress
        state.initialise(restoringActivity = true)
        assertFalse(state.interrupted)
        assertSame(progress, state.progress)
    }

    @Test fun `lost process state requires caller retry before any automatic execution`() {
        val freshState = ExternalWorkflowViewModel()
        freshState.initialise(restoringActivity = true)
        assertTrue(freshState.interrupted)
        assertTrue(freshState.progress.confirmed.isEmpty())
        assertTrue(freshState.genericResults.isEmpty())
    }
}
