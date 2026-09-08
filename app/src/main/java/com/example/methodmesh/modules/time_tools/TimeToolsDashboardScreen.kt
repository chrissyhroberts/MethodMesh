package com.example.methodmesh.modules.time_tools

/**
 * Data model for the optional Time Tools dashboard/control-centre surface.
 *
 * The dashboard is explicitly not the capability API. Each item launches the same first-class
 * method ID used by presets, protocols, schedules/widgets and ODK.
 */
object TimeToolsDashboardScreen {
    data class Item(val methodId: String, val label: String)

    val items: List<Item> = listOf(
        Item(TimeToolsContract.COUNTDOWN, "Timer"),
        Item(TimeToolsContract.STOPWATCH, "Stopwatch"),
        Item(TimeToolsContract.INTERVAL, "Intervals"),
        Item(TimeToolsContract.UNTIL, "Until…"),
        Item(TimeToolsContract.ELAPSED, "Elapsed time"),
        Item(TimeToolsContract.DURATION_CALCULATE, "Duration calculator")
    )
}
