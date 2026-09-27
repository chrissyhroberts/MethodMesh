package com.example.methodmesh.modules.nfc

import com.example.methodmesh.core.crypto.Digests

/**
 * NFC-owned canonical credential evidence contract.
 *
 * Both NFC read/provision results expose these generic dependency fields.
 * Consumers such as attestation use the fields without importing NFC code or
 * reconstructing NFC-specific evidence themselves.
 */
object NfcCredentialEvidence {
    const val FORMAT = "nfc_uid_ndef_payload_sha256_v1"
    const val FORMAT_FIELD = "verification_evidence_format"
    const val HASH_FIELD = "verification_evidence_hash"
    const val CREDENTIAL_VERIFICATION_FORMAT =
        "methodmesh_nfc_credential_verification_v1"

    fun credentialVerificationFields(
        tagUidHex: String,
        credentialId: String,
        credentialSubjectId: String,
        credentialEnvelopeHash: String,
        issuerPublicKeyFingerprintSha256: String,
        issuerSignatureValid: Boolean,
        pinVerified: Boolean
    ): Map<String, String> {
        val uid = normalizeHex(tagUidHex, "NFC tag UID")
        val envelopeHash = normalizeSha256(
            credentialEnvelopeHash,
            "Credential envelope hash"
        )
        val issuerFingerprint = normalizeSha256(
            issuerPublicKeyFingerprintSha256,
            "Issuer public-key fingerprint"
        )

        val canonicalEvidence = listOf(
            "verification_evidence_format=$CREDENTIAL_VERIFICATION_FORMAT",
            "tag_uid_hex=$uid",
            "credential_id=${credentialId.trim()}",
            "credential_subject_id=${credentialSubjectId.trim()}",
            "credential_envelope_hash=$envelopeHash",
            "issuer_public_key_fingerprint_sha256=$issuerFingerprint",
            "issuer_signature_valid=$issuerSignatureValid",
            "pin_verified=$pinVerified"
        ).joinToString("\n")

        return linkedMapOf(
            FORMAT_FIELD to CREDENTIAL_VERIFICATION_FORMAT,
            HASH_FIELD to Digests.sha256Hex(canonicalEvidence)
        )
    }

    fun fields(tagValues: Map<String, String>): Map<String, String> {
        val uid = normalizeHex(
            tagValues[NfcEvidenceFields.TAG_UID_HEX].orEmpty(),
            "NFC tag UID"
        )
        val payloadDigest = tagValues[NfcEvidenceFields.NDEF_FIRST_PAYLOAD_HEX]
            ?.takeIf(String::isNotBlank)
            ?.let { Digests.sha256Hex(decodeHex(it, "NFC NDEF payload")) }
            ?: "NONE"
        val canonicalEvidence = listOf(
            "uid_hex=$uid",
            "ndef_payload_sha256=$payloadDigest"
        ).joinToString("\n")
        return linkedMapOf(
            FORMAT_FIELD to FORMAT,
            HASH_FIELD to Digests.sha256Hex(canonicalEvidence)
        )
    }

    private fun normalizeHex(value: String, label: String): String {
        val normalized = value.filterNot(Char::isWhitespace).uppercase()
        require(normalized.isNotBlank() && normalized.length % 2 == 0 && HEX.matches(normalized)) {
            "$label must be non-empty hexadecimal bytes"
        }
        return normalized
    }

    private fun normalizeSha256(value: String, label: String): String {
        val normalized = value.trim().lowercase()
        require(SHA256_HEX.matches(normalized)) {
            "$label must be a 64-character hexadecimal SHA-256 digest"
        }
        return normalized
    }

    private fun decodeHex(value: String, label: String): ByteArray {
        val normalized = normalizeHex(value, label)
        return ByteArray(normalized.length / 2) { index ->
            normalized.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private val HEX = Regex("^[0-9A-F]+$")
    private val SHA256_HEX = Regex("^[0-9a-f]{64}$")
}
