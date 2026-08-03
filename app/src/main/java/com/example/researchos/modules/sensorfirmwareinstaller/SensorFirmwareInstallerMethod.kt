package com.example.researchos.modules.sensorfirmwareinstaller

import com.example.researchos.core.researchos.ArchitectureId
import com.example.researchos.core.researchos.ArchitectureRef
import com.example.researchos.core.researchos.ExecutionRequest
import com.example.researchos.core.researchos.ExecutionResult
import com.example.researchos.core.researchos.InvocationContext
import com.example.researchos.core.researchos.KnowledgeObjectType
import com.example.researchos.core.researchos.MethodContract
import com.example.researchos.core.researchos.MethodDescriptor
import com.example.researchos.core.researchos.MethodObjectType
import com.example.researchos.core.researchos.Observation
import com.example.researchos.core.researchos.ProvenanceContext
import com.example.researchos.core.researchos.Signal
import com.example.researchos.core.researchos.Transformation
import com.example.researchos.core.researchos.TransformationStatus
import com.example.researchos.core.researchos.runtime.As100ExecutionEngine
import com.example.researchos.core.researchos.runtime.As100Method
import com.example.researchos.core.researchos.withInvocationContext
import com.example.researchos.settings.SettingsState
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
    val firmwareVersion: String = "researchos-sensor-0.1.1",
    val firmwareBytes: String = "",
    val usbDevice: String = "",
    val error: String = ""
)

object As100SensorFirmwareInstallerMethod : As100Method {
    const val ID = "sensor_firmware_installer"
    private const val VERSION = "0.1.0"
    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Install ResearchOS ESP32 sensor firmware")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID), methodType = MethodObjectType.Workflow,
        name = "Install ESP32 sensor firmware", version = VERSION,
        description = "Install the bundled ResearchOS MicroPython sensor firmware to an ESP32-C3 over USB.",
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
        As100ExecutionEngine.complete(request, TransformationStatus.Unsupported, diagnostics = mapOf("reason" to "Firmware installation requires the Android USB boundary."))

    fun result(request: ExecutionRequest, outcome: SensorFirmwareInstallOutcome, invocation: InvocationContext?): ExecutionResult {
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
        val status = if (outcome.status == "installed") TransformationStatus.Succeeded else TransformationStatus.Failed
        val provenance = ProvenanceContext("android.usb", ID, VERSION)
        val observation = Observation(
            phenomenon = "sensor.firmware.installation",
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
