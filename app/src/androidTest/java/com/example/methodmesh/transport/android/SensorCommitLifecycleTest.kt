package com.example.methodmesh.transport.android

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
import com.example.methodmesh.modules.compass.CompassCapabilityScreen
import com.example.methodmesh.modules.signals.SignalSensorScopeCapabilityScreen
import com.example.methodmesh.modules.signals.SignalSensorScopeFields
import com.example.methodmesh.platform.sensors.PhoneSensorRepository
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.ReturnMode
import com.example.methodmesh.transport.workflow.ui.CapabilityCompletionMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.RetainedExecutionStore
import com.example.methodmesh.ui.theme.MethodMeshTheme
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/** Actual Compass/sensor-scope Commit screens with emulator sensors, no camera or caller app. */
class SensorCommitLifecycleTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext
    private fun root(): AccessibilityNodeInfo? {
        if (Build.VERSION.SDK_INT >= 34) instrumentation.uiAutomation.clearCache()
        return instrumentation.uiAutomation.rootInActiveWindow
    }
    private fun find(predicate: (AccessibilityNodeInfo) -> Boolean, node: AccessibilityNodeInfo? = root()): AccessibilityNodeInfo? {
        if (node == null) return null
        if (predicate(node)) return node
        for (index in 0 until node.childCount) find(predicate, node.getChild(index))?.let { return it }
        return null
    }
    private fun text(value: String) = find({ it.text?.toString()?.startsWith(value) == true })
    private fun await(condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 12_000
        while (!condition()) {
            check(SystemClock.uptimeMillis() < end) { "Sensor Commit fixture timed out" }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(25)
        }
    }
    private fun show(label: String): AccessibilityNodeInfo {
        repeat(3) { find({ it.isScrollable })?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) }
        instrumentation.waitForIdleSync()
        SystemClock.sleep(200)
        await {
            if (text(label) != null) true else {
                find({ it.isScrollable })?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                SystemClock.sleep(250)
                false
            }
        }
        return requireNotNull(text(label))
    }
    private fun click(label: String) {
        var node = show(label)
        while (!node.isClickable) node = requireNotNull(node.parent)
        node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
        instrumentation.waitForIdleSync()
        SystemClock.sleep(200)
        node = requireNotNull(text(label))
        while (!node.isClickable) node = requireNotNull(node.parent)
        assertTrue("Disabled control: $label", node.isEnabled)
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
    private fun commitLabel(compass: Boolean) = if (compass) "Commit reading" else "Commit snapshot"
    private fun recommitLabel(compass: Boolean) = if (compass) "Recommit reading" else "Recommit snapshot"
    private fun live(compass: Boolean) = await {
        if (compass) PhoneSensorRepository.headingDegrees != null
        else PhoneSensorRepository.readings["accelerometer"]?.let { it.available && it.values.isNotEmpty() } == true
    }

    private fun fixture(compass: Boolean, automatic: Boolean = false,
        run: (ActivityScenario<ExternalWorkflowActivity>, List<ExecutionResult>) -> Unit) {
        val token = UUID.randomUUID().toString()
        val returned = CopyOnWriteArrayList<ExecutionResult>()
        val incoming = Intent().putExtra("method_id", if (compass) "compass.read" else "signal.sensor.scope")
            .putExtra("input_methodmesh_native_preset_run", (!automatic).toString())
            .putExtra("input_methodmesh_finish_to_launcher", "true")
            .putExtra("input_sensor", "accelerometer")
            .putExtra("input_target_mode", "north")
            .putExtra("input_start_in_sight_mode", "false")
            .putExtra("input_show_camera_in_sight", "false")
        val request = AndroidIntentRequestReader.workflowRequest(incoming)
        val context = CapabilityScreenContext(request.actions.single(), request, 1, 1,
            if (automatic) CapabilityCompletionMode.AutomaticReturn else CapabilityCompletionMode.ManualConfirmation,
            hostOwnsNativePresetCloseout = true)
        val callback = ActivityLifecycleCallback { activity, stage ->
            if (activity is ExternalWorkflowActivity && stage == Stage.CREATED && activity.intent.getStringExtra("sensor_commit_fixture") == token) {
                val guarded = object : ContextWrapper(activity) {
                    override fun startActivity(intent: Intent) { fail("Unexpected camera/share/Home launch: $intent") }
                }
                activity.setContent { MethodMeshTheme { CompositionLocalProvider(LocalContext provides guarded) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        val screen = if (compass) CompassCapabilityScreen else SignalSensorScopeCapabilityScreen
                        screen.Render(context, {}, { returned += it }, {})
                    }
                } } }
            }
        }
        instrumentation.runOnMainSync { ActivityLifecycleMonitorRegistry.getInstance().addLifecycleCallback(callback) }
        try {
            ActivityScenario.launch<ExternalWorkflowActivity>(Intent(app, ExternalWorkflowActivity::class.java)
                .putExtra("sensor_commit_fixture", token)).use { scenario ->
                live(compass)
                run(scenario, returned)
            }
        } finally {
            instrumentation.runOnMainSync { ActivityLifecycleMonitorRegistry.getInstance().removeLifecycleCallback(callback) }
        }
    }

    private fun retention(compass: Boolean) = fixture(compass) { scenario, returned ->
        click(commitLabel(compass))
        await { captured(scenario) != null }
        val original = requireNotNull(captured(scenario))
        val json = full(original)
        assertTrue(returned.isEmpty())
        scenario.recreate()
        show(recommitLabel(compass))
        assertSame(original, captured(scenario))
        assertEquals(json, full(requireNotNull(captured(scenario))))
        click("Done")
        await { returned.size == 1 }
        assertSame(original, returned.single())
        assertEquals(json, full(returned.single()))
    }
    private fun recommit(compass: Boolean) = fixture(compass) { scenario, returned ->
        if (!compass) scenario.onActivity {
            // Identical readings must still produce a new explicit Recommit, not reuse a JSON-keyed envelope.
            PhoneSensorRepository.stop()
            PhoneSensorRepository.readings["accelerometer"] = PhoneSensorRepository.readings.getValue("accelerometer")
                .copy(values = listOf(3f, 4f, 0f), accuracy = 3)
        }
        click(commitLabel(compass))
        await { captured(scenario) != null }
        val first = requireNotNull(captured(scenario))
        val firstJson = full(first)
        click(recommitLabel(compass))
        await { captured(scenario)?.request?.id != first.request.id }
        val second = requireNotNull(captured(scenario))
        assertNotEquals(first.request.id, second.request.id)
        assertEquals(firstJson, full(first))
        if (!compass) {
            val firstFields = OutputFormatter.fields(first, includeProvenance = false)
            val secondFields = OutputFormatter.fields(second, includeProvenance = false)
            assertEquals("3.0000", firstFields[SignalSensorScopeFields.VALUE_0])
            assertEquals(firstFields[SignalSensorScopeFields.VALUES_JSON], secondFields[SignalSensorScopeFields.VALUES_JSON])
        }
        val secondJson = full(second)
        scenario.recreate()
        show(recommitLabel(compass))
        assertSame(second, captured(scenario))
        assertEquals(secondJson, full(requireNotNull(captured(scenario))))
        assertTrue(returned.isEmpty())
        click("Done")
        await { returned.size == 1 }
        assertSame(second, returned.single())
    }
    private fun unavailable(compass: Boolean) = fixture(compass) { scenario, returned ->
        click(commitLabel(compass))
        await { captured(scenario) != null }
        val original = requireNotNull(captured(scenario))
        scenario.onActivity { activity ->
            val store = ViewModelProvider(activity)[RetainedExecutionStore::class.java]
            store.snapshot().keys.forEach(store::forget)
        }
        scenario.recreate()
        show("Captured result unavailable.")
        assertNull(captured(scenario))
        assertTrue(returned.isEmpty())
        assertNull(text("Done"))
        live(compass)
        click(commitLabel(compass))
        await { captured(scenario) != null }
        val fresh = requireNotNull(captured(scenario))
        assertNotEquals(original.request.id, fresh.request.id)
        click("Done")
        await { returned.size == 1 }
        assertSame(fresh, returned.single())
    }
    private fun automatic(compass: Boolean) = fixture(compass, automatic = true) { scenario, returned ->
        click(commitLabel(compass))
        await { returned.size == 1 }
        val original = returned.single()
        val json = full(original)
        assertSame(original, captured(scenario))
        scenario.recreate()
        show(recommitLabel(compass))
        assertSame(original, captured(scenario))
        assertEquals(json, full(requireNotNull(captured(scenario))))
        assertEquals("Recreation returned the same capture twice", 1, returned.size)
    }

    @Test fun compassRotationAndDonePreserveTheOriginalFullExecution() = retention(true)
    @Test fun scopeRotationAndDonePreserveTheOriginalFullExecution() = retention(false)
    @Test fun compassRecommitCreatesOneNewExecutionWithoutChangingTheOldOne() = recommit(true)
    @Test fun scopeRecommitCreatesOneNewExecutionWithoutChangingTheOldOne() = recommit(false)
    @Test fun compassMissingEnvelopeRequiresAnExplicitNewLiveCommit() = unavailable(true)
    @Test fun scopeMissingEnvelopeRequiresAnExplicitNewLiveCommit() = unavailable(false)
    @Test fun compassAutomaticReturnUsesTheRetainedObjectWithoutReplay() = automatic(true)
    @Test fun scopeAutomaticReturnUsesTheRetainedObjectWithoutReplay() = automatic(false)
}
