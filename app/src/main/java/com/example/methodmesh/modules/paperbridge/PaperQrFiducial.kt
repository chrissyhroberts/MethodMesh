package com.example.methodmesh.modules.paperbridge

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import java.security.MessageDigest

/**
 * QR4 is retained for backward compatibility with earlier Paper Bridge schemas.
 *
 * The four QR centres define the canonical analysis rectangle. The payloads also
 * carry a short schema key so a sheet can be associated with its schema without
 * relying on visual shape classification.
 *
 * QR generation is intentionally module-local and dependency-free. It emits a
 * standards-compliant Version 1 / error-correction L symbol, which is sufficient
 * for the compact `PB:<schemaKey>:<corner>` payloads used here. Runtime decoding
 * uses the ML Kit barcode dependency that MethodMesh already ships.
 */
internal object PaperQrFiducial {
    const val TYPE = "qr4"
    private const val QR_VERSION = 1
    private const val QR_SIZE = 21
    private const val DATA_CODEWORDS = 19
    private const val ECC_CODEWORDS = 7
    private const val QUIET_MODULES = 4

    private data class Corner(
        val name: String,
        val suffix: String,
        val centre: Pair<Float, Float>,
        val searchRect: (Int, Int) -> Rect
    )

    private val defaultCentres = listOf(
        0.065f to 0.065f,
        0.935f to 0.065f,
        0.935f to 0.935f,
        0.065f to 0.935f
    )

    fun defaultRegistration(schemaKey: String): PaperRegistrationSpec = PaperRegistrationSpec(
        type = PaperRegistrationType.QR4,
        schemaKey = schemaKey,
        markerSizeFraction = 0.075f,
        centres = defaultCentres
    )

    fun newSchemaKey(): String = java.util.UUID.randomUUID().toString()
        .replace("-", "")
        .take(6)
        .uppercase()

    fun deterministicSchemaKey(templateId: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(templateId.toByteArray(Charsets.UTF_8))
        return digest.take(3).joinToString("") { "%02X".format(it) }
    }

    fun payload(schemaKey: String, suffix: String): String = "PB:${schemaKey.uppercase()}:$suffix"

    fun payloads(spec: PaperRegistrationSpec): Map<String, String> = linkedMapOf(
        "top-left" to payload(spec.schemaKey, "TL"),
        "top-right" to payload(spec.schemaKey, "TR"),
        "bottom-right" to payload(spec.schemaKey, "BR"),
        "bottom-left" to payload(spec.schemaKey, "BL")
    )

    /**
     * Create a printable page from an existing canonical artwork image.
     *
     * The artwork is scaled into the rectangle between the four QR centres. Full QR
     * symbols are then drawn at those centres. Cropping the prepared page centre-to-
     * centre therefore recovers exactly the canonical artwork coordinate system.
     */
    fun preparePage(sourceCanonical: Bitmap, spec: PaperRegistrationSpec): Bitmap {
        require(spec.type == PaperRegistrationType.QR4) { "QR preparation requires qr4 registration." }
        val output = Bitmap.createBitmap(sourceCanonical.width, sourceCanonical.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.WHITE)
        val centres = spec.centres
        require(centres.size == 4) { "QR4 requires four centres." }
        val left = ((centres[0].first + centres[3].first) / 2f) * output.width
        val right = ((centres[1].first + centres[2].first) / 2f) * output.width
        val top = ((centres[0].second + centres[1].second) / 2f) * output.height
        val bottom = ((centres[2].second + centres[3].second) / 2f) * output.height
        val content = RectF(left, top, right, bottom)
        canvas.drawBitmap(
            sourceCanonical,
            null,
            content,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        )

        val markerPx = (min(output.width, output.height) * spec.markerSizeFraction)
            .roundToInt()
            .coerceIn(72, 220)
        val entries = listOf("TL", "TR", "BR", "BL")
        centres.forEachIndexed { index, centre ->
            drawQr(canvas, payload(spec.schemaKey, entries[index]), centre.first * output.width, centre.second * output.height, markerPx)
        }
        return output
    }

    /** Crop a prepared QR page by the known centre coordinates without decoding it. */
    fun canonicalFromPrepared(
        prepared: Bitmap,
        spec: PaperRegistrationSpec,
        widthPx: Int,
        heightPx: Int
    ): Bitmap {
        val bounds = knownCentreBounds(prepared, spec)
        val out = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            drawColor(Color.WHITE)
            drawBitmap(prepared, bounds, Rect(0, 0, widthPx, heightPx), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }
        return out
    }

    /**
     * Detect the four expected schema-specific QR codes and register the page.
     * ML Kit document scans are already flattened, so they are cropped axis-aligned.
     * Raw/fallback images retain the legacy four-point perspective correction.
     */
    suspend fun rectify(
        source: Bitmap,
        template: PaperTemplate,
        mlKitPreprocessed: Boolean
    ): PaperRectification {
        val spec = template.registration
        require(spec.type == PaperRegistrationType.QR4) { "QR registration requested for a non-QR schema." }
        val anchors = detectExpected(source, spec)
        validateGeometry(anchors, source.width, source.height)
        if (mlKitPreprocessed) {
            val bounds = centreBounds(source, anchors)
            val registered = Bitmap.createBitmap(template.pageWidthPx, template.pageHeightPx, Bitmap.Config.ARGB_8888)
            Canvas(registered).apply {
                drawColor(Color.WHITE)
                drawBitmap(source, bounds, Rect(0, 0, template.pageWidthPx, template.pageHeightPx), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            }
            return PaperRectification(
                bitmap = registered,
                anchors = anchors,
                sourceWidth = source.width,
                sourceHeight = source.height,
                registrationMode = "mlkit_qr4_centre_crop_resize"
            )
        }

        val src = FloatArray(8)
        val dst = floatArrayOf(
            0f, 0f,
            (template.pageWidthPx - 1).coerceAtLeast(1).toFloat(), 0f,
            (template.pageWidthPx - 1).coerceAtLeast(1).toFloat(), (template.pageHeightPx - 1).coerceAtLeast(1).toFloat(),
            0f, (template.pageHeightPx - 1).coerceAtLeast(1).toFloat()
        )
        anchors.forEachIndexed { index, evidence ->
            src[index * 2] = evidence.point.x
            src[index * 2 + 1] = evidence.point.y
        }
        val matrix = Matrix()
        require(matrix.setPolyToPoly(src, 0, dst, 0, 4)) { "Could not compute QR4 perspective transform." }
        val registered = Bitmap.createBitmap(template.pageWidthPx, template.pageHeightPx, Bitmap.Config.ARGB_8888)
        Canvas(registered).apply {
            drawColor(Color.WHITE)
            drawBitmap(source, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }
        return PaperRectification(
            bitmap = registered,
            anchors = anchors,
            sourceWidth = source.width,
            sourceHeight = source.height,
            registrationMode = "qr4_four_point_perspective"
        )
    }

    /** Return true only if all four exact payloads for this schema are present. */
    suspend fun hasExpectedMarkers(source: Bitmap, spec: PaperRegistrationSpec): Boolean =
        runCatching { detectExpected(source, spec); true }.getOrDefault(false)

    suspend fun detectExpected(source: Bitmap, spec: PaperRegistrationSpec): List<PaperAnchorEvidence> {
        require(spec.schemaKey.isNotBlank()) { "QR4 schema key is blank." }
        val centres = spec.centres.ifEmpty { defaultCentres }
        require(centres.size == 4) { "QR4 requires exactly four marker centres." }
        val corners = listOf(
            Corner("top-left", "TL", centres[0]) { w, h -> Rect(0, 0, (w * 0.30f).roundToInt(), (h * 0.30f).roundToInt()) },
            Corner("top-right", "TR", centres[1]) { w, h -> Rect((w * 0.70f).roundToInt(), 0, w, (h * 0.30f).roundToInt()) },
            Corner("bottom-right", "BR", centres[2]) { w, h -> Rect((w * 0.70f).roundToInt(), (h * 0.70f).roundToInt(), w, h) },
            Corner("bottom-left", "BL", centres[3]) { w, h -> Rect(0, (h * 0.70f).roundToInt(), (w * 0.30f).roundToInt(), h) }
        )
        return corners.map { corner -> detectCorner(source, spec, corner) }
    }

    private suspend fun detectCorner(source: Bitmap, spec: PaperRegistrationSpec, corner: Corner): PaperAnchorEvidence {
        val region = corner.searchRect(source.width, source.height)
        val crop = Bitmap.createBitmap(source, region.left, region.top, region.width(), region.height())
        try {
            val expected = payload(spec.schemaKey, corner.suffix)
            val attempts = mutableListOf<Pair<Bitmap, Float>>()
            attempts += crop to 1f
            // Screen photographs and low-resolution scans can leave a fiducial only
            // a few dozen pixels wide. Give ML Kit several larger views rather than
            // failing after one modest upscale. Nearest-neighbour preserves QR module
            // edges; the filtered and binary views help with camera blur / display moire.
            val scale = min(6.0f, 2400f / max(crop.width, crop.height).toFloat()).coerceAtLeast(1f)
            if (scale > 1.05f) {
                val scaledNearest = Bitmap.createScaledBitmap(
                    crop,
                    (crop.width * scale).roundToInt().coerceAtLeast(1),
                    (crop.height * scale).roundToInt().coerceAtLeast(1),
                    false
                )
                attempts += scaledNearest to scale
                val scaledFiltered = Bitmap.createScaledBitmap(
                    crop,
                    (crop.width * scale).roundToInt().coerceAtLeast(1),
                    (crop.height * scale).roundToInt().coerceAtLeast(1),
                    true
                )
                attempts += scaledFiltered to scale
                attempts += qrBinaryView(scaledFiltered) to scale
            } else {
                attempts += qrBinaryView(crop) to 1f
            }
            var paperBridgeWrongSchema: String? = null
            try {
                attempts.forEach { (attempt, attemptScale) ->
                    val hits = recognise(attempt)
                    hits.forEach { hit ->
                        val raw = hit.rawValue.orEmpty()
                        if (raw.startsWith("PB:", ignoreCase = true) && raw.endsWith(":${corner.suffix}", ignoreCase = true) && raw != expected) {
                            paperBridgeWrongSchema = raw
                        }
                        if (raw == expected) {
                            val points = hit.cornerPoints.orEmpty()
                            val box = hit.boundingBox
                            val localCentre = when {
                                points.isNotEmpty() -> PointF(points.map { it.x }.average().toFloat(), points.map { it.y }.average().toFloat())
                                box != null -> PointF(box.exactCenterX(), box.exactCenterY())
                                else -> PointF(attempt.width / 2f, attempt.height / 2f)
                            }
                            val centre = PointF(
                                region.left + localCentre.x / attemptScale,
                                region.top + localCentre.y / attemptScale
                            )
                            val bw = ((box?.width() ?: (attempt.width * 0.10f).roundToInt()) / attemptScale).roundToInt().coerceAtLeast(1)
                            val bh = ((box?.height() ?: (attempt.height * 0.10f).roundToInt()) / attemptScale).roundToInt().coerceAtLeast(1)
                            return PaperAnchorEvidence(
                                corner = corner.name,
                                point = centre,
                                score = 1f,
                                componentArea = bw * bh,
                                boundingWidth = bw,
                                boundingHeight = bh
                            )
                        }
                    }
                }
            } finally {
                attempts.drop(1).forEach { (bitmap, _) -> if (!bitmap.isRecycled) bitmap.recycle() }
            }
            if (paperBridgeWrongSchema != null) {
                throw IllegalArgumentException("QR marker in ${corner.name} belongs to another Paper Bridge schema ($paperBridgeWrongSchema).")
            }
            throw IllegalArgumentException("Paper Bridge QR marker ${corner.suffix} was not decoded in the ${corner.name} region. Keep the full marker and quiet zone visible; use the QR-prepared form at roughly A4 size or move closer.")
        } finally {
            if (!crop.isRecycled) crop.recycle()
        }
    }

    private fun qrBinaryView(source: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(source.width * source.height)
        source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        // QR crops are dominated by white paper. Derive a conservative threshold from
        // their upper luminance distribution so grey screen pixels become white while
        // black QR modules stay black.
        val sampleStep = max(1, pixels.size / 6000)
        val samples = ArrayList<Int>(min(6000, pixels.size / sampleStep + 1))
        var i = 0
        while (i < pixels.size) {
            val c = pixels[i]
            val l = (Color.red(c) * 77 + Color.green(c) * 150 + Color.blue(c) * 29) shr 8
            samples += l
            i += sampleStep
        }
        samples.sort()
        val paper = samples.getOrElse((samples.size * 0.80f).roundToInt().coerceIn(0, samples.lastIndex)) { 235 }
        val threshold = (paper - 58).coerceIn(115, 205)
        for (index in pixels.indices) {
            val c = pixels[index]
            val l = (Color.red(c) * 77 + Color.green(c) * 150 + Color.blue(c) * 29) shr 8
            pixels[index] = if (l < threshold) Color.BLACK else Color.WHITE
        }
        out.setPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        return out
    }

    private suspend fun recognise(bitmap: Bitmap): List<com.google.mlkit.vision.barcode.common.Barcode> =
        suspendCancellableCoroutine { continuation ->
            val scanner = BarcodeScanning.getClient()
            scanner.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { hits ->
                    scanner.close()
                    if (continuation.isActive) continuation.resume(hits)
                }
                .addOnFailureListener { error ->
                    scanner.close()
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
        }

    private fun knownCentreBounds(source: Bitmap, spec: PaperRegistrationSpec): Rect {
        val centres = spec.centres.ifEmpty { defaultCentres }
        val left = ((centres[0].first + centres[3].first) / 2f * source.width).roundToInt().coerceIn(0, source.width - 2)
        val right = ((centres[1].first + centres[2].first) / 2f * source.width).roundToInt().coerceIn(left + 1, source.width)
        val top = ((centres[0].second + centres[1].second) / 2f * source.height).roundToInt().coerceIn(0, source.height - 2)
        val bottom = ((centres[2].second + centres[3].second) / 2f * source.height).roundToInt().coerceIn(top + 1, source.height)
        return Rect(left, top, right, bottom)
    }

    private fun centreBounds(source: Bitmap, anchors: List<PaperAnchorEvidence>): Rect {
        val tl = anchors[0].point; val tr = anchors[1].point; val br = anchors[2].point; val bl = anchors[3].point
        val left = ((tl.x + bl.x) / 2f).roundToInt().coerceIn(0, source.width - 2)
        val right = ((tr.x + br.x) / 2f).roundToInt().coerceIn(left + 1, source.width)
        val top = ((tl.y + tr.y) / 2f).roundToInt().coerceIn(0, source.height - 2)
        val bottom = ((br.y + bl.y) / 2f).roundToInt().coerceIn(top + 1, source.height)
        require(right - left > source.width * 0.25f && bottom - top > source.height * 0.25f) {
            "QR centres do not define a credible analysis rectangle."
        }
        return Rect(left, top, right, bottom)
    }

    private fun validateGeometry(anchors: List<PaperAnchorEvidence>, width: Int, height: Int) {
        require(anchors.size == 4) { "Four QR fiducials are required." }
        val tl = anchors[0].point; val tr = anchors[1].point; val br = anchors[2].point; val bl = anchors[3].point
        require(tl.x < tr.x && bl.x < br.x && tl.y < bl.y && tr.y < br.y) { "QR fiducials are not in TL/TR/BR/BL order." }
        val horizontal = min(tr.x - tl.x, br.x - bl.x)
        val vertical = min(bl.y - tl.y, br.y - tr.y)
        require(horizontal > width * 0.35f && vertical > height * 0.35f) { "QR fiducials are too close together to define the page." }
        val topSlope = abs(tl.y - tr.y) / max(1f, tr.x - tl.x)
        val bottomSlope = abs(bl.y - br.y) / max(1f, br.x - bl.x)
        if (topSlope > 0.22f || bottomSlope > 0.22f) {
            // Raw fallback can correct this; ML Kit normally removes most slope. Keep the
            // geometry accepted but the audit will show exact marker locations.
        }
    }

    private fun drawQr(canvas: Canvas, value: String, centreX: Float, centreY: Float, pixelSize: Int) {
        val matrix = encodeVersion1L(value)
        val totalModules = QR_SIZE + QUIET_MODULES * 2
        val module = max(1f, pixelSize.toFloat() / totalModules)
        val actual = module * totalModules
        val left = centreX - actual / 2f
        val top = centreY - actual / 2f
        val white = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
        val black = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
        canvas.drawRect(left, top, left + actual, top + actual, white)
        for (y in 0 until QR_SIZE) {
            for (x in 0 until QR_SIZE) {
                if (!matrix[y][x]) continue
                val x0 = left + (x + QUIET_MODULES) * module
                val y0 = top + (y + QUIET_MODULES) * module
                canvas.drawRect(x0, y0, x0 + module + 0.02f, y0 + module + 0.02f, black)
            }
        }
    }

    /** Fixed Version-1/L encoder for compact ASCII payloads (maximum 17 bytes). */
    private fun encodeVersion1L(value: String): Array<BooleanArray> {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size <= 17) { "Paper Bridge QR payload is too long for the compact fiducial encoder." }
        val bits = ArrayList<Boolean>(DATA_CODEWORDS * 8)
        fun append(valueBits: Int, count: Int) {
            for (i in count - 1 downTo 0) bits += ((valueBits ushr i) and 1) != 0
        }
        append(0x4, 4) // byte mode
        append(bytes.size, 8)
        bytes.forEach { append(it.toInt() and 0xFF, 8) }
        repeat(min(4, DATA_CODEWORDS * 8 - bits.size)) { bits += false }
        while (bits.size % 8 != 0) bits += false
        val data = ArrayList<Int>(DATA_CODEWORDS)
        var offset = 0
        while (offset < bits.size) {
            var b = 0
            repeat(8) { index -> if (bits[offset + index]) b = b or (1 shl (7 - index)) }
            data += b
            offset += 8
        }
        var pad = 0
        while (data.size < DATA_CODEWORDS) {
            data += if (pad++ % 2 == 0) 0xEC else 0x11
        }
        val divisor = reedSolomonDivisor(ECC_CODEWORDS)
        val ecc = reedSolomonRemainder(data, divisor)
        val codewords = (data + ecc).toIntArray()

        var best: Array<BooleanArray>? = null
        var bestPenalty = Int.MAX_VALUE
        for (mask in 0..7) {
            val modules = Array(QR_SIZE) { BooleanArray(QR_SIZE) }
            val function = Array(QR_SIZE) { BooleanArray(QR_SIZE) }
            drawFunctionPatterns(modules, function, mask)
            drawCodewords(modules, function, codewords)
            applyMask(modules, function, mask)
            drawFormatBits(modules, function, mask)
            val penalty = penaltyScore(modules)
            if (penalty < bestPenalty) {
                bestPenalty = penalty
                best = Array(QR_SIZE) { y -> modules[y].clone() }
            }
        }
        return requireNotNull(best)
    }

    private fun drawFunctionPatterns(modules: Array<BooleanArray>, function: Array<BooleanArray>, mask: Int) {
        fun set(x: Int, y: Int, dark: Boolean) {
            if (x !in 0 until QR_SIZE || y !in 0 until QR_SIZE) return
            modules[y][x] = dark
            function[y][x] = true
        }
        for (i in 0 until QR_SIZE) {
            set(6, i, i % 2 == 0)
            set(i, 6, i % 2 == 0)
        }
        fun finder(cx: Int, cy: Int) {
            for (dy in -4..4) for (dx in -4..4) {
                val dist = max(abs(dx), abs(dy))
                set(cx + dx, cy + dy, dist != 2 && dist != 4)
            }
        }
        finder(3, 3)
        finder(QR_SIZE - 4, 3)
        finder(3, QR_SIZE - 4)
        drawFormatBits(modules, function, mask)
        set(8, QR_SIZE - 8, true)
    }

    private fun drawFormatBits(modules: Array<BooleanArray>, function: Array<BooleanArray>, mask: Int) {
        val data = (1 shl 3) or mask // error-correction level L has format bits 01
        var rem = data
        repeat(10) { rem = (rem shl 1) xor ((rem ushr 9) * 0x537) }
        val bits = ((data shl 10) or rem) xor 0x5412
        fun bit(i: Int) = ((bits ushr i) and 1) != 0
        fun set(x: Int, y: Int, dark: Boolean) {
            modules[y][x] = dark
            function[y][x] = true
        }
        for (i in 0..5) set(8, i, bit(i))
        set(8, 7, bit(6))
        set(8, 8, bit(7))
        set(7, 8, bit(8))
        for (i in 9..14) set(14 - i, 8, bit(i))
        for (i in 0..7) set(QR_SIZE - 1 - i, 8, bit(i))
        for (i in 8..14) set(8, QR_SIZE - 15 + i, bit(i))
        set(8, QR_SIZE - 8, true)
    }

    private fun drawCodewords(modules: Array<BooleanArray>, function: Array<BooleanArray>, codewords: IntArray) {
        var bitIndex = 0
        var right = QR_SIZE - 1
        while (right >= 1) {
            if (right == 6) right--
            for (vert in 0 until QR_SIZE) {
                val upward = ((right + 1) and 2) == 0
                val y = if (upward) QR_SIZE - 1 - vert else vert
                for (j in 0..1) {
                    val x = right - j
                    if (!function[y][x] && bitIndex < codewords.size * 8) {
                        modules[y][x] = ((codewords[bitIndex ushr 3] ushr (7 - (bitIndex and 7))) and 1) != 0
                        bitIndex++
                    }
                }
            }
            right -= 2
        }
        require(bitIndex == codewords.size * 8) { "QR data placement did not consume all codewords." }
    }

    private fun applyMask(modules: Array<BooleanArray>, function: Array<BooleanArray>, mask: Int) {
        for (y in 0 until QR_SIZE) for (x in 0 until QR_SIZE) {
            if (function[y][x]) continue
            val invert = when (mask) {
                0 -> (x + y) % 2 == 0
                1 -> y % 2 == 0
                2 -> x % 3 == 0
                3 -> (x + y) % 3 == 0
                4 -> (x / 3 + y / 2) % 2 == 0
                5 -> (x * y % 2 + x * y % 3) == 0
                6 -> (x * y % 2 + x * y % 3) % 2 == 0
                7 -> ((x + y) % 2 + x * y % 3) % 2 == 0
                else -> false
            }
            if (invert) modules[y][x] = !modules[y][x]
        }
    }

    private fun penaltyScore(m: Array<BooleanArray>): Int {
        var result = 0
        // Runs in rows/columns.
        for (y in 0 until QR_SIZE) {
            var run = 1
            for (x in 1 until QR_SIZE) {
                if (m[y][x] == m[y][x - 1]) run++ else { if (run >= 5) result += 3 + run - 5; run = 1 }
            }
            if (run >= 5) result += 3 + run - 5
        }
        for (x in 0 until QR_SIZE) {
            var run = 1
            for (y in 1 until QR_SIZE) {
                if (m[y][x] == m[y - 1][x]) run++ else { if (run >= 5) result += 3 + run - 5; run = 1 }
            }
            if (run >= 5) result += 3 + run - 5
        }
        // 2x2 blocks.
        for (y in 0 until QR_SIZE - 1) for (x in 0 until QR_SIZE - 1) {
            val c = m[y][x]
            if (m[y][x + 1] == c && m[y + 1][x] == c && m[y + 1][x + 1] == c) result += 3
        }
        // Finder-like 1:1:3:1:1 patterns with four white modules either side.
        val pattern = booleanArrayOf(true, false, true, true, true, false, true)
        fun finderPenalty(line: BooleanArray): Int {
            var score = 0
            for (i in 0..line.size - 7) {
                var match = true
                for (j in 0..6) if (line[i + j] != pattern[j]) { match = false; break }
                if (!match) continue
                val before = i >= 4 && (i - 4 until i).all { !line[it] }
                val after = i + 11 <= line.size && (i + 7 until i + 11).all { !line[it] }
                if (before || after) score += 40
            }
            return score
        }
        for (y in 0 until QR_SIZE) result += finderPenalty(m[y])
        for (x in 0 until QR_SIZE) result += finderPenalty(BooleanArray(QR_SIZE) { y -> m[y][x] })
        val dark = m.sumOf { row -> row.count { it } }
        val total = QR_SIZE * QR_SIZE
        val k = abs(dark * 20 - total * 10) / total
        result += k * 10
        return result
    }

    private fun reedSolomonDivisor(degree: Int): IntArray {
        val result = IntArray(degree)
        result[degree - 1] = 1
        var root = 1
        repeat(degree) {
            for (j in result.indices) {
                result[j] = gfMultiply(result[j], root)
                if (j + 1 < result.size) result[j] = result[j] xor result[j + 1]
            }
            root = gfMultiply(root, 0x02)
        }
        return result
    }

    private fun reedSolomonRemainder(data: List<Int>, divisor: IntArray): List<Int> {
        val result = IntArray(divisor.size)
        data.forEach { b ->
            val factor = b xor result[0]
            for (i in 0 until result.lastIndex) result[i] = result[i + 1]
            result[result.lastIndex] = 0
            for (i in divisor.indices) result[i] = result[i] xor gfMultiply(divisor[i], factor)
        }
        return result.toList()
    }

    private fun gfMultiply(x: Int, y: Int): Int {
        var z = 0
        for (i in 7 downTo 0) {
            z = (z shl 1) xor ((z ushr 7) * 0x11D)
            if (((y ushr i) and 1) != 0) z = z xor x
        }
        return z and 0xFF
    }
}
