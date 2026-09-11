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
    val rawText: String = "",
    val relevant: Boolean? = true,
    val requiredNow: Boolean? = null,
    val constraintSatisfied: Boolean? = true,
    val logicUnknown: Boolean = false,
    val logicViolation: Boolean = false,
    val logicMessage: String = "",
    val naOverride: Boolean = false
) {
    val effectiveRequired: Boolean get() = relevant != false && (requiredNow ?: spec.required)
    val resolved: Boolean get() = when {
        naOverride -> true
        relevant == false -> finalValue.isBlank()
        logicViolation -> false
        else -> valid && (!reviewRequired || reviewed)
    }
}

internal data class PaperExtractionSession(
    val template: PaperTemplate,
    val sourceUri: Uri,
    val rectifiedUri: Uri,
    val rectifiedBitmap: Bitmap,
    val rectification: PaperRectification,
    val pageWhiteLuma: Float,
    val results: List<PaperFieldResult>,
    val scanTimeIso: String,
    val acquisitionMode: String = "direct"
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
        scanTimeIso: String,
        acquisitionMode: String = "direct"
    ): PaperExtractionSession {
        val source = withContext(Dispatchers.Default) {
            PaperImageEngine.loadOrientedBitmap(context, sourceUri, manualQuarterTurns)
        }
        val mlKitPreprocessed = acquisitionMode == "mlkit_document_scanner"
        val rectification = when (template.registration.type) {
            PaperRegistrationType.APRILTAG8 -> withContext(Dispatchers.Default) {
                PaperAprilTagFiducial.rectify(source, template, mlKitPreprocessed)
            }
            PaperRegistrationType.QR4 -> PaperQrFiducial.rectify(source, template, mlKitPreprocessed)
            PaperRegistrationType.BULLSEYE4 -> withContext(Dispatchers.Default) {
                PaperImageEngine.rectify(source, template, mlKitPreprocessed = mlKitPreprocessed)
            }
        }
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
                PaperFieldType.BARCODE -> extractBarcode(rectified, field, white)
                PaperFieldType.IMAGE -> extractImage(context, rectified, field, white)
            }
        }
        return PaperExtractionSession(template, sourceUri, rectifiedUri, rectified, rectification, white, PaperXlsLogic.apply(results), scanTimeIso, acquisitionMode)
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
            reason = if (validation.first) "Confirmed by operator" else validation.second,
            naOverride = false,
            relevant = true,
            requiredNow = null,
            constraintSatisfied = true,
            logicUnknown = false,
            logicViolation = false,
            logicMessage = ""
        )
    }

    fun withNaOverride(result: PaperFieldResult): PaperFieldResult = result.copy(
        finalValue = "na",
        valid = true,
        reviewRequired = false,
        reviewed = true,
        autoAccepted = false,
        reason = "NA override confirmed by operator",
        relevant = true,
        requiredNow = false,
        constraintSatisfied = true,
        logicUnknown = false,
        logicViolation = false,
        logicMessage = "NA override confirmed by operator",
        naOverride = true
    )

    fun valuesJson(results: List<PaperFieldResult>): String = JSONObject().apply {
        results.forEach { put(it.spec.name, it.finalValue) }
    }.toString()

    fun dynamicFieldsJson(results: List<PaperFieldResult>): String = JSONArray(results.map { it.spec.name }).toString()

    fun auditJson(session: PaperExtractionSession, results: List<PaperFieldResult>, manualEditsJson: String = "[]"): String = JSONObject().apply {
        put("schema", "methodmesh.paper.audit.v2")
        put("template_id", session.template.templateId)
        put("template_version", session.template.version)
        put("page_white_luma", session.pageWhiteLuma)
        put("acquisition_mode", session.acquisitionMode)
        put("registration_mode", session.rectification.registrationMode)
        put("registration_type", session.template.registration.type.name.lowercase())
        if (session.template.registration.type != PaperRegistrationType.BULLSEYE4 && session.template.registration.schemaKey.isNotBlank()) put("registration_schema_key", session.template.registration.schemaKey)
        put("registration_markers_detected", session.rectification.detectedMarkerCount)
        put("registration_markers_expected", session.rectification.expectedMarkerCount)
        put("registration_correspondences", session.rectification.correspondenceCount)
        session.rectification.reprojectionRmsPx?.let { put("registration_reprojection_rms_px", it) }
        session.rectification.reprojectionMaxPx?.let { put("registration_reprojection_max_px", it) }
        put("registration_quality", session.rectification.registrationQuality)
        put("registration_passed", session.rectification.registrationPassed)
        put("manual_edits", runCatching { JSONArray(manualEditsJson) }.getOrDefault(JSONArray()))
        put("logic_checks", JSONArray(PaperXlsLogic.checksJson(results)))
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
                    put("relevant", result.relevant ?: JSONObject.NULL)
                    put("required_now", result.requiredNow ?: JSONObject.NULL)
                    put("constraint_satisfied", result.constraintSatisfied ?: JSONObject.NULL)
                    put("logic_unknown", result.logicUnknown)
                    put("logic_violation", result.logicViolation)
                    put("logic_message", result.logicMessage)
                    put("na_override", result.naOverride)
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
        // Free handwritten/printed text is intrinsically open-ended: even a plausible
        // OCR string has no strong value-space check. Keep OCR_TEXT in review. Narrow
        // numeric fields may still auto-accept when explicitly enabled and constraints pass.
        val auto = validation.first && manifestAllows && field.type != PaperFieldType.OCR_TEXT
        val reason = when {
            !validation.first -> validation.second
            field.type == PaperFieldType.OCR_TEXT -> "Free-text OCR requires operator confirmation against the paper ROI"
            auto -> "OCR candidate passed declared constraints and template permits auto-accept"
            else -> "OCR requires operator confirmation"
        }
        return PaperFieldResult(field, candidate, candidate, validation.first, !auto, false, auto, reason, rawText = raw)
    }

    private suspend fun extractBarcode(
        bitmap: Bitmap,
        field: PaperFieldSpec,
        pageWhite: Float
    ): PaperFieldResult {
        val crop = PaperImageEngine.crop(bitmap, requireNotNull(field.roi))
        try {
            val decoded = recogniseBarcodesRobust(crop)
            val candidate = decoded.singleOrNull().orEmpty()
            val validation = validate(field, candidate)
            val manifestAllows = field.autoAccept ?: true
            if (decoded.isNotEmpty()) {
                val auto = decoded.size == 1 && validation.first && manifestAllows
                val reason = when {
                    decoded.size > 1 -> "Multiple barcodes were decoded in the declared ROI"
                    !validation.first -> validation.second
                    auto -> "Single barcode decoded and accepted"
                    else -> "Barcode decoded but requires operator confirmation"
                }
                return PaperFieldResult(
                    field, candidate, candidate, validation.first,
                    reviewRequired = !auto,
                    reviewed = false,
                    autoAccepted = auto,
                    reason = reason,
                    rawText = decoded.joinToString(" | ")
                )
            }

            // An optional barcode is only genuinely blank when the ROI is visually blank.
            // A printed label/sticker that ML Kit failed to decode must never disappear silently.
            val hasVisibleContent = withContext(Dispatchers.Default) { imageRoiHasInk(crop, pageWhite) }
            if (!hasVisibleContent) {
                val valid = !field.required
                return PaperFieldResult(
                    field, "", "", valid,
                    reviewRequired = field.required,
                    reviewed = false,
                    autoAccepted = !field.required,
                    reason = if (field.required) "Required barcode ROI appears blank" else "Barcode ROI appears blank"
                )
            }

            val ocrView = barcodeOcrView(crop)
            val ocrRaw = try {
                runCatching { recogniseText(ocrView) }.getOrDefault("")
            } finally {
                ocrView.recycle()
            }
            val ocrCandidate = barcodeLikeToken(ocrRaw)
            val fallbackValidation = validate(field, ocrCandidate)
            return PaperFieldResult(
                spec = field,
                candidate = ocrCandidate,
                finalValue = ocrCandidate,
                valid = fallbackValidation.first,
                reviewRequired = true,
                reviewed = false,
                autoAccepted = false,
                reason = if (ocrCandidate.isNotBlank()) {
                    "Barcode-like content is present but ML Kit could not decode it; OCR suggestion requires confirmation"
                } else {
                    "Barcode-like content is present but no code was decoded; inspect the ROI and enter the value manually"
                },
                rawText = ocrRaw
            )
        } finally {
            crop.recycle()
        }
    }


    private suspend fun extractImage(
        context: Context,
        bitmap: Bitmap,
        field: PaperFieldSpec,
        pageWhite: Float
    ): PaperFieldResult {
        val crop = PaperImageEngine.crop(bitmap, requireNotNull(field.roi))
        val hasContent = try {
            withContext(Dispatchers.Default) { imageRoiHasInk(crop, pageWhite) }
        } catch (_: Throwable) {
            true // fail safe: preserve the visual region rather than silently discarding it
        }
        if (!hasContent) {
            crop.recycle()
            val valid = !field.required
            return PaperFieldResult(
                spec = field,
                candidate = "",
                finalValue = "",
                valid = valid,
                reviewRequired = field.required,
                reviewed = false,
                autoAccepted = !field.required,
                reason = if (field.required) "Required image region appears blank" else "Image region appears blank; no attachment returned"
            )
        }
        val uri = try {
            withContext(Dispatchers.IO) { PaperBridgeFiles.saveBitmap(context, crop, "field-${field.name}") }
        } finally {
            crop.recycle()
        }
        val value = uri.toString()
        return PaperFieldResult(
            spec = field,
            candidate = value,
            finalValue = value,
            valid = true,
            reviewRequired = false,
            reviewed = false,
            autoAccepted = true,
            reason = "Image ROI contains content and was returned as an attachment"
        )
    }

    /**
     * Detect meaningful handwriting/drawing inside an image ROI while ignoring the
     * printed black rectangle used to define the ROI. The outer 7% is excluded,
     * then we count pixels substantially darker than the page-white estimate.
     */
    private fun imageRoiHasInk(crop: Bitmap, pageWhite: Float): Boolean {
        if (crop.width < 8 || crop.height < 8) return false
        val insetX = (crop.width * 0.07f).toInt().coerceAtLeast(2)
        val insetY = (crop.height * 0.07f).toInt().coerceAtLeast(2)
        val left = insetX.coerceAtMost(crop.width / 3)
        val right = (crop.width - insetX).coerceAtLeast(left + 1)
        val top = insetY.coerceAtMost(crop.height / 3)
        val bottom = (crop.height - insetY).coerceAtLeast(top + 1)
        val threshold = (pageWhite - 45f).coerceIn(80f, 205f)
        var dark = 0
        var total = 0
        val step = maxOf(1, minOf(crop.width, crop.height) / 220)
        var y = top
        while (y < bottom) {
            var x = left
            while (x < right) {
                val pixel = crop.getPixel(x, y)
                val r = android.graphics.Color.red(pixel)
                val g = android.graphics.Color.green(pixel)
                val b = android.graphics.Color.blue(pixel)
                val luma = 0.2126f * r + 0.7152f * g + 0.0722f * b
                if (luma < threshold) dark++
                total++
                x += step
            }
            y += step
        }
        return total > 0 && dark.toFloat() / total.toFloat() >= 0.0045f
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

    private suspend fun recogniseBarcodesRobust(bitmap: Bitmap): List<String> {
        recogniseBarcodes(bitmap).takeIf { it.isNotEmpty() }?.let { return it }

        val scale = minOf(3f, 1800f / maxOf(bitmap.width, 1).toFloat()).coerceAtLeast(1f)
        val enlarged = if (scale > 1.05f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else null
        try {
            enlarged?.let { recogniseBarcodes(it).takeIf { values -> values.isNotEmpty() }?.let { values -> return values } }

            val contrastSource = enlarged ?: bitmap
            val contrast = barcodeHighContrast(contrastSource)
            try {
                recogniseBarcodes(contrast).takeIf { it.isNotEmpty() }?.let { return it }
            } finally {
                contrast.recycle()
            }

            val band = barcodeDenseBand(contrastSource)
            try {
                if (band != null) {
                    recogniseBarcodes(band).takeIf { it.isNotEmpty() }?.let { return it }
                    val bandScale = minOf(4f, 1800f / maxOf(band.width, 1).toFloat()).coerceAtLeast(1f)
                    if (bandScale > 1.05f) {
                        val largeBand = Bitmap.createScaledBitmap(
                            band,
                            (band.width * bandScale).toInt().coerceAtLeast(1),
                            (band.height * bandScale).toInt().coerceAtLeast(1),
                            true
                        )
                        try {
                            recogniseBarcodes(largeBand).takeIf { it.isNotEmpty() }?.let { return it }
                            val high = barcodeHighContrast(largeBand)
                            try {
                                recogniseBarcodes(high).takeIf { it.isNotEmpty() }?.let { return it }
                            } finally {
                                high.recycle()
                            }
                        } finally {
                            largeBand.recycle()
                        }
                    }
                }
            } finally {
                band?.recycle()
            }
        } finally {
            enlarged?.recycle()
        }
        return emptyList()
    }

    /**
     * Build a high-contrast monochrome view for difficult 1-D labels. The source ROI
     * is preserved; this is only a secondary ML Kit decode attempt.
     */
    private fun barcodeHighContrast(source: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        var sum = 0L
        var count = 0L
        val sampleStep = maxOf(1, minOf(source.width, source.height) / 180)
        var y = 0
        while (y < source.height) {
            var x = 0
            while (x < source.width) {
                val c = source.getPixel(x, y)
                sum += (android.graphics.Color.red(c) * 299 + android.graphics.Color.green(c) * 587 + android.graphics.Color.blue(c) * 114) / 1000
                count++
                x += sampleStep
            }
            y += sampleStep
        }
        val mean = if (count > 0) (sum / count).toInt() else 160
        val threshold = mean.coerceIn(105, 210)
        for (row in 0 until source.height) {
            for (col in 0 until source.width) {
                val c = source.getPixel(col, row)
                val luma = (android.graphics.Color.red(c) * 299 + android.graphics.Color.green(c) * 587 + android.graphics.Color.blue(c) * 114) / 1000
                output.setPixel(col, row, if (luma < threshold) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        return output
    }

    /**
     * Locate the horizontal band with the densest left/right luminance transitions.
     * This tends to isolate a small 1-D barcode printed inside a much larger paper ROI
     * while ignoring the surrounding form rectangle and human-readable caption.
     */
    private fun barcodeDenseBand(source: Bitmap): Bitmap? {
        if (source.width < 40 || source.height < 20) return null
        val insetX = (source.width * 0.04f).toInt().coerceAtLeast(2)
        val insetY = (source.height * 0.04f).toInt().coerceAtLeast(2)
        val left = insetX
        val right = (source.width - insetX).coerceAtLeast(left + 2)
        val top = insetY
        val bottom = (source.height - insetY).coerceAtLeast(top + 2)
        var bestY = (top + bottom) / 2
        var bestScore = -1
        val rowStep = maxOf(1, (bottom - top) / 120)
        val colStep = maxOf(1, (right - left) / 420)
        var y = top
        while (y < bottom) {
            var previous: Int? = null
            var transitions = 0
            var x = left
            while (x < right) {
                val c = source.getPixel(x, y)
                val l = (android.graphics.Color.red(c) * 299 + android.graphics.Color.green(c) * 587 + android.graphics.Color.blue(c) * 114) / 1000
                previous?.let { if (kotlin.math.abs(l - it) >= 42) transitions++ }
                previous = l
                x += colStep
            }
            if (transitions > bestScore) {
                bestScore = transitions
                bestY = y
            }
            y += rowStep
        }
        if (bestScore < 8) return null
        val bandHeight = maxOf(24, ((bottom - top) * 0.34f).toInt()).coerceAtMost(bottom - top)
        val bandTop = (bestY - bandHeight / 2).coerceIn(top, bottom - bandHeight)
        return Bitmap.createBitmap(source, left, bandTop, right - left, bandHeight)
    }

    private fun barcodeOcrView(source: Bitmap): Bitmap {
        val scale = minOf(3f, 1600f / maxOf(source.width, 1).toFloat()).coerceAtLeast(1f)
        return if (scale > 1.05f) {
            Bitmap.createScaledBitmap(source, (source.width * scale).toInt(), (source.height * scale).toInt(), true)
        } else source.copy(Bitmap.Config.ARGB_8888, false)
    }

    private fun barcodeLikeToken(raw: String): String {
        val cleaned = raw.replace(Regex("\\s+"), " ").trim()
        if (cleaned.isBlank()) return ""
        val labelled = Regex("(?i)(?:serial(?:[- ]?number)?|barcode|code|id)\\s*[:#-]?\\s*([A-Z0-9][A-Z0-9._/-]{3,})")
            .find(cleaned)?.groupValues?.getOrNull(1)
        if (!labelled.isNullOrBlank()) return labelled
        return Regex("[A-Z0-9][A-Z0-9._/-]{4,}", RegexOption.IGNORE_CASE)
            .findAll(cleaned)
            .map { it.value }
            .maxByOrNull { it.length }
            .orEmpty()
    }

    private suspend fun recogniseBarcodes(bitmap: Bitmap): List<String> = suspendCancellableCoroutine { continuation ->
        val scanner = BarcodeScanning.getClient()
        scanner.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { barcodes -> scanner.close(); if (continuation.isActive) continuation.resume(barcodes.mapNotNull { it.rawValue }.distinct()) }
            .addOnFailureListener { error -> scanner.close(); if (continuation.isActive) continuation.resumeWithException(error) }
    }
}
