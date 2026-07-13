package com.example.researchos.transport.ril

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for RilRequestParser.
 *
 * These tests promote the existing RilConformanceSmokeTests cases into the
 * real test suite and add a focused set of parser behaviour assertions.
 */
class RilParserTest {

    // ── Promote existing conformance smoke-test cases ───────────────────────

    @Test
    fun `all RilConformanceSmokeTests cases pass`() {
        val results = RilConformanceSmokeTests.runAll()
        results.forEach { result ->
            assertTrue("${result.name}: ${result.message}", result.passed)
        }
    }

    // ── Semicolon-separated section syntax ──────────────────────────────────

    @Test
    fun `semicolon-separated sections parse single action`() {
        val ril = "WHAT; scan nfc; WHERE; participant/P001; RESULT; return observation.nfc.uid as tag_uid; format json"
        val parsed = RilRequestParser.parse(ril)
        assertEquals(listOf("nfc.read"), parsed.actionIds)
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
        val ril = "WHAT; scan nfc; verify identity fingerprint; WHERE; participant/P001; RESULT; return observation.nfc.uid as tag_uid; return observation.identity.verified as verified; format json"
        val parsed = RilRequestParser.parse(ril)
        assertEquals(listOf("nfc.read", "identity.verify"), parsed.actionIds)
        assertEquals(2, parsed.returnSelectors.size)
        assertEquals("tag_uid", parsed.returnSelectors[0].alias)
        assertEquals("verified", parsed.returnSelectors[1].alias)
    }

    // ── Compact one-line syntax ──────────────────────────────────────────────

    @Test
    fun `compact one-line syntax resolves action and subject`() {
        val ril = "WHAT scan nfc WHERE participant/P001 RESULT return observation.nfc.uid as tag_uid format json"
        val parsed = RilRequestParser.parse(ril)
        assertEquals(listOf("nfc.read"), parsed.actionIds)
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
        val result = RilRequestParser.looksLikeRil("method_id=nfc.read")
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
        val ril = "execute nfc.read for participant/P001"
        val parsed = RilRequestParser.parse(ril, source = "ril_text")
        assertEquals("nfc.read", parsed.actionIds.firstOrNull())
        assertEquals("participant/P001", parsed.context["subject"])
    }
}

