package com.example.methodmesh.modules.paperbridge

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.media.ExifInterface
import android.net.Uri
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal data class PaperAnchorEvidence(
    val corner: String,
    val point: PointF,
    val score: Float,
    val componentArea: Int,
    val boundingWidth: Int,
    val boundingHeight: Int
)

internal data class PaperRectification(
    val bitmap: Bitmap,
    val anchors: List<PaperAnchorEvidence>,
    val sourceWidth: Int,
    val sourceHeight: Int
)

internal object PaperImageEngine {
    fun loadOrientedBitmap(context: Context, uri: Uri, manualQuarterTurns: Int = 0): Bitmap {
        val source = context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
            ?: throw IllegalArgumentException("Paper image could not be decoded.")
        val exifRotation = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                when (ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 270
                    else -> 0
                }
            } ?: 0
        }.getOrDefault(0)
        val rotation = ((exifRotation + (manualQuarterTurns.mod(4) * 90)) % 360 + 360) % 360
        if (rotation == 0) return source
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true).also {
            if (it !== source) source.recycle()
        }
    }

    fun rectify(source: Bitmap, template: PaperTemplate): PaperRectification {
        val maxDimension = max(source.width, source.height)
        val scale = min(1f, 1200f / maxDimension.toFloat())
        val analysis = if (scale < 0.999f) {
            Bitmap.createScaledBitmap(source, (source.width * scale).roundToInt(), (source.height * scale).roundToInt(), true)
        } else source
        val anchors = detectAnchors(analysis, template.anchors).map { evidence ->
            if (scale >= 0.999f) evidence else evidence.copy(
                point = PointF(evidence.point.x / scale, evidence.point.y / scale),
                componentArea = (evidence.componentArea / (scale * scale)).roundToInt(),
                boundingWidth = (evidence.boundingWidth / scale).roundToInt(),
                boundingHeight = (evidence.boundingHeight / scale).roundToInt()
            )
        }
        if (analysis !== source) analysis.recycle()
        validateAnchorGeometry(anchors, source.width, source.height)

        val src = FloatArray(8)
        val dst = FloatArray(8)
        anchors.forEachIndexed { index, evidence ->
            src[index * 2] = evidence.point.x
            src[index * 2 + 1] = evidence.point.y
            dst[index * 2] = template.anchors.targets[index].first * template.pageWidthPx
            dst[index * 2 + 1] = template.anchors.targets[index].second * template.pageHeightPx
        }
        val matrix = Matrix()
        require(matrix.setPolyToPoly(src, 0, dst, 0, 4)) { "Could not compute four-anchor perspective transform." }
        val rectified = Bitmap.createBitmap(template.pageWidthPx, template.pageHeightPx, Bitmap.Config.ARGB_8888)
        Canvas(rectified).apply {
            drawColor(Color.WHITE)
            drawBitmap(source, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }
        return PaperRectification(rectified, anchors, source.width, source.height)
    }

    fun roiRect(bitmap: Bitmap, roi: NormalisedRoi, insetFraction: Float = 0f): Rect {
        val left = (roi.left * bitmap.width).roundToInt().coerceIn(0, bitmap.width - 1)
        val top = (roi.top * bitmap.height).roundToInt().coerceIn(0, bitmap.height - 1)
        val right = (roi.right * bitmap.width).roundToInt().coerceIn(left + 1, bitmap.width)
        val bottom = (roi.bottom * bitmap.height).roundToInt().coerceIn(top + 1, bitmap.height)
        if (insetFraction <= 0f) return Rect(left, top, right, bottom)
        val dx = ((right - left) * insetFraction).roundToInt()
        val dy = ((bottom - top) * insetFraction).roundToInt()
        return Rect(
            (left + dx).coerceAtMost(right - 1),
            (top + dy).coerceAtMost(bottom - 1),
            (right - dx).coerceAtLeast(left + 1),
            (bottom - dy).coerceAtLeast(top + 1)
        )
    }

    fun crop(bitmap: Bitmap, roi: NormalisedRoi): Bitmap {
        val rect = roiRect(bitmap, roi)
        return Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width(), rect.height())
    }

    fun markScore(bitmap: Bitmap, roi: NormalisedRoi, pageWhiteLuma: Float): Float {
        val rect = roiRect(bitmap, roi, insetFraction = 0.12f)
        var darkness = 0.0
        var darkPixels = 0
        var count = 0
        val threshold = (pageWhiteLuma - 55f).coerceIn(70f, 205f)
        for (y in rect.top until rect.bottom) {
            for (x in rect.left until rect.right) {
                val l = luma(bitmap.getPixel(x, y)).toFloat()
                darkness += ((pageWhiteLuma - l).coerceAtLeast(0f) / pageWhiteLuma.coerceAtLeast(1f)).toDouble()
                if (l < threshold) darkPixels++
                count++
            }
        }
        if (count == 0) return 0f
        val meanDarkness = (darkness / count).toFloat()
        val darkFraction = darkPixels.toFloat() / count.toFloat()
        return (0.58f * meanDarkness + 0.42f * darkFraction).coerceIn(0f, 1f)
    }

    fun estimatePaperWhiteLuma(bitmap: Bitmap): Float {
        val step = max(1, max(bitmap.width, bitmap.height) / 150)
        val samples = ArrayList<Int>(25000)
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                samples += luma(bitmap.getPixel(x, y))
                x += step
            }
            y += step
        }
        if (samples.isEmpty()) return 245f
        samples.sort()
        return samples[(samples.size * 0.78).toInt().coerceIn(0, samples.lastIndex)].toFloat().coerceAtLeast(150f)
    }

    private fun detectAnchors(bitmap: Bitmap, spec: PaperAnchorSpec): List<PaperAnchorEvidence> {
        val w = bitmap.width
        val h = bitmap.height
        val sx = (w * spec.searchFraction).roundToInt().coerceAtLeast(40)
        val sy = (h * spec.searchFraction).roundToInt().coerceAtLeast(40)
        val regions = listOf(
            Triple("top-left", Rect(0, 0, sx, sy), 0),
            Triple("top-right", Rect(w - sx, 0, w, sy), 1),
            Triple("bottom-right", Rect(w - sx, h - sy, w, h), 2),
            Triple("bottom-left", Rect(0, h - sy, sx, h), 3)
        )
        return regions.map { (label, region, _) ->
            detectAnchorInRegion(bitmap, region, label, spec)
                ?: throw IllegalArgumentException("Anchor not confidently identified in $label region. Rescan the complete page with all four anchor symbols visible.")
        }
    }

    private fun detectAnchorInRegion(bitmap: Bitmap, region: Rect, label: String, spec: PaperAnchorSpec): PaperAnchorEvidence? {
        val rw = region.width()
        val rh = region.height()
        val pixels = IntArray(rw * rh)
        bitmap.getPixels(pixels, 0, rw, region.left, region.top, rw, rh)
        val dark = BooleanArray(pixels.size) { index -> luma(pixels[index]) < spec.thresholdLuma }
        val visited = BooleanArray(pixels.size)
        val stack = IntArray(pixels.size)
        val regionArea = rw * rh
        val minArea = (regionArea * spec.minComponentFraction).roundToInt().coerceAtLeast(12)
        val maxArea = (regionArea * spec.maxComponentFraction).roundToInt().coerceAtLeast(minArea + 1)
        var best: PaperAnchorEvidence? = null
        var bestComposite = Float.NEGATIVE_INFINITY

        for (start in dark.indices) {
            if (!dark[start] || visited[start]) continue
            var stackSize = 0
            stack[stackSize++] = start
            visited[start] = true
            var area = 0
            var sumX = 0L
            var sumY = 0L
            var minX = rw
            var maxX = 0
            var minY = rh
            var maxY = 0
            while (stackSize > 0) {
                val index = stack[--stackSize]
                val x = index % rw
                val y = index / rw
                area++
                sumX += x
                sumY += y
                minX = min(minX, x); maxX = max(maxX, x)
                minY = min(minY, y); maxY = max(maxY, y)
                val neighbours = intArrayOf(index - 1, index + 1, index - rw, index + rw)
                for (next in neighbours) {
                    if (next !in dark.indices || visited[next] || !dark[next]) continue
                    val nx = next % rw
                    val ny = next / rw
                    if (abs(nx - x) + abs(ny - y) != 1) continue
                    visited[next] = true
                    stack[stackSize++] = next
                }
            }
            if (area !in minArea..maxArea) continue
            val bw = maxX - minX + 1
            val bh = maxY - minY + 1
            val aspect = min(bw, bh).toFloat() / max(bw, bh).toFloat().coerceAtLeast(1f)
            if (1f - aspect > spec.maxAspectError) continue
            val fill = area.toFloat() / (bw * bh).toFloat().coerceAtLeast(1f)
            val areaScore = ((area - minArea).toFloat() / (maxArea - minArea).toFloat()).coerceIn(0f, 1f)
            val compactness = (0.65f * aspect + 0.35f * fill.coerceIn(0f, 1f))
            val centre = PointF(region.left + sumX.toFloat() / area, region.top + sumY.toFloat() / area)
            // A Paper Bridge target is deliberately more distinctive than a plain dot:
            // dark centre, light annulus, dark outer ring. We still retain enough
            // compactness weight that older solid-dot forms remain backwards compatible.
            val targetStructure = targetPatternScore(
                bitmap = bitmap,
                centre = centre,
                radius = max(bw, bh).toFloat() / 2f,
                thresholdLuma = spec.thresholdLuma
            )
            val score = (0.10f * areaScore + 0.15f * compactness + 0.75f * targetStructure).coerceIn(0f, 1f)
            if (score < spec.minimumScore) continue
            // Prefer compact, substantial, target-like components inside the reserved
            // quiet corner zones. The target-structure term sharply reduces accidental
            // matches on ordinary text and filled checkboxes.
            val composite = score + area.toFloat() / regionArea.toFloat() + 0.20f * targetStructure
            if (composite > bestComposite) {
                bestComposite = composite
                best = PaperAnchorEvidence(
                    corner = label,
                    point = centre,
                    score = score,
                    componentArea = area,
                    boundingWidth = bw,
                    boundingHeight = bh
                )
            }
        }
        return best
    }


    private fun targetPatternScore(bitmap: Bitmap, centre: PointF, radius: Float, thresholdLuma: Int): Float {
        if (radius < 3f) return 0f
        val sampleRadius = (radius * 1.08f).coerceAtLeast(4f)
        val left = (centre.x - sampleRadius).roundToInt().coerceIn(0, bitmap.width - 1)
        val right = (centre.x + sampleRadius).roundToInt().coerceIn(left + 1, bitmap.width)
        val top = (centre.y - sampleRadius).roundToInt().coerceIn(0, bitmap.height - 1)
        val bottom = (centre.y + sampleRadius).roundToInt().coerceIn(top + 1, bitmap.height)

        var centreDark = 0
        var centreCount = 0
        var gapLight = 0
        var gapCount = 0
        var ringDark = 0
        var ringCount = 0
        for (y in top until bottom) {
            for (x in left until right) {
                val dx = x - centre.x
                val dy = y - centre.y
                val r = kotlin.math.sqrt(dx * dx + dy * dy) / radius.coerceAtLeast(1f)
                val dark = luma(bitmap.getPixel(x, y)) < thresholdLuma
                when {
                    r <= 0.22f -> {
                        centreCount++
                        if (dark) centreDark++
                    }
                    r in 0.34f..0.56f -> {
                        gapCount++
                        if (!dark) gapLight++
                    }
                    r in 0.70f..1.02f -> {
                        ringCount++
                        if (dark) ringDark++
                    }
                }
            }
        }
        if (centreCount == 0 || gapCount == 0 || ringCount == 0) return 0f
        val centreScore = centreDark.toFloat() / centreCount
        val gapScore = gapLight.toFloat() / gapCount
        val ringScore = ringDark.toFloat() / ringCount
        return (0.34f * centreScore + 0.32f * gapScore + 0.34f * ringScore).coerceIn(0f, 1f)
    }

    private fun validateAnchorGeometry(anchors: List<PaperAnchorEvidence>, width: Int, height: Int) {
        require(anchors.size == 4) { "Four anchors are required." }
        val p = anchors.map { it.point }
        require(distanceSquared(p[0], p[1]) > width * width * 0.15f) { "Top anchors are too close together." }
        require(distanceSquared(p[3], p[2]) > width * width * 0.15f) { "Bottom anchors are too close together." }
        require(distanceSquared(p[0], p[3]) > height * height * 0.15f) { "Left anchors are too close together." }
        require(distanceSquared(p[1], p[2]) > height * height * 0.15f) { "Right anchors are too close together." }
        val area = polygonArea(p)
        require(area > width * height * 0.20f) { "Detected anchors do not define a credible page quadrilateral." }
    }

    private fun polygonArea(points: List<PointF>): Float {
        var sum = 0f
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            sum += a.x * b.y - b.x * a.y
        }
        return abs(sum) / 2f
    }

    private fun distanceSquared(a: PointF, b: PointF): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return dx * dx + dy * dy
    }

    private fun luma(pixel: Int): Int {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        return (77 * r + 150 * g + 29 * b) shr 8
    }
}
