package com.example.methodmesh.core.scheduling

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
        if (!schedule.enabled) return
        if (schedule.chainId.isNotBlank() && schedule.chainOrder > 0) {
            // A chained action is dispatched by its owner; it must never have
            // an independent reminder of its own.
            SchedulerRepository.cancel(context, schedule.id)
            return
        }
        val kind = intent.getStringExtra("kind").orEmpty()
        SchedulerRepository.recordEvent(context, id, if (kind == "retry") "reminder_delivered" else "notification_delivered")
        SchedulerNotifications.ensureChannel(context)
        val open = Intent(context, SchedulerDispatchActivity::class.java)
            .setAction("com.example.methodmesh.SCHEDULED_DISPATCH")
            .setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("schedule_id", id)
            .putExtra("notification_kind", kind.ifBlank { "primary" })
        // Use a dedicated open request code so reminder alarms and notification
        // taps cannot accidentally reuse one another's PendingIntent state.
        val pending = PendingIntent.getActivity(
            context,
            SchedulerRepository.requestCode(id, "open"),
            open,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val reminder = kind == "retry"
        val title = schedule.notificationTitle.ifBlank { if (reminder) "Reminder: ${schedule.name}" else schedule.name }
        val text = schedule.notificationMessage.ifBlank {
            when (schedule.target) {
                SchedulerTarget.ODK_FORM -> "Complete the scheduled ODK form"
                SchedulerTarget.WEB_FORM -> "Complete the scheduled online form"
                SchedulerTarget.CAPABILITY -> "Run the scheduled MethodMesh capability"
                SchedulerTarget.PRESET -> "Run the scheduled MethodMesh preset"
                SchedulerTarget.PROTOCOL -> "Run the scheduled MethodMesh protocol"
                SchedulerTarget.CLIPBOARD -> "Run the scheduled clipboard action"
            }
        }
        context.getSystemService(NotificationManager::class.java).notify(id.hashCode(), NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(text).setContentIntent(pending).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_DEFAULT).build())

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
        val alarm = context.getSystemService(android.app.AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            SchedulerRepository.requestCode(schedule.id, "retry"),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        runCatching {
            alarm.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, whenMillis, pending)
        }.onFailure {
            alarm.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, whenMillis, pending)
        }
    }

    companion object { const val ACTION = "com.example.methodmesh.SCHEDULER_ALARM"; const val CHANNEL = "methodmesh_scheduler" }
}

object SchedulerNotifications {
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) context.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(SchedulerAlarmReceiver.CHANNEL, "MethodMesh schedules", NotificationManager.IMPORTANCE_DEFAULT))
    }
}
