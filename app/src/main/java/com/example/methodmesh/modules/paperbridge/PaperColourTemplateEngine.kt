package com.example.methodmesh.modules.paperbridge

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Design-time semantic parser for deliberately colour-annotated paper templates.
 *
 * The colours are NOT required at data-entry time. They are only an authoring
 * language used to recover question grouping/type. Runtime extraction uses the
 * saved black response ROIs after registration.
 *
 * Fixed authoring palette (sRGB):
 *  - scalar envelope:        #D81B60 (magenta/red)
 *  - select-one envelope:    #00897B (teal/green)
 *  - select-multiple:        #EF6C00 (orange)
 *  - barcode envelope:       #5E35B1 (violet)
 *  - image envelope:         #7CB342 (lime green)
 *  - choice/answer labels:   #1565C0 (blue)
 *  - question text / ROIs:   black
 */
internal object PaperColourTemplateEngine {
    internal enum class Hint(val rgb: Int, val display: String) {
        SCALAR(Color.rgb(216, 27, 96), "scalar entry"),
        SELECT_ONE(Color.rgb(0, 137, 123), "select one"),
        SELECT_MULTIPLE(Color.rgb(239, 108, 0), "select multiple"),
        BARCODE(Color.rgb(94, 53, 177), "barcode"),
        IMAGE(Color.rgb(124, 179, 66), "image attachment")
    }

    internal val CHOICE_LABEL_RGB: Int = Color.rgb(21, 101, 192)
    // The closest palette colours are ~81 RGB units apart. Keep semantic
    // segmentation comfortably below that so blue option text can never be
    // mistaken for a teal/violet question envelope.
    private const val PALETTE_TOLERANCE = 55

    internal data class RecognisedLine(
        val text: String,
        val roi: NormalisedRoi
    )

    internal data class OptionProposal(
        val text: String,
        val responseRoi: NormalisedRoi?,
        val choiceValue: String?,
        val choiceLabel: String?,
        val confidence: Float
    )

    internal data class QuestionProposal(
        val hint: Hint,
        val envelope: NormalisedRoi,
        val questionText: String,
        val responseRois: List<NormalisedRoi>,
        val fieldName: String?,
        val fieldLabel: String?,
        val fieldConfidence: Float,
        val options: List<OptionProposal>
    )

    internal data class Analysis(
        val questions: List<QuestionProposal>,
        val warnings: List<String>
    ) {
        val mappedQuestionCount: Int get() = questions.count { it.fieldName != null }
        val detectedQuestionCount: Int get() = questions.size
    }

    suspend fun analyse(bitmap: Bitmap, schema: PaperOdkSchema): Analysis {
        val envelopes = detectEnvelopes(bitmap)
        if (envelopes.isEmpty()) return Analysis(emptyList(), listOf("No Paper Bridge colour envelopes were detected."))
        val blackBoxes = PaperBoxDetectionEngine.detect(bitmap, neutralOnly = true).map { it.roi }
        val warnings = mutableListOf<String>()
        val proposals = mutableListOf<QuestionProposal>()

        for ((hint, envelope) in envelopes) {
            val innerBoxes = blackBoxes.filter { box -> centreInside(box, envelope) && roiArea(box) < roiArea(envelope) * 0.70f }
                .sortedWith(compareBy<NormalisedRoi> { it.top }.thenBy { it.left })
            val questionText = recogniseMaskedText(bitmap, envelope, MaskMode.QUESTION_BLACK)
            val compatible = schema.fields.filter { fieldCompatible(hint, it.type) }
            val rankedFields = compatible.map { it to labelSimilarity(questionText, it.label) }.sortedByDescending { it.second }
            val best = rankedFields.firstOrNull()
            val next = rankedFields.getOrNull(1)?.second ?: 0f
            // A unique compatible workbook field is still a useful type-based guess
            // even when OCR is weak. Keep its confidence low so the designer marks it
            // as a QUERY rather than silently treating it as authoritative.
            val fieldAccepted = best != null && (
                (best.second >= 0.72f && best.second - next >= 0.06f) || compatible.size == 1
            )
            val field = if (fieldAccepted) best!!.first else null
            if (field == null) {
                warnings += "${hint.display}: '${questionText.ifBlank { "unread question" }}' did not uniquely match a compatible survey label."
            }

            val options = if (hint == Hint.SELECT_ONE || hint == Hint.SELECT_MULTIPLE) {
                val blueLines = recogniseBlueLines(bitmap, envelope)
                pairOptions(blueLines, innerBoxes, field)
            } else emptyList()

            proposals += QuestionProposal(
                hint = hint,
                envelope = envelope,
                questionText = questionText,
                responseRois = innerBoxes,
                fieldName = field?.name,
                fieldLabel = field?.label,
                fieldConfidence = if (fieldAccepted) best?.second ?: 0f else best?.second ?: 0f,
                options = options
            )
        }
        return Analysis(proposals.sortedWith(compareBy<QuestionProposal> { it.envelope.top }.thenBy { it.envelope.left }), warnings.distinct())
    }

    private enum class MaskMode { QUESTION_BLACK }

    private suspend fun recogniseMaskedText(bitmap: Bitmap, roi: NormalisedRoi, mode: MaskMode): String {
        val crop = PaperImageEngine.crop(bitmap, roi)
        val masked = Bitmap.createBitmap(crop.width, crop.height, Bitmap.Config.ARGB_8888)
        try {
            val src = IntArray(crop.width * crop.height)
            val dst = IntArray(src.size)
            crop.getPixels(src, 0, crop.width, 0, 0, crop.width, crop.height)
            for (i in src.indices) {
                val p = src[i]
                val dark = luma(p) < 115
                val coloured = isNear(p, CHOICE_LABEL_RGB, PALETTE_TOLERANCE) || Hint.values().any { isNear(p, it.rgb, PALETTE_TOLERANCE) }
                dst[i] = if (mode == MaskMode.QUESTION_BLACK && dark && !coloured) Color.BLACK else Color.WHITE
            }
            masked.setPixels(dst, 0, crop.width, 0, 0, crop.width, crop.height)
            return recogniseText(masked).replace(Regex("\\s+"), " ").trim()
        } finally {
            crop.recycle()
            masked.recycle()
        }
    }

    private suspend fun recogniseBlueLines(bitmap: Bitmap, envelope: NormalisedRoi): List<RecognisedLine> {
        val crop = PaperImageEngine.crop(bitmap, envelope)
        val masked = Bitmap.createBitmap(crop.width, crop.height, Bitmap.Config.ARGB_8888)
        try {
            val src = IntArray(crop.width * crop.height)
            val dst = IntArray(src.size)
            crop.getPixels(src, 0, crop.width, 0, 0, crop.width, crop.height)
            for (i in src.indices) dst[i] = if (isNear(src[i], CHOICE_LABEL_RGB, PALETTE_TOLERANCE)) Color.BLACK else Color.WHITE
            masked.setPixels(dst, 0, crop.width, 0, 0, crop.width, crop.height)
            val lines = recogniseLines(masked)
            val ew = envelope.right - envelope.left
            val eh = envelope.bottom - envelope.top
            return lines.mapNotNull { (text, rect) ->
                val cleaned = text.replace(Regex("\\s+"), " ").trim()
                if (cleaned.isBlank()) return@mapNotNull null
                val left = envelope.left + (rect.left.toFloat() / crop.width.toFloat()) * ew
                val top = envelope.top + (rect.top.toFloat() / crop.height.toFloat()) * eh
                val right = envelope.left + (rect.right.toFloat() / crop.width.toFloat()) * ew
                val bottom = envelope.top + (rect.bottom.toFloat() / crop.height.toFloat()) * eh
                if (right <= left || bottom <= top) null else RecognisedLine(
                    cleaned,
                    NormalisedRoi(left.coerceIn(0f, 1f), top.coerceIn(0f, 1f), right.coerceIn(0f, 1f), bottom.coerceIn(0f, 1f))
                )
            }.sortedWith(compareBy<RecognisedLine> { it.roi.top }.thenBy { it.roi.left })
        } finally {
            crop.recycle()
            masked.recycle()
        }
    }

    private fun pairOptions(
        labels: List<RecognisedLine>,
        boxes: List<NormalisedRoi>,
        field: PaperOdkField?
    ): List<OptionProposal> {
        if (labels.isEmpty() && boxes.isEmpty()) return emptyList()
        val remainingBoxes = boxes.toMutableList()
        return labels.map { label ->
            val box = remainingBoxes.minByOrNull { box ->
                val dy = abs(centreY(box) - centreY(label.roi))
                val horizontalPenalty = if (box.right <= label.roi.left + 0.02f) 0f else 0.08f
                dy + horizontalPenalty + abs(box.left - label.roi.left) * 0.08f
            }?.also { remainingBoxes.remove(it) }
            val choices = field?.options.orEmpty()
            val ranked = choices.map { it to labelSimilarity(label.text, it.second) }.sortedByDescending { it.second }
            val best = ranked.firstOrNull()
            val second = ranked.getOrNull(1)?.second ?: 0f
            val accepted = best != null && best.second >= 0.70f && best.second - second >= 0.05f
            OptionProposal(
                text = label.text,
                responseRoi = box,
                choiceValue = if (accepted) best!!.first.first else null,
                choiceLabel = if (accepted) best!!.first.second else null,
                confidence = best?.second ?: 0f
            )
        } + remainingBoxes.map { box -> OptionProposal("", box, null, null, 0f) }
    }

    private fun fieldCompatible(hint: Hint, type: PaperFieldType): Boolean = when (hint) {
        Hint.SCALAR -> type in setOf(PaperFieldType.OCR_TEXT, PaperFieldType.OCR_INTEGER, PaperFieldType.OCR_DECIMAL)
        Hint.SELECT_ONE -> type == PaperFieldType.OMR_SINGLE
        Hint.SELECT_MULTIPLE -> type == PaperFieldType.OMR_MULTIPLE
        Hint.BARCODE -> type == PaperFieldType.BARCODE
        Hint.IMAGE -> type == PaperFieldType.IMAGE
    }

    private fun detectEnvelopes(bitmap: Bitmap): List<Pair<Hint, NormalisedRoi>> {
        val longSide = max(bitmap.width, bitmap.height)
        val scale = min(1f, 1600f / longSide.toFloat())
        val analysis = if (scale < 0.999f) Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).roundToInt().coerceAtLeast(1),
            (bitmap.height * scale).roundToInt().coerceAtLeast(1), true
        ) else bitmap
        try {
            val w = analysis.width
            val h = analysis.height
            val pixels = IntArray(w * h)
            analysis.getPixels(pixels, 0, w, 0, 0, w, h)
            val out = mutableListOf<Pair<Hint, NormalisedRoi>>()
            Hint.values().forEach { hint ->
                val mask = BooleanArray(pixels.size) { isNear(pixels[it], hint.rgb, PALETTE_TOLERANCE) }
                val visited = BooleanArray(mask.size)
                val stack = IntArray(mask.size)
                for (start in mask.indices) {
                    if (!mask[start] || visited[start]) continue
                    var n = 0
                    stack[n++] = start
                    visited[start] = true
                    var area = 0
                    var minX = w
                    var maxX = 0
                    var minY = h
                    var maxY = 0
                    while (n > 0) {
                        val i = stack[--n]
                        val x = i % w
                        val y = i / w
                        area++
                        minX = min(minX, x); maxX = max(maxX, x); minY = min(minY, y); maxY = max(maxY, y)
                        for (yy in max(0, y - 1)..min(h - 1, y + 1)) for (xx in max(0, x - 1)..min(w - 1, x + 1)) {
                            val j = yy * w + xx
                            if (mask[j] && !visited[j]) { visited[j] = true; stack[n++] = j }
                        }
                    }
                    val bw = maxX - minX + 1
                    val bh = maxY - minY + 1
                    val bbox = bw.toLong() * bh.toLong()
                    val page = w.toLong() * h.toLong()
                    if (area < 45 || bw < w * 0.08f || bh < h * 0.018f) continue
                    if (bbox < page * 0.0012 || bbox > page * 0.70) continue
                    val density = area.toFloat() / bbox.toFloat()
                    // Support both legacy outline envelopes and the preferred solid
                    // semantic panels used by the tutorial. Text/ROI cut-outs leave
                    // holes in a solid panel, but it remains one connected component.
                    // Tiny coloured text is already excluded by the size/bbox guards.
                    if (density < 0.015f) continue
                    val roi = NormalisedRoi(
                        minX.toFloat() / w, minY.toFloat() / h,
                        (maxX + 1).toFloat() / w, (maxY + 1).toFloat() / h
                    )
                    out += hint to roi
                }
            }
            return dedupeEnvelopes(out)
        } finally {
            if (analysis !== bitmap && !analysis.isRecycled) analysis.recycle()
        }
    }

    private fun dedupeEnvelopes(input: List<Pair<Hint, NormalisedRoi>>): List<Pair<Hint, NormalisedRoi>> {
        val kept = mutableListOf<Pair<Hint, NormalisedRoi>>()
        input.sortedByDescending { roiArea(it.second) }.forEach { candidate ->
            if (kept.none { overlapFraction(candidate.second, it.second) > 0.90f }) kept += candidate
        }
        return kept
    }

    private suspend fun recogniseText(bitmap: Bitmap): String = suspendCancellableCoroutine { continuation ->
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { text -> recognizer.close(); if (continuation.isActive) continuation.resume(text.text) }
            .addOnFailureListener { error -> recognizer.close(); if (continuation.isActive) continuation.resumeWithException(error) }
    }

    private suspend fun recogniseLines(bitmap: Bitmap): List<Pair<String, Rect>> = suspendCancellableCoroutine { continuation ->
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { text ->
                val result = text.textBlocks.flatMap { block -> block.lines.mapNotNull { line -> line.boundingBox?.let { line.text to it } } }
                recognizer.close()
                if (continuation.isActive) continuation.resume(result)
            }
            .addOnFailureListener { error -> recognizer.close(); if (continuation.isActive) continuation.resumeWithException(error) }
    }

    private fun labelSimilarity(a: String, b: String): Float {
        val aa = normaliseLabel(a)
        val bb = normaliseLabel(b)
        if (aa.isBlank() || bb.isBlank()) return 0f
        if (aa == bb) return 1f
        // Paper layouts often append operational instructions such as
        // "Tick ONE" or "Tick ALL that apply" to the workbook label. If the
        // complete normalised survey label is present in the OCR text (or vice
        // versa), treat that as a very strong identity signal rather than
        // punishing the extra instruction words with edit distance.
        val shorter = if (aa.length <= bb.length) aa else bb
        val longer = if (aa.length > bb.length) aa else bb
        if (shorter.length >= 4 && longer.contains(shorter)) {
            val coverage = shorter.length.toFloat() / longer.length.toFloat()
            return (0.92f + 0.08f * coverage).coerceAtMost(0.995f)
        }
        val at = aa.split(' ').filter(String::isNotBlank).toSet()
        val bt = bb.split(' ').filter(String::isNotBlank).toSet()
        val union = (at + bt).size.coerceAtLeast(1)
        val jaccard = at.intersect(bt).size.toFloat() / union.toFloat()
        val lev = 1f - levenshtein(aa, bb).toFloat() / max(aa.length, bb.length).coerceAtLeast(1).toFloat()
        return (0.58f * lev + 0.42f * jaccard).coerceIn(0f, 1f)
    }

    private fun normaliseLabel(text: String): String = text.lowercase()
        .replace(Regex("[^a-z0-9]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun levenshtein(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            val current = IntArray(b.length + 1)
            current[0] = i + 1
            for (j in b.indices) {
                val cost = if (a[i] == b[j]) 0 else 1
                current[j + 1] = min(min(current[j] + 1, previous[j + 1] + 1), previous[j] + cost)
            }
            previous = current
        }
        return previous[b.length]
    }

    private fun centreInside(inner: NormalisedRoi, outer: NormalisedRoi): Boolean {
        val x = (inner.left + inner.right) / 2f
        val y = (inner.top + inner.bottom) / 2f
        return x in outer.left..outer.right && y in outer.top..outer.bottom
    }
    private fun centreY(roi: NormalisedRoi): Float = (roi.top + roi.bottom) / 2f
    private fun roiArea(roi: NormalisedRoi): Float = (roi.right - roi.left).coerceAtLeast(0f) * (roi.bottom - roi.top).coerceAtLeast(0f)
    private fun overlapFraction(a: NormalisedRoi, b: NormalisedRoi): Float {
        val l = max(a.left, b.left); val t = max(a.top, b.top); val r = min(a.right, b.right); val bt = min(a.bottom, b.bottom)
        if (r <= l || bt <= t) return 0f
        val intersection = (r - l) * (bt - t)
        return intersection / min(roiArea(a), roiArea(b)).coerceAtLeast(0.000001f)
    }
    private fun luma(p: Int): Int {
        val r = Color.red(p); val g = Color.green(p); val b = Color.blue(p)
        return (299 * r + 587 * g + 114 * b) / 1000
    }
    private fun isNear(pixel: Int, target: Int, tolerance: Int): Boolean {
        val dr = Color.red(pixel) - Color.red(target)
        val dg = Color.green(pixel) - Color.green(target)
        val db = Color.blue(pixel) - Color.blue(target)
        return dr * dr + dg * dg + db * db <= tolerance * tolerance
    }
}
