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

object PaperDesignFields {
    const val STATUS = "paper_design_status"
    const val TEMPLATE_JSON = "paper_template_json"
    const val TEMPLATE_ID = "paper_template_id"
    const val TEMPLATE_VERSION = "paper_template_version"
    const val TEMPLATE_JSON_URI = "paper_template_json_uri"
    const val TEMPLATE_YAML_URI = "paper_template_yaml_uri"
    const val TEMPLATE_BUNDLE_URI = "paper_template_bundle_uri"
    const val TEMPLATE_IMAGE_URI = "paper_design_template_image"
    const val PREPARED_FORM_URI = "paper_design_prepared_form_image"
    const val MARKUP_IMAGE_URI = "paper_design_markup_image"
    const val FIELD_COUNT = "paper_design_field_count"
    const val ODK_FIELD_COUNT = "paper_design_odk_field_count"
    const val ERROR_COUNT = "paper_design_error_count"
    const val WARNING_COUNT = "paper_design_warning_count"
    const val SOURCE_TYPE = "paper_design_source_type"
    const val ERROR = "paper_design_error"

    val outputs = listOf(
        STATUS, TEMPLATE_JSON, TEMPLATE_ID, TEMPLATE_VERSION, TEMPLATE_JSON_URI, TEMPLATE_YAML_URI, TEMPLATE_BUNDLE_URI,
        TEMPLATE_IMAGE_URI, PREPARED_FORM_URI, MARKUP_IMAGE_URI, FIELD_COUNT,
        ODK_FIELD_COUNT, ERROR_COUNT, WARNING_COUNT, SOURCE_TYPE, ERROR
    )
}

object As100PaperFormDesignMethod : As100Method {
    const val ID = "paper.form.design"
    private const val VERSION = "0.6.0"

    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Paper form designer")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.SignalInterpreter,
        name = "Paper form designer",
        version = VERSION,
        description = "Import an XLSForm-style survey workbook and paper template, detect semantic colour envelopes/response geometry, match printed labels, review mappings, test extraction, and export a portable Paper Bridge bundle.",
        inputs = emptyList(),
        outputs = PaperDesignFields.outputs,
        graphOutputs = listOf("paper.form.design"),
        parameters = mapOf(
            "category" to "Data collection",
            "status" to PaperBridgeContractMetadata.MATURITY,
            "connectivity" to PaperBridgeContractMetadata.CONNECTIVITY,
            "interaction" to "visual designer"
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
            diagnostics = mapOf("reason" to "Paper form design requires the Android visual designer boundary.")
        )

    fun result(
        request: ExecutionRequest,
        values: Map<String, String>,
        invocation: InvocationContext?
    ): ExecutionResult {
        val ok = values[PaperDesignFields.STATUS] == "succeeded"
        val entity = Entity(
            ArchitectureId("paper-design:${System.currentTimeMillis()}"),
            "PaperFormDesign",
            temporalContext = request.temporalContext
        )
        val provenance = ProvenanceContext("paperbridge.android", ID, VERSION)
        val observation = Observation(
            phenomenon = "paper.form.design",
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
            request,
            if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed,
            entities = listOf(entity),
            observations = listOf(observation),
            transformations = listOf(transformation),
            diagnostics = if (ok) emptyMap() else mapOf(PaperDesignFields.ERROR to values[PaperDesignFields.ERROR].orEmpty())
        ).withInvocationContext(invocation)
    }
}
