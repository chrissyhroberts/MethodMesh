package com.example.methodmesh.modules.paperbridge

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.methodmesh.core.methodmesh.ExecutionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Main-thread-owned, screen-local transaction. No persistence or automatic retry. */
internal class PaperCommitController {
    var inFlight by mutableStateOf(false)
        private set
    private var generation = 0L
    private var job: Job? = null

    fun cancel() {
        generation += 1
        job?.cancel()
        job = null
        inFlight = false
    }

    fun start(
        scope: CoroutineScope,
        prepare: suspend () -> ExecutionResult,
        onReady: (ExecutionResult) -> Unit,
        onFailure: (Exception) -> Unit,
        isCurrent: () -> Boolean = { true },
        onStale: () -> Unit = {}
    ): Boolean {
        if (inFlight) return false
        val ticket = ++generation
        inFlight = true
        val next = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val result = try {
                    prepare()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    currentCoroutineContext().ensureActive()
                    if (ticket == generation) onFailure(error)
                    return@launch
                }
                currentCoroutineContext().ensureActive()
                if (ticket == generation) {
                    if (isCurrent()) onReady(result) else onStale()
                }
            } finally {
                // An old cancelled operation cannot release a newer operation's gate.
                if (ticket == generation) {
                    inFlight = false
                    job = null
                }
            }
        }
        job = next
        // Release the gate even if a cancelled scope never enters the body.
        next.invokeOnCompletion {
            if (ticket == generation) {
                inFlight = false
                job = null
            }
        }
        next.start()
        return true
    }
}
