package com.example.methodmesh.modules.astronomy

import android.Manifest
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Actual three automatic Astronomy screens, with synthetic providers and no real GPS/network. */
class AstronomyAutoLifecycleTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext
    private val weather = """{"current":{"temperature_2m":17,"relative_humidity_2m":50,"cloud_cover":10,"wind_speed_10m":5,"visibility":30000}}"""
    private val forecast = """{"hourly":{"time":["2026-10-28T22:00:00Z"],"cloud_cover":[10],"wind_speed_10m":[5],"visibility":[30000]}}"""
    private fun payload() = AstronomyLivePayload(weather, "")
    private fun find(predicate: (AccessibilityNodeInfo) -> Boolean,
        root: AccessibilityNodeInfo? = instrumentation.uiAutomation.rootInActiveWindow): AccessibilityNodeInfo? {
        if (root == null) return null
        if (predicate(root)) return root
        for (i in 0 until root.childCount) find(predicate, root.getChild(i))?.let { return it }
        return null
    }
    private fun node(text: String) = find({ it.text?.toString() == text })
    private fun await(condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 12_000
        while (!condition()) {
            check(SystemClock.uptimeMillis() < end) { "Astronomy auto screen condition timed out" }
            instrumentation.waitForIdleSync(); SystemClock.sleep(25)
        }
    }
    private fun button(text: String): AccessibilityNodeInfo {
        var target = requireNotNull(node(text)) { "Missing button: " + text }
        while (!target.isClickable) target = requireNotNull(target.parent)
        return target
    }
    private fun click(text: String) {
        await { node(text) != null }
        assertTrue(button(text).isEnabled)
        assertTrue(button(text).performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }
    private fun captured(scenario: ActivityScenario<ExternalWorkflowActivity>): ExecutionResult? {
        val result = AtomicReference<ExecutionResult>()
        scenario.onActivity { result.set(ViewModelProvider(it)[RetainedExecutionStore::class.java]
            .snapshot().values.filterNotNull().singleOrNull()) }
        return result.get()
    }
    private fun withScreen(
        kind: AstronomyAutoKind = AstronomyAutoKind.Dew,
        settings: Map<String, String> = emptyMap(),
        runtimeFields: String = "latitude",
        automatic: Boolean = false,
        loadCurrent: (Context, Double, Double) -> AstronomyLivePayload = { _, _, _ -> payload() },
        loadHourly: (Context, Double, Double) -> String = { _, _, _ -> forecast },
        gps: suspend (Context) -> AstroLocation = { error("Unexpected GPS request") },
        onConfirmed: (ExecutionResult) -> Unit = {},
        run: (ActivityScenario<ExternalWorkflowActivity>, AtomicReference<ExternalWorkflowActivity>,
            AtomicInteger, AtomicReference<Map<String, String>>) -> Unit
    ) {
        val token = UUID.randomUUID().toString()
        val input = mapOf("location_source" to "manual", "latitude" to "51.1", "longitude" to "-0.1",
            "start_time_iso" to "2026-10-28T18:00:00Z", "methodmesh_finish_to_launcher" to "true") + settings
        val intent = Intent(app, ExternalWorkflowActivity::class.java).putExtra("method_id", kind.method.id)
            .putExtra("input_methodmesh_native_preset_run", (!automatic).toString())
            .putExtra("input_methodmesh_runtime_fields", runtimeFields)
        input.forEach { (key, value) -> intent.putExtra("input_" + key, value) }
        val request = AndroidIntentRequestReader.workflowRequest(intent)
        val changed = AtomicReference<Map<String, String>>(emptyMap())
        val context = CapabilityScreenContext(request.actions.single(), request, 1, 1,
            if (automatic) CapabilityCompletionMode.AutomaticReturn else CapabilityCompletionMode.ManualConfirmation,
            onSettingsChanged = changed::set)
        val active = AtomicReference<ExternalWorkflowActivity>()
        val calls = AtomicInteger()
        val callback = ActivityLifecycleCallback { activity, stage ->
            if (activity is ExternalWorkflowActivity && stage == Stage.CREATED &&
                activity.intent.getStringExtra("review_astro_auto") == token) {
                active.set(activity)
                activity.setContent { MethodMeshTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
                    AstronomyAutoScreen(kind, context, {}, onConfirmed, {},
                        { c, lat, lon -> calls.incrementAndGet(); loadCurrent(c, lat, lon) },
                        { c, lat, lon -> calls.incrementAndGet(); loadHourly(c, lat, lon) }, gps)
                } } }
            }
        }
        instrumentation.runOnMainSync { ActivityLifecycleMonitorRegistry.getInstance().addLifecycleCallback(callback) }
        try {
            ActivityScenario.launch<ExternalWorkflowActivity>(Intent(app, ExternalWorkflowActivity::class.java)
                .putExtra("review_astro_auto", token)).use { scenario ->
                await { active.get() != null && (node("Run") != null || captured(scenario) != null) }
                run(scenario, active, calls, changed)
            }
        } finally {
            instrumentation.runOnMainSync { ActivityLifecycleMonitorRegistry.getInstance().removeLifecycleCallback(callback) }
        }
    }

    @Test fun runtimeInputsWaitForRunAndEveryDeclaredAskFieldIsExposedWhileFixedFieldsAreHidden() {
        for (kind in AstronomyAutoKind.entries) {
            val runtime = if (kind == AstronomyAutoKind.Window) "hours_ahead" else "temperature_c"
            withScreen(kind, settings = mapOf("source_mode" to "manual"), runtimeFields = runtime) { scenario, _, calls, _ ->
                await { node("Enter runtime inputs, then Run.") != null }
                assertEquals(0, calls.get()); assertNull(captured(scenario))
                assertNull(find({ it.isEditable && it.text?.toString() == "-0.1" }))
                val default = if (kind == AstronomyAutoKind.Window) "14" else "10.0"
                assertNotNull(find({ it.isEditable && it.text?.toString() == default }))
                click("Run"); await { captured(scenario) != null }
            }
        }
    }

    @Test fun manualAndUpstreamWeatherNeedNeitherGpsNorLiveProviders() {
        for (kind in listOf(AstronomyAutoKind.Conditions, AstronomyAutoKind.Dew)) {
            for (input in listOf(mapOf("source_mode" to "manual"),
                mapOf("previous_weather_json" to weather, "temperature_c" to "10", "relative_humidity_pct" to "70"))) {
                withScreen(kind, settings = input, runtimeFields = "") { scenario, _, calls, _ ->
                    await { captured(scenario) != null }
                    assertEquals(0, calls.get())
                    val record = captured(scenario)!!
                    assertTrue(record.observations.single().values.values.any { it == "succeeded" })
                    if (input.containsKey("previous_weather_json")) {
                        assertEquals(weather, record.request.context["weather_json"])
                        assertNull(record.request.context["temperature_c"])
                    }
                }
            }
        }
    }

    @Test fun upstreamHumidityAliasOverridesPresetDefaultInTheCanonicalDewRecord() {
        withScreen(settings = mapOf("previous_temperature_c" to "18", "previous_humidity_pct" to "45",
            "relative_humidity_pct" to "70"), runtimeFields = "") { scenario, _, calls, _ ->
            await { captured(scenario) != null }
            assertEquals("45", captured(scenario)!!.request.context["relative_humidity_pct"])
            assertEquals("18", captured(scenario)!!.request.context["temperature_c"])
            assertEquals(0, calls.get())
        }
    }

    @Test fun allThreeScreensRetainTheExactOriginalAndFullEnvelopeThroughRotation() {
        for (kind in AstronomyAutoKind.entries) {
            withScreen(kind) { scenario, _, calls, _ ->
                click("Run"); await { captured(scenario) != null }
                val original = captured(scenario)!!
                val full = OutputFormatter.format(original, ReturnMode.Json, payloadMode = OutputFormatter.PayloadMode.FULL)
                scenario.recreate(); await { captured(scenario) != null }
                assertSame(original, captured(scenario))
                assertEquals(full, OutputFormatter.format(captured(scenario)!!, ReturnMode.Json,
                    payloadMode = OutputFormatter.PayloadMode.FULL))
                assertEquals(1, calls.get())
            }
        }
    }

    @Test fun missingOriginalRequiresExplicitNewAcquisitionWithoutReconstructingEvidence() {
        withScreen { scenario, _, calls, _ ->
            click("Run"); await { captured(scenario) != null }
            scenario.onActivity { activity ->
                val store = ViewModelProvider(activity)[RetainedExecutionStore::class.java]
                store.snapshot().keys.forEach(store::forget)
            }
            scenario.recreate(); await { node("Captured result unavailable") != null }
            assertEquals(1, calls.get()); assertNull(captured(scenario))
            click("Start new run"); await { captured(scenario) != null }
            assertEquals(2, calls.get())
        }
    }

    @Test fun interruptedCurrentAndHourlyRequestsReleaseRunWithoutReplayOrFallbackEvidence() {
        for (kind in listOf(AstronomyAutoKind.Dew, AstronomyAutoKind.Window)) {
            val started = CountDownLatch(1); val release = CountDownLatch(1); val returned = CountDownLatch(1)
            fun block() { started.countDown(); check(release.await(30, TimeUnit.SECONDS)); returned.countDown() }
            try {
                withScreen(kind, loadCurrent = { _, _, _ -> block(); payload() },
                    loadHourly = { _, _, _ -> block(); forecast }) { scenario, active, calls, _ ->
                    click("Run"); assertTrue(started.await(8, TimeUnit.SECONDS))
                    assertEquals(1, calls.get())
                    await { node("Working…") != null }
                    assertFalse(button("Working…").isEnabled)
                    val old = active.get()
                    instrumentation.runOnMainSync { old.recreate() }
                    await { active.get() !== old && node("Previous request was interrupted. Run to retry.") != null }
                    assertTrue(button("Run").isEnabled); assertEquals(1, calls.get())
                    release.countDown(); assertTrue(returned.await(8, TimeUnit.SECONDS))
                    instrumentation.waitForIdleSync(); assertNull(captured(scenario))
                }
            } finally { release.countDown() }
        }
    }

    @Test fun editsDuringFetchSurviveButTheResultUsesStartingInputsAndFetchedJsonIsNotSavedToPreset() {
        val started = CountDownLatch(1); val release = CountDownLatch(1)
        try {
            withScreen(loadCurrent = { _, lat, _ ->
                assertEquals(51.1, lat, 0.00001); started.countDown()
                check(release.await(30, TimeUnit.SECONDS)); payload()
            }) { scenario, _, _, changed ->
                click("Run"); assertTrue(started.await(8, TimeUnit.SECONDS))
                val field = requireNotNull(find({ it.isEditable && it.text?.toString() == "51.1" }))
                assertTrue(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "52.2")
                }))
                release.countDown(); await { captured(scenario) != null }
                assertEquals("51.1", captured(scenario)!!.request.context["latitude"])
                assertEquals(weather, captured(scenario)!!.request.context["weather_json"])
                assertEquals("52.2", changed.get()["latitude"])
                assertNull(changed.get()["weather_json"])
                scenario.recreate(); click("Retry")
                await { find({ it.isEditable && it.text?.toString() == "52.2" }) != null }
            }
        } finally { release.countDown() }
    }

    @Test fun cancelledHourlyProviderCannotBecomeAnAstronomyOnlySuccess() {
        withScreen(AstronomyAutoKind.Window, loadHourly = { _, _, _ -> throw CancellationException("fixture cancelled") }) {
            scenario, _, calls, _ ->
            click("Run"); await { node("Request cancelled. Run to retry.") != null }
            assertNull(captured(scenario)); assertEquals(1, calls.get()); assertTrue(button("Run").isEnabled)
        }
    }

    @Test fun ordinaryForecastFailureCreatesAnExplicitAstronomyOnlyPlanWithWarningInOriginalAudit() {
        withScreen(AstronomyAutoKind.Window, loadHourly = { _, _, _ -> error("fixture offline") }) { scenario, _, _, _ ->
            click("Run"); await { captured(scenario) != null }
            val record = captured(scenario)!!
            assertEquals("astronomy_only", record.request.context["data_source"])
            assertTrue(record.request.context.getValue("forecast_warning").contains("Astronomy-only"))
            assertTrue(record.observations.single().values.getValue(ImagingWindowFields.AUDIT).contains("fixture offline"))
            await { find({ it.text?.toString()?.contains(record.request.context.getValue("forecast_warning")) == true }) != null }
        }
    }

    @Test fun suppliedForecastSkipsProviderAndManualSiteWinsOverStalePlusCode() {
        withScreen(AstronomyAutoKind.Window, settings = mapOf("forecast_json" to forecast, "plus_code" to "stale"),
            runtimeFields = "") { scenario, _, calls, changed ->
            await { captured(scenario) != null }
            val record = captured(scenario)!!
            assertEquals(0, calls.get()); assertTrue(changed.get().isEmpty())
            assertEquals("supplied_forecast", record.request.context["data_source"])
            assertEquals("", record.request.context["plus_code"])
            assertEquals("51.1", record.request.context["latitude"])
        }
    }

    @Test fun automaticCompletionDeliversOriginalExactlyOnceEvenWhenTheScreenIsRecreated() {
        val confirmations = AtomicInteger(); val delivered = AtomicReference<ExecutionResult>()
        withScreen(automatic = true, runtimeFields = "", onConfirmed = { confirmations.incrementAndGet(); delivered.set(it) }) {
            scenario, _, calls, _ ->
            await { confirmations.get() == 1 }
            assertSame(captured(scenario), delivered.get())
            val original = delivered.get()
            scenario.recreate(); await { captured(scenario) != null }
            instrumentation.waitForIdleSync()
            assertSame(original, captured(scenario)); assertEquals(1, confirmations.get()); assertEquals(1, calls.get())
        }
    }

    @Test fun cancelThenRetryCannotBeOverwrittenByAnOlderBlockedProvider() {
        val started = CountDownLatch(1); val release = CountDownLatch(1); val returned = CountDownLatch(1)
        val attempts = AtomicInteger()
        try {
            withScreen(loadCurrent = { _, _, _ ->
                if (attempts.incrementAndGet() == 1) {
                    started.countDown(); check(release.await(30, TimeUnit.SECONDS)); returned.countDown()
                }
                payload()
            }) { scenario, _, calls, _ ->
                click("Run"); assertTrue(started.await(8, TimeUnit.SECONDS))
                click("Cancel request"); click("Run"); await { captured(scenario) != null }
                val retry = captured(scenario)!!
                assertEquals(2, calls.get())
                release.countDown(); assertTrue(returned.await(8, TimeUnit.SECONDS))
                instrumentation.waitForIdleSync(); assertSame(retry, captured(scenario))
            }
        } finally { release.countDown() }
    }

    @Test fun leavingDuringGpsCancelsTheLocationAndNeverStartsTheWeatherProvider() {
        instrumentation.uiAutomation.grantRuntimePermission(app.packageName, Manifest.permission.ACCESS_FINE_LOCATION)
        val started = CountDownLatch(1); val cancelled = CountDownLatch(1)
        withScreen(settings = mapOf("location_source" to "auto", "latitude" to "0", "longitude" to "0"), gps = {
            started.countDown()
            try { awaitCancellation() } finally { cancelled.countDown() }
        }) { scenario, active, calls, _ ->
            click("Run"); assertTrue(started.await(8, TimeUnit.SECONDS))
            val old = active.get()
            instrumentation.runOnMainSync { old.recreate() }
            assertTrue(cancelled.await(8, TimeUnit.SECONDS))
            await { active.get() !== old && node("Previous request was interrupted. Run to retry.") != null }
            assertEquals(0, calls.get()); assertNull(captured(scenario)); assertTrue(button("Run").isEnabled)
        }
    }

    @Test fun incompleteUpstreamMeasurementShowsTheCanonicalErrorAndCanBeRetriedExplicitly() {
        withScreen(settings = mapOf("source_mode" to "upstream"), runtimeFields = "") { scenario, _, calls, _ ->
            await { captured(scenario) != null }
            val record = captured(scenario)!!
            assertEquals("failed", record.observations.single().values[DewFields.STATUS])
            val message = record.observations.single().values.getValue(DewFields.ERROR)
            await { node(message) != null }
            assertEquals(0, calls.get())
            click("Retry"); await { node("Run") != null }
            assertNull(captured(scenario))
        }
    }
}
