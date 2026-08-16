package com.example.researchos.modules.qrcode

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
import com.example.researchos.core.crypto.Digests
import com.example.researchos.settings.SettingsState
import java.time.Instant

object QrEvidenceFields {
    const val FORMAT = "qr_payload_utf8_sha256_v1"
    const val FORMAT_FIELD = "verification_evidence_format"
    const val HASH_FIELD = "verification_evidence_hash"
}

object As100QrScanMethod : As100Method {
    const val ID = "qr.scan"
    private const val VERSION = "0.3.0"

    override val id: String = ID
    override val ref: ArchitectureRef = ArchitectureRef(ArchitectureId(ID), "Method", "Automatic code scanner")
    override val descriptor: MethodDescriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.SignalInterpreter,
        name = "Automatic code scanner",
        version = VERSION,
        description = "Automatically decode QR, Data Matrix, and common 1D barcode formats and convert the payload into canonical evidence.",
        outputs = listOf(
            "qr_payload",
            "qr_payload_hash",
            QrEvidenceFields.FORMAT_FIELD,
            QrEvidenceFields.HASH_FIELD,
            "barcode_format",
            "qr_scan_time_iso",
            "qr_source"
        ),
        graphOutputs = listOf("qr.token_evidence"),
        parameters = mapOf(
            "category" to "Code scanning",
            "status" to "Experimental",
            "barcode_formats" to "optional pipe-delimited ZXing format names; all supported formats by default"
        )
    )
    override val contract: MethodContract = MethodContract(
        method = ref,
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs,
        producedGraphOutputs = descriptor.graphOutputs
    )

    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>): ExecutionRequest =
        As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)

    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult {
        val c = request.context
        val payload = c["qr_payload"].orEmpty().ifBlank { c["token"].orEmpty() }
        val source = c["qr_source"].orEmpty().ifBlank { "camera_or_external_scanner" }
        val format = c["barcode_format"].orEmpty().ifBlank { "UNKNOWN" }
        if (payload.isBlank()) {
            return As100ExecutionEngine.complete(
                request = request,
                status = TransformationStatus.Unsupported,
                diagnostics = mapOf("reason" to "QR decoding did not produce a payload.")
            )
        }
        val scanTime = Instant.ofEpochMilli(System.currentTimeMillis()).toString()
        val payloadHash = Digests.sha256Hex(payload)
        val values = linkedMapOf(
            "qr_payload" to payload,
            "qr_payload_hash" to payloadHash,
            QrEvidenceFields.FORMAT_FIELD to QrEvidenceFields.FORMAT,
            QrEvidenceFields.HASH_FIELD to payloadHash,
            "barcode_format" to format,
            "qr_scan_time_iso" to scanTime,
            "qr_source" to source
        )
        val provenance = ProvenanceContext(
            provider = "researchos.qrcode",
            methodId = ID,
            methodVersion = VERSION,
            operatorId = c["operator_id"]
        )
        val observation = Observation(
            phenomenon = "qr.token_evidence",
            subject = InvocationContext.from(c)?.subjectRef(),
            values = values,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        val transformation = Transformation(
            action = "qr.scan_token",
            method = ref,
            outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)),
            status = TransformationStatus.Succeeded,
            temporalContext = observation.temporalContext,
            provenance = provenance,
            diagnostics = mapOf(
                "qr_payload_hash" to values["qr_payload_hash"].orEmpty(),
                "barcode_format" to format,
                "qr_source" to source
            )
        )
        return As100ExecutionEngine.complete(
            request = request,
            status = TransformationStatus.Succeeded,
            observations = listOf(observation),
            transformations = listOf(transformation),
            diagnostics = transformation.diagnostics
        )
    }
}
