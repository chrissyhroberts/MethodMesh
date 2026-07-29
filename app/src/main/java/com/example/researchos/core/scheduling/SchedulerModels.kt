package com.example.researchos.core.scheduling

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

enum class SchedulerTarget { ODK_FORM, WEB_FORM, CAPABILITY, CLIPBOARD }
enum class SchedulerFrequency { HOURLY, DAILY, WEEKLY, MONTHLY, CUSTOM }

data class ResearchSchedule(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val target: SchedulerTarget,
    val targetValue: String,
    val targetSettings: String = "",
    val projectId: String = "",
    val packageName: String = "",
    val chainId: String = "",
    val chainOrder: Int = 0,
    val frequency: SchedulerFrequency,
    val hour: Int,
    val minute: Int,
    val dayOfWeek: Int = 1,
    val dayOfMonth: Int = 1,
    val ordinal: Int = 1,
    val customWeekday: Int = 1,
    val retryCount: Int = 0,
    val retryIntervalMinutes: Int = 60,
    val retryWindowMinutes: Int = 1440,
    val notificationTitle: String = "ResearchOS reminder",
    val notificationMessage: String = "A scheduled task is due.",
    val enabled: Boolean = true
    ,val cronExpression: String = ""
) {
    fun nextOccurrence(after: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime {
        if (cronExpression.isNotBlank()) return CronSchedule.next(cronExpression, after)
        val zone = after.zone
        val time = LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
        var date = after.toLocalDate()
        repeat(370) {
            val candidate = when (frequency) {
                SchedulerFrequency.HOURLY -> date
                SchedulerFrequency.DAILY -> if (date >= after.toLocalDate()) date else null
                SchedulerFrequency.WEEKLY -> if (date.dayOfWeek.value == dayOfWeek.coerceIn(1, 7)) date else null
                SchedulerFrequency.MONTHLY -> if (date.dayOfMonth == dayOfMonth.coerceIn(1, 31)) date else null
                SchedulerFrequency.CUSTOM -> if (date.dayOfWeek.value == customWeekday.coerceIn(1, 7) &&
                    ((date.dayOfMonth - 1) / 7 + 1) == ordinal.coerceIn(1, 5)) date else null
            }
            if (candidate != null) {
                val result = if (frequency == SchedulerFrequency.HOURLY) {
                    after.withSecond(0).withNano(0).plusHours(if (after.minute < minute) 0 else 1).withMinute(minute)
                } else ZonedDateTime.of(candidate, time, zone)
                if (result.isAfter(after)) return result
            }
            date = date.plusDays(1)
        }
        return after.plusDays(1).withHour(hour).withMinute(minute).withSecond(0).withNano(0)
    }
}

/** Small dependency-free cron evaluator: 5 fields plus MON#2-style nth weekdays. */
object CronSchedule {
    fun next(expression: String, after: ZonedDateTime): ZonedDateTime {
        val fields = expression.trim().split(Regex("\\s+"))
        require(fields.size == 5) { "Cron expression must have 5 fields." }
        var candidate = after.withSecond(0).withNano(0).plusMinutes(1)
        repeat(366 * 24 * 60) {
            val dow = candidate.dayOfWeek.value % 7
            if (matches(fields[0], candidate.minute) && matches(fields[1], candidate.hour) &&
                matches(fields[2], candidate.dayOfMonth) && matches(fields[3], candidate.monthValue) &&
                matchesDay(fields[4], dow, candidate.dayOfMonth)) return candidate
            candidate = candidate.plusMinutes(1)
        }
        error("Cron expression has no occurrence within one year.")
    }

    private fun matches(field: String, value: Int): Boolean = field == "*" || field.split(',').any { token ->
        when {
            token.contains('/') -> {
                val (base, stepText) = token.split('/', limit = 2); val step = stepText.toIntOrNull() ?: return@any false
                val start = if (base == "*") 0 else base.toIntOrNull() ?: return@any false
                value >= start && (value - start) % step == 0
            }
            token.contains('-') -> { val p = token.split('-', limit = 2); value in ((p[0].toIntOrNull() ?: 0)..(p[1].toIntOrNull() ?: -1)) }
            else -> token.toIntOrNull() == value
        }
    }

    private fun matchesDay(field: String, value: Int, dayOfMonth: Int): Boolean {
        val nth = Regex("(\\d+|[A-Za-z]{3})#([1-5])").matchEntire(field)
        if (nth != null) return nth.groupValues[1].toIntOrNull() == value && ((dayOfMonth - 1) / 7 + 1) == nth.groupValues[2].toInt()
        return matches(field, value)
    }
}
