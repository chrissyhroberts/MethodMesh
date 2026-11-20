package com.example.methodmesh.modules.signals

import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/** Actual screen-only QR transmit cycles; no camera, microphone, radio, torch or caller app. */
class SignalCommitLifecycleTest {
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
    private fun text(label: String) = find({ it.text?.toString() == label })
    private fun await(condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 15_000
        while (!condition()) {
            check(SystemClock.uptimeMillis() < end) { "Signals Commit fixture timed out" }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(30)
        }
    }
    private fun clickable(node: AccessibilityNodeInfo): AccessibilityNodeInfo {
        var value = node
        while (!value.isClickable) value = requireNotNull(value.parent)
        return value
    }
    private fun show(label: String): AccessibilityNodeInfo {
        repeat(3) { find({ it.isScrollable })?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) }
        instrumentation.waitForIdleSync()
        SystemClock.sleep(120)
        await {
            if (text(label) != null) true else {
                find({ it.isScrollable })?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                SystemClock.sleep(120)
                false
            }
        }
        return requireNotNull(text(label))
    }
    private fun click(label: String) {
        var node = clickable(show(label))
        node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
        instrumentation.waitForIdleSync()
        SystemClock.sleep(100)
        node = clickable(requireNotNull(text(label)))
        assertTrue("Disabled Signals control: $label", node.isEnabled)
        assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
    }
    private fun captured(scenario: ActivityScenario<ExternalWorkflowActivity>): ExecutionResult? {
        val value = AtomicReference<ExecutionResult>()
        scenario.onActivity { value.set(ViewModelProvider(it)[RetainedExecutionStore::class.java].snapshot().values.filterNotNull().singleOrNull()) }
        return value.get()
    }
    private fun full(result: ExecutionResult) = OutputFormatter.format(result, ReturnMode.Json,
        includeProvenance = true, payloadMode = OutputFormatter.PayloadMode.FULL)
    private fun cycle(burst: Boolean, recommit: Boolean = false) {
        val start = if (burst) "Start QR Burst" else "Start blast"
        click(start)
        val label = if (recommit) "Recommit" else "Commit"
        show(label)
        await { text(label)?.let { clickable(it).isEnabled } == true }
    }
    private fun fixture(burst: Boolean, automatic: Boolean = false,
        run: (ActivityScenario<ExternalWorkflowActivity>, List<ExecutionResult>) -> Unit) {
        val token = UUID.randomUUID().toString()
        val returned = CopyOnWriteArrayList<ExecutionResult>()
        val id = if (burst) As100SignalQrBurstTransmitMethod.id else As100SignalQrTransmitMethod.id
        val incoming = Intent().putExtra("method_id", id)
            .putExtra("input_payload", "Evidence fixture").putExtra("input_content_mode", "text")
            .putExtra("input_loop", "false").putExtra("input_frame_duration_ms", "150")
            .putExtra("input_shard_bytes", "384").putExtra("input_methodmesh_native_preset_run", (!automatic).toString())
            .putExtra("input_methodmesh_finish_to_launcher", "true")
        val request = AndroidIntentRequestReader.workflowRequest(incoming)
        val context = CapabilityScreenContext(request.actions.single(), request, 1, 1,
            if (automatic) CapabilityCompletionMode.AutomaticReturn else CapabilityCompletionMode.ManualConfirmation,
            hostOwnsNativePresetCloseout = true)
        val callback = ActivityLifecycleCallback { activity, stage ->
            if (activity is ExternalWorkflowActivity && stage == Stage.CREATED && activity.intent.getStringExtra("signal_commit_fixture") == token) {
                val guarded = object : ContextWrapper(activity) {
                    override fun startActivity(intent: Intent) { fail("Unexpected caller/Home/share launch: $intent") }
                }
                activity.setContent { MethodMeshTheme { CompositionLocalProvider(LocalContext provides guarded) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        val screen = if (burst) SignalQrBurstTransmitCapabilityScreen else SignalQrTransmitCapabilityScreen
                        screen.Render(context, {}, { returned += it }, {})
                    }
                } } }
            }
        }
        instrumentation.runOnMainSync { ActivityLifecycleMonitorRegistry.getInstance().addLifecycleCallback(callback) }
        try {
            ActivityScenario.launch<ExternalWorkflowActivity>(Intent(app, ExternalWorkflowActivity::class.java)
                .putExtra("signal_commit_fixture", token)).use { run(it, returned) }
        } finally {
            instrumentation.runOnMainSync { ActivityLifecycleMonitorRegistry.getInstance().removeLifecycleCallback(callback) }
        }
    }

    private fun retention(burst: Boolean) = fixture(burst) { scenario, returned ->
        cycle(burst); click("Commit")
        val original = requireNotNull(captured(scenario))
        val json = full(original)
        assertTrue(returned.isEmpty())
        scenario.recreate()
        show("Recommit")
        assertSame(original, captured(scenario))
        assertEquals(json, full(requireNotNull(captured(scenario))))
        show(if (burst) "Start QR Burst" else "Start blast")
        click("Done")
        await { returned.size == 1 }
        assertSame(original, returned.single())
        assertEquals(json, full(returned.single()))
    }
    private fun recommit(burst: Boolean) = fixture(burst) { scenario, _ ->
        cycle(burst); click("Commit")
        val original = requireNotNull(captured(scenario)); val json = full(original)
        cycle(burst, recommit = true); click("Recommit")
        val next = requireNotNull(captured(scenario))
        assertEquals(original.observations.single().values, next.observations.single().values)
        assertNotEquals(original.request.id, next.request.id)
        assertEquals(json, full(original))
        scenario.recreate(); show("Recommit")
        assertSame(next, captured(scenario))
    }
    private fun lost(burst: Boolean) = fixture(burst) { scenario, returned ->
        cycle(burst); click("Commit")
        val original = requireNotNull(captured(scenario))
        scenario.onActivity { activity -> ViewModelProvider(activity)[RetainedExecutionStore::class.java].let { store ->
            store.snapshot().keys.toList().forEach(store::forget)
        } }
        scenario.recreate()
        repeat(4) { find({ it.isScrollable })?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) }
        instrumentation.waitForIdleSync()
        await { find({ it.text?.toString()?.startsWith("Committed result unavailable after restart.") == true }) != null }
        assertNull(captured(scenario)); assertTrue(returned.isEmpty())
        assertNull(text("Done"))
        cycle(burst); click("Commit")
        assertNotEquals(original.request.id, requireNotNull(captured(scenario)).request.id)
        click("Done"); await { returned.size == 1 }
        assertNotSame(original, returned.single())
    }
    private fun automatic(burst: Boolean) = fixture(burst, automatic = true) { scenario, returned ->
        cycle(burst); click("Commit")
        await { returned.size == 1 }
        val original = returned.single(); val json = full(original)
        assertSame(original, captured(scenario))
        scenario.recreate(); show("Recommit")
        assertEquals(1, returned.size)
        assertSame(original, captured(scenario))
        assertEquals(json, full(requireNotNull(captured(scenario))))
    }
    @Test fun qrRetainsExactCommittedEvidenceThroughRecreationAndDone() = retention(false)
    @Test fun burstRetainsExactCommittedEvidenceThroughRecreationAndDone() = retention(true)
    @Test fun qrExplicitRecommitWithSameValuesCreatesANewExecution() = recommit(false)
    @Test fun burstExplicitRecommitWithSameValuesCreatesANewExecution() = recommit(true)
    @Test fun qrLostEnvelopeCannotBeReturnedAsARestoredResult() = lost(false)
    @Test fun burstLostEnvelopeCannotBeReturnedAsARestoredResult() = lost(true)
    @Test fun qrAutomaticReturnUsesCapturedObjectWithoutReplayOnRecreation() = automatic(false)
    @Test fun burstAutomaticReturnUsesCapturedObjectWithoutReplayOnRecreation() = automatic(true)
}
