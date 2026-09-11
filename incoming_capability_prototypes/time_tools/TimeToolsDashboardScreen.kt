package com.example.methodmesh.modules.time_tools

object TimeToolsDashboardScreen {
    data class Item(val methodId: String, val label: String, val subtitle: String)
    val items = listOf(
        Item(TimeToolsContract.COUNTDOWN, "Countdown", "Minutes, hours or a target date/time"),
        Item(TimeToolsContract.STOPWATCH, "Stopwatch", "Live laps and lock-screen controls"),
        Item(TimeToolsContract.ALARM, "Alarm", "Once, daily, weekdays or custom days"),
        Item(TimeToolsContract.INTERVAL, "Intervals", "Repeated labelled phases"),
        Item(TimeToolsContract.UNTIL, "Date & time countdown", "Long-range and anchor-offset reminders"),
        Item(TimeToolsContract.ELAPSED, "Elapsed time", "Difference between two date/times"),
        Item(TimeToolsContract.DURATION_CALCULATE, "Duration calculator", "Add or subtract durations")
    )
}
