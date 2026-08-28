package com.example.methodmesh.platform.camera

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
    restartKey: Any = Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember(context) {
        LifecycleCameraController(context.applicationContext).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        }
    }

    DisposableEffect(controller, lifecycleOwner, enabled, restartKey) {
        if (enabled) {
            runCatching {
                controller.bindToLifecycle(lifecycleOwner)
            }.onFailure { error ->
                onError(error.message ?: "Camera preview is unavailable.")
            }
        } else {
            controller.unbind()
        }

        onDispose {
            controller.unbind()
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
