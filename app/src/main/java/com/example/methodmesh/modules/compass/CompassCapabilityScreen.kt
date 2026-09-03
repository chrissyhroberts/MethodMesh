package com.example.methodmesh.modules.compass

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.SensorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.withInvocationContext
import com.example.methodmesh.platform.camera.LiveCameraPreview
import com.example.methodmesh.platform.sensors.PhoneSensorRepository
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import org.json.JSONObject
import kotlin.math.cos
import kotlin.math.sin

object CompassCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100CompassMethod.ID
    override val title = "Compass"
    override val description = "Read a bearing or sight North / a configured target bearing."

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
        val startInSight = action.settings.setting("start_in_sight_mode")?.toBooleanStrictOrNull() ?: false

        var sightOpen by rememberSaveable(action.canonicalId) { mutableStateOf(false) }
        var resultFieldsJson by rememberSaveable(action.canonicalId) { mutableStateOf<String?>(null) }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var status by rememberSaveable(action.canonicalId) { mutableStateOf("Move away from magnets or metal for the best reading.") }
        var cameraError by rememberSaveable(action.canonicalId) { mutableStateOf("") }
        var hasCameraPermission by remember { mutableStateOf(hasCameraPermission(androidContext)) }

        val cameraPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            hasCameraPermission = granted || hasCameraPermission(androidContext)
            if (!hasCameraPermission) {
                cameraError = "Camera permission denied. Sighting remains available on a dark background."
            }
        }

        DisposableEffect(androidContext) {
            PhoneSensorRepository.start(androidContext)
            onDispose { PhoneSensorRepository.stop() }
        }

        LaunchedEffect(targetMode, targetBearingText, toleranceText, showCamera) {
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

        fun capture(heading: Float?, viewMode: String, axis: String) {
            val targetBearing = targetBearingOrNull()
            if (targetBearing == null) {
                status = "Enter a target bearing from 0 to less than 360 degrees."
                return
            }
            val execution = As100CompassMethod.capture(
                request = As100CompassMethod.request(
                    action = action.canonicalId,
                    context = request.invocationContext.asMap(action.canonicalId) + action.settings + mapOf(
                        "target_mode" to targetMode,
                        "target_bearing_deg" to targetBearing.toString(),
                        "alignment_tolerance_deg" to tolerance().toString()
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

            val fields = OutputFormatter.fields(execution, includeProvenance = false)
            status = fields[CompassFields.ERROR]?.toString().orEmpty().ifBlank {
                fields[CompassFields.RESULT]?.toString().orEmpty().ifBlank { "Bearing captured." }
            }
            if (context.submitsImmediately) {
                sightOpen = false
                onConfirmed(execution)
            } else {
                result = execution
                resultFieldsJson = compassFieldsToJson(fields.filterKeys { it.startsWith("compass_") })
            }
        }

        val restoredResult = remember(resultFieldsJson) {
            resultFieldsJson?.let(::compassFieldsFromJson)?.let { fields ->
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
        val capturedResult = result ?: restoredResult

        fun openSight() {
            sightOpen = true
            if (showCamera && !hasCameraPermission) {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        LaunchedEffect(startInSight, capturedResult) {
            if (startInSight && capturedResult == null && !sightOpen) openSight()
        }

        CapabilityScreenScaffold(
            title = title,
            capabilityId = action.canonicalId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = capturedResult,
            resultPreview = capturedResult?.let { execution ->
                val fields = OutputFormatter.fields(execution, includeProvenance = false)
                mapOf(CompassFields.RESULT to fields[CompassFields.RESULT]?.toString().orEmpty())
            }.orEmpty(),
            onBack = onBack,
            onRetry = {
                result = null
                resultFieldsJson = null
                status = "Ready for another reading."
            },
            onConfirm = { capturedResult?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            val flatHeading = PhoneSensorRepository.headingDegrees
            val target = targetBearingForDisplay()
            val tol = tolerance()
            val flatError = flatHeading?.let { CompassMath.signedErrorDegrees(target, it) }
            val flatAligned = flatHeading?.let { CompassMath.isAligned(target, it, tol) } == true

            if (context.settingShouldBeShown("target_mode")) {
                Text("Target", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth()) {
                    ModeButton("North", targetMode == "north", Modifier.weight(1f)) { targetMode = "north" }
                    ModeButton("Bearing", targetMode == "bearing", Modifier.weight(1f)) { targetMode = "bearing" }
                }
                Spacer(Modifier.height(6.dp))
            }

            if (targetMode == "bearing" && context.settingShouldBeShown("target_bearing_deg")) {
                OutlinedTextField(
                    value = targetBearingText,
                    onValueChange = { targetBearingText = it.degreeText() },
                    label = { Text("Target bearing (degrees)") },
                    supportingText = { Text("0–359.9°") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(6.dp))
            }

            if (context.settingShouldBeShown("alignment_tolerance_deg")) {
                OutlinedTextField(
                    value = toleranceText,
                    onValueChange = { toleranceText = it.degreeText() },
                    label = { Text("Green-zone tolerance (± degrees)") },
                    supportingText = { Text("Default ±5°. Wider values are easier to hold by hand.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(10.dp))
            }

            CompassCard(
                headingDegrees = flatHeading,
                targetDegrees = target,
                aligned = flatAligned,
                errorDegrees = flatError,
                toleranceDegrees = tol
            )

            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                Button(
                    onClick = { capture(flatHeading, "flat", "device_top_edge") },
                    enabled = flatHeading != null,
                    modifier = Modifier.weight(1f).padding(end = 4.dp)
                ) { Text("Capture bearing") }
                Button(
                    onClick = ::openSight,
                    modifier = Modifier.weight(1f).padding(start = 4.dp)
                ) { Text("Sight target") }
            }

            if (context.settingShouldBeShown("show_camera_in_sight")) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (showCamera) "Camera background on" else "Camera background off",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = { showCamera = !showCamera }) {
                        Text(if (showCamera) "Use dark sight" else "Use camera")
                    }
                }
            }

            val sensorAccuracy = PhoneSensorRepository.readings["magnetometer"]?.accuracy
            Text(
                "Magnetic north · sensor accuracy ${sensorAccuracyLabel(sensorAccuracy)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (cameraError.isNotBlank()) {
                Text(cameraError, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(6.dp))
            Text(status, style = MaterialTheme.typography.bodySmall)
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
                onCapture = { heading -> capture(heading, "sight", "rear_camera_optical_axis") }
            )
        }
    }
}

@Composable
private fun ModeButton(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier.padding(2.dp)) { Text("✓ $label") }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier.padding(2.dp)) { Text(label) }
    }
}

@Composable
private fun CompassCard(
    headingDegrees: Float?,
    targetDegrees: Float,
    aligned: Boolean,
    errorDegrees: Float?,
    toleranceDegrees: Float
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                headingDegrees?.let(CompassMath::headingLabel) ?: "—°",
                fontSize = 34.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                errorDegrees?.let { CompassMath.alignmentInstruction(it, toleranceDegrees) } ?: "Waiting for orientation sensor",
                color = if (aligned) Color(0xFF178A45) else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (aligned) FontWeight.Bold else FontWeight.Normal
            )
            Spacer(Modifier.height(12.dp))
            Box(Modifier.size(250.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val radius = size.minDimension * 0.43f
                    val centre = center
                    drawCircle(
                        color = Color.Gray.copy(alpha = 0.35f),
                        radius = radius,
                        center = centre,
                        style = Stroke(width = 3f)
                    )
                    for (degree in 0 until 360 step 15) {
                        val radians = Math.toRadians((degree - 90).toDouble())
                        val major = degree % 45 == 0
                        val outer = Offset(
                            centre.x + cos(radians).toFloat() * radius,
                            centre.y + sin(radians).toFloat() * radius
                        )
                        val innerRadius = radius - if (major) 22f else 12f
                        val inner = Offset(
                            centre.x + cos(radians).toFloat() * innerRadius,
                            centre.y + sin(radians).toFloat() * innerRadius
                        )
                        drawLine(
                            color = Color.Gray.copy(alpha = if (major) 0.75f else 0.4f),
                            start = inner,
                            end = outer,
                            strokeWidth = if (major) 4f else 2f,
                            cap = StrokeCap.Round
                        )
                    }
                    headingDegrees?.let { heading ->
                        rotate(-heading, pivot = centre) {
                            drawLine(
                                color = Color(0xFFD64545),
                                start = centre,
                                end = Offset(centre.x, centre.y - radius + 30f),
                                strokeWidth = 8f,
                                cap = StrokeCap.Round
                            )
                            drawCircle(Color(0xFFD64545), 9f, Offset(centre.x, centre.y - radius + 30f))
                        }
                        rotate(CompassMath.signedErrorDegrees(targetDegrees, heading), pivot = centre) {
                            drawLine(
                                color = if (aligned) Color(0xFF2DBE68) else Color(0xFF3478F6),
                                start = centre,
                                end = Offset(centre.x, centre.y - radius + 55f),
                                strokeWidth = 5f,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                    drawCircle(
                        color = if (aligned) Color(0xFF2DBE68) else Color.Gray.copy(alpha = 0.45f),
                        radius = 15f,
                        center = centre,
                        style = Stroke(width = 5f)
                    )
                }
                Text("N", modifier = Modifier.align(Alignment.TopCenter).padding(top = 7.dp), fontWeight = FontWeight.Bold)
                Text("E", modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp), fontWeight = FontWeight.Bold)
                Text("S", modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 7.dp), fontWeight = FontWeight.Bold)
                Text("W", modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp), fontWeight = FontWeight.Bold)
            }
        }
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
    onCapture: (Float?) -> Unit
) {
    val heading = PhoneSensorRepository.rearCameraHeadingDegrees
    val error = heading?.let { CompassMath.signedErrorDegrees(targetDegrees, it) }
    val aligned = heading?.let { CompassMath.isAligned(targetDegrees, it, toleranceDegrees) } == true
    val targetLabel = if (targetMode == "north") "NORTH" else "TARGET ${targetDegrees.toInt()}°"

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
                val ringRadius = size.minDimension * 0.10f
                val ringColor = if (aligned) Color(0xFF35D16F) else Color.White
                drawCircle(
                    color = Color.Black.copy(alpha = 0.28f),
                    radius = ringRadius + 14f,
                    center = centre
                )
                drawCircle(
                    color = ringColor,
                    radius = ringRadius,
                    center = centre,
                    style = Stroke(width = 12f)
                )
                drawCircle(
                    color = ringColor,
                    radius = 5f,
                    center = centre
                )
                drawLine(
                    color = ringColor,
                    start = Offset(centre.x - ringRadius - 34f, centre.y),
                    end = Offset(centre.x - ringRadius - 10f, centre.y),
                    strokeWidth = 6f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = ringColor,
                    start = Offset(centre.x + ringRadius + 10f, centre.y),
                    end = Offset(centre.x + ringRadius + 34f, centre.y),
                    strokeWidth = 6f,
                    cap = StrokeCap.Round
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(20.dp), color = Color.Black.copy(alpha = 0.55f)) {
                        Text(targetLabel, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                    OutlinedButton(onClick = onClose) { Text("Close") }
                }
                Spacer(Modifier.height(12.dp))
                Surface(shape = RoundedCornerShape(24.dp), color = Color.Black.copy(alpha = 0.55f)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
                        Text(
                            heading?.let(CompassMath::headingLabel) ?: "Hold phone upright",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            error?.let { CompassMath.alignmentInstruction(it, toleranceDegrees) }
                                ?: "Sighting axis not available yet",
                            color = if (aligned) Color(0xFF54E88C) else Color.White
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!showCamera) {
                    Text("Dark sighting mode", color = Color.White.copy(alpha = 0.8f))
                }
                if (cameraError.isNotBlank()) {
                    Text(cameraError, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { onCapture(heading) },
                    enabled = heading != null,
                    modifier = Modifier.fillMaxWidth().height(54.dp)
                ) {
                    Text(if (aligned) "Capture aligned bearing" else "Capture bearing")
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
