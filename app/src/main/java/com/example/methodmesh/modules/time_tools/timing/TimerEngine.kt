package com.example.methodmesh.modules.time_tools.timing

import java.time.Instant
import java.util.UUID

class TimerEngine(
    private val monotonicClock: MonotonicClock = AndroidMonotonicClock,
    private val wallClock: WallClock = WallClock()
) {
    fun newCountdown(durationMs: Long, label: String? = null): MonotonicTimerState {
        require(durationMs > 0) { "Duration must be greater than zero" }
        return MonotonicTimerState(
            id = UUID.randomUUID().toString(),
            label = label,
            requestedDurationMs = durationMs
        )
    }

    fun start(state: MonotonicTimerState): MonotonicTimerState {
        require(state.status == TimerStatus.Configured) { "Timer is not configurable" }
        return state.copy(
            startedElapsedRealtimeMs = monotonicClock.nowMs(),
            startedAt = wallClock.now(),
            status = TimerStatus.Running
        )
    }

    fun pause(state: MonotonicTimerState): MonotonicTimerState {
        require(state.status == TimerStatus.Running) { "Timer is not running" }
        return state.copy(
            pausedAtElapsedRealtimeMs = monotonicClock.nowMs(),
            status = TimerStatus.Paused
        )
    }

    fun resume(state: MonotonicTimerState): MonotonicTimerState {
        require(state.status == TimerStatus.Paused) { "Timer is not paused" }
        val pausedAt = requireNotNull(state.pausedAtElapsedRealtimeMs)
        val pause = (monotonicClock.nowMs() - pausedAt).coerceAtLeast(0L)
        return state.copy(
            pausedAtElapsedRealtimeMs = null,
            accumulatedPauseMs = state.accumulatedPauseMs + pause,
            status = TimerStatus.Running
        )
    }

    fun cancel(state: MonotonicTimerState): MonotonicTimerState =
        state.copy(status = TimerStatus.Cancelled, completedAt = wallClock.now())

    fun elapsedMs(state: MonotonicTimerState): Long {
        val start = state.startedElapsedRealtimeMs ?: return 0L
        state.finalElapsedMs?.let { return it }
        val endpoint = state.pausedAtElapsedRealtimeMs ?: monotonicClock.nowMs()
        return (endpoint - start - state.accumulatedPauseMs).coerceAtLeast(0L)
    }

    fun remainingMs(state: MonotonicTimerState): Long =
        (state.requestedDurationMs - elapsedMs(state)).coerceAtLeast(0L)

    fun refresh(state: MonotonicTimerState): MonotonicTimerState {
        if (state.status != TimerStatus.Running) return state
        val elapsed = elapsedMs(state)
        return if (elapsed >= state.requestedDurationMs) {
            state.copy(
                finalElapsedMs = elapsed,
                completedAt = wallClock.now(),
                status = TimerStatus.Completed
            )
        } else state
    }

    fun addDuration(state: MonotonicTimerState, deltaMs: Long): MonotonicTimerState {
        require(state.status == TimerStatus.Running || state.status == TimerStatus.Paused) {
            "Timer must be active"
        }
        val newDuration = (state.requestedDurationMs + deltaMs).coerceAtLeast(1_000L)
        return state.copy(requestedDurationMs = newDuration)
    }

    fun newStopwatch(label: String? = null): StopwatchState =
        StopwatchState(id = UUID.randomUUID().toString(), label = label)

    fun start(state: StopwatchState): StopwatchState {
        require(state.status == TimerStatus.Configured) { "Stopwatch is not configurable" }
        return state.copy(
            startedElapsedRealtimeMs = monotonicClock.nowMs(),
            startedAt = wallClock.now(),
            status = TimerStatus.Running
        )
    }

    fun stopwatchElapsedMs(state: StopwatchState): Long {
        val start = state.startedElapsedRealtimeMs ?: return 0L
        state.finalElapsedMs?.let { return it }
        val endpoint = state.pausedAtElapsedRealtimeMs ?: monotonicClock.nowMs()
        return (endpoint - start - state.accumulatedPauseMs).coerceAtLeast(0L)
    }

    fun pause(state: StopwatchState): StopwatchState {
        require(state.status == TimerStatus.Running)
        return state.copy(pausedAtElapsedRealtimeMs = monotonicClock.nowMs(), status = TimerStatus.Paused)
    }

    fun resume(state: StopwatchState): StopwatchState {
        require(state.status == TimerStatus.Paused)
        val pause = monotonicClock.nowMs() - requireNotNull(state.pausedAtElapsedRealtimeMs)
        return state.copy(
            accumulatedPauseMs = state.accumulatedPauseMs + pause.coerceAtLeast(0L),
            pausedAtElapsedRealtimeMs = null,
            status = TimerStatus.Running
        )
    }

    fun lap(state: StopwatchState): StopwatchState {
        require(state.status == TimerStatus.Running)
        val total = stopwatchElapsedMs(state)
        val previous = state.laps.lastOrNull()?.totalDurationMs ?: 0L
        val lap = Lap(state.laps.size + 1, total - previous, total)
        return state.copy(laps = state.laps + lap)
    }

    fun stop(state: StopwatchState): StopwatchState {
        require(state.status == TimerStatus.Running || state.status == TimerStatus.Paused)
        val elapsed = stopwatchElapsedMs(state)
        return state.copy(
            finalElapsedMs = elapsed,
            completedAt = wallClock.now(),
            status = TimerStatus.Completed
        )
    }

    fun cancel(state: StopwatchState): StopwatchState =
        state.copy(status = TimerStatus.Cancelled, completedAt = wallClock.now())

    fun intervalPosition(program: IntervalProgram, elapsedMs: Long): IntervalPosition {
        val clamped = elapsedMs.coerceAtLeast(0L)
        val total = program.totalDurationMs
        if (clamped >= total) {
            val last = program.phases.last()
            return IntervalPosition(
                cycleIndex = program.cycles - 1,
                phaseIndex = program.phases.lastIndex,
                phase = last,
                elapsedInPhaseMs = last.durationMs,
                remainingInPhaseMs = 0L,
                elapsedTotalMs = total,
                remainingTotalMs = 0L,
                complete = true
            )
        }

        val cycleDuration = program.phases.sumOf { it.durationMs }
        val cycleIndex = (clamped / cycleDuration).toInt()
        var withinCycle = clamped % cycleDuration
        var phaseIndex = 0
        while (withinCycle >= program.phases[phaseIndex].durationMs) {
            withinCycle -= program.phases[phaseIndex].durationMs
            phaseIndex += 1
        }
        val phase = program.phases[phaseIndex]
        return IntervalPosition(
            cycleIndex = cycleIndex,
            phaseIndex = phaseIndex,
            phase = phase,
            elapsedInPhaseMs = withinCycle,
            remainingInPhaseMs = phase.durationMs - withinCycle,
            elapsedTotalMs = clamped,
            remainingTotalMs = total - clamped,
            complete = false
        )
    }
}
