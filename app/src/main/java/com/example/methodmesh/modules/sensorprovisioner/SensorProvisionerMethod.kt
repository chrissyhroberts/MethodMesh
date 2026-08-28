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
import java.time.Instant

object SensorProvisionerFields {
    const val STATUS = "sensor_provisioning_status"
    const val DEVICE_ID = "sensor_device_id"
    const val DEVICE_NAME = "sensor_device_name"
    const val DEVICE_ADDRESS = "sensor_device_address"
    const val SAMPLE_INTERVAL_MS = "sensor_sample_interval_ms"
    const val MANIFEST_JSON = "sensor_manifest_json"
    const val COMMAND_RESPONSE_JSON = "sensor_command_response_json"
    const val CONFIRMATION_READING_JSON = "sensor_confirmation_reading_json"
    const val REGISTRY_DEVICE_ID = "registry_device_id"
    const val ERROR = "sensor_provisioning_error"
    const val PROVISIONED_TIME_ISO = "sensor_provisioned_time_iso"

    val outputs = listOf(
        STATUS,
        DEVICE_ID,
        DEVICE_NAME,
        DEVICE_ADDRESS,
        SAMPLE_INTERVAL_MS,
        MANIFEST_JSON,
        COMMAND_RESPONSE_JSON,
        CONFIRMATION_READING_JSON,
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
        parameters = mapOf("category" to "Device interoperability", "status" to "Prototype")
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
        val values = linkedMapOf(
            SensorProvisionerFields.STATUS to outcome.status,
            SensorProvisionerFields.DEVICE_ID to outcome.deviceId,
            SensorProvisionerFields.DEVICE_NAME to outcome.deviceName,
            SensorProvisionerFields.DEVICE_ADDRESS to outcome.deviceAddress,
            SensorProvisionerFields.SAMPLE_INTERVAL_MS to outcome.sampleIntervalMs,
            SensorProvisionerFields.MANIFEST_JSON to outcome.manifestJson,
            SensorProvisionerFields.COMMAND_RESPONSE_JSON to outcome.commandResponseJson,
            SensorProvisionerFields.CONFIRMATION_READING_JSON to outcome.confirmationReadingJson,
            SensorProvisionerFields.REGISTRY_DEVICE_ID to outcome.registryDeviceId,
            SensorProvisionerFields.ERROR to outcome.error,
            SensorProvisionerFields.PROVISIONED_TIME_ISO to Instant.now().toString()
        )
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
