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
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.example.methodmesh.core.methodmesh.ExecutionResult
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

/** Writes direct-run results and returned attachments to the user-configured folder. */
object OutputExportRepository {
    private class DownloadCleanupFailure(cause: Throwable? = null) :
        IllegalStateException("Could not remove incomplete download.", cause)
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
        jsonText: String = "",
        mediaMimeTypes: Map<String, String> = emptyMap()
    ): DownloadsExport {
        LegacyDownloadsPermission.requireAccess(context)
        val operationInstant = Instant.now()
        val stem = ResultShare.coherentFileStem(label, operationInstant)
        val folderName = stem
        val exported = mutableListOf<ExportedFile>()
        fun write(field: String, name: String, mime: String, contents: (OutputStream) -> Unit) {
            val uri = writePublicDownload(context, folderName, name, mime, contents)
                ?: error("Could not create Downloads file: $name")
            exported += ExportedFile(field, name, uri, mime)
        }
        try {
            if (text.isNotBlank()) {
                write("result_text", "${stem}_result.txt", "text/plain") { it.write(text.toByteArray(Charsets.UTF_8)) }
            }
            if (jsonText.isNotBlank()) {
                write("metadata_json", "${stem}_metadata.json", "application/json") { it.write(jsonText.toByteArray(Charsets.UTF_8)) }
            }
            mediaUris.distinct().forEachIndexed { index, source ->
                val sourceUri = Uri.parse(source)
                val resolvedMime = if (source.startsWith("content://")) {
                    context.contentResolver.getType(sourceUri)?.takeIf { it.isNotBlank() && it != "application/octet-stream" }
                } else null
                val usefulMime = resolvedMime ?: mediaMimeTypes[source]?.takeIf { it.isNotBlank() && it != "application/octet-stream" }
                val ext = extension(source).takeIf { it != "bin" }
                    ?: usefulMime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
                    ?: "bin"
                val sourceBase = sourceDisplayName(context, source)
                    ?.let { name -> name.substringBeforeLast('.', name) }
                    ?.let(::safeSegment)?.lowercase()?.takeIf { it.isNotBlank() }
                val suffix = sourceBase?.let { "${it}_${index + 1}" } ?: "media_${index + 1}"
                val name = "${stem}_${suffix}.$ext"
                write("media_${index + 1}", name, usefulMime ?: mimeTypeFor(name)) { output ->
                    // Stream large artefacts; never report a partial bundle as a
                    // successful save merely because text was written first.
                    requireNotNull(openSource(context, source)) { "Could not read selected media." }
                        .use { input -> input.copyTo(output) }
                }
            }
            check(exported.isNotEmpty()) { "No text or media was available to save." }
            return DownloadsExport(exported, folderName)
        } catch (failure: Exception) {
            var cleanupFailed = failure.suppressed.any { it is DownloadCleanupFailure }
            exported.forEach { file ->
                runCatching {
                    if (file.uri.startsWith("content://")) {
                        check(context.contentResolver.delete(Uri.parse(file.uri), null, null) > 0) { "Could not remove incomplete download." }
                    } else {
                        val path = File(file.uri)
                        check(!path.exists() || path.delete()) { "Could not remove incomplete download." }
                    }
                }.onFailure { cleanupFailed = true; failure.addSuppressed(it) }
            }
            val detail = if (cleanupFailed) " Some incomplete files could not be removed." else " No files from this attempt were kept."
            throw IllegalStateException("Could not save all result files.$detail", failure)
        }
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
        val treeFolder = tree?.let { checkNotNull(createTreeDirectoryPath(context, it, folderName)) { "The configured output folder is unavailable. Choose a writable folder and retry." } }
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

        val plan = ExportAttachmentProjection.plan(fields, ::uniqueAttachmentName)
        val jsonFields = plan.fields

        plan.attachments
            .forEach { attachment ->
                val field = attachment.field
                val source = attachment.source
                val name = attachment.filename
                val mime = mimeTypeFor(name)
                val written = if (treeFolder != null) {
                    copyToTreeFolder(context, treeFolder, source, name, mime)
                } else {
                    copyToDefault(context, folderName, source, name, mime)
                }
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

    @Synchronized
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
        val treeFolder = tree?.let { checkNotNull(createOrFindTreeDirectoryPath(context, it, folderName)) { "The configured output folder is unavailable. Choose a writable folder and retry." } }
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

        // Read history before copying anything. Unreadable/corrupt existing evidence
        // must not be interpreted as a new, empty protocol run.
        val prior = readProtocolResultJson(context, treeFolder, folderName)
        val plan = ExportAttachmentProjection.plan(fields, ::uniqueAttachmentName)
        val jsonFields = plan.fields

        plan.attachments
            .forEach { attachment ->
                val field = attachment.field
                val source = attachment.source
                val name = attachment.filename
                val mime = mimeTypeFor(name)
                val written = if (treeFolder != null) {
                    copyToTreeFolder(context, treeFolder, source, name, mime)
                } else {
                    copyToDefault(context, folderName, source, name, mime)
                }
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

        val existing = prior ?: JSONObject().apply {
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
        val tree = configuredFolder(context).takeIf { it.isNotBlank() }?.let(Uri::parse)
        val treeFolder = tree?.let { checkNotNull(createTreeDirectory(context, it, folderName)) { "The configured output folder is unavailable. Choose a writable folder and retry." } }
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

        val plan = ExportAttachmentProjection.plan(fields, ::uniqueAttachmentName)
        val jsonFields = plan.fields

        fun addAttachment(field: String, source: String, name: String) {
            val mime = mimeTypeFor(name)
            val written = if (treeFolder != null) {
                copyToTreeFolder(context, treeFolder, source, name, mime)
            } else {
                copyToDefault(context, folderName, source, name, mime)
            }
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
        plan.attachments.forEach { attachment -> addAttachment(attachment.field, attachment.source, attachment.filename) }
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
        val files = buildList {
            exportPackage.attachments.forEach { add(ResultShare.Attachment(it.filename, Uri.parse(it.uri))) }
            add(ResultShare.Attachment(exportPackage.json.filename, Uri.parse(exportPackage.json.uri)))
            exportPackage.manifest?.let { add(ResultShare.Attachment(it.filename, Uri.parse(it.uri))) }
        }
        ResultShare.share(
            context = context,
            chooserTitle = "Share MethodMesh export",
            text = exportPackageHumanText(context, exportPackage),
            attachments = files
        )
    }

    private fun exportPackageHumanText(context: Context, exportPackage: ExportPackage): String = runCatching {
        context.contentResolver.openInputStream(Uri.parse(exportPackage.json.uri))?.bufferedReader()?.use { reader ->
            val json = JSONObject(reader.readText())
            val methodId = json.optString("methodmesh_method_id")
            val fields = json
                .optJSONArray("steps")
                ?.optJSONObject(0)
                ?.optJSONObject("fields")
            if (fields == null) return@use "MethodMesh export\n${exportPackage.summary}"
            if (methodId == "paper.form.transcribe") {
                return@use paperBridgeTranscriptionText(fields).ifBlank { exportPackage.summary }
            }
            buildString {
                append("MethodMesh export\n")
                for (key in fields.keys()) {
                    if (isHumanExportField(key, fields.optString(key))) {
                        append(key).append(": ").append(fields.opt(key)).append('\n')
                    }
                }
            }.trim()
        }
    }.getOrNull().orEmpty().ifBlank { "MethodMesh export\n${exportPackage.summary}" }

    private fun paperBridgeTranscriptionText(fields: JSONObject): String {
        val values = runCatching { JSONObject(fields.optString("paper_values_json")) }.getOrNull()
        if (values != null && values.length() > 0) {
            return buildString {
                for (key in values.keys()) {
                    val value = values.optString(key)
                    if (isHumanExportField(key, value)) {
                        append(key).append(": ").append(value).append('\n')
                    }
                }
            }.trim()
        }
        return buildString {
            for (key in fields.keys()) {
                val value = fields.optString(key)
                if (key.startsWith("paper_").not() && isHumanExportField(key, value)) {
                    append(key).append(": ").append(value).append('\n')
                }
            }
        }.trim()
    }

    private fun isHumanExportField(key: String, value: String): Boolean {
        if (value.isBlank()) return false
        if (ResultShare.isLikelyMediaFieldName(key) || ResultShare.isShareableMediaField(key, value)) return false
        if (key.startsWith("methodmesh_") || key.startsWith("diagnostic_")) return false
        if (key in setOf("subject_id", "context_entity_id", "visit_id", "form_id", "operator_id")) return false
        if (key.endsWith("_json") || key.endsWith("_payload")) return false
        if (key.endsWith("_sha256") || key.contains("sha", ignoreCase = true) || key.contains("hash", ignoreCase = true)) return false
        if (key.endsWith("_count") || key.contains("count", ignoreCase = true)) return false
        if (key.contains("audit", ignoreCase = true) || key.contains("metadata", ignoreCase = true)) return false
        if (key.contains("status", ignoreCase = true) || key.endsWith("_error")) return false
        if (key.endsWith("_time_iso") || key.endsWith("_at_iso")) return false
        if (key.endsWith("_id") && key !in setOf("participant_id", "specimen_id", "sample_id")) return false
        return true
    }

    fun shareMedia(context: Context, exportPackage: ExportPackage) {
        val media = exportPackage.attachments.filterNot { it.mimeType == "application/json" || it.mimeType == "text/plain" }
        if (media.isEmpty()) throw IllegalStateException("No media attachments are available to share.")
        ResultShare.share(
            context = context,
            chooserTitle = "Share MethodMesh media",
            text = "MethodMesh media export: ${exportPackage.summary}",
            attachments = media.map { ResultShare.Attachment(it.filename, Uri.parse(it.uri)) }
        )
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

    private fun sourceDisplayName(context: Context, source: String): String? {
        if (!source.startsWith("content://")) {
            return source.substringAfterLast('/').substringBefore('?').takeIf { it.isNotBlank() }
        }
        return runCatching {
            context.contentResolver.query(
                Uri.parse(source),
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index < 0) null else cursor.getString(index)
            }
        }.getOrNull()
    }

    private fun extension(source: String): String = Uri.parse(source).lastPathSegment?.substringAfterLast('.', "bin")?.takeIf { it.length <= 8 } ?: "bin"

    private fun copyToDefault(context: Context, folderName: String, source: String, name: String, mime: String): String {
        return requireNotNull(openSource(context, source)) { "Could not read selected attachment: $name" }.use { input ->
            checkNotNull(writeDefault(context, folderName, name, mime) { input.copyTo(it) }) { "Could not save selected attachment: $name" }
        }
    }

    private fun copyToTreeFolder(context: Context, folder: Uri, source: String, name: String, mime: String): String {
        return requireNotNull(openSource(context, source)) { "Could not read selected attachment: $name" }.use { input ->
            checkNotNull(writeTreeDocument(context, folder, name, mime) { input.copyTo(it) }) { "Could not save selected attachment: $name" }
        }
    }

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

    private fun readProtocolResultJson(context: Context, treeFolder: Uri?, folderName: String): JSONObject? {
        val uri = if (treeFolder != null) {
            findTreeChild(context, treeFolder, safeSegment("${folderName.substringAfterLast('/')}_protocol_result") + ".json", directory = false)
                ?: findTreeChild(context, treeFolder, "protocol_result.json", directory = false)
        } else {
            findDefaultDocument(context, folderName, safeSegment("${folderName.substringAfterLast('/')}_protocol_result") + ".json")
                ?: findDefaultDocument(context, folderName, "protocol_result.json")
        } ?: return null
        return requireNotNull(context.contentResolver.openInputStream(uri)) { "Existing protocol export could not be read." }.bufferedReader().use { reader ->
            JSONObject(reader.readText()).also { json ->
                val steps = requireNotNull(json.optJSONArray("steps")) { "Existing protocol export has no valid step history." }
                for (index in 0 until steps.length()) steps.getJSONObject(index)
            }
        }
    }

    private fun createOrFindTreeDirectoryPath(context: Context, tree: Uri, path: String): Uri? = runCatching {
        var parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        path.split('/').filter { it.isNotBlank() }.forEach { segment ->
            parent = findTreeChild(context, parent, segment, directory = true)
                ?: DocumentsContract.createDocument(context.contentResolver, parent, DocumentsContract.Document.MIME_TYPE_DIR, segment)
                ?: return@runCatching null
        }
        parent
    }.getOrNull()

    private fun findTreeChild(context: Context, parent: Uri, name: String, directory: Boolean): Uri? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parent, DocumentsContract.getDocumentId(parent))
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        requireNotNull(context.contentResolver.query(childrenUri, projection, null, null, null)) { "Output folder could not be read." }.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                val display = cursor.getString(nameIndex)
                val mime = cursor.getString(mimeIndex)
                if (display == name && (!directory || mime == DocumentsContract.Document.MIME_TYPE_DIR)) {
                    return DocumentsContract.buildDocumentUriUsingTree(parent, cursor.getString(idIndex))
                }
            }
        }
        return null
    }

    private fun writeTreeDocumentReplace(context: Context, parent: Uri, name: String, mime: String, bytes: ByteArray): String? {
        val existing = findTreeChild(context, parent, name, directory = false)
            ?: return writeTreeDocument(context, parent, name, mime, bytes)
        return replaceProtocolDocument(context, existing, bytes, backup = { previous ->
            checkNotNull(writeTreeDocument(context, parent, "${name.removeSuffix(".json")}_recovery_${UUID.randomUUID()}.json", mime, previous)) {
                "Could not back up the previous protocol history; it was not overwritten."
            }
        }, removeBackup = { DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(it)) })
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
        requireNotNull(context.contentResolver.query(MediaStore.Files.getContentUri("external"), projection, selection, args, null)) { "Existing protocol export folder could not be read." }.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID))
                return Uri.withAppendedPath(MediaStore.Files.getContentUri("external"), id.toString())
            }
        }
        return null
    }

    private fun writeDefaultReplace(context: Context, folderName: String, name: String, mime: String, bytes: ByteArray): String? {
        val existing = findDefaultDocument(context, folderName, name)
            ?: return writeDefault(context, folderName, name, mime, bytes)
        return replaceProtocolDocument(context, existing, bytes, backup = { previous ->
            checkNotNull(writeDefault(context, folderName, "${name.removeSuffix(".json")}_recovery_${UUID.randomUUID()}.json", mime, previous)) {
                "Could not back up the previous protocol history; it was not overwritten."
            }
        }, removeBackup = { raw ->
            if (raw.startsWith("content://")) context.contentResolver.delete(Uri.parse(raw), null, null)
            else File(raw).delete()
        })
    }

    private fun replaceProtocolDocument(context: Context, existing: Uri, bytes: ByteArray,
                                        backup: (ByteArray) -> String, removeBackup: (String) -> Unit): String {
        val previous = requireNotNull(context.contentResolver.openInputStream(existing)) { "Previous protocol history could not be read." }.use { it.readBytes() }
        fun read(uri: Uri) = requireNotNull(context.contentResolver.openInputStream(uri)) { "Protocol document could not be read back." }.use { it.readBytes() }
        RecoverableExportReplace.write(previous, bytes, backup = {
            val recovery = backup(previous)
            val readable = if (recovery.startsWith("content://")) read(Uri.parse(recovery)) else File(recovery).readBytes()
            check(previous.contentEquals(readable)) { "Protocol recovery copy could not be verified; previous history was not overwritten." }
            recovery
        }, overwrite = { contents ->
            requireNotNull(context.contentResolver.openOutputStream(existing, "rwt")) { "Protocol document could not be opened for replacement." }
                .use { it.write(contents) }
            check(contents.contentEquals(read(existing))) { "Protocol replacement could not be verified." }
        }, removeBackup = removeBackup)
        return existing.toString()
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

    private fun writeTreeDocument(context: Context, parent: Uri, name: String, mime: String, bytes: ByteArray): String? =
        writeTreeDocument(context, parent, name, mime) { it.write(bytes) }

    private fun writeTreeDocument(context: Context, parent: Uri, name: String, mime: String, contents: (OutputStream) -> Unit): String? {
        val uri = DocumentsContract.createDocument(context.contentResolver, parent, mime, name) ?: return null
        try {
            requireNotNull(context.contentResolver.openOutputStream(uri)) { "Output document could not be opened." }.use(contents)
            return uri.toString()
        } catch (failure: Exception) {
            runCatching { check(DocumentsContract.deleteDocument(context.contentResolver, uri)) }
                .onFailure { failure.addSuppressed(it) }
            throw failure
        }
    }

    private fun writeDefault(context: Context, folderName: String, name: String, mime: String, bytes: ByteArray): String? =
        writeDefault(context, folderName, name, mime) { it.write(bytes) }

    private fun writeDefault(context: Context, folderName: String, name: String, mime: String, contents: (OutputStream) -> Unit): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = android.content.ContentValues().apply {
                put(MediaStore.Files.FileColumns.DISPLAY_NAME, name)
                put(MediaStore.Files.FileColumns.MIME_TYPE, mime)
                put(MediaStore.Files.FileColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/MethodMesh/outputs/$folderName")
                put(MediaStore.Files.FileColumns.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Files.getContentUri("external"), values) ?: return null
            return runCatching {
                context.contentResolver.openOutputStream(uri)?.use(contents) ?: error("Unable to write output")
                val completed = android.content.ContentValues().apply { put(MediaStore.Files.FileColumns.IS_PENDING, 0) }
                check(context.contentResolver.update(uri, completed, null, null) > 0) { "Output could not be published." }
                uri.toString()
            }.getOrElse {
                context.contentResolver.delete(uri, null, null)
                throw it
            }
        }
        val out = File(defaultFolder(context), folderName).apply { mkdirs() }.resolve(name)
        out.outputStream().use(contents)
        return out.absolutePath
    }

    private fun writePublicDownload(context: Context, folderName: String, name: String, mime: String, contents: (OutputStream) -> Unit): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = android.content.ContentValues().apply {
                put(MediaStore.Files.FileColumns.DISPLAY_NAME, name)
                put(MediaStore.Files.FileColumns.MIME_TYPE, mime)
                put(MediaStore.Files.FileColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MethodMesh/$folderName")
                put(MediaStore.Files.FileColumns.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Files.getContentUri("external"), values) ?: return null
            return runCatching {
                context.contentResolver.openOutputStream(uri)?.use(contents) ?: error("Unable to write download")
                val completed = android.content.ContentValues().apply { put(MediaStore.Files.FileColumns.IS_PENDING, 0) }
                check(context.contentResolver.update(uri, completed, null, null) > 0) { "Unable to finalise download" }
                uri.toString()
            }.getOrElse {
                runCatching {
                    check(context.contentResolver.delete(uri, null, null) > 0) { "Could not remove incomplete download." }
                }.onFailure { cleanup -> it.addSuppressed(DownloadCleanupFailure(cleanup)) }
                throw it
            }
        }
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val out = File(downloads, "MethodMesh/$folderName").apply { mkdirs() }.resolve(name)
        try {
            out.outputStream().use(contents)
        } catch (failure: Exception) {
            if (out.exists() && !out.delete()) failure.addSuppressed(DownloadCleanupFailure())
            throw failure
        }
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
        "yaml", "yml" -> "application/yaml"
        "zip" -> "application/zip"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "pdf" -> "application/pdf"
        "txt" -> "text/plain"
        "csv" -> "text/csv"
        else -> "application/octet-stream"
    }
}
