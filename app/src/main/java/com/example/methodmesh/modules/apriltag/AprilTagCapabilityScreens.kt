package com.example.methodmesh.modules.apriltag

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.transport.workflow.ui.CapabilityHostPresentation
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import org.json.JSONArray
import org.json.JSONObject

private val supportedFamilies = listOf(
    "tagStandard41h12", "tag36h11", "tag25h9", "tag16h5",
    "tagCircle21h7", "tagCircle49h12", "tagStandard52h13"
)

private class AprilTagCapabilityScreenSpec(
    override val capabilityId: String,
    override val title: String,
    override val description: String
) : CapabilityScreenSpec {
    override val hostPresentation = CapabilityHostPresentation.Standard

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val method = aprilTagMethod(capabilityId)
        var committedValuesJson by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
        val committedValues = remember(committedValuesJson) { valuesFromJson(committedValuesJson) }
        val committedResult = remember(committedValuesJson) {
            if (committedValuesJson.isBlank()) null else method.result(
                request = method.request(
                    action = capabilityId,
                    context = context.request.invocationContext.asMap(capabilityId) + context.action.settings,
                    signals = emptyList(),
                    inputs = emptyList()
                ),
                rawValues = committedValues,
                invocation = context.request.invocationContext
            )
        }
        val preview = committedValues.filterKeys { it in primaryPreviewKeys(capabilityId) }

        CapabilityScreenScaffold(
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = committedResult,
            resultPreview = preview,
            onBack = onBack,
            onRetry = { committedValuesJson = "" },
            onConfirm = { committedResult?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            when (capabilityId) {
                As100AprilTagDetectMethod.id -> DetectInstrument(context) { committedValuesJson = valuesToJson(it) }
                As100AprilTagCalibrateFocalMethod.id -> CalibrationInstrument(context) { committedValuesJson = valuesToJson(it) }
                As100AprilTagRangePoseMethod.id -> RangePoseInstrument(context) { committedValuesJson = valuesToJson(it) }
                As100AprilTagRelativePoseMethod.id -> RelativePoseInstrument(context) { committedValuesJson = valuesToJson(it) }
                As100AprilTagPlanarMeasureMethod.id -> PlanarMeasureInstrument(context) { committedValuesJson = valuesToJson(it) }
                As100AprilTagTrackPoseMethod.id -> TrackPoseInstrument(context) { committedValuesJson = valuesToJson(it) }
            }
        }
    }
}

object AprilTagDetectCapabilityScreen : CapabilityScreenSpec by AprilTagCapabilityScreenSpec(
    As100AprilTagDetectMethod.id, "Detect AprilTag tags", "Identify AprilTags and inspect image-space detection evidence."
)
object AprilTagCalibrateCapabilityScreen : CapabilityScreenSpec by AprilTagCapabilityScreenSpec(
    As100AprilTagCalibrateFocalMethod.id, "Calibrate AprilTag range", "Estimate a practical pinhole focal length from a known-distance tag."
)
object AprilTagRangePoseCapabilityScreen : CapabilityScreenSpec by AprilTagCapabilityScreenSpec(
    As100AprilTagRangePoseMethod.id, "Tag range and pose", "Measure range and six-degree-of-freedom pose from a known-size tag."
)
object AprilTagRelativePoseCapabilityScreen : CapabilityScreenSpec by AprilTagCapabilityScreenSpec(
    As100AprilTagRelativePoseMethod.id, "Relative tag pose", "Measure a target tag in the coordinate frame of a reference tag."
)
object AprilTagPlanarMeasureCapabilityScreen : CapabilityScreenSpec by AprilTagCapabilityScreenSpec(
    As100AprilTagPlanarMeasureMethod.id, "Planar AprilTag measurement", "Tap points on a tagged plane to capture metric positions, lengths, areas, or trajectories."
)
object AprilTagTrackPoseCapabilityScreen : CapabilityScreenSpec by AprilTagCapabilityScreenSpec(
    As100AprilTagTrackPoseMethod.id, "Track tagged object", "Record a tagged rigid object's movement through time."
)

private fun aprilTagMethod(id: String): AprilTagAs100Method = when (id) {
    As100AprilTagDetectMethod.id -> As100AprilTagDetectMethod
    As100AprilTagCalibrateFocalMethod.id -> As100AprilTagCalibrateFocalMethod
    As100AprilTagRangePoseMethod.id -> As100AprilTagRangePoseMethod
    As100AprilTagRelativePoseMethod.id -> As100AprilTagRelativePoseMethod
    As100AprilTagPlanarMeasureMethod.id -> As100AprilTagPlanarMeasureMethod
    As100AprilTagTrackPoseMethod.id -> As100AprilTagTrackPoseMethod
    else -> error("Unknown AprilTag capability $id")
}

private fun primaryPreviewKeys(id: String): Set<String> = when (id) {
    As100AprilTagDetectMethod.id -> setOf(AprilTagCommonFields.RESULT, "apriltag_selected_tag_id", "apriltag_apparent_edge_px", "apriltag_decision_margin", AprilTagCommonFields.WARNING)
    As100AprilTagCalibrateFocalMethod.id -> setOf(AprilTagCommonFields.RESULT, "apriltag_fx_px", "apriltag_fy_px", "apriltag_edge_cv", AprilTagCommonFields.GEOMETRY_VALID, AprilTagCommonFields.WARNING)
    As100AprilTagRangePoseMethod.id -> setOf(AprilTagCommonFields.RESULT, "apriltag_distance_m", "apriltag_x_right_m", "apriltag_y_down_m", "apriltag_z_forward_m", "apriltag_pose_error", AprilTagCommonFields.WARNING)
    As100AprilTagRelativePoseMethod.id -> setOf(AprilTagCommonFields.RESULT, "apriltag_relative_x_m", "apriltag_relative_y_m", "apriltag_relative_z_m", "apriltag_relative_distance_m", AprilTagCommonFields.WARNING)
    As100AprilTagPlanarMeasureMethod.id -> setOf(AprilTagCommonFields.RESULT, "apriltag_point_x_mm", "apriltag_point_y_mm", "apriltag_distance_mm", "apriltag_area_mm2", "apriltag_path_length_mm", AprilTagCommonFields.WARNING)
    else -> setOf(AprilTagCommonFields.RESULT, "apriltag_sample_count", "apriltag_path_length_m", "apriltag_displacement_m", "apriltag_mean_speed_m_s", AprilTagCommonFields.WARNING)
}

@Composable
private fun DetectInstrument(context: CapabilityScreenContext, onCommit: (Map<String, String>) -> Unit) {
    val state = rememberAprilTagSettings(context, includeIntrinsics = false)
    var frame by remember { mutableStateOf<AprilTagFrame?>(null) }
    var tapMessage by rememberSaveable { mutableStateOf("") }
    val targetId = state.targetTagId.toIntOrNull() ?: -1
    val selected = frame?.selected(targetId)
    val allMode = targetId < 0
    val ids = frame?.detections?.map { it.id }.orEmpty()
    val hud = AprilTagHud(
        roles = if (!allMode && selected != null) mapOf(selected.id to AprilTagHudRole.TARGET) else emptyMap(),
        banner = if (allMode) "Detect all visible tags" else "Target tag $targetId",
        lines = listOf(
            "Visible: ${ids.size}${if (ids.isEmpty()) "" else " · ${ids.joinToString(", ")}"}",
            selected?.let { "Best/target: #${it.id} · edge ${fmt(it.meanEdgePx, 1)} px" }.orEmpty(),
            selected?.let { "Margin ${fmt(it.decisionMargin, 1)} · hamming ${it.hamming}" }.orEmpty(),
            tapMessage
        )
    )
    CameraInstrument(
        settings = state.cameraSettings(),
        frame = frame,
        onFrame = { frame = it },
        hud = hud,
        onTap = { point ->
            val hit = frame?.let { AprilTagGeometry.detectionAt(point, it.detections) }
            if (hit == null) tapMessage = "Tap directly on a detected tag." else {
                state.targetTagId = hit.id.toString()
                tapMessage = "Target set to tag ${hit.id}."
            }
        }
    )
    Guidance(
        "Detect one or many",
        "With Target ID = -1, Commit captures every visible tag in apriltag_detections_json and apriltag_tag_ids. Tap a tag in the image to switch to a single-tag target."
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = { state.targetTagId = "-1"; tapMessage = "All visible tags selected." },
            modifier = Modifier.weight(1f)
        ) { Text("All visible") }
        Button(
            onClick = { frame?.let { onCommit(As100AprilTagDetectMethod.capture(it, targetId, state.family)) } },
            enabled = if (allMode) frame?.detections?.isNotEmpty() == true else selected != null,
            modifier = Modifier.weight(1f)
        ) { Text(if (allMode) "Commit tags" else "Commit tag") }
    }
    SettingsDivider()
    BasicDetectorSettings(state, showTagSize = false, targetMeaning = "-1 = all visible")
}

@Composable
private fun CalibrationInstrument(context: CapabilityScreenContext, onCommit: (Map<String, String>) -> Unit) {
    val state = rememberAprilTagSettings(context, includeIntrinsics = false)
    var knownDistance by rememberSaveable { mutableStateOf(context.value(AprilTagInputs.KNOWN_DISTANCE_MM) ?: "1000") }
    var requiredSamples by rememberSaveable { mutableStateOf(context.value(AprilTagInputs.CALIBRATION_SAMPLES) ?: "15") }
    var samplesJson by rememberSaveable { mutableStateOf("[]") }
    var collecting by rememberSaveable { mutableStateOf(false) }
    var lastAccepted by rememberSaveable { mutableStateOf(0L) }
    var frame by remember { mutableStateOf<AprilTagFrame?>(null) }
    var calibrationTagId by rememberSaveable { mutableStateOf(-1) }
    var sampleStatus by rememberSaveable { mutableStateOf("Show the tag front-on, then start sampling.") }
    val samples = remember(samplesJson) { jsonDoubles(samplesJson) }
    val required = requiredSamples.toIntOrNull()?.coerceIn(3, 100) ?: 15
    val targetId = state.targetTagId.toIntOrNull() ?: -1
    val effectiveTarget = if (targetId >= 0) targetId else calibrationTagId
    val currentDet = frame?.selected(effectiveTarget)
    val cv = AprilTagGeometry.coefficientOfVariation(samples)
    val hud = AprilTagHud(
        roles = currentDet?.let { mapOf(it.id to AprilTagHudRole.TARGET) }.orEmpty(),
        banner = if (collecting) "CALIBRATING · ${samples.size}/$required" else "Calibration ready",
        lines = listOf(
            currentDet?.let { "Tag #${it.id} · edge ${fmt(it.meanEdgePx, 1)} px" }.orEmpty(),
            if (samples.isNotEmpty()) "Mean ${fmt(samples.average(), 1)} px · CV ${fmt(cv, 4)}" else "",
            sampleStatus
        )
    )

    CameraInstrument(
        settings = state.cameraSettings(tagSizeMmOverride = 0.0),
        frame = frame,
        onFrame = { incoming ->
            frame = incoming
            if (collecting && samples.size < required) {
                val lockedTarget = if (targetId >= 0) targetId else calibrationTagId
                val det = incoming.selected(lockedTarget)
                val now = System.currentTimeMillis()
                when {
                    det == null -> sampleStatus = "Waiting: calibration tag is not visible."
                    det.hamming != 0 -> sampleStatus = "Waiting: decode error correction is non-zero."
                    !det.meanEdgePx.isFinite() -> sampleStatus = "Waiting: edge measurement is unstable."
                    edgeShapeCv(det) > 0.08 -> sampleStatus = "Waiting: hold the tag more front-facing."
                    now - lastAccepted < 100 -> Unit
                    else -> {
                        if (calibrationTagId < 0) calibrationTagId = det.id
                        samplesJson = JSONArray(samples + det.meanEdgePx).toString()
                        lastAccepted = now
                        sampleStatus = "Accepted sample ${samples.size + 1} of $required."
                        if (samples.size + 1 >= required) collecting = false
                    }
                }
            }
        },
        hud = hud,
        onTap = { point ->
            val hit = frame?.let { AprilTagGeometry.detectionAt(point, it.detections) }
            if (hit != null && !collecting) {
                state.targetTagId = hit.id.toString()
                calibrationTagId = hit.id
                sampleStatus = "Calibration tag set to #${hit.id}."
            }
        }
    )
    Guidance(
        "Known-distance calibration",
        "Measure the camera optical centre to the tag plane. Keep one tag front-facing and still. Tap it to lock the tag, then collect repeated observations."
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!collecting) Button(onClick = {
            if (samples.size >= required) { samplesJson = "[]"; calibrationTagId = -1 }
            collecting = true
            sampleStatus = "Sampling… keep tag still and front-facing."
        }, modifier = Modifier.weight(1f)) { Text(if (samples.isEmpty()) "Start sampling" else "Resume") }
        else OutlinedButton(onClick = { collecting = false; sampleStatus = "Paused." }, modifier = Modifier.weight(1f)) { Text("Pause") }
        OutlinedButton(onClick = { samplesJson = "[]"; collecting = false; calibrationTagId = -1; sampleStatus = "Cleared." }, modifier = Modifier.weight(1f)) { Text("Clear") }
    }
    Button(
        onClick = {
            val f = frame ?: return@Button
            val id = if (targetId >= 0) targetId else calibrationTagId
            val det = f.selected(id) ?: return@Button
            onCommit(As100AprilTagCalibrateFocalMethod.capture(
                tagId = det.id,
                family = state.family,
                tagSizeMm = state.tagSizeMm.toDoubleOrNull() ?: 100.0,
                knownDistanceMm = knownDistance.toDoubleOrNull() ?: 1000.0,
                edgeSamplesPx = samples,
                imageWidth = f.width,
                imageHeight = f.height,
                requiredSamples = required
            ))
        },
        enabled = samples.size >= required && currentDet != null,
        modifier = Modifier.fillMaxWidth()
    ) { Text("Commit calibration") }
    if (samples.size < required) Text("Need ${required - samples.size} more accepted sample${if (required - samples.size == 1) "" else "s"} before Commit.", style = MaterialTheme.typography.bodySmall)
    SettingsDivider()
    BasicDetectorSettings(state, showTagSize = true, targetMeaning = "-1 = tap/lock first suitable tag")
    NumericField("Known distance (mm)", knownDistance) { knownDistance = numeric(it) }
    NumericField("Samples", requiredSamples) { requiredSamples = integer(it) }
    LaunchedEffect(knownDistance, requiredSamples) {
        context.onSettingsChanged(mapOf(AprilTagInputs.KNOWN_DISTANCE_MM to knownDistance, AprilTagInputs.CALIBRATION_SAMPLES to requiredSamples))
    }
}

@Composable
private fun RangePoseInstrument(context: CapabilityScreenContext, onCommit: (Map<String, String>) -> Unit) {
    val state = rememberAprilTagSettings(context, includeIntrinsics = true)
    var frame by remember { mutableStateOf<AprilTagFrame?>(null) }
    var tapMessage by rememberSaveable { mutableStateOf("") }
    val targetId = state.targetTagId.toIntOrNull() ?: -1
    val det = frame?.selected(targetId)
    val pose = det?.pose
    val euler = pose?.let { AprilTagGeometry.eulerDegrees(it.rotation) }
    val roles = det?.let { mapOf(it.id to AprilTagHudRole.TARGET) }.orEmpty()
    val hud = AprilTagHud(
        roles = roles,
        banner = det?.let { "TAG ${it.id} · LIVE POSE" } ?: "Range + pose",
        lines = listOf(
            pose?.let { "Range ${fmt(AprilTagGeometry.distanceFromCamera(it), 3)} m" } ?: if (det != null) "Tag detected; metric pose unavailable" else "Aim at a tag",
            pose?.let { "XYZ ${fmt(it.translation.x, 3)} · ${fmt(it.translation.y, 3)} · ${fmt(it.translation.z, 3)} m" }.orEmpty(),
            euler?.let { "Yaw ${fmt(it.first, 1)}° · pitch ${fmt(it.second, 1)}° · roll ${fmt(it.third, 1)}°" }.orEmpty(),
            det?.let { "Edge ${fmt(it.meanEdgePx, 0)} px · margin ${fmt(it.decisionMargin, 1)}" }.orEmpty(),
            tapMessage
        )
    )
    CameraInstrument(
        settings = state.cameraSettings(),
        frame = frame,
        onFrame = { frame = it },
        hud = hud,
        onTap = { point ->
            val hit = frame?.let { AprilTagGeometry.detectionAt(point, it.detections) }
            if (hit == null) tapMessage = "Tap directly on a detected tag." else {
                state.targetTagId = hit.id.toString()
                tapMessage = "Target locked to tag ${hit.id}."
            }
        }
    )
    Guidance(
        "Aim, inspect, Commit",
        "All visible tags are outlined. Tap a tag to make it the target. Live range, XYZ and orientation stay in the HUD; Commit freezes the current solution. Oblique/small tags are harder to solve, so the default detector now uses full-resolution quads."
    )
    Button(
        onClick = { frame?.let { onCommit(As100AprilTagRangePoseMethod.capture(it, targetId, state.tagSizeMm.toDoubleOrNull() ?: 100.0, state.family)) } },
        enabled = pose != null,
        modifier = Modifier.fillMaxWidth()
    ) { Text("Commit range and pose") }
    if (det == null) Text("Commit unavailable: target tag is not visible.", style = MaterialTheme.typography.bodySmall)
    else if (pose == null) Text("Commit unavailable: tag is detected but a metric pose solution is not currently available. Check tag size/camera model and reduce angle or distance.", style = MaterialTheme.typography.bodySmall)
    SettingsDivider()
    BasicDetectorSettings(state, showTagSize = true, targetMeaning = "-1 = strongest visible; or tap a tag")
    IntrinsicsSettings(state)
}

@Composable
private fun RelativePoseInstrument(context: CapabilityScreenContext, onCommit: (Map<String, String>) -> Unit) {
    val state = rememberAprilTagSettings(context, includeIntrinsics = true)
    var referenceId by rememberSaveable { mutableStateOf(context.value(AprilTagInputs.REFERENCE_TAG_ID) ?: "0") }
    var targetId by rememberSaveable { mutableStateOf(context.value(AprilTagInputs.TARGET_TAG_ID) ?: "1") }
    var pickRole by rememberSaveable { mutableStateOf("target") }
    var tapMessage by rememberSaveable { mutableStateOf("Tap Target or Reference, then tap a visible tag.") }
    var frame by remember { mutableStateOf<AprilTagFrame?>(null) }
    val refInt = referenceId.toIntOrNull() ?: 0
    val targetInt = targetId.toIntOrNull() ?: 1
    val ref = frame?.detections?.firstOrNull { it.id == refInt }
    val target = frame?.detections?.firstOrNull { it.id == targetInt }
    val relative = if (ref?.pose != null && target?.pose != null) AprilTagGeometry.relativePose(ref.pose, target.pose) else null
    val euler = relative?.let { AprilTagGeometry.eulerDegrees(it.rotation) }
    val hud = AprilTagHud(
        roles = buildMap {
            if (ref != null) put(ref.id, AprilTagHudRole.REFERENCE)
            if (target != null) put(target.id, AprilTagHudRole.TARGET)
        },
        banner = "REF #$refInt → TARGET #$targetInt",
        lines = listOf(
            "Reference ${if (ref?.pose != null) "✓" else "not ready"} · Target ${if (target?.pose != null) "✓" else "not ready"}",
            relative?.let { "Separation ${fmt(AprilTagGeometry.distanceFromCamera(it), 3)} m" }.orEmpty(),
            relative?.let { "XYZ ${fmt(it.translation.x, 3)} · ${fmt(it.translation.y, 3)} · ${fmt(it.translation.z, 3)} m" }.orEmpty(),
            euler?.let { "Yaw ${fmt(it.first, 1)}° · pitch ${fmt(it.second, 1)}° · roll ${fmt(it.third, 1)}°" }.orEmpty(),
            "Tap assigns: ${pickRole.uppercase()}",
            tapMessage
        )
    )
    CameraInstrument(
        settings = state.cameraSettings(),
        frame = frame,
        onFrame = { frame = it },
        hud = hud,
        onTap = { point ->
            val hit = frame?.let { AprilTagGeometry.detectionAt(point, it.detections) }
            if (hit == null) tapMessage = "No detected tag at that point." else if (pickRole == "reference") {
                if (hit.id == targetInt) tapMessage = "Reference and target must be different tags."
                else { referenceId = hit.id.toString(); tapMessage = "Reference set to tag ${hit.id}." }
            } else {
                if (hit.id == refInt) tapMessage = "Reference and target must be different tags."
                else { targetId = hit.id.toString(); tapMessage = "Target set to tag ${hit.id}." }
            }
        }
    )
    Guidance(
        "Measure one tag from another",
        "The reference tag defines the coordinate system. Keep both tags visible at the same time. Choose which role you are assigning, then tap the tag in the live image."
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { pickRole = "reference"; tapMessage = "Tap the fixed/reference tag." }, modifier = Modifier.weight(1f)) { Text(if (pickRole == "reference") "✓ Reference" else "Set reference") }
        OutlinedButton(onClick = { pickRole = "target"; tapMessage = "Tap the target tag." }, modifier = Modifier.weight(1f)) { Text(if (pickRole == "target") "✓ Target" else "Set target") }
    }
    Button(
        onClick = { frame?.let { onCommit(As100AprilTagRelativePoseMethod.capture(it, refInt, targetInt, state.tagSizeMm.toDoubleOrNull() ?: 100.0, state.family)) } },
        enabled = relative != null && refInt != targetInt,
        modifier = Modifier.fillMaxWidth()
    ) { Text("Commit relative pose") }
    if (relative == null) Text("Commit requires simultaneous metric pose for both configured tags.", style = MaterialTheme.typography.bodySmall)
    SettingsDivider()
    NumericField("Reference tag ID", referenceId) { referenceId = integer(it) }
    NumericField("Target tag ID", targetId) { targetId = integer(it) }
    BasicDetectorSettings(state, showTagSize = true, showTargetId = false)
    IntrinsicsSettings(state)
    LaunchedEffect(referenceId, targetId) { context.onSettingsChanged(mapOf(AprilTagInputs.REFERENCE_TAG_ID to referenceId, AprilTagInputs.TARGET_TAG_ID to targetId)) }
}

@Composable
private fun PlanarMeasureInstrument(context: CapabilityScreenContext, onCommit: (Map<String, String>) -> Unit) {
    val state = rememberAprilTagSettings(context, includeIntrinsics = false)
    var mode by rememberSaveable { mutableStateOf(context.value(AprilTagInputs.MEASUREMENT_MODE) ?: "point") }
    var layoutJson by rememberSaveable { mutableStateOf(context.value(AprilTagInputs.ANCHOR_LAYOUT_JSON) ?: "") }
    var pointsJson by rememberSaveable { mutableStateOf("[]") }
    var frame by remember { mutableStateOf<AprilTagFrame?>(null) }
    var referenceFrame by rememberSaveable { mutableStateOf("") }
    var activeReferenceTagId by rememberSaveable { mutableStateOf(-1) }
    var tapStatus by rememberSaveable { mutableStateOf("Show the reference tag, then tap on the physical plane.") }
    val points = remember(pointsJson) { planarPoints(pointsJson) }
    val targetId = state.targetTagId.toIntOrNull() ?: -1
    val effectiveReferenceTagId = if (targetId >= 0) targetId else activeReferenceTagId
    val det = frame?.selected(effectiveReferenceTagId)
    val tagSize = state.tagSizeMm.toDoubleOrNull() ?: 100.0
    val working = if (points.isNotEmpty() && effectiveReferenceTagId >= 0) {
        As100AprilTagPlanarMeasureMethod.capture(effectiveReferenceTagId, state.family, tagSize, mode, referenceFrame.ifBlank { "tag:$effectiveReferenceTagId" }, points)
    } else emptyMap()
    val imagePoints = if (det != null) points.mapNotNull { point ->
        AprilTagGeometry.worldToTag(PixelPoint(point.xMm, point.yMm), det.id, layoutJson)
            ?.let { local -> AprilTagGeometry.tagPlaneToImage(local, det, tagSize) }
    } else emptyList()
    val instruction = when (mode) {
        "point" -> if (points.isEmpty()) "Tap the point once." else "Point captured. Tap again to replace it."
        "distance" -> "Tap endpoint ${points.size.coerceAtMost(1) + 1} of 2."
        "area" -> "Tap boundary points in order (minimum 3), then Commit."
        else -> "Tap the object repeatedly over time; every tap is a timed observation."
    }
    val hud = AprilTagHud(
        roles = det?.let { mapOf(it.id to AprilTagHudRole.REFERENCE) }.orEmpty(),
        points = imagePoints,
        connectPoints = mode != "point",
        closePolygon = mode == "area",
        banner = "${mode.replaceFirstChar { it.uppercase() }} · ${points.size} point${if (points.size == 1) "" else "s"}",
        lines = listOf(
            if (effectiveReferenceTagId >= 0) "Reference tag #$effectiveReferenceTagId${if (det == null) " · LOST" else ""}" else "Waiting for reference tag",
            working[AprilTagCommonFields.RESULT].orEmpty(),
            instruction,
            tapStatus
        )
    )

    CameraInstrument(
        settings = state.cameraSettings(tagSizeMmOverride = 0.0),
        frame = frame,
        onFrame = { frame = it },
        hud = hud,
        onTap = { imagePoint ->
            val currentFrame = frame
            if (currentFrame == null) {
                tapStatus = "Camera frame is not ready yet."
                return@CameraInstrument
            }
            val chosen = currentFrame.selected(effectiveReferenceTagId)
            if (chosen == null) {
                tapStatus = if (effectiveReferenceTagId >= 0) "Reference tag #$effectiveReferenceTagId is not visible; no point captured." else "No AprilTag is visible; show the reference tag first."
                return@CameraInstrument
            }
            if (activeReferenceTagId < 0) activeReferenceTagId = chosen.id
            val local = AprilTagGeometry.imageToTagPlane(imagePoint, chosen, tagSize)
            if (local == null) {
                tapStatus = "Could not map that tap to the tag plane."
                return@CameraInstrument
            }
            val (world, frameName) = AprilTagGeometry.tagToWorld(local, chosen.id, layoutJson)
            referenceFrame = frameName
            val candidate = PlanarPoint(world.x, world.y, System.currentTimeMillis())
            val updated = when (mode) {
                "point" -> listOf(candidate)
                "distance" -> (points + candidate).take(2)
                else -> points + candidate
            }
            pointsJson = JSONArray().apply { updated.forEach { put(it.toJson()) } }.toString()
            tapStatus = "Captured point ${updated.size}: ${fmt(world.x, 1)}, ${fmt(world.y, 1)} mm."
        }
    )
    Guidance(
        "Tap directly on the live image",
        "The visible tag defines scale and the plane. You can tap anywhere on that same physical plane, not only inside the tag. Captured points are re-projected into the live HUD while the reference tag remains visible."
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = {
            pointsJson = JSONArray(points.dropLast(1).map { it.toJson() }).toString()
            tapStatus = "Removed last point."
        }, enabled = points.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Undo") }
        OutlinedButton(onClick = { pointsJson = "[]"; referenceFrame = ""; activeReferenceTagId = -1; tapStatus = "Cleared. Show a reference tag and tap the plane." }, enabled = points.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Clear") }
    }
    Button(
        onClick = {
            val committedTagId = if (targetId >= 0) targetId else activeReferenceTagId
            onCommit(As100AprilTagPlanarMeasureMethod.capture(
                committedTagId,
                state.family,
                tagSize,
                mode,
                referenceFrame.ifBlank { "tag:$committedTagId" },
                points,
                if (layoutJson.isBlank()) "Coordinates use the locked reference tag as the local origin." else ""
            ))
        },
        enabled = planarEnough(mode, points.size) && effectiveReferenceTagId >= 0,
        modifier = Modifier.fillMaxWidth()
    ) { Text("Commit ${mode.replace('_', ' ')}") }
    if (!planarEnough(mode, points.size)) Text("More points required before Commit: $instruction", style = MaterialTheme.typography.bodySmall)
    SettingsDivider()
    ChoiceField("Measurement", mode, listOf("point", "distance", "area", "trajectory")) { mode = it; pointsJson = "[]"; referenceFrame = ""; activeReferenceTagId = -1; tapStatus = "Mode changed. Show a reference tag and begin tapping." }
    BasicDetectorSettings(state, showTagSize = true, targetMeaning = "-1 = lock first visible reference tag")
    OutlinedTextField(value = layoutJson, onValueChange = { layoutJson = it }, label = { Text("Optional planar tag layout JSON") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
    Text("Validity rail: measured objects/points must lie on the same physical plane as the reference tag/layout. The module does not infer arbitrary 3-D depth from a planar tap.", style = MaterialTheme.typography.bodySmall)
    LaunchedEffect(mode, layoutJson) { context.onSettingsChanged(mapOf(AprilTagInputs.MEASUREMENT_MODE to mode, AprilTagInputs.ANCHOR_LAYOUT_JSON to layoutJson)) }
}

@Composable
private fun TrackPoseInstrument(context: CapabilityScreenContext, onCommit: (Map<String, String>) -> Unit) {
    val state = rememberAprilTagSettings(context, includeIntrinsics = true)
    var movingId by rememberSaveable { mutableStateOf(context.value(AprilTagInputs.MOVING_TAG_ID) ?: "1") }
    var referenceId by rememberSaveable { mutableStateOf(context.value(AprilTagInputs.REFERENCE_TAG_ID) ?: "-1") }
    var intervalMs by rememberSaveable { mutableStateOf(context.value(AprilTagInputs.SAMPLE_INTERVAL_MS) ?: "200") }
    var maxSamples by rememberSaveable { mutableStateOf(context.value(AprilTagInputs.MAX_SAMPLES) ?: "3000") }
    var samplesJson by rememberSaveable { mutableStateOf("[]") }
    var recording by rememberSaveable { mutableStateOf(false) }
    var pickRole by rememberSaveable { mutableStateOf("moving") }
    var statusMessage by rememberSaveable { mutableStateOf("Attach a tag to the moving object, then choose how the coordinate frame is fixed.") }
    var frame by remember { mutableStateOf<AprilTagFrame?>(null) }
    val samples = remember(samplesJson) { poseSamples(samplesJson) }
    val moving = movingId.toIntOrNull() ?: 1
    val reference = referenceId.toIntOrNull() ?: -1
    val interval = intervalMs.toLongOrNull()?.coerceAtLeast(50L) ?: 200L
    val cap = maxSamples.toIntOrNull()?.coerceIn(2, 100000) ?: 3000
    val movingDet = frame?.detections?.firstOrNull { it.id == moving }
    val refDet = if (reference >= 0) frame?.detections?.firstOrNull { it.id == reference } else null
    val currentPosition = movingDet?.pose?.let { movingPose ->
        if (reference >= 0) refDet?.pose?.let { AprilTagGeometry.relativePose(it, movingPose).translation } else movingPose.translation
    }
    val ready = movingDet?.pose != null && (reference < 0 || refDet?.pose != null)
    val path = AprilTagGeometry.pathLength3d(samples)
    val duration = if (samples.size >= 2) (samples.last().timeMs - samples.first().timeMs) / 1000.0 else 0.0
    val hud = AprilTagHud(
        roles = buildMap {
            if (movingDet != null) put(movingDet.id, AprilTagHudRole.MOVING)
            if (refDet != null) put(refDet.id, AprilTagHudRole.REFERENCE)
        },
        banner = if (recording) "● RECORDING" else "Object tracking",
        lines = listOf(
            "Moving #$moving ${if (movingDet?.pose != null) "✓" else "not ready"}",
            if (reference >= 0) "Reference #$reference ${if (refDet?.pose != null) "✓" else "not ready"}" else "Frame: fixed camera — DO NOT MOVE PHONE",
            currentPosition?.let { "Live XYZ ${fmt(it.x, 3)} · ${fmt(it.y, 3)} · ${fmt(it.z, 3)} m" }.orEmpty(),
            "Samples ${samples.size} · path ${fmt(path, 3)} m${if (duration > 0) " · ${fmt(duration, 1)} s" else ""}",
            statusMessage
        )
    )

    CameraInstrument(
        settings = state.cameraSettings(),
        frame = frame,
        onFrame = { incoming ->
            frame = incoming
            if (recording && samples.size < cap) {
                val now = System.currentTimeMillis()
                val previousTime = samples.lastOrNull()?.timeMs ?: Long.MIN_VALUE
                if (now - previousTime >= interval) {
                    val movingPose = incoming.detections.firstOrNull { it.id == moving }?.pose
                    val position = if (movingPose == null) null else if (reference >= 0) {
                        incoming.detections.firstOrNull { it.id == reference }?.pose?.let { refPose ->
                            AprilTagGeometry.relativePose(refPose, movingPose).translation
                        }
                    } else movingPose.translation
                    if (position != null) {
                        val error = incoming.detections.firstOrNull { it.id == moving }?.pose?.error
                        val updated = samples + PoseSample(now, position, error)
                        samplesJson = JSONArray().apply { updated.forEach { put(it.toJson()) } }.toString()
                        statusMessage = "Recorded sample ${updated.size}."
                        if (updated.size >= cap) { recording = false; statusMessage = "Stopped at maximum sample count." }
                    } else {
                        statusMessage = if (reference >= 0) "No sample: keep moving and reference tags visible together." else "No sample: moving tag pose is unavailable."
                    }
                }
            }
        },
        hud = hud,
        onTap = { point ->
            if (recording) { statusMessage = "Stop recording before changing tag roles."; return@CameraInstrument }
            val hit = frame?.let { AprilTagGeometry.detectionAt(point, it.detections) }
            if (hit == null) statusMessage = "No detected tag at that point." else if (pickRole == "reference") {
                if (hit.id == moving) statusMessage = "Moving and reference tags must be different."
                else { referenceId = hit.id.toString(); statusMessage = "Reference set to tag ${hit.id}." }
            } else {
                if (hit.id == reference) statusMessage = "Moving and reference tags must be different."
                else { movingId = hit.id.toString(); statusMessage = "Moving object set to tag ${hit.id}." }
            }
        }
    )
    Guidance(
        "What this records",
        "Attach the moving tag rigidly to the object. Recommended: place a second tag somewhere fixed and keep both visible; positions are then recorded in that fixed tag's 3-D coordinate frame. Alternatively choose Fixed camera, but the phone must not move at all during recording."
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { pickRole = "moving"; statusMessage = "Tap the tag attached to the moving object." }, enabled = !recording, modifier = Modifier.weight(1f)) { Text(if (pickRole == "moving") "✓ Moving" else "Set moving") }
        OutlinedButton(onClick = { pickRole = "reference"; statusMessage = "Tap the fixed reference tag." }, enabled = !recording, modifier = Modifier.weight(1f)) { Text(if (pickRole == "reference") "✓ Reference" else "Set reference") }
        OutlinedButton(onClick = { referenceId = "-1"; statusMessage = "Fixed-camera mode selected. Do not move the phone while recording." }, enabled = !recording, modifier = Modifier.weight(1f)) { Text("Fixed camera") }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!recording) Button(
            onClick = { if (samples.isNotEmpty()) samplesJson = "[]"; recording = true; statusMessage = "Recording positions every ${interval} ms." },
            enabled = ready,
            modifier = Modifier.weight(1f)
        ) { Text("Start recording") }
        else Button(onClick = { recording = false; statusMessage = "Recording stopped; review live summary then Commit." }, modifier = Modifier.weight(1f)) { Text("Stop") }
        OutlinedButton(onClick = { samplesJson = "[]"; recording = false; statusMessage = "Trajectory cleared." }, modifier = Modifier.weight(1f)) { Text("Clear") }
    }
    if (!ready && !recording) Text(if (reference >= 0) "Start requires a metric pose for both moving #$moving and reference #$reference." else "Start requires a metric pose for moving tag #$moving. Keep the phone fixed after Start.", style = MaterialTheme.typography.bodySmall)
    Button(
        onClick = {
            val intrinsicsSource = frame?.intrinsics?.source.orEmpty()
            val referenceFrame = if (reference >= 0) "tag:$reference" else "camera_fixed"
            val warning = buildList {
                frame?.intrinsics?.warning?.takeIf { it.isNotBlank() }?.let(::add)
                if (reference < 0) add("Camera-frame trajectories are interpretable only if the camera remains physically fixed throughout recording.")
            }.joinToString(" ")
            onCommit(As100AprilTagTrackPoseMethod.capture(moving, reference, state.family, state.tagSizeMm.toDoubleOrNull() ?: 100.0, referenceFrame, intrinsicsSource, samples, warning))
        },
        enabled = !recording && samples.size >= 2,
        modifier = Modifier.fillMaxWidth()
    ) { Text("Commit trajectory") }
    if (samples.size < 2) Text("Commit requires at least two recorded positions.", style = MaterialTheme.typography.bodySmall)
    SettingsDivider()
    NumericField("Moving tag ID", movingId) { movingId = integer(it) }
    NumericField("Reference tag ID (-1 = fixed camera)", referenceId) { referenceId = signedInteger(it) }
    NumericField("Sample interval (ms)", intervalMs) { intervalMs = integer(it) }
    NumericField("Maximum samples", maxSamples) { maxSamples = integer(it) }
    BasicDetectorSettings(state, showTagSize = true, showTargetId = false)
    IntrinsicsSettings(state)
    LaunchedEffect(movingId, referenceId, intervalMs, maxSamples) {
        context.onSettingsChanged(mapOf(
            AprilTagInputs.MOVING_TAG_ID to movingId,
            AprilTagInputs.REFERENCE_TAG_ID to referenceId,
            AprilTagInputs.SAMPLE_INTERVAL_MS to intervalMs,
            AprilTagInputs.MAX_SAMPLES to maxSamples
        ))
    }
}

@Composable
private fun CameraInstrument(
    settings: CameraSettings,
    frame: AprilTagFrame?,
    onFrame: (AprilTagFrame) -> Unit,
    hud: AprilTagHud = AprilTagHud(),
    onTap: ((PixelPoint) -> Unit)? = null
) {
    Box(Modifier.fillMaxWidth().height(430.dp)) {
        AprilTagCameraSurface(
            settings = settings,
            modifier = Modifier.fillMaxWidth().height(430.dp),
            onFrame = onFrame,
            onImageTap = onTap,
            hud = hud
        )
    }
    if (!AprilTagNativeBridge.isAvailable) {
        Text(
            "AprilTag native backend not installed. This module fails closed until libmethodmesh_apriltag is integrated.",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
    } else if (!frame?.error.isNullOrBlank()) {
        Text(frame?.error.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Guidance(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private class AprilTagSettingsState(
    val context: CapabilityScreenContext,
    private val familyState: MutableState<String>,
    private val tagSizeState: MutableState<String>,
    private val targetIdState: MutableState<String>,
    private val threadsState: MutableState<String>,
    private val decimateState: MutableState<String>,
    private val refineState: MutableState<Boolean>,
    private val intrinsicsModeState: MutableState<String>,
    private val fxState: MutableState<String>,
    private val fyState: MutableState<String>,
    private val cxState: MutableState<String>,
    private val cyState: MutableState<String>,
    private val intrinsicsWidthState: MutableState<String>,
    private val intrinsicsHeightState: MutableState<String>
) {
    var family by familyState
    var tagSizeMm by tagSizeState
    var targetTagId by targetIdState
    var threads by threadsState
    var quadDecimate by decimateState
    var refineEdges by refineState
    var intrinsicsMode by intrinsicsModeState
    var fx by fxState
    var fy by fyState
    var cx by cxState
    var cy by cyState
    var intrinsicsWidth by intrinsicsWidthState
    var intrinsicsHeight by intrinsicsHeightState

    fun cameraSettings(tagSizeMmOverride: Double? = null) = CameraSettings(
        family = family,
        tagSizeMm = tagSizeMmOverride ?: (tagSizeMm.toDoubleOrNull() ?: 100.0),
        targetTagId = targetTagId.toIntOrNull() ?: -1,
        intrinsicsMode = intrinsicsMode,
        fx = fx.toDoubleOrNull() ?: 0.0,
        fy = fy.toDoubleOrNull() ?: 0.0,
        cx = cx.toDoubleOrNull() ?: 0.0,
        cy = cy.toDoubleOrNull() ?: 0.0,
        intrinsicsWidth = intrinsicsWidth.toIntOrNull() ?: 0,
        intrinsicsHeight = intrinsicsHeight.toIntOrNull() ?: 0,
        threads = threads.toIntOrNull() ?: 2,
        quadDecimate = quadDecimate.toDoubleOrNull() ?: 1.0,
        refineEdges = refineEdges
    )

    fun settingsMap() = mapOf(
        AprilTagInputs.TAG_FAMILY to family,
        AprilTagInputs.TAG_SIZE_MM to tagSizeMm,
        AprilTagInputs.TARGET_TAG_ID to targetTagId,
        AprilTagInputs.THREADS to threads,
        AprilTagInputs.QUAD_DECIMATE to quadDecimate,
        AprilTagInputs.REFINE_EDGES to refineEdges.toString(),
        AprilTagInputs.INTRINSICS_MODE to intrinsicsMode,
        AprilTagInputs.FX_PX to fx,
        AprilTagInputs.FY_PX to fy,
        AprilTagInputs.CX_PX to cx,
        AprilTagInputs.CY_PX to cy,
        AprilTagInputs.INTRINSICS_WIDTH_PX to intrinsicsWidth,
        AprilTagInputs.INTRINSICS_HEIGHT_PX to intrinsicsHeight
    )
}

@Composable
private fun rememberAprilTagSettings(context: CapabilityScreenContext, includeIntrinsics: Boolean): AprilTagSettingsState {
    val family = rememberSaveable { mutableStateOf(context.value(AprilTagInputs.TAG_FAMILY) ?: AprilTagContractMetadata.DEFAULT_FAMILY) }
    val tagSize = rememberSaveable { mutableStateOf(context.value(AprilTagInputs.TAG_SIZE_MM) ?: "100") }
    val targetId = rememberSaveable { mutableStateOf(context.value(AprilTagInputs.TARGET_TAG_ID) ?: "-1") }
    val threads = rememberSaveable { mutableStateOf(context.value(AprilTagInputs.THREADS) ?: "2") }
    val decimate = rememberSaveable { mutableStateOf(context.value(AprilTagInputs.QUAD_DECIMATE) ?: "1.0") }
    val refine = rememberSaveable { mutableStateOf(context.value(AprilTagInputs.REFINE_EDGES)?.toBooleanStrictOrNull() ?: true) }
    val mode = rememberSaveable { mutableStateOf(context.value(AprilTagInputs.INTRINSICS_MODE) ?: "auto") }
    val fx = rememberSaveable { mutableStateOf(context.value(AprilTagInputs.FX_PX) ?: "0") }
    val fy = rememberSaveable { mutableStateOf(context.value(AprilTagInputs.FY_PX) ?: "0") }
    val cx = rememberSaveable { mutableStateOf(context.value(AprilTagInputs.CX_PX) ?: "0") }
    val cy = rememberSaveable { mutableStateOf(context.value(AprilTagInputs.CY_PX) ?: "0") }
    val iw = rememberSaveable { mutableStateOf(context.value(AprilTagInputs.INTRINSICS_WIDTH_PX) ?: "0") }
    val ih = rememberSaveable { mutableStateOf(context.value(AprilTagInputs.INTRINSICS_HEIGHT_PX) ?: "0") }

    val state = remember(context.action.canonicalId) {
        AprilTagSettingsState(context, family, tagSize, targetId, threads, decimate, refine, mode, fx, fy, cx, cy, iw, ih)
    }
    LaunchedEffect(state.family, state.tagSizeMm, state.targetTagId, state.threads, state.quadDecimate, state.refineEdges, state.intrinsicsMode, state.fx, state.fy, state.cx, state.cy, state.intrinsicsWidth, state.intrinsicsHeight) {
        context.onSettingsChanged(state.settingsMap())
    }
    return state
}

@Composable
private fun BasicDetectorSettings(
    state: AprilTagSettingsState,
    showTagSize: Boolean,
    showTargetId: Boolean = true,
    targetMeaning: String = "-1 = strongest visible"
) {
    FamilyPicker(state.family) { state.family = it }
    if (showTagSize) NumericField("Tag detection-edge size (mm)", state.tagSizeMm) { state.tagSizeMm = numeric(it) }
    if (showTargetId) NumericField("Target tag ID ($targetMeaning)", state.targetTagId) { state.targetTagId = signedInteger(it) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumericField("Threads", state.threads, Modifier.weight(1f)) { state.threads = integer(it) }
        NumericField("Quad decimate", state.quadDecimate, Modifier.weight(1f)) { state.quadDecimate = numeric(it) }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Edge refinement", style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { state.refineEdges = !state.refineEdges }) { Text(if (state.refineEdges) "On" else "Off") }
    }
}

@Composable
private fun IntrinsicsSettings(state: AprilTagSettingsState) {
    ChoiceField("Camera model", state.intrinsicsMode, listOf("auto", "manual")) { state.intrinsicsMode = it }
    if (state.intrinsicsMode == "manual") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumericField("fx (px)", state.fx, Modifier.weight(1f)) { state.fx = numeric(it) }
            NumericField("fy (px)", state.fy, Modifier.weight(1f)) { state.fy = numeric(it) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumericField("cx (px)", state.cx, Modifier.weight(1f)) { state.cx = numeric(it) }
            NumericField("cy (px)", state.cy, Modifier.weight(1f)) { state.cy = numeric(it) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumericField("Calibration width", state.intrinsicsWidth, Modifier.weight(1f)) { state.intrinsicsWidth = integer(it) }
            NumericField("Calibration height", state.intrinsicsHeight, Modifier.weight(1f)) { state.intrinsicsHeight = integer(it) }
        }
    }
}

@Composable
private fun FamilyPicker(value: String, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text("Tag family · $value") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            supportedFamilies.forEach { family -> DropdownMenuItem(text = { Text(family) }, onClick = { onChange(family); expanded = false }) }
        }
    }
}

@Composable
private fun ChoiceField(label: String, value: String, choices: List<String>, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text("$label · $value") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { choice -> DropdownMenuItem(text = { Text(choice.replace('_', ' ')) }, onClick = { onChange(choice); expanded = false }) }
        }
    }
}

@Composable
private fun NumericField(label: String, value: String, modifier: Modifier = Modifier.fillMaxWidth(), onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true, modifier = modifier)
}

@Composable
private fun CopyableValue(label: String, value: String) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = {
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, value))
        },
        enabled = value.isNotBlank(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label)
            Text(value, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SettingsDivider() {
    Spacer(Modifier.height(12.dp)); HorizontalDivider(); Spacer(Modifier.height(12.dp)); Text("Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

private fun numeric(value: String): String = value.filter { it.isDigit() || it == '.' }
private fun integer(value: String): String = value.filter(Char::isDigit)
private fun signedInteger(value: String): String = value.filterIndexed { index, c -> c.isDigit() || (c == '-' && index == 0) }

private fun edgeShapeCv(det: AprilTagDetection): Double {
    if (det.corners.size != 4) return Double.POSITIVE_INFINITY
    val edges = det.corners.indices.map { i ->
        val a = det.corners[i]; val b = det.corners[(i + 1) % 4]
        kotlin.math.hypot(a.x - b.x, a.y - b.y)
    }
    return AprilTagGeometry.coefficientOfVariation(edges)
}

private fun jsonDoubles(json: String): List<Double> = runCatching {
    val a = JSONArray(json); (0 until a.length()).map { a.getDouble(it) }
}.getOrDefault(emptyList())

private fun planarPoints(json: String): List<PlanarPoint> = runCatching {
    val a = JSONArray(json); (0 until a.length()).map { i ->
        val o = a.getJSONObject(i); PlanarPoint(o.getDouble("x_mm"), o.getDouble("y_mm"), o.getLong("time_ms"))
    }
}.getOrDefault(emptyList())

private fun poseSamples(json: String): List<PoseSample> = runCatching {
    val a = JSONArray(json); (0 until a.length()).map { i ->
        val o = a.getJSONObject(i)
        PoseSample(o.getLong("time_ms"), Vec3(o.getDouble("x_m"), o.getDouble("y_m"), o.getDouble("z_m")), if (o.isNull("pose_error")) null else o.getDouble("pose_error"))
    }
}.getOrDefault(emptyList())

private fun planarEnough(mode: String, count: Int): Boolean = when (mode) {
    "point" -> count >= 1
    "distance" -> count >= 2
    "area" -> count >= 3
    "trajectory" -> count >= 2
    else -> false
}

private fun CapabilityScreenContext.value(key: String): String? =
    (action.settings[key] ?: action.settings["input_$key"] ?: request.settings[key] ?: request.settings["input_$key"])
        ?.takeIf { it.isNotBlank() }
