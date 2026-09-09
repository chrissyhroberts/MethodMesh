package com.example.methodmesh.core.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.ZonedDateTime

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
        val instance = SchedulePlanEngine.instantiate(plan, anchoredAt)
        SchedulePlanStore.saveInstance(context, instance)
        armNext(context, instance)
        return instance
    }

    fun armNext(context: Context, instance: ScheduleInstance) {
        val next = instance.occurrences.firstOrNull { it.state == ScheduleOccurrenceState.UPCOMING || it.state == ScheduleOccurrenceState.WINDOW_OPEN } ?: return
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

    private fun requestCode(instanceId: String, occurrenceId: String): Int = "$instanceId:$occurrenceId".hashCode()
}
