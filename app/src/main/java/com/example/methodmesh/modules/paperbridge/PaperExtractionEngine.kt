package com.example.methodmesh.modules.paperbridge

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs

internal data class PaperFieldResult(
    val spec: PaperFieldSpec,
    val candidate: String,
    val finalValue: String,
    val valid: Boolean,
    val reviewRequired: Boolean,
    val reviewed: Boolean,
    val autoAccepted: Boolean,
    val reason: String,
    val scores: Map<String, Float> = emptyMap(),
    val rawText: String = ""
) {
    val resolved: Boolean get() = valid && (!reviewRequired || reviewed)
}

internal data class PaperExtractionSession(
    val template: PaperTemplate,
    val sourceUri: Uri,
    val rectifiedUri: Uri,
    val rectifiedBitmap: Bitmap,
    val rectification: PaperRectification,
    val pageWhiteLuma: Float,
    val results: List<PaperFieldResult>,
    val scanTimeIso: String
)

internal data class PaperExtractionSettings(
    val autoAcceptOmr: Boolean,
    val autoAcceptOcr: Boolean
)

internal object PaperExtractionEngine {
    suspend fun extract(
        context: Context,
        sourceUri: Uri,
        template: PaperTemplate,
        settings: PaperExtractionSettings,
        manualQuarterTurns: Int,
        scanTimeIso: String
    ): PaperExtractionSession {
        val source = withContext(Dispatchers.Default) {
            PaperImageEngine.loadOrientedBitmap(context, sourceUri, manualQuarterTurns)
        }
        val rectification = withContext(Dispatchers.Default) { PaperImageEngine.rectify(source, template) }
        if (rectification.bitmap !== source) source.recycle()
        val rectified = rectification.bitmap
        val rectifiedUri = withContext(Dispatchers.IO) { PaperBridgeFiles.saveBitmap(context, rectified, "rectified") }
        val white = withContext(Dispatchers.Default) { PaperImageEngine.estimatePaperWhiteLuma(rectified) }
        val results = mutableListOf<PaperFieldResult>()
        for (field in template.fields) {
            results += when (field.type) {
                PaperFieldType.OMR_SINGLE -> withContext(Dispatchers.Default) { extractSingleMark(rectified, field, white, settings) }
                PaperFieldType.OMR_MULTIPLE -> withContext(Dispatchers.Default) { extractMultipleMarks(rectified, field, white, settings) }
                PaperFieldType.OCR_TEXT, PaperFieldType.OCR_INTEGER, PaperFieldType.OCR_DECIMAL -> extractOcr(rectified, field, settings)
                PaperFieldType.BARCODE -> extractBarcode(rectified, field, settings)
            }
        }
        return PaperExtractionSession(template, sourceUri, rectifiedUri, rectified, rectification, white, results, scanTimeIso)
    }

    fun withManualValue(result: PaperFieldResult, value: String): PaperFieldResult {
        val normalised = normalise(result.spec, value)
        val validation = validate(result.spec, normalised)
        return result.copy(
            finalValue = normalised,
            valid = validation.first,
            reviewRequired = !validation.first,
            reviewed = validation.first,
            autoAccepted = false,
            reason = if (validation.first) "Confirmed by operator" else validation.second
        )
    }

    fun valuesJson(results: List<PaperFieldResult>): String = JSONObject().apply {
        results.forEach { put(it.spec.name, it.finalValue) }
    }.toString()

    fun dynamicFieldsJson(results: List<PaperFieldResult>): String = JSONArray(results.map { it.spec.name }).toString()

    fun auditJson(session: PaperExtractionSession, results: List<PaperFieldResult>): String = JSONObject().apply {
        put("schema", "methodmesh.paper.audit.v1")
        put("template_id", session.template.templateId)
        put("template_version", session.template.version)
        put("page_white_luma", session.pageWhiteLuma)
        put("anchors", JSONArray().apply {
            session.rectification.anchors.forEach { anchor ->
                put(JSONObject().apply {
                    put("corner", anchor.corner)
                    put("x", anchor.point.x)
                    put("y", anchor.point.y)
                    put("score", anchor.score)
                    put("component_area", anchor.componentArea)
                    put("bounding_width", anchor.boundingWidth)
                    put("bounding_height", anchor.boundingHeight)
                })
            }
        })
        put("fields", JSONArray().apply {
            results.forEach { result ->
                put(JSONObject().apply {
                    put("name", result.spec.name)
                    put("type", result.spec.type.name.lowercase())
                    put("candidate", result.candidate)
                    put("final", result.finalValue)
                    put("valid", result.valid)
                    put("review_required", result.reviewRequired)
                    put("reviewed", result.reviewed)
                    put("auto_accepted", result.autoAccepted)
                    put("reason", result.reason)
                    if (result.scores.isNotEmpty()) put("scores", JSONObject(result.scores))
                    if (result.rawText.isNotBlank()) put("raw_text", result.rawText)
                })
            }
        })
    }.toString()

    private fun extractSingleMark(bitmap: Bitmap, field: PaperFieldSpec, pageWhite: Float, settings: PaperExtractionSettings): PaperFieldResult {
        val scores = field.options.associate { option -> option.value to PaperImageEngine.markScore(bitmap, option.roi, pageWhite) }
        val ranked = scores.entries.sortedByDescending { it.value }
        val top = ranked.first()
        val second = ranked.getOrNull(1)?.value ?: 0f
        val marked = ranked.filter { it.value >= field.threshold }
        val unique = marked.size == 1 && top.value - second >= field.minimumSeparation
        val candidate = if (unique) top.key else ""
        val validation = validate(field, candidate)
        val manifestAllows = field.autoAccept ?: settings.autoAcceptOmr
        val auto = unique && validation.first && manifestAllows
        val reason = when {
            marked.isEmpty() -> "No option exceeded the mark threshold"
            marked.size > 1 -> "Multiple options exceeded the mark threshold"
            top.value - second < field.minimumSeparation -> "Top mark was not sufficiently separated from the runner-up"
            !validation.first -> validation.second
            auto -> "Unambiguous mark accepted"
            else -> "Mark candidate requires confirmation"
        }
        return PaperFieldResult(field, candidate, candidate, validation.first, !auto, false, auto, reason, scores)
    }

    private fun extractMultipleMarks(bitmap: Bitmap, field: PaperFieldSpec, pageWhite: Float, settings: PaperExtractionSettings): PaperFieldResult {
        val scores = field.options.associate { option -> option.value to PaperImageEngine.markScore(bitmap, option.roi, pageWhite) }
        val selected = field.options.filter { scores[it.value]!! >= field.threshold }.map { it.value }
        val nearThreshold = scores.values.any { abs(it - field.threshold) < field.minimumSeparation }
        val candidate = selected.joinToString(field.separator)
        val validation = validate(field, candidate)
        val manifestAllows = field.autoAccept ?: settings.autoAcceptOmr
        val auto = !nearThreshold && validation.first && manifestAllows
        val reason = when {
            nearThreshold -> "One or more marks are too close to the decision threshold"
            !validation.first -> validation.second
            auto -> "Unambiguous multiple marks accepted"
            else -> "Multiple-mark result requires confirmation"
        }
        return PaperFieldResult(field, candidate, candidate, validation.first, !auto, false, auto, reason, scores)
    }

    private suspend fun extractOcr(bitmap: Bitmap, field: PaperFieldSpec, settings: PaperExtractionSettings): PaperFieldResult {
        val crop = PaperImageEngine.crop(bitmap, requireNotNull(field.roi))
        val raw = try {
            recogniseText(crop)
        } finally {
            crop.recycle()
        }
        val candidate = normalise(field, raw)
        val validation = validate(field, candidate)
        val manifestAllows = field.autoAccept ?: settings.autoAcceptOcr
        val auto = validation.first && manifestAllows
        val reason = when {
            !validation.first -> validation.second
            auto -> "OCR candidate passed declared constraints and template permits auto-accept"
            else -> "OCR requires operator confirmation"
        }
        return PaperFieldResult(field, candidate, candidate, validation.first, !auto, false, auto, reason, rawText = raw)
    }

    private suspend fun extractBarcode(bitmap: Bitmap, field: PaperFieldSpec, settings: PaperExtractionSettings): PaperFieldResult {
        val crop = PaperImageEngine.crop(bitmap, requireNotNull(field.roi))
        val values = try { recogniseBarcodes(crop) } finally { crop.recycle() }
        val candidate = values.singleOrNull().orEmpty()
        val validation = validate(field, candidate)
        val manifestAllows = field.autoAccept ?: settings.autoAcceptOcr
        val auto = values.size == 1 && validation.first && manifestAllows
        val reason = when {
            values.isEmpty() -> "No barcode found in declared ROI"
            values.size > 1 -> "Multiple barcodes found in declared ROI"
            !validation.first -> validation.second
            auto -> "Single barcode accepted"
            else -> "Barcode requires operator confirmation"
        }
        return PaperFieldResult(field, candidate, candidate, validation.first, !auto, false, auto, reason, rawText = values.joinToString(" | "))
    }

    private fun normalise(field: PaperFieldSpec, raw: String): String {
        val collapsed = raw.replace(Regex("\\s+"), " ").trim()
        return when (field.normalise.lowercase()) {
            "none" -> raw
            "trim" -> raw.trim()
            "uppercase_trim" -> raw.trim().uppercase()
            "lowercase_trim" -> raw.trim().lowercase()
            "digits_only" -> raw.filter(Char::isDigit)
            "collapse_whitespace" -> collapsed
            else -> collapsed
        }
    }

    private fun validate(field: PaperFieldSpec, value: String): Pair<Boolean, String> {
        if (field.required && value.isBlank()) return false to "Required field is blank"
        if (value.isBlank()) return true to "Blank optional field"
        field.regex?.let { pattern -> if (!Regex(pattern).matches(value)) return false to "Value does not match declared pattern" }
        if (field.allowedValues.isNotEmpty()) {
            val values = if (field.type == PaperFieldType.OMR_MULTIPLE) value.split(field.separator).filter(String::isNotBlank) else listOf(value)
            if (values.any { it !in field.allowedValues }) return false to "Value is outside the declared allowed set"
        }
        when (field.type) {
            PaperFieldType.OCR_INTEGER -> {
                val number = value.toLongOrNull() ?: return false to "OCR result is not a valid integer"
                if (field.minimum != null && number < field.minimum) return false to "Value is below declared minimum"
                if (field.maximum != null && number > field.maximum) return false to "Value is above declared maximum"
            }
            PaperFieldType.OCR_DECIMAL -> {
                val number = value.toDoubleOrNull() ?: return false to "OCR result is not a valid decimal"
                if (field.minimum != null && number < field.minimum) return false to "Value is below declared minimum"
                if (field.maximum != null && number > field.maximum) return false to "Value is above declared maximum"
            }
            else -> Unit
        }
        return true to "Valid"
    }

    private suspend fun recogniseText(bitmap: Bitmap): String = suspendCancellableCoroutine { continuation ->
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { text -> recognizer.close(); if (continuation.isActive) continuation.resume(text.text) }
            .addOnFailureListener { error -> recognizer.close(); if (continuation.isActive) continuation.resumeWithException(error) }
    }

    private suspend fun recogniseBarcodes(bitmap: Bitmap): List<String> = suspendCancellableCoroutine { continuation ->
        val scanner = BarcodeScanning.getClient()
        scanner.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { barcodes -> scanner.close(); if (continuation.isActive) continuation.resume(barcodes.mapNotNull { it.rawValue }.distinct()) }
            .addOnFailureListener { error -> scanner.close(); if (continuation.isActive) continuation.resumeWithException(error) }
    }
}
