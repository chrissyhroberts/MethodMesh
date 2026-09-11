package com.example.methodmesh.modules.time_tools.timing

/**
 * Zero-dependency contract checks that can run without JUnit.
 * The host repository can wrap these cases in its own test framework after admission.
 */
object TimerEngineSelfTest {
    private class FakeClock(var time: Long = 0L) : MonotonicClock {
        override fun nowMs(): Long = time
    }

    fun runAll() {
        countdownUsesMonotonicTime()
        pauseIsExcluded()
        intervalFindsCorrectPhase()
    }

    fun countdownUsesMonotonicTime() {
        val clock = FakeClock(1_000)
        val engine = TimerEngine(clock)
        var timer = engine.start(engine.newCountdown(10_000))
        clock.time = 4_000
        check(engine.elapsedMs(timer) == 3_000L)
        check(engine.remainingMs(timer) == 7_000L)
        clock.time = 11_000
        timer = engine.refresh(timer)
        check(timer.status == TimerStatus.Completed)
    }

    fun pauseIsExcluded() {
        val clock = FakeClock(0)
        val engine = TimerEngine(clock)
        var timer = engine.start(engine.newCountdown(10_000))
        clock.time = 2_000
        timer = engine.pause(timer)
        clock.time = 7_000
        timer = engine.resume(timer)
        clock.time = 9_000
        check(engine.elapsedMs(timer) == 4_000L)
    }

    fun intervalFindsCorrectPhase() {
        val engine = TimerEngine(FakeClock())
        val program = IntervalProgram(
            listOf(IntervalPhase("A", 1_000), IntervalPhase("B", 2_000)),
            cycles = 2
        )
        val position = engine.intervalPosition(program, 3_500)
        check(position.cycleIndex == 1)
        check(position.phaseIndex == 0)
        check(position.elapsedInPhaseMs == 500L)
    }
}
