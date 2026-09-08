package com.example.methodmesh.modules.bluetoothprinter

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

object As100BluetoothPrinterMethod : As100Method {
    const val ID = "bluetooth_print"
    const val VERSION = "2.12.0"

    override val id = ID
    override val ref = ArchitectureRef(
        ArchitectureId(ID),
        "Method",
        "Print via the Qutie/FF00 family of low-cost BLE thermal printers"
    )

    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.Workflow,
        name = "Qutie-family thermal printer",
        version = VERSION,
        description = "Compose and print text, QR codes, Code 128 barcodes, or raw bytes using the Qutie-compatible FF00/LuckPrinter-family BLE protocol.",
        outputs = BluetoothPrinterFields.outputs,
        graphOutputs = listOf("bluetooth.print"),
        parameters = mapOf("category" to "Device interoperability", "status" to "Production")
    )

    override val contract = MethodContract(
        method = ref,
        requiredContext = emptyList(),
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
    ): ExecutionResult = As100ExecutionEngine.complete(
        request,
        TransformationStatus.Unsupported,
        diagnostics = mapOf("reason" to "Bluetooth printing requires the Android Bluetooth boundary.")
    )

    fun result(
        request: ExecutionRequest,
        values: Map<String, String>,
        invocation: InvocationContext?
    ): ExecutionResult {
        val entity = Entity(
            ArchitectureId("bluetooth-print:${System.currentTimeMillis()}"),
            "BluetoothPrint",
            temporalContext = request.temporalContext
        )
        val provenance = ProvenanceContext("android.bluetooth", ID, VERSION)
        val observation = Observation(
            phenomenon = "bluetooth.print",
            subject = ArchitectureRef(entity.id, entity.objectType, ID),
            values = values + (BluetoothPrinterFields.PRINTED_TIME_ISO to Instant.now().toString()),
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        val succeeded = values[BluetoothPrinterFields.STATUS] == "succeeded"
        val transformation = Transformation(
            action = ID,
            method = ref,
            outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)),
            status = if (succeeded) TransformationStatus.Succeeded else TransformationStatus.Failed,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        return As100ExecutionEngine.complete(
            request,
            if (succeeded) TransformationStatus.Succeeded else TransformationStatus.Failed,
            entities = listOf(entity),
            observations = listOf(observation),
            transformations = listOf(transformation),
            diagnostics = if (succeeded) {
                emptyMap()
            } else {
                mapOf("status" to (values[BluetoothPrinterFields.STATUS] ?: "failed"))
            }
        ).withInvocationContext(invocation ?: InvocationContext.from(emptyMap()))
    }
}
