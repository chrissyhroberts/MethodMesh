package com.example.methodmesh.core.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.ZonedDateTime
import java.time.Duration

object SchedulePlanRuntime {
    fun rescheduleAll(context: Context) {
        SchedulePlanStore.allInstances(context).filter { it.stoppedAt == null }.forEach { armNext(context, it) }
        val now = ZonedDateTime.now()
        SchedulePlanStore.allPlans(context)
            .filter { it.enabled }
            .filter { it.activation != ScheduleActivation.MANUAL_DAY_ONE && SchedulePlanStore.allInstances(context).none { instance -> instance.planId == it.id && instance.stoppedAt == null } }
            .filter { it.startAt == null || !it.startAt.isAfter(now) }
            .forEach { start(context, it, now.withZoneSameInstant(it.timezone)) }
    }

    fun start(context: Context, plan: SchedulePlan, anchoredAt: ZonedDateTime = ZonedDateTime.now(plan.timezone)): ScheduleInstance {
        val instance = SchedulePlanEngine.instantiate(plan, anchoredAt, generationSpec(plan, anchoredAt))
        SchedulePlanStore.saveInstance(context, instance)
        armNext(context, instance)
        return instance
    }

    fun armNext(context: Context, instance: ScheduleInstance) {
        if (instance.stoppedAt != null) return
        var next = instance.occurrences.firstOrNull {
            it.state == ScheduleOccurrenceState.UPCOMING || it.state == ScheduleOccurrenceState.WINDOW_OPEN
        }

        if (next == null) {
            val plan = SchedulePlanStore.plan(context, instance.planId)
            val last = instance.occurrences.maxByOrNull { it.scheduledAt }
            val unfinishedFiniteWindow = last != null && instance.effectiveEnd?.let { end -> last.scheduledAt.isBefore(end) } == true
            val shouldRoll = plan != null && plan.enabled && (
                plan.termination.mode == ScheduleEndMode.FOREVER || unfinishedFiniteWindow
            )
            if (shouldRoll && plan != null) {
                val timing = plan.rules.firstOrNull()?.timing
                val continuationAnchor = when (timing) {
                    is ScheduleTimingRule.ElapsedInterval -> {
                        val base = last?.scheduledAt ?: instance.anchoredAt
                        if (timing.runImmediately) base.plus(timing.interval) else base
                    }
                    is ScheduleTimingRule.AnchoredCalendarDays -> {
                        val base = last?.scheduledAt ?: instance.anchoredAt
                        if (timing.runImmediately) base.plusDays(timing.everyDays.toLong()) else base
                    }
                    is ScheduleTimingRule.Once, is ScheduleTimingRule.RelativeDays -> null
                    else -> (last?.scheduledAt ?: instance.anchoredAt).plusSeconds(1)
                }
                if (continuationAnchor != null) {
                    val continuationPlan = if (unfinishedFiniteWindow && instance.effectiveEnd != null) {
                        plan.copy(
                            termination = ScheduleTermination(
                                ScheduleEndMode.ABSOLUTE,
                                absoluteEnd = instance.effectiveEnd
                            )
                        )
                    } else plan
                    SchedulePlanStore.saveInstance(context, instance.copy(stoppedAt = ZonedDateTime.now(instance.anchoredAt.zone)))
                    start(context, continuationPlan, continuationAnchor)
                }
            }
            return
        }

        val at = next.windowOpen ?: next.scheduledAt
        val intent = Intent(context, SchedulePlanAlarmReceiver::class.java).setAction(SchedulePlanAlarmReceiver.ACTION)
            .putExtra("instance_id", instance.id).putExtra("occurrence_id", next.id)
        val pending = PendingIntent.getBroadcast(context, requestCode(instance.id, next.id), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val alarm = context.getSystemService(AlarmManager::class.java)
        runCatching { alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toInstant().toEpochMilli(), pending) }
            .onFailure { alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toInstant().toEpochMilli(), pending) }
    }

    fun snooze(context: Context, instance: ScheduleInstance, occurrence: ScheduleOccurrence, minutes: Int) {
        val at = ZonedDateTime.now().plusMinutes(minutes.coerceAtLeast(1).toLong())
        val intent = Intent(context, SchedulePlanAlarmReceiver::class.java).setAction(SchedulePlanAlarmReceiver.ACTION)
            .putExtra("instance_id", instance.id).putExtra("occurrence_id", occurrence.id)
        val pending = PendingIntent.getBroadcast(context, "${instance.id}:${occurrence.id}".hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val alarm = context.getSystemService(AlarmManager::class.java)
        runCatching { alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toInstant().toEpochMilli(), pending) }
            .onFailure { alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toInstant().toEpochMilli(), pending) }
    }

    fun cancel(context: Context, instance: ScheduleInstance) {
        instance.occurrences.forEach { occurrence ->
            val intent = Intent(context, SchedulePlanAlarmReceiver::class.java).setAction(SchedulePlanAlarmReceiver.ACTION)
            context.getSystemService(AlarmManager::class.java).cancel(PendingIntent.getBroadcast(context, requestCode(instance.id, occurrence.id), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        }
    }

    private fun generationSpec(plan: SchedulePlan, anchor: ZonedDateTime): ScheduleGenerationSpec {
        val timing = plan.rules.firstOrNull()?.timing
        if (plan.termination.mode == ScheduleEndMode.OCCURRENCE_COUNT) {
            val count = plan.termination.occurrenceCount?.coerceAtLeast(1) ?: 1
            val horizon = when (timing) {
                is ScheduleTimingRule.ElapsedInterval -> timing.interval.multipliedBy(count.toLong() + 1)
                is ScheduleTimingRule.AnchoredCalendarDays -> Duration.ofDays(timing.everyDays.toLong() * (count.toLong() + 1))
                is ScheduleTimingRule.Weekly -> Duration.ofDays((count.toLong() + 1) * 8)
                is ScheduleTimingRule.MonthlyDayOfMonth, is ScheduleTimingRule.MonthlyNthWeekday -> Duration.ofDays((count.toLong() + 1) * 32)
                is ScheduleTimingRule.Cron -> Duration.ofDays((count.toLong() + 1) * 366)
                else -> Duration.ofDays(366)
            }
            return ScheduleGenerationSpec(horizon = horizon, now = anchor)
        }
        if (plan.termination.mode != ScheduleEndMode.FOREVER) return ScheduleGenerationSpec(now = anchor)
        val horizon = when (timing) {
            is ScheduleTimingRule.ElapsedInterval -> {
                val candidate = timing.interval.multipliedBy(128)
                when {
                    candidate < Duration.ofDays(1) -> Duration.ofDays(1)
                    candidate > Duration.ofDays(30) -> Duration.ofDays(30)
                    else -> candidate
                }
            }
            is ScheduleTimingRule.AnchoredCalendarDays -> Duration.ofDays(90)
            is ScheduleTimingRule.Weekly, is ScheduleTimingRule.MonthlyDayOfMonth, is ScheduleTimingRule.MonthlyNthWeekday -> Duration.ofDays(90)
            is ScheduleTimingRule.Cron -> Duration.ofDays(366)
            else -> Duration.ofDays(366)
        }
        return ScheduleGenerationSpec(horizon = horizon, now = anchor)
    }

    private fun requestCode(instanceId: String, occurrenceId: String): Int = "$instanceId:$occurrenceId".hashCode()
}
