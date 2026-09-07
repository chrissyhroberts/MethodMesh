package com.example.methodmesh.modules.countertracker

import com.example.methodmesh.core.methodmesh.*
import com.example.methodmesh.core.methodmesh.runtime.As100ExecutionEngine
import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.core.methodmesh.withInvocationContext
import com.example.methodmesh.settings.SettingsState
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

object CounterTrackerFields {
    const val STATUS = "counter_status"
    const val SNAPSHOT_JSON = "counter_snapshot_json"
    const val PRIMARY_NAME = "counter_primary_name"
    const val PRIMARY_VALUE = "counter_primary_value"
    const val COUNTER_COUNT = "counter_count"
    const val ACTIVE_FLAG_COUNT = "counter_active_flag_count"
    const val CAPTURED_TIME_ISO = "counter_captured_time_iso"
    const val METADATA_JSON = "counter_metadata_json"
    const val ERROR = "counter_error"
    val outputs = listOf(STATUS, SNAPSHOT_JSON, PRIMARY_NAME, PRIMARY_VALUE, COUNTER_COUNT, ACTIVE_FLAG_COUNT, CAPTURED_TIME_ISO, METADATA_JSON, ERROR)
}

object As100CounterTrackerMethod : As100Method {
    const val ID = "counter.workspace"
    private const val VERSION = "0.1.0"

    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Counter and tracker workspace")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID), methodType = MethodObjectType.Workflow,
        name = "Counter / Tracker", version = VERSION,
        description = "Capture a snapshot from one or more named counters and status flags.",
        outputs = CounterTrackerFields.outputs,
        graphOutputs = listOf(ID),
        parameters = mapOf("category" to "Utility", "status" to "Development")
    )
    override val contract = MethodContract(
        method = ref,
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs,
        producedGraphOutputs = descriptor.graphOutputs
    )

    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) =
        As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)

    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult {
        val values = snapshotValues(request.context)
        return result(request, values, InvocationContext.from(request.context))
    }

    fun snapshotValues(settings: Map<String, String>): Map<String, String> {
        val raw = settings.value("snapshot_json").orEmpty()
        val snapshot = runCatching { JSONObject(raw) }.getOrElse {
            JSONObject().apply {
                put("counters", JSONArray().put(JSONObject().apply {
                    put("name", settings.value("name") ?: "Counter")
                    put("value", settings.value("value")?.toLongOrNull() ?: 0L)
                    put("kind", settings.value("kind") ?: "tally")
                }))
                put("flags", JSONArray())
            }
        }
        val counters = snapshot.optJSONArray("counters") ?: JSONArray()
        val flags = snapshot.optJSONArray("flags") ?: JSONArray()
        val primary = counters.optJSONObject(0)
        val activeFlags = (0 until flags.length()).count { flags.optJSONObject(it)?.optBoolean("value", false) == true }
        val captured = Instant.now().toString()
        val metadata = JSONObject().apply {
            put("method_id", ID)
            put("version", VERSION)
            put("captured_time_iso", captured)
            put("counter_count", counters.length())
            put("active_flag_count", activeFlags)
        }
        return linkedMapOf(
            CounterTrackerFields.STATUS to "succeeded",
            CounterTrackerFields.SNAPSHOT_JSON to snapshot.toString(),
            CounterTrackerFields.PRIMARY_NAME to (primary?.optString("name").orEmpty()),
            CounterTrackerFields.PRIMARY_VALUE to (primary?.optLong("value")?.toString().orEmpty()),
            CounterTrackerFields.COUNTER_COUNT to counters.length().toString(),
            CounterTrackerFields.ACTIVE_FLAG_COUNT to activeFlags.toString(),
            CounterTrackerFields.CAPTURED_TIME_ISO to captured,
            CounterTrackerFields.METADATA_JSON to metadata.toString(),
            CounterTrackerFields.ERROR to ""
        )
    }

    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?): ExecutionResult {
        val ok = values[CounterTrackerFields.STATUS] == "succeeded"
        val observation = Observation(
            phenomenon = ID, subject = null, values = values,
            temporalContext = request.temporalContext,
            provenance = ProvenanceContext("methodmesh.countertracker", ID, VERSION)
        )
        val transformation = Transformation(
            action = ID, method = ref,
            outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)),
            status = if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed,
            temporalContext = request.temporalContext,
            provenance = ProvenanceContext("methodmesh.countertracker", ID, VERSION)
        )
        return As100ExecutionEngine.complete(
            request, if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed,
            observations = listOf(observation), transformations = listOf(transformation),
            diagnostics = if (ok) emptyMap() else mapOf(CounterTrackerFields.ERROR to values[CounterTrackerFields.ERROR].orEmpty())
        ).withInvocationContext(invocation)
    }

    private fun Map<String, String>.value(key: String): String? =
        (this[key] ?: this["input_$key"])?.takeIf { it.isNotBlank() }
}
