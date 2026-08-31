package com.example.methodmesh.transport

import com.example.methodmesh.core.methodmesh.ArchitectureId
import com.example.methodmesh.core.methodmesh.ArchitectureRef
import com.example.methodmesh.core.methodmesh.Entity
import com.example.methodmesh.core.methodmesh.ExecutionRequest
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.Observation
import com.example.methodmesh.core.methodmesh.ProvenanceContext
import com.example.methodmesh.core.methodmesh.State
import com.example.methodmesh.core.methodmesh.TransformationStatus
import com.example.methodmesh.modules.nfc.As100NfcReadMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for OutputFormatter.
 *
 * These tests exercise the compact transport payload logic: fields(),
 * detailedFields(), selectedFields() and the ReturnMode format variants.
 * No Android SDK is involved.
 */
class OutputFormatterTest {

    // ── ReturnMode.fromId ────────────────────────────────────────────────────

    @Test
    fun `ReturnMode fromId recognises all canonical ids`() {
        assertEquals(ReturnMode.Json, ReturnMode.fromId("json"))
        assertEquals(ReturnMode.Fields, ReturnMode.fromId("fields"))
        assertEquals(ReturnMode.Single, ReturnMode.fromId("single"))
        assertEquals(ReturnMode.Datapoints, ReturnMode.fromId("datapoints"))
    }

    @Test
    fun `ReturnMode fromId falls back to Json for unknown id`() {
        assertEquals(ReturnMode.Json, ReturnMode.fromId("unknown"))
        assertEquals(ReturnMode.Json, ReturnMode.fromId(""))
    }

    // ── OutputFormatter.fields – standard structure ──────────────────────────

    @Test
    fun `fields always includes execution id, method id and status`() {
        val result = makeResult(methodId = As100NfcReadMethod.ID, status = TransformationStatus.Succeeded)
        val fields = OutputFormatter.fields(result)
        assertTrue("execution id present", fields.containsKey("methodmesh_execution_id"))
        assertEquals(As100NfcReadMethod.ID, fields["methodmesh_method_id"])
        assertEquals("Succeeded", fields["methodmesh_status"])
    }

    @Test
    fun `single observation values are inlined without prefix`() {
        val obs = makeObservation("nfc.tag", mapOf("uid" to "AABBCCDD"))
        val result = makeResult(observations = listOf(obs))
        val fields = OutputFormatter.fields(result)
        assertEquals("AABBCCDD", fields["uid"])
        // Should not have numbered prefix for a single observation
        assertFalse("no observation_1_ prefix for single obs", fields.containsKey("observation_1_uid"))
    }

    @Test
    fun `two observations get numbered prefixes`() {
        val obs1 = makeObservation("nfc.tag", mapOf("uid" to "AA"))
        val obs2 = makeObservation("nfc.tag", mapOf("uid" to "BB"))
        val result = makeResult(observations = listOf(obs1, obs2))
        val fields = OutputFormatter.fields(result)
        assertEquals(2, fields["observation_count"])
        assertEquals("nfc.tag", fields["observation_1_type"])
        assertEquals("nfc.tag", fields["observation_2_type"])
        assertEquals("AA", fields["observation_1_uid"])
        assertEquals("BB", fields["observation_2_uid"])
    }

    @Test
    fun `single state values are inlined without prefix when no observations`() {
        val entity = ArchitectureRef(ArchitectureId("entity:001"), "Entity")
        val state = makeState("navigation.arrived", mapOf("arrived" to "true"), subject = entity)
        val result = makeResult(states = listOf(state))
        val fields = OutputFormatter.fields(result)
        assertEquals("true", fields["arrived"])
    }

    @Test
    fun `context keys subject_id and visit_id are forwarded`() {
        val result = makeResult(context = mapOf("subject_id" to "P001", "visit_id" to "V02"))
        val fields = OutputFormatter.fields(result)
        assertEquals("P001", fields["subject_id"])
        assertEquals("V02", fields["visit_id"])
    }

    @Test
    fun `diagnostics are omitted on success`() {
        val result = makeResult(
            status = TransformationStatus.Succeeded,
            diagnostics = mapOf("error" to "none")
        )
        val fields = OutputFormatter.fields(result)
        assertFalse(fields.containsKey("diagnostic_error"))
    }

    @Test
    fun `diagnostics are included on failure`() {
        val result = makeResult(
            status = TransformationStatus.Failed,
            diagnostics = mapOf("reason" to "timeout")
        )
        val fields = OutputFormatter.fields(result)
        assertEquals("timeout", fields["diagnostic_reason"])
    }

    @Test
    fun `core projection keeps redacted image uri without redaction metadata`() {
        val fields = mapOf(
            "redacted_image_uri" to "content://com.example.methodmesh/redacted.jpg",
            "redacted_image_name" to "redacted.jpg",
            "redaction_mask_json" to "[\"r1c1\"]",
            "redaction_grid_rows" to "10",
            "redaction_grid_columns" to "10",
            "redaction_style" to "black",
            "image_redaction_status" to "succeeded"
        )

        val projected = OutputFormatter.projectFields(fields, OutputFormatter.PayloadMode.CORE, TransformationStatus.Succeeded)

        assertEquals(mapOf("redacted_image_uri" to "content://com.example.methodmesh/redacted.jpg"), projected)
    }

    // ── OutputFormatter.format – ReturnMode formatting ───────────────────────

    @Test
    fun `Json format wraps values in braces and quotes keys`() {
        val result = makeResult(methodId = As100NfcReadMethod.ID)
        val json = OutputFormatter.format(result, ReturnMode.Json, includeProvenance = false)
        assertTrue("starts with {", json.startsWith("{"))
        assertTrue("ends with }", json.trimEnd().endsWith("}"))
        assertTrue("contains quoted key", json.contains("\"methodmesh_status\""))
    }

    @Test
    fun `Fields format produces key=value lines`() {
        val result = makeResult(methodId = As100NfcReadMethod.ID)
        val output = OutputFormatter.format(
            result,
            ReturnMode.Fields,
            includeProvenance = false,
            payloadMode = OutputFormatter.PayloadMode.CORE
        )
        assertTrue("each line has =", output.lines().filter { it.isNotBlank() }.all { "=" in it })
    }

    @Test
    fun `Single format returns the first value only`() {
        val obs = makeObservation("nfc.tag", mapOf("uid" to "AABB"))
        val result = makeResult(observations = listOf(obs))
        // fields() puts execution_id first; Single must return just that first value
        val output = OutputFormatter.format(result, ReturnMode.Single, includeProvenance = false)
        assertFalse("single value has no newline", output.contains('\n'))
    }

    @Test
    fun `Datapoints format produces numbered CSV lines`() {
        val result = makeResult(methodId = As100NfcReadMethod.ID)
        val output = OutputFormatter.format(result, ReturnMode.Datapoints, includeProvenance = false)
        val lines = output.lines().filter { it.isNotBlank() }
        assertTrue("first line starts with 1,", lines.first().startsWith("1,"))
        assertTrue("second line starts with 2,", lines[1].startsWith("2,"))
    }

    // ── OutputFormatter.selectedFields with GraphSelectors ───────────────────

    @Test
    fun `selectedFields with execution id selector returns execution id`() {
        val result = makeResult(methodId = As100NfcReadMethod.ID)
        val selectors = GraphSelectorParser.parse("execution.id as exec_id")
        val fields = OutputFormatter.selectedFields(result, selectors, includeProvenance = false)
        assertEquals(result.request.id.value, fields["exec_id"])
    }

    @Test
    fun `selectedFields with observation value selector resolves value`() {
        val obs = makeObservation("nfc.tag", mapOf("uid" to "AABBCCDD"))
        val result = makeResult(observations = listOf(obs))
        val selectors = GraphSelectorParser.parse("observation.nfc.uid as tag_uid")
        val fields = OutputFormatter.selectedFields(result, selectors, includeProvenance = false)
        assertEquals("AABBCCDD", fields["tag_uid"])
    }

    @Test
    fun `selectedFields always includes methodmesh_execution_id and status`() {
        val result = makeResult()
        val selectors = GraphSelectorParser.parse("execution.status as status")
        val fields = OutputFormatter.selectedFields(result, selectors, includeProvenance = false)
        assertTrue(fields.containsKey("methodmesh_execution_id"))
        assertTrue(fields.containsKey("methodmesh_status"))
    }

    @Test
    fun `selectedFields empty selectors falls back to full fields`() {
        val result = makeResult()
        val fields = OutputFormatter.selectedFields(result, emptyList(), includeProvenance = false)
        assertTrue(fields.containsKey("methodmesh_execution_id"))
        assertTrue(fields.containsKey("methodmesh_method_id"))
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun makeResult(
        methodId: String = "test.method",
        status: TransformationStatus = TransformationStatus.Succeeded,
        observations: List<Observation> = emptyList(),
        states: List<State> = emptyList(),
        entities: List<Entity> = emptyList(),
        context: Map<String, String> = emptyMap(),
        diagnostics: Map<String, String> = emptyMap()
    ): ExecutionResult {
        val methodRef = ArchitectureRef(ArchitectureId(methodId), "Method")
        val request = ExecutionRequest(
            action = methodId,
            method = methodRef,
            context = context
        )
        return ExecutionResult(
            request = request,
            status = status,
            observations = observations,
            states = states,
            entities = entities,
            diagnostics = diagnostics
        )
    }

    private fun makeObservation(
        phenomenon: String,
        values: Map<String, String>,
        subject: ArchitectureRef? = null
    ): Observation = Observation(
        phenomenon = phenomenon,
        values = values,
        subject = subject,
        provenance = ProvenanceContext(provider = "test")
    )

    private fun makeState(
        stateType: String,
        values: Map<String, String>,
        subject: ArchitectureRef = ArchitectureRef(ArchitectureId("entity:001"), "Entity")
    ): State = State(
        stateType = stateType,
        values = values,
        subject = subject
    )
}
