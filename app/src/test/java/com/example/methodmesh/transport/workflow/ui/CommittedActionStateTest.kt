package com.example.methodmesh.transport.workflow.ui

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException

class CommittedActionStateTest {
    @Test fun duplicateTapCannotStartSecondExportOrFinishEarly() = runBlocking {
        val state = CommittedActionState(); val release = CompletableDeferred<Unit>(); var writes = 0
        assertTrue(state.start(this, true, "Save failed") { writes++; release.await(); "Saved" })
        assertFalse(state.start(this, true, "Save failed") { writes++; "Duplicate" })
        yield(); assertEquals(1, writes); assertTrue(state.busy); assertFalse(state.takeFinish())
        release.complete(Unit); yield(); assertFalse(state.busy); assertTrue(state.takeFinish()); assertFalse(state.takeFinish())
        assertFalse(state.start(this, true, "Save failed") { "After closeout" })
    }
    @Test fun storageFailureRetainsResultAndNeedsExplicitRetry() = runBlocking {
        val state = CommittedActionState(); var attempts = 0
        state.start(this, true, "Save failed") { attempts++; throw IOException("Denied") }; yield()
        assertEquals("Save failed: Denied", state.status); assertFalse(state.busy); assertFalse(state.takeFinish()); assertEquals(1, attempts)
        yield(); assertEquals(1, attempts)
        state.start(this, true, "Save failed") { attempts++; "Saved" }; yield()
        assertEquals(2, attempts); assertTrue(state.takeFinish())
    }
    @Test fun permissionPreflightRunsBeforeWorkerAndNeverReplaysAfterGrant() = runBlocking {
        val state = CommittedActionState(); var writes = 0; var granted = false; var requests = 0
        val before = { if (!granted) { requests++; throw IllegalStateException("Allow then retry") } }
        state.start(this, true, "Save failed", before) { writes++; "Saved" }; yield()
        assertEquals(1, requests); assertEquals(0, writes); assertFalse(state.takeFinish())
        granted = true; yield(); assertEquals(0, writes)
        state.start(this, true, "Save failed", before) { writes++; "Saved" }; yield()
        assertEquals(1, writes); assertTrue(state.takeFinish())
    }
    @Test fun manualSaveDoesNotConfirmAndExplicitSecondSaveRemainsPossible() = runBlocking {
        val state = CommittedActionState(); var writes = 0
        repeat(2) { state.start(this, false, "Save failed") { writes++; "Saved" }; yield(); assertFalse(state.takeFinish()) }
        assertEquals(2, writes); assertFalse(state.finished)
    }
    @Test fun pendingCompletionSurvivesAbsenceOfAnObserverButIsConsumedOnlyOnce() = runBlocking {
        val state = CommittedActionState(); val release = CompletableDeferred<Unit>()
        state.start(this, true, "Share failed") { release.await(); "Shared" }; yield(); release.complete(Unit); yield()
        assertTrue(state.finishPending)
        assertFalse(state.start(this, true, "Share failed") { "Duplicate" })
        assertTrue(state.takeFinish()); assertTrue(state.finished); assertFalse(state.takeFinish())
    }
    @Test fun cancelledScopeCannotLeaveBusyOrConfirm() = runBlocking {
        val job = Job().apply { cancel() }; val state = CommittedActionState(); var ran = false
        state.start(CoroutineScope(coroutineContext + job), true, "Save failed") { ran = true; "Saved" }; yield()
        assertFalse(state.busy); assertFalse(ran); assertFalse(state.takeFinish())
    }
    @Test fun ownerCancellationDoesNotReportFailureOrFinish() = runBlocking {
        val job = Job(); val state = CommittedActionState(); val release = CompletableDeferred<Unit>()
        state.start(CoroutineScope(coroutineContext + job), true, "Save failed") { release.await(); "Saved" }
        yield(); job.cancelAndJoin(); release.complete(Unit); yield()
        assertFalse(state.busy); assertFalse(state.takeFinish()); assertFalse(state.status.startsWith("Save failed"))
    }
    private val root get() = listOf(File("."), File("app")).first { File(it, "src/main/java").isDirectory }
    private fun source(path: String) = File(root, "src/main/java/com/example/methodmesh/" + path).readText()
    @Test fun allTenCanonicalCallSitesPassOriginWithoutModuleSpecialCases() {
        val files = listOf("modules/media/MediaCapabilityScreens.kt", "modules/svgselector/SvgSelectorCapabilityScreen.kt",
            "modules/geocaching/GeocachingUiComponents.kt", "modules/expenses/ExpensesCapabilityScreen.kt")
        val calls = files.flatMap { source(it).split("CanonicalCommittedResultActions(").drop(1) }
        assertEquals(10, calls.size); calls.forEach { assertTrue(it.substringBefore("result =").contains("screenContext = context")) }
        val shared = source("transport/workflow/ui/CanonicalCommittedResultActions.kt")
        assertTrue(shared.contains("screenContext.isNativePresetRun && screenContext.isLastStep && !screenContext.submitsImmediately"))
        assertTrue(shared.contains("withContext(Dispatchers.IO)")); assertTrue(shared.contains("LegacyDownloadsPermission.requireAccess(appContext)"))
        assertFalse(shared.contains("startActivity(")); assertTrue(shared.contains("state.takeFinish()"))
    }
    @Test fun originalRecordsAreRetainedAndSvgCannotReconstructOrBypassCloseout() {
        listOf("modules/media/MediaCapabilityScreens.kt", "modules/expenses/ExpensesCapabilityScreen.kt", "modules/svgselector/SvgSelectorCapabilityScreen.kt").forEach {
            assertTrue(source(it).contains("rememberRetainedExecutionResult(context.action.canonicalId)"))
        }
        val svg = source("modules/svgselector/SvgSelectorCapabilityScreen.kt")
        assertFalse(svg.contains("restoredCommittedResult")); assertTrue(svg.contains("CapturedExecutionRecovery("))
        assertFalse(svg.contains("if (committedResult == null) onCancel else onDone"))
    }
    @Test fun workbenchContractInspectionIsNotExecutionOrPresetAuthoringPermission() {
        val home = source("ui/HomeScreen.kt")
        val contract = home.substringBefore("title = \"ODK / return contract\"").takeLast(120)
        assertFalse(contract.contains("isProtocolPrimitive"))
        val actions = home.substringAfter("InlineAction(\"Open\"").substringBefore("if (lastResult")
        assertTrue(actions.contains("if (isProtocolPrimitive)"))
        assertTrue(actions.contains("Save preset"))
    }
}
