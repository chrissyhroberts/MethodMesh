package com.example.methodmesh.modules.signals

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.workflow.ui.rememberRetainedExecutionResult

/**
 * One original execution for native display, export and caller return. Only the
 * small committed marker is saved; an unavailable envelope is never rebuilt from
 * display values or mistaken for a new physical acquisition.
 */
internal class SignalCommitState(
    private val execution: MutableState<ExecutionResult?>,
    private val hadCommit: MutableState<Boolean>
) {
    val result: ExecutionResult? get() = execution.value
    // Signals methods produce one canonical observation. Do not expand this into
    // generic transport fields: existing native text exports use these entries.
    val fields: Map<String, String> get() = result?.observations?.singleOrNull()?.values.orEmpty()
    val missing: Boolean get() = hadCommit.value && result == null

    fun capture(result: ExecutionResult): ExecutionResult {
        execution.value = result
        hadCommit.value = true
        return result
    }

    fun clear() {
        execution.value = null
        hadCommit.value = false
    }
}

@Composable
internal fun rememberSignalCommit(scope: String): SignalCommitState {
    val execution = rememberRetainedExecutionResult("signals:$scope")
    val marker = rememberSaveable(scope) { mutableStateOf(false) }
    return remember(execution, marker) { SignalCommitState(execution, marker) }
}

@Composable
internal fun SignalCommitRecoveryNotice(commit: SignalCommitState) {
    if (commit.missing) Text(
        "Committed result unavailable after restart. The original record cannot be shared or returned. " +
            "Explicitly Commit again to create a new record from the current working state; this does not restore the original execution.",
        color = SignalAmber
    )
}
