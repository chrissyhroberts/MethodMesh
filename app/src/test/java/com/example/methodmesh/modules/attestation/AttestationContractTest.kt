package com.example.methodmesh.modules.attestation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttestationContractTest {
    @Test
    fun `schema four output and canonical payload contain no raw verification credential`() {
        val secret = "lskdfjslkdfj"
        val evidence = AttestationEvidence(
            format = "qr_payload_utf8_sha256_v1",
            hash = AttestationCrypto.sha256Hex(secret)
        )
        val record = AttestationRecord(
            attestationId = "att_test",
            studyId = "study",
            operatorId = "geoff",
            subjectRef = "participant/P001",
            eventType = "form_submission",
            eventPayloadHash = "a".repeat(64),
            eventPayloadMode = "supplied_hash",
            verificationMethod = AttestationVerificationMethod.Qr,
            verificationEvidenceFormat = evidence.format,
            verificationEvidenceHash = evidence.hash,
            deviceEventTimeIso = "2026-07-27T12:00:00Z",
            deviceEventTimeEpochMs = 0,
            deviceMonotonicCounter = 1,
            previousAttestationHash = "GENESIS",
            publicKeyId = "key",
            publicKeyAlgorithm = "EC",
            publicKeyFormat = "X.509",
            publicKeyBase64 = "public",
            signature = "signature"
        )

        assertEquals("4", record.asOutputMap()["attestation_schema_version"])
        assertEquals(evidence.format, record.asOutputMap()["verification_evidence_format"])
        assertEquals(evidence.hash, record.asOutputMap()["verification_evidence_hash"])
        assertFalse(record.asOutputMap().containsKey("verification_evidence_payload"))
        assertFalse(record.canonicalPayload.contains(secret))
        assertTrue(record.canonicalPayload.contains("event_payload_hash=${"a".repeat(64)}"))
        assertTrue(record.canonicalPayload.contains("verification_evidence_hash=${evidence.hash}"))
    }

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
