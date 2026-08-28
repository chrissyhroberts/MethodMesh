package com.example.methodmesh.modules.spatialgeometry

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.withInvocationContext
import com.example.methodmesh.platform.sensors.PhoneSensorRepository
import com.example.methodmesh.settings.MethodSetting
import com.example.methodmesh.settings.SettingsState
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec

private enum class GeometryMode(val method: SpatialGeometryMethod, val title: String, val description: String, val fields: List<Pair<String, String>>) {
    Tree(As100TreeHeightMethod, "Height estimator", "Stand at a measured horizontal distance. Capture the base and top sightlines with the phone held along each sightline.", listOf(SpatialGeometryFields.HORIZONTAL_DISTANCE_M to "Horizontal distance (m)", SpatialGeometryFields.OBSERVER_HEIGHT_M to "Eye height (m)", SpatialGeometryFields.BASE_ANGLE_DEG to "Base angle (degrees)", SpatialGeometryFields.TOP_ANGLE_DEG to "Top angle (degrees)")),
    Slope(As100SlopeInclinationMethod, "Slope and inclination", "Capture top/bottom inclination and left/right tilt independently.", listOf(SpatialGeometryFields.SLOPE_ANGLE_DEG to "Top/bottom inclination (degrees)", SpatialGeometryFields.TILT_ANGLE_DEG to "Left/right tilt (degrees)")),
    Distance(As100GeometryDistanceMethod, "Distance from known reference", "Estimate distance when a known-height object fills a measurable angular span in the camera view.", listOf(SpatialGeometryFields.REFERENCE_HEIGHT_M to "Reference height (m)", SpatialGeometryFields.ANGULAR_SIZE_DEG to "Angular size (degrees)"))
}

@Composable
private fun GeometryScreen(context: CapabilityScreenContext, mode: GeometryMode, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
    val settingsDefinition = when (mode) {
        GeometryMode.Tree -> SpatialGeometryModule.capabilitySettings()[As100TreeHeightMethod.id].orEmpty()
        GeometryMode.Slope -> SpatialGeometryModule.capabilitySettings()[As100SlopeInclinationMethod.id].orEmpty()
        GeometryMode.Distance -> SpatialGeometryModule.capabilitySettings()[As100GeometryDistanceMethod.id].orEmpty()
    }
    val settings = remember(context.action.settings, mode) {
        SettingsState(settingsDefinition) { key, value ->
            context.onSettingsChanged(mapOf(key to value.toString()))
        }.also { applyParameters(it, settingsDefinition, context.request.settings + context.action.settings) }
    }
    var result by remember { mutableStateOf<ExecutionResult?>(null) }
    var status by remember { mutableStateOf("Ready.") }
    var values by remember(mode) { mutableStateOf(mode.fields.associate { it.first to settingsValue(settings, it.first) }) }
    val sensorContext = LocalContext.current
    var cameraEnabled by remember { mutableStateOf(false) }
    var cameraFullscreen by remember { mutableStateOf(false) }
    var cameraPermission by remember { mutableStateOf(hasCameraPermission(sensorContext)) }
    var distanceSightline1 by remember { mutableStateOf<Float?>(null) }
    var distanceSightline2 by remember { mutableStateOf<Float?>(null) }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraPermission = granted
        cameraEnabled = granted
    }

    DisposableEffect(sensorContext) {
        PhoneSensorRepository.start(sensorContext)
        onDispose { PhoneSensorRepository.stop() }
    }

    fun capture(key: String) {
        val sensorReading = if (key == SpatialGeometryFields.TILT_ANGLE_DEG) {
            PhoneSensorRepository.rollDegrees
        } else {
            PhoneSensorRepository.pitchDegrees
        }
        val reading = sensorReading?.let {
            // The phone pitch is zeroed at the downward-facing position and
            // reaches -90° at the horizon. Geometry calculations use the
            // conventional elevation angle measured from the horizon.
            if (key == SpatialGeometryFields.BASE_ANGLE_DEG || key == SpatialGeometryFields.TOP_ANGLE_DEG) {
                (it + 90f).coerceIn(-89.9f, 89.9f)
            } else it
        }
        if (reading == null) {
            status = "Waiting for the phone orientation sensor…"
        } else {
            values = values.toMutableMap().apply { put(key, "%.2f".format(java.util.Locale.US, reading)) }
            settings.setFloat(key, reading)
            val label = when (key) {
                SpatialGeometryFields.TOP_ANGLE_DEG -> "top sightline"
                SpatialGeometryFields.BASE_ANGLE_DEG -> "base sightline"
                SpatialGeometryFields.TILT_ANGLE_DEG -> "left/right tilt"
                else -> "top/bottom inclination"
            }
            status = "Captured ${"%.2f".format(java.util.Locale.US, reading)}° for $label."
        }
    }

    fun captureDistanceSightline(lower: Boolean) {
        val pitch = PhoneSensorRepository.pitchDegrees
        if (pitch == null) {
            status = "Waiting for the phone orientation sensor…"
            return
        }
        val elevation = sightlineElevation(pitch)
        if (lower) {
            distanceSightline1 = elevation
            status = "Lower sightline captured at %.2f°. Aim at the upper point.".format(java.util.Locale.US, elevation)
        } else {
            distanceSightline2 = elevation
            val first = distanceSightline1
            if (first == null) {
                status = "Capture the lower sightline first."
            } else {
                val angularSize = kotlin.math.abs(elevation - first)
                values = values.toMutableMap().apply { put(SpatialGeometryFields.ANGULAR_SIZE_DEG, "%.2f".format(java.util.Locale.US, angularSize)) }
                settings.setFloat(SpatialGeometryFields.ANGULAR_SIZE_DEG, angularSize)
                status = "Upper sightline captured. Angular size %.2f° is ready.".format(java.util.Locale.US, angularSize)
            }
        }
    }

    fun execute() {
        status = "Calculating measurement…"
        values.forEach { (key, value) -> value.toFloatOrNull()?.let { settings.setFloat(key, it) } }
        val request = mode.method.request(mode.method.id, context.request.invocationContext.asMap(mode.method.id) + values)
        val execution = mode.method.execute(request, settings, context.request.source).withInvocationContext(context.request.invocationContext)
        result = execution
    }

    CapabilityScreenScaffold(mode.title, mode.method.id, context, context.stepNumber > 1, result, result?.let { OutputFormatter.fields(it, false) }.orEmpty(), onBack, { result = null }, { result?.let(onConfirmed) }, onCancel) {
        Column(Modifier.fillMaxWidth()) {
            Text(mode.description, style = MaterialTheme.typography.bodyLarge)
            when (mode) {
                GeometryMode.Slope -> InclinationPreview(PhoneSensorRepository.pitchDegrees, PhoneSensorRepository.rollDegrees)
                GeometryMode.Tree, GeometryMode.Distance -> {
                    SightlineCameraPreview(
                        enabled = cameraEnabled && cameraPermission && !cameraFullscreen,
                        onEnable = {
                            if (cameraPermission) {
                                cameraEnabled = !cameraEnabled
                                status = if (cameraEnabled) "Camera started." else "Camera stopped."
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                status = "Requesting camera permission…"
                            }
                        },
                        angle = PhoneSensorRepository.pitchDegrees?.let { if (mode == GeometryMode.Tree) sightlineElevation(it) else it },
                        caption = if (mode == GeometryMode.Distance) "Live pitch — aim at a sightline" else "Live elevation — aim at target",
                        restartKey = mode,
                        onOpenFullscreen = { cameraFullscreen = true; status = "Full-screen camera opened." },
                        captureActions = if (mode == GeometryMode.Tree) listOf(
                            "Capture base" to { capture(SpatialGeometryFields.BASE_ANGLE_DEG) },
                            "Capture top" to { capture(SpatialGeometryFields.TOP_ANGLE_DEG) }
                        ) else if (mode == GeometryMode.Distance) listOf(
                            "Capture lower" to { captureDistanceSightline(true) },
                            "Capture upper" to { captureDistanceSightline(false) }
                        ) else emptyList()
                    )
                }
            }
            Text(
                "Live orientation — top/bottom: ${PhoneSensorRepository.pitchDegrees?.let { "%.2f°".format(java.util.Locale.US, it) } ?: "waiting"}  •  left/right: ${PhoneSensorRepository.rollDegrees?.let { "%.2f°".format(java.util.Locale.US, it) } ?: "waiting"}",
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(Modifier.height(8.dp))
            mode.fields.forEach { (key, label) ->
                OutlinedTextField(values[key].orEmpty(), { text -> values = values.toMutableMap().apply { put(key, text) } }, label = { Text(label) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(6.dp))
            }
            when (mode) {
                GeometryMode.Tree -> {
                    Button({ capture(SpatialGeometryFields.BASE_ANGLE_DEG) }, Modifier.fillMaxWidth()) { Text("Capture base sightline") }
                    Button({ capture(SpatialGeometryFields.TOP_ANGLE_DEG) }, Modifier.fillMaxWidth()) { Text("Capture top sightline") }
                }
                GeometryMode.Slope -> {
                    Button({ capture(SpatialGeometryFields.SLOPE_ANGLE_DEG) }, Modifier.fillMaxWidth()) { Text("Capture top/bottom inclination") }
                    Button({ capture(SpatialGeometryFields.TILT_ANGLE_DEG) }, Modifier.fillMaxWidth()) { Text("Capture left/right tilt") }
                }
                GeometryMode.Distance -> {
                    Text("Place a known-height object in view. Aim at its lower point and capture, then aim at its upper point and capture. MethodMesh uses the angle difference to estimate distance.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Button({ captureDistanceSightline(true) }, Modifier.weight(1f)) { Text("Capture lower") }
                        Spacer(Modifier.size(8.dp))
                        Button({ captureDistanceSightline(false) }, Modifier.weight(1f)) { Text("Capture upper") }
                    }
                    Text("Lower: ${distanceSightline1?.let { "%.2f°".format(java.util.Locale.US, it) } ?: "not captured"}  •  Upper: ${distanceSightline2?.let { "%.2f°".format(java.util.Locale.US, it) } ?: "not captured"}", style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(status, style = MaterialTheme.typography.bodySmall)
            Button(::execute, Modifier.fillMaxWidth()) { Text("Calculate measurement") }
        }
    }

    if (cameraFullscreen && cameraEnabled && cameraPermission) {
        FullscreenSightlineCamera(
            angle = PhoneSensorRepository.pitchDegrees?.let { if (mode == GeometryMode.Tree) sightlineElevation(it) else it },
            caption = if (mode == GeometryMode.Distance) "Aim at a sightline" else "Aim at the target",
            measurement = if (mode == GeometryMode.Distance) {
                val referenceHeight = values[SpatialGeometryFields.REFERENCE_HEIGHT_M]?.toDoubleOrNull()
                val angularSize = values[SpatialGeometryFields.ANGULAR_SIZE_DEG]?.toDoubleOrNull()
                val estimatedDistance = if (referenceHeight != null && angularSize != null && angularSize > 0.0 && angularSize < 180.0) {
                    referenceHeight / (2.0 * kotlin.math.tan(Math.toRadians(angularSize / 2.0)))
                } else null
                "Reference ${referenceHeight?.let { "%.2f m".format(java.util.Locale.US, it) } ?: "--"}  •  Span ${angularSize?.let { "%.2f°".format(java.util.Locale.US, it) } ?: "--"}  •  Distance ${estimatedDistance?.let { "%.2f m".format(java.util.Locale.US, it) } ?: "--"}"
            } else if (mode == GeometryMode.Tree) {
                val distance = values[SpatialGeometryFields.HORIZONTAL_DISTANCE_M]?.toDoubleOrNull()
                val observer = values[SpatialGeometryFields.OBSERVER_HEIGHT_M]?.toDoubleOrNull()
                val base = values[SpatialGeometryFields.BASE_ANGLE_DEG]?.toDoubleOrNull()
                val top = values[SpatialGeometryFields.TOP_ANGLE_DEG]?.toDoubleOrNull()
                val height = if (distance != null && observer != null && base != null && top != null) {
                    observer + distance * (kotlin.math.tan(Math.toRadians(top)) - kotlin.math.tan(Math.toRadians(base)))
                } else null
                "Distance ${distance?.let { "%.2f m".format(java.util.Locale.US, it) } ?: "--"}  •  Base ${base?.let { "%.2f°".format(java.util.Locale.US, it) } ?: "--"}  •  Top ${top?.let { "%.2f°".format(java.util.Locale.US, it) } ?: "--"}  •  Height ${height?.let { "%.2f m".format(java.util.Locale.US, it) } ?: "--"}"
            } else "",
            captureActions = if (mode == GeometryMode.Tree) listOf(
                "Capture base" to { capture(SpatialGeometryFields.BASE_ANGLE_DEG) },
                "Capture top" to { capture(SpatialGeometryFields.TOP_ANGLE_DEG) }
            ) else if (mode == GeometryMode.Distance) listOf(
                "Capture lower" to { captureDistanceSightline(true) },
                "Capture upper" to { captureDistanceSightline(false) }
            ) else emptyList(),
            onClose = { cameraFullscreen = false }
        )
    }
}

@Composable
private fun InclinationPreview(pitch: Float?, roll: Float?) {
    val angle = pitch ?: 0f
    val tilt = roll ?: 0f
    val displayRange = 90f
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.large
    ) {
        Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("INCLINATION", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Top / bottom", style = MaterialTheme.typography.labelMedium)
                    Text("${"%.1f".format(java.util.Locale.US, angle)}°", style = MaterialTheme.typography.displayMedium, color = if (kotlin.math.abs(angle) < 1f) Color(0xFF187A3D) else MaterialTheme.colorScheme.primary)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Left / right", style = MaterialTheme.typography.labelMedium)
                    Text("${"%.1f".format(java.util.Locale.US, tilt)}°", style = MaterialTheme.typography.displayMedium, color = if (kotlin.math.abs(tilt) < 1f) Color(0xFF187A3D) else MaterialTheme.colorScheme.primary)
                }
            }
            Box(Modifier.fillMaxWidth().aspectRatio(1f).border(2.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)) {
                Canvas(Modifier.fillMaxSize()) {
                    val centre = size.width / 2f
                    val verticalOffset = (angle.coerceIn(-displayRange, displayRange) / displayRange) * (size.height / 2f - 12f)
                    val horizontalOffset = (tilt.coerceIn(-displayRange, displayRange) / displayRange) * (size.width / 2f - 24f)
                    drawLine(Color.Gray, Offset(centre, 8f), Offset(centre, size.height - 8f), strokeWidth = 3f)
                    drawLine(Color.Gray, Offset(8f, size.height / 2f), Offset(size.width - 8f, size.height / 2f), strokeWidth = 3f)
                    drawCircle(Color(0xFF187A3D), 12f, Offset(centre + horizontalOffset, size.height / 2f - verticalOffset))
                }
            }
            Text("Vertical position shows top/bottom inclination; horizontal position shows left/right tilt.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SightlineCameraPreview(
    enabled: Boolean,
    onEnable: () -> Unit,
    angle: Float?,
    caption: String,
    restartKey: Any,
    onOpenFullscreen: () -> Unit,
    captureActions: List<Pair<String, () -> Unit>>
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Box(Modifier.fillMaxWidth().height(250.dp).background(Color.Black, MaterialTheme.shapes.large)) {
            if (enabled) {
                com.example.methodmesh.platform.camera.LiveCameraPreview(Modifier.fillMaxSize(), restartKey = restartKey)
                Canvas(Modifier.fillMaxSize()) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    drawLine(Color.White, Offset(cx - 48f, cy), Offset(cx + 48f, cy), strokeWidth = 3f)
                    drawLine(Color.White, Offset(cx, cy - 48f), Offset(cx, cy + 48f), strokeWidth = 3f)
                    drawCircle(Color.White, 34f, Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
                }
                Text("$caption  ${angle?.let { "%.1f°".format(java.util.Locale.US, it) } ?: "--"}", color = Color.White, modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp))
            } else {
                Text("Camera sightline view", color = Color.White, modifier = Modifier.align(Alignment.Center))
            }
        }
        Button(onClick = onEnable, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(if (enabled) "Stop camera" else "Start camera and crosshair") }
        if (enabled) Button(onClick = onOpenFullscreen, Modifier.fillMaxWidth().padding(top = 6.dp)) { Text("Open full-screen camera") }
    }
}

@Composable
private fun FullscreenSightlineCamera(
    angle: Float?,
    caption: String,
    measurement: String,
    captureActions: List<Pair<String, () -> Unit>>,
    onClose: () -> Unit
) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            com.example.methodmesh.platform.camera.LiveCameraPreview(Modifier.fillMaxSize())
            Canvas(Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                drawLine(Color.White, Offset(cx - 64f, cy), Offset(cx + 64f, cy), strokeWidth = 3f)
                drawLine(Color.White, Offset(cx, cy - 64f), Offset(cx, cy + 64f), strokeWidth = 3f)
                drawCircle(Color.White, 42f, Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
            }
            Text(
                "$caption  ${angle?.let { "%.1f°".format(java.util.Locale.US, it) } ?: "--"}",
                color = Color.White,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 28.dp)
            )
            if (measurement.isNotBlank()) {
                Text(
                    measurement,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center).padding(top = 92.dp)
                )
            }
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(18.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
            ) {
                captureActions.forEach { (label, action) ->
                    Button(onClick = action, modifier = Modifier.weight(1f)) { Text(label) }
                }
                Button(onClick = onClose, modifier = Modifier.weight(1f)) { Text("Close") }
            }
        }
    }
}

private fun hasCameraPermission(context: Context): Boolean = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun sightlineElevation(pitch: Float): Float = (pitch + 90f).coerceIn(-89.9f, 89.9f)

private fun settingsValue(settings: SettingsState, key: String): String = settings.getFloat(key).toString()

private fun applyParameters(state: SettingsState, definitions: List<MethodSetting>, values: Map<String, String>) {
    definitions.forEach { setting ->
        val raw = values[setting.id] ?: return@forEach
        raw.toFloatOrNull()?.let { state.setFloat(setting.id, it) }
    }
}

object TreeHeightCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100TreeHeightMethod.id
    override val title = "Height estimator"
    override val description = "Triangulate height from distance and phone orientation."
    @Composable override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) = GeometryScreen(context, GeometryMode.Tree, onBack, onConfirmed, onCancel)
}

object SlopeInclinationCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100SlopeInclinationMethod.id
    override val title = "Slope and inclination"
    override val description = "Measure slope angle and grade with the phone orientation sensor."
    @Composable override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) = GeometryScreen(context, GeometryMode.Slope, onBack, onConfirmed, onCancel)
}

object GeometryDistanceCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100GeometryDistanceMethod.id
    override val title = "Distance from known reference"
    override val description = "Estimate distance from a known reference height and angular span."
    @Composable override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) = GeometryScreen(context, GeometryMode.Distance, onBack, onConfirmed, onCancel)
}
