package com.example.methodmesh.modules.compass

import com.example.methodmesh.core.methodmesh.ArchitectureId
import com.example.methodmesh.core.methodmesh.ArchitectureRef
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
import org.json.JSONObject
import java.time.Instant
import java.util.Locale
import kotlin.math.abs

object CompassFields {
    const val STATUS = "compass_status"
    const val RESULT = "compass_result"
    const val HEADING_DEG = "compass_heading_deg"
    const val CARDINAL = "compass_cardinal"
    const val TARGET_MODE = "compass_target_mode"
    const val TARGET_BEARING_DEG = "compass_target_bearing_deg"
    const val ERROR_DEG = "compass_error_deg"
    const val ABS_ERROR_DEG = "compass_abs_error_deg"
    const val ALIGNED = "compass_aligned"
    const val TOLERANCE_DEG = "compass_tolerance_deg"
    const val VIEW_MODE = "compass_view_mode"
    const val HEADING_AXIS = "compass_heading_axis"
    const val NORTH_REFERENCE = "compass_north_reference"
    const val PITCH_DEG = "compass_pitch_deg"
    const val ROLL_DEG = "compass_roll_deg"
    const val MAGNETOMETER_ACCURACY = "compass_magnetometer_accuracy"
    const val CAPTURED_TIME_ISO = "compass_captured_time_iso"
    const val AUDIT_JSON = "compass_audit_json"
    const val ERROR = "compass_error"

    val outputs = listOf(
        STATUS,
        RESULT,
        HEADING_DEG,
        CARDINAL,
        TARGET_MODE,
        TARGET_BEARING_DEG,
        ERROR_DEG,
        ABS_ERROR_DEG,
        ALIGNED,
        TOLERANCE_DEG,
        VIEW_MODE,
        HEADING_AXIS,
        NORTH_REFERENCE,
        PITCH_DEG,
        ROLL_DEG,
        MAGNETOMETER_ACCURACY,
        CAPTURED_TIME_ISO,
        AUDIT_JSON,
        ERROR
    )
}

object As100CompassMethod : As100Method {
    const val ID = "compass.read"
    const val VERSION = "0.1.0"
    private const val ALGORITHM_VERSION = "1.0.0"

    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Compass bearing")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.SignalInterpreter,
        name = "Compass",
        version = VERSION,
        description = "Capture a phone magnetic heading and optionally assess alignment with a configured bearing.",
        outputs = CompassFields.outputs,
        graphOutputs = listOf("compass.read"),
        parameters = mapOf(
            "category" to "Navigation",
            "status" to "Development",
            "north_reference" to "magnetic",
            "sensor_boundary" to "PhoneSensorRepository"
        )
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

    override fun execute(
        request: ExecutionRequest,
        settingsState: SettingsState?,
        transport: String?
    ): ExecutionResult {
        val values = failure("Compass capture requires an interactive live sensor reading.")
        return result(request, values, InvocationContext.from(request.context))
    }

    fun capture(
        request: ExecutionRequest,
        headingDegrees: Float?,
        targetMode: String,
        targetBearingDegrees: Float,
        toleranceDegrees: Float,
        viewMode: String,
        headingAxis: String,
        pitchDegrees: Float?,
        rollDegrees: Float?,
        magnetometerAccuracy: Int?,
        invocation: InvocationContext?
    ): ExecutionResult {
        val values = if (headingDegrees == null || !headingDegrees.isFinite()) {
            failure("A stable compass heading is not available yet.")
        } else {
            success(
                headingDegrees = headingDegrees,
                targetMode = targetMode,
                targetBearingDegrees = targetBearingDegrees,
                toleranceDegrees = toleranceDegrees,
                viewMode = viewMode,
                headingAxis = headingAxis,
                pitchDegrees = pitchDegrees,
                rollDegrees = rollDegrees,
                magnetometerAccuracy = magnetometerAccuracy
            )
        }
        return result(request, values, invocation)
    }

    fun result(
        request: ExecutionRequest,
        values: Map<String, String>,
        invocation: InvocationContext?
    ): ExecutionResult {
        val ok = values[CompassFields.STATUS] == "succeeded"
        val provenance = ProvenanceContext("methodmesh.phone_sensors", ID, VERSION)
        val observation = Observation(
            phenomenon = "orientation.compass_bearing",
            values = values,
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
            observations = listOf(observation),
            transformations = listOf(transformation),
            diagnostics = if (ok) emptyMap() else mapOf(CompassFields.ERROR to values[CompassFields.ERROR].orEmpty())
        ).withInvocationContext(invocation)
    }

    private fun success(
        headingDegrees: Float,
        targetMode: String,
        targetBearingDegrees: Float,
        toleranceDegrees: Float,
        viewMode: String,
        headingAxis: String,
        pitchDegrees: Float?,
        rollDegrees: Float?,
        magnetometerAccuracy: Int?
    ): Map<String, String> {
        val heading = CompassMath.normaliseDegrees(headingDegrees)
        val target = if (targetMode == "bearing") CompassMath.normaliseDegrees(targetBearingDegrees) else 0f
        val tolerance = toleranceDegrees.coerceIn(1f, 30f)
        val error = CompassMath.signedErrorDegrees(target, heading)
        val aligned = CompassMath.isAligned(target, heading, tolerance)
        val cardinal = CompassMath.cardinalDirection(heading)
        val captured = Instant.now().toString()
        val targetLabel = if (targetMode == "north") "North" else "${format1(target)}°"
        val main = "${CompassMath.headingLabel(heading)} · $targetLabel · ${CompassMath.alignmentInstruction(error, tolerance)}"

        val core = linkedMapOf(
            CompassFields.STATUS to "succeeded",
            CompassFields.RESULT to main,
            CompassFields.HEADING_DEG to format1(heading),
            CompassFields.CARDINAL to cardinal,
            CompassFields.TARGET_MODE to if (targetMode == "bearing") "bearing" else "north",
            CompassFields.TARGET_BEARING_DEG to format1(target),
            CompassFields.ERROR_DEG to format1(error),
            CompassFields.ABS_ERROR_DEG to format1(abs(error)),
            CompassFields.ALIGNED to aligned.toString(),
            CompassFields.TOLERANCE_DEG to format1(tolerance),
            CompassFields.VIEW_MODE to viewMode,
            CompassFields.HEADING_AXIS to headingAxis,
            CompassFields.NORTH_REFERENCE to "magnetic",
            CompassFields.PITCH_DEG to pitchDegrees?.let(::format1).orEmpty(),
            CompassFields.ROLL_DEG to rollDegrees?.let(::format1).orEmpty(),
            CompassFields.MAGNETOMETER_ACCURACY to magnetometerAccuracy?.toString().orEmpty(),
            CompassFields.CAPTURED_TIME_ISO to captured,
            CompassFields.ERROR to ""
        )

        val audit = JSONObject().apply {
            put("method_id", ID)
            put("method_version", VERSION)
            put("alignment_algorithm_version", ALGORITHM_VERSION)
            put("north_reference", "magnetic")
            put("heading_deg", heading.toDouble())
            put("cardinal", cardinal)
            put("target_mode", if (targetMode == "bearing") "bearing" else "north")
            put("target_bearing_deg", target.toDouble())
            put("signed_error_deg", error.toDouble())
            put("absolute_error_deg", abs(error).toDouble())
            put("alignment_tolerance_deg", tolerance.toDouble())
            put("aligned", aligned)
            put("view_mode", viewMode)
            put("heading_axis", headingAxis)
            put("pitch_deg", pitchDegrees?.toDouble() ?: JSONObject.NULL)
            put("roll_deg", rollDegrees?.toDouble() ?: JSONObject.NULL)
            put("magnetometer_accuracy", magnetometerAccuracy ?: JSONObject.NULL)
            put("captured_time_iso", captured)
            put("sensor_boundary", "PhoneSensorRepository")
            put("network_used", false)
            put("location_used", false)
        }.toString()

        core[CompassFields.AUDIT_JSON] = audit
        return core
    }

    private fun failure(message: String): Map<String, String> = linkedMapOf(
        CompassFields.STATUS to "failed",
        CompassFields.RESULT to "",
        CompassFields.HEADING_DEG to "",
        CompassFields.CARDINAL to "",
        CompassFields.TARGET_MODE to "",
        CompassFields.TARGET_BEARING_DEG to "",
        CompassFields.ERROR_DEG to "",
        CompassFields.ABS_ERROR_DEG to "",
        CompassFields.ALIGNED to "false",
        CompassFields.TOLERANCE_DEG to "",
        CompassFields.VIEW_MODE to "",
        CompassFields.HEADING_AXIS to "",
        CompassFields.NORTH_REFERENCE to "magnetic",
        CompassFields.PITCH_DEG to "",
        CompassFields.ROLL_DEG to "",
        CompassFields.MAGNETOMETER_ACCURACY to "",
        CompassFields.CAPTURED_TIME_ISO to Instant.now().toString(),
        CompassFields.AUDIT_JSON to JSONObject(mapOf("method_id" to ID, "method_version" to VERSION, "error" to message)).toString(),
        CompassFields.ERROR to message
    )

    private fun format1(value: Float): String = String.format(Locale.US, "%.1f", value)
}
