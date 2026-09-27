package com.example.methodmesh.modules.textdocuments

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

enum class DocumentFormat(
    val contractValue: String,
    val label: String,
    val extension: String,
    val mimeType: String,
    val group: String
) {
    TEXT("text", "Plain text", "txt", "text/plain", "Text & data"),
    MARKDOWN("markdown", "Markdown", "md", "text/markdown", "Text & data"),
    JSON("json", "JSON", "json", "application/json", "Text & data"),
    JSONL("jsonl", "JSON Lines", "jsonl", "application/x-ndjson", "Text & data"),
    YAML("yaml", "YAML", "yaml", "application/yaml", "Data & config"),
    TOML("toml", "TOML", "toml", "application/toml", "Data & config"),
    XML("xml", "XML", "xml", "application/xml", "Web & markup"),
    HTML("html", "HTML", "html", "text/html", "Web & markup"),
    CSS("css", "CSS", "css", "text/css", "Web & markup"),
    JAVASCRIPT("javascript", "JavaScript", "js", "text/javascript", "Source code"),
    TYPESCRIPT("typescript", "TypeScript", "ts", "text/plain", "Source code"),
    PYTHON("python", "Python", "py", "text/x-python", "Source code"),
    KOTLIN("kotlin", "Kotlin", "kt", "text/plain", "Source code"),
    JAVA("java", "Java", "java", "text/plain", "Source code"),
    SHELL("shell", "Shell script", "sh", "application/x-sh", "Source code"),
    SQL("sql", "SQL", "sql", "application/sql", "Source code"),
    CSV("csv", "CSV", "csv", "text/csv", "Text & data"),
    PROPERTIES("properties", "Properties / INI", "properties", "text/plain", "Data & config"),
    CODE("code", "Source code", "txt", "text/plain", "Source code");

    companion object {
        fun fromContract(value: String?): DocumentFormat = when (value?.trim()?.lowercase()) {
            "markdown", "md" -> MARKDOWN
            "json" -> JSON
            "jsonl", "ndjson" -> JSONL
            "yaml", "yml" -> YAML
            "toml" -> TOML
            "xml" -> XML
            "html", "htm" -> HTML
            "css" -> CSS
            "javascript", "js" -> JAVASCRIPT
            "typescript", "ts" -> TYPESCRIPT
            "python", "py" -> PYTHON
            "kotlin", "kt" -> KOTLIN
            "java" -> JAVA
            "shell", "sh", "bash" -> SHELL
            "sql" -> SQL
            "csv", "tsv" -> CSV
            "properties", "ini", "cfg", "conf" -> PROPERTIES
            "code", "source" -> CODE
            else -> TEXT
        }

        fun detect(name: String, mime: String?): DocumentFormat {
            val extension = name.substringAfterLast('.', "").lowercase()
            return when (extension) {
                "md", "markdown" -> MARKDOWN
                "json" -> JSON
                "jsonl", "ndjson" -> JSONL
                "yaml", "yml" -> YAML
                "toml" -> TOML
                "xml" -> XML
                "html", "htm" -> HTML
                "css", "scss", "sass", "less" -> CSS
                "js", "mjs", "cjs" -> JAVASCRIPT
                "ts", "tsx" -> TYPESCRIPT
                "py", "pyw" -> PYTHON
                "kt", "kts" -> KOTLIN
                "java" -> JAVA
                "sh", "bash", "zsh", "fish", "command", "bat", "cmd", "ps1" -> SHELL
                "sql" -> SQL
                "csv", "tsv" -> CSV
                "ini", "cfg", "conf", "properties", "env" -> PROPERTIES
                "c", "h", "cc", "cpp", "cxx", "hh", "hpp", "cs", "go", "rs", "swift",
                "rb", "php", "lua", "r", "pl", "pm", "dart", "groovy", "gradle", "vue", "svelte" -> CODE
                "txt", "log" -> TEXT
                else -> when {
                    mime.equals("text/markdown", true) -> MARKDOWN
                    mime.equals("application/json", true) || mime.equals("text/json", true) -> JSON
                    mime.equals("application/x-ndjson", true) || mime.equals("application/ndjson", true) ||
                        mime.equals("application/jsonl", true) -> JSONL
                    mime.equals("application/yaml", true) || mime.equals("text/yaml", true) -> YAML
                    mime.equals("application/xml", true) || mime.equals("text/xml", true) -> XML
                    mime.equals("text/html", true) -> HTML
                    mime.equals("text/css", true) -> CSS
                    mime.equals("text/javascript", true) || mime.equals("application/javascript", true) -> JAVASCRIPT
                    mime.equals("application/sql", true) -> SQL
                    mime.equals("text/csv", true) -> CSV
                    mime?.startsWith("text/", true) == true -> TEXT
                    else -> TEXT
                }
            }
        }

        val pickerMimeTypes = arrayOf(
            "*/*"
        )
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
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("The document provider did not supply a readable stream.")
            if (bytes.any { it == 0.toByte() }) {
                error("This appears to be a binary file. Text documents opens UTF-8 text files only.")
            }
            val text = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
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
        val existingExtension = clean.substringAfterLast('.', "")
        return if (existingExtension.isNotBlank() && !clean.endsWith('.')) clean
        else "$clean.${format.extension}"
    }
}
