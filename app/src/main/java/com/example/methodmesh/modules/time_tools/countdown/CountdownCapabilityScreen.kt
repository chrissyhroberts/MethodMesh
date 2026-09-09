package com.example.methodmesh.modules.time_tools.countdown

import com.example.methodmesh.modules.time_tools.timing.CountdownViewModel
import com.example.methodmesh.modules.time_tools.timing.TimeFormatting
import com.example.methodmesh.modules.time_tools.timing.TimeResultPayloads
import com.example.methodmesh.modules.time_tools.timing.TimerStatus

/**
 * Repository-neutral presentation contract for the countdown capability.
 *
 * The current MethodMesh repository should bind this controller to its normal capability
 * screen mechanism (Compose, Views, or another renderer). Keeping the controller free of
 * UI-framework imports prevents this module from forcing Compose dependencies into core.
 */
class CountdownCapabilityScreen(
    initialDurationMs: Long = 5 * 60_000L,
    private val model: CountdownViewModel = CountdownViewModel()
) {
    init {
        if (model.state == null) model.configure(initialDurationMs)
    }

    data class UiState(
        val formattedRemaining: String,
        val status: TimerStatus,
        val canStart: Boolean,
        val canPause: Boolean,
        val canResume: Boolean,
        val canCancel: Boolean,
        val canAdjust: Boolean,
        val remainingMs: Long,
        val requestedDurationMs: Long
    )

    fun uiState(): UiState {
        val state = requireNotNull(model.refresh())
        return UiState(
            formattedRemaining = TimeFormatting.duration(model.remainingMs()),
            status = state.status,
            canStart = state.status == TimerStatus.Configured,
            canPause = state.status == TimerStatus.Running,
            canResume = state.status == TimerStatus.Paused,
            canCancel = state.status == TimerStatus.Running || state.status == TimerStatus.Paused,
            canAdjust = state.status == TimerStatus.Configured || state.status == TimerStatus.Running || state.status == TimerStatus.Paused,
            remainingMs = model.remainingMs(),
            requestedDurationMs = state.requestedDurationMs
        )
    }

    fun start() = model.start()
    fun pause() = model.pause()
    fun resume() = model.resume()
    fun cancel() = model.cancel()
    fun addMinute() = model.addTime(60_000L)
    fun subtractMinute() = model.addTime(-60_000L)
    fun addSeconds(seconds: Long) = model.addTime(seconds * 1_000L)
    fun setDuration(durationMs: Long) = model.setDuration(durationMs)

    fun resultPayload(): Map<String, Any?>? {
        val state = model.refresh() ?: return null
        return if (state.status == TimerStatus.Completed) {
            TimeResultPayloads.countdown(state, model.elapsedMs())
        } else null
    }

    fun completedBeefOrNull(): String? {
        val state = model.refresh() ?: return null
        return if (state.status == TimerStatus.Completed) {
            TimeFormatting.duration(state.finalElapsedMs ?: state.requestedDurationMs)
        } else null
    }
}
