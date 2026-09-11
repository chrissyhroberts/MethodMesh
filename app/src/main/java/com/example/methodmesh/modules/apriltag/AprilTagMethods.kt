package com.example.methodmesh.modules.apriltag

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
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.sqrt

abstract class AprilTagAs100Method(
    final override val id: String,
    displayName: String,
    description: String,
    private val outputs: List<String>,
    private val graphOutput: String
) : As100Method {
    final override val ref = ArchitectureRef(ArchitectureId(id), "Method", displayName)
    final override val descriptor = MethodDescriptor(
        id = ArchitectureId(id),
        methodType = MethodObjectType.SignalInterpreter,
        name = displayName,
        version = AprilTagContractMetadata.VERSION,
        description = description,
        outputs = outputs,
        graphOutputs = listOf(graphOutput),
        parameters = mapOf(
            "category" to "AprilTag",
            "status" to AprilTagContractMetadata.MATURITY,
            "connectivity" to AprilTagContractMetadata.CONNECTIVITY,
            "apriltag_backend" to "AprilTag 3",
            "default_family" to AprilTagContractMetadata.DEFAULT_FAMILY,
            "interaction_lifecycle" to "live_working_result_commit",
            "icon_key" to "tool"
        )
    )
    final override val contract = MethodContract(
        method = ref,
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs,
        producedGraphOutputs = descriptor.graphOutputs
    )

    final override fun request(
        action: String,
        context: Map<String, String>,
        signals: List<Signal>,
        inputs: List<ArchitectureRef>
    ): ExecutionRequest = As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)

    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult =
        result(
            request,
            failureValues("This experimental method requires the interactive camera surface."),
            InvocationContext.from(request.context)
        )

    fun result(request: ExecutionRequest, rawValues: Map<String, String>, invocation: InvocationContext?): ExecutionResult {
        val values = linkedMapOf<String, String>().apply {
            outputs.forEach { put(it, "") }
            putAll(rawValues.filterKeys { it in outputs })
        }
        val succeeded = values[AprilTagCommonFields.STATUS] == "succeeded"
        val provenance = ProvenanceContext(
            provider = "methodmesh.apriltag3",
            methodId = id,
            methodVersion = AprilTagContractMetadata.VERSION,
            operatorId = request.context["operator_id"]
        )
        val observation = Observation(
            phenomenon = graphOutput,
            values = values,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        val transformation = Transformation(
            action = id,
            method = ref,
            outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)),
            status = if (succeeded) TransformationStatus.Succeeded else TransformationStatus.Failed,
            temporalContext = request.temporalContext,
            provenance = provenance,
            diagnostics = mapOf(
                AprilTagCommonFields.WARNING to values[AprilTagCommonFields.WARNING].orEmpty(),
                AprilTagCommonFields.ERROR to values[AprilTagCommonFields.ERROR].orEmpty()
            ).filterValues { it.isNotBlank() }
        )
        return As100ExecutionEngine.complete(
            request = request,
            status = transformation.status,
            observations = listOf(observation),
            transformations = listOf(transformation),
            diagnostics = transformation.diagnostics
        ).withInvocationContext(invocation)
    }

    protected fun failureValues(message: String): Map<String, String> = statusValues(
        ok = false,
        result = "",
        family = AprilTagContractMetadata.DEFAULT_FAMILY,
        error = message,
        audit = JSONObject().put("method_id", id).put("method_version", AprilTagContractMetadata.VERSION).put("error", message)
    )
}

object As100AprilTagDetectMethod : AprilTagAs100Method(
    id = "apriltag.detect",
    displayName = "Detect AprilTag tags",
    description = "Detect and identify AprilTags in a live camera frame and return image-space geometry and detector evidence.",
    outputs = DetectFields.outputs,
    graphOutput = "apriltag.detection"
) {
    fun capture(frame: AprilTagFrame, targetTagId: Int, family: String): Map<String, String> {
        val selected = frame.selected(targetTagId)
        val ok = selected != null && frame.error.isBlank()
        val warning = when {
            frame.error.isNotBlank() -> ""
            selected == null && frame.detections.isNotEmpty() && targetTagId >= 0 -> "Requested tag $targetTagId was not visible."
            else -> ""
        }
        val audit = JSONObject()
            .put("method_id", id)
            .put("method_version", AprilTagContractMetadata.VERSION)
            .put("family", family)
            .put("requested_tag_id", targetTagId)
            .put("detected_tag_count", frame.detections.size)
            .put("image_width_px", frame.width)
            .put("image_height_px", frame.height)
            .put("detector_backend", frame.backend)
            .put("network_used", false)
        val multiTagMode = targetTagId < 0
        val resultText = when {
            !ok -> ""
            multiTagMode -> "${frame.detections.size} tag${if (frame.detections.size == 1) "" else "s"} · ${frame.detections.joinToString(", ") { "#${it.id}" }}"
            else -> selected?.let { "Tag ${it.id} · ${fmt(it.meanEdgePx, 1)} px · margin ${fmt(it.decisionMargin, 1)}" }.orEmpty()
        }
        audit.put("selection_mode", if (multiTagMode) "all_visible" else "single_target")
        return statusValues(
            ok = ok,
            result = resultText,
            family = family,
            warning = warning,
            error = frame.error.ifBlank { if (selected == null) if (multiTagMode) "No AprilTags are visible." else "No matching AprilTag is visible." else "" },
            audit = audit,
            extra = mapOf(
                "apriltag_tag_count" to frame.detections.size.toString(),
                "apriltag_tag_ids" to frame.detections.joinToString("|") { it.id.toString() },
                "apriltag_selected_tag_id" to selected?.id?.toString().orEmpty(),
                "apriltag_apparent_edge_px" to selected?.meanEdgePx?.let { fmt(it, 2) }.orEmpty(),
                "apriltag_center_x_px" to selected?.center?.x?.let { fmt(it, 2) }.orEmpty(),
                "apriltag_center_y_px" to selected?.center?.y?.let { fmt(it, 2) }.orEmpty(),
                "apriltag_decision_margin" to selected?.decisionMargin?.let { fmt(it, 3) }.orEmpty(),
                "apriltag_hamming" to selected?.hamming?.toString().orEmpty(),
                "apriltag_detections_json" to frame.detectionsJson(),
                "apriltag_image_width_px" to frame.width.toString(),
                "apriltag_image_height_px" to frame.height.toString()
            )
        )
    }
}

object As100AprilTagCalibrateFocalMethod : AprilTagAs100Method(
    id = "apriltag.calibrate_focal",
    displayName = "Calibrate AprilTag range",
    description = "Estimate pinhole focal length from repeated observations of a known-size tag at a known distance.",
    outputs = CalibrationFields.outputs,
    graphOutput = "apriltag.camera_calibration"
) {
    fun capture(
        tagId: Int,
        family: String,
        tagSizeMm: Double,
        knownDistanceMm: Double,
        edgeSamplesPx: List<Double>,
        imageWidth: Int,
        imageHeight: Int,
        requiredSamples: Int
    ): Map<String, String> {
        val mean = edgeSamplesPx.takeIf { it.isNotEmpty() }?.average() ?: Double.NaN
        val cv = AprilTagGeometry.coefficientOfVariation(edgeSamplesPx)
        val focal = if (mean.isFinite() && knownDistanceMm > 0 && tagSizeMm > 0) {
            AprilTagGeometry.calibrateFocalPx(mean, knownDistanceMm, tagSizeMm)
        } else Double.NaN
        val geometryValid = edgeSamplesPx.size >= requiredSamples.coerceAtLeast(3) && focal.isFinite() && cv.isFinite() && cv <= 0.05
        val warning = buildList {
            add("Approximate pinhole calibration assumes a fronto-parallel tag and does not estimate lens distortion.")
            if (cv.isFinite() && cv > 0.05) add("Edge-size variation exceeds 5%; repeat with a steadier, front-facing tag.")
        }.joinToString(" ")
        val profile = JSONObject()
            .put("fx_px", focal)
            .put("fy_px", focal)
            .put("cx_px", (imageWidth - 1) / 2.0)
            .put("cy_px", (imageHeight - 1) / 2.0)
            .put("width_px", imageWidth)
            .put("height_px", imageHeight)
            .put("source", "known_distance_single_tag")
            .put("tag_family", family)
            .put("tag_size_mm", tagSizeMm)
            .put("known_distance_mm", knownDistanceMm)
            .put("sample_count", edgeSamplesPx.size)
            .put("edge_cv", cv)
        val audit = JSONObject(profile.toString())
            .put("method_id", id)
            .put("method_version", AprilTagContractMetadata.VERSION)
            .put("geometry_valid", geometryValid)
            .put("assumptions", JSONArray(listOf("fronto_parallel_tag", "pinhole_camera", "principal_point_image_centre", "distortion_unmodelled")))
        return statusValues(
            ok = focal.isFinite() && edgeSamplesPx.isNotEmpty(),
            result = if (focal.isFinite()) "f ≈ ${fmt(focal, 1)} px · ${edgeSamplesPx.size} samples" else "",
            family = family,
            warning = warning,
            error = if (!focal.isFinite()) "Calibration could not be calculated." else "",
            audit = audit,
            extra = mapOf(
                "apriltag_calibration_tag_id" to tagId.toString(),
                "apriltag_tag_size_mm" to fmt(tagSizeMm, 2),
                "apriltag_known_distance_mm" to fmt(knownDistanceMm, 2),
                "apriltag_calibration_sample_count" to edgeSamplesPx.size.toString(),
                "apriltag_mean_edge_px" to fmt(mean, 3),
                "apriltag_edge_cv" to fmt(cv, 5),
                "apriltag_fx_px" to fmt(focal, 4),
                "apriltag_fy_px" to fmt(focal, 4),
                "apriltag_cx_px" to fmt((imageWidth - 1) / 2.0, 4),
                "apriltag_cy_px" to fmt((imageHeight - 1) / 2.0, 4),
                "apriltag_intrinsics_width_px" to imageWidth.toString(),
                "apriltag_intrinsics_height_px" to imageHeight.toString(),
                "apriltag_intrinsics_source" to "known_distance_single_tag",
                "apriltag_calibration_profile_json" to profile.toString(),
                AprilTagCommonFields.GEOMETRY_VALID to geometryValid.toString()
            )
        )
    }
}

object As100AprilTagRangePoseMethod : AprilTagAs100Method(
    id = "apriltag.range_pose",
    displayName = "Tag range and pose",
    description = "Estimate metric range and six-degree-of-freedom pose of a known-size AprilTag relative to the camera.",
    outputs = RangePoseFields.outputs,
    graphOutput = "apriltag.metric_pose"
) {
    fun capture(frame: AprilTagFrame, targetTagId: Int, tagSizeMm: Double, family: String): Map<String, String> {
        val det = frame.selected(targetTagId)
        val pose = det?.pose
        val euler = pose?.let { AprilTagGeometry.eulerDegrees(it.rotation) }
        val valid = pose != null && frame.intrinsics?.usableForPose == true && pose.translation.z > 0
        val warning = listOfNotNull(
            frame.intrinsics?.warning?.takeIf { it.isNotBlank() },
            if (frame.intrinsics?.source == "android_physical_estimate") "Use a calibrated/manual profile for quantitative work." else null
        ).joinToString(" ")
        val audit = JSONObject()
            .put("method_id", id)
            .put("method_version", AprilTagContractMetadata.VERSION)
            .put("tag_id", det?.id ?: JSONObject.NULL)
            .put("tag_size_mm", tagSizeMm)
            .put("intrinsics", frame.intrinsics?.toJson() ?: JSONObject.NULL)
            .put("pose_error", pose?.error ?: JSONObject.NULL)
            .put("geometry_valid", valid)
            .put("coordinate_frame", "camera: +x right, +y down, +z forward")
            .put("network_used", false)
        return statusValues(
            ok = valid,
            result = if (valid && pose != null) "Tag ${det!!.id} · ${fmt(AprilTagGeometry.distanceFromCamera(pose), 3)} m" else "",
            family = family,
            warning = warning,
            error = frame.error.ifBlank {
                when {
                    det == null -> "No matching AprilTag is visible."
                    frame.intrinsics?.usableForPose != true -> "Camera intrinsics are unavailable; select a manual calibration profile or use focal calibration."
                    pose == null -> "AprilTag pose estimation did not return a solution."
                    else -> "Metric pose is geometrically invalid."
                }
            },
            audit = audit,
            extra = mapOf(
                "apriltag_tag_id" to det?.id?.toString().orEmpty(),
                "apriltag_tag_size_mm" to fmt(tagSizeMm, 2),
                "apriltag_distance_m" to pose?.let { fmt(AprilTagGeometry.distanceFromCamera(it), 4) }.orEmpty(),
                "apriltag_x_right_m" to pose?.translation?.x?.let { fmt(it, 5) }.orEmpty(),
                "apriltag_y_down_m" to pose?.translation?.y?.let { fmt(it, 5) }.orEmpty(),
                "apriltag_z_forward_m" to pose?.translation?.z?.let { fmt(it, 5) }.orEmpty(),
                "apriltag_yaw_deg" to euler?.first?.let { fmt(it, 3) }.orEmpty(),
                "apriltag_pitch_deg" to euler?.second?.let { fmt(it, 3) }.orEmpty(),
                "apriltag_roll_deg" to euler?.third?.let { fmt(it, 3) }.orEmpty(),
                "apriltag_apparent_edge_px" to det?.meanEdgePx?.let { fmt(it, 2) }.orEmpty(),
                "apriltag_decision_margin" to det?.decisionMargin?.let { fmt(it, 3) }.orEmpty(),
                "apriltag_hamming" to det?.hamming?.toString().orEmpty(),
                "apriltag_pose_error" to pose?.error?.let { fmt(it, 8) }.orEmpty(),
                "apriltag_intrinsics_source" to frame.intrinsics?.source.orEmpty(),
                "apriltag_fx_px" to frame.intrinsics?.fx?.let { fmt(it, 4) }.orEmpty(),
                "apriltag_fy_px" to frame.intrinsics?.fy?.let { fmt(it, 4) }.orEmpty(),
                "apriltag_cx_px" to frame.intrinsics?.cx?.let { fmt(it, 4) }.orEmpty(),
                "apriltag_cy_px" to frame.intrinsics?.cy?.let { fmt(it, 4) }.orEmpty(),
                "apriltag_image_width_px" to frame.width.toString(),
                "apriltag_image_height_px" to frame.height.toString(),
                AprilTagCommonFields.GEOMETRY_VALID to valid.toString()
            )
        )
    }
}

object As100AprilTagRelativePoseMethod : AprilTagAs100Method(
    id = "apriltag.relative_pose",
    displayName = "Relative tag pose",
    description = "Measure one tagged rigid frame relative to another tag visible in the same camera frame.",
    outputs = RelativePoseFields.outputs,
    graphOutput = "apriltag.relative_pose"
) {
    fun capture(frame: AprilTagFrame, referenceTagId: Int, targetTagId: Int, tagSizeMm: Double, family: String): Map<String, String> {
        val ref = frame.detections.firstOrNull { it.id == referenceTagId }
        val target = frame.detections.firstOrNull { it.id == targetTagId }
        val relative = if (ref?.pose != null && target?.pose != null) AprilTagGeometry.relativePose(ref.pose, target.pose) else null
        val euler = relative?.let { AprilTagGeometry.eulerDegrees(it.rotation) }
        val valid = relative != null && frame.intrinsics?.usableForPose == true
        val audit = JSONObject()
            .put("method_id", id)
            .put("method_version", AprilTagContractMetadata.VERSION)
            .put("reference_tag_id", referenceTagId)
            .put("target_tag_id", targetTagId)
            .put("tag_size_mm", tagSizeMm)
            .put("intrinsics", frame.intrinsics?.toJson() ?: JSONObject.NULL)
            .put("geometry_valid", valid)
            .put("coordinate_frame", "reference tag frame")
        val error = frame.error.ifBlank {
            when {
                referenceTagId == targetTagId -> "Reference and target tag IDs must differ."
                ref == null -> "Reference tag $referenceTagId is not visible."
                target == null -> "Target tag $targetTagId is not visible."
                ref.pose == null || target.pose == null -> "Metric pose is unavailable for one or both tags."
                else -> "Relative pose is unavailable."
            }
        }
        return statusValues(
            ok = valid && referenceTagId != targetTagId,
            result = if (valid && relative != null) "Tag $targetTagId from $referenceTagId · ${fmt(AprilTagGeometry.distanceFromCamera(relative), 3)} m" else "",
            family = family,
            warning = frame.intrinsics?.warning.orEmpty(),
            error = if (valid && referenceTagId != targetTagId) "" else error,
            audit = audit,
            extra = mapOf(
                "apriltag_reference_tag_id" to referenceTagId.toString(),
                "apriltag_target_tag_id" to targetTagId.toString(),
                "apriltag_tag_size_mm" to fmt(tagSizeMm, 2),
                "apriltag_relative_x_m" to relative?.translation?.x?.let { fmt(it, 5) }.orEmpty(),
                "apriltag_relative_y_m" to relative?.translation?.y?.let { fmt(it, 5) }.orEmpty(),
                "apriltag_relative_z_m" to relative?.translation?.z?.let { fmt(it, 5) }.orEmpty(),
                "apriltag_relative_distance_m" to relative?.let { fmt(AprilTagGeometry.distanceFromCamera(it), 5) }.orEmpty(),
                "apriltag_relative_yaw_deg" to euler?.first?.let { fmt(it, 3) }.orEmpty(),
                "apriltag_relative_pitch_deg" to euler?.second?.let { fmt(it, 3) }.orEmpty(),
                "apriltag_relative_roll_deg" to euler?.third?.let { fmt(it, 3) }.orEmpty(),
                "apriltag_reference_pose_error" to ref?.pose?.error?.let { fmt(it, 8) }.orEmpty(),
                "apriltag_target_pose_error" to target?.pose?.error?.let { fmt(it, 8) }.orEmpty(),
                "apriltag_intrinsics_source" to frame.intrinsics?.source.orEmpty(),
                AprilTagCommonFields.GEOMETRY_VALID to valid.toString()
            )
        )
    }
}

object As100AprilTagPlanarMeasureMethod : AprilTagAs100Method(
    id = "apriltag.planar_measure",
    displayName = "Planar AprilTag measurement",
    description = "Map tapped image points into millimetres on a tagged plane for points, distances, areas, and manual trajectories.",
    outputs = PlanarMeasureFields.outputs,
    graphOutput = "apriltag.planar_measurement"
) {
    fun capture(
        referenceTagId: Int,
        family: String,
        tagSizeMm: Double,
        mode: String,
        referenceFrame: String,
        points: List<PlanarPoint>,
        warning: String = ""
    ): Map<String, String> {
        val point = points.lastOrNull()
        val distance = if (points.size >= 2) kotlin.math.hypot(points[1].xMm - points[0].xMm, points[1].yMm - points[0].yMm) else Double.NaN
        val area = if (points.size >= 3) AprilTagGeometry.polygonArea(points) else Double.NaN
        val perimeter = if (points.size >= 3) AprilTagGeometry.polygonPerimeter(points) else Double.NaN
        val path = if (points.size >= 2) AprilTagGeometry.pathLength2d(points) else 0.0
        val duration = if (points.size >= 2) (points.last().timeMs - points.first().timeMs) / 1000.0 else 0.0
        val meanSpeed = if (duration > 0) path / duration else 0.0
        val valid = when (mode) {
            "point" -> points.isNotEmpty()
            "distance" -> points.size >= 2
            "area" -> points.size >= 3
            "trajectory" -> points.size >= 2
            else -> false
        }
        val result = when (mode) {
            "point" -> point?.let { "x ${fmt(it.xMm, 1)} mm · y ${fmt(it.yMm, 1)} mm" }.orEmpty()
            "distance" -> if (distance.isFinite()) "${fmt(distance, 1)} mm" else ""
            "area" -> if (area.isFinite()) "${fmt(area, 1)} mm²" else ""
            "trajectory" -> if (valid) "${fmt(path, 1)} mm path · ${fmt(duration, 1)} s" else ""
            else -> ""
        }
        val pointsJson = JSONArray().apply { points.forEach { put(it.toJson()) } }.toString()
        val audit = JSONObject()
            .put("method_id", id)
            .put("method_version", AprilTagContractMetadata.VERSION)
            .put("reference_tag_id", referenceTagId)
            .put("tag_size_mm", tagSizeMm)
            .put("measurement_mode", mode)
            .put("reference_frame", referenceFrame)
            .put("point_count", points.size)
            .put("geometry_valid", valid)
            .put("assumption", "all tapped points lie on the same physical plane as the reference tag/layout")
        return statusValues(
            ok = valid,
            result = result,
            family = family,
            warning = warning,
            error = if (valid) "" else "Not enough valid planar points have been captured for $mode.",
            audit = audit,
            extra = mapOf(
                "apriltag_reference_tag_id" to referenceTagId.toString(),
                "apriltag_tag_size_mm" to fmt(tagSizeMm, 2),
                "apriltag_measurement_mode" to mode,
                "apriltag_reference_frame" to referenceFrame,
                "apriltag_point_count" to points.size.toString(),
                "apriltag_point_x_mm" to point?.xMm?.let { fmt(it, 3) }.orEmpty(),
                "apriltag_point_y_mm" to point?.yMm?.let { fmt(it, 3) }.orEmpty(),
                "apriltag_distance_mm" to distance.takeIf { it.isFinite() }?.let { fmt(it, 3) }.orEmpty(),
                "apriltag_area_mm2" to area.takeIf { it.isFinite() }?.let { fmt(it, 3) }.orEmpty(),
                "apriltag_perimeter_mm" to perimeter.takeIf { it.isFinite() }?.let { fmt(it, 3) }.orEmpty(),
                "apriltag_path_length_mm" to fmt(path, 3),
                "apriltag_duration_s" to fmt(duration, 3),
                "apriltag_mean_speed_mm_s" to fmt(meanSpeed, 3),
                "apriltag_points_json" to pointsJson,
                "apriltag_assumption" to "coplanar_with_reference",
                AprilTagCommonFields.GEOMETRY_VALID to valid.toString()
            )
        )
    }
}

object As100AprilTagTrackPoseMethod : AprilTagAs100Method(
    id = "apriltag.track_pose",
    displayName = "Track tagged object",
    description = "Record a moving tag through time in a fixed camera frame or relative to a simultaneously visible fixed reference tag.",
    outputs = TrackPoseFields.outputs,
    graphOutput = "apriltag.pose_trajectory"
) {
    fun capture(
        movingTagId: Int,
        referenceTagId: Int,
        family: String,
        tagSizeMm: Double,
        referenceFrame: String,
        intrinsicsSource: String,
        samples: List<PoseSample>,
        warning: String
    ): Map<String, String> {
        val valid = samples.size >= 2
        val duration = if (valid) (samples.last().timeMs - samples.first().timeMs) / 1000.0 else 0.0
        val path = AprilTagGeometry.pathLength3d(samples)
        val displacement = if (valid) samples.first().position.distanceTo(samples.last().position) else 0.0
        val meanSpeed = if (duration > 0) path / duration else 0.0
        val maxSpeed = AprilTagGeometry.maxSpeed3d(samples)
        val json = JSONArray().apply { samples.forEach { put(it.toJson()) } }.toString()
        val audit = JSONObject()
            .put("method_id", id)
            .put("method_version", AprilTagContractMetadata.VERSION)
            .put("moving_tag_id", movingTagId)
            .put("reference_tag_id", if (referenceTagId >= 0) referenceTagId else JSONObject.NULL)
            .put("reference_frame", referenceFrame)
            .put("sample_count", samples.size)
            .put("intrinsics_source", intrinsicsSource)
            .put("geometry_valid", valid)
        val start = samples.firstOrNull()?.position
        val end = samples.lastOrNull()?.position
        return statusValues(
            ok = valid,
            result = if (valid) "${samples.size} samples · ${fmt(path, 3)} m path · ${fmt(duration, 2)} s" else "",
            family = family,
            warning = warning,
            error = if (valid) "" else "At least two valid pose samples are required.",
            audit = audit,
            extra = mapOf(
                "apriltag_moving_tag_id" to movingTagId.toString(),
                "apriltag_reference_tag_id" to if (referenceTagId >= 0) referenceTagId.toString() else "",
                "apriltag_tag_size_mm" to fmt(tagSizeMm, 2),
                "apriltag_reference_frame" to referenceFrame,
                "apriltag_sample_count" to samples.size.toString(),
                "apriltag_duration_s" to fmt(duration, 4),
                "apriltag_path_length_m" to fmt(path, 5),
                "apriltag_displacement_m" to fmt(displacement, 5),
                "apriltag_mean_speed_m_s" to fmt(meanSpeed, 5),
                "apriltag_max_speed_m_s" to fmt(maxSpeed, 5),
                "apriltag_start_x_m" to start?.x?.let { fmt(it, 5) }.orEmpty(),
                "apriltag_start_y_m" to start?.y?.let { fmt(it, 5) }.orEmpty(),
                "apriltag_start_z_m" to start?.z?.let { fmt(it, 5) }.orEmpty(),
                "apriltag_end_x_m" to end?.x?.let { fmt(it, 5) }.orEmpty(),
                "apriltag_end_y_m" to end?.y?.let { fmt(it, 5) }.orEmpty(),
                "apriltag_end_z_m" to end?.z?.let { fmt(it, 5) }.orEmpty(),
                "apriltag_samples_json" to json,
                "apriltag_intrinsics_source" to intrinsicsSource,
                AprilTagCommonFields.GEOMETRY_VALID to valid.toString()
            )
        )
    }
}
