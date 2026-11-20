package com.example.methodmesh.transport.workflow

import com.example.methodmesh.core.methodmesh.*
import com.example.methodmesh.transport.ReturnMode
import org.junit.Assert.*
import org.junit.Test

class ExternalWorkflowProgressTest {
    private fun action(id: String, settings: Map<String, String> = emptyMap()) = ExternalActionRequest(id, id, settings)
    private fun result(value: String) = ExecutionResult(
        ExecutionRequest(action = "test", method = ArchitectureRef(ArchitectureId("test"), "Method")),
        TransformationStatus.Succeeded,
        observations = listOf(Observation(phenomenon = "test", values = mapOf("answer" to value), provenance = ProvenanceContext("test")))
    )
    private val request = ExternalWorkflowRequest(
        actions = listOf(action("first"), action("second"), action("third")),
        invocationContext = InvocationContext(), returns = emptyList(), returnMode = ReturnMode.Json
    )

    @Test fun `revisited first step receives neither its old outputs nor later outputs`() {
        val start = ExternalWorkflowProgress()
        val second = start.confirm(start.stepKey, request.actions[0], result("old first"))
        val third = second.confirm(second.stepKey, request.actions[1], result("old second"))
        val back = third.back().back()
        assertTrue(back.actionFor(request).settings.isEmpty())
        val replacement = back.confirm(back.stepKey, request.actions[0], result("new first"))
        assertEquals(1, replacement.confirmed.size)
        assertEquals("new first", replacement.actionFor(request).settings["previous_answer"])
        assertFalse(replacement.actionFor(request).settings.containsKey("step_2_answer"))
    }

    @Test fun `explicit configuration overrides pipes and previous means the latest earlier step`() {
        val start = ExternalWorkflowProgress()
        val second = start.confirm(start.stepKey, request.actions[0], result("one"))
        val third = second.confirm(second.stepKey, request.actions[1], result("two"))
        val configured = request.copy(actions = request.actions.take(2) + action("third", mapOf("answer" to "fixed")))
        val settings = third.actionFor(configured).settings
        assertEquals("fixed", settings["answer"])
        assertEquals("two", settings["previous_answer"])
        assertEquals("one", settings["step_1_answer"])
        assertEquals("two", settings["step_2_answer"])
    }

    @Test fun `late and duplicate confirmations cannot advance another step`() {
        val start = ExternalWorkflowProgress()
        val next = start.confirm(start.stepKey, request.actions[0], result("one"))
        assertSame(next, next.confirm(start.stepKey, request.actions[0], result("duplicate")))
        val back = next.back()
        assertNotEquals(start.stepKey, back.stepKey)
        assertSame(back, back.confirm(start.stepKey, request.actions[0], result("late")))
    }

    @Test fun `accepted step returns to intro after back and new attempts have distinct state keys`() {
        val start = ExternalWorkflowProgress().accept()
        val next = start.confirm(start.stepKey, request.actions[0], result("one")).accept()
        assertEquals(1, next.acceptedIndex)
        assertEquals(-1, next.back().acceptedIndex)
        assertNotEquals(start.stepKey, next.back().stepKey)
    }
}
