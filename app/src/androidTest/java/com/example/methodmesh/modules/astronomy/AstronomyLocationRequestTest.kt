package com.example.methodmesh.modules.astronomy

import android.location.Location
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.CancellationToken
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class AstronomyLocationRequestTest {
    @Test fun completedLocationIsReturnedUnchanged() = runBlocking {
        val location = Location("review fixture").apply { latitude = 51.1; longitude = -0.1 }
        assertSame(location, withTimeout(3000) { awaitAstronomyLocation { Tasks.forResult(location) } })
    }
    @Test fun providerFailureReachesTheRequestingScreen() = runBlocking {
        val failure = IllegalStateException("fixture GPS unavailable")
        assertSame(failure, runCatching { withTimeout(3000) { awaitAstronomyLocation { Tasks.forException(failure) } } }.exceptionOrNull())
    }
    @Test fun cancelledRequestCancelsProviderTokenAndFencesLateSuccess() = runBlocking {
        val task = TaskCompletionSource<Location>()
        var token: CancellationToken? = null
        var continued = false
        val pending = launch(start = CoroutineStart.UNDISPATCHED) {
            awaitAstronomyLocation { cancellation -> token = cancellation; task.task }
            continued = true
        }
        pending.cancelAndJoin()
        assertTrue(token!!.isCancellationRequested)
        task.setResult(Location("late fixture"))
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertFalse(continued)
    }
}
