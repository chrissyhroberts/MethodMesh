package com.example.methodmesh.modules.time_tools.timing

import java.time.Instant

sealed interface TimerStatus {
    data object Configured : TimerStatus
    data object Running : TimerStatus
    data object Paused : TimerStatus
    data object Completed : TimerStatus
    data object Cancelled : TimerStatus
    data class Failed(val diagnostic: String) : TimerStatus
}

data class MonotonicTimerState(
    val id: String,
    val label: String? = null,
    val requestedDurationMs: Long,
    val startedElapsedRealtimeMs: Long? = null,
    val startedAt: Instant? = null,
    val pausedAtElapsedRealtimeMs: Long? = null,
    val accumulatedPauseMs: Long = 0L,
    val completedAt: Instant? = null,
    val finalElapsedMs: Long? = null,
    val status: TimerStatus = TimerStatus.Configured
)

data class Lap(
    val index: Int,
    val lapDurationMs: Long,
    val totalDurationMs: Long
)

data class StopwatchState(
    val id: String,
    val label: String? = null,
    val startedElapsedRealtimeMs: Long? = null,
    val startedAt: Instant? = null,
    val pausedAtElapsedRealtimeMs: Long? = null,
    val accumulatedPauseMs: Long = 0L,
    val laps: List<Lap> = emptyList(),
    val completedAt: Instant? = null,
    val finalElapsedMs: Long? = null,
    val status: TimerStatus = TimerStatus.Configured
)

data class IntervalPhase(
    val label: String,
    val durationMs: Long,
    val alertOnStart: Boolean = true
)

data class IntervalProgram(
    val phases: List<IntervalPhase>,
    val cycles: Int = 1,
    val label: String? = null
) {
    init {
        require(phases.isNotEmpty()) { "Interval program must contain at least one phase" }
        require(phases.all { it.durationMs > 0 }) { "All interval durations must be positive" }
        require(cycles > 0) { "Cycles must be positive" }
    }

    val totalDurationMs: Long
        get() = phases.sumOf { it.durationMs } * cycles
}

data class IntervalPosition(
    val cycleIndex: Int,
    val phaseIndex: Int,
    val phase: IntervalPhase,
    val elapsedInPhaseMs: Long,
    val remainingInPhaseMs: Long,
    val elapsedTotalMs: Long,
    val remainingTotalMs: Long,
    val complete: Boolean
)
