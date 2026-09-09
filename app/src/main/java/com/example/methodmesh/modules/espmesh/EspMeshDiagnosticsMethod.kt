package com.example.methodmesh.modules.espmesh

import com.example.methodmesh.core.methodmesh.ArchitectureId
import com.example.methodmesh.core.methodmesh.ArchitectureRef
import com.example.methodmesh.core.methodmesh.ExecutionRequest
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.MethodContract
import com.example.methodmesh.core.methodmesh.MethodDescriptor
import com.example.methodmesh.core.methodmesh.MethodObjectType
import com.example.methodmesh.core.methodmesh.TransformationStatus
import com.example.methodmesh.core.methodmesh.runtime.As100ExecutionEngine
import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.settings.SettingsState

object As100EspMeshDiagnosticsMethod : As100Method {
    const val ID = "espmesh.diagnostics"
    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Inspect ESP mesh transport diagnostics")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID), methodType = MethodObjectType.Workflow,
        name = "ESP mesh diagnostics", version = "0.1.0",
        description = "Inspect gateway and durable transport state without exposing secrets.",
        outputs = listOf("esp_mesh_diagnostics_status"), graphOutputs = listOf("transport.diagnostics"),
        parameters = mapOf("category" to "Field transport", "status" to "Preview")
    )
    override val contract = MethodContract(method = ref, producedFields = descriptor.outputs, producedGraphOutputs = descriptor.graphOutputs)
    override fun request(action: String, context: Map<String, String>, signals: List<com.example.methodmesh.core.methodmesh.Signal>, inputs: List<ArchitectureRef>) =
        As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)
    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult =
        As100ExecutionEngine.complete(request, TransformationStatus.Succeeded, diagnostics = mapOf("esp_mesh_diagnostics_status" to "available"))
}
