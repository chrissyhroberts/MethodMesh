package com.example.methodmesh.modules.imageredaction

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

object ImageRedactionFields {
    const val STATUS = "image_redaction_status"
    const val REDACTED_IMAGE_URI = "redacted_image_uri"
    const val REDACTED_IMAGE_NAME = "redacted_image_name"
    const val REDACTED_IMAGE_SHA256 = "redacted_image_sha256"
    const val MASK_JSON = "redaction_mask_json"
    const val SELECTED_CELLS = "redacted_cells"
    const val GRID_ROWS = "redaction_grid_rows"
    const val GRID_COLUMNS = "redaction_grid_columns"
    const val STYLE = "redaction_style"
    const val SOURCE = "redaction_input_source"
    const val CREATED_TIME_ISO = "redaction_created_time_iso"
    const val ERROR = "image_redaction_error"

    val outputs = listOf(STATUS, REDACTED_IMAGE_URI, REDACTED_IMAGE_NAME, REDACTED_IMAGE_SHA256, MASK_JSON, SELECTED_CELLS, GRID_ROWS, GRID_COLUMNS, STYLE, SOURCE, CREATED_TIME_ISO, ERROR)
}

object As100ImageRedactionMethod : As100Method {
    const val ID = "image.redact"
    private const val VERSION = "0.1.0"

    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Image redaction")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.SignalInterpreter,
        name = "Image redaction",
        version = VERSION,
        description = "Apply an irreversible grid mask and return a redacted image attachment.",
        outputs = ImageRedactionFields.outputs,
        graphOutputs = listOf("image.redacted"),
        parameters = mapOf(
            "category" to "Image",
            "status" to "Production"
        )
    )
    override val contract = MethodContract(
        method = ref,
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs,
        producedGraphOutputs = descriptor.graphOutputs
    )

    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>): ExecutionRequest =
        As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)

    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult =
        result(request, request.context, InvocationContext.from(request.context))

    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?): ExecutionResult {
        val ok = values[ImageRedactionFields.STATUS] == "succeeded"
        val provenance = ProvenanceContext("methodmesh.image_redaction", ID, VERSION)
        val observation = Observation(
            phenomenon = "image.redacted",
            subject = null,
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
            observations = listOf(observation),
            transformations = listOf(transformation),
            diagnostics = if (ok) emptyMap() else mapOf(ImageRedactionFields.ERROR to values[ImageRedactionFields.ERROR].orEmpty())
        ).withInvocationContext(invocation)
    }
}
