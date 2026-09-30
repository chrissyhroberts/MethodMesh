package com.example.methodmesh.modules.livestreamtranslate

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

internal enum class LiveStreamMode(val wireValue: String) {
    FIXED("fixed"),
    AUTO("auto"),
    STREAMING("streaming")
}

object LiveStreamTranslateFields {
    const val TRANSCRIPT = "live_translation_transcript"
    const val SEGMENTS_JSON = "live_translation_segments_json"
    const val MODE = "live_translation_mode"
    const val SOURCE_LANGUAGE = "live_translation_source_language"
    const val TARGET_LANGUAGE = "live_translation_target_language"
    const val AUTO_ALLOWED_LANGUAGES = "live_translation_auto_allowed_languages"
    const val SWITCH_SENSITIVITY = "live_translation_switch_sensitivity"
    const val SPEECH_ENGINE_REQUESTED = "live_translation_speech_engine_requested"
    const val LAST_SPEECH_ENGINE = "live_translation_last_speech_engine"
    const val ENGINE_SWITCH_COUNT = "live_translation_engine_switch_count"
    const val PREFER_OFFLINE = "live_translation_prefer_offline"
    const val TRANSCRIPT_ENABLED_AT_END = "live_translation_transcript_enabled_at_end"
    const val LAST_DETECTED_LANGUAGE = "live_translation_last_detected_language"
    const val SEGMENT_COUNT = "live_translation_segment_count"
    const val STARTED_TIME_ISO = "live_translation_started_time_iso"
    const val FINISHED_TIME_ISO = "live_translation_finished_time_iso"
    const val STATUS = "live_translation_status"
    const val ERROR = "live_translation_error"

    val outputs = listOf(
        TRANSCRIPT,
        SEGMENTS_JSON,
        MODE,
        SOURCE_LANGUAGE,
        TARGET_LANGUAGE,
        AUTO_ALLOWED_LANGUAGES,
        SWITCH_SENSITIVITY,
        SPEECH_ENGINE_REQUESTED,
        LAST_SPEECH_ENGINE,
        ENGINE_SWITCH_COUNT,
        PREFER_OFFLINE,
        TRANSCRIPT_ENABLED_AT_END,
        LAST_DETECTED_LANGUAGE,
        SEGMENT_COUNT,
        STARTED_TIME_ISO,
        FINISHED_TIME_ISO,
        STATUS,
        ERROR
    )
}

private object LiveStreamTranslateMethodSupport {
    const val VERSION = "0.3.3"

    fun descriptor(
        id: String,
        name: String,
        description: String,
        mode: LiveStreamMode,
        inputs: List<String>,
        maturity: String,
        connectivity: String
    ) = MethodDescriptor(
        id = ArchitectureId(id),
        methodType = MethodObjectType.SignalInterpreter,
        name = name,
        version = VERSION,
        description = description,
        inputs = inputs,
        outputs = LiveStreamTranslateFields.outputs,
        graphOutputs = listOf(id),
        parameters = mapOf(
            "category" to "Audio",
            "maturity" to maturity,
            "connectivity" to connectivity,
            "interaction_lifecycle" to "live_working_result_commit",
            "icon_key" to "language",
            "mode" to mode.wireValue,
            "speech_provider" to when (mode) {
                LiveStreamMode.STREAMING -> "ML Kit continuous partial/final stream; working partial translations are revision-conflated and never persisted as final transcript segments"
                LiveStreamMode.AUTO -> "Android language detection/switching; final-utterance translation"
                LiveStreamMode.FIXED -> "hot-swappable Android / ML Kit Basic / ML Kit GenAI; final-utterance translation"
            }
        )
    )

    fun contract(ref: ArchitectureRef, descriptor: MethodDescriptor) = MethodContract(
        method = ref,
        requiredContext = emptyList(),
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs,
        producedGraphOutputs = descriptor.graphOutputs
    )

    fun result(
        method: As100Method,
        request: ExecutionRequest,
        values: Map<String, String>,
        invocation: InvocationContext?
    ): ExecutionResult {
        val ok = values[LiveStreamTranslateFields.STATUS] == "succeeded"
        val entity = Entity(
            ArchitectureId("live-translation:${System.currentTimeMillis()}"),
            "LiveTranslationSession",
            temporalContext = request.temporalContext
        )
        val provenance = ProvenanceContext("methodmesh.live.stream.translate", method.id, VERSION)
        val observation = Observation(
            phenomenon = method.id,
            subject = ArchitectureRef(entity.id, entity.objectType, method.id),
            values = values + (
                LiveStreamTranslateFields.FINISHED_TIME_ISO to
                    (values[LiveStreamTranslateFields.FINISHED_TIME_ISO] ?: Instant.now().toString())
                ),
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        val transformation = Transformation(
            action = method.id,
            method = method.ref,
            outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)),
            status = if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        return As100ExecutionEngine.complete(
            request = request,
            status = if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed,
            entities = listOf(entity),
            observations = listOf(observation),
            transformations = listOf(transformation),
            diagnostics = if (ok) emptyMap() else mapOf(
                LiveStreamTranslateFields.ERROR to values[LiveStreamTranslateFields.ERROR].orEmpty()
            )
        ).withInvocationContext(invocation)
    }
}

object As100LiveStreamTranslateFixedMethod : As100Method {
    const val ID = "conversation.translate.live.fixed"

    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Live stream translation · fixed source")
    override val descriptor = LiveStreamTranslateMethodSupport.descriptor(
        id = ID,
        name = "Live stream translation · fixed source",
        description = "Continuously capture meeting speech in a selected source language and translate final recognition segments into a selected target language.",
        mode = LiveStreamMode.FIXED,
        inputs = listOf("source_language", "target_language", "speech_engine", "prefer_offline", "transcript_on_start"),
        maturity = "Development",
        connectivity = "ONLINE_OFFLINE"
    )
    override val contract = LiveStreamTranslateMethodSupport.contract(ref, descriptor)

    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>): ExecutionRequest =
        As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)

    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult =
        As100ExecutionEngine.complete(
            request,
            TransformationStatus.Unsupported,
            diagnostics = mapOf("reason" to "Live stream translation requires the Android microphone boundary plus a configured speech-recognition provider and ML Kit translation.")
        )

    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?): ExecutionResult =
        LiveStreamTranslateMethodSupport.result(this, request, values, invocation)
}

object As100LiveStreamTranslateAutoMethod : As100Method {
    const val ID = "conversation.translate.live.auto"

    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Live stream translation · auto language")
    override val descriptor = LiveStreamTranslateMethodSupport.descriptor(
        id = ID,
        name = "Live stream translation · auto language",
        description = "Continuously capture meeting speech, ask Android speech recognition to detect/switch language, and translate final recognition segments into a selected target language.",
        mode = LiveStreamMode.AUTO,
        inputs = listOf("target_language", "allowed_languages", "switch_sensitivity", "speech_engine", "prefer_offline", "transcript_on_start"),
        maturity = "Development",
        connectivity = "ONLINE_OFFLINE"
    )
    override val contract = LiveStreamTranslateMethodSupport.contract(ref, descriptor)

    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>): ExecutionRequest =
        As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)

    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult =
        As100ExecutionEngine.complete(
            request,
            TransformationStatus.Unsupported,
            diagnostics = mapOf("reason" to "Automatic live language switching requires Android 14+ platform recognizer support.")
        )

    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?): ExecutionResult =
        LiveStreamTranslateMethodSupport.result(this, request, values, invocation)
}

object As100LiveStreamTranslateStreamingMethod : As100Method {
    const val ID = "conversation.translate.live.streaming"

    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Live translation · streaming")
    override val descriptor = LiveStreamTranslateMethodSupport.descriptor(
        id = ID,
        name = "Live translation · streaming",
        description = "Translate a continuously revised ML Kit speech-recognition stream while speech is still in progress; final recognition segments remain the committed transcript boundary.",
        mode = LiveStreamMode.STREAMING,
        inputs = listOf("source_language", "target_language", "speech_engine", "stream_response", "transcript_on_start"),
        maturity = "Experimental",
        connectivity = "ONLINE_OFFLINE"
    )
    override val contract = LiveStreamTranslateMethodSupport.contract(ref, descriptor)

    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>): ExecutionRequest =
        As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)

    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult =
        As100ExecutionEngine.complete(
            request,
            TransformationStatus.Unsupported,
            diagnostics = mapOf("reason" to "Streaming live translation requires the Android microphone boundary, ML Kit Speech Recognition streaming output, and ML Kit on-device translation.")
        )

    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?): ExecutionResult =
        LiveStreamTranslateMethodSupport.result(this, request, values, invocation)
}
