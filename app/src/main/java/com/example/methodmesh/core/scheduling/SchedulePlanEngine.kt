package com.example.methodmesh.core.scheduling

import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

/** Pure occurrence generation for plans and instances. It never materialises forever. */
object SchedulePlanEngine {
    fun instantiate(plan: SchedulePlan, anchoredAt: ZonedDateTime, spec: ScheduleGenerationSpec = ScheduleGenerationSpec(now = anchoredAt)): ScheduleInstance {
        val anchor = anchoredAt.withZoneSameInstant(plan.timezone)
        val end = terminationEnd(plan.termination, anchor, spec.horizon)
        val instanceId = java.util.UUID.randomUUID().toString()
        val perRuleLimit = when (plan.termination.mode) {
            ScheduleEndMode.OCCURRENCE_COUNT -> plan.termination.occurrenceCount
            ScheduleEndMode.FOREVER -> 2048
            ScheduleEndMode.DURATION, ScheduleEndMode.ABSOLUTE -> 4096
            else -> null
        }
        val occurrences = plan.rules.flatMap { rule -> generateRule(plan, rule, anchor, end, spec.horizon, instanceId, perRuleLimit) }
            .sortedBy { it.scheduledAt }
            .let { generated ->
                val limit = plan.termination.occurrenceCount
                if (limit == null) generated else generated.take(limit)
            }
        return ScheduleInstance(planId = plan.id, planVersion = plan.version, anchoredAt = anchor, effectiveEnd = end, occurrences = occurrences)
    }

    private fun terminationEnd(termination: ScheduleTermination, anchor: ZonedDateTime, horizon: Duration): ZonedDateTime = when (termination.mode) {
        ScheduleEndMode.DURATION -> anchor.plus(termination.duration!!)
        ScheduleEndMode.ABSOLUTE -> termination.absoluteEnd!!.withZoneSameInstant(anchor.zone)
        ScheduleEndMode.FOREVER, ScheduleEndMode.PARENT_SEQUENCE, ScheduleEndMode.OCCURRENCE_COUNT -> anchor.plus(horizon)
    }

    private fun generateRule(plan: SchedulePlan, rule: ScheduleRule, anchor: ZonedDateTime, end: ZonedDateTime, horizon: Duration, instanceId: String, limitHint: Int?): List<ScheduleOccurrence> {
        val lane = plan.lanes.first { it.id == rule.laneId }
        val actions = rule.actions ?: lane.defaultActions
        val times = when (val timing = rule.timing) {
            is ScheduleTimingRule.Once -> once(anchor, timing, end)
            is ScheduleTimingRule.ElapsedInterval -> elapsedInterval(anchor, timing, end, limitHint)
            is ScheduleTimingRule.AnchoredCalendarDays -> anchoredCalendarDays(anchor, timing, end, limitHint)
            is ScheduleTimingRule.RelativeDays -> relativeDays(anchor, timing, end, lane.missedStartPolicy)
            is ScheduleTimingRule.Weekly -> weekly(anchor, timing, end, lane.missedStartPolicy)
            is ScheduleTimingRule.MonthlyDayOfMonth -> monthlyDay(anchor, timing, end, lane.missedStartPolicy)
            is ScheduleTimingRule.MonthlyNthWeekday -> monthlyNth(anchor, timing, end, lane.missedStartPolicy)
            is ScheduleTimingRule.IntradayInterval -> intraday(anchor, timing, end, lane.missedStartPolicy, limitHint)
            is ScheduleTimingRule.Cron -> cron(anchor, timing, end, limitHint)
        }
        return times.map { scheduled ->
            val before = rule.timing.windowBefore() ?: lane.defaultWindowBefore
            val after = rule.timing.windowAfter() ?: lane.defaultWindowAfter
            ScheduleOccurrence(
                instanceId = instanceId,
                laneId = lane.id,
                laneName = lane.name,
                scheduledAt = scheduled,
                windowOpen = before?.let { scheduled.minus(it) },
                windowClose = after?.let { scheduled.plus(it) },
                actions = actions
            )
        }
    }


    private fun once(anchor: ZonedDateTime, timing: ScheduleTimingRule.Once, end: ZonedDateTime): List<ZonedDateTime> {
        val due = anchor.plus(timing.offset)
        return if (due <= end) listOf(due) else emptyList()
    }

    private fun elapsedInterval(anchor: ZonedDateTime, timing: ScheduleTimingRule.ElapsedInterval, end: ZonedDateTime, limitHint: Int?): List<ZonedDateTime> {
        val result = mutableListOf<ZonedDateTime>()
        var candidate = if (timing.runImmediately) anchor else anchor.plus(timing.interval)
        while (candidate <= end && (limitHint == null || result.size < limitHint)) {
            result += candidate
            candidate = candidate.plus(timing.interval)
        }
        return result
    }

    private fun anchoredCalendarDays(anchor: ZonedDateTime, timing: ScheduleTimingRule.AnchoredCalendarDays, end: ZonedDateTime, limitHint: Int?): List<ZonedDateTime> {
        val result = mutableListOf<ZonedDateTime>()
        var candidate = if (timing.runImmediately) anchor else anchor.plusDays(timing.everyDays.toLong())
        while (candidate <= end && (limitHint == null || result.size < limitHint)) {
            result += candidate
            candidate = candidate.plusDays(timing.everyDays.toLong())
        }
        return result
    }

    private fun relativeDays(anchor: ZonedDateTime, timing: ScheduleTimingRule.RelativeDays, end: ZonedDateTime, policy: ScheduleMissedStartPolicy): List<ZonedDateTime> {
        val lastDay = java.time.Duration.between(anchor.toLocalDate().atStartOfDay(anchor.zone), end).toDays().toInt().coerceAtLeast(1)
        return timing.days.filter { it <= lastDay }.map { day -> ZonedDateTime.of(anchor.toLocalDate().plusDays((day - 1).toLong()), timing.time, anchor.zone) }.filter { candidate -> candidate <= end && (candidate >= anchor || policy == ScheduleMissedStartPolicy.RUN_MISSED && candidate.toLocalDate() == anchor.toLocalDate() && candidate.isBefore(anchor)) }
    }

    private fun weekly(anchor: ZonedDateTime, timing: ScheduleTimingRule.Weekly, end: ZonedDateTime, policy: ScheduleMissedStartPolicy): List<ZonedDateTime> {
        val result = mutableListOf<ZonedDateTime>()
        var date = anchor.toLocalDate()
        while (!date.atStartOfDay(anchor.zone).isAfter(end)) {
            if (date.dayOfWeek.value in timing.weekdays) result += ZonedDateTime.of(date, timing.time, anchor.zone)
            date = date.plusDays(1)
        }
        return result.filter { it <= end && (it >= anchor || policy == ScheduleMissedStartPolicy.RUN_MISSED && it.toLocalDate() == anchor.toLocalDate() && it.isBefore(anchor)) }
    }


    private fun monthlyDay(anchor: ZonedDateTime, timing: ScheduleTimingRule.MonthlyDayOfMonth, end: ZonedDateTime, policy: ScheduleMissedStartPolicy): List<ZonedDateTime> {
        val result = mutableListOf<ZonedDateTime>()
        var month = anchor.toLocalDate().withDayOfMonth(1)
        while (!month.atStartOfDay(anchor.zone).isAfter(end)) {
            val day = timing.dayOfMonth.coerceAtMost(month.lengthOfMonth())
            result += ZonedDateTime.of(month.withDayOfMonth(day), timing.time, anchor.zone)
            month = month.plusMonths(1)
        }
        return result.filter { it <= end && (it >= anchor || policy == ScheduleMissedStartPolicy.RUN_MISSED && it.toLocalDate() == anchor.toLocalDate() && it.isBefore(anchor)) }
    }

    private fun monthlyNth(anchor: ZonedDateTime, timing: ScheduleTimingRule.MonthlyNthWeekday, end: ZonedDateTime, policy: ScheduleMissedStartPolicy): List<ZonedDateTime> {
        val result = mutableListOf<ZonedDateTime>()
        var month = anchor.toLocalDate().withDayOfMonth(1)
        while (!month.atStartOfDay(anchor.zone).isAfter(end)) {
            val day = month.with(java.time.temporal.TemporalAdjusters.dayOfWeekInMonth(timing.ordinal, java.time.DayOfWeek.of(timing.weekday)))
            result += ZonedDateTime.of(day, timing.time, anchor.zone)
            month = month.plusMonths(1)
        }
        return result.filter { it <= end && (it >= anchor || policy == ScheduleMissedStartPolicy.RUN_MISSED && it.toLocalDate() == anchor.toLocalDate() && it.isBefore(anchor)) }
    }

    private fun intraday(anchor: ZonedDateTime, timing: ScheduleTimingRule.IntradayInterval, end: ZonedDateTime, policy: ScheduleMissedStartPolicy, limitHint: Int?): List<ZonedDateTime> {
        val result = mutableListOf<ZonedDateTime>()
        var date = anchor.toLocalDate()
        while (!date.atStartOfDay(anchor.zone).isAfter(end)) {
            if (date.dayOfWeek.value in timing.weekdays) {
                var time = timing.firstTime
                while (!time.isAfter(timing.lastTime)) {
                    val candidate = ZonedDateTime.of(date, time, anchor.zone)
                    if (candidate <= end && (candidate >= anchor || policy == ScheduleMissedStartPolicy.RUN_MISSED && candidate.toLocalDate() == anchor.toLocalDate() && candidate.isBefore(anchor))) result += candidate
                    if (limitHint != null && result.size >= limitHint) return result
                    time = time.plus(timing.interval)
                }
            }
            date = date.plusDays(1)
        }
        return result
    }

    private fun cron(anchor: ZonedDateTime, timing: ScheduleTimingRule.Cron, end: ZonedDateTime, limitHint: Int?): List<ZonedDateTime> {
        val start = anchor.plus(if (timing.timing == ScheduleTimingMode.RELATIVE) timing.offset else Duration.ZERO)
        val result = mutableListOf<ZonedDateTime>()
        var after = start.minusMinutes(1)
        while (limitHint == null || result.size < limitHint) {
            val next = CronSchedule.nextOrNull(timing.expression, after) ?: break
            if (next.isAfter(end)) break
            result += next
            after = next
        }
        return result
    }

    private fun ScheduleTimingRule.windowBefore(): Duration? = when (this) {
        is ScheduleTimingRule.RelativeDays -> windowBefore
        is ScheduleTimingRule.Weekly -> windowBefore
        else -> null
    }

    private fun ScheduleTimingRule.windowAfter(): Duration? = when (this) {
        is ScheduleTimingRule.RelativeDays -> windowAfter
        is ScheduleTimingRule.Weekly -> windowAfter
        else -> null
    }
}
