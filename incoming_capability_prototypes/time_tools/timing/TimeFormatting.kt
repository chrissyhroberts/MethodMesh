package com.example.methodmesh.modules.time_tools.timing

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

object TimeFormatting {
    fun duration(milliseconds: Long, centiseconds: Boolean = false): String {
        val safe = milliseconds.coerceAtLeast(0L)
        val hours = safe / 3_600_000L
        val minutes = (safe % 3_600_000L) / 60_000L
        val seconds = (safe % 60_000L) / 1_000L
        val cs = (safe % 1_000L) / 10L
        return when {
            hours > 0 && centiseconds -> "%02d:%02d:%02d.%02d".format(hours, minutes, seconds, cs)
            hours > 0 -> "%02d:%02d:%02d".format(hours, minutes, seconds)
            centiseconds -> "%02d:%02d.%02d".format(minutes, seconds, cs)
            else -> "%02d:%02d".format(minutes, seconds)
        }
    }

    fun duration(d: Duration): String = duration(d.toMillis())

    fun longRange(target: Instant, now: Instant = Instant.now(), zoneId: String = ZoneId.systemDefault().id): String {
        if (!target.isAfter(now)) return "00:00"
        val zone = ZoneId.of(zoneId)
        var cursor = now.atZone(zone)
        val end = target.atZone(zone)
        var months = ChronoUnit.MONTHS.between(cursor, end).coerceAtLeast(0)
        if (cursor.plusMonths(months).isAfter(end)) months -= 1
        cursor = cursor.plusMonths(months)
        val days = ChronoUnit.DAYS.between(cursor, end).coerceAtLeast(0)
        cursor = cursor.plusDays(days)
        val remainder = Duration.between(cursor, end).coerceAtLeast(Duration.ZERO)
        val h = remainder.toHours()
        val m = remainder.minusHours(h).toMinutes()
        val s = remainder.minusHours(h).minusMinutes(m).seconds
        val parts = mutableListOf<String>()
        if (months > 0) parts += "$months ${if (months == 1L) "month" else "months"}"
        if (days > 0 || months > 0) parts += "$days ${if (days == 1L) "day" else "days"}"
        parts += "%02d:%02d:%02d".format(h, m, s)
        return parts.joinToString("  ")
    }

    fun conciseLongRange(target: Instant, now: Instant = Instant.now(), zoneId: String = ZoneId.systemDefault().id): String {
        if (!target.isAfter(now)) return "Due now"
        val zone = ZoneId.of(zoneId)
        val start = now.atZone(zone)
        val end = target.atZone(zone)
        val months = ChronoUnit.MONTHS.between(start, end).coerceAtLeast(0)
        if (months > 0) return "$months mo ${ChronoUnit.DAYS.between(start.plusMonths(months), end).coerceAtLeast(0)} d"
        val days = ChronoUnit.DAYS.between(start, end).coerceAtLeast(0)
        if (days > 0) return "$days d ${Duration.between(start.plusDays(days), end).toHours()} h"
        return duration(Duration.between(now, target).toMillis())
    }
}
