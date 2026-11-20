package com.example.methodmesh.modules.astronomy

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Production dashboard UI; every provider result is synthetic and no map data is downloaded. */
class AstronomyDashboardLifecycleTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext
    private val refresh = "↻  Refresh"
    private fun payload(retrieved: String = "2026-10-20T10:00:00Z") = AstronomyDashboardLivePayload(
        weatherJson = """{"current":{"temperature_2m":17,"relative_humidity_2m":50,"cloud_cover":10,"wind_speed_10m":5,"visibility":30000}}""",
        airQualityJson = "", hourlyJson = """{"hourly":{"time":[]}}""", jetJson = "",
        retrievedTimeIso = retrieved, fromCache = true, dataAgeHours = 24.0)

    private fun find(predicate: (AccessibilityNodeInfo) -> Boolean, root: AccessibilityNodeInfo? = instrumentation.uiAutomation.rootInActiveWindow): AccessibilityNodeInfo? {
        if (root == null) return null
        if (predicate(root)) return root
        for (index in 0 until root.childCount) find(predicate, root.getChild(index))?.let { return it }
        return null
    }
    private fun node(text: String) = find({ it.text?.toString() == text })
    private fun await(condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 12_000
        while (!condition()) {
            check(SystemClock.uptimeMillis() < end) { "Astronomy dashboard condition timed out" }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(25)
        }
    }
    private fun button(text: String): AccessibilityNodeInfo {
        var target = requireNotNull(node(text))
        while (!target.isClickable) target = requireNotNull(target.parent)
        return target
    }
    private fun clickRefresh() {
        await { node(refresh) != null }
        assertTrue(button(refresh).isEnabled)
        assertTrue(button(refresh).performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }
    private fun captured(scenario: ActivityScenario<ExternalWorkflowActivity>): ExecutionResult? {
        val result = AtomicReference<ExecutionResult>()
        scenario.onActivity { result.set(ViewModelProvider(it)[RetainedExecutionStore::class.java].snapshot().values.filterNotNull().singleOrNull()) }
        return result.get()
    }
    private fun withScreen(
        load: (Context, Double, Double) -> AstronomyDashboardLivePayload = { _, _, _ -> payload() },
        nativePreset: Boolean = true,
        runtimeFields: String = "latitude",
        onConfirmed: (ExecutionResult) -> Unit = {},
        run: (ActivityScenario<ExternalWorkflowActivity>, AtomicReference<ExternalWorkflowActivity>, AtomicInteger) -> Unit
    ) {
        val token = UUID.randomUUID().toString()
        val request = AndroidIntentRequestReader.workflowRequest(Intent(app, ExternalWorkflowActivity::class.java)
            .putExtra("method_id", As100DashboardMethod.id)
            .putExtra("input_methodmesh_native_preset_run", nativePreset.toString())
            .putExtra("input_methodmesh_runtime_fields", runtimeFields)
            .putExtra("input_location_source", "manual")
            .putExtra("input_latitude", "51.1").putExtra("input_longitude", "-0.1"))
        val context = CapabilityScreenContext(request.actions.single(), request, 1, 1,
            if (nativePreset) CapabilityCompletionMode.ManualConfirmation else CapabilityCompletionMode.AutomaticReturn)
        val active = AtomicReference<ExternalWorkflowActivity>()
        val calls = AtomicInteger()
        val callback = ActivityLifecycleCallback { activity, stage ->
            if (activity is ExternalWorkflowActivity && stage == Stage.CREATED && activity.intent.getStringExtra("review_astronomy") == token) {
                active.set(activity)
                activity.setContent { MethodMeshTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
                    AstronomyDashboardScreen(context, {}, onConfirmed, {}, { c, lat, lon -> calls.incrementAndGet(); load(c, lat, lon) })
                } } }
            }
        }
        instrumentation.runOnMainSync { ActivityLifecycleMonitorRegistry.getInstance().addLifecycleCallback(callback) }
        try {
            ActivityScenario.launch<ExternalWorkflowActivity>(Intent(app, ExternalWorkflowActivity::class.java)
                .putExtra("review_astronomy", token)).use { scenario ->
                await { if (nativePreset) node(refresh) != null else calls.get() > 0 }
                run(scenario, active, calls)
            }
        } finally {
            instrumentation.runOnMainSync { ActivityLifecycleMonitorRegistry.getInstance().removeLifecycleCallback(callback) }
        }
    }

    @Test fun runtimeInputsWaitForRefreshAndDoNotExposeFixedCoordinatesOrGps() = withScreen { scenario, _, calls ->
        await { node("Enter runtime inputs, then Refresh.") != null }
        assertEquals(0, calls.get())
        assertNull(captured(scenario))
        assertNotNull(find({ it.isEditable && it.text?.toString() == "51.1" }))
        assertNull(find({ it.isEditable && it.text?.toString() == "-0.1" }))
        assertNull(node("◎ GPS"))
        assertNull(node("Latitude / longitude  ▾"))
    }

    @Test fun completedSnapshotRetainsExactEnvelopeWithoutReplayThroughRotation() = withScreen { scenario, _, calls ->
        clickRefresh()
        await { captured(scenario) != null }
        val original = captured(scenario)!!
        val full = OutputFormatter.format(original, ReturnMode.Json)
        assertEquals("succeeded", original.observations.single().values[DashboardFields.STATUS])
        scenario.recreate()
        await { captured(scenario) != null }
        assertSame(original, captured(scenario))
        assertEquals(full, OutputFormatter.format(captured(scenario)!!, ReturnMode.Json))
        assertEquals(1, calls.get())
    }

    @Test fun missingRetainedSnapshotRequiresExplicitAcquisitionNotReconstruction() = withScreen { scenario, _, calls ->
        clickRefresh()
        await { captured(scenario) != null }
        scenario.onActivity { activity ->
            val store = ViewModelProvider(activity)[RetainedExecutionStore::class.java]
            store.snapshot().keys.forEach(store::forget)
        }
        scenario.recreate()
        await { node("Snapshot unavailable. Refresh to acquire a new snapshot.") != null }
        assertNull(captured(scenario))
        assertEquals(1, calls.get())
        clickRefresh()
        await { captured(scenario) != null }
        assertEquals(2, calls.get())
    }

    @Test fun interruptedAcquisitionReleasesRefreshWithoutAutomaticReplayOrLateEvidence() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        try {
            withScreen(load = { _, _, _ ->
                started.countDown()
                check(release.await(30, TimeUnit.SECONDS))
                payload()
            }) { scenario, active, calls ->
                clickRefresh()
                assertTrue(started.await(8, TimeUnit.SECONDS))
                val previous = active.get()
                instrumentation.runOnMainSync { previous.recreate() }
                await { active.get() !== previous && node("Previous request was interrupted. Refresh to retry.") != null }
                assertTrue(button(refresh).isEnabled)
                assertEquals(1, calls.get())
                release.countDown()
                instrumentation.waitForIdleSync()
                assertNull(captured(scenario))
            }
        } finally { release.countDown() }
    }

    @Test fun snapshotUsesAcquisitionInputsNotEditsMadeWhileWaiting() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        try {
            withScreen(load = { _, lat, _ ->
                assertEquals(51.1, lat, 0.00001)
                started.countDown(); check(release.await(30, TimeUnit.SECONDS)); payload()
            }) { scenario, _, _ ->
                clickRefresh()
                assertTrue(started.await(8, TimeUnit.SECONDS))
                val field = find({ it.isEditable && it.text?.toString() == "51.1" })!!
                assertTrue(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,
                    Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "52.2") }))
                release.countDown()
                await { captured(scenario) != null }
                assertEquals("51.1", captured(scenario)!!.request.context["latitude"])
                scenario.recreate()
                await { find({ it.isEditable && it.text?.toString() == "52.2" }) != null }
                assertEquals("51.1", captured(scenario)!!.request.context["latitude"])
            }
        } finally { release.countDown() }
    }

    @Test fun failedProviderReleasesBusyStateAndExplicitRetrySucceeds() {
        val attempts = AtomicInteger()
        withScreen(load = { _, _, _ ->
            if (attempts.incrementAndGet() == 1) error("fixture provider unavailable")
            payload()
        }) { scenario, _, calls ->
            clickRefresh()
            await { node("Refresh failed: fixture provider unavailable") != null }
            assertNull(captured(scenario))
            assertTrue(button(refresh).isEnabled)
            clickRefresh()
            await { captured(scenario) != null }
            assertEquals(2, calls.get())
        }
    }

    @Test fun absentRetrievalMetadataIsNotReplacedByRefreshTime() = withScreen(load = { _, _, _ -> payload("") }) { scenario, _, _ ->
        clickRefresh()
        await { captured(scenario) != null }
        assertEquals("", captured(scenario)!!.request.context["retrieved_time_iso"])
        assertEquals("", captured(scenario)!!.observations.single().values[DashboardFields.RETRIEVED_TIME])
    }

    @Test fun planningRuntimeFieldsAreEditableWhileFixedLocationStaysHidden() =
        withScreen(runtimeFields = "hours_ahead,minimum_score") { scenario, _, calls ->
            assertNotNull(node("Plan ahead"))
            assertNotNull(node("Usable window threshold"))
            assertNull(find({ it.isEditable && it.text?.toString() == "51.1" }))
            assertNull(find({ it.isEditable && it.text?.toString() == "-0.1" }))
            assertEquals(0, calls.get())
            assertNull(captured(scenario))
        }

    @Test fun externalAutomaticReturnUsesTheExactCompletedSnapshot() {
        val delivered = AtomicReference<ExecutionResult>()
        val returns = AtomicInteger()
        withScreen(nativePreset = false, runtimeFields = "", onConfirmed = { delivered.set(it); returns.incrementAndGet() }) { scenario, _, calls ->
            await { delivered.get() != null }
            assertSame(captured(scenario), delivered.get())
            assertEquals("succeeded", delivered.get().observations.single().values[DashboardFields.STATUS])
            assertEquals(1, calls.get())
            assertEquals(1, returns.get())
        }
    }
}
