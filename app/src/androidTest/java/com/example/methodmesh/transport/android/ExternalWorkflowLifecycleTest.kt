package com.example.methodmesh.transport.android

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.intent.IntentCallback
import androidx.test.runner.intent.IntentMonitorRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.example.methodmesh.core.methodmesh.*
import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.core.methodmesh.runtime.As100MethodRegistry
import com.example.methodmesh.settings.SettingsState
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** No hardware or network: the fixture counts canonical executions at the host boundary. */
class ExternalWorkflowLifecycleTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private var original = emptyList<As100Method>()
    private val executions = AtomicInteger()
    private val fixture = object : As100Method {
        override val id = "test.workflow_lifecycle"
        override val ref = ArchitectureRef(ArchitectureId(id), "Method")
        override val descriptor = MethodDescriptor(ArchitectureId(id), MethodObjectType.Calculation, "Lifecycle fixture", "1")
        override val contract = MethodContract(ref)
        override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) =
            ExecutionRequest(action = action, method = ref, context = context, signals = signals, inputs = inputs)
        override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult {
            val count = executions.incrementAndGet()
            return ExecutionResult(request, TransformationStatus.Succeeded, observations = listOf(
                Observation(phenomenon = "test", values = mapOf("answer" to count.toString()), provenance = ProvenanceContext("test"))
            ))
        }
    }

    @Before fun installFixture() {
        original = As100MethodRegistry.all()
        As100MethodRegistry.install(original + fixture)
    }

    @After fun restoreRegistry() { As100MethodRegistry.install(original) }

    private fun intent() = Intent(
        instrumentation.targetContext,
        ExternalWorkflowActivity::class.java
    )

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (!condition()) {
            check(SystemClock.uptimeMillis() < deadline) { "Workflow condition timed out" }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(20)
        }
    }

    private fun nodeWithText(text: String, node: AccessibilityNodeInfo? = instrumentation.uiAutomation.rootInActiveWindow): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.text?.toString() == text || node.contentDescription?.toString() == text) return node
        for (index in 0 until node.childCount) {
            nodeWithText(text, node.getChild(index))?.let { return it }
        }
        return null
    }

    private fun hasText(text: String): Boolean = nodeWithText(text) != null

    private fun click(text: String) {
        await { hasText(text) }
        var node = requireNotNull(nodeWithText(text))
        while (!node.isClickable) node = requireNotNull(node.parent)
        assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }

    @Test fun completedStepAndNextIntroSurviveRecreation() {
        val intent = intent().putExtra("ril", "WHAT; execute ${fixture.id}; execute ${fixture.id}; RESULT; format json")
        assertEquals(2, AndroidIntentRequestReader.workflowRequest(intent).actions.size)
        ActivityScenario.launch<ExternalWorkflowActivity>(intent).use { scenario ->
            click("Go")
            await { executions.get() == 1 && hasText("Step 2 of 2") }
            scenario.recreate()
            await { hasText("Step 2 of 2") }
            assertEquals(1, executions.get())
            scenario.onActivity {
                val state = ViewModelProvider(it)[ExternalWorkflowViewModel::class.java]
                assertFalse(state.interrupted)
                assertEquals(1, state.progress.confirmed.size)
                assertEquals(1, state.progress.index)
            }
        }
    }

    @Test fun genericCaptureIsNotRepeatedWhenManualPresetScreenRecreates() {
        val intent = intent().putExtra("method_id", fixture.id)
            .putExtra("input_methodmesh_native_preset_run", "true")
        ActivityScenario.launch<ExternalWorkflowActivity>(intent).use { scenario ->
            await { executions.get() == 1 }
            scenario.recreate()
            instrumentation.waitForIdleSync()
            assertEquals(1, executions.get())
            scenario.onActivity {
                val state = ViewModelProvider(it)[ExternalWorkflowViewModel::class.java]
                assertFalse(state.interrupted)
                assertEquals(1, state.genericResults.size)
                assertTrue(state.progress.confirmed.isEmpty())
            }
        }
    }

    @Test fun browserRouterRecreationKeepsOneChildWorkflowAndResultChannel() {
        val childStarts = AtomicInteger()
        val routerCreations = AtomicInteger()
        val routerDestructions = AtomicInteger()
        val router = AtomicReference<IntentRouterActivity>()
        val child = AtomicReference<ExternalWorkflowActivity>()
        val intentCallback = IntentCallback { launched ->
            if (launched.component?.className == ExternalWorkflowActivity::class.java.name) childStarts.incrementAndGet()
        }
        val lifecycleCallback = ActivityLifecycleCallback { activity, stage ->
            if (activity is IntentRouterActivity && stage == Stage.DESTROYED) routerDestructions.incrementAndGet()
            if (stage == Stage.CREATED) {
                if (activity is IntentRouterActivity) {
                    router.set(activity)
                    routerCreations.incrementAndGet()
                }
                if (activity is ExternalWorkflowActivity) child.set(activity)
            }
        }
        instrumentation.runOnMainSync {
            IntentMonitorRegistry.getInstance().addIntentCallback(intentCallback)
            ActivityLifecycleMonitorRegistry.getInstance().addLifecycleCallback(lifecycleCallback)
        }
        val incoming = Intent(instrumentation.targetContext, IntentRouterActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setData(Uri.parse("methodmesh://run"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("method_id", fixture.id)
            .putExtra("input_methodmesh_native_preset_run", "true")
        try {
            // Launch as an external browser task. ActivityScenario's task
            // ownership is incompatible with the router deliberately backing
            // and finishing that transient task after the child returns.
            instrumentation.runOnMainSync { instrumentation.targetContext.startActivity(incoming) }
            await { executions.get() == 1 }
            assertEquals(1, childStarts.get())
            instrumentation.runOnMainSync { router.get().recreate() }
            // Android defers recreating a covered router until the child
            // returns. Restore it through the real pending-result path.
            await { routerDestructions.get() == 1 }
            instrumentation.runOnMainSync { child.get().finish() }
            await { routerCreations.get() == 2 }
            await { routerDestructions.get() == 2 }
            assertEquals("router replayed its child launch", 1, childStarts.get())
            assertEquals(1, executions.get())
        } finally {
            instrumentation.runOnMainSync {
                child.get()?.finish()
                router.get()?.finish()
                IntentMonitorRegistry.getInstance().removeIntentCallback(intentCallback)
                ActivityLifecycleMonitorRegistry.getInstance().removeLifecycleCallback(lifecycleCallback)
            }
        }
    }
}
