package com.example.methodmesh.modules.imageredaction

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.util.Locale

internal class RedactionViewportState(initial: RedactionView = RedactionView(), navigate: Boolean = false) {
    var view by mutableStateOf(initial)
    var navigate by mutableStateOf(navigate)
    companion object {
        val Saver = listSaver<RedactionViewportState, Any>(
            save = { listOf(it.view.zoom, it.view.centerX, it.view.centerY, it.navigate) },
            restore = { RedactionViewportState(RedactionView(it[0] as Float, it[1] as Float, it[2] as Float), it[3] as Boolean) })
    }
}

@Composable
internal fun rememberRedactionViewportState(vararg keys: Any?): RedactionViewportState =
    rememberSaveable(*keys, saver = RedactionViewportState.Saver) { RedactionViewportState() }

internal sealed interface RedactionMarks {
    data class Grid(val rows: Int, val columns: Int, val selected: Set<String>, val onChanged: (Set<String>) -> Unit) : RedactionMarks
    data class Boxes(val page: Int, val regions: List<PdfRedactionRegion>, val onAdded: (PdfRedactionRegion) -> Unit) : RedactionMarks
}

/** One clipped drawing/hit-testing coordinate system for both photos and PDF pages. */
@Composable
internal fun RedactionViewport(bitmap: Bitmap, state: RedactionViewportState, description: String, marks: RedactionMarks,
    modifier: Modifier = Modifier, enabled: Boolean = true, canRedact: Boolean = true) {
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var start by remember { mutableStateOf<Offset?>(null) }
    var end by remember { mutableStateOf<Offset?>(null) }
    var draftCells by remember { mutableStateOf<Set<String>?>(null) }
    val currentMarks by rememberUpdatedState(marks)
    val navigate = state.navigate || !canRedact
    val geometryKey = when (marks) {
        is RedactionMarks.Grid -> "grid:${marks.rows}:${marks.columns}"
        is RedactionMarks.Boxes -> "page:${marks.page}"
    }
    fun layout() = redactionViewportLayout(viewport.width.toFloat(), viewport.height.toFloat(), bitmap.width, bitmap.height, state.view)
    fun zoom(factor: Float) {
        layout()?.let { state.view = it.transform(it.viewWidth / 2, it.viewHeight / 2, 0f, 0f, factor) }
    }
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !navigate, onClick = { state.navigate = false }, enabled = enabled && canRedact, label = { Text("Redact") })
            FilterChip(selected = navigate, onClick = { state.navigate = true }, enabled = enabled, label = { Text("Pan / zoom") })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { zoom(.5f) }, enabled = enabled && state.view.zoom > 1f,
                modifier = Modifier.weight(1f).semantics { contentDescription = "Zoom out" }) { Text("−") }
            OutlinedButton(onClick = { state.view = RedactionView() }, enabled = enabled, modifier = Modifier.weight(1f)) { Text("Fit") }
            OutlinedButton(onClick = { zoom(2f) }, enabled = enabled && state.view.zoom < 16f,
                modifier = Modifier.weight(1f).semantics { contentDescription = "Zoom in" }) { Text("+") }
        }
        Text(String.format(Locale.ROOT, "%.1f×", state.view.zoom) + if (navigate) " · Drag to pan; pinch to zoom" else " · Mark regions; use Pan / zoom to move",
            style = MaterialTheme.typography.bodySmall)
        val image = remember(bitmap) { bitmap.asImageBitmap() }
        Canvas(Modifier.fillMaxWidth().weight(1f).clipToBounds().background(Color(0xFF202322))
            .onSizeChanged { viewport = it }.semantics { contentDescription = description }
            .pointerInput(bitmap, state, navigate, enabled, geometryKey) {
                if (!enabled) return@pointerInput
                if (navigate) {
                    detectTransformGestures { centroid, pan, factor, _ ->
                        val current = redactionViewportLayout(size.width.toFloat(), size.height.toFloat(), bitmap.width, bitmap.height, state.view)
                        current?.let { state.view = it.transform(centroid.x, centroid.y, pan.x, pan.y, factor) }
                    }
                } else awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val current = redactionViewportLayout(size.width.toFloat(), size.height.toFloat(), bitmap.width, bitmap.height, state.view)
                        ?: return@awaitEachGesture
                    val model = currentMarks
                    val first = down.position
                    start = first; end = first
                    var cells = (model as? RedactionMarks.Grid)?.let { grid ->
                        current.point(first.x, first.y)?.gridCell(grid.rows, grid.columns)?.let { cell ->
                            if (cell in grid.selected) grid.selected - cell else grid.selected + cell
                        }
                    }
                    draftCells = cells
                    var cancelled = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            // A second finger in Redact mode cancels the whole mark, never draws a pinch as a box.
                            if (event.changes.any { it.id != down.id && it.pressed }) cancelled = true
                            val pointer = event.changes.firstOrNull { it.id == down.id }
                            if (pointer == null) { cancelled = true; break }
                            if (pointer.isConsumed) cancelled = true
                            val position = pointer.position
                            end = position
                            if (model is RedactionMarks.Grid && cells != null && position != first) {
                                current.point(position.x, position.y)?.gridCell(model.rows, model.columns)?.let { cells = cells!! + it }
                                draftCells = cells
                            }
                            event.changes.forEach { it.consume() }
                            if (!pointer.pressed) break
                        }
                        if (!cancelled) when (val latest = currentMarks) {
                            is RedactionMarks.Grid -> cells?.let(latest.onChanged)
                            is RedactionMarks.Boxes -> end?.let { last -> current.region(latest.page, first.x, first.y, last.x, last.y)?.let(latest.onAdded) }
                        }
                    } finally { start = null; end = null; draftCells = null }
                }
            }) {
            val current = redactionViewportLayout(size.width, size.height, bitmap.width, bitmap.height, state.view) ?: return@Canvas
            withTransform({
                translate(current.originX, current.originY)
                scale(current.width / bitmap.width, current.height / bitmap.height, Offset.Zero)
            }) { drawImage(image) }
            when (marks) {
                is RedactionMarks.Grid -> {
                    val selected = draftCells ?: marks.selected
                    val cellWidth = current.width / marks.columns; val cellHeight = current.height / marks.rows
                    for (row in 0 until marks.rows) for (column in 0 until marks.columns) {
                        val topLeft = Offset(current.originX + column * cellWidth, current.originY + row * cellHeight)
                        val cellSize = Size(cellWidth, cellHeight)
                        if ("r${row + 1}c${column + 1}" in selected) drawRect(Color(0xDD000000), topLeft, cellSize)
                        drawRect(Color.White, topLeft, cellSize, style = Stroke(1.5f))
                    }
                }
                is RedactionMarks.Boxes -> {
                    val draft = start?.let { a -> end?.let { b -> current.region(marks.page, a.x, a.y, b.x, b.y) } }
                    (marks.regions.filter { it.page == marks.page } + listOfNotNull(draft)).forEach { r ->
                        drawRect(Color.Black, Offset(current.originX + r.left * current.width, current.originY + r.top * current.height),
                            Size((r.right - r.left) * current.width, (r.bottom - r.top) * current.height))
                    }
                }
            }
        }
    }
}
