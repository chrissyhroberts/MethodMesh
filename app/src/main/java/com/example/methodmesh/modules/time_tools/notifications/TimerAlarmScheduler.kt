package com.example.methodmesh.modules.time_tools.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

class TimerAlarmScheduler(private val context: Context) {
    data class ScheduleResult(val exact: Boolean, val diagnostic: String? = null)
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedulePrimary(record: ActiveTimerRecord): ScheduleResult {
        val elapsedTarget = record.targetElapsedRealtimeMs?.takeIf { record.kind != ActiveTimerKind.ALARM && record.kind != ActiveTimerKind.UNTIL }
        val wallTarget = record.targetWallClockMs
        if (elapsedTarget == null && wallTarget == null) return ScheduleResult(false, "Timer has no target")
        val type = if (elapsedTarget != null) AlarmManager.ELAPSED_REALTIME_WAKEUP else AlarmManager.RTC_WAKEUP
        return schedule(type, elapsedTarget ?: wallTarget!!, pending(record.id, KIND_PRIMARY, 0))
    }

    fun scheduleFollowUp(record: ActiveTimerRecord, attempt: Int): ScheduleResult {
        val whenMillis = System.currentTimeMillis() + record.followUpIntervalMinutes.coerceAtLeast(1) * 60_000L
        return schedule(AlarmManager.RTC_WAKEUP, whenMillis, pending(record.id, KIND_FOLLOWUP, attempt))
    }

    fun scheduleSnooze(record: ActiveTimerRecord): ScheduleResult {
        val whenMillis = System.currentTimeMillis() + record.snoozeMinutes.coerceAtLeast(1) * 60_000L
        return schedule(AlarmManager.RTC_WAKEUP, whenMillis, pending(record.id, KIND_SNOOZE, 0))
    }

    fun cancelPrimary(timerId: String) = alarmManager.cancel(pending(timerId, KIND_PRIMARY, 0))
    fun cancelSnooze(timerId: String) = alarmManager.cancel(pending(timerId, KIND_SNOOZE, 0))

    fun cancelFollowUps(record: ActiveTimerRecord) {
        for (attempt in 1..record.followUpCount.coerceAtMost(100)) {
            alarmManager.cancel(pending(record.id, KIND_FOLLOWUP, attempt))
        }
    }

    fun cancelAll(record: ActiveTimerRecord) {
        cancelPrimary(record.id); cancelSnooze(record.id); cancelFollowUps(record)
    }

    fun canScheduleExact(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun schedule(type: Int, whenValue: Long, pending: PendingIntent): ScheduleResult = if (canScheduleExact()) {
        alarmManager.setExactAndAllowWhileIdle(type, whenValue, pending); ScheduleResult(true)
    } else {
        alarmManager.setAndAllowWhileIdle(type, whenValue, pending); ScheduleResult(false, "Exact-alarm access unavailable; delivery may be approximate")
    }

    private fun pending(timerId: String, kind: String, attempt: Int): PendingIntent {
        val intent = Intent(context, TimerAlertReceiver::class.java)
            .setAction(TimerAlertReceiver.ACTION_TIMER_DUE)
            .putExtra(TimerAlertReceiver.EXTRA_TIMER_ID, timerId)
            .putExtra(TimerAlertReceiver.EXTRA_KIND, kind)
            .putExtra(TimerAlertReceiver.EXTRA_ATTEMPT, attempt)
        return PendingIntent.getBroadcast(context, "$timerId:$kind:$attempt".hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object { const val KIND_PRIMARY = "primary"; const val KIND_FOLLOWUP = "followup"; const val KIND_SNOOZE = "snooze" }
}
