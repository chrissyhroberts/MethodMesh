package com.example.methodmesh.modules.calibratedscale

import com.example.methodmesh.core.methodmesh.ArchitectureId
import com.example.methodmesh.core.methodmesh.ArchitectureRef
import com.example.methodmesh.core.methodmesh.MethodContract
import com.example.methodmesh.core.methodmesh.ExecutionRequest
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.InvocationContext
import com.example.methodmesh.core.methodmesh.KnowledgeObjectType
import com.example.methodmesh.core.methodmesh.MethodDescriptor
import com.example.methodmesh.core.methodmesh.MethodObjectType
import com.example.methodmesh.core.methodmesh.Observation
import com.example.methodmesh.core.methodmesh.ProvenanceContext
import com.example.methodmesh.core.methodmesh.Signal
import com.example.methodmesh.core.methodmesh.Transformation
import com.example.methodmesh.core.methodmesh.TransformationStatus
import com.example.methodmesh.core.methodmesh.runtime.As100ExecutionEngine
import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.settings.SettingsState
import com.example.methodmesh.calibration.CalibrationRepository

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
            "lower_value",
            "upper_value",
            "minimum",
            "maximum",
            "use_range",
            "scale_length_mm",
            "scale_length_dp",
            "dp_per_mm",
            "vertical_mode"
        ),
        parameters = mapOf(
            "category" to "Measurement",
            "status" to "Production",
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
            provider = "methodmesh.presentation.calibrated_scale",
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

        return linkedMapOf<String, Any?>().apply {
            if (useRange) {
                put("lower_value", normalisedLower)
                put("upper_value", normalisedUpper)
            } else {
                put("value", value)
            }
            put("minimum", minimum)
            put("maximum", maximum)
            put("use_range", useRange)
            put("scale_length_mm", lengthMm)
            put("scale_length_dp", scaleLengthDp(lengthMm, calibration.dpPerMm))
            put("dp_per_mm", calibration.dpPerMm)
            put("vertical_mode", settingsState.getBoolean("vertical_mode"))
        }
    }
}
