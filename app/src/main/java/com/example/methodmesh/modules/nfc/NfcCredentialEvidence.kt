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

    private fun decodeHex(value: String, label: String): ByteArray {
        val normalized = normalizeHex(value, label)
        return ByteArray(normalized.length / 2) { index ->
            normalized.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private val HEX = Regex("^[0-9A-F]+$")
}
