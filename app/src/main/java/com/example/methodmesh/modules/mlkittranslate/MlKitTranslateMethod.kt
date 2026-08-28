package com.example.methodmesh.modules.mlkittranslate

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

object MlKitTranslateFields {
    const val SOURCE_LANGUAGE = "mlkit_translate_source_language"
    const val TARGET_LANGUAGE = "mlkit_translate_target_language"
    const val INPUT_TEXT = "mlkit_translate_input_text"
    const val TRANSLATED_TEXT = "mlkit_translate_text"
    const val MODEL_ACTION = "mlkit_translate_model_action"
    const val DOWNLOADED_MODELS = "mlkit_translate_downloaded_models"
    const val AVAILABLE_LANGUAGES = "mlkit_translate_available_languages"
    const val STATUS = "mlkit_translate_status"
    const val ERROR = "mlkit_translate_error"
    const val TRANSLATED_TIME_ISO = "mlkit_translate_time_iso"

    val outputs = listOf(
        SOURCE_LANGUAGE,
        TARGET_LANGUAGE,
        INPUT_TEXT,
        TRANSLATED_TEXT,
        MODEL_ACTION,
        DOWNLOADED_MODELS,
        AVAILABLE_LANGUAGES,
        STATUS,
        ERROR,
        TRANSLATED_TIME_ISO
    )
}

object As100MlKitTranslateMethod : As100Method {
    const val ID = "mlkit.translate"
    private const val VERSION = "0.1.0"

    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "ML Kit translation")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.Calculation,
        name = "ML Kit translation",
        version = VERSION,
        description = "Manage ML Kit translation language models and translate text on device.",
        outputs = MlKitTranslateFields.outputs,
        graphOutputs = listOf("mlkit.translate"),
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
            diagnostics = mapOf("reason" to "ML Kit translation requires the Android model manager boundary.")
        )

    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?): ExecutionResult {
        val ok = values[MlKitTranslateFields.STATUS] == "succeeded"
        val entity = Entity(ArchitectureId("mlkit-translate:${System.currentTimeMillis()}"), "MlKitTranslation", temporalContext = request.temporalContext)
        val provenance = ProvenanceContext("mlkit.android", ID, VERSION)
        val observation = Observation(
            phenomenon = "mlkit.translate",
            subject = ArchitectureRef(entity.id, entity.objectType, ID),
            values = values + (MlKitTranslateFields.TRANSLATED_TIME_ISO to (values[MlKitTranslateFields.TRANSLATED_TIME_ISO] ?: Instant.now().toString())),
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
            diagnostics = if (ok) emptyMap() else mapOf("mlkit_translate_error" to (values[MlKitTranslateFields.ERROR] ?: "ML Kit translation failed."))
        ).withInvocationContext(invocation)
    }
}
