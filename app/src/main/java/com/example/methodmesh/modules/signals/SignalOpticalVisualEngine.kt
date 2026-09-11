package com.example.methodmesh.modules.signals

import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import androidx.camera.core.Camera as CameraXCamera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** One camera observation of a spatial optical grid. */
data class SignalGridGeometry(
    val left: Float, val top: Float, val right: Float, val bottom: Float, val confidence: Double
)

data class SignalGridCameraSample(
    val timestampMs: Long,
    val luma: DoubleArray,
    val decoded: OpticalGridCodec.State?,
    val geometry: SignalGridGeometry? = null
)

/**
 * Samples a square target ROI into an N×N luminance matrix. The pure grid codec
 * resolves 90-degree rotations from transmitted corner pilots and derives the
 * four amplitude levels from those same pilots, so exposure need not be calibrated
 * to an absolute camera value.
 */
class SignalCameraGridAnalyzer(
    private val onSample: (SignalGridCameraSample) -> Unit
) : ImageAnalysis.Analyzer {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var gridSize = 4
    @Volatile private var roiMode = SignalCameraRoiMode.FOCUS
    @Volatile private var centerX = 0.5f
    @Volatile private var centerY = 0.5f
    @Volatile private var generation = 0
    private var handledGeneration = -1
    private var stableGate = OpticalGridStableGate(2)

    private data class RawBox(val left: Float, val top: Float, val right: Float, val bottom: Float, val confidence: Double)

    fun updateConfiguration(size: Int, roi: SignalCameraRoiMode, x: Float, y: Float, generation: Int) {
        gridSize = size.coerceIn(4, 10)
        roiMode = roi
        centerX = x.coerceIn(0f, 1f)
        centerY = y.coerceIn(0f, 1f)
        this.generation = generation
    }

    override fun analyze(image: ImageProxy) {
        try {
            if (handledGeneration != generation) {
                handledGeneration = generation
                stableGate = OpticalGridStableGate(2)
            }
            val plane = image.planes.firstOrNull() ?: return
            val width = image.width
            val height = image.height
            if (width <= 0 || height <= 0) return
            val buffer = plane.buffer
            val rotation = ((image.imageInfo.rotationDegrees % 360) + 360) % 360
            val detected = detectFinderBox(
                bufferLimit = buffer.limit(),
                getter = { index -> buffer.get(index).toInt() and 0xff },
                rowStride = plane.rowStride,
                pixelStride = plane.pixelStride,
                width = width,
                height = height,
                rotation = rotation
            )
            if (detected == null) {
                main.post { onSample(SignalGridCameraSample(System.currentTimeMillis(), DoubleArray(0), null, null)) }
                return
            }
            val size = gridSize
            val values = DoubleArray(size * size)
            // The transmitted white finder frame surrounds a black moat. The grid occupies
            // the central 78% of the detected frame, so camera motion changes this box rather
            // than changing the logical sample coordinates.
            val inset = 0.11f
            val innerLeft = detected.left + (detected.right - detected.left) * inset
            val innerTop = detected.top + (detected.bottom - detected.top) * inset
            val innerRight = detected.right - (detected.right - detected.left) * inset
            val innerBottom = detected.bottom - (detected.bottom - detected.top) * inset
            val normalizedCell = min((innerRight - innerLeft) / size.toFloat(), (innerBottom - innerTop) / size.toFloat())
            for (row in 0 until size) {
                for (column in 0 until size) {
                    val rawX = innerLeft + (innerRight - innerLeft) * (column + 0.5f) / size.toFloat()
                    val rawY = innerTop + (innerBottom - innerTop) * (row + 0.5f) / size.toFloat()
                    values[row * size + column] = localMedian(
                        bufferLimit = buffer.limit(), getter = { index -> buffer.get(index).toInt() and 0xff },
                        rowStride = plane.rowStride, pixelStride = plane.pixelStride,
                        width = width, height = height, x = rawX, y = rawY, normalizedCell = normalizedCell
                    )
                }
            }
            val decoded = OpticalGridCodec.decodeLuma(values, size)
            val stable = stableGate.feed(decoded)
            val tl = rawToDisplay(detected.left, detected.top, rotation)
            val br = rawToDisplay(detected.right, detected.bottom, rotation)
            val geometry = SignalGridGeometry(
                left = min(tl.first, br.first), top = min(tl.second, br.second),
                right = max(tl.first, br.first), bottom = max(tl.second, br.second),
                confidence = detected.confidence
            )
            main.post { onSample(SignalGridCameraSample(System.currentTimeMillis(), values, stable, geometry)) }
        } finally {
            image.close()
        }
    }

    /** Find the persistent high-luma rectangular finder frame inside the user-selected search area. */
    private fun detectFinderBox(
        bufferLimit: Int, getter: (Int) -> Int, rowStride: Int, pixelStride: Int,
        width: Int, height: Int, rotation: Int
    ): RawBox? {
        val fraction = roiMode.fraction.toFloat()
        val dl = (centerX - fraction / 2f).coerceIn(0f, 1f)
        val dt = (centerY - fraction / 2f).coerceIn(0f, 1f)
        val dr = (centerX + fraction / 2f).coerceIn(0f, 1f)
        val db = (centerY + fraction / 2f).coerceIn(0f, 1f)
        val rawCorners = listOf(displayToRaw(dl, dt, rotation), displayToRaw(dr, dt, rotation), displayToRaw(dr, db, rotation), displayToRaw(dl, db, rotation))
        val left = rawCorners.minOf { it.first }.coerceIn(0f, 1f)
        val right = rawCorners.maxOf { it.first }.coerceIn(0f, 1f)
        val top = rawCorners.minOf { it.second }.coerceIn(0f, 1f)
        val bottom = rawCorners.maxOf { it.second }.coerceIn(0f, 1f)
        val x0 = (left * (width - 1)).roundToInt().coerceIn(0, width - 1)
        val x1 = (right * (width - 1)).roundToInt().coerceIn(x0, width - 1)
        val y0 = (top * (height - 1)).roundToInt().coerceIn(0, height - 1)
        val y1 = (bottom * (height - 1)).roundToInt().coerceIn(y0, height - 1)
        val stepX = max(2, (x1 - x0 + 1) / 64)
        val stepY = max(2, (y1 - y0 + 1) / 64)
        var low = 255
        var high = 0
        val samples = ArrayList<Triple<Int, Int, Int>>(4096)
        for (y in y0..y1 step stepY) for (x in x0..x1 step stepX) {
            val at = y * rowStride + x * pixelStride
            if (at !in 0 until bufferLimit) continue
            val value = getter(at)
            low = min(low, value); high = max(high, value)
            samples += Triple(x, y, value)
        }
        val span = high - low
        if (samples.size < 64 || span < 24) return null
        val threshold = low + span * 0.82
        val bright = samples.filter { it.third >= threshold }
        if (bright.size < 12) return null
        val bx0 = bright.minOf { it.first }
        val bx1 = bright.maxOf { it.first }
        val by0 = bright.minOf { it.second }
        val by1 = bright.maxOf { it.second }
        val bw = (bx1 - bx0).coerceAtLeast(1)
        val bh = (by1 - by0).coerceAtLeast(1)
        val searchW = (x1 - x0).coerceAtLeast(1)
        val searchH = (y1 - y0).coerceAtLeast(1)
        if (bw < searchW * 0.15 || bh < searchH * 0.15) return null
        val aspect = bw.toDouble() / bh.toDouble()
        if (aspect !in 0.55..1.8) return null

        // Finder evidence: high-luma samples should occur close to all four bbox edges.
        val edgeBandX = max(stepX * 2, (bw * 0.08).roundToInt())
        val edgeBandY = max(stepY * 2, (bh * 0.08).roundToInt())
        val edgeHits = bright.count { (x, y, _) -> x - bx0 <= edgeBandX || bx1 - x <= edgeBandX || y - by0 <= edgeBandY || by1 - y <= edgeBandY }
        val confidence = (edgeHits.toDouble() / bright.size.toDouble()).coerceIn(0.0, 1.0)
        if (confidence < 0.35) return null
        return RawBox(
            bx0.toFloat() / width.toFloat(), by0.toFloat() / height.toFloat(),
            bx1.toFloat() / width.toFloat(), by1.toFloat() / height.toFloat(), confidence
        )
    }

    private fun localMedian(
        bufferLimit: Int, getter: (Int) -> Int, rowStride: Int, pixelStride: Int,
        width: Int, height: Int, x: Float, y: Float, normalizedCell: Float
    ): Double {
        val cx = (x * width).roundToInt().coerceIn(0, width - 1)
        val cy = (y * height).roundToInt().coerceIn(0, height - 1)
        val rx = max(1, (width * normalizedCell * 0.19f).roundToInt())
        val ry = max(1, (height * normalizedCell * 0.19f).roundToInt())
        val samples = ArrayList<Int>(49)
        val stepX = max(1, rx / 3); val stepY = max(1, ry / 3)
        for (py in max(0, cy - ry)..min(height - 1, cy + ry) step stepY) for (px in max(0, cx - rx)..min(width - 1, cx + rx) step stepX) {
            val at = py * rowStride + px * pixelStride
            if (at in 0 until bufferLimit) samples += getter(at)
        }
        if (samples.isEmpty()) return 0.0
        samples.sort(); return samples[samples.size / 2].toDouble()
    }

    private fun displayToRaw(x: Float, y: Float, rotation: Int): Pair<Float, Float> = when (rotation) {
        90 -> y to (1f - x); 180 -> (1f - x) to (1f - y); 270 -> (1f - y) to x; else -> x to y
    }
    private fun rawToDisplay(x: Float, y: Float, rotation: Int): Pair<Float, Float> = when (rotation) {
        90 -> (1f - y) to x; 180 -> (1f - x) to (1f - y); 270 -> y to (1f - x); else -> x to y
    }
}

/** Camera preview and target lattice for Screen Grid reception. */
@Composable
fun SignalCameraGridPreview(
    modifier: Modifier = Modifier,
    gridSize: Int,
    zoomRatio: Float = 1f,
    roiMode: SignalCameraRoiMode = SignalCameraRoiMode.FOCUS,
    roiCenterX: Float = 0.5f,
    roiCenterY: Float = 0.5f,
    searchGeneration: Int = 0,
    exposureReduction: Float = 0.55f,
    onRoiMoved: (Float, Float) -> Unit = { _, _ -> },
    onCameraInfo: (SignalCameraInfo) -> Unit = {},
    onSample: (SignalGridCameraSample) -> Unit,
    onError: (String) -> Unit
) {
    val currentSample = rememberUpdatedState(onSample)
    val currentError = rememberUpdatedState(onError)
    val currentCameraInfo = rememberUpdatedState(onCameraInfo)
    val currentRoiMoved = rememberUpdatedState(onRoiMoved)
    val analysisExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    val providerHolder = remember { arrayOfNulls<ProcessCameraProvider>(1) }
    val cameraHolder = remember { arrayOfNulls<CameraXCamera>(1) }
    val lastAppliedZoom = remember { floatArrayOf(Float.NaN) }
    val lastAppliedExposure = remember { intArrayOf(Int.MIN_VALUE) }
    val geometryState = remember { androidx.compose.runtime.mutableStateOf<SignalGridGeometry?>(null) }
    val analyzer = remember { SignalCameraGridAnalyzer { sample -> geometryState.value = sample.geometry; currentSample.value(sample) } }
    analyzer.updateConfiguration(gridSize, roiMode, roiCenterX, roiCenterY, searchGeneration)

    Box(
        modifier = modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures { offset ->
                if (size.width > 0 && size.height > 0) currentRoiMoved.value(
                    (offset.x / size.width.toFloat()).coerceIn(0f, 1f),
                    (offset.y / size.height.toFloat()).coerceIn(0f, 1f)
                )
            }
        }
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                PreviewView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    scaleType = PreviewView.ScaleType.FIT_CENTER
                    val owner = context.findLifecycleOwner()
                    if (owner == null) currentError.value("Camera preview could not find an Android lifecycle owner.")
                    else {
                        val future = ProcessCameraProvider.getInstance(context)
                        future.addListener({
                            runCatching {
                                val provider = future.get()
                                providerHolder[0] = provider
                                val preview = Preview.Builder().build().also { it.setSurfaceProvider(surfaceProvider) }
                                val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                                    .also { it.setAnalyzer(analysisExecutor, analyzer) }
                                provider.unbindAll()
                                val camera = provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                                cameraHolder[0] = camera
                                val zoom = camera.cameraInfo.zoomState.value
                                val minZoom = zoom?.minZoomRatio ?: 1f
                                val maxZoom = zoom?.maxZoomRatio ?: 1f
                                val target = zoomRatio.coerceIn(minZoom, maxZoom)
                                camera.cameraControl.setZoomRatio(target)
                                lastAppliedZoom[0] = target
                                val exposureRange = camera.cameraInfo.exposureState.exposureCompensationRange
                                val exposureTarget = if (exposureRange.lower < 0) (exposureRange.lower * exposureReduction.coerceIn(0f, 1f)).roundToInt().coerceIn(exposureRange.lower, exposureRange.upper) else 0
                                camera.cameraControl.setExposureCompensationIndex(exposureTarget)
                                lastAppliedExposure[0] = exposureTarget
                                currentCameraInfo.value(SignalCameraInfo(target, maxZoom))
                            }.onFailure { currentError.value(it.message ?: "Optical grid camera failed to start.") }
                        }, ContextCompat.getMainExecutor(context))
                    }
                }
            },
            update = {
                cameraHolder[0]?.let { camera ->
                    val zoom = camera.cameraInfo.zoomState.value
                    val minZoom = zoom?.minZoomRatio ?: 1f
                    val maxZoom = zoom?.maxZoomRatio ?: 1f
                    val target = zoomRatio.coerceIn(minZoom, maxZoom)
                    if (!lastAppliedZoom[0].isFinite() || abs(lastAppliedZoom[0] - target) >= 0.01f) {
                        camera.cameraControl.setZoomRatio(target); lastAppliedZoom[0] = target
                        currentCameraInfo.value(SignalCameraInfo(target, maxZoom))
                    }
                    val exposureRange = camera.cameraInfo.exposureState.exposureCompensationRange
                    val exposureTarget = if (exposureRange.lower < 0) (exposureRange.lower * exposureReduction.coerceIn(0f, 1f)).roundToInt().coerceIn(exposureRange.lower, exposureRange.upper) else 0
                    if (lastAppliedExposure[0] != exposureTarget) {
                        camera.cameraControl.setExposureCompensationIndex(exposureTarget); lastAppliedExposure[0] = exposureTarget
                    }
                }
            }
        )

        val fraction = roiMode.fraction.toFloat()
        Canvas(Modifier.fillMaxSize()) {
            val searchSide = min(size.width * fraction, size.height * fraction)
            val searchLeft = (size.width * roiCenterX - searchSide / 2f).coerceIn(0f, max(0f, size.width - searchSide))
            val searchTop = (size.height * roiCenterY - searchSide / 2f).coerceIn(0f, max(0f, size.height - searchSide))
            drawRect(SignalCyan.copy(alpha = 0.45f), Offset(searchLeft, searchTop), Size(searchSide, searchSide), style = Stroke(width = 2f))
            geometryState.value?.let { g ->
                val left = size.width * g.left; val top = size.height * g.top
                val right = size.width * g.right; val bottom = size.height * g.bottom
                val w = right - left; val h = bottom - top
                drawRect(SignalGreen, Offset(left, top), Size(w, h), style = Stroke(width = 4f))
                val insetX = w * 0.11f; val insetY = h * 0.11f
                val gl = left + insetX; val gt = top + insetY; val gw = w - insetX * 2; val gh = h - insetY * 2
                for (i in 1 until gridSize) {
                    val x = gl + gw * i / gridSize.toFloat(); val y = gt + gh * i / gridSize.toFloat()
                    drawLine(SignalGreen.copy(alpha = 0.5f), Offset(x, gt), Offset(x, gt + gh), strokeWidth = 1f)
                    drawLine(SignalGreen.copy(alpha = 0.5f), Offset(gl, y), Offset(gl + gw, y), strokeWidth = 1f)
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { providerHolder[0]?.unbindAll() }
            analysisExecutor.shutdownNow()
        }
    }
}

/** One stable AprilTag observation from the optical screen receiver. */
data class SignalAprilTagCameraSample(
    val timestampMs: Long,
    val tagId: Int?,
    val hamming: Int? = null,
    val geometry: SignalGridGeometry? = null
)

/**
 * Constrained AprilTag16h5 detector for a full-screen MethodMesh transmitter.
 * The screen is white outside the tag, so dark-pixel extrema locate the black
 * 6x6 tag body; a four-corner bilinear sample then decodes the 4x4 payload.
 * This handles perspective/hand motion without requiring a fixed camera lattice.
 */
class SignalCameraAprilTagAnalyzer(
    private val onSample: (SignalAprilTagCameraSample) -> Unit
) : ImageAnalysis.Analyzer {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var roiMode = SignalCameraRoiMode.FOCUS
    @Volatile private var centerX = 0.5f
    @Volatile private var centerY = 0.5f
    @Volatile private var generation = 0
    private var handledGeneration = -1
    private var stableGate = AprilTagBurstStableGate(2)

    private data class P(val x: Float, val y: Float)
    private data class Quad(val tl: P, val tr: P, val br: P, val bl: P, val confidence: Double)

    fun updateConfiguration(roi: SignalCameraRoiMode, x: Float, y: Float, generation: Int) {
        roiMode = roi
        centerX = x.coerceIn(0f, 1f)
        centerY = y.coerceIn(0f, 1f)
        this.generation = generation
    }

    override fun analyze(image: ImageProxy) {
        try {
            if (handledGeneration != generation) {
                handledGeneration = generation
                stableGate = AprilTagBurstStableGate(2)
            }
            val plane = image.planes.firstOrNull() ?: return
            val width = image.width
            val height = image.height
            if (width <= 0 || height <= 0) return
            val buffer = plane.buffer
            val rotation = ((image.imageInfo.rotationDegrees % 360) + 360) % 360
            val quad = detectTagQuad(
                buffer.limit(), { index -> buffer.get(index).toInt() and 0xff },
                plane.rowStride, plane.pixelStride, width, height, rotation
            )
            if (quad == null) {
                stableGate.feed(null)
                main.post { onSample(SignalAprilTagCameraSample(System.currentTimeMillis(), null, null, null)) }
                return
            }

            val lowHigh = tagContrast(
                buffer.limit(), { index -> buffer.get(index).toInt() and 0xff },
                plane.rowStride, plane.pixelStride, width, height, quad
            )
            if (lowHigh == null || lowHigh.second - lowHigh.first < 22) {
                stableGate.feed(null)
                main.post { onSample(SignalAprilTagCameraSample(System.currentTimeMillis(), null, null, geometry(quad, rotation))) }
                return
            }
            val threshold = lowHigh.first + (lowHigh.second - lowHigh.first) * 0.48
            val data = Array(4) { BooleanArray(4) }
            for (row in 0 until 4) {
                for (column in 0 until 4) {
                    // Data cells are modules 1..4 inside the 6x6 black-border body.
                    val u = (column + 1.5f) / 6f
                    val v = (row + 1.5f) / 6f
                    val point = project(quad, u, v)
                    val value = localMedian(
                        buffer.limit(), { index -> buffer.get(index).toInt() and 0xff },
                        plane.rowStride, plane.pixelStride, width, height, point.x, point.y,
                        radius = max(1, (min(width, height) * 0.0045f).roundToInt())
                    )
                    data[row][column] = value > threshold
                }
            }
            val detection = AprilTag16h5.decodeData(data, maxHamming = 1)
            val stable = stableGate.feed(detection?.id)
            main.post {
                onSample(
                    SignalAprilTagCameraSample(
                        System.currentTimeMillis(), stable,
                        if (stable != null) detection?.hamming else null,
                        geometry(quad, rotation)
                    )
                )
            }
        } finally {
            image.close()
        }
    }

    private fun detectTagQuad(
        bufferLimit: Int, getter: (Int) -> Int, rowStride: Int, pixelStride: Int,
        width: Int, height: Int, rotation: Int
    ): Quad? {
        val fraction = roiMode.fraction.toFloat()
        val dl = (centerX - fraction / 2f).coerceIn(0f, 1f)
        val dt = (centerY - fraction / 2f).coerceIn(0f, 1f)
        val dr = (centerX + fraction / 2f).coerceIn(0f, 1f)
        val db = (centerY + fraction / 2f).coerceIn(0f, 1f)
        val rawCorners = listOf(displayToRaw(dl, dt, rotation), displayToRaw(dr, dt, rotation), displayToRaw(dr, db, rotation), displayToRaw(dl, db, rotation))
        val left = rawCorners.minOf { it.first }.coerceIn(0f, 1f)
        val right = rawCorners.maxOf { it.first }.coerceIn(0f, 1f)
        val top = rawCorners.minOf { it.second }.coerceIn(0f, 1f)
        val bottom = rawCorners.maxOf { it.second }.coerceIn(0f, 1f)
        val x0 = (left * (width - 1)).roundToInt().coerceIn(0, width - 1)
        val x1 = (right * (width - 1)).roundToInt().coerceIn(x0, width - 1)
        val y0 = (top * (height - 1)).roundToInt().coerceIn(0, height - 1)
        val y1 = (bottom * (height - 1)).roundToInt().coerceIn(y0, height - 1)
        val step = max(2, min((x1 - x0 + 1) / 110, (y1 - y0 + 1) / 110))
        var low = 255
        var high = 0
        val sampled = ArrayList<Triple<Int, Int, Int>>(12000)
        for (y in y0..y1 step step) for (x in x0..x1 step step) {
            val at = y * rowStride + x * pixelStride
            if (at !in 0 until bufferLimit) continue
            val value = getter(at)
            low = min(low, value); high = max(high, value)
            sampled += Triple(x, y, value)
        }
        val span = high - low
        if (sampled.size < 100 || span < 28) return null
        val darkThreshold = low + span * 0.42
        val dark = sampled.filter { it.third <= darkThreshold }
        if (dark.size < 40) return null

        val tl0 = dark.minByOrNull { it.first + it.second } ?: return null
        val br0 = dark.maxByOrNull { it.first + it.second } ?: return null
        val tr0 = dark.maxByOrNull { it.first - it.second } ?: return null
        val bl0 = dark.minByOrNull { it.first - it.second } ?: return null
        val tl = P(tl0.first.toFloat() / width, tl0.second.toFloat() / height)
        val tr = P(tr0.first.toFloat() / width, tr0.second.toFloat() / height)
        val br = P(br0.first.toFloat() / width, br0.second.toFloat() / height)
        val bl = P(bl0.first.toFloat() / width, bl0.second.toFloat() / height)
        val topWidth = distance(tl, tr); val bottomWidth = distance(bl, br)
        val leftHeight = distance(tl, bl); val rightHeight = distance(tr, br)
        val meanWidth = (topWidth + bottomWidth) / 2f
        val meanHeight = (leftHeight + rightHeight) / 2f
        if (meanWidth < 0.08f || meanHeight < 0.08f) return null
        val aspect = meanWidth / meanHeight
        if (aspect !in 0.55f..1.8f) return null
        val confidence = (span / 160.0).coerceIn(0.0, 1.0) * (1.0 - kotlin.math.abs(1.0 - aspect.toDouble()).coerceAtMost(0.45))
        return Quad(tl, tr, br, bl, confidence)
    }

    private fun tagContrast(
        bufferLimit: Int, getter: (Int) -> Int, rowStride: Int, pixelStride: Int,
        width: Int, height: Int, quad: Quad
    ): Pair<Double, Double>? {
        val black = mutableListOf<Double>()
        val white = mutableListOf<Double>()
        val borderPoints = listOf(
            0.08f to 0.50f, 0.92f to 0.50f, 0.50f to 0.08f, 0.50f to 0.92f,
            0.08f to 0.08f, 0.92f to 0.08f, 0.92f to 0.92f, 0.08f to 0.92f
        )
        borderPoints.forEach { (u, v) ->
            val p = project(quad, u, v)
            black += localMedian(bufferLimit, getter, rowStride, pixelStride, width, height, p.x, p.y, 2)
        }
        // Quiet-zone samples just outside each side, clamped to image bounds.
        val quietPoints = listOf(
            -0.10f to 0.50f, 1.10f to 0.50f, 0.50f to -0.10f, 0.50f to 1.10f
        )
        quietPoints.forEach { (u, v) ->
            val p = project(quad, u, v)
            white += localMedian(bufferLimit, getter, rowStride, pixelStride, width, height, p.x, p.y, 2)
        }
        if (black.isEmpty() || white.isEmpty()) return null
        black.sort(); white.sort()
        return black[black.size / 2] to white[white.size / 2]
    }

    private fun project(q: Quad, u: Float, v: Float): P {
        // Bilinear quad interpolation is sufficient for the modest phone-to-phone perspective expected here.
        val topX = q.tl.x + (q.tr.x - q.tl.x) * u
        val topY = q.tl.y + (q.tr.y - q.tl.y) * u
        val bottomX = q.bl.x + (q.br.x - q.bl.x) * u
        val bottomY = q.bl.y + (q.br.y - q.bl.y) * u
        return P(topX + (bottomX - topX) * v, topY + (bottomY - topY) * v)
    }

    private fun geometry(q: Quad, rotation: Int): SignalGridGeometry {
        val display = listOf(q.tl, q.tr, q.br, q.bl).map { rawToDisplay(it.x, it.y, rotation) }
        return SignalGridGeometry(
            display.minOf { it.first }, display.minOf { it.second },
            display.maxOf { it.first }, display.maxOf { it.second }, q.confidence
        )
    }

    private fun localMedian(
        bufferLimit: Int, getter: (Int) -> Int, rowStride: Int, pixelStride: Int,
        width: Int, height: Int, x: Float, y: Float, radius: Int
    ): Double {
        val cx = (x.coerceIn(0f, 1f) * (width - 1)).roundToInt()
        val cy = (y.coerceIn(0f, 1f) * (height - 1)).roundToInt()
        val r = radius.coerceAtLeast(1)
        val samples = ArrayList<Int>(25)
        for (py in max(0, cy - r)..min(height - 1, cy + r) step max(1, r)) {
            for (px in max(0, cx - r)..min(width - 1, cx + r) step max(1, r)) {
                val at = py * rowStride + px * pixelStride
                if (at in 0 until bufferLimit) samples += getter(at)
            }
        }
        if (samples.isEmpty()) return 0.0
        samples.sort()
        return samples[samples.size / 2].toDouble()
    }

    private fun distance(a: P, b: P): Float = kotlin.math.sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y))
    private fun displayToRaw(x: Float, y: Float, rotation: Int): Pair<Float, Float> = when (rotation) {
        90 -> y to (1f - x); 180 -> (1f - x) to (1f - y); 270 -> (1f - y) to x; else -> x to y
    }
    private fun rawToDisplay(x: Float, y: Float, rotation: Int): Pair<Float, Float> = when (rotation) {
        90 -> (1f - y) to x; 180 -> (1f - x) to (1f - y); 270 -> y to (1f - x); else -> x to y
    }
}

/** Camera preview for AprilTag Burst. The cyan box is only a search area. */
@Composable
fun SignalCameraAprilTagPreview(
    modifier: Modifier = Modifier,
    zoomRatio: Float = 1f,
    roiMode: SignalCameraRoiMode = SignalCameraRoiMode.FOCUS,
    roiCenterX: Float = 0.5f,
    roiCenterY: Float = 0.5f,
    searchGeneration: Int = 0,
    exposureReduction: Float = 0.25f,
    onRoiMoved: (Float, Float) -> Unit = { _, _ -> },
    onCameraInfo: (SignalCameraInfo) -> Unit = {},
    onSample: (SignalAprilTagCameraSample) -> Unit,
    onError: (String) -> Unit
) {
    val currentSample = rememberUpdatedState(onSample)
    val currentError = rememberUpdatedState(onError)
    val currentCameraInfo = rememberUpdatedState(onCameraInfo)
    val currentRoiMoved = rememberUpdatedState(onRoiMoved)
    val analysisExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    val providerHolder = remember { arrayOfNulls<ProcessCameraProvider>(1) }
    val cameraHolder = remember { arrayOfNulls<CameraXCamera>(1) }
    val lastAppliedZoom = remember { floatArrayOf(Float.NaN) }
    val lastAppliedExposure = remember { intArrayOf(Int.MIN_VALUE) }
    val geometryState = remember { androidx.compose.runtime.mutableStateOf<SignalGridGeometry?>(null) }
    val analyzer = remember { SignalCameraAprilTagAnalyzer { sample -> geometryState.value = sample.geometry; currentSample.value(sample) } }
    analyzer.updateConfiguration(roiMode, roiCenterX, roiCenterY, searchGeneration)

    Box(
        modifier = modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures { offset ->
                if (size.width > 0 && size.height > 0) currentRoiMoved.value(
                    (offset.x / size.width.toFloat()).coerceIn(0f, 1f),
                    (offset.y / size.height.toFloat()).coerceIn(0f, 1f)
                )
            }
        }
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                PreviewView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    scaleType = PreviewView.ScaleType.FIT_CENTER
                    val owner = context.findLifecycleOwner()
                    if (owner == null) currentError.value("Camera preview could not find an Android lifecycle owner.")
                    else {
                        val future = ProcessCameraProvider.getInstance(context)
                        future.addListener({
                            runCatching {
                                val provider = future.get()
                                providerHolder[0] = provider
                                val preview = Preview.Builder().build().also { it.setSurfaceProvider(surfaceProvider) }
                                val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                                    .also { it.setAnalyzer(analysisExecutor, analyzer) }
                                provider.unbindAll()
                                val camera = provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                                cameraHolder[0] = camera
                                val zoom = camera.cameraInfo.zoomState.value
                                val minZoom = zoom?.minZoomRatio ?: 1f
                                val maxZoom = zoom?.maxZoomRatio ?: 1f
                                val target = zoomRatio.coerceIn(minZoom, maxZoom)
                                camera.cameraControl.setZoomRatio(target)
                                lastAppliedZoom[0] = target
                                val exposureRange = camera.cameraInfo.exposureState.exposureCompensationRange
                                val exposureTarget = if (exposureRange.lower < 0) (exposureRange.lower * exposureReduction.coerceIn(0f, 1f)).roundToInt().coerceIn(exposureRange.lower, exposureRange.upper) else 0
                                camera.cameraControl.setExposureCompensationIndex(exposureTarget)
                                lastAppliedExposure[0] = exposureTarget
                                currentCameraInfo.value(SignalCameraInfo(target, maxZoom))
                            }.onFailure { currentError.value(it.message ?: "AprilTag optical camera failed to start.") }
                        }, ContextCompat.getMainExecutor(context))
                    }
                }
            },
            update = {
                cameraHolder[0]?.let { camera ->
                    val zoom = camera.cameraInfo.zoomState.value
                    val minZoom = zoom?.minZoomRatio ?: 1f
                    val maxZoom = zoom?.maxZoomRatio ?: 1f
                    val target = zoomRatio.coerceIn(minZoom, maxZoom)
                    if (!lastAppliedZoom[0].isFinite() || abs(lastAppliedZoom[0] - target) >= 0.01f) {
                        camera.cameraControl.setZoomRatio(target); lastAppliedZoom[0] = target
                        currentCameraInfo.value(SignalCameraInfo(target, maxZoom))
                    }
                    val exposureRange = camera.cameraInfo.exposureState.exposureCompensationRange
                    val exposureTarget = if (exposureRange.lower < 0) (exposureRange.lower * exposureReduction.coerceIn(0f, 1f)).roundToInt().coerceIn(exposureRange.lower, exposureRange.upper) else 0
                    if (lastAppliedExposure[0] != exposureTarget) {
                        camera.cameraControl.setExposureCompensationIndex(exposureTarget); lastAppliedExposure[0] = exposureTarget
                    }
                }
            }
        )

        val fraction = roiMode.fraction.toFloat()
        Canvas(Modifier.fillMaxSize()) {
            val searchSide = min(size.width * fraction, size.height * fraction)
            val searchLeft = (size.width * roiCenterX - searchSide / 2f).coerceIn(0f, max(0f, size.width - searchSide))
            val searchTop = (size.height * roiCenterY - searchSide / 2f).coerceIn(0f, max(0f, size.height - searchSide))
            drawRect(SignalCyan.copy(alpha = 0.45f), Offset(searchLeft, searchTop), Size(searchSide, searchSide), style = Stroke(width = 2f))
            geometryState.value?.let { g ->
                val left = size.width * g.left; val top = size.height * g.top
                val right = size.width * g.right; val bottom = size.height * g.bottom
                drawRect(SignalGreen, Offset(left, top), Size(right - left, bottom - top), style = Stroke(width = 4f))
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { providerHolder[0]?.unbindAll() }
            analysisExecutor.shutdownNow()
        }
    }
}
