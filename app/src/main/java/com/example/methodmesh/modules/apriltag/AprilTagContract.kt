package com.example.methodmesh.modules.apriltag

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.Locale
import kotlin.math.sqrt

object AprilTagContractMetadata {
    const val MATURITY = "Experimental"
    const val CONNECTIVITY = "Offline"
    const val VERSION = "0.2.0"
    const val DEFAULT_FAMILY = "tagStandard41h12"
    const val DEFAULT_TAG_SIZE_MM = 100f
}

object AprilTagInputs {
    const val TAG_FAMILY = "tag_family"
    const val TAG_SIZE_MM = "tag_size_mm"
    const val TARGET_TAG_ID = "target_tag_id"
    const val REFERENCE_TAG_ID = "reference_tag_id"
    const val MOVING_TAG_ID = "moving_tag_id"
    const val KNOWN_DISTANCE_MM = "known_distance_mm"
    const val CALIBRATION_SAMPLES = "calibration_samples"
    const val INTRINSICS_MODE = "intrinsics_mode"
    const val FX_PX = "fx_px"
    const val FY_PX = "fy_px"
    const val CX_PX = "cx_px"
    const val CY_PX = "cy_px"
    const val INTRINSICS_WIDTH_PX = "intrinsics_width_px"
    const val INTRINSICS_HEIGHT_PX = "intrinsics_height_px"
    const val MEASUREMENT_MODE = "measurement_mode"
    const val ANCHOR_LAYOUT_JSON = "anchor_layout_json"
    const val SAMPLE_INTERVAL_MS = "sample_interval_ms"
    const val MAX_SAMPLES = "max_samples"
    const val QUAD_DECIMATE = "quad_decimate"
    const val THREADS = "detector_threads"
    const val REFINE_EDGES = "refine_edges"
}

object AprilTagCommonFields {
    const val STATUS = "apriltag_status"
    const val RESULT = "apriltag_result"
    const val FAMILY = "apriltag_family"
    const val CAPTURED_TIME_ISO = "apriltag_captured_time_iso"
    const val AUDIT_JSON = "apriltag_audit_json"
    const val ERROR = "apriltag_error"
    const val WARNING = "apriltag_warning"
    const val GEOMETRY_VALID = "apriltag_geometry_valid"
    const val BACKEND = "apriltag_backend"
}

object DetectFields {
    val outputs = listOf(
        AprilTagCommonFields.STATUS,
        AprilTagCommonFields.RESULT,
        "apriltag_tag_count",
        "apriltag_tag_ids",
        "apriltag_selected_tag_id",
        AprilTagCommonFields.FAMILY,
        "apriltag_apparent_edge_px",
        "apriltag_center_x_px",
        "apriltag_center_y_px",
        "apriltag_decision_margin",
        "apriltag_hamming",
        "apriltag_detections_json",
        "apriltag_image_width_px",
        "apriltag_image_height_px",
        AprilTagCommonFields.BACKEND,
        AprilTagCommonFields.CAPTURED_TIME_ISO,
        AprilTagCommonFields.AUDIT_JSON,
        AprilTagCommonFields.WARNING,
        AprilTagCommonFields.ERROR
    )
}

object CalibrationFields {
    val outputs = listOf(
        AprilTagCommonFields.STATUS,
        AprilTagCommonFields.RESULT,
        "apriltag_calibration_tag_id",
        AprilTagCommonFields.FAMILY,
        "apriltag_tag_size_mm",
        "apriltag_known_distance_mm",
        "apriltag_calibration_sample_count",
        "apriltag_mean_edge_px",
        "apriltag_edge_cv",
        "apriltag_fx_px",
        "apriltag_fy_px",
        "apriltag_cx_px",
        "apriltag_cy_px",
        "apriltag_intrinsics_width_px",
        "apriltag_intrinsics_height_px",
        "apriltag_intrinsics_source",
        "apriltag_calibration_profile_json",
        AprilTagCommonFields.GEOMETRY_VALID,
        AprilTagCommonFields.BACKEND,
        AprilTagCommonFields.CAPTURED_TIME_ISO,
        AprilTagCommonFields.AUDIT_JSON,
        AprilTagCommonFields.WARNING,
        AprilTagCommonFields.ERROR
    )
}

object RangePoseFields {
    val outputs = listOf(
        AprilTagCommonFields.STATUS,
        AprilTagCommonFields.RESULT,
        "apriltag_tag_id",
        AprilTagCommonFields.FAMILY,
        "apriltag_tag_size_mm",
        "apriltag_distance_m",
        "apriltag_x_right_m",
        "apriltag_y_down_m",
        "apriltag_z_forward_m",
        "apriltag_yaw_deg",
        "apriltag_pitch_deg",
        "apriltag_roll_deg",
        "apriltag_apparent_edge_px",
        "apriltag_decision_margin",
        "apriltag_hamming",
        "apriltag_pose_error",
        "apriltag_intrinsics_source",
        "apriltag_fx_px",
        "apriltag_fy_px",
        "apriltag_cx_px",
        "apriltag_cy_px",
        "apriltag_image_width_px",
        "apriltag_image_height_px",
        AprilTagCommonFields.GEOMETRY_VALID,
        AprilTagCommonFields.BACKEND,
        AprilTagCommonFields.CAPTURED_TIME_ISO,
        AprilTagCommonFields.AUDIT_JSON,
        AprilTagCommonFields.WARNING,
        AprilTagCommonFields.ERROR
    )
}

object RelativePoseFields {
    val outputs = listOf(
        AprilTagCommonFields.STATUS,
        AprilTagCommonFields.RESULT,
        "apriltag_reference_tag_id",
        "apriltag_target_tag_id",
        AprilTagCommonFields.FAMILY,
        "apriltag_tag_size_mm",
        "apriltag_relative_x_m",
        "apriltag_relative_y_m",
        "apriltag_relative_z_m",
        "apriltag_relative_distance_m",
        "apriltag_relative_yaw_deg",
        "apriltag_relative_pitch_deg",
        "apriltag_relative_roll_deg",
        "apriltag_reference_pose_error",
        "apriltag_target_pose_error",
        "apriltag_intrinsics_source",
        AprilTagCommonFields.GEOMETRY_VALID,
        AprilTagCommonFields.BACKEND,
        AprilTagCommonFields.CAPTURED_TIME_ISO,
        AprilTagCommonFields.AUDIT_JSON,
        AprilTagCommonFields.WARNING,
        AprilTagCommonFields.ERROR
    )
}

object PlanarMeasureFields {
    val outputs = listOf(
        AprilTagCommonFields.STATUS,
        AprilTagCommonFields.RESULT,
        "apriltag_reference_tag_id",
        AprilTagCommonFields.FAMILY,
        "apriltag_tag_size_mm",
        "apriltag_measurement_mode",
        "apriltag_reference_frame",
        "apriltag_point_count",
        "apriltag_point_x_mm",
        "apriltag_point_y_mm",
        "apriltag_distance_mm",
        "apriltag_area_mm2",
        "apriltag_perimeter_mm",
        "apriltag_path_length_mm",
        "apriltag_duration_s",
        "apriltag_mean_speed_mm_s",
        "apriltag_points_json",
        "apriltag_assumption",
        AprilTagCommonFields.GEOMETRY_VALID,
        AprilTagCommonFields.BACKEND,
        AprilTagCommonFields.CAPTURED_TIME_ISO,
        AprilTagCommonFields.AUDIT_JSON,
        AprilTagCommonFields.WARNING,
        AprilTagCommonFields.ERROR
    )
}

object TrackPoseFields {
    val outputs = listOf(
        AprilTagCommonFields.STATUS,
        AprilTagCommonFields.RESULT,
        "apriltag_moving_tag_id",
        "apriltag_reference_tag_id",
        AprilTagCommonFields.FAMILY,
        "apriltag_tag_size_mm",
        "apriltag_reference_frame",
        "apriltag_sample_count",
        "apriltag_duration_s",
        "apriltag_path_length_m",
        "apriltag_displacement_m",
        "apriltag_mean_speed_m_s",
        "apriltag_max_speed_m_s",
        "apriltag_start_x_m",
        "apriltag_start_y_m",
        "apriltag_start_z_m",
        "apriltag_end_x_m",
        "apriltag_end_y_m",
        "apriltag_end_z_m",
        "apriltag_samples_json",
        "apriltag_intrinsics_source",
        AprilTagCommonFields.GEOMETRY_VALID,
        AprilTagCommonFields.BACKEND,
        AprilTagCommonFields.CAPTURED_TIME_ISO,
        AprilTagCommonFields.AUDIT_JSON,
        AprilTagCommonFields.WARNING,
        AprilTagCommonFields.ERROR
    )
}

data class PixelPoint(val x: Double, val y: Double)
data class Vec3(val x: Double, val y: Double, val z: Double) {
    fun distanceTo(other: Vec3): Double = sqrt(
        (x - other.x) * (x - other.x) +
            (y - other.y) * (y - other.y) +
            (z - other.z) * (z - other.z)
    )
}

data class Pose3(
    val translation: Vec3,
    /** Row-major 3x3 rotation matrix: tag frame -> camera frame. */
    val rotation: DoubleArray,
    val error: Double? = null
)

data class AprilTagDetection(
    val id: Int,
    val family: String,
    val hamming: Int,
    val decisionMargin: Double,
    val center: PixelPoint,
    val corners: List<PixelPoint>,
    val pose: Pose3? = null
) {
    val meanEdgePx: Double
        get() {
            if (corners.size != 4) return Double.NaN
            return corners.indices.map { i ->
                val a = corners[i]
                val b = corners[(i + 1) % corners.size]
                kotlin.math.hypot(a.x - b.x, a.y - b.y)
            }.average()
        }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("family", family)
        put("hamming", hamming)
        put("decision_margin", decisionMargin)
        put("center", JSONArray(listOf(center.x, center.y)))
        put("corners", JSONArray().apply { corners.forEach { put(JSONArray(listOf(it.x, it.y))) } })
        pose?.let { p ->
            put("pose", JSONObject().apply {
                put("t", JSONArray(listOf(p.translation.x, p.translation.y, p.translation.z)))
                put("R", JSONArray(p.rotation.toList()))
                p.error?.let { put("error", it) }
            })
        }
    }

    companion object {
        fun fromJson(json: JSONObject): AprilTagDetection {
            val c = json.getJSONArray("center")
            val corners = json.getJSONArray("corners")
            val p = json.optJSONObject("pose")
            return AprilTagDetection(
                id = json.getInt("id"),
                family = json.optString("family", AprilTagContractMetadata.DEFAULT_FAMILY),
                hamming = json.optInt("hamming", 0),
                decisionMargin = json.optDouble("decision_margin", Double.NaN),
                center = PixelPoint(c.getDouble(0), c.getDouble(1)),
                corners = (0 until corners.length()).map { i ->
                    val q = corners.getJSONArray(i)
                    PixelPoint(q.getDouble(0), q.getDouble(1))
                },
                pose = p?.let {
                    val t = it.getJSONArray("t")
                    val r = it.getJSONArray("R")
                    Pose3(
                        translation = Vec3(t.getDouble(0), t.getDouble(1), t.getDouble(2)),
                        rotation = DoubleArray(9) { index -> r.getDouble(index) },
                        error = if (it.has("error")) it.optDouble("error") else null
                    )
                }
            )
        }
    }
}

data class CameraIntrinsics(
    val fx: Double,
    val fy: Double,
    val cx: Double,
    val cy: Double,
    val width: Int,
    val height: Int,
    val source: String,
    val warning: String = ""
) {
    val usableForPose: Boolean
        get() = fx.isFinite() && fy.isFinite() && fx > 0.0 && fy > 0.0 &&
            cx.isFinite() && cy.isFinite() && width > 0 && height > 0

    fun toJson(): JSONObject = JSONObject()
        .put("fx_px", fx)
        .put("fy_px", fy)
        .put("cx_px", cx)
        .put("cy_px", cy)
        .put("width_px", width)
        .put("height_px", height)
        .put("source", source)
        .put("warning", warning)
}

data class AprilTagFrame(
    val detections: List<AprilTagDetection>,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val intrinsics: CameraIntrinsics?,
    val timestampIso: String = Instant.now().toString(),
    val backend: String = "AprilTag3 JNI",
    val error: String = ""
) {
    fun selected(preferredId: Int): AprilTagDetection? = when {
        preferredId >= 0 -> detections.firstOrNull { it.id == preferredId }
        else -> detections.maxByOrNull { it.decisionMargin.takeIf(Double::isFinite) ?: -1.0 }
    }

    fun detectionsJson(): String = JSONArray().apply { detections.forEach { put(it.toJson()) } }.toString()
}

data class PlanarPoint(val xMm: Double, val yMm: Double, val timeMs: Long) {
    fun toJson(): JSONObject = JSONObject().put("x_mm", xMm).put("y_mm", yMm).put("time_ms", timeMs)
}

data class PoseSample(val timeMs: Long, val position: Vec3, val poseError: Double?) {
    fun toJson(): JSONObject = JSONObject()
        .put("time_ms", timeMs)
        .put("x_m", position.x)
        .put("y_m", position.y)
        .put("z_m", position.z)
        .put("pose_error", poseError ?: JSONObject.NULL)
}

internal fun fmt(value: Double, decimals: Int = 3): String =
    if (!value.isFinite()) "" else String.format(Locale.US, "%.${decimals}f", value)

internal fun statusValues(
    ok: Boolean,
    result: String,
    family: String,
    warning: String = "",
    error: String = "",
    audit: JSONObject = JSONObject(),
    extra: Map<String, String> = emptyMap()
): LinkedHashMap<String, String> = linkedMapOf<String, String>().apply {
    put(AprilTagCommonFields.STATUS, if (ok) "succeeded" else "failed")
    put(AprilTagCommonFields.RESULT, result)
    put(AprilTagCommonFields.FAMILY, family)
    putAll(extra)
    put(AprilTagCommonFields.BACKEND, "AprilTag3 JNI")
    put(AprilTagCommonFields.CAPTURED_TIME_ISO, Instant.now().toString())
    put(AprilTagCommonFields.WARNING, warning)
    put(AprilTagCommonFields.ERROR, error)
    put(AprilTagCommonFields.AUDIT_JSON, audit.toString())
}

internal fun valuesToJson(values: Map<String, String>): String = JSONObject(values).toString()
internal fun valuesFromJson(json: String): Map<String, String> = runCatching {
    val o = JSONObject(json)
    o.keys().asSequence().associateWith { key -> o.optString(key, "") }
}.getOrDefault(emptyMap())
