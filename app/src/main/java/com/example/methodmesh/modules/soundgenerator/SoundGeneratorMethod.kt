package com.example.methodmesh.modules.soundgenerator

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

object SoundGeneratorFields {
    const val STATUS = "sound_status"
    const val SUMMARY = "sound_summary"
    const val STIMULUS_TYPE = "sound_stimulus_type"
    const val WAVEFORM = "sound_waveform"
    const val FREQUENCY_HZ = "sound_frequency_hz"
    const val NOISE_TYPE = "sound_noise_type"
    const val SWEEP_START_HZ = "sound_sweep_start_hz"
    const val SWEEP_END_HZ = "sound_sweep_end_hz"
    const val SWEEP_SCALE = "sound_sweep_scale"
    const val LEVEL_DBFS = "sound_level_dbfs"
    const val AMPLITUDE_LINEAR = "sound_amplitude_linear"
    const val DURATION_MS = "sound_duration_ms"
    const val CHANNEL = "sound_channel"
    const val FADE_MS = "sound_fade_ms"
    const val GATE_MODE = "sound_gate_mode"
    const val PULSE_ON_MS = "sound_pulse_on_ms"
    const val PULSE_OFF_MS = "sound_pulse_off_ms"
    const val SAMPLE_RATE_HZ = "sound_sample_rate_hz"
    const val ALGORITHM_ID = "sound_algorithm_id"
    const val ALGORITHM_VERSION = "sound_algorithm_version"
    const val NOISE_SEED = "sound_noise_seed"
    const val PCM_SHA256 = "sound_pcm_sha256"
    const val WRITTEN_PCM_SHA256 = "sound_written_pcm_sha256"
    const val FRAMES_PLANNED = "sound_frames_planned"
    const val FRAMES_WRITTEN = "sound_frames_written"
    const val FRAMES_PLAYED = "sound_frames_played"
    const val REQUESTED_DEVICE = "sound_requested_device"
    const val ROUTED_DEVICE = "sound_routed_device"
    const val ROUTED_DEVICE_ID = "sound_routed_device_id"
    const val ROUTED_DEVICE_TYPE = "sound_routed_device_type"
    const val VOLUME_POLICY = "sound_system_volume_policy"
    const val VOLUME_PERCENT = "sound_system_volume_percent"
    const val VOLUME_BEFORE = "sound_media_volume_before"
    const val VOLUME_TARGET = "sound_media_volume_target"
    const val VOLUME_DURING = "sound_media_volume_during"
    const val VOLUME_AFTER = "sound_media_volume_after"
    const val VOLUME_MAX = "sound_media_volume_max"
    const val AUDIO_FOCUS_GRANTED = "sound_audio_focus_granted"
    const val AUDIO_FOCUS_INTERRUPTED = "sound_audio_focus_interrupted"
    const val PREFERRED_ROUTE_ACCEPTED = "sound_preferred_route_accepted"
    const val STARTED_TIME_ISO = "sound_started_time_iso"
    const val FINISHED_TIME_ISO = "sound_finished_time_iso"
    const val AUDIT_JSON = "sound_audit_json"
    const val ERROR = "sound_error"

    val outputs = listOf(
        STATUS, SUMMARY, STIMULUS_TYPE, WAVEFORM, FREQUENCY_HZ, NOISE_TYPE,
        SWEEP_START_HZ, SWEEP_END_HZ, SWEEP_SCALE, LEVEL_DBFS, AMPLITUDE_LINEAR,
        DURATION_MS, CHANNEL, FADE_MS, GATE_MODE, PULSE_ON_MS, PULSE_OFF_MS,
        SAMPLE_RATE_HZ, ALGORITHM_ID, ALGORITHM_VERSION, NOISE_SEED, PCM_SHA256,
        WRITTEN_PCM_SHA256, FRAMES_PLANNED, FRAMES_WRITTEN, FRAMES_PLAYED,
        REQUESTED_DEVICE, ROUTED_DEVICE, ROUTED_DEVICE_ID, ROUTED_DEVICE_TYPE,
        VOLUME_POLICY, VOLUME_PERCENT, VOLUME_BEFORE, VOLUME_TARGET, VOLUME_DURING, VOLUME_AFTER,
        VOLUME_MAX, AUDIO_FOCUS_GRANTED, AUDIO_FOCUS_INTERRUPTED,
        PREFERRED_ROUTE_ACCEPTED, STARTED_TIME_ISO, FINISHED_TIME_ISO, AUDIT_JSON, ERROR
    )
}

object As100SoundPlayMethod : As100Method {
    const val ID = "sound.play"
    private const val VERSION = "0.1.0"

    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Generate and play a sound stimulus")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.Workflow,
        name = "Sound generator",
        version = VERSION,
        description = "Generate and play a reproducible local digital audio stimulus.",
        outputs = SoundGeneratorFields.outputs,
        graphOutputs = listOf(ID),
        parameters = mapOf("category" to "Stimulus generation", "status" to "Development")
    )
    override val contract = MethodContract(
        method = ref,
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs,
        producedGraphOutputs = descriptor.graphOutputs
    )

    override fun request(
        action: String,
        context: Map<String, String>,
        signals: List<Signal>,
        inputs: List<ArchitectureRef>
    ) = As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)

    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult =
        As100ExecutionEngine.complete(
            request,
            TransformationStatus.Unsupported,
            diagnostics = mapOf("reason" to "Sound playback requires the Android AudioTrack boundary.")
        )

    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?): ExecutionResult {
        val statusText = values[SoundGeneratorFields.STATUS].orEmpty()
        val transformationStatus = when (statusText) {
            "played" -> TransformationStatus.Succeeded
            "stopped" -> TransformationStatus.Cancelled
            else -> TransformationStatus.Failed
        }
        val entity = Entity(
            ArchitectureId("sound-stimulus:${System.currentTimeMillis()}"),
            "SoundStimulus",
            temporalContext = request.temporalContext
        )
        val provenance = ProvenanceContext("android.media.AudioTrack", ID, VERSION)
        val observation = Observation(
            phenomenon = ID,
            subject = ArchitectureRef(entity.id, entity.objectType, ID),
            values = values,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        val transformation = Transformation(
            action = ID,
            method = ref,
            outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)),
            status = transformationStatus,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        return As100ExecutionEngine.complete(
            request,
            transformationStatus,
            entities = listOf(entity),
            observations = listOf(observation),
            transformations = listOf(transformation),
            diagnostics = if (transformationStatus == TransformationStatus.Succeeded) emptyMap()
            else mapOf(SoundGeneratorFields.ERROR to values[SoundGeneratorFields.ERROR].orEmpty())
        ).withInvocationContext(invocation ?: InvocationContext.from(emptyMap()))
    }
}
