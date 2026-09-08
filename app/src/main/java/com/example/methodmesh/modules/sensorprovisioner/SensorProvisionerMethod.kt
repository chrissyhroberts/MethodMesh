package com.example.methodmesh.modules.sensorprovisioner

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
import org.json.JSONObject
import java.time.Instant

object SensorProvisionerFields {
    const val STATUS = "sensor_provisioning_status"
    const val DEVICE_ID = "sensor_device_id"
    const val DEVICE_NAME = "sensor_device_name"
    const val DEVICE_ADDRESS = "sensor_device_address"
    const val SAMPLE_INTERVAL_MS = "sensor_sample_interval_ms"
    const val SENSOR_PROFILE = "sensor_profile"
    const val SENSOR_TYPE = "sensor_type"
    const val SENSOR_ID = "sensor_id"
    const val SENSOR_STATUS = "sensor_status"
    const val MANIFEST_JSON = "sensor_manifest_json"
    const val COMMAND_RESPONSE_JSON = "sensor_command_response_json"
    const val CONFIRMATION_READING_JSON = "sensor_confirmation_reading_json"
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
    const val REGISTRY_DEVICE_ID = "registry_device_id"
    const val ERROR = "sensor_provisioning_error"
    const val PROVISIONED_TIME_ISO = "sensor_provisioned_time_iso"

    val outputs = listOf(
        STATUS,
        DEVICE_ID,
        DEVICE_NAME,
        DEVICE_ADDRESS,
        SAMPLE_INTERVAL_MS,
        SENSOR_PROFILE,
        SENSOR_TYPE,
        SENSOR_ID,
        SENSOR_STATUS,
        MANIFEST_JSON,
        COMMAND_RESPONSE_JSON,
        CONFIRMATION_READING_JSON,
        TEMPERATURE_C,
        RELATIVE_HUMIDITY_PCT,
        PRESENCE,
        TARGET_STATE,
        MOVING_DISTANCE_CM,
        MOVING_ENERGY,
        STATIONARY_DISTANCE_CM,
        STATIONARY_ENERGY,
        DETECTION_DISTANCE_CM,
        PAYLOAD_SHA256,
        REGISTRY_DEVICE_ID,
        ERROR,
        PROVISIONED_TIME_ISO
    )
}

data class SensorProvisioningOutcome(
    val status: String,
    val deviceId: String = "",
    val deviceName: String = "",
    val deviceAddress: String = "",
    val sampleIntervalMs: String = "",
    val manifestJson: String = "",
    val commandResponseJson: String = "",
    val confirmationReadingJson: String = "",
    val registryDeviceId: String = "",
    val error: String = ""
)

object As100SensorProvisionerMethod : As100Method {
    const val ID = "sensor_node_provisioner"
    private const val VERSION = "0.1.0"
    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Provision MethodMesh BLE sensor node")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.Workflow,
        name = "Provision BLE sensor node",
        version = VERSION,
        description = "Configure a MethodMesh BLE environmental sensor node and save it into the device registry.",
        outputs = SensorProvisionerFields.outputs,
        graphOutputs = listOf("sensor.node.provisioning"),
        parameters = mapOf("category" to "Device interoperability", "status" to "Production")
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

    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?) =
        As100ExecutionEngine.complete(request, TransformationStatus.Unsupported, diagnostics = mapOf("reason" to "Sensor provisioning requires the Android BLE boundary."))

    fun result(request: ExecutionRequest, outcome: SensorProvisioningOutcome, invocation: InvocationContext?): ExecutionResult {
        val reading = extractJsonObject(outcome.confirmationReadingJson)
        val values = linkedMapOf(
            SensorProvisionerFields.STATUS to outcome.status,
            SensorProvisionerFields.DEVICE_ID to outcome.deviceId,
            SensorProvisionerFields.DEVICE_NAME to outcome.deviceName,
            SensorProvisionerFields.DEVICE_ADDRESS to outcome.deviceAddress,
            SensorProvisionerFields.SAMPLE_INTERVAL_MS to outcome.sampleIntervalMs,
            SensorProvisionerFields.SENSOR_PROFILE to reading.value("sensor_profile"),
            SensorProvisionerFields.SENSOR_TYPE to reading.value("sensor_type"),
            SensorProvisionerFields.SENSOR_ID to reading.value("sensor_id"),
            SensorProvisionerFields.SENSOR_STATUS to reading.value("status"),
            SensorProvisionerFields.MANIFEST_JSON to outcome.manifestJson,
            SensorProvisionerFields.COMMAND_RESPONSE_JSON to outcome.commandResponseJson,
            SensorProvisionerFields.CONFIRMATION_READING_JSON to outcome.confirmationReadingJson,
            SensorProvisionerFields.TEMPERATURE_C to reading.value("temperature_c"),
            SensorProvisionerFields.RELATIVE_HUMIDITY_PCT to reading.value("relative_humidity_pct"),
            SensorProvisionerFields.PRESENCE to reading.value("presence"),
            SensorProvisionerFields.TARGET_STATE to reading.value("target_state"),
            SensorProvisionerFields.MOVING_DISTANCE_CM to reading.value("moving_distance_cm"),
            SensorProvisionerFields.MOVING_ENERGY to reading.value("moving_energy"),
            SensorProvisionerFields.STATIONARY_DISTANCE_CM to reading.value("stationary_distance_cm"),
            SensorProvisionerFields.STATIONARY_ENERGY to reading.value("stationary_energy"),
            SensorProvisionerFields.DETECTION_DISTANCE_CM to reading.value("detection_distance_cm"),
            SensorProvisionerFields.PAYLOAD_SHA256 to reading.value("payload_sha256"),
            SensorProvisionerFields.REGISTRY_DEVICE_ID to outcome.registryDeviceId,
            SensorProvisionerFields.ERROR to outcome.error,
            SensorProvisionerFields.PROVISIONED_TIME_ISO to Instant.now().toString()
        ).filterValues { it.isNotBlank() }
        val status = if (outcome.status == "provisioned") TransformationStatus.Succeeded else TransformationStatus.Failed
        val provenance = ProvenanceContext(provider = "android.bluetooth", methodId = ID, methodVersion = VERSION)
        val observation = Observation(
            phenomenon = "sensor.node.provisioning",
            subject = null,
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
            observations = listOf(observation),
            transformations = listOf(transformation)
        ).withInvocationContext(invocation)
    }
}

private fun JSONObject?.value(key: String): String {
    if (this == null || !has(key) || isNull(key)) return ""
    return opt(key)?.toString().orEmpty()
}
