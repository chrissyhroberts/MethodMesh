package com.example.methodmesh.modules.astronomy

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class AstronomyAutoRequestControllerTest {
    @Test fun duplicateRunCannotStartOrPublishAnotherCapture() = runBlocking {
        val gate = AstronomyAutoRequestController(); val release = CompletableDeferred<Unit>()
        val returned = mutableListOf<String>(); var started = 0
        assertTrue(gate.start(this, { started++; release.await(); "original" }, returned::add, { throw it }))
        assertFalse(gate.start(this, { started++; "duplicate" }, returned::add, { throw it }))
        yield(); assertEquals(1, started); assertTrue(gate.busy)
        release.complete(Unit); yield()
        assertEquals(listOf("original"), returned); assertFalse(gate.busy)
    }

    @Test fun explicitCancelDoesNotPublishSuccessOrFailureAndReleasesRetry() = runBlocking {
        val gate = AstronomyAutoRequestController(); val release = CompletableDeferred<Unit>()
        val returned = mutableListOf<String>(); val errors = mutableListOf<Exception>()
        gate.start(this, { release.await(); "late" }, returned::add, errors::add)
        yield(); gate.cancel(); release.complete(Unit); yield()
        assertFalse(gate.busy); assertTrue(returned.isEmpty()); assertTrue(errors.isEmpty())
        gate.start(this, { "retry" }, returned::add, errors::add); yield()
        assertEquals(listOf("retry"), returned)
    }

    @Test fun nonCooperativeOldRequestCannotPublishOrReleaseANewerRequest() = runBlocking {
        val gate = AstronomyAutoRequestController(); val old = CompletableDeferred<Unit>()
        val next = CompletableDeferred<Unit>(); val returned = mutableListOf<String>()
        gate.start(this, { withContext(NonCancellable) { old.await() }; "old" }, returned::add, { throw it })
        yield(); gate.cancel()
        gate.start(this, { next.await(); "new" }, returned::add, { throw it })
        yield(); old.complete(Unit); repeat(4) { yield() }
        assertTrue(gate.busy); assertTrue(returned.isEmpty())
        next.complete(Unit); yield()
        assertFalse(gate.busy); assertEquals(listOf("new"), returned)
    }

    @Test fun owningScopeCancellationDoesNotReportAProviderFailure() = runBlocking {
        val parent = Job(); val scope = CoroutineScope(coroutineContext + parent)
        val gate = AstronomyAutoRequestController(); val release = CompletableDeferred<Unit>()
        gate.start(scope, { release.await(); "late" }, { fail("Late capture") }, { fail("Cancelled request became a failure") })
        yield(); parent.cancelAndJoin()
        assertFalse(gate.busy)
    }

    @Test fun alreadyCancelledScopeDoesNotLeaveRunLocked() = runBlocking {
        val parent = Job().apply { cancel() }; val gate = AstronomyAutoRequestController()
        var started = false
        gate.start(CoroutineScope(coroutineContext + parent), { started = true; "late" },
            { fail("Late result") }, { fail("Late failure") })
        yield(); assertFalse(gate.busy); assertFalse(started)
    }

    @Test fun actualProviderErrorRequiresExplicitRetry() = runBlocking {
        val gate = AstronomyAutoRequestController(); val errors = mutableListOf<Exception>()
        var attempts = 0
        gate.start(this, { attempts++; error("provider offline") }, { _: String -> fail("Unexpected result") }, errors::add)
        yield()
        assertEquals(1, attempts); assertEquals("provider offline", errors.single().message)
        assertFalse(gate.busy)
    }
}
