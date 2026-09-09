package com.example.methodmesh.core.scheduling

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

class SchedulePlanAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION && intent.action != ACTION_DONE && intent.action != ACTION_SNOOZE) return
        val instance = SchedulePlanStore.instance(context, intent.getStringExtra("instance_id").orEmpty()) ?: return
        val occurrence = instance.occurrences.firstOrNull { it.id == intent.getStringExtra("occurrence_id") } ?: return
        if (intent.action == ACTION_DONE) {
            SchedulePlanStore.updateOccurrence(context, instance.id, occurrence.id, ScheduleOccurrenceState.COMPLETED, java.time.ZonedDateTime.now())
            SchedulePlanRuntime.armNext(context, SchedulePlanStore.instance(context, instance.id) ?: instance)
            context.getSystemService(NotificationManager::class.java).cancel(occurrence.id.hashCode())
            return
        }
        if (intent.action == ACTION_SNOOZE) {
            SchedulePlanStore.updateOccurrence(context, instance.id, occurrence.id, ScheduleOccurrenceState.SNOOZED)
            SchedulePlanRuntime.snooze(context, instance, occurrence, occurrence.actions.firstOrNull()?.snoozeMinutes ?: 10)
            context.getSystemService(NotificationManager::class.java).cancel(occurrence.id.hashCode())
            return
        }
        SchedulePlanStore.updateOccurrence(context, instance.id, occurrence.id, ScheduleOccurrenceState.DUE)
        // A due notification must not block every later occurrence. The user can
        // still complete or snooze this item independently while the next alarm
        // remains armed.
        SchedulePlanStore.instance(context, instance.id)?.let { SchedulePlanRuntime.armNext(context, it) }
        val open = Intent(context, SchedulePlanDispatchActivity::class.java).setPackage(context.packageName)
            .putExtra("instance_id", instance.id).putExtra("occurrence_id", occurrence.id)
        val pending = PendingIntent.getActivity(context, occurrence.id.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val done = PendingIntent.getBroadcast(context, occurrence.id.hashCode() + 1, Intent(context, SchedulePlanAlarmReceiver::class.java).setAction(ACTION_DONE).putExtra("instance_id", instance.id).putExtra("occurrence_id", occurrence.id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val snooze = PendingIntent.getBroadcast(context, occurrence.id.hashCode() + 2, Intent(context, SchedulePlanAlarmReceiver::class.java).setAction(ACTION_SNOOZE).putExtra("instance_id", instance.id).putExtra("occurrence_id", occurrence.id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val manager = context.getSystemService(NotificationManager::class.java)
        if (android.os.Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL, "MethodMesh schedule plans", NotificationManager.IMPORTANCE_DEFAULT))
        val title = occurrence.actions.firstOrNull()?.title?.ifBlank { occurrence.laneName } ?: occurrence.laneName
        val actionCount = occurrence.actions.size
        val message = occurrence.actions.firstOrNull()?.message?.ifBlank { "Scheduled activity due" } ?: "Scheduled activity due"
        val detail = if (actionCount > 1) "$message · $actionCount actions" else message
        manager.notify(occurrence.id.hashCode(), NotificationCompat.Builder(context, CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(detail).setContentIntent(pending).addAction(0, "Done", done).addAction(0, "Snooze", snooze).setAutoCancel(true).build())
    }

    companion object { const val ACTION = "com.example.methodmesh.SCHEDULE_PLAN_ALARM"; const val ACTION_DONE = "com.example.methodmesh.SCHEDULE_PLAN_DONE"; const val ACTION_SNOOZE = "com.example.methodmesh.SCHEDULE_PLAN_SNOOZE"; const val CHANNEL = "methodmesh_schedule_plans" }
}
