package com.example.methodmesh.modules.paperbridge

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONObject
import java.io.File
import java.util.UUID

internal data class PaperDesignSource(
    val path: String,
    val mimeType: String,
    val displayName: String
)

/** Persistent blank-form sources only. No completed questionnaire images live here. */
internal object PaperDesignSourceStore {
    private const val PREFS = "paperbridge_design_sources_v1"
    private const val KEY_BINDINGS = "bindings"

    fun import(context: Context, uri: Uri): PaperDesignSource {
        val mime = context.contentResolver.getType(uri).orEmpty().ifBlank {
            if (uri.toString().lowercase().endsWith(".pdf")) "application/pdf" else "image/*"
        }
        val name = displayName(context, uri).ifBlank { if (mime == "application/pdf") "paper-form.pdf" else "paper-form-image" }
        val extension = when {
            mime == "application/pdf" -> ".pdf"
            mime.contains("png") -> ".png"
            mime.contains("webp") -> ".webp"
            else -> ".jpg"
        }
        val dir = File(context.filesDir, "paperbridge/design_sources").apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}$extension")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= 50L * 1024 * 1024) { "Blank paper source is larger than 50 MB." }
                        output.write(buffer, 0, read)
                    }
                }
            } ?: throw IllegalArgumentException("Could not open blank paper form.")
            require(file.length() > 0L) { "Blank paper form was empty." }
        } catch (error: Throwable) {
            runCatching { file.delete() }
            throw error
        }
        return PaperDesignSource(file.absolutePath, mime, name)
    }

    fun bind(context: Context, template: PaperTemplate, source: PaperDesignSource) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val root = runCatching { JSONObject(prefs.getString(KEY_BINDINGS, "{}") ?: "{}") }.getOrDefault(JSONObject())
        root.put(
            PaperBridgeWorkspace.templateKey(template),
            JSONObject().put("path", source.path).put("mime", source.mimeType).put("name", source.displayName)
        )
        prefs.edit().putString(KEY_BINDINGS, root.toString()).apply()
    }

    fun sourceFor(context: Context, template: PaperTemplate): PaperDesignSource? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val root = runCatching { JSONObject(prefs.getString(KEY_BINDINGS, "{}") ?: "{}") }.getOrNull() ?: JSONObject()
        val item = root.optJSONObject(PaperBridgeWorkspace.templateKey(template))
        if (item != null) {
            val path = item.optString("path")
            if (path.isNotBlank() && File(path).exists()) {
                return PaperDesignSource(path, item.optString("mime", "image/*"), item.optString("name", File(path).name))
            }
        }
        return if (PaperBridgeBuiltInExamples.isDemo(template)) {
            runCatching { PaperBridgeBuiltInExamples.paperSource(context) }.getOrNull()
        } else {
            null
        }
    }

    private fun displayName(context: Context, uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
        }.orEmpty()
    }.getOrDefault("")
}
