package com.example.methodmesh.modules.svgselector

import com.example.methodmesh.core.methodmesh.*
import com.example.methodmesh.core.methodmesh.runtime.As100ExecutionEngine
import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.settings.SettingsState

object As100SvgSelectorMethod : As100Method {
    const val ID = "svg.select"
    private const val VERSION = "0.1.0"

    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "SVG polygon selector")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID), methodType = MethodObjectType.SignalInterpreter,
        name = "SVG polygon selector", version = VERSION,
        description = "Select one, multiple, or an ordered sequence of SVG polygons.",
        outputs = listOf("svg_name", "selection_mode", "selected_polygons", "selection_events", "selection_audit_hash", "selection_started_at", "selection_completed_at"),
        graphOutputs = listOf("svg.selection")
    )
    override val contract = MethodContract(method = ref, producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation), producedFields = descriptor.outputs, producedGraphOutputs = descriptor.graphOutputs)
    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) =
        As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)

    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult {
        val c = request.context
        val selected = c["selected_polygons"].orEmpty()
        val events = c["selection_events"].orEmpty()
        if (selected.isBlank() && events.isBlank()) {
            return As100ExecutionEngine.complete(request, TransformationStatus.Unsupported, diagnostics = mapOf("reason" to "No SVG selection result was supplied."))
        }
        val values = linkedMapOf(
            "svg_name" to c["svg_name"].orEmpty(),
            "selection_mode" to c["selection_mode"].orEmpty().ifBlank { "single" },
            "selected_polygons" to selected.ifBlank { "[]" },
            "selection_events" to events.ifBlank { "[]" },
            "selection_audit_hash" to SvgSelectorCodec.auditHash(events.ifBlank { selected }),
            "selection_started_at" to c["selection_started_at"].orEmpty(),
            "selection_completed_at" to c["selection_completed_at"].orEmpty().ifBlank { SvgSelectorCodec.now() }
        )
        val provenance = ProvenanceContext("methodmesh.svgselector", ID, VERSION, c["operator_id"])
        val observation = Observation(phenomenon = "svg.selection", subject = InvocationContext.from(c)?.subjectRef(), values = values, temporalContext = request.temporalContext, provenance = provenance)
        return As100ExecutionEngine.complete(request, TransformationStatus.Succeeded, observations = listOf(observation), diagnostics = mapOf("selection_audit_hash" to values["selection_audit_hash"].orEmpty()))
    }
}
