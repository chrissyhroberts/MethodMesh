package com.example.methodmesh.modules.paperbridge

import com.example.methodmesh.core.methodmesh.ExecutionRequest
import com.example.methodmesh.core.methodmesh.ExecutionResult
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException

class PaperCommitControllerTest {
    private fun result() = As100PaperFormTranscribeMethod.result(
        ExecutionRequest(action = As100PaperFormTranscribeMethod.id, method = As100PaperFormTranscribeMethod.ref),
        mapOf(PaperBridgeFields.STATUS to "succeeded"), mapOf("answer" to "frozen"), null)

    @Test fun duplicateStartCannotPrepareOrDeliverASecondRecord() = runBlocking {
        val controller = PaperCommitController(); val release = CompletableDeferred<Unit>()
        val original = result(); val returned = mutableListOf<ExecutionResult>(); var starts = 0
        assertTrue(controller.start(this, { starts++; release.await(); original }, returned::add, { throw it }))
        assertTrue(controller.inFlight)
        assertFalse(controller.start(this, { starts++; result() }, returned::add, { throw it }))
        yield(); assertEquals(1, starts); assertTrue(returned.isEmpty())
        release.complete(Unit); yield()
        assertFalse(controller.inFlight); assertSame(original, returned.single())
    }

    @Test fun explicitCancelPreventsReturnAndDoesNotReportFailure() = runBlocking {
        val controller = PaperCommitController(); val release = CompletableDeferred<Unit>()
        val returned = mutableListOf<ExecutionResult>(); val errors = mutableListOf<Exception>()
        controller.start(this, { release.await(); result() }, returned::add, errors::add)
        yield(); controller.cancel(); release.complete(Unit); yield()
        assertFalse(controller.inFlight); assertTrue(returned.isEmpty()); assertTrue(errors.isEmpty())
    }

    @Test fun cancelledNonCooperativeWorkCannotReturnOrUnlockANewerCommit() = runBlocking {
        val controller = PaperCommitController(); val oldRelease = CompletableDeferred<Unit>()
        val newRelease = CompletableDeferred<Unit>(); val returned = mutableListOf<ExecutionResult>()
        val errors = mutableListOf<Exception>(); val next = result()
        controller.start(this, { withContext(NonCancellable) { oldRelease.await() }; result() }, returned::add, errors::add)
        yield(); controller.cancel()
        controller.start(this, { newRelease.await(); next }, returned::add, errors::add)
        yield(); oldRelease.complete(Unit); repeat(4) { yield() }
        assertTrue(controller.inFlight); assertTrue(returned.isEmpty()); assertTrue(errors.isEmpty())
        newRelease.complete(Unit); yield()
        assertFalse(controller.inFlight); assertSame(next, returned.single())
    }

    @Test fun hashFailureDoesNotReturnAndAllowsOnlyAnExplicitRetry() = runBlocking {
        val controller = PaperCommitController(); val returned = mutableListOf<ExecutionResult>()
        val errors = mutableListOf<Exception>(); var attempts = 0
        controller.start(this, { attempts++; throw IOException("Unreadable attachment") }, returned::add, errors::add)
        yield(); assertFalse(controller.inFlight); assertEquals(1, attempts)
        assertTrue(returned.isEmpty()); assertEquals("Unreadable attachment", errors.single().message)
        val retry = result()
        controller.start(this, { attempts++; retry }, returned::add, errors::add); yield()
        assertEquals(2, attempts); assertSame(retry, returned.single())
    }

    @Test fun leavingTheOwningScopeCancelsPreparationWithoutDelivery() = runBlocking {
        val parent = Job(); val scope = CoroutineScope(coroutineContext + parent)
        val controller = PaperCommitController(); val release = CompletableDeferred<Unit>()
        val returned = mutableListOf<ExecutionResult>(); val errors = mutableListOf<Exception>()
        controller.start(scope, { release.await(); result() }, returned::add, errors::add)
        yield(); parent.cancelAndJoin(); release.complete(Unit); yield()
        assertFalse(controller.inFlight); assertTrue(returned.isEmpty()); assertTrue(errors.isEmpty())
    }

    @Test fun alreadyCancelledScopeCannotLeaveTheGateBusy() = runBlocking {
        val parent = Job().apply { cancel() }; val controller = PaperCommitController()
        var prepared = false
        controller.start(CoroutineScope(coroutineContext + parent), { prepared = true; result() }, { fail("Returned after cancellation") }, { fail("Failure after cancellation") })
        yield(); assertFalse(controller.inFlight); assertFalse(prepared)
    }

    @Test fun changedWorkingStateRejectsPreparedEvidenceWithoutReturnOrAutomaticRetry() = runBlocking {
        val controller = PaperCommitController(); val release = CompletableDeferred<Unit>()
        var workingUnchanged = true; var prepared = 0; var rejected = 0
        val returned = mutableListOf<ExecutionResult>(); val errors = mutableListOf<Exception>()
        controller.start(this, { prepared++; release.await(); result() }, returned::add, errors::add,
            isCurrent = { workingUnchanged }, onStale = { rejected++ })
        yield(); workingUnchanged = false; release.complete(Unit); yield()
        assertFalse(controller.inFlight); assertEquals(1, prepared); assertEquals(1, rejected)
        assertTrue(returned.isEmpty()); assertTrue(errors.isEmpty())
    }

    @Test fun screenUsesFrozenEvidenceAndFencesWorkingChangesBeforePublishing() {
        val root = listOf(File("."), File("app")).first { File(it, "src/main/java").isDirectory }
        val source = File(root, "src/main/java/com/example/methodmesh/modules/paperbridge/PaperFormTranscribeCapabilityScreen.kt").readText()
        val commit = source.substringAfter("fun commit() {").substringBefore("val unresolved =")
        assertTrue(commit.indexOf("PaperCommitSnapshot.capture(") < commit.indexOf("commitController.start("))
        val prepare = commit.substringAfter("prepare = {").substringBefore("}, isCurrent")
        assertFalse(prepare.contains("manualAuditJson")); assertFalse(prepare.contains("results"))
        listOf("session === extracted", "results === reviewedResults", "manualAuditJson == reviewedAudit", "templateJson == reviewedTemplateJson",
            "sourceUriText == reviewedSource", "workspaceMode == reviewedMode").forEach { fence ->
            assertTrue(fence, commit.indexOf(fence) in 0 until commit.indexOf("committedResult = execution"))
        }
        assertTrue(source.contains("onDispose { commitController.cancel() }"))
        assertTrue(source.contains("Cancel commit"))
        assertTrue(source.indexOf("if (commitController.inFlight) {") < source.indexOf("if (nativeDashboard && workspaceMode == \"designer\""))
        assertTrue(commit.contains("fieldCount = snapshot.fieldCount"))
        assertTrue(commit.contains("template?.rawJson != extracted.template.rawJson"))
    }
}
