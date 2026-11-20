package com.example.methodmesh.modules.astronomy

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
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
import com.example.methodmesh.transport.workflow.ui.*
import com.example.methodmesh.ui.theme.MethodMeshTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Production measurement screens with injected frames/timers/sensor summaries; no camera or NFC radio. */
class AstronomyMeasurementLifecycleTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext
    private fun find(predicate: (AccessibilityNodeInfo) -> Boolean,
        root: AccessibilityNodeInfo? = instrumentation.uiAutomation.rootInActiveWindow): AccessibilityNodeInfo? {
        if (root == null) return null
        if (predicate(root)) return root
        for (i in 0 until root.childCount) find(predicate, root.getChild(i))?.let { return it }
        return null
    }
    private fun text(value: String) = find({ it.text?.toString() == value })
    private fun await(condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 12_000
        while (!condition()) {
            check(SystemClock.uptimeMillis() < end) { "Astronomy measurement condition timed out" }
            instrumentation.waitForIdleSync(); SystemClock.sleep(25)
        }
    }
    private fun show(label: String) {
        repeat(4) { find({ it.isScrollable })?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) }
        instrumentation.waitForIdleSync()
        await {
            if (text(label) != null) true else {
                find({ it.isScrollable })?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                SystemClock.sleep(60); false
            }
        }
        text(label)!!.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
        instrumentation.waitForIdleSync()
    }
    private fun button(label: String): AccessibilityNodeInfo {
        var node = requireNotNull(text(label)) { label }
        while (!node.isClickable) node = requireNotNull(node.parent)
        return node
    }
    private fun click(label: String) {
        show(label); assertTrue(button(label).isEnabled)
        assertTrue(button(label).performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
    }
    private fun captured(scenario: ActivityScenario<ExternalWorkflowActivity>): ExecutionResult? {
        val result = AtomicReference<ExecutionResult>()
        scenario.onActivity { result.set(ViewModelProvider(it)[RetainedExecutionStore::class.java]
            .snapshot().values.filterNotNull().singleOrNull()) }
        return result.get()
    }
    private fun metric(width: Double) = StarFrameMetric(1, 10.0, 10.0, width, 12.0, 150.0, 100.0, true)
    private class Evidence(val kind: String) {
        val token = UUID.randomUUID().toString()
        val method get() = when (kind) { "sky" -> As100SkyTestMethod; "focus" -> As100FocusMethod; else -> As100MountStabilityMethod }
        val session = AtomicReference<AstronomyFrameSession>()
        val waits = CopyOnWriteArrayList<CompletableDeferred<Unit>>()
        val starts = AtomicInteger()
        val stops = AtomicInteger()
        val sourceNumber = AtomicInteger()
        val liveSources = CopyOnWriteArrayList<Int>()
        var nonCooperativeWait = false
        val binds = AtomicInteger()
        val returned = CopyOnWriteArrayList<ExecutionResult>()
        val changed = AtomicReference<Map<String, String>>(emptyMap())
        val cameraError = mutableStateOf<String?>(null)
    }
    private fun fixture(e: Evidence, automatic: Boolean = false,
        runtime: String = if (e.kind == "focus") "sample_frames" else "duration_seconds",
        run: (ActivityScenario<ExternalWorkflowActivity>) -> Unit) {
        val intent = Intent().putExtra("method_id", e.method.id)
            .putExtra("input_methodmesh_native_preset_run", (!automatic).toString())
            .putExtra("input_methodmesh_runtime_fields", runtime)
            .putExtra("input_methodmesh_finish_to_launcher", "true")
            .putExtra("input_duration_seconds", "3").putExtra("input_minimum_frames", "10")
            .putExtra("input_sample_frames", "5").putExtra("input_camera_facing", "rear")
            .putExtra("input_calibration_note", "Frozen reference note")
        val request = AndroidIntentRequestReader.workflowRequest(intent)
        val context = CapabilityScreenContext(request.actions.single(), request, 1, 1,
            if (automatic) CapabilityCompletionMode.AutomaticReturn else CapabilityCompletionMode.ManualConfirmation,
            onSettingsChanged = e.changed::set)
        val callback = ActivityLifecycleCallback { activity, stage ->
            if (activity is ExternalWorkflowActivity && stage == Stage.CREATED &&
                activity.intent.getStringExtra("measurement_fixture") == e.token) {
                activity.setContent { MethodMeshTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
                    val cameraContent: @Composable (AstronomyFrameSession, (String) -> Unit) -> Unit = { sample, error ->
                        SideEffect { e.session.set(sample) }
                        DisposableEffect(sample) { e.binds.incrementAndGet(); onDispose {} }
                        LaunchedEffect(e.cameraError.value) { e.cameraError.value?.let(error) }
                        Text("Synthetic point-source preview")
                    }
                    val wait: suspend (Long) -> Unit = {
                        val release = CompletableDeferred<Unit>(); e.waits += release
                        if (e.nonCooperativeWait) withContext(NonCancellable) { release.await() } else release.await()
                    }
                    when (e.kind) {
                        "sky" -> AstronomySkyTestScreen(context, {}, e.returned::add, {},
                            cameraPermission = true, waitForSample = wait, camera = cameraContent)
                        "focus" -> AstronomyFocusScreen(context, {}, e.returned::add, {}, cameraPermission = true,
                            camera = { sample, _, error -> cameraContent(sample, error) })
                        else -> AstronomyMountScreen(context, {}, e.returned::add, {}, waitForSample = wait,
                            sourceFactory = {
                                val key = e.sourceNumber.incrementAndGet()
                                AstronomyMountSource({ e.starts.incrementAndGet(); e.liveSources.add(key); Unit },
                                    { e.stops.incrementAndGet(); e.liveSources.remove(key); Unit },
                                    { MountStabilitySampler.Summary(50, 0.01, 0.001, 0.02, "EXCELLENT") })
                            })
                    }
                } } }
            }
        }
        instrumentation.runOnMainSync { ActivityLifecycleMonitorRegistry.getInstance().addLifecycleCallback(callback) }
        try {
            ActivityScenario.launch<ExternalWorkflowActivity>(Intent(app, ExternalWorkflowActivity::class.java)
                .putExtra("measurement_fixture", e.token)).use { scenario ->
                show(if (e.kind == "focus") "Capture focus" else if (e.kind == "sky") "Run sky test" else "Measure mount")
                run(scenario)
            }
        } finally {
            e.waits.forEach { it.complete(Unit) }
            instrumentation.runOnMainSync { ActivityLifecycleMonitorRegistry.getInstance().removeLifecycleCallback(callback) }
        }
    }
    private fun complete(e: Evidence, scenario: ActivityScenario<ExternalWorkflowActivity>) {
        if (e.kind != "focus") {
            val previous = e.session.get()
            click(if (e.kind == "sky") "Run sky test" else "Measure mount")
            await { e.waits.isNotEmpty() }
            if (e.kind == "sky") await { e.session.get() !== previous }
        }
        if (e.kind != "mount") scenario.onActivity { repeat(40) { e.session.get().accept(metric(2.0)) } }
        if (e.kind == "focus") click("Capture focus") else e.waits.last().complete(Unit)
        await { captured(scenario) != null }
    }

    @Test fun allThreeCompletedMeasurementsKeepTheExactOriginalAndFullEnvelopeThroughRotationAndDone() {
        for (kind in listOf("sky", "focus", "mount")) {
            val e = Evidence(kind)
            fixture(e) { scenario ->
                complete(e, scenario)
                val original = captured(scenario)!!
                val full = OutputFormatter.format(original, ReturnMode.Json, payloadMode = OutputFormatter.PayloadMode.FULL)
                val bindings = e.binds.get()
                scenario.recreate(); await { captured(scenario) != null }
                assertSame(original, captured(scenario))
                assertEquals(full, OutputFormatter.format(captured(scenario)!!, ReturnMode.Json, payloadMode = OutputFormatter.PayloadMode.FULL))
                assertEquals(bindings, e.binds.get())
                click("Done"); await { e.returned.size == 1 }
                assertSame(original, e.returned.single())
            }
        }
    }

    @Test fun interruptedSkyAndMountRunsStopWithoutReplayOrLateResults() {
        for (kind in listOf("sky", "mount")) {
            val e = Evidence(kind)
            fixture(e) { scenario ->
                click(if (kind == "sky") "Run sky test" else "Measure mount")
                await { e.waits.size == 1 }
                scenario.recreate()
                val message = if (kind == "sky") "Sample interrupted. Run a new sky test."
                    else "Measurement interrupted. Start a new mount sample."
                show(message)
                assertEquals(1, e.waits.size); assertNull(captured(scenario))
                e.waits.single().complete(Unit); instrumentation.waitForIdleSync()
                assertNull(captured(scenario)); assertTrue(e.returned.isEmpty())
                if (kind == "mount") assertTrue(e.stops.get() > 0)
            }
        }
    }

    @Test fun backgroundingCancelsAWindowAndResumeRequiresANewExplicitRun() {
        for (kind in listOf("sky", "mount")) {
            val e = Evidence(kind)
            fixture(e) { scenario ->
                click(if (kind == "sky") "Run sky test" else "Measure mount"); await { e.waits.size == 1 }
                scenario.moveToState(Lifecycle.State.CREATED)
                scenario.moveToState(Lifecycle.State.RESUMED)
                e.waits.single().complete(Unit)
                show(if (kind == "sky") "Run sky test" else "Measure mount")
                assertEquals(1, e.waits.size); assertNull(captured(scenario))
            }
        }
    }

    @Test fun missingRetainedEvidenceRequiresExplicitRecoveryWithoutReconstructionOrCameraReplay() {
        for (kind in listOf("sky", "focus", "mount")) {
            val e = Evidence(kind)
            fixture(e) { scenario ->
                complete(e, scenario); val binds = e.binds.get()
                scenario.onActivity { activity ->
                    val store = ViewModelProvider(activity)[RetainedExecutionStore::class.java]
                    store.snapshot().keys.forEach(store::forget)
                }
                scenario.recreate(); show("Captured result unavailable")
                assertNull(captured(scenario)); assertEquals(binds, e.binds.get())
                assertTrue(e.returned.isEmpty())
            }
        }
    }

    @Test fun focusResetRejectsOldFramesAndFixedPresetCameraControlsStayHidden() {
        val e = Evidence("focus")
        fixture(e) { scenario ->
            val old = e.session.get()
            scenario.onActivity { old.accept(metric(1.0)) }
            click("Reset best"); await { e.session.get() !== old }
            val fresh = e.session.get()
            scenario.onActivity { old.accept(metric(0.1)); fresh.accept(metric(7.0)) }
            assertFalse(old.isActive)
            assertNull(text("front")); assertNull(text("✓ rear"))
            click("Capture focus"); await { captured(scenario) != null }
            val values = captured(scenario)!!.observations.single().values
            assertEquals("7.0000", values[FocusFields.BEST])
            assertEquals("rear", values[FocusFields.CAMERA])
            assertEquals("5", values[FocusFields.AVERAGING])
        }
    }

    @Test fun askedCameraSwitchStartsFreshAndRecordsTheSelectedCameraWithoutOldMetrics() {
        val e = Evidence("focus")
        fixture(e, runtime = "camera_facing,sample_frames") { scenario ->
            val rear = e.session.get()
            scenario.onActivity { rear.accept(metric(1.0)) }
            click("front"); await { e.session.get() !== rear }
            scenario.onActivity { rear.accept(metric(0.1)); e.session.get().accept(metric(8.0)) }
            click("Capture focus"); await { captured(scenario) != null }
            val fields = captured(scenario)!!.observations.single().values
            assertEquals("front", fields[FocusFields.CAMERA])
            assertEquals("8.0000", fields[FocusFields.BEST])
            assertFalse(rear.isActive)
        }
    }

    @Test fun runtimeDurationIsVisibleAndCapturedFromStartDespiteEditsDuringSampling() {
        val e = Evidence("mount")
        fixture(e) { scenario ->
            click("Measure mount"); await { e.waits.size == 1 }
            val field = requireNotNull(find({ it.isEditable && it.text?.toString() == "3" }))
            assertTrue(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "4")
            }))
            await { e.changed.get()["duration_seconds"] == "4" }
            e.waits.single().complete(Unit); await { captured(scenario) != null }
            assertEquals("3", captured(scenario)!!.request.context["duration_seconds"])
            scenario.recreate(); click("Retry"); show("Measurement duration (s)")
            assertNotNull(find({ it.isEditable && it.text?.toString() == "4" }))
        }
    }

    @Test fun completedSkySampleCanBeSavedAsTheExactClearNightReferenceAfterRotation() {
        val prefs = app.getSharedPreferences("methodmesh_astronomy", 0)
        val previous = prefs.getString("good_night_calibration", null)
        try {
            val e = Evidence("sky")
            fixture(e) { scenario ->
                complete(e, scenario); val original = captured(scenario)!!
                scenario.recreate()
                click("Save this run as CLEAR-NIGHT reference")
                val reference = AstronomyRepository(app).loadCalibration()!!
                assertEquals(2.0, reference.fwhmPx, 0.0)
                assertEquals(12.0, reference.backgroundLuma, 0.0)
                assertEquals("Frozen reference note", reference.note)
                assertSame(original, captured(scenario))
            }
        } finally {
            prefs.edit().apply {
                if (previous == null) remove("good_night_calibration") else putString("good_night_calibration", previous)
            }.commit()
        }
    }

    @Test fun automaticCompletedMeasurementsReturnTheirOriginalExactlyOnce() {
        for (kind in listOf("sky", "focus", "mount")) {
            val e = Evidence(kind)
            fixture(e, automatic = true, runtime = "") { scenario ->
                complete(e, scenario); await { e.returned.size == 1 }
                assertSame(captured(scenario), e.returned.single())
                scenario.recreate(); instrumentation.waitForIdleSync()
                assertEquals(1, e.returned.size)
            }
        }
    }

    @Test fun cameraFailureDuringSkySampleCancelsWithoutRecordingOrReturningIt() {
        val e = Evidence("sky")
        fixture(e) { scenario ->
            click("Run sky test"); await { e.waits.size == 1 }
            scenario.onActivity { e.cameraError.value = "Camera unavailable: synthetic disconnect" }
            show("Camera unavailable: synthetic disconnect")
            e.waits.single().complete(Unit); instrumentation.waitForIdleSync()
            assertNull(captured(scenario)); assertTrue(e.returned.isEmpty())
            show("Run sky test"); assertTrue(button("Run sky test").isEnabled)
        }
    }

    @Test fun lateCancelledMountRunCannotStopTheNewerSensorSourceOrPublishItsResult() {
        val e = Evidence("mount").apply { nonCooperativeWait = true }
        fixture(e) { scenario ->
            click("Measure mount"); await { e.waits.size == 1 }
            click("Cancel sample"); click("Measure mount"); await { e.waits.size == 2 }
            assertEquals(listOf(2), e.liveSources.toList())
            e.waits[0].complete(Unit)
            await { e.stops.get() >= 2 }
            assertEquals(listOf(2), e.liveSources.toList())
            assertNull(captured(scenario))
            e.waits[1].complete(Unit); await { captured(scenario) != null }
            assertTrue(e.liveSources.isEmpty())
            assertTrue(e.returned.isEmpty())
            assertEquals(2, e.starts.get())
        }
    }
}
