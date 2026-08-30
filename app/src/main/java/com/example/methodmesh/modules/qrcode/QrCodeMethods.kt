package com.example.methodmesh.modules.qrcode

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
import com.example.methodmesh.core.crypto.Digests
import com.example.methodmesh.settings.SettingsState
import java.net.URI
import java.time.Instant

object BarcodeEvidenceFields {
    const val FORMAT = "barcode_payload_utf8_sha256_v1"
    const val FORMAT_FIELD = "verification_evidence_format"
    const val HASH_FIELD = "verification_evidence_hash"
}

/** Legacy QR evidence contract retained while qr.scan is deprecated. */
object QrEvidenceFields {
    const val FORMAT = "qr_payload_utf8_sha256_v1"
    const val FORMAT_FIELD = BarcodeEvidenceFields.FORMAT_FIELD
    const val HASH_FIELD = BarcodeEvidenceFields.HASH_FIELD
}

internal object BarcodePayloadSemantics {
    const val KIND_TEXT = "text"
    const val KIND_URL = "url"

    fun safeHttpUrl(payload: String): String? {
        if (payload != payload.trim()) return null
        val parsed = runCatching { URI(payload) }.getOrNull() ?: return null
        if (parsed.isOpaque || parsed.host.isNullOrBlank()) return null
        return payload.takeIf { parsed.scheme?.lowercase() in setOf("http", "https") }
    }
}

object As100BarcodeScanMethod : As100Method {
    const val ID = "barcode.scan"
    private const val VERSION = "1.0.0"

    override val id: String = ID
    override val ref: ArchitectureRef = ArchitectureRef(ArchitectureId(ID), "Method", "Automatic code scanner")
    override val descriptor: MethodDescriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.SignalInterpreter,
        name = "Automatic code scanner",
        version = VERSION,
        description = "Automatically decode QR, Data Matrix, and common 1D barcode formats and convert the payload into canonical evidence.",
        outputs = listOf(
            "barcode_payload",
            "barcode_payload_kind",
            "barcode_payload_url",
            "barcode_format",
            "barcode_payload_sha256",
            BarcodeEvidenceFields.FORMAT_FIELD,
            BarcodeEvidenceFields.HASH_FIELD,
            "barcode_scan_time_iso",
            "barcode_source"
        ),
        graphOutputs = listOf("barcode.payload_evidence"),
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

    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult =
        BarcodeScanExecution.execute(request, ref, ID, VERSION, legacyQrContract = false)
}

/**
 * Deprecated compatibility contract for existing forms and integrations.
 *
 * This remains a real registered method rather than a silent resolver alias. New
 * RIL phrases and documentation resolve to [As100BarcodeScanMethod].
 */
object As100QrScanMethod : As100Method {
    const val ID = "qr.scan"
    private const val VERSION = "0.4.0-deprecated"

    override val id: String = ID
    override val ref: ArchitectureRef = ArchitectureRef(ArchitectureId(ID), "Method", "Legacy QR scanner")
    override val descriptor: MethodDescriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.SignalInterpreter,
        name = "Legacy QR scanner",
        version = VERSION,
        description = "Deprecated compatibility contract. Use barcode.scan for new integrations.",
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
            "status" to "Deprecated",
            "replacement" to As100BarcodeScanMethod.ID,
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

    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult =
        BarcodeScanExecution.execute(request, ref, ID, VERSION, legacyQrContract = true)
}

private object BarcodeScanExecution {
    fun execute(
        request: ExecutionRequest,
        methodRef: ArchitectureRef,
        methodId: String,
        methodVersion: String,
        legacyQrContract: Boolean
    ): ExecutionResult {
        val c = request.context
        val payload = c["barcode_payload"].orEmpty()
            .ifEmpty { c["qr_payload"].orEmpty() }
            .ifEmpty { c["token"].orEmpty() }
        val source = c["barcode_source"].orEmpty()
            .ifBlank { c["qr_source"].orEmpty() }
            .ifBlank { "camera_or_external_scanner" }
        val format = c["barcode_format"].orEmpty().ifBlank { "UNKNOWN" }
        if (payload.isEmpty()) {
            return As100ExecutionEngine.complete(
                request = request,
                status = TransformationStatus.Unsupported,
                diagnostics = mapOf("reason" to "Barcode decoding did not produce a payload.")
            )
        }
        val scanTime = Instant.ofEpochMilli(System.currentTimeMillis()).toString()
        val payloadHash = Digests.sha256Hex(payload)
        val url = BarcodePayloadSemantics.safeHttpUrl(payload)
        val values = if (legacyQrContract) {
            linkedMapOf(
                "qr_payload" to payload,
                "qr_payload_hash" to payloadHash,
                QrEvidenceFields.FORMAT_FIELD to QrEvidenceFields.FORMAT,
                QrEvidenceFields.HASH_FIELD to payloadHash,
                "barcode_format" to format,
                "qr_scan_time_iso" to scanTime,
                "qr_source" to source
            )
        } else {
            linkedMapOf<String, String>().apply {
                put("barcode_payload", payload)
                put("barcode_payload_kind", if (url == null) BarcodePayloadSemantics.KIND_TEXT else BarcodePayloadSemantics.KIND_URL)
                url?.let { put("barcode_payload_url", it) }
                put("barcode_format", format)
                put("barcode_payload_sha256", payloadHash)
                put(BarcodeEvidenceFields.FORMAT_FIELD, BarcodeEvidenceFields.FORMAT)
                put(BarcodeEvidenceFields.HASH_FIELD, payloadHash)
                put("barcode_scan_time_iso", scanTime)
                put("barcode_source", source)
            }
        }
        val provenance = ProvenanceContext(
            provider = if (legacyQrContract) "methodmesh.qrcode" else "methodmesh.barcode",
            methodId = methodId,
            methodVersion = methodVersion,
            operatorId = c["operator_id"]
        )
        val observation = Observation(
            phenomenon = if (legacyQrContract) "qr.token_evidence" else "barcode.payload_evidence",
            subject = InvocationContext.from(c)?.subjectRef(),
            values = values,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        val transformation = Transformation(
            action = if (legacyQrContract) "qr.scan_token" else "barcode.scan_payload",
            method = methodRef,
            outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)),
            status = TransformationStatus.Succeeded,
            temporalContext = observation.temporalContext,
            provenance = provenance,
            diagnostics = mapOf(
                (if (legacyQrContract) "qr_payload_hash" else "barcode_payload_sha256") to payloadHash,
                "barcode_format" to format,
                (if (legacyQrContract) "qr_source" else "barcode_source") to source
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
