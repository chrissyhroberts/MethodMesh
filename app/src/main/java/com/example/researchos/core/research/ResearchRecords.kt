package com.example.researchos.core.research

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class ResearchLayer {
    Protocol,
    Workflow,
    Activity,
    Session,
    Evidence,
    Artifact,
    Transport
}

enum class MethodFamily {
    Measurement,
    Interaction,
    Identification,
    Intervention,
    Utility
}

enum class CaptureStrategy {
    Instant,
    Manual,
    Average,
    Stable,
    Threshold,
    Trigger,
    Continuous
}

enum class CaptureOutcomeStatus {
    Success,
    Override,
    Timeout,
    Cancelled,
    Error,
    Unsupported
}

enum class EvidenceKind {
    Observation,
    Intervention,
    DerivedState,
    Attestation
}

enum class TemporalSemantics {
    PointObservation,
    RepeatedPointObservation,
    WindowedObservation,
    TimeSeries,
    Trajectory,
    StateAtTime,
    DerivedSummary,
    CumulativeRecord
}

enum class AggregationSemantics {
    NonAggregatable,
    Averagable,
    Countable,
    Concatenatable,
    TraceableOverTime,
    BeforeAfterComparable,
    SpatiallyMappable,
    IdentifyingOnly,
    AttestationOnly,
    DerivedState
}

enum class LineageSemantics {
    SingleEvent,
    SummaryOfSamples,
    OrderedSequence,
    ComputedFromRecordSet,
    OperatorDeclared,
    DeviceReported,
    ActionOutcome
}

enum class ArtifactKind {
    NfcTag,
    Image,
    Location,
    Trace,
    Annotation,
    Document,
    StructuredJson
}

private fun mapToJsonObject(map: Map<String, *>): JSONObject {
    val json = JSONObject()
    for ((key, value) in map) {
        json.put(key, value)
    }
    return json
}

private fun recordsToJsonArray(records: Iterable<Map<String, String>>): JSONArray {
    val array = JSONArray()
    for (record in records) {
        array.put(mapToJsonObject(record))
    }
    return array
}

private fun namesToJsonArray(values: Iterable<String>): JSONArray {
    val array = JSONArray()
    for (value in values) {
        array.put(value)
    }
    return array
}

private fun MutableMap<String, String>.putEntries(entries: Map<String, String>) {
    for ((key, value) in entries) {
        this[key] = value
    }
}

private fun MutableMap<String, String>.putPrefixedEntries(prefix: String, entries: Map<String, String>) {
    for ((key, value) in entries) {
        this["$prefix$key"] = value
    }
}

data class ProvenanceRecord(
    val methodId: String,
    val methodVersion: String,
    val observedAtEpochMs: Long = System.currentTimeMillis(),
    val deviceManufacturer: String = android.os.Build.MANUFACTURER.orEmpty(),
    val deviceModel: String = android.os.Build.MODEL.orEmpty(),
    val androidSdkInt: Int = android.os.Build.VERSION.SDK_INT,
    val provider: String,
    val executionId: String = UUID.randomUUID().toString(),
    val sessionId: String = executionId,
    val operatorId: String? = null,
    val extra: Map<String, String> = emptyMap()
) {
    fun asMap(): Map<String, String> = linkedMapOf<String, String>().apply {
        put("method_id", methodId)
        put("method_version", methodVersion)
        put("observed_at_epoch_ms", observedAtEpochMs.toString())
        put("device_manufacturer", deviceManufacturer)
        put("device_model", deviceModel)
        put("android_sdk_int", androidSdkInt.toString())
        put("provider", provider)
        put("execution_id", executionId)
        put("session_id", sessionId)
        operatorId?.let { put("operator_id", it) }
        putEntries(extra)
    }

    fun toJson(): String = mapToJsonObject(asMap()).toString()
}

data class CaptureOutcome(
    val strategy: CaptureStrategy,
    val status: CaptureOutcomeStatus,
    val message: String = "",
    val startedAtEpochMs: Long? = null,
    val completedAtEpochMs: Long = System.currentTimeMillis(),
    val overrideReason: String? = null
) {
    fun asMap(): Map<String, String> = linkedMapOf<String, String>().apply {
        put("capture_strategy", strategy.name)
        put("capture_status", status.name)
        put("capture_message", message)
        startedAtEpochMs?.let { put("capture_started_at_epoch_ms", it.toString()) }
        put("capture_completed_at_epoch_ms", completedAtEpochMs.toString())
        overrideReason?.let { put("override_reason", it) }
    }

    fun toJson(): String = mapToJsonObject(asMap()).toString()
}

data class QualityRecord(
    val valid: Boolean,
    val grade: String = if (valid) "usable" else "not_usable",
    val messages: List<String> = emptyList(),
    val metrics: Map<String, String> = emptyMap()
) {
    fun asMap(): Map<String, String> = linkedMapOf<String, String>().apply {
        put("quality_valid", valid.toString())
        put("quality_grade", grade)
        put("quality_messages", messages.joinToString(" | "))
        putEntries(metrics)
    }

    fun toJson(): String = mapToJsonObject(asMap()).toString()
}

data class ValidationRecord(
    val passed: Boolean,
    val messages: List<String> = emptyList()
) {
    fun asMap(): Map<String, String> = mapOf(
        "validation_passed" to passed.toString(),
        "validation_messages" to messages.joinToString(" | ")
    )

    fun toJson(): String = mapToJsonObject(asMap()).toString()
}

data class EvidenceRecord(
    val id: String = UUID.randomUUID().toString(),
    val kind: EvidenceKind,
    val phenomenon: String,
    val values: Map<String, String>,
    val unit: String? = null,
    val method: String,
    val temporalSemantics: TemporalSemantics,
    val aggregationSemantics: AggregationSemantics,
    val lineage: LineageSemantics,
    val provenance: ProvenanceRecord,
    val captureOutcome: CaptureOutcome,
    val quality: QualityRecord,
    val validation: ValidationRecord
) {
    fun semanticsMap(): Map<String, String> = linkedMapOf<String, String>().apply {
        put("evidence_id", id)
        put("evidence_kind", kind.name)
        put("phenomenon", phenomenon)
        unit?.let { put("unit", it) }
        put("method", method)
        put("temporal_semantics", temporalSemantics.name)
        put("aggregation_semantics", aggregationSemantics.name)
        put("lineage", lineage.name)
    }

    fun asMap(): Map<String, String> {
        val map = linkedMapOf<String, String>()
        map.putEntries(semanticsMap())
        map.putEntries(this.values)
        map.putEntries(provenance.asMap())
        map.putEntries(captureOutcome.asMap())
        map.putEntries(quality.asMap())
        map.putEntries(validation.asMap())
        return map
    }

    fun toJson(): String = mapToJsonObject(asMap()).toString()
}

data class ArtifactRecord(
    val id: String = UUID.randomUUID().toString(),
    val kind: ArtifactKind,
    val mediaType: String,
    val values: Map<String, String>,
    val provenance: ProvenanceRecord
) {
    fun asMap(): Map<String, String> {
        val map = linkedMapOf<String, String>()
        map["artifact_id"] = id
        map["artifact_kind"] = kind.name
        map["artifact_media_type"] = mediaType
        map.putEntries(this.values)
        map.putPrefixedEntries("artifact_", provenance.asMap())
        return map
    }

    fun toJson(): String = mapToJsonObject(asMap()).toString()
}

data class InterventionRecord(
    val id: String = UUID.randomUUID().toString(),
    val action: String,
    val target: String,
    val inputs: Map<String, String>,
    val outcome: CaptureOutcome,
    val provenance: ProvenanceRecord,
    val validation: ValidationRecord
) {
    fun asMap(): Map<String, String> = linkedMapOf<String, String>().apply {
        put("intervention_id", id)
        put("intervention_action", action)
        put("intervention_target", target)
        putPrefixedEntries("intervention_input_", inputs)
        putEntries(outcome.asMap())
        putEntries(provenance.asMap())
        putEntries(validation.asMap())
    }

    fun toJson(): String = mapToJsonObject(asMap()).toString()
}

data class MethodExecutionRecord(
    val id: String,
    val methodId: String,
    val methodVersion: String,
    val layers: List<ResearchLayer>,
    val evidence: List<EvidenceRecord> = emptyList(),
    val artifacts: List<ArtifactRecord> = emptyList(),
    val interventions: List<InterventionRecord> = emptyList(),
    val diagnostics: Map<String, String> = emptyMap()
) {
    fun toJson(): String = JSONObject().apply {
        put("execution_id", id)
        put("method_id", methodId)
        put("method_version", methodVersion)
        put("layers", namesToJsonArray(layers.map { it.name }))
        put("evidence", recordsToJsonArray(evidence.map { it.asMap() }))
        put("artifacts", recordsToJsonArray(artifacts.map { it.asMap() }))
        put("interventions", recordsToJsonArray(interventions.map { it.asMap() }))
        put("diagnostics", mapToJsonObject(diagnostics))
    }.toString()
}
