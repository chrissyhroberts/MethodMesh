package com.example.methodmesh.modules.time_tools

object TimeToolsContract {
    const val COUNTDOWN = "time.countdown"
    const val STOPWATCH = "time.stopwatch"
    const val INTERVAL = "time.interval"
    const val UNTIL = "time.until"
    const val ELAPSED = "time.elapsed"
    const val DURATION_CALCULATE = "time.duration.calculate"

    val methodIds = listOf(COUNTDOWN, STOPWATCH, INTERVAL, UNTIL, ELAPSED, DURATION_CALCULATE)

    object Output {
        const val FORMATTED_DURATION = "formatted_duration"
        const val DURATION_MS = "duration_ms"
        const val STARTED_AT = "started_at"
        const val COMPLETED_AT = "completed_at"
        const val COMPLETION_STATUS = "completion_status"
        const val METHODMESH_FULL_JSON = "methodmesh_full_json"
    }
}
