package com.example.methodmesh.modules.paperbridge

import android.content.Context
import android.net.Uri
import com.example.methodmesh.core.artifacts.AndroidArtifacts
import com.example.methodmesh.core.artifacts.ArtifactRef
import java.io.ByteArrayInputStream
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
 * Commit itself remains non-persistent. Share is handled by the canonical
 * MethodMesh output package exporter; Save copies the frozen result into the
 * shared MethodMesh Files/ArtifactStore.
 */
internal object PaperBridgeResultActions {
    private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneOffset.UTC)

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
