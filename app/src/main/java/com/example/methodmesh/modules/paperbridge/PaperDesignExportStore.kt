package com.example.methodmesh.modules.paperbridge

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.content.FileProvider
import java.io.File

internal data class PaperDesignExport(
    val templateJsonUri: String,
    val markupImageUri: String,
    val templateJsonPath: String,
    val markupImagePath: String
)

/**
 * Durable design artefacts for a saved Paper Bridge form.
 *
 * These contain only the blank source geometry and field mappings: never a
 * completed questionnaire or participant response. They are safe to keep with
 * the saved form definition and to return from paper.form.design.
 */
internal object PaperDesignExportStore {
    fun save(
        context: Context,
        template: PaperTemplate,
        sourceBitmap: Bitmap
    ): PaperDesignExport {
        val safeName = "${safeFilePart(template.templateId)}-v${safeFilePart(template.version)}"
        val dir = File(context.filesDir, "paperbridge/design_exports").apply { mkdirs() }
        val jsonFile = File(dir, "$safeName.paperbridge.json")
        val imageFile = File(dir, "$safeName.mapping.png")

        jsonFile.writeText(template.rawJson, Charsets.UTF_8)
        val annotated = renderAnnotated(sourceBitmap, template)
        try {
            imageFile.outputStream().use { output ->
                check(annotated.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    "Could not encode marked-up design image."
                }
            }
        } finally {
            if (annotated !== sourceBitmap && !annotated.isRecycled) annotated.recycle()
        }

        val authority = "${context.packageName}.fileprovider"
        val jsonUri = FileProvider.getUriForFile(context, authority, jsonFile).toString()
        val imageUri = FileProvider.getUriForFile(context, authority, imageFile).toString()
        return PaperDesignExport(
            templateJsonUri = jsonUri,
            markupImageUri = imageUri,
            templateJsonPath = jsonFile.absolutePath,
            markupImagePath = imageFile.absolutePath
        )
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
            val rect = RectF(
                roi.left * output.width,
                roi.top * output.height,
                roi.right * output.width,
                roi.bottom * output.height
            )
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
                PaperFieldType.OMR_SINGLE, PaperFieldType.OMR_MULTIPLE -> field.options.forEach { option ->
                    drawRegion(option.roi, "${field.name} = ${option.value}")
                }
                else -> field.roi?.let { drawRegion(it, field.name) }
            }
        }
        return output
    }

    private fun safeFilePart(value: String): String = value
        .trim()
        .replace(Regex("[^A-Za-z0-9._-]+"), "_")
        .trim('_')
        .ifBlank { "paper-form" }
}
