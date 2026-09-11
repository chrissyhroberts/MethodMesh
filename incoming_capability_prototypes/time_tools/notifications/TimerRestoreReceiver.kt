package com.example.methodmesh.modules.time_tools.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.methodmesh.modules.time_tools.timing.AlarmRepeat

class TimerRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_LOCKED_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)) return
        val runtime = TimeToolsTimerRuntime(context)
        val scheduler = TimerAlarmScheduler(context)
        val notifications = TimerNotificationManager(context)
        ActiveTimerStore(context).all().forEach { record ->
            if (record.cancelled || record.completed) return@forEach
            val effective = if (record.kind == ActiveTimerKind.ALARM && record.alarmRepeat != null && record.alarmRepeat != AlarmRepeat.ONCE && (record.targetWallClockMs ?: 0) <= System.currentTimeMillis()) {
                runtime.ensureNextAlarm(record) ?: record
            } else record
            if (effective.targetWallClockMs != null && effective.targetWallClockMs > System.currentTimeMillis()) scheduler.schedulePrimary(effective)
            if (effective.ongoingNotification) notifications.showOngoing(effective)
        }
    }
}
