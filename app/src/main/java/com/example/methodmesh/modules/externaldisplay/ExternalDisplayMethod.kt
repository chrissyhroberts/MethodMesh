package com.example.methodmesh.modules.externaldisplay

import com.example.methodmesh.core.methodmesh.*
import com.example.methodmesh.core.methodmesh.runtime.As100ExecutionEngine
import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.core.methodmesh.withInvocationContext
import com.example.methodmesh.settings.SettingsState

object ExternalDisplayFields {
    const val STATUS = "external_display_status"
    const val DISPLAY_ID = "external_display_id"
    const val DISPLAY_NAME = "external_display_name"
    const val CONNECTION = "external_display_connection"
    const val NATIVE_VIDEO = "external_display_native_video_capability"
    const val SYSTEM_HANDOFF = "external_display_system_handoff"
    const val BLANKED = "external_display_blanked"
    const val MESSAGE = "external_display_message"
    const val ERROR = "external_display_error"
    val outputs = listOf(STATUS, DISPLAY_ID, DISPLAY_NAME, CONNECTION, NATIVE_VIDEO, SYSTEM_HANDOFF, BLANKED, MESSAGE, ERROR)
}

object As100ExternalDisplayMethod : As100Method {
    const val ID = "external_display.present"
    private const val VERSION = "0.1.0"
    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Present MethodMesh content on an external display")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID), methodType = MethodObjectType.Workflow, name = "External display",
        version = VERSION, description = "Discover displays, present MethodMesh content, control blank/stop state, or hand off to Android system casting.",
        outputs = ExternalDisplayFields.outputs, graphOutputs = listOf(ID),
        parameters = mapOf("category" to "Presentation", "status" to "Development", "offline" to "true")
    )
    override val contract = MethodContract(ref, producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation), producedFields = descriptor.outputs, producedGraphOutputs = descriptor.graphOutputs)
    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) =
        As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)
    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?) =
        As100ExecutionEngine.complete(request, TransformationStatus.Unsupported, diagnostics = mapOf(ExternalDisplayFields.ERROR to "External display control requires the Android presentation surface."))
    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?) =
        As100ExecutionEngine.complete(request, if (values[ExternalDisplayFields.STATUS] == "presenting") TransformationStatus.Succeeded else TransformationStatus.Failed,
            observations = listOf(Observation(id = ArchitectureId("external-display:${System.currentTimeMillis()}"), phenomenon = ID, values = values, provenance = ProvenanceContext("android.hardware.display.DisplayManager", ID, VERSION))),
            diagnostics = values[ExternalDisplayFields.ERROR]?.let { mapOf(ExternalDisplayFields.ERROR to it) } ?: emptyMap()
        ).withInvocationContext(invocation ?: InvocationContext.from(emptyMap()))
}
