package com.example.methodmesh.modules.screentools

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
import java.util.UUID

object ScreenCaptureFields {
    const val STATUS = "screen_capture_status"
    const val ERROR = "screen_capture_error"
    const val FRAME_REF = "screen_capture_frame_ref"
    const val SOURCE_KIND = "screen_capture_source_kind"
    const val CAPTURE_TIME_ISO = "screen_capture_time_iso"
    const val WIDTH_PX = "screen_capture_width_px"
    const val HEIGHT_PX = "screen_capture_height_px"
    const val FRAME_COUNT = "screen_capture_frame_count"
    const val RETENTION = "screen_capture_retention"

    val outputs = listOf(
        STATUS, ERROR, FRAME_REF, SOURCE_KIND, CAPTURE_TIME_ISO,
        WIDTH_PX, HEIGHT_PX, FRAME_COUNT, RETENTION
    )
}

object ScreenCaptureMethod : As100Method {
    const val ID = "screen.capture"
    private const val VERSION = "0.1.0"

    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Screen capture")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.SignalInterpreter,
        name = "Screen capture",
        version = VERSION,
        description = "Capture a user-authorised visible screen into a volatile MethodMesh frame.",
        outputs = ScreenCaptureFields.outputs,
        graphOutputs = listOf(ID),
        parameters = mapOf(
            "category" to "Screen Tools",
            "status" to "Development",
            "connectivity" to "Offline",
            "retention" to "volatile_process_memory"
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
            diagnostics = mapOf("reason" to "Screen capture requires the Android MediaProjection boundary.")
        )

    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?): ExecutionResult {
        val succeeded = values[ScreenCaptureFields.STATUS] == "succeeded"
        val entity = Entity(
            ArchitectureId("screen-capture:${UUID.randomUUID()}"),
            "ScreenCapture",
            temporalContext = request.temporalContext
        )
        val provenance = ProvenanceContext("android.media_projection", ID, VERSION)
        val observation = Observation(
            phenomenon = ID,
            subject = ArchitectureRef(entity.id, entity.objectType, ID),
            values = values + (ScreenCaptureFields.CAPTURE_TIME_ISO to
                (values[ScreenCaptureFields.CAPTURE_TIME_ISO] ?: Instant.now().toString())),
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
            request,
            if (succeeded) TransformationStatus.Succeeded else TransformationStatus.Failed,
            entities = listOf(entity),
            observations = listOf(observation),
            transformations = listOf(transformation),
            diagnostics = if (succeeded) emptyMap() else mapOf(
                ScreenCaptureFields.ERROR to values[ScreenCaptureFields.ERROR].orEmpty()
            )
        ).withInvocationContext(invocation)
    }
}
