package com.example.methodmesh.modules.multicounter

import com.example.methodmesh.core.methodmesh.ArchitectureId
import com.example.methodmesh.core.methodmesh.ArchitectureRef
import com.example.methodmesh.core.methodmesh.Entity
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
import com.example.methodmesh.settings.SettingsState
import org.json.JSONObject
import java.time.Instant

object MulticounterFields {
    const val STATUS = "counter_status"
    const val RESULT = "counter_result"
    const val ENTITIES_JSON = "counter_entities_json"
    const val AUDIT_JSON = "counter_audit_json"
    const val FINISHED_TIME_ISO = "counter_finished_time_iso"
    const val ERROR = "counter_error"

    val outputs = listOf(
        STATUS,
        RESULT,
        ENTITIES_JSON,
        AUDIT_JSON,
        FINISHED_TIME_ISO,
        ERROR
    )
}

object As100MulticounterMethod : As100Method {
    const val ID = "counter.multientity"
    private const val VERSION = "0.1.0"

    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Multi-entity counter")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.Calculation,
        name = "Multi-counter",
        version = VERSION,
        description = "Track compact multi-entity counts, score tallies and optional staggered timers.",
        outputs = MulticounterFields.outputs,
        graphOutputs = listOf("counter.multientity"),
        parameters = mapOf("category" to "Counters", "status" to "Development")
    )
    override val contract = MethodContract(
        method = ref,
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs,
        producedGraphOutputs = descriptor.graphOutputs
    )

    override fun request(
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

    /**
     * Headless callers receive a valid initial snapshot. Normal native/ODK use is
     * interactive through MulticounterCapabilityScreen and returns only when the
     * operator finishes the session.
     */
    override fun execute(
        request: ExecutionRequest,
        settingsState: SettingsState?,
        transport: String?
    ): ExecutionResult {
        val nowIso = Instant.now().toString()
        val state = MulticounterSessionState.create(request.context, nowRealtimeMs = 0L, startedTimeIso = nowIso)
        return result(
            request = request,
            values = fieldsFor(state, nowRealtimeMs = 0L, finishedTimeIso = nowIso),
            invocation = InvocationContext.from(request.context)
        )
    }

    internal fun fieldsFor(
        state: MulticounterSessionState,
        nowRealtimeMs: Long,
        finishedTimeIso: String
    ): Map<String, String> = runCatching {
        val normalized = state.normalize(nowRealtimeMs, finishedTimeIso)
        linkedMapOf(
            MulticounterFields.STATUS to "succeeded",
            MulticounterFields.RESULT to normalized.resultText(nowRealtimeMs),
            MulticounterFields.ENTITIES_JSON to normalized.entitiesJson(nowRealtimeMs),
            MulticounterFields.AUDIT_JSON to normalized.auditJson(nowRealtimeMs, finishedTimeIso),
            MulticounterFields.FINISHED_TIME_ISO to finishedTimeIso,
            MulticounterFields.ERROR to ""
        )
    }.getOrElse { error ->
        failure(error.message ?: "Unable to create counter result.", finishedTimeIso)
    }

    fun result(
        request: ExecutionRequest,
        values: Map<String, String>,
        invocation: InvocationContext?
    ): ExecutionResult {
        val ok = values[MulticounterFields.STATUS] == "succeeded"
        val entity = Entity(
            ArchitectureId("multicounter:${System.currentTimeMillis()}"),
            "MulticounterSession",
            temporalContext = request.temporalContext
        )
        val provenance = ProvenanceContext("methodmesh.multicounter", ID, VERSION)
        val observation = Observation(
            phenomenon = "counter.multientity",
            subject = null,
            values = values,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        val transformation = Transformation(
            action = ID,
            method = ref,
            outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)),
            status = if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        return As100ExecutionEngine.complete(
            request,
            if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed,
            entities = listOf(entity),
            observations = listOf(observation),
            transformations = listOf(transformation),
            diagnostics = if (ok) emptyMap() else mapOf(
                MulticounterFields.ERROR to values[MulticounterFields.ERROR].orEmpty()
            )
        ).withInvocationContext(invocation)
    }

    fun fieldsFromJson(jsonText: String): Map<String, String> {
        val json = JSONObject(jsonText)
        return MulticounterFields.outputs.associateWith { key -> json.optString(key, "") }
    }

    fun fieldsToJson(values: Map<String, String>): String = JSONObject().apply {
        MulticounterFields.outputs.forEach { key -> put(key, values[key].orEmpty()) }
    }.toString()

    private fun failure(message: String, finishedTimeIso: String) = linkedMapOf(
        MulticounterFields.STATUS to "failed",
        MulticounterFields.RESULT to "",
        MulticounterFields.ENTITIES_JSON to "[]",
        MulticounterFields.AUDIT_JSON to "{}",
        MulticounterFields.FINISHED_TIME_ISO to finishedTimeIso,
        MulticounterFields.ERROR to message
    )
}
