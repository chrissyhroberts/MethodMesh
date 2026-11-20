package com.example.methodmesh.transport.android

import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.TransformationStatus
import com.example.methodmesh.core.onlinedata.*
import com.example.methodmesh.modules.apiget.ApiGetFields
import com.example.methodmesh.modules.apiget.As100ApiGetMethod
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.ReturnMode
import com.example.methodmesh.transport.workflow.ui.RetainedExecutionStore
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/** Cached fixtures or a blocked loopback TLS handshake; no live provider traffic. */
class ApiGetLifecycleTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext
    private fun definition(url: String = "https://fixture.invalid/never-called", cache: CachePolicy = CachePolicy.ttl(900)) =
        ApiDefinition("test.api.${UUID.randomUUID()}", "Local API fixture", urlTemplate = url,
            inputs = listOf(ApiInputDefinition("note", "Fixture note", required = false)),
            response = ApiResponseDefinition(expectedPaths = listOf("value")), cache = cache)
    private fun intent(id: String) = Intent(app, ExternalWorkflowActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        .putExtra("method_id", As100ApiGetMethod.ID).putExtra("input_methodmesh_native_preset_run", "true")
        .putExtra("input_definition_id", id).putExtra("input_result_path", "value")
    private fun withDefinition(definition: ApiDefinition, action: () -> Unit) {
        ApiDefinitionRepository.initialise(app)
        ApiDefinitionRepository.saveUserDefinition(app, definition)
        try { action() } finally { ApiDefinitionRepository.removeUserDefinition(app, definition.id) }
    }
    private fun captured(scenario: ActivityScenario<ExternalWorkflowActivity>): ExecutionResult? {
        val value = AtomicReference<ExecutionResult>()
        scenario.onActivity { value.set(ViewModelProvider(it)[RetainedExecutionStore::class.java].snapshot().values.filterNotNull().singleOrNull()) }
        return value.get()
    }
    private fun await(condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 10_000
        while (!condition()) {
            check(SystemClock.uptimeMillis() < end) { "API lifecycle fixture timed out" }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(25)
        }
    }
    private fun node(text: String, root: AccessibilityNodeInfo? = instrumentation.uiAutomation.rootInActiveWindow): AccessibilityNodeInfo? {
        if (root == null) return null
        if (root.text?.toString() == text) return root
        for (index in 0 until root.childCount) node(text, root.getChild(index))?.let { return it }
        return null
    }

    @Test fun cachedExecutionRetainsItsIdentityAndHistoricalEvidenceThroughRotation() {
        val definition = definition()
        val now = Instant.now()
        val cached = ApiExecutionResult(definitionId = definition.id, definitionVersion = definition.version,
            status = OnlineExecutionStatus.SUCCESS, meta = ApiResultMeta(now, now),
            data = ResultTree.objectNode("value" to ResultTree.number(123.4)))
        val key = ApiGetExecutor(InMemoryApiDefinitionRegistry(listOf(definition)), object : OnlineHttpClient {
            override fun get(request: OnlineHttpRequest): OnlineHttpResponse = error("Fixture must stay offline")
        }).prepare(definition, emptyMap()).cacheKey
        SharedApiResultCache.save(key, cached, CacheRecord(apiDefinitionId = definition.id, apiDefinitionVersion = definition.version,
            resultId = cached.resultId, retrievedAt = now, freshUntil = now.plusSeconds(900)))
        withDefinition(definition) {
            ActivityScenario.launch<ExternalWorkflowActivity>(intent(definition.id)).use { scenario ->
                await { captured(scenario) != null && node("Result") != null }
                val original = captured(scenario)!!
                assertEquals("true", OutputFormatter.fields(original)[ApiGetFields.FROM_CACHE])
                val json = OutputFormatter.format(original, ReturnMode.Json)
                scenario.recreate()
                await { node("Result") != null }
                assertSame(original, captured(scenario))
                assertEquals(json, OutputFormatter.format(captured(scenario)!!, ReturnMode.Json))
            }
        }
    }

    @Test fun rotatingAnInFlightRequestAllowsExplicitRetryWithoutAnImplicitSecondRequest() {
        ServerSocket(0, 10, InetAddress.getByName("127.0.0.1")).use { server ->
            val started = CountDownLatch(1)
            val release = CountDownLatch(1)
            val client = AtomicReference<Socket>()
            val worker = thread(isDaemon = true) {
                runCatching { server.accept().use { socket ->
                    client.set(socket)
                    socket.getInputStream().read() // TLS ClientHello, never an external request.
                    started.countDown()
                    release.await(10, TimeUnit.SECONDS)
                } }
            }
            try {
                val definition = definition("https://127.0.0.1:${server.localPort}/fixture", CachePolicy.noStore())
                withDefinition(definition) {
                    ActivityScenario.launch<ExternalWorkflowActivity>(intent(definition.id)).use { scenario ->
                        assertTrue(started.await(8, TimeUnit.SECONDS))
                        scenario.recreate()
                        await { node("Previous request was interrupted. Run API to retry.") != null }
                        var retry = node("Run API")!!
                        while (!retry.isClickable) retry = retry.parent!!
                        assertTrue(retry.isEnabled)
                        assertNull(captured(scenario))
                        server.soTimeout = 400
                        try { server.accept().close(); fail("Rotation replayed the HTTP request") } catch (_: SocketTimeoutException) { }
                    }
                }
            } finally { release.countDown(); client.get()?.close(); worker.join(1000) }
        }
    }

    @Test fun unknownFixedDefinitionFailsLocallyInsteadOfChoosingAnotherProvider() {
        val missing = "test.missing.${UUID.randomUUID()}"
        ActivityScenario.launch<ExternalWorkflowActivity>(intent(missing)).use { scenario ->
            await { captured(scenario) != null }
            val result = captured(scenario)!!
            assertEquals(TransformationStatus.Failed, result.status)
            val fields = OutputFormatter.fields(result)
            assertEquals(missing, fields[ApiGetFields.DEFINITION_ID])
            assertEquals("API definition was not found.", fields[ApiGetFields.ERROR])
            assertEquals("", fields[ApiGetFields.RETRIEVED_TIME_ISO])
        }
    }

    @Test fun nativePresetRuntimeInputsDoNotTriggerAnAutomaticProviderRequest() {
        ServerSocket(0, 10, InetAddress.getByName("127.0.0.1")).use { server ->
            val definition = definition("https://127.0.0.1:${server.localPort}/fixture", CachePolicy.noStore())
            withDefinition(definition) {
                ActivityScenario.launch<ExternalWorkflowActivity>(intent(definition.id).putExtra("input_methodmesh_runtime_fields", "note")).use { scenario ->
                    await { node("Run API") != null }
                    instrumentation.waitForIdleSync()
                    assertNull(captured(scenario))
                    server.soTimeout = 400
                    try { server.accept().close(); fail("Preset skipped its Ask when run input gate") } catch (_: SocketTimeoutException) { }
                }
            }
        }
    }
}
