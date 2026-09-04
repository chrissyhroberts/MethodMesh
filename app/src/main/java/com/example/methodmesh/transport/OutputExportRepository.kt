package com.example.methodmesh.transport

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.example.methodmesh.core.methodmesh.ExecutionResult
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

/** Writes direct-run results and returned attachments to the user-configured folder. */
object OutputExportRepository {
    private const val PREFS = "methodmesh_global_settings"
    private const val OUTPUT_TREE_URI = "output_tree_uri"
    private const val LAST_OUTPUT_FOLDER_URI = "last_output_folder_uri"
    private const val LAST_OUTPUT_FOLDER_NAME = "last_output_folder_name"
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
        "MethodMesh/outputs"
    )

    data class ExportedFile(
        val field: String,
        val filename: String,
        val uri: String,
        val mimeType: String
    )

    data class ExportPackage(
        val json: ExportedFile,
        val manifest: ExportedFile?,
        val attachments: List<ExportedFile>,
        val folderUri: String?,
        val usedDefaultFolder: Boolean,
        val folderName: String,
        val packageId: String
    ) {
        val allFiles: List<ExportedFile> get() = listOf(json) + listOfNotNull(manifest) + attachments
        val summary: String
            get() = buildString {
                append(folderName)
                append(" · ")
                append(json.filename)
                if (attachments.isNotEmpty()) append(" + ${attachments.size} attachment${if (attachments.size == 1) "" else "s"}")
            }
    }

    data class DownloadsExport(
        val files: List<ExportedFile>,
        val folderName: String
    ) {
        val summary: String
            get() = "${files.size} file${if (files.size == 1) "" else "s"} in Downloads/MethodMesh/$folderName"
    }

    fun export(context: Context, result: ExecutionResult): String = exportPackage(context, result).json.uri

    fun saveToDownloads(
        context: Context,
        label: String,
        text: String,
        mediaUris: List<String>,
        jsonText: String = ""
    ): DownloadsExport {
        val timestamp = stamp.format(Instant.now())
        val folderName = safeSegment("${timestamp}_${safeSegment(label).ifBlank { "methodmesh_result" }}")
        val exported = mutableListOf<ExportedFile>()
        if (text.isNotBlank()) {
            val name = "result.txt"
            val uri = writePublicDownload(
                context = context,
                folderName = folderName,
                name = name,
                mime = "text/plain",
                bytes = text.toByteArray(Charsets.UTF_8)
            ) ?: throw IllegalStateException("Could not write text result to Downloads.")
            exported += ExportedFile("result_text", name, uri, "text/plain")
        }
        if (jsonText.isNotBlank()) {
            val name = "metadata.json"
            val uri = writePublicDownload(
                context = context,
                folderName = folderName,
                name = name,
                mime = "application/json",
                bytes = jsonText.toByteArray(Charsets.UTF_8)
            ) ?: throw IllegalStateException("Could not write JSON metadata to Downloads.")
            exported += ExportedFile("metadata_json", name, uri, "application/json")
        }
        mediaUris.distinct().forEachIndexed { index, source ->
            val ext = extension(source).takeIf { it != "bin" } ?: "bin"
            val name = safeSegment("media_${index + 1}") + ".$ext"
            val mime = mimeTypeFor(name)
            val bytes = openSource(context, source)?.use { it.readBytes() } ?: return@forEachIndexed
            val uri = writePublicDownload(context, folderName, name, mime, bytes)
            if (uri != null) exported += ExportedFile("media_${index + 1}", name, uri, mime)
        }
        if (exported.isEmpty()) throw IllegalStateException("No text or media was available to save.")
        return DownloadsExport(exported, folderName)
    }

    fun exportFlatPackage(
        context: Context,
        packageLabel: String,
        methodId: String,
        status: String,
        fields: Map<String, String?>,
        parentFolder: String? = null
    ): ExportPackage {
        val timestamp = stamp.format(Instant.now())
        val packageId = UUID.randomUUID().toString()
        val sourceStem = safeSegment(packageLabel).ifBlank { safeSegment(methodId) }
        val segment = safeSegment("${timestamp}_${sourceStem}_$packageId")
        val folderName = listOfNotNull(parentFolder?.takeIf { it.isNotBlank() }?.let(::safePath), segment)
            .joinToString("/")
        val attachments = JSONArray()
        val tree = configuredFolder(context).takeIf { it.isNotBlank() }?.let(Uri::parse)
        val treeFolder = tree?.let { createTreeDirectoryPath(context, it, folderName) }
        val folderUri = treeFolder?.toString() ?: defaultOutputFolderDocumentUri(folderName)
        val exportedAttachments = mutableListOf<ExportedFile>()
        val usedNames = mutableSetOf<String>()

        fun uniqueAttachmentName(field: String, source: String): String {
            val stem = safeSegment(field.substringBeforeLast("_uri")).ifBlank { "attachment" }
            val ext = extension(source)
            var candidate = safeSegment("${timestamp}_${sourceStem}_${stem}_${packageId}_${UUID.randomUUID()}") + ".$ext"
            var index = 2
            while (!usedNames.add(candidate.lowercase())) {
                candidate = safeSegment("${timestamp}_${sourceStem}_${stem}_${packageId}_${UUID.randomUUID()}_$index") + ".$ext"
                index += 1
            }
            return candidate
        }

        val jsonFields = JSONObject()
        fields.toSortedMap().forEach { (key, value) ->
            val exportedValue = if (key.endsWith("_uri") && value?.isNotBlank() == true) {
                uniqueAttachmentName(key, value)
            } else {
                value
            }
            jsonFields.put(key, exportedValue ?: JSONObject.NULL)
        }

        fields.filter { (key, value) -> key.endsWith("_uri") && value?.isNotBlank() == true }
            .forEach { (field, source) ->
                val name = jsonFields.optString(field).takeIf { it.isNotBlank() && it != "null" } ?: uniqueAttachmentName(field, source.orEmpty())
                val mime = mimeTypeFor(name)
                val written = if (treeFolder != null) {
                    copyToTreeFolder(context, treeFolder, source.orEmpty(), name, mime)
                } else {
                    copyToDefault(context, folderName, source.orEmpty(), name, mime)
                }
                if (written != null) {
                    attachments.put(JSONObject().apply {
                        put("step_index", 0)
                        put("step_number", 1)
                        put("step_name", sourceStem)
                        put("field", field)
                        put("filename", name)
                        put("uri", written)
                        put("mime_type", mime)
                        put("exported_at", Instant.now().toString())
                    })
                    exportedAttachments += ExportedFile(field, name, written, mime)
                }
            }

        val exportedAt = Instant.now().toString()
        val json = JSONObject().apply {
            put("methodmesh_output_schema", "methodmesh.output.package")
            put("methodmesh_export_version", "2")
            put("export_kind", "single_run")
            put("export_package_id", packageId)
            put("methodmesh_submission_id", packageId)
            put("export_package_name", folderName)
            put("exported_at", exportedAt)
            put("methodmesh_method_id", methodId)
            put("methodmesh_status", status)
            put("step_count", 1)
            put("steps", JSONArray().apply {
                put(JSONObject().apply {
                    put("step_index", 0)
                    put("step_number", 1)
                    put("step_name", sourceStem)
                    put("export_package_id", packageId)
                    put("methodmesh_submission_id", packageId)
                    put("methodmesh_method_id", methodId)
                    put("methodmesh_status", status)
                    put("exported_at", exportedAt)
                    put("fields", jsonFields)
                    put("attachments", attachments)
                })
            })
            put("attachments", attachments)
        }
        val jsonName = safeSegment("${timestamp}_${sourceStem}_${packageId}_result") + ".json"
        val jsonUri = if (treeFolder != null) {
            writeTreeDocument(context, treeFolder, jsonName, "application/json", json.toString(2).toByteArray(Charsets.UTF_8))
        } else {
            writeDefault(context, folderName, jsonName, "application/json", json.toString(2).toByteArray(Charsets.UTF_8))
        } ?: throw IllegalStateException("The selected output folder could not be written.")
        return rememberLastExport(context, ExportPackage(
            json = ExportedFile("result_json", jsonName, jsonUri, "application/json"),
            manifest = null,
            attachments = exportedAttachments,
            folderUri = folderUri,
            usedDefaultFolder = tree == null,
            folderName = folderName,
            packageId = packageId
        ))
    }

    fun exportProtocolStepPackage(
        context: Context,
        protocolFolder: String,
        protocolName: String,
        protocolSubmissionId: String,
        stepIndex: Int,
        stepName: String,
        methodId: String,
        status: String,
        fields: Map<String, String?>
    ): ExportPackage {
        val packageId = protocolSubmissionId.ifBlank { UUID.randomUUID().toString() }
        val folderName = safePath(protocolFolder).ifBlank { safeSegment("protocol_${UUID.randomUUID()}") }
        val tree = configuredFolder(context).takeIf { it.isNotBlank() }?.let(Uri::parse)
        val treeFolder = tree?.let { createOrFindTreeDirectoryPath(context, it, folderName) }
        val folderUri = treeFolder?.toString() ?: defaultOutputFolderDocumentUri(folderName)
        val exportedAt = Instant.now().toString()
        val attachments = JSONArray()
        val exportedAttachments = mutableListOf<ExportedFile>()
        val usedNames = mutableSetOf<String>()
        val stepPrefix = "step_${"%02d".format(stepIndex + 1)}"

        fun uniqueAttachmentName(field: String, source: String): String {
            val stem = safeSegment(field.substringBeforeLast("_uri")).ifBlank { "attachment" }
            val ext = extension(source)
            var candidate = safeSegment("${stepPrefix}_${stem}_${packageId}_${UUID.randomUUID()}") + ".$ext"
            var index = 2
            while (!usedNames.add(candidate.lowercase())) {
                candidate = safeSegment("${stepPrefix}_${stem}_${packageId}_${UUID.randomUUID()}_$index") + ".$ext"
                index += 1
            }
            return candidate
        }

        val jsonFields = JSONObject()
        fields.toSortedMap().forEach { (key, value) ->
            val exportedValue = if (key.endsWith("_uri") && value?.isNotBlank() == true) {
                uniqueAttachmentName(key, value)
            } else {
                value
            }
            jsonFields.put(key, exportedValue ?: JSONObject.NULL)
        }

        fields.filter { (key, value) -> key.endsWith("_uri") && value?.isNotBlank() == true }
            .forEach { (field, source) ->
                val name = jsonFields.optString(field).takeIf { it.isNotBlank() && it != "null" } ?: uniqueAttachmentName(field, source.orEmpty())
                val mime = mimeTypeFor(name)
                val written = if (treeFolder != null) {
                    copyToTreeFolder(context, treeFolder, source.orEmpty(), name, mime)
                } else {
                    copyToDefault(context, folderName, source.orEmpty(), name, mime)
                }
                if (written != null) {
                    attachments.put(JSONObject().apply {
                        put("step_index", stepIndex)
                        put("step_number", stepIndex + 1)
                        put("step_name", stepName)
                        put("field", field)
                        put("filename", name)
                        put("uri", written)
                        put("mime_type", mime)
                        put("exported_at", exportedAt)
                    })
                    exportedAttachments += ExportedFile(field, name, written, mime)
                }
            }

        val existing = readProtocolResultJson(context, treeFolder, folderName) ?: JSONObject().apply {
            put("methodmesh_output_schema", "methodmesh.output.package")
            put("methodmesh_export_version", "2")
            put("export_kind", "protocol_run")
            put("export_package_id", packageId)
            put("methodmesh_submission_id", packageId)
            put("protocol_name", protocolName)
            put("export_package_name", folderName)
            put("created_at", exportedAt)
            put("steps", JSONArray())
        }
        existing.put("protocol_name", protocolName)
        existing.put("export_package_id", existing.optString("export_package_id").ifBlank { packageId })
        existing.put("methodmesh_submission_id", existing.optString("methodmesh_submission_id").ifBlank { packageId })
        existing.put("export_package_name", folderName)
        existing.put("updated_at", exportedAt)
        val previousSteps = existing.optJSONArray("steps") ?: JSONArray()
        val nextSteps = JSONArray()
        for (i in 0 until previousSteps.length()) {
            val step = previousSteps.optJSONObject(i) ?: continue
            if (step.optInt("step_index", -1) != stepIndex) nextSteps.put(step)
        }
        nextSteps.put(JSONObject().apply {
            put("step_index", stepIndex)
            put("step_number", stepIndex + 1)
            put("step_name", stepName)
            put("export_package_id", existing.optString("export_package_id", packageId))
            put("methodmesh_submission_id", existing.optString("methodmesh_submission_id", packageId))
            put("methodmesh_method_id", methodId)
            put("methodmesh_status", status)
            put("exported_at", exportedAt)
            put("fields", jsonFields)
            put("attachments", attachments)
        })
        existing.put("steps", sortSteps(nextSteps))
        existing.put("step_count", existing.getJSONArray("steps").length())
        existing.put("attachments", JSONArray().apply {
            val steps = existing.getJSONArray("steps")
            for (i in 0 until steps.length()) {
                val stepAttachments = steps.getJSONObject(i).optJSONArray("attachments") ?: JSONArray()
                for (j in 0 until stepAttachments.length()) put(stepAttachments.getJSONObject(j))
            }
        })

        val jsonName = safeSegment("${folderName.substringAfterLast('/')}_protocol_result") + ".json"
        val jsonBytes = existing.toString(2).toByteArray(Charsets.UTF_8)
        val jsonUri = if (treeFolder != null) {
            writeTreeDocumentReplace(context, treeFolder, jsonName, "application/json", jsonBytes)
        } else {
            writeDefaultReplace(context, folderName, jsonName, "application/json", jsonBytes)
        } ?: throw IllegalStateException("The selected output folder could not be written.")
        return rememberLastExport(context, ExportPackage(
            json = ExportedFile("protocol_result_json", jsonName, jsonUri, "application/json"),
            manifest = null,
            attachments = exportedAttachments,
            folderUri = folderUri,
            usedDefaultFolder = tree == null,
            folderName = folderName,
            packageId = existing.optString("export_package_id", packageId)
        ))
    }

    fun exportPackage(context: Context, result: ExecutionResult): ExportPackage {
        val fields = OutputFormatter.fields(result, includeProvenance = true)
        val timestamp = stamp.format(Instant.now())
        val packageId = UUID.randomUUID().toString()
        val method = safeSegment(result.request.method.id.value).ifBlank { "methodmesh_method" }
        val folderName = safeSegment("${timestamp}_${method}_${result.request.id.value}_$packageId")
        val attachments = JSONArray()
        val attachmentFields = fields.filter { (key, value) -> key.endsWith("_uri") && value?.toString()?.isNotBlank() == true }
        val tree = configuredFolder(context).takeIf { it.isNotBlank() }?.let(Uri::parse)
        val treeFolder = tree?.let { createTreeDirectory(context, it, folderName) }
        val folderUri = treeFolder?.toString() ?: defaultOutputFolderDocumentUri(folderName)
        val exportedAttachments = mutableListOf<ExportedFile>()
        val usedNames = mutableSetOf<String>()

        fun uniqueAttachmentName(field: String, source: String): String {
            val stem = safeSegment(field.substringBeforeLast("_uri")).ifBlank { "attachment" }
            val ext = extension(source)
            var candidate = safeSegment("${timestamp}_${method}_${stem}_${packageId}_${UUID.randomUUID()}") + ".$ext"
            var index = 2
            while (!usedNames.add(candidate.lowercase())) {
                candidate = safeSegment("${timestamp}_${method}_${stem}_${packageId}_${UUID.randomUUID()}_$index") + ".$ext"
                index += 1
            }
            return candidate
        }

        val jsonFields = JSONObject().apply {
            fields.forEach { (key, value) ->
                val exportedValue = if (key.endsWith("_uri") && value != null) {
                    uniqueAttachmentName(key, value.toString())
                } else value
                put(key, exportedValue ?: JSONObject.NULL)
            }
        }

        fun addAttachment(field: String, source: String) {
            val exportedName = jsonFields.optString(field)
            val name = exportedName.takeIf { it.isNotBlank() && it != "null" } ?: uniqueAttachmentName(field, source)
            val mime = mimeTypeFor(name)
            val written = if (treeFolder != null) {
                copyToTreeFolder(context, treeFolder, source, name, mime)
            } else {
                copyToDefault(context, folderName, source, name, mime)
            }
            if (written != null) {
                attachments.put(JSONObject().apply {
                    put("step_index", 0)
                    put("step_number", 1)
                    put("step_name", method)
                    put("field", field)
                    put("filename", name)
                    put("uri", written)
                    put("mime_type", mime)
                    put("exported_at", Instant.now().toString())
                })
                exportedAttachments += ExportedFile(field, name, written, mime)
            }
        }
        attachmentFields.forEach { (field, value) -> addAttachment(field, value.toString()) }
        val exportedAt = Instant.now().toString()
        val json = JSONObject().apply {
            put("methodmesh_output_schema", "methodmesh.output.package")
            put("methodmesh_export_version", "2")
            put("export_kind", "single_run")
            put("export_package_id", packageId)
            put("methodmesh_submission_id", packageId)
            put("export_package_name", folderName)
            put("exported_at", exportedAt)
            put("methodmesh_execution_id", result.request.id.value)
            put("methodmesh_method_id", result.request.method.id.value)
            put("methodmesh_status", result.status.name)
            put("step_count", 1)
            put("steps", JSONArray().apply {
                put(JSONObject().apply {
                    put("step_index", 0)
                    put("step_number", 1)
                    put("step_name", method)
                    put("export_package_id", packageId)
                    put("methodmesh_submission_id", packageId)
                    put("methodmesh_execution_id", result.request.id.value)
                    put("methodmesh_method_id", result.request.method.id.value)
                    put("methodmesh_status", result.status.name)
                    put("exported_at", exportedAt)
                    put("fields", jsonFields)
                    put("attachments", attachments)
                })
            })
            put("attachments", attachments)
        }
        val outputName = safeSegment("${timestamp}_${method}_${result.request.id.value}_${packageId}_result") + ".json"
        val jsonBytes = json.toString(2).toByteArray(Charsets.UTF_8)
        val outputUri = if (treeFolder != null) {
            writeTreeDocument(context, treeFolder, outputName, "application/json", jsonBytes)
        } else {
            writeDefault(context, folderName, outputName, "application/json", jsonBytes)
        }
        val jsonUri = outputUri ?: throw IllegalStateException("The selected output folder could not be written.")
        return rememberLastExport(context, ExportPackage(
            json = ExportedFile("result_json", outputName, jsonUri, "application/json"),
            manifest = null,
            attachments = exportedAttachments,
            folderUri = folderUri,
            usedDefaultFolder = tree == null,
            folderName = folderName,
            packageId = packageId
        ))
    }

    fun share(context: Context, exportPackage: ExportPackage) {
        val uris = ArrayList(exportPackage.allFiles.mapNotNull { shareableUri(context, it.uri) })
        if (uris.isEmpty()) throw IllegalStateException("No exported files are available to share.")
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = exportPackage.allFiles.first().mimeType
                putExtra(Intent.EXTRA_STREAM, uris.first())
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
        }.apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_SUBJECT, "MethodMesh export")
            putExtra(Intent.EXTRA_TEXT, "MethodMesh export: ${exportPackage.summary}")
        }
        context.startActivity(Intent.createChooser(intent, "Share MethodMesh export").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun shareMedia(context: Context, exportPackage: ExportPackage) {
        val media = exportPackage.attachments.filterNot { it.mimeType == "application/json" || it.mimeType == "text/plain" }
        val uris = ArrayList(media.mapNotNull { shareableUri(context, it.uri) })
        if (uris.isEmpty()) throw IllegalStateException("No media attachments are available to share.")
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = media.first().mimeType
                putExtra(Intent.EXTRA_STREAM, uris.first())
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
        }.apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_SUBJECT, "MethodMesh media export")
            putExtra(Intent.EXTRA_TEXT, "MethodMesh media export: ${exportPackage.summary}")
        }
        context.startActivity(Intent.createChooser(intent, "Share MethodMesh media").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun notifySaved(context: Context, exportPackage: ExportPackage) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channelId = "methodmesh_outputs"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    "MethodMesh outputs",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Saved MethodMesh result packages" }
            )
        }
        val openIntent = openLocationIntent(context, exportPackage)
        val requestCode = exportPackage.folderName.hashCode()
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setContentTitle("MethodMesh output saved")
            .setContentText("Tap Open to view the export folder")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Saved ${exportPackage.summary}"))
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_view, "Open", pendingIntent)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(requestCode, notification) }
    }

    fun openLocationIntent(context: Context, exportPackage: ExportPackage): Intent {
        val target = exportPackage.folderUri?.let(Uri::parse) ?: shareableUri(context, exportPackage.json.uri)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(target, if (exportPackage.folderUri != null) DocumentsContract.Document.MIME_TYPE_DIR else exportPackage.json.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun openLocation(context: Context, exportPackage: ExportPackage) {
        runCatching { context.startActivity(openLocationIntent(context, exportPackage)) }.getOrElse {
            // Some Android file managers cannot open tree/document URIs. The
            // chooser still gives users a route to inspect or forward the files.
            share(context, exportPackage)
        }
    }

    fun lastOutputLabel(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(LAST_OUTPUT_FOLDER_NAME, null).orEmpty()

    fun openLatestOutput(context: Context) {
        val uri = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(LAST_OUTPUT_FOLDER_URI, null)
            ?.takeIf { it.isNotBlank() }
            ?.let(Uri::parse)
        if (uri == null) {
            openOutputs(context)
            return
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }.getOrElse { openOutputs(context) }
    }

    fun openOutputsIntent(context: Context): Intent {
        val configured = configuredFolder(context).takeIf { it.isNotBlank() }?.let(Uri::parse)
        val target = configured ?: defaultOutputsDocumentUri()
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(target, DocumentsContract.Document.MIME_TYPE_DIR)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun openOutputs(context: Context) {
        val documentsPath = defaultOutputsDocumentUri()
        runCatching { context.startActivity(openOutputsIntent(context)) }.getOrElse {
            val fallback = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, documentsPath)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        }
    }

    private fun extension(source: String): String = Uri.parse(source).lastPathSegment?.substringAfterLast('.', "bin")?.takeIf { it.length <= 8 } ?: "bin"

    private fun copyToDefault(context: Context, folderName: String, source: String, name: String, mime: String): String? = runCatching {
        val bytes = openSource(context, source)?.use { it.readBytes() } ?: return null
        writeDefault(context, folderName, name, mime, bytes)
    }.getOrNull()

    private fun copyToTreeFolder(context: Context, folder: Uri, source: String, name: String, mime: String): String? = runCatching {
        val bytes = openSource(context, source)?.use { it.readBytes() } ?: return null
        writeTreeDocument(context, folder, name, mime, bytes)
    }.getOrNull()

    private fun openSource(context: Context, source: String) = if (source.startsWith("content://")) {
        context.contentResolver.openInputStream(Uri.parse(source))
    } else {
        File(source.removePrefix("file://")).takeIf { it.exists() }?.inputStream()
    }


    private fun sortSteps(steps: JSONArray): JSONArray {
        val objects = mutableListOf<JSONObject>()
        for (i in 0 until steps.length()) steps.optJSONObject(i)?.let(objects::add)
        return JSONArray().apply { objects.sortedBy { it.optInt("step_index", 0) }.forEach { put(it) } }
    }

    private fun readProtocolResultJson(context: Context, treeFolder: Uri?, folderName: String): JSONObject? = runCatching {
        val uri = if (treeFolder != null) {
            findTreeChild(context, treeFolder, safeSegment("${folderName.substringAfterLast('/')}_protocol_result") + ".json", directory = false)
                ?: findTreeChild(context, treeFolder, "protocol_result.json", directory = false)
        } else {
            findDefaultDocument(context, folderName, safeSegment("${folderName.substringAfterLast('/')}_protocol_result") + ".json")
                ?: findDefaultDocument(context, folderName, "protocol_result.json")
        } ?: return@runCatching null
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
            JSONObject(reader.readText())
        }
    }.getOrNull()

    private fun createOrFindTreeDirectoryPath(context: Context, tree: Uri, path: String): Uri? = runCatching {
        var parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        path.split('/').filter { it.isNotBlank() }.forEach { segment ->
            parent = findTreeChild(context, parent, segment, directory = true)
                ?: DocumentsContract.createDocument(context.contentResolver, parent, DocumentsContract.Document.MIME_TYPE_DIR, segment)
                ?: return@runCatching null
        }
        parent
    }.getOrNull()

    private fun findTreeChild(context: Context, parent: Uri, name: String, directory: Boolean): Uri? = runCatching {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parent, DocumentsContract.getDocumentId(parent))
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                val display = cursor.getString(nameIndex)
                val mime = cursor.getString(mimeIndex)
                if (display == name && (!directory || mime == DocumentsContract.Document.MIME_TYPE_DIR)) {
                    return@runCatching DocumentsContract.buildDocumentUriUsingTree(parent, cursor.getString(idIndex))
                }
            }
        }
        null
    }.getOrNull()

    private fun writeTreeDocumentReplace(context: Context, parent: Uri, name: String, mime: String, bytes: ByteArray): String? {
        findTreeChild(context, parent, name, directory = false)?.let { existing ->
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, existing) }
        }
        return writeTreeDocument(context, parent, name, mime, bytes)
    }

    private fun findDefaultDocument(context: Context, folderName: String, name: String): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val file = File(defaultFolder(context), folderName).resolve(name)
            return file.takeIf { it.exists() }?.let { fileProviderUri(context, it) }
        }
        val relativePath = Environment.DIRECTORY_DOCUMENTS + "/MethodMesh/outputs/$folderName/"
        val projection = arrayOf(MediaStore.Files.FileColumns._ID)
        val selection = "${MediaStore.Files.FileColumns.RELATIVE_PATH}=? AND ${MediaStore.Files.FileColumns.DISPLAY_NAME}=?"
        val args = arrayOf(relativePath, name)
        context.contentResolver.query(MediaStore.Files.getContentUri("external"), projection, selection, args, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID))
                return Uri.withAppendedPath(MediaStore.Files.getContentUri("external"), id.toString())
            }
        }
        return null
    }

    private fun writeDefaultReplace(context: Context, folderName: String, name: String, mime: String, bytes: ByteArray): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            findDefaultDocument(context, folderName, name)?.let { existing ->
                runCatching { context.contentResolver.delete(existing, null, null) }
            }
        } else {
            File(defaultFolder(context), folderName).resolve(name).delete()
        }
        return writeDefault(context, folderName, name, mime, bytes)
    }

    private fun createTreeDirectory(context: Context, tree: Uri, name: String): Uri? = runCatching {
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        DocumentsContract.createDocument(context.contentResolver, parent, DocumentsContract.Document.MIME_TYPE_DIR, name)
    }.getOrNull()

    private fun createTreeDirectoryPath(context: Context, tree: Uri, path: String): Uri? = runCatching {
        var parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        path.split('/').filter { it.isNotBlank() }.forEach { segment ->
            parent = DocumentsContract.createDocument(context.contentResolver, parent, DocumentsContract.Document.MIME_TYPE_DIR, segment)
                ?: return@runCatching null
        }
        parent
    }.getOrNull()

    private fun writeTreeDocument(context: Context, parent: Uri, name: String, mime: String, bytes: ByteArray): String? {
        val uri = DocumentsContract.createDocument(context.contentResolver, parent, mime, name) ?: return null
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return null
        return uri.toString()
    }

    private fun writeDefault(context: Context, folderName: String, name: String, mime: String, bytes: ByteArray): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = android.content.ContentValues().apply {
                put(MediaStore.Files.FileColumns.DISPLAY_NAME, name)
                put(MediaStore.Files.FileColumns.MIME_TYPE, mime)
                put(MediaStore.Files.FileColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/MethodMesh/outputs/$folderName")
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
        val out = File(defaultFolder(context), folderName).apply { mkdirs() }.resolve(name)
        out.writeBytes(bytes)
        return out.absolutePath
    }

    private fun writePublicDownload(context: Context, folderName: String, name: String, mime: String, bytes: ByteArray): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = android.content.ContentValues().apply {
                put(MediaStore.Files.FileColumns.DISPLAY_NAME, name)
                put(MediaStore.Files.FileColumns.MIME_TYPE, mime)
                put(MediaStore.Files.FileColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MethodMesh/$folderName")
                put(MediaStore.Files.FileColumns.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Files.getContentUri("external"), values) ?: return null
            return runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Unable to write download")
                val completed = android.content.ContentValues().apply { put(MediaStore.Files.FileColumns.IS_PENDING, 0) }
                context.contentResolver.update(uri, completed, null, null)
                uri.toString()
            }.getOrElse {
                context.contentResolver.delete(uri, null, null)
                throw it
            }
        }
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val out = File(downloads, "MethodMesh/$folderName").apply { mkdirs() }.resolve(name)
        out.writeBytes(bytes)
        return out.absolutePath
    }


    private fun rememberLastExport(context: Context, exportPackage: ExportPackage): ExportPackage {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(LAST_OUTPUT_FOLDER_URI, exportPackage.folderUri.orEmpty())
            .putString(LAST_OUTPUT_FOLDER_NAME, exportPackage.folderName)
            .apply()
        return exportPackage
    }

    private fun defaultOutputsDocumentUri(): Uri = Uri.parse(
        "content://com.android.externalstorage.documents/document/primary%3ADocuments%2FMethodMesh%2Foutputs"
    )

    private fun defaultOutputFolderDocumentUri(folderName: String): String {
        val documentId = Uri.encode("primary:Documents/MethodMesh/outputs/$folderName")
        return "content://com.android.externalstorage.documents/document/$documentId"
    }

    fun defaultOutputsPathLabel(folderName: String? = null): String =
        listOf("Documents", "MethodMesh", "outputs", folderName.orEmpty()).filter { it.isNotBlank() }.joinToString("/")

    private fun safeSegment(value: String): String = value.replace(Regex("[^A-Za-z0-9_.-]"), "_").trim('_')

    private fun safePath(value: String): String = value.split('/').joinToString("/") { safeSegment(it) }.trim('/')

    private fun shareableUri(context: Context, value: String): Uri? = when {
        value.startsWith("content://") -> Uri.parse(value)
        value.startsWith("file://") -> File(value.removePrefix("file://")).takeIf { it.exists() }?.let { fileProviderUri(context, it) }
        else -> File(value).takeIf { it.exists() }?.let { fileProviderUri(context, it) }
    }

    private fun fileProviderUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    private fun mimeTypeFor(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "json" -> "application/json"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "pdf" -> "application/pdf"
        "txt" -> "text/plain"
        "csv" -> "text/csv"
        else -> "application/octet-stream"
    }
}
