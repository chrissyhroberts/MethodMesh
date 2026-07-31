package com.example.researchos.modules.scaledphoto

import com.example.researchos.core.crypto.Digests
import com.example.researchos.core.researchos.*
import com.example.researchos.core.researchos.runtime.As100ExecutionEngine
import com.example.researchos.core.researchos.runtime.As100Method
import com.example.researchos.settings.SettingsState

object As100ScaledPhotoMethod : As100Method {
    const val ID = "scaled_photo.capture"
    private const val VERSION = "0.1.0"
    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Scaled photo and grid selection")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID), methodType = MethodObjectType.SignalInterpreter,
        name = "Scaled photo and selector overlay", version = VERSION,
        description = "Capture an original calibrated photograph, optionally select grid regions, and save an annotated copy.",
        outputs = listOf("original_image_uri", "annotated_image_uri", "grid_selection_json", "grid_selection_hash", "ruler_length_mm", "ruler_target_length_mm", "hud_scale_ratio", "hud_display_length_mm", "calibration_pixels_per_mm", "photo_captured_at", "overlay_completed_at"),
        graphOutputs = listOf("scaled.photo")
    )
    override val contract = MethodContract(method = ref, producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation), producedFields = descriptor.outputs, producedGraphOutputs = descriptor.graphOutputs)
    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) = As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)
    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult {
        val c = request.context
        val original = c["original_image_uri"].orEmpty()
        if (original.isBlank()) return As100ExecutionEngine.complete(request, TransformationStatus.Unsupported, diagnostics = mapOf("reason" to "No captured image was supplied."))
        val grid = c["grid_selection_json"].orEmpty().ifBlank { "[]" }
        val values = linkedMapOf(
            "original_image_uri" to original,
            "annotated_image_uri" to c["annotated_image_uri"].orEmpty(),
            "grid_selection_json" to grid,
            "grid_selection_hash" to Digests.sha256Hex(grid),
            "ruler_length_mm" to c["ruler_length_mm"].orEmpty(),
            "ruler_target_length_mm" to c["ruler_target_length_mm"].orEmpty(),
            "hud_scale_ratio" to c["hud_scale_ratio"].orEmpty().ifBlank { "1" },
            "hud_display_length_mm" to c["hud_display_length_mm"].orEmpty(),
            "calibration_pixels_per_mm" to c["calibration_pixels_per_mm"].orEmpty(),
            "photo_captured_at" to c["photo_captured_at"].orEmpty(),
            "overlay_completed_at" to c["overlay_completed_at"].orEmpty()
        )
        val provenance = ProvenanceContext("researchos.scaledphoto", ID, VERSION, c["operator_id"])
        val observation = Observation(phenomenon = "scaled.photo", subject = InvocationContext.from(c)?.subjectRef(), values = values, temporalContext = request.temporalContext, provenance = provenance)
        return As100ExecutionEngine.complete(request, TransformationStatus.Succeeded, observations = listOf(observation), diagnostics = mapOf("grid_selection_hash" to values["grid_selection_hash"].orEmpty()))
    }
}
