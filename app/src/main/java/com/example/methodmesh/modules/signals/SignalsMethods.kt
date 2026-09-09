package com.example.methodmesh.modules.signals

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

private const val SIGNALS_VERSION = "0.4.5"

object SignalMorseTransmitFields {
    const val RESULT = "signal_morse_result"
    const val PAYLOAD = "signal_morse_payload"
    const val NOTATION = "signal_morse_notation"
    const val ROUTE = "signal_morse_route"
    const val WPM = "signal_morse_wpm"
    const val REPETITIONS = "signal_morse_repetitions"
    const val STATUS = "signal_morse_status"
    const val ERROR = "signal_morse_error"
    val outputs = listOf(RESULT, PAYLOAD, NOTATION, ROUTE, WPM, REPETITIONS, STATUS, ERROR)
}

object SignalMorseReceiveFields {
    const val RESULT = "signal_morse_received_text"
    const val NOTATION = "signal_morse_received_notation"
    const val SOURCE = "signal_morse_source"
    const val DOT_MS = "signal_morse_dot_ms"
    const val TRANSITIONS = "signal_morse_transitions"
    const val COPIES = "signal_morse_copies"
    const val CONSENSUS_CONFIDENCE = "signal_morse_consensus_confidence"
    const val STATUS = "signal_morse_status"
    const val ERROR = "signal_morse_error"
    val outputs = listOf(RESULT, NOTATION, SOURCE, DOT_MS, TRANSITIONS, COPIES, CONSENSUS_CONFIDENCE, STATUS, ERROR)
}

object SignalQrTransmitFields {
    const val RESULT = "signal_qr_result"
    const val PAYLOAD = "signal_qr_payload"
    const val CONTENT_TYPE = "signal_qr_content_type"
    const val FILE_NAME = "signal_qr_file_name"
    const val FILE_MIME = "signal_qr_file_mime"
    const val FILE_BYTES = "signal_qr_file_bytes"
    const val CHECKSUM_SHA256 = "signal_qr_checksum_sha256"
    const val MESSAGE_ID = "signal_qr_message_id"
    const val DATA_SHARDS = "signal_qr_data_shards"
    const val PARITY_FRAMES = "signal_qr_parity_frames"
    const val FRAME_COUNT = "signal_qr_frame_count"
    const val MESSAGE_CRC32 = "signal_qr_message_crc32"
    const val ROBUSTNESS = "signal_qr_robustness"
    const val CYCLES = "signal_qr_cycles"
    const val STATUS = "signal_qr_status"
    const val ERROR = "signal_qr_error"
    val outputs = listOf(RESULT, PAYLOAD, CONTENT_TYPE, FILE_NAME, FILE_MIME, FILE_BYTES, CHECKSUM_SHA256, MESSAGE_ID, DATA_SHARDS, PARITY_FRAMES, FRAME_COUNT, MESSAGE_CRC32, ROBUSTNESS, CYCLES, STATUS, ERROR)
}

object SignalQrReceiveFields {
    const val RESULT = "signal_qr_received_text"
    const val CONTENT_TYPE = "signal_qr_content_type"
    const val RECEIVED_FILE_URI = "signal_qr_received_file_uri"
    const val FILE_NAME = "signal_qr_file_name"
    const val FILE_MIME = "signal_qr_file_mime"
    const val FILE_BYTES = "signal_qr_file_bytes"
    const val EXPECTED_SHA256 = "signal_qr_expected_sha256"
    const val RECONSTRUCTED_SHA256 = "signal_qr_reconstructed_sha256"
    const val CHECKSUM_VERIFIED = "signal_qr_checksum_verified"
    const val MESSAGE_ID = "signal_qr_message_id"
    const val FRAMES_SEEN = "signal_qr_frames_seen"
    const val FRAMES_ACCEPTED = "signal_qr_frames_accepted"
    const val FRAMES_REJECTED = "signal_qr_frames_rejected"
    const val RECOVERED_MISSING = "signal_qr_recovered_missing_shards"
    const val CRC_VERIFIED = "signal_qr_crc_verified"
    const val STATUS = "signal_qr_status"
    const val ERROR = "signal_qr_error"
    val outputs = listOf(RESULT, CONTENT_TYPE, RECEIVED_FILE_URI, FILE_NAME, FILE_MIME, FILE_BYTES, EXPECTED_SHA256, RECONSTRUCTED_SHA256, CHECKSUM_VERIFIED, MESSAGE_ID, FRAMES_SEEN, FRAMES_ACCEPTED, FRAMES_REJECTED, RECOVERED_MISSING, CRC_VERIFIED, STATUS, ERROR)
}

object SignalFskTransmitFields {
    const val RESULT = "signal_fsk_result"
    const val PAYLOAD = "signal_fsk_payload"
    const val MESSAGE_ID = "signal_fsk_message_id"
    const val FRAME_COUNT = "signal_fsk_frame_count"
    const val MARK_HZ = "signal_fsk_mark_hz"
    const val SPACE_HZ = "signal_fsk_space_hz"
    const val BIT_MS = "signal_fsk_bit_ms"
    const val PTT_LEAD_MS = "signal_fsk_ptt_lead_ms"
    const val ROBUSTNESS = "signal_fsk_robustness"
    const val CYCLES = "signal_fsk_cycles"
    const val STATUS = "signal_fsk_status"
    const val ERROR = "signal_fsk_error"
    val outputs = listOf(RESULT, PAYLOAD, MESSAGE_ID, FRAME_COUNT, MARK_HZ, SPACE_HZ, BIT_MS, PTT_LEAD_MS, ROBUSTNESS, CYCLES, STATUS, ERROR)
}

object SignalFskReceiveFields {
    const val RESULT = "signal_fsk_received_text"
    const val MESSAGE_ID = "signal_fsk_message_id"
    const val FRAMES_ACCEPTED = "signal_fsk_frames_accepted"
    const val FRAMES_REJECTED = "signal_fsk_frames_rejected"
    const val RECOVERED_MISSING = "signal_fsk_recovered_missing_shards"
    const val CRC_VERIFIED = "signal_fsk_crc_verified"
    const val MARK_HZ = "signal_fsk_mark_hz"
    const val SPACE_HZ = "signal_fsk_space_hz"
    const val BIT_MS = "signal_fsk_bit_ms"
    const val STATUS = "signal_fsk_status"
    const val ERROR = "signal_fsk_error"
    val outputs = listOf(RESULT, MESSAGE_ID, FRAMES_ACCEPTED, FRAMES_REJECTED, RECOVERED_MISSING, CRC_VERIFIED, MARK_HZ, SPACE_HZ, BIT_MS, STATUS, ERROR)
}

object SignalUltrasonicTransmitFields {
    const val RESULT = "signal_ultrasonic_result"
    const val PAYLOAD = "signal_ultrasonic_payload"
    const val MESSAGE_ID = "signal_ultrasonic_message_id"
    const val FRAME_COUNT = "signal_ultrasonic_frame_count"
    const val MARK_HZ = "signal_ultrasonic_mark_hz"
    const val SPACE_HZ = "signal_ultrasonic_space_hz"
    const val BIT_MS = "signal_ultrasonic_bit_ms"
    const val ROBUSTNESS = "signal_ultrasonic_robustness"
    const val CYCLES = "signal_ultrasonic_cycles"
    const val STATUS = "signal_ultrasonic_status"
    const val ERROR = "signal_ultrasonic_error"
    val outputs = listOf(RESULT, PAYLOAD, MESSAGE_ID, FRAME_COUNT, MARK_HZ, SPACE_HZ, BIT_MS, ROBUSTNESS, CYCLES, STATUS, ERROR)
}

object SignalUltrasonicReceiveFields {
    const val RESULT = "signal_ultrasonic_received_text"
    const val MESSAGE_ID = "signal_ultrasonic_message_id"
    const val FRAMES_ACCEPTED = "signal_ultrasonic_frames_accepted"
    const val FRAMES_REJECTED = "signal_ultrasonic_frames_rejected"
    const val RECOVERED_MISSING = "signal_ultrasonic_recovered_missing_shards"
    const val CRC_VERIFIED = "signal_ultrasonic_crc_verified"
    const val MARK_HZ = "signal_ultrasonic_mark_hz"
    const val SPACE_HZ = "signal_ultrasonic_space_hz"
    const val BIT_MS = "signal_ultrasonic_bit_ms"
    const val STATUS = "signal_ultrasonic_status"
    const val ERROR = "signal_ultrasonic_error"
    val outputs = listOf(RESULT, MESSAGE_ID, FRAMES_ACCEPTED, FRAMES_REJECTED, RECOVERED_MISSING, CRC_VERIFIED, MARK_HZ, SPACE_HZ, BIT_MS, STATUS, ERROR)
}

object SignalSurfaceTransmitFields {
    const val RESULT = "signal_surface_result"
    const val PAYLOAD = "signal_surface_payload"
    const val MESSAGE_ID = "signal_surface_message_id"
    const val FRAME_COUNT = "signal_surface_frame_count"
    const val CARRIER_HZ = "signal_surface_carrier_hz"
    const val BIT_MS = "signal_surface_bit_ms"
    const val ROBUSTNESS = "signal_surface_robustness"
    const val CYCLES = "signal_surface_cycles"
    const val CHECKSUM_SHA256 = "signal_surface_checksum_sha256"
    const val STATUS = "signal_surface_status"
    const val ERROR = "signal_surface_error"
    val outputs = listOf(RESULT, PAYLOAD, MESSAGE_ID, FRAME_COUNT, CARRIER_HZ, BIT_MS, ROBUSTNESS, CYCLES, CHECKSUM_SHA256, STATUS, ERROR)
}

object SignalSurfaceReceiveFields {
    const val RESULT = "signal_surface_received_text"
    const val MESSAGE_ID = "signal_surface_message_id"
    const val FRAMES_ACCEPTED = "signal_surface_frames_accepted"
    const val FRAMES_REJECTED = "signal_surface_frames_rejected"
    const val RECOVERED_MISSING = "signal_surface_recovered_missing_shards"
    const val CRC_VERIFIED = "signal_surface_crc_verified"
    const val CHECKSUM_SHA256 = "signal_surface_checksum_sha256"
    const val CHECKSUM_VERIFIED = "signal_surface_checksum_verified"
    const val CARRIER_HZ = "signal_surface_carrier_hz"
    const val BIT_MS = "signal_surface_bit_ms"
    const val BASELINE = "signal_surface_baseline"
    const val THRESHOLD = "signal_surface_threshold"
    const val STATUS = "signal_surface_status"
    const val ERROR = "signal_surface_error"
    val outputs = listOf(RESULT, MESSAGE_ID, FRAMES_ACCEPTED, FRAMES_REJECTED, RECOVERED_MISSING, CRC_VERIFIED, CHECKSUM_SHA256, CHECKSUM_VERIFIED, CARRIER_HZ, BIT_MS, BASELINE, THRESHOLD, STATUS, ERROR)
}

object SignalSensorScopeFields {
    const val RESULT = "signal_sensor_result"
    const val SENSOR_ID = "signal_sensor_id"
    const val VALUE_0 = "signal_sensor_value_0"
    const val VALUE_1 = "signal_sensor_value_1"
    const val VALUE_2 = "signal_sensor_value_2"
    const val MAGNITUDE = "signal_sensor_magnitude"
    const val UNIT = "signal_sensor_unit"
    const val ACCURACY = "signal_sensor_accuracy"
    const val VALUES_JSON = "signal_sensor_values_json"
    const val STATUS = "signal_sensor_status"
    const val ERROR = "signal_sensor_error"
    val outputs = listOf(RESULT, SENSOR_ID, VALUE_0, VALUE_1, VALUE_2, MAGNITUDE, UNIT, ACCURACY, VALUES_JSON, STATUS, ERROR)
}

private object SignalMethodSupport {
    fun request(
        action: String,
        method: ArchitectureRef,
        context: Map<String, String>,
        signals: List<Signal>,
        inputs: List<ArchitectureRef>
    ): ExecutionRequest = As100ExecutionEngine.request(action = action, method = method, context = context, signals = signals, inputs = inputs)

    fun complete(
        request: ExecutionRequest,
        invocation: InvocationContext?,
        methodId: String,
        phenomenon: String,
        ref: ArchitectureRef,
        statusField: String,
        errorField: String,
        values: Map<String, String>
    ): ExecutionResult {
        val status = values[statusField].orEmpty().lowercase()
        val ok = status in setOf("succeeded", "sent", "received", "captured", "complete", "committed")
        val entity = Entity(
            id = ArchitectureId("signal:${methodId.substringAfterLast('.')}:${System.currentTimeMillis()}"),
            entityType = "SignalExchange",
            attributes = values,
            temporalContext = request.temporalContext
        )
        val provenance = ProvenanceContext("methodmesh.signals", methodId, SIGNALS_VERSION)
        val observation = Observation(
            phenomenon = phenomenon,
            subject = ArchitectureRef(entity.id, entity.objectType, "Signals result"),
            values = values,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        val transformation = Transformation(
            action = methodId,
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
            diagnostics = if (ok) emptyMap() else mapOf(errorField to values[errorField].orEmpty())
        ).withInvocationContext(invocation)
    }

    fun unsupported(request: ExecutionRequest, reason: String): ExecutionResult =
        As100ExecutionEngine.complete(request, TransformationStatus.Unsupported, diagnostics = mapOf("reason" to reason))
}

abstract class SignalsMethod(
    final override val id: String,
    name: String,
    description: String,
    type: MethodObjectType,
    inputs: List<String>,
    outputs: List<String>,
    graphOutput: String,
    private val statusField: String,
    private val errorField: String,
    private val phenomenon: String,
    maturity: String
) : As100Method {
    final override val ref = ArchitectureRef(ArchitectureId(id), "Method", name)
    final override val descriptor = MethodDescriptor(
        id = ArchitectureId(id),
        methodType = type,
        name = name,
        version = SIGNALS_VERSION,
        description = description,
        inputs = inputs,
        outputs = outputs,
        graphOutputs = listOf(graphOutput),
        parameters = mapOf(
            "category" to "Signals",
            "status" to maturity,
            "maturity" to maturity.uppercase(),
            "connectivity" to "OFFLINE",
            "offline" to "true"
        )
    )
    final override val contract = MethodContract(
        method = ref,
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs,
        producedGraphOutputs = descriptor.graphOutputs
    )
    final override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) =
        SignalMethodSupport.request(action, ref, context, signals, inputs)

    final override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult =
        SignalMethodSupport.unsupported(request, "${descriptor.name} requires its Android physical-signal capability screen.")

    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?): ExecutionResult =
        SignalMethodSupport.complete(request, invocation, id, phenomenon, ref, statusField, errorField, values)
}

object As100SignalMorseTransmitMethod : SignalsMethod(
    id = "signal.morse.transmit",
    name = "Morse transmitter",
    description = "Transmit text as Morse code using the front screen, rear torch, sound, or combined routes.",
    type = MethodObjectType.Workflow,
    inputs = listOf("payload", "route", "wpm", "tone_frequency_hz", "element_gap_units", "letter_gap_units", "word_gap_units", "loop_mode", "repeat_count", "loop_gap_units"),
    outputs = SignalMorseTransmitFields.outputs,
    graphOutput = "signal.morse.transmission",
    statusField = SignalMorseTransmitFields.STATUS,
    errorField = SignalMorseTransmitFields.ERROR,
    phenomenon = "signal.morse.transmission",
    maturity = "Development"
)

object As100SignalMorseReceiveMethod : SignalsMethod(
    id = "signal.morse.receive",
    name = "Morse receiver",
    description = "Decode framed Morse timing from camera luminance or microphone tone envelope.",
    type = MethodObjectType.SignalInterpreter,
    inputs = listOf("source", "dot_ms", "auto_timing", "optical_profile", "microphone_tone_hz", "microphone_tolerance_hz", "microphone_min_dbfs", "element_gap_units", "letter_gap_units", "word_gap_units"),
    outputs = SignalMorseReceiveFields.outputs,
    graphOutput = "signal.morse.reception",
    statusField = SignalMorseReceiveFields.STATUS,
    errorField = SignalMorseReceiveFields.ERROR,
    phenomenon = "signal.morse.reception",
    maturity = "Development"
)

object As100SignalQrTransmitMethod : SignalsMethod(
    id = "signal.qr.transmit",
    name = "QR burst transmitter",
    description = "Loop error-corrected MMS/1 text or a small checksum-protected file as QR frames for camera reception.",
    type = MethodObjectType.Workflow,
    inputs = listOf("content_mode", "payload", "file_uri", "file_name", "file_mime", "robustness", "shard_bytes", "frame_duration_ms", "loop"),
    outputs = SignalQrTransmitFields.outputs,
    graphOutput = "signal.qr.transmission",
    statusField = SignalQrTransmitFields.STATUS,
    errorField = SignalQrTransmitFields.ERROR,
    phenomenon = "signal.qr.transmission",
    maturity = "Development"
)

object As100SignalQrReceiveMethod : SignalsMethod(
    id = "signal.qr.receive",
    name = "QR burst receiver",
    description = "Continuously collect MMS/1 QR frames and reconstruct checksum-verified text or a file up to 1 MiB despite missing, duplicated, or reordered frames.",
    type = MethodObjectType.SignalInterpreter,
    inputs = listOf("message_id_filter"),
    outputs = SignalQrReceiveFields.outputs,
    graphOutput = "signal.qr.reception",
    statusField = SignalQrReceiveFields.STATUS,
    errorField = SignalQrReceiveFields.ERROR,
    phenomenon = "signal.qr.reception",
    maturity = "Development"
)

object As100SignalFskTransmitMethod : SignalsMethod(
    id = "signal.audio_fsk.transmit",
    name = "Audio FSK transmitter",
    description = "Transmit MMS/1 frames as robust audible binary FSK, including a configurable radio PTT/squelch lead-in.",
    type = MethodObjectType.Workflow,
    inputs = listOf("payload", "robustness", "channel_profile", "mark_hz", "space_hz", "bit_ms", "ptt_lead_ms", "repeat_count"),
    outputs = SignalFskTransmitFields.outputs,
    graphOutput = "signal.audio_fsk.transmission",
    statusField = SignalFskTransmitFields.STATUS,
    errorField = SignalFskTransmitFields.ERROR,
    phenomenon = "signal.audio_fsk.transmission",
    maturity = "Development"
)

object As100SignalFskReceiveMethod : SignalsMethod(
    id = "signal.audio_fsk.receive",
    name = "Audio FSK receiver",
    description = "Decode MMS/1 frames from audible binary FSK received directly or through an acoustic/radio path.",
    type = MethodObjectType.SignalInterpreter,
    inputs = listOf("channel_profile", "mark_hz", "space_hz", "bit_ms", "minimum_dbfs"),
    outputs = SignalFskReceiveFields.outputs,
    graphOutput = "signal.audio_fsk.reception",
    statusField = SignalFskReceiveFields.STATUS,
    errorField = SignalFskReceiveFields.ERROR,
    phenomenon = "signal.audio_fsk.reception",
    maturity = "Development"
)

object As100SignalUltrasonicTransmitMethod : SignalsMethod(
    id = "signal.ultrasonic.transmit",
    name = "Near-ultrasonic transmitter",
    description = "Experimental high-frequency MMS/1 FSK transmitter using the phone speaker; actual usable bandwidth is device-specific.",
    type = MethodObjectType.Workflow,
    inputs = listOf("payload", "robustness", "channel_profile", "mark_hz", "space_hz", "bit_ms", "repeat_count"),
    outputs = SignalUltrasonicTransmitFields.outputs,
    graphOutput = "signal.ultrasonic.transmission",
    statusField = SignalUltrasonicTransmitFields.STATUS,
    errorField = SignalUltrasonicTransmitFields.ERROR,
    phenomenon = "signal.ultrasonic.transmission",
    maturity = "Experimental"
)

object As100SignalUltrasonicReceiveMethod : SignalsMethod(
    id = "signal.ultrasonic.receive",
    name = "Near-ultrasonic receiver",
    description = "Experimental microphone receiver for high-frequency MMS/1 FSK; carrier audibility and response vary by phone.",
    type = MethodObjectType.SignalInterpreter,
    inputs = listOf("channel_profile", "mark_hz", "space_hz", "bit_ms", "minimum_dbfs"),
    outputs = SignalUltrasonicReceiveFields.outputs,
    graphOutput = "signal.ultrasonic.reception",
    statusField = SignalUltrasonicReceiveFields.STATUS,
    errorField = SignalUltrasonicReceiveFields.ERROR,
    phenomenon = "signal.ultrasonic.reception",
    maturity = "Experimental"
)

object As100SignalSurfaceTransmitMethod : SignalsMethod(
    id = "signal.surface.transmit",
    name = "Tabletop surface transmitter",
    description = "Experimental contact-coupled text packet through a shared table, box or rail using low-frequency speaker vibration.",
    type = MethodObjectType.Workflow,
    inputs = listOf("payload", "robustness", "surface_profile", "carrier_hz", "bit_ms", "repeat_count"),
    outputs = SignalSurfaceTransmitFields.outputs,
    graphOutput = "signal.surface.transmission",
    statusField = SignalSurfaceTransmitFields.STATUS,
    errorField = SignalSurfaceTransmitFields.ERROR,
    phenomenon = "signal.surface.transmission",
    maturity = "Experimental"
)

object As100SignalSurfaceReceiveMethod : SignalsMethod(
    id = "signal.surface.receive",
    name = "Tabletop surface receiver",
    description = "Experimental accelerometer receiver for contact-coupled shared-surface packets; current phone-pair reception is not yet reliable.",
    type = MethodObjectType.SignalInterpreter,
    inputs = listOf("surface_profile", "carrier_hz", "bit_ms", "calibration_ms", "sensitivity"),
    outputs = SignalSurfaceReceiveFields.outputs,
    graphOutput = "signal.surface.reception",
    statusField = SignalSurfaceReceiveFields.STATUS,
    errorField = SignalSurfaceReceiveFields.ERROR,
    phenomenon = "signal.surface.reception",
    maturity = "Experimental"
)

object As100SignalSensorScopeMethod : SignalsMethod(
    id = "signal.sensor.scope",
    name = "Signal sensor scope",
    description = "Inspect and commit live phone light, magnetic, motion, pressure, or proximity sensor values for signal diagnostics.",
    type = MethodObjectType.SignalInterpreter,
    inputs = listOf("sensor"),
    outputs = SignalSensorScopeFields.outputs,
    graphOutput = "signal.sensor.observation",
    statusField = SignalSensorScopeFields.STATUS,
    errorField = SignalSensorScopeFields.ERROR,
    phenomenon = "signal.sensor.observation",
    maturity = "Development"
)
