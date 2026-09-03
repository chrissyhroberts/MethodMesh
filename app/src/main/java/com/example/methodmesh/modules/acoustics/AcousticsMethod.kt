package com.example.methodmesh.modules.acoustics

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

object AcousticAnalyseFields {
    const val RESULT = "acoustic_analysis_result"
    const val FREQUENCY_HZ = "acoustic_frequency_hz"
    const val NOTE = "acoustic_note"
    const val CENTS = "acoustic_cents"
    const val WAVELENGTH_M = "acoustic_wavelength_m"
    const val SPEED_OF_SOUND_MPS = "acoustic_speed_of_sound_mps"
    const val RMS = "acoustic_rms"
    const val PEAK = "acoustic_peak"
    const val DBFS = "acoustic_dbfs"
    const val PEAK_DBFS = "acoustic_peak_dbfs"
    const val PITCH_CONFIDENCE = "acoustic_pitch_confidence"
    const val FREQUENCY_SD_HZ = "acoustic_frequency_sd_hz"
    const val FREQUENCY_SD_CENTS = "acoustic_frequency_sd_cents"
    const val STABLE_DURATION_MS = "acoustic_stable_duration_ms"
    const val HARMONICS_JSON = "acoustic_harmonics_json"
    const val STATUS = "acoustic_status"
    const val AUDIT_JSON = "acoustic_audit_json"
    const val ERROR = "acoustic_error"

    val outputs = listOf(
        RESULT, FREQUENCY_HZ, NOTE, CENTS, WAVELENGTH_M, SPEED_OF_SOUND_MPS,
        RMS, PEAK, DBFS, PEAK_DBFS, PITCH_CONFIDENCE,
        FREQUENCY_SD_HZ, FREQUENCY_SD_CENTS, STABLE_DURATION_MS,
        HARMONICS_JSON, STATUS, AUDIT_JSON, ERROR
    )
}

object AcousticTunerFields {
    const val RESULT = "acoustic_tuner_result"
    const val NOTE = "acoustic_tuner_note"
    const val TARGET_LABEL = "acoustic_tuner_target_label"
    const val TARGET_HZ = "acoustic_tuner_target_hz"
    const val MEASURED_HZ = "acoustic_tuner_measured_hz"
    const val CENTS = "acoustic_tuner_cents"
    const val STATE = "acoustic_tuner_state"
    const val CONFIDENCE = "acoustic_tuner_confidence"
    const val FREQUENCY_SD_HZ = "acoustic_tuner_frequency_sd_hz"
    const val STABLE_DURATION_MS = "acoustic_tuner_stable_duration_ms"
    const val STATUS = "acoustic_tuner_status"
    const val AUDIT_JSON = "acoustic_tuner_audit_json"
    const val ERROR = "acoustic_tuner_error"

    val outputs = listOf(
        RESULT, NOTE, TARGET_LABEL, TARGET_HZ, MEASURED_HZ, CENTS, STATE,
        CONFIDENCE, FREQUENCY_SD_HZ, STABLE_DURATION_MS, STATUS, AUDIT_JSON, ERROR
    )
}

object AcousticLevelFields {
    const val RESULT = "acoustic_level_result"
    const val DBFS = "acoustic_level_dbfs"
    const val LEQ_DBFS = "acoustic_level_leq_dbfs"
    const val PEAK_DBFS = "acoustic_level_peak_dbfs"
    const val DB_SPL = "acoustic_level_db_spl"
    const val LEQ_DB_SPL = "acoustic_level_leq_db_spl"
    const val CALIBRATED = "acoustic_level_calibrated"
    const val CALIBRATION_OFFSET_DB = "acoustic_level_calibration_offset_db"
    const val CALIBRATION_REFERENCE_DB_SPL = "acoustic_level_calibration_reference_db_spl"
    const val STATUS = "acoustic_level_status"
    const val AUDIT_JSON = "acoustic_level_audit_json"
    const val ERROR = "acoustic_level_error"

    val outputs = listOf(
        RESULT, DBFS, LEQ_DBFS, PEAK_DBFS, DB_SPL, LEQ_DB_SPL,
        CALIBRATED, CALIBRATION_OFFSET_DB, CALIBRATION_REFERENCE_DB_SPL,
        STATUS, AUDIT_JSON, ERROR
    )
}

object AcousticCompareFields {
    const val RESULT = "acoustic_compare_result"
    const val TARGET_HZ = "acoustic_compare_target_hz"
    const val MEASURED_HZ = "acoustic_compare_measured_hz"
    const val DIFFERENCE_HZ = "acoustic_compare_difference_hz"
    const val DIFFERENCE_PERCENT = "acoustic_compare_difference_percent"
    const val DIFFERENCE_CENTS = "acoustic_compare_difference_cents"
    const val TOLERANCE_MODE = "acoustic_compare_tolerance_mode"
    const val TOLERANCE_VALUE = "acoustic_compare_tolerance_value"
    const val WITHIN_TOLERANCE = "acoustic_compare_within_tolerance"
    const val FREQUENCY_SD_HZ = "acoustic_compare_frequency_sd_hz"
    const val FREQUENCY_SD_CENTS = "acoustic_compare_frequency_sd_cents"
    const val STABLE_DURATION_MS = "acoustic_compare_stable_duration_ms"
    const val CONFIDENCE = "acoustic_compare_confidence"
    const val STATUS = "acoustic_compare_status"
    const val AUDIT_JSON = "acoustic_compare_audit_json"
    const val ERROR = "acoustic_compare_error"

    val outputs = listOf(
        RESULT, TARGET_HZ, MEASURED_HZ, DIFFERENCE_HZ, DIFFERENCE_PERCENT,
        DIFFERENCE_CENTS, TOLERANCE_MODE, TOLERANCE_VALUE, WITHIN_TOLERANCE,
        FREQUENCY_SD_HZ, FREQUENCY_SD_CENTS, STABLE_DURATION_MS, CONFIDENCE,
        STATUS, AUDIT_JSON, ERROR
    )
}

private object AcousticMethodSupport {
    fun request(
        action: String,
        method: ArchitectureRef,
        context: Map<String, String>,
        signals: List<Signal>,
        inputs: List<ArchitectureRef>
    ): ExecutionRequest = As100ExecutionEngine.request(
        action = action,
        method = method,
        context = context,
        signals = signals,
        inputs = inputs
    )

    fun complete(
        request: ExecutionRequest,
        invocation: InvocationContext?,
        methodId: String,
        version: String,
        ref: ArchitectureRef,
        phenomenon: String,
        statusField: String,
        errorField: String,
        values: Map<String, String>
    ): ExecutionResult {
        val ok = values[statusField] == "succeeded"
        val entity = Entity(
            id = ArchitectureId("acoustic:${methodId.substringAfterLast('.')}:${System.currentTimeMillis()}"),
            entityType = "AcousticMeasurement",
            attributes = values,
            temporalContext = request.temporalContext
        )
        val provenance = ProvenanceContext("methodmesh.acoustics", methodId, version)
        val observation = Observation(
            phenomenon = phenomenon,
            subject = ArchitectureRef(entity.id, entity.objectType, "Acoustic measurement"),
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

    fun missingCapture(statusField: String, errorField: String, outputFields: List<String>): Map<String, String> =
        outputFields.associateWith { "" }.toMutableMap().apply {
            this[statusField] = "failed"
            this[errorField] = "Microphone capture must run through the Acoustics capability screen."
        }
}

object As100AcousticAnalyseMethod : As100Method {
    const val ID = "acoustic.analyse"
    const val VERSION = "0.1.0"
    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Acoustic signal analyser")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.SignalInterpreter,
        name = "Acoustic analyser",
        version = VERSION,
        description = "Measure pitch/frequency, waveform/spectrum, amplitude and derived wavelength from a microphone signal.",
        inputs = listOf("capture_seconds", "sample_rate_hz", "min_frequency_hz", "max_frequency_hz", "reference_a4_hz", "temperature_c", "speed_of_sound_mode", "speed_of_sound_mps"),
        outputs = AcousticAnalyseFields.outputs,
        graphOutputs = listOf("acoustic.signal.analysis"),
        parameters = mapOf("category" to "Development", "status" to "Development", "offline" to "true")
    )
    override val contract = MethodContract(ref, producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation), producedFields = descriptor.outputs, producedGraphOutputs = descriptor.graphOutputs)
    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) = AcousticMethodSupport.request(action, ref, context, signals, inputs)
    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult =
        result(request, AcousticMethodSupport.missingCapture(AcousticAnalyseFields.STATUS, AcousticAnalyseFields.ERROR, AcousticAnalyseFields.outputs), InvocationContext.from(request.context))
    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?) = AcousticMethodSupport.complete(request, invocation, ID, VERSION, ref, "acoustic.signal.analysis", AcousticAnalyseFields.STATUS, AcousticAnalyseFields.ERROR, values)
}

object As100AcousticTunerMethod : As100Method {
    const val ID = "acoustic.tune"
    const val VERSION = "0.1.0"
    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Instrument tuner")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.SignalInterpreter,
        name = "Instrument tuner",
        version = VERSION,
        description = "Detect pitch and compare it with chromatic or instrument-specific tuning targets.",
        inputs = listOf("instrument", "string_index", "reference_a4_hz", "green_zone_cents", "capture_seconds", "minimum_stable_ms", "maximum_sd_cents"),
        outputs = AcousticTunerFields.outputs,
        graphOutputs = listOf("acoustic.tuning.measurement"),
        parameters = mapOf("category" to "Development", "status" to "Development", "offline" to "true")
    )
    override val contract = MethodContract(ref, producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation), producedFields = descriptor.outputs, producedGraphOutputs = descriptor.graphOutputs)
    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) = AcousticMethodSupport.request(action, ref, context, signals, inputs)
    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult =
        result(request, AcousticMethodSupport.missingCapture(AcousticTunerFields.STATUS, AcousticTunerFields.ERROR, AcousticTunerFields.outputs), InvocationContext.from(request.context))
    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?) = AcousticMethodSupport.complete(request, invocation, ID, VERSION, ref, "acoustic.tuning.measurement", AcousticTunerFields.STATUS, AcousticTunerFields.ERROR, values)
}

object As100AcousticLevelMethod : As100Method {
    const val ID = "acoustic.level"
    const val VERSION = "0.1.0"
    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Acoustic level measurement")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.SignalInterpreter,
        name = "Sound-level meter",
        version = VERSION,
        description = "Measure digital sound level in dBFS and optionally convert to estimated dB SPL using an operator-supplied calibration offset.",
        inputs = listOf("capture_seconds", "sample_rate_hz", "calibration_mode", "calibration_offset_db", "calibration_reference_db_spl", "calibration_note"),
        outputs = AcousticLevelFields.outputs,
        graphOutputs = listOf("acoustic.level.measurement"),
        parameters = mapOf("category" to "Development", "status" to "Development", "offline" to "true")
    )
    override val contract = MethodContract(ref, producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation), producedFields = descriptor.outputs, producedGraphOutputs = descriptor.graphOutputs)
    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) = AcousticMethodSupport.request(action, ref, context, signals, inputs)
    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult =
        result(request, AcousticMethodSupport.missingCapture(AcousticLevelFields.STATUS, AcousticLevelFields.ERROR, AcousticLevelFields.outputs), InvocationContext.from(request.context))
    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?) = AcousticMethodSupport.complete(request, invocation, ID, VERSION, ref, "acoustic.level.measurement", AcousticLevelFields.STATUS, AcousticLevelFields.ERROR, values)
}

object As100AcousticCompareMethod : As100Method {
    const val ID = "acoustic.compare"
    const val VERSION = "0.1.0"
    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Tone comparison")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.SignalInterpreter,
        name = "Tone comparator",
        version = VERSION,
        description = "Compare a stable observed tone against a specified target and Hz, percentage or cents tolerance.",
        inputs = listOf("target_hz", "tolerance_mode", "tolerance_value", "capture_seconds", "minimum_stable_ms", "maximum_sd_cents", "minimum_pitch_confidence"),
        outputs = AcousticCompareFields.outputs,
        graphOutputs = listOf("acoustic.tone.comparison"),
        parameters = mapOf("category" to "Development", "status" to "Development", "offline" to "true")
    )
    override val contract = MethodContract(ref, producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation), producedFields = descriptor.outputs, producedGraphOutputs = descriptor.graphOutputs)
    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) = AcousticMethodSupport.request(action, ref, context, signals, inputs)
    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult =
        result(request, AcousticMethodSupport.missingCapture(AcousticCompareFields.STATUS, AcousticCompareFields.ERROR, AcousticCompareFields.outputs), InvocationContext.from(request.context))
    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?) = AcousticMethodSupport.complete(request, invocation, ID, VERSION, ref, "acoustic.tone.comparison", AcousticCompareFields.STATUS, AcousticCompareFields.ERROR, values)
}
