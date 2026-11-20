package com.example.methodmesh.modules.screentools

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import com.example.methodmesh.transport.workflow.ui.rememberRetainedExecutionResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private fun CapabilityScreenContext.ocrSetting(key: String, default: String = ""): String =
    action.settings[key] ?: action.settings["input_$key"] ?: request.settings[key] ?: request.settings["input_$key"] ?: default

private fun normalizedRegion(left: Int, top: Int, right: Int, bottom: Int, width: Int, height: Int): JSONArray =
    JSONArray()
        .put((left.toFloat() / width).coerceIn(0f, 1f).toDouble())
        .put((top.toFloat() / height).coerceIn(0f, 1f).toDouble())
        .put((right.toFloat() / width).coerceIn(0f, 1f).toDouble())
        .put((bottom.toFloat() / height).coerceIn(0f, 1f).toDouble())

object ScreenOcrCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = ScreenOcrMethod.ID
    override val title = "Screen OCR"
    override val description = "Read text and normalized word regions from a captured volatile frame."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val appContext = LocalContext.current
        val scope = rememberCoroutineScope()
        val frameRef = context.ocrSetting("frame_ref")
        val frame = remember(frameRef, ScreenToolsSession.activeFrameId, ScreenToolsSession.frames.size) {
            if (frameRef.isBlank()) ScreenToolsSession.active()
            else ScreenToolsSession.frames.firstOrNull { it.ref == frameRef }
        }
        var status by rememberSaveable { mutableStateOf("Ready to read the latest captured frame.") }
        var text by rememberSaveable { mutableStateOf("") }
        var elementsJson by rememberSaveable { mutableStateOf("[]") }
        var wordCount by rememberSaveable { mutableStateOf(0) }
        var attemptedFrameRef by rememberSaveable { mutableStateOf("") }
        var result by rememberRetainedExecutionResult("${context.action.canonicalId}:ocr")

        fun runOcr() {
            val selected = frame
            if (selected == null) {
                status = if (frameRef.isBlank()) "No captured frame is available. Capture a screen first."
                else "The requested frame is no longer available in this process."
                result = null
                return
            }
            attemptedFrameRef = selected.ref
            result = null
            status = "Reading text on device…"
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(InputImage.fromBitmap(selected.bitmap, 0))
                .addOnSuccessListener { visionText ->
                    val elements = JSONArray()
                    var words = 0
                    visionText.textBlocks.forEach { block ->
                        block.lines.forEach { line ->
                            line.elements.forEach { element ->
                                val box = element.boundingBox ?: return@forEach
                                words += 1
                                elements.put(JSONObject().apply {
                                    put("text", element.text)
                                    put("roi", normalizedRegion(box.left, box.top, box.right, box.bottom, selected.bitmap.width, selected.bitmap.height))
                                })
                            }
                        }
                    }
                    text = visionText.text
                    elementsJson = elements.toString()
                    wordCount = words
                    status = "OCR complete. Review the text, then Commit the result."
                    val request = ScreenOcrMethod.request(
                        action = ScreenOcrMethod.ID,
                        context = context.request.invocationContext.asMap(ScreenOcrMethod.ID) + context.action.settings,
                        signals = emptyList(),
                        inputs = emptyList()
                    )
                    val values = mapOf(
                        ScreenOcrFields.STATUS to "succeeded",
                        ScreenOcrFields.ERROR to "",
                        ScreenOcrFields.FRAME_REF to selected.ref,
                        ScreenOcrFields.CAPTURE_TIME_ISO to selected.capturedAt,
                        ScreenOcrFields.TEXT to visionText.text,
                        ScreenOcrFields.ELEMENTS_JSON to elements.toString(),
                        ScreenOcrFields.WORD_COUNT to words.toString(),
                        ScreenOcrFields.RETENTION to "volatile_process_memory"
                    )
                    val execution = ScreenOcrMethod.result(request, values, context.request.invocationContext)
                    result = execution
                    if (context.submitsImmediately) onConfirmed(execution)
                    recognizer.close()
                }
                .addOnFailureListener { error ->
                    status = "OCR failed: ${error.message ?: error.javaClass.simpleName}"
                    val request = ScreenOcrMethod.request(
                        action = ScreenOcrMethod.ID,
                        context = context.request.invocationContext.asMap(ScreenOcrMethod.ID) + context.action.settings,
                        signals = emptyList(),
                        inputs = emptyList()
                    )
                    result = ScreenOcrMethod.result(request, mapOf(
                        ScreenOcrFields.STATUS to "failed",
                        ScreenOcrFields.ERROR to (error.message ?: "OCR failed"),
                        ScreenOcrFields.FRAME_REF to selected.ref,
                        ScreenOcrFields.RETENTION to "volatile_process_memory"
                    ), context.request.invocationContext)
                    recognizer.close()
                }
        }

        LaunchedEffect(context.startsImmediately, frame?.ref) {
            if (context.startsImmediately && frame != null && attemptedFrameRef != frame.ref && result == null) {
                runOcr()
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
            onRetry = { result = null; attemptedFrameRef = ""; runOcr() },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Text("OCR runs locally against one volatile frame. Word regions use normalized image coordinates.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            if (frameRef.isNotBlank()) {
                OutlinedTextField(
                    value = frameRef,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Frame reference") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Text(if (frame == null) "No frame available" else "Frame ${frame.bitmap.width} × ${frame.bitmap.height}")
            Spacer(Modifier.height(8.dp))
            Button(onClick = ::runOcr, enabled = frame != null) { Text("Read text") }
            Spacer(Modifier.height(8.dp))
            Text(status, style = MaterialTheme.typography.bodySmall)
            if (text.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text("$wordCount word region(s)", style = MaterialTheme.typography.titleMedium)
                Text(text, style = MaterialTheme.typography.bodyLarge)
                Text("The complete normalized regions remain available in the committed result.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
