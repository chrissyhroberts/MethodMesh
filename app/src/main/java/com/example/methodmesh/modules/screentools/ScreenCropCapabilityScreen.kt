package com.example.methodmesh.modules.screentools

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import com.example.methodmesh.transport.workflow.ui.rememberRetainedExecutionResult
import org.json.JSONArray

private fun CapabilityScreenContext.cropSetting(key: String, default: String = ""): String =
    action.settings[key] ?: action.settings["input_$key"] ?: request.settings[key] ?: request.settings["input_$key"] ?: default

private fun parseRoi(raw: String): ScreenRegion? = runCatching {
    val values = raw.split(',', ';', ' ', '|').filter { it.isNotBlank() }.map { it.toFloat() }
    require(values.size == 4)
    ScreenRegion(values[0], values[1], values[2], values[3])
}.getOrNull()

object ScreenCropCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = ScreenCropMethod.ID
    override val title = "Crop image"
    override val description = "Create a volatile crop from a normalized region of a captured frame."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val frameRef = context.cropSetting("frame_ref")
        val frame = remember(frameRef, ScreenToolsSession.activeFrameId, ScreenToolsSession.frames.size) {
            if (frameRef.isBlank()) ScreenToolsSession.active()
            else ScreenToolsSession.frames.firstOrNull { it.ref == frameRef }
        }
        var roiText by rememberSaveable {
            mutableStateOf(context.cropSetting("roi").ifBlank { "0.0,0.0,1.0,1.0" })
        }
        var status by rememberSaveable { mutableStateOf("Enter a normalized ROI: left,top,right,bottom.") }
        var cropFrame by remember { mutableStateOf<ScreenFrame?>(null) }
        var result by rememberRetainedExecutionResult("${context.action.canonicalId}:crop")

        fun crop() {
            val source = frame ?: run {
                status = "No source frame is available. Capture a screen first."
                return
            }
            val region = parseRoi(roiText)
            if (region == null) {
                status = "ROI must contain four normalized values between 0 and 1."
                return
            }
            runCatching {
                val rect = android.graphics.Rect(
                    (region.left * source.bitmap.width).toInt().coerceIn(0, source.bitmap.width - 1),
                    (region.top * source.bitmap.height).toInt().coerceIn(0, source.bitmap.height - 1),
                    (region.right * source.bitmap.width).toInt().coerceIn(1, source.bitmap.width),
                    (region.bottom * source.bitmap.height).toInt().coerceIn(1, source.bitmap.height)
                )
                require(rect.right > rect.left && rect.bottom > rect.top)
                val bitmap = android.graphics.Bitmap.createBitmap(
                    source.bitmap,
                    rect.left,
                    rect.top,
                    rect.width(),
                    rect.height()
                )
                val crop = ScreenToolsSession.add(bitmap)
                cropFrame = crop
                val request = ScreenCropMethod.request(
                    action = ScreenCropMethod.ID,
                    context = context.request.invocationContext.asMap(ScreenCropMethod.ID) + context.action.settings,
                    signals = emptyList(),
                    inputs = emptyList()
                )
                val values = mapOf(
                    ScreenCropFields.STATUS to "succeeded",
                    ScreenCropFields.ERROR to "",
                    ScreenCropFields.SOURCE_FRAME_REF to source.ref,
                    ScreenCropFields.CROP_FRAME_REF to crop.ref,
                    ScreenCropFields.ROI_JSON to JSONArray().put(region.left).put(region.top).put(region.right).put(region.bottom).toString(),
                    ScreenCropFields.WIDTH_PX to crop.bitmap.width.toString(),
                    ScreenCropFields.HEIGHT_PX to crop.bitmap.height.toString(),
                    ScreenCropFields.RETENTION to "volatile_process_memory"
                )
                val execution = ScreenCropMethod.result(request, values, context.request.invocationContext)
                result = execution
                status = "Crop ready for Commit. It is not a durable image attachment."
                if (context.submitsImmediately) onConfirmed(execution)
            }.onFailure { status = "Crop failed: ${it.message.orEmpty()}" }
        }

        CapabilityScreenScaffold(
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = result,
            resultPreview = result?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty(),
            onBack = onBack,
            onRetry = { result = null; cropFrame = null },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Text("Crops remain in volatile process memory and are never written to Photos, MediaStore, or a file URI.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Text(if (frame == null) "No source frame available" else "Source frame ${frame.bitmap.width} × ${frame.bitmap.height}")
            OutlinedTextField(roiText, { roiText = it }, label = { Text("ROI [left, top, right, bottom]") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = ::crop, enabled = frame != null) { Text("Create crop") }
            cropFrame?.let { cropped ->
                Spacer(Modifier.height(8.dp))
                Text("Crop ${cropped.bitmap.width} × ${cropped.bitmap.height}")
                Image(bitmap = cropped.bitmap.asImageBitmap(), contentDescription = "Volatile cropped screen region", modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(8.dp))
            Text(status, style = MaterialTheme.typography.bodySmall)
        }
    }
}
