package com.example.methodmesh.modules.paperbridge

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.content.FileProvider
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal data class PaperDesignExport(
    val templateJsonUri: String,
    val templateYamlUri: String,
    val bundleUri: String,
    val templateImageUri: String,
    val preparedFormUri: String?,
    val markupImageUri: String,
    val templateJsonPath: String,
    val templateYamlPath: String,
    val bundlePath: String,
    val templateImagePath: String,
    val preparedFormPath: String?,
    val markupImagePath: String
)

/**
 * Durable, portable authoring artefacts for a saved Paper Bridge form.
 *
 * The bundle intentionally contains no completed questionnaire data. It can be
 * copied to another device and restored without ODK or the original XLSX.
 */
internal object PaperDesignExportStore {
    fun save(context: Context, template: PaperTemplate, sourceBitmap: Bitmap): PaperDesignExport {
        val safeName = "${safeFilePart(template.templateId)}-v${safeFilePart(template.version)}"
        val dir = File(context.filesDir, "paperbridge/design_exports").apply { mkdirs() }
        val jsonFile = File(dir, "$safeName.paperbridge.json")
        val yamlFile = File(dir, "$safeName.paperbridge.yaml")
        val templateImageFile = File(dir, "$safeName.template.png")
        val preparedFormFile = File(dir, "$safeName.prepared-form.png")
        val imageFile = File(dir, "$safeName.mapping.png")
        val bundleFile = File(dir, "$safeName.paperbridge.zip")

        jsonFile.writeText(template.rawJson, Charsets.UTF_8)
        yamlFile.writeText(renderYamlMirror(template), Charsets.UTF_8)
        templateImageFile.outputStream().use { output ->
            check(sourceBitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Could not encode canonical template image." }
        }
        if (template.registration.type == PaperRegistrationType.QR4 || template.registration.type == PaperRegistrationType.APRILTAG8) {
            val prepared = when (template.registration.type) {
                PaperRegistrationType.APRILTAG8 -> PaperAprilTagFiducial.preparePage(sourceBitmap, template.registration)
                PaperRegistrationType.QR4 -> PaperQrFiducial.preparePage(sourceBitmap, template.registration)
                PaperRegistrationType.BULLSEYE4 -> sourceBitmap
            }
            try {
                preparedFormFile.outputStream().use { output ->
                    check(prepared.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Could not encode prepared production form." }
                }
            } finally {
                if (prepared !== sourceBitmap && !prepared.isRecycled) prepared.recycle()
            }
        } else if (preparedFormFile.exists()) {
            preparedFormFile.delete()
        }
        val annotated = renderAnnotated(sourceBitmap, template)
        try {
            imageFile.outputStream().use { output ->
                check(annotated.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Could not encode marked-up design image." }
            }
        } finally {
            if (annotated !== sourceBitmap && !annotated.isRecycled) annotated.recycle()
        }
        ZipOutputStream(bundleFile.outputStream().buffered()).use { zip ->
            addFile(zip, "template.paperbridge.json", jsonFile)
            addFile(zip, "template.paperbridge.yaml", yamlFile)
            addFile(zip, "template.png", templateImageFile)
            if (preparedFormFile.exists()) addFile(zip, "prepared_form.png", preparedFormFile)
            addFile(zip, "mapping.png", imageFile)
        }

        val authority = "${context.packageName}.fileprovider"
        fun uri(file: File) = FileProvider.getUriForFile(context, authority, file).toString()
        return PaperDesignExport(
            templateJsonUri = uri(jsonFile),
            templateYamlUri = uri(yamlFile),
            bundleUri = uri(bundleFile),
            templateImageUri = uri(templateImageFile),
            preparedFormUri = preparedFormFile.takeIf { it.exists() }?.let(::uri),
            markupImageUri = uri(imageFile),
            templateJsonPath = jsonFile.absolutePath,
            templateYamlPath = yamlFile.absolutePath,
            bundlePath = bundleFile.absolutePath,
            templateImagePath = templateImageFile.absolutePath,
            preparedFormPath = preparedFormFile.takeIf { it.exists() }?.absolutePath,
            markupImagePath = imageFile.absolutePath
        )
    }

    /** YAML is deliberately a fixed Paper Bridge profile, not arbitrary YAML. */
    private fun renderYamlMirror(template: PaperTemplate): String = buildString {
        appendLine("schema: methodmesh.paper.bundle.v1")
        appendLine("template_id: ${yamlScalar(template.templateId)}")
        appendLine("version: ${yamlScalar(template.version)}")
        appendLine("title: ${yamlScalar(template.title)}")
        appendLine("coordinate_frame: ${if (template.registration.type == PaperRegistrationType.APRILTAG8) "canonical_page" else "anchor_centres"}")
        appendLine("registration_type: ${template.registration.type.name.lowercase()}")
        if (template.registration.type != PaperRegistrationType.BULLSEYE4 && template.registration.schemaKey.isNotBlank()) appendLine("registration_schema_key: ${yamlScalar(template.registration.schemaKey)}")
        if (template.registration.type == PaperRegistrationType.APRILTAG8) appendLine("registration_family: ${PaperAprilTagFiducial.FAMILY}")
        appendLine("canonical_width_px: ${template.pageWidthPx}")
        appendLine("canonical_height_px: ${template.pageHeightPx}")
        appendLine("# Canonical restore payload. Paper Bridge imports this literal block.")
        appendLine("manifest_json: |")
        template.rawJson.lines().forEach { append("  ").appendLine(it) }
    }

    private fun yamlScalar(value: String): String = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

    private fun addFile(zip: ZipOutputStream, name: String, file: File) {
        zip.putNextEntry(ZipEntry(name))
        file.inputStream().buffered().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private fun renderAnnotated(source: Bitmap, template: PaperTemplate): Bitmap {
        val output = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        val strokeWidth = (output.width / 360f).coerceIn(3f, 10f)
        val textSize = (output.width / 43f).coerceIn(22f, 54f)
        val pad = (textSize * 0.28f).coerceAtLeast(5f)
        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0, 105, 92)
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            this.textSize = textSize
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
        }
        val labelBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(225, 18, 48, 43)
            style = Paint.Style.FILL
        }

        fun drawRegion(roi: NormalisedRoi, label: String) {
            val rect = RectF(roi.left * output.width, roi.top * output.height, roi.right * output.width, roi.bottom * output.height)
            canvas.drawRect(rect, boxPaint)
            val shown = label.take(48)
            val width = labelPaint.measureText(shown)
            val labelTop = (rect.top - textSize - 2f * pad).coerceAtLeast(0f)
            val labelRight = (rect.left + width + 2f * pad).coerceAtMost(output.width.toFloat())
            canvas.drawRect(rect.left, labelTop, labelRight, labelTop + textSize + 2f * pad, labelBg)
            canvas.drawText(shown, rect.left + pad, labelTop + textSize + pad * 0.55f, labelPaint)
        }

        template.fields.forEach { field ->
            when (field.type) {
                PaperFieldType.OMR_SINGLE, PaperFieldType.OMR_MULTIPLE -> field.options.forEach { option -> drawRegion(option.roi, "${field.name} = ${option.value}") }
                else -> field.roi?.let { drawRegion(it, field.name) }
            }
        }
        return output
    }

    private fun safeFilePart(value: String): String = value.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').ifBlank { "paper-form" }
}
