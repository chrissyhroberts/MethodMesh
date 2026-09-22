package com.example.methodmesh.modules.attestation

import com.example.methodmesh.core.ResearchRuntime
import com.example.methodmesh.core.crypto.Digests
import com.example.methodmesh.core.methodmesh.TransformationStatus

/**
 * Hash-only verification evidence carried by a signed attestation.
 *
 * Raw QR, NFC, biometric and study-token values are consumed transiently by the
 * capability screen. They do not enter the attestation execution request,
 * observation, graph record, or caller-facing return.
 */
data class AttestationEvidence(
    val format: String,
    val hash: String
) {
    init {
        require(format.isNotBlank()) { "verification_evidence_format is required" }
        require(SHA256_HEX.matches(hash)) {
            "verification_evidence_hash must be a 64-character hexadecimal SHA-256 digest"
        }
    }

    companion object {
        private val SHA256_HEX = Regex("^[0-9a-f]{64}$")
    }
}

data class ResolvedAttestationVerification(
    val evidence: AttestationEvidence,
    val authenticatedOperatorId: String? = null
)

object AttestationEvidenceFactory {
    // Public nfc_credential_verification contract identifiers. Keep these as
    // contract strings rather than importing NFC implementation classes: the
    // attestation module composes with the capability boundary, not NFC internals.
    private const val NFC_CREDENTIAL_VERIFICATION_METHOD_ID = "nfc_credential_verification"
    private const val CREDENTIAL_VERIFIED_FIELD = "credential_verified"
    private const val CREDENTIAL_ID_FIELD = "credential_id"
    private const val CREDENTIAL_SUBJECT_ID_FIELD = "credential_subject_id"
    private const val CREDENTIAL_ENVELOPE_HASH_FIELD = "credential_envelope_hash"
    private const val ISSUER_KEY_ID_FIELD = "issuer_key_id"
    private const val PIN_VERIFIED_FIELD = "pin_verified"
    private const val ISSUER_SIGNATURE_VALID_FIELD = "issuer_signature_valid"
    private const val ISSUER_TRUST_STATUS_FIELD = "issuer_trust_status"
    const val BIOMETRIC_FORMAT = "android_biometric_result_sha256_v1"
    const val DEVICE_CREDENTIAL_FORMAT = "android_device_credential_result_sha256_v1"
    const val STUDY_TOKEN_FORMAT = "study_token_utf8_sha256_v1"
    const val NFC_CREDENTIAL_EXECUTION_FORMAT = "methodmesh_nfc_credential_execution_sha256_v1"

    fun biometric(result: String): AttestationEvidence = resultEvidence(
        result = result,
        format = BIOMETRIC_FORMAT,
        fallback = "biometric"
    )

    fun deviceCredential(result: String): AttestationEvidence = resultEvidence(
        result = result,
        format = DEVICE_CREDENTIAL_FORMAT,
        fallback = "device_credential"
    )

    fun studyToken(token: String): AttestationEvidence {
        require(token.isNotBlank()) { "Password verification requires a non-blank study token" }
        return AttestationEvidence(
            format = STUDY_TOKEN_FORMAT,
            hash = Digests.sha256Hex(token)
        )
    }

    /**
     * Resolve a prior successful MethodMesh NFC credential + PIN verification.
     *
     * The caller supplies only the opaque MethodMesh execution ID. MethodMesh
     * retrieves its own prior execution record and derives the evidence hash
     * internally. Caller-provided hidden fields are never accepted as proof of
     * credential/PIN success.
     *
     * To prevent replay between ODK forms, the prior verification and the
     * attestation call must carry the same caller, form_id and visit_id. The
     * reference ODK workflow uses visit_id as the unique ODK instance ID.
     */
    fun nfcCredentialExecution(context: Map<String, String>): ResolvedAttestationVerification {
        val executionId = context["verification_execution_id"]?.trim().orEmpty()
        require(executionId.isNotBlank()) {
            "NfcCredential verification requires verification_execution_id from the earlier nfc_credential_verification call"
        }

        val prior = ResearchRuntime.session.executionResults
            .firstOrNull { it.request.id.value == executionId }
            ?: throw IllegalArgumentException(
                "The referenced NFC credential verification execution is not available in the current MethodMesh session; verify the NFC credential again"
            )

        require(prior.request.method.id.value == NFC_CREDENTIAL_VERIFICATION_METHOD_ID) {
            "verification_execution_id does not reference nfc_credential_verification"
        }
        require(prior.status == TransformationStatus.Succeeded) {
            "The referenced NFC credential verification did not succeed"
        }

        val sourceSoftware = prior.softwareProvenance
            .firstOrNull { it.id == NFC_CREDENTIAL_VERIFICATION_METHOD_ID }
            ?: throw IllegalArgumentException(
                "The referenced NFC credential execution is missing frozen capability provenance"
            )
        require(sourceSoftware.version.isNotBlank() && sourceSoftware.module.version.isNotBlank()) {
            "The referenced NFC credential execution has incomplete capability/module version provenance"
        }

        val verification = prior.observations
            .firstOrNull { it.phenomenon == "nfc.credential.verified" }
            ?: throw IllegalArgumentException(
                "The referenced NFC credential execution contains no successful credential verification observation"
            )
        val values = verification.values

        require(values[CREDENTIAL_VERIFIED_FIELD].equals("true", ignoreCase = true)) {
            "The referenced NFC credential was not verified"
        }
        require(values[PIN_VERIFIED_FIELD].equals("true", ignoreCase = true)) {
            "The referenced NFC credential PIN was not verified"
        }
        require(values[ISSUER_SIGNATURE_VALID_FIELD].equals("true", ignoreCase = true)) {
            "The referenced NFC credential issuer signature was not valid"
        }

        val sourceCaller = prior.request.context["caller"]?.trim().orEmpty()
        val currentCaller = context["caller"]?.trim().orEmpty()
        require(sourceCaller.isNotBlank() && currentCaller.isNotBlank() && sourceCaller == currentCaller) {
            "The referenced NFC credential verification belongs to a different caller context"
        }

        val sourceFormId = prior.request.context["form_id"]?.trim().orEmpty()
        val currentFormId = context["form_id"]?.trim().orEmpty()
        require(sourceFormId.isNotBlank() && currentFormId.isNotBlank() && sourceFormId == currentFormId) {
            "NfcCredential verification reuse requires the same non-blank form_id on both MethodMesh calls"
        }

        val sourceVisitId = prior.request.context["visit_id"]?.trim().orEmpty()
        val currentVisitId = context["visit_id"]?.trim().orEmpty()
        require(sourceVisitId.isNotBlank() && currentVisitId.isNotBlank() && sourceVisitId == currentVisitId) {
            "NfcCredential verification reuse requires the same non-blank visit_id on both MethodMesh calls"
        }

        val sourceSubject = prior.request.context["context_entity_id"]
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: prior.request.context["subject_id"]?.trim().orEmpty()
        val currentSubject = context["context_entity_id"]
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: context["subject_id"]?.trim().orEmpty()
        if (sourceSubject.isNotBlank() || currentSubject.isNotBlank()) {
            require(sourceSubject.isNotBlank() && currentSubject.isNotBlank() && sourceSubject == currentSubject) {
                "The referenced NFC credential verification belongs to a different subject context"
            }
        }

        val credentialId = values[CREDENTIAL_ID_FIELD]?.trim().orEmpty()
        val credentialSubjectId = values[CREDENTIAL_SUBJECT_ID_FIELD]?.trim().orEmpty()
        val credentialEnvelopeHash = values[CREDENTIAL_ENVELOPE_HASH_FIELD]?.trim()?.lowercase().orEmpty()
        val issuerKeyId = values[ISSUER_KEY_ID_FIELD]?.trim().orEmpty()
        val issuerTrustStatus = values[ISSUER_TRUST_STATUS_FIELD]?.trim().orEmpty()
        require(credentialId.isNotBlank() && credentialSubjectId.isNotBlank()) {
            "The referenced NFC credential execution is missing credential identity fields"
        }
        require(SHA256_HEX.matches(credentialEnvelopeHash)) {
            "The referenced NFC credential execution contains an invalid credential envelope hash"
        }
        require(issuerKeyId.isNotBlank()) {
            "The referenced NFC credential execution is missing issuer identity"
        }
        require(issuerTrustStatus.isNotBlank()) {
            "The referenced NFC credential execution is missing issuer trust status"
        }

        val canonical = listOf(
            "verification_evidence_format=$NFC_CREDENTIAL_EXECUTION_FORMAT",
            "source_execution_id=$executionId",
            "source_method_id=${NFC_CREDENTIAL_VERIFICATION_METHOD_ID}",
            "source_method_version=${sourceSoftware.version}",
            "source_module_id=${sourceSoftware.module.id}",
            "source_module_version=${sourceSoftware.module.version}",
            "credential_id=$credentialId",
            "credential_subject_id=$credentialSubjectId",
            "credential_envelope_hash=$credentialEnvelopeHash",
            "issuer_key_id=$issuerKeyId",
            "issuer_trust_status=$issuerTrustStatus",
            "credential_verified=true",
            "pin_verified=true",
            "issuer_signature_valid=true",
            "caller=$currentCaller",
            "form_id=$currentFormId",
            "visit_id=$currentVisitId",
            "subject_id=$currentSubject"
        ).joinToString("\n")

        return ResolvedAttestationVerification(
            evidence = AttestationEvidence(
                format = NFC_CREDENTIAL_EXECUTION_FORMAT,
                hash = Digests.sha256Hex(canonical)
            ),
            authenticatedOperatorId = credentialSubjectId
        )
    }

    private fun resultEvidence(result: String, format: String, fallback: String): AttestationEvidence =
        AttestationEvidence(
            format = format,
            hash = Digests.sha256Hex(result.ifBlank { fallback })
        )

    private val SHA256_HEX = Regex("^[0-9a-f]{64}$")
}
