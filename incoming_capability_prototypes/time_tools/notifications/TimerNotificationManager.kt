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

/**
 * Native Android presentation for active Time Tools clocks.
 *
 * Running countdowns/stopwatches/intervals are intentionally represented as
 * ongoing notifications. The system chronometer renders the moving clock so
 * MethodMesh does not need to wake up and republish a notification every
 * second. Timer state remains authoritative in [ActiveTimerStore].
 */
class TimerNotificationManager(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val store = ActiveTimerStore(context)

    fun showOngoing(record: ActiveTimerRecord) {
        if (!record.ongoingNotification || record.completed || record.cancelled) {
            cancelOngoing(record.id)
            return
        }
        if (record.kind == ActiveTimerKind.ALARM) return

        ensureOngoingChannel()
        val builder = NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(record.label)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setCategory(Notification.CATEGORY_STOPWATCH)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(
                if (record.ongoingLockScreen) {
                    NotificationCompat.VISIBILITY_PUBLIC
                } else {
                    NotificationCompat.VISIBILITY_SECRET
                }
            )
            .setGroup(GROUP_ACTIVE_TIMERS)
            .setContentIntent(openApp(record.id))

        when (record.kind) {
            ActiveTimerKind.STOPWATCH -> configureStopwatch(builder, record)
            ActiveTimerKind.COUNTDOWN -> configureCountdown(builder, record)
            ActiveTimerKind.INTERVAL -> configureInterval(builder, record)
            ActiveTimerKind.UNTIL -> configureLongRange(builder, record)
            ActiveTimerKind.ALARM -> return
        }

        // Ongoing notifications deliberately do not expose the free-text
        // reminder message on the lock screen by default. That text belongs to
        // the due alert unless the user explicitly opted into it.
        if (record.ongoingLockScreen && !record.showMessageOnLockScreen) {
            builder.setPublicVersion(
                NotificationCompat.Builder(context, CHANNEL_ONGOING)
                    .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setContentTitle(record.label)
                    .setContentText(publicOngoingText(record))
                    .setCategory(Notification.CATEGORY_STOPWATCH)
                    .setOngoing(true)
                    .build()
            )
        } else if (record.message.isNotBlank()) {
            builder.setSubText(record.message)
        }

        manager.notify(ongoingId(record.id), builder.build())
        refreshGroupSummary()
    }

    private fun configureStopwatch(builder: NotificationCompat.Builder, record: ActiveTimerRecord) {
        if (record.paused) {
            builder
                .setContentText("Paused · ${TimeFormatting.duration(elapsed(record), true)}")
                .setShowWhen(false)
        } else {
            // Notification.when is wall-clock epoch time, not elapsedRealtime.
            // Project the monotonic stopwatch elapsed value onto wall time so
            // Android's own chronometer continues to tick while the app sleeps.
            val whenWallClockMs = System.currentTimeMillis() - elapsed(record)
            builder
                .setContentText("Running")
                .setUsesChronometer(true)
                .setWhen(whenWallClockMs)
                .setShowWhen(true)
        }
        builder
            .addAction(action("Lap", TimerControlReceiver.ACTION_LAP, record.id))
            .addAction(
                action(
                    if (record.paused) "Resume" else "Pause",
                    if (record.paused) TimerControlReceiver.ACTION_RESUME else TimerControlReceiver.ACTION_PAUSE,
                    record.id
                )
            )
            .addAction(action("Stop", TimerControlReceiver.ACTION_STOP, record.id))
    }

    private fun configureCountdown(builder: NotificationCompat.Builder, record: ActiveTimerRecord) {
        if (record.paused) {
            builder
                .setContentText("Paused · ${TimeFormatting.duration(remaining(record))}")
                .setShowWhen(false)
        } else {
            // Preserve monotonic countdown semantics while giving Android the
            // wall-clock 'when' value expected by Notification chronometers.
            val targetWallClockMs = System.currentTimeMillis() + remaining(record)
            builder
                .setContentText("Remaining")
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(targetWallClockMs)
                .setShowWhen(true)
        }
        builder
            .addAction(action("+1:00", TimerControlReceiver.ACTION_ADD_MINUTE, record.id))
            .addAction(
                action(
                    if (record.paused) "Resume" else "Pause",
                    if (record.paused) TimerControlReceiver.ACTION_RESUME else TimerControlReceiver.ACTION_PAUSE,
                    record.id
                )
            )
            .addAction(action("Stop", TimerControlReceiver.ACTION_STOP, record.id))
    }

    private fun configureInterval(builder: NotificationCompat.Builder, record: ActiveTimerRecord) {
        val intervalText = intervalStatus(record)
        if (record.paused) {
            builder
                .setContentText("Paused · $intervalText · ${TimeFormatting.duration(remaining(record))}")
                .setShowWhen(false)
        } else {
            val targetWallClockMs = System.currentTimeMillis() + remaining(record)
            builder
                .setContentText(intervalText)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(targetWallClockMs)
                .setShowWhen(true)
        }
        builder
            .addAction(
                action(
                    if (record.paused) "Resume" else "Pause",
                    if (record.paused) TimerControlReceiver.ACTION_RESUME else TimerControlReceiver.ACTION_PAUSE,
                    record.id
                )
            )
            .addAction(action("Stop", TimerControlReceiver.ACTION_STOP, record.id))
    }

    private fun configureLongRange(builder: NotificationCompat.Builder, record: ActiveTimerRecord) {
        val target = record.targetWallClockMs?.let(Instant::ofEpochMilli)
        builder
            .setContentText(
                target?.let {
                    TimeFormatting.conciseLongRange(
                        it,
                        Instant.now(),
                        record.zoneId ?: ZoneId.systemDefault().id
                    )
                } ?: "Scheduled"
            )
            .setShowWhen(false)
            .addAction(action("Stop", TimerControlReceiver.ACTION_STOP, record.id))
    }

    fun showDue(record: ActiveTimerRecord, reminderNumber: Int = 0) {
        // Completion alerts supersede the little live timer in the shade.
        cancelOngoing(record.id)

        val channel = ensureAlertChannel(record)
        val message = record.message.ifBlank {
            when (record.kind) {
                ActiveTimerKind.ALARM -> "Alarm"
                ActiveTimerKind.STOPWATCH -> "Stopwatch"
                else -> "Time is up"
            }
        }
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(if (reminderNumber > 0) "Reminder · ${record.label}" else record.label)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(false)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(
                if (record.alertProfile.highPriority) {
                    NotificationCompat.PRIORITY_HIGH
                } else {
                    NotificationCompat.PRIORITY_DEFAULT
                }
            )
            .setVisibility(
                if (record.alertProfile.showOnLockScreen) {
                    NotificationCompat.VISIBILITY_PUBLIC
                } else {
                    NotificationCompat.VISIBILITY_SECRET
                }
            )
            .setContentIntent(openApp(record.id))
            .addAction(action("Done", TimerControlReceiver.ACTION_DONE, record.id))

        if (record.snoozeMinutes > 0) {
            builder.addAction(
                action(
                    "Snooze ${record.snoozeMinutes}m",
                    TimerControlReceiver.ACTION_SNOOZE,
                    record.id
                )
            )
        }
        if (!record.showMessageOnLockScreen && record.alertProfile.showOnLockScreen) {
            builder.setPublicVersion(
                NotificationCompat.Builder(context, channel)
                    .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setContentTitle(record.label)
                    .setContentText("Reminder due")
                    .build()
            )
        }
        manager.notify(dueId(record.id), builder.build())
    }

    fun cancelOngoing(id: String) {
        manager.cancel(ongoingId(id))
        refreshGroupSummary(excludingId = id)
    }

    fun cancelDue(id: String) = manager.cancel(dueId(id))

    fun cancelAll(id: String) {
        manager.cancel(ongoingId(id))
        manager.cancel(dueId(id))
        refreshGroupSummary(excludingId = id)
    }

    private fun ensureOngoingChannel() {
        if (Build.VERSION.SDK_INT >= 26 && manager.getNotificationChannel(CHANNEL_ONGOING) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ONGOING,
                    "Active timers",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Live countdown, stopwatch and interval controls"
                    setSound(null, null)
                    enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
            )
        }
    }

    private fun ensureAlertChannel(record: ActiveTimerRecord): String {
        val id = "methodmesh_time_alert_${record.alertProfile.stableKey()}"
        if (Build.VERSION.SDK_INT >= 26 && manager.getNotificationChannel(id) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    id,
                    "Timer alerts",
                    if (record.alertProfile.highPriority) {
                        NotificationManager.IMPORTANCE_HIGH
                    } else {
                        NotificationManager.IMPORTANCE_DEFAULT
                    }
                ).apply {
                    description = "Timer and alarm alerts"
                    enableVibration(record.alertProfile.vibration)
                    if (!record.alertProfile.sound) setSound(null, null)
                    enableLights(record.alertProfile.lights)
                    lightColor = Color.WHITE
                    lockscreenVisibility =
                        if (record.alertProfile.showOnLockScreen) {
                            Notification.VISIBILITY_PUBLIC
                        } else {
                            Notification.VISIBILITY_SECRET
                        }
                }
            )
        }
        return id
    }

    private fun refreshGroupSummary(excludingId: String? = null) {
        val live = store.all().filter {
            it.id != excludingId &&
                it.ongoingNotification &&
                !it.completed &&
                !it.cancelled &&
                it.kind != ActiveTimerKind.ALARM
        }

        if (live.size < 2) {
            manager.cancel(GROUP_SUMMARY_ID)
            return
        }

        ensureOngoingChannel()
        val summary = NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("MethodMesh timers")
            .setContentText("${live.size} timers active")
            .setNumber(live.size)
            .setSilent(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_STOPWATCH)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setGroup(GROUP_ACTIVE_TIMERS)
            .setGroupSummary(true)
            .setContentIntent(openApp(live.first().id))
            .build()
        manager.notify(GROUP_SUMMARY_ID, summary)
    }

    private fun publicOngoingText(record: ActiveTimerRecord): String = when (record.kind) {
        ActiveTimerKind.STOPWATCH -> if (record.paused) "Stopwatch paused" else "Stopwatch running"
        ActiveTimerKind.COUNTDOWN -> if (record.paused) "Timer paused" else "Timer running"
        ActiveTimerKind.INTERVAL -> if (record.paused) "Interval paused" else "Interval running"
        ActiveTimerKind.UNTIL -> "Countdown active"
        ActiveTimerKind.ALARM -> "Alarm set"
    }

    private fun intervalStatus(record: ActiveTimerRecord): String {
        if (record.phaseDurationsMs.isEmpty() || record.phaseLabels.size != record.phaseDurationsMs.size) {
            return "Interval running"
        }
        val elapsed = elapsed(record)
        val cycleMs = record.phaseDurationsMs.sum().coerceAtLeast(1L)
        val cycle = ((elapsed / cycleMs).toInt() + 1).coerceIn(1, record.intervalCycles)
        var within = elapsed % cycleMs
        var phase = record.phaseLabels.last()
        for (index in record.phaseDurationsMs.indices) {
            if (within < record.phaseDurationsMs[index]) {
                phase = record.phaseLabels[index]
                break
            }
            within -= record.phaseDurationsMs[index]
        }
        return "$phase · cycle $cycle/${record.intervalCycles}"
    }

    private fun action(label: String, action: String, id: String): NotificationCompat.Action {
        val intent = Intent(context, TimerControlReceiver::class.java)
            .setAction(action)
            .putExtra(TimerControlReceiver.EXTRA_TIMER_ID, id)
        val pending = PendingIntent.getBroadcast(
            context,
            "$id:$action".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action(0, label, pending)
    }

    private fun openApp(id: String): PendingIntent = PendingIntent.getActivity(
        context,
        "open:$id".hashCode(),
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("time_tools_timer_id", id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun elapsed(record: ActiveTimerRecord): Long {
        record.finalElapsedMs?.let { return it }
        val start = record.startedElapsedRealtimeMs
            ?: return (System.currentTimeMillis() - record.startedWallClockMs).coerceAtLeast(0L)
        val endpoint = record.pausedAtElapsedRealtimeMs ?: SystemClock.elapsedRealtime()
        return (endpoint - start - record.accumulatedPauseMs).coerceAtLeast(0L)
    }

    private fun remaining(record: ActiveTimerRecord): Long = when {
        record.targetElapsedRealtimeMs != null -> {
            val endpoint = if (record.paused) {
                record.pausedAtElapsedRealtimeMs ?: SystemClock.elapsedRealtime()
            } else {
                SystemClock.elapsedRealtime()
            }
            (record.targetElapsedRealtimeMs - endpoint).coerceAtLeast(0L)
        }
        record.targetWallClockMs != null -> {
            (record.targetWallClockMs - System.currentTimeMillis()).coerceAtLeast(0L)
        }
        else -> 0L
    }

    private fun ongoingId(id: String) = ("ongoing:$id").hashCode()
    private fun dueId(id: String) = ("due:$id").hashCode()

    companion object {
        // v2 is intentional: Android notification channel importance cannot be
        // raised after a channel has already been created. The previous active
        // timer channel was LOW; this DEFAULT-but-silent channel keeps ongoing
        // timers readily visible without turning them into alerts.
        const val CHANNEL_ONGOING = "methodmesh_time_active_v2"
        private const val GROUP_ACTIVE_TIMERS = "methodmesh_time_tools_active"
        private const val GROUP_SUMMARY_ID = 0x54_49_4D_45
    }
}
