package com.example.methodmesh.modules.time_tools.until

import com.example.methodmesh.modules.time_tools.timing.TimeFormatting
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object UntilTimeCapability {
    fun execute(targetTimestamp: String, now: Instant = Instant.now()): Map<String, Any?> {
        val target = Instant.parse(targetTimestamp)
        val remaining = Duration.between(now, target)
        return linkedMapOf(
            "target_timestamp" to target.toString(),
            "formatted_duration" to TimeFormatting.duration(remaining.toMillis().coerceAtLeast(0L)),
            "remaining_ms" to remaining.toMillis().coerceAtLeast(0L),
            "completion_status" to if (remaining.isNegative || remaining.isZero) "completed" else "pending"
        )
    }

    fun nextLocalTime(localTime: String, zoneId: String = ZoneId.systemDefault().id): String {
        val zone = ZoneId.of(zoneId)
        val now = ZonedDateTime.now(zone)
        val time = LocalTime.parse(localTime)
        val today = now.toLocalDate().atTime(time).atZone(zone)
        return (if (today.isAfter(now)) today else today.plusDays(1)).toInstant().toString()
    }
}
