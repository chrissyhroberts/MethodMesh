package com.example.methodmesh.modules.time_tools.notifications

import android.content.Context
import android.os.SystemClock
import com.example.methodmesh.modules.time_tools.timing.AlarmDefinition
import com.example.methodmesh.modules.time_tools.timing.AlarmRepeat
import com.example.methodmesh.modules.time_tools.timing.AlarmSchedule
import com.example.methodmesh.modules.time_tools.timing.AlertProfile
import com.example.methodmesh.modules.time_tools.timing.TimeFormatting
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

class TimeToolsTimerRuntime(context: Context) {
    data class StartResult(val timerId: String, val targetTimestamp: String?, val exactAlertScheduled: Boolean?, val diagnostic: String? = null)
    data class Snapshot(
        val id: String,
        val kind: ActiveTimerKind,
        val label: String,
        val message: String,
        val formatted: String,
        val elapsedMs: Long,
        val remainingMs: Long?,
        val paused: Boolean,
        val completed: Boolean,
        val laps: List<Long>,
        val currentPhase: String? = null,
        val cycle: Int? = null,
        val cycles: Int? = null,
        val targetTimestamp: String? = null
    )

    private val appContext = context.applicationContext
    private val store = ActiveTimerStore(appContext)
    private val scheduler = TimerAlarmScheduler(appContext)
    private val notifications = TimerNotificationManager(appContext)

    fun allRecords(): List<ActiveTimerRecord> = store.all()
    fun record(id: String): ActiveTimerRecord? = store.get(id)

    fun startCountdown(durationMs: Long, label: String = "Countdown", message: String = "", alertProfile: AlertProfile = AlertProfile(), showOngoing: Boolean = true, ongoingLockScreen: Boolean = true, showMessageOnLockScreen: Boolean = false, requireConfirmation: Boolean = false, followUpCount: Int = 0, followUpIntervalMinutes: Int = 10, snoozeMinutes: Int = 10): StartResult {
        require(durationMs > 0)
        val nowWall = System.currentTimeMillis(); val nowElapsed = SystemClock.elapsedRealtime()
        return activate(ActiveTimerRecord(
            id = UUID.randomUUID().toString(), kind = ActiveTimerKind.COUNTDOWN, label = label, message = message,
            startedWallClockMs = nowWall, targetWallClockMs = nowWall + durationMs,
            startedElapsedRealtimeMs = nowElapsed, targetElapsedRealtimeMs = nowElapsed + durationMs,
            alertProfile = alertProfile, ongoingNotification = showOngoing, ongoingLockScreen = ongoingLockScreen,
            showMessageOnLockScreen = showMessageOnLockScreen, completionAlert = true, requireConfirmation = requireConfirmation,
            followUpCount = followUpCount.coerceIn(0, 100), followUpIntervalMinutes = followUpIntervalMinutes.coerceAtLeast(1), snoozeMinutes = snoozeMinutes.coerceAtLeast(0)
        ))
    }

    fun startStopwatch(label: String = "Stopwatch", message: String = "", showOngoing: Boolean = true, ongoingLockScreen: Boolean = true): StartResult {
        val r = ActiveTimerRecord(
            id = UUID.randomUUID().toString(), kind = ActiveTimerKind.STOPWATCH, label = label, message = message,
            startedWallClockMs = System.currentTimeMillis(), startedElapsedRealtimeMs = SystemClock.elapsedRealtime(),
            ongoingNotification = showOngoing, ongoingLockScreen = ongoingLockScreen, completionAlert = false
        )
        store.put(r); notifications.showOngoing(r); return StartResult(r.id, null, null)
    }

    fun startUntil(target: Instant, label: String = "Date & time countdown", message: String = "", zoneId: String = ZoneId.systemDefault().id, alertProfile: AlertProfile = AlertProfile(), showOngoing: Boolean = false, ongoingLockScreen: Boolean = false, showMessageOnLockScreen: Boolean = false, requireConfirmation: Boolean = false, followUpCount: Int = 0, followUpIntervalMinutes: Int = 10, snoozeMinutes: Int = 10): StartResult {
        require(target.isAfter(Instant.now())) { "Target must be in the future" }
        return activate(ActiveTimerRecord(
            id = UUID.randomUUID().toString(), kind = ActiveTimerKind.UNTIL, label = label, message = message,
            startedWallClockMs = System.currentTimeMillis(), targetWallClockMs = target.toEpochMilli(),
            alertProfile = alertProfile, ongoingNotification = showOngoing, ongoingLockScreen = ongoingLockScreen,
            showMessageOnLockScreen = showMessageOnLockScreen, completionAlert = true, zoneId = zoneId,
            requireConfirmation = requireConfirmation, followUpCount = followUpCount.coerceIn(0, 100),
            followUpIntervalMinutes = followUpIntervalMinutes.coerceAtLeast(1), snoozeMinutes = snoozeMinutes.coerceAtLeast(0)
        ))
    }

    fun startInterval(phaseLabels: List<String>, phaseDurationsMs: List<Long>, cycles: Int, label: String = "Interval timer", message: String = "", alertProfile: AlertProfile = AlertProfile(), showOngoing: Boolean = true, ongoingLockScreen: Boolean = true, showMessageOnLockScreen: Boolean = false, requireConfirmation: Boolean = false, followUpCount: Int = 0, followUpIntervalMinutes: Int = 10, snoozeMinutes: Int = 10): StartResult {
        require(phaseLabels.isNotEmpty() && phaseLabels.size == phaseDurationsMs.size && phaseDurationsMs.all { it > 0 })
        val total = phaseDurationsMs.sum() * cycles.coerceAtLeast(1)
        val nowWall = System.currentTimeMillis(); val nowElapsed = SystemClock.elapsedRealtime()
        return activate(ActiveTimerRecord(
            id = UUID.randomUUID().toString(), kind = ActiveTimerKind.INTERVAL, label = label, message = message,
            startedWallClockMs = nowWall, targetWallClockMs = nowWall + total,
            startedElapsedRealtimeMs = nowElapsed, targetElapsedRealtimeMs = nowElapsed + total,
            phaseLabels = phaseLabels, phaseDurationsMs = phaseDurationsMs, intervalCycles = cycles.coerceAtLeast(1),
            alertProfile = alertProfile, ongoingNotification = showOngoing, ongoingLockScreen = ongoingLockScreen,
            showMessageOnLockScreen = showMessageOnLockScreen, completionAlert = true, requireConfirmation = requireConfirmation,
            followUpCount = followUpCount.coerceIn(0, 100), followUpIntervalMinutes = followUpIntervalMinutes.coerceAtLeast(1), snoozeMinutes = snoozeMinutes.coerceAtLeast(0)
        ))
    }

    fun startAlarm(time: String, date: String?, repeat: AlarmRepeat, weekdays: Set<Int>, zoneId: String, label: String, message: String, alertProfile: AlertProfile, requireConfirmation: Boolean, followUpCount: Int, followUpIntervalMinutes: Int, snoozeMinutes: Int): StartResult {
        val zone = ZoneId.of(zoneId)
        val definition = AlarmDefinition(LocalTime.parse(time), zone.id, repeat, date?.takeIf(String::isNotBlank)?.let(LocalDate::parse), weekdays)
        val target = AlarmSchedule.next(definition, ZonedDateTime.now(zone)) ?: error("This one-off alarm time has already passed")
        val r = ActiveTimerRecord(
            id = UUID.randomUUID().toString(), kind = ActiveTimerKind.ALARM, label = label.ifBlank { "Alarm" }, message = message,
            startedWallClockMs = System.currentTimeMillis(), targetWallClockMs = target.toInstant().toEpochMilli(), alertProfile = alertProfile,
            ongoingNotification = false, ongoingLockScreen = false, completionAlert = true, requireConfirmation = requireConfirmation,
            followUpCount = followUpCount.coerceIn(0, 100), followUpIntervalMinutes = followUpIntervalMinutes.coerceAtLeast(1), snoozeMinutes = snoozeMinutes.coerceAtLeast(0),
            zoneId = zone.id, alarmTime = time, alarmDate = date, alarmRepeat = repeat, alarmWeekdays = weekdays
        )
        return activate(r)
    }

    fun snapshot(id: String): Snapshot? = store.get(id)?.let(::snapshot)
    fun snapshot(record: ActiveTimerRecord): Snapshot {
        val nowElapsed = SystemClock.elapsedRealtime(); val nowWall = System.currentTimeMillis()
        val elapsed = elapsedMs(record, nowElapsed, nowWall)
        val remaining = remainingMs(record, nowElapsed, nowWall)
        val phase = if (record.kind == ActiveTimerKind.INTERVAL) intervalPosition(record, elapsed) else null
        val formatted = when (record.kind) {
            ActiveTimerKind.STOPWATCH -> TimeFormatting.duration(elapsed, true)
            ActiveTimerKind.UNTIL, ActiveTimerKind.ALARM -> record.targetWallClockMs?.let { TimeFormatting.longRange(Instant.ofEpochMilli(it), Instant.ofEpochMilli(nowWall), record.zoneId ?: ZoneId.systemDefault().id) } ?: ""
            else -> TimeFormatting.duration(remaining ?: 0L)
        }
        return Snapshot(record.id, record.kind, record.label, record.message, formatted, elapsed, remaining, record.paused, record.completed, record.lapTotalsMs, phase?.first, phase?.second, if (record.kind == ActiveTimerKind.INTERVAL) record.intervalCycles else null, record.targetWallClockMs?.let { Instant.ofEpochMilli(it).toString() })
    }

    fun pause(id: String) {
        val r = store.get(id) ?: return
        if (r.paused || r.completed || r.cancelled || r.kind == ActiveTimerKind.ALARM || r.kind == ActiveTimerKind.UNTIL) return
        val updated = r.copy(paused = true, pausedAtElapsedRealtimeMs = SystemClock.elapsedRealtime())
        store.put(updated); scheduler.cancelPrimary(id); notifications.showOngoing(updated)
    }

    fun resume(id: String) {
        val r = store.get(id) ?: return
        if (!r.paused) return
        val now = SystemClock.elapsedRealtime(); val pausedAt = r.pausedAtElapsedRealtimeMs ?: now
        val delta = (now - pausedAt).coerceAtLeast(0L)
        val updated = r.copy(
            paused = false, pausedAtElapsedRealtimeMs = null, accumulatedPauseMs = r.accumulatedPauseMs + delta,
            targetElapsedRealtimeMs = r.targetElapsedRealtimeMs?.plus(delta), targetWallClockMs = r.targetWallClockMs?.plus(delta)
        )
        store.put(updated); if (updated.completionAlert && updated.targetWallClockMs != null) scheduler.schedulePrimary(updated); notifications.showOngoing(updated)
    }

    fun addMinute(id: String) {
        val r = store.get(id) ?: return
        if (r.kind !in setOf(ActiveTimerKind.COUNTDOWN, ActiveTimerKind.INTERVAL)) return
        val updated = r.copy(targetElapsedRealtimeMs = r.targetElapsedRealtimeMs?.plus(60_000L), targetWallClockMs = r.targetWallClockMs?.plus(60_000L))
        store.put(updated); scheduler.cancelPrimary(id); scheduler.schedulePrimary(updated); notifications.showOngoing(updated)
    }

    fun lap(id: String) {
        val r = store.get(id) ?: return
        if (r.kind != ActiveTimerKind.STOPWATCH || r.paused) return
        val total = elapsedMs(r, SystemClock.elapsedRealtime(), System.currentTimeMillis())
        val updated = r.copy(lapTotalsMs = r.lapTotalsMs + total)
        store.put(updated); notifications.showOngoing(updated)
    }

    fun stop(id: String): Snapshot? {
        val r = store.get(id) ?: return null
        val elapsed = elapsedMs(r, SystemClock.elapsedRealtime(), System.currentTimeMillis())
        val updated = r.copy(finalElapsedMs = elapsed, completed = true, paused = false)
        store.put(updated); scheduler.cancelAll(updated); notifications.cancelAll(id)
        return snapshot(updated)
    }

    fun cancel(id: String) { store.get(id)?.let { scheduler.cancelAll(it) }; notifications.cancelAll(id); store.remove(id) }

    fun done(id: String) {
        val r = store.get(id) ?: return
        scheduler.cancelFollowUps(r); scheduler.cancelSnooze(id); notifications.cancelDue(id)
        if (r.kind == ActiveTimerKind.ALARM && r.alarmRepeat != null && r.alarmRepeat != AlarmRepeat.ONCE) {
            ensureNextAlarm(r)
        } else store.remove(id)
    }

    fun snooze(id: String) {
        val r = store.get(id) ?: return
        if (r.snoozeMinutes <= 0) return
        scheduler.cancelFollowUps(r); notifications.cancelDue(id); scheduler.scheduleSnooze(r)
    }

    fun ensureNextAlarm(record: ActiveTimerRecord): ActiveTimerRecord? {
        if (record.kind != ActiveTimerKind.ALARM || record.alarmRepeat == null || record.alarmRepeat == AlarmRepeat.ONCE) return null
        val zone = ZoneId.of(record.zoneId ?: ZoneId.systemDefault().id)
        val def = AlarmDefinition(LocalTime.parse(record.alarmTime ?: return null), zone.id, record.alarmRepeat, null, record.alarmWeekdays)
        val next = AlarmSchedule.next(def, ZonedDateTime.now(zone).plusSeconds(1)) ?: return null
        val updated = record.copy(targetWallClockMs = next.toInstant().toEpochMilli(), completed = false)
        store.put(updated); scheduler.schedulePrimary(updated); return updated
    }

    fun removeFinished(id: String) { store.remove(id) }

    private fun activate(record: ActiveTimerRecord): StartResult {
        store.put(record); if (record.ongoingNotification) notifications.showOngoing(record)
        val scheduled = if (record.completionAlert && record.targetWallClockMs != null) scheduler.schedulePrimary(record) else TimerAlarmScheduler.ScheduleResult(false)
        return StartResult(record.id, record.targetWallClockMs?.let { Instant.ofEpochMilli(it).toString() }, scheduled.exact, scheduled.diagnostic)
    }

    private fun elapsedMs(r: ActiveTimerRecord, nowElapsed: Long, nowWall: Long): Long {
        r.finalElapsedMs?.let { return it }
        val startElapsed = r.startedElapsedRealtimeMs
        if (startElapsed != null) {
            val endpoint = r.pausedAtElapsedRealtimeMs ?: nowElapsed
            return (endpoint - startElapsed - r.accumulatedPauseMs).coerceAtLeast(0L)
        }
        return (nowWall - r.startedWallClockMs).coerceAtLeast(0L)
    }

    private fun remainingMs(r: ActiveTimerRecord, nowElapsed: Long, nowWall: Long): Long? {
        val targetElapsed = r.targetElapsedRealtimeMs
        return when {
            targetElapsed != null -> ((if (r.paused) r.pausedAtElapsedRealtimeMs ?: nowElapsed else nowElapsed).let { targetElapsed - it }).coerceAtLeast(0L)
            r.targetWallClockMs != null -> (r.targetWallClockMs - nowWall).coerceAtLeast(0L)
            else -> null
        }
    }

    private fun intervalPosition(r: ActiveTimerRecord, elapsedMs: Long): Pair<String, Int>? {
        if (r.phaseDurationsMs.isEmpty() || r.phaseLabels.size != r.phaseDurationsMs.size) return null
        val cycleMs = r.phaseDurationsMs.sum().coerceAtLeast(1L)
        val cycle = (elapsedMs / cycleMs).toInt().coerceAtMost(r.intervalCycles - 1)
        var within = elapsedMs % cycleMs
        for (i in r.phaseDurationsMs.indices) {
            if (within < r.phaseDurationsMs[i]) return r.phaseLabels[i] to (cycle + 1)
            within -= r.phaseDurationsMs[i]
        }
        return r.phaseLabels.last() to r.intervalCycles
    }
}
