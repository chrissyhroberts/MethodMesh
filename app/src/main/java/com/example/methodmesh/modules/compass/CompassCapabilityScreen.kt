package com.example.methodmesh.modules.compass

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.SensorManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.methodmesh.MainActivity
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.withInvocationContext
import com.example.methodmesh.core.protocols.PresetResultAction
import com.example.methodmesh.platform.camera.LiveCameraPreview
import com.example.methodmesh.platform.sensors.PhoneSensorRepository
import com.example.methodmesh.transport.OutputExportRepository
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.ReturnMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Purpose-built v1.05 compass surface.
 *
 * The live instrument remains visible while the operator works. Commit freezes a
 * canonical result; native result actions stay on this same screen. Automatic
 * callers (ODK/protocol/schedule/dependency) receive the committed result
 * immediately through the normal onConfirmed callback.
 */
object CompassCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100CompassMethod.ID
    override val title = "Compass"
    override val description = "Read a magnetic heading or sight North / a configured target bearing."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val androidContext = LocalContext.current
        val action = context.action
        val request = context.request

        var targetMode by rememberSaveable(action.settings) {
            mutableStateOf(action.settings.setting("target_mode") ?: "north")
        }
        var targetBearingText by rememberSaveable(action.settings) {
            mutableStateOf(action.settings.setting("target_bearing_deg") ?: "0")
        }
        var toleranceText by rememberSaveable(action.settings) {
            mutableStateOf(action.settings.setting("alignment_tolerance_deg") ?: "5")
        }
        var showCamera by rememberSaveable(action.settings) {
            mutableStateOf(action.settings.setting("show_camera_in_sight")?.toBooleanStrictOrNull() ?: true)
        }
        var startInSight by rememberSaveable(action.settings) {
            mutableStateOf(action.settings.setting("start_in_sight_mode")?.toBooleanStrictOrNull() ?: false)
        }

        var sightOpen by rememberSaveable(action.canonicalId) { mutableStateOf(false) }
        var initialSightHandled by rememberSaveable(action.canonicalId) { mutableStateOf(false) }
        var committedFieldsJson by rememberSaveable(action.canonicalId) { mutableStateOf<String?>(null) }
        var committedResult by remember { mutableStateOf<ExecutionResult?>(null) }
        var status by rememberSaveable(action.canonicalId) {
            mutableStateOf("Live magnetic heading. Keep the phone away from magnets and steel.")
        }
        var cameraError by rememberSaveable(action.canonicalId) { mutableStateOf("") }
        var hasCameraPermission by remember { mutableStateOf(hasCameraPermission(androidContext)) }
        var includeFullJson by rememberSaveable(action.canonicalId) { mutableStateOf(false) }
        var exportStatus by rememberSaveable(action.canonicalId) { mutableStateOf("") }
        var showTechnicalDetails by rememberSaveable(action.canonicalId) { mutableStateOf(false) }

        val cameraPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            hasCameraPermission = granted || hasCameraPermission(androidContext)
            if (!hasCameraPermission) {
                cameraError = "Camera permission denied. The sight remains available on a dark background."
            }
        }

        DisposableEffect(androidContext) {
            PhoneSensorRepository.start(androidContext)
            onDispose { PhoneSensorRepository.stop() }
        }

        LaunchedEffect(targetMode, targetBearingText, toleranceText, showCamera, startInSight) {
            context.onSettingsChanged(
                mapOf(
                    "target_mode" to targetMode,
                    "target_bearing_deg" to targetBearingText,
                    "alignment_tolerance_deg" to toleranceText,
                    "show_camera_in_sight" to showCamera.toString(),
                    "start_in_sight_mode" to startInSight.toString()
                )
            )
        }

        fun targetBearingOrNull(): Float? = if (targetMode == "bearing") {
            targetBearingText.toFloatOrNull()?.takeIf { it >= 0f && it < 360f }
        } else {
            0f
        }

        fun targetBearingForDisplay(): Float = targetBearingOrNull() ?: 0f

        fun tolerance(): Float = toleranceText.toFloatOrNull()?.coerceIn(1f, 30f) ?: 5f

        fun buildExecution(heading: Float?, viewMode: String, axis: String): ExecutionResult? {
            val targetBearing = targetBearingOrNull()
            if (targetBearing == null) {
                status = "Enter a target bearing from 0 to less than 360 degrees."
                return null
            }
            return As100CompassMethod.capture(
                request = As100CompassMethod.request(
                    action = action.canonicalId,
                    context = request.invocationContext.asMap(action.canonicalId) + action.settings + mapOf(
                        "target_mode" to targetMode,
                        "target_bearing_deg" to targetBearing.toString(),
                        "alignment_tolerance_deg" to tolerance().toString(),
                        "show_camera_in_sight" to showCamera.toString(),
                        "start_in_sight_mode" to startInSight.toString()
                    ),
                    signals = emptyList(),
                    inputs = emptyList()
                ),
                headingDegrees = heading,
                targetMode = targetMode,
                targetBearingDegrees = targetBearing,
                toleranceDegrees = tolerance(),
                viewMode = viewMode,
                headingAxis = axis,
                pitchDegrees = PhoneSensorRepository.pitchDegrees,
                rollDegrees = PhoneSensorRepository.rollDegrees,
                magnetometerAccuracy = PhoneSensorRepository.readings["magnetometer"]?.accuracy,
                invocation = request.invocationContext
            ).withInvocationContext(request.invocationContext)
        }

        fun commit(heading: Float?, viewMode: String, axis: String) {
            val execution = buildExecution(heading, viewMode, axis) ?: return
            val fields = OutputFormatter.fields(execution, includeProvenance = false)
            val error = fields[CompassFields.ERROR]?.toString().orEmpty()
            if (error.isNotBlank()) {
                status = error
                return
            }

            val compassFields = fields.filterKeys { it.startsWith("compass_") }
            committedResult = execution
            committedFieldsJson = compassFieldsToJson(compassFields)
            status = "Committed. The live compass remains active; recommit to replace this frozen reading."
            exportStatus = ""
            sightOpen = false

            // ODK, protocol, schedules and dependency runs are automatic-return
            // surfaces. Commit is the explicit boundary at which the canonical
            // payload is returned to the caller.
            if (context.submitsImmediately) onConfirmed(execution)
        }

        val restoredResult = remember(committedFieldsJson) {
            committedFieldsJson?.let(::compassFieldsFromJson)?.takeIf { it.isNotEmpty() }?.let { fields ->
                As100CompassMethod.result(
                    request = As100CompassMethod.request(
                        action = action.canonicalId,
                        context = request.invocationContext.asMap(action.canonicalId) + action.settings,
                        signals = emptyList(),
                        inputs = emptyList()
                    ),
                    values = fields,
                    invocation = request.invocationContext
                )
            }
        }
        val frozenResult = committedResult ?: restoredResult
        val frozenFields = remember(frozenResult?.request?.id?.value, committedFieldsJson) {
            frozenResult?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty()
        }
        val fullJsonText = remember(frozenResult?.request?.id?.value) {
            frozenResult?.let {
                OutputFormatter.format(
                    result = it,
                    returnMode = ReturnMode.Json,
                    includeProvenance = true,
                    payloadMode = OutputFormatter.PayloadMode.FULL
                )
            }.orEmpty()
        }

        fun openSight() {
            sightOpen = true
            if (showCamera && !hasCameraPermission) {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        fun copy(label: String, value: String) {
            if (value.isBlank()) return
            androidContext.getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText(label, value))
            Toast.makeText(androidContext, "Copied $label", Toast.LENGTH_SHORT).show()
        }

        fun resultPayload(): String {
            val beef = frozenFields[CompassFields.RESULT]?.toString().orEmpty()
            return if (includeFullJson && fullJsonText.isNotBlank()) {
                "$beef\n\n$fullJsonText"
            } else {
                beef
            }
        }

        fun shareCommitted() {
            val text = resultPayload()
            if (text.isBlank()) return
            runCatching {
                androidContext.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        },
                        "Share compass reading"
                    )
                )
            }.onFailure {
                exportStatus = "Share failed: ${it.message ?: "no sharing app available"}"
            }
        }

        fun saveCommitted() {
            val beef = frozenFields[CompassFields.RESULT]?.toString().orEmpty()
            if (beef.isBlank()) return
            runCatching {
                OutputExportRepository.saveToDownloads(
                    context = androidContext,
                    label = "Compass",
                    text = beef,
                    mediaUris = emptyList(),
                    jsonText = if (includeFullJson) fullJsonText else ""
                )
            }.onSuccess {
                exportStatus = "Saved ${it.summary}"
            }.onFailure {
                exportStatus = "Downloads save failed: ${it.message ?: "storage error"}"
            }
        }

        fun finishCommitted() {
            val execution = frozenResult ?: return
            if (!context.isNativePresetRun || !context.isLastStep) {
                onConfirmed(execution)
                return
            }

            val presetResultAction = PresetResultAction.normalize(
                request.settings["methodmesh_preset_result_action"]
                    ?: request.settings["input_methodmesh_preset_result_action"]
                    ?: PresetResultAction.HOME
            )
            val finishToLauncher = request.settings["methodmesh_finish_to_launcher"] == "true" ||
                request.settings["input_methodmesh_finish_to_launcher"] == "true"

            if (presetResultAction == PresetResultAction.SAVE) {
                saveCommitted()
                onConfirmed(execution)
            } else if (finishToLauncher) {
                onConfirmed(execution)
            } else {
                androidContext.startActivity(
                    Intent(androidContext, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }
        }

        LaunchedEffect(startInSight, initialSightHandled, frozenResult) {
            if (!initialSightHandled) {
                initialSightHandled = true
                if (startInSight && frozenResult == null) openSight()
            }
        }

        val flatHeading = PhoneSensorRepository.headingDegrees
        val target = targetBearingForDisplay()
        val tol = tolerance()
        val flatError = flatHeading?.let { CompassMath.signedErrorDegrees(target, it) }
        val flatAligned = flatHeading?.let { CompassMath.isAligned(target, it, tol) } == true
        val sensorAccuracy = PhoneSensorRepository.readings["magnetometer"]?.accuracy
        val committedSettingsChanged = frozenFields.isNotEmpty() && (
            frozenFields[CompassFields.TARGET_MODE]?.toString() != targetMode ||
                frozenFields[CompassFields.TARGET_BEARING_DEG]?.toString()?.toFloatOrNull()?.let {
                    abs(it - target) > 0.05f
                } == true ||
                frozenFields[CompassFields.TOLERANCE_DEG]?.toString()?.toFloatOrNull()?.let {
                    abs(it - tol) > 0.05f
                } == true
            )

        // Host surfaces (dashboard dialog and ExternalWorkflowActivity) already own
        // vertical scrolling. Keeping a second verticalScroll here produces an
        // infinite-height measurement crash when this screen is embedded in them.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CompassInstrument(
                headingDegrees = flatHeading,
                targetDegrees = target,
                targetMode = targetMode,
                aligned = flatAligned,
                errorDegrees = flatError,
                toleranceDegrees = tol,
                sensorAccuracy = sensorAccuracy,
                onCopyHeading = {
                    flatHeading?.let { copy("heading", format1(CompassMath.normaliseDegrees(it))) }
                },
                onCopyTarget = { copy("target bearing", format1(target)) },
                onCopyError = { flatError?.let { copy("bearing error", formatSigned1(it)) } }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { commit(flatHeading, "flat", "device_top_edge") },
                    enabled = flatHeading != null && targetBearingOrNull() != null,
                    modifier = Modifier.weight(1.25f).height(52.dp)
                ) {
                    Text(if (frozenResult == null) "Commit reading" else "Recommit reading")
                }
                OutlinedButton(
                    onClick = ::openSight,
                    modifier = Modifier.weight(0.75f).height(52.dp)
                ) {
                    Text("Sight")
                }
            }

            if (frozenResult != null && !context.submitsImmediately) {
                CommittedReadingCard(
                    fields = frozenFields,
                    includeFullJson = includeFullJson,
                    onIncludeFullJsonChanged = { includeFullJson = it },
                    settingsChanged = committedSettingsChanged,
                    exportStatus = exportStatus,
                    onTapResult = {
                        copy("compass result", frozenFields[CompassFields.RESULT]?.toString().orEmpty())
                    },
                    onCopyBundle = { copy("compass result", resultPayload()) },
                    onCopyField = ::copy,
                    onShare = ::shareCommitted,
                    onSave = ::saveCommitted,
                    onDone = ::finishCommitted,
                    showTechnicalDetails = showTechnicalDetails,
                    onShowTechnicalDetailsChanged = { showTechnicalDetails = it }
                )
            }

            CompassSettingsCard(
                context = context,
                targetMode = targetMode,
                onTargetModeChanged = { targetMode = it },
                targetBearingText = targetBearingText,
                onTargetBearingChanged = { targetBearingText = it.degreeText() },
                toleranceText = toleranceText,
                onToleranceChanged = { toleranceText = it.degreeText() },
                showCamera = showCamera,
                onShowCameraChanged = { showCamera = it },
                startInSight = startInSight,
                onStartInSightChanged = { startInSight = it }
            )

            SensorStatusCard(
                sensorAccuracy = sensorAccuracy,
                cameraError = cameraError,
                status = status
            )

            if (frozenResult == null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (context.stepNumber > 1) {
                        OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Back") }
                    }
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                        Text("Cancel")
                    }
                }
            }
        }

        if (sightOpen) {
            SightingDialog(
                showCamera = showCamera && hasCameraPermission,
                targetMode = targetMode,
                targetDegrees = targetBearingForDisplay(),
                toleranceDegrees = tolerance(),
                cameraError = cameraError,
                onCameraError = { cameraError = it },
                onClose = { sightOpen = false },
                onCommit = { heading -> commit(heading, "sight", "rear_camera_optical_axis") }
            )
        }
    }
}

private val CompassFace = Color(0xFF171613)
private val CompassFaceLift = Color(0xFF23211C)
private val CompassIvory = Color(0xFFF4EEDC)
private val CompassBrass = Color(0xFFC7A861)
private val CompassBrassMuted = Color(0xFF8C7746)
private val CompassNorth = Color(0xFFB8413B)
private val CompassSouth = Color(0xFFE6DFC9)
private val CompassAligned = Color(0xFF68C08B)
private val CompassTarget = Color(0xFF79A7D8)

@Composable
private fun CompassInstrument(
    headingDegrees: Float?,
    targetDegrees: Float,
    targetMode: String,
    aligned: Boolean,
    errorDegrees: Float?,
    toleranceDegrees: Float,
    sensorAccuracy: Int?,
    onCopyHeading: () -> Unit,
    onCopyTarget: () -> Unit,
    onCopyError: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = CompassFace)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "COMPASS",
                        color = CompassBrass,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.8.sp
                    )
                    Text(
                        if (targetMode == "north") "Magnetic north" else "Bearing target ${format1(targetDegrees)}°",
                        color = CompassIvory.copy(alpha = 0.68f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = when (sensorAccuracy) {
                        SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> CompassAligned.copy(alpha = 0.18f)
                        SensorManager.SENSOR_STATUS_UNRELIABLE -> CompassNorth.copy(alpha = 0.20f)
                        else -> CompassIvory.copy(alpha = 0.08f)
                    }
                ) {
                    Text(
                        sensorAccuracyLabel(sensorAccuracy).uppercase(Locale.US),
                        color = CompassIvory.copy(alpha = 0.86f),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                headingDegrees?.let(CompassMath::headingLabel) ?: "—°",
                modifier = Modifier.clickable(enabled = headingDegrees != null, onClick = onCopyHeading),
                color = CompassIvory,
                fontSize = 38.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                errorDegrees?.let { CompassMath.alignmentInstruction(it, toleranceDegrees) }
                    ?: "Waiting for orientation sensor",
                color = if (aligned) CompassAligned else CompassIvory.copy(alpha = 0.68f),
                fontWeight = if (aligned) FontWeight.Bold else FontWeight.Normal
            )

            Spacer(Modifier.height(8.dp))
            Box(Modifier.size(282.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val centre = center
                    val radius = size.minDimension * 0.455f
                    val innerRadius = radius * 0.81f

                    drawCircle(CompassFaceLift, radius + 10f, centre)
                    drawCircle(CompassBrassMuted, radius + 2f, centre, style = Stroke(width = 4f))
                    drawCircle(CompassBrass.copy(alpha = 0.52f), innerRadius, centre, style = Stroke(width = 2f))

                    for (degree in 0 until 360 step 5) {
                        val radians = Math.toRadians((degree - 90).toDouble())
                        val major = degree % 30 == 0
                        val medium = !major && degree % 10 == 0
                        val tick = when {
                            major -> 23f
                            medium -> 15f
                            else -> 8f
                        }
                        val outer = Offset(
                            centre.x + cos(radians).toFloat() * radius,
                            centre.y + sin(radians).toFloat() * radius
                        )
                        val inner = Offset(
                            centre.x + cos(radians).toFloat() * (radius - tick),
                            centre.y + sin(radians).toFloat() * (radius - tick)
                        )
                        drawLine(
                            color = if (major) CompassBrass else CompassIvory.copy(alpha = if (medium) 0.58f else 0.30f),
                            start = inner,
                            end = outer,
                            strokeWidth = if (major) 3.2f else if (medium) 2.2f else 1.25f,
                            cap = StrokeCap.Round
                        )
                    }

                    headingDegrees?.let { heading ->
                        // The magnetic needle is free relative to the device case:
                        // its north tip therefore rotates by -heading from the phone's top edge.
                        rotate(-heading, pivot = centre) {
                            val northTip = Offset(centre.x, centre.y - innerRadius + 20f)
                            val southTip = Offset(centre.x, centre.y + innerRadius - 20f)
                            val northNeedle = Path().apply {
                                moveTo(centre.x - 11f, centre.y + 10f)
                                lineTo(northTip.x, northTip.y)
                                lineTo(centre.x + 11f, centre.y + 10f)
                                close()
                            }
                            val southNeedle = Path().apply {
                                moveTo(centre.x - 9f, centre.y - 8f)
                                lineTo(southTip.x, southTip.y)
                                lineTo(centre.x + 9f, centre.y - 8f)
                                close()
                            }
                            drawPath(northNeedle, CompassNorth)
                            drawPath(southNeedle, CompassSouth.copy(alpha = 0.88f))
                        }

                        // Target bug: signed error is target relative to the current phone heading.
                        rotate(CompassMath.signedErrorDegrees(targetDegrees, heading), pivot = centre) {
                            drawLine(
                                color = if (aligned) CompassAligned else CompassTarget,
                                start = Offset(centre.x, centre.y - innerRadius + 8f),
                                end = Offset(centre.x, centre.y - radius + 3f),
                                strokeWidth = 7f,
                                cap = StrokeCap.Round
                            )
                        }
                    }

                    drawCircle(CompassBrass, 14f, centre)
                    drawCircle(CompassFace, 7f, centre)

                    // Fixed lubber line / direction-of-travel marker at the phone's top edge.
                    val marker = Path().apply {
                        moveTo(centre.x, centre.y - radius - 3f)
                        lineTo(centre.x - 10f, centre.y - radius + 15f)
                        lineTo(centre.x + 10f, centre.y - radius + 15f)
                        close()
                    }
                    drawPath(marker, CompassBrass)
                }

                Text("N", color = CompassNorth, modifier = Modifier.align(Alignment.TopCenter).padding(top = 25.dp), fontWeight = FontWeight.Black, fontSize = 19.sp)
                Text("E", color = CompassIvory, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 28.dp), fontWeight = FontWeight.Bold)
                Text("S", color = CompassIvory, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 25.dp), fontWeight = FontWeight.Bold)
                Text("W", color = CompassIvory, modifier = Modifier.align(Alignment.CenterStart).padding(start = 27.dp), fontWeight = FontWeight.Bold)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DarkValueTile(
                    label = "HEADING",
                    value = headingDegrees?.let { "${format1(CompassMath.normaliseDegrees(it))}°" } ?: "—",
                    modifier = Modifier.weight(1f),
                    enabled = headingDegrees != null,
                    onClick = onCopyHeading
                )
                DarkValueTile(
                    label = "TARGET",
                    value = "${format1(targetDegrees)}°",
                    modifier = Modifier.weight(1f),
                    enabled = true,
                    onClick = onCopyTarget
                )
                DarkValueTile(
                    label = "ERROR",
                    value = errorDegrees?.let { "${formatSigned1(it)}°" } ?: "—",
                    modifier = Modifier.weight(1f),
                    enabled = errorDegrees != null,
                    onClick = onCopyError,
                    accent = if (aligned) CompassAligned else CompassIvory
                )
            }
            Text(
                "Tap any live value to copy it.",
                color = CompassIvory.copy(alpha = 0.46f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun DarkValueTile(
    label: String,
    value: String,
    modifier: Modifier,
    enabled: Boolean,
    onClick: () -> Unit,
    accent: Color = CompassIvory
) {
    Surface(
        modifier = modifier
            .border(1.dp, CompassBrassMuted.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = Color.Black.copy(alpha = 0.16f)
    ) {
        Column(Modifier.padding(horizontal = 9.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = CompassBrass, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text(value, color = accent, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun CommittedReadingCard(
    fields: Map<String, Any?>,
    includeFullJson: Boolean,
    onIncludeFullJsonChanged: (Boolean) -> Unit,
    settingsChanged: Boolean,
    exportStatus: String,
    onTapResult: () -> Unit,
    onCopyBundle: () -> Unit,
    onCopyField: (String, String) -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onDone: () -> Unit,
    showTechnicalDetails: Boolean,
    onShowTechnicalDetailsChanged: (Boolean) -> Unit
) {
    val result = fields[CompassFields.RESULT]?.toString().orEmpty()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.40f))
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("COMMITTED", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(
                        result.ifBlank { "Compass reading committed" },
                        modifier = Modifier.clickable(enabled = result.isNotBlank(), onClick = onTapResult),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
                    Text("✓", modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp), fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                }
            }

            if (settingsChanged) {
                Text(
                    "Settings changed since this commit. The committed payload is still frozen; recommit to replace it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CommittedValue(
                    "Heading",
                    fields[CompassFields.HEADING_DEG]?.toString().orEmpty(),
                    "°",
                    Modifier.weight(1f),
                    onCopyField
                )
                CommittedValue(
                    "Bearing",
                    fields[CompassFields.TARGET_BEARING_DEG]?.toString().orEmpty(),
                    "°",
                    Modifier.weight(1f),
                    onCopyField
                )
                CommittedValue(
                    "Error",
                    fields[CompassFields.ERROR_DEG]?.toString().orEmpty(),
                    "°",
                    Modifier.weight(1f),
                    onCopyField
                )
            }

            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Include full JSON", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (includeFullJson) "Copy, share and save include the full auditable payload." else "Actions use only the main compass result.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = includeFullJson, onCheckedChange = onIncludeFullJsonChanged)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCopyBundle, modifier = Modifier.weight(1f)) { Text("Copy") }
                OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f)) { Text("Share") }
                OutlinedButton(onClick = onSave, modifier = Modifier.weight(1f)) { Text("Save") }
            }
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Done") }

            if (exportStatus.isNotBlank()) {
                Text(exportStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }

            OutlinedButton(
                onClick = { onShowTechnicalDetailsChanged(!showTechnicalDetails) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (showTechnicalDetails) "Hide technical details" else "Technical details")
            }
            if (showTechnicalDetails) {
                TechnicalDetails(fields = fields, onCopyField = onCopyField)
            }
        }
    }
}

@Composable
private fun CommittedValue(
    label: String,
    rawValue: String,
    suffix: String,
    modifier: Modifier,
    onCopyField: (String, String) -> Unit
) {
    Surface(
        modifier = modifier.clickable(enabled = rawValue.isNotBlank()) { onCopyField(label.lowercase(Locale.US), rawValue) },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                if (rawValue.isBlank()) "—" else rawValue + suffix,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun TechnicalDetails(fields: Map<String, Any?>, onCopyField: (String, String) -> Unit) {
    val rows = listOf(
        "Cardinal" to CompassFields.CARDINAL,
        "Target mode" to CompassFields.TARGET_MODE,
        "View mode" to CompassFields.VIEW_MODE,
        "Heading axis" to CompassFields.HEADING_AXIS,
        "North reference" to CompassFields.NORTH_REFERENCE,
        "Pitch" to CompassFields.PITCH_DEG,
        "Roll" to CompassFields.ROLL_DEG,
        "Sensor accuracy" to CompassFields.MAGNETOMETER_ACCURACY,
        "Captured" to CompassFields.CAPTURED_TIME_ISO,
        "Audit JSON" to CompassFields.AUDIT_JSON
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { (label, key) ->
            val value = fields[key]?.toString().orEmpty()
            if (value.isNotBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { onCopyField(label.lowercase(Locale.US), value) },
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(0.35f))
                        Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(0.65f))
                    }
                }
            }
        }
        Text("Tap any technical value to copy it.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CompassSettingsCard(
    context: CapabilityScreenContext,
    targetMode: String,
    onTargetModeChanged: (String) -> Unit,
    targetBearingText: String,
    onTargetBearingChanged: (String) -> Unit,
    toleranceText: String,
    onToleranceChanged: (String) -> Unit,
    showCamera: Boolean,
    onShowCameraChanged: (Boolean) -> Unit,
    startInSight: Boolean,
    onStartInSightChanged: (Boolean) -> Unit
) {
    val anySettingsVisible = context.settingShouldBeShown("target_mode") ||
        (targetMode == "bearing" && context.settingShouldBeShown("target_bearing_deg")) ||
        context.settingShouldBeShown("alignment_tolerance_deg") ||
        context.settingShouldBeShown("show_camera_in_sight") ||
        context.settingShouldBeShown("start_in_sight_mode")
    if (!anySettingsVisible) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f))
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

            if (context.settingShouldBeShown("target_mode")) {
                Text("Target", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeButton("North", targetMode == "north", Modifier.weight(1f)) { onTargetModeChanged("north") }
                    ModeButton("Bearing", targetMode == "bearing", Modifier.weight(1f)) { onTargetModeChanged("bearing") }
                }
            }

            if (targetMode == "bearing" && context.settingShouldBeShown("target_bearing_deg")) {
                OutlinedTextField(
                    value = targetBearingText,
                    onValueChange = onTargetBearingChanged,
                    label = { Text("Target bearing") },
                    suffix = { Text("°") },
                    supportingText = { Text("0–359.9°") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            if (context.settingShouldBeShown("alignment_tolerance_deg")) {
                val toleranceValue = toleranceText.toFloatOrNull()?.coerceIn(1f, 30f) ?: 5f
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Alignment tolerance", style = MaterialTheme.typography.labelLarge)
                        Text("±${toleranceValue.toInt()}°", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                    }
                    Slider(
                        value = toleranceValue,
                        onValueChange = { onToleranceChanged(it.toInt().coerceIn(1, 30).toString()) },
                        valueRange = 1f..30f,
                        steps = 28
                    )
                    Text(
                        "The target marker counts as aligned inside this angular window.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (context.settingShouldBeShown("show_camera_in_sight")) {
                SettingSwitchRow(
                    title = "Camera sight",
                    detail = "Show the rear camera behind the full-screen sighting reticle.",
                    checked = showCamera,
                    onCheckedChange = onShowCameraChanged
                )
            }

            if (context.settingShouldBeShown("start_in_sight_mode")) {
                SettingSwitchRow(
                    title = "Start in sight mode",
                    detail = "Useful for presets that should open directly into the sight.",
                    checked = startInSight,
                    onCheckedChange = onStartInSightChanged
                )
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SensorStatusCard(sensorAccuracy: Int?, cameraError: String, status: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f)
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Magnetic sensor · ${sensorAccuracyLabel(sensorAccuracy)} accuracy",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (cameraError.isNotBlank()) {
                Text(cameraError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ModeButton(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) { Text("✓ $label") }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(label) }
    }
}

@Composable
private fun SightingDialog(
    showCamera: Boolean,
    targetMode: String,
    targetDegrees: Float,
    toleranceDegrees: Float,
    cameraError: String,
    onCameraError: (String) -> Unit,
    onClose: () -> Unit,
    onCommit: (Float?) -> Unit
) {
    val heading = PhoneSensorRepository.rearCameraHeadingDegrees
    val error = heading?.let { CompassMath.signedErrorDegrees(targetDegrees, it) }
    val aligned = heading?.let { CompassMath.isAligned(targetDegrees, it, toleranceDegrees) } == true
    val targetLabel = if (targetMode == "north") "NORTH" else "TARGET ${format1(targetDegrees)}°"

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (showCamera) {
                LiveCameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    onError = onCameraError
                )
            }

            Canvas(Modifier.fillMaxSize()) {
                val centre = center
                val ringRadius = size.minDimension * 0.105f
                val ringColor = if (aligned) CompassAligned else Color.White
                drawCircle(Color.Black.copy(alpha = 0.30f), ringRadius + 18f, centre)
                drawCircle(ringColor, ringRadius, centre, style = Stroke(width = 10f))
                drawCircle(ringColor, 5f, centre)
                drawLine(
                    ringColor,
                    Offset(centre.x - ringRadius - 38f, centre.y),
                    Offset(centre.x - ringRadius - 12f, centre.y),
                    strokeWidth = 5f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    ringColor,
                    Offset(centre.x + ringRadius + 12f, centre.y),
                    Offset(centre.x + ringRadius + 38f, centre.y),
                    strokeWidth = 5f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    ringColor.copy(alpha = 0.65f),
                    Offset(centre.x, centre.y - ringRadius - 38f),
                    Offset(centre.x, centre.y - ringRadius - 12f),
                    strokeWidth = 4f,
                    cap = StrokeCap.Round
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(20.dp), color = Color.Black.copy(alpha = 0.58f)) {
                        Text(targetLabel, color = CompassBrass, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                    OutlinedButton(onClick = onClose) { Text("Close") }
                }
                Spacer(Modifier.height(12.dp))
                Surface(shape = RoundedCornerShape(24.dp), color = Color.Black.copy(alpha = 0.58f)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                        Text(
                            heading?.let(CompassMath::headingLabel) ?: "Hold phone upright",
                            color = Color.White,
                            fontSize = 25.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            error?.let { CompassMath.alignmentInstruction(it, toleranceDegrees) }
                                ?: "Sighting axis not available yet",
                            color = if (aligned) CompassAligned else Color.White.copy(alpha = 0.84f)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!showCamera) {
                    Text("Dark sighting mode", color = Color.White.copy(alpha = 0.72f))
                }
                if (cameraError.isNotBlank()) {
                    Text(cameraError, color = Color.White.copy(alpha = 0.80f), style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { onCommit(heading) },
                    enabled = heading != null,
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text(if (aligned) "Commit aligned reading" else "Commit reading")
                }
            }
        }
    }
}

private fun hasCameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun sensorAccuracyLabel(value: Int?): String = when (value) {
    SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "high"
    SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "medium"
    SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "low"
    SensorManager.SENSOR_STATUS_UNRELIABLE -> "unreliable"
    else -> "unknown"
}

private fun format1(value: Float): String = String.format(Locale.US, "%.1f", value)

private fun formatSigned1(value: Float): String = String.format(Locale.US, "%+.1f", value)

private fun String.degreeText(): String = filter { it.isDigit() || it == '.' }.let { filtered ->
    filtered.split('.').let { parts ->
        parts.first() + if (parts.size > 1) "." + parts.drop(1).joinToString("") else ""
    }
}

private fun Map<String, String>.setting(key: String): String? =
    (this[key] ?: this["input_$key"])?.takeIf { it.isNotBlank() }

private fun compassFieldsToJson(values: Map<String, Any?>): String =
    JSONObject().apply {
        values.toSortedMap().forEach { (key, value) -> put(key, value?.toString().orEmpty()) }
    }.toString()

private fun compassFieldsFromJson(json: String): Map<String, String> = runCatching {
    val root = JSONObject(json.ifBlank { "{}" })
    buildMap {
        root.keys().forEach { key -> put(key, root.optString(key)) }
    }
}.getOrDefault(emptyMap())
