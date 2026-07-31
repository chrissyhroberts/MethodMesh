package com.example.researchos.transport

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.example.researchos.core.researchos.ExecutionResult
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Writes direct-run results and returned attachments to the user-configured folder. */
object OutputExportRepository {
    private const val PREFS = "researchos_global_settings"
    private const val OUTPUT_TREE_URI = "output_tree_uri"
    private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS").withZone(ZoneOffset.UTC)

    fun configuredFolder(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(OUTPUT_TREE_URI, null).orEmpty()

    fun setConfiguredFolder(context: Context, uri: Uri?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (uri == null) remove(OUTPUT_TREE_URI) else putString(OUTPUT_TREE_URI, uri.toString())
        }.apply()
    }

    fun defaultFolder(context: Context): File = File(
        context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir,
        "ResearchOS/outputs"
    )

    fun export(context: Context, result: ExecutionResult): String {
        val fields = OutputFormatter.fields(result, includeProvenance = true)
        val timestamp = stamp.format(Instant.now())
        val method = result.request.method.id.value.replace(Regex("[^A-Za-z0-9_.-]"), "_")
        val base = "${timestamp}_${method}_${result.request.id.value}"
        val attachments = JSONArray()
        val attachmentFields = fields.filter { (key, value) -> key.endsWith("_uri") && value?.toString()?.isNotBlank() == true }
        val tree = configuredFolder(context).takeIf { it.isNotBlank() }?.let(Uri::parse)
        val json = JSONObject().apply {
            put("researchos_export_version", "1")
            put("exported_at", Instant.now().toString())
            put("researchos_execution_id", result.request.id.value)
            put("researchos_method_id", result.request.method.id.value)
            put("researchos_status", result.status.name)
            put("fields", JSONObject().apply {
                fields.forEach { (key, value) ->
                    val exportedValue = if (key.endsWith("_uri") && value != null) {
                        "${base}_${key.substringBeforeLast("_uri")}.${extension(value.toString())}"
                    } else value
                    put(key, exportedValue ?: JSONObject.NULL)
                }
            })
            put("attachments", attachments)
        }

        fun addAttachment(field: String, source: String) {
            val name = "${base}_${field.substringBeforeLast("_uri")}.${extension(source)}"
            val written = if (tree != null) copyToTree(context, tree, source, name) else copyToDefault(context, source, name)
            if (written != null) attachments.put(JSONObject().apply { put("field", field); put("filename", name); put("uri", written); put("exported_at", Instant.now().toString()) })
        }
        attachmentFields.forEach { (field, value) -> addAttachment(field, value.toString()) }
        val outputName = "$base.json"
        val jsonBytes = json.toString(2).toByteArray(Charsets.UTF_8)
        val outputUri = if (tree != null) writeTree(context, tree, outputName, "application/json", jsonBytes) else {
            writeDefault(context, outputName, "application/json", jsonBytes)
        }
        return outputUri ?: throw IllegalStateException("The selected output folder could not be written.")
    }

    private fun extension(source: String): String = Uri.parse(source).lastPathSegment?.substringAfterLast('.', "bin")?.takeIf { it.length <= 8 } ?: "bin"

    private fun copyToDefault(context: Context, source: String, name: String): String? = runCatching {
        val bytes = openSource(context, source)?.use { it.readBytes() } ?: return null
        writeDefault(context, name, "application/octet-stream", bytes)
    }.getOrNull()

    private fun copyToTree(context: Context, tree: Uri, source: String, name: String): String? = runCatching {
        val bytes = openSource(context, source)?.use { it.readBytes() } ?: return null
        writeTree(context, tree, name, "application/octet-stream", bytes)
    }.getOrNull()

    private fun openSource(context: Context, source: String) = if (source.startsWith("content://")) {
        context.contentResolver.openInputStream(Uri.parse(source))
    } else {
        File(source.removePrefix("file://")).takeIf { it.exists() }?.inputStream()
    }

    private fun writeTree(context: Context, tree: Uri, name: String, mime: String, bytes: ByteArray): String? {
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val uri = DocumentsContract.createDocument(context.contentResolver, parent, mime, name) ?: return null
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return null
        return uri.toString()
    }

    private fun writeDefault(context: Context, name: String, mime: String, bytes: ByteArray): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = android.content.ContentValues().apply {
                put(MediaStore.Files.FileColumns.DISPLAY_NAME, name)
                put(MediaStore.Files.FileColumns.MIME_TYPE, mime)
                put(MediaStore.Files.FileColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/ResearchOS/outputs")
                put(MediaStore.Files.FileColumns.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Files.getContentUri("external"), values) ?: return null
            return runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Unable to write output")
                val completed = android.content.ContentValues().apply { put(MediaStore.Files.FileColumns.IS_PENDING, 0) }
                context.contentResolver.update(uri, completed, null, null)
                uri.toString()
            }.getOrElse {
                context.contentResolver.delete(uri, null, null)
                throw it
            }
        }
        val out = File(defaultFolder(context).apply { mkdirs() }, name)
        out.writeBytes(bytes)
        return out.absolutePath
    }
}
