package com.example.methodmesh.modules.paperbridge

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest

internal object PaperBridgeFiles {
    fun newCaptureUri(context: Context): Uri {
        val file = File(context.cacheDir, "paperbridge-source-${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun copyIntoCache(context: Context, uri: Uri, prefix: String = "source"): Uri {
        val file = File(context.cacheDir, "paperbridge-$prefix-${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use(input::copyTo) }
            ?: throw IllegalArgumentException("The selected paper image could not be opened.")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun saveBitmap(context: Context, bitmap: Bitmap, prefix: String): Uri {
        val file = File(context.cacheDir, "paperbridge-$prefix-${System.currentTimeMillis()}.jpg")
        file.outputStream().use { output ->
            require(bitmap.compress(Bitmap.CompressFormat.JPEG, 96, output)) { "Could not encode paper image." }
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun sha256(context: Context, uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        } ?: throw IllegalArgumentException("Could not hash paper image.")
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun sha256Text(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
