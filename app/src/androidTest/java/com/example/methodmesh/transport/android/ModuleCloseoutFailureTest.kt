package com.example.methodmesh.transport.android

import android.content.*
import android.database.Cursor
import android.net.Uri
import android.os.SystemClock
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.example.methodmesh.core.methodmesh.*
import com.example.methodmesh.modules.networktools.NetworkCommittedActions
import com.example.methodmesh.modules.weather.WeatherCommittedActions
import com.example.methodmesh.modules.qrcode.BarcodeGenerateCapabilityScreen
import com.example.methodmesh.transport.workflow.ui.CapabilityCompletionMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.ui.theme.MethodMeshTheme
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** Exercise actual module actions with denied storage/chooser dispatch; never share with another app. */
class ModuleCloseoutFailureTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext
    private fun freshRoot(): AccessibilityNodeInfo? {
        if (Build.VERSION.SDK_INT >= 34) instrumentation.uiAutomation.clearCache()
        return instrumentation.uiAutomation.rootInActiveWindow
    }
    private fun node(prefix: String, root: AccessibilityNodeInfo? = freshRoot()): AccessibilityNodeInfo? {
        if (root == null) return null
        if (root.text?.toString()?.startsWith(prefix) == true) return root
        for (index in 0 until root.childCount) node(prefix, root.getChild(index))?.let { return it }
        return null
    }
    private fun await(condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 8000
        while (!condition()) {
            check(SystemClock.uptimeMillis() < end) { "Module closeout fixture timed out" }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(25)
        }
    }
    private fun verify(module: String) {
        val weather = module == "weather"
        listOf("save", "share").forEach { action ->
            val done = AtomicInteger()
            val provider = object : ContentProvider() {
                override fun onCreate() = true
                override fun getType(uri: Uri) = "text/plain"
                override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
                override fun insert(uri: Uri, values: ContentValues?): Uri? = throw SecurityException("Fixture storage denied")
                override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
                override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
            }
            val resolver = ContentResolver.wrap(provider)
            val id = when (module) { "weather" -> "weather.conditions"; "barcode" -> "barcode.generate"; else -> "network.tools" }
            val incoming = Intent(app, ExternalWorkflowActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            val requestIntent = Intent().putExtra("method_id", id).putExtra("input_methodmesh_native_preset_run", "true")
                .putExtra("input_methodmesh_preset_result_action", action).putExtra("input_methodmesh_finish_to_launcher", "true")
                .putExtra("input_barcode_payload", "Failure fixture").putExtra("input_barcode_auto_cycle", "false")
            val request = AndroidIntentRequestReader.workflowRequest(requestIntent)
            val context = CapabilityScreenContext(request.actions.single(), request, 1, 1, CapabilityCompletionMode.ManualConfirmation)
            val result = ExecutionResult(ExecutionRequest(action = id, method = ArchitectureRef(ArchitectureId(id), "Method")),
                TransformationStatus.Succeeded, observations = listOf(Observation(phenomenon = id,
                    values = mapOf((if (weather) "weather_conditions_result" else "network_value") to "Captured fixture result"), provenance = ProvenanceContext("test"))))
            ActivityScenario.launch<ExternalWorkflowActivity>(incoming).use { scenario ->
                scenario.onActivity { activity ->
                    val denied = object : ContextWrapper(activity) {
                        override fun getContentResolver() = resolver
                        override fun startActivity(intent: Intent) { throw ActivityNotFoundException("Fixture chooser denied") }
                    }
                    activity.setContent { MethodMeshTheme { CompositionLocalProvider(LocalContext provides denied) {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            if (module == "barcode") BarcodeGenerateCapabilityScreen.Render(context, {}, { done.incrementAndGet() }, {})
                            else if (weather) WeatherCommittedActions(context, "Review Weather", result, false) { done.incrementAndGet() }
                            else NetworkCommittedActions(context, "Review Network", result, false) { done.incrementAndGet() }
                        }
                    } } }
                }
                if (module == "barcode") {
                    await { node("Commit") != null }
                    var commit = node("Commit")!!
                    while (!commit.isClickable) commit = requireNotNull(commit.parent)
                    commit.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
                    instrumentation.waitForIdleSync()
                    assertTrue(commit.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                }
                val label = if (action == "save") "Save and finish" else "Share and finish"
                await { node(label) != null }
                var button = node(label)!!
                while (!button.isClickable) button = requireNotNull(button.parent)
                button.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
                instrumentation.waitForIdleSync()
                assertTrue(button.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                await { node(if (action == "save") (if (module == "barcode") "Save failed:" else "Downloads save failed:") else "Share failed:") != null }
                assertEquals("Failed $action closed the committed result", 0, done.get())
                scenario.onActivity { assertFalse(it.isFinishing) }
            }
        }
    }
    @Test fun weatherDoesNotFinishAfterFailedSaveOrShare() = verify("weather")
    @Test fun networkDoesNotFinishAfterFailedSaveOrShare() = verify("network")
    @Test fun barcodeGeneratorDoesNotFinishAfterFailedSaveOrShare() = verify("barcode")
}
