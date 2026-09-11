package com.example.methodmesh.modules.time_tools.timing

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

enum class AlarmRepeat { ONCE, DAILY, WEEKDAYS, WEEKENDS, WEEKLY, CUSTOM }

data class AlarmDefinition(
    val time: LocalTime,
    val zoneId: String = ZoneId.systemDefault().id,
    val repeat: AlarmRepeat = AlarmRepeat.ONCE,
    val date: LocalDate? = null,
    val weekdays: Set<Int> = emptySet()
)

object AlarmSchedule {
    fun next(definition: AlarmDefinition, after: ZonedDateTime = ZonedDateTime.now(ZoneId.of(definition.zoneId))): ZonedDateTime? {
        val zone = ZoneId.of(definition.zoneId)
        val base = after.withZoneSameInstant(zone)
        return when (definition.repeat) {
            AlarmRepeat.ONCE -> {
                val date = definition.date ?: base.toLocalDate()
                val candidate = date.atTime(definition.time).atZone(zone)
                when {
                    candidate.isAfter(base) -> candidate
                    definition.date == null -> candidate.plusDays(1)
                    else -> null
                }
            }
            AlarmRepeat.DAILY -> nextMatchingDay(base, definition.time) { true }
            AlarmRepeat.WEEKDAYS -> nextMatchingDay(base, definition.time) { it.dayOfWeek.value in 1..5 }
            AlarmRepeat.WEEKENDS -> nextMatchingDay(base, definition.time) { it.dayOfWeek.value in setOf(6, 7) }
            AlarmRepeat.WEEKLY -> {
                val wanted = definition.weekdays.firstOrNull()?.coerceIn(1, 7) ?: base.dayOfWeek.value
                nextMatchingDay(base, definition.time) { it.dayOfWeek.value == wanted }
            }
            AlarmRepeat.CUSTOM -> {
                val wanted = definition.weekdays.map { it.coerceIn(1, 7) }.toSet()
                require(wanted.isNotEmpty()) { "Select at least one weekday" }
                nextMatchingDay(base, definition.time) { it.dayOfWeek.value in wanted }
            }
        }
    }

    private fun nextMatchingDay(
        after: ZonedDateTime,
        time: LocalTime,
        predicate: (LocalDate) -> Boolean
    ): ZonedDateTime {
        var date = after.toLocalDate()
        repeat(370) {
            if (predicate(date)) {
                val candidate = date.atTime(time).atZone(after.zone)
                if (candidate.isAfter(after)) return candidate
            }
            date = date.plusDays(1)
        }
        error("No alarm occurrence found within one year")
    }

    fun parseWeekdays(raw: String): Set<Int> = raw
        .split('|', ',', ';', ' ')
        .mapNotNull { token -> token.trim().takeIf { it.isNotBlank() }?.toIntOrNull() }
        .map { it.coerceIn(1, 7) }
        .toSet()

    fun weekdayLabel(days: Set<Int>): String = days.sorted().joinToString(" · ") { day ->
        DayOfWeek.of(day.coerceIn(1, 7)).name.take(3).lowercase().replaceFirstChar { it.uppercase() }
    }
}
