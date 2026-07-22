package com.example.researchos.modules.attestation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AttestationContractTest {
    @Test
    fun `canonical timestamp policies parse`() {
        assertEquals(
            TrustedTimestampPolicy.Disabled,
            TrustedTimestampPolicy.fromContext(emptyMap())
        )
        assertEquals(
            TrustedTimestampPolicy.Preferred,
            TrustedTimestampPolicy.fromContext(mapOf("trusted_timestamp" to "preferred"))
        )
        assertEquals(
            TrustedTimestampPolicy.Required,
            TrustedTimestampPolicy.fromContext(mapOf("trusted_timestamp" to "required"))
        )
    }

    @Test
    fun `removed timestamp policy aliases are rejected`() {
        listOf("on", "true", "optional", "require", "off").forEach { removedValue ->
            val failed = runCatching {
                TrustedTimestampPolicy.fromContext(mapOf("trusted_timestamp" to removedValue))
            }.isFailure
            assertTrue("Expected $removedValue to be rejected", failed)
        }
    }

    @Test
    fun `removed timestamp keys have no effect`() {
        assertEquals(
            TrustedTimestampPolicy.Disabled,
            TrustedTimestampPolicy.fromContext(mapOf("server_side_verification" to "on"))
        )
        assertEquals(
            TrustedTimestampPolicy.Disabled,
            TrustedTimestampPolicy.fromContext(mapOf("trusted_timestamp_policy" to "required"))
        )
    }
}
