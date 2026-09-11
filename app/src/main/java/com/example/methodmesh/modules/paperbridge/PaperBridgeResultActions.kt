package com.example.methodmesh.modules.paperbridge

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.methodmesh.core.artifacts.AndroidArtifacts
import com.example.methodmesh.core.artifacts.ArtifactRef
import java.io.ByteArrayInputStream
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

internal data class PaperBridgeShareAttachment(
    val name: String,
    val uri: String
)

internal data class PaperBridgeFilesSaveSummary(
    val collectionId: String,
    val fileCount: Int,
    val collectionLabel: String
)

/**
 * Native post-Commit result actions for Paper Bridge.
 *
 * Commit itself remains non-persistent. Share uses caller-readable content URIs;
 * Save copies the frozen result into the shared MethodMesh Files/ArtifactStore.
 */
internal object PaperBridgeResultActions {
    private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneOffset.UTC)

    fun share(
        context: Context,
        chooserTitle: String,
        text: String,
        attachments: List<PaperBridgeShareAttachment>,
        jsonText: String = ""
    ) {
        val unique = attachments
            .filter { it.uri.startsWith("content://") }
            .distinctBy { it.uri }
            .toMutableList()
        val mediaAttachmentCount = unique.size

        // Leave the existing optional provenance attachment path in place.
        // This v0.5.5 change is specifically about making beef + media reliable.
        if (jsonText.isNotBlank()) {
            unique += PaperBridgeShareAttachment(
                name = "metadata.json",
                uri = temporaryJsonShareUri(context, jsonText).toString()
            )
        }

        val shareable = ArrayList(unique.map { Uri.parse(it.uri) })
        if (shareable.isEmpty() && text.isBlank()) {
            throw IllegalStateException("No shareable result.")
        }

        /*
         * Use the Magnifier/shared-scaffold payload shape for the actual share:
         * scalar EXTRA_TEXT plus EXTRA_STREAM / EXTRA_STREAM ArrayList.
         *
         * Paper Bridge additionally has several FileProvider/content URIs, so
         * propagate their read grants through ClipData and explicit package grants.
         * ClipData carries URI items ONLY; putting the beef in ClipData as well was
         * the source of the earlier duplicated message.
         */
        val intent = when (shareable.size) {
            0 -> Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                if (text.isNotBlank()) putExtra(Intent.EXTRA_TEXT, text)
            }

            1 -> Intent(Intent.ACTION_SEND).apply {
                // A human transcription with only a JSON sidecar remains a
                // text share. Sending it as application/json makes several
                // receivers turn the transcription itself into a document.
                type = if (mediaAttachmentCount == 0 && jsonText.isNotBlank() && text.isNotBlank()) {
                    "text/plain"
                } else {
                    mimeFor(context, shareable.first(), unique.first().name)
                }
                putExtra(Intent.EXTRA_STREAM, shareable.first())
                if (text.isNotBlank()) putExtra(Intent.EXTRA_TEXT, text)
            }

            else -> Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, shareable)
                if (text.isNotBlank()) putExtra(Intent.EXTRA_TEXT, text)
            }
        }

        if (shareable.isNotEmpty()) {
            val clip = ClipData.newUri(
                context.contentResolver,
                unique.first().name,
                shareable.first()
            ).also { data ->
                shareable.drop(1).forEach { uri ->
                    data.addItem(ClipData.Item(uri))
                }
            }
            intent.clipData = clip
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            // Some chooser targets do not reliably inherit grants for every item in
            // ACTION_SEND_MULTIPLE. Grant each URI explicitly as a compatibility
            // backstop. This does not add another copy of the beef.
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

        context.startActivity(
            Intent.createChooser(intent, chooserTitle)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun saveToFiles(
        context: Context,
        collectionLabel: String,
        textFileName: String,
        text: String,
        attachments: List<PaperBridgeShareAttachment>,
        jsonText: String = "",
        entryId: String? = null
    ): PaperBridgeFilesSaveSummary {
        val service = AndroidArtifacts.service(context)
        val collectionId = "paperbridge-${UUID.randomUUID()}"
        val stem = "Paper Bridge/${stamp.format(Instant.now())}_${safeSegment(collectionLabel).ifBlank { "result" }}"
        val created = mutableListOf<ArtifactRef>()

        fun createText(name: String, mime: String, value: String) {
            if (value.isBlank()) return
            created += service.createPersistent(
                name = "$stem/${safeFileName(name)}",
                mime = mime,
                input = ByteArrayInputStream(value.toByteArray(Charsets.UTF_8)),
                collectionId = collectionId,
                entryId = entryId
            )
        }

        try {
            createText(textFileName, "text/plain", text)
            createText("metadata.json", "application/json", jsonText)

            attachments
                .filter { it.uri.startsWith("content://") }
                .distinctBy { it.uri }
                .forEach { attachment ->
                    val uri = Uri.parse(attachment.uri)
                    val input = context.contentResolver.openInputStream(uri)
                        ?: throw IllegalStateException("Could not open ${attachment.name} for saving.")
                    input.use {
                        created += service.createPersistent(
                            name = "$stem/${safeFileName(attachment.name)}",
                            mime = mimeFor(context, uri, attachment.name),
                            input = it,
                            collectionId = collectionId,
                            entryId = entryId,
                            mediaId = UUID.randomUUID().toString()
                        )
                    }
                }

            if (created.isEmpty()) throw IllegalStateException("No result files were available to save.")
            return PaperBridgeFilesSaveSummary(collectionId, created.size, stem)
        } catch (error: Throwable) {
            created.asReversed().forEach { ref -> runCatching { service.delete(ref) } }
            throw error
        }
    }

    private fun temporaryJsonShareUri(context: Context, jsonText: String): Uri {
        val folder = File(context.cacheDir, "paperbridge_share").apply { mkdirs() }
        val file = File(folder, "metadata_${System.currentTimeMillis()}.json")
        file.writeText(jsonText, Charsets.UTF_8)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun mimeFor(context: Context, uri: Uri, name: String): String =
        context.contentResolver.getType(uri)?.takeIf { it.isNotBlank() }
            ?: when (name.substringAfterLast('.', "").lowercase()) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "webp" -> "image/webp"
                "json" -> "application/json"
                "yaml", "yml" -> "application/yaml"
                "zip" -> "application/zip"
                "pdf" -> "application/pdf"
                "txt" -> "text/plain"
                "csv" -> "text/csv"
                else -> "application/octet-stream"
            }

    private fun safeSegment(raw: String): String = raw
        .trim()
        .replace(Regex("[^A-Za-z0-9._-]+"), "_")
        .trim('_')
        .take(80)

    private fun safeFileName(raw: String): String {
        val cleaned = raw.substringAfterLast('/').trim().replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_')
        return cleaned.ifBlank { "file" }.take(120)
    }
}
