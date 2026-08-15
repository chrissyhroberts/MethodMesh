package com.example.researchos.modules.imageredaction

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

object ImageRedactionFields {
    const val STATUS = "image_redaction_status"
    const val REDACTED_IMAGE_URI = "redacted_image_uri"
    const val REDACTED_IMAGE_NAME = "redacted_image_name"
    const val MASK_JSON = "redaction_mask_json"
    const val SELECTED_CELLS = "redacted_cells"
    const val GRID_ROWS = "redaction_grid_rows"
    const val GRID_COLUMNS = "redaction_grid_columns"
    const val STYLE = "redaction_style"
    const val SOURCE = "redaction_input_source"
    const val CREATED_TIME_ISO = "redaction_created_time_iso"
    const val ERROR = "image_redaction_error"

    val outputs = listOf(STATUS, REDACTED_IMAGE_URI, REDACTED_IMAGE_NAME, MASK_JSON, SELECTED_CELLS, GRID_ROWS, GRID_COLUMNS, STYLE, SOURCE, CREATED_TIME_ISO, ERROR)
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
        parameters = mapOf("category" to "Image")
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
        val provenance = ProvenanceContext("researchos.image_redaction", ID, VERSION)
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
