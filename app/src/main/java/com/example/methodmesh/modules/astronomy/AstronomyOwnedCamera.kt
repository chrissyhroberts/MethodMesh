package com.example.methodmesh.modules.astronomy

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

@Composable
internal fun AstronomyOwnedCamera(
    analyzer: StarFrameAnalyzer? = null,
    facing: String = "rear",
    modifier: Modifier = Modifier.fillMaxWidth().height(280.dp),
    onError: (String) -> Unit = {}
) {
    val app = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current
    val reportError by rememberUpdatedState(onError)
    var view by remember { mutableStateOf<PreviewView?>(null) }
    AndroidView(factory = { context -> PreviewView(context).also { view = it } },
        modifier = modifier.background(Color.Black))
    DisposableEffect(view, lifecycle, analyzer, facing) {
        val lease = AstronomyCameraLease()
        val executor = Executors.newSingleThreadExecutor()
        val preview = Preview.Builder().build()
        val analysis = analyzer?.let { target ->
            ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                .also { it.setAnalyzer(executor, target) }
        }
        val uses = listOfNotNull<UseCase>(preview, analysis).toTypedArray()
        view?.let { surface ->
            preview.setSurfaceProvider(surface.surfaceProvider)
            val future = ProcessCameraProvider.getInstance(app)
            future.addListener({
                if (lease.active) {
                    try {
                        lease.bind {
                            val provider = future.get()
                            val selector = if (facing == "front") CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                            try { provider.bindToLifecycle(lifecycle, selector, *uses) }
                            catch (failure: Exception) { runCatching { provider.unbind(*uses) }; throw failure }
                            ({ analysis?.clearAnalyzer(); provider.unbind(*uses) })
                        }
                    } catch (failure: Exception) {
                        if (lease.active) reportError("Camera unavailable: " + (failure.message ?: "binding failed"))
                    }
                }
            }, ContextCompat.getMainExecutor(app))
        }
        onDispose {
            try { lease.close() }
            finally { analysis?.clearAnalyzer(); executor.shutdown() }
        }
    }
}
