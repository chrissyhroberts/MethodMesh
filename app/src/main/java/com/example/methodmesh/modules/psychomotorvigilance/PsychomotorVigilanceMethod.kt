package com.example.methodmesh.modules.psychomotorvigilance

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
import com.example.methodmesh.settings.SettingsState
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

object PsychomotorVigilanceFields {
    const val STATUS = "pvt_status"
    const val RESULT = "pvt_result"
    const val PROTOCOL = "pvt_protocol"
    const val RESPONSE_SPEED = "pvt_response_speed_per_s"
    const val LAPSES = "pvt_lapses"
    const val FALSE_STARTS = "pvt_false_starts"
    const val LAPSES_PLUS_FALSE_STARTS = "pvt_lapses_plus_false_starts"
    const val VALID_RESPONSES = "pvt_valid_responses"
    const val PERFORMANCE_SCORE = "pvt_performance_score_percent"
    const val AUDIT_JSON = "pvt_audit_json"
    const val ERROR = "pvt_error"

    val outputs = listOf(
        STATUS,
        RESULT,
        PROTOCOL,
        RESPONSE_SPEED,
        LAPSES,
        FALSE_STARTS,
        LAPSES_PLUS_FALSE_STARTS,
        VALID_RESPONSES,
        PERFORMANCE_SCORE,
        AUDIT_JSON,
        ERROR
    )
}

object As100PsychomotorVigilanceMethod : As100Method {
    const val ID = "psychomotor.vigilance.run"
    const val VERSION = "0.1.0"

    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Psychomotor vigilance test")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.Method,
        name = "Psychomotor vigilance test (PVT)",
        version = VERSION,
        description = "Run a locally timed visual PVT or PVT-B session and return vigilance/reaction-time outcomes.",
        outputs = PsychomotorVigilanceFields.outputs,
        graphOutputs = listOf("psychomotor.vigilance"),
        parameters = mapOf(
            "category" to "Behavioural measurement",
            "status" to "Development"
        )
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

    override fun execute(
        request: ExecutionRequest,
        settingsState: SettingsState?,
        transport: String?
    ): ExecutionResult = failureResult(
        request,
        "PVT requires the interactive capability screen and cannot be executed as a headless calculation.",
        InvocationContext.from(request.context)
    )

    fun result(
        request: ExecutionRequest,
        values: Map<String, String>,
        invocation: InvocationContext?
    ): ExecutionResult {
        val ok = values[PsychomotorVigilanceFields.STATUS] == "succeeded"
        val provenance = ProvenanceContext("methodmesh.pvt", ID, VERSION)
        val observation = Observation(
            phenomenon = "psychomotor.vigilance",
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
            observations = listOf(observation),
            transformations = listOf(transformation),
            diagnostics = if (ok) emptyMap() else mapOf(
                PsychomotorVigilanceFields.ERROR to values[PsychomotorVigilanceFields.ERROR].orEmpty()
            )
        ).withInvocationContext(invocation)
    }

    fun valuesForSession(session: PvtSession): Map<String, String> {
        val scores = PvtScoring.score(session)
        val speed = scores.responseSpeedPerSecond?.format(3).orEmpty()
        val performance = scores.performanceScorePercent?.format(1).orEmpty()
        val resultText = buildString {
            append("Response speed ")
            append(speed.ifBlank { "n/a" })
            append(" s⁻¹ · ")
            append(scores.lapses)
            append(if (scores.lapses == 1) " lapse · " else " lapses · ")
            append(scores.falseStarts)
            append(if (scores.falseStarts == 1) " false start" else " false starts")
        }
        return linkedMapOf(
            PsychomotorVigilanceFields.STATUS to "succeeded",
            PsychomotorVigilanceFields.RESULT to resultText,
            PsychomotorVigilanceFields.PROTOCOL to session.protocol.key,
            PsychomotorVigilanceFields.RESPONSE_SPEED to speed,
            PsychomotorVigilanceFields.LAPSES to scores.lapses.toString(),
            PsychomotorVigilanceFields.FALSE_STARTS to scores.falseStarts.toString(),
            PsychomotorVigilanceFields.LAPSES_PLUS_FALSE_STARTS to scores.lapsesPlusFalseStarts.toString(),
            PsychomotorVigilanceFields.VALID_RESPONSES to scores.validResponses.toString(),
            PsychomotorVigilanceFields.PERFORMANCE_SCORE to performance,
            PsychomotorVigilanceFields.AUDIT_JSON to auditJson(session, scores).toString(),
            PsychomotorVigilanceFields.ERROR to ""
        )
    }

    private fun failureResult(
        request: ExecutionRequest,
        error: String,
        invocation: InvocationContext?
    ): ExecutionResult = result(
        request,
        linkedMapOf(
            PsychomotorVigilanceFields.STATUS to "failed",
            PsychomotorVigilanceFields.RESULT to "",
            PsychomotorVigilanceFields.PROTOCOL to request.context.value("protocol").orEmpty(),
            PsychomotorVigilanceFields.RESPONSE_SPEED to "",
            PsychomotorVigilanceFields.LAPSES to "",
            PsychomotorVigilanceFields.FALSE_STARTS to "",
            PsychomotorVigilanceFields.LAPSES_PLUS_FALSE_STARTS to "",
            PsychomotorVigilanceFields.VALID_RESPONSES to "",
            PsychomotorVigilanceFields.PERFORMANCE_SCORE to "",
            PsychomotorVigilanceFields.AUDIT_JSON to "{}",
            PsychomotorVigilanceFields.ERROR to error
        ),
        invocation
    )

    private fun auditJson(session: PvtSession, scores: PvtScores): JSONObject = JSONObject().apply {
        put("schema_version", "methodmesh.pvt.audit.v1")
        put("implementation", JSONObject().apply {
            put("method_id", ID)
            put("method_version", VERSION)
            put("status", "Development")
            put("network_used", false)
            put("participant_data_transmitted", false)
        })
        put("protocol", JSONObject().apply {
            put("key", session.protocol.key)
            put("display_name", session.protocol.displayName)
            put("task_duration_ms", session.protocol.taskDurationMs)
            put("min_isi_ms", session.protocol.minIsiMs)
            put("max_isi_ms", session.protocol.maxIsiMs)
            put("lapse_threshold_ms", session.protocol.lapseThresholdMs)
            put("false_start_threshold_ms", session.protocol.falseStartThresholdMs)
            put("response_timeout_ms", session.protocol.responseTimeoutMs)
            put("feedback_duration_ms", session.protocol.feedbackDurationMs)
            put("countdown_seconds", session.countdownSeconds)
        })
        put("session", JSONObject().apply {
            put("started_time_iso", session.startedTimeIso)
            put("ended_time_iso", session.endedTimeIso)
            put("actual_duration_ms", session.actualDurationMs)
            put("valid_responses", scores.validResponses)
            put("lapses", scores.lapses)
            put("false_starts", scores.falseStarts)
            put("timeouts", scores.timeouts)
        })
        put("scores", JSONObject().apply {
            putNullable("response_speed_per_s", scores.responseSpeedPerSecond)
            putNullable("lapse_probability", scores.lapseProbability)
            putNullable("performance_score_percent", scores.performanceScorePercent)
            putNullable("mean_rt_ms", scores.meanRtMs)
            putNullable("median_rt_ms", scores.medianRtMs)
            putNullable("fastest_10pct_mean_rt_ms", scores.fastest10PctMeanRtMs)
            putNullable("slowest_10pct_response_speed_per_s", scores.slowest10PctResponseSpeedPerSecond)
        })
        put("timing", JSONObject().apply {
            put("clock", session.timingClock)
            put("stimulus_timestamp_method", session.stimulusTimestampMethod)
            put("response_timestamp_method", session.responseTimestampMethod)
            put("response_modality", session.responseModality)
            put("device_calibrated", false)
            put("calibration_status", "not_characterised")
            put("warning", "Software timestamps do not measure display scan-out or touchscreen hardware latency. Device-level calibration is required before claiming timing-equivalence with validated PVT hardware.")
        })
        put("device", JSONObject().apply {
            put("manufacturer", session.manufacturer)
            put("model", session.model)
            put("sdk_int", session.sdkInt)
            put("display_refresh_rate_hz", session.displayRefreshRateHz.toDouble())
            put("screen_width_px", session.screenWidthPx)
            put("screen_height_px", session.screenHeightPx)
        })
        put("trials", JSONArray().apply {
            session.trials.forEach { trial ->
                put(JSONObject().apply {
                    put("sequence", trial.sequence)
                    putNullable("isi_ms", trial.isiMs)
                    putNullable("stimulus_onset_uptime_ms", trial.stimulusOnsetUptimeMs)
                    putNullable("response_uptime_ms", trial.responseUptimeMs)
                    putNullable("reaction_time_ms", trial.reactionTimeMs)
                    put("outcome", trial.outcome.name.lowercase(Locale.US))
                    putNullable("stimulus_onset_time_iso", trial.stimulusOnsetTimeIso)
                })
            }
        })
        put("references", JSONArray().apply {
            put("Dinges DF, Powell JW. Behav Res Methods Instrum Comput. 1985;17:652-655. doi:10.3758/BF03200977")
            put("Basner M, Dinges DF. Sleep. 2011;34(5):581-591. doi:10.1093/sleep/34.5.581")
            put("Basner M, Mollicone D, Dinges DF. Acta Astronaut. 2011;69:949-959. doi:10.1016/j.actaastro.2011.07.015")
            put("Basner M et al. Sleep. 2021;44(1):zsaa121. doi:10.1093/sleep/zsaa121")
        })
    }

    private fun JSONObject.putNullable(key: String, value: Any?) {
        put(key, value ?: JSONObject.NULL)
    }

    private fun Map<String, String>.value(key: String): String? =
        (this[key] ?: this["input_$key"])?.takeIf { it.isNotBlank() }

    private fun Double.format(decimals: Int): String =
        String.format(Locale.US, "%.${decimals}f", this)
}
