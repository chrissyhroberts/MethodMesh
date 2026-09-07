package com.example.methodmesh.modules.emergency

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Staged v2 vault backend. UI must still gate access with BiometricPrompt/PIN
 * before calling read(). No vault bytes are exposed through AS100 results.
 */
object EmergencyVaultRepository {
    private const val KEY_ALIAS = "methodmesh_emergency_vault_v2"
    private const val ROOT = "emergency/vault"

    enum class Slot(val wireName: String) {
        PASSPORT("passport"),
        TRAVEL_INSURANCE("travel_insurance"),
        CRITICAL_DOCUMENT("critical_document"),
        EMERGENCY_CARD("emergency_card")
    }

    data class SlotMetadata(val slot: Slot, val present: Boolean, val bytes: Long)

    fun write(context: Context, slot: Slot, bytes: ByteArray) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(bytes)
        val file = file(context, slot)
        file.parentFile?.mkdirs()
        file.outputStream().use { out ->
            out.write(cipher.iv.size)
            out.write(cipher.iv)
            out.write(encrypted)
        }
    }

    fun read(context: Context, slot: Slot): ByteArray {
        val bytes = file(context, slot).readBytes()
        require(bytes.isNotEmpty()) { "Vault slot is empty." }
        val ivLength = bytes[0].toInt() and 0xff
        require(ivLength in 12..32 && bytes.size > 1 + ivLength) { "Invalid vault payload." }
        val iv = bytes.copyOfRange(1, 1 + ivLength)
        val encrypted = bytes.copyOfRange(1 + ivLength, bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(encrypted)
    }

    fun delete(context: Context, slot: Slot): Boolean = file(context, slot).delete()

    fun metadata(context: Context): List<SlotMetadata> = Slot.entries.map { slot ->
        val file = file(context, slot)
        SlotMetadata(slot, file.exists(), if (file.exists()) file.length() else 0L)
    }

    private fun file(context: Context, slot: Slot): File = File(context.filesDir, "$ROOT/${slot.wireName}.mmv")

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            generateKey()
        }
    }
}
