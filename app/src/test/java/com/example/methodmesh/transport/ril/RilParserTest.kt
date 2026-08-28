package com.example.methodmesh.transport.ril

import com.example.methodmesh.modules.adminfingerprint.As100VerifyFingerprintMethod
import com.example.methodmesh.modules.MethodMeshModuleRegistry
import com.example.methodmesh.modules.nfc.As100NfcReadMethod
import com.example.methodmesh.modules.nfc.NfcModule
import com.example.methodmesh.modules.adminfingerprint.AdminFingerprintModule
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * JVM unit tests for RilRequestParser.
 *
 * These tests promote the existing RilConformanceSmokeTests cases into the
 * real test suite and add a focused set of parser behaviour assertions.
 */
class RilParserTest {

    @Before
    fun installTestModules() {
        MethodMeshModuleRegistry.install(listOf(NfcModule, AdminFingerprintModule))
    }

    // ── Semicolon-separated section syntax ──────────────────────────────────

    @Test
    fun `semicolon-separated sections parse single action`() {
        val ril = "WHAT; scan nfc; WHERE; participant/P001; RESULT; return observation.nfc.uid as tag_uid; format json"
        val parsed = RilRequestParser.parse(ril)
        assertEquals(listOf(As100NfcReadMethod.ID), parsed.actionIds)
        assertEquals("participant/P001", parsed.context["subject"])
        assertEquals("participant", parsed.context["entity_type"])
        assertEquals("P001", parsed.context["entity_id"])
        assertEquals(1, parsed.returnSelectors.size)
        assertEquals("observation.nfc.uid", parsed.returnSelectors[0].path)
        assertEquals("tag_uid", parsed.returnSelectors[0].alias)
        assertEquals("json", parsed.returnMode?.id)
    }

    @Test
    fun `semicolon-separated sections parse multiple actions`() {
        val ril = "WHAT; scan nfc; authorize local access; WHERE; participant/P001; RESULT; return observation.nfc.uid as tag_uid; return observation.authorization.confirmed as confirmed; format json"
        val parsed = RilRequestParser.parse(ril)
        assertEquals(listOf(As100NfcReadMethod.ID, As100VerifyFingerprintMethod.ID), parsed.actionIds)
        assertEquals(2, parsed.returnSelectors.size)
        assertEquals("tag_uid", parsed.returnSelectors[0].alias)
        assertEquals("confirmed", parsed.returnSelectors[1].alias)
    }

    // ── Compact one-line syntax ──────────────────────────────────────────────

    @Test
    fun `compact one-line syntax resolves action and subject`() {
        val ril = "WHAT scan nfc WHERE participant/P001 RESULT return observation.nfc.uid as tag_uid format json"
        val parsed = RilRequestParser.parse(ril)
        assertEquals(listOf(As100NfcReadMethod.ID), parsed.actionIds)
        assertEquals("participant/P001", parsed.context["subject"])
        assertEquals("json", parsed.returnMode?.id)
    }

    // ── Return mode ──────────────────────────────────────────────────────────

    @Test
    fun `format fields is recognised`() {
        val ril = "WHAT; scan nfc; WHERE; participant/P001; RESULT; format fields"
        val parsed = RilRequestParser.parse(ril)
        assertEquals("fields", parsed.returnMode?.id)
    }

    @Test
    fun `format single is recognised`() {
        val ril = "WHAT; scan nfc; WHERE; participant/P001; RESULT; return observation.nfc.uid as tag_uid; format single"
        val parsed = RilRequestParser.parse(ril)
        assertEquals("single", parsed.returnMode?.id)
    }

    // ── looksLikeRil ────────────────────────────────────────────────────────

    @Test
    fun `looksLikeRil true for WHAT section`() {
        assertTrue(RilRequestParser.looksLikeRil("WHAT\nscan nfc\nWHERE\nparticipant/P001"))
    }

    @Test
    fun `looksLikeRil true for semicolon-separated`() {
        assertTrue(RilRequestParser.looksLikeRil("WHAT; scan nfc; WHERE; participant/P001"))
    }

    @Test
    fun `looksLikeRil false for plain key-value pair`() {
        val result = RilRequestParser.looksLikeRil("method_id=${As100NfcReadMethod.ID}")
        // Key-value pairs do not look like RIL.
        assertEquals(false, result)
    }

    // ── Source tagging ───────────────────────────────────────────────────────

    @Test
    fun `source label is preserved`() {
        val ril = "WHAT; scan nfc; WHERE; participant/P001; RESULT; format json"
        val parsed = RilRequestParser.parse(ril, source = "unit_test")
        assertEquals("unit_test", parsed.source)
    }

    // ── Inline subject using execute … for … syntax ──────────────────────────

    @Test
    fun `execute for shorthand resolves action and subject`() {
        val ril = "execute ${As100NfcReadMethod.ID} for participant/P001"
        val parsed = RilRequestParser.parse(ril, source = "ril_text")
        assertEquals(As100NfcReadMethod.ID, parsed.actionIds.firstOrNull())
        assertEquals("participant/P001", parsed.context["subject"])
    }

    @Test
    fun `removed method aliases do not resolve`() {
        assertNull(MethodMeshModuleRegistry.canonicalAction("nfc.read"))
        assertNull(MethodMeshModuleRegistry.canonicalAction("identity.verify"))
        assertNull(MethodMeshModuleRegistry.canonicalAction("org.lshtm.choice.pairwise"))
    }

    @Test
    fun `removed transport selector keys are ignored`() {
        val parsed = RilTransportAdapter.parse(
            values = mapOf(
                "method" to As100NfcReadMethod.ID,
                "actions" to As100NfcReadMethod.ID,
                "subject" to "participant/P001"
            ),
            source = "unit_test"
        )
        assertTrue(parsed.actionIds.isEmpty())
        assertTrue(parsed.context.isEmpty())
    }

    @Test
    fun `ODK namespaced inputs preserve multiline item lists without parsing punctuation`() {
        val items = "Clinic A\nClinic B\nClinic C"
        val parsed = RilTransportAdapter.parse(
            values = mapOf(
                "method_id" to "dce.ranking",
                "input_rounds" to "3",
                "input_items" to items,
                "input_seed" to "participant_001"
            ),
            source = "android_extras"
        )

        assertEquals("dce.ranking", parsed.methodId)
        assertEquals(items, parsed.settings["items"])
        assertEquals("3", parsed.settings["rounds"])
    }

    @Test
    fun `ODK namespaced inputs preserve conjoint class syntax as data`() {
        val classes = "BRAND: Panasonic, Sony\nFEATURE: Basic, Premium\nPRICE: 100, 200"
        val parsed = RilTransportAdapter.parse(
            values = mapOf(
                "method_id" to "dce.conjoint",
                "input_classes" to classes
            ),
            source = "android_extras"
        )

        assertEquals(classes, parsed.settings["classes"])
    }

    @Test
    fun `unprefixed return placeholders never become capability settings`() {
        val parsed = RilTransportAdapter.parse(
            values = mapOf(
                "method_id" to "calibrated_scale",
                "input_use_range" to "true",
                "use_range" to "",
                "value" to "0"
            ),
            source = "android_extras"
        )

        assertEquals("true", parsed.settings["use_range"])
        assertNull(parsed.settings["value"])
    }

    @Test
    fun `legacy unprefixed capability inputs are rejected`() {
        val parsed = RilTransportAdapter.parse(
            values = mapOf(
                "method_id" to "calibrated_scale",
                "vas_length_mm" to "50",
                "vertical_mode" to "true"
            ),
            source = "android_extras"
        )

        assertTrue(parsed.settings.isEmpty())
    }

    @Test
    fun `URL safe Base64 inputs decode structured values`() {
        val classes = "BRAND: Panasonic, Sony\nFEATURE: Basic, Premium"
        val encoded = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(classes.toByteArray(StandardCharsets.UTF_8))
        val parsed = RilTransportAdapter.parse(
            values = mapOf(
                "method_id" to "dce.conjoint",
                "input64_classes" to encoded
            ),
            source = "android_action"
        )

        assertEquals(classes, parsed.settings["classes"])
    }

    @Test
    fun `encoded input deterministically overrides duplicate plain input`() {
        val encoded = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString("encoded value".toByteArray(StandardCharsets.UTF_8))
        val parsed = RilTransportAdapter.parse(
            values = mapOf(
                "method_id" to "dce.ranking",
                "input_items" to "plain value",
                "input64_items" to encoded
            ),
            source = "android_action"
        )

        assertEquals("encoded value", parsed.settings["items"])
    }

    @Test
    fun `malformed Base64 input is ignored`() {
        val parsed = RilTransportAdapter.parse(
            values = mapOf(
                "method_id" to "dce.conjoint",
                "input64_classes" to "not valid base64!"
            ),
            source = "android_action"
        )

        assertNull(parsed.settings["classes"])
    }
}
