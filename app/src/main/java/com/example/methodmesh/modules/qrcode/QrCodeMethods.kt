package com.example.methodmesh.modules.qrcode

import com.example.methodmesh.core.crypto.Digests
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
import com.example.methodmesh.settings.SettingsState
import java.net.URI
import java.time.Instant

object BarcodeEvidenceFields {
    const val FORMAT = "barcode_payload_utf8_sha256_v1"
    const val FORMAT_FIELD = "verification_evidence_format"
    const val HASH_FIELD = "verification_evidence_hash"
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
    internal const val VERSION = "1.1.2"

    override val id: String = ID
    override val ref: ArchitectureRef = ArchitectureRef(ArchitectureId(ID), "Method", "Automatic code scanner")
    override val descriptor: MethodDescriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.SignalInterpreter,
        name = "Automatic code scanner",
        version = VERSION,
        description = "Automatically decode QR, Data Matrix, Aztec, PDF417, and common 1D barcode formats and convert the payload into canonical evidence.",
        inputs = listOf("barcode_formats"),
        outputs = listOf(
            "barcode_payload", "barcode_payload_kind", "barcode_payload_url", "barcode_format",
            "barcode_payload_sha256", BarcodeEvidenceFields.FORMAT_FIELD, BarcodeEvidenceFields.HASH_FIELD,
            "barcode_scan_time_iso", "barcode_source"
        ),
        graphOutputs = listOf("barcode.payload_evidence"),
        parameters = mapOf(
            "category" to "Code scanning", "status" to "Production", "maturity" to "Production",
            "connectivity" to "Offline", "interactive" to "true", "core_return" to "barcode_payload",
            "odk_metadata_return" to "methodmesh_full_json",
            "barcode_formats" to "optional ZXing format names separated by spaces, pipes, commas or semicolons; all supported formats by default"
        )
    )
    override val contract: MethodContract = MethodContract(
        method = ref, producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs, producedGraphOutputs = descriptor.graphOutputs
    )

    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>): ExecutionRequest =
        As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)

    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult =
        BarcodeScanExecution.execute(request, ref, ID, VERSION)

    internal fun executeWithIdentity(
        request: ExecutionRequest, observationId: ArchitectureId, transformationId: ArchitectureId
    ): ExecutionResult = BarcodeScanExecution.execute(
        request = request, methodRef = ref, methodId = ID, methodVersion = VERSION,
        observationId = observationId, transformationId = transformationId
    )
}

private object BarcodeScanExecution {
    fun execute(
        request: ExecutionRequest, methodRef: ArchitectureRef, methodId: String, methodVersion: String,
        observationId: ArchitectureId = ArchitectureId(), transformationId: ArchitectureId = ArchitectureId()
    ): ExecutionResult {
        val c = request.context
        val payload = c["barcode_payload"].orEmpty().ifEmpty { c["token"].orEmpty() }
        val source = c["barcode_source"].orEmpty().ifBlank { "camera_or_external_scanner" }
        val format = c["barcode_format"].orEmpty().ifBlank { "UNKNOWN" }
        if (payload.isEmpty()) return As100ExecutionEngine.complete(
            request = request, status = TransformationStatus.Unsupported,
            diagnostics = mapOf("reason" to "Barcode decoding did not produce a payload.")
        )
        val scanTime = c["barcode_scan_time_iso"].orEmpty().ifBlank { Instant.ofEpochMilli(System.currentTimeMillis()).toString() }
        val payloadHash = Digests.sha256Hex(payload)
        val url = BarcodePayloadSemantics.safeHttpUrl(payload)
        val values = linkedMapOf<String, String>().apply {
            put("barcode_payload", payload)
            put("barcode_payload_kind", if (url == null) BarcodePayloadSemantics.KIND_TEXT else BarcodePayloadSemantics.KIND_URL)
            url?.let { put("barcode_payload_url", it) }
            put("barcode_format", format); put("barcode_payload_sha256", payloadHash)
            put(BarcodeEvidenceFields.FORMAT_FIELD, BarcodeEvidenceFields.FORMAT)
            put(BarcodeEvidenceFields.HASH_FIELD, payloadHash); put("barcode_scan_time_iso", scanTime); put("barcode_source", source)
        }
        val provenance = ProvenanceContext(provider = "methodmesh.barcode", methodId = methodId, methodVersion = methodVersion, operatorId = c["operator_id"])
        val observation = Observation(id = observationId, phenomenon = "barcode.payload_evidence", subject = InvocationContext.from(c)?.subjectRef(), values = values, temporalContext = request.temporalContext, provenance = provenance)
        val transformation = Transformation(
            id = transformationId, action = "barcode.scan_payload", method = methodRef,
            outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)),
            status = TransformationStatus.Succeeded, temporalContext = observation.temporalContext, provenance = provenance,
            diagnostics = mapOf("barcode_payload_sha256" to payloadHash, "barcode_format" to format, "barcode_source" to source)
        )
        return As100ExecutionEngine.complete(request = request, status = TransformationStatus.Succeeded, observations = listOf(observation), transformations = listOf(transformation), diagnostics = transformation.diagnostics)
    }
}

// ---- barcode.generate -------------------------------------------------------

object As100BarcodeGenerateMethod : As100Method {
    const val ID = "barcode.generate"
    internal const val VERSION = "1.0.3"

    override val id: String = ID
    override val ref: ArchitectureRef = ArchitectureRef(ArchitectureId(ID), "Method", "Code generator")
    override val descriptor: MethodDescriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.Calculation,
        name = "Code generator",
        version = VERSION,
        description = "Render exact payload text as a compatible QR, Data Matrix, Aztec, PDF417, or common 1D barcode without changing the payload.",
        inputs = listOf("barcode_payload", "barcode_format", "barcode_auto_cycle"),
        outputs = listOf(
            "barcode_payload",
            "barcode_payload_kind",
            "barcode_payload_url",
            "barcode_format",
            "barcode_payload_sha256",
            "barcode_generated_time_iso",
            "barcode_source"
        ),
        graphOutputs = listOf("barcode.generated_payload"),
        parameters = mapOf(
            "category" to "Code generation",
            "status" to "Development",
            "maturity" to "Development",
            "connectivity" to "Offline",
            "interactive" to "true",
            "core_return" to "barcode_payload",
            "odk_metadata_return" to "methodmesh_full_json",
            "native_primary_artifact" to "rendered barcode PNG",
            "native_share_save" to "PNG first; optional JSON sidecar; no payload text file",
            "preset_result_actions" to "HOME|SHARE|SAVE",
            "preset_payload_modes" to "CORE|AUDIT|FULL",
            "round_trip" to "exact payload; presentation must never silently transform input"
        )
    )

    override val contract: MethodContract = MethodContract(
        method = ref,
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs,
        producedGraphOutputs = descriptor.graphOutputs
    )

    override fun request(
        action: String,
        context: Map<String, String>,
        signals: List<Signal>,
        inputs: List<ArchitectureRef>
    ): ExecutionRequest =
        As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)

    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult =
        BarcodeGenerateExecution.execute(request, ref, ID, VERSION)

    internal fun executeWithIdentity(
        request: ExecutionRequest,
        observationId: ArchitectureId,
        transformationId: ArchitectureId
    ): ExecutionResult = BarcodeGenerateExecution.execute(
        request = request,
        methodRef = ref,
        methodId = ID,
        methodVersion = VERSION,
        observationId = observationId,
        transformationId = transformationId
    )
}

private object BarcodeGenerateExecution {
    fun execute(
        request: ExecutionRequest,
        methodRef: ArchitectureRef,
        methodId: String,
        methodVersion: String,
        observationId: ArchitectureId = ArchitectureId(),
        transformationId: ArchitectureId = ArchitectureId()
    ): ExecutionResult {
        val c = request.context
        val payload = c["barcode_payload"].orEmpty()
        if (payload.isEmpty()) {
            return As100ExecutionEngine.complete(
                request = request,
                status = TransformationStatus.Unsupported,
                diagnostics = mapOf("reason" to "No payload was supplied for code generation.")
            )
        }

        val format = c["barcode_format"].orEmpty().ifBlank { "QR_CODE" }
        val generatedTime = c["barcode_generated_time_iso"].orEmpty()
            .ifBlank { Instant.ofEpochMilli(System.currentTimeMillis()).toString() }
        val payloadHash = Digests.sha256Hex(payload)
        val url = BarcodePayloadSemantics.safeHttpUrl(payload)
        val values = linkedMapOf<String, String>().apply {
            put("barcode_payload", payload)
            put("barcode_payload_kind", if (url == null) BarcodePayloadSemantics.KIND_TEXT else BarcodePayloadSemantics.KIND_URL)
            url?.let { put("barcode_payload_url", it) }
            put("barcode_format", format)
            put("barcode_payload_sha256", payloadHash)
            put("barcode_generated_time_iso", generatedTime)
            put("barcode_source", c["barcode_source"].orEmpty().ifBlank { "methodmesh_generator" })
        }
        val provenance = ProvenanceContext(
            provider = "methodmesh.barcode",
            methodId = methodId,
            methodVersion = methodVersion,
            operatorId = c["operator_id"]
        )
        val observation = Observation(
            id = observationId,
            phenomenon = "barcode.generated_payload",
            subject = InvocationContext.from(c)?.subjectRef(),
            values = values,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        val transformation = Transformation(
            id = transformationId,
            action = "barcode.generate_payload",
            method = methodRef,
            outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)),
            status = TransformationStatus.Succeeded,
            temporalContext = observation.temporalContext,
            provenance = provenance,
            diagnostics = mapOf("barcode_payload_sha256" to payloadHash, "barcode_format" to format)
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

// ---- barcode.clone ----------------------------------------------------------

object As100BarcodeCloneMethod : As100Method {
    const val ID = "barcode.clone"
    internal const val VERSION = "1.0.3"

    override val id: String = ID
    override val ref: ArchitectureRef = ArchitectureRef(ArchitectureId(ID), "Method", "Code clone")
    override val descriptor: MethodDescriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.Calculation,
        name = "Code clone",
        version = VERSION,
        description = "Scan a supported code, preserve its exact payload, and re-present that payload in the source or another compatible barcode symbology.",
        inputs = listOf("barcode_clone_format", "barcode_auto_cycle", "barcode_return_text_payload"),
        outputs = listOf(
            "barcode_clone_image_uri",
            "barcode_payload",
            "barcode_payload_kind",
            "barcode_payload_url",
            "barcode_source_format",
            "barcode_clone_format",
            "barcode_payload_sha256",
            BarcodeEvidenceFields.FORMAT_FIELD,
            BarcodeEvidenceFields.HASH_FIELD,
            "barcode_scan_time_iso",
            "barcode_clone_time_iso",
            "barcode_source"
        ),
        graphOutputs = listOf("barcode.cloned_payload"),
        parameters = mapOf(
            "category" to "Code cloning",
            "status" to "Development",
            "maturity" to "Development",
            "connectivity" to "Offline",
            "interactive" to "true",
            "core_return" to "barcode_clone_image_uri",
            "odk_metadata_return" to "methodmesh_full_json",
            "odk_primary_artifact" to "barcode_clone_image_uri as caller-readable image attachment",
            "text_payload_return" to "controlled by barcode_return_text_payload; true by default",
            "native_primary_artifact" to "cloned barcode PNG",
            "native_share_save" to "PNG first; payload text optional in Share/Copy; no payload text file",
            "native_finalization" to "Share|Copy|Save|Return atomically snapshot the current live clone; no separate Commit gate",
            "presentation" to "standard host with embedded orientation-aware scanner; optional full-screen code presentation only",
            "preset_result_actions" to "HOME|SHARE|SAVE",
            "preset_payload_modes" to "CORE|AUDIT|FULL",
            "round_trip" to "scan exact payload then render only compatible symbologies; never rewrite payload"
        )
    )

    override val contract: MethodContract = MethodContract(
        method = ref,
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs,
        producedGraphOutputs = descriptor.graphOutputs
    )

    override fun request(
        action: String,
        context: Map<String, String>,
        signals: List<Signal>,
        inputs: List<ArchitectureRef>
    ): ExecutionRequest =
        As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)

    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult =
        BarcodeCloneExecution.execute(request, ref, ID, VERSION)

    internal fun executeWithIdentity(
        request: ExecutionRequest,
        observationId: ArchitectureId,
        transformationId: ArchitectureId
    ): ExecutionResult = BarcodeCloneExecution.execute(
        request = request,
        methodRef = ref,
        methodId = ID,
        methodVersion = VERSION,
        observationId = observationId,
        transformationId = transformationId
    )
}

private object BarcodeCloneExecution {
    fun execute(
        request: ExecutionRequest,
        methodRef: ArchitectureRef,
        methodId: String,
        methodVersion: String,
        observationId: ArchitectureId = ArchitectureId(),
        transformationId: ArchitectureId = ArchitectureId()
    ): ExecutionResult {
        val c = request.context
        val payload = c["barcode_payload"].orEmpty()
        if (payload.isEmpty()) {
            return As100ExecutionEngine.complete(
                request = request,
                status = TransformationStatus.Unsupported,
                diagnostics = mapOf("reason" to "No scanned payload was supplied for cloning.")
            )
        }

        val sourceFormat = c["barcode_source_format"].orEmpty()
            .ifBlank { c["barcode_format"].orEmpty().ifBlank { "UNKNOWN" } }
        val cloneFormat = c["barcode_clone_format"].orEmpty()
            .ifBlank { sourceFormat.takeIf { it != "UNKNOWN" }.orEmpty().ifBlank { "QR_CODE" } }
        val scanTime = c["barcode_scan_time_iso"].orEmpty()
            .ifBlank { Instant.ofEpochMilli(System.currentTimeMillis()).toString() }
        val cloneTime = c["barcode_clone_time_iso"].orEmpty()
            .ifBlank { Instant.ofEpochMilli(System.currentTimeMillis()).toString() }
        val imageUri = c["barcode_clone_image_uri"].orEmpty()
        if (imageUri.isBlank()) {
            return As100ExecutionEngine.complete(
                request = request,
                status = TransformationStatus.Unsupported,
                diagnostics = mapOf("reason" to "Clone rendering did not produce a caller-readable PNG artefact.")
            )
        }
        val returnTextPayload = c["barcode_return_text_payload"].orEmpty().ifBlank { "true" }.toBoolean()
        val payloadHash = Digests.sha256Hex(payload)
        val url = BarcodePayloadSemantics.safeHttpUrl(payload)
        val values = linkedMapOf<String, String>().apply {
            put("barcode_clone_image_uri", imageUri)
            if (returnTextPayload) {
                put("barcode_payload", payload)
                put("barcode_payload_kind", if (url == null) BarcodePayloadSemantics.KIND_TEXT else BarcodePayloadSemantics.KIND_URL)
                url?.let { put("barcode_payload_url", it) }
            }
            put("barcode_source_format", sourceFormat)
            put("barcode_clone_format", cloneFormat)
            put("barcode_payload_sha256", payloadHash)
            put(BarcodeEvidenceFields.FORMAT_FIELD, BarcodeEvidenceFields.FORMAT)
            put(BarcodeEvidenceFields.HASH_FIELD, payloadHash)
            put("barcode_scan_time_iso", scanTime)
            put("barcode_clone_time_iso", cloneTime)
            put("barcode_source", c["barcode_source"].orEmpty().ifBlank { "camera_zxing_clone" })
        }
        val provenance = ProvenanceContext(
            provider = "methodmesh.barcode",
            methodId = methodId,
            methodVersion = methodVersion,
            operatorId = c["operator_id"]
        )
        val observation = Observation(
            id = observationId,
            phenomenon = "barcode.cloned_payload",
            subject = InvocationContext.from(c)?.subjectRef(),
            values = values,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        val transformation = Transformation(
            id = transformationId,
            action = "barcode.clone_payload",
            method = methodRef,
            outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)),
            status = TransformationStatus.Succeeded,
            temporalContext = observation.temporalContext,
            provenance = provenance,
            diagnostics = mapOf(
                "barcode_payload_sha256" to payloadHash,
                "barcode_source_format" to sourceFormat,
                "barcode_clone_format" to cloneFormat,
                "barcode_clone_image_uri" to imageUri,
                "barcode_return_text_payload" to returnTextPayload.toString()
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
