package com.example.methodmesh.modules.screentools

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import java.time.Instant

private fun CapabilityScreenContext.setting(key: String, default: String = ""): String =
    action.settings[key] ?: action.settings["input_$key"] ?: request.settings[key] ?: request.settings["input_$key"] ?: default

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

object ScreenCaptureCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = ScreenCaptureMethod.ID
    override val title = "Capture screen"
    override val description = "Capture a user-authorised visible screen into volatile process memory."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val appContext = LocalContext.current
        val projectionManager = appContext.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        var status by rememberSaveable { mutableStateOf("Screen capture has not started.") }
        var authorised by rememberSaveable { mutableStateOf(false) }
        var committed by rememberSaveable { mutableStateOf(false) }

        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK || result.data == null) {
                status = "Screen-sharing permission was not granted."
                authorised = false
                return@rememberLauncherForActivityResult
            }
            val serviceIntent = Intent(appContext, ScreenProjectionService::class.java)
                .setAction(ScreenProjectionService.ACTION_START)
                .putExtra(ScreenProjectionService.EXTRA_RESULT_CODE, result.resultCode)
                .putExtra(ScreenProjectionService.EXTRA_DATA, result.data)
            ContextCompat.startForegroundService(appContext, serviceIntent)
            authorised = true
            status = "Capture is ready. Use Capture in the notification, then return here."
        }

        fun requestCapturePermission() {
            val activity = appContext.findActivity()
            if (activity == null) {
                status = "No Android activity is available for screen-sharing consent."
                return
            }
            launcher.launch(projectionManager.createScreenCaptureIntent())
        }

        fun stopCapture() {
            appContext.startService(Intent(appContext, ScreenProjectionService::class.java).setAction(ScreenProjectionService.ACTION_STOP))
            authorised = false
            status = "Capture session stopped. Frames remain only in this process."
        }

        fun commit() {
            val frame = ScreenToolsSession.active()
            if (frame == null) {
                status = "Capture a frame before committing."
                return
            }
            val request = ScreenCaptureMethod.request(
                action = ScreenCaptureMethod.ID,
                context = context.request.invocationContext.asMap(ScreenCaptureMethod.ID) + context.action.settings,
                signals = emptyList(),
                inputs = emptyList()
            )
            val values = mapOf(
                ScreenCaptureFields.STATUS to "succeeded",
                ScreenCaptureFields.ERROR to "",
                ScreenCaptureFields.FRAME_REF to frame.ref,
                ScreenCaptureFields.SOURCE_KIND to "media_projection",
                ScreenCaptureFields.CAPTURE_TIME_ISO to frame.capturedAt.ifBlank { Instant.now().toString() },
                ScreenCaptureFields.WIDTH_PX to frame.bitmap.width.toString(),
                ScreenCaptureFields.HEIGHT_PX to frame.bitmap.height.toString(),
                ScreenCaptureFields.FRAME_COUNT to ScreenToolsSession.frames.size.toString(),
                ScreenCaptureFields.RETENTION to "volatile_process_memory"
            )
            committed = true
            status = "Frame ready for Commit. It is not a durable image attachment."
            val execution = ScreenCaptureMethod.result(request, values, context.request.invocationContext)
            if (context.submitsImmediately) onConfirmed(execution)
        }

        LaunchedEffect(context.startsImmediately) {
            if (context.startsImmediately && context.setting("clear_previous_frames", "true").equals("true", true)) {
                ScreenToolsSession.clear()
            }
        }

        val frame = ScreenToolsSession.active()
        val result = if (committed && frame != null) {
            val request = ScreenCaptureMethod.request(
                action = ScreenCaptureMethod.ID,
                context = context.request.invocationContext.asMap(ScreenCaptureMethod.ID) + context.action.settings,
                signals = emptyList(),
                inputs = emptyList()
            )
            ScreenCaptureMethod.result(request, mapOf(
                ScreenCaptureFields.STATUS to "succeeded",
                ScreenCaptureFields.FRAME_REF to frame.ref,
                ScreenCaptureFields.SOURCE_KIND to "media_projection",
                ScreenCaptureFields.CAPTURE_TIME_ISO to frame.capturedAt,
                ScreenCaptureFields.WIDTH_PX to frame.bitmap.width.toString(),
                ScreenCaptureFields.HEIGHT_PX to frame.bitmap.height.toString(),
                ScreenCaptureFields.FRAME_COUNT to ScreenToolsSession.frames.size.toString(),
                ScreenCaptureFields.RETENTION to "volatile_process_memory"
            ), context.request.invocationContext)
        } else null

        CapabilityScreenScaffold(
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = result,
            resultPreview = result?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty(),
            onBack = onBack,
            onRetry = { committed = false; status = "Ready for another capture." },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = { stopCapture(); onCancel() }
        ) {
            Text("The frame stays in volatile process memory and is never presented as an ODK image attachment.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Text(status, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Button(onClick = ::requestCapturePermission, enabled = !authorised) {
                Text(if (authorised) "Capture session active" else "Authorise screen capture")
            }
            if (authorised) {
                OutlinedButton(onClick = ::stopCapture) { Text("Stop capture session") }
            }
            if (frame != null) {
                Spacer(Modifier.height(8.dp))
                Text("Latest frame: ${frame.bitmap.width} × ${frame.bitmap.height} · ${ScreenToolsSession.frames.size} retained")
                Image(
                    bitmap = frame.bitmap.asImageBitmap(),
                    contentDescription = "Latest captured screen",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = ::commit) { Text("Use latest frame") }
            }
        }
    }
}
