package com.example.methodmesh.modules.textdocuments

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class DocumentFormat(val contractValue: String, val label: String, val extension: String, val mimeType: String) {
    TEXT("text", "Plain text", "txt", "text/plain"),
    MARKDOWN("markdown", "Markdown", "md", "text/markdown"),
    JSON("json", "JSON", "json", "application/json"),
    JSONL("jsonl", "JSON Lines", "jsonl", "application/x-ndjson");

    companion object {
        fun fromContract(value: String?): DocumentFormat = when (value?.trim()?.lowercase()) {
            "markdown", "md" -> MARKDOWN
            "json" -> JSON
            "jsonl", "ndjson" -> JSONL
            else -> TEXT
        }

        fun detect(name: String, mime: String?): DocumentFormat = when {
            mime.equals("text/markdown", true) || name.endsWith(".md", true) || name.endsWith(".markdown", true) -> MARKDOWN
            mime.equals("application/json", true) || mime.equals("text/json", true) || name.endsWith(".json", true) -> JSON
            mime.equals("application/x-ndjson", true) || mime.equals("application/ndjson", true) ||
                mime.equals("application/jsonl", true) || name.endsWith(".jsonl", true) || name.endsWith(".ndjson", true) -> JSONL
            else -> TEXT
        }
    }
}

data class DocumentBuffer(
    val uri: Uri?,
    val title: String,
    val format: DocumentFormat,
    val text: String,
    val writable: Boolean
)

object DocumentIo {
    suspend fun read(context: Context, uri: Uri, mime: String? = null): Result<DocumentBuffer> = withContext(Dispatchers.IO) {
        runCatching {
            val title = displayName(context, uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Document"
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: error("The document provider did not supply a readable stream.")
            val writable = runCatching {
                context.contentResolver.openAssetFileDescriptor(uri, "rw")?.use { true } ?: false
            }.getOrDefault(false)
            DocumentBuffer(uri, title, DocumentFormat.detect(title, mime ?: context.contentResolver.getType(uri)), text, writable)
        }
    }

    suspend fun write(context: Context, uri: Uri, text: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use { it.write(text) }
                ?: error("The document provider did not supply a writable stream.")
        }
    }

    fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    /**
     * Attempt to retain long-lived access to a content URI.
     *
     * Returns true only when Android reports a persisted read grant afterwards.
     * Transient provider grants (for example many messaging-app attachments)
     * therefore remain usable for the current external-open session but are not
     * advertised later as reopenable recent files.
     */
    fun retainAccess(context: Context, uri: Uri, flags: Int): Boolean {
        if (uri.scheme != "content") return false

        val requested = flags and
            (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

        val candidates = listOf(
            requested,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        ).filter { it != 0 }.distinct()

        candidates.forEach { mode ->
            runCatching { context.contentResolver.takePersistableUriPermission(uri, mode) }
            if (hasPersistedReadAccess(context, uri)) return true
        }
        return hasPersistedReadAccess(context, uri)
    }

    fun hasPersistedReadAccess(context: Context, uri: Uri): Boolean =
        uri.scheme == "content" &&
            context.contentResolver.persistedUriPermissions.any { permission ->
                permission.uri == uri && permission.isReadPermission
            }

    fun userFacingReadError(error: Throwable): String {
        val message = error.message.orEmpty().lowercase()
        return when {
            error is SecurityException || "permission denial" in message || "permission" in message ->
                "Permission to this file is no longer available."
            "no such file" in message || "not found" in message ->
                "The file is no longer available."
            else -> "The file could not be read."
        }
    }

    fun userFacingWriteError(error: Throwable): String {
        val message = error.message.orEmpty().lowercase()
        return when {
            error is SecurityException || "permission denial" in message || "permission" in message ->
                "Permission to save this file is not available. Try Save as."
            else -> "The file could not be saved."
        }
    }

    fun createIntent(format: DocumentFormat, suggestedTitle: String): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = format.mimeType
            putExtra(Intent.EXTRA_TITLE, ensureExtension(suggestedTitle, format))
        }

    fun ensureExtension(title: String, format: DocumentFormat): String {
        val clean = title.trim().ifBlank { "document.${format.extension}" }
        return if (clean.substringAfterLast('.', "").equals(format.extension, true) ||
            (format == DocumentFormat.MARKDOWN && clean.endsWith(".markdown", true)) ||
            (format == DocumentFormat.JSONL && clean.endsWith(".ndjson", true))) clean
        else "$clean.${format.extension}"
    }
}
