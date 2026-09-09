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
        val end = anchor.plus(horizon)
        return bundle.tasks.flatMap { task -> occurrences(task, anchor, end) }
            .sortedBy { it.scheduledAt }
    }

    fun next(task: CronTask, initiatedAt: ZonedDateTime): ZonedDateTime {
        val anchor = initiatedAt.plus(if (task.timing == ScheduleTimingMode.RELATIVE) task.relativeOffset else Duration.ZERO)
        return CronSchedule.next(task.cronExpression, anchor.minusMinutes(1))
    }

    private fun occurrences(task: CronTask, anchor: ZonedDateTime, end: ZonedDateTime): List<CronTaskOccurrence> {
        val firstAnchor = anchor.plus(if (task.timing == ScheduleTimingMode.RELATIVE) task.relativeOffset else Duration.ZERO)
        val result = mutableListOf<CronTaskOccurrence>()
        var after = firstAnchor.minusMinutes(1)
        while (true) {
            val next = CronSchedule.next(task.cronExpression, after)
            if (next.isAfter(end)) break
            result += CronTaskOccurrence(task.id, task.name, next)
            if (task.maxOccurrences != null && result.size >= task.maxOccurrences) break
            after = next
        }
        return result
    }
}
