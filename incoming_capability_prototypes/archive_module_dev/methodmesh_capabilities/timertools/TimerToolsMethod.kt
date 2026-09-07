package com.example.methodmesh.modules.timertools

import com.example.methodmesh.core.methodmesh.*
import com.example.methodmesh.core.methodmesh.runtime.As100ExecutionEngine
import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.core.methodmesh.withInvocationContext
import com.example.methodmesh.settings.SettingsState
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

object TimerToolsFields {
    const val STATUS = "timer_status"
    const val TIMERS_JSON = "timer_timers_json"
    const val PRIMARY_NAME = "timer_primary_name"
    const val PRIMARY_MODE = "timer_primary_mode"
    const val PRIMARY_ELAPSED_MS = "timer_primary_elapsed_ms"
    const val PRIMARY_REMAINING_MS = "timer_primary_remaining_ms"
    const val LAP_COUNT = "timer_lap_count"
    const val CAPTURED_TIME_ISO = "timer_captured_time_iso"
    const val METADATA_JSON = "timer_metadata_json"
    const val ERROR = "timer_error"
    val outputs = listOf(STATUS, TIMERS_JSON, PRIMARY_NAME, PRIMARY_MODE, PRIMARY_ELAPSED_MS, PRIMARY_REMAINING_MS, LAP_COUNT, CAPTURED_TIME_ISO, METADATA_JSON, ERROR)
}

object As100TimerToolsMethod : As100Method {
    const val ID = "timer.tools"
    private const val VERSION = "0.1.0"
    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Timer tools snapshot")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID), methodType = MethodObjectType.Workflow,
        name = "Timer tools", version = VERSION,
        description = "Capture stopwatch, countdown, interval and multi-timer state.",
        outputs = TimerToolsFields.outputs, graphOutputs = listOf(ID),
        parameters = mapOf("category" to "Utility", "status" to "Development")
    )
    override val contract = MethodContract(method = ref, producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation), producedFields = descriptor.outputs, producedGraphOutputs = descriptor.graphOutputs)
    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) = As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)
    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult =
        result(request, values(request.context), InvocationContext.from(request.context))

    fun values(settings: Map<String, String>): Map<String, String> {
        val timersJson = settings.value("timers_json") ?: "[]"
        val timers = runCatching { JSONArray(timersJson) }.getOrDefault(JSONArray())
        val primary = timers.optJSONObject(0)
        val now = Instant.now().toString()
        val metadata = JSONObject().apply { put("method_id", ID); put("version", VERSION); put("timer_count", timers.length()); put("captured_time_iso", now) }
        return linkedMapOf(
            TimerToolsFields.STATUS to "succeeded",
            TimerToolsFields.TIMERS_JSON to timers.toString(),
            TimerToolsFields.PRIMARY_NAME to primary?.optString("name").orEmpty(),
            TimerToolsFields.PRIMARY_MODE to primary?.optString("mode").orEmpty(),
            TimerToolsFields.PRIMARY_ELAPSED_MS to (primary?.optLong("elapsed_ms")?.toString().orEmpty()),
            TimerToolsFields.PRIMARY_REMAINING_MS to (primary?.optLong("remaining_ms")?.toString().orEmpty()),
            TimerToolsFields.LAP_COUNT to (primary?.optJSONArray("laps_ms")?.length() ?: 0).toString(),
            TimerToolsFields.CAPTURED_TIME_ISO to now,
            TimerToolsFields.METADATA_JSON to metadata.toString(),
            TimerToolsFields.ERROR to ""
        )
    }

    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?): ExecutionResult {
        val observation = Observation(phenomenon = ID, subject = null, values = values, temporalContext = request.temporalContext, provenance = ProvenanceContext("methodmesh.timertools", ID, VERSION))
        val transformation = Transformation(action = ID, method = ref, outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)), status = TransformationStatus.Succeeded, temporalContext = request.temporalContext, provenance = ProvenanceContext("methodmesh.timertools", ID, VERSION))
        return As100ExecutionEngine.complete(request, TransformationStatus.Succeeded, observations = listOf(observation), transformations = listOf(transformation)).withInvocationContext(invocation)
    }
    private fun Map<String, String>.value(key: String) = (this[key] ?: this["input_$key"])?.takeIf { it.isNotBlank() }
}
