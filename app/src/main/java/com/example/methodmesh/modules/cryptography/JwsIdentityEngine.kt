package com.example.methodmesh.modules.cryptography

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec

/** Thin Android-Keystore wrapper around the dependency-free JwsEngine. */
object JwsIdentityEngine {
    const val DEFAULT_ALIAS = "methodmesh.crypto.signing.es256.v1"

    fun ensureIdentity(alias: String = DEFAULT_ALIAS): String {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!store.containsAlias(alias)) {
            val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
            val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()
            generator.initialize(spec)
            generator.generateKeyPair()
        }
        return publicJwk(alias)
    }

    fun hasIdentity(alias: String = DEFAULT_ALIAS): Boolean =
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.containsAlias(alias)

    fun publicJwk(alias: String = DEFAULT_ALIAS): String {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val cert = store.getCertificate(alias) ?: error("No signing identity for alias: $alias")
        val publicKey = cert.publicKey as? ECPublicKey ?: error("Signing key is not EC.")
        return JwsEngine.publicJwk(publicKey)
    }

    fun thumbprint(jwk: String): String = JwsEngine.thumbprint(jwk)

    fun signDetached(data: ByteArray, alias: String = DEFAULT_ALIAS): String {
        val jwk = ensureIdentity(alias)
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val privateKey = store.getKey(alias, null) as? java.security.PrivateKey ?: error("Signing key not found.")
        return JwsEngine.signDetached(data, privateKey, jwk)
    }

    fun verifyDetached(data: ByteArray, compactDetachedJws: String, jwk: String): String =
        JwsEngine.verifyDetached(data, compactDetachedJws, jwk)
}
