package com.example.researchos.modules.scaledphoto

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.media.ExifInterface
import android.graphics.Matrix
import android.net.Uri
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.example.researchos.calibration.CalibrationRepository
import com.example.researchos.core.researchos.ExecutionResult
import com.example.researchos.core.researchos.withInvocationContext
import com.example.researchos.settings.SettingsState
import com.example.researchos.settings.MethodSetting
import com.example.researchos.transport.OutputFormatter
import com.example.researchos.transport.workflow.ui.*
import org.json.JSONArray
import java.io.File
import java.time.Instant
import java.util.concurrent.Executors
import android.os.Handler
import android.os.Looper

object ScaledPhotoCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100ScaledPhotoMethod.ID
    override val title = "Scaled photo selector"
    override val description = "Capture a ruler-calibrated photo and select regions on a configurable grid."

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val appContext = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val settings = remember(context.action.settings) { SettingsState(ScaledPhotoModule.capabilitySettings()[capabilityId].orEmpty()).also { state ->
            val definitions = ScaledPhotoModule.capabilitySettings()[capabilityId].orEmpty().associateBy { it.id }
            context.action.settings.forEach { (k, v) -> when (definitions[k]) {
                is MethodSetting.BooleanSetting -> state.setBoolean(k, v.equals("true", true))
                is MethodSetting.IntSetting -> v.toIntOrNull()?.let { state.setInt(k, it) }
                is MethodSetting.FloatSetting -> v.toFloatOrNull()?.let { state.setFloat(k, it) }
                else -> state.setString(k, v)
            } }
        } }
        val calibration by CalibrationRepository.calibration
        val controller = remember(appContext) { LifecycleCameraController(appContext).apply { cameraSelector = androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA } }
        val executor = remember { Executors.newSingleThreadExecutor() }
        var capturedFile by remember { mutableStateOf<File?>(null) }
        var annotatedFile by remember { mutableStateOf<File?>(null) }
        var bitmap by remember { mutableStateOf<Bitmap?>(null) }
        var selected by remember { mutableStateOf(setOf<String>()) }
        var status by remember { mutableStateOf("Align the physical ruler with the HUD, then capture.") }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        DisposableEffect(controller, lifecycleOwner) { controller.bindToLifecycle(lifecycleOwner); onDispose { controller.unbind(); executor.shutdown() } }
        fun capture() {
            val file = File(appContext.cacheDir, "researchos-scaled-original-${System.currentTimeMillis()}.jpg")
            val save = {
                controller.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), executor, object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val raw = BitmapFactory.decodeFile(file.absolutePath)
                    val oriented = raw?.let { orientForCapture(it, settings.getString("capture_orientation")) }
                    if (oriented != null && oriented !== raw) {
                        file.outputStream().use { oriented.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                    }
                    runCatching { ExifInterface(file.absolutePath).apply { setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString()); saveAttributes() } }
                    capturedFile = file; bitmap = oriented ?: raw; status = "Photo captured. Select grid cells, then use the result."
                }
                override fun onError(exception: ImageCaptureException) { status = "Capture failed: ${exception.message ?: "camera error"}" }
                })
            }
            if (settings.getBoolean("macro_mode")) {
                val centre = SurfaceOrientedMeteringPointFactory(1f, 1f).createPoint(.5f, .5f)
                controller.cameraControl?.setZoomRatio(1.25f)
                controller.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(centre).setAutoCancelDuration(5, java.util.concurrent.TimeUnit.SECONDS).build())
                Handler(Looper.getMainLooper()).postDelayed(save, 350L)
            } else {
                controller.cameraControl?.setZoomRatio(1f)
                save()
            }
        }
        fun finish() {
            val original = capturedFile ?: return
            val annotated = File(appContext.cacheDir, "researchos-scaled-annotated-${System.currentTimeMillis()}.jpg")
            val source = BitmapFactory.decodeFile(original.absolutePath) ?: return
            val out = source.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(out)
            val dimensions = gridDimensions(source.width, source.height, settings, calibration.dpPerMm)
            val rows = dimensions.first
            val cols = dimensions.second
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = AndroidColor.WHITE }
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = AndroidColor.argb(90, 30, 136, 229) }
            val cw = out.width.toFloat() / cols; val ch = out.height.toFloat() / rows
            for (r in 0 until rows) for (c in 0 until cols) { val id = "r${r + 1}c${c + 1}"; val l = c * cw; val t = r * ch; val rr = l + cw; val b = t + ch; if (id in selected) canvas.drawRect(l, t, rr, b, fill); if (settings.getBoolean("show_grid")) canvas.drawRect(l, t, rr, b, paint) }
            val originPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = AndroidColor.WHITE; textSize = (minOf(cw, ch) * .22f).coerceIn(24f, 72f); setShadowLayer(5f, 2f, 2f, AndroidColor.BLACK) }
            canvas.drawText("r1c1", 12f, originPaint.textSize + 12f, originPaint)
            annotated.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            runCatching {
                val orientation = ExifInterface(original.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                ExifInterface(annotated.absolutePath).apply { setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString()); saveAttributes() }
            }
            annotatedFile = annotated
            val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", original).toString()
            val annotatedUri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", annotated).toString()
            val grid = JSONArray(selected.toList().sorted()).toString()
            val pixelsPerMm = (source.width * 0.6f / settings.getFloat("ruler_length_mm").coerceAtLeast(1f)).toString()
            val hudRatio = settings.getString("hud_scale_ratio").toFloatOrNull() ?: 1f
            result = As100ScaledPhotoMethod.execute(As100ScaledPhotoMethod.request(action = capabilityId, context = context.request.invocationContext.asMap(capabilityId) + mapOf(
                "original_image_uri" to uri, "annotated_image_uri" to annotatedUri, "grid_selection_json" to grid,
                "ruler_length_mm" to settings.getFloat("ruler_length_mm").toString(), "calibration_pixels_per_mm" to pixelsPerMm,
                "hud_scale_ratio" to hudRatio.toString(), "hud_display_length_mm" to settings.getFloat("ruler_length_mm").toString(), "ruler_target_length_mm" to (settings.getFloat("ruler_length_mm") * hudRatio).toString(),
                "photo_captured_at" to Instant.now().toString(), "overlay_completed_at" to Instant.now().toString()
            )), transport = context.request.source).withInvocationContext(context.request.invocationContext)
            status = "Original and annotated images saved."; if (context.startsImmediately) onConfirmed(result!!)
        }
        CapabilityScreenScaffold(title = title, capabilityId = capabilityId, context = context, canGoBack = context.stepNumber > 1, capturedResult = result, resultPreview = result?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty(), onBack = onBack, onRetry = { capturedFile = null; annotatedFile = null; bitmap = null; selected = emptySet(); result = null; status = "Align the physical ruler with the HUD, then capture." }, onConfirm = { result?.let(onConfirmed) }, onCancel = onCancel) {
            Text(status)
            if (!context.startsImmediately) {
                OutlinedTextField(value = settings.getFloat("ruler_length_mm").toString(), onValueChange = { it.toFloatOrNull()?.let { value -> settings.setFloat("ruler_length_mm", value) } }, label = { Text("HUD ruler length (mm)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Button(onClick = { settings.setString("capture_orientation", if (settings.getString("capture_orientation") == "landscape") "portrait" else "landscape") }, modifier = Modifier.fillMaxWidth()) { Text("Photo orientation: ${settings.getString("capture_orientation")}") }
                OutlinedTextField(value = settings.getInt("grid_rows").toString(), onValueChange = { it.toIntOrNull()?.let { value -> settings.setInt("grid_rows", value) } }, label = { Text("Grid rows (default 10)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = settings.getInt("grid_columns").toString(), onValueChange = { it.toIntOrNull()?.let { value -> settings.setInt("grid_columns", value) } }, label = { Text("Grid columns (default 10)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Button(onClick = { val next = listOf("1", "2", "3", "4"); settings.setString("hud_scale_ratio", next[(next.indexOf(settings.getString("hud_scale_ratio")).coerceAtLeast(0) + 1) % next.size]) }, modifier = Modifier.fillMaxWidth()) { Text("HUD scale: 1:${settings.getString("hud_scale_ratio")}") }
                Text("System calibration: %.2f dp/mm".format(calibration.dpPerMm))
                Button(onClick = { settings.setBoolean("macro_mode", !settings.getBoolean("macro_mode")) }, modifier = Modifier.fillMaxWidth()) { Text("Macro focus: ${if (settings.getBoolean("macro_mode")) "on" else "off"}") }
            }
            if (bitmap == null) {
                Box(Modifier.fillMaxWidth().height(640.dp)) {
                    AndroidView(modifier = Modifier.fillMaxSize(), factory = { PreviewView(it).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE; this.controller = controller } })
                    val density = androidx.compose.ui.platform.LocalDensity.current
                    Canvas(Modifier.fillMaxSize()) {
                        val ratio = settings.getString("hud_scale_ratio").toFloatOrNull() ?: 1f
                        // The line remains the calibrated on-screen reference.  The ratio changes
                        // the real-world ruler it represents: 50 mm at 1:2 is still a 50 mm line,
                        // but the operator aligns it to a 100 mm physical ruler.
                        val referenceMm = settings.getFloat("ruler_length_mm")
                        val displayedMm = referenceMm * ratio
                        val hud = referenceMm * calibration.dpPerMm * density.density
                        val edge = 44f * density.density
                        val top = 56f * density.density
                        val bottom = 56f * density.density
                        val clamped = hud.coerceAtMost((size.height - top - bottom).coerceAtLeast(40f))
                        val x = edge.coerceAtMost(size.width - 24f)
                        val y1 = (size.height - clamped) / 2f
                        val y2 = y1 + clamped
                        drawLine(androidx.compose.ui.graphics.Color.White, Offset(x, y1), Offset(x, y2), 4f)
                        drawLine(androidx.compose.ui.graphics.Color.White, Offset(x - 14f, y1), Offset(x + 14f, y1), 4f)
                        drawLine(androidx.compose.ui.graphics.Color.White, Offset(x - 14f, y2), Offset(x + 14f, y2), 4f)
                    }
                    Text("${settings.getFloat("ruler_length_mm") * (settings.getString("hud_scale_ratio").toFloatOrNull() ?: 1f)} mm ruler · ${settings.getFloat("ruler_length_mm")} mm HUD · 1:${settings.getString("hud_scale_ratio")}", color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp).background(MaterialTheme.colorScheme.scrim.copy(alpha = .6f)))
                }
                Button(onClick = ::capture, modifier = Modifier.fillMaxWidth()) { Text("Capture original photo") }
            } else {
                val dimensions = gridDimensions(bitmap!!.width, bitmap!!.height, settings, calibration.dpPerMm)
                GridSelector(bitmap!!, dimensions.first, dimensions.second, settings.getBoolean("show_grid"), selected) { selected = it }
                Button(onClick = ::finish, modifier = Modifier.fillMaxWidth()) { Text("Use photo result") }
            }
        }
    }
}

private fun orientForCapture(bitmap: Bitmap, orientation: String): Bitmap {
    val wantsPortrait = orientation != "landscape"
    val isPortrait = bitmap.height >= bitmap.width
    if (wantsPortrait == isPortrait) return bitmap
    val matrix = Matrix().apply { postRotate(90f) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

private fun gridDimensions(widthPx: Int, heightPx: Int, settings: SettingsState, dpPerMm: Float): Pair<Int, Int> {
    return settings.getInt("grid_rows").coerceAtLeast(1) to settings.getInt("grid_columns").coerceAtLeast(1)
}

@Composable
private fun GridSelector(bitmap: Bitmap, rows: Int, columns: Int, showGrid: Boolean, selected: Set<String>, onChanged: (Set<String>) -> Unit) {
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    Box(Modifier.fillMaxWidth().height(420.dp)) {
        Image(bitmap.asImageBitmap(), "Captured photo", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        Canvas(Modifier.fillMaxSize()) {
            val image = fittedImageRect(size.width, size.height, bitmap.width, bitmap.height)
            val cw = image.width / columns.coerceAtLeast(1); val ch = image.height / rows.coerceAtLeast(1)
            for (r in 0 until rows) for (c in 0 until columns) { val id = "r${r + 1}c${c + 1}"; val l = image.left + c * cw; val t = image.top + r * ch; if (id in selected) drawRect(androidx.compose.ui.graphics.Color(0x553098D9), topLeft = Offset(l, t), size = androidx.compose.ui.geometry.Size(cw, ch)); if (showGrid) drawRect(androidx.compose.ui.graphics.Color.White, topLeft = Offset(l, t), size = androidx.compose.ui.geometry.Size(cw, ch), style = androidx.compose.ui.graphics.drawscope.Stroke(2f)) }
        }
        Box(Modifier.fillMaxSize().onSizeChanged { boxSize = it }.pointerInput(rows, columns, selected, boxSize) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val initialCell = cellAt(down.position, boxSize, rows, columns, bitmap)
                var moved = false
                var swipeSelection = selected
                drag(down.id) { change ->
                    change.consume()
                    moved = true
                    cellAt(change.position, boxSize, rows, columns, bitmap)?.let {
                        swipeSelection = swipeSelection + it
                        onChanged(swipeSelection)
                    }
                }
                if (!moved) initialCell?.let { onChanged(if (it in selected) selected - it else selected + it) }
            }
        })
    }
}

private fun fittedImageRect(width: Float, height: Float, imageWidth: Int, imageHeight: Int): Rect {
    if (width <= 0f || height <= 0f || imageWidth <= 0 || imageHeight <= 0) return Rect.Zero
    val scale = minOf(width / imageWidth.toFloat(), height / imageHeight.toFloat())
    val fittedWidth = imageWidth * scale
    val fittedHeight = imageHeight * scale
    return Rect((width - fittedWidth) / 2f, (height - fittedHeight) / 2f, (width + fittedWidth) / 2f, (height + fittedHeight) / 2f)
}

private fun cellAt(offset: Offset, boxSize: IntSize, rows: Int, columns: Int, bitmap: Bitmap): String? {
    if (boxSize.width <= 0 || boxSize.height <= 0) return null
    val image = fittedImageRect(boxSize.width.toFloat(), boxSize.height.toFloat(), bitmap.width, bitmap.height)
    if (!image.contains(offset)) return null
    val column = ((offset.x - image.left) / (image.width / columns.coerceAtLeast(1))).toInt()
    val row = ((offset.y - image.top) / (image.height / rows.coerceAtLeast(1))).toInt()
    if (row !in 0 until rows || column !in 0 until columns) return null
    return "r${row + 1}c${column + 1}"
}
