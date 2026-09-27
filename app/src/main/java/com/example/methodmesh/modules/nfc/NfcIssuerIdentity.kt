package com.example.methodmesh.modules.nfc

import com.example.methodmesh.core.crypto.Digests
import java.security.PublicKey
import java.util.Base64

internal data class NfcIssuerIdentityRecord(
    val issuerKeyId: String,
    val publicKeyFingerprintSha256: String,
    val publicKeyBase64: String,
    val signatureAlgorithm: String
)

/**
 * Canonical issuer identity derivation shared by provisioning, verification and
 * the Workbench issuer-identity capability.
 *
 * The full SHA-256 fingerprint of SubjectPublicKeyInfo (`PublicKey.encoded`) is
 * canonical. The first 16 hex characters remain the legacy/display key ID.
 */
internal object NfcIssuerIdentityResolver {
    fun local(): NfcIssuerIdentityRecord = fromPublicKey(AndroidNfcCredentialSigner.publicKey)

    fun fromPublicKey(publicKey: PublicKey): NfcIssuerIdentityRecord =
        fromEncodedPublicKey(publicKey.encoded)

    fun fromPublicKeyBase64(publicKeyBase64: String): NfcIssuerIdentityRecord {
        require(publicKeyBase64.isNotBlank()) { "Issuer public key is missing." }
        return fromEncodedPublicKey(Base64.getDecoder().decode(publicKeyBase64))
    }

    private fun fromEncodedPublicKey(encoded: ByteArray): NfcIssuerIdentityRecord {
        require(encoded.isNotEmpty()) { "Issuer public key is empty." }
        val fingerprint = Digests.sha256Hex(encoded)
        return NfcIssuerIdentityRecord(
            issuerKeyId = fingerprint.take(16),
            publicKeyFingerprintSha256 = fingerprint,
            publicKeyBase64 = Base64.getEncoder().encodeToString(encoded),
            signatureAlgorithm = NfcCredentialSigner.SIGNATURE_ALGORITHM
        )
    }
}
