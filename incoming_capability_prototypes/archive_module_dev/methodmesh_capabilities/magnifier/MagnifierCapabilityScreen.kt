package com.example.methodmesh.modules.magnifier

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.workflow.ui.*
import java.io.File
import java.time.Instant
import java.util.concurrent.Executors

object MagnifierCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100MagnifierMethod.ID
    override val title = "Visual inspection / Magnifier"
    override val description = "Full-screen camera inspection and capture."
    override val hostPresentation = CapabilityHostPresentation.Immersive

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val appContext = LocalContext.current.applicationContext
        val lifecycleOwner = LocalLifecycleOwner.current
        var cameraGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(appContext, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
        val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { cameraGranted = it }
        val controller = remember(appContext) { LifecycleCameraController(appContext).apply { cameraSelector = androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA } }
        val executor = remember { Executors.newSingleThreadExecutor() }
        var zoom by rememberSaveable { mutableFloatStateOf(((context.action.settings["initial_zoom"] ?: context.action.settings["input_initial_zoom"])?.toFloatOrNull() ?: 1f).coerceAtLeast(1f)) }
        var torch by rememberSaveable { mutableStateOf(false) }
        var focusLocked by rememberSaveable { mutableStateOf(false) }
        var filter by rememberSaveable { mutableStateOf(context.action.settings["default_filter"] ?: context.action.settings["input_default_filter"] ?: "normal") }
        var frozenPath by rememberSaveable { mutableStateOf("") }
        var frozenBitmap by remember(frozenPath) { mutableStateOf(frozenPath.takeIf { it.isNotBlank() }?.let(BitmapFactory::decodeFile)) }
        var status by rememberSaveable { mutableStateOf("Ready to inspect.") }
        val allowTorch = (context.action.settings["allow_torch"] ?: context.action.settings["input_allow_torch"])?.toBooleanStrictOrNull() ?: true

        DisposableEffect(controller, lifecycleOwner, cameraGranted) {
            if (cameraGranted) runCatching { controller.bindToLifecycle(lifecycleOwner) }.onFailure { status = it.message ?: "Camera unavailable." }
            onDispose { controller.unbind() }
        }
        DisposableEffect(Unit) { onDispose { executor.shutdown() } }
        LaunchedEffect(zoom, cameraGranted) { if (cameraGranted) runCatching { controller.cameraControl?.setZoomRatio(zoom) } }
        LaunchedEffect(torch, cameraGranted) { if (cameraGranted) runCatching { controller.cameraControl?.enableTorch(torch) } }

        fun focus(lock: Boolean) {
            val point = SurfaceOrientedMeteringPointFactory(1f, 1f).createPoint(.5f, .5f)
            if (lock) {
                runCatching { controller.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(point).disableAutoCancel().build()) }
                focusLocked = true; status = "Focus held at centre."
            } else {
                runCatching { controller.cameraControl?.cancelFocusAndMetering() }
                focusLocked = false; status = "Continuous autofocus restored."
            }
        }

        fun freeze() {
            val file = File(appContext.cacheDir, "methodmesh-magnifier-freeze-${System.currentTimeMillis()}.jpg")
            controller.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), executor, object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    frozenPath = file.absolutePath; frozenBitmap = BitmapFactory.decodeFile(file.absolutePath); status = "Frame frozen. Choose a filter or capture it."
                }
                override fun onError(exception: ImageCaptureException) { status = "Freeze failed: ${exception.message ?: "camera error"}" }
            })
        }

        fun finish() {
            val source = frozenBitmap ?: return
            val rendered = applyInspectionFilter(source, filter)
            val file = File(appContext.cacheDir, "methodmesh-magnifier-${System.currentTimeMillis()}.jpg")
            file.outputStream().use { rendered.compress(Bitmap.CompressFormat.JPEG, 94, it) }
            val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file).toString()
            val captured = Instant.now().toString()
            val settings = mapOf("image_uri" to uri, "filter" to filter, "zoom_ratio" to zoom.toString(), "torch" to torch.toString(), "focus_locked" to focusLocked.toString(), "captured_time_iso" to captured)
            val request = As100MagnifierMethod.request(capabilityId, context.request.invocationContext.asMap(capabilityId) + context.action.settings + settings, emptyList(), emptyList())
            val result = As100MagnifierMethod.result(request, As100MagnifierMethod.values(settings), context.request.invocationContext)
            onConfirmed(result)
        }

        if (!cameraGranted) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(title, style = MaterialTheme.typography.titleLarge)
                        Text("Camera permission is required for visual inspection.")
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }, modifier = Modifier.fillMaxWidth()) { Text("Allow camera") }
                        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Close") }
                    }
                }
            }
            return
        }

        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (context.stepNumber > 1) TextButton(onClick = onBack) { Text("Back") }
                TextButton(onClick = onCancel) { Text("Close") }
            }
            Box(Modifier.fillMaxWidth().weight(1f).background(MaterialTheme.colorScheme.surfaceVariant)) {
                val bitmap = frozenBitmap
                if (bitmap == null) {
                    AndroidView(modifier = Modifier.fillMaxSize(), factory = { PreviewView(it).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE; scaleType = PreviewView.ScaleType.FILL_CENTER; this.controller = controller } })
                    Text("${"%.1f".format(zoom)}×${if (torch) " · torch" else ""}${if (focusLocked) " · focus held" else ""}", modifier = Modifier.align(Alignment.TopStart).padding(8.dp).background(MaterialTheme.colorScheme.scrim.copy(alpha = .5f)).padding(6.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Image(applyInspectionFilter(bitmap, filter).asImageBitmap(), "Frozen inspection frame", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    Text("Frozen · ${filter.replace('_', ' ')}", modifier = Modifier.align(Alignment.TopStart).padding(8.dp).background(MaterialTheme.colorScheme.scrim.copy(alpha = .5f)).padding(6.dp), color = MaterialTheme.colorScheme.onPrimary)
                }
            }
            Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(status, style = MaterialTheme.typography.bodySmall)
                if (frozenBitmap == null) {
                    Slider(value = zoom, onValueChange = { zoom = it }, valueRange = 1f..10f, steps = 17)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { torch = !torch }, enabled = allowTorch, modifier = Modifier.weight(1f)) { Text(if (torch) "Torch off" else "Torch on") }
                        OutlinedButton(onClick = { focus(!focusLocked) }, modifier = Modifier.weight(1f)) { Text(if (focusLocked) "Release focus" else "Hold focus") }
                        Button(onClick = ::freeze, modifier = Modifier.weight(1f)) { Text("Freeze") }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("normal", "high_contrast", "monochrome", "negative").forEach { f -> FilterChip(filter == f, { filter = f }, { Text(f.replace('_', ' ')) }, modifier = Modifier.weight(1f)) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { frozenPath = ""; frozenBitmap = null; status = "Live inspection resumed." }, modifier = Modifier.weight(1f)) { Text("Resume") }
                        Button(onClick = ::finish, modifier = Modifier.weight(1f)) { Text("Capture image") }
                    }
                }
            }
        }
    }
}

private fun applyInspectionFilter(source: Bitmap, filter: String): Bitmap {
    if (filter == "normal") return source
    val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val matrix = when (filter) {
        "monochrome" -> ColorMatrix().apply { setSaturation(0f) }
        "negative" -> ColorMatrix(floatArrayOf(-1f,0f,0f,0f,255f, 0f,-1f,0f,0f,255f, 0f,0f,-1f,0f,255f, 0f,0f,0f,1f,0f))
        "high_contrast" -> ColorMatrix(floatArrayOf(1.7f,0f,0f,0f,-89.25f, 0f,1.7f,0f,0f,-89.25f, 0f,0f,1.7f,0f,-89.25f, 0f,0f,0f,1f,0f))
        else -> ColorMatrix()
    }
    canvas.drawBitmap(source, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply { colorFilter = ColorMatrixColorFilter(matrix) })
    return out
}
