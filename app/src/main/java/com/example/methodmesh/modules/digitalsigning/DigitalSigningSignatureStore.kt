package com.example.methodmesh.modules.digitalsigning

import android.content.Context
import com.example.methodmesh.platform.BiometricAuthHelper
import java.io.File
import java.util.UUID

/**
 * Private reusable-signature storage. Listing metadata is harmless; reading the
 * signature bytes always requires biometric authentication and never accepts a
 * device credential fallback.
 */
object DigitalSigningSignatureStore {
    data class SavedSignature(val id: String, val displayName: String, val file: File)

    fun list(context: Context): List<SavedSignature> = directory(context).listFiles()
        .orEmpty()
        .filter { it.isFile && it.extension == "sig" }
        .sortedBy { it.name }
        .map { file -> SavedSignature(file.nameWithoutExtension, file.nameWithoutExtension, file) }

    fun save(context: Context, displayName: String, bytes: ByteArray): SavedSignature {
        require(bytes.isNotEmpty()) { "Signature data is empty." }
        val id = UUID.randomUUID().toString()
        val file = File(directory(context), "$id.sig")
        file.writeBytes(bytes)
        return SavedSignature(id, displayName.trim().ifBlank { "Saved signature" }, file)
    }

    fun unlock(
        context: Context,
        signature: SavedSignature,
        onUnlocked: (ByteArray, authenticationMethod: String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        BiometricAuthHelper.authenticate(
            context = context,
            title = "Unlock saved signature",
            subtitle = signature.displayName,
            description = "Fingerprint authentication is required before this saved signature can be reused.",
            cancelText = "Cancel",
            confirmationRequired = true,
            allowDeviceCredential = false,
            onSuccess = { method ->
                runCatching { signature.file.readBytes() }
                    .onSuccess { onUnlocked(it, method) }
                    .onFailure { onFailure("Could not read saved signature: ${it.message ?: "file error"}") }
            },
            onFailure = onFailure
        )
    }

    private fun directory(context: Context): File =
        File(context.filesDir, "methodmesh/digital_signing/signatures").apply { mkdirs() }
}
