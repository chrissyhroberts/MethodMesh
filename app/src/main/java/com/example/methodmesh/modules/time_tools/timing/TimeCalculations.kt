package com.example.methodmesh.modules.time_tools.timing

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object TimeCalculations {
    fun elapsed(start: Instant, end: Instant): Duration = Duration.between(start, end)

    fun until(target: Instant, now: Instant = Instant.now()): Duration = Duration.between(now, target)

    fun nextOccurrence(
        localTime: LocalTime,
        now: ZonedDateTime = ZonedDateTime.now(),
        zoneId: ZoneId = now.zone
    ): ZonedDateTime {
        val today = ZonedDateTime.of(LocalDate.from(now), localTime, zoneId)
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    fun add(a: Duration, b: Duration): Duration = a.plus(b)
    fun subtract(a: Duration, b: Duration): Duration = a.minus(b)
}
