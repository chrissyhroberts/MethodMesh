package com.example.methodmesh.modules.sensorfirmwareinstaller

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

object SensorFirmwareInstallerFields {
    const val STATUS = "firmware_install_status"
    const val BOARD = "firmware_board"
    const val FIRMWARE_NAME = "firmware_name"
    const val FIRMWARE_VERSION = "firmware_version"
    const val FIRMWARE_BYTES = "firmware_bytes"
    const val USB_DEVICE = "usb_device"
    const val ERROR = "firmware_install_error"
    const val INSTALLED_TIME_ISO = "firmware_installed_time_iso"
    val outputs = listOf(STATUS, BOARD, FIRMWARE_NAME, FIRMWARE_VERSION, FIRMWARE_BYTES, USB_DEVICE, ERROR, INSTALLED_TIME_ISO)
}

data class SensorFirmwareInstallOutcome(
    val status: String,
    val board: String = "ESP32-C3",
    val firmwareName: String = "esp32c3_sensor_node/main.py",
    val firmwareVersion: String = "methodmesh-sensor-0.1.6",
    val firmwareBytes: String = "",
    val usbDevice: String = "",
    val error: String = ""
)

fun sensorFirmwareInstallResult(
    method: As100Method,
    request: ExecutionRequest,
    outcome: SensorFirmwareInstallOutcome,
    invocation: InvocationContext?
): ExecutionResult {
    val values = linkedMapOf(
        SensorFirmwareInstallerFields.STATUS to outcome.status,
        SensorFirmwareInstallerFields.BOARD to outcome.board,
        SensorFirmwareInstallerFields.FIRMWARE_NAME to outcome.firmwareName,
        SensorFirmwareInstallerFields.FIRMWARE_VERSION to outcome.firmwareVersion,
        SensorFirmwareInstallerFields.FIRMWARE_BYTES to outcome.firmwareBytes,
        SensorFirmwareInstallerFields.USB_DEVICE to outcome.usbDevice,
        SensorFirmwareInstallerFields.ERROR to outcome.error,
        SensorFirmwareInstallerFields.INSTALLED_TIME_ISO to Instant.now().toString()
    )
    val status = if (outcome.error.isBlank() && outcome.status != "failed") TransformationStatus.Succeeded else TransformationStatus.Failed
    val methodVersion = method.descriptor.version
    val provenance = ProvenanceContext("android.usb", method.id, methodVersion)
    val observation = Observation(
        phenomenon = "sensor.firmware.installation",
        subject = null,
        values = values,
        temporalContext = request.temporalContext,
        provenance = provenance
    )
    val transformation = Transformation(
        action = method.id,
        method = method.ref,
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

private class Esp32FirmwareBoundaryMethod(
    override val id: String,
    private val methodName: String,
    private val methodDescription: String
) : As100Method {
    private val version = "0.1.0"
    override val ref = ArchitectureRef(ArchitectureId(id), "Method", methodName)
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(id),
        methodType = MethodObjectType.Workflow,
        name = methodName,
        version = version,
        description = methodDescription,
        outputs = SensorFirmwareInstallerFields.outputs,
        graphOutputs = listOf("sensor.firmware.installation"),
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
        As100ExecutionEngine.complete(request, TransformationStatus.Unsupported, diagnostics = mapOf("reason" to "ESP32 firmware operations require the Android USB boundary."))
}

object As100Esp32BoardWipeMethod : As100Method by Esp32FirmwareBoundaryMethod(
    id = "esp32.board_wipe",
    methodName = "Wipe ESP32 board",
    methodDescription = "Erase an ESP32-C3 and verify that old firmware is no longer running."
)

object As100Esp32RuntimeInstallMethod : As100Method by Esp32FirmwareBoundaryMethod(
    id = "esp32.runtime_install",
    methodName = "Install MethodMesh ESP32 runtime",
    methodDescription = "Write the bundled board-level MicroPython image and generic MethodMesh ESP32 runtime."
)

object As100Esp32SensorProfileInstallMethod : As100Method by Esp32FirmwareBoundaryMethod(
    id = "esp32.sensor_profile_install",
    methodName = "Install ESP32 sensor image",
    methodDescription = "Erase and install a complete MethodMesh ESP32-C3 image for the selected sensor."
)
