package com.example.methodmesh.modules.time_tools.duration

import com.example.methodmesh.modules.time_tools.timing.TimeFormatting
import java.time.Duration

object DurationCalculatorCapability {
    enum class Operation { ADD, SUBTRACT }

    fun execute(aMs: Long, bMs: Long, operation: Operation): Map<String, Any?> {
        val a = Duration.ofMillis(aMs)
        val b = Duration.ofMillis(bMs)
        val result = when (operation) {
            Operation.ADD -> a.plus(b)
            Operation.SUBTRACT -> a.minus(b)
        }
        return linkedMapOf(
            "formatted_duration" to TimeFormatting.duration(result.toMillis().coerceAtLeast(0L)),
            "duration_ms" to result.toMillis(),
            "operation" to operation.name.lowercase(),
            "input_a_ms" to aMs,
            "input_b_ms" to bMs
        )
    }
}
