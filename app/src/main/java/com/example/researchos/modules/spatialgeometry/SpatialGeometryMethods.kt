package com.example.researchos.modules.spatialgeometry

import com.example.researchos.core.researchos.ArchitectureId
import com.example.researchos.core.researchos.ArchitectureRef
import com.example.researchos.core.researchos.Entity
import com.example.researchos.core.researchos.ExecutionRequest
import com.example.researchos.core.researchos.ExecutionResult
import com.example.researchos.core.researchos.InvocationContext
import com.example.researchos.core.researchos.KnowledgeObjectType
import com.example.researchos.core.researchos.MethodContract
import com.example.researchos.core.researchos.MethodDescriptor
import com.example.researchos.core.researchos.MethodObjectType
import com.example.researchos.core.researchos.Observation
import com.example.researchos.core.researchos.ProvenanceContext
import com.example.researchos.core.researchos.Signal
import com.example.researchos.core.researchos.Transformation
import com.example.researchos.core.researchos.TransformationStatus
import com.example.researchos.core.researchos.runtime.As100ExecutionEngine
import com.example.researchos.core.researchos.runtime.As100Method
import com.example.researchos.core.researchos.withInvocationContext
import com.example.researchos.settings.SettingsState
import java.time.Instant
import kotlin.math.tan

object SpatialGeometryFields {
    const val MEASUREMENT_TYPE = "measurement_type"
    const val MEASUREMENT_VALID = "measurement_valid"
    const val HORIZONTAL_DISTANCE_M = "horizontal_distance_m"
    const val OBSERVER_HEIGHT_M = "observer_height_m"
    const val BASE_ANGLE_DEG = "base_angle_deg"
    const val TOP_ANGLE_DEG = "top_angle_deg"
    const val ANGLE_DIFFERENCE_DEG = "angle_difference_deg"
    const val OBJECT_HEIGHT_M = "object_height_m"
    const val SLOPE_ANGLE_DEG = "slope_angle_deg"
    const val TILT_ANGLE_DEG = "tilt_angle_deg"
    const val GRADE_PERCENT = "grade_percent"
    const val SLOPE_RATIO = "slope_ratio"
    const val REFERENCE_HEIGHT_M = "reference_height_m"
    const val ANGULAR_SIZE_DEG = "angular_size_deg"
    const val ESTIMATED_DISTANCE_M = "estimated_distance_m"
    const val SENSOR_SOURCE = "sensor_source"
    const val MEASURED_TIME_ISO = "measured_time_iso"
    const val FORMULA = "formula"
    val outputs = listOf(MEASUREMENT_TYPE, MEASUREMENT_VALID, HORIZONTAL_DISTANCE_M, OBSERVER_HEIGHT_M, BASE_ANGLE_DEG, TOP_ANGLE_DEG, ANGLE_DIFFERENCE_DEG, OBJECT_HEIGHT_M, SLOPE_ANGLE_DEG, TILT_ANGLE_DEG, GRADE_PERCENT, SLOPE_RATIO, REFERENCE_HEIGHT_M, ANGULAR_SIZE_DEG, ESTIMATED_DISTANCE_M, SENSOR_SOURCE, MEASURED_TIME_ISO, FORMULA)
}

abstract class SpatialGeometryMethod(private val measurement: String) : As100Method {
    override val id = measurement
    override val ref = ArchitectureRef(ArchitectureId(id), "Method", measurement)
    override val descriptor = MethodDescriptor(id = ArchitectureId(id), methodType = MethodObjectType.Workflow, name = measurement, version = "1.0.0", description = "Sensor-assisted spatial geometry measurement", outputs = SpatialGeometryFields.outputs, graphOutputs = listOf("spatial.geometry.measurement"), parameters = mapOf("category" to "Spatial geometry"))
    override val contract = MethodContract(method = ref, requiredContext = emptyList(), producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation), producedFields = descriptor.outputs, producedGraphOutputs = descriptor.graphOutputs)
    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) = As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)
    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?) = run(request.context).withInvocationContext(InvocationContext.from(request.context))
    protected abstract fun values(context: Map<String, String>): Pair<Boolean, Map<String, String>>

    private fun run(context: Map<String, String>): ExecutionResult {
        val (valid, values) = values(context)
        val request = request(action = id, context = context, signals = emptyList(), inputs = emptyList())
        val entity = Entity(ArchitectureId("geometry:${id}:${System.currentTimeMillis()}"), "SpatialMeasurement", attributes = mapOf(SpatialGeometryFields.MEASUREMENT_TYPE to id), temporalContext = request.temporalContext)
        val provenance = ProvenanceContext("android.sensors", id, "1.0.0")
        val observation = Observation(phenomenon = "spatial.geometry.measurement", subject = ArchitectureRef(entity.id, entity.objectType, id), values = values + (SpatialGeometryFields.MEASURED_TIME_ISO to Instant.now().toString()), temporalContext = request.temporalContext, provenance = provenance)
        val transformation = Transformation(action = id, method = ref, outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)), status = if (valid) TransformationStatus.Succeeded else TransformationStatus.Failed, temporalContext = request.temporalContext, provenance = provenance)
        return As100ExecutionEngine.complete(request, if (valid) TransformationStatus.Succeeded else TransformationStatus.Failed, entities = listOf(entity), observations = listOf(observation), transformations = listOf(transformation), diagnostics = mapOf("measurement_valid" to valid.toString())).withInvocationContext(InvocationContext.from(context))
    }
}

object As100TreeHeightMethod : SpatialGeometryMethod("tree_height_measurement") {
    override fun values(context: Map<String, String>): Pair<Boolean, Map<String, String>> {
        val distance = context[SpatialGeometryFields.HORIZONTAL_DISTANCE_M]?.toDoubleOrNull()
        val observer = context[SpatialGeometryFields.OBSERVER_HEIGHT_M]?.toDoubleOrNull()
        val base = context[SpatialGeometryFields.BASE_ANGLE_DEG]?.toDoubleOrNull()
        val top = context[SpatialGeometryFields.TOP_ANGLE_DEG]?.toDoubleOrNull()
        val valid = listOf(distance, observer, base, top).all { it != null && it.isFinite() } && (distance ?: -1.0) > 0 && (observer ?: -1.0) >= 0 && (base ?: 90.0) in -89.0..89.0 && (top ?: 90.0) in -89.0..89.0
        val height = if (valid) (observer ?: 0.0) + (distance ?: 0.0) * (tan(Math.toRadians(top ?: 0.0)) - tan(Math.toRadians(base ?: 0.0))) else Double.NaN
        return valid to mapOf(SpatialGeometryFields.MEASUREMENT_TYPE to id, SpatialGeometryFields.MEASUREMENT_VALID to valid.toString(), SpatialGeometryFields.HORIZONTAL_DISTANCE_M to distance.format(), SpatialGeometryFields.OBSERVER_HEIGHT_M to observer.format(), SpatialGeometryFields.BASE_ANGLE_DEG to base.format(), SpatialGeometryFields.TOP_ANGLE_DEG to top.format(), SpatialGeometryFields.ANGLE_DIFFERENCE_DEG to if (valid) ((top ?: 0.0) - (base ?: 0.0)).format() else "", SpatialGeometryFields.OBJECT_HEIGHT_M to height.takeIf { it.isFinite() }.format(), SpatialGeometryFields.SENSOR_SOURCE to "rotation_vector_or_accelerometer_magnetometer", SpatialGeometryFields.FORMULA to "observer_height + horizontal_distance × (tan(top_angle) − tan(base_angle))")
    }
}

object As100SlopeInclinationMethod : SpatialGeometryMethod("slope_inclination_measurement") {
    override fun values(context: Map<String, String>): Pair<Boolean, Map<String, String>> {
        val angle = context[SpatialGeometryFields.SLOPE_ANGLE_DEG]?.toDoubleOrNull()
        val tilt = context[SpatialGeometryFields.TILT_ANGLE_DEG]?.toDoubleOrNull()
        val valid = angle != null && angle.isFinite() && angle in -89.0..89.0
        return valid to mapOf(SpatialGeometryFields.MEASUREMENT_TYPE to id, SpatialGeometryFields.MEASUREMENT_VALID to valid.toString(), SpatialGeometryFields.SLOPE_ANGLE_DEG to angle.format(), SpatialGeometryFields.TILT_ANGLE_DEG to tilt.format(), SpatialGeometryFields.GRADE_PERCENT to angle?.let { Math.tan(Math.toRadians(it)) * 100 }.format(), SpatialGeometryFields.SLOPE_RATIO to angle?.let { 1.0 / Math.tan(Math.toRadians(it)).coerceAtLeast(1e-12) }.format(), SpatialGeometryFields.SENSOR_SOURCE to "rotation_vector_or_accelerometer_magnetometer", SpatialGeometryFields.FORMULA to "grade_percent = tan(top_bottom_inclination) × 100; left_right_tilt returned separately")
    }
}

object As100GeometryDistanceMethod : SpatialGeometryMethod("geometry_distance_estimation") {
    override fun values(context: Map<String, String>): Pair<Boolean, Map<String, String>> {
        val height = context[SpatialGeometryFields.REFERENCE_HEIGHT_M]?.toDoubleOrNull()
        val angle = context[SpatialGeometryFields.ANGULAR_SIZE_DEG]?.toDoubleOrNull()
        val valid = height != null && height > 0 && angle != null && angle > 0 && angle < 180
        val distance = if (valid) height!! / (2 * tan(Math.toRadians(angle!! / 2))) else Double.NaN
        return valid to mapOf(SpatialGeometryFields.MEASUREMENT_TYPE to id, SpatialGeometryFields.MEASUREMENT_VALID to valid.toString(), SpatialGeometryFields.REFERENCE_HEIGHT_M to height.format(), SpatialGeometryFields.ANGULAR_SIZE_DEG to angle.format(), SpatialGeometryFields.ESTIMATED_DISTANCE_M to distance.takeIf { it.isFinite() }.format(), SpatialGeometryFields.SENSOR_SOURCE to "phone_orientation", SpatialGeometryFields.FORMULA to "distance = reference_height / (2 × tan(angular_size / 2))")
    }
}

private fun Double?.format(): String = this?.let { "%.4f".format(java.util.Locale.US, it) }.orEmpty()
