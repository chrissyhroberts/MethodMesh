package com.example.methodmesh.modules.time_tools

/*
 * METHODMESH INTEGRATION ADAPTER
 *
 * This file deliberately contains the thin repository-facing layer. The timing engine and
 * capability logic elsewhere in this folder are concrete. Because the current MethodMesh
 * repository source/interfaces were not supplied with this handoff, wire the six descriptors
 * below to the repository's current MethodMeshModule / Method / MethodSetting constructors.
 *
 * Required invariant: all six method IDs are independently discoverable. The dashboard is
 * an optional launcher, never the execution API.
 */
object TimeToolsModuleDefinition {
    const val moduleId = "time_tools"
    const val displayName = "Time Tools"
    const val status = "Development"
    const val iconKey = "tool"

    data class MethodDescriptor(
        val id: String,
        val title: String,
        val description: String,
        val coreOutputs: List<String>,
        val supportsPresets: Boolean = true,
        val supportsProtocols: Boolean = true,
        val supportsOdk: Boolean = true
    )

    val methods = listOf(
        MethodDescriptor(TimeToolsContract.COUNTDOWN, "Countdown", "Count down a duration.", listOf("formatted_duration", "duration_ms", "completion_status")),
        MethodDescriptor(TimeToolsContract.STOPWATCH, "Stopwatch", "Measure elapsed time with optional laps.", listOf("formatted_duration", "duration_ms", "lap_count", "laps_json")),
        MethodDescriptor(TimeToolsContract.INTERVAL, "Interval timer", "Run labelled timed phases and repeated cycles.", listOf("formatted_duration", "completed_cycles", "configured_cycles", "completion_status")),
        MethodDescriptor(TimeToolsContract.UNTIL, "Until", "Count down to a wall-clock instant.", listOf("formatted_duration", "remaining_ms", "target_timestamp")),
        MethodDescriptor(TimeToolsContract.ELAPSED, "Elapsed time", "Calculate elapsed time between timestamps.", listOf("formatted_duration", "duration_ms", "start_timestamp", "end_timestamp")),
        MethodDescriptor(TimeToolsContract.DURATION_CALCULATE, "Duration calculator", "Add or subtract durations.", listOf("formatted_duration", "duration_ms"))
    )
}
