package com.example.methodmesh.modules.sensorread

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
import java.time.Instant

object SensorReadFields {
    const val STATUS = "sensor_read_status"
    const val MODE = "sensor_read_mode"
    const val REQUESTED_DEVICE_ID = "requested_device_id"
    const val ACTUAL_DEVICE_ID = "actual_device_id"
    const val DEVICE_NAME = "sensor_device_name"
    const val DEVICE_ADDRESS = "sensor_device_address"
    const val REQUESTED_SENSOR_ID = "requested_sensor_id"
    const val ACTUAL_SENSOR_ID = "actual_sensor_id"
    const val SENSOR_PROFILE = "sensor_profile"
    const val DEVICE_SELECTION_MODE = "device_selection_mode"
    const val DEVICE_SUBSTITUTION = "device_substitution"
    const val DEVICE_SUBSTITUTION_REASON = "device_substitution_reason"
    const val SAMPLE_COUNT = "sensor_sample_count"
    const val DURATION_SECONDS = "sensor_duration_seconds"
    const val SAMPLE_INTERVAL_SECONDS = "sensor_sample_interval_seconds"
    const val STARTED_TIME_ISO = "sensor_sample_started_time_iso"
    const val FINISHED_TIME_ISO = "sensor_sample_finished_time_iso"
    const val MANIFEST_JSON = "sensor_manifest_json"
    const val READING_JSON = "sensor_reading_json"
    const val TRACE_JSON = "sensor_trace_json"
    const val SUMMARY_JSON = "sensor_summary_json"
    const val TEMPERATURE_C = "temperature_c"
    const val RELATIVE_HUMIDITY_PCT = "relative_humidity_pct"
    const val PRESENCE = "presence"
    const val TARGET_STATE = "target_state"
    const val MOVING_DISTANCE_CM = "moving_distance_cm"
    const val MOVING_ENERGY = "moving_energy"
    const val STATIONARY_DISTANCE_CM = "stationary_distance_cm"
    const val STATIONARY_ENERGY = "stationary_energy"
    const val DETECTION_DISTANCE_CM = "detection_distance_cm"
    const val PAYLOAD_SHA256 = "payload_sha256"
    const val IN_RANGE_DEVICE_COUNT = "in_range_sensor_count"
    const val IN_RANGE_DEVICES_JSON = "in_range_devices_json"
    const val WARNING = "sensor_read_warning"
    const val ERROR = "sensor_read_error"

    val outputs = listOf(
        STATUS, MODE, REQUESTED_DEVICE_ID, ACTUAL_DEVICE_ID, DEVICE_NAME, DEVICE_ADDRESS,
        REQUESTED_SENSOR_ID, ACTUAL_SENSOR_ID, SENSOR_PROFILE, DEVICE_SELECTION_MODE,
        DEVICE_SUBSTITUTION, DEVICE_SUBSTITUTION_REASON, SAMPLE_COUNT, DURATION_SECONDS,
        SAMPLE_INTERVAL_SECONDS, STARTED_TIME_ISO, FINISHED_TIME_ISO, MANIFEST_JSON,
        READING_JSON, TRACE_JSON, SUMMARY_JSON, TEMPERATURE_C, RELATIVE_HUMIDITY_PCT,
        PRESENCE, TARGET_STATE, MOVING_DISTANCE_CM, MOVING_ENERGY, STATIONARY_DISTANCE_CM,
        STATIONARY_ENERGY, DETECTION_DISTANCE_CM, PAYLOAD_SHA256, IN_RANGE_DEVICE_COUNT,
        IN_RANGE_DEVICES_JSON, WARNING, ERROR
    )
}

data class SensorReadOutcome(
    val values: Map<String, String>,
    val succeeded: Boolean
)

object As100SensorReadMethod : As100Method {
    const val ID = "sensor.read"
    private const val VERSION = "0.1.0"
    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Read a MethodMesh sensor")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.Method,
        name = "Read sensor",
        version = VERSION,
        description = "Connect to a registered or nearby MethodMesh sensor and return single, trace, or averaged measurements.",
        outputs = SensorReadFields.outputs,
        graphOutputs = listOf("sensor.reading"),
        parameters = mapOf("category" to "Device interoperability", "transport" to "BLE")
    )
    override val contract = MethodContract(
        method = ref,
        requiredContext = emptyList(),
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs,
        producedGraphOutputs = descriptor.graphOutputs
    )

    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) =
        As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)

    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult =
        As100ExecutionEngine.complete(
            request,
            TransformationStatus.Unsupported,
            diagnostics = mapOf("reason" to "Sensor reads require the Android BLE boundary.")
        )

    fun result(request: ExecutionRequest, outcome: SensorReadOutcome, invocation: InvocationContext?): ExecutionResult {
        val status = if (outcome.succeeded) TransformationStatus.Succeeded else TransformationStatus.Failed
        val values = (outcome.values + (SensorReadFields.FINISHED_TIME_ISO to (outcome.values[SensorReadFields.FINISHED_TIME_ISO] ?: Instant.now().toString())))
            .filterValues { it.isNotBlank() }
        val entity = Entity(
            id = ArchitectureId("sensor-read:${System.currentTimeMillis()}"),
            entityType = "SensorReading",
            temporalContext = request.temporalContext
        )
        val provenance = ProvenanceContext(provider = "android.bluetooth", methodId = ID, methodVersion = VERSION)
        val observation = Observation(
            phenomenon = "sensor.reading",
            subject = ArchitectureRef(entity.id, entity.objectType, ID),
            values = values,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        val transformation = Transformation(
            action = ID,
            method = ref,
            outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)),
            status = status,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        return As100ExecutionEngine.complete(
            request = request,
            status = status,
            entities = listOf(entity),
            observations = listOf(observation),
            transformations = listOf(transformation),
            diagnostics = if (outcome.succeeded) emptyMap() else mapOf("sensor_read_error" to (outcome.values[SensorReadFields.ERROR] ?: "Sensor read failed."))
        ).withInvocationContext(invocation)
    }
}
