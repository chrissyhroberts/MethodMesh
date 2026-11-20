package com.example.methodmesh.modules.astronomy

import android.content.*
import android.os.Bundle
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** Actual local screens/repository; private fixture preferences and controlled chooser/faults. */
class AstronomyLocalWorkflowTest {
    private var currentFixture = ""
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext
    private fun root(): AccessibilityNodeInfo? {
        if (Build.VERSION.SDK_INT >= 34) instrumentation.uiAutomation.clearCache()
        return instrumentation.uiAutomation.rootInActiveWindow
    }
    private fun find(predicate: (AccessibilityNodeInfo) -> Boolean,
        root: AccessibilityNodeInfo? = root()): AccessibilityNodeInfo? {
        if (root == null) return null
        if (predicate(root)) return root
        for (i in 0 until root.childCount) find(predicate, root.getChild(i))?.let { return it }
        return null
    }
    private fun text(label: String) = find({ it.text?.toString() == label })
    private fun await(condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 12_000
        while (!condition()) {
            check(SystemClock.uptimeMillis() < end) { "Astronomy local workflow timed out: " + currentFixture + " / " + visibleText() }
            instrumentation.waitForIdleSync(); SystemClock.sleep(25)
        }
    }
    private fun visibleText(): String {
        val values = mutableListOf<String>()
        fun collect(node: AccessibilityNodeInfo?) {
            if (node == null) return
            node.text?.toString()?.let { values += it.take(200) }
            for (i in 0 until node.childCount) collect(node.getChild(i))
        }
        collect(instrumentation.uiAutomation.rootInActiveWindow)
        return values.joinToString(" | ").take(3000)
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
        var value = requireNotNull(text(label)) { label }
        while (!value.isClickable) value = requireNotNull(value.parent)
        return value
    }
    private fun click(label: String) {
        show(label)
        button(label).performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
        instrumentation.waitForIdleSync()
        await { button(label).isEnabled && button(label).isVisibleToUser }
        assertTrue(button(label).performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
    }
    private fun status(prefix: String) {
        repeat(4) { find({ it.isScrollable })?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) }
        await {
            if (find({ it.text?.toString()?.startsWith(prefix) == true }) != null) true else {
                find({ it.isScrollable })?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                SystemClock.sleep(60); false
            }
        }
    }
    private fun full(result: ExecutionResult) = OutputFormatter.format(result, ReturnMode.Json, true,
        payloadMode = OutputFormatter.PayloadMode.FULL)

    private inner class Evidence(val kind: String = "session") : AutoCloseable {
        val token = UUID.randomUUID().toString()
        val method get() = when (kind) { "exposure" -> As100ExposureLimitMethod; "scale" -> As100ImageScaleMethod; else -> As100SessionMethod }
        val prefs = app.getSharedPreferences("astronomy_local_test_" + token, 0)
        val failNext = AtomicBoolean(false)
        val blockFinish = AtomicBoolean(false)
        val denyChooser = AtomicBoolean(false)
        val entered = CountDownLatch(1); val release = CountDownLatch(1); val completedWrite = CountDownLatch(1)
        val now = AtomicLong(1_800_000_000_000)
        val transactions = CopyOnWriteArrayList<Set<String>>()
        val returned = CopyOnWriteArrayList<ExecutionResult>()
        val choosers = CopyOnWriteArrayList<Intent>()
        val active = AtomicReference<ExternalWorkflowActivity>()
        val guardedPrefs = object : SharedPreferences by prefs {
            override fun edit(): SharedPreferences.Editor {
                val base = prefs.edit()
                val keys = mutableSetOf<String>()
                var removesActive = false
                return object : SharedPreferences.Editor by base {
                    override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                        if (key != null) keys += key
                        base.putString(key, value); return this
                    }
                    override fun remove(key: String?): SharedPreferences.Editor {
                        if (key != null) keys += key
                        if (key == "active_session") removesActive = true
                        base.remove(key); return this
                    }
                    override fun commit(): Boolean {
                        assertNotEquals("Session writes must run off the screen thread", Looper.getMainLooper(), Looper.myLooper())
                        if (removesActive && blockFinish.compareAndSet(true, false)) {
                            entered.countDown(); check(release.await(25, TimeUnit.SECONDS))
                        }
                        val failed = failNext.compareAndSet(true, false)
                        val committed = base.commit()
                        if (committed) transactions += keys.toSet()
                        if (removesActive) completedWrite.countDown()
                        // Deliberately simulate a failure after memory mutation, to exercise rollback.
                        return committed && !failed
                    }
                    override fun apply() { error("Unchecked session persistence") }
                }
            }
        }
        val storage = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String?, mode: Int) = guardedPrefs
        }
        val repo = AstronomyRepository(storage, now::get)
        fun history(): JSONArray = JSONArray(prefs.getString("sessions", "[]"))
        override fun close() {
            release.countDown()
            if (entered.count == 0L) completedWrite.await(8, TimeUnit.SECONDS)
            prefs.edit().clear().commit()
        }
    }

    private fun result(e: Evidence, scenario: ActivityScenario<ExternalWorkflowActivity>): ExecutionResult? {
        val value = AtomicReference<ExecutionResult>()
        scenario.onActivity {
            value.set(if (e.kind == "session") ViewModelProvider(it)[AstronomySessionStore::class.java]
                .snapshot().values.mapNotNull { run -> run.result }.singleOrNull()
            else ViewModelProvider(it)[RetainedExecutionStore::class.java].snapshot().values.filterNotNull().singleOrNull())
        }
        return value.get()
    }
    private fun fixture(e: Evidence, automatic: Boolean = false, runtime: String = if (e.kind == "session") "target" else "focal_length_mm",
        history: Boolean? = null, action: String = "", run: (ActivityScenario<ExternalWorkflowActivity>) -> Unit) {
        currentFixture = e.kind + " action=" + action
        val intent = Intent().putExtra("method_id", e.method.id)
            .putExtra("input_methodmesh_native_preset_run", "true")
            .putExtra("input_methodmesh_runtime_fields", runtime)
            .putExtra("input_methodmesh_finish_to_launcher", "true")
            .putExtra("input_methodmesh_preset_result_action", action)
            .putExtra("input_target", "M31").putExtra("input_equipment", "Fixed fixture telescope")
        if (history != null) intent.putExtra("input_save_session", history.toString())
        val request = AndroidIntentRequestReader.workflowRequest(intent)
        val context = CapabilityScreenContext(request.actions.single(), request, 1, 1,
            if (automatic) CapabilityCompletionMode.AutomaticReturn else CapabilityCompletionMode.ManualConfirmation)
        val callback = ActivityLifecycleCallback { activity, stage ->
            if (activity is ExternalWorkflowActivity && stage == Stage.CREATED &&
                activity.intent.getStringExtra("local_astronomy_fixture") == e.token) {
                e.active.set(activity)
                val guarded = object : ContextWrapper(activity) {
                    override fun startActivity(intent: Intent) {
                        if (e.denyChooser.get()) throw ActivityNotFoundException("Fixture chooser denied")
                        assertEquals(Intent.ACTION_CHOOSER, intent.action); e.choosers += intent
                    }
                }
                activity.setContent { MethodMeshTheme { CompositionLocalProvider(LocalContext provides guarded) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        when (e.kind) {
                            "exposure" -> AstronomyExposureScreen(context, {}, e.returned::add, {})
                            "scale" -> ImageScaleCapabilityScreen.Render(context, {}, e.returned::add, {})
                            else -> AstronomySessionScreen(context, {}, e.returned::add, {}, repository = { e.repo })
                        }
                    }
                } } }
            }
        }
        instrumentation.runOnMainSync { ActivityLifecycleMonitorRegistry.getInstance().addLifecycleCallback(callback) }
        try {
            ActivityScenario.launch<ExternalWorkflowActivity>(Intent(app, ExternalWorkflowActivity::class.java)
                .putExtra("local_astronomy_fixture", e.token)).use { scenario ->
                if (!automatic) show(if (e.kind == "session") "Reload unfinished session" else "Calculate")
                run(scenario)
            }
        } finally {
            instrumentation.runOnMainSync { ActivityLifecycleMonitorRegistry.getInstance().removeLifecycleCallback(callback) }
        }
    }
    private fun complete(e: Evidence, scenario: ActivityScenario<ExternalWorkflowActivity>) {
        if (e.kind == "session") {
            click("Start session"); show("Stop"); e.now.addAndGet(5000); click("Stop")
        } else click("Calculate")
        await { result(e, scenario) != null }
    }

    @Test fun calculatorsWaitForAskedInputsAndHideFixedValues() {
        for (kind in listOf("exposure", "scale")) Evidence(kind).use { e -> fixture(e) { scenario ->
            assertNull(result(e, scenario))
            assertNotNull(find({ it.isEditable }))
            assertNull(text("Fixed fixture telescope"))
            assertNull(find({ it.isEditable && it.text?.toString() == if (kind == "scale") "3.76" else "4.0" }))
            complete(e, scenario)
        } }
    }

    @Test fun allThreeCompletedResultsKeepExactOriginalFullAndDoneThroughRotation() {
        for (kind in listOf("exposure", "scale", "session")) Evidence(kind).use { e -> fixture(e, history = true) { scenario ->
            complete(e, scenario); val original = result(e, scenario)!!; val json = full(original)
            scenario.recreate(); await { result(e, scenario) != null }
            assertSame(original, result(e, scenario)); assertEquals(json, full(result(e, scenario)!!))
            click("Done"); await { e.returned.size == 1 }; assertSame(original, e.returned.single())
            if (kind == "session") assertEquals(1, e.history().length())
        } }
    }

    @Test fun missingOriginalRequiresExplicitRecoveryWithoutRecalculationOrSessionReplay() {
        for (kind in listOf("exposure", "scale", "session")) Evidence(kind).use { e -> fixture(e) { scenario ->
            complete(e, scenario)
            scenario.onActivity { activity ->
                if (kind == "session") {
                    val store = ViewModelProvider(activity)[AstronomySessionStore::class.java]
                    store.snapshot().keys.forEach(store::forget)
                } else {
                    val store = ViewModelProvider(activity)[RetainedExecutionStore::class.java]
                    store.snapshot().keys.forEach(store::forget)
                }
            }
            scenario.recreate(); show("Captured result unavailable")
            assertNull(result(e, scenario)); assertTrue(e.returned.isEmpty())
        } }
    }

    @Test fun nonFiniteImageScaleTextDoesNotCrashTheSliderOrProduceAResult() = Evidence("scale").use { e ->
        fixture(e) { scenario ->
            val field = requireNotNull(find({ it.isEditable }))
            assertTrue(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "NaN")
            }))
            click("Calculate"); status("Enter a valid focal length")
            assertNull(result(e, scenario))
        }
    }

    @Test fun activeSessionAndEditsRestoreWithoutPresetDefaultsOverwritingThem() = Evidence().use { e ->
        e.repo.startSession("Existing target", "Existing telescope", "Existing note")
        fixture(e, runtime = "target,equipment,notes") { scenario ->
            show("Existing target"); show("Existing note")
            scenario.recreate(); show("Existing target"); show("Existing note")
            assertEquals("Existing target", e.repo.loadActiveSession()!!.target)
            assertEquals("Existing telescope", e.repo.loadActiveSession()!!.equipment)
        }
    }

    @Test fun failedStopRestoresActiveDataAndDoesNotConfirmOrDuplicateHistoryOnRetry() = Evidence().use { e ->
        fixture(e, history = true) { scenario ->
            click("Start session"); show("Stop")
            val previous = e.prefs.getString("active_session", null)
            e.failNext.set(true); click("Stop"); status("Stop/save failed:")
            assertEquals(previous, e.prefs.getString("active_session", null))
            assertEquals(0, e.history().length()); assertNull(result(e, scenario)); assertTrue(e.returned.isEmpty())
            scenario.recreate(); status("Stop/save failed:")
            click("Stop"); await { result(e, scenario) != null }
            assertNull(e.repo.loadActiveSession()); assertEquals(1, e.history().length())
        }
    }

    @Test fun blockedStopKeepsUnfinishedDataUntilOneCommitAndSurvivesRotation() = Evidence().use { e ->
        fixture(e, history = true) { scenario ->
            click("Start session"); show("Stop")
            e.blockFinish.set(true); click("Stop"); assertTrue(e.entered.await(8, TimeUnit.SECONDS))
            assertNotNull(e.prefs.getString("active_session", null)); assertTrue(e.returned.isEmpty())
            show("Stop"); assertFalse(button("Stop").isEnabled)
            val old = e.active.get()
            instrumentation.runOnMainSync { old.recreate() }
            await { e.active.get() !== old }; e.release.countDown(); await { result(e, scenario) != null }
            assertEquals(1, e.history().length())
            assertTrue(e.transactions.any { "sessions" in it && "active_session" in it })
            val original = result(e, scenario)!!
            scenario.recreate(); await { result(e, scenario) != null }
            assertSame(original, result(e, scenario)); assertEquals(1, e.history().length())
        }
    }

    @Test fun historyIsOffUnlessRequestedAndItsAuditReflectsWhatWasSaved() = Evidence().use { e ->
        fixture(e) { scenario ->
            complete(e, scenario)
            assertFalse(e.prefs.contains("sessions"))
            assertFalse(JSONObject(result(e, scenario)!!.observations.single().values.getValue(SessionFields.AUDIT)).getBoolean("saved_locally"))
        }
    }

    @Test fun staleMetadataCannotResumeAPausedTimerOrResurrectAFinishedSession() = Evidence().use { e ->
        val original = e.repo.startSession("Target", "Telescope", "First note")
        e.now.addAndGet(3000); val paused = e.repo.pauseSession(original)
        val edited = e.repo.updateActiveSession(original.copy(notes = "New note"))
        assertEquals("paused", edited.state); assertEquals(paused.accumulatedMs, edited.accumulatedMs)
        e.repo.finishSession(edited, true)
        assertTrue(e.history().getJSONObject(0).get("active_duration_seconds") is Number)
        assertThrows(IllegalStateException::class.java) { e.repo.updateActiveSession(original) }
        assertThrows(IllegalStateException::class.java) { e.repo.finishSession(edited, true) }
        assertNull(e.repo.loadActiveSession()); assertEquals(1, e.history().length())
    }

    @Test fun malformedStoredSessionOrHistoryIsNeverSilentlyReplaced() = Evidence().use { e ->
        e.prefs.edit().putString("active_session", "{bad").commit()
        assertThrows(IllegalStateException::class.java) { e.repo.startSession("New", "", "") }
        assertEquals("{bad", e.prefs.getString("active_session", null))
        e.prefs.edit().remove("active_session").commit()
        val active = e.repo.startSession("Saved", "", "")
        e.prefs.edit().putString("sessions", "{bad").commit()
        assertThrows(IllegalStateException::class.java) { e.repo.finishSession(active, true) }
        assertEquals(active.id, e.repo.loadActiveSession()!!.id)
        assertEquals("{bad", e.prefs.getString("sessions", null))
    }

    @Test fun aSecondStartCannotOverwriteAnUnfinishedSession() = Evidence().use { e ->
        val active = e.repo.startSession("Original", "Scope", "Keep these notes")
        assertThrows(IllegalStateException::class.java) { e.repo.startSession("Replacement", "", "") }
        assertEquals(active, e.repo.loadActiveSession())
    }

    @Test fun preparingAFailedReturnDoesNotRemoveTheActiveSession() = Evidence().use { e ->
        val active = e.repo.startSession("Target", "", "")
        assertThrows(IllegalStateException::class.java) {
            e.repo.completeSession<Unit>(active, true) { error("Fixture canonical preparation failed") }
        }
        assertEquals(active, e.repo.loadActiveSession()); assertEquals(0, e.history().length())
    }

    @Test fun discardRequiresExplicitConfirmationAndKeepPreservesNotes() = Evidence().use { e ->
        fixture(e) { _ ->
            click("Start session"); show("Discard session"); click("Discard session")
            click("Keep session"); assertNotNull(e.repo.loadActiveSession())
            click("Discard session"); click("Discard"); await { e.repo.loadActiveSession() == null }
        }
    }

    @Test fun automaticOriginalReturnsOnceWithoutReplayingAfterRotation() {
        for (kind in listOf("exposure", "scale", "session")) Evidence(kind).use { e ->
            fixture(e, automatic = true, runtime = "") { scenario ->
                if (kind == "session") complete(e, scenario)
                await { e.returned.size == 1 }; val original = e.returned.single()
                scenario.recreate(); await { result(e, scenario) != null }
                assertEquals(1, e.returned.size); assertSame(original, result(e, scenario))
            }
        }
    }

    @Test fun finalPresetShareCannotBypassAFailedChooserAndRetriesTheOriginal() {
        for (kind in listOf("exposure", "scale", "session")) Evidence(kind).use { e ->
            fixture(e, action = "share") { scenario ->
                complete(e, scenario); val original = result(e, scenario)!!
                e.denyChooser.set(true); click("Share and finish"); status("Share failed:")
                assertTrue(e.returned.isEmpty())
                e.denyChooser.set(false); click("Share and finish"); await { e.returned.size == 1 }
                assertSame(original, e.returned.single()); assertEquals(1, e.choosers.size)
            }
        }
    }

    @Test fun explicitReloadFindsAnExistingSessionWithoutReplacingItsMetadata() = Evidence().use { e ->
        fixture(e, runtime = "target,equipment,notes") { _ ->
            e.repo.startSession("Recovered target", "Recovered scope", "Recovered notes")
            click("Reload unfinished session"); show("Recovered target"); show("Recovered notes")
            assertEquals("Recovered target", e.repo.loadActiveSession()!!.target)
            assertEquals("Recovered scope", e.repo.loadActiveSession()!!.equipment)
        }
    }
}
