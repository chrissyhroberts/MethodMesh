package com.example.methodmesh.modules.digitalsigning

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

object DigitalSigningFields {
    const val STATUS = "digital_signing_status"
    const val SIGNED_PDF_URI = "digital_signing_signed_pdf_uri"
    const val SIGNED_PDF_NAME = "digital_signing_signed_pdf_name"
    const val SIGNED_SHA256 = "digital_signing_signed_sha256"
    const val VERIFICATION_BUNDLE_URI = "digital_signing_verification_bundle_uri"
    const val VERIFICATION_BUNDLE_NAME = "digital_signing_verification_bundle_name"
    const val VERIFICATION_BUNDLE_SHA256 = "digital_signing_verification_bundle_sha256"
    const val SOURCE_SHA256 = "digital_signing_source_sha256"
    const val SOURCE_ORIGIN = "digital_signing_source_origin"
    const val PAGE_COUNT = "digital_signing_page_count"
    const val INK_PRESENT = "digital_signing_ink_present"
    const val INK_STROKE_COUNT = "digital_signing_ink_stroke_count"
    const val FINALISED = "digital_signing_finalised"
    const val FINALISATION_MODE = "digital_signing_finalisation_mode"
    const val COMMITTED_TIME_ISO = "digital_signing_committed_time_iso"
    const val TSA_STATUS = "digital_signing_tsa_status"
    const val TSA_TIME_ISO = "digital_signing_tsa_time_iso"
    const val TSA_AUTHORITY = "digital_signing_tsa_authority"
    const val TSA_JSON = "digital_signing_tsa_json"
    const val RESULT_JSON = "digital_signing_result_json"
    const val ERROR = "digital_signing_error"

    val outputs = listOf(
        STATUS,
        SIGNED_PDF_URI,
        SIGNED_PDF_NAME,
        SIGNED_SHA256,
        VERIFICATION_BUNDLE_URI,
        VERIFICATION_BUNDLE_NAME,
        VERIFICATION_BUNDLE_SHA256,
        SOURCE_SHA256,
        SOURCE_ORIGIN,
        PAGE_COUNT,
        INK_PRESENT,
        INK_STROKE_COUNT,
        FINALISED,
        FINALISATION_MODE,
        COMMITTED_TIME_ISO,
        TSA_STATUS,
        TSA_TIME_ISO,
        TSA_AUTHORITY,
        TSA_JSON,
        RESULT_JSON,
        ERROR
    )
}

object As100DigitalSigningMethod : As100Method {
    const val ID = "document.sign_pdf"
    const val VERSION = "1.5.0"

    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "PDF ink signing")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.Workflow,
        name = "Digital signing",
        version = VERSION,
        description = "Open a PDF full-screen, add freehand markup/ink, commit a new PDF, and optionally obtain an RFC 3161 trusted timestamp.",
        inputs = listOf("pdf_uri"),
        outputs = DigitalSigningFields.outputs,
        graphOutputs = listOf(ID),
        parameters = mapOf(
            "category" to "Documents",
            "status" to "Development",
            "interactive" to "true",
            "core_return" to "${DigitalSigningFields.SIGNED_PDF_URI}, ${DigitalSigningFields.VERIFICATION_BUNDLE_URI}, ${DigitalSigningFields.SIGNED_SHA256}, ${DigitalSigningFields.RESULT_JSON}, ${DigitalSigningFields.TSA_JSON}",
            "audit_return" to "source hash, source origin, page count, finalisation state, TSA verification details, verification bundle hash",
            "signature_semantics" to "visible handwritten electronic ink/markup; not certificate-backed signer identity",
            "timestamp_semantics" to "RFC3161 attestation over the SHA-256 of the exact committed PDF",
            "deliverable_a" to DigitalSigningFields.SIGNED_PDF_URI,
            "deliverable_b" to DigitalSigningFields.VERIFICATION_BUNDLE_URI
        )
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
    ): ExecutionRequest = As100ExecutionEngine.request(
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
        request = request,
        status = TransformationStatus.Unsupported,
        diagnostics = mapOf("reason" to "PDF signing requires the interactive Android document/ink surface.")
    )

    fun result(
        request: ExecutionRequest,
        values: Map<String, String>,
        invocation: InvocationContext?
    ): ExecutionResult {
        val ok = values[DigitalSigningFields.STATUS] == "succeeded"
        val entity = Entity(
            id = ArchitectureId("digital-signing:${System.currentTimeMillis()}"),
            entityType = "SignedDocument",
            temporalContext = request.temporalContext
        )
        val provenance = ProvenanceContext(
            provider = "methodmesh.digital_signing",
            methodId = ID,
            methodVersion = VERSION
        )
        val observation = Observation(
            phenomenon = "document.digital_signing",
            subject = ArchitectureRef(entity.id, entity.objectType, ID),
            values = values,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        val transformation = Transformation(
            action = ID,
            method = ref,
            outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)),
            status = if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        return As100ExecutionEngine.complete(
            request = request,
            status = if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed,
            entities = listOf(entity),
            observations = listOf(observation),
            transformations = listOf(transformation),
            diagnostics = if (ok) emptyMap() else mapOf(
                DigitalSigningFields.ERROR to values[DigitalSigningFields.ERROR].orEmpty().ifBlank { "Digital signing failed." }
            )
        ).withInvocationContext(invocation)
    }
}
