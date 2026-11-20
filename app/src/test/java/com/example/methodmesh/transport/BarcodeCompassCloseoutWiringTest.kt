package com.example.methodmesh.transport

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class BarcodeCompassCloseoutWiringTest {
    private val root get() = listOf(File("."), File("app")).first { File(it, "src/main/java").isDirectory }
    private fun source(path: String) = File(root, "src/main/java/com/example/methodmesh/" + path).readText()
    private val barcode get() = source("modules/qrcode/QrCodeCapabilityScreen.kt")
    private val compass get() = source("modules/compass/CompassCapabilityScreen.kt")
    @Test fun bothActualScreensDelegateCommittedActionsWithoutPrivateSaveOrHomeRouting() {
        assertTrue(barcode.contains("BarcodeScanCommittedResult(")); assertTrue(compass.contains("CompassCommittedResult("))
        for (text in listOf(barcode, compass)) {
            assertTrue(text.contains("CanonicalCommittedResultActions("))
            assertFalse(text.contains("OutputExportRepository")); assertFalse(text.contains("Intent(androidContext, MainActivity"))
            assertFalse(text.contains("Intent(appContext, MainActivity")); assertFalse(text.contains("PresetResultAction"))
            assertTrue(text.contains("onDone = { onConfirmed(result) }"))
        }
    }
    @Test fun primaryValuesAreExplicitAndBarcodeUriTextCannotBecomeAFile() {
        assertTrue(barcode.contains("primaryText = payload, attachments = emptyList()"))
        assertTrue(compass.contains("primaryText = primary, attachments = emptyList()"))
        val shared = source("transport/workflow/ui/CanonicalCommittedResultActions.kt")
        assertTrue(shared.contains("text = primaryText ?: original.text"))
        assertTrue(shared.contains("attachments = attachments?.map { it.name to it.uri } ?: original.attachments"))
    }
    @Test fun reacquisitionIsFencedWhileExportsOrCompletionArePending() {
        assertTrue(barcode.contains("enabled = !busy, onClick = { if (!busy) onNewScan() }"))
        assertTrue(barcode.contains("if (!actionsBusy)"))
        assertTrue(compass.contains("if (actionsBusy) return"))
        assertTrue(compass.contains("enabled = !actionsBusy && flatHeading != null"))
        val shared = source("transport/workflow/ui/CanonicalCommittedResultActions.kt")
        assertTrue(shared.contains("onBusyChanged?.invoke(!enabled)"))
        assertTrue(shared.contains("onBusyChanged?.invoke(state.busy || state.finishPending || state.finished)"))
    }
    @Test fun originalRetentionAndAutomaticCommitReturnRemainInTheModule() {
        assertTrue(barcode.contains("committedResult = result")); assertTrue(barcode.contains("if (automaticReturn) onConfirmed(result)"))
        assertTrue(compass.contains("committedResult = execution")); assertTrue(compass.contains("if (context.submitsImmediately) onConfirmed(execution)"))
        assertTrue(barcode.contains("rememberRetainedExecutionResult(")); assertTrue(compass.contains("rememberRetainedExecutionResult("))
        assertTrue(barcode.contains("CapturedExecutionRecovery(")); assertTrue(compass.contains("Captured result unavailable."))
    }
    @Test fun finalStepOnlySuccessGateAndAsyncSaveStayGeneric() {
        val shared = source("transport/workflow/ui/CanonicalCommittedResultActions.kt")
        assertTrue(shared.contains("screenContext.isNativePresetRun && screenContext.isLastStep && !screenContext.submitsImmediately"))
        assertTrue(shared.contains("PresetResultAction.SHARE -> share(true)"))
        assertTrue(shared.contains("PresetResultAction.SAVE -> save(true)"))
        assertTrue(shared.contains("withContext(Dispatchers.IO)")); assertTrue(shared.contains("state.takeFinish()"))
    }
}
