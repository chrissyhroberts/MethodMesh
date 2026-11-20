package com.example.methodmesh.modules.astronomy

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.*
import kotlinx.coroutines.delay
import org.json.JSONObject

@Composable
private fun OnMeasurementStop(onStop: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val current by rememberUpdatedState(onStop)
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) current() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}

@Composable
private fun measurementInputs(context: CapabilityScreenContext, method: AstronomyMethodBase): MutableState<String> {
    val initial = remember {
        AstronomyModule.capabilitySettings().getValue(method.id).associate { it.id to it.autoDefault() } +
            context.action.settings.mapKeys { it.key.removePrefix("input_") }
    }
    return rememberSaveable(method.id) { mutableStateOf(JSONObject(initial).toString()) }
}

private fun sampleInt(settings: Map<String, String>, key: String, low: Int, high: Int): Int {
    val value = settings[key]?.toIntOrNull()
    require(value != null && value in low..high) { "Enter " + key.replace('_', ' ') + " between " + low + " and " + high + "." }
    return value
}

@Composable
private fun SampleInput(context: CapabilityScreenContext, settings: Map<String, String>, key: String,
    label: String, update: (String, String) -> Unit) {
    if (context.settingShouldBeShown(key))
        OutlinedTextField(settings[key].orEmpty(), { update(key, it) }, label = { Text(label) },
            modifier = Modifier.fillMaxWidth(), singleLine = true)
}

private fun samplePreview(result: ExecutionResult?, primary: String, error: String): Map<String, Any?> =
    result?.let { original ->
        val fields = OutputFormatter.fields(original, false)
        mapOf(primary to fields[primary]?.toString().orEmpty().ifBlank { fields[error]?.toString().orEmpty() })
    }.orEmpty()

@Composable
internal fun AstronomySkyTestScreen(
    context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit,
    cameraPermission: Boolean? = null,
    waitForSample: suspend (Long) -> Unit = { delay(it) },
    camera: @Composable (AstronomyFrameSession, (String) -> Unit) -> Unit = { session, error ->
        AstronomyOwnedCamera(session.analyzer, onError = error)
    }
) {
    val app = LocalContext.current.applicationContext
    val repo = remember { AstronomyRepository(app) }
    var settingsJson by measurementInputs(context, As100SkyTestMethod)
    val settings = settingsMap(settingsJson)
    val scope = rememberCoroutineScope()
    val controller = remember { AstronomyAutoRequestController() }
    var result by rememberRetainedExecutionResult(As100SkyTestMethod.id)
    var referenceSample by rememberSkyReferenceSample()
    var hadResult by rememberSaveable { mutableStateOf(false) }
    var wasSampling by rememberSaveable { mutableStateOf(false) }
    var status by rememberSaveable { mutableStateOf("Aim at a bright point source and keep the phone fixed.") }
    var session by remember { mutableStateOf(AstronomyFrameSession()) }
    var granted by remember { mutableStateOf(cameraPermission ?: (ContextCompat.checkSelfPermission(app, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)) }
    var calibration by remember { mutableStateOf(repo.loadCalibration()) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    fun update(key: String, value: String) {
        settingsJson = JSONObject(settingsMap(settingsJson) + (key to value)).toString()
        context.onSettingsChanged(settingsMap(settingsJson))
    }
    fun interrupt(message: String) {
        controller.cancel(); session.close(); wasSampling = false; status = message
    }
    fun start() {
        if (controller.busy || !granted) return
        try {
            val frozen = settingsMap(settingsJson).toMap()
            val seconds = sampleInt(frozen, "duration_seconds", 3, 60)
            val frames = sampleInt(frozen, "minimum_frames", 10, 300)
            session.close()
            val sample = AstronomyFrameSession()
            session = sample
            wasSampling = true
            status = "Sampling a new sky test…"
            controller.start(scope, {
                waitForSample(seconds * 1000L)
                sample.freeze(frames)
            }, { capture ->
                val usedCalibration = repo.loadCalibration()
                val values = As100SkyTestMethod.capture(capture.summary,
                    repo.scoreAgainstCalibration(capture.summary, usedCalibration), usedCalibration)
                result = methodExecution(As100SkyTestMethod, context, values, frozen)
                referenceSample = capture.summary
                hadResult = true; wasSampling = false; status = "Sky test completed."
            }, { failure -> interrupt("Sample failed: " + failure.message) },
                onCancelled = { session.close(); wasSampling = false; status = "Sample interrupted. Run a new sky test." })
        } catch (failure: Exception) { status = failure.message ?: "Invalid sky-test inputs." }
    }
    DisposableEffect(Unit) { onDispose { controller.cancel(); session.close() } }
    OnMeasurementStop { if (controller.busy) interrupt("Sample interrupted. Run a new sky test.") }
    LaunchedEffect(Unit) {
        if (wasSampling && result == null) { wasSampling = false; status = "Sample interrupted. Run a new sky test." }
    }
    if (hadResult && result == null) {
        CapturedExecutionRecovery({ hadResult = false; referenceSample = null }, onCancel)
        return
    }
    if (result != null && !context.submitsImmediately) {
        referenceSample?.takeIf { it.detectionCount > 0 }?.let { completed ->
            OutlinedButton(onClick = {
                try {
                    repo.saveCalibration(completed, result!!.request.context["calibration_note"].orEmpty())
                    calibration = repo.loadCalibration()
                    status = "Clear-night reference saved for future tests."
                } catch (failure: Exception) { status = "Reference save failed: " + failure.message }
            }, modifier = Modifier.fillMaxWidth()) { Text("Save this run as CLEAR-NIGHT reference") }
        }
        Text(status, style = MaterialTheme.typography.bodySmall)
    }
    CapabilityScreenScaffold("Relative sky-quality test", As100SkyTestMethod.id, context, context.stepNumber > 1,
        result, samplePreview(result, SkyTestFields.RESULT, SkyTestFields.ERROR), onBack,
        { interrupt("Ready for a new sky test."); result = null; referenceSample = null; hadResult = false },
        { result?.let(onConfirmed) }, onCancel) {
        Text("Relative phone-camera centroid, FWHM, contrast and scintillation proxies. Use the same camera, point source and mounting geometry. These are not calibrated seeing or sky magnitude.")
        SampleInput(context, settings, "duration_seconds", "Test duration (s)", ::update)
        SampleInput(context, settings, "minimum_frames", "Minimum usable frames", ::update)
        SampleInput(context, settings, "calibration_note", "Clear-night reference note", ::update)
        if (calibration != null) {
            Text("Clear-night reference: " + calibration!!.savedAtIso)
            OutlinedButton(onClick = { repo.clearCalibration(); calibration = null }) { Text("Clear saved reference") }
        } else Text("No clear-night reference saved.")
        if (!granted) Button(onClick = { permission.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
        else if (result == null) {
            camera(session) { interrupt(it) }
            Button(onClick = ::start, enabled = !controller.busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (controller.busy) "Sampling…" else "Run sky test")
            }
        }
        if (controller.busy) OutlinedButton(onClick = { interrupt("Sample cancelled. Run a new sky test.") }) { Text("Cancel sample") }
        Text(status)
    }
}

@Composable
internal fun AstronomyFocusScreen(
    context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit,
    cameraPermission: Boolean? = null,
    camera: @Composable (AstronomyFrameSession, String, (String) -> Unit) -> Unit = { session, facing, error ->
        AstronomyOwnedCamera(session.analyzer, facing, onError = error)
    }
) {
    val app = LocalContext.current.applicationContext
    var settingsJson by measurementInputs(context, As100FocusMethod)
    val settings = settingsMap(settingsJson)
    val facing = settings["camera_facing"].orEmpty().lowercase()
    val framesText = settings["sample_frames"]
    var generation by remember { mutableStateOf(0) }
    var revision by remember { mutableStateOf(0L) }
    val handler = remember { Handler(Looper.getMainLooper()) }
    val session = remember(facing, framesText, generation) {
        AstronomyFrameSession(framesText?.toIntOrNull()?.coerceIn(5, 200) ?: 30) { owner, _ ->
            handler.postAtTime({ if (owner.isActive) revision++ }, owner, SystemClock.uptimeMillis())
        }
    }
    var result by rememberRetainedExecutionResult(As100FocusMethod.id)
    var hadResult by rememberSaveable { mutableStateOf(false) }
    var status by rememberSaveable { mutableStateOf("Use an optical adapter/telescope and a bright point source.") }
    var granted by remember { mutableStateOf(cameraPermission ?: (ContextCompat.checkSelfPermission(app, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; if (it) generation++ }
    fun update(key: String, value: String) {
        settingsJson = JSONObject(settingsMap(settingsJson) + (key to value)).toString()
        context.onSettingsChanged(settingsMap(settingsJson))
    }
    fun reset() {
        session.close(); generation++; result = null; hadResult = false
        status = "New focus sample. Adjust focus while watching the rolling median."
    }
    DisposableEffect(session) {
        onDispose { session.close(); handler.removeCallbacksAndMessages(session) }
    }
    OnMeasurementStop {
        if (result == null) { session.close(); status = "Camera interrupted. Reset best to start a fresh sample." }
    }
    if (hadResult && result == null) {
        CapturedExecutionRecovery(::reset, onCancel)
        return
    }
    CapabilityScreenScaffold("Telescope focus assistant", As100FocusMethod.id, context, context.stepNumber > 1,
        result, samplePreview(result, FocusFields.RESULT, FocusFields.ERROR), onBack, ::reset,
        { result?.let(onConfirmed) }, onCancel) {
        Text("Phone camera through an adjustable telescope/eyepiece. Lower rolling-median FWHM is better; this is a relative camera measurement.")
        if (context.settingShouldBeShown("camera_facing")) Row {
            listOf("rear", "front").forEach { side ->
                OutlinedButton(onClick = { update("camera_facing", side) }) { Text(if (facing == side) "✓ " + side else side) }
            }
        }
        SampleInput(context, settings, "sample_frames", "Rolling-median frames", ::update)
        if (!granted) Button(onClick = { permission.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
        else if (result == null) {
            camera(session, facing) { message -> session.close(); status = message }
            val live = remember(session, revision) { session.readout() }
            Text("Current frame: " + (live.current?.let { "%.2f px".format(it) } ?: "—"))
            Text("Rolling median: " + (live.median?.let { "%.2f px".format(it) } ?: "—"))
            Text("Best rolling median: " + (live.best?.let { "%.2f px".format(it) } ?: "—"))
            OutlinedButton(onClick = ::reset) { Text("Reset best") }
            Button(onClick = {
                try {
                    val frozen = settingsMap(settingsJson)
                    require(facing in listOf("rear", "front")) { "Choose rear or front camera." }
                    val count = sampleInt(frozen, "sample_frames", 5, 200)
                    val capture = session.freeze(10)
                    val current = capture.readout.median ?: capture.summary.medianFwhmPx
                    val values = As100FocusMethod.capture(capture.summary, capture.readout.best ?: current, current, facing, count)
                    result = methodExecution(As100FocusMethod, context, values, frozen)
                    hadResult = true
                } catch (failure: Exception) { status = failure.message ?: "Focus capture failed." }
            }, enabled = session.isActive) { Text("Capture focus") }
        }
        Text(status)
    }
}

internal data class AstronomyMountSource(val start: () -> Unit, val stop: () -> Unit,
    val summary: () -> MountStabilitySampler.Summary)

@Composable
internal fun AstronomyMountScreen(
    context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit,
    waitForSample: suspend (Long) -> Unit = { delay(it) },
    sourceFactory: (Context) -> AstronomyMountSource = { app ->
        val sampler = MountStabilitySampler(app)
        AstronomyMountSource(sampler::start, { sampler.stop() }, sampler::summary)
    }
) {
    val app = LocalContext.current.applicationContext
    var activeSource by remember { mutableStateOf<AstronomyMountSource?>(null) }
    var settingsJson by measurementInputs(context, As100MountStabilityMethod)
    val settings = settingsMap(settingsJson)
    val scope = rememberCoroutineScope()
    val controller = remember { AstronomyAutoRequestController() }
    var result by rememberRetainedExecutionResult(As100MountStabilityMethod.id)
    var hadResult by rememberSaveable { mutableStateOf(false) }
    var wasSampling by rememberSaveable { mutableStateOf(false) }
    var status by rememberSaveable { mutableStateOf("Leave the mount alone for baseline; tap once after starting for a tap test.") }
    fun cancelSample(message: String) {
        controller.cancel(); activeSource?.stop?.invoke(); activeSource = null
        wasSampling = false; status = message
    }
    fun start() {
        if (controller.busy) return
        try {
            val frozen = settingsMap(settingsJson).toMap()
            val seconds = sampleInt(frozen, "duration_seconds", 3, 60)
            val source = sourceFactory(app)
            activeSource = source
            wasSampling = true; status = "Measuring mount vibration…"
            controller.start(scope, {
                try { source.start(); waitForSample(seconds * 1000L) }
                finally { source.stop() }
                source.summary()
            }, { capture ->
                val values = As100MountStabilityMethod.capture(capture)
                result = methodExecution(As100MountStabilityMethod, context, values, frozen)
                activeSource = null
                hadResult = true; wasSampling = false; status = "Mount sample completed."
            }, { failure -> cancelSample("Measurement failed: " + failure.message) },
                onCancelled = { source.stop(); wasSampling = false; status = "Measurement interrupted. Start a new mount sample." })
        } catch (failure: Exception) { status = failure.message ?: "Invalid duration." }
    }
    DisposableEffect(Unit) { onDispose { controller.cancel(); activeSource?.stop?.invoke() } }
    OnMeasurementStop { if (controller.busy) cancelSample("Measurement interrupted. Start a new mount sample.") }
    LaunchedEffect(Unit) {
        if (wasSampling && result == null) { wasSampling = false; status = "Measurement interrupted. Start a new mount sample." }
    }
    if (hadResult && result == null) {
        CapturedExecutionRecovery({ hadResult = false }, onCancel)
        return
    }
    CapabilityScreenScaffold("Mount stability", As100MountStabilityMethod.id, context, context.stepNumber > 1,
        result, samplePreview(result, MountFields.RESULT, MountFields.ERROR), onBack,
        { cancelSample("Ready for a fresh mount sample."); result = null; hadResult = false },
        { result?.let(onConfirmed) }, onCancel) {
        SampleInput(context, settings, "duration_seconds", "Measurement duration (s)") { key, value ->
            settingsJson = JSONObject(settingsMap(settingsJson) + (key to value)).toString()
            context.onSettingsChanged(settingsMap(settingsJson))
        }
        Button(onClick = ::start, enabled = result == null && !controller.busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (controller.busy) "Measuring…" else "Measure mount")
        }
        if (controller.busy) OutlinedButton(onClick = { cancelSample("Sample cancelled. Start a new mount sample.") }) { Text("Cancel sample") }
        Text(status)
    }
}
