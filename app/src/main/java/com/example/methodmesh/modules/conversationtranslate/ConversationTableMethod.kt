package com.example.methodmesh.modules.conversationtranslate

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

object ConversationTableFields {
    const val TRANSCRIPT = "conversation_transcript"
    const val TURNS_JSON = "conversation_table_turns_json"
    const val EVENTS_JSON = "conversation_transcript_events_json"
    const val LANGUAGE_A = "conversation_language_a"
    const val LANGUAGE_B = "conversation_language_b"
    const val LANGUAGE_C = "conversation_language_c"
    const val LANGUAGE_D = "conversation_language_d"
    const val ARABIC_VARIANT_A = "conversation_arabic_variant_a"
    const val ARABIC_VARIANT_B = "conversation_arabic_variant_b"
    const val ARABIC_VARIANT_C = "conversation_arabic_variant_c"
    const val ARABIC_VARIANT_D = "conversation_arabic_variant_d"
    const val SPEECH_LOCALE_A = "conversation_speech_locale_a"
    const val SPEECH_LOCALE_B = "conversation_speech_locale_b"
    const val SPEECH_LOCALE_C = "conversation_speech_locale_c"
    const val SPEECH_LOCALE_D = "conversation_speech_locale_d"
    const val VOICE_A = "conversation_voice_a"
    const val VOICE_B = "conversation_voice_b"
    const val VOICE_C = "conversation_voice_c"
    const val VOICE_D = "conversation_voice_d"
    const val SPOKEN_OUTPUT = "conversation_spoken_output"
    const val PREFER_OFFLINE = "conversation_prefer_offline"
    const val TRANSCRIPT_ENABLED_AT_END = "conversation_transcript_enabled_at_end"
    const val TURN_COUNT = "conversation_turn_count"
    const val STARTED_TIME_ISO = "conversation_started_time_iso"
    const val FINISHED_TIME_ISO = "conversation_finished_time_iso"
    const val STATUS = "conversation_status"
    const val ERROR = "conversation_error"

    val outputs = listOf(
        TRANSCRIPT, TURNS_JSON, EVENTS_JSON,
        LANGUAGE_A, LANGUAGE_B, LANGUAGE_C, LANGUAGE_D,
        ARABIC_VARIANT_A, ARABIC_VARIANT_B, ARABIC_VARIANT_C, ARABIC_VARIANT_D,
        SPEECH_LOCALE_A, SPEECH_LOCALE_B, SPEECH_LOCALE_C, SPEECH_LOCALE_D,
        VOICE_A, VOICE_B, VOICE_C, VOICE_D,
        SPOKEN_OUTPUT, PREFER_OFFLINE, TRANSCRIPT_ENABLED_AT_END, TURN_COUNT,
        STARTED_TIME_ISO, FINISHED_TIME_ISO, STATUS, ERROR
    )
}

object As100ConversationTranslateTableMethod : As100Method {
    const val ID = "conversation.translate.table4"
    private const val VERSION = "0.7.0"

    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Four-person table conversation translator")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.SignalInterpreter,
        name = "Four-person table conversation translator",
        version = VERSION,
        description = "Run a four-seat translated conversation with deduplicated target languages, regional speech routing and independently pauseable transcript capture.",
        outputs = ConversationTableFields.outputs,
        graphOutputs = listOf("conversation.translate.table4"),
        parameters = mapOf(
            "category" to "Audio",
            "status" to "Production",
            "offline" to "requires downloaded speech and translation models"
        )
    )
    override val contract = MethodContract(
        method = ref,
        requiredContext = emptyList(),
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs,
        producedGraphOutputs = descriptor.graphOutputs
    )

    override fun request(
        action: String,
        context: Map<String, String>,
        signals: List<Signal>,
        inputs: List<ArchitectureRef>
    ): ExecutionRequest = As100ExecutionEngine.request(
        action = action,
        method = ref,
        context = context,
        signals = signals,
        inputs = inputs
    )

    override fun execute(
        request: ExecutionRequest,
        settingsState: SettingsState?,
        transport: String?
    ): ExecutionResult = As100ExecutionEngine.complete(
        request,
        TransformationStatus.Unsupported,
        diagnostics = mapOf("reason" to "Four-person conversation translation requires Android speech, ML Kit translation and text-to-speech boundaries.")
    )

    fun result(
        request: ExecutionRequest,
        values: Map<String, String>,
        invocation: InvocationContext?
    ): ExecutionResult {
        val ok = values[ConversationTableFields.STATUS] == "succeeded"
        val entity = Entity(
            ArchitectureId("conversation-table-translation:${System.currentTimeMillis()}"),
            "ConversationTranslation",
            temporalContext = request.temporalContext
        )
        val provenance = ProvenanceContext("methodmesh.conversation.translate.table4", ID, VERSION)
        val observation = Observation(
            phenomenon = "conversation.translate.table4",
            subject = ArchitectureRef(entity.id, entity.objectType, ID),
            values = values + (ConversationTableFields.FINISHED_TIME_ISO to (values[ConversationTableFields.FINISHED_TIME_ISO] ?: Instant.now().toString())),
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
            request = request,
            status = if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed,
            entities = listOf(entity),
            observations = listOf(observation),
            transformations = listOf(transformation),
            diagnostics = if (ok) emptyMap() else mapOf(ConversationTableFields.ERROR to values[ConversationTableFields.ERROR].orEmpty())
        ).withInvocationContext(invocation)
    }
}
