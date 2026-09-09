package com.example.methodmesh.modules.time_tools.notifications

import com.example.methodmesh.modules.time_tools.timing.AlarmRepeat
import com.example.methodmesh.modules.time_tools.timing.AlertProfile

enum class ActiveTimerKind { COUNTDOWN, STOPWATCH, INTERVAL, UNTIL, ALARM }

data class ActiveTimerRecord(
    val id: String,
    val kind: ActiveTimerKind,
    val label: String,
    val message: String = "",
    val startedWallClockMs: Long,
    val targetWallClockMs: Long? = null,
    val startedElapsedRealtimeMs: Long? = null,
    val targetElapsedRealtimeMs: Long? = null,
    val pausedAtElapsedRealtimeMs: Long? = null,
    val accumulatedPauseMs: Long = 0L,
    val finalElapsedMs: Long? = null,
    val lapTotalsMs: List<Long> = emptyList(),
    val phaseLabels: List<String> = emptyList(),
    val phaseDurationsMs: List<Long> = emptyList(),
    val intervalCycles: Int = 1,
    val alertProfile: AlertProfile = AlertProfile(),
    val ongoingNotification: Boolean = true,
    val ongoingLockScreen: Boolean = true,
    val showMessageOnLockScreen: Boolean = false,
    val completionAlert: Boolean = true,
    val requireConfirmation: Boolean = false,
    val followUpCount: Int = 0,
    val followUpIntervalMinutes: Int = 10,
    val snoozeMinutes: Int = 10,
    val zoneId: String? = null,
    val paused: Boolean = false,
    val completed: Boolean = false,
    val cancelled: Boolean = false,
    val alarmTime: String? = null,
    val alarmDate: String? = null,
    val alarmRepeat: AlarmRepeat? = null,
    val alarmWeekdays: Set<Int> = emptySet(),
    val payloadJson: String? = null
)
