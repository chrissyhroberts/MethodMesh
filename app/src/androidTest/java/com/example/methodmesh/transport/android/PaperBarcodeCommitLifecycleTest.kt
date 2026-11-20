package com.example.methodmesh.transport.android

import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
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
import com.example.methodmesh.core.methodmesh.ExecutionRequest
import com.example.methodmesh.modules.qrcode.BarcodeGenerateCapabilityScreen
import com.example.methodmesh.modules.paperbridge.*
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import com.example.methodmesh.transport.workflow.ui.CapabilityPresentationMode
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
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/** Real generator Commit cycles plus synthetic Paper post-Commit rendering boundaries.
 * Paper tests do NOT exercise designer safety gates, scanner, OCR or real process death. */
class PaperBarcodeCommitLifecycleTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext
    private fun root(): AccessibilityNodeInfo? {
        if (Build.VERSION.SDK_INT >= 34) instrumentation.uiAutomation.clearCache()
        return instrumentation.uiAutomation.rootInActiveWindow
    }
    private fun find(predicate: (AccessibilityNodeInfo) -> Boolean, node: AccessibilityNodeInfo? = root()): AccessibilityNodeInfo? {
        if (node == null) return null
        if (predicate(node)) return node
        for (i in 0 until node.childCount) find(predicate, node.getChild(i))?.let { return it }
        return null
    }
    private fun text(label: String) = find({ it.text?.toString() == label })
    private fun await(condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 15_000
        while (!condition()) {
            check(SystemClock.uptimeMillis() < end) { "Paper/Barcode Commit fixture timed out" }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(30)
        }
    }
    private fun clickable(node: AccessibilityNodeInfo): AccessibilityNodeInfo {
        var value = node
        while (!value.isClickable) value = requireNotNull(value.parent)
        return value
    }
    private fun show(label: String): AccessibilityNodeInfo {
        repeat(3) { find({ it.isScrollable })?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) }
        instrumentation.waitForIdleSync()
        SystemClock.sleep(120)
        await {
            if (text(label) != null) true else {
                find({ it.isScrollable })?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                SystemClock.sleep(120)
                false
            }
        }
        return requireNotNull(text(label))
    }
    private fun click(label: String) {
        var node = clickable(show(label))
        node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
        instrumentation.waitForIdleSync()
        SystemClock.sleep(100)
        node = clickable(requireNotNull(text(label)))
        assertTrue("Disabled Paper/Barcode control: $label", node.isEnabled)
        assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
    }
    private fun captured(scenario: ActivityScenario<ExternalWorkflowActivity>): ExecutionResult? {
        val value = AtomicReference<ExecutionResult>()
        scenario.onActivity { value.set(ViewModelProvider(it)[RetainedExecutionStore::class.java].snapshot().values.filterNotNull().singleOrNull()) }
        return value.get()
    }
    private fun full(result: ExecutionResult) = OutputFormatter.format(result, ReturnMode.Json,
        includeProvenance = true, payloadMode = OutputFormatter.PayloadMode.FULL)
    private fun fixture(id: String = "barcode.generate", automatic: Boolean = false,
        run: (ActivityScenario<ExternalWorkflowActivity>, List<ExecutionResult>) -> Unit) {
        val token = UUID.randomUUID().toString()
        val returned = CopyOnWriteArrayList<ExecutionResult>()
        val incoming = Intent().putExtra("method_id", id)
            .putExtra("input_barcode_payload", "Evidence fixture").putExtra("input_barcode_format", "QR_CODE")
            .putExtra("input_barcode_auto_cycle", "false")
            .putExtra("input_methodmesh_native_preset_run", (id == "barcode.generate" && !automatic).toString())
            .putExtra("input_methodmesh_finish_to_launcher", "true")
        val request = AndroidIntentRequestReader.workflowRequest(incoming)
        val context = CapabilityScreenContext(request.actions.single(), request, 1, 1,
            if (automatic) CapabilityCompletionMode.AutomaticReturn else CapabilityCompletionMode.ManualConfirmation,
            presentationMode = if (id == "barcode.generate") CapabilityPresentationMode.Dashboard else CapabilityPresentationMode.IntentLaunch,
            hostOwnsNativePresetCloseout = true)
        val callback = ActivityLifecycleCallback { activity, stage ->
            if (activity is ExternalWorkflowActivity && stage == Stage.CREATED && activity.intent.getStringExtra("paper_barcode_commit_fixture") == token) {
                val guarded = object : ContextWrapper(activity) {
                    override fun startActivity(intent: Intent) { fail("Unexpected caller/Home/share launch: $intent") }
                }
                activity.setContent { MethodMeshTheme { CompositionLocalProvider(LocalContext provides guarded) {
                    Surface(Modifier.fillMaxSize()) {
                        val screen: CapabilityScreenSpec = when (id) {
                            "paper.form.design" -> PaperFormDesignCapabilityScreen
                            "paper.form.transcribe" -> PaperFormTranscribeCapabilityScreen
                            else -> BarcodeGenerateCapabilityScreen
                        }
                        if (id == "barcode.generate") screen.Render(context, {}, { returned += it }, {})
                        else Column(Modifier.verticalScroll(rememberScrollState())) {
                            screen.Render(context, {}, { returned += it }, {})
                        }
                    }
                } } }
            }
        }
        instrumentation.runOnMainSync { ActivityLifecycleMonitorRegistry.getInstance().addLifecycleCallback(callback) }
        try {
            ActivityScenario.launch<ExternalWorkflowActivity>(Intent(app, ExternalWorkflowActivity::class.java)
                .putExtra("paper_barcode_commit_fixture", token)).use { run(it, returned) }
        } finally {
            instrumentation.runOnMainSync { ActivityLifecycleMonitorRegistry.getInstance().removeLifecycleCallback(callback) }
        }
    }

    @Test fun generatorRotationAndDoneReturnExactCapturedEvidence() = fixture { scenario, returned ->
        click("Commit")
        val original = requireNotNull(captured(scenario)); val json = full(original)
        assertTrue(returned.isEmpty())
        scenario.recreate(); show("Done")
        assertSame(original, captured(scenario))
        assertEquals(json, full(requireNotNull(captured(scenario))))
        click("Done"); await { returned.size == 1 }
        assertSame(original, returned.single()); assertEquals(json, full(returned.single()))
    }

    @Test fun generatorExplicitEditAndCommitCreatesANewRecord() = fixture { scenario, _ ->
        click("Commit")
        val original = requireNotNull(captured(scenario)); val json = full(original)
        click("Edit"); click("Commit")
        val next = requireNotNull(captured(scenario))
        assertEquals(original.observations.single().values["barcode_payload"], next.observations.single().values["barcode_payload"])
        assertNotEquals(original.request.id, next.request.id)
        assertEquals(json, full(original))
        scenario.recreate(); show("Done"); assertSame(next, captured(scenario))
    }

    @Test fun generatorMissingRecordRequiresExplicitNewRun() = fixture { scenario, returned ->
        click("Commit")
        val original = requireNotNull(captured(scenario))
        scenario.onActivity { activity -> ViewModelProvider(activity)[RetainedExecutionStore::class.java].let { store ->
            store.snapshot().keys.toList().forEach(store::forget)
        } }
        scenario.recreate(); show("Captured result unavailable")
        assertNull(captured(scenario)); assertTrue(returned.isEmpty())
        assertNull(text("Done")); assertNull(text("Share image"))
        click("Start new run"); click("Commit")
        val next = requireNotNull(captured(scenario))
        assertNotEquals(original.request.id, next.request.id)
        click("Done"); await { returned.size == 1 }; assertSame(next, returned.single())
    }

    @Test fun generatorAutomaticCommitReturnsOnceWithoutReplay() = fixture(automatic = true) { scenario, returned ->
        click("Commit and return"); await { returned.size == 1 }
        val original = returned.single(); val json = full(original)
        assertSame(original, captured(scenario))
        scenario.recreate(); show("Done")
        assertEquals(1, returned.size); assertSame(original, captured(scenario))
        assertEquals(json, full(requireNotNull(captured(scenario))))
    }

    private fun paperBoundary(design: Boolean) {
        val method = if (design) As100PaperFormDesignMethod else As100PaperFormTranscribeMethod
        val original = if (design) As100PaperFormDesignMethod.result(
            ExecutionRequest(action = method.id, method = method.ref), mapOf(
                PaperDesignFields.STATUS to "succeeded", PaperDesignFields.TEMPLATE_ID to "synthetic-boundary",
                PaperDesignFields.TEMPLATE_VERSION to "1", PaperDesignFields.FIELD_COUNT to "1",
                PaperDesignFields.ODK_FIELD_COUNT to "1", PaperDesignFields.TEMPLATE_JSON to "{}",
                PaperDesignFields.TEMPLATE_IMAGE_URI to "content://fixture/unchanged-design"), null)
        else As100PaperFormTranscribeMethod.result(
            ExecutionRequest(action = method.id, method = method.ref), mapOf(
                PaperBridgeFields.STATUS to "succeeded", PaperBridgeFields.TEMPLATE_ID to "synthetic-boundary",
                PaperBridgeFields.VALUES_JSON to "{\"participant_code\":\"Unchanged fixture\"}",
                PaperBridgeFields.EXTRACTION_AUDIT_JSON to "{\"boundary\":\"synthetic\"}",
                PaperBridgeFields.SOURCE_IMAGE to "content://fixture/unchanged-source",
                PaperBridgeFields.SOURCE_SHA256 to "fixture-hash"), mapOf("participant_code" to "Unchanged fixture"), null)
        val json = full(original)
        fixture(id = method.id) { scenario, returned ->
            // Deliberately synthetic post-Commit injection: no bypass of production capture gates.
            await {
                val ready = AtomicReference(false)
                scenario.onActivity { ready.set(ViewModelProvider(it)[RetainedExecutionStore::class.java].snapshot().size == 1) }
                ready.get()
            }
            scenario.onActivity { activity -> ViewModelProvider(activity)[RetainedExecutionStore::class.java].let { store ->
                store.slot(store.snapshot().keys.single()).value = original
            } }
            show(if (design) "Committed design" else "Committed transcription")
            scenario.recreate(); show(if (design) "Committed design" else "Committed transcription")
            assertSame(original, captured(scenario)); assertEquals(json, full(requireNotNull(captured(scenario))))
            click("Done"); await { returned.size == 1 }
            assertSame(original, returned.single()); assertEquals(json, full(returned.single()))
        }
    }
    @Test fun paperDesignSyntheticPostCommitBoundaryRetainsExactEvidence() = paperBoundary(true)
    @Test fun paperTranscriptionSyntheticPostCommitBoundaryRetainsExactEvidence() = paperBoundary(false)
}
