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
import com.google.mlkit.vision.text.Text.TextBlock
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch
import org.json.JSONObject

private fun CapabilityScreenContext.extractSetting(key: String, default: String = ""): String =
    action.settings[key] ?: action.settings["input_$key"] ?: request.settings[key] ?: request.settings["input_$key"] ?: default

private data class ExtractedValue(val name: String, val value: String, val status: String = "ocr")

private fun valueForRegion(blocks: List<TextBlock>, region: ScreenRegion, width: Int, height: Int): String {
    val matches = blocks.flatMap { block ->
        block.lines.flatMap { line ->
            line.elements.mapNotNull { element ->
                val box = element.boundingBox ?: return@mapNotNull null
                val centerX = (box.left + box.right) / 2f / width
                val centerY = (box.top + box.bottom) / 2f / height
                if (centerX in region.left..region.right && centerY in region.top..region.bottom) element.text else null
            }
        }
    }
    return matches.joinToString(" ").trim()
}

object ScreenExtractCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = ScreenExtractMethod.ID
    override val title = "Visual extraction"
    override val description = "Resolve and review named values from a captured frame."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val appContext = LocalContext.current
        val scope = rememberCoroutineScope()
        val frameRef = context.extractSetting("frame_ref")
        val frame = remember(frameRef, ScreenToolsSession.activeFrameId, ScreenToolsSession.frames.size) {
            if (frameRef.isBlank()) ScreenToolsSession.active()
            else ScreenToolsSession.frames.firstOrNull { it.ref == frameRef }
        }
        var memoryId by rememberSaveable { mutableStateOf(context.extractSetting("memory_id")) }
        var memoryVersion by rememberSaveable { mutableStateOf(context.extractSetting("memory_version")) }
        var resolvedVersion by rememberSaveable { mutableStateOf("") }
        var fields by remember { mutableStateOf<List<ExtractedValue>>(emptyList()) }
        var status by rememberSaveable { mutableStateOf("Run extraction against the selected frame.") }
        var result by rememberRetainedExecutionResult("${context.action.canonicalId}:extract")

        fun loadSelections(): List<ScreenSelection>? {
            if (memoryId.isBlank()) {
                resolvedVersion = ""
                return ScreenToolsSession.selections.toList().takeIf { it.isNotEmpty() }
            }
            val pinned = memoryVersion.toIntOrNull()
            if (memoryVersion.isNotBlank() && pinned == null) {
                status = "Selection memory version must be a number."
                return null
            }
            val definition = ScreenMemoryStore.get(appContext, memoryId, pinned)
            if (definition == null) {
                status = "Selection memory or revision not found."
                return null
            }
            resolvedVersion = definition.optInt("version").toString()
            return runCatching { ScreenMemoryStore.selections(definition) }
                .onFailure { status = "Invalid selection memory: ${it.message.orEmpty()}" }
                .getOrNull()
        }

        fun runExtraction() {
            val selectedFrame = frame ?: run {
                status = "No valid frame is available. Capture a screen first."
                return
            }
            val selections = loadSelections()
            if (selections.isNullOrEmpty()) {
                status = "Create or load at least one visual selection first."
                return
            }
            result = null
            status = "Reading selected regions on device…"
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(InputImage.fromBitmap(selectedFrame.bitmap, 0))
                .addOnSuccessListener { visionText ->
                    fields = selections.map { selection ->
                        val value = valueForRegion(visionText.textBlocks, selection.region, selectedFrame.bitmap.width, selectedFrame.bitmap.height)
                        ExtractedValue(selection.name, value, if (value.isBlank()) "empty" else "ocr")
                    }
                    status = "Review or correct every value before Commit."
                    recognizer.close()
                }
                .addOnFailureListener { error ->
                    status = "Extraction failed: ${error.message ?: error.javaClass.simpleName}"
                    recognizer.close()
                }
        }

        fun commit() {
            val selectedFrame = frame ?: return
            if (fields.isEmpty() || fields.any { it.value.isBlank() }) {
                status = "Every extracted field needs a non-empty reviewed value."
                return
            }
            val dynamic = linkedMapOf<String, String>()
            fields.forEach { field ->
                val safe = field.name.trim().replace(Regex("[^A-Za-z0-9_]+"), "_")
                if (!safe.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) {
                    status = "Field '${field.name}' has no valid MethodMesh output name."
                    return
                }
                if (safe in ScreenExtractFields.outputs || safe in dynamic) {
                    status = "Field names must be unique and must not shadow reserved outputs."
                    return
                }
                dynamic[safe] = field.value
            }
            val valuesJson = JSONObject().apply { fields.forEach { put(it.name, it.value) } }.toString()
            val request = ScreenExtractMethod.request(
                action = ScreenExtractMethod.ID,
                context = context.request.invocationContext.asMap(ScreenExtractMethod.ID) + context.action.settings,
                signals = emptyList(),
                inputs = emptyList()
            )
            val values = mapOf(
                ScreenExtractFields.STATUS to "succeeded",
                ScreenExtractFields.ERROR to "",
                ScreenExtractFields.FRAME_REF to selectedFrame.ref,
                ScreenExtractFields.CAPTURE_TIME_ISO to selectedFrame.capturedAt,
                ScreenExtractFields.MEMORY_ID to memoryId,
                ScreenExtractFields.MEMORY_VERSION to resolvedVersion,
                ScreenExtractFields.VALUES_JSON to valuesJson,
                ScreenExtractFields.DYNAMIC_FIELDS_JSON to JSONObject(dynamic).toString(),
                ScreenExtractFields.FIELD_COUNT to fields.size.toString(),
                ScreenExtractFields.RETENTION to "volatile_process_memory"
            )
            val execution = ScreenExtractMethod.result(request, values, dynamic, context.request.invocationContext)
            result = execution
            status = "Reviewed values ready for Commit."
            if (context.submitsImmediately) onConfirmed(execution)
        }

        LaunchedEffect(context.startsImmediately, frame?.ref, memoryId, memoryVersion) {
            if (context.startsImmediately && frame != null && fields.isEmpty() && result == null) runExtraction()
        }

        CapabilityScreenScaffold(
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = result,
            resultPreview = result?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty(),
            onBack = onBack,
            onRetry = { result = null; fields = emptyList(); runExtraction() },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Text("Extraction is tied to one frame and one selection revision. Review/correct values before Commit.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Text(if (frame == null) "No frame available" else "Frame ${frame.bitmap.width} × ${frame.bitmap.height}")
            OutlinedTextField(memoryId, { memoryId = it }, label = { Text("Selection memory ID (blank uses current selections)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(memoryVersion, { memoryVersion = it.filter(Char::isDigit) }, label = { Text("Pinned memory version (optional)") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = ::runExtraction, enabled = frame != null) { Text("Extract named values") }
            fields.forEachIndexed { index, field ->
                OutlinedTextField(
                    value = field.value,
                    onValueChange = { changed -> fields = fields.toMutableList().also { it[index] = field.copy(value = changed, status = "corrected") } },
                    label = { Text("${field.name} · ${field.status}") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (fields.isNotEmpty()) Button(onClick = ::commit) { Text("Commit reviewed values") }
            Spacer(Modifier.height(8.dp))
            Text(status, style = MaterialTheme.typography.bodySmall)
        }
    }
}
