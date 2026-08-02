package com.example.researchos.modules.bluetoothprinter

import com.example.researchos.core.researchos.ArchitectureId
import com.example.researchos.core.researchos.ArchitectureRef
import com.example.researchos.core.researchos.Entity
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

object BluetoothPrinterFields {
    const val DEVICE_NAME = "printer_device_name"
    const val DEVICE_ADDRESS = "printer_device_address"
    const val SERVICE_UUID = "printer_service_uuid"
    const val WRITE_UUID = "printer_write_uuid"
    const val PAYLOAD = "printer_payload"
    const val FORMAT = "printer_payload_format"
    const val FONT_SIZE = "printer_font_size"
    const val LINE_SPACING = "printer_line_spacing"
    const val LABEL_HEIGHT = "printer_label_height"
    const val PROFILE = "printer_profile"
    const val STATUS = "printer_status"
    const val BYTES_SENT = "printer_bytes_sent"
    const val PRINTED_TIME_ISO = "printer_printed_time_iso"
    val outputs = listOf(DEVICE_NAME, DEVICE_ADDRESS, SERVICE_UUID, WRITE_UUID, PAYLOAD, FORMAT, FONT_SIZE, LINE_SPACING, LABEL_HEIGHT, PROFILE, STATUS, BYTES_SENT, PRINTED_TIME_ISO)
}

object As100BluetoothPrinterMethod : As100Method {
    const val ID = "bluetooth_print"
    private const val VERSION = "1.0.0"
    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Send a print payload to a paired Bluetooth thermal printer")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID), methodType = MethodObjectType.Workflow, name = "Bluetooth printer",
        version = VERSION, description = "Send text or raw bytes to a paired Bluetooth thermal printer endpoint.",
        outputs = BluetoothPrinterFields.outputs, graphOutputs = listOf("bluetooth.print")
    )
    override val contract = MethodContract(method = ref, requiredContext = emptyList(), producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation), producedFields = descriptor.outputs, producedGraphOutputs = descriptor.graphOutputs)
    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) = As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)
    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult = As100ExecutionEngine.complete(request, TransformationStatus.Unsupported, diagnostics = mapOf("reason" to "Bluetooth printing requires the Android Bluetooth boundary."))

    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?): ExecutionResult {
        val entity = Entity(ArchitectureId("bluetooth-print:${System.currentTimeMillis()}"), "BluetoothPrint", temporalContext = request.temporalContext)
        val provenance = ProvenanceContext("android.bluetooth", ID, VERSION)
        val observation = Observation(
            phenomenon = "bluetooth.print",
            subject = ArchitectureRef(entity.id, entity.objectType, ID),
            values = values + (BluetoothPrinterFields.PRINTED_TIME_ISO to Instant.now().toString()),
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        val ok = values[BluetoothPrinterFields.STATUS] == "succeeded"
        val transformation = Transformation(
            action = ID,
            method = ref,
            outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)),
            status = if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        return As100ExecutionEngine.complete(request, if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed, entities = listOf(entity), observations = listOf(observation), transformations = listOf(transformation), diagnostics = if (ok) emptyMap() else mapOf("status" to (values[BluetoothPrinterFields.STATUS] ?: "failed"))).withInvocationContext(invocation ?: InvocationContext.from(emptyMap()))
    }
}
