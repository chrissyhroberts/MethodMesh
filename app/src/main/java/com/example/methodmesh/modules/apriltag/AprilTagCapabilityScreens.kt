package com.example.methodmesh.modules.apriltag

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.transport.workflow.ui.CapabilityHostPresentation
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityPresentationMode
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
    override val hostPresentation = CapabilityHostPresentation.Immersive

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val method = aprilTagMethod(capabilityId)
        val presetSchema = remember(capabilityId) { AprilTagModule.capabilitySettings()[capabilityId].orEmpty() }
        val presetSettingIds = remember(presetSchema) { presetSchema.map { it.id }.toSet() }
        val currentPresetSettings = remember(capabilityId, context.action.canonicalId) {
            mutableStateMapOf<String, String>().apply {
                presetSchema.forEach { setting ->
                    put(
                        setting.id,
                        context.action.settings[setting.id]
                            ?: context.request.settings[setting.id]
                            ?: aprilTagDefaultString(setting)
                    )
                }
                val runtimeFields = context.action.settings["methodmesh_runtime_fields"]
                    ?: context.request.settings["methodmesh_runtime_fields"]
                if (!runtimeFields.isNullOrBlank()) put("methodmesh_runtime_fields", runtimeFields)
            }
        }
        val instrumentContext = context.copy(
            onSettingsChanged = { updated ->
                updated.forEach { (key, value) ->
                    if (key in presetSettingIds) currentPresetSettings[key] = value
                }
                context.onSettingsChanged(updated)
            }
        )
        var presetDialogOpen by rememberSaveable("${context.action.canonicalId}:apriltagPreset") { mutableStateOf(false) }
        var presetStatus by rememberSaveable("${context.action.canonicalId}:apriltagPresetStatus") { mutableStateOf<String?>(null) }
        val canSavePreset = context.presentationMode == CapabilityPresentationMode.Dashboard && !context.isNativePresetRun
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

        // External/preset/protocol execution is hosted by ExternalWorkflowActivity, whose
        // workflow column is vertically scrollable. A fillMaxSize() child in that parent is
        // measured with an unbounded vertical constraint, which leaves Android PreviewView
        // without a reliable camera viewport. The capability owns its optical surface, so it
        // establishes a finite screen-sized viewport for every intent-launched run. Dashboard
        // launch already supplies finite fullscreen constraints and continues to use fillMaxSize.
        val instrumentViewport = if (context.presentationMode == CapabilityPresentationMode.IntentLaunch) {
            val screenHeightDp = LocalConfiguration.current.screenHeightDp
            Modifier
                .fillMaxWidth()
                .height((screenHeightDp - 16).coerceAtLeast(320).dp)
        } else {
            Modifier.fillMaxSize()
        }

        if (committedResult != null) {
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
            ) { }
        } else {
            Column(instrumentViewport) {
                ImmersiveAprilTagTopBar(
                    title = title,
                    onBack = if (context.stepNumber > 1) onBack else onCancel,
                    presetStatus = presetStatus,
                    onSavePreset = if (canSavePreset) ({ presetDialogOpen = true }) else null
                )
                Column(Modifier.fillMaxWidth().weight(1f)) {
                    when (capabilityId) {
                        As100AprilTagDetectMethod.id -> DetectInstrument(instrumentContext) { committedValuesJson = valuesToJson(it) }
                        As100AprilTagCalibrateFocalMethod.id -> CalibrationInstrument(instrumentContext) { committedValuesJson = valuesToJson(it) }
                        As100AprilTagRangePoseMethod.id -> RangePoseInstrument(instrumentContext) { committedValuesJson = valuesToJson(it) }
                        As100AprilTagRelativePoseMethod.id -> RelativePoseInstrument(instrumentContext) { committedValuesJson = valuesToJson(it) }
                        As100AprilTagPlanarMeasureMethod.id -> PlanarMeasureInstrument(instrumentContext) { committedValuesJson = valuesToJson(it) }
                        As100AprilTagTrackPoseMethod.id -> TrackPoseInstrument(instrumentContext) { committedValuesJson = valuesToJson(it) }
                    }
                }
                if (presetDialogOpen) {
                    AprilTagSavePresetDialog(
                        methodId = capabilityId,
                        methodTitle = title,
                        methodDescription = description,
                        schema = presetSchema,
                        currentSettings = currentPresetSettings.toMap(),
                        onDismiss = { presetDialogOpen = false },
                        onSaved = { savedName ->
                            presetDialogOpen = false
                            presetStatus = "Preset saved · $savedName"
                        }
                    )
                }
            }
        }
    }
}

object AprilTagDetectCapabilityScreen : CapabilityScreenSpec by AprilTagCapabilityScreenSpec(
    As100AprilTagDetectMethod.id, "Detect AprilTag tags", "Identify AprilTags and inspect image-space detection evidence."
)
object AprilTagCalibrateCapabilityScreen : CapabilityScreenSpec by AprilTagCapabilityScreenSpec(
    As100AprilTagCalibrateFocalMethod.id, "Calibrate AprilTag measurements", "Set a persistent real-world distance adjustment from a known camera-to-tag distance."
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
    As100AprilTagCalibrateFocalMethod.id -> setOf(AprilTagCommonFields.RESULT, "apriltag_distance_scale", "apriltag_raw_distance_m", "apriltag_corrected_distance_m", "apriltag_effective_tag_size_mm", "apriltag_range_cv", AprilTagCommonFields.GEOMETRY_VALID, AprilTagCommonFields.WARNING)
    As100AprilTagRangePoseMethod.id -> setOf(AprilTagCommonFields.RESULT, "apriltag_distance_m", "apriltag_x_right_m", "apriltag_y_down_m", "apriltag_z_forward_m", "apriltag_pose_error", AprilTagCommonFields.WARNING)
    As100AprilTagRelativePoseMethod.id -> setOf(AprilTagCommonFields.RESULT, "apriltag_relative_x_m", "apriltag_relative_y_m", "apriltag_relative_z_m", "apriltag_relative_distance_m", AprilTagCommonFields.WARNING)
    As100AprilTagPlanarMeasureMethod.id -> setOf(AprilTagCommonFields.RESULT, "apriltag_point_x_mm", "apriltag_point_y_mm", "apriltag_distance_mm", "apriltag_area_mm2", "apriltag_path_length_mm", AprilTagCommonFields.WARNING)
    else -> setOf(AprilTagCommonFields.RESULT, "apriltag_sample_count", "apriltag_path_length_m", "apriltag_displacement_m", "apriltag_mean_speed_m_s", AprilTagCommonFields.WARNING)
}

@Composable
private fun DetectInstrument(context: CapabilityScreenContext, onCommit: (Map<String, String>) -> Unit) {
    val state = rememberAprilTagSettings(context, includeIntrinsics = false, includeDistanceScale = false)
    var frame by remember { mutableStateOf<AprilTagFrame?>(null) }
    var tapMessage by rememberSaveable { mutableStateOf("") }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val targetId = state.targetTagId.toIntOrNull() ?: -1
    val selected = frame?.selected(targetId)
    val allMode = targetId < 0
    val ids = frame?.detections?.map { it.id }.orEmpty()
    val hud = AprilTagHud(
        roles = if (!allMode && selected != null) mapOf(selected.id to AprilTagHudRole.TARGET) else emptyMap(),
        banner = if (allMode) "DETECT · ALL TAGS" else "DETECT · TARGET #$targetId",
        lines = listOf(
            "Visible ${ids.size}${if (ids.isEmpty()) "" else " · ${ids.joinToString(", ")}"}",
            selected?.let { "Tag #${it.id} · edge ${fmt(it.meanEdgePx, 1)} px" }.orEmpty(),
            selected?.let { "Margin ${fmt(it.decisionMargin, 1)} · hamming ${it.hamming}" }.orEmpty(),
            tapMessage.ifBlank { if (allMode) "Tap a tag for single-target mode." else "Tap another visible tag to retarget." }
        )
    )
    AprilTagInstrumentShell(
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
        },
        instruction = if (allMode) "All visible tags will be committed together." else "Tag #$targetId is selected. Tap another tag to retarget.",
        showSettings = showSettings,
        onToggleSettings = { showSettings = !showSettings },
        controls = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { state.targetTagId = "-1"; tapMessage = "All visible tags selected." },
                    modifier = Modifier.weight(1f)
                ) { Text("All visible") }
                Button(
                    onClick = { frame?.let { onCommit(As100AprilTagDetectMethod.capture(it, targetId, state.family)) } },
                    enabled = if (allMode) frame?.detections?.isNotEmpty() == true else selected != null,
                    modifier = Modifier.weight(1f)
                ) { Text(if (allMode) "Commit ${ids.size} tag${if (ids.size == 1) "" else "s"}" else "Commit tag") }
            }
        },
        settingsContent = {
            BasicDetectorSettings(state, showTagSize = false, targetMeaning = "-1 = all visible")
        }
    )
}
@Composable
private fun CalibrationInstrument(context: CapabilityScreenContext, onCommit: (Map<String, String>) -> Unit) {
    val androidContext = LocalContext.current
    val state = rememberAprilTagSettings(context, includeIntrinsics = true, includeDistanceScale = true)
    var knownDistance by rememberSaveable { mutableStateOf(context.value(AprilTagInputs.KNOWN_DISTANCE_MM) ?: "1000") }
    var requiredSamples by rememberSaveable { mutableStateOf(context.value(AprilTagInputs.CALIBRATION_SAMPLES) ?: "15") }
    var edgeSamplesJson by rememberSaveable { mutableStateOf("[]") }
    var rangeSamplesJson by rememberSaveable { mutableStateOf("[]") }
    var collecting by rememberSaveable { mutableStateOf(false) }
    var lastAccepted by rememberSaveable { mutableStateOf(0L) }
    var frame by remember { mutableStateOf<AprilTagFrame?>(null) }
    var calibrationTagId by rememberSaveable { mutableStateOf(-1) }
    var sampleStatus by rememberSaveable { mutableStateOf("Show one tag front-on, measure the true camera-to-tag distance, then sample.") }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    val edgeSamples = remember(edgeSamplesJson) { jsonDoubles(edgeSamplesJson) }
    val rawRangeSamples = remember(rangeSamplesJson) { jsonDoubles(rangeSamplesJson) }
    val required = requiredSamples.toIntOrNull()?.coerceIn(3, 100) ?: 15
    val targetId = state.targetTagId.toIntOrNull() ?: -1
    val effectiveTarget = if (targetId >= 0) targetId else calibrationTagId
    val currentDet = frame?.selected(effectiveTarget)
    val currentRawRange = currentDet?.pose?.let(AprilTagGeometry::distanceFromCamera)
    val knownDistanceMmValue = knownDistance.toDoubleOrNull() ?: 1000.0
    val meanRawRange = rawRangeSamples.takeIf { it.isNotEmpty() }?.average()
    val rangeCv = AprilTagGeometry.coefficientOfVariation(rawRangeSamples)
    val scaleCandidate = meanRawRange?.takeIf { it.isFinite() && it > 0.0 }?.let { raw ->
        (knownDistanceMmValue / 1000.0) / raw
    }
    val tagSizeMmValue = state.tagSizeMm.toDoubleOrNull() ?: 100.0
    val effectiveTagSize = scaleCandidate?.let { tagSizeMmValue * it }
    val currentSavedScale = state.distanceScaleValue()

    val hud = AprilTagHud(
        roles = currentDet?.let { mapOf(it.id to AprilTagHudRole.TARGET) }.orEmpty(),
        banner = if (collecting) "METRIC CALIBRATION · ${rawRangeSamples.size}/$required" else "METRIC CALIBRATION",
        lines = listOf(
            currentDet?.let { "Tag #${it.id} · physical edge ${fmt(tagSizeMmValue, 1)} mm" }.orEmpty(),
            currentRawRange?.let { "Raw live range ${fmt(it, 3)} m" } ?: "Waiting for a metric pose",
            meanRawRange?.let { "Mean raw ${fmt(it, 3)} m · CV ${fmt(rangeCv, 4)}" }.orEmpty(),
            scaleCandidate?.let { "Adjustment ×${fmt(it, 4)} · corrected ${fmt((meanRawRange ?: 0.0) * it, 3)} m" }
                ?: "Current saved adjustment ×${fmt(currentSavedScale, 4)}",
            effectiveTagSize?.let { "Effective pose size ${fmt(it, 2)} mm = tag size × adjustment" }.orEmpty(),
            sampleStatus
        )
    )

    AprilTagInstrumentShell(
        // Calibration must observe the uncorrected pose so the new factor is not
        // recursively applied while it is being estimated.
        settings = state.cameraSettings(distanceScaleOverride = 1.0),
        frame = frame,
        onFrame = { incoming ->
            frame = incoming
            if (collecting && rawRangeSamples.size < required) {
                val lockedTarget = if (targetId >= 0) targetId else calibrationTagId
                val det = incoming.selected(lockedTarget)
                val pose = det?.pose
                val rawRange = pose?.let(AprilTagGeometry::distanceFromCamera)
                val now = System.currentTimeMillis()
                when {
                    det == null -> sampleStatus = "Waiting: calibration tag is not visible."
                    pose == null || rawRange == null || !rawRange.isFinite() || rawRange <= 0.0 -> sampleStatus = "Waiting: metric pose is unavailable; check camera model, tag size and viewing angle."
                    det.hamming != 0 -> sampleStatus = "Waiting: decode error correction is non-zero."
                    !det.meanEdgePx.isFinite() -> sampleStatus = "Waiting: edge measurement is unstable."
                    edgeShapeCv(det) > 0.08 -> sampleStatus = "Waiting: hold the tag more front-facing."
                    now - lastAccepted < 100 -> Unit
                    else -> {
                        if (calibrationTagId < 0) calibrationTagId = det.id
                        edgeSamplesJson = JSONArray(edgeSamples + det.meanEdgePx).toString()
                        rangeSamplesJson = JSONArray(rawRangeSamples + rawRange).toString()
                        lastAccepted = now
                        val accepted = rawRangeSamples.size + 1
                        sampleStatus = "Accepted sample $accepted of $required."
                        if (accepted >= required) collecting = false
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
        },
        instruction = if (collecting) {
            "Keep the selected tag centred, still and front-facing while raw pose samples accumulate."
        } else {
            "Measure camera optical centre → tag centre, enter that true distance, then sample. The resulting factor scales X/Y/Z and range."
        },
        showSettings = showSettings,
        onToggleSettings = { showSettings = !showSettings },
        controls = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!collecting) Button(onClick = {
                    if (rawRangeSamples.size >= required) {
                        edgeSamplesJson = "[]"
                        rangeSamplesJson = "[]"
                        calibrationTagId = -1
                    }
                    collecting = true
                    sampleStatus = "Sampling raw pose… keep tag still and front-facing."
                }, modifier = Modifier.weight(1f)) { Text(if (rawRangeSamples.isEmpty()) "Start sampling" else "Resume") }
                else Button(onClick = { collecting = false; sampleStatus = "Paused." }, modifier = Modifier.weight(1f)) { Text("Pause") }
                OutlinedButton(onClick = {
                    edgeSamplesJson = "[]"
                    rangeSamplesJson = "[]"
                    collecting = false
                    calibrationTagId = -1
                    sampleStatus = "Cleared."
                }, modifier = Modifier.weight(1f)) { Text("Clear") }
            }
            Button(
                onClick = {
                    val f = frame ?: return@Button
                    val id = if (targetId >= 0) targetId else calibrationTagId
                    val det = f.selected(id) ?: return@Button
                    val result = As100AprilTagCalibrateFocalMethod.capture(
                        tagId = det.id,
                        family = state.family,
                        tagSizeMm = tagSizeMmValue,
                        knownDistanceMm = knownDistanceMmValue,
                        edgeSamplesPx = edgeSamples,
                        rawRangeSamplesM = rawRangeSamples,
                        imageWidth = f.width,
                        imageHeight = f.height,
                        requiredSamples = required,
                        intrinsicsSource = f.intrinsics?.source.orEmpty()
                    )
                    val newScale = result["apriltag_distance_scale"]?.toDoubleOrNull()
                    val rawDistance = result["apriltag_raw_distance_m"]?.toDoubleOrNull()
                    if (newScale != null && rawDistance != null && newScale.isFinite() && newScale > 0.0) {
                        AprilTagCalibrationStore.save(
                            context = androidContext,
                            distanceScale = newScale,
                            tagSizeMm = tagSizeMmValue,
                            rawDistanceM = rawDistance,
                            trueDistanceMm = knownDistanceMmValue,
                            tagId = det.id,
                            intrinsicsSource = f.intrinsics?.source.orEmpty()
                        )
                        state.distanceScale = fmt(newScale, 6)
                        context.onSettingsChanged(mapOf(AprilTagInputs.DISTANCE_SCALE to state.distanceScale))
                    }
                    onCommit(result)
                },
                enabled = rawRangeSamples.size >= required && currentDet?.pose != null && scaleCandidate?.isFinite() == true,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (rawRangeSamples.size >= required) "Save calibration + Commit" else "Need ${required - rawRangeSamples.size} more") }
        },
        settingsContent = {
            BasicDetectorSettings(state, showTagSize = true, targetMeaning = "-1 = tap/lock first suitable tag")
            MetricScaleSetting(state, label = "Current distance adjustment")
            OutlinedButton(
                onClick = {
                    AprilTagCalibrationStore.saveManual(androidContext, state.distanceScaleValue(), tagSizeMmValue)
                    sampleStatus = "Saved manual adjustment ×${fmt(state.distanceScaleValue(), 4)} as the device default."
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Use adjustment as device default") }
            NumericField("True camera-to-tag distance (mm)", knownDistance) { knownDistance = numeric(it) }
            NumericField("Samples", requiredSamples) { requiredSamples = integer(it) }
            IntrinsicsSettings(state)
            Guidance(
                "Metric rule",
                "corrected XYZ/range = raw pose × adjustment. Equivalently, effective pose tag size = measured tag size × adjustment. Planar measurements continue to use the physical tag size directly."
            )
        }
    )
    LaunchedEffect(knownDistance, requiredSamples) {
        context.onSettingsChanged(mapOf(AprilTagInputs.KNOWN_DISTANCE_MM to knownDistance, AprilTagInputs.CALIBRATION_SAMPLES to requiredSamples))
    }
}
@Composable
private fun RangePoseInstrument(context: CapabilityScreenContext, onCommit: (Map<String, String>) -> Unit) {
    val state = rememberAprilTagSettings(context, includeIntrinsics = true, includeDistanceScale = true)
    var frame by remember { mutableStateOf<AprilTagFrame?>(null) }
    var tapMessage by rememberSaveable { mutableStateOf("") }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val targetId = state.targetTagId.toIntOrNull() ?: -1
    val det = frame?.selected(targetId)
    val pose = det?.pose
    val euler = pose?.let { AprilTagGeometry.eulerDegrees(it.rotation) }
    val roles = det?.let { mapOf(it.id to AprilTagHudRole.TARGET) }.orEmpty()
    val hud = AprilTagHud(
        roles = roles,
        banner = det?.let { "TAG ${it.id} · LIVE RANGE + POSE" } ?: "RANGE + POSE",
        lines = listOf(
            pose?.let { "Range ${fmt(AprilTagGeometry.distanceFromCamera(it), 3)} m" } ?: if (det != null) "Tag detected · pose marginal/unavailable" else "Aim at a tag",
            pose?.let { "XYZ ${fmt(it.translation.x, 3)} · ${fmt(it.translation.y, 3)} · ${fmt(it.translation.z, 3)} m" }.orEmpty(),
            "Metric adjustment ×${fmt(state.distanceScaleValue(), 4)} · effective tag ${fmt((state.tagSizeMm.toDoubleOrNull() ?: 100.0) * state.distanceScaleValue(), 2)} mm",
            euler?.let { "Yaw ${fmt(it.first, 1)}° · pitch ${fmt(it.second, 1)}° · roll ${fmt(it.third, 1)}°" }.orEmpty(),
            det?.let { "Edge ${fmt(it.meanEdgePx, 0)} px · margin ${fmt(it.decisionMargin, 1)}${it.pose?.error?.let { e -> " · error ${fmt(e, 3)}" }.orEmpty()}" }.orEmpty(),
            tapMessage.ifBlank { if (det == null) "Any visible tag can be tapped to target it." else "Tap another tag to retarget." }
        )
    )
    AprilTagInstrumentShell(
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
        },
        instruction = when {
            det == null -> "Keep a tag in view. Tap it to target it."
            pose == null -> "Detection is live but pose is not reliable enough. Move closer, reduce the angle, or check tag size/calibration."
            else -> "Live metric pose is ready. Commit freezes exactly the values shown now."
        },
        showSettings = showSettings,
        onToggleSettings = { showSettings = !showSettings },
        controls = {
            Button(
                onClick = { frame?.let { onCommit(As100AprilTagRangePoseMethod.capture(it, targetId, state.tagSizeMm.toDoubleOrNull() ?: 100.0, state.family, state.distanceScaleValue())) } },
                enabled = pose != null,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Commit range and pose") }
        },
        settingsContent = {
            BasicDetectorSettings(state, showTagSize = true, targetMeaning = "-1 = strongest visible; or tap a tag")
            MetricScaleSetting(state)
            IntrinsicsSettings(state)
        }
    )
}
@Composable
private fun RelativePoseInstrument(context: CapabilityScreenContext, onCommit: (Map<String, String>) -> Unit) {
    val state = rememberAprilTagSettings(context, includeIntrinsics = true, includeDistanceScale = true)
    var referenceId by rememberSaveable { mutableStateOf(context.value(AprilTagInputs.REFERENCE_TAG_ID) ?: "0") }
    var targetId by rememberSaveable { mutableStateOf(context.value(AprilTagInputs.TARGET_TAG_ID) ?: "1") }
    var pickRole by rememberSaveable { mutableStateOf("target") }
    var tapMessage by rememberSaveable { mutableStateOf("Choose Reference or Target, then tap a visible tag.") }
    var frame by remember { mutableStateOf<AprilTagFrame?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
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
            "Metric adjustment ×${fmt(state.distanceScaleValue(), 4)}",
            euler?.let { "Yaw ${fmt(it.first, 1)}° · pitch ${fmt(it.second, 1)}° · roll ${fmt(it.third, 1)}°" }.orEmpty(),
            "Tap assigns ${pickRole.uppercase()}",
            tapMessage
        )
    )
    AprilTagInstrumentShell(
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
        },
        instruction = if (relative == null) "Keep both tags visible. Choose a role below, then tap the corresponding tag." else "Relative transform is live and ready to commit.",
        showSettings = showSettings,
        onToggleSettings = { showSettings = !showSettings },
        controls = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickRole = "reference"; tapMessage = "Tap the fixed/reference tag." }, modifier = Modifier.weight(1f)) { Text(if (pickRole == "reference") "✓ Reference" else "Set reference") }
                OutlinedButton(onClick = { pickRole = "target"; tapMessage = "Tap the target tag." }, modifier = Modifier.weight(1f)) { Text(if (pickRole == "target") "✓ Target" else "Set target") }
            }
            Button(
                onClick = { frame?.let { onCommit(As100AprilTagRelativePoseMethod.capture(it, refInt, targetInt, state.tagSizeMm.toDoubleOrNull() ?: 100.0, state.family, state.distanceScaleValue())) } },
                enabled = relative != null && refInt != targetInt,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Commit relative pose") }
        },
        settingsContent = {
            NumericField("Reference tag ID", referenceId) { referenceId = integer(it) }
            NumericField("Target tag ID", targetId) { targetId = integer(it) }
            BasicDetectorSettings(state, showTagSize = true, showTargetId = false)
            MetricScaleSetting(state)
            IntrinsicsSettings(state)
        }
    )
    LaunchedEffect(referenceId, targetId) {
        context.onSettingsChanged(mapOf(AprilTagInputs.REFERENCE_TAG_ID to referenceId, AprilTagInputs.TARGET_TAG_ID to targetId))
    }
}
@Composable
private fun PlanarMeasureInstrument(context: CapabilityScreenContext, onCommit: (Map<String, String>) -> Unit) {
    val state = rememberAprilTagSettings(context, includeIntrinsics = false, includeDistanceScale = false)
    var mode by rememberSaveable { mutableStateOf(context.value(AprilTagInputs.MEASUREMENT_MODE) ?: "point") }
    var layoutJson by rememberSaveable { mutableStateOf(context.value(AprilTagInputs.ANCHOR_LAYOUT_JSON) ?: "") }
    var pointsJson by rememberSaveable { mutableStateOf("[]") }
    var frame by remember { mutableStateOf<AprilTagFrame?>(null) }
    var referenceFrame by rememberSaveable { mutableStateOf("") }
    var activeReferenceTagId by rememberSaveable { mutableStateOf(-1) }
    var tapStatus by rememberSaveable { mutableStateOf("Show the reference tag, then tap on the physical plane.") }
    var showSettings by rememberSaveable { mutableStateOf(false) }
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
        "distance" -> if (points.size < 2) "Tap endpoint ${points.size + 1} of 2." else "Distance complete."
        "area" -> if (points.size < 3) "Tap boundary points in order (minimum 3)." else "Area polygon ready; add more boundary points or Commit."
        else -> "Tap the object's position repeatedly over time; every tap becomes a timed observation."
    }
    val hud = AprilTagHud(
        roles = det?.let { mapOf(it.id to AprilTagHudRole.REFERENCE) }.orEmpty(),
        points = imagePoints,
        connectPoints = mode != "point",
        closePolygon = mode == "area",
        banner = "${mode.replaceFirstChar { it.uppercase() }} · ${points.size} point${if (points.size == 1) "" else "s"}",
        lines = listOf(
            if (effectiveReferenceTagId >= 0) "Reference #$effectiveReferenceTagId${if (det == null) " · LOST" else " · plane locked"}" else "Waiting for reference tag",
            working[AprilTagCommonFields.RESULT].orEmpty(),
            instruction,
            tapStatus
        )
    )
    AprilTagInstrumentShell(
        settings = state.cameraSettings(tagSizeMmOverride = 0.0),
        frame = frame,
        onFrame = { frame = it },
        hud = hud,
        onTap = { imagePoint ->
            val currentFrame = frame
            if (currentFrame == null) {
                tapStatus = "Camera frame is not ready yet."
            } else {
                val chosen = currentFrame.selected(effectiveReferenceTagId)
                if (chosen == null) {
                    tapStatus = if (effectiveReferenceTagId >= 0) "Reference tag #$effectiveReferenceTagId is not visible; no point captured." else "No AprilTag is visible; show the reference tag first."
                } else {
                    if (activeReferenceTagId < 0) activeReferenceTagId = chosen.id
                    val local = AprilTagGeometry.imageToTagPlane(imagePoint, chosen, tagSize)
                    if (local == null) {
                        tapStatus = "Could not map that tap to the tag plane."
                    } else {
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
                }
            }
        },
        instruction = "Tap directly on the live camera. All measured points must lie on the same physical plane as the reference tag.",
        showSettings = showSettings,
        onToggleSettings = { showSettings = !showSettings },
        controls = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceCompactButton("Mode", mode, listOf("point", "distance", "area", "trajectory"), Modifier.weight(1.2f)) {
                    mode = it; pointsJson = "[]"; referenceFrame = ""; activeReferenceTagId = -1
                    tapStatus = "Mode changed. Show a reference tag and begin tapping."
                }
                OutlinedButton(onClick = {
                    pointsJson = JSONArray(points.dropLast(1).map { it.toJson() }).toString()
                    tapStatus = "Removed last point."
                }, enabled = points.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Undo") }
                OutlinedButton(onClick = {
                    pointsJson = "[]"; referenceFrame = ""; activeReferenceTagId = -1
                    tapStatus = "Cleared. Show a reference tag and tap the plane."
                }, enabled = points.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Clear") }
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
        },
        settingsContent = {
            ChoiceField("Measurement", mode, listOf("point", "distance", "area", "trajectory")) {
                mode = it; pointsJson = "[]"; referenceFrame = ""; activeReferenceTagId = -1
                tapStatus = "Mode changed. Show a reference tag and begin tapping."
            }
            BasicDetectorSettings(state, showTagSize = true, targetMeaning = "-1 = lock first visible reference tag")
            OutlinedTextField(value = layoutJson, onValueChange = { layoutJson = it }, label = { Text("Optional planar tag layout JSON") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            Text("Validity: this is a planar transform. The module does not infer arbitrary 3-D depth from an image tap.", style = MaterialTheme.typography.bodySmall)
        }
    )
    LaunchedEffect(mode, layoutJson) {
        context.onSettingsChanged(mapOf(AprilTagInputs.MEASUREMENT_MODE to mode, AprilTagInputs.ANCHOR_LAYOUT_JSON to layoutJson))
    }
}
@Composable
private fun TrackPoseInstrument(context: CapabilityScreenContext, onCommit: (Map<String, String>) -> Unit) {
    val state = rememberAprilTagSettings(context, includeIntrinsics = true, includeDistanceScale = true)
    var movingId by rememberSaveable { mutableStateOf(context.value(AprilTagInputs.MOVING_TAG_ID) ?: "1") }
    var referenceId by rememberSaveable { mutableStateOf(context.value(AprilTagInputs.REFERENCE_TAG_ID) ?: "-1") }
    var intervalMs by rememberSaveable { mutableStateOf(context.value(AprilTagInputs.SAMPLE_INTERVAL_MS) ?: "200") }
    var maxSamples by rememberSaveable { mutableStateOf(context.value(AprilTagInputs.MAX_SAMPLES) ?: "3000") }
    var samplesJson by rememberSaveable { mutableStateOf("[]") }
    var recording by rememberSaveable { mutableStateOf(false) }
    var pickRole by rememberSaveable { mutableStateOf("moving") }
    var statusMessage by rememberSaveable { mutableStateOf("Choose the moving tag. A fixed reference tag is recommended.") }
    var frame by remember { mutableStateOf<AprilTagFrame?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
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
    val displacement = if (samples.size >= 2) {
        val a = samples.first().position; val b = samples.last().position
        kotlin.math.sqrt((b.x-a.x)*(b.x-a.x) + (b.y-a.y)*(b.y-a.y) + (b.z-a.z)*(b.z-a.z))
    } else 0.0
    val meanSpeed = if (duration > 0.0) path / duration else 0.0
    val hud = AprilTagHud(
        roles = buildMap {
            if (movingDet != null) put(movingDet.id, AprilTagHudRole.MOVING)
            if (refDet != null) put(refDet.id, AprilTagHudRole.REFERENCE)
        },
        banner = if (recording) "● RECORDING · ${fmt(duration, 1)} s" else if (samples.size >= 2) "TRACK COMPLETE · NOT YET COMMITTED" else "TRACK TAGGED OBJECT",
        lines = listOf(
            "Moving #$moving ${if (movingDet?.pose != null) "✓" else "not ready"}",
            if (reference >= 0) "Reference #$reference ${if (refDet?.pose != null) "✓" else "not ready"}" else "Frame: fixed camera · DO NOT MOVE PHONE",
            currentPosition?.let { "Live XYZ ${fmt(it.x, 3)} · ${fmt(it.y, 3)} · ${fmt(it.z, 3)} m" }.orEmpty(),
            "Metric adjustment ×${fmt(state.distanceScaleValue(), 4)}",
            "Samples ${samples.size} · path ${fmt(path, 3)} m · displacement ${fmt(displacement, 3)} m",
            if (duration > 0) "Mean speed ${fmt(meanSpeed, 3)} m/s" else "",
            statusMessage
        )
    )
    AprilTagInstrumentShell(
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
            if (recording) {
                statusMessage = "Stop recording before changing tag roles."
            } else {
                val hit = frame?.let { AprilTagGeometry.detectionAt(point, it.detections) }
                if (hit == null) statusMessage = "No detected tag at that point." else if (pickRole == "reference") {
                    if (hit.id == moving) statusMessage = "Moving and reference tags must be different."
                    else { referenceId = hit.id.toString(); statusMessage = "Reference set to tag ${hit.id}." }
                } else {
                    if (hit.id == reference) statusMessage = "Moving and reference tags must be different."
                    else { movingId = hit.id.toString(); statusMessage = "Moving object set to tag ${hit.id}." }
                }
            }
        },
        instruction = when {
            recording -> "Recording 3-D positions. Keep the moving tag visible${if (reference >= 0) " together with the reference tag" else " and keep the phone completely still"}."
            samples.size >= 2 -> "Recording stopped. Review the live summary, then Commit or Clear."
            !ready -> if (reference >= 0) "Select moving/reference tags and keep both visible until both have metric pose." else "Select the moving tag and keep the phone fixed."
            else -> "Ready. Start recording to create a timestamped 3-D trajectory."
        },
        showSettings = showSettings,
        onToggleSettings = { if (!recording) showSettings = !showSettings },
        controls = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { pickRole = "moving"; statusMessage = "Tap the tag attached to the moving object." }, enabled = !recording, modifier = Modifier.weight(1f)) { Text(if (pickRole == "moving") "✓ Moving" else "Moving") }
                OutlinedButton(onClick = { pickRole = "reference"; statusMessage = "Tap the fixed reference tag." }, enabled = !recording, modifier = Modifier.weight(1f)) { Text(if (pickRole == "reference") "✓ Ref" else "Reference") }
                OutlinedButton(onClick = { referenceId = "-1"; statusMessage = "Fixed-camera mode selected. Do not move the phone while recording." }, enabled = !recording, modifier = Modifier.weight(1f)) { Text("Fixed cam") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!recording) Button(
                    onClick = { if (samples.isNotEmpty()) samplesJson = "[]"; recording = true; statusMessage = "Recording every ${interval} ms." },
                    enabled = ready,
                    modifier = Modifier.weight(1f)
                ) { Text("Start recording") }
                else Button(onClick = { recording = false; statusMessage = "Recording stopped; review summary then Commit." }, modifier = Modifier.weight(1f)) { Text("Stop recording") }
                OutlinedButton(onClick = { samplesJson = "[]"; recording = false; statusMessage = "Trajectory cleared." }, enabled = !recording && samples.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Clear") }
            }
            Button(
                onClick = {
                    val intrinsicsSource = frame?.intrinsics?.source.orEmpty()
                    val referenceFrame = if (reference >= 0) "tag:$reference" else "camera_fixed"
                    val warning = buildList {
                        frame?.intrinsics?.warning?.takeIf { it.isNotBlank() }?.let(::add)
                        if (reference < 0) add("Camera-frame trajectories are interpretable only if the camera remains physically fixed throughout recording.")
                    }.joinToString(" ")
                    onCommit(As100AprilTagTrackPoseMethod.capture(moving, reference, state.family, state.tagSizeMm.toDoubleOrNull() ?: 100.0, state.distanceScaleValue(), referenceFrame, intrinsicsSource, samples, warning))
                },
                enabled = !recording && samples.size >= 2,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Commit trajectory") }
        },
        settingsContent = {
            NumericField("Moving tag ID", movingId) { movingId = integer(it) }
            NumericField("Reference tag ID (-1 = fixed camera)", referenceId) { referenceId = signedInteger(it) }
            NumericField("Sample interval (ms)", intervalMs) { intervalMs = integer(it) }
            NumericField("Maximum samples", maxSamples) { maxSamples = integer(it) }
            BasicDetectorSettings(state, showTagSize = true, showTargetId = false)
            MetricScaleSetting(state)
            IntrinsicsSettings(state)
        }
    )
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
    onTap: ((PixelPoint) -> Unit)? = null,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    Box(modifier) {
        AprilTagCameraSurface(
            settings = settings,
            modifier = Modifier.fillMaxSize(),
            onFrame = onFrame,
            onImageTap = onTap,
            hud = hud
        )
        val errorText = when {
            !AprilTagNativeBridge.isAvailable -> "AprilTag backend unavailable"
            !frame?.error.isNullOrBlank() -> frame?.error.orEmpty()
            else -> ""
        }
        if (errorText.isNotBlank()) {
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(10.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.94f),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(errorText, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ImmersiveAprilTagTopBar(
    title: String,
    onBack: () -> Unit,
    presetStatus: String?,
    onSavePreset: (() -> Unit)?
) {
    Surface(tonalElevation = 2.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    presetStatus ?: "LIVE · tap tags/points in the camera · Commit freezes the result",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (presetStatus == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                )
            }
            if (onSavePreset != null) {
                OutlinedButton(onClick = onSavePreset) { Text("Save preset") }
            }
        }
    }
}

@Composable
private fun AprilTagInstrumentShell(
    settings: CameraSettings,
    frame: AprilTagFrame?,
    onFrame: (AprilTagFrame) -> Unit,
    hud: AprilTagHud,
    onTap: ((PixelPoint) -> Unit)?,
    instruction: String,
    showSettings: Boolean,
    onToggleSettings: () -> Unit,
    controls: @Composable () -> Unit,
    settingsContent: @Composable () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        CameraInstrument(
            settings = settings,
            frame = frame,
            onFrame = onFrame,
            hud = hud,
            onTap = onTap,
            modifier = Modifier.fillMaxSize()
        )
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.93f),
            tonalElevation = 8.dp
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text(instruction, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                controls()
                TextButton(onClick = onToggleSettings, modifier = Modifier.align(Alignment.End)) {
                    Text(if (showSettings) "Hide settings" else "Settings")
                }
            }
        }
        if (showSettings) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.985f)
            ) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("AprilTag settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        TextButton(onClick = onToggleSettings) { Text("Done") }
                    }
                    Text("Settings change the live working result. Nothing is recorded until Commit.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    settingsContent()
                }
            }
        }
    }
}

@Composable
private fun ChoiceCompactButton(label: String, value: String, choices: List<String>, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text("$label: ${value.replace('_', ' ')}") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { choice ->
                DropdownMenuItem(text = { Text(choice.replace('_', ' ')) }, onClick = { onChange(choice); expanded = false })
            }
        }
    }
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
    private val distanceScaleState: MutableState<String>,
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
    private val intrinsicsHeightState: MutableState<String>,
    private val includeDistanceScale: Boolean
) {
    var family by familyState
    var tagSizeMm by tagSizeState
    var distanceScale by distanceScaleState
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

    fun distanceScaleValue(): Double = distanceScale.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: 1.0

    fun cameraSettings(tagSizeMmOverride: Double? = null, distanceScaleOverride: Double? = null) = CameraSettings(
        family = family,
        tagSizeMm = tagSizeMmOverride ?: (tagSizeMm.toDoubleOrNull() ?: 100.0),
        distanceScale = distanceScaleOverride ?: distanceScaleValue(),
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

    fun settingsMap() = buildMap {
        put(AprilTagInputs.TAG_FAMILY, family)
        put(AprilTagInputs.TAG_SIZE_MM, tagSizeMm)
        put(AprilTagInputs.TARGET_TAG_ID, targetTagId)
        put(AprilTagInputs.THREADS, threads)
        put(AprilTagInputs.QUAD_DECIMATE, quadDecimate)
        put(AprilTagInputs.REFINE_EDGES, refineEdges.toString())
        put(AprilTagInputs.INTRINSICS_MODE, intrinsicsMode)
        put(AprilTagInputs.FX_PX, fx)
        put(AprilTagInputs.FY_PX, fy)
        put(AprilTagInputs.CX_PX, cx)
        put(AprilTagInputs.CY_PX, cy)
        put(AprilTagInputs.INTRINSICS_WIDTH_PX, intrinsicsWidth)
        put(AprilTagInputs.INTRINSICS_HEIGHT_PX, intrinsicsHeight)
        if (includeDistanceScale) put(AprilTagInputs.DISTANCE_SCALE, distanceScale)
    }
}

@Composable
private fun rememberAprilTagSettings(context: CapabilityScreenContext, includeIntrinsics: Boolean, includeDistanceScale: Boolean): AprilTagSettingsState {
    val family = rememberSaveable { mutableStateOf(context.value(AprilTagInputs.TAG_FAMILY) ?: AprilTagContractMetadata.DEFAULT_FAMILY) }
    val androidContext = LocalContext.current
    val persistedScale = remember(androidContext) { AprilTagCalibrationStore.load(androidContext).distanceScale }
    val tagSize = rememberSaveable { mutableStateOf(context.value(AprilTagInputs.TAG_SIZE_MM) ?: "100") }
    val explicitScaleText = when {
        context.request.settings.containsKey(AprilTagInputs.DISTANCE_SCALE) -> context.request.settings[AprilTagInputs.DISTANCE_SCALE]
        context.isNativePresetRun || context.presentationMode == CapabilityPresentationMode.IntentLaunch -> context.action.settings[AprilTagInputs.DISTANCE_SCALE]
        else -> null
    }
    val requestedScale = explicitScaleText?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
    val distanceScale = rememberSaveable { mutableStateOf(fmt(requestedScale ?: persistedScale, 6)) }
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
        AprilTagSettingsState(context, family, tagSize, distanceScale, targetId, threads, decimate, refine, mode, fx, fy, cx, cy, iw, ih, includeDistanceScale)
    }
    LaunchedEffect(state.family, state.tagSizeMm, state.distanceScale, state.targetTagId, state.threads, state.quadDecimate, state.refineEdges, state.intrinsicsMode, state.fx, state.fy, state.cx, state.cy, state.intrinsicsWidth, state.intrinsicsHeight) {
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
private fun MetricScaleSetting(state: AprilTagSettingsState, label: String = "Distance adjustment") {
    NumericField(label, state.distanceScale) { state.distanceScale = numeric(it) }
    Text(
        "Corrected range and X/Y/Z = raw pose × adjustment. 1.0000 means no correction.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
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
