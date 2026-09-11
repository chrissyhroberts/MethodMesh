package com.example.methodmesh.modules.paperbridge

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal data class PaperResultArchive(
    val uri: Uri,
    val fileName: String
)

internal object PaperBridgeFiles {
    private val archiveStamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneOffset.UTC)

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

    /**
     * Build the portable standalone transcription artifact. ODK callers do not need this
     * archive because they receive the named values/media directly through Android result
     * transport. The archive remains available as a portable helper; native post-Commit
     * Save writes the frozen result into the shared MethodMesh Files/ArtifactStore.
     */
    fun createResultArchive(
        context: Context,
        template: PaperTemplate,
        stableValues: Map<String, String>,
        results: List<PaperFieldResult>
    ): PaperResultArchive {
        val stem = safeFileSegment(template.title).ifBlank { safeFileSegment(template.templateId).ifBlank { "paperbridge" } }
        val fileName = "${archiveStamp.format(Instant.now())}_${stem}_paperbridge.zip"
        val file = File(context.cacheDir, fileName)

        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            fun textEntry(name: String, value: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(value.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            fun uriEntry(name: String, uriText: String) {
                if (uriText.isBlank()) return
                val uri = Uri.parse(uriText)
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalArgumentException("Could not open attachment '$name'.")
                zip.putNextEntry(ZipEntry(name))
                input.use { it.copyTo(zip) }
                zip.closeEntry()
            }

            val portableValues = JSONObject()
            results.forEach { result ->
                if (result.spec.type == PaperFieldType.IMAGE && result.finalValue.isNotBlank()) {
                    portableValues.put(result.spec.name, "attachments/field_${safeFileSegment(result.spec.name)}.jpg")
                } else {
                    portableValues.put(result.spec.name, result.finalValue)
                }
            }

            val resultJson = JSONObject().apply {
                put("schema", "methodmesh.paper.result.v1")
                put("template_id", template.templateId)
                put("template_version", template.version)
                put("template_title", template.title)
                put("scan_time_iso", stableValues[PaperBridgeFields.SCAN_TIME_ISO].orEmpty())
                put("values", portableValues)
                put("source_image", "attachments/paper_source.jpg")
                put("rectified_image", "attachments/paper_rectified.jpg")
                put("template_sha256", stableValues[PaperBridgeFields.TEMPLATE_SHA256].orEmpty())
                put("source_sha256", stableValues[PaperBridgeFields.SOURCE_SHA256].orEmpty())
                put("rectified_sha256", stableValues[PaperBridgeFields.RECTIFIED_SHA256].orEmpty())
            }
            textEntry("result.json", resultJson.toString(2))
            textEntry("schema.json", template.rawJson)
            textEntry("audit.json", stableValues[PaperBridgeFields.EXTRACTION_AUDIT_JSON].orEmpty())
            textEntry("attachment_metadata.json", stableValues[PaperBridgeFields.ATTACHMENT_METADATA_JSON].orEmpty())

            val csv = buildString {
                append("field,label,type,value\n")
                results.forEach { result ->
                    val value = if (result.spec.type == PaperFieldType.IMAGE && result.finalValue.isNotBlank()) {
                        "attachments/field_${safeFileSegment(result.spec.name)}.jpg"
                    } else result.finalValue
                    append(csvCell(result.spec.name)).append(',')
                    append(csvCell(result.spec.label)).append(',')
                    append(csvCell(result.spec.type.name.lowercase())).append(',')
                    append(csvCell(value)).append('\n')
                }
            }
            textEntry("values.csv", csv)

            uriEntry("attachments/paper_source.jpg", stableValues[PaperBridgeFields.SOURCE_IMAGE].orEmpty())
            uriEntry("attachments/paper_rectified.jpg", stableValues[PaperBridgeFields.RECTIFIED_IMAGE].orEmpty())
            results.filter { it.spec.type == PaperFieldType.IMAGE && it.finalValue.isNotBlank() }.forEach { result ->
                uriEntry("attachments/field_${safeFileSegment(result.spec.name)}.jpg", result.finalValue)
            }
        }

        return PaperResultArchive(
            uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file),
            fileName = fileName
        )
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

    private fun safeFileSegment(raw: String): String = raw
        .trim()
        .replace(Regex("[^A-Za-z0-9._-]+"), "_")
        .trim('_')
        .take(80)

    private fun csvCell(raw: String): String = "\"${raw.replace("\"", "\"\"")}\""
}
