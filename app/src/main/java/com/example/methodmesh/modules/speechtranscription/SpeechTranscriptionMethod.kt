package com.example.methodmesh.modules.speechtranscription

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

object SpeechTranscriptionFields {
    const val LANGUAGE = "speech_language"
    const val PROMPT = "speech_prompt"
    const val PREFER_OFFLINE = "speech_prefer_offline"
    const val TEXT = "speech_text"
    const val ALTERNATIVES_JSON = "speech_alternatives_json"
    const val STATUS = "speech_status"
    const val ERROR = "speech_error"
    const val TRANSCRIBED_TIME_ISO = "speech_transcribed_time_iso"

    val outputs = listOf(
        LANGUAGE,
        PROMPT,
        PREFER_OFFLINE,
        TEXT,
        ALTERNATIVES_JSON,
        STATUS,
        ERROR,
        TRANSCRIBED_TIME_ISO
    )
}

object As100SpeechTranscriptionMethod : As100Method {
    const val ID = "speech.transcribe"
    private const val VERSION = "0.1.0"

    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Speech transcription")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.SignalInterpreter,
        name = "Speech transcription",
        version = VERSION,
        description = "Capture speech through Android speech recognition and return a text transcript.",
        outputs = SpeechTranscriptionFields.outputs,
        graphOutputs = listOf("speech.transcribe"),
        parameters = mapOf("category" to "Audio")
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
            diagnostics = mapOf("reason" to "Speech transcription requires the Android speech recognizer boundary.")
        )

    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?): ExecutionResult {
        val ok = values[SpeechTranscriptionFields.STATUS] == "succeeded"
        val entity = Entity(ArchitectureId("speech-transcription:${System.currentTimeMillis()}"), "SpeechTranscription", temporalContext = request.temporalContext)
        val provenance = ProvenanceContext("android.speech", ID, VERSION)
        val observation = Observation(
            phenomenon = "speech.transcribe",
            subject = ArchitectureRef(entity.id, entity.objectType, ID),
            values = values + (SpeechTranscriptionFields.TRANSCRIBED_TIME_ISO to (values[SpeechTranscriptionFields.TRANSCRIBED_TIME_ISO] ?: Instant.now().toString())),
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
            diagnostics = if (ok) emptyMap() else mapOf("speech_error" to (values[SpeechTranscriptionFields.ERROR] ?: "Speech transcription failed."))
        ).withInvocationContext(invocation)
    }
}
