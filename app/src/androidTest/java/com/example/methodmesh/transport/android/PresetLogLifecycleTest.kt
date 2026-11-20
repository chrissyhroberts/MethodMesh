package com.example.methodmesh.transport.android

import android.app.Activity
import android.content.Intent
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.example.methodmesh.core.artifacts.AndroidArtifacts
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.modules.randomnumber.As100RandomNumberMethod
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.ReturnMode
import com.example.methodmesh.transport.workflow.ui.RetainedExecutionStore
import kotlinx.coroutines.CompletableDeferred
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class PresetLogLifecycleTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext
    private fun incoming() = Intent(app, ExternalWorkflowActivity::class.java)
        .putExtra("method_id", As100RandomNumberMethod.ID).putExtra("input_methodmesh_native_preset_run", "true")
        .putExtra("input_seed_mode", "fixed_seed").putExtra("input_seed", "log-lifecycle")
    private fun await(condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 10_000
        while (!condition()) {
            check(SystemClock.uptimeMillis() < end) { "Log lifecycle timed out" }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(25)
        }
    }
    private fun captured(scenario: ActivityScenario<ExternalWorkflowActivity>): ExecutionResult? {
        val result = AtomicReference<ExecutionResult>()
        scenario.onActivity { result.set(ViewModelProvider(it)[RetainedExecutionStore::class.java].snapshot().values.filterNotNull().singleOrNull()) }
        return result.get()
    }

    @Test fun optInLogDoesNotPersistAnUnconfirmedCaptureOnRotationOrCancel() {
        val service = AndroidArtifacts.service(app)
        val log = service.createPersistent("Review unconfirmed log", "application/x-ndjson", byteArrayOf().inputStream())
        try {
            ActivityScenario.launch<ExternalWorkflowActivity>(incoming().putExtra("input_methodmesh_log_ref", log.id)).use { scenario ->
                await { captured(scenario) != null }
                val result = captured(scenario)!!
                assertEquals("", service.open(log).bufferedReader().use { it.readText() })
                scenario.recreate()
                await { captured(scenario) != null }
                assertSame(result, captured(scenario))
                assertEquals("", service.open(log).bufferedReader().use { it.readText() })
            }
            assertEquals("", service.open(log).bufferedReader().use { it.readText() })
            assertTrue(service.collection(log.id).isEmpty())
        } finally { service.delete(log) }
    }

    @Test fun pendingConfirmationSurvivesRotationAndIgnoresDuplicateDeliveryWithoutChangingEvidence() {
        // This case verifies caller delivery. Actual Home closeout is covered separately.
        val incoming = incoming().putExtra("input_methodmesh_finish_to_launcher", "true")
        val deferred = CompletableDeferred<String?>()
        val writes = AtomicInteger()
        ActivityScenario.launchActivityForResult<ExternalWorkflowActivity>(incoming).use { scenario ->
            await { captured(scenario) != null }
            val result = captured(scenario)!!
            val full = OutputFormatter.format(result, ReturnMode.Json)
            val request = AndroidIntentRequestReader.workflowRequest(incoming)
            val retained = AtomicReference<ExternalWorkflowViewModel>()
            scenario.onActivity { activity ->
                val state = ViewModelProvider(activity)[ExternalWorkflowViewModel::class.java]
                retained.set(state)
                val key = state.progress.stepKey
                repeat(2) { state.confirmStep(key, request.actions.single(), request, result) { writes.incrementAndGet(); deferred.await() } }
            }
            assertEquals(1, writes.get())
            scenario.recreate()
            scenario.onActivity { assertSame(retained.get(), ViewModelProvider(it)[ExternalWorkflowViewModel::class.java]) }
            deferred.complete("Persistent log could not be saved completely. Captured result returned unchanged.")
            val returned = scenario.result
            assertEquals(Activity.RESULT_OK, returned.resultCode)
            assertTrue(returned.resultData.getStringExtra("methodmesh_log_warning")!!.contains("Captured result returned unchanged"))
            val returnedFull = returned.resultData.getStringExtra("methodmesh_full_json")!!
            assertEquals(result.request.id.value, JSONObject(returnedFull).getString("methodmesh_execution_id"))
            assertEquals(full, returnedFull)
            assertEquals(1, writes.get())
        }
    }
}
