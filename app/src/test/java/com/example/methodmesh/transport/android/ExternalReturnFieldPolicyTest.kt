package com.example.methodmesh.transport.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalReturnFieldPolicyTest {

    @Test
    fun callerDeclaredAuditFieldsAreReturnedWithoutWideningInputControls() {
        val incoming = linkedSetOf(
            "credential_id",
            "credential_subject_id",
            "credential_envelope_hash",
            "credential_verified_time_iso",
            "issuer_key_id",
            "verification_evidence_hash",
            "methodmesh_full_json",
            "input_pin_length",
            "input_payload_mode",
            "return_mode",
            "method_id"
        )

        val canonical = linkedMapOf<String, Any?>(
            "credential_id" to "cred_123",
            "credential_subject_id" to "PD09875",
            "credential_envelope_hash" to "abc123",
            "credential_verified_time_iso" to "2026-09-22T12:34:56Z",
            "issuer_key_id" to "issuer_001",
            "verification_evidence_hash" to "def456"
        )

        val target = linkedMapOf<String, String?>()
        ExternalReturnFieldPolicy.mergeCallerDeclaredFields(
            incomingExtraKeys = incoming,
            canonicalFields = canonical,
            selectedFields = emptyMap(),
            target = target
        )

        assertEquals("cred_123", target["credential_id"])
        assertEquals("PD09875", target["credential_subject_id"])
        assertEquals("abc123", target["credential_envelope_hash"])
        assertEquals("2026-09-22T12:34:56Z", target["credential_verified_time_iso"])
        assertEquals("issuer_001", target["issuer_key_id"])
        assertEquals("def456", target["verification_evidence_hash"])

        assertFalse(target.containsKey("input_pin_length"))
        assertFalse(target.containsKey("input_payload_mode"))
        assertFalse(target.containsKey("return_mode"))
        assertFalse(target.containsKey("method_id"))

        // methodmesh_full_json is constructed explicitly by ExternalWorkflowActivity.
        assertFalse(target.containsKey("methodmesh_full_json"))
    }

    @Test
    fun selectedAliasWinsCanonicalValueWhenCallerDeclaredIt() {
        val target = linkedMapOf<String, String?>()
        ExternalReturnFieldPolicy.mergeCallerDeclaredFields(
            incomingExtraKeys = setOf("credential_id"),
            canonicalFields = mapOf("credential_id" to "canonical"),
            selectedFields = mapOf("credential_id" to "selected"),
            target = target
        )

        assertEquals("selected", target["credential_id"])
    }

    @Test
    fun unknownCallerFieldCannotCreateAnOutput() {
        val target = linkedMapOf<String, String?>()
        ExternalReturnFieldPolicy.mergeCallerDeclaredFields(
            incomingExtraKeys = setOf("made_up_field"),
            canonicalFields = mapOf("credential_id" to "cred_123"),
            selectedFields = emptyMap(),
            target = target
        )

        assertTrue(target.isEmpty())
    }
}
