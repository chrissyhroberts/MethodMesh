package com.example.methodmesh.modules.time_tools.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.example.methodmesh.MainActivity
import com.example.methodmesh.modules.time_tools.timing.TimeFormatting
import java.time.Instant
import java.time.ZoneId

class TimerNotificationManager(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun showOngoing(record: ActiveTimerRecord) {
        if (!record.ongoingNotification || record.completed || record.cancelled) return
        ensureOngoingChannel()
        val b = NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(record.label)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(if (record.ongoingLockScreen) NotificationCompat.VISIBILITY_PUBLIC else NotificationCompat.VISIBILITY_SECRET)
            .setContentIntent(openApp(record.id))

        when (record.kind) {
            ActiveTimerKind.STOPWATCH -> {
                if (record.paused) {
                    b.setContentText("Paused · ${TimeFormatting.duration(elapsed(record), true)}")
                } else {
                    val base = (record.startedElapsedRealtimeMs ?: SystemClock.elapsedRealtime()) + record.accumulatedPauseMs
                    b.setUsesChronometer(true).setWhen(base).setShowWhen(true)
                }
                b.addAction(action("Lap", TimerControlReceiver.ACTION_LAP, record.id))
                b.addAction(action(if (record.paused) "Resume" else "Pause", if (record.paused) TimerControlReceiver.ACTION_RESUME else TimerControlReceiver.ACTION_PAUSE, record.id))
                b.addAction(action("Stop", TimerControlReceiver.ACTION_STOP, record.id))
            }
            ActiveTimerKind.COUNTDOWN, ActiveTimerKind.INTERVAL -> {
                if (record.paused) b.setContentText("Paused · ${TimeFormatting.duration(remaining(record))}")
                else {
                    val whenElapsed = record.targetElapsedRealtimeMs ?: (SystemClock.elapsedRealtime() + remaining(record))
                    b.setUsesChronometer(true).setChronometerCountDown(true).setWhen(whenElapsed).setShowWhen(true)
                }
                if (record.kind == ActiveTimerKind.COUNTDOWN) b.addAction(action("+1:00", TimerControlReceiver.ACTION_ADD_MINUTE, record.id))
                b.addAction(action(if (record.paused) "Resume" else "Pause", if (record.paused) TimerControlReceiver.ACTION_RESUME else TimerControlReceiver.ACTION_PAUSE, record.id))
                b.addAction(action("Stop", TimerControlReceiver.ACTION_STOP, record.id))
            }
            ActiveTimerKind.UNTIL -> {
                val target = record.targetWallClockMs?.let(Instant::ofEpochMilli)
                b.setContentText(target?.let { TimeFormatting.conciseLongRange(it, Instant.now(), record.zoneId ?: ZoneId.systemDefault().id) } ?: "Scheduled")
                b.addAction(action("Stop", TimerControlReceiver.ACTION_STOP, record.id))
            }
            ActiveTimerKind.ALARM -> return
        }
        manager.notify(ongoingId(record.id), b.build())
    }

    fun showDue(record: ActiveTimerRecord, reminderNumber: Int = 0) {
        val channel = ensureAlertChannel(record)
        val message = record.message.ifBlank {
            when (record.kind) {
                ActiveTimerKind.ALARM -> "Alarm"
                ActiveTimerKind.STOPWATCH -> "Stopwatch"
                else -> "Time is up"
            }
        }
        val b = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(if (reminderNumber > 0) "Reminder · ${record.label}" else record.label)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(false)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(if (record.alertProfile.highPriority) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(if (record.alertProfile.showOnLockScreen) NotificationCompat.VISIBILITY_PUBLIC else NotificationCompat.VISIBILITY_SECRET)
            .setContentIntent(openApp(record.id))
            .addAction(action("Done", TimerControlReceiver.ACTION_DONE, record.id))
        if (record.snoozeMinutes > 0) b.addAction(action("Snooze ${record.snoozeMinutes}m", TimerControlReceiver.ACTION_SNOOZE, record.id))
        if (!record.showMessageOnLockScreen && record.alertProfile.showOnLockScreen) {
            b.setPublicVersion(NotificationCompat.Builder(context, channel)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(record.label)
                .setContentText("Reminder due")
                .build())
        }
        manager.notify(dueId(record.id), b.build())
    }

    fun cancelOngoing(id: String) = manager.cancel(ongoingId(id))
    fun cancelDue(id: String) = manager.cancel(dueId(id))
    fun cancelAll(id: String) { cancelOngoing(id); cancelDue(id) }

    private fun ensureOngoingChannel() {
        if (Build.VERSION.SDK_INT >= 26 && manager.getNotificationChannel(CHANNEL_ONGOING) == null) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ONGOING, "Active timers", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Live countdown and stopwatch controls"
                setSound(null, null); enableVibration(false); lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            })
        }
    }

    private fun ensureAlertChannel(record: ActiveTimerRecord): String {
        val id = "methodmesh_time_alert_${record.alertProfile.stableKey()}"
        if (Build.VERSION.SDK_INT >= 26 && manager.getNotificationChannel(id) == null) {
            manager.createNotificationChannel(NotificationChannel(id, "Timer alerts", if (record.alertProfile.highPriority) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Timer and alarm alerts"
                enableVibration(record.alertProfile.vibration)
                if (!record.alertProfile.sound) setSound(null, null)
                enableLights(record.alertProfile.lights); lightColor = Color.WHITE
                lockscreenVisibility = if (record.alertProfile.showOnLockScreen) Notification.VISIBILITY_PUBLIC else Notification.VISIBILITY_SECRET
            })
        }
        return id
    }

    private fun action(label: String, action: String, id: String): NotificationCompat.Action {
        val intent = Intent(context, TimerControlReceiver::class.java).setAction(action).putExtra(TimerControlReceiver.EXTRA_TIMER_ID, id)
        val pending = PendingIntent.getBroadcast(context, "$id:$action".hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Action(0, label, pending)
    }

    private fun openApp(id: String): PendingIntent = PendingIntent.getActivity(
        context, "open:$id".hashCode(), Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("time_tools_timer_id", id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun elapsed(r: ActiveTimerRecord): Long {
        r.finalElapsedMs?.let { return it }
        val start = r.startedElapsedRealtimeMs ?: return (System.currentTimeMillis() - r.startedWallClockMs).coerceAtLeast(0L)
        val endpoint = r.pausedAtElapsedRealtimeMs ?: SystemClock.elapsedRealtime()
        return (endpoint - start - r.accumulatedPauseMs).coerceAtLeast(0L)
    }
    private fun remaining(r: ActiveTimerRecord): Long = when {
        r.targetElapsedRealtimeMs != null -> ((if (r.paused) r.pausedAtElapsedRealtimeMs ?: SystemClock.elapsedRealtime() else SystemClock.elapsedRealtime()).let { r.targetElapsedRealtimeMs - it }).coerceAtLeast(0L)
        r.targetWallClockMs != null -> (r.targetWallClockMs - System.currentTimeMillis()).coerceAtLeast(0L)
        else -> 0L
    }
    private fun ongoingId(id: String) = ("ongoing:$id").hashCode()
    private fun dueId(id: String) = ("due:$id").hashCode()

    companion object { const val CHANNEL_ONGOING = "methodmesh_time_active" }
}
