package com.example.methodmesh.modules.paperbridge

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Detects printed response rectangles on an already registered blank paper form.
 *
 * This deliberately does NOT infer questionnaire semantics. Geometry comes from the
 * paper; meaning comes from the XLSForm. Returned boxes are therefore unlinked ROIs
 * ready for the designer to map to ODK fields/options.
 *
 * The detector is dependency-free and operates on the same canonical canvas used
 * by Paper Bridge scanning. It looks for connected dark components whose bounding box
 * has strong dark occupancy on all four edges and a substantially cleaner interior.
 */
internal object PaperBoxDetectionEngine {
    internal data class Detection(
        val roi: NormalisedRoi,
        val confidence: Float
    )

    fun detect(bitmap: Bitmap, neutralOnly: Boolean = false): List<Detection> {
        require(bitmap.width > 0 && bitmap.height > 0) { "Blank form image is empty." }

        val longSide = max(bitmap.width, bitmap.height)
        val scale = min(1f, 1400f / longSide.toFloat())
        val analysis = if (scale < 0.999f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).roundToInt().coerceAtLeast(1),
                (bitmap.height * scale).roundToInt().coerceAtLeast(1),
                true
            )
        } else bitmap

        try {
            val width = analysis.width
            val height = analysis.height
            val pixelCount = width * height
            val pixels = IntArray(pixelCount)
            analysis.getPixels(pixels, 0, width, 0, 0, width, height)

            // Estimate the paper white point cheaply from a sparse sample. This keeps
            // detection useful for camera/gallery images that are not perfectly white.
            val samples = ArrayList<Int>(20_000)
            val step = max(1, max(width, height) / 180)
            var sy = 0
            while (sy < height) {
                var sx = 0
                while (sx < width) {
                    samples += luma(pixels[sy * width + sx])
                    sx += step
                }
                sy += step
            }
            samples.sort()
            val white = if (samples.isEmpty()) 245 else samples[(samples.size * 0.78f).toInt().coerceIn(0, samples.lastIndex)]
            val threshold = (white - 55).coerceIn(90, 205)
            val dark = BooleanArray(pixelCount) { index ->
                val pixel = pixels[index]
                val isDark = luma(pixel) < threshold
                if (!neutralOnly) isDark else {
                    val r = (pixel shr 16) and 0xFF
                    val g = (pixel shr 8) and 0xFF
                    val b = pixel and 0xFF
                    isDark && (max(r, max(g, b)) - min(r, min(g, b)) <= 38)
                }
            }
            val visited = BooleanArray(pixelCount)
            val stack = IntArray(pixelCount)

            val pageArea = width.toLong() * height.toLong()
            val boundaryMargin = max(4, (min(width, height) * 0.015f).roundToInt())
            val minWidth = max(14, (width * 0.012f).roundToInt())
            val minHeight = max(14, (height * 0.009f).roundToInt())
            val candidates = mutableListOf<Detection>()

            for (start in 0 until pixelCount) {
                if (!dark[start] || visited[start]) continue
                var stackSize = 0
                stack[stackSize++] = start
                visited[start] = true
                var area = 0
                var minX = width
                var maxX = 0
                var minY = height
                var maxY = 0

                while (stackSize > 0) {
                    val index = stack[--stackSize]
                    val x = index % width
                    val y = index / width
                    area++
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y

                    val x0 = max(0, x - 1)
                    val x1 = min(width - 1, x + 1)
                    val y0 = max(0, y - 1)
                    val y1 = min(height - 1, y + 1)
                    var ny = y0
                    while (ny <= y1) {
                        var nx = x0
                        while (nx <= x1) {
                            val next = ny * width + nx
                            if (!visited[next] && dark[next]) {
                                visited[next] = true
                                stack[stackSize++] = next
                            }
                            nx++
                        }
                        ny++
                    }
                }

                val boxWidth = maxX - minX + 1
                val boxHeight = maxY - minY + 1
                if (boxWidth < minWidth || boxHeight < minHeight) continue
                if (minX <= boundaryMargin || minY <= boundaryMargin ||
                    maxX >= width - 1 - boundaryMargin || maxY >= height - 1 - boundaryMargin) continue

                val boundingArea = boxWidth.toLong() * boxHeight.toLong()
                if (boundingArea < pageArea * 0.00025 || boundingArea > pageArea * 0.22) continue
                val aspect = boxWidth.toFloat() / boxHeight.toFloat()
                if (aspect !in 0.35f..12f) continue
                val density = area.toFloat() / boundingArea.toFloat()
                if (density !in 0.015f..0.42f) continue

                val strip = max(1, min(boxWidth, boxHeight) / 10)
                fun fractionDark(xStart: Int, yStart: Int, xEnd: Int, yEnd: Int): Float {
                    var count = 0
                    var darkCount = 0
                    var yy = yStart
                    while (yy < yEnd) {
                        var xx = xStart
                        while (xx < xEnd) {
                            count++
                            if (dark[yy * width + xx]) darkCount++
                            xx++
                        }
                        yy++
                    }
                    return if (count == 0) 0f else darkCount.toFloat() / count.toFloat()
                }

                val top = fractionDark(minX, minY, maxX + 1, minY + strip)
                val bottom = fractionDark(minX, maxY - strip + 1, maxX + 1, maxY + 1)
                val left = fractionDark(minX, minY, minX + strip, maxY + 1)
                val right = fractionDark(maxX - strip + 1, minY, maxX + 1, maxY + 1)
                val inner = if (boxWidth > 2 * strip && boxHeight > 2 * strip) {
                    fractionDark(minX + strip, minY + strip, maxX - strip + 1, maxY - strip + 1)
                } else 1f
                val weakestEdge = min(min(top, bottom), min(left, right))
                if (weakestEdge < 0.08f || inner > 0.20f) continue

                val averageEdge = (top + bottom + left + right) / 4f
                val edgeBalance = 1f - ((max(max(top, bottom), max(left, right)) - weakestEdge).coerceIn(0f, 1f))
                val confidence = (0.62f * averageEdge + 0.23f * (1f - inner) + 0.15f * edgeBalance).coerceIn(0f, 1f)
                if (confidence < 0.28f) continue

                candidates += Detection(
                    roi = NormalisedRoi(
                        left = minX.toFloat() / width.toFloat(),
                        top = minY.toFloat() / height.toFloat(),
                        right = (maxX + 1).toFloat() / width.toFloat(),
                        bottom = (maxY + 1).toFloat() / height.toFloat()
                    ),
                    confidence = confidence
                )
            }

            return deduplicate(candidates)
                .sortedWith(compareBy<Detection> { it.roi.top }.thenBy { it.roi.left })
        } finally {
            if (analysis !== bitmap && !analysis.isRecycled) analysis.recycle()
        }
    }

    /**
     * Thin anti-aliased borders can occasionally form near-identical components.
     * Keep the stronger detection when two candidates substantially describe the
     * same printed rectangle.
     */
    private fun deduplicate(input: List<Detection>): List<Detection> {
        val kept = mutableListOf<Detection>()
        input.sortedByDescending { it.confidence }.forEach { candidate ->
            if (kept.none { overlap(candidate.roi, it.roi) > 0.80f }) kept += candidate
        }
        return kept
    }

    private fun overlap(a: NormalisedRoi, b: NormalisedRoi): Float {
        val left = max(a.left, b.left)
        val top = max(a.top, b.top)
        val right = min(a.right, b.right)
        val bottom = min(a.bottom, b.bottom)
        if (right <= left || bottom <= top) return 0f
        val intersection = (right - left) * (bottom - top)
        val union = (a.right - a.left) * (a.bottom - a.top) +
            (b.right - b.left) * (b.bottom - b.top) - intersection
        return if (union <= 0f) 0f else intersection / union
    }

    private fun luma(pixel: Int): Int {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return ((299 * r + 587 * g + 114 * b) / 1000)
    }
}
