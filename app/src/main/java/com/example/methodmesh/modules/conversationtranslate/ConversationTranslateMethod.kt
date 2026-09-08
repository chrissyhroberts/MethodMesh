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

object ConversationTranslateFields {
    const val TRANSCRIPT = "conversation_transcript"
    const val TURNS_JSON = "conversation_turns_json"
    const val LANGUAGE_A = "conversation_language_a"
    const val LANGUAGE_B = "conversation_language_b"
    const val LABEL_A = "conversation_label_a"
    const val LABEL_B = "conversation_label_b"
    const val SPOKEN_OUTPUT = "conversation_spoken_output"
    const val PREFER_OFFLINE = "conversation_prefer_offline"
    const val TURN_COUNT = "conversation_turn_count"
    const val STARTED_TIME_ISO = "conversation_started_time_iso"
    const val FINISHED_TIME_ISO = "conversation_finished_time_iso"
    const val STATUS = "conversation_status"
    const val ERROR = "conversation_error"

    val outputs = listOf(
        TRANSCRIPT,
        TURNS_JSON,
        LANGUAGE_A,
        LANGUAGE_B,
        LABEL_A,
        LABEL_B,
        SPOKEN_OUTPUT,
        PREFER_OFFLINE,
        TURN_COUNT,
        STARTED_TIME_ISO,
        FINISHED_TIME_ISO,
        STATUS,
        ERROR
    )
}

object As100ConversationTranslateMethod : As100Method {
    const val ID = "conversation.translate"
    private const val VERSION = "0.1.0"

    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Live conversation translator")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.SignalInterpreter,
        name = "Live conversation translator",
        version = VERSION,
        description = "Capture a bilingual conversation as turn-by-turn speech, translation and optional spoken output.",
        outputs = ConversationTranslateFields.outputs,
        graphOutputs = listOf("conversation.translate"),
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
        diagnostics = mapOf("reason" to "Conversation translation requires Android speech, ML Kit translation and text-to-speech boundaries.")
    )

    fun result(
        request: ExecutionRequest,
        values: Map<String, String>,
        invocation: InvocationContext?
    ): ExecutionResult {
        val ok = values[ConversationTranslateFields.STATUS] == "succeeded"
        val entity = Entity(
            ArchitectureId("conversation-translation:${System.currentTimeMillis()}"),
            "ConversationTranslation",
            temporalContext = request.temporalContext
        )
        val provenance = ProvenanceContext("methodmesh.conversation.translate", ID, VERSION)
        val observation = Observation(
            phenomenon = "conversation.translate",
            subject = ArchitectureRef(entity.id, entity.objectType, ID),
            values = values + (ConversationTranslateFields.FINISHED_TIME_ISO to (values[ConversationTranslateFields.FINISHED_TIME_ISO] ?: Instant.now().toString())),
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
            diagnostics = if (ok) emptyMap() else mapOf(ConversationTranslateFields.ERROR to values[ConversationTranslateFields.ERROR].orEmpty())
        ).withInvocationContext(invocation)
    }
}
