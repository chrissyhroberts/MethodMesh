package com.example.methodmesh.modules.screentools

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import com.example.methodmesh.transport.workflow.ui.rememberRetainedExecutionResult
import java.time.Instant

private fun CapabilityScreenContext.visualSetting(key: String, default: String = ""): String =
    action.settings[key] ?: action.settings["input_$key"] ?: request.settings[key] ?: request.settings["input_$key"] ?: default

object ScreenVisualSelectCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = ScreenVisualSelectMethod.ID
    override val title = "Visual selection"
    override val description = "Draw and name normalized regions on a captured volatile frame."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val frameRef = context.visualSetting("frame_ref")
        val frame = remember(frameRef, ScreenToolsSession.activeFrameId, ScreenToolsSession.frames.size) {
            if (frameRef.isBlank()) ScreenToolsSession.active()
            else ScreenToolsSession.frames.firstOrNull { it.ref == frameRef }
        }
        var status by rememberSaveable { mutableStateOf("Drag a region on the frame, then give it a name.") }
        var name by rememberSaveable { mutableStateOf("") }
        var draft by remember { mutableStateOf<ScreenRegion?>(null) }
        var dragStart by remember { mutableStateOf<Offset?>(null) }
        var canvasSize by remember { mutableStateOf(IntSize.Zero) }
        var result by rememberRetainedExecutionResult("${context.action.canonicalId}:visual-select")

        fun commit() {
            val selected = frame
            if (selected == null) {
                status = "The requested frame is no longer available in this process."
                return
            }
            if (ScreenToolsSession.selections.isEmpty()) {
                status = "Add at least one named region before committing."
                return
            }
            val request = ScreenVisualSelectMethod.request(
                action = ScreenVisualSelectMethod.ID,
                context = context.request.invocationContext.asMap(ScreenVisualSelectMethod.ID) + context.action.settings,
                signals = emptyList(),
                inputs = emptyList()
            )
            val values = mapOf(
                ScreenVisualSelectFields.STATUS to "succeeded",
                ScreenVisualSelectFields.ERROR to "",
                ScreenVisualSelectFields.FRAME_REF to selected.ref,
                ScreenVisualSelectFields.CAPTURE_TIME_ISO to selected.capturedAt.ifBlank { Instant.now().toString() },
                ScreenVisualSelectFields.SELECTIONS_JSON to ScreenToolsSession.selections.toSelectionJson(),
                ScreenVisualSelectFields.SELECTION_COUNT to ScreenToolsSession.selections.size.toString(),
                ScreenVisualSelectFields.RETENTION to "volatile_process_memory"
            )
            val execution = ScreenVisualSelectMethod.result(request, values, context.request.invocationContext)
            result = execution
            status = "Selections ready for Commit. Definitions are not persisted yet."
            if (context.submitsImmediately) onConfirmed(execution)
        }

        CapabilityScreenScaffold(
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = result,
            resultPreview = result?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty(),
            onBack = onBack,
            onRetry = { result = null; status = "Continue editing selections." },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Text("Selections use normalized image coordinates and remain volatile until Selection Memory is added.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            if (frame == null) {
                Text(if (frameRef.isBlank()) "No captured frame is available. Capture a screen first." else "The requested frame is no longer available.")
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .sizeIn(minHeight = 220.dp, maxHeight = 520.dp)
                        .onSizeChanged { canvasSize = it }
                        .pointerInput(frame.ref) {
                            detectDragGestures(
                                onDragStart = { dragStart = it },
                                onDragCancel = { dragStart = null; draft = null },
                                onDragEnd = { dragStart = null },
                                onDrag = { change, _ ->
                                    val start = dragStart ?: return@detectDragGestures
                                    change.consume()
                                    val width = canvasSize.width.toFloat().coerceAtLeast(1f)
                                    val height = canvasSize.height.toFloat().coerceAtLeast(1f)
                                    draft = normalizedRegion(
                                        start.x / width,
                                        start.y / height,
                                        change.position.x / width,
                                        change.position.y / height
                                    )
                                }
                            )
                        }
                ) {
                    Image(
                        bitmap = frame.bitmap.asImageBitmap(),
                        contentDescription = "Captured screen for visual selection",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                    )
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        ScreenToolsSession.selections.forEach { selection ->
                            drawRect(
                                color = Color(0xCC00A6A6),
                                topLeft = Offset(selection.region.left * size.width, selection.region.top * size.height),
                                size = androidx.compose.ui.geometry.Size(
                                    (selection.region.right - selection.region.left) * size.width,
                                    (selection.region.bottom - selection.region.top) * size.height
                                ),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                            )
                        }
                        draft?.let { region ->
                            drawRect(
                                color = Color(0xFFFFC107),
                                topLeft = Offset(region.left * size.width, region.top * size.height),
                                size = androidx.compose.ui.geometry.Size(
                                    (region.right - region.left) * size.width,
                                    (region.bottom - region.top) * size.height
                                ),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name for drawn region") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Button(
                    onClick = {
                        val region = draft
                        val cleanName = name.trim()
                        when {
                            region == null -> status = "Drag a region on the frame first."
                            cleanName.isBlank() -> status = "Give the region a name first."
                            ScreenToolsSession.selections.any { it.name.equals(cleanName, ignoreCase = true) } -> status = "That selection name is already in use."
                            else -> {
                                ScreenToolsSession.selections.add(ScreenSelection(cleanName, region))
                                draft = null
                                name = ""
                                status = "Added '$cleanName'. Draw another region or Commit."
                            }
                        }
                    },
                    enabled = true
                ) { Text("Add named region") }
                if (ScreenToolsSession.selections.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("${ScreenToolsSession.selections.size} named region(s)", style = MaterialTheme.typography.titleMedium)
                    ScreenToolsSession.selections.forEach { selection ->
                        Text("${selection.name}: ${selection.region.left.formatCoordinate()}, ${selection.region.top.formatCoordinate()} → ${selection.region.right.formatCoordinate()}, ${selection.region.bottom.formatCoordinate()}")
                    }
                    OutlinedButton(onClick = { ScreenToolsSession.selections.clear(); result = null; status = "Selections cleared." }) {
                        Text("Clear selections")
                    }
                    Button(onClick = ::commit) { Text("Commit selections") }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(status, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun Float.formatCoordinate(): String = "%.3f".format(this)
