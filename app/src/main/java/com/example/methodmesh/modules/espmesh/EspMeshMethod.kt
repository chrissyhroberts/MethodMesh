package com.example.methodmesh.modules.espmesh

import com.example.methodmesh.core.methodmesh.ArchitectureId
import com.example.methodmesh.core.methodmesh.ArchitectureRef
import com.example.methodmesh.core.methodmesh.ExecutionRequest
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.InvocationContext
import com.example.methodmesh.core.methodmesh.MethodContract
import com.example.methodmesh.core.methodmesh.MethodDescriptor
import com.example.methodmesh.core.methodmesh.MethodObjectType
import com.example.methodmesh.core.methodmesh.TransformationStatus
import com.example.methodmesh.core.methodmesh.runtime.As100ExecutionEngine
import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.settings.SettingsState

object EspMeshFields {
    const val STATUS = "esp_mesh_status"
    const val MESSAGE_ID = "esp_mesh_message_id"
    const val DESTINATION = "esp_mesh_destination"
    const val MESSAGE_TYPE = "esp_mesh_message_type"
    const val PAYLOAD = "esp_mesh_payload"
    const val ERROR = "esp_mesh_error"
    val outputs = listOf(STATUS, MESSAGE_ID, DESTINATION, MESSAGE_TYPE, PAYLOAD, ERROR)
}

object As100EspMeshMessageMethod : As100Method {
    const val ID = "espmesh.message.send"
    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Send a MethodMesh message through the ESP mesh")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID), methodType = MethodObjectType.Workflow,
        name = "ESP mesh transport test", version = "0.4.0",
        description = "Workbench test harness for the persistent encrypted ESP mesh transport.",
        outputs = EspMeshFields.outputs, graphOutputs = listOf("transport.message.queued"),
        parameters = mapOf("category" to "Field transport", "status" to "Preview")
    )
    override val contract = MethodContract(method = ref, producedFields = descriptor.outputs, producedGraphOutputs = descriptor.graphOutputs)
    override fun request(action: String, context: Map<String, String>, signals: List<com.example.methodmesh.core.methodmesh.Signal>, inputs: List<ArchitectureRef>) =
        As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)
    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult =
        As100ExecutionEngine.complete(request, TransformationStatus.Succeeded, diagnostics = mapOf(EspMeshFields.STATUS to "ready_to_queue"))
}
