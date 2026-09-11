package com.example.methodmesh.modules.time_tools

import com.example.methodmesh.core.methodmesh.ArchitectureId
import com.example.methodmesh.core.methodmesh.ArchitectureRef
import com.example.methodmesh.core.methodmesh.ExecutionRequest
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.InvocationContext
import com.example.methodmesh.core.methodmesh.KnowledgeObjectType
import com.example.methodmesh.core.methodmesh.MethodContract
import com.example.methodmesh.core.methodmesh.MethodDescriptor
import com.example.methodmesh.core.methodmesh.MethodObjectType
import com.example.methodmesh.core.methodmesh.Observation
import com.example.methodmesh.core.methodmesh.ProvenanceContext
import com.example.methodmesh.core.methodmesh.Signal
import com.example.methodmesh.core.methodmesh.Transformation
import com.example.methodmesh.core.methodmesh.TransformationStatus
import com.example.methodmesh.core.methodmesh.runtime.As100ExecutionEngine
import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.core.methodmesh.withInvocationContext
import com.example.methodmesh.modules.time_tools.duration.DurationCalculatorCapability
import com.example.methodmesh.modules.time_tools.elapsed.ElapsedTimeCapability
import com.example.methodmesh.modules.time_tools.timing.TimeResultPayloads
import com.example.methodmesh.modules.time_tools.until.UntilTimeCapability
import com.example.methodmesh.settings.SettingsState
import java.time.Instant

object TimeToolsFields {
    const val STATUS = "time_status"
    const val RESULT = "time_result"
    const val FORMATTED_DURATION = "formatted_duration"
    const val DURATION_MS = "duration_ms"
    const val REQUESTED_DURATION_MS = "requested_duration_ms"
    const val ACTUAL_ELAPSED_MS = "actual_elapsed_ms"
    const val REMAINING_MS = "remaining_ms"
    const val STARTED_AT = "started_at"
    const val COMPLETED_AT = "completed_at"
    const val COMPLETION_STATUS = "completion_status"
    const val LAP_COUNT = "lap_count"
    const val LAPS_JSON = "laps_json"
    const val COMPLETED_CYCLES = "completed_cycles"
    const val CONFIGURED_CYCLES = "configured_cycles"
    const val CURRENT_PHASE = "current_phase"
    const val TARGET_TIMESTAMP = "target_timestamp"
    const val START_TIMESTAMP = "start_timestamp"
    const val END_TIMESTAMP = "end_timestamp"
    const val OPERATION = "operation"
    const val INPUT_A_MS = "input_a_ms"
    const val INPUT_B_MS = "input_b_ms"
    const val TIMER_ID = "timer_id"
    const val EXACT_ALERT_SCHEDULED = "exact_alert_scheduled"
    const val FULL_JSON = "methodmesh_full_json"
    const val MESSAGE = "message"
    const val ALARM_REPEAT = "alarm_repeat"
    const val ALARM_WEEKDAYS = "alarm_weekdays"
    const val NEXT_TRIGGER_TIMESTAMP = "next_trigger_timestamp"
    const val ERROR = "time_error"
}

private const val TIME_TOOLS_VERSION = "0.6.0"

abstract class BaseTimeToolsMethod(
    final override val id: String,
    name: String,
    description: String,
    outputs: List<String>,
    methodType: MethodObjectType = MethodObjectType.Calculation
) : As100Method {
    final override val ref = ArchitectureRef(ArchitectureId(id), "Method", name)
    final override val descriptor = MethodDescriptor(
        id = ArchitectureId(id),
        methodType = methodType,
        name = name,
        version = TIME_TOOLS_VERSION,
        description = description,
        outputs = outputs,
        graphOutputs = listOf(id),
        parameters = mapOf(
            "category" to "Time",
            "status" to "Development",
            "icon_key" to "schedule",
            "offline" to "true"
        )
    )
    final override val contract = MethodContract(
        method = ref,
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs,
        producedGraphOutputs = descriptor.graphOutputs
    )

    final override fun request(
        action: String,
        context: Map<String, String>,
        signals: List<Signal>,
        inputs: List<ArchitectureRef>
    ) = As100ExecutionEngine.request(
        action = action,
        method = ref,
        context = context,
        signals = signals,
        inputs = inputs
    )

    open override fun execute(
        request: ExecutionRequest,
        settingsState: SettingsState?,
        transport: String?
    ): ExecutionResult = result(
        request,
        failure("$id requires its interactive MethodMesh timing surface."),
        InvocationContext.from(request.context)
    )

    fun result(
        request: ExecutionRequest,
        rawValues: Map<String, String>,
        invocation: InvocationContext?
    ): ExecutionResult {
        val values = normalise(rawValues)
        val ok = values[TimeToolsFields.STATUS] != "failed"
        val provenance = ProvenanceContext("methodmesh.time_tools", id, TIME_TOOLS_VERSION)
        val observation = Observation(
            phenomenon = id,
            values = values,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        val transformation = Transformation(
            action = id,
            method = ref,
            outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)),
            status = if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        return As100ExecutionEngine.complete(
            request = request,
            status = if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed,
            observations = listOf(observation),
            transformations = listOf(transformation),
            diagnostics = if (ok) emptyMap() else mapOf(TimeToolsFields.ERROR to values[TimeToolsFields.ERROR].orEmpty())
        ).withInvocationContext(invocation)
    }

    fun successfulValues(core: Map<String, Any?>): Map<String, String> {
        val strings = core.mapValues { (_, value) -> value?.toString().orEmpty() }.toMutableMap()
        strings[TimeToolsFields.STATUS] = "succeeded"
        if (strings[TimeToolsFields.RESULT].isNullOrBlank()) {
            strings[TimeToolsFields.RESULT] = strings[TimeToolsFields.FORMATTED_DURATION].orEmpty()
        }
        strings[TimeToolsFields.ERROR] = ""
        strings[TimeToolsFields.FULL_JSON] = TimeResultPayloads.fullJson(core, id)
        return strings
    }

    fun failure(message: String): Map<String, String> = linkedMapOf(
        TimeToolsFields.STATUS to "failed",
        TimeToolsFields.RESULT to "",
        TimeToolsFields.FULL_JSON to TimeResultPayloads.fullJson(mapOf("error" to message), id),
        TimeToolsFields.ERROR to message
    )

    private fun normalise(raw: Map<String, String>): Map<String, String> {
        val values = raw.toMutableMap()
        values.putIfAbsent(TimeToolsFields.STATUS, "succeeded")
        values.putIfAbsent(TimeToolsFields.RESULT, values[TimeToolsFields.FORMATTED_DURATION].orEmpty())
        values.putIfAbsent(TimeToolsFields.ERROR, "")
        values.putIfAbsent(
            TimeToolsFields.FULL_JSON,
            TimeResultPayloads.fullJson(values.filterKeys { it != TimeToolsFields.FULL_JSON }, id)
        )
        return values
    }

    protected fun Map<String, String>.value(key: String): String? =
        (this[key] ?: this["input_$key"])?.trim()?.takeIf(String::isNotEmpty)
}

object As100DashboardMethod : BaseTimeToolsMethod(
    id = TimeToolsContract.DASHBOARD, name = "Time & alarms", description = "Manage active timers, stopwatches and alarms.",
    outputs = listOf(TimeToolsFields.STATUS, TimeToolsFields.RESULT, TimeToolsFields.FULL_JSON, TimeToolsFields.ERROR),
    methodType = MethodObjectType.Workflow
) { const val ID = TimeToolsContract.DASHBOARD }

object As100CountdownMethod : BaseTimeToolsMethod(
    id = TimeToolsContract.COUNTDOWN,
    name = "Countdown",
    description = "Count down a duration with optional persistent and due notifications.",
    outputs = listOf(
        TimeToolsFields.RESULT,
        TimeToolsFields.FORMATTED_DURATION,
        TimeToolsFields.DURATION_MS,
        TimeToolsFields.REQUESTED_DURATION_MS,
        TimeToolsFields.ACTUAL_ELAPSED_MS,
        TimeToolsFields.STARTED_AT,
        TimeToolsFields.COMPLETED_AT,
        TimeToolsFields.COMPLETION_STATUS,
        TimeToolsFields.MESSAGE,
        TimeToolsFields.TIMER_ID,
        TimeToolsFields.EXACT_ALERT_SCHEDULED,
        TimeToolsFields.STATUS,
        TimeToolsFields.FULL_JSON,
        TimeToolsFields.ERROR
    )
) {
    const val ID = TimeToolsContract.COUNTDOWN
}

object As100StopwatchMethod : BaseTimeToolsMethod(
    id = TimeToolsContract.STOPWATCH,
    name = "Stopwatch",
    description = "Measure elapsed time with optional laps and a persistent count-up notification.",
    outputs = listOf(
        TimeToolsFields.RESULT,
        TimeToolsFields.FORMATTED_DURATION,
        TimeToolsFields.DURATION_MS,
        TimeToolsFields.LAP_COUNT,
        TimeToolsFields.LAPS_JSON,
        TimeToolsFields.STARTED_AT,
        TimeToolsFields.COMPLETED_AT,
        TimeToolsFields.COMPLETION_STATUS,
        TimeToolsFields.MESSAGE,
        TimeToolsFields.TIMER_ID,
        TimeToolsFields.STATUS,
        TimeToolsFields.FULL_JSON,
        TimeToolsFields.ERROR
    )
) {
    const val ID = TimeToolsContract.STOPWATCH
}

object As100IntervalMethod : BaseTimeToolsMethod(
    id = TimeToolsContract.INTERVAL,
    name = "Interval timer",
    description = "Run labelled timed phases over one or more cycles.",
    outputs = listOf(
        TimeToolsFields.RESULT,
        TimeToolsFields.FORMATTED_DURATION,
        TimeToolsFields.DURATION_MS,
        TimeToolsFields.COMPLETED_CYCLES,
        TimeToolsFields.CONFIGURED_CYCLES,
        TimeToolsFields.CURRENT_PHASE,
        TimeToolsFields.COMPLETION_STATUS,
        TimeToolsFields.MESSAGE,
        TimeToolsFields.TIMER_ID,
        TimeToolsFields.EXACT_ALERT_SCHEDULED,
        TimeToolsFields.STATUS,
        TimeToolsFields.FULL_JSON,
        TimeToolsFields.ERROR
    )
) {
    const val ID = TimeToolsContract.INTERVAL
}

object As100UntilMethod : BaseTimeToolsMethod(
    id = TimeToolsContract.UNTIL,
    name = "Date & time countdown",
    description = "Count down to a wall-clock target or a local time a number of calendar days after an anchor.",
    outputs = listOf(
        TimeToolsFields.RESULT,
        TimeToolsFields.FORMATTED_DURATION,
        TimeToolsFields.REMAINING_MS,
        TimeToolsFields.TARGET_TIMESTAMP,
        TimeToolsFields.COMPLETION_STATUS,
        TimeToolsFields.MESSAGE,
        TimeToolsFields.TIMER_ID,
        TimeToolsFields.EXACT_ALERT_SCHEDULED,
        TimeToolsFields.STATUS,
        TimeToolsFields.FULL_JSON,
        TimeToolsFields.ERROR
    )
) {
    const val ID = TimeToolsContract.UNTIL

    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult {
        val values = runCatching {
            val context = request.context
            val mode = context.value("target_mode") ?: "absolute"
            val target = if (mode == "anchor_offset_local_time") {
                val anchor = context.value("anchor_timestamp") ?: Instant.now().toString()
                val offset = context.value("day_offset")?.toIntOrNull() ?: 0
                val localTime = context.value("local_time") ?: "21:00"
                val zone = context.value("zone_id").orEmpty().ifBlank { java.time.ZoneId.systemDefault().id }
                UntilTimeCapability.daysAfterAnchorAtLocalTime(anchor, offset, localTime, zone)
            } else {
                context.value("target_timestamp") ?: error("target_timestamp is required in absolute mode")
            }
            successfulValues(UntilTimeCapability.execute(target))
        }.getOrElse { failure(it.message ?: "Unable to calculate target time") }
        return result(request, values, InvocationContext.from(request.context))
    }
}

object As100AlarmMethod : BaseTimeToolsMethod(
    id = TimeToolsContract.ALARM,
    name = "Alarm",
    description = "Create a one-off or recurring alarm with snooze, confirmation and follow-up reminders.",
    outputs = listOf(
        TimeToolsFields.RESULT, TimeToolsFields.NEXT_TRIGGER_TIMESTAMP, TimeToolsFields.ALARM_REPEAT, TimeToolsFields.ALARM_WEEKDAYS,
        TimeToolsFields.MESSAGE, TimeToolsFields.TIMER_ID, TimeToolsFields.EXACT_ALERT_SCHEDULED, TimeToolsFields.STATUS, TimeToolsFields.FULL_JSON, TimeToolsFields.ERROR
    ),
    methodType = MethodObjectType.Workflow
) { const val ID = TimeToolsContract.ALARM }

object As100ElapsedMethod : BaseTimeToolsMethod(
    id = TimeToolsContract.ELAPSED,
    name = "Elapsed time",
    description = "Calculate elapsed time between two ISO-8601 timestamps.",
    outputs = listOf(
        TimeToolsFields.RESULT,
        TimeToolsFields.FORMATTED_DURATION,
        TimeToolsFields.DURATION_MS,
        TimeToolsFields.START_TIMESTAMP,
        TimeToolsFields.END_TIMESTAMP,
        TimeToolsFields.STATUS,
        TimeToolsFields.FULL_JSON,
        TimeToolsFields.ERROR
    )
) {
    const val ID = TimeToolsContract.ELAPSED

    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult {
        val values = runCatching {
            val start = request.context.value("start_timestamp") ?: error("start_timestamp is required")
            val end = request.context.value("end_timestamp") ?: error("end_timestamp is required")
            successfulValues(ElapsedTimeCapability.execute(start, end))
        }.getOrElse { failure(it.message ?: "Unable to calculate elapsed time") }
        return result(request, values, InvocationContext.from(request.context))
    }
}

object As100DurationCalculateMethod : BaseTimeToolsMethod(
    id = TimeToolsContract.DURATION_CALCULATE,
    name = "Duration calculator",
    description = "Add or subtract two durations.",
    outputs = listOf(
        TimeToolsFields.RESULT,
        TimeToolsFields.FORMATTED_DURATION,
        TimeToolsFields.DURATION_MS,
        TimeToolsFields.OPERATION,
        TimeToolsFields.INPUT_A_MS,
        TimeToolsFields.INPUT_B_MS,
        TimeToolsFields.STATUS,
        TimeToolsFields.FULL_JSON,
        TimeToolsFields.ERROR
    )
) {
    const val ID = TimeToolsContract.DURATION_CALCULATE

    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult {
        val values = runCatching {
            val a = request.context.value("a_ms")?.toLongOrNull()
                ?: request.context.value("duration_a_ms")?.toLongOrNull()
                ?: 0L
            val b = request.context.value("b_ms")?.toLongOrNull()
                ?: request.context.value("duration_b_ms")?.toLongOrNull()
                ?: 0L
            val operation = when ((request.context.value("operation") ?: "add").lowercase()) {
                "subtract", "sub", "minus" -> DurationCalculatorCapability.Operation.SUBTRACT
                else -> DurationCalculatorCapability.Operation.ADD
            }
            val core = DurationCalculatorCapability.execute(a, b, operation).toMutableMap().apply {
                this[TimeToolsFields.INPUT_A_MS] = a
                this[TimeToolsFields.INPUT_B_MS] = b
            }
            successfulValues(core)
        }.getOrElse { failure(it.message ?: "Unable to calculate duration") }
        return result(request, values, InvocationContext.from(request.context))
    }
}
