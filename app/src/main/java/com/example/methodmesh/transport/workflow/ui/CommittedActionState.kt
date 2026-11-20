package com.example.methodmesh.transport.workflow.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Main-thread state only: no execution, Activity, callback or file bytes in saved state. */
internal class CommittedActionState {
    var busy by mutableStateOf(false)
        private set
    var status by mutableStateOf("")
        private set
    var finishPending by mutableStateOf(false)
        private set
    var finished by mutableStateOf(false)
        private set

    fun start(scope: CoroutineScope, finishing: Boolean, failureLabel: String, before: () -> Unit = {}, operation: suspend () -> String): Boolean {
        if (busy || finishPending || finished) return false
        busy = true
        try { before() } catch (failure: Exception) {
            busy = false
            status = failureLabel + ": " + (failure.message ?: "operation unavailable")
            return true
        }
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                status = operation()
                currentCoroutineContext().ensureActive()
                finishPending = finishing
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                currentCoroutineContext().ensureActive()
                status = failureLabel + ": " + (failure.message ?: "operation unavailable")
            } finally {
                busy = false
            }
        }
        job.invokeOnCompletion { busy = false }
        job.start()
        return true
    }

    /** Consumed by the current composition, never a callback retained by an old Activity. */
    fun takeFinish(): Boolean {
        if (!finishPending || finished) return false
        finishPending = false
        finished = true
        return true
    }
}

internal class CommittedActionStore : ViewModel() {
    private val slots = mutableMapOf<String, CommittedActionState>()
    fun slot(key: String) = slots.getOrPut(key) { CommittedActionState() }
    fun forget(key: String) { slots.remove(key) }
    fun start(state: CommittedActionState, finishing: Boolean, failureLabel: String, before: () -> Unit = {}, operation: suspend () -> String) =
        state.start(viewModelScope, finishing, failureLabel, before, operation)
}
