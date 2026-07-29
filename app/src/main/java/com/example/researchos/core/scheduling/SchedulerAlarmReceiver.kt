package com.example.researchos.core.scheduling

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.time.ZonedDateTime

class SchedulerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val id = intent.getStringExtra("schedule_id") ?: return
        val schedule = SchedulerRepository.get(context, id) ?: return
        val kind = intent.getStringExtra("kind").orEmpty()
        SchedulerRepository.recordEvent(context, id, if (kind == "retry") "reminder_delivered" else "notification_delivered")
        SchedulerNotifications.ensureChannel(context)
        val open = Intent(context, SchedulerDispatchActivity::class.java).putExtra("schedule_id", id)
        val pending = PendingIntent.getActivity(context, id.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val reminder = kind == "retry"
        val title = schedule.notificationTitle.ifBlank { if (reminder) "Reminder: ${schedule.name}" else schedule.name }
        val text = schedule.notificationMessage.ifBlank { if (schedule.target == SchedulerTarget.ODK_FORM) "Complete the scheduled ODK form" else "Complete the scheduled online form" }
        context.getSystemService(NotificationManager::class.java).notify(id.hashCode(), NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(text).setContentIntent(pending).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_HIGH).build())

        if (kind == "primary" && schedule.retryCount > 0) {
            scheduleRetry(context, schedule, 1)
        } else if (kind == "retry") {
            val attempt = intent.getIntExtra("attempt", 1)
            if (attempt < schedule.retryCount) scheduleRetry(context, schedule, attempt + 1) else SchedulerAlarm.schedule(context, schedule)
        } else {
            SchedulerAlarm.schedule(context, schedule)
        }
    }

    private fun scheduleRetry(context: Context, schedule: ResearchSchedule, attempt: Int) {
        val whenMillis = ZonedDateTime.now().plusMinutes(schedule.retryIntervalMinutes.coerceAtLeast(1).toLong()).toInstant().toEpochMilli()
        val intent = Intent(context, SchedulerAlarmReceiver::class.java).setAction(ACTION).putExtra("schedule_id", schedule.id).putExtra("kind", "retry").putExtra("attempt", attempt)
        context.getSystemService(android.app.AlarmManager::class.java).setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, whenMillis,
            PendingIntent.getBroadcast(context, SchedulerRepository.requestCode(schedule.id, "retry"), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
    }

    companion object { const val ACTION = "com.example.researchos.SCHEDULER_ALARM"; const val CHANNEL = "researchos_scheduler" }
}

object SchedulerNotifications {
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) context.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(SchedulerAlarmReceiver.CHANNEL, "ResearchOS schedules", NotificationManager.IMPORTANCE_HIGH))
    }
}
