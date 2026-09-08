package com.example.methodmesh.modules.magnifier

import com.example.methodmesh.core.methodmesh.*
import com.example.methodmesh.core.methodmesh.runtime.As100ExecutionEngine
import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.core.methodmesh.withInvocationContext
import com.example.methodmesh.settings.SettingsState
import org.json.JSONObject
import java.time.Instant

object MagnifierFields {
    const val STATUS = "magnifier_status"
    const val IMAGE_URI = "magnifier_image_uri"
    const val FILTER = "magnifier_filter"
    const val ZOOM_RATIO = "magnifier_zoom_ratio"
    const val TORCH = "magnifier_torch"
    const val FOCUS_LOCKED = "magnifier_focus_locked"
    const val CAPTURED_TIME_ISO = "magnifier_captured_time_iso"
    const val METADATA_JSON = "magnifier_metadata_json"
    const val ERROR = "magnifier_error"
    val outputs = listOf(STATUS, IMAGE_URI, FILTER, ZOOM_RATIO, TORCH, FOCUS_LOCKED, CAPTURED_TIME_ISO, METADATA_JSON, ERROR)
}

object As100MagnifierMethod : As100Method {
    const val ID = "visual.magnifier.capture"
    private const val VERSION = "0.1.0"
    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Visual inspection magnifier")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID), methodType = MethodObjectType.Measurement,
        name = "Visual inspection / Magnifier", version = VERSION,
        description = "Capture a visually inspected camera frame with zoom, torch, focus and optional inspection filter metadata.",
        outputs = MagnifierFields.outputs, graphOutputs = listOf(ID),
        parameters = mapOf("category" to "Imaging", "status" to "Development")
    )
    override val contract = MethodContract(method = ref, producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation), producedFields = descriptor.outputs, producedGraphOutputs = descriptor.graphOutputs)
    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) = As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)
    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult = result(request, values(request.context), InvocationContext.from(request.context))

    fun values(settings: Map<String, String>): Map<String, String> {
        val uri = settings.value("image_uri").orEmpty()
        if (uri.isBlank()) return failure("No inspected image was captured.")
        val filter = settings.value("filter") ?: "normal"
        val zoom = settings.value("zoom_ratio") ?: "1.0"
        val torch = settings.value("torch") ?: "false"
        val focus = settings.value("focus_locked") ?: "false"
        val captured = settings.value("captured_time_iso") ?: Instant.now().toString()
        val metadata = JSONObject().apply { put("method_id", ID); put("version", VERSION); put("filter", filter); put("zoom_ratio", zoom.toDoubleOrNull() ?: 1.0); put("torch", torch.toBoolean()); put("focus_locked", focus.toBoolean()); put("captured_time_iso", captured) }
        return linkedMapOf(MagnifierFields.STATUS to "succeeded", MagnifierFields.IMAGE_URI to uri, MagnifierFields.FILTER to filter, MagnifierFields.ZOOM_RATIO to zoom, MagnifierFields.TORCH to torch, MagnifierFields.FOCUS_LOCKED to focus, MagnifierFields.CAPTURED_TIME_ISO to captured, MagnifierFields.METADATA_JSON to metadata.toString(), MagnifierFields.ERROR to "")
    }
    private fun failure(error: String) = linkedMapOf(MagnifierFields.STATUS to "failed", MagnifierFields.IMAGE_URI to "", MagnifierFields.FILTER to "normal", MagnifierFields.ZOOM_RATIO to "1.0", MagnifierFields.TORCH to "false", MagnifierFields.FOCUS_LOCKED to "false", MagnifierFields.CAPTURED_TIME_ISO to Instant.now().toString(), MagnifierFields.METADATA_JSON to "{}", MagnifierFields.ERROR to error)

    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?): ExecutionResult {
        val ok = values[MagnifierFields.STATUS] == "succeeded"
        val observation = Observation(phenomenon = ID, subject = null, values = values, temporalContext = request.temporalContext, provenance = ProvenanceContext("methodmesh.magnifier", ID, VERSION))
        val transformation = Transformation(action = ID, method = ref, outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)), status = if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed, temporalContext = request.temporalContext, provenance = ProvenanceContext("methodmesh.magnifier", ID, VERSION))
        return As100ExecutionEngine.complete(request, if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed, observations = listOf(observation), transformations = listOf(transformation), diagnostics = if (ok) emptyMap() else mapOf(MagnifierFields.ERROR to values[MagnifierFields.ERROR].orEmpty())).withInvocationContext(invocation)
    }
    private fun Map<String, String>.value(key: String) = (this[key] ?: this["input_$key"])?.takeIf { it.isNotBlank() }
}
