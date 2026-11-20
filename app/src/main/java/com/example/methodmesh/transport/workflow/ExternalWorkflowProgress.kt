package com.example.methodmesh.transport.workflow

import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.OutputFormatter

/** Immutable progress retained by the host across activity recreation. */
internal data class ExternalWorkflowProgress(
    val confirmed: List<ConfirmedWorkflowStep> = emptyList(),
    val index: Int = 0,
    val acceptedIndex: Int = -1,
    val attempt: Long = 0
) {
    val stepKey: String get() = "$index:$attempt"

    fun actionFor(request: ExternalWorkflowRequest): ExternalActionRequest {
        val action = request.actions[index]
        val piped = linkedMapOf<String, String>()
        // A revisited step cannot consume its own old result or later results.
        confirmed.take(index).forEachIndexed { stepIndex, step ->
            OutputFormatter.fields(step.result, includeProvenance = false).forEach { (key, value) ->
                val text = value?.toString().orEmpty()
                if (text.isNotBlank()) {
                    piped["step_${stepIndex + 1}_$key"] = text
                    piped["previous_$key"] = text
                    piped.putIfAbsent(key, text)
                }
            }
        }
        return action.copy(settings = piped + action.settings)
    }

    fun accept(): ExternalWorkflowProgress = copy(acceptedIndex = index)

    fun back(): ExternalWorkflowProgress = if (index == 0) this else copy(
        index = index - 1,
        acceptedIndex = -1,
        attempt = attempt + 1
    )

    fun confirm(
        expectedStepKey: String,
        action: ExternalActionRequest,
        result: ExecutionResult
    ): ExternalWorkflowProgress {
        // Ignore duplicate or late callbacks from a screen that has left the host.
        if (expectedStepKey != stepKey) return this
        return copy(
            confirmed = confirmed.take(index) + ConfirmedWorkflowStep(action, result),
            index = index + 1,
            acceptedIndex = -1,
            attempt = attempt + 1
        )
    }
}
