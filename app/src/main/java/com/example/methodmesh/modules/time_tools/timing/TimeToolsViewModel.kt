package com.example.methodmesh.modules.time_tools.timing

/**
 * Dependency-light state holders for MethodMesh UI bindings.
 *
 * These intentionally do not extend androidx.lifecycle.ViewModel and do not depend on
 * Compose or coroutines. The host MethodMesh capability screen can retain them using
 * whatever lifecycle/state mechanism the repository already uses.
 */
class CountdownViewModel(
    private val engine: TimerEngine = TimerEngine()
) {
    var state: MonotonicTimerState? = null
        private set

    fun configure(durationMs: Long, label: String? = null) {
        state = engine.newCountdown(durationMs, label)
    }

    fun start() { state = state?.let(engine::start) }
    fun pause() { state = state?.let(engine::pause) }
    fun resume() { state = state?.let(engine::resume) }
    fun cancel() { state = state?.let(engine::cancel) }
    fun addTime(deltaMs: Long) {
        state = state?.let {
            if (it.status == TimerStatus.Configured) {
                it.copy(requestedDurationMs = (it.requestedDurationMs + deltaMs).coerceAtLeast(1_000L))
            } else {
                engine.addDuration(it, deltaMs)
            }
        }
    }

    fun setDuration(durationMs: Long) {
        val current = state
        require(current == null || current.status == TimerStatus.Configured) { "Duration can only be changed before start" }
        configure(durationMs.coerceAtLeast(1_000L), current?.label)
    }

    /** Refresh before rendering/serialising so completion is observed authoritatively. */
    fun refresh(): MonotonicTimerState? {
        state = state?.let(engine::refresh)
        return state
    }

    fun remainingMs(): Long = state?.let(engine::remainingMs) ?: 0L
    fun elapsedMs(): Long = state?.let(engine::elapsedMs) ?: 0L
}

class StopwatchViewModel(
    private val engine: TimerEngine = TimerEngine()
) {
    var state: StopwatchState = engine.newStopwatch()
        private set

    fun start() { state = engine.start(state) }
    fun pause() { state = engine.pause(state) }
    fun resume() { state = engine.resume(state) }
    fun lap() { state = engine.lap(state) }
    fun stop() { state = engine.stop(state) }
    fun cancel() { state = engine.cancel(state) }
    fun elapsedMs(): Long = engine.stopwatchElapsedMs(state)
}
