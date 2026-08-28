package com.example.methodmesh.modules.bluetoothinspector

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

object BluetoothInspectorFields {
    const val SCAN_RESULTS = "scan_results"
    const val SELECTED_DEVICE = "selected_device"
    const val CONNECTION_STATUS = "connection_status"
    const val GATT_ENDPOINTS = "gatt_endpoints"
    const val CAPTURED_DATA = "captured_data"
    const val SERIAL_CANDIDATES = "serial_candidates"
    const val REGISTRY_PROFILE = "registry_profile"
    const val INSPECTED_TIME_ISO = "inspected_time_iso"
    val outputs = listOf(SCAN_RESULTS, SELECTED_DEVICE, CONNECTION_STATUS, GATT_ENDPOINTS, CAPTURED_DATA, SERIAL_CANDIDATES, REGISTRY_PROFILE, INSPECTED_TIME_ISO)
}

data class BluetoothInspectionOutcome(
    val scanResults: String = "",
    val selectedDevice: String = "",
    val connectionStatus: String = "",
    val gattEndpoints: String = "",
    val capturedData: String = "",
    val serialCandidates: String = "",
    val registryProfile: String = ""
)

object As100BluetoothInspectorMethod : As100Method {
    const val ID = "bluetooth_device_inspector"
    private const val VERSION = "0.1.0"
    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Discover and assay Bluetooth devices")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID), methodType = MethodObjectType.Workflow,
        name = "Bluetooth device inspector", version = VERSION,
        description = "Discover nearby Bluetooth devices and inspect authorised BLE endpoints.",
        outputs = BluetoothInspectorFields.outputs,
        graphOutputs = listOf("bluetooth.device.inspection"),
        parameters = mapOf("category" to "Device interoperability", "status" to "Prototype")
    )
    override val contract = MethodContract(
        method = ref, requiredContext = emptyList(), producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs, producedGraphOutputs = descriptor.graphOutputs
    )
    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) =
        As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)
    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?) =
        As100ExecutionEngine.complete(request, TransformationStatus.Unsupported, diagnostics = mapOf("reason" to "Bluetooth inspection requires the Android Bluetooth boundary."))

    fun result(request: ExecutionRequest, outcome: BluetoothInspectionOutcome, invocation: InvocationContext?): ExecutionResult {
        val values = linkedMapOf(
            BluetoothInspectorFields.SCAN_RESULTS to outcome.scanResults,
            BluetoothInspectorFields.SELECTED_DEVICE to outcome.selectedDevice,
            BluetoothInspectorFields.CONNECTION_STATUS to outcome.connectionStatus,
            BluetoothInspectorFields.GATT_ENDPOINTS to outcome.gattEndpoints,
            BluetoothInspectorFields.CAPTURED_DATA to outcome.capturedData,
            BluetoothInspectorFields.SERIAL_CANDIDATES to outcome.serialCandidates,
            BluetoothInspectorFields.REGISTRY_PROFILE to outcome.registryProfile,
            BluetoothInspectorFields.INSPECTED_TIME_ISO to Instant.now().toString()
        )
        val provenance = ProvenanceContext(provider = "android.bluetooth", methodId = ID, methodVersion = VERSION)
        val observation = Observation(phenomenon = "bluetooth.device.inspection", subject = null, values = values, temporalContext = request.temporalContext, provenance = provenance)
        val transformation = Transformation(action = ID, method = ref, outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)), status = TransformationStatus.Succeeded, temporalContext = request.temporalContext, provenance = provenance)
        return As100ExecutionEngine.complete(request = request, status = TransformationStatus.Succeeded, observations = listOf(observation), transformations = listOf(transformation)).withInvocationContext(invocation)
    }
}
