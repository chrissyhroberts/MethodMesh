package com.example.methodmesh.transport.workflow.ui

import com.example.methodmesh.core.methodmesh.InvocationContext
import com.example.methodmesh.transport.ReturnMode
import com.example.methodmesh.transport.workflow.ExternalActionRequest
import com.example.methodmesh.transport.workflow.ExternalWorkflowRequest
import org.junit.Assert.assertEquals
import org.junit.Test

class CapabilityCompletionModeTest {
    @Test
    fun `dashboard requires manual confirmation`() {
        assertEquals(CapabilityCompletionMode.ManualConfirmation, context("dashboard").completionMode)
    }

    @Test
    fun `external caller returns automatically`() {
        assertEquals(CapabilityCompletionMode.AutomaticReturn, context("odk_collect").completionMode)
    }

    @Test
    fun `dependency can explicitly return automatically inside dashboard`() {
        val dependency = context("dashboard").copy(completionMode = CapabilityCompletionMode.AutomaticReturn)
        assertEquals(CapabilityCompletionMode.AutomaticReturn, dependency.completionMode)
    }

    private fun context(source: String): CapabilityScreenContext {
        val action = ExternalActionRequest("test.method", "test.method")
        return CapabilityScreenContext(
            action = action,
            request = ExternalWorkflowRequest(
                actions = listOf(action), invocationContext = InvocationContext(),
                returns = emptyList(), returnMode = ReturnMode.Json, source = source
            ),
            stepNumber = 1,
            totalSteps = 1
        )
    }
}
