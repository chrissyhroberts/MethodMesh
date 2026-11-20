package com.example.methodmesh.transport.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.methodmesh.core.ResearchRuntime
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.withInvocationContext
import com.example.methodmesh.transport.workflow.ExternalActionRequest
import com.example.methodmesh.transport.workflow.ExternalWorkflowRequest
import com.example.methodmesh.transport.workflow.ExternalWorkflowProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** In-memory ownership of one caller's run; no result payload is written to a Bundle. */
internal class ExternalWorkflowViewModel : ViewModel() {
    var progress by mutableStateOf(ExternalWorkflowProgress())
    val genericResults = mutableStateMapOf<String, ExecutionResult>()
    val logWarnings = mutableStateMapOf<String, String>()
    private val pendingConfirmations = mutableSetOf<String>()
    var resultReturned = false
    var interrupted by mutableStateOf(false)
        private set
    private var initialised = false

    fun confirmStep(key: String, action: ExternalActionRequest, request: ExternalWorkflowRequest,
                    result: ExecutionResult, log: suspend (ExecutionResult) -> String?) {
        if (resultReturned || key != progress.stepKey || !pendingConfirmations.add(key)) return
        // The ViewModel, not the departing composition/activity, owns closeout.
        // Rotation must not cancel logging and leave a manual screen stranded.
        viewModelScope.launch {
            try {
                val recorded = ResearchRuntime.session.record(result.withInvocationContext(request.invocationContext))
                val warning = try { log(recorded) } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    "Persistent log could not be saved completely. Captured result returned unchanged."
                }
                if (!resultReturned && key == progress.stepKey) {
                    if (warning != null) logWarnings[recorded.request.id.value] = warning
                    progress = progress.confirm(key, action, recorded)
                }
            } finally { pendingConfirmations.remove(key) }
        }
    }

    fun logWarning(): String? {
        val ids = progress.confirmed.take(progress.index).map { it.result.request.id.value }.toSet()
        return logWarnings.filterKeys { it in ids }.takeIf { it.isNotEmpty() }?.entries
            ?.joinToString("\n") { (id, message) -> "$id: $message" }
    }

    fun initialise(restoringActivity: Boolean) {
        if (initialised) return
        // Ordinary recreation retains this ViewModel. A new ViewModel with saved
        // activity state means process recovery lost the run. Require caller retry
        // rather than silently replaying hardware/network actions from step one.
        interrupted = restoringActivity
        initialised = true
    }
}
