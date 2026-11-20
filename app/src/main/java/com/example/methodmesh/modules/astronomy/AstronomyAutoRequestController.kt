package com.example.methodmesh.modules.astronomy

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Main-thread-owned acquisition; leaving invalidates even non-cooperative provider work. */
internal class AstronomyAutoRequestController {
    var busy by mutableStateOf(false)
        private set
    private var generation = 0L
    private var job: Job? = null

    fun cancel() {
        generation++
        job?.cancel()
        job = null
        busy = false
    }

    fun <T> start(scope: CoroutineScope, acquire: suspend () -> T,
        onReady: (T) -> Unit, onFailure: (Exception) -> Unit, onCancelled: () -> Unit = {}): Boolean {
        if (busy) return false
        val ticket = ++generation
        busy = true
        val next = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val capture = try { acquire() }
                catch (cancelled: CancellationException) {
                    if (ticket == generation) onCancelled()
                    throw cancelled
                }
                catch (failure: Exception) {
                    currentCoroutineContext().ensureActive()
                    if (ticket == generation) onFailure(failure)
                    return@launch
                }
                currentCoroutineContext().ensureActive()
                if (ticket == generation) onReady(capture)
            } finally {
                if (ticket == generation) { busy = false; job = null }
            }
        }
        job = next
        next.invokeOnCompletion { if (ticket == generation) { busy = false; job = null } }
        next.start()
        return true
    }
}
