package com.example.methodmesh.modules.magnifier

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

object MagnifierFields {
    const val STATUS = "magnifier_status"
    const val IMAGE_URI = "magnifier_image_uri"
    const val IMAGE_SHA256 = "magnifier_image_sha256"
    const val CAMERA_FACING = "magnifier_camera_facing"
    const val FILTER_MODE = "magnifier_filter_mode"
    const val ZOOM_REQUESTED_RATIO = "magnifier_zoom_requested_ratio"
    const val ZOOM_ACTUAL_RATIO = "magnifier_zoom_actual_ratio"
    const val TORCH_MODE = "magnifier_torch_mode"
    const val FRONT_LIGHT_MODE = "magnifier_front_light_mode"
    const val FOCUS_MODE = "magnifier_focus_mode"
    const val FROZEN_TIME_ISO = "magnifier_frozen_time_iso"
    const val CAPTURED_TIME_ISO = "magnifier_captured_time_iso"
    const val METADATA_JSON = "magnifier_metadata_json"
    const val ERROR = "magnifier_error"

    val outputs = listOf(
        STATUS,
        IMAGE_URI,
        IMAGE_SHA256,
        CAMERA_FACING,
        FILTER_MODE,
        ZOOM_REQUESTED_RATIO,
        ZOOM_ACTUAL_RATIO,
        TORCH_MODE,
        FRONT_LIGHT_MODE,
        FOCUS_MODE,
        FROZEN_TIME_ISO,
        CAPTURED_TIME_ISO,
        METADATA_JSON,
        ERROR
    )
}

object As100MagnifierCaptureMethod : As100Method {
    const val ID = "visual.magnifier.capture"
    private const val VERSION = "0.3.1"

    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Visual magnifier capture")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.SignalInterpreter,
        name = "Visual magnifier capture",
        version = VERSION,
        description = "Interactively inspect a subject with the rear or front camera, use rear LED or display illumination, freeze a frame, apply a viewing filter, and return the captured image.",
        outputs = MagnifierFields.outputs,
        graphOutputs = listOf("visual.magnifier.capture"),
        parameters = mapOf(
            "category" to "Image",
            "status" to "Development"
        )
    )
    override val contract = MethodContract(
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
    ): ExecutionResult = result(
        request = request,
        values = request.context,
        invocation = InvocationContext.from(request.context)
    )

    fun result(
        request: ExecutionRequest,
        values: Map<String, String>,
        invocation: InvocationContext?
    ): ExecutionResult {
        val succeeded = values[MagnifierFields.STATUS] == "succeeded" &&
            values[MagnifierFields.IMAGE_URI].orEmpty().isNotBlank()

        val provenance = ProvenanceContext("methodmesh.magnifier", ID, VERSION)
        val observation = Observation(
            phenomenon = "visual.magnifier.capture",
            subject = invocation?.subjectRef(),
            values = values,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        val transformation = Transformation(
            action = ID,
            method = ref,
            outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)),
            status = if (succeeded) TransformationStatus.Succeeded else TransformationStatus.Failed,
            temporalContext = request.temporalContext,
            provenance = provenance
        )

        return As100ExecutionEngine.complete(
            request = request,
            status = if (succeeded) TransformationStatus.Succeeded else TransformationStatus.Failed,
            observations = listOf(observation),
            transformations = listOf(transformation),
            diagnostics = if (succeeded) {
                emptyMap()
            } else {
                mapOf(MagnifierFields.ERROR to values[MagnifierFields.ERROR].orEmpty().ifBlank { "No captured magnifier image was supplied." })
            }
        ).withInvocationContext(invocation)
    }
}
