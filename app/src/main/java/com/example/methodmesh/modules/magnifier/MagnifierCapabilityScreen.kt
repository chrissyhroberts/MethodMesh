package com.example.methodmesh.modules.magnifier

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.methodmesh.core.crypto.Digests
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.InvocationContext
import com.example.methodmesh.settings.MethodSetting
import com.example.methodmesh.settings.SettingsState
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityPresentationMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.concurrent.Executors

object MagnifierCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100MagnifierCaptureMethod.ID
    override val title = "Magnifier"
    override val description = "Zoom in with live Normal, Contrast, Mono or Negative filtering, hold focus if useful, freeze the frame, inspect it, then capture the image you want to return."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val appContext = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val definitions = remember { MagnifierModule.capabilitySettings()[capabilityId].orEmpty() }
        val settings = remember(context.action.settings) {
            SettingsState(definitions) { key, value ->
                context.onSettingsChanged(mapOf(key to value.toString()))
            }.also { state -> initialiseSettings(state, definitions, context.action.settings) }
        }

        val controller = remember(appContext) { LifecycleCameraController(appContext) }
        val executor = remember { Executors.newSingleThreadExecutor() }
        val mainHandler = remember { Handler(Looper.getMainLooper()) }

        var hasCameraPermission by remember { mutableStateOf(cameraPermissionGranted(appContext)) }
        var cameraFacing by rememberSaveable {
            mutableStateOf(settings.getString("camera_facing").takeIf { it == "front" } ?: "rear")
        }
        var requestedZoom by rememberSaveable { mutableStateOf(settings.getFloat("initial_zoom").coerceIn(1f, 10f)) }
        var actualZoom by rememberSaveable { mutableStateOf(requestedZoom) }
        var maxZoom by rememberSaveable { mutableStateOf(10f) }
        var torchAvailable by rememberSaveable { mutableStateOf(false) }
        val allowFrontLight = settings.getBoolean("allow_front_light")
        val defaultLight = settings.getString("default_light").ifBlank { "off" }
        val frontLightBrightness = settings.getFloat("front_light_brightness").coerceIn(0.25f, 1f)
        var torchOn by rememberSaveable { mutableStateOf(defaultLight == "back") }
        var frontLightOn by rememberSaveable { mutableStateOf(allowFrontLight && defaultLight == "front") }
        var torchAtFreeze by rememberSaveable { mutableStateOf(false) }
        var frontLightAtFreeze by rememberSaveable { mutableStateOf(false) }
        var focusHeld by rememberSaveable { mutableStateOf(false) }
        var filterWireValue by rememberSaveable { mutableStateOf(settings.getString("default_filter").ifBlank { "normal" }) }
        var frozenPath by rememberSaveable { mutableStateOf<String?>(null) }
        var frozenTimeIso by rememberSaveable { mutableStateOf<String?>(null) }
        var finalPath by rememberSaveable { mutableStateOf<String?>(null) }
        var finalSha256 by rememberSaveable { mutableStateOf<String?>(null) }
        var capturedTimeIso by rememberSaveable { mutableStateOf<String?>(null) }
        var error by rememberSaveable { mutableStateOf<String?>(null) }
        var statusText by rememberSaveable { mutableStateOf("Move close to the subject and adjust zoom.") }
        var inspectionScale by rememberSaveable { mutableStateOf(1f) }
        var inspectionPanX by rememberSaveable { mutableStateOf(0f) }
        var inspectionPanY by rememberSaveable { mutableStateOf(0f) }

        val cameraPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            hasCameraPermission = granted || cameraPermissionGranted(appContext)
            if (hasCameraPermission) {
                error = null
                statusText = "Move close to the subject and adjust zoom."
            } else {
                error = "Camera permission is required for the magnifier."
                statusText = "Camera permission denied."
            }
        }

        val allowTorch = settings.getBoolean("allow_torch")
        val cameraSelector = if (cameraFacing == "front") CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        val currentFilter = MagnifierFilter.fromWireValue(filterWireValue)
        val frozenBitmap: Bitmap? = remember(frozenPath, filterWireValue) {
            frozenPath
                ?.let(::File)
                ?.takeIf(File::isFile)
                ?.let(::decodeOrientedBitmap)
                ?.let { bitmap ->
                    val filtered = applyMagnifierFilter(bitmap, currentFilter)
                    bitmap.recycle()
                    filtered
                }
        }

        DisposableEffect(frozenBitmap) {
            onDispose { frozenBitmap?.takeUnless { it.isRecycled }?.recycle() }
        }

        fun applyZoom(target: Float) {
            val zoomState = controller.zoomState.value
            val deviceMin = zoomState?.minZoomRatio ?: 1f
            val deviceMax = (zoomState?.maxZoomRatio ?: 10f).coerceAtMost(10f).coerceAtLeast(deviceMin)
            maxZoom = deviceMax
            val clamped = target.coerceIn(deviceMin, deviceMax)
            requestedZoom = target.coerceIn(1f, 10f)
            actualZoom = clamped
            controller.cameraControl?.setZoomRatio(clamped)
            mainHandler.postDelayed({
                controller.zoomState.value?.let { settled ->
                    actualZoom = settled.zoomRatio
                    maxZoom = settled.maxZoomRatio.coerceAtMost(10f).coerceAtLeast(settled.minZoomRatio)
                }
            }, 120L)
        }

        fun setCameraFacing(facing: String) {
            val normalized = if (facing == "front") "front" else "rear"
            if (normalized == cameraFacing) return
            controller.cameraControl?.enableTorch(false)
            controller.cameraControl?.cancelFocusAndMetering()
            torchOn = false
            torchAvailable = false
            focusHeld = false
            cameraFacing = normalized
            settings.setString("camera_facing", normalized)
            statusText = if (normalized == "front") "Front camera selected. Adjust zoom." else "Rear camera selected. Adjust zoom."
        }

        fun setTorch(enabled: Boolean) {
            if (!allowTorch || !torchAvailable) return
            if (enabled) frontLightOn = false
            controller.cameraControl?.enableTorch(enabled)
            torchOn = enabled
            statusText = if (enabled) "Back light on." else "Back light off."
        }

        fun setFrontLight(enabled: Boolean) {
            if (!allowFrontLight) return
            if (enabled && torchOn) {
                controller.cameraControl?.enableTorch(false)
                torchOn = false
            }
            frontLightOn = enabled
            statusText = if (enabled) "Front light on." else "Front light off."
        }

        fun toggleFocusHold() {
            val cameraControl = controller.cameraControl ?: return
            if (focusHeld) {
                cameraControl.cancelFocusAndMetering()
                focusHeld = false
                statusText = "Continuous autofocus restored."
            } else {
                val point = SurfaceOrientedMeteringPointFactory(1f, 1f).createPoint(0.5f, 0.5f)
                cameraControl.startFocusAndMetering(
                    FocusMeteringAction.Builder(point)
                        .disableAutoCancel()
                        .build()
                )
                focusHeld = true
                statusText = "Focus held at the centre point."
            }
        }

        fun freezeFrame() {
            error = null
            val target = File(appContext.cacheDir, "methodmesh-magnifier-freeze-${System.currentTimeMillis()}.jpg")
            controller.takePicture(
                ImageCapture.OutputFileOptions.Builder(target).build(),
                executor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        mainHandler.post {
                            torchAtFreeze = torchOn
                            frontLightAtFreeze = frontLightOn
                            controller.cameraControl?.enableTorch(false)
                            torchOn = false
                            frontLightOn = false
                            frozenPath?.let(::File)?.takeIf { it != target }?.delete()
                            frozenPath = target.absolutePath
                            frozenTimeIso = Instant.now().toString()
                            inspectionScale = 1f
                            inspectionPanX = 0f
                            inspectionPanY = 0f
                            statusText = "Frame frozen. Inspect it; the live filter remains applied and can still be changed."
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        target.delete()
                        mainHandler.post {
                            error = exception.message ?: "Camera capture failed."
                            statusText = "Could not freeze the frame."
                        }
                    }
                }
            )
        }

        fun returnToLive() {
            frozenPath?.let(::File)?.delete()
            frozenPath = null
            frozenTimeIso = null
            inspectionScale = 1f
            inspectionPanX = 0f
            inspectionPanY = 0f
            statusText = "Live view restored."
        }

        fun useFrozenFrame() {
            val frozen = frozenPath?.let(::File)?.takeIf(File::isFile) ?: return
            val finalFile = File(appContext.cacheDir, "methodmesh-magnifier-${System.currentTimeMillis()}.jpg")
            val written = runCatching { writeFilteredJpeg(frozen, finalFile, currentFilter) }.getOrDefault(false)
            if (!written) {
                finalFile.delete()
                error = "The frozen frame could not be decoded or written."
                statusText = "Could not create the result image."
                return
            }
            finalPath?.let(::File)?.takeIf { it != finalFile }?.delete()
            finalPath = finalFile.absolutePath
            finalSha256 = Digests.sha256Hex(finalFile.readBytes())
            capturedTimeIso = Instant.now().toString()
            error = null
            statusText = "Image ready."
        }

        fun clearRunFiles() {
            frozenPath?.let(::File)?.delete()
            finalPath?.let(::File)?.delete()
            frozenPath = null
            frozenTimeIso = null
            finalPath = null
            finalSha256 = null
            capturedTimeIso = null
            torchAtFreeze = false
            frontLightAtFreeze = false
            controller.cameraControl?.enableTorch(false)
            torchOn = allowTorch && defaultLight == "back"
            if (torchOn && torchAvailable) controller.cameraControl?.enableTorch(true)
            frontLightOn = allowFrontLight && defaultLight == "front"
            error = null
            statusText = "Move close to the subject and adjust zoom."
            inspectionScale = 1f
            inspectionPanX = 0f
            inspectionPanY = 0f
        }

        val result: ExecutionResult? = remember(
            finalPath,
            finalSha256,
            filterWireValue,
            requestedZoom,
            actualZoom,
            cameraFacing,
            torchAtFreeze,
            frontLightAtFreeze,
            focusHeld,
            frozenTimeIso,
            capturedTimeIso,
            error,
            context.request.invocationContext
        ) {
            val file = finalPath?.let(::File)?.takeIf(File::isFile) ?: return@remember null
            val uri = FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                file
            ).toString()
            val values = linkedMapOf(
                MagnifierFields.STATUS to "succeeded",
                MagnifierFields.IMAGE_URI to uri,
                MagnifierFields.IMAGE_SHA256 to finalSha256.orEmpty(),
                MagnifierFields.CAMERA_FACING to cameraFacing,
                MagnifierFields.FILTER_MODE to currentFilter.wireValue,
                MagnifierFields.ZOOM_REQUESTED_RATIO to requestedZoom.toString(),
                MagnifierFields.ZOOM_ACTUAL_RATIO to actualZoom.toString(),
                MagnifierFields.TORCH_MODE to if (torchAtFreeze) "on" else "off",
                MagnifierFields.FRONT_LIGHT_MODE to if (frontLightAtFreeze) "on" else "off",
                MagnifierFields.FOCUS_MODE to if (focusHeld) "held" else "continuous",
                MagnifierFields.FROZEN_TIME_ISO to frozenTimeIso.orEmpty(),
                MagnifierFields.CAPTURED_TIME_ISO to capturedTimeIso.orEmpty(),
                MagnifierFields.ERROR to ""
            )
            values[MagnifierFields.METADATA_JSON] = JSONObject(values as Map<*, *>).toString()
            val request = As100MagnifierCaptureMethod.request(
                action = capabilityId,
                context = context.request.invocationContext.asMap(capabilityId) + values
            )
            As100MagnifierCaptureMethod.result(
                request = request,
                values = values,
                invocation = context.request.invocationContext
            )
        }

        DisposableEffect(controller, lifecycleOwner, hasCameraPermission, cameraFacing) {
            if (hasCameraPermission) {
                runCatching {
                    controller.cameraSelector = cameraSelector
                    controller.bindToLifecycle(lifecycleOwner)
                }.onFailure { failure ->
                    error = "${if (cameraFacing == "front") "Front" else "Rear"} camera is unavailable: ${failure.message ?: "camera binding failed"}"
                    statusText = "Selected camera unavailable."
                }
            }
            onDispose { controller.unbind() }
        }

        DisposableEffect(executor) {
            onDispose { executor.shutdown() }
        }

        DisposableEffect(frontLightOn, frontLightBrightness) {
            val activity = appContext.findActivity()
            val window = activity?.window
            val previousBrightness = window?.attributes?.screenBrightness
            if (frontLightOn && window != null) {
                val attributes = window.attributes
                attributes.screenBrightness = frontLightBrightness
                window.attributes = attributes
            }
            onDispose {
                if (window != null && previousBrightness != null) {
                    val attributes = window.attributes
                    attributes.screenBrightness = previousBrightness
                    window.attributes = attributes
                }
            }
        }

        DisposableEffect(context.presentationMode) {
            val activity = appContext.findActivity()
            val window = activity?.window
            val insetsController = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
            if (context.presentationMode == CapabilityPresentationMode.IntentLaunch && insetsController != null) {
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
            }
            onDispose {
                if (window != null && insetsController != null) {
                    insetsController.show(WindowInsetsCompat.Type.systemBars())
                }
            }
        }

        LaunchedEffect(hasCameraPermission) {
            if (!hasCameraPermission) {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        LaunchedEffect(controller, hasCameraPermission, cameraFacing) {
            if (hasCameraPermission) {
                delay(250)
                torchAvailable = controller.cameraInfo?.hasFlashUnit() == true
                applyZoom(requestedZoom)
                if (allowTorch && torchAvailable && torchOn) {
                    frontLightOn = false
                    controller.cameraControl?.enableTorch(true)
                } else if (torchOn && !torchAvailable) {
                    torchOn = false
                    statusText = "Back light unavailable on the selected camera."
                }
                if (focusHeld) {
                    val point = SurfaceOrientedMeteringPointFactory(1f, 1f).createPoint(0.5f, 0.5f)
                    controller.cameraControl?.startFocusAndMetering(
                        FocusMeteringAction.Builder(point)
                            .disableAutoCancel()
                            .build()
                    )
                }
            }
        }

        LaunchedEffect(allowTorch) {
            if ((!allowTorch || !torchAvailable) && torchOn) {
                controller.cameraControl?.enableTorch(false)
                torchOn = false
            }
        }

        CapabilityScreenScaffold(
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = result,
            resultPreview = result?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty(),
            onBack = onBack,
            onRetry = ::clearRunFiles,
            onConfirm = { result?.let(onConfirmed) },
            onCancel = {
                frozenPath?.let(::File)?.delete()
                finalPath?.let(::File)?.delete()
                onCancel()
            }
        ) {
            Text(statusText)
            error?.takeIf(String::isNotBlank)?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }
            Spacer(Modifier.height(12.dp))

            if (!hasCameraPermission) {
                Text("Camera access is required to use the magnifier.")
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Allow camera") }
                return@CapabilityScreenScaffold
            }

            if (frozenPath == null) {
                if (context.settingShouldBeShown("camera_facing")) {
                    Text("Camera", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (cameraFacing == "rear") {
                            Button(onClick = { setCameraFacing("rear") }, modifier = Modifier.weight(1f)) { Text("Rear") }
                            OutlinedButton(onClick = { setCameraFacing("front") }, modifier = Modifier.weight(1f)) { Text("Front") }
                        } else {
                            OutlinedButton(onClick = { setCameraFacing("rear") }, modifier = Modifier.weight(1f)) { Text("Rear") }
                            Button(onClick = { setCameraFacing("front") }, modifier = Modifier.weight(1f)) { Text("Front") }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(560.dp)
                        .background(if (frontLightOn) Color.White else MaterialTheme.colorScheme.surfaceVariant)
                        .padding(if (frontLightOn) 34.dp else 0.dp)
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { viewContext ->
                            val cameraController = controller
                            PreviewView(viewContext).apply {
                                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                this.controller = cameraController
                                applyLiveMagnifierFilter(currentFilter)
                            }
                        },
                        update = { previewView ->
                            previewView.controller = controller
                            previewView.applyLiveMagnifierFilter(currentFilter)
                        }
                    )
                    Text(
                        text = "%.2f×".format(actualZoom),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text("Live filter", style = MaterialTheme.typography.labelLarge)
                MagnifierFilterSelector(
                    selected = currentFilter,
                    onSelected = { filterWireValue = it.wireValue }
                )
                Spacer(Modifier.height(8.dp))
                Text("Zoom %.2f×".format(actualZoom), style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = actualZoom.coerceIn(1f, maxZoom.coerceAtLeast(1f)),
                    onValueChange = ::applyZoom,
                    valueRange = 1f..maxZoom.coerceAtLeast(1f)
                )
                Text("Light", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (allowTorch && torchAvailable) {
                        if (torchOn) {
                            Button(onClick = { setTorch(false) }, modifier = Modifier.weight(1f)) { Text("Back light") }
                        } else {
                            OutlinedButton(onClick = { setTorch(true) }, modifier = Modifier.weight(1f)) { Text("Back light") }
                        }
                    }
                    if (allowFrontLight) {
                        if (frontLightOn) {
                            Button(onClick = { setFrontLight(false) }, modifier = Modifier.weight(1f)) { Text("Front light") }
                        } else {
                            OutlinedButton(onClick = { setFrontLight(true) }, modifier = Modifier.weight(1f)) { Text("Front light") }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = ::toggleFocusHold, modifier = Modifier.fillMaxWidth()) {
                    Text(if (focusHeld) "Release focus" else "Hold focus")
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = ::freezeFrame, modifier = Modifier.fillMaxWidth()) {
                    Text("Freeze frame")
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(560.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clipToBounds()
                        .pointerInput(frozenPath, filterWireValue) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                inspectionScale = (inspectionScale * zoom).coerceIn(1f, 8f)
                                inspectionPanX += pan.x
                                inspectionPanY += pan.y
                            }
                        }
                ) {
                    frozenBitmap?.let { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Frozen magnifier frame",
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = inspectionScale
                                    scaleY = inspectionScale
                                    translationX = inspectionPanX
                                    translationY = inspectionPanY
                                },
                            contentScale = ContentScale.Fit
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Inspection filter", style = MaterialTheme.typography.labelLarge)
                MagnifierFilterSelector(
                    selected = currentFilter,
                    onSelected = { filterWireValue = it.wireValue }
                )
                Text(
                    "Pinch to zoom and drag the frozen image. Inspection pan/zoom does not resample the returned image.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = ::returnToLive, modifier = Modifier.weight(1f)) {
                        Text("Back to live")
                    }
                    Button(onClick = ::useFrozenFrame, modifier = Modifier.weight(1f)) {
                        Text("Use this image")
                    }
                }
            }
        }
    }
}

private fun initialiseSettings(
    state: SettingsState,
    definitions: List<MethodSetting>,
    supplied: Map<String, String>
) {
    val byId = definitions.associateBy { it.id }
    supplied.forEach { (key, value) ->
        when (byId[key]) {
            is MethodSetting.BooleanSetting -> state.setBoolean(key, value.equals("true", ignoreCase = true))
            is MethodSetting.IntSetting -> value.toIntOrNull()?.let { state.setInt(key, it) }
            is MethodSetting.FloatSetting -> value.toFloatOrNull()?.let { state.setFloat(key, it) }
            else -> state.setString(key, value)
        }
    }
}

@Composable
private fun MagnifierFilterSelector(
    selected: MagnifierFilter,
    onSelected: (MagnifierFilter) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        MagnifierFilter.values().toList().chunked(2).forEach { filters ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                filters.forEach { filter ->
                    FilterChip(
                        selected = selected == filter,
                        onClick = { onSelected(filter) },
                        label = { Text(filter.label()) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (filters.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private fun PreviewView.applyLiveMagnifierFilter(filter: MagnifierFilter) {
    val matrix = magnifierColorMatrix(filter)
    if (matrix == null) {
        setLayerType(View.LAYER_TYPE_NONE, null)
    } else {
        setLayerType(
            View.LAYER_TYPE_HARDWARE,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(matrix)
            }
        )
    }
    invalidate()
}

private fun MagnifierFilter.label(): String = when (this) {
    MagnifierFilter.NORMAL -> "Normal"
    MagnifierFilter.HIGH_CONTRAST -> "Contrast"
    MagnifierFilter.MONOCHROME -> "Mono"
    MagnifierFilter.NEGATIVE -> "Negative"
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun cameraPermissionGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
