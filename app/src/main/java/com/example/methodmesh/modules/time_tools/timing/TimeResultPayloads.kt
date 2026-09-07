package com.example.methodmesh.modules.time_tools.timing

import java.time.Duration
import java.time.Instant

object TimeResultPayloads {
    fun countdown(state: MonotonicTimerState, elapsedMs: Long): Map<String, Any?> {
        val formatted = TimeFormatting.duration(elapsedMs)
        return linkedMapOf(
            "formatted_duration" to formatted,
            "duration_ms" to elapsedMs,
            "requested_duration_ms" to state.requestedDurationMs,
            "actual_elapsed_ms" to elapsedMs,
            "started_at" to state.startedAt?.toString(),
            "completed_at" to state.completedAt?.toString(),
            "completion_status" to statusName(state.status)
        )
    }

    fun stopwatch(state: StopwatchState, elapsedMs: Long): Map<String, Any?> {
        val lapsJson = state.laps.joinToString(prefix = "[", postfix = "]") { lap ->
            jsonObject(
                linkedMapOf(
                    "index" to lap.index,
                    "lap_duration_ms" to lap.lapDurationMs,
                    "total_duration_ms" to lap.totalDurationMs,
                    "lap_formatted" to TimeFormatting.duration(lap.lapDurationMs, true),
                    "total_formatted" to TimeFormatting.duration(lap.totalDurationMs, true)
                )
            )
        }
        return linkedMapOf(
            "formatted_duration" to TimeFormatting.duration(elapsedMs, true),
            "duration_ms" to elapsedMs,
            "lap_count" to state.laps.size,
            "laps_json" to lapsJson,
            "started_at" to state.startedAt?.toString(),
            "completed_at" to state.completedAt?.toString(),
            "completion_status" to statusName(state.status)
        )
    }

    fun elapsed(start: Instant, end: Instant): Map<String, Any?> {
        val duration = Duration.between(start, end)
        return linkedMapOf(
            "formatted_duration" to TimeFormatting.duration(duration.toMillis()),
            "duration_ms" to duration.toMillis(),
            "start_timestamp" to start.toString(),
            "end_timestamp" to end.toString()
        )
    }

    fun fullJson(core: Map<String, Any?>, methodId: String): String {
        val payload = linkedMapOf<String, Any?>(
            "schema" to "methodmesh.time_tools.v1",
            "method_id" to methodId,
            "generated_at" to Instant.now().toString()
        )
        payload.putAll(core)
        return jsonObject(payload)
    }

    private fun statusName(status: TimerStatus): String = when (status) {
        TimerStatus.Configured -> "configured"
        TimerStatus.Running -> "running"
        TimerStatus.Paused -> "paused"
        TimerStatus.Completed -> "completed"
        TimerStatus.Cancelled -> "cancelled"
        is TimerStatus.Failed -> "failed"
    }

    private fun jsonObject(values: Map<String, Any?>): String =
        values.entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
            "${jsonString(key)}:${jsonValue(value)}"
        }

    private fun jsonValue(value: Any?): String = when (value) {
        null -> "null"
        is Number, is Boolean -> value.toString()
        is String -> jsonString(value)
        else -> jsonString(value.toString())
    }

    private fun jsonString(value: String): String = buildString(value.length + 2) {
        append('"')
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (ch.code < 0x20) append("\\u%04x".format(ch.code)) else append(ch)
            }
        }
        append('"')
    }
}
