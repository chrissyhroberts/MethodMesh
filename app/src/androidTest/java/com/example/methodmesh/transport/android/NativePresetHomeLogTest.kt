package com.example.methodmesh.transport.android

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.SystemClock
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.intent.IntentStubber
import androidx.test.runner.intent.IntentStubberRegistry
import com.example.methodmesh.MainActivity
import com.example.methodmesh.core.artifacts.AndroidArtifacts
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.modules.randomnumber.As100RandomNumberMethod
import com.example.methodmesh.modules.conversions.As100ConversionsMethod
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.ReturnMode
import com.example.methodmesh.transport.workflow.ui.RetainedExecutionStore
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Intercept Home: no dashboard/service launch. Confirmation and logging must precede navigation. */
class NativePresetHomeLogTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext
    private fun freshRoot(): AccessibilityNodeInfo? {
        if (Build.VERSION.SDK_INT >= 34) instrumentation.uiAutomation.clearCache()
        return instrumentation.uiAutomation.rootInActiveWindow
    }
    private fun node(text: String, root: AccessibilityNodeInfo? = freshRoot()): AccessibilityNodeInfo? {
        if (root == null) return null
        if (root.text?.toString() == text) return root
        for (index in 0 until root.childCount) node(text, root.getChild(index))?.let { return it }
        return null
    }
    private fun scrollable(root: AccessibilityNodeInfo? = freshRoot()): AccessibilityNodeInfo? {
        if (root == null) return null
        if (root.isScrollable) return root
        for (index in 0 until root.childCount) scrollable(root.getChild(index))?.let { return it }
        return null
    }
    private fun await(condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 10_000
        while (!condition()) {
            check(SystemClock.uptimeMillis() < end) { "Native Home log fixture timed out" }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(25)
        }
    }
    private fun click(text: String) {
        await { if (node(text) == null) { scrollable()?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD); false } else true }
        var button = node(text)!!
        while (!button.isClickable) button = requireNotNull(button.parent)
        button.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
        instrumentation.waitForIdleSync()
        assertTrue(button.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }
    private fun captured(scenario: ActivityScenario<ExternalWorkflowActivity>): ExecutionResult? {
        val value = AtomicReference<ExecutionResult>()
        scenario.onActivity { value.set(ViewModelProvider(it)[RetainedExecutionStore::class.java].snapshot().values.filterNotNull().singleOrNull()) }
        return value.get()
    }
    private fun verify(id: String, commit: String?, finish: String, missingLog: Boolean = false) {
        if (id.startsWith("weather.")) ProviderScreenLifecycleTest.cacheWeather()
        val service = AndroidArtifacts.service(app)
        val log = service.createPersistent("Review Home ${UUID.randomUUID()}", "application/x-ndjson", byteArrayOf().inputStream())
        val summary = service.createPersistent("Review Home summary", "text/plain", byteArrayOf().inputStream())
        val state = AtomicReference<ExternalWorkflowViewModel>()
        val original = AtomicReference<ExecutionResult>()
        val failure = AtomicReference<Throwable>()
        val navigated = CountDownLatch(1)
        IntentStubberRegistry.load(object : IntentStubber {
            override fun getActivityResultForIntent(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.component?.className != MainActivity::class.java.name) return null
                runCatching {
                    val confirmed = state.get().progress.confirmed.single().result
                    if (id == "barcode.generate") {
                        assertEquals("Closeout barcode fixture", confirmed.observations.first().values["barcode_payload"])
                    } else {
                        assertEquals(original.get().request.id.value, confirmed.request.id.value)
                        assertEquals(OutputFormatter.format(original.get(), ReturnMode.Json), OutputFormatter.format(confirmed, ReturnMode.Json))
                    }
                    assertTrue(state.get().resultReturned)
                    assertTrue(intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TASK != 0)
                    if (missingLog) {
                        assertTrue(state.get().logWarning()!!.contains("Captured result returned unchanged"))
                    } else {
                        val entry = service.open(log).bufferedReader().use { it.readLines() }.filter { it.isNotBlank() }.map(::JSONObject).single()
                        assertEquals(confirmed.request.id.value, entry.getString("entry_id"))
                        assertEquals(OutputFormatter.format(confirmed, ReturnMode.Json), entry.getString("result_json"))
                        assertTrue(service.open(summary).bufferedReader().use { it.readText() }.isNotBlank())
                    }
                }.onFailure(failure::set)
                navigated.countDown()
                return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
            }
        })
        try {
            val incoming = Intent(app, ExternalWorkflowActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                .putExtra("method_id", id).putExtra("input_methodmesh_native_preset_run", "true")
                .putExtra("input_methodmesh_log_ref", if (missingLog) "missing-review-log-${UUID.randomUUID()}" else log.id)
                .putExtra("input_methodmesh_log_summary_ref", summary.id)
                .putExtra("input_seed_mode", "fixed_seed").putExtra("input_seed", "native-home-review")
                .putExtra("input_category", "length").putExtra("input_value", "12").putExtra("input_from_unit", "m").putExtra("input_to_unit", "cm")
                .putExtra("input_operation", "cidr").putExtra("input_cidr", "192.168.10.0/24")
                .putExtra("input_latitude", "51.1").putExtra("input_longitude", "-0.1").putExtra("input_offline_only", "true")
                .putExtra("input_barcode_payload", "Closeout barcode fixture").putExtra("input_barcode_auto_cycle", "false")
            ActivityScenario.launch<ExternalWorkflowActivity>(incoming).use { scenario ->
                if (id == "compass.read") await { com.example.methodmesh.platform.sensors.PhoneSensorRepository.headingDegrees != null }
                if (commit != null) click(commit)
                if (id != "barcode.generate") {
                    await { captured(scenario) != null }
                    original.set(captured(scenario))
                }
                scenario.onActivity { state.set(ViewModelProvider(it)[ExternalWorkflowViewModel::class.java]) }
                click(finish)
                assertTrue("Native Home was not requested", navigated.await(10, TimeUnit.SECONDS))
                failure.get()?.let { throw AssertionError("Home bypassed confirmation or logging", it) }
            }
        } finally {
            IntentStubberRegistry.reset()
            service.collection(log.id).forEach { service.delete(it.ref) }
            service.delete(log)
            service.delete(summary)
        }
    }
    @Test fun sharedScaffoldHomeConfirmsAndLogsBeforeNavigation() = verify(As100RandomNumberMethod.ID, null, "Home")
    @Test fun conversionsDoneConfirmsAndLogsBeforeNavigation() = verify(As100ConversionsMethod.ID, "COMMIT", "DONE")
    @Test fun weatherDoneConfirmsAndLogsBeforeNavigation() = verify("weather.conditions", "Commit result", "Done")
    @Test fun networkDoneConfirmsAndLogsBeforeNavigation() = verify("network.tools", "Commit", "Done")
    @Test fun nativeHomeStillConfirmsWithAnExplicitWarningWhenOptInLogFails() = verify(As100RandomNumberMethod.ID, null, "Home", true)
    @Test fun compassDoneConfirmsAndLogsBeforeNavigation() = verify("compass.read", "Commit reading", "Done")
    @Test fun barcodeGeneratorHomeConfirmsAndLogsBeforeNavigation() = verify("barcode.generate", "Commit", "Home")
}
