package com.example.methodmesh.modules.imageredaction

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.example.methodmesh.transport.android.ExternalWorkflowActivity
import com.example.methodmesh.ui.theme.MethodMeshTheme
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/** Real viewport/gestures/export, synthetic documents only; no camera, scanner or receiver. */
class RedactionViewportInteractionTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext
    private data class Snapshot(val view: RedactionView, val navigate: Boolean, val regions: List<PdfRedactionRegion>, val cells: Set<String>)
    private class Harness {
        val snapshot = AtomicReference<Snapshot>()
        var editable by mutableStateOf(true)
    }
    private fun find(predicate: (AccessibilityNodeInfo) -> Boolean, root: AccessibilityNodeInfo? = instrumentation.uiAutomation.rootInActiveWindow): AccessibilityNodeInfo? {
        if (root == null) return null
        if (predicate(root)) return root
        for (index in 0 until root.childCount) find(predicate, root.getChild(index))?.let { return it }
        return null
    }
    private fun await(condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 12_000
        while (!condition()) {
            check(SystemClock.uptimeMillis() < end) { "Zoom/pan interaction timed out" }
            instrumentation.waitForIdleSync(); SystemClock.sleep(25)
        }
    }
    private fun click(label: String) {
        var target: AccessibilityNodeInfo? = null
        await {
            var candidate = find({ it.text?.toString() == label || it.contentDescription?.toString() == label })
            while (candidate != null && !candidate.isClickable) candidate = candidate.parent
            target = candidate?.takeIf { it.isEnabled }; target != null
        }
        assertTrue(requireNotNull(target).performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
    }
    private fun bounds(): Rect {
        await { find({ it.contentDescription?.toString() == "Zoom test canvas" }) != null }
        return Rect().also { find({ it.contentDescription?.toString() == "Zoom test canvas" })!!.getBoundsInScreen(it) }
    }
    private fun drag(bounds: Rect, sx: Float, sy: Float, ex: Float, ey: Float) {
        val down = SystemClock.uptimeMillis()
        fun event(action: Int, fraction: Float) {
            val e = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                bounds.left + bounds.width() * (sx + (ex - sx) * fraction), bounds.top + bounds.height() * (sy + (ey - sy) * fraction), 0)
            e.source = InputDevice.SOURCE_TOUCHSCREEN
            try { assertTrue(instrumentation.uiAutomation.injectInputEvent(e, true)) } finally { e.recycle() }
        }
        event(MotionEvent.ACTION_DOWN, 0f)
        repeat(12) { SystemClock.sleep(16); event(MotionEvent.ACTION_MOVE, (it + 1) / 12f) }
        event(MotionEvent.ACTION_UP, 1f)
        instrumentation.waitForIdleSync()
    }
    private fun pinch(bounds: Rect) {
        val down = SystemClock.uptimeMillis()
        val properties = Array(2) { index -> MotionEvent.PointerProperties().apply { id = index; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        fun event(action: Int, count: Int, distance: Float) {
            val coordinates = Array(2) { index -> MotionEvent.PointerCoords().apply {
                x = bounds.exactCenterX() + (if (index == 0) -1 else 1) * bounds.width() * distance
                y = bounds.exactCenterY(); pressure = 1f; size = 1f
            } }
            val e = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, count, properties, coordinates,
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
            try { assertTrue(instrumentation.uiAutomation.injectInputEvent(e, true)) } finally { e.recycle() }
        }
        event(MotionEvent.ACTION_DOWN, 1, .12f)
        event(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2, .12f)
        repeat(12) { SystemClock.sleep(16); event(MotionEvent.ACTION_MOVE, 2, .12f + .18f * (it + 1) / 12) }
        event(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2, .3f)
        event(MotionEvent.ACTION_UP, 1, .3f)
        instrumentation.waitForIdleSync()
    }
    private fun fixture(grid: Boolean = false, run: (Harness, ActivityScenario<ExternalWorkflowActivity>, File) -> Unit) {
        val folder = File(app.cacheDir, "redaction-viewport-${UUID.randomUUID()}").apply { mkdirs() }
        val source = File(folder, "source.pdf"); PdfRedactionEngineTest.source(source)
        val bitmap = PdfRedactionEngine.preview(source, 0)
        val harness = Harness(); val token = UUID.randomUUID().toString()
        val callback = ActivityLifecycleCallback { activity, stage ->
            if (activity is ExternalWorkflowActivity && stage == Stage.CREATED && activity.intent.getStringExtra("review_harness") == token) {
                activity.setContent { MethodMeshTheme {
                    val state = rememberRedactionViewportState("zoom-fixture")
                    var regionsJson by rememberSaveable { mutableStateOf("[]") }
                    var cellsText by rememberSaveable { mutableStateOf("") }
                    val regions = PdfRedactionRegions.decode(regionsJson)
                    val cells = cellsText.split('|').filter(String::isNotBlank).toSet()
                    val view = state.view; val navigate = state.navigate
                    SideEffect { harness.snapshot.set(Snapshot(view, navigate, regions, cells)) }
                    val marks = if (grid) RedactionMarks.Grid(2, 2, cells) { cellsText = it.sorted().joinToString("|") }
                        else RedactionMarks.Boxes(0, regions) { regionsJson = PdfRedactionRegions.encode(regions + it) }
                    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                        RedactionViewport(bitmap, state, "Zoom test canvas", marks, Modifier.fillMaxSize(), canRedact = harness.editable)
                    }
                } }
            }
        }
        instrumentation.runOnMainSync { ActivityLifecycleMonitorRegistry.getInstance().addLifecycleCallback(callback) }
        try {
            ActivityScenario.launch<ExternalWorkflowActivity>(Intent(app, ExternalWorkflowActivity::class.java).putExtra("review_harness", token)).use { scenario ->
                await { harness.snapshot.get() != null }; run(harness, scenario, source)
            }
        } finally {
            instrumentation.runOnMainSync { ActivityLifecycleMonitorRegistry.getInstance().removeLifecycleCallback(callback) }
            bitmap.recycle(); folder.deleteRecursively()
        }
    }
    @Test fun zoomPanRotationAndFitKeepPdfMasksAnchoredToExportedPixels() = fixture { harness, scenario, source ->
        click("Zoom in"); click("Pan / zoom")
        await { harness.snapshot.get().navigate }
        drag(bounds(), .4f, .4f, .6f, .6f)
        await { harness.snapshot.get().view.centerX < .5f }
        val view = harness.snapshot.get().view
        assertEquals(2f, view.zoom)
        assertTrue(harness.snapshot.get().regions.isEmpty())
        scenario.recreate()
        await { harness.snapshot.get().view == view && find({ it.text?.toString() == "Redact" }) != null }
        assertTrue(harness.snapshot.get().navigate)
        click("Redact")
        await { !harness.snapshot.get().navigate }
        val box = bounds()
        val layout = redactionViewportLayout(box.width().toFloat(), box.height().toFloat(), 400, 400, view)!!
        val expected = layout.region(0, box.width() * .25f, box.height() * .25f, box.width() * .55f, box.height() * .55f)!!
        drag(box, .25f, .25f, .55f, .55f)
        await { harness.snapshot.get().regions.size == 1 }
        val actual = harness.snapshot.get().regions.single()
        assertEquals(expected.left, actual.left, .005f); assertEquals(expected.top, actual.top, .005f)
        assertEquals(expected.right, actual.right, .005f); assertEquals(expected.bottom, actual.bottom, .005f)
        instrumentation.uiAutomation.takeScreenshot()?.let { screenshot ->
            try { File(app.cacheDir, "redaction-zoom-review.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            finally { screenshot.recycle() }
        }
        click("Fit")
        await { harness.snapshot.get().view == RedactionView() }
        assertEquals(listOf(actual), harness.snapshot.get().regions)
        val before = source.readBytes()
        val output = File(source.parentFile, "redacted.pdf")
        PdfRedactionEngine.redact(source, listOf(actual), output)
        assertArrayEquals(before, source.readBytes())
        val preview = PdfRedactionEngine.preview(output, 0)
        try {
            assertEquals(Color.BLACK, preview.getPixel(((actual.left + actual.right) / 2 * preview.width).toInt(),
                ((actual.top + actual.bottom) / 2 * preview.height).toInt()))
            assertEquals(Color.WHITE, preview.getPixel((.95f * preview.width).toInt(), (.05f * preview.height).toInt()))
        } finally { preview.recycle() }
        scenario.onActivity { harness.editable = false }
        click("Zoom in"); drag(bounds(), .4f, .4f, .6f, .6f)
        assertEquals(listOf(actual), harness.snapshot.get().regions)
    }
    @Test fun navigationPinchesNeverMarkAndMultitouchInRedactModeCancelsTheWholeBox() = fixture { harness, _, _ ->
        click("Pan / zoom"); await { harness.snapshot.get().navigate }; pinch(bounds())
        await { harness.snapshot.get().view.zoom > 1.5f }
        assertTrue(harness.snapshot.get().regions.isEmpty())
        val view = harness.snapshot.get().view
        click("Redact"); await { !harness.snapshot.get().navigate }; pinch(bounds())
        assertEquals(view, harness.snapshot.get().view)
        assertTrue(harness.snapshot.get().regions.isEmpty())
    }
    @Test fun photoGridSelectionFollowsTheZoomedPannedImageNotTheViewportGrid() = fixture(grid = true) { harness, _, _ ->
        click("Zoom in"); click("Pan / zoom")
        await { harness.snapshot.get().navigate }
        drag(bounds(), .4f, .4f, .6f, .6f)
        await { harness.snapshot.get().view.centerX < .5f && harness.snapshot.get().view.centerY < .5f }
        assertTrue(harness.snapshot.get().cells.isEmpty())
        click("Redact")
        await { !harness.snapshot.get().navigate }
        // A tap, not a drag: the source point under the viewport centre is now in r1c1.
        val box = bounds(); val down = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            if (action == MotionEvent.ACTION_UP) SystemClock.sleep(80)
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, box.exactCenterX(), box.exactCenterY(), 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
        await { harness.snapshot.get().cells.isNotEmpty() }
        assertEquals(setOf("r1c1"), harness.snapshot.get().cells)
        click("Fit")
        assertEquals(setOf("r1c1"), harness.snapshot.get().cells)
    }
}
