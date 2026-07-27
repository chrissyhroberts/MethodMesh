package com.example.researchos.modules.attestation

import com.example.researchos.core.crypto.Digests

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

object AttestationEvidenceFactory {
    const val BIOMETRIC_FORMAT = "android_biometric_result_sha256_v1"
    const val DEVICE_CREDENTIAL_FORMAT = "android_device_credential_result_sha256_v1"
    const val STUDY_TOKEN_FORMAT = "study_token_utf8_sha256_v1"

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

    private fun resultEvidence(result: String, format: String, fallback: String): AttestationEvidence =
        AttestationEvidence(
            format = format,
            hash = Digests.sha256Hex(result.ifBlank { fallback })
        )

}
