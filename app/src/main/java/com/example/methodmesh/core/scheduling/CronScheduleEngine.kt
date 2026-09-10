package com.example.methodmesh.core.scheduling

import java.time.Duration
import java.time.ZonedDateTime

data class CronTaskOccurrence(
    val taskId: String,
    val taskName: String,
    val scheduledAt: ZonedDateTime
)

/** Pure cron expansion for a schedule bundle. Android alarms can consume the next result. */
object CronScheduleEngine {
    fun occurrences(
        bundle: CronScheduleBundle,
        initiatedAt: ZonedDateTime,
        horizon: Duration = Duration.ofDays(366)
    ): List<CronTaskOccurrence> {
        val anchor = initiatedAt.withZoneSameInstant(bundle.timezone)
        val horizonEnd = anchor.plus(horizon)
        val configuredEnd = when (val rule = bundle.stopRule) {
            ScheduleStopRule.Never -> null
            is ScheduleStopRule.Absolute -> rule.stopAt.withZoneSameInstant(bundle.timezone)
            is ScheduleStopRule.Relative -> anchor.plus(rule.delay)
        }
        val end = configuredEnd?.takeIf { it.isBefore(horizonEnd) } ?: horizonEnd
        if (!end.isAfter(anchor)) return emptyList()
        return bundle.tasks.flatMap { task -> occurrences(task, anchor, end) }
            .sortedBy { it.scheduledAt }
    }

    fun next(task: CronTask, initiatedAt: ZonedDateTime): ZonedDateTime {
        val anchor = initiatedAt.plus(if (task.timing == ScheduleTimingMode.RELATIVE) task.relativeOffset else Duration.ZERO)
        if (task.recurrence == CronTaskRecurrence.ONCE) return anchor
        return CronSchedule.next(task.cronExpression, anchor.minusMinutes(1))
    }

    private fun occurrences(task: CronTask, anchor: ZonedDateTime, end: ZonedDateTime): List<CronTaskOccurrence> {
        val firstAnchor = anchor.plus(if (task.timing == ScheduleTimingMode.RELATIVE) task.relativeOffset else Duration.ZERO)
        if (task.recurrence == CronTaskRecurrence.ONCE) {
            return if (firstAnchor.isBefore(end)) listOf(CronTaskOccurrence(task.id, task.name, firstAnchor)) else emptyList()
        }
        val result = mutableListOf<CronTaskOccurrence>()
        var after = firstAnchor.minusMinutes(1)
        while (true) {
            val next = CronSchedule.next(task.cronExpression, after)
            if (!next.isBefore(end)) break
            result += CronTaskOccurrence(task.id, task.name, next)
            if (task.maxOccurrences != null && result.size >= task.maxOccurrences) break
            after = next
        }
        return result
    }
}
