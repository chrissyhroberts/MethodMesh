package com.example.methodmesh.modules.time_tools.stopwatch

import com.example.methodmesh.modules.time_tools.timing.Lap
import com.example.methodmesh.modules.time_tools.timing.StopwatchViewModel
import com.example.methodmesh.modules.time_tools.timing.TimeFormatting
import com.example.methodmesh.modules.time_tools.timing.TimerStatus

/** Repository-neutral presentation contract for the stopwatch capability. */
class StopwatchCapabilityScreen(
    private val model: StopwatchViewModel = StopwatchViewModel()
) {
    data class UiState(
        val formattedElapsed: String,
        val status: TimerStatus,
        val laps: List<Lap>,
        val canStart: Boolean,
        val canLap: Boolean,
        val canPause: Boolean,
        val canResume: Boolean,
        val canStop: Boolean
    )

    fun uiState(): UiState {
        val state = model.state
        return UiState(
            formattedElapsed = TimeFormatting.duration(model.elapsedMs(), true),
            status = state.status,
            laps = state.laps,
            canStart = state.status == TimerStatus.Configured,
            canLap = state.status == TimerStatus.Running,
            canPause = state.status == TimerStatus.Running,
            canResume = state.status == TimerStatus.Paused,
            canStop = state.status == TimerStatus.Running || state.status == TimerStatus.Paused
        )
    }

    fun start() = model.start()
    fun lap() = model.lap()
    fun pause() = model.pause()
    fun resume() = model.resume()
    fun stop() = model.stop()
    fun cancel() = model.cancel()

    fun completedBeefOrNull(): String? =
        if (model.state.status == TimerStatus.Completed) {
            TimeFormatting.duration(model.elapsedMs(), true)
        } else null
}
