package com.example.methodmesh.modules.mlkitvision

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

object MlKitVisionFields {
    const val MODE = "mlkit_mode"
    const val SOURCE_URI = "mlkit_source_uri"
    const val IMAGE_URI = "mlkit_image_uri"
    const val PDF_URI = "mlkit_pdf_uri"
    const val TEXT_FILE_URI = "mlkit_text_file_uri"
    const val STATUS = "mlkit_status"
    const val TEXT = "mlkit_text"
    const val TEXT_BLOCK_COUNT = "mlkit_text_block_count"
    const val BARCODES_JSON = "mlkit_barcodes_json"
    const val BARCODE_COUNT = "mlkit_barcode_count"
    const val FIRST_BARCODE_RAW_VALUE = "mlkit_first_barcode_raw_value"
    const val FIRST_BARCODE_FORMAT = "mlkit_first_barcode_format"
    const val ANALYSED_TIME_ISO = "mlkit_analysed_time_iso"
    const val ERROR = "mlkit_error"

    val outputs = listOf(
        MODE,
        SOURCE_URI,
        IMAGE_URI,
        PDF_URI,
        TEXT_FILE_URI,
        STATUS,
        TEXT,
        TEXT_BLOCK_COUNT,
        BARCODES_JSON,
        BARCODE_COUNT,
        FIRST_BARCODE_RAW_VALUE,
        FIRST_BARCODE_FORMAT,
        ANALYSED_TIME_ISO,
        ERROR
    )
}

object As100MlKitVisionMethod : As100Method {
    const val ID = "mlkit.vision.analyze"
    private const val VERSION = "0.1.0"

    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "ML Kit image analysis")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.SignalInterpreter,
        name = "ML Kit vision analysis",
        version = VERSION,
        description = "Run on-device ML Kit OCR and/or barcode detection on a captured or selected image.",
        outputs = MlKitVisionFields.outputs,
        graphOutputs = listOf("mlkit.vision.analyze"),
        parameters = mapOf("category" to "Recognition")
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
            diagnostics = mapOf("reason" to "ML Kit image analysis requires the Android camera/file boundary.")
        )

    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?): ExecutionResult {
        val ok = values[MlKitVisionFields.STATUS] == "succeeded"
        val entity = Entity(ArchitectureId("mlkit-vision:${System.currentTimeMillis()}"), "MlKitVisionAnalysis", temporalContext = request.temporalContext)
        val provenance = ProvenanceContext("mlkit.android", ID, VERSION)
        val observation = Observation(
            phenomenon = "mlkit.vision.analyze",
            subject = ArchitectureRef(entity.id, entity.objectType, ID),
            values = values + (MlKitVisionFields.ANALYSED_TIME_ISO to (values[MlKitVisionFields.ANALYSED_TIME_ISO] ?: Instant.now().toString())),
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
            request,
            if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed,
            entities = listOf(entity),
            observations = listOf(observation),
            transformations = listOf(transformation),
            diagnostics = if (ok) emptyMap() else mapOf("mlkit_error" to (values[MlKitVisionFields.ERROR] ?: "ML Kit analysis failed."))
        ).withInvocationContext(invocation)
    }
}
