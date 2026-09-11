package com.example.methodmesh.modules.time_tools

object TimeToolsContract {
    const val DASHBOARD = "time.dashboard"
    const val COUNTDOWN = "time.countdown"
    const val STOPWATCH = "time.stopwatch"
    const val INTERVAL = "time.interval"
    const val UNTIL = "time.until"
    const val ALARM = "time.alarm"
    const val ELAPSED = "time.elapsed"
    const val DURATION_CALCULATE = "time.duration.calculate"
    val methodIds = listOf(DASHBOARD, COUNTDOWN, STOPWATCH, INTERVAL, UNTIL, ALARM, ELAPSED, DURATION_CALCULATE)
}
