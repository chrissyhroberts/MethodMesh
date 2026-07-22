package com.example.researchos.modules.attestation

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import android.util.Base64

/**
 * Device-owned signing key for traceable field attestations.
 *
 * The private key is generated once inside Android Keystore and is not exported.
 * The public key can be exported with study metadata and used after the study to
 * verify every attestation and nightly anchor bundle produced by this device.
 */
object AttestationCrypto {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "researchos_attestation_signing_key_v1"
    private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"

    fun ensureKeyPair(): KeyPair {
        existingKeyPair()?.let { return it }
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
            .setUserAuthenticationRequired(false)
            .build()
        generator.initialize(spec)
        return generator.generateKeyPair()
    }

    fun publicKeyBase64(): String = base64(ensureKeyPair().public.encoded)

    fun publicKeyId(): String = sha256Hex(publicKeyBase64()).take(16)

    fun signCanonical(canonical: String): String {
        val privateKey = ensureKeyPair().private
        val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
        signature.initSign(privateKey)
        signature.update(canonical.toByteArray(Charsets.UTF_8))
        return base64(signature.sign())
    }

    fun verifyCanonical(canonical: String, signatureBase64: String): Boolean = runCatching {
        val verifier = Signature.getInstance(SIGNATURE_ALGORITHM)
        verifier.initVerify(ensureKeyPair().public)
        verifier.update(canonical.toByteArray(Charsets.UTF_8))
        verifier.verify(Base64.decode(signatureBase64, Base64.NO_WRAP))
    }.getOrDefault(false)

    fun sha256Hex(value: String): String = sha256(value.toByteArray(Charsets.UTF_8))

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private fun existingKeyPair(): KeyPair? = runCatching {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) return null
        val privateKey = keyStore.getKey(KEY_ALIAS, null) as? PrivateKey ?: return null
        val publicKey = keyStore.getCertificate(KEY_ALIAS)?.publicKey ?: return null
        KeyPair(publicKey, privateKey)
    }.getOrNull()

    private fun base64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
}
