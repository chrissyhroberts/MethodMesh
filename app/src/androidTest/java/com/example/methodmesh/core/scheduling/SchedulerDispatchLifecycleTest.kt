package com.example.methodmesh.core.scheduling

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.intent.IntentCallback
import androidx.test.runner.intent.IntentMonitorRegistry
import androidx.test.runner.intent.IntentStubber
import androidx.test.runner.intent.IntentStubberRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.example.methodmesh.core.methodmesh.*
import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.core.protocols.*
import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.MethodMeshModuleRegistry
import com.example.methodmesh.settings.SettingsState
import com.example.methodmesh.transport.android.IntentRouterActivity
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.ZonedDateTime
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Isolated emulator fixtures: no card/device operations, form servers or real alarms. */
class SchedulerDispatchLifecycleTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val runId = "review-${UUID.randomUUID()}"
    private var originalModules = emptyList<MethodMeshModule>()
    private val activities = CopyOnWriteArrayList<Activity>()
    private val parent = AtomicReference<Activity>()
    private val parentCreations = AtomicInteger()
    private val routerStarts = AtomicInteger()
    private val webStarts = AtomicInteger()
    private val executions = AtomicInteger()
    private val status = AtomicReference(TransformationStatus.Succeeded)
    private val schedules = mutableListOf<String>()
    private val presets = mutableListOf<String>()
    private val protocols = mutableListOf<String>()
    private val instancePlanIds = mutableListOf<String>()
    private val preferenceKeys = mutableListOf<String>()

    private val method = object : As100Method {
        override val id = "test.scheduler_lifecycle"
        override val ref = ArchitectureRef(ArchitectureId(id), "Method")
        override val descriptor = MethodDescriptor(ArchitectureId(id), MethodObjectType.Calculation, "Dispatch fixture", "1")
        override val contract = MethodContract(ref)
        override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) =
            ExecutionRequest(action = action, method = ref, context = context, signals = signals, inputs = inputs)
        override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?) = ExecutionResult(
            request = request, status = status.get(), observations = listOf(Observation(
                phenomenon = "test.fixture", values = mapOf("answer" to executions.incrementAndGet().toString()),
                provenance = ProvenanceContext("test")
            ))
        )
    }
    private val screen = object : CapabilityScreenSpec {
        override val capabilityId = method.id
        override val title = "Dispatch fixture"
        override val description = "Controlled lifecycle test"
        @Composable override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
            Button(onClick = { onConfirmed(method.execute(method.request())) }) { Text("Complete fixture") }
        }
    }
    private val module = object : MethodMeshModule {
        override val moduleId = "test_dispatch_fixture"
        override val displayName = "Dispatch fixture"
        override fun as100Methods() = listOf(method)
        override fun capabilityScreens() = listOf(screen)
    }
    private val intentCallback = IntentCallback {
        if (it.component?.className == IntentRouterActivity::class.java.name) routerStarts.incrementAndGet()
        if (it.action == Intent.ACTION_VIEW && it.dataString == "https://example.invalid/methodmesh-lifecycle-fixture") webStarts.incrementAndGet()
    }
    private val lifecycle = ActivityLifecycleCallback { activity, stage ->
        if (stage == Stage.CREATED) {
            activities += activity
            if (activity is SchedulerDispatchActivity || activity is SchedulePlanDispatchActivity) {
                parent.set(activity)
                parentCreations.incrementAndGet()
            }
        }
    }

    @Before fun setUp() {
        originalModules = MethodMeshModuleRegistry.all()
        MethodMeshModuleRegistry.install(originalModules + module)
        instrumentation.runOnMainSync {
            IntentMonitorRegistry.getInstance().addIntentCallback(intentCallback)
            ActivityLifecycleMonitorRegistry.getInstance().addLifecycleCallback(lifecycle)
        }
    }

    @After fun tearDown() {
        instrumentation.runOnMainSync {
            activities.reversed().forEach { if (!it.isFinishing && !it.isDestroyed) it.finish() }
            IntentMonitorRegistry.getInstance().removeIntentCallback(intentCallback)
            ActivityLifecycleMonitorRegistry.getInstance().removeLifecycleCallback(lifecycle)
        }
        instrumentation.waitForIdleSync()
        if (IntentStubberRegistry.isLoaded()) IntentStubberRegistry.reset()
        schedules.forEach { SchedulerRepository.remove(context, it) }
        presets.forEach { ProtocolLibraryRepository.removePreset(context, it) }
        protocols.forEach { ProtocolLibraryRepository.removeProtocol(context, it) }
        instancePlanIds.forEach { SchedulePlanStore.removeInstancesForPlan(context, it) }
        context.getSharedPreferences("methodmesh_scheduler", Context.MODE_PRIVATE).edit().also { editor ->
            preferenceKeys.forEach(editor::remove)
        }.apply()
        MethodMeshModuleRegistry.install(originalModules)
    }

    private fun await(condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 15_000
        while (!condition()) {
            check(SystemClock.uptimeMillis() < end) { "Dispatch condition timed out; executions=${executions.get()}, routers=${routerStarts.get()}, parents=${parentCreations.get()}" }
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
    private fun click(text: String) {
        await { node(text) != null }
        var target = requireNotNull(node(text))
        while (!target.isClickable) target = requireNotNull(target.parent)
        assertTrue(target.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }
    private fun launch(intent: Intent) {
        instrumentation.runOnMainSync {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK))
        }
    }
    private fun preset(suffix: String = "one"): CapabilityPreset = ProtocolLibraryRepository.savePreset(context, CapabilityPreset(
        id = "$runId-preset-$suffix", name = "Lifecycle fixture $suffix", methodId = method.id
    )).also { presets += it.id }

    @Test fun chainRecreationKeepsOneChildAndPreservesFirstStepContextAndRunIdentity() {
        val schedule = ResearchSchedule(id = "$runId-schedule", name = "Fixture chain", target = SchedulerTarget.CAPABILITY,
            targetValue = method.id, chainId = "$runId-chain", frequency = SchedulerFrequency.DAILY, hour = 0, minute = 0, enabled = false)
        schedules += schedule.id
        SchedulerRepository.save(context, schedule)
        val actualContextKey = "schedule_run_context_${schedule.chainId}"
        preferenceKeys += actualContextKey
        preferenceKeys += listOf("schedule_chain_folder_${schedule.chainId}", "schedule_chain_submission_${schedule.chainId}", "schedule_chain_name_${schedule.chainId}", "scheduler_clipboard_${schedule.chainId}")
        launch(Intent(context, SchedulerDispatchActivity::class.java).putExtra("schedule_id", schedule.id))
        await { node("Go") != null }
        val prefs = context.getSharedPreferences("methodmesh_scheduler", Context.MODE_PRIVATE)
        val submission = prefs.getString("schedule_chain_submission_${schedule.chainId}", null)
        assertNotNull(submission)
        prefs.edit().putString(actualContextKey, "{\"marker\":\"preserved\"}").apply()
        click("Go")
        await { node("Complete fixture") != null }
        instrumentation.runOnMainSync { parent.get().recreate() }
        click("Complete fixture")
        await { node("Run result") != null && parentCreations.get() == 2 }
        assertEquals(1, routerStarts.get())
        assertEquals(1, executions.get())
        assertEquals(submission, prefs.getString("schedule_chain_submission_${schedule.chainId}", null))
        assertEquals("preserved", JSONObject(prefs.getString(actualContextKey, "{}")!!).getString("marker"))
        assertEquals(1, SchedulerRepository.events(context, schedule.id).count { it.event.startsWith("dispatch_started:") })
    }

    @Test fun schedulePlanRecreationDoesNotRelaunchAndFailedExecutionCannotCompleteOccurrence() {
        val preset = preset()
        val planId = "$runId-plan"
        val instanceId = "$runId-instance"
        val occurrence = ScheduleOccurrence(instanceId = instanceId, laneId = "fixture", laneName = "Fixture",
            scheduledAt = ZonedDateTime.now(), actions = listOf(ScheduleAction(type = ScheduleActionType.PRESET, presetId = preset.id)))
        instancePlanIds += planId
        SchedulePlanStore.saveInstance(context, ScheduleInstance(id = instanceId, planId = planId, planVersion = 1,
            anchoredAt = ZonedDateTime.now(), occurrences = listOf(occurrence)))
        launch(Intent(context, SchedulePlanDispatchActivity::class.java).putExtra("instance_id", instanceId).putExtra("occurrence_id", occurrence.id))
        await { node("Complete fixture") != null }
        val started = SchedulePlanStore.instance(context, instanceId)!!.occurrences.single().actionExecutions.single().startedAt
        status.set(TransformationStatus.Failed)
        instrumentation.runOnMainSync { parent.get().recreate() }
        click("Complete fixture")
        await { SchedulePlanStore.instance(context, instanceId)!!.occurrences.single().state == ScheduleOccurrenceState.FAILED }
        val saved = SchedulePlanStore.instance(context, instanceId)!!.occurrences.single()
        assertEquals(ScheduleActionExecutionState.FAILED, saved.actionExecutions.single().state)
        assertEquals(started, saved.actionExecutions.single().startedAt)
        assertEquals(1, executions.get())
        assertEquals(1, routerStarts.get())
    }

    @Test fun successfulTwoStepProtocolSurvivesParentRecreationAndCompletesEachStepOnce() {
        val one = preset("one")
        val two = preset("two")
        val protocol = ProtocolLibraryRepository.saveProtocol(context, ProtocolDefinition(id = "$runId-protocol", name = "Fixture protocol",
            steps = listOf(ProtocolStep(name = "First", presetId = one.id, order = 0), ProtocolStep(name = "Second", presetId = two.id, order = 1))))
        protocols += protocol.id
        launch(Intent(context, SchedulerDispatchActivity::class.java).putExtra("protocol_id", protocol.id)
            .putExtra("transient_protocol_run", true).putExtra("suppress_output", true))
        click("Go")
        await { node("Complete fixture") != null }
        instrumentation.runOnMainSync { parent.get().recreate() }
        click("Complete fixture")
        await { executions.get() == 1 && node("Go") != null }
        click("Go")
        click("Complete fixture")
        await { node("Run result") != null }
        assertEquals(2, executions.get())
        assertEquals(2, routerStarts.get())
    }

    @Test fun manualFormPromptCancelAndSummarySurviveRecreationWithoutReopeningTheForm() {
        IntentStubberRegistry.load(object : IntentStubber {
            override fun getActivityResultForIntent(intent: Intent): Instrumentation.ActivityResult? =
                if (intent.dataString == "https://example.invalid/methodmesh-lifecycle-fixture") Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null) else null
        })
        val schedule = ResearchSchedule(id = "$runId-web", name = "Fixture web form", target = SchedulerTarget.WEB_FORM,
            targetValue = "https://example.invalid/methodmesh-lifecycle-fixture", frequency = SchedulerFrequency.DAILY, hour = 0, minute = 0, enabled = false)
        schedules += schedule.id
        preferenceKeys += "schedule_run_context_${schedule.id}"
        SchedulerRepository.save(context, schedule)
        assertNotNull("Fixture schedule must be stored", SchedulerRepository.get(context, schedule.id))
        launch(Intent(context, SchedulerDispatchActivity::class.java).putExtra("schedule_id", schedule.id))
        click("Go")
        await { node("Confirm step") != null }
        instrumentation.runOnMainSync { parent.get().recreate() }
        await { node("Confirm step") != null && parentCreations.get() == 2 }
        assertEquals(1, webStarts.get())
        click("Cancel run")
        instrumentation.runOnMainSync { parent.get().recreate() }
        await { node("Cancel run?") != null }
        click("Keep going")
        await { node("Confirm step") != null }
        assertEquals(1, webStarts.get())
        click("No, try again")
        await { webStarts.get() == 2 && node("Confirm step") != null }
        click("Yes, completed")
        await { node("Run result") != null }
        instrumentation.runOnMainSync { parent.get().recreate() }
        await { node("Run result") != null && parentCreations.get() == 4 }
        assertEquals(2, webStarts.get())
        assertEquals(1, SchedulerRepository.events(context, schedule.id).count { it.event.startsWith("dispatch_started:") })
    }

    @Test fun failedFirstProtocolExecutionDoesNotPipeItsResultOrLaunchTheNextStep() {
        val one = preset("failure-one")
        val two = preset("failure-two")
        val protocol = ProtocolLibraryRepository.saveProtocol(context, ProtocolDefinition(id = "$runId-failed-protocol", name = "Failure fixture protocol",
            steps = listOf(ProtocolStep(name = "First", presetId = one.id, order = 0), ProtocolStep(name = "Second", presetId = two.id, order = 1))))
        protocols += protocol.id
        launch(Intent(context, SchedulerDispatchActivity::class.java).putExtra("protocol_id", protocol.id)
            .putExtra("transient_protocol_run", true).putExtra("suppress_output", true))
        click("Go")
        await { node("Complete fixture") != null }
        val current = parent.get()
        val submission = current.intent.getStringExtra("protocol_submission_id")!!
        val contextKey = "protocol_run_context_$submission"
        preferenceKeys += contextKey
        status.set(TransformationStatus.Failed)
        click("Complete fixture")
        await { current.isDestroyed }
        assertEquals(1, executions.get())
        assertEquals(1, routerStarts.get())
        assertEquals(2, parentCreations.get()) // protocol launcher plus its first step
        assertTrue(SchedulerRepository.events(context, "__protocol_run_${protocol.id}").any { it.event == "failed" })
        assertFalse(JSONObject(context.getSharedPreferences("methodmesh_scheduler", Context.MODE_PRIVATE)
            .getString(contextKey, "{}")!!).has("answer"))
    }
}
