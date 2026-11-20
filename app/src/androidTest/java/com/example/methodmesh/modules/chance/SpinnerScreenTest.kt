package com.example.methodmesh.modules.chance

import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.ReturnMode
import com.example.methodmesh.transport.android.AndroidIntentRequestReader
import com.example.methodmesh.transport.android.ExternalWorkflowActivity
import com.example.methodmesh.transport.workflow.ui.CapabilityCompletionMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.RetainedExecutionStore
import com.example.methodmesh.ui.theme.MethodMeshTheme
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/** Actual spinner UI; no caller app, sharing, storage exports or physical hardware. */
class SpinnerScreenTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext
    private fun root(): AccessibilityNodeInfo? {
        if (Build.VERSION.SDK_INT >= 34) instrumentation.uiAutomation.clearCache()
        return instrumentation.uiAutomation.rootInActiveWindow
    }
    private fun find(predicate: (AccessibilityNodeInfo) -> Boolean, node: AccessibilityNodeInfo? = root()): AccessibilityNodeInfo? {
        if (node == null) return null
        if (predicate(node)) return node
        for (i in 0 until node.childCount) find(predicate, node.getChild(i))?.let { return it }
        return null
    }
    private fun text(value: String) = find({ it.text?.toString() == value })
    private fun await(condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 12_000
        while (!condition()) {
            check(SystemClock.uptimeMillis() < end) { "Spinner fixture timed out" }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(30)
        }
    }
    private fun click(label: String) {
        await {
            if (text(label) != null) true else {
                find({ it.isScrollable })?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                SystemClock.sleep(120)
                false
            }
        }
        var node = requireNotNull(text(label))
        while (!node.isClickable) node = requireNotNull(node.parent)
        node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
        instrumentation.waitForIdleSync()
        SystemClock.sleep(100)
        node = requireNotNull(text(label))
        while (!node.isClickable) node = requireNotNull(node.parent)
        assertTrue("Disabled spinner control: $label", node.isEnabled)
        assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
    }
    private fun retained(scenario: ActivityScenario<ExternalWorkflowActivity>): ExecutionResult? {
        val result = AtomicReference<ExecutionResult>()
        scenario.onActivity { result.set(ViewModelProvider(it)[RetainedExecutionStore::class.java].snapshot().values.filterNotNull().singleOrNull()) }
        return result.get()
    }
    private fun full(result: ExecutionResult) = OutputFormatter.format(result, ReturnMode.Json, includeProvenance = true,
        payloadMode = OutputFormatter.PayloadMode.FULL)
    private fun screenshot(name: String) {
        val target = File(app.getExternalFilesDir(null), "spinner-$name.png")
        FileOutputStream(target).use { assertTrue(instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
    private fun fixture(remove: Boolean = true, animation: String = "off", automatic: Boolean = false,
        nativePreset: Boolean = false, hostCloseout: Boolean = false,
        run: (ActivityScenario<ExternalWorkflowActivity>, List<ExecutionResult>) -> Unit) {
        val token = UUID.randomUUID().toString()
        val returned = CopyOnWriteArrayList<ExecutionResult>()
        val incoming = Intent().putExtra("method_id", As100SpinnerMethod.ID)
            .putExtra("input_items", "Joe\nJeff\nJayne").putExtra("input_remove_after_spin", remove.toString())
            .putExtra("input_animation_mode", animation).putExtra("input_rng_mode", "fixed_seed").putExtra("input_seed", "spinner-regression")
            .putExtra("input_methodmesh_native_preset_run", nativePreset.toString())
        val request = AndroidIntentRequestReader.workflowRequest(incoming)
        val context = CapabilityScreenContext(request.actions.single(), request, 1, 1,
            if (automatic) CapabilityCompletionMode.AutomaticReturn else CapabilityCompletionMode.ManualConfirmation,
            hostOwnsNativePresetCloseout = hostCloseout)
        val callback = ActivityLifecycleCallback { activity, stage ->
            if (activity is ExternalWorkflowActivity && stage == Stage.CREATED && activity.intent.getStringExtra("spinner_fixture") == token) {
                activity.setContent { MethodMeshTheme { Surface(Modifier.fillMaxSize()) {
                    SpinnerCapabilityScreen.Render(context, {}, { returned += it }, {})
                } } }
            }
        }
        instrumentation.runOnMainSync { ActivityLifecycleMonitorRegistry.getInstance().addLifecycleCallback(callback) }
        try {
            ActivityScenario.launch<ExternalWorkflowActivity>(Intent(app, ExternalWorkflowActivity::class.java).putExtra("spinner_fixture", token)).use {
                run(it, returned)
            }
        } finally {
            instrumentation.runOnMainSync { ActivityLifecycleMonitorRegistry.getInstance().removeLifecycleCallback(callback) }
        }
    }

    @Test fun removalDepletesToZeroSurvivesRecreationAndRestoresAll() = fixture { scenario, returned ->
        click("Open spinner")
        click("SPIN")
        await { text("Removed from next spin · 2 remaining") != null }
        val original = requireNotNull(retained(scenario))
        val originalJson = full(original)
        screenshot("portrait")
        scenario.recreate()
        await { text("Removed from next spin · 2 remaining") != null }
        assertSame(original, retained(scenario))
        assertEquals(originalJson, full(requireNotNull(retained(scenario))))
        assertTrue(returned.isEmpty())
        click("Spin remaining (2)")
        await { text("Removed from next spin · 1 remaining") != null }
        click("Spin remaining (1)")
        await { text("All labels drawn") != null }
        assertNotSame(original, retained(scenario))
        assertTrue(returned.isEmpty())
        click("Restore all")
        await { text("3 labels · remove winners on") != null }
        assertNull(retained(scenario))
        assertNull(text("WINNER"))
    }

    @Test fun standardModeKeepsEveryNameAndCommitReturnsExactExecution() = fixture(remove = false) { scenario, returned ->
        click("Open spinner"); click("SPIN")
        await { text("WINNER") != null }
        assertNotNull(text("3 labels · winners stay in"))
        val original = requireNotNull(retained(scenario))
        click("Commit")
        await { text("Committed") != null }
        assertTrue(returned.isEmpty())
        scenario.recreate()
        await { text("Committed") != null }
        assertSame(original, retained(scenario))
        click("Done")
        await { returned.size == 1 }
        assertSame(original, returned.single())
    }

    @Test fun animationRemovesOnceAndLandscapeUsesWholeViewport() = fixture(animation = "fast") { scenario, _ ->
        click("Open spinner"); click("SPIN")
        await { text("Removed from next spin · 2 remaining") != null }
        val execution = requireNotNull(retained(scenario))
        scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        await {
            val bounds = Rect(); root()?.getBoundsInScreen(bounds)
            val wheel = find({ it.contentDescription?.toString()?.startsWith("Spinner wheel:") == true })
            val wheelBounds = Rect(); wheel?.getBoundsInScreen(wheelBounds)
            val winnerBounds = Rect(); text("WINNER")?.getBoundsInScreen(winnerBounds)
            bounds.width() > bounds.height() && wheelBounds.right > 0 && winnerBounds.left >= wheelBounds.right &&
                text("Removed from next spin · 2 remaining") != null
        }
        assertSame(execution, retained(scenario))
        val wheel = requireNotNull(find({ it.contentDescription?.toString()?.startsWith("Spinner wheel:") == true }))
        val wheelBounds = Rect(); wheel.getBoundsInScreen(wheelBounds)
        val viewport = Rect(); root()?.getBoundsInScreen(viewport)
        assertTrue("Wheel should occupy the main landscape bay", wheelBounds.width() > viewport.width() / 2)
        // Accessibility switches bounds before Android's screen-rotation animation finishes.
        // Capture the settled landscape layout, not an intermediate rotated portrait frame.
        SystemClock.sleep(1000)
        instrumentation.waitForIdleSync()
        screenshot("landscape")
    }

    @Test fun fixedNativePresetAutostartsAndCommitDelegatesHostCloseout() = fixture(nativePreset = true, hostCloseout = true) { scenario, returned ->
        await { text("Removed from next spin · 2 remaining") != null }
        assertNull(text("Open spinner"))
        assertNull(text("Spinner labels"))
        val original = requireNotNull(retained(scenario))
        click("Commit")
        await { returned.size == 1 }
        assertSame(original, returned.single())
        assertNull(text("Committed"))
    }

    @Test fun externalAutomaticRunReturnsOneCanonicalDrawWithoutNativeSteps() = fixture(automatic = true) { _, returned ->
        await { returned.size == 1 }
        assertEquals(1, returned.size)
        val json = full(returned.single())
        assertTrue(json.contains("spinner_remaining_items_json"))
        assertTrue(JSONObject(json).toString().contains("spinner_remove_after_spin"))
        assertNull(text("WINNER"))
    }
}
