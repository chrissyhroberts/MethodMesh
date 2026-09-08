package com.example.methodmesh.modules.visualacuity

import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.unit.dp
import com.example.methodmesh.calibration.CalibrationRepository
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityPresentationMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.time.Instant
import java.util.Locale
import kotlinx.coroutines.delay
import kotlin.math.abs

object VisualAcuityCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100VisualAcuityMethod.ID
    override val title = "Visual acuity"
    override val description = "Rapid calibrated tumbling-E visual acuity measurement."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val appContext = LocalContext.current
        val displayDensity = LocalDensity.current.density
        val calibration by CalibrationRepository.calibration

        fun initialSetting(name: String, fallback: String): String =
            context.action.settings[name]
                ?: context.action.settings["input_$name"]
                ?: fallback

        var testMode by rememberSaveable { mutableStateOf(initialSetting("test_mode", "distance")) }
        var eye by rememberSaveable { mutableStateOf(initialSetting("eye", "right")) }
        var correction by rememberSaveable { mutableStateOf(initialSetting("correction", "habitual")) }
        var ambientLightWarning by rememberSaveable {
            mutableStateOf(initialSetting("ambient_light_warning", "true").toBooleanStrictOrNull() ?: true)
        }
        var stage by rememberSaveable { mutableStateOf("setup") }
        var setupMessage by rememberSaveable { mutableStateOf("") }
        var sessionJson by rememberSaveable { mutableStateOf(sessionToJson(VisualAcuityStaircase.newSession())) }
        var targetOrientation by rememberSaveable { mutableStateOf(randomOrientation().name) }
        var trialsJson by rememberSaveable { mutableStateOf("[]") }
        var startedAt by rememberSaveable { mutableStateOf("") }
        var resultValuesJson by rememberSaveable { mutableStateOf("") }
        var latestLux by rememberSaveable { mutableStateOf(Double.NaN) }
        var luxSum by rememberSaveable { mutableStateOf(0.0) }
        var luxCount by rememberSaveable { mutableStateOf(0) }
        var currentCrowdingMode by rememberSaveable { mutableStateOf("full") }

        val sensorManager = remember(appContext) {
            appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        }
        val lightSensor = remember(sensorManager) { sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT) }

        DisposableEffect(stage, ambientLightWarning, lightSensor) {
            if (!ambientLightWarning || lightSensor == null || stage == "result") {
                onDispose { }
            } else {
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        val lux = event.values.firstOrNull()?.toDouble() ?: return
                        latestLux = lux
                        if (stage == "testing") {
                            luxSum += lux
                            luxCount += 1
                        }
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                }
                sensorManager.registerListener(listener, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)
                onDispose { sensorManager.unregisterListener(listener) }
            }
        }

        // Peek Acuity validation used 100% screen brightness. Use a reversible
        // per-window override rather than changing the device-wide setting.
        val activity = appContext as? Activity
        DisposableEffect(stage, activity) {
            if (stage != "testing" || activity == null) {
                onDispose { }
            } else {
                val original = activity.window.attributes.screenBrightness
                val attrs = activity.window.attributes
                attrs.screenBrightness = 1.0f
                activity.window.attributes = attrs
                onDispose {
                    val restore = activity.window.attributes
                    restore.screenBrightness = original
                    activity.window.attributes = restore
                }
            }
        }

        LaunchedEffect(testMode, eye, correction, ambientLightWarning) {
            context.onSettingsChanged(
                mapOf(
                    "test_mode" to testMode,
                    "eye" to eye,
                    "correction" to correction,
                    "ambient_light_warning" to ambientLightWarning.toString()
                )
            )
        }

        fun beginTest() {
            if (!calibration.calibrated) {
                setupMessage = "Screen calibration is required. Calibrate MethodMesh against a physical ruler before using visual acuity."
                return
            }
            if (ambientLightWarning && latestLux.isFinite() && latestLux > 1000.0) {
                setupMessage = "Ambient light is above 1000 lux. Move to a lower-light location before starting."
                return
            }
            val distanceMm = if (testMode == "near") VisualAcuityMath.NEAR_MM else VisualAcuityMath.DISTANCE_MM
            val minimumStrokePx = VisualAcuityMath.strokeSizeMm(0.0, distanceMm) * calibration.dpPerMm * displayDensity
            if (minimumStrokePx < 1.0) {
                setupMessage = "This calibrated display cannot render the smallest 0.0 logMAR limb at the selected distance with at least one physical pixel. Use a higher-resolution device."
                return
            }
            sessionJson = sessionToJson(VisualAcuityStaircase.newSession())
            targetOrientation = randomOrientation().name
            trialsJson = "[]"
            startedAt = Instant.now().toString()
            luxSum = 0.0
            luxCount = 0
            setupMessage = ""
            resultValuesJson = ""
            stage = "testing"
        }

        fun buildRequest() = As100VisualAcuityMethod.request(
            action = As100VisualAcuityMethod.ID,
            context = context.request.invocationContext.asMap(As100VisualAcuityMethod.ID) +
                context.action.settings +
                mapOf(
                    "test_mode" to testMode,
                    "eye" to eye,
                    "correction" to correction,
                    "ambient_light_warning" to ambientLightWarning.toString()
                ),
            signals = emptyList(),
            inputs = emptyList()
        )

        fun finishTest(session: VisualAcuitySession) {
            val values = resultValues(
                session = session,
                testMode = testMode,
                eye = eye,
                correction = correction,
                calibrationDpPerMm = calibration.dpPerMm.toDouble(),
                calibrationConfirmed = calibration.calibrated,
                displayDensityPxPerDp = displayDensity.toDouble(),
                meanLux = if (luxCount > 0) luxSum / luxCount else null,
                lightSensorAvailable = lightSensor != null,
                trialsJson = trialsJson,
                startedAt = startedAt.ifBlank { Instant.now().toString() }
            )
            resultValuesJson = JSONObject(values).toString()
            stage = "result"
        }

        fun recordResponse(response: EOrientation?, seen: Boolean) {
            if (stage != "testing") return
            val session = sessionFromJson(sessionJson)
            if (session.completed) return
            val target = EOrientation.valueOf(targetOrientation)
            val correct = seen && response == target

            val trials = JSONArray(trialsJson)
            trials.put(
                JSONObject()
                    .put("sequence", session.totalTrials + 1)
                    .put("level_logmar", session.currentLogMar)
                    .put("level_trial", session.trialAtLevel + 1)
                    .put("target_orientation", target.name.lowercase(Locale.ROOT))
                    .put("response_orientation", response?.name?.lowercase(Locale.ROOT) ?: JSONObject.NULL)
                    .put("seen", seen)
                    .put("correct", correct)
                    .put("optotype_size_mm", VisualAcuityMath.optotypeSizeMm(session.currentLogMar, if (testMode == "near") VisualAcuityMath.NEAR_MM else VisualAcuityMath.DISTANCE_MM))
                    .put("optotype_size_dp", VisualAcuityMath.optotypeSizeMm(session.currentLogMar, if (testMode == "near") VisualAcuityMath.NEAR_MM else VisualAcuityMath.DISTANCE_MM) * calibration.dpPerMm)
                    .put("optotype_limb_px", VisualAcuityMath.strokeSizeMm(session.currentLogMar, if (testMode == "near") VisualAcuityMath.NEAR_MM else VisualAcuityMath.DISTANCE_MM) * calibration.dpPerMm * displayDensity)
                    .put("crowding_mode", currentCrowdingMode)
                    .put("recorded_at", Instant.now().toString())
            )
            trialsJson = trials.toString()

            val step = VisualAcuityStaircase.recordResponse(session, correct)
            sessionJson = sessionToJson(step.state)
            if (step.state.completed) {
                finishTest(step.state)
            } else {
                // Never present the same orientation twice in succession.
                // This makes a successful swipe perceptually unambiguous while
                // keeping the next target uniformly random across the other
                // three possible orientations.
                targetOrientation = randomOrientation(excluding = target).name
            }
        }

        // ODK/external calls supply settings through intent extras and should go
        // straight to the operator test. Native presets still need their runtime
        // settings shown before the test starts.
        LaunchedEffect(context.presentationMode, context.isNativePresetRun, calibration.calibrated) {
            if (
                context.presentationMode == CapabilityPresentationMode.IntentLaunch &&
                !context.isNativePresetRun &&
                stage == "setup" &&
                calibration.calibrated
            ) {
                if (ambientLightWarning && lightSensor != null && !latestLux.isFinite()) delay(750)
                beginTest()
            }
        }

        val capturedResult = remember(resultValuesJson) {
            if (resultValuesJson.isBlank()) {
                null
            } else {
                val values = jsonToStringMap(resultValuesJson)
                As100VisualAcuityMethod.result(buildRequest(), values, context.request.invocationContext)
            }
        }

        when (stage) {
            "testing" -> VisualAcuityTestSurface(
                session = sessionFromJson(sessionJson),
                target = EOrientation.valueOf(targetOrientation),
                testMode = testMode,
                eye = eye,
                correction = correction,
                dpPerMm = calibration.dpPerMm,
                currentLux = latestLux.takeIf { it.isFinite() },
                warnOnLux = ambientLightWarning,
                onCrowdingModeChanged = { currentCrowdingMode = it },
                onResponse = { recordResponse(it, true) },
                onNotSeen = { recordResponse(null, false) },
                onCancel = onCancel
            )

            "result" -> CapabilityScreenScaffold(
                title = title,
                capabilityId = capabilityId,
                context = context,
                canGoBack = context.stepNumber > 1,
                capturedResult = capturedResult,
                resultPreview = capturedResult?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty(),
                onBack = onBack,
                onRetry = {
                    stage = "setup"
                    resultValuesJson = ""
                },
                onConfirm = { capturedResult?.let(onConfirmed) },
                onCancel = onCancel
            ) { }

            else -> CapabilityScreenScaffold(
                title = title,
                capabilityId = capabilityId,
                context = context,
                canGoBack = context.stepNumber > 1,
                capturedResult = null,
                resultPreview = emptyMap(),
                onBack = onBack,
                onRetry = { beginTest() },
                onConfirm = { },
                onCancel = onCancel
            ) {
                Text(
                    "Calibrated tumbling-E visual acuity. The physical optotype size is generated from MethodMesh's global screen calibration.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))

                if (!calibration.calibrated) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Screen calibration has not been confirmed. Open MethodMesh screen calibration and check it with a physical ruler before testing.",
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                } else {
                    Text(
                        "Screen calibration: ${format1(calibration.dpPerMm.toDouble())} dp/mm",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                }

                if (context.settingShouldBeShown("test_mode")) {
                    ChoiceDropdown(
                        label = "Test mode",
                        value = testMode,
                        options = listOf("distance" to "Distance · 2 m", "near" to "Near · 40 cm"),
                        onSelected = {
                            testMode = it
                            if (it == "near" && eye == "right") eye = "binocular"
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                }

                if (context.settingShouldBeShown("eye")) {
                    ChoiceDropdown(
                        label = "Eye",
                        value = eye,
                        options = listOf("right" to "Right", "left" to "Left", "binocular" to "Binocular"),
                        onSelected = { eye = it }
                    )
                    Spacer(Modifier.height(8.dp))
                }

                if (context.settingShouldBeShown("correction")) {
                    ChoiceDropdown(
                        label = "Correction",
                        value = correction,
                        options = listOf(
                            "habitual" to "Habitual correction",
                            "none" to "No correction",
                            "not_recorded" to "Not recorded"
                        ),
                        onSelected = { correction = it }
                    )
                    Spacer(Modifier.height(8.dp))
                }

                if (context.settingShouldBeShown("ambient_light_warning")) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Warn above 1000 lux", modifier = Modifier.weight(1f))
                        Switch(checked = ambientLightWarning, onCheckedChange = { ambientLightWarning = it })
                    }
                    Spacer(Modifier.height(8.dp))
                }

                val distanceText = if (testMode == "near") "40 cm" else "2 m"
                Text(
                    "Measure $distanceText from the screen to the participant's eyes. ${if (testMode == "near") "The V@home validation used binocular near acuity." else "Cover the non-tested eye for monocular testing."}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "The participant indicates the direction of the E arms. The operator swipes the same direction. No correctness feedback is shown during the test.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))

                if (ambientLightWarning) {
                    Text(
                        when {
                            lightSensor == null -> "Ambient-light sensor: unavailable; lighting will be recorded as unavailable."
                            latestLux.isFinite() -> "Ambient light: ${format0(latestLux)} lux${if (latestLux > 1000.0) " — too bright for the Peek validation conditions" else ""}"
                            else -> "Ambient light: measuring…"
                        },
                        color = if (latestLux.isFinite() && latestLux > 1000.0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                }

                setupMessage.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                }

                Button(
                    onClick = { beginTest() },
                    enabled = calibration.calibrated,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Start visual acuity test")
                }
            }
        }
    }
}

@Composable
private fun VisualAcuityTestSurface(
    session: VisualAcuitySession,
    target: EOrientation,
    testMode: String,
    eye: String,
    correction: String,
    dpPerMm: Float,
    currentLux: Double?,
    warnOnLux: Boolean,
    onCrowdingModeChanged: (String) -> Unit,
    onResponse: (EOrientation) -> Unit,
    onNotSeen: () -> Unit,
    onCancel: () -> Unit
) {
    val distanceMm = if (testMode == "near") VisualAcuityMath.NEAR_MM else VisualAcuityMath.DISTANCE_MM
    val eMm = VisualAcuityMath.optotypeSizeMm(session.currentLogMar, distanceMm)
    val eDp = eMm * dpPerMm
    val barDp = eDp / 5.0
    val gapDp = eDp / 2.0
    val outerDp = eDp + 2.0 * (barDp + gapDp)

    // IMPORTANT: the MethodMesh capability itself is rendered inside the Home /
    // preset content slot. fillMaxSize() there only fills that slot. A platform
    // Dialog creates a separate window, so the active acuity test genuinely
    // covers the entire display instead of inheriting the parent layout bounds.
    Dialog(
        onDismissRequest = { /* A test cannot be dismissed accidentally. */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val dialogView = LocalView.current

        // Hide Android system chrome on the DIALOG window itself. Hiding it only
        // on the Activity is insufficient because Dialog owns its own Window.
        DisposableEffect(dialogView) {
            val dialogWindow = (dialogView.parent as? DialogWindowProvider)?.window
            val decor = dialogWindow?.decorView
            val originalFlags = decor?.systemUiVisibility

            if (decor != null) {
                decor.systemUiVisibility =
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            }

            onDispose {
                if (decor != null && originalFlags != null) {
                    decor.systemUiVisibility = originalFlags
                }
            }
        }

        var drag by remember(target, session.totalTrials) { mutableStateOf(Offset.Zero) }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                // Gesture handling belongs to the fullscreen window, not the
                // optotype itself: a swipe anywhere on the display is valid.
                .pointerInput(target, session.totalTrials) {
                    detectDragGestures(
                        onDragStart = { drag = Offset.Zero },
                        onDrag = { change, amount ->
                            change.consume()
                            drag += amount
                        },
                        onDragEnd = {
                            if (abs(drag.x) >= 24f || abs(drag.y) >= 24f) {
                                EOrientation.fromSwipe(drag.x, drag.y)?.let(onResponse)
                            }
                            drag = Offset.Zero
                        },
                        onDragCancel = { drag = Offset.Zero }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            val eFits = eDp.toFloat().dp <= maxWidth && eDp.toFloat().dp <= maxHeight
            val crowdingFits = outerDp.toFloat().dp <= maxWidth && outerDp.toFloat().dp <= maxHeight

            LaunchedEffect(eFits, crowdingFits) {
                onCrowdingModeChanged(
                    if (crowdingFits) "full"
                    else if (eFits) "omitted_screen_constraint"
                    else "not_renderable"
                )
            }

            // Deliberately nothing else is drawn during testing: no Home,
            // preset controls, MethodMesh scaffold, title, instructions,
            // progress, buttons, correctness feedback or warnings.
            if (eFits) {
                TumblingEWithCrowding(
                    orientation = target,
                    eSizeDp = eDp.toFloat(),
                    showCrowding = crowdingFits,
                    modifier = Modifier.size(
                        if (crowdingFits) outerDp.toFloat().dp else eDp.toFloat().dp
                    )
                )
            }
        }
    }
}

@Composable
private fun TumblingEWithCrowding(
    orientation: EOrientation,
    eSizeDp: Float,
    showCrowding: Boolean = true,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val ePx = eSizeDp.dp.toPx()
        val stroke = ePx / 5f
        val gap = ePx / 2f
        val outer = ePx + 2f * (stroke + gap)
        val eLeft = stroke + gap
        val eTop = stroke + gap

        // Four crowding bars: one limb thick, with half-an-optotype clear gap.
        // If the complete frame cannot fit, v0.1.1 keeps the optotype at its
        // calibrated physical size and omits the bars rather than shrinking the E.
        if (showCrowding) {
            drawRect(Color.Black, topLeft = Offset.Zero, size = Size(outer, stroke))
            drawRect(Color.Black, topLeft = Offset(0f, outer - stroke), size = Size(outer, stroke))
            drawRect(Color.Black, topLeft = Offset.Zero, size = Size(stroke, outer))
            drawRect(Color.Black, topLeft = Offset(outer - stroke, 0f), size = Size(stroke, outer))
        }

        val origin = if (showCrowding) Offset(eLeft, eTop) else Offset.Zero
        rotate(orientation.degreesClockwise.toFloat(), pivot = Offset(origin.x + ePx / 2f, origin.y + ePx / 2f)) {
            // 5x5-grid tumbling E. Standard orientation has arms pointing right.
            drawRect(Color.Black, topLeft = Offset(origin.x, origin.y), size = Size(ePx, stroke))
            drawRect(Color.Black, topLeft = Offset(origin.x, origin.y + 2f * stroke), size = Size(ePx, stroke))
            drawRect(Color.Black, topLeft = Offset(origin.x, origin.y + 4f * stroke), size = Size(ePx, stroke))
            drawRect(Color.Black, topLeft = Offset(origin.x, origin.y), size = Size(stroke, ePx))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceDropdown(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val display = options.firstOrNull { it.first == value }?.second ?: value
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = display,
            onValueChange = { },
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (key, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        onSelected(key)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun resultValues(
    session: VisualAcuitySession,
    testMode: String,
    eye: String,
    correction: String,
    calibrationDpPerMm: Double,
    calibrationConfirmed: Boolean,
    displayDensityPxPerDp: Double,
    meanLux: Double?,
    lightSensorAvailable: Boolean,
    trialsJson: String,
    startedAt: String
): Map<String, String> {
    val distanceMm = if (testMode == "near") VisualAcuityMath.NEAR_MM else VisualAcuityMath.DISTANCE_MM
    val finishedAt = Instant.now().toString()
    val trials = JSONArray(trialsJson)

    val result: String
    val logMar: String
    val metric: String
    val imperial: String
    val decimal: String

    if (session.poorerThanOne || session.thresholdTenth == null) {
        result = "<6/60 · <20/200 · >1.0 logMAR"
        logMar = ">1.0"
        metric = "<6/60"
        imperial = "<20/200"
        decimal = "<0.1"
    } else {
        val level = session.thresholdTenth
        val value = level / 10.0
        metric = VisualAcuityMath.metricSnellen(level)
        imperial = VisualAcuityMath.imperialSnellen(level)
        decimal = format2(VisualAcuityMath.decimalAcuity(value))
        logMar = format2(value)
        result = "$metric · $imperial · $logMar logMAR"
    }

    val audit = JSONObject()
        .put("schema", "methodmesh.visual_acuity.audit.v1")
        .put("method_id", As100VisualAcuityMethod.ID)
        .put("method_version", As100VisualAcuityMethod.VERSION)
        .put("algorithm_id", VisualAcuityStaircase.ALGORITHM_ID)
        .put("algorithm_version", VisualAcuityStaircase.ALGORITHM_VERSION)
        .put("algorithm_note", "Published V@home coarse staircase with explicit MethodMesh 0.1-logMAR bracket refinement")
        .put("test_mode", testMode)
        .put("test_distance_mm", distanceMm)
        .put("eye", eye)
        .put("correction", correction)
        .put("screen_calibration_dp_per_mm", calibrationDpPerMm)
        .put("display_density_px_per_dp", displayDensityPxPerDp)
        .put("effective_pixels_per_mm", calibrationDpPerMm * displayDensityPxPerDp)
        .put("screen_calibration_confirmed", calibrationConfirmed)
        .put("minimum_renderable_limb_rule", ">=1 physical pixel at 0.0 logMAR")
        .put("screen_brightness_policy", "window_override_100_percent_during_test")
        .put("ambient_light_sensor_available", lightSensorAvailable)
        .put("ambient_light_mean_lux", meanLux ?: JSONObject.NULL)
        .put("ambient_light_warning_threshold_lux", 1000)
        .put("optotype", "5x5_tumbling_E")
        .put("orientations", JSONArray(listOf("right", "down", "left", "up")))
        .put("crowding_bar_thickness", "one_optotype_limb")
        .put("crowding_gap", "half_total_optotype_size")
        .put("level_pass_rule", "4_of_5")
        .put("trial_count", session.totalTrials)
        .put("poorer_than_1_0_logmar", session.poorerThanOne)
        .put("threshold_logmar", session.thresholdTenth?.div(10.0) ?: JSONObject.NULL)
        .put("started_at", startedAt)
        .put("finished_at", finishedAt)
        .put("trials", trials)

    return linkedMapOf(
        VisualAcuityFields.STATUS to "succeeded",
        VisualAcuityFields.RESULT to result,
        VisualAcuityFields.LOGMAR to logMar,
        VisualAcuityFields.SNELLEN_METRIC to metric,
        VisualAcuityFields.SNELLEN_IMPERIAL to imperial,
        VisualAcuityFields.DECIMAL to decimal,
        VisualAcuityFields.AUDIT_JSON to audit.toString(),
        VisualAcuityFields.ERROR to ""
    )
}

private fun sessionToJson(session: VisualAcuitySession): String = JSONObject()
    .put("currentLevelTenth", session.currentLevelTenth)
    .put("phase", session.phase.name)
    .put("coarseIndex", session.coarseIndex)
    .put("lastPassTenth", session.lastPassTenth ?: JSONObject.NULL)
    .put("failedBoundaryTenth", session.failedBoundaryTenth ?: JSONObject.NULL)
    .put("correctAtLevel", session.correctAtLevel)
    .put("incorrectAtLevel", session.incorrectAtLevel)
    .put("trialAtLevel", session.trialAtLevel)
    .put("totalTrials", session.totalTrials)
    .put("completed", session.completed)
    .put("poorerThanOne", session.poorerThanOne)
    .put("thresholdTenth", session.thresholdTenth ?: JSONObject.NULL)
    .toString()

private fun sessionFromJson(json: String): VisualAcuitySession {
    val o = JSONObject(json)
    fun nullableInt(name: String): Int? = if (o.isNull(name)) null else o.getInt(name)
    return VisualAcuitySession(
        currentLevelTenth = o.getInt("currentLevelTenth"),
        phase = StaircasePhase.valueOf(o.getString("phase")),
        coarseIndex = o.getInt("coarseIndex"),
        lastPassTenth = nullableInt("lastPassTenth"),
        failedBoundaryTenth = nullableInt("failedBoundaryTenth"),
        correctAtLevel = o.getInt("correctAtLevel"),
        incorrectAtLevel = o.getInt("incorrectAtLevel"),
        trialAtLevel = o.getInt("trialAtLevel"),
        totalTrials = o.getInt("totalTrials"),
        completed = o.getBoolean("completed"),
        poorerThanOne = o.getBoolean("poorerThanOne"),
        thresholdTenth = nullableInt("thresholdTenth")
    )
}

private fun jsonToStringMap(json: String): Map<String, String> {
    val o = JSONObject(json)
    return o.keys().asSequence().associateWith { key -> if (o.isNull(key)) "" else o.get(key).toString() }
}

private val visualAcuitySecureRandom = SecureRandom()

private fun randomOrientation(excluding: EOrientation? = null): EOrientation {
    val candidates = if (excluding == null) {
        EOrientation.entries
    } else {
        EOrientation.entries.filter { it != excluding }
    }
    return candidates[visualAcuitySecureRandom.nextInt(candidates.size)]
}

private fun format0(value: Double): String = String.format(Locale.ROOT, "%.0f", value)
private fun format1(value: Double): String = String.format(Locale.ROOT, "%.1f", value)
private fun format2(value: Double): String = String.format(Locale.ROOT, "%.2f", value)
