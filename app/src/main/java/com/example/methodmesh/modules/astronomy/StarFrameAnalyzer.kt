package com.example.methodmesh.modules.astronomy

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** One analysed camera frame. Values are relative camera-luma metrics, not calibrated photometry. */
data class StarFrameMetric(
    val timestampNs: Long,
    val centroidX: Double,
    val centroidY: Double,
    val fwhmPx: Double,
    val backgroundLuma: Double,
    val peakLuma: Double,
    val integratedSignal: Double,
    val detected: Boolean
)

data class StarTestSummary(
    val frameCount: Int,
    val detectionCount: Int,
    val centroidRmsPx: Double,
    val medianFwhmPx: Double,
    val medianBackgroundLuma: Double,
    val medianPeakLuma: Double,
    val medianContrast: Double,
    val scintillationCv: Double,
    val qualityWarning: String?
)

/**
 * CameraX Y-plane analyser for a single bright point source near the centre of frame.
 * It deliberately returns relative engineering proxies rather than arcsecond seeing.
 */
class StarFrameAnalyzer(
    private val roiFraction: Double = 0.55,
    private val onMetric: (StarFrameMetric) -> Unit = {}
) : ImageAnalysis.Analyzer {
    private val metrics = CopyOnWriteArrayList<StarFrameMetric>()

    override fun analyze(image: ImageProxy) {
        try {
            val metric = analyseYPlane(image)
            metrics += metric
            if (metrics.size > 600) metrics.removeAt(0)
            onMetric(metric)
        } finally {
            image.close()
        }
    }

    fun clear() = metrics.clear()
    fun snapshot(): List<StarFrameMetric> = metrics.toList()
    fun summary(minFrames: Int = 25): StarTestSummary = summarize(metrics.toList(), minFrames)

    private fun analyseYPlane(image: ImageProxy): StarFrameMetric {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val w = image.width
        val h = image.height
        val roiW = (w * roiFraction).toInt().coerceAtLeast(32)
        val roiH = (h * roiFraction).toInt().coerceAtLeast(32)
        val x0 = ((w - roiW) / 2).coerceAtLeast(0)
        val y0 = ((h - roiH) / 2).coerceAtLeast(0)
        val x1 = min(w, x0 + roiW)
        val y1 = min(h, y0 + roiH)

        var count = 0
        var sum = 0.0
        var sum2 = 0.0
        var peak = 0.0
        for (y in y0 until y1 step 2) {
            for (x in x0 until x1 step 2) {
                val v = luma(buffer, y * rowStride + x * pixelStride)
                count++
                sum += v
                sum2 += v * v
                if (v > peak) peak = v
            }
        }
        val mean = if (count > 0) sum / count else 0.0
        val variance = max(0.0, if (count > 0) sum2 / count - mean * mean else 0.0)
        val sd = sqrt(variance)
        val threshold = max(mean + max(10.0, 4.0 * sd), peak * 0.55)

        var weight = 0.0
        var sx = 0.0
        var sy = 0.0
        for (y in y0 until y1) {
            for (x in x0 until x1) {
                val v = luma(buffer, y * rowStride + x * pixelStride)
                if (v >= threshold) {
                    val q = v - mean
                    weight += q
                    sx += x * q
                    sy += y * q
                }
            }
        }
        if (weight <= 0.0 || peak - mean < 12.0) {
            return StarFrameMetric(image.imageInfo.timestamp, Double.NaN, Double.NaN, Double.NaN, mean, peak, 0.0, false)
        }
        val cx = sx / weight
        val cy = sy / weight
        var secondMoment = 0.0
        var signal = 0.0
        for (y in y0 until y1) {
            for (x in x0 until x1) {
                val v = luma(buffer, y * rowStride + x * pixelStride)
                if (v >= threshold) {
                    val q = v - mean
                    val dx = x - cx
                    val dy = y - cy
                    secondMoment += q * (dx * dx + dy * dy)
                    signal += q
                }
            }
        }
        val sigma = sqrt(max(0.0, secondMoment / max(signal, 1e-9) / 2.0))
        val fwhm = 2.354820045 * sigma
        return StarFrameMetric(image.imageInfo.timestamp, cx, cy, fwhm, mean, peak, signal, true)
    }

    private fun luma(buffer: ByteBuffer, index: Int): Double =
        if (index >= 0 && index < buffer.limit()) (buffer.get(index).toInt() and 0xFF).toDouble() else 0.0

    companion object {
        fun summarize(input: List<StarFrameMetric>, minFrames: Int = 25): StarTestSummary {
            val valid = input.filter { it.detected && it.centroidX.isFinite() && it.fwhmPx.isFinite() }
            if (valid.isEmpty()) return StarTestSummary(input.size, 0, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, "No stable bright point source detected.")
            val mx = valid.map { it.centroidX }.average()
            val my = valid.map { it.centroidY }.average()
            val rms = sqrt(valid.map { (it.centroidX - mx) * (it.centroidX - mx) + (it.centroidY - my) * (it.centroidY - my) }.average())
            val fwhm = median(valid.map { it.fwhmPx })
            val bg = median(valid.map { it.backgroundLuma })
            val peak = median(valid.map { it.peakLuma })
            val contrast = (peak - bg) / max(bg, 1.0)
            val flux = valid.map { it.integratedSignal }.filter { it > 0 }
            val fluxMean = flux.average().takeIf { it.isFinite() && it > 0 } ?: Double.NaN
            val fluxSd = if (fluxMean.isFinite()) sqrt(flux.map { (it - fluxMean) * (it - fluxMean) }.average()) else Double.NaN
            val scint = if (fluxMean.isFinite()) fluxSd / fluxMean else Double.NaN
            val warning = when {
                input.size < minFrames -> "Short sample: collect at least $minFrames frames."
                valid.size < minFrames -> "Star detection was intermittent; centre a brighter star and stabilise the phone."
                valid.size < input.size * 0.7 -> "Point-source detection was unstable."
                peak > 250.0 -> "Star core is close to clipping; reduce exposure if possible."
                else -> null
            }
            return StarTestSummary(input.size, valid.size, rms, fwhm, bg, peak, contrast, scint, warning)
        }

        private fun median(values: List<Double>): Double {
            val sorted = values.filter { it.isFinite() }.sorted()
            if (sorted.isEmpty()) return Double.NaN
            val m = sorted.size / 2
            return if (sorted.size % 2 == 1) sorted[m] else (sorted[m - 1] + sorted[m]) / 2.0
        }
    }
}
