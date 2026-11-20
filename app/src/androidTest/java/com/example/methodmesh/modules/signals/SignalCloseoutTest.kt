package com.example.methodmesh.modules.signals

import android.content.*
import android.database.Cursor
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.example.methodmesh.core.methodmesh.*
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.ReturnMode
import com.example.methodmesh.transport.android.AndroidIntentRequestReader
import com.example.methodmesh.transport.workflow.ui.CapabilityCompletionMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import org.junit.Assert.*
import org.junit.Test

/** Shared closeout boundary only: no microphone, camera, transmitter or user export. */
class SignalCloseoutTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun context(action: String = "home", native: Boolean = true, host: Boolean = true, step: Int = 1, total: Int = 1, launcher: Boolean = false): CapabilityScreenContext {
        val intent = Intent().putExtra("method_id", "signal.morse.transmit")
            .putExtra("input_methodmesh_native_preset_run", native.toString())
            .putExtra("input_methodmesh_preset_result_action", action)
            .putExtra("input_methodmesh_finish_to_launcher", launcher.toString())
        val request = AndroidIntentRequestReader.workflowRequest(intent)
        return CapabilityScreenContext(request.actions.single(), request, step, total,
            CapabilityCompletionMode.ManualConfirmation, hostOwnsNativePresetCloseout = host)
    }
    private fun result() = ExecutionResult(
        ExecutionRequest(action = "signal.morse.transmit", method = ArchitectureRef(ArchitectureId("signal.morse.transmit"), "Method")),
        TransformationStatus.Succeeded,
        observations = listOf(Observation(phenomenon = "signal", values = mapOf("signal_result" to "Frozen fixture"), provenance = ProvenanceContext("test")))
    )
    private fun noNavigation() = object : ContextWrapper(app) {
        override fun startActivity(intent: Intent) { fail("Module bypassed confirmation-owned navigation") }
    }
    private fun deniedStorage(): Context {
        val provider = object : ContentProvider() {
            override fun onCreate() = true
            override fun getType(uri: Uri) = "text/plain"
            override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
            override fun insert(uri: Uri, values: ContentValues?): Uri? = throw SecurityException("Fixture storage denied")
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
            override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
        }
        val resolver = ContentResolver.wrap(provider)
        return object : ContextWrapper(noNavigation()) { override fun getContentResolver() = resolver }
    }
    @Test fun hostedHomeAndShareDelegateTheSameFrozenResultWithoutNavigating() {
        listOf("home", "share").forEach { action ->
            val frozen = result()
            val json = OutputFormatter.format(frozen, ReturnMode.Json)
            var confirmed: ExecutionResult? = null
            var shares = 0
            finishSignalResult(context(action), noNavigation(), frozen, { confirmed = it }, onShareRequested = { shares++; true }) {
                error("Home/Done must not save implicitly")
            }
            assertEquals(if (action == "share") 1 else 0, shares)
            assertSame(frozen, confirmed)
            assertEquals(json, OutputFormatter.format(confirmed!!, ReturnMode.Json))
        }
    }
    @Test fun deniedTextAndMediaSaveDoNotConfirmAndExposeTheFailure() {
        listOf(false, true).forEach { media ->
            var confirmations = 0
            var outcome: SignalSaveOutcome? = null
            val denied = deniedStorage()
            finishSignalResult(context("save"), denied, result(), { confirmations++ }) {
                (if (media) saveSignalMediaChecked(denied, "fixture", "content://fixture/missing", "Frozen fixture")
                else saveSignalTextChecked(denied, "fixture", "Frozen fixture")).also { outcome = it }
            }
            assertEquals(0, confirmations)
            assertFalse(outcome!!.saved)
            assertTrue(outcome!!.message.startsWith("Save failed:"))
        }
    }
    @Test fun successfulSaveConfirmsOnlyAfterTheSaveCallback() {
        val events = mutableListOf<String>()
        val frozen = result()
        finishSignalResult(context("save"), noNavigation(), frozen, {
            assertSame(frozen, it)
            events += "confirmed"
        }) { events += "saved"; SignalSaveOutcome(true, "Fixture save succeeded") }
        assertEquals(listOf("saved", "confirmed"), events)
    }
    @Test fun intermediateAndExternalCompletionDoNotPerformPresetExports() {
        listOf(context("save", native = false), context("save", total = 2)).forEach { ctx ->
            val frozen = result()
            var confirmed: ExecutionResult? = null
            finishSignalResult(ctx, noNavigation(), frozen, { confirmed = it }) {
                error("Caller/intermediate result must not perform final preset export")
            }
            assertSame(frozen, confirmed)
        }
    }
    @Test fun launcherReturnConfirmsWithoutOpeningDashboardEvenForALegacyHost() {
        val frozen = result()
        var confirmed: ExecutionResult? = null
        finishSignalResult(context(host = false, launcher = true), noNavigation(), frozen, { confirmed = it }) {
            error("Widget closeout must not save implicitly")
        }
        assertSame(frozen, confirmed)
    }
}
