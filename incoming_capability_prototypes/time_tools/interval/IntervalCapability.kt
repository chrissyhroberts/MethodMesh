package com.example.methodmesh.modules.time_tools.interval

import com.example.methodmesh.modules.time_tools.timing.IntervalPhase
import com.example.methodmesh.modules.time_tools.timing.IntervalProgram
import com.example.methodmesh.modules.time_tools.timing.TimerEngine

object IntervalCapability {
    private val engine = TimerEngine()

    fun buildProgram(labels: List<String>, durationsMs: List<Long>, cycles: Int): IntervalProgram {
        require(labels.size == durationsMs.size) { "labels and durations must have equal length" }
        return IntervalProgram(labels.zip(durationsMs) { label, duration -> IntervalPhase(label, duration) }, cycles)
    }

    fun position(program: IntervalProgram, elapsedMs: Long) = engine.intervalPosition(program, elapsedMs)
}
