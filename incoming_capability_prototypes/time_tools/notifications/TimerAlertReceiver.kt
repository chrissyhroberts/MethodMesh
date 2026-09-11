package com.example.methodmesh.modules.time_tools.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.methodmesh.modules.time_tools.timing.AlarmRepeat

class TimerAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TIMER_DUE) return
        val id = intent.getStringExtra(EXTRA_TIMER_ID) ?: return
        val kind = intent.getStringExtra(EXTRA_KIND) ?: TimerAlarmScheduler.KIND_PRIMARY
        val attempt = intent.getIntExtra(EXTRA_ATTEMPT, 0)
        val store = ActiveTimerStore(context)
        var record = store.get(id) ?: return
        val scheduler = TimerAlarmScheduler(context)
        val notifications = TimerNotificationManager(context)

        if (kind == TimerAlarmScheduler.KIND_PRIMARY) {
            notifications.cancelOngoing(id)
            if (record.kind == ActiveTimerKind.ALARM && record.alarmRepeat != null && record.alarmRepeat != AlarmRepeat.ONCE) {
                record = TimeToolsTimerRuntime(context).ensureNextAlarm(record) ?: record
            }
        }

        notifications.showDue(record, if (kind == TimerAlarmScheduler.KIND_FOLLOWUP) attempt else 0)

        if (record.requireConfirmation && record.followUpCount > 0) {
            val next = if (kind == TimerAlarmScheduler.KIND_FOLLOWUP) attempt + 1 else 1
            if (next <= record.followUpCount) scheduler.scheduleFollowUp(record, next)
        }
    }

    companion object {
        const val ACTION_TIMER_DUE = "com.example.methodmesh.modules.time_tools.TIMER_DUE"
        const val EXTRA_TIMER_ID = "timer_id"
        const val EXTRA_KIND = "kind"
        const val EXTRA_ATTEMPT = "attempt"
    }
}
