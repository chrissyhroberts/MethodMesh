package com.example.methodmesh.modules.signals

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class SignalTorchController(context: Context) {
    private val manager = context.applicationContext.getSystemService(CameraManager::class.java)
    private val cameraId: String? = manager?.cameraIdList?.firstOrNull { id ->
        val c = manager.getCameraCharacteristics(id)
        c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK &&
            c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
    } ?: manager?.cameraIdList?.firstOrNull { id ->
        manager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
    }

    val available: Boolean get() = cameraId != null

    fun set(enabled: Boolean) {
        val id = cameraId ?: return
        manager?.setTorchMode(id, enabled)
    }

    fun off() = runCatching { set(false) }
}

enum class SignalCameraRoiMode(val fraction: Double, val label: String) {
    FULL(0.55, "Full"),
    FOCUS(0.22, "Focus"),
    PINPOINT(0.08, "Pinpoint")
}

enum class SignalCameraLockState {
    MANUAL,
    SEARCHING,
    LOCKED,
    LOST
}

data class CameraLumaSample(
    val timestampMs: Long,
    val luma: Double,
    val roiCenterX: Float = 0.5f,
    val roiCenterY: Float = 0.5f,
    val lockState: SignalCameraLockState = SignalCameraLockState.MANUAL,
    val lockConfidence: Double = 0.0,
    val modulationScore: Double = 0.0
)

data class SignalCameraInfo(
    val actualZoomRatio: Float,
    val maxZoomRatio: Float
)

/**
 * Luminance analyser for optical Signals reception.
 *
 * In manual mode only the configured inset ROI contributes to the detector. Auto-lock divides the
 * camera image into tiles and scores temporal modulation, with frame-wide exposure changes removed.
 * This means a blinking torch/screen can win over a merely bright object. Once acquired, the search
 * becomes local around the locked tile so modest hand movement is tracked without hopping around the
 * scene. Coordinates exposed to the UI are in displayed-preview space after CameraX rotation.
 */
class SignalCameraLumaAnalyzer(
    private val onSample: (CameraLumaSample) -> Unit
) : ImageAnalysis.Analyzer {
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var roiMode: SignalCameraRoiMode = SignalCameraRoiMode.FOCUS
    @Volatile private var manualCenterX = 0.5f
    @Volatile private var manualCenterY = 0.5f
    @Volatile private var autoLock = false
    @Volatile private var searchGeneration = 0
    @Volatile private var opticalProfile = "screen"

    private val gridColumns = 12
    private val gridRows = 9
    private val tileCount = gridColumns * gridRows
    private val previous = DoubleArray(tileCount) { Double.NaN }
    private val scores = DoubleArray(tileCount)
    private var previousGlobal = Double.NaN
    private var framesObserved = 0
    private var lockIndex: Int? = null
    private var lowLockFrames = 0
    private var handledSearchGeneration = -1

    fun updateConfiguration(
        roiMode: SignalCameraRoiMode,
        centerX: Float,
        centerY: Float,
        autoLock: Boolean,
        searchGeneration: Int,
        opticalProfile: String = "screen"
    ) {
        this.roiMode = roiMode
        this.manualCenterX = centerX.coerceIn(0f, 1f)
        this.manualCenterY = centerY.coerceIn(0f, 1f)
        this.autoLock = autoLock
        this.searchGeneration = searchGeneration
        this.opticalProfile = opticalProfile
    }

    private fun resetSearch() {
        previous.fill(Double.NaN)
        scores.fill(0.0)
        previousGlobal = Double.NaN
        framesObserved = 0
        lockIndex = null
        lowLockFrames = 0
    }

    override fun analyze(image: ImageProxy) {
        try {
            if (handledSearchGeneration != searchGeneration) {
                handledSearchGeneration = searchGeneration
                resetSearch()
            }

            val plane = image.planes.firstOrNull() ?: return
            val width = image.width
            val height = image.height
            if (width <= 0 || height <= 0) return
            val buffer = plane.buffer
            val rotation = ((image.imageInfo.rotationDegrees % 360) + 360) % 360

            val tileValues = DoubleArray(tileCount)
            var globalSum = 0.0
            var globalCount = 0
            for (row in 0 until gridRows) {
                val y0 = row * height / gridRows
                val y1 = max(y0 + 1, (row + 1) * height / gridRows)
                for (column in 0 until gridColumns) {
                    val x0 = column * width / gridColumns
                    val x1 = max(x0 + 1, (column + 1) * width / gridColumns)
                    var sum = 0.0
                    var count = 0
                    val stepX = max(1, (x1 - x0) / 8)
                    val stepY = max(1, (y1 - y0) / 8)
                    for (y in y0 until y1 step stepY) {
                        for (x in x0 until x1 step stepX) {
                            val index = y * plane.rowStride + x * plane.pixelStride
                            if (index in 0 until buffer.limit()) {
                                val value = buffer.get(index).toInt() and 0xFF
                                sum += value
                                count++
                            }
                        }
                    }
                    val tileIndex = row * gridColumns + column
                    val average = if (count > 0) sum / count else 0.0
                    tileValues[tileIndex] = average
                    globalSum += sum
                    globalCount += count
                }
            }

            val global = if (globalCount > 0) globalSum / globalCount else 0.0
            val globalDelta = if (previousGlobal.isFinite()) global - previousGlobal else 0.0
            previousGlobal = global
            for (index in 0 until tileCount) {
                val old = previous[index]
                if (old.isFinite()) {
                    val deTrendedChange = abs((tileValues[index] - old) - globalDelta)
                    scores[index] = scores[index] * 0.86 + deTrendedChange * 0.14
                }
                previous[index] = tileValues[index]
            }
            framesObserved++

            var state = if (autoLock) SignalCameraLockState.SEARCHING else SignalCameraLockState.MANUAL
            var confidence = 0.0
            var winningScore = 0.0

            if (autoLock && framesObserved >= 8) {
                val candidateIndices = lockIndex?.let { locked ->
                    val row = locked / gridColumns
                    val column = locked % gridColumns
                    buildList {
                        for (r in max(0, row - 2)..min(gridRows - 1, row + 2)) {
                            for (c in max(0, column - 2)..min(gridColumns - 1, column + 2)) add(r * gridColumns + c)
                        }
                    }
                } ?: (0 until tileCount).toList()

                val ordered = candidateIndices.sortedByDescending { scores[it] }
                val best = ordered.firstOrNull()
                val second = ordered.getOrNull(1)
                winningScore = best?.let { scores[it] } ?: 0.0
                val secondScore = second?.let { scores[it] } ?: 0.0
                confidence = if (winningScore > 0.0) winningScore / max(0.75, secondScore) else 0.0

                if (lockIndex == null) {
                    if (best != null && winningScore >= 2.0 && confidence >= 1.18) {
                        lockIndex = best
                        state = SignalCameraLockState.LOCKED
                    } else {
                        state = SignalCameraLockState.SEARCHING
                    }
                } else {
                    if (best != null && winningScore >= 1.15) {
                        lockIndex = best
                        lowLockFrames = 0
                        state = SignalCameraLockState.LOCKED
                    } else {
                        lowLockFrames++
                        if (lowLockFrames >= 16) {
                            lockIndex = null
                            lowLockFrames = 0
                            state = SignalCameraLockState.LOST
                        } else {
                            state = SignalCameraLockState.LOCKED
                        }
                    }
                }
            }

            val selectedDisplayCenter = if (autoLock && lockIndex != null) {
                val index = lockIndex!!
                val rawX = ((index % gridColumns) + 0.5f) / gridColumns.toFloat()
                val rawY = ((index / gridColumns) + 0.5f) / gridRows.toFloat()
                rawToDisplay(rawX, rawY, rotation)
            } else {
                manualCenterX to manualCenterY
            }

            val rawCenter = displayToRaw(selectedDisplayCenter.first, selectedDisplayCenter.second, rotation)
            val luma = roiSignal(
                bufferLimit = buffer.limit(),
                getter = { index -> buffer.get(index).toInt() and 0xFF },
                rowStride = plane.rowStride,
                pixelStride = plane.pixelStride,
                width = width,
                height = height,
                centerX = rawCenter.first,
                centerY = rawCenter.second,
                fraction = roiMode.fraction,
                profile = opticalProfile
            )

            main.post {
                onSample(
                    CameraLumaSample(
                        timestampMs = System.currentTimeMillis(),
                        luma = luma,
                        roiCenterX = selectedDisplayCenter.first,
                        roiCenterY = selectedDisplayCenter.second,
                        lockState = state,
                        lockConfidence = confidence,
                        modulationScore = winningScore
                    )
                )
            }
        } finally {
            image.close()
        }
    }

    private fun roiSignal(
        bufferLimit: Int,
        getter: (Int) -> Int,
        rowStride: Int,
        pixelStride: Int,
        width: Int,
        height: Int,
        centerX: Float,
        centerY: Float,
        fraction: Double,
        profile: String
    ): Double {
        val roiWidth = (width * fraction).toInt().coerceIn(10, width)
        val roiHeight = (height * fraction).toInt().coerceIn(10, height)
        val centerPixelX = (centerX.coerceIn(0f, 1f) * width).toInt()
        val centerPixelY = (centerY.coerceIn(0f, 1f) * height).toInt()
        val x0 = (centerPixelX - roiWidth / 2).coerceIn(0, max(0, width - roiWidth))
        val y0 = (centerPixelY - roiHeight / 2).coerceIn(0, max(0, height - roiHeight))
        val x1 = min(width, x0 + roiWidth)
        val y1 = min(height, y0 + roiHeight)
        val stepX = max(1, roiWidth / 24)
        val stepY = max(1, roiHeight / 24)
        val samples = ArrayList<Int>(576)
        for (y in y0 until y1 step stepY) {
            for (x in x0 until x1 step stepX) {
                val index = y * rowStride + x * pixelStride
                if (index in 0 until bufferLimit) samples += getter(index)
            }
        }
        if (samples.isEmpty()) return 0.0
        samples.sort()
        // Screen flashes often contain a rolling-shutter diagonal transition. Median
        // luminance makes a partially swept frame land between stable ON/OFF states,
        // where the receiver's stable-frame gate can reject it. Torch mode uses an
        // upper quartile inside a pinpoint ROI so the point source remains measurable
        // after deliberate underexposure without letting one saturated pixel dominate.
        val quantile = if (profile == "torch") 0.75 else 0.50
        val index = ((samples.lastIndex) * quantile).roundToInt().coerceIn(0, samples.lastIndex)
        return samples[index].toDouble()
    }

    private fun displayToRaw(x: Float, y: Float, rotation: Int): Pair<Float, Float> = when (rotation) {
        90 -> y to (1f - x)
        180 -> (1f - x) to (1f - y)
        270 -> (1f - y) to x
        else -> x to y
    }

    private fun rawToDisplay(x: Float, y: Float, rotation: Int): Pair<Float, Float> = when (rotation) {
        90 -> (1f - y) to x
        180 -> (1f - x) to (1f - y)
        270 -> y to (1f - x)
        else -> x to y
    }
}

@Composable
fun SignalCameraLumaPreview(
    modifier: Modifier = Modifier,
    zoomRatio: Float = 1f,
    roiMode: SignalCameraRoiMode = SignalCameraRoiMode.FOCUS,
    roiCenterX: Float = 0.5f,
    roiCenterY: Float = 0.5f,
    autoLock: Boolean = false,
    lockState: SignalCameraLockState = if (autoLock) SignalCameraLockState.SEARCHING else SignalCameraLockState.MANUAL,
    searchGeneration: Int = 0,
    exposureReduction: Float = 0f,
    opticalProfile: String = "screen",
    onRoiMoved: (Float, Float) -> Unit = { _, _ -> },
    onCameraInfo: (SignalCameraInfo) -> Unit = {},
    onSample: (CameraLumaSample) -> Unit,
    onError: (String) -> Unit
) {
    val currentSample by rememberUpdatedState(onSample)
    val currentError by rememberUpdatedState(onError)
    val currentCameraInfo by rememberUpdatedState(onCameraInfo)
    val currentRoiMoved by rememberUpdatedState(onRoiMoved)
    val analysisExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    val providerHolder = remember { arrayOfNulls<ProcessCameraProvider>(1) }
    val cameraHolder = remember { arrayOfNulls<CameraXCamera>(1) }
    val lastAppliedZoom = remember { floatArrayOf(Float.NaN) }
    val lastAppliedExposure = remember { intArrayOf(Int.MIN_VALUE) }
    val analyzer = remember { SignalCameraLumaAnalyzer { sample -> currentSample(sample) } }
    analyzer.updateConfiguration(roiMode, roiCenterX, roiCenterY, autoLock, searchGeneration, opticalProfile)

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    if (size.width > 0 && size.height > 0) {
                        currentRoiMoved(
                            (offset.x / size.width.toFloat()).coerceIn(0f, 1f),
                            (offset.y / size.height.toFloat()).coerceIn(0f, 1f)
                        )
                    }
                }
            }
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                PreviewView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    // FIT_CENTER makes tap-to-position coordinates stable. Optical zoom supplies range.
                    scaleType = PreviewView.ScaleType.FIT_CENTER
                    val owner = context.findLifecycleOwner()
                    if (owner == null) {
                        currentError("Camera preview could not find an Android lifecycle owner.")
                    } else {
                        val future = ProcessCameraProvider.getInstance(context)
                        future.addListener({
                            runCatching {
                                val provider = future.get()
                                providerHolder[0] = provider
                                val preview = Preview.Builder().build().also { it.setSurfaceProvider(surfaceProvider) }
                                val analysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()
                                    .also { it.setAnalyzer(analysisExecutor, analyzer) }
                                provider.unbindAll()
                                val camera = provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                                cameraHolder[0] = camera
                                val zoomState = camera.cameraInfo.zoomState.value
                                val minZoom = zoomState?.minZoomRatio ?: 1f
                                val maxZoom = zoomState?.maxZoomRatio ?: 1f
                                val target = zoomRatio.coerceIn(minZoom, maxZoom)
                                camera.cameraControl.setZoomRatio(target)
                                lastAppliedZoom[0] = target
                                val exposureRange = camera.cameraInfo.exposureState.exposureCompensationRange
                                val exposureTarget = if (exposureRange.lower < 0) {
                                    (exposureRange.lower * exposureReduction.coerceIn(0f, 1f)).roundToInt().coerceIn(exposureRange.lower, exposureRange.upper)
                                } else 0
                                camera.cameraControl.setExposureCompensationIndex(exposureTarget)
                                lastAppliedExposure[0] = exposureTarget
                                currentCameraInfo(SignalCameraInfo(target, maxZoom))
                            }.onFailure { currentError(it.message ?: "Camera receiver failed to start.") }
                        }, ContextCompat.getMainExecutor(context))
                    }
                }
            },
            update = {
                cameraHolder[0]?.let { camera ->
                    val zoomState = camera.cameraInfo.zoomState.value
                    val minZoom = zoomState?.minZoomRatio ?: 1f
                    val maxZoom = zoomState?.maxZoomRatio ?: 1f
                    val target = zoomRatio.coerceIn(minZoom, maxZoom)
                    if (!lastAppliedZoom[0].isFinite() || abs(lastAppliedZoom[0] - target) >= 0.01f) {
                        camera.cameraControl.setZoomRatio(target)
                        lastAppliedZoom[0] = target
                        currentCameraInfo(SignalCameraInfo(target, maxZoom))
                    }
                    val exposureRange = camera.cameraInfo.exposureState.exposureCompensationRange
                    val exposureTarget = if (exposureRange.lower < 0) {
                        (exposureRange.lower * exposureReduction.coerceIn(0f, 1f)).roundToInt().coerceIn(exposureRange.lower, exposureRange.upper)
                    } else 0
                    if (lastAppliedExposure[0] != exposureTarget) {
                        camera.cameraControl.setExposureCompensationIndex(exposureTarget)
                        lastAppliedExposure[0] = exposureTarget
                    }
                }
            }
        )

        val fraction = roiMode.fraction.toFloat()
        val border = when (lockState) {
            SignalCameraLockState.LOCKED -> SignalGreen
            SignalCameraLockState.LOST -> SignalRed
            SignalCameraLockState.SEARCHING -> SignalAmber
            SignalCameraLockState.MANUAL -> SignalAmber.copy(alpha = 0.82f)
        }
        Canvas(Modifier.fillMaxSize()) {
            val roiWidth = size.width * fraction
            val roiHeight = size.height * fraction
            val left = (size.width * roiCenterX - roiWidth / 2f).coerceIn(0f, max(0f, size.width - roiWidth))
            val top = (size.height * roiCenterY - roiHeight / 2f).coerceIn(0f, max(0f, size.height - roiHeight))
            drawRect(border, Offset(left, top), Size(roiWidth, roiHeight), style = Stroke(width = 3f))
            val centerX = left + roiWidth / 2f
            val centerY = top + roiHeight / 2f
            val arm = min(roiWidth, roiHeight) * 0.09f
            drawLine(border.copy(alpha = 0.85f), Offset(centerX - arm, centerY), Offset(centerX + arm, centerY), strokeWidth = 2f)
            drawLine(border.copy(alpha = 0.85f), Offset(centerX, centerY - arm), Offset(centerX, centerY + arm), strokeWidth = 2f)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { providerHolder[0]?.unbindAll() }
            analysisExecutor.shutdownNow()
        }
    }
}

/** Adaptive high/low detector for uncontrolled optical amplitude. */
class AdaptiveLevelDetector(
    private val minimumSpan: Double,
    private val hysteresisFraction: Double = 0.10
) {
    private var low = Double.POSITIVE_INFINITY
    private var high = Double.NEGATIVE_INFINITY
    private var state: Boolean? = null

    data class Detection(val state: Boolean?, val low: Double, val high: Double, val threshold: Double, val span: Double)

    fun reset() {
        low = Double.POSITIVE_INFINITY
        high = Double.NEGATIVE_INFINITY
        state = null
    }

    fun feed(value: Double): Detection {
        if (!value.isFinite()) return Detection(state, low, high, Double.NaN, Double.NaN)
        if (!low.isFinite()) low = value
        if (!high.isFinite()) high = value
        if (value < low) low = value else low += (value - low) * 0.003
        if (value > high) high = value else high += (value - high) * 0.003
        if (high < low) high = low
        val span = high - low
        val threshold = (high + low) / 2.0
        if (span >= minimumSpan) {
            val hysteresis = max(minimumSpan * 0.05, span * hysteresisFraction)
            state = when (state) {
                true -> value >= threshold - hysteresis
                false -> value > threshold + hysteresis
                null -> value > threshold
            }
        }
        return Detection(state, low, high, threshold, span)
    }
}

object SignalQrRenderer {
    fun bitmap(payload: String, sizePx: Int = 900): Bitmap {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 2,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val pixels = IntArray(sizePx * sizePx)
        val black = 0xFF000000.toInt()
        val white = 0xFFFFFFFF.toInt()
        for (y in 0 until sizePx) for (x in 0 until sizePx) pixels[y * sizePx + x] = if (matrix[x, y]) black else white
        return Bitmap.createBitmap(pixels, sizePx, sizePx, Bitmap.Config.ARGB_8888)
    }
}

/** Continuous QR scanning with actual camera zoom rather than preview-only magnification. */
@Composable
fun SignalQrScanner(
    modifier: Modifier = Modifier,
    zoomRatio: Float = 1f,
    onZoomInfo: (SignalCameraInfo) -> Unit = {},
    onPayload: (String) -> Unit,
    onError: (String) -> Unit
) {
    val currentPayload by rememberUpdatedState(onPayload)
    val currentError by rememberUpdatedState(onError)
    val currentZoomInfo by rememberUpdatedState(onZoomInfo)
    val viewHolder = remember { arrayOfNulls<DecoratedBarcodeView>(1) }
    val lastRequestedZoom = remember { floatArrayOf(Float.NaN) }

    fun applyZoom(view: DecoratedBarcodeView, requested: Float) {
        view.barcodeView.changeCameraParameters { parameters ->
            if (!parameters.isZoomSupported) {
                view.post { currentZoomInfo(SignalCameraInfo(1f, 1f)) }
                parameters
            } else {
                val ratios = parameters.zoomRatios.orEmpty()
                val maxIndex = parameters.maxZoom.coerceAtLeast(0)
                val desiredPercent = (requested.coerceAtLeast(1f) * 100f).toInt()
                val index = if (ratios.isNotEmpty()) {
                    ratios.indices.minByOrNull { abs(ratios[it] - desiredPercent) }?.coerceAtMost(maxIndex) ?: 0
                } else {
                    ((requested - 1f) / max(1f, requested) * maxIndex).toInt().coerceIn(0, maxIndex)
                }
                parameters.zoom = index
                val actual = ratios.getOrNull(index)?.div(100f) ?: (1f + index.toFloat() / max(1, maxIndex))
                val maxRatio = ratios.getOrNull(maxIndex)?.div(100f) ?: max(1f, actual)
                view.post { currentZoomInfo(SignalCameraInfo(actual, maxRatio)) }
                parameters
            }
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            DecoratedBarcodeView(context).apply {
                viewHolder[0] = this
                barcodeView.decoderFactory = DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))
                statusView.text = "Collecting MethodMesh QR frames"
                decodeContinuous(object : BarcodeCallback {
                    override fun barcodeResult(result: BarcodeResult?) {
                        val text = result?.text.orEmpty()
                        if (text.isNotBlank()) currentPayload(text)
                    }
                    override fun possibleResultPoints(resultPoints: MutableList<com.google.zxing.ResultPoint>?) = Unit
                })
                runCatching {
                    resume()
                    postDelayed({
                        applyZoom(this, zoomRatio)
                        lastRequestedZoom[0] = zoomRatio
                    }, 350L)
                }.onFailure { currentError(it.message ?: "QR receiver failed to start.") }
            }
        },
        update = { view ->
            if (!lastRequestedZoom[0].isFinite() || abs(lastRequestedZoom[0] - zoomRatio) >= 0.01f) {
                applyZoom(view, zoomRatio)
                lastRequestedZoom[0] = zoomRatio
            }
        }
    )

    DisposableEffect(Unit) {
        onDispose { runCatching { viewHolder[0]?.pause() } }
    }
}

private fun Context.findLifecycleOwner(): LifecycleOwner? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is LifecycleOwner) return current
        current = current.baseContext
    }
    return current as? LifecycleOwner
}
