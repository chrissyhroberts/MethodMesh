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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal data class SignalQrBurstFrame(
    val luma: ByteArray,
    val width: Int,
    val height: Int,
    val timestampNs: Long
)

/** Bounded in-memory camera burst. No video compression or QR decoding happens during capture. */
internal class SignalQrBurstFrameBuffer(
    private val maxFrames: Int = 240,
    private val maxDimension: Int = 480
) {
    private val frames = ArrayList<SignalQrBurstFrame>(maxFrames)

    @Synchronized fun clear() = frames.clear()
    @Synchronized fun size(): Int = frames.size
    @Synchronized fun isFull(): Boolean = frames.size >= maxFrames
    @Synchronized fun snapshot(): List<SignalQrBurstFrame> = frames.toList()

    @Synchronized fun offer(image: ImageProxy): Boolean {
        if (frames.size >= maxFrames) return false
        val plane = image.planes.firstOrNull() ?: return false
        val inputWidth = image.width
        val inputHeight = image.height
        if (inputWidth <= 0 || inputHeight <= 0) return false
        val side = min(inputWidth, inputHeight)
        val step = max(1, (side + maxDimension - 1) / maxDimension)
        val outSide = max(1, side / step)
        val x0 = (inputWidth - side) / 2
        val y0 = (inputHeight - side) / 2
        val out = ByteArray(outSide * outSide)
        val buffer = plane.buffer
        var outIndex = 0
        for (oy in 0 until outSide) {
            val y = y0 + oy * step
            for (ox in 0 until outSide) {
                val x = x0 + ox * step
                val index = y * plane.rowStride + x * plane.pixelStride
                out[outIndex++] = if (index in 0 until buffer.limit()) buffer.get(index) else 0
            }
        }
        frames += SignalQrBurstFrame(out, outSide, outSide, image.imageInfo.timestamp)
        return true
    }
}

private class SignalQrBurstCaptureAnalyzer(
    private val buffer: SignalQrBurstFrameBuffer,
    private val onCount: (Int) -> Unit
) : ImageAnalysis.Analyzer {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var active = false

    fun setActive(value: Boolean) { active = value }

    override fun analyze(image: ImageProxy) {
        try {
            if (!active || buffer.isFull()) return
            if (buffer.offer(image)) {
                val count = buffer.size()
                main.post { onCount(count) }
            }
        } finally {
            image.close()
        }
    }
}

/** CameraX preview whose analyzer only copies grayscale frames into a bounded buffer. */
@Composable
internal fun SignalQrBurstCapturePreview(
    modifier: Modifier,
    active: Boolean,
    buffer: SignalQrBurstFrameBuffer,
    zoomRatio: Float,
    onFrameCount: (Int) -> Unit,
    onCameraInfo: (SignalCameraInfo) -> Unit,
    onError: (String) -> Unit
) {
    val currentCount by rememberUpdatedState(onFrameCount)
    val currentInfo by rememberUpdatedState(onCameraInfo)
    val currentError by rememberUpdatedState(onError)
    val executor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    val providerHolder = remember { arrayOfNulls<ProcessCameraProvider>(1) }
    val cameraHolder = remember { arrayOfNulls<CameraXCamera>(1) }
    val lastAppliedZoom = remember { floatArrayOf(Float.NaN) }
    val analyzer = remember { SignalQrBurstCaptureAnalyzer(buffer) { currentCount(it) } }
    analyzer.setActive(active)

    AndroidView(
        modifier = modifier,
        factory = { context ->
            PreviewView(context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                scaleType = PreviewView.ScaleType.FIT_CENTER
                val owner = context.findLifecycleOwner()
                if (owner == null) {
                    currentError("QR Burst camera could not find an Android lifecycle owner.")
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
                                .also { it.setAnalyzer(executor, analyzer) }
                            provider.unbindAll()
                            val camera = provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                            cameraHolder[0] = camera
                            val zoomState = camera.cameraInfo.zoomState.value
                            val minZoom = zoomState?.minZoomRatio ?: 1f
                            val maxZoom = zoomState?.maxZoomRatio ?: 1f
                            val target = zoomRatio.coerceIn(minZoom, maxZoom)
                            camera.cameraControl.setZoomRatio(target)
                            lastAppliedZoom[0] = target
                            currentInfo(SignalCameraInfo(target, maxZoom))
                        }.onFailure { currentError(it.message ?: "QR Burst camera failed to start.") }
                    }, ContextCompat.getMainExecutor(context))
                }
            }
        },
        update = {
            analyzer.setActive(active)
            cameraHolder[0]?.let { camera ->
                val zoomState = camera.cameraInfo.zoomState.value
                val minZoom = zoomState?.minZoomRatio ?: 1f
                val maxZoom = zoomState?.maxZoomRatio ?: 1f
                val target = zoomRatio.coerceIn(minZoom, maxZoom)
                if (!lastAppliedZoom[0].isFinite() || abs(lastAppliedZoom[0] - target) >= 0.01f) {
                    camera.cameraControl.setZoomRatio(target)
                    lastAppliedZoom[0] = target
                    currentInfo(SignalCameraInfo(target, maxZoom))
                }
            }
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            runCatching { providerHolder[0]?.unbindAll() }
            executor.shutdownNow()
        }
    }
}

internal data class SignalQrBurstDecodeReport(
    val framesAnalyzed: Int,
    val qrDecodes: Int,
    val decodedPayloads: List<String>
)

/** Offline QR decode. Frames are processed only after capture has stopped. */
internal fun decodeQrBurstFrames(frames: List<SignalQrBurstFrame>): SignalQrBurstDecodeReport {
    val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true,
                DecodeHintType.CHARACTER_SET to "UTF-8"
            )
        )
    }
    val decoded = ArrayList<String>()
    var analyzed = 0
    frames.forEach { frame ->
        analyzed++
        if (frame.luma.isEmpty() || frame.width <= 0 || frame.height <= 0) return@forEach
        val source = PlanarYUVLuminanceSource(frame.luma, frame.width, frame.height, 0, 0, frame.width, frame.height, false)
        fun attempt(candidate: com.google.zxing.LuminanceSource): String? = runCatching {
            val bitmap = BinaryBitmap(HybridBinarizer(candidate))
            reader.decodeWithState(bitmap).text
        }.getOrNull().also { reader.reset() }
        val text = attempt(source) ?: attempt(source.invert())
        if (!text.isNullOrBlank()) decoded += text
    }
    return SignalQrBurstDecodeReport(analyzed, decoded.size, decoded)
}
