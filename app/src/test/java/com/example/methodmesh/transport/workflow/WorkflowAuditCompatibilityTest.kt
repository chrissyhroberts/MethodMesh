package com.example.methodmesh.transport.workflow

import com.example.methodmesh.core.methodmesh.*
import com.example.methodmesh.core.timeassurance.*
import com.example.methodmesh.modules.attestation.*
import com.example.methodmesh.modules.nfc.NfcCredentialEvidence
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.android.ExternalReturnFieldPolicy
import com.example.methodmesh.transport.android.ReturnIntentProjector
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Frozen synthetic evidence, not a cryptographic or clinical end-to-end certification. */
class WorkflowAuditCompatibilityTest {
    private val context = mapOf(
        "caller" to "odk", "study_id" to "fixture-study", "form_id" to "fixture-form",
        "form_version" to "20261020", "form_instance_id" to "uuid:fixture-instance",
        "submission_id" to "uuid:fixture-submission", "subject_id" to "participant/P001",
        "operator_id" to "staff/001", "data_originator_id" to "device/001",
        "data_originator_type" to "device"
    )

    private fun result(methodId: String, values: Map<String, String>, elapsed: Long = 200) = ExecutionResult(
        request = ExecutionRequest(
            id = ArchitectureId("fixture-$methodId"), action = methodId,
            method = ArchitectureRef(ArchitectureId(methodId), "Method"), context = context,
            startTiming = ExecutionTimingBoundary("2026-10-20T12:00:00Z", 100, "fixture-boot")
        ),
        status = TransformationStatus.Succeeded,
        observations = listOf(Observation(
            phenomenon = "fixture.evidence", values = values, provenance = ProvenanceContext("test")
        )),
        timeAssurance = ClockEvidenceSnapshot(
            observedWallTimeIso = "2026-10-20T12:00:01Z", elapsedRealtimeMillis = elapsed,
            bootSessionId = "fixture-boot", evidenceState = ClockEvidenceState.WALL_CLOCK_ONLY,
            monotonicContinuity = MonotonicContinuity.NO_ANCHOR, reason = "Offline fixture; no trusted anchor."
        ),
        applicationProvenance = ExecutionApplicationIdentity("com.example.methodmesh", "fixture-build", 17)
    )

    private fun credentialFields(): Map<String, String> = linkedMapOf(
        "credential_id" to "fixture-credential", "credential_subject_id" to "staff/001",
        "credential_envelope_hash" to "a".repeat(64), "issuer_key_id" to "b".repeat(16),
        "issuer_public_key_fingerprint_sha256" to "b".repeat(64), "issuer_signature_valid" to "true",
        "pin_verified" to "true", "issuer_trust_status" to "not_checked", "tag_uid_hex" to "04A1",
        "credential_verified_time_iso" to "2026-10-20T12:00:01Z", "valid_until_iso" to "2030-01-01T00:00:00Z"
    ).apply {
        putAll(NfcCredentialEvidence.credentialVerificationFields(
            tagUidHex = getValue("tag_uid_hex"), credentialId = getValue("credential_id"),
            credentialSubjectId = getValue("credential_subject_id"),
            credentialEnvelopeHash = getValue("credential_envelope_hash"),
            issuerPublicKeyFingerprintSha256 = getValue("issuer_public_key_fingerprint_sha256"),
            issuerSignatureValid = true, pinVerified = true
        ))
    }

    @Test fun `single credential result preserves evidence and caller linkage through JSON and namespaces`() {
        val expected = credentialFields()
        val committed = result("nfc_credential_verification", expected)
        assertSame(committed, WorkflowResultProjection.combine(listOf(committed)))
        val json = JSONObject(WorkflowResultProjection.fullJson(listOf(committed)))
        expected.forEach { (key, value) -> assertEquals(key, value, json.getString(key)) }
        assertEquals(committed.request.id.value, json.getString("methodmesh_execution_id"))
        val provenance = json.getJSONObject("execution_provenance")
        val record = provenance.getJSONObject("record_context")
        listOf("study_id", "form_id", "form_version", "form_instance_id", "submission_id").forEach {
            assertEquals(it, context[it], record.getString(it))
        }
        assertEquals("request_context", provenance.getJSONObject("actors").getJSONObject("operator").getString("assertion_basis"))
        assertEquals("wall_clock_only", json.getJSONObject("time_assurance").getString("evidence_state"))
        assertEquals(100L, json.getJSONObject("execution_timing").getLong("monotonic_duration_ms"))
        assertEquals("fixture-build", json.getJSONObject("application").getString("version_name"))

        listOf("", "visit_1").forEach { namespace ->
            val prefix = if (namespace.isBlank()) "" else "${namespace}_"
            val returned = linkedMapOf<String, String?>()
            ExternalReturnFieldPolicy.mergeCallerDeclaredFields(
                incomingExtraKeys = expected.keys.map { "$prefix$it" }.toSet() + "input_pin_length",
                canonicalFields = OutputFormatter.fields(committed), selectedFields = emptyMap(),
                target = returned, returnNamespace = namespace
            )
            val extras = ReturnIntentProjector.projectFlatReturn(returned, namespace).extras
            expected.forEach { (key, value) -> assertEquals(key, value, extras["$prefix$key"]) }
            assertFalse(extras.keys.any { it.contains("input_pin") })
        }
    }

    @Test fun `attestation commitment signature and offline timestamp evidence are not rewritten at closeout`() {
        val record = AttestationRecord(
            attestationId = "fixture-attestation", studyId = "fixture-study", operatorId = "staff/001",
            subjectRef = "participant/P001", eventType = "form_submission", eventPayloadHash = "c".repeat(64),
            eventPayloadMode = "supplied_hash", commitmentRecipe = DEFAULT_ATTESTATION_COMMITMENT_RECIPE,
            commitmentRecipeSha256 = AttestationCommitmentRecipe.sha256(DEFAULT_ATTESTATION_COMMITMENT_RECIPE),
            verificationMethod = AttestationVerificationMethod.NfcCredential,
            verificationEvidenceFormat = NfcCredentialEvidence.CREDENTIAL_VERIFICATION_FORMAT,
            verificationEvidenceHash = credentialFields().getValue("verification_evidence_hash"),
            deviceEventTimeIso = "2026-10-20T12:00:01Z", deviceEventTimeEpochMs = 0,
            deviceMonotonicCounter = 200, previousAttestationHash = "GENESIS", publicKeyId = "fixture-key",
            publicKeyAlgorithm = "EC", publicKeyFormat = "X.509", publicKeyBase64 = "fixture-public-key",
            signature = "fixture-signature-not-a-real-signature", trustedTimestampPolicy = TrustedTimestampPolicy.Preferred
        )
        val canonical = record.canonicalPayload
        val hash = record.attestationHash
        val committed = result("attestation.create", record.asOutputMap())
        val json = JSONObject(WorkflowResultProjection.fullJson(listOf(committed)))
        record.asOutputMap().forEach { (key, value) -> assertEquals(key, value, json.getString(key)) }
        assertEquals("unavailable", json.getString("trusted_timestamp_status"))
        assertEquals("preferred", json.getString("trusted_timestamp_policy"))
        assertFalse(json.has("trusted_timestamp_time_iso"))
        assertEquals(canonical, record.canonicalPayload)
        assertEquals(hash, json.getString("attestation_hash"))
    }

    @Test fun `multi step sidecar preserves each credential and attestation boundary without promoting failed work`() {
        val first = result("nfc_credential_verification", credentialFields())
        val second = result("attestation.create", mapOf("attestation_hash" to "d".repeat(64)), 700)
            .copy(status = TransformationStatus.Failed, diagnostics = mapOf("error" to "Fixture failure"))
        val json = JSONObject(WorkflowResultProjection.fullJson(listOf(first, second)))
        assertEquals("Failed", json.getString("methodmesh_status"))
        val steps = json.getJSONArray("workflow_steps")
        listOf(first, second).forEachIndexed { index, committed ->
            val step = steps.getJSONObject(index)
            assertEquals(committed.request.id.value, step.getString("methodmesh_execution_id"))
            assertEquals(committed.status.name, step.getString("methodmesh_status"))
            assertEquals(committed.timeAssurance!!.elapsedRealtimeMillis,
                step.getJSONObject("execution_timing").getJSONObject("completed").getLong("elapsed_realtime_ms"))
        }
        assertEquals(credentialFields().getValue("verification_evidence_hash"), steps.getJSONObject(0).getString("verification_evidence_hash"))
        assertEquals("request_context", steps.getJSONObject(1).getJSONObject("execution_provenance")
            .getJSONObject("actors").getJSONObject("operator").getString("assertion_basis"))
    }

    @Test fun `cancelled closeout cannot return an earlier credential as a successful verification`() {
        val request = ExternalWorkflowRequest(
            actions = listOf(ExternalActionRequest("nfc_credential_verification", "nfc_credential_verification")),
            invocationContext = InvocationContext(caller = "odk"), returns = emptyList(),
            returnMode = com.example.methodmesh.transport.ReturnMode.Json
        )
        val fields = WorkflowResultProjection.cancellationFields(request, 0, 0, "Fixture cancellation")
        val json = JSONObject(fields.getValue("methodmesh_full_json")!!)
        assertEquals("Cancelled", fields["methodmesh_status"])
        assertEquals("Cancelled", json.getString("methodmesh_status"))
        assertEquals("false", fields["methodmesh_closeout_has_payload"])
        assertFalse(fields.containsKey("credential_id"))
        assertFalse(json.has("verification_evidence_hash"))
    }
}
