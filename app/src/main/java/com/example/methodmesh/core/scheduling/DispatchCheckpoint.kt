package com.example.methodmesh.core.scheduling

/** Small saved-state checkpoint; execution payloads remain outside Android's instance-state Bundle. */
internal data class DispatchCheckpoint(
    val phase: Phase = Phase.New,
    val initialised: Boolean = false,
    val resumePhase: Phase = Phase.Intro
) {
    enum class Phase {
        New, Intro, Ready, AwaitingResult, ResultReceived, ManualCompletion,
        CancelConfirmation, Summary, Finished, Interrupted
    }

    fun initialise(): DispatchCheckpoint = if (initialised) this else copy(initialised = true)

    fun beginChild(): DispatchCheckpoint? =
        if (phase == Phase.Ready) copy(phase = Phase.AwaitingResult) else null

    fun takeResult(): DispatchCheckpoint? =
        if (phase == Phase.AwaitingResult) copy(phase = Phase.ResultReceived) else null

    fun showCancel(): DispatchCheckpoint = copy(phase = Phase.CancelConfirmation, resumePhase = phase)

    fun keepGoing(): DispatchCheckpoint = copy(phase = resumePhase)

    companion object {
        fun restore(phase: String?, initialised: Boolean, resumePhase: String?): DispatchCheckpoint {
            fun parse(value: String?) = Phase.entries.firstOrNull { it.name == value }
            return DispatchCheckpoint(
                phase = parse(phase) ?: Phase.Interrupted,
                initialised = initialised,
                resumePhase = parse(resumePhase) ?: Phase.Intro
            )
        }
    }
}
