package com.example.methodmesh.modules.cryptography

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Small device-local encrypted vault. The AES key is generated inside Android
 * Keystore and is usable for a short window (30 s) after strong biometric authentication.
 *
 * Biometric templates are never available to MethodMesh and are never stored.
 */
class AndroidBiometricVault(private val context: Context) {
    companion object {
        const val KEY_ALIAS = "methodmesh.crypto.biometric.vault.v1"
        private const val PREFS = "methodmesh_crypto_vault_v1"
        private const val FIELD_IV = "iv"
        private const val FIELD_CIPHERTEXT = "ciphertext"
    }

    data class CiphertextBlob(val iv: ByteArray, val ciphertext: ByteArray)

    fun ensureKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationParameters(
                30,
                KeyProperties.AUTH_BIOMETRIC_STRONG
            )
            .setInvalidatedByBiometricEnrollment(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    fun newEncryptCipher(): Cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
        init(Cipher.ENCRYPT_MODE, ensureKey())
    }

    fun newDecryptCipher(): Cipher {
        val blob = loadBlob() ?: error("Vault is empty.")
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, ensureKey(), GCMParameterSpec(128, blob.iv))
        }
    }

    fun saveWithAuthenticatedCipher(cipher: Cipher, accounts: List<TotpAccount>) {
        val json = JSONArray().apply {
            accounts.forEach { account ->
                put(
                    JSONObject(
                        linkedMapOf(
                            "issuer" to account.issuer,
                            "account_name" to account.accountName,
                            "secret_base32" to account.secretBase32,
                            "digits" to account.digits,
                            "period_seconds" to account.periodSeconds,
                            "algorithm" to account.algorithm
                        )
                    )
                )
            }
        }.toString().toByteArray(Charsets.UTF_8)
        val encrypted = cipher.doFinal(json)
        val iv = cipher.iv ?: error("Cipher did not expose an IV.")
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(FIELD_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
            .putString(FIELD_CIPHERTEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
    }

    fun loadWithAuthenticatedCipher(cipher: Cipher): List<TotpAccount> {
        val blob = loadBlob() ?: return emptyList()
        val clear = cipher.doFinal(blob.ciphertext).toString(Charsets.UTF_8)
        val array = JSONArray(clear)
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                add(
                    TotpAccount(
                        issuer = obj.optString("issuer"),
                        accountName = obj.optString("account_name"),
                        secretBase32 = obj.getString("secret_base32"),
                        digits = obj.optInt("digits", 6),
                        periodSeconds = obj.optInt("period_seconds", 30),
                        algorithm = obj.optString("algorithm", "SHA1")
                    )
                )
            }
        }
    }

    fun hasVault(): Boolean = loadBlob() != null

    fun clearVault() {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun loadBlob(): CiphertextBlob? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val iv = prefs.getString(FIELD_IV, null) ?: return null
        val ciphertext = prefs.getString(FIELD_CIPHERTEXT, null) ?: return null
        return CiphertextBlob(
            Base64.decode(iv, Base64.NO_WRAP),
            Base64.decode(ciphertext, Base64.NO_WRAP)
        )
    }
}
