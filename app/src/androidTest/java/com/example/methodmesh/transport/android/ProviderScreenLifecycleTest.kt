package com.example.methodmesh.transport.android

import android.content.Intent
import android.os.SystemClock
import android.os.Bundle
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
import com.example.methodmesh.core.onlinedata.*
import com.example.methodmesh.modules.networktools.As100NetworkToolsMethod
import com.example.methodmesh.modules.weather.*
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.ReturnMode
import com.example.methodmesh.transport.workflow.ui.*
import com.example.methodmesh.ui.theme.MethodMeshTheme
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Real screens with cached Weather and local-only CIDR; blocked fixtures never contact a provider. */
class ProviderScreenLifecycleTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext
    private fun incoming(id: String, dashboard: Boolean = false) = Intent(app, ExternalWorkflowActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        .putExtra("method_id", id).putExtra("input_methodmesh_native_preset_run", "true")
        .apply { if (dashboard) putExtra("input_methodmesh_dashboard", "true") }
    private fun weather(id: String) = incoming(id).putExtra("input_latitude", "51.1").putExtra("input_longitude", "-0.1").putExtra("input_offline_only", "true")
    private fun network(dashboard: Boolean) = incoming(As100NetworkToolsMethod.ID, dashboard)
        .putExtra("input_operation", "cidr").putExtra("input_cidr", "192.168.10.0/24")
    private fun await(condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 12_000
        while (!condition()) {
            check(SystemClock.uptimeMillis() < end) { "Provider-screen condition timed out. Visible UI: ${visibleText()}" }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(25)
        }
    }
    private fun visibleText(root: AccessibilityNodeInfo? = instrumentation.uiAutomation.rootInActiveWindow): String {
        if (root == null) return ""
        return buildString {
            root.text?.let { append(it).append(" | ") }
            for (index in 0 until root.childCount) append(visibleText(root.getChild(index)))
        }.take(1500)
    }
    private fun find(predicate: (AccessibilityNodeInfo) -> Boolean, root: AccessibilityNodeInfo? = instrumentation.uiAutomation.rootInActiveWindow): AccessibilityNodeInfo? {
        if (root == null) return null
        if (predicate(root)) return root
        for (index in 0 until root.childCount) find(predicate, root.getChild(index))?.let { return it }
        return null
    }
    private fun node(text: String) = find({ it.text?.toString() == text })
    private fun scroll(forward: Boolean) = find({ it.isScrollable })?.performAction(
        if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) == true
    private fun click(text: String) {
        await {
            if (node(text) == null) { scroll(true); false } else true
        }
        var target = requireNotNull(node(text))
        while (!target.isClickable) target = requireNotNull(target.parent)
        target.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
        instrumentation.waitForIdleSync()
        target = requireNotNull(node(text))
        while (!target.isClickable) target = requireNotNull(target.parent)
        assertTrue("Disabled button: $text", target.isEnabled)
        assertTrue(target.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
    }
    private fun captured(scenario: ActivityScenario<ExternalWorkflowActivity>): ExecutionResult? {
        val value = AtomicReference<ExecutionResult>()
        scenario.onActivity { value.set(ViewModelProvider(it)[RetainedExecutionStore::class.java].snapshot().values.filterNotNull().singleOrNull()) }
        return value.get()
    }
    private fun forget(scenario: ActivityScenario<ExternalWorkflowActivity>) {
        scenario.onActivity { activity ->
            val store = ViewModelProvider(activity)[RetainedExecutionStore::class.java]
            store.snapshot().keys.forEach(store::forget)
        }
        scenario.recreate()
    }
    companion object {
    internal fun cacheWeather() {
        val now = Instant.now()
        val time = now.truncatedTo(ChronoUnit.HOURS)
        val root = JSONObject().put("latitude", 51.1).put("longitude", -0.1)
            .put("current", JSONObject().put("time", time.toString()).put("temperature_2m", 17.0).put("weather_code", 0))
            .put("hourly", JSONObject().put("time", JSONArray((0..3).map { time.plusSeconds(it * 3600L).toString() }))
                .put("temperature_2m", JSONArray(listOf(17, 18, 19, 20))).put("precipitation", JSONArray(listOf(0, 0, 0, 0))))
        val locations = mapOf("latitude" to "51.1", "longitude" to "-0.1")
        fun seed(definition: ApiDefinition, inputs: Map<String, String>, data: JSONObject) {
            val key = ApiGetExecutor(WeatherApiDefinitions.registry, object : OnlineHttpClient {
                override fun get(request: OnlineHttpRequest): OnlineHttpResponse = error("No live requests in this fixture")
            }).prepare(definition, inputs).cacheKey
            val response = ApiExecutionResult(definitionId = definition.id, definitionVersion = definition.version,
                status = OnlineExecutionStatus.SUCCESS, meta = ApiResultMeta(now.minusSeconds(60), now.minusSeconds(60)), data = ResultTree.fromJsonObject(data))
            SharedApiResultCache.save(key, response, CacheRecord(apiDefinitionId = definition.id, apiDefinitionVersion = definition.version,
                resultId = response.resultId, retrievedAt = now.minusSeconds(60), freshUntil = now.plusSeconds(3600)))
        }
        listOf("1", "10").forEach { days -> seed(WeatherApiDefinitions.forecast, locations + ("forecast_days" to days), root) }
        seed(WeatherApiDefinitions.atmosphere, locations + ("forecast_hours" to "24"), root)
        seed(WeatherApiDefinitions.ensemble, locations + ("forecast_hours" to "48"), root)
        seed(WeatherApiDefinitions.radar, emptyMap(), JSONObject().put("radar", JSONObject().put("past", JSONArray()).put("nowcast", JSONArray())))
    }
    }
    private fun verifyWeather(dashboard: Boolean, lose: Boolean) {
        cacheWeather()
        val id = if (dashboard) "weather.dashboard" else "weather.conditions"
        ActivityScenario.launch<ExternalWorkflowActivity>(weather(id)).use { scenario ->
            click(if (dashboard) "Commit dashboard snapshot" else "Commit result")
            await { captured(scenario) != null }
            val original = captured(scenario)!!
            assertEquals("51.1", original.request.context["latitude"])
            assertEquals("true", OutputFormatter.fields(original)["${id.replace('.', '_')}_from_cache"])
            assertFalse("Do not duplicate the provider body into saved input settings", original.request.context.containsKey("weather_json"))
            val json = OutputFormatter.format(original, ReturnMode.Json)
            if (lose) {
                forget(scenario)
                await { captured(scenario) == null }
                repeat(15) { scroll(false) }
                await { find({ it.text?.toString()?.startsWith("Committed result unavailable.") == true }) != null }
                assertNull(captured(scenario))
            } else {
                scenario.recreate()
                await { captured(scenario) != null }
                assertSame(original, captured(scenario))
                assertEquals(json, OutputFormatter.format(captured(scenario)!!, ReturnMode.Json))
            }
        }
    }
    private fun verifyNetwork(dashboard: Boolean, lose: Boolean) {
        ActivityScenario.launch<ExternalWorkflowActivity>(network(dashboard)).use { scenario ->
            if (dashboard) click("Calculate")
            click("Commit")
            await { captured(scenario) != null }
            val original = captured(scenario)!!
            assertEquals("192.168.10.0/24", original.request.context["cidr"])
            val json = OutputFormatter.format(original, ReturnMode.Json)
            if (lose) {
                forget(scenario)
                await { captured(scenario) == null }
                repeat(15) { scroll(false) }
                // The dashboard's recovery message follows the live connection/Wi-Fi cards.
                // Accessibility omits it while it is off screen; search down from the top.
                await {
                    if (find({ it.text?.toString()?.startsWith("Committed result unavailable.") == true }) != null) true
                    else { scroll(true); false }
                }
            } else {
                scenario.recreate()
                await { captured(scenario) != null }
                assertSame(original, captured(scenario))
                assertEquals(json, OutputFormatter.format(captured(scenario)!!, ReturnMode.Json))
            }
        }
    }

    @Test fun weatherToolCommitRetainsExactExecutionAndHistoricalEvidence() = verifyWeather(false, false)
    @Test fun weatherDashboardCommitRetainsExactExecutionAndHistoricalEvidence() = verifyWeather(true, false)
    @Test fun weatherToolDoesNotRebuildLostCommittedEvidence() = verifyWeather(false, true)
    @Test fun weatherDashboardDoesNotRebuildLostCommittedEvidence() = verifyWeather(true, true)
    @Test fun networkFocusedCommitRetainsExactExecutionAndHistoricalEvidence() = verifyNetwork(false, false)
    @Test fun networkDashboardCommitRetainsExactExecutionAndHistoricalEvidence() = verifyNetwork(true, false)
    @Test fun networkFocusedDoesNotRebuildLostCommittedEvidence() = verifyNetwork(false, true)
    @Test fun networkDashboardDoesNotRebuildLostCommittedEvidence() = verifyNetwork(true, true)

    private fun interruptedWeather(dashboard: Boolean, editBeforeCompletion: Boolean = false) {
        val token = UUID.randomUUID().toString()
        val incoming = Intent(app, ExternalWorkflowActivity::class.java).putExtra("review_harness", token)
        val request = AndroidIntentRequestReader.workflowRequest(weather(if (dashboard) "weather.dashboard" else "weather.conditions")
            .putExtra("input_methodmesh_runtime_fields", "latitude"))
        val screenContext = CapabilityScreenContext(request.actions.single(), request, 1, 1, CapabilityCompletionMode.ManualConfirmation)
        val started = CountDownLatch(if (dashboard) 3 else 1)
        val release = CountDownLatch(1)
        val calls = AtomicInteger()
        val active = AtomicReference<ExternalWorkflowActivity>()
        val callback = ActivityLifecycleCallback { activity, stage ->
            if (activity is ExternalWorkflowActivity && stage == Stage.CREATED && activity.intent.getStringExtra("review_harness") == token) {
                active.set(activity)
                activity.setContent { MethodMeshTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
                    val capture: (WeatherMethodBase, Map<String, String>) -> WeatherRuntimeCapture = { method, settings ->
                        calls.incrementAndGet(); started.countDown()
                        check(release.await(30, TimeUnit.SECONDS)) { "Blocked fixture was not released" }
                        WeatherRuntimeCapture(settings, method.calculate(settings + ("weather_json" to
                            """{"current":{"time":"2026-10-21T11:00:00Z","temperature_2m":17,"weather_code":0}}""")))
                    }
                    if (dashboard) WeatherDashboardScreen(screenContext, {}, {}, {}, capture)
                    else WeatherToolScreen(As100WeatherConditionsMethod, screenContext, {}, {}, {}, capture)
                } } }
            }
        }
        instrumentation.runOnMainSync { ActivityLifecycleMonitorRegistry.getInstance().addLifecycleCallback(callback) }
        try {
            ActivityScenario.launch<ExternalWorkflowActivity>(incoming).use { scenario ->
                await { if (node("Refresh") == null) { scroll(true); false } else true }
                var refreshButton = node("Refresh")!!
                while (!refreshButton.isClickable) refreshButton = requireNotNull(refreshButton.parent)
                assertTrue(refreshButton.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                assertTrue(started.await(8, TimeUnit.SECONDS))
                if (editBeforeCompletion) {
                    await { if (find({ it.isEditable && it.text?.toString() == "51.1" }) == null) { scroll(true); false } else true }
                    assertNull("A runtime latitude must not expose a fixed longitude", find({ it.isEditable && it.text?.toString() == "-0.1" }))
                    assertTrue(find({ it.isEditable && it.text?.toString() == "51.1" })!!.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,
                        Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "52.2") }))
                    release.countDown()
                    click("Commit result")
                    await { captured(scenario) != null }
                    val committed = captured(scenario)!!
                    assertEquals("51.1", committed.request.context["latitude"])
                    assertEquals("51.1", OutputFormatter.fields(committed)["weather_conditions_requested_latitude"])
                    return@use
                }
                val expected = calls.get()
                val previous = active.get()
                // Do not wait for the deliberately blocked request to idle before
                // asking Android to recreate the activity under test.
                instrumentation.runOnMainSync { previous.recreate() }
                await { active.get() !== previous }
                repeat(10) { scroll(false) }
                await { find({ it.text?.toString() == "Previous request was interrupted. Refresh to retry." }) != null }
                await { if (node("Refresh") == null) { scroll(true); false } else true }
                val refresh = node("Refresh")!!
                var button = refresh
                while (!button.isClickable) button = requireNotNull(button.parent)
                assertTrue(button.isEnabled)
                assertEquals("Rotation replayed acquisition", expected, calls.get())
                assertNull(captured(scenario))
            }
        } finally {
            release.countDown()
            instrumentation.runOnMainSync { ActivityLifecycleMonitorRegistry.getInstance().removeLifecycleCallback(callback) }
        }
    }
    @Test fun interruptedWeatherToolCanRetryWithoutAutomaticReplay() = interruptedWeather(false)
    @Test fun interruptedWeatherDashboardCanRetryWithoutAutomaticReplay() = interruptedWeather(true)
    @Test fun weatherCommitUsesCapturedInputsNotEditsMadeWhileWaitingAndKeepsFixedFieldsHidden() = interruptedWeather(false, true)

    @Test fun networkCommitUsesTheInputsThatProducedTheWorkingResultNotUnrunEdits() {
        ActivityScenario.launch<ExternalWorkflowActivity>(network(false).putExtra("input_methodmesh_runtime_fields", "cidr")).use { scenario ->
            click("Calculate CIDR")
            await { node("Commit") != null }
            repeat(10) { scroll(false) }
            val field = find({ it.isEditable && it.text?.toString() == "192.168.10.0/24" })!!
            assertTrue(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,
                Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "10.10.0.0/16") }))
            click("Commit")
            await { captured(scenario) != null }
            assertEquals("192.168.10.0/24", captured(scenario)!!.request.context["cidr"])
        }
    }
}
