package com.example.methodmesh.modules.paperbridge

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

object PaperBridgeInputs {
    const val TEMPLATE_JSON = "paper_template_json"
    const val INPUT_SOURCE = "source_mode"
    const val SOURCE_IMAGE_URI = "source_image_uri"
    const val AUTO_ACCEPT_OMR = "auto_accept_omr"
    const val AUTO_ACCEPT_OCR = "auto_accept_ocr"
    const val RETURN_SOURCE_IMAGE = "return_source_image"
    const val RETURN_RECTIFIED_IMAGE = "return_rectified_image"
}

object PaperBridgeFields {
    const val STATUS = "paper_status"
    const val TEMPLATE_ID = "paper_template_id"
    const val TEMPLATE_VERSION = "paper_template_version"
    const val VALUES_JSON = "paper_values_json"
    const val DYNAMIC_FIELDS_JSON = "paper_dynamic_fields_json"
    const val FIELD_COUNT = "paper_field_count"
    const val AUTO_ACCEPTED_COUNT = "paper_auto_accepted_count"
    const val REVIEWED_COUNT = "paper_reviewed_count"
    const val UNRESOLVED_COUNT = "paper_unresolved_count"
    const val SOURCE_IMAGE = "paper_source_image"
    const val RECTIFIED_IMAGE = "paper_rectified_image"
    const val TEMPLATE_SHA256 = "paper_template_sha256"
    const val SOURCE_SHA256 = "paper_source_sha256"
    const val RECTIFIED_SHA256 = "paper_rectified_sha256"
    const val EXTRACTION_AUDIT_JSON = "paper_extraction_audit_json"
    const val SCAN_TIME_ISO = "paper_scan_time_iso"
    const val ERROR = "paper_error"

    val outputs = listOf(
        STATUS, TEMPLATE_ID, TEMPLATE_VERSION, VALUES_JSON, DYNAMIC_FIELDS_JSON,
        FIELD_COUNT, AUTO_ACCEPTED_COUNT, REVIEWED_COUNT, UNRESOLVED_COUNT,
        SOURCE_IMAGE, RECTIFIED_IMAGE, TEMPLATE_SHA256, SOURCE_SHA256,
        RECTIFIED_SHA256, EXTRACTION_AUDIT_JSON, SCAN_TIME_ISO, ERROR
    )
}

object PaperBridgeContractMetadata {
    const val MATURITY = "Development"
    const val CONNECTIVITY = "Offline"

    val interactiveIntent =
        "com.example.methodmesh.EXECUTE_METHOD(" +
            "method_id='paper.form.transcribe'," +
            "input_paper_template_json=\${paper_template_json}," +
            "input_source_mode='camera'," +
            "input_auto_accept_omr='true'," +
            "input_auto_accept_ocr='false'," +
            "input_return_source_image='true'," +
            "input_return_rectified_image='true'," +
            "input_payload_mode='FULL',return_mode='flat')"
}

object As100PaperFormTranscribeMethod : As100Method {
    const val ID = "paper.form.transcribe"
    private const val VERSION = "0.1.0"

    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Paper form transcription")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.SignalInterpreter,
        name = "Paper form transcription",
        version = VERSION,
        description = "Rectify an anchored paper questionnaire, extract marks/text in declared ROIs, fail closed on ambiguity, review, then return structured values.",
        inputs = listOf(
            PaperBridgeInputs.TEMPLATE_JSON,
            PaperBridgeInputs.INPUT_SOURCE,
            PaperBridgeInputs.SOURCE_IMAGE_URI,
            PaperBridgeInputs.AUTO_ACCEPT_OMR,
            PaperBridgeInputs.AUTO_ACCEPT_OCR,
            PaperBridgeInputs.RETURN_SOURCE_IMAGE,
            PaperBridgeInputs.RETURN_RECTIFIED_IMAGE
        ),
        outputs = PaperBridgeFields.outputs,
        graphOutputs = listOf("paper.form.transcribe"),
        parameters = mapOf(
            "category" to "Data collection",
            "status" to PaperBridgeContractMetadata.MATURITY,
            "connectivity" to PaperBridgeContractMetadata.CONNECTIVITY,
            "dynamic_returns" to "Manifest field names are flat projections of paper_values_json"
        )
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

    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult =
        As100ExecutionEngine.complete(
            request,
            TransformationStatus.Unsupported,
            diagnostics = mapOf("reason" to "Paper transcription requires the Android image/review boundary.")
        )

    fun result(
        request: ExecutionRequest,
        stableValues: Map<String, String>,
        dynamicValues: Map<String, String>,
        invocation: InvocationContext?
    ): ExecutionResult {
        val ok = stableValues[PaperBridgeFields.STATUS] == "succeeded"
        val entity = Entity(
            ArchitectureId("paper-transcription:${System.currentTimeMillis()}"),
            "PaperFormTranscription",
            temporalContext = request.temporalContext
        )
        val provenance = ProvenanceContext("paperbridge.android", ID, VERSION)
        val observationValues = linkedMapOf<String, String>().apply {
            putAll(stableValues)
            // These keys are explicitly declared by the versioned paper manifest.
            // The stable structured equivalent remains paper_values_json.
            putAll(dynamicValues)
            putIfAbsent(PaperBridgeFields.SCAN_TIME_ISO, Instant.now().toString())
        }
        val observation = Observation(
            phenomenon = "paper.form.transcribe",
            subject = ArchitectureRef(entity.id, entity.objectType, ID),
            values = observationValues,
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
            request,
            if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed,
            entities = listOf(entity),
            observations = listOf(observation),
            transformations = listOf(transformation),
            diagnostics = if (ok) emptyMap() else mapOf(PaperBridgeFields.ERROR to stableValues[PaperBridgeFields.ERROR].orEmpty())
        ).withInvocationContext(invocation)
    }
}
