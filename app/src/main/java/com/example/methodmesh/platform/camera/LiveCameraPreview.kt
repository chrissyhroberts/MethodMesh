package com.example.methodmesh.platform.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.CameraController.OutputSize
import java.util.concurrent.Executors
import androidx.compose.runtime.rememberUpdatedState
import androidx.camera.core.CameraSelector
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Transport-level live camera surface. Capability modules supply their own
 * overlays and interpretation; this component only owns camera lifecycle.
 */
@Composable
fun LiveCameraPreview(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onError: (String) -> Unit = {},
    restartKey: Any = Unit,
    /** Runs on a single worker. Return promptly; the surface always closes the image. */
    onAnalysisFrame: ((ImageProxy) -> Unit)? = null,
    analysisResolution: android.util.Size? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember(context) {
        LifecycleCameraController(context.applicationContext).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        }
    }

    val currentAnalysis = rememberUpdatedState(onAnalysisFrame)
    val currentError = rememberUpdatedState(onError)
    DisposableEffect(controller, lifecycleOwner, enabled, restartKey, onAnalysisFrame != null, analysisResolution) {
        val active = java.util.concurrent.atomic.AtomicBoolean(true)
        val executor = if (enabled && onAnalysisFrame != null) Executors.newSingleThreadExecutor() else null
        runCatching {
            if (executor != null) {
                controller.setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
                controller.imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                controller.imageAnalysisTargetSize = analysisResolution?.let { OutputSize(it) }
                controller.setImageAnalysisAnalyzer(executor) { image ->
                    try { if (active.get()) currentAnalysis.value?.invoke(image) }
                    catch (e: Exception) { if (active.get()) currentError.value(e.message ?: "Camera analysis failed") }
                    finally { image.close() }
                }
            } else {
                controller.clearImageAnalysisAnalyzer()
                controller.setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            }
            if (enabled) controller.bindToLifecycle(lifecycleOwner) else controller.unbind()
        }.onFailure { error ->
            active.set(false)
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
            executor?.shutdown()
            onError(error.message ?: "Camera preview is unavailable.")
        }

        onDispose {
            active.set(false)
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
            executor?.shutdown()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            PreviewView(viewContext).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
                this.controller = controller
            }
        },
        update = { previewView ->
            previewView.controller = controller
        }
    )
}
