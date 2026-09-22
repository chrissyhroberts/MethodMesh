package com.example.methodmesh.modules.adminfingerprint

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.UUID
import org.json.JSONObject

/**
 * Issues a compact, device-signed proof after the browser biometric call-out
 * succeeds.
 *
 * The private signing key is generated once in Android Keystore and never
 * exported. The token deliberately does not claim that a named person was
 * identified; it proves that MethodMesh recorded successful local biometric
 * verification for this call-out on the signing device.
 *
 * Token format:
 *   MMBV1.<base64url JSON payload>.<base64url ECDSA signature>.<key id>
 *
 * A study/backend can register [publicKeySpkiBase64Url] for [keyId] and later
 * verify pasted tokens without receiving any biometric template or image.
 */
object BrowserBiometricVerificationToken {
    const val FORMAT = "MMBV1"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "methodmesh.adminfingerprint.browserproof.es256.v1"
    private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"

    data class IssuedToken(
        val token: String,
        val keyId: String,
        val publicKeySpkiBase64Url: String,
        val issuedAtMs: Long,
        val nonce: String
    )

    fun issue(
        requestRef: String,
        caller: String,
        reason: String,
        authMethod: String,
        issuedAtMs: Long = System.currentTimeMillis()
    ): IssuedToken {
        val keyPair = ensureKeyPair()
        val keyId = sha256Hex(keyPair.public.encoded).take(16)
        val nonce = UUID.randomUUID().toString()
        val payload = JSONObject()
            .put("v", 1)
            .put("verified", true)
            .put("scope", "local_device_access")
            .put("identity_claimed", false)
            .put("auth_method", authMethod)
            .put("caller", caller)
            .put("request_ref", requestRef)
            .put("reason", reason)
            .put("iat_ms", issuedAtMs)
            .put("nonce", nonce)
            .put("kid", keyId)
            .toString()

        val payloadPart = base64Url(payload.toByteArray(Charsets.UTF_8))
        val signingInput = "$FORMAT.$payloadPart"
        val signature = Signature.getInstance(SIGNATURE_ALGORITHM).run {
            initSign(keyPair.private)
            update(signingInput.toByteArray(Charsets.US_ASCII))
            sign()
        }
        val token = "$signingInput.${base64Url(signature)}.$keyId"
        return IssuedToken(
            token = token,
            keyId = keyId,
            publicKeySpkiBase64Url = base64Url(keyPair.public.encoded),
            issuedAtMs = issuedAtMs,
            nonce = nonce
        )
    }

    private fun ensureKeyPair(): KeyPair {
        existingKeyPair()?.let { return it }
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        )
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setUserAuthenticationRequired(false)
            .build()
        generator.initialize(spec)
        return generator.generateKeyPair()
    }

    private fun existingKeyPair(): KeyPair? = runCatching {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        if (!store.containsAlias(KEY_ALIAS)) return null
        val privateKey = store.getKey(KEY_ALIAS, null) as? PrivateKey ?: return null
        val publicKey = store.getCertificate(KEY_ALIAS)?.publicKey ?: return null
        KeyPair(publicKey, privateKey)
    }.getOrNull()

    private fun base64Url(bytes: ByteArray): String = Base64.encodeToString(
        bytes,
        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
    )

    private fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }
}
