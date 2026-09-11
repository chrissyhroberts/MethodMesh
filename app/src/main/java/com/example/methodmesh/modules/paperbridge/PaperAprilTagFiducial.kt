package com.example.methodmesh.modules.paperbridge

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Paper Bridge geometric registration based on eight uniquely identified
 * AprilTag 36h11 markers distributed around the printable page perimeter.
 *
 * The implementation is deliberately module-local: it renders the eight tags
 * used by Paper Bridge and decodes only those IDs, avoiding an app-wide native
 * OpenCV/AprilTag dependency.  Each detected tag contributes its four outer
 * corners, so a complete page supplies 32 point correspondences for one robust
 * projective transform into the canonical template coordinate system.
 *
 * QR remains available separately for form/schema identity. It is not used to
 * determine geometry for APRILTAG8 templates.
 */
internal object PaperAprilTagFiducial {
    const val TYPE = "apriltag8"
    const val FAMILY = "tag36h11"

    // TL, TC, TR, LC, RC, BL, BC, BR. Kept inside ordinary printer safe margins.
    private val defaultCentres = listOf(
        0.060f to 0.060f,
        0.500f to 0.060f,
        0.940f to 0.060f,
        0.060f to 0.500f,
        0.940f to 0.500f,
        0.060f to 0.940f,
        0.500f to 0.940f,
        0.940f to 0.940f
    )

    val defaultIds = listOf(0, 1, 2, 3, 4, 5, 6, 7)
    val labels = listOf("TL", "TC", "TR", "LC", "RC", "BL", "BC", "BR")

    // OpenCV DICT_APRILTAG_36h11 IDs 0..7. 0 = black, 1 = white.
    // Each matrix is the 6x6 payload; renderTag adds the required one-cell black border.
    private val payloadBits: Array<IntArray> = arrayOf(
        intArrayOf(0,0,1,0,0,0, 0,1,1,0,1,0, 0,0,0,1,0,1, 0,0,0,1,1,0, 1,0,1,1,1,0, 1,0,1,0,1,1),
        intArrayOf(1,0,0,1,0,0, 1,0,1,1,0,1, 0,0,0,1,1,0, 0,0,1,1,1,1, 1,1,1,0,1,0, 0,1,1,0,1,1),
        intArrayOf(0,1,1,1,0,0, 0,0,1,0,0,0, 1,0,0,1,0,0, 0,0,0,0,0,1, 0,1,0,0,1,0, 1,1,1,0,1,1),
        intArrayOf(0,0,0,1,1,0, 0,1,0,0,1,1, 1,0,0,1,0,1, 1,1,1,0,0,1, 1,1,1,0,0,0, 1,0,0,1,1,1),
        intArrayOf(0,1,0,0,0,1, 0,0,0,0,0,1, 0,1,0,1,0,0, 1,1,1,1,0,1, 0,0,1,1,1,1, 0,1,0,1,1,1),
        intArrayOf(0,0,1,1,0,1, 0,1,1,1,0,0, 1,1,0,1,0,1, 0,1,1,0,1,1, 1,0,0,0,1,1, 0,0,1,1,1,1),
        intArrayOf(1,0,1,0,0,0, 0,1,0,0,0,0, 1,0,1,1,1,0, 1,0,0,1,0,1, 0,1,1,0,1,0, 1,0,0,0,0,0),
        intArrayOf(0,0,1,0,1,0, 1,1,1,0,0,0, 0,1,1,1,0,1, 0,0,1,0,1,0, 0,1,1,0,0,0, 0,0,1,0,0,0)
    )

    fun defaultRegistration(schemaKey: String = ""): PaperRegistrationSpec = PaperRegistrationSpec(
        type = PaperRegistrationType.APRILTAG8,
        schemaKey = schemaKey,
        markerSizeFraction = 0.065f,
        centres = defaultCentres,
        markerIds = defaultIds
    )

    fun preparePage(sourceCanonical: Bitmap, spec: PaperRegistrationSpec): Bitmap {
        require(spec.type == PaperRegistrationType.APRILTAG8) { "AprilTag preparation requires apriltag8 registration." }
        val ids = spec.markerIds.ifEmpty { defaultIds }
        require(ids.size == 8 && ids.distinct().size == 8) { "APRILTAG8 requires eight unique marker IDs." }

        // Registration lives in a dedicated printable border, not on top of form
        // content. This preserves the canonical artwork and every saved ROI 1:1.
        val margin = registrationMarginPixels(sourceCanonical.width, sourceCanonical.height, spec.markerSizeFraction)
        val output = Bitmap.createBitmap(sourceCanonical.width + margin * 2, sourceCanonical.height + margin * 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(sourceCanonical, margin.toFloat(), margin.toFloat(), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        val bodyPx = markerBodyPixels(sourceCanonical.width, sourceCanonical.height, spec.markerSizeFraction)
        val centres = preparedCentres(output.width, output.height, margin)
        centres.forEachIndexed { index, centre -> drawTag(canvas, ids[index], centre.x, centre.y, bodyPx) }
        return output
    }

    /** The canonical artwork is not rescaled for APRILTAG8; tags are an overlay in the page margin. */
    fun canonicalFromPrepared(prepared: Bitmap, widthPx: Int, heightPx: Int): Bitmap {
        val marginX = ((prepared.width - widthPx) / 2).coerceAtLeast(0)
        val marginY = ((prepared.height - heightPx) / 2).coerceAtLeast(0)
        if (marginX > 0 && marginY > 0 && marginX * 2 + widthPx <= prepared.width && marginY * 2 + heightPx <= prepared.height) {
            return Bitmap.createBitmap(prepared, marginX, marginY, widthPx, heightPx)
        }
        return Bitmap.createScaledBitmap(prepared, widthPx, heightPx, true)
    }


    fun hasExpectedMarkers(source: Bitmap, spec: PaperRegistrationSpec): Boolean =
        runCatching { detect(source, spec).size >= 4 }.getOrDefault(false)

    fun rectify(source: Bitmap, template: PaperTemplate, mlKitPreprocessed: Boolean): PaperRectification {
        val spec = template.registration
        require(spec.type == PaperRegistrationType.APRILTAG8) { "AprilTag registration requested for a non-AprilTag schema." }
        val detections = detect(source, spec)
        validateSpatialDistribution(detections, spec)

        val bodyPxCanonical = markerBodyPixels(template.pageWidthPx, template.pageHeightPx, spec.markerSizeFraction).toFloat()
        val half = bodyPxCanonical / 2f
        val margin = registrationMarginPixels(template.pageWidthPx, template.pageHeightPx, spec.markerSizeFraction)
        val preparedWidth = template.pageWidthPx + margin * 2
        val preparedHeight = template.pageHeightPx + margin * 2
        val canonicalCentres = preparedCentres(preparedWidth, preparedHeight, margin)
        val idToIndex = (spec.markerIds.ifEmpty { defaultIds }).mapIndexed { index, id -> id to index }.toMap()

        val correspondences = mutableListOf<Pair<PointF, PointF>>()
        detections.forEach { detection ->
            val index = idToIndex[detection.id] ?: return@forEach
            val centre = canonicalCentres[index]
            val cx = centre.x
            val cy = centre.y
            val dst = listOf(
                PointF(cx - half, cy - half),
                PointF(cx + half, cy - half),
                PointF(cx + half, cy + half),
                PointF(cx - half, cy + half)
            )
            detection.corners.zip(dst).forEach { correspondences += it }
        }
        require(correspondences.size >= 16) { "At least four AprilTags (16 corner points) are required for trusted registration." }

        val robust = robustHomography(correspondences)
        val matrix = Matrix().apply {
            setValues(floatArrayOf(
                robust.h[0].toFloat(), robust.h[1].toFloat(), robust.h[2].toFloat(),
                robust.h[3].toFloat(), robust.h[4].toFloat(), robust.h[5].toFloat(),
                robust.h[6].toFloat(), robust.h[7].toFloat(), 1f
            ))
        }
        val preparedCanonical = Bitmap.createBitmap(preparedWidth, preparedHeight, Bitmap.Config.ARGB_8888)
        Canvas(preparedCanonical).apply {
            drawColor(Color.WHITE)
            drawBitmap(source, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }
        val registered = Bitmap.createBitmap(preparedCanonical, margin, margin, template.pageWidthPx, template.pageHeightPx)
        if (!preparedCanonical.isRecycled) preparedCanonical.recycle()

        val anchors = detections.map { d ->
            PaperAnchorEvidence(
                corner = labels.getOrNull(idToIndex[d.id] ?: -1) ?: "tag-${d.id}",
                point = PointF(d.corners.map { it.x }.average().toFloat(), d.corners.map { it.y }.average().toFloat()),
                score = d.score,
                componentArea = d.area,
                boundingWidth = d.width,
                boundingHeight = d.height
            )
        }
        val rmsLimit = max(3.0, min(template.pageWidthPx, template.pageHeightPx) * 0.004)
        val maxLimit = max(7.0, min(template.pageWidthPx, template.pageHeightPx) * 0.010)
        require(robust.rms <= rmsLimit && robust.maxError <= maxLimit) {
            "AprilTag registration residual is too large (RMS %.1f px, max %.1f px). Retake the page flatter and keep all perimeter tags visible.".format(robust.rms, robust.maxError)
        }
        val quality = when {
            detections.size >= 8 && robust.rms <= rmsLimit * 0.45 -> "excellent"
            detections.size >= 6 && robust.rms <= rmsLimit * 0.70 -> "very_good"
            else -> "acceptable"
        }
        return PaperRectification(
            bitmap = registered,
            anchors = anchors,
            sourceWidth = source.width,
            sourceHeight = source.height,
            registrationMode = if (mlKitPreprocessed) "apriltag8_robust_homography_after_mlkit" else "apriltag8_robust_homography",
            detectedMarkerCount = detections.size,
            expectedMarkerCount = 8,
            correspondenceCount = robust.inliers.size,
            reprojectionRmsPx = robust.rms.toFloat(),
            reprojectionMaxPx = robust.maxError.toFloat(),
            registrationQuality = quality,
            registrationPassed = true,
            residualVectors = robust.inliers.map { pair ->
                val p = project(robust.h, pair.first)
                PaperRegistrationResidual(pair.second, p)
            }
        )
    }

    private data class Detection(
        val id: Int,
        val corners: List<PointF>, // TL, TR, BR, BL in image coordinates
        val score: Float,
        val area: Int,
        val width: Int,
        val height: Int
    )

    private data class Candidate(val corners: List<PointF>, val area: Int, val width: Int, val height: Int)

    private fun detect(source: Bitmap, spec: PaperRegistrationSpec): List<Detection> {
        val maxDimension = max(source.width, source.height)
        val scale = min(1f, 1800f / maxDimension.toFloat())
        val bitmap = if (scale < 0.999f) Bitmap.createScaledBitmap(
            source,
            (source.width * scale).roundToInt().coerceAtLeast(1),
            (source.height * scale).roundToInt().coerceAtLeast(1),
            true
        ) else source
        try {
            val expectedBody = markerBodyPixels(bitmap.width, bitmap.height, spec.markerSizeFraction).coerceAtLeast(28)
            val centres = spec.centres.ifEmpty { defaultCentres }
            val expectedIds = spec.markerIds.ifEmpty { defaultIds }
            val detections = mutableListOf<Detection>()
            val usedIds = mutableSetOf<Int>()
            centres.forEachIndexed { regionIndex, centre ->
                val radiusX = max(expectedBody * 2.4f, bitmap.width * if (centre.first in 0.2f..0.8f) 0.16f else 0.14f)
                val radiusY = max(expectedBody * 2.4f, bitmap.height * if (centre.second in 0.2f..0.8f) 0.13f else 0.11f)
                val left = (centre.first * bitmap.width - radiusX).roundToInt().coerceIn(0, bitmap.width - 2)
                val right = (centre.first * bitmap.width + radiusX).roundToInt().coerceIn(left + 2, bitmap.width)
                val top = (centre.second * bitmap.height - radiusY).roundToInt().coerceIn(0, bitmap.height - 2)
                val bottom = (centre.second * bitmap.height + radiusY).roundToInt().coerceIn(top + 2, bitmap.height)
                val candidates = squareCandidates(bitmap, left, top, right, bottom, expectedBody)
                var best: Detection? = null
                candidates.forEach { candidate ->
                    val decoded = decodeCandidate(bitmap, candidate, expectedIds)
                    if (decoded != null && decoded.first !in usedIds) {
                        val (id, score) = decoded
                        val preferred = id == expectedIds.getOrNull(regionIndex)
                        val adjusted = (score + if (preferred) 0.08f else 0f).coerceAtMost(1f)
                        if (best == null || adjusted > best!!.score) {
                            best = Detection(id, candidate.corners, adjusted, candidate.area, candidate.width, candidate.height)
                        }
                    }
                }
                best?.takeIf { it.score >= 0.72f }?.let {
                    usedIds += it.id
                    detections += if (scale >= 0.999f) it else it.copy(
                        corners = it.corners.map { p -> PointF(p.x / scale, p.y / scale) },
                        area = (it.area / (scale * scale)).roundToInt(),
                        width = (it.width / scale).roundToInt(),
                        height = (it.height / scale).roundToInt()
                    )
                }
            }
            return detections.distinctBy { it.id }
        } finally {
            if (bitmap !== source && !bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun squareCandidates(bitmap: Bitmap, left: Int, top: Int, right: Int, bottom: Int, expectedBody: Int): List<Candidate> {
        val w = right - left
        val h = bottom - top
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, left, top, w, h)
        val threshold = otsuThreshold(pixels)
        val dark = BooleanArray(pixels.size) { i -> luma(pixels[i]) < threshold }
        val visited = BooleanArray(pixels.size)
        val stack = IntArray(pixels.size)
        val minDim = (expectedBody * 0.45f).roundToInt().coerceAtLeast(12)
        val maxDim = (expectedBody * 2.2f).roundToInt().coerceAtLeast(minDim + 1)
        val out = mutableListOf<Candidate>()
        for (start in dark.indices) {
            if (!dark[start] || visited[start]) continue
            var stackSize = 0
            stack[stackSize++] = start
            visited[start] = true
            var area = 0
            var minX = w; var minY = h; var maxX = 0; var maxY = 0
            var tl = start; var tr = start; var br = start; var bl = start
            var minSum = Int.MAX_VALUE; var maxSum = Int.MIN_VALUE
            var minDiff = Int.MAX_VALUE; var maxDiff = Int.MIN_VALUE
            while (stackSize > 0) {
                val index = stack[--stackSize]
                val x = index % w; val y = index / w
                area++
                if (x < minX) minX = x; if (x > maxX) maxX = x
                if (y < minY) minY = y; if (y > maxY) maxY = y
                val sum = x + y; val diff = x - y
                if (sum < minSum) { minSum = sum; tl = index }
                if (sum > maxSum) { maxSum = sum; br = index }
                if (diff > maxDiff) { maxDiff = diff; tr = index }
                if (diff < minDiff) { minDiff = diff; bl = index }
                val x0 = x > 0
                val x1 = x + 1 < w
                val y0 = y > 0
                val y1 = y + 1 < h
                if (x0) { val n=index-1; if (dark[n] && !visited[n]) { visited[n]=true; stack[stackSize++]=n } }
                if (x1) { val n=index+1; if (dark[n] && !visited[n]) { visited[n]=true; stack[stackSize++]=n } }
                if (y0) { val n=index-w; if (dark[n] && !visited[n]) { visited[n]=true; stack[stackSize++]=n } }
                if (y1) { val n=index+w; if (dark[n] && !visited[n]) { visited[n]=true; stack[stackSize++]=n } }
            }
            val bw = maxX - minX + 1; val bh = maxY - minY + 1
            if (bw !in minDim..maxDim || bh !in minDim..maxDim) continue
            val aspect = min(bw, bh).toFloat() / max(bw, bh).toFloat().coerceAtLeast(1f)
            if (aspect < 0.58f) continue
            val boxArea = bw * bh
            val fill = area.toFloat() / boxArea.toFloat().coerceAtLeast(1f)
            if (fill !in 0.20f..0.88f) continue
            fun point(index: Int): PointF = PointF(left + (index % w).toFloat(), top + (index / w).toFloat())
            out += Candidate(listOf(point(tl), point(tr), point(br), point(bl)), area, bw, bh)
        }
        return out.sortedByDescending { it.area }.take(24)
    }

    private fun decodeCandidate(bitmap: Bitmap, candidate: Candidate, allowedIds: List<Int>): Pair<Int, Float>? {
        var bestId = -1
        var bestMismatch = Int.MAX_VALUE
        var borderErrorsBest = Int.MAX_VALUE
        for (rotation in 0 until 4) {
            val sampled = IntArray(64)
            var borderErrors = 0
            for (gy in 0 until 8) {
                for (gx in 0 until 8) {
                    val logical = rotateCell(gx, gy, rotation)
                    val p = bilinearQuad(candidate.corners, (logical.first + 0.5f) / 8f, (logical.second + 0.5f) / 8f)
                    val dark = localDark(bitmap, p.x, p.y)
                    sampled[gy * 8 + gx] = if (dark) 0 else 1
                    if ((gx == 0 || gx == 7 || gy == 0 || gy == 7) && !dark) borderErrors++
                }
            }
            if (borderErrors > 8) continue
            allowedIds.forEach { id ->
                if (id !in payloadBits.indices) return@forEach
                var mismatch = 0
                val bits = payloadBits[id]
                for (y in 0 until 6) for (x in 0 until 6) {
                    if (sampled[(y + 1) * 8 + (x + 1)] != bits[y * 6 + x]) mismatch++
                }
                if (mismatch < bestMismatch || (mismatch == bestMismatch && borderErrors < borderErrorsBest)) {
                    bestMismatch = mismatch
                    borderErrorsBest = borderErrors
                    bestId = id
                }
            }
        }
        if (bestId < 0 || bestMismatch > 8) return null
        val score = (1f - bestMismatch / 36f) * (1f - borderErrorsBest / 32f)
        return bestId to score.coerceIn(0f, 1f)
    }

    private fun rotateCell(x: Int, y: Int, rotation: Int): Pair<Int, Int> = when (rotation.mod(4)) {
        1 -> 7 - y to x
        2 -> 7 - x to 7 - y
        3 -> y to 7 - x
        else -> x to y
    }

    private fun bilinearQuad(c: List<PointF>, u: Float, v: Float): PointF {
        val topX = c[0].x + (c[1].x - c[0].x) * u
        val topY = c[0].y + (c[1].y - c[0].y) * u
        val bottomX = c[3].x + (c[2].x - c[3].x) * u
        val bottomY = c[3].y + (c[2].y - c[3].y) * u
        return PointF(topX + (bottomX - topX) * v, topY + (bottomY - topY) * v)
    }

    private fun localDark(bitmap: Bitmap, x: Float, y: Float): Boolean {
        val cx = x.roundToInt().coerceIn(1, bitmap.width - 2)
        val cy = y.roundToInt().coerceIn(1, bitmap.height - 2)
        var sum = 0
        var n = 0
        for (dy in -1..1) for (dx in -1..1) { sum += luma(bitmap.getPixel(cx + dx, cy + dy)); n++ }
        return sum / n < 145
    }

    private fun validateSpatialDistribution(detections: List<Detection>, spec: PaperRegistrationSpec) {
        require(detections.size >= 4) { "Paper Bridge detected ${detections.size}/8 AprilTags. At least four well-distributed tags are required." }
        val ids = spec.markerIds.ifEmpty { defaultIds }
        val idToCentre = ids.zip(defaultCentres).toMap()
        val used = detections.mapNotNull { idToCentre[it.id] }
        val minX = used.minOf { it.first }; val maxX = used.maxOf { it.first }
        val minY = used.minOf { it.second }; val maxY = used.maxOf { it.second }
        val top = used.any { it.second < 0.25f }
        val bottom = used.any { it.second > 0.75f }
        val left = used.any { it.first < 0.25f }
        val right = used.any { it.first > 0.75f }
        require(maxX - minX > 0.60f && maxY - minY > 0.60f && top && bottom && left && right) {
            "AprilTags were found but are too spatially clustered for trusted registration. Keep tags visible around all sides of the page."
        }
    }

    private data class RobustFit(
        val h: DoubleArray,
        val inliers: List<Pair<PointF, PointF>>,
        val rms: Double,
        val maxError: Double
    )

    /** Iteratively reweighted all-point fit; gross corner outliers are discarded. */
    private fun robustHomography(input: List<Pair<PointF, PointF>>): RobustFit {
        var inliers = input.toList()
        repeat(5) {
            val h = solveHomography(inliers)
            val errors = inliers.map { distance(project(h, it.first), it.second) }
            val sorted = errors.sorted()
            val median = sorted[sorted.size / 2]
            val threshold = max(3.0, median * 3.5)
            val retained = inliers.zip(errors).filter { it.second <= threshold }.map { it.first }
            if (retained.size < 12 || retained.size == inliers.size) return@repeat
            inliers = retained
        }
        val h = solveHomography(inliers)
        val errors = inliers.map { distance(project(h, it.first), it.second) }
        val rms = sqrt(errors.sumOf { it * it } / errors.size.coerceAtLeast(1))
        return RobustFit(h, inliers, rms, errors.maxOrNull() ?: 0.0)
    }

    /** Least-squares projective transform, source -> destination, h33 fixed at 1. */
    private fun solveHomography(points: List<Pair<PointF, PointF>>): DoubleArray {
        require(points.size >= 4) { "At least four point pairs are required." }
        val ata = Array(8) { DoubleArray(8) }
        val atb = DoubleArray(8)
        fun accumulate(row: DoubleArray, b: Double) {
            for (i in 0 until 8) {
                atb[i] += row[i] * b
                for (j in 0 until 8) ata[i][j] += row[i] * row[j]
            }
        }
        points.forEach { (s, d) ->
            val x=s.x.toDouble(); val y=s.y.toDouble(); val u=d.x.toDouble(); val v=d.y.toDouble()
            accumulate(doubleArrayOf(x,y,1.0,0.0,0.0,0.0,-u*x,-u*y), u)
            accumulate(doubleArrayOf(0.0,0.0,0.0,x,y,1.0,-v*x,-v*y), v)
        }
        return gaussianSolve(ata, atb)
    }

    private fun gaussianSolve(aInput: Array<DoubleArray>, bInput: DoubleArray): DoubleArray {
        val n = bInput.size
        val a = Array(n) { i -> aInput[i].copyOf() }
        val b = bInput.copyOf()
        for (col in 0 until n) {
            var pivot = col
            for (r in col + 1 until n) if (abs(a[r][col]) > abs(a[pivot][col])) pivot = r
            require(abs(a[pivot][col]) > 1e-9) { "Registration geometry is singular." }
            if (pivot != col) {
                val tmp=a[pivot]; a[pivot]=a[col]; a[col]=tmp
                val tb=b[pivot]; b[pivot]=b[col]; b[col]=tb
            }
            val div=a[col][col]
            for (j in col until n) a[col][j] /= div
            b[col] /= div
            for (r in 0 until n) {
                if (r == col) continue
                val f=a[r][col]
                if (abs(f) < 1e-12) continue
                for (j in col until n) a[r][j] -= f*a[col][j]
                b[r] -= f*b[col]
            }
        }
        return b
    }

    private fun project(h: DoubleArray, p: PointF): PointF {
        val x=p.x.toDouble(); val y=p.y.toDouble()
        val w=h[6]*x + h[7]*y + 1.0
        return PointF(((h[0]*x+h[1]*y+h[2])/w).toFloat(), ((h[3]*x+h[4]*y+h[5])/w).toFloat())
    }

    private fun distance(a: PointF, b: PointF): Double {
        val dx=(a.x-b.x).toDouble(); val dy=(a.y-b.y).toDouble(); return sqrt(dx*dx+dy*dy)
    }

    private fun registrationMarginPixels(width: Int, height: Int, markerFraction: Float): Int {
        val body = markerBodyPixels(width, height, markerFraction)
        return max((body * 1.42f).roundToInt(), (min(width, height) * 0.085f).roundToInt())
    }

    private fun preparedCentres(width: Int, height: Int, margin: Int): List<PointF> {
        val edge = margin / 2f
        return listOf(
            PointF(edge, edge), PointF(width / 2f, edge), PointF(width - edge, edge),
            PointF(edge, height / 2f), PointF(width - edge, height / 2f),
            PointF(edge, height - edge), PointF(width / 2f, height - edge), PointF(width - edge, height - edge)
        )
    }

    private fun markerBodyPixels(width: Int, height: Int, fraction: Float): Int =
        (min(width, height) * fraction).roundToInt().coerceIn(64, 260)

    private fun drawTag(canvas: Canvas, id: Int, cx: Float, cy: Float, bodyPx: Int) {
        require(id in payloadBits.indices) { "Paper Bridge currently embeds AprilTag36h11 IDs 0..7." }
        val paint = Paint().apply { isAntiAlias = false; style = Paint.Style.FILL }
        val cell = bodyPx / 8f
        val quiet = cell * 1.25f
        paint.color = Color.WHITE
        canvas.drawRect(cx - bodyPx/2f - quiet, cy - bodyPx/2f - quiet, cx + bodyPx/2f + quiet, cy + bodyPx/2f + quiet, paint)
        val bits = payloadBits[id]
        for (y in 0 until 8) for (x in 0 until 8) {
            val black = x == 0 || x == 7 || y == 0 || y == 7 || bits[(y-1).coerceIn(0,5)*6 + (x-1).coerceIn(0,5)] == 0
            paint.color = if (black) Color.BLACK else Color.WHITE
            val l = cx - bodyPx/2f + x*cell
            val t = cy - bodyPx/2f + y*cell
            canvas.drawRect(l, t, l+cell+0.25f, t+cell+0.25f, paint)
        }
    }

    private fun otsuThreshold(pixels: IntArray): Int {
        val hist = IntArray(256)
        pixels.forEach { hist[luma(it)]++ }
        val total = pixels.size.toDouble().coerceAtLeast(1.0)
        var sum = 0.0
        for (i in 0..255) sum += i * hist[i].toDouble()
        var sumB = 0.0; var wB = 0.0; var best = 145; var bestVar = -1.0
        for (t in 0..255) {
            wB += hist[t]
            if (wB == 0.0) continue
            val wF = total - wB
            if (wF <= 0.0) break
            sumB += t * hist[t].toDouble()
            val mB=sumB/wB; val mF=(sum-sumB)/wF
            val between=wB*wF*(mB-mF)*(mB-mF)
            if (between > bestVar) { bestVar=between; best=t }
        }
        return best.coerceIn(80, 200)
    }

    private fun luma(color: Int): Int = ((Color.red(color)*299 + Color.green(color)*587 + Color.blue(color)*114) / 1000)
}
