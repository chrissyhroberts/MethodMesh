package com.example.methodmesh.modules.attestation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AttestationScreenDefaultsTest {
    @Test
    fun `manual debug launch receives a valid deterministic placeholder hash`() {
        val hash = initialAttestationPayloadHash(null, startsImmediately = false)
        assertTrue(hash.matches(Regex("[0-9a-f]{64}")))
        assertEquals(MANUAL_DEBUG_EVENT_PAYLOAD_HASH, hash)
    }

    @Test
    fun `external launch never receives a placeholder hash`() {
        assertEquals("", initialAttestationPayloadHash(null, startsImmediately = true))
    }

    @Test
    fun `caller supplied hash always remains authoritative`() {
        val supplied = "a".repeat(64)
        assertEquals(supplied, initialAttestationPayloadHash(supplied, startsImmediately = false))
        assertEquals(supplied, initialAttestationPayloadHash(supplied, startsImmediately = true))
    }
}
