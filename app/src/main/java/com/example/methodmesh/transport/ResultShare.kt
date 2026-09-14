package com.example.methodmesh.transport

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONArray
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Canonical native Share transport.
 *
 * Share is file-oriented: human-readable payloads are materialised as a text
 * attachment, real media/file artefacts remain typed URI streams, and optional
 * FULL JSON is materialised as a JSON attachment. The UI already supports
 * tap-to-copy for text; native Share should therefore hand receivers the files
 * that form the result bundle instead of sending clipboard-style message text.
 *
 * File-oriented persistence still belongs to OutputExportRepository.saveToDownloads(),
 * which materialises result.txt, media/files and optional metadata.json.
 */
object ResultShare {
    private val fileStamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS'Z'").withZone(ZoneOffset.UTC)

    /** Stable human-facing filename stem for a single native Share/Save operation. */
    fun coherentFileStem(label: String, instant: Instant = Instant.now()): String {
        val token = label
            .removePrefix("Share ")
            .removePrefix("share ")
            .replace(Regex("(?i)^methodmesh[ _-]*"), "")
            .trim()
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .take(72)
            .ifBlank { "result" }
        return "methodmesh_${token}_${fileStamp.format(instant)}"
    }

    data class Attachment(
        val name: String,
        val uri: Uri
    )

    private data class ShareItem(
        val name: String,
        val uri: Uri,
        val mime: String
    )

    fun share(
        context: Context,
        chooserTitle: String,
        text: String,
        attachments: List<Attachment>,
        jsonText: String = "",
        jsonFileName: String = "metadata.json",
        textFileName: String = "",
        fileLabel: String = chooserTitle
    ) {
        val textSidecar = text
            .takeIf { it.isNotBlank() }
            ?.let { payload ->
                val name = textFileName.ifBlank { "${coherentFileStem(fileLabel)}_result.txt" }
                ShareItem(
                    name = name,
                    uri = temporaryTextShareUri(context, name, payload),
                    mime = "text/plain"
                )
            }
        val jsonSidecar = jsonText
            .takeIf { it.isNotBlank() }
            ?.let { payload ->
                val name = jsonFileName.ifBlank { "metadata.json" }
                ShareItem(
                    name = name,
                    uri = temporaryTextShareUri(context, name, payload),
                    mime = "application/json"
                )
            }
        val mediaItems = attachments
            .map { attachment ->
                Attachment(
                    name = attachment.name.ifBlank { attachment.uri.lastPathSegment.orEmpty().ifBlank { "attachment" } },
                    uri = shareableUri(context, attachment.uri)
                )
            }
            .distinctBy { it.uri.toString() }
            .map { attachment ->
                ShareItem(
                    name = attachment.name,
                    uri = attachment.uri,
                    mime = mimeFor(context, attachment.uri, attachment.name)
                )
            }
        val items = ArrayList<ShareItem>().apply {
            textSidecar?.let { add(it) }
            addAll(mediaItems)
            jsonSidecar?.let { add(it) }
        }
        val shareable = ArrayList(items.map { it.uri })
        if (items.isEmpty()) throw IllegalStateException("No shareable result.")

        val intent = when (items.size) {
            1 -> Intent(Intent.ACTION_SEND).apply {
                type = items.first().mime
                putExtra(Intent.EXTRA_STREAM, shareable.first())
            }

            else -> Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = aggregateMime(items)
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, shareable)
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                putExtra(Intent.EXTRA_MIME_TYPES, items.map { it.mime }.distinct().toTypedArray())
            }
        }.apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (shareable.isNotEmpty()) {
                clipData = ClipData.newUri(context.contentResolver, "MethodMesh result", shareable.first()).apply {
                    shareable.drop(1).forEach { addItem(ClipData.Item(it)) }
                }
            }
        }

        if (shareable.isNotEmpty()) {
            context.packageManager
                .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                .forEach { info ->
                    shareable.forEach { uri ->
                        context.grantUriPermission(
                            info.activityInfo.packageName,
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                }
        }

        val chooser = Intent.createChooser(intent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (shareable.isNotEmpty()) clipData = intent.clipData
        }
        context.startActivity(chooser)
    }

    fun buildShareText(text: String, jsonText: String = ""): String = buildString {
        if (text.isNotBlank()) append(text.trimEnd())
        if (jsonText.isNotBlank()) {
            if (isNotEmpty()) append("\n\n")
            append("metadata.json\n")
            append(jsonText)
        }
    }

    fun isShareableMediaField(key: String, value: String): Boolean {
        if (value.startsWith("content://")) return true
        if (!(value.startsWith("file://") || value.startsWith("/"))) return false
        val lowerValue = value.lowercase()
        return isLikelyMediaFieldName(key) ||
            lowerValue.endsWith(".jpg") ||
            lowerValue.endsWith(".jpeg") ||
            lowerValue.endsWith(".png") ||
            lowerValue.endsWith(".webp") ||
            lowerValue.endsWith(".pdf")
    }

    fun isLikelyMediaFieldName(key: String): Boolean {
        val lowerKey = key.lowercase()
        return lowerKey.endsWith("_uri") ||
            lowerKey.endsWith("_image") ||
            lowerKey.endsWith("_photo") ||
            lowerKey.endsWith("_pdf") ||
            lowerKey.endsWith("_file") ||
            lowerKey.contains("image") ||
            lowerKey.contains("photo") ||
            lowerKey.contains("attachment") ||
            lowerKey.contains("media") ||
            lowerKey.contains("pdf")
    }

    fun attachmentName(key: String, value: String, fallbackExtension: String = "bin", index: Int? = null): String {
        val stem = key
            .substringBeforeLast("_uri")
            .removeSuffix("_uris")
            .removeSuffix("_json")
            .trim()
            .replace(Regex("[^A-Za-z0-9._-]+"), "_")
            .trim('_')
            .ifBlank { "attachment" }
        val extension = value
            .substringBefore('?', value)
            .substringBefore('#', value)
            .substringAfterLast('/', value)
            .substringAfterLast('.', "")
            .takeIf { it.isNotBlank() && it.length <= 8 }
            ?: fallbackExtension
        val suffix = index?.let { "_$it" }.orEmpty()
        return "$stem$suffix.$extension"
    }

    internal data class MediaAttachmentValue(
        val field: String,
        val name: String,
        val uri: String
    )

    internal fun shareableMediaAttachmentValues(fields: Map<String, Any?>): List<MediaAttachmentValue> {
        val attachments = mutableListOf<MediaAttachmentValue>()
        fields.forEach { (key, value) ->
            val raw = value?.toString()?.trim().orEmpty()
            if (raw.isBlank()) return@forEach
            if (isShareableMediaField(key, raw)) {
                attachments += MediaAttachmentValue(key, attachmentName(key, raw), raw)
                return@forEach
            }
            if (!isLikelyMediaFieldName(key)) return@forEach
            parseUriArray(raw).forEachIndexed { index, uri ->
                if (isShareableMediaField(key, uri)) {
                    attachments += MediaAttachmentValue(key, attachmentName(key, uri, index = index + 1), uri)
                }
            }
        }
        return attachments.distinctBy { it.uri }
    }

    fun shareableMediaAttachments(fields: Map<String, Any?>): List<Attachment> =
        shareableMediaAttachmentValues(fields).map { Attachment(it.name, Uri.parse(it.uri)) }

    fun shareableMediaUris(fields: Map<String, Any?>): List<Uri> =
        shareableMediaAttachmentValues(fields).map { Uri.parse(it.uri) }

    private fun parseUriArray(value: String): List<String> = runCatching {
        val array = JSONArray(value)
        (0 until array.length()).mapNotNull { index ->
            array.optString(index).trim().takeIf { it.isNotBlank() }
        }
    }.getOrDefault(emptyList())

    private fun temporaryTextShareUri(context: Context, fileName: String, text: String): Uri {
        val safeName = fileName
            .replace(Regex("[^A-Za-z0-9._-]+"), "_")
            .trim('_')
            .ifBlank { "${coherentFileStem("result")}_result.txt" }
        val folder = File(
            File(context.cacheDir, "methodmesh_share"),
            System.currentTimeMillis().toString()
        ).apply { mkdirs() }
        val file = File(folder, safeName)
        file.writeText(text, Charsets.UTF_8)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun shareableUri(context: Context, uri: Uri): Uri {
        val value = uri.toString()
        return when {
            value.startsWith("content://") -> uri
            value.startsWith("file://") -> File(value.removePrefix("file://")).let { file ->
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }
            else -> File(value).let { file ->
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }
        }
    }

    private fun mimeFor(context: Context, uri: Uri, name: String): String =
        context.contentResolver.getType(uri)?.takeIf { it.isNotBlank() }
            ?: when (name.substringAfterLast('.', "").lowercase()) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "webp" -> "image/webp"
                "gif" -> "image/gif"
                "mp4" -> "video/mp4"
                "webm" -> "video/webm"
                "mp3" -> "audio/mpeg"
                "wav" -> "audio/wav"
                "m4a" -> "audio/mp4"
                "ogg" -> "audio/ogg"
                "json" -> "application/json"
                "yaml", "yml" -> "application/yaml"
                "zip" -> "application/zip"
                "pdf" -> "application/pdf"
                "txt" -> "text/plain"
                "csv" -> "text/csv"
                else -> "application/octet-stream"
            }

    private fun aggregateMime(items: List<ShareItem>): String {
        if (items.isEmpty()) return "text/plain"
        if (items.size == 1) return items.first().mime
        val mimes = items.map { it.mime }
        return when {
            mimes.all { it.startsWith("image/") } -> "image/*"
            mimes.all { it.startsWith("video/") } -> "video/*"
            mimes.all { it.startsWith("audio/") } -> "audio/*"
            mimes.all { it == "application/pdf" } -> "application/pdf"
            mimes.all { it == "text/plain" } -> "text/plain"
            else -> "*/*"
        }
    }
}
