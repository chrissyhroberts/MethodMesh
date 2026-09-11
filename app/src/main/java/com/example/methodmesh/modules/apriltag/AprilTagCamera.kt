package com.example.methodmesh.modules.apriltag

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Paint
import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.util.SizeF
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

internal data class CameraSettings(
    val family: String,
    val tagSizeMm: Double,
    val targetTagId: Int,
    val intrinsicsMode: String,
    val fx: Double,
    val fy: Double,
    val cx: Double,
    val cy: Double,
    val intrinsicsWidth: Int,
    val intrinsicsHeight: Int,
    val threads: Int,
    val quadDecimate: Double,
    val refineEdges: Boolean
)

internal enum class AprilTagHudRole { TARGET, REFERENCE, MOVING }

internal data class AprilTagHud(
    val roles: Map<Int, AprilTagHudRole> = emptyMap(),
    val points: List<PixelPoint> = emptyList(),
    val connectPoints: Boolean = false,
    val closePolygon: Boolean = false,
    val lines: List<String> = emptyList(),
    val banner: String = ""
)

@Composable
internal fun AprilTagCameraSurface(
    settings: CameraSettings,
    modifier: Modifier = Modifier,
    onFrame: (AprilTagFrame) -> Unit,
    onImageTap: ((PixelPoint) -> Unit)? = null,
    hud: AprilTagHud = AprilTagHud()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) permissionLauncher.launch(Manifest.permission.CAMERA) }

    if (!granted) {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
            Text("Camera permission is required for this AprilTag method.", Modifier.padding(16.dp))
        }
        return
    }

    val controller = remember(context) {
        LifecycleCameraController(context.applicationContext).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
        }
    }
    val executor = remember(settings) { Executors.newSingleThreadExecutor() }
    val latestFrame = remember { mutableStateOf<AprilTagFrame?>(null) }
    val latestOnFrame = rememberUpdatedState(onFrame)
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val lastProcessed = remember { AtomicLong(0L) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    DisposableEffect(controller, lifecycleOwner, executor, settings) {
        controller.setImageAnalysisAnalyzer(executor) { image ->
            val now = System.currentTimeMillis()
            if (now - lastProcessed.get() < 70L) {
                image.close()
                return@setImageAnalysisAnalyzer
            }
            lastProcessed.set(now)
            try {
                val rotated = RotatedLuma.from(image)
                val intrinsics = CameraIntrinsicsResolver.resolve(context, settings, rotated.width, rotated.height, image.imageInfo.rotationDegrees)
                val result = AprilTagNativeBridge.detect(
                    gray = rotated.bytes,
                    width = rotated.width,
                    height = rotated.height,
                    config = AprilTagNativeBridge.DetectorConfig(
                        family = settings.family,
                        threads = settings.threads,
                        quadDecimate = settings.quadDecimate,
                        refineEdges = settings.refineEdges,
                        tagSizeMeters = settings.tagSizeMm / 1000.0
                    ),
                    intrinsics = intrinsics
                )
                val frame = AprilTagFrame(
                    detections = result.getOrDefault(emptyList()),
                    width = rotated.width,
                    height = rotated.height,
                    rotationDegrees = image.imageInfo.rotationDegrees,
                    intrinsics = intrinsics,
                    error = result.exceptionOrNull()?.message.orEmpty()
                )
                mainHandler.post {
                    latestFrame.value = frame
                    latestOnFrame.value(frame)
                }
            } finally {
                image.close()
            }
        }
        runCatching { controller.bindToLifecycle(lifecycleOwner) }
            .onFailure { error ->
                latestOnFrame.value(AprilTagFrame(emptyList(), 0, 0, 0, null, error = error.message ?: "Camera unavailable."))
            }
        onDispose {
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
            executor.shutdown()
        }
    }

    val normalColor = MaterialTheme.colorScheme.onSurface
    val targetColor = MaterialTheme.colorScheme.primary
    val referenceColor = MaterialTheme.colorScheme.tertiary
    val movingColor = MaterialTheme.colorScheme.secondary
    val pointColor = MaterialTheme.colorScheme.primary

    Box(modifier = modifier.onSizeChanged { viewSize = it }) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                PreviewView(viewContext).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FIT_CENTER
                    this.controller = controller
                }
            },
            update = { it.controller = controller }
        )

        val frame = latestFrame.value
        if (frame != null && frame.width > 0 && frame.height > 0) {
            Canvas(Modifier.fillMaxSize()) {
                val scale = min(size.width / frame.width, size.height / frame.height)
                val ox = (size.width - frame.width * scale) / 2f
                val oy = (size.height - frame.height * scale) / 2f
                fun imageOffset(p: PixelPoint) = Offset(ox + (p.x * scale).toFloat(), oy + (p.y * scale).toFloat())
                val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = 18.sp.toPx()
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }

                frame.detections.forEach { det ->
                    val role = hud.roles[det.id]
                    val color = when (role) {
                        AprilTagHudRole.TARGET -> targetColor
                        AprilTagHudRole.REFERENCE -> referenceColor
                        AprilTagHudRole.MOVING -> movingColor
                        null -> normalColor
                    }
                    val pts = det.corners.map(::imageOffset)
                    if (pts.size == 4) {
                        for (i in 0 until 4) drawLine(color, pts[i], pts[(i + 1) % 4], strokeWidth = if (role == null) 3f else 6f)
                        drawCircle(color, radius = if (role == null) 6f else 9f, center = imageOffset(det.center), style = Stroke(width = 3f))
                        val roleText = when (role) {
                            AprilTagHudRole.TARGET -> "TARGET"
                            AprilTagHudRole.REFERENCE -> "REF"
                            AprilTagHudRole.MOVING -> "MOVING"
                            null -> "TAG"
                        }
                        labelPaint.color = color.toArgb()
                        drawContext.canvas.nativeCanvas.drawText("$roleText ${det.id}", pts.minByOrNull { it.y }?.x ?: pts[0].x, (pts.minOfOrNull { it.y } ?: pts[0].y) - 8f, labelPaint)
                    }
                }

                val hudPts = hud.points.map(::imageOffset)
                hudPts.forEachIndexed { index, p ->
                    drawCircle(pointColor, radius = 9f, center = p)
                    drawCircle(Color.Black, radius = 9f, center = p, style = Stroke(width = 2f))
                    labelPaint.color = pointColor.toArgb()
                    drawContext.canvas.nativeCanvas.drawText("${index + 1}", p.x + 11f, p.y - 8f, labelPaint)
                }
                if (hud.connectPoints && hudPts.size >= 2) {
                    hudPts.zipWithNext().forEach { (a, b) -> drawLine(pointColor, a, b, strokeWidth = 4f) }
                    if (hud.closePolygon && hudPts.size >= 3) drawLine(pointColor, hudPts.last(), hudPts.first(), strokeWidth = 4f)
                }
            }
        }

        if (hud.lines.isNotEmpty() || hud.banner.isNotBlank()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .widthIn(max = 310.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (hud.banner.isNotBlank()) Text(hud.banner, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    hud.lines.filter { it.isNotBlank() }.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }

        // This transparent Compose layer deliberately sits above PreviewView. AndroidView
        // can consume pointer events before a parent modifier sees them; keeping the tap
        // surface here makes planar capture and tap-to-select reliable.
        if (onImageTap != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(frame?.timestampIso, viewSize) {
                        detectTapGestures { offset ->
                            frame?.let { current ->
                                fitCenterViewToImage(offset, viewSize, current.width, current.height)?.let(onImageTap)
                            }
                        }
                    }
            )
        }
    }
}

private data class RotatedLuma(val bytes: ByteArray, val width: Int, val height: Int) {
    companion object {
        fun from(image: ImageProxy): RotatedLuma {
            val plane = image.planes[0]
            val raw = ByteArray(image.width * image.height)
            val buffer = plane.buffer
            val base = buffer.position()
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            for (y in 0 until image.height) {
                for (x in 0 until image.width) {
                    raw[y * image.width + x] = buffer.get(base + y * rowStride + x * pixelStride)
                }
            }
            return rotate(raw, image.width, image.height, image.imageInfo.rotationDegrees)
        }

        private fun rotate(raw: ByteArray, width: Int, height: Int, degrees: Int): RotatedLuma = when ((degrees % 360 + 360) % 360) {
            90 -> {
                val out = ByteArray(raw.size)
                for (y in 0 until height) for (x in 0 until width) out[x * height + (height - 1 - y)] = raw[y * width + x]
                RotatedLuma(out, height, width)
            }
            180 -> {
                val out = ByteArray(raw.size)
                for (i in raw.indices) out[raw.lastIndex - i] = raw[i]
                RotatedLuma(out, width, height)
            }
            270 -> {
                val out = ByteArray(raw.size)
                for (y in 0 until height) for (x in 0 until width) out[(width - 1 - x) * height + y] = raw[y * width + x]
                RotatedLuma(out, height, width)
            }
            else -> RotatedLuma(raw, width, height)
        }
    }
}

private object CameraIntrinsicsResolver {
    fun resolve(context: Context, settings: CameraSettings, width: Int, height: Int, rotationDegrees: Int): CameraIntrinsics? {
        if (settings.intrinsicsMode == "manual" && settings.fx > 0 && settings.fy > 0) {
            val sx = if (settings.intrinsicsWidth > 0) width.toDouble() / settings.intrinsicsWidth else 1.0
            val sy = if (settings.intrinsicsHeight > 0) height.toDouble() / settings.intrinsicsHeight else 1.0
            return CameraIntrinsics(
                settings.fx * sx,
                settings.fy * sy,
                settings.cx * sx,
                settings.cy * sy,
                width,
                height,
                "manual",
                if (settings.intrinsicsWidth <= 0 || settings.intrinsicsHeight <= 0) "Manual intrinsics were not tied to a calibration image size." else ""
            )
        }
        return androidIntrinsics(context, width, height, rotationDegrees)
    }

    private fun androidIntrinsics(context: Context, width: Int, height: Int, rotationDegrees: Int): CameraIntrinsics? {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = manager.cameraIdList.firstOrNull { id ->
            manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: return null
        val c = manager.getCameraCharacteristics(cameraId)
        val rawWidth = if (rotationDegrees == 90 || rotationDegrees == 270) height else width
        val rawHeight = if (rotationDegrees == 90 || rotationDegrees == 270) width else height
        val active = c.get(CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE)
            ?: c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
        if (active == null) {
            val estimated = physicalEstimate(c, rawWidth, rawHeight) ?: return null
            return rotateIntrinsics(estimated, rotationDegrees)
        }
        val intrinsic = c.get(CameraCharacteristics.LENS_INTRINSIC_CALIBRATION)
        val raw = if (intrinsic != null && intrinsic.size >= 4) {
            CameraIntrinsics(
                fx = intrinsic[0].toDouble() * rawWidth / active.width(),
                fy = intrinsic[1].toDouble() * rawHeight / active.height(),
                cx = (intrinsic[2].toDouble() - active.left) * rawWidth / active.width(),
                cy = (intrinsic[3].toDouble() - active.top) * rawHeight / active.height(),
                width = rawWidth,
                height = rawHeight,
                source = "android_factory_scaled",
                warning = "Factory intrinsics are scaled to the CameraX analysis buffer; digital crop/distortion may add error."
            )
        } else {
            val estimated = physicalEstimate(c, rawWidth, rawHeight) ?: return null
            return rotateIntrinsics(estimated, rotationDegrees)
        }
        return rotateIntrinsics(raw, rotationDegrees)
    }

    private fun physicalEstimate(c: CameraCharacteristics, width: Int, height: Int): CameraIntrinsics? {
        val focal = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()?.toDouble() ?: return null
        val sensor: SizeF = c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: return null
        if (sensor.width <= 0 || sensor.height <= 0) return null
        return CameraIntrinsics(
            fx = focal / sensor.width * width,
            fy = focal / sensor.height * height,
            cx = (width - 1) / 2.0,
            cy = (height - 1) / 2.0,
            width = width,
            height = height,
            source = "android_physical_estimate",
            warning = "Estimated from focal length and sensor size; calibrate for quantitative work."
        )
    }

    private fun rotateIntrinsics(raw: CameraIntrinsics, degrees: Int): CameraIntrinsics = when ((degrees % 360 + 360) % 360) {
        90 -> CameraIntrinsics(raw.fy, raw.fx, raw.height - 1.0 - raw.cy, raw.cx, raw.height, raw.width, raw.source, raw.warning)
        180 -> CameraIntrinsics(raw.fx, raw.fy, raw.width - 1.0 - raw.cx, raw.height - 1.0 - raw.cy, raw.width, raw.height, raw.source, raw.warning)
        270 -> CameraIntrinsics(raw.fy, raw.fx, raw.cy, raw.width - 1.0 - raw.cx, raw.height, raw.width, raw.source, raw.warning)
        else -> raw
    }
}

private fun fitCenterViewToImage(offset: Offset, viewSize: IntSize, imageWidth: Int, imageHeight: Int): PixelPoint? {
    if (viewSize.width <= 0 || viewSize.height <= 0 || imageWidth <= 0 || imageHeight <= 0) return null
    val scale = min(viewSize.width.toDouble() / imageWidth, viewSize.height.toDouble() / imageHeight)
    val drawWidth = imageWidth * scale
    val drawHeight = imageHeight * scale
    val ox = (viewSize.width - drawWidth) / 2.0
    val oy = (viewSize.height - drawHeight) / 2.0
    if (offset.x < ox || offset.x > ox + drawWidth || offset.y < oy || offset.y > oy + drawHeight) return null
    return PixelPoint((offset.x - ox) / scale, (offset.y - oy) / scale)
}
