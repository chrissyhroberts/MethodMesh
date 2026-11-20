package com.example.methodmesh.transport.android

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.modules.calibratedscale.As100CalibratedScaleMethod
import com.example.methodmesh.modules.conversions.As100ConversionsMethod
import com.example.methodmesh.modules.multicounter.As100MulticounterMethod
import com.example.methodmesh.modules.multicounter.MulticounterFields
import com.example.methodmesh.modules.randomnumber.As100RandomNumberMethod
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.ReturnMode
import com.example.methodmesh.transport.workflow.ui.RetainedExecutionStore
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

/** Actual module screens, deterministic input, no hardware/network or exported files. */
class CapabilityResultRecreationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun intent(id: String) = Intent(instrumentation.targetContext, ExternalWorkflowActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        .putExtra("method_id", id).putExtra("input_methodmesh_native_preset_run", "true")
    private fun await(condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 10_000
        while (!condition()) {
            check(SystemClock.uptimeMillis() < end) { "Capability recreation condition timed out" }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(25)
        }
    }
    private fun node(text: String, root: AccessibilityNodeInfo? = instrumentation.uiAutomation.rootInActiveWindow): AccessibilityNodeInfo? {
        if (root == null) return null
        if (root.text?.toString() == text || root.contentDescription?.toString() == text) return root
        for (index in 0 until root.childCount) node(text, root.getChild(index))?.let { return it }
        return null
    }
    private fun captured(scenario: ActivityScenario<ExternalWorkflowActivity>): ExecutionResult? {
        val value = AtomicReference<ExecutionResult>()
        scenario.onActivity { activity ->
            value.set(ViewModelProvider(activity)[RetainedExecutionStore::class.java].snapshot().values.filterNotNull().singleOrNull())
        }
        return value.get()
    }
    private fun click(text: String) {
        await { button(text)?.isEnabled == true }
        var target = requireNotNull(node(text))
        while (!target.isClickable) target = requireNotNull(target.parent)
        assertTrue(target.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }
    private fun button(text: String): AccessibilityNodeInfo? {
        var target = node(text) ?: return null
        while (!target.isClickable && target.parent != null) target = target.parent
        return target
    }
    private fun scale(label: String, root: AccessibilityNodeInfo? = instrumentation.uiAutomation.rootInActiveWindow): AccessibilityNodeInfo? {
        if (root == null) return null
        if (root.contentDescription?.toString() == label && root.rangeInfo != null) return root
        for (index in 0 until root.childCount) scale(label, root.getChild(index))?.let { return it }
        return null
    }
    private fun select(label: String, value: Float) {
        await { scale(label) != null }
        assertTrue(requireNotNull(scale(label)).performAction(
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id,
            Bundle().apply { putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, value) }
        ))
        instrumentation.waitForIdleSync()
    }
    private fun scaleIntent() = intent(As100CalibratedScaleMethod.ID)
        .putExtra("input_vas_length_mm", "50")
    private fun counterIntent(mode: String = "count") = intent(As100MulticounterMethod.ID)
        .putExtra("input_mode", mode).putExtra("input_entity_count", "1")
        .putExtra("input_entity_names", "Fixture").putExtra("input_step", "2")
    private fun assertRetained(scenario: ActivityScenario<ExternalWorkflowActivity>, original: ExecutionResult) {
        val json = OutputFormatter.format(original, ReturnMode.Json)
        scenario.recreate()
        await { node("Result") != null }
        assertSame(original, captured(scenario))
        assertEquals(json, OutputFormatter.format(captured(scenario)!!, ReturnMode.Json))
    }
    private fun forgetCaptured(scenario: ActivityScenario<ExternalWorkflowActivity>) {
        // Deliberately lose the in-memory envelope while retaining Android saved
        // state. This tests recovery policy, not a full OS process-kill scenario.
        scenario.onActivity { activity ->
            val store = ViewModelProvider(activity)[RetainedExecutionStore::class.java]
            store.snapshot().keys.forEach(store::forget)
        }
        scenario.recreate()
    }

    @Test fun randomNumberRecreationKeepsTheGeneratedExecutionAndVisibleResult() {
        val incoming = intent(As100RandomNumberMethod.ID).putExtra("input_seed_mode", "fixed_seed").putExtra("input_seed", "fixture")
        ActivityScenario.launch<ExternalWorkflowActivity>(incoming).use { scenario ->
            await { captured(scenario) != null && node("Result") != null }
            val original = captured(scenario)!!
            val json = OutputFormatter.format(original, ReturnMode.Json)
            scenario.recreate()
            await { node("Result") != null }
            assertSame(original, captured(scenario))
            assertEquals(json, OutputFormatter.format(captured(scenario)!!, ReturnMode.Json))
        }
    }

    @Test fun conversionsRecreationPreservesTheCommittedExecutionIdentityAndHistoricalEvidence() {
        val incoming = intent(As100ConversionsMethod.ID).putExtra("input_category", "length")
            .putExtra("input_value", "12").putExtra("input_from_unit", "m").putExtra("input_to_unit", "cm")
        ActivityScenario.launch<ExternalWorkflowActivity>(incoming).use { scenario ->
            click("COMMIT")
            await { captured(scenario) != null && node("FROZEN") != null }
            val original = captured(scenario)!!
            val json = OutputFormatter.format(original, ReturnMode.Json)
            scenario.recreate()
            await { node("FROZEN") != null }
            assertSame(original, captured(scenario))
            assertEquals(json, OutputFormatter.format(captured(scenario)!!, ReturnMode.Json))
        }
    }

    @Test fun scalarSelectionAndCapturedCalibrationEvidenceSurviveRecreation() {
        ActivityScenario.launch<ExternalWorkflowActivity>(scaleIntent()).use { scenario ->
            await { button("Use this measurement") != null }
            assertFalse(button("Use this measurement")!!.isEnabled)
            select("Current value", 62.25f)
            scenario.recreate()
            await { scale("Current value") != null }
            assertEquals(62.25f, scale("Current value")!!.rangeInfo.current, 0f)
            await { button("Use this measurement")?.isEnabled == true }
            click("Use this measurement")
            await { captured(scenario) != null }
            val original = captured(scenario)!!
            val values = original.observations.flatMap { it.values.entries }.associate { it.toPair() }
            assertEquals(62.25f, values["value"].toString().toFloat(), 0f)
            assertEquals(50f, values["scale_length_mm"].toString().toFloat(), 0f)
            assertFalse(values.containsKey("lower_value"))
            assertRetained(scenario, original)
        }
    }

    @Test fun rangeRestoresWorkingValuesWithoutMarkingTheUntouchedMarkerAsSelected() {
        val incoming = scaleIntent().putExtra("input_use_range", "true")
            .putExtra("input_lower_label", "Lower selection").putExtra("input_upper_label", "Upper selection")
        ActivityScenario.launch<ExternalWorkflowActivity>(incoming).use { scenario ->
            select("Lower selection", 25f)
            scenario.recreate()
            await { scale("Lower selection") != null }
            assertEquals(25f, scale("Lower selection")!!.rangeInfo.current, 0f)
            assertFalse(button("Use this measurement")!!.isEnabled)
            select("Upper selection", 80f)
            await { button("Use this measurement")?.isEnabled == true }
            click("Use this measurement")
            await { captured(scenario) != null }
            val original = captured(scenario)!!
            val values = original.observations.flatMap { it.values.entries }.associate { it.toPair() }
            assertEquals(25f, values["lower_value"].toString().toFloat(), 0f)
            assertEquals(80f, values["upper_value"].toString().toFloat(), 0f)
            assertFalse(values.containsKey("value"))
            assertRetained(scenario, original)
        }
    }

    @Test fun multicounterKeepsActiveCountsAndTheExactFinishedAuditAcrossRecreation() {
        ActivityScenario.launch<ExternalWorkflowActivity>(counterIntent()).use { scenario ->
            click("+")
            await { node("2") != null }
            click("+")
            await { node("4") != null }
            scenario.recreate()
            await { node("4") != null }
            assertNull(captured(scenario))
            click("Finish session")
            await { captured(scenario) != null }
            val original = captured(scenario)!!
            val fields = OutputFormatter.fields(original)
            val entity = JSONArray(fields[MulticounterFields.ENTITIES_JSON].toString()).getJSONObject(0)
            assertEquals(4, entity.getInt("value"))
            val events = JSONObject(fields[MulticounterFields.AUDIT_JSON].toString()).getJSONArray("events")
            assertEquals(1, (0 until events.length()).count { events.getJSONObject(it).getString("event_type") == "session_finished" })
            assertRetained(scenario, original)
        }
    }

    @Test fun multicounterTimerContinuesThroughRotationThenFreezesInTheFinishedEvidence() {
        ActivityScenario.launch<ExternalWorkflowActivity>(counterIntent("count_and_time")).use { scenario ->
            click("Start")
            click("+")
            scenario.recreate()
            await { node("Pause") != null && node("2") != null }
            click("Finish session")
            await { captured(scenario) != null }
            val original = captured(scenario)!!
            val entity = JSONArray(OutputFormatter.fields(original)[MulticounterFields.ENTITIES_JSON].toString()).getJSONObject(0)
            assertEquals(2, entity.getInt("value"))
            assertTrue(entity.getLong("elapsed_ms") > 0)
            assertFalse(entity.getBoolean("running"))
            assertFalse(entity.getBoolean("scheduled"))
            assertRetained(scenario, original)
        }
    }

    @Test fun lostScaleEnvelopeRequiresANewExplicitSelectionInsteadOfReissuingEvidence() {
        ActivityScenario.launch<ExternalWorkflowActivity>(scaleIntent()).use { scenario ->
            select("Current value", 62f)
            click("Use this measurement")
            await { captured(scenario) != null }
            forgetCaptured(scenario)
            await { node("Captured measurement unavailable") != null }
            assertNull(captured(scenario))
            click("Start new measurement")
            await { button("Use this measurement") != null }
            assertFalse(button("Use this measurement")!!.isEnabled)
            assertNull(captured(scenario))
        }
    }

    @Test fun lostCounterEnvelopeDoesNotRebuildOrSilentlyReturnTheFinishedSession() {
        ActivityScenario.launch<ExternalWorkflowActivity>(counterIntent()).use { scenario ->
            click("+")
            click("Finish session")
            await { captured(scenario) != null }
            forgetCaptured(scenario)
            await { node("Finished session unavailable") != null }
            assertNull(captured(scenario))
            click("Start new session")
            click("Start session")
            await { node("0") != null && node("+") != null }
            assertNull(captured(scenario))
        }
    }
}
