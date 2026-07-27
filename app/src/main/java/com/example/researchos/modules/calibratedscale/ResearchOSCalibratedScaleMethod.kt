package com.example.researchos.modules.calibratedscale

import com.example.researchos.core.researchos.ArchitectureId
import com.example.researchos.core.researchos.ArchitectureRef
import com.example.researchos.core.researchos.MethodContract
import com.example.researchos.core.researchos.ExecutionRequest
import com.example.researchos.core.researchos.ExecutionResult
import com.example.researchos.core.researchos.InvocationContext
import com.example.researchos.core.researchos.KnowledgeObjectType
import com.example.researchos.core.researchos.MethodDescriptor
import com.example.researchos.core.researchos.MethodObjectType
import com.example.researchos.core.researchos.Observation
import com.example.researchos.core.researchos.ProvenanceContext
import com.example.researchos.core.researchos.Signal
import com.example.researchos.core.researchos.Transformation
import com.example.researchos.core.researchos.TransformationStatus
import com.example.researchos.core.researchos.runtime.As100ExecutionEngine
import com.example.researchos.core.researchos.runtime.As100Method
import com.example.researchos.settings.SettingsState
import com.example.researchos.calibration.CalibrationRepository

/**
 * Native AS1.00 method for calibrated scalar / range measurement.
 *
 * The Compose interaction owns presentation; this object owns the canonical
 * method contract and result construction.
 */
object As100CalibratedScaleMethod : As100Method {
    const val ID = "calibrated_scale"
    const val VERSION = "1.1.0"

    override val id: String = ID

    override val ref: ArchitectureRef = ArchitectureRef(
        id = ArchitectureId(ID),
        type = "Method",
        label = "Calibrated Scale"
    )

    override val descriptor: MethodDescriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.Calculation,
        name = "Calibrated Scale",
        version = VERSION,
        description = "Collect a calibrated scalar or range value and emit explicit measurement evidence.",
        inputs = listOf("manual.scale.input"),
        outputs = listOf(
            "value",
            "minimum",
            "maximum",
            "lower_value",
            "upper_value",
            "use_range",
            "scale_length_mm",
            "scale_length_dp",
            "dp_per_mm",
            "vertical_mode"
        ),
        parameters = mapOf(
            "category" to "Measurement",
            "status" to "Experimental",
            "interaction" to "manual_calibrated_visual_scale",
            "physical_length" to "vas_length_mm × calibrated device dp_per_mm"
        )
    )

    override val contract: MethodContract = MethodContract(
        method = ref,
        acceptedSignals = listOf("manual.scale.input"),
        requiredContext = emptyList(),
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs
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
    ): ExecutionResult {
        if (settingsState == null) {
            return As100ExecutionEngine.complete(
                request = request,
                status = TransformationStatus.Unsupported,
                diagnostics = mapOf("reason" to "Calibrated Scale requires a SettingsState containing the current scale values.")
            )
        }

        val values = measurementValues(settingsState)
        val provenance = ProvenanceContext(
            provider = "researchos.presentation.calibrated_scale",
            methodId = ID,
            methodVersion = VERSION
        )
        val observation = Observation(
            phenomenon = "measurement.calibrated_scale",
            subject = InvocationContext.from(request.context)?.subjectRef(),
            values = values.mapValues { it.value.toString() },
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        val transformation = Transformation(
            action = request.action,
            method = ref,
            inputs = request.inputs + request.signals.map { ArchitectureRef(it.id, "Signal", it.signalType) },
            outputs = listOf(ArchitectureRef(observation.id, "Observation", observation.phenomenon)),
            status = TransformationStatus.Succeeded,
            temporalContext = request.temporalContext,
            provenance = provenance
        )

        return As100ExecutionEngine.complete(
            request = request,
            status = TransformationStatus.Succeeded,
            observations = listOf(observation),
            transformations = listOf(transformation)
        )
    }

    fun measurementValues(settingsState: SettingsState): Map<String, Any?> {
        val minimum = settingsState.getFloat("minimum")
        val maximum = settingsState.getFloat("maximum").let { if (it > minimum) it else minimum + 1f }
        val useRange = settingsState.getBoolean("use_range")
        val value = settingsState.getFloat("value").coerceIn(minimum, maximum)
        val lower = settingsState.getFloat("lower_value").coerceIn(minimum, maximum)
        val upper = settingsState.getFloat("upper_value").coerceIn(minimum, maximum)
        val normalisedLower = minOf(lower, upper)
        val normalisedUpper = maxOf(lower, upper)
        val lengthMm = settingsState.getFloat("vas_length_mm").coerceIn(40f, 200f)
        val calibration = CalibrationRepository.current()

        return linkedMapOf<String, Any?>(
            "minimum" to minimum,
            "maximum" to maximum,
            "use_range" to useRange,
            "scale_length_mm" to lengthMm,
            "scale_length_dp" to scaleLengthDp(lengthMm, calibration.dpPerMm),
            "dp_per_mm" to calibration.dpPerMm,
            "vertical_mode" to settingsState.getBoolean("vertical_mode")
        ).apply {
            if (useRange) {
                put("lower_value", normalisedLower)
                put("upper_value", normalisedUpper)
            } else {
                put("value", value)
            }
        }
    }
}
