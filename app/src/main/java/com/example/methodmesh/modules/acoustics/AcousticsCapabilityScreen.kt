package com.example.methodmesh.modules.acoustics

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import java.time.Instant
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max

private enum class AcousticMode { ANALYSE, TUNE, LEVEL, COMPARE }

object AcousticAnalyseCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100AcousticAnalyseMethod.ID
    override val title = "Acoustic analyser"
    override val description = "Measure frequency, waveform, spectrum, amplitude and derived wavelength."
    @Composable override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) =
        AcousticCapabilityUi(AcousticMode.ANALYSE, title, capabilityId, context, onBack, onConfirmed, onCancel)
}

object AcousticTunerCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100AcousticTunerMethod.ID
    override val title = "Instrument tuner"
    override val description = "Chromatic and string-instrument tuner with measured frequency and cents deviation."
    @Composable override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) =
        AcousticCapabilityUi(AcousticMode.TUNE, title, capabilityId, context, onBack, onConfirmed, onCancel)
}

object AcousticLevelCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100AcousticLevelMethod.ID
    override val title = "Sound-level meter"
    override val description = "Measure dBFS, or estimated dB SPL when a calibration offset is supplied."
    @Composable override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) =
        AcousticCapabilityUi(AcousticMode.LEVEL, title, capabilityId, context, onBack, onConfirmed, onCancel)
}

object AcousticCompareCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100AcousticCompareMethod.ID
    override val title = "Tone comparator"
    override val description = "Compare a stable observed tone with a target frequency and tolerance."
    @Composable override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) =
        AcousticCapabilityUi(AcousticMode.COMPARE, title, capabilityId, context, onBack, onConfirmed, onCancel)
}

private class AcousticSessionAccumulator {
    val pitchObservations = mutableListOf<AcousticsAlgorithms.FrequencyObservation>()
    var frameCount: Int = 0
    var sumFrameMeanSquare: Double = 0.0
    var maxPeak: Double = 0.0
    var lastFrame: AcousticsCaptureEngine.AcousticFrame? = null

    fun reset() {
        pitchObservations.clear()
        frameCount = 0
        sumFrameMeanSquare = 0.0
        maxPeak = 0.0
        lastFrame = null
    }

    fun add(frame: AcousticsCaptureEngine.AcousticFrame) {
        frameCount += 1
        sumFrameMeanSquare += frame.rms * frame.rms
        maxPeak = max(maxPeak, frame.peak)
        lastFrame = frame
        frame.frequencyHz?.let { frequency ->
            pitchObservations += AcousticsAlgorithms.FrequencyObservation(frame.timestampMs, frequency, frame.pitchConfidence)
        }
        if (pitchObservations.size > 600) pitchObservations.removeAt(0)
    }

    fun leqDbfs(): Double {
        if (frameCount <= 0) return -120.0
        val meanSquare = sumFrameMeanSquare / frameCount
        return if (meanSquare <= 1e-12) -120.0 else 10.0 * kotlin.math.log10(meanSquare)
    }
}

@Composable
private fun AcousticCapabilityUi(
    mode: AcousticMode,
    title: String,
    capabilityId: String,
    context: CapabilityScreenContext,
    onBack: () -> Unit,
    onConfirmed: (ExecutionResult) -> Unit,
    onCancel: () -> Unit
) {
    val androidContext = LocalContext.current
    val engine = remember { AcousticsCaptureEngine(androidContext.applicationContext) }
    val accumulator = remember { AcousticSessionAccumulator() }

    fun initial(key: String, fallback: String): String =
        context.action.settings[key] ?: context.action.settings["input_$key"] ?: fallback

    var captureSeconds by rememberSaveable { mutableStateOf(initial("capture_seconds", if (mode == AcousticMode.LEVEL) "3.0" else "2.0")) }
    var sampleRateHz by rememberSaveable { mutableStateOf(initial("sample_rate_hz", "48000")) }
    var minFrequencyHz by rememberSaveable { mutableStateOf(initial("min_frequency_hz", "40")) }
    var maxFrequencyHz by rememberSaveable { mutableStateOf(initial("max_frequency_hz", "5000")) }
    var referenceA4Hz by rememberSaveable { mutableStateOf(initial("reference_a4_hz", "440")) }
    var speedMode by rememberSaveable { mutableStateOf(initial("speed_of_sound_mode", "temperature")) }
    var temperatureC by rememberSaveable { mutableStateOf(initial("temperature_c", "20")) }
    var fixedSpeedMps by rememberSaveable { mutableStateOf(initial("speed_of_sound_mps", "343")) }
    var minimumStableMs by rememberSaveable { mutableStateOf(initial("minimum_stable_ms", "500")) }
    var maximumSdCents by rememberSaveable { mutableStateOf(initial("maximum_sd_cents", if (mode == AcousticMode.COMPARE) "3" else "5")) }
    var minimumPitchConfidence by rememberSaveable { mutableStateOf(initial("minimum_pitch_confidence", if (mode == AcousticMode.COMPARE) "0.65" else "0.60")) }

    var instrument by rememberSaveable { mutableStateOf(initial("instrument", "chromatic")) }
    var stringIndex by rememberSaveable { mutableStateOf(initial("string_index", "0")) }
    var greenZoneCents by rememberSaveable { mutableStateOf(initial("green_zone_cents", "5")) }

    var calibrationMode by rememberSaveable { mutableStateOf(initial("calibration_mode", "uncalibrated")) }
    var calibrationOffsetDb by rememberSaveable { mutableStateOf(initial("calibration_offset_db", "0")) }
    var calibrationReferenceDbSpl by rememberSaveable { mutableStateOf(initial("calibration_reference_db_spl", "94")) }
    var calibrationNote by rememberSaveable { mutableStateOf(initial("calibration_note", "")) }

    var targetHz by rememberSaveable { mutableStateOf(initial("target_hz", "1000")) }
    var toleranceMode by rememberSaveable { mutableStateOf(initial("tolerance_mode", "hz")) }
    var toleranceValue by rememberSaveable { mutableStateOf(initial("tolerance_value", "5")) }

    var hasAudioPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(androidContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    var pendingPermissionStart by rememberSaveable { mutableStateOf(false) }
    var listening by rememberSaveable { mutableStateOf(false) }
    var captureStartedAtMs by rememberSaveable { mutableStateOf(0L) }
    var captureStartedIso by rememberSaveable { mutableStateOf("") }
    var launchAttempted by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
    var frame by remember { mutableStateOf<AcousticsCaptureEngine.AcousticFrame?>(null) }
    var captureInfo by remember { mutableStateOf<AcousticsCaptureEngine.CaptureInfo?>(null) }
    var status by rememberSaveable { mutableStateOf("Ready to listen.") }
    var result by remember { mutableStateOf<ExecutionResult?>(null) }
    var resultValuesJson by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
    var captureSequence by remember { mutableIntStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasAudioPermission = granted
        if (!granted) {
            pendingPermissionStart = false
            listening = false
            status = "Microphone permission is required for acoustic measurement."
        }
    }

    val settings = when (mode) {
        AcousticMode.ANALYSE -> linkedMapOf(
            "capture_seconds" to captureSeconds,
            "sample_rate_hz" to sampleRateHz,
            "min_frequency_hz" to minFrequencyHz,
            "max_frequency_hz" to maxFrequencyHz,
            "reference_a4_hz" to referenceA4Hz,
            "speed_of_sound_mode" to speedMode,
            "temperature_c" to temperatureC,
            "speed_of_sound_mps" to fixedSpeedMps,
            "minimum_stable_ms" to minimumStableMs,
            "maximum_sd_cents" to maximumSdCents,
            "minimum_pitch_confidence" to minimumPitchConfidence
        )
        AcousticMode.TUNE -> linkedMapOf(
            "instrument" to instrument,
            "string_index" to stringIndex,
            "reference_a4_hz" to referenceA4Hz,
            "green_zone_cents" to greenZoneCents,
            "capture_seconds" to captureSeconds,
            "minimum_stable_ms" to minimumStableMs,
            "maximum_sd_cents" to maximumSdCents,
            "minimum_pitch_confidence" to minimumPitchConfidence,
            "sample_rate_hz" to sampleRateHz
        )
        AcousticMode.LEVEL -> linkedMapOf(
            "capture_seconds" to captureSeconds,
            "sample_rate_hz" to sampleRateHz,
            "calibration_mode" to calibrationMode,
            "calibration_offset_db" to calibrationOffsetDb,
            "calibration_reference_db_spl" to calibrationReferenceDbSpl,
            "calibration_note" to calibrationNote
        )
        AcousticMode.COMPARE -> linkedMapOf(
            "target_hz" to targetHz,
            "tolerance_mode" to toleranceMode,
            "tolerance_value" to toleranceValue,
            "capture_seconds" to captureSeconds,
            "minimum_stable_ms" to minimumStableMs,
            "maximum_sd_cents" to maximumSdCents,
            "minimum_pitch_confidence" to minimumPitchConfidence,
            "sample_rate_hz" to sampleRateHz,
            "min_frequency_hz" to minFrequencyHz,
            "max_frequency_hz" to maxFrequencyHz
        )
    }

    val runtimeInputsVisible = settings.keys.any(context::settingIsRuntimeInput)
    val autoStartEligible = context.submitsImmediately || (context.isNativePresetRun && !runtimeInputsVisible)

    val restoredResult = result ?: remember(resultValuesJson) {
        resultValuesJson?.let(::jsonToStringMap)?.let { values -> executionFor(mode, context, values) }
    }

    LaunchedEffect(settings) { context.onSettingsChanged(settings) }

    fun stopListening() {
        engine.stop()
        listening = false
        pendingPermissionStart = false
    }

    fun startListening() {
        if (!hasAudioPermission) {
            pendingPermissionStart = true
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        if (engine.isRunning()) return

        accumulator.reset()
        frame = null
        captureInfo = null
        result = null
        resultValuesJson = null
        captureStartedAtMs = System.currentTimeMillis()
        captureStartedIso = Instant.now().toString()
        listening = true
        captureSequence += 1
        status = "Listening…"

        engine.start(
            sampleRateHz = sampleRateHz.toIntOrNull() ?: 48_000,
            frameSizeSamples = 4096,
            minFrequencyHz = minFrequencyHz.toDoubleOrNull() ?: 40.0,
            maxFrequencyHz = maxFrequencyHz.toDoubleOrNull() ?: 5000.0,
            onInfo = { info ->
                captureInfo = info
                status = if (info.unprocessedUsed) "Listening · unprocessed microphone path" else "Listening · ${info.audioSourceLabel} input"
            },
            onFrame = { next ->
                accumulator.add(next)
                frame = next
            },
            onError = { message ->
                listening = false
                status = message
            }
        )
    }

    fun finaliseCapture() {
        if (accumulator.frameCount <= 0) {
            stopListening()
            status = "No audio frames were captured."
            return
        }
        stopListening()
        val values = when (mode) {
            AcousticMode.ANALYSE -> buildAnalyseValues(settings, accumulator, captureInfo, captureStartedIso)
            AcousticMode.TUNE -> buildTunerValues(settings, accumulator, captureInfo, captureStartedIso)
            AcousticMode.LEVEL -> buildLevelValues(settings, accumulator, captureInfo, captureStartedIso)
            AcousticMode.COMPARE -> buildCompareValues(settings, accumulator, captureInfo, captureStartedIso)
        }
        resultValuesJson = stringMapToJson(values)
        result = executionFor(mode, context, values)
        status = values[statusField(mode)].orEmpty().ifBlank { "Measurement complete." }
    }

    LaunchedEffect(hasAudioPermission, pendingPermissionStart) {
        if (hasAudioPermission && pendingPermissionStart) {
            pendingPermissionStart = false
            startListening()
        }
    }

    LaunchedEffect(autoStartEligible, launchAttempted) {
        if (autoStartEligible && !launchAttempted) {
            launchAttempted = true
            startListening()
        }
    }

    LaunchedEffect(listening, captureSequence, context.startsImmediately, captureSeconds) {
        if (listening && context.startsImmediately) {
            val durationMs = ((captureSeconds.toDoubleOrNull() ?: 2.0).coerceIn(0.5, 60.0) * 1000.0).toLong()
            delay(durationMs)
            if (listening) finaliseCapture()
        }
    }

    DisposableEffect(Unit) {
        onDispose { engine.stop() }
    }

    CapabilityScreenScaffold(
        title = title,
        capabilityId = capabilityId,
        context = context,
        canGoBack = context.stepNumber > 1,
        capturedResult = restoredResult,
        resultPreview = restoredResult?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty(),
        onBack = onBack,
        onRetry = {
            result = null
            resultValuesJson = null
            startListening()
        },
        onConfirm = { restoredResult?.let(onConfirmed) },
        onCancel = {
            stopListening()
            onCancel()
        }
    ) {
        when (mode) {
            AcousticMode.ANALYSE -> AnalyseControlsAndDisplay(context, settings, frame, captureInfo, listening,
                onCaptureSeconds = { captureSeconds = it }, onSampleRate = { sampleRateHz = it },
                onMinFrequency = { minFrequencyHz = it }, onMaxFrequency = { maxFrequencyHz = it },
                onReferenceA4 = { referenceA4Hz = it }, onSpeedMode = { speedMode = it },
                onTemperature = { temperatureC = it }, onFixedSpeed = { fixedSpeedMps = it },
                onMinimumStable = { minimumStableMs = it }, onMaximumSdCents = { maximumSdCents = it },
                onMinimumConfidence = { minimumPitchConfidence = it })
            AcousticMode.TUNE -> TunerControlsAndDisplay(context, settings, frame, accumulator, listening,
                onInstrument = { instrument = it }, onStringIndex = { stringIndex = it },
                onReferenceA4 = { referenceA4Hz = it }, onGreenZone = { greenZoneCents = it },
                onCaptureSeconds = { captureSeconds = it }, onMinimumStable = { minimumStableMs = it },
                onMaximumSdCents = { maximumSdCents = it }, onMinimumConfidence = { minimumPitchConfidence = it },
                onSampleRate = { sampleRateHz = it })
            AcousticMode.LEVEL -> LevelControlsAndDisplay(context, settings, frame, accumulator, captureInfo, listening,
                onCaptureSeconds = { captureSeconds = it }, onSampleRate = { sampleRateHz = it },
                onCalibrationMode = { calibrationMode = it }, onCalibrationOffset = { calibrationOffsetDb = it },
                onCalibrationReference = { calibrationReferenceDbSpl = it }, onCalibrationNote = { calibrationNote = it })
            AcousticMode.COMPARE -> CompareControlsAndDisplay(context, settings, frame, accumulator, listening,
                onTarget = { targetHz = it }, onToleranceMode = { toleranceMode = it }, onToleranceValue = { toleranceValue = it },
                onCaptureSeconds = { captureSeconds = it }, onMinimumStable = { minimumStableMs = it },
                onMaximumSdCents = { maximumSdCents = it }, onMinimumConfidence = { minimumPitchConfidence = it },
                onSampleRate = { sampleRateHz = it }, onMinFrequency = { minFrequencyHz = it }, onMaxFrequency = { maxFrequencyHz = it })
        }

        Spacer(Modifier.height(12.dp))
        if (!hasAudioPermission) {
            Text("Microphone permission is required.", color = MaterialTheme.colorScheme.error)
        }
        captureInfo?.let { info ->
            if (!info.unprocessedUsed) {
                Text(
                    "Input path: ${info.audioSourceLabel}. The device did not supply an unprocessed microphone path; pitch is usually still useful, but absolute level calibration needs extra caution.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        when {
            context.submitsImmediately -> {
                if (listening) Text("Automatic capture in progress…", style = MaterialTheme.typography.bodyMedium)
            }
            context.isNativePresetRun -> {
                if (!listening) {
                    Button(onClick = { startListening() }, modifier = Modifier.fillMaxWidth()) { Text("Start measurement") }
                } else {
                    Text("Preset capture in progress…", style = MaterialTheme.typography.bodyMedium)
                }
            }
            !listening -> {
                Button(onClick = { startListening() }, modifier = Modifier.fillMaxWidth()) { Text("Start listening") }
            }
            else -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { finaliseCapture() }, modifier = Modifier.weight(1f)) { Text("Capture result") }
                    OutlinedButton(onClick = { stopListening(); status = "Stopped." }, modifier = Modifier.weight(1f)) { Text("Stop") }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(status, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AnalyseControlsAndDisplay(
    context: CapabilityScreenContext,
    settings: Map<String, String>,
    frame: AcousticsCaptureEngine.AcousticFrame?,
    captureInfo: AcousticsCaptureEngine.CaptureInfo?,
    listening: Boolean,
    onCaptureSeconds: (String) -> Unit,
    onSampleRate: (String) -> Unit,
    onMinFrequency: (String) -> Unit,
    onMaxFrequency: (String) -> Unit,
    onReferenceA4: (String) -> Unit,
    onSpeedMode: (String) -> Unit,
    onTemperature: (String) -> Unit,
    onFixedSpeed: (String) -> Unit,
    onMinimumStable: (String) -> Unit,
    onMaximumSdCents: (String) -> Unit,
    onMinimumConfidence: (String) -> Unit
) {
    val frequency = frame?.frequencyHz
    val note = frequency?.let { AcousticsAlgorithms.noteFromFrequency(it, settings.d("reference_a4_hz", 440.0)) }
    val speed = if (settings["speed_of_sound_mode"] == "fixed_speed") settings.d("speed_of_sound_mps", 343.0)
        else AcousticsAlgorithms.soundSpeedMps(settings.d("temperature_c", 20.0))
    val wavelength = frequency?.let { AcousticsAlgorithms.derivedWavelengthM(it, speed) }

    BigMeasurement(
        primary = frequency?.let { "${fmt(it, 2)} Hz" } ?: "— Hz",
        secondary = note?.let { "${it.note} · ${signed(it.cents, 1)} cents" } ?: if (listening) "Listening for a stable pitch" else "Frequency"
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricCard("Derived λ", wavelength?.let { "${fmt(it, 4)} m" } ?: "—", Modifier.weight(1f))
        MetricCard("Level", frame?.let { "${fmt(it.dbfs, 1)} dBFS" } ?: "—", Modifier.weight(1f))
    }
    Spacer(Modifier.height(10.dp))
    WaveformView(frame?.waveform ?: FloatArray(0))
    Spacer(Modifier.height(8.dp))
    SpectrumView(frame?.spectrumDb ?: FloatArray(0))
    Spacer(Modifier.height(8.dp))
    Text("Wavelength is derived from frequency and an assumed/calculated speed of sound; it is not directly measured by the microphone.", style = MaterialTheme.typography.bodySmall)

    SettingsHeader()
    if (context.settingShouldBeShown("capture_seconds")) NumberField("Automatic capture (s)", settings["capture_seconds"].orEmpty(), onCaptureSeconds)
    if (context.settingShouldBeShown("sample_rate_hz")) ChoiceDropdown("Sample rate", settings["sample_rate_hz"].orEmpty(), listOf("44100", "48000"), onSampleRate)
    if (context.settingShouldBeShown("min_frequency_hz")) NumberField("Minimum pitch (Hz)", settings["min_frequency_hz"].orEmpty(), onMinFrequency)
    if (context.settingShouldBeShown("max_frequency_hz")) NumberField("Maximum pitch (Hz)", settings["max_frequency_hz"].orEmpty(), onMaxFrequency)
    if (context.settingShouldBeShown("reference_a4_hz")) NumberField("Reference A4 (Hz)", settings["reference_a4_hz"].orEmpty(), onReferenceA4)
    if (context.settingShouldBeShown("speed_of_sound_mode")) ChoiceDropdown("Wavelength basis", settings["speed_of_sound_mode"].orEmpty(), listOf("temperature", "fixed_speed"), onSpeedMode)
    if (settings["speed_of_sound_mode"] == "temperature" && context.settingShouldBeShown("temperature_c")) NumberField("Air temperature (°C)", settings["temperature_c"].orEmpty(), onTemperature)
    if (settings["speed_of_sound_mode"] == "fixed_speed" && context.settingShouldBeShown("speed_of_sound_mps")) NumberField("Speed of sound (m/s)", settings["speed_of_sound_mps"].orEmpty(), onFixedSpeed)
    if (context.settingShouldBeShown("minimum_stable_ms")) NumberField("Minimum stable duration (ms)", settings["minimum_stable_ms"].orEmpty(), onMinimumStable, integerOnly = true)
    if (context.settingShouldBeShown("maximum_sd_cents")) NumberField("Maximum pitch SD (cents)", settings["maximum_sd_cents"].orEmpty(), onMaximumSdCents)
    if (context.settingShouldBeShown("minimum_pitch_confidence")) NumberField("Minimum pitch confidence", settings["minimum_pitch_confidence"].orEmpty(), onMinimumConfidence)

    captureInfo?.let { Text("Capture: ${it.sampleRateHz} Hz · ${it.frameSizeSamples} samples/frame", style = MaterialTheme.typography.labelSmall) }
}

@Composable
private fun TunerControlsAndDisplay(
    context: CapabilityScreenContext,
    settings: Map<String, String>,
    frame: AcousticsCaptureEngine.AcousticFrame?,
    accumulator: AcousticSessionAccumulator,
    listening: Boolean,
    onInstrument: (String) -> Unit,
    onStringIndex: (String) -> Unit,
    onReferenceA4: (String) -> Unit,
    onGreenZone: (String) -> Unit,
    onCaptureSeconds: (String) -> Unit,
    onMinimumStable: (String) -> Unit,
    onMaximumSdCents: (String) -> Unit,
    onMinimumConfidence: (String) -> Unit,
    onSampleRate: (String) -> Unit
) {
    val frequency = frame?.frequencyHz
    val target = frequency?.let {
        AcousticsAlgorithms.tuningTarget(it, settings.d("reference_a4_hz", 440.0), settings["instrument"] ?: "chromatic", settings.i("string_index", 0))
    }
    val green = settings.d("green_zone_cents", 5.0)
    val state = target?.let { if (abs(it.cents) <= green) "IN TUNE" else if (it.cents < 0) "FLAT" else "SHARP" } ?: "—"
    val stable = AcousticsAlgorithms.findStableWindow(
        accumulator.pitchObservations,
        settings.i("minimum_stable_ms", 500).toLong(),
        settings.d("maximum_sd_cents", 5.0),
        settings.d("minimum_pitch_confidence", 0.60)
    )

    BigMeasurement(
        primary = target?.note ?: "—",
        secondary = target?.let { "${fmt(frequency, 2)} Hz · ${signed(it.cents, 1)} cents · $state" } ?: if (listening) "Play a note" else "Tuner"
    )
    TunerGauge(target?.cents ?: 0.0, green, target != null)
    target?.let { Text("Target: ${it.label} · ${fmt(it.targetHz, 2)} Hz", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
    Text(
        if (stable != null) "Stable for ${stable.durationMs} ms · SD ${fmt(stable.sdCents, 2)} cents" else "Waiting for stable pitch…",
        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall
    )

    SettingsHeader()
    if (context.settingShouldBeShown("instrument")) ChoiceDropdown("Instrument", settings["instrument"].orEmpty(), listOf("chromatic", "guitar", "guitar_drop_d", "ukulele_high_g", "ukulele_low_g", "violin", "viola", "cello", "bass", "mandolin"), onInstrument)
    if (settings["instrument"] != "chromatic" && context.settingShouldBeShown("string_index")) {
        val stringChoices = listOf("0") + AcousticsAlgorithms.instrumentPresets[settings["instrument"]].orEmpty().indices.map { (it + 1).toString() }
        ChoiceDropdown("String (0 = nearest)", settings["string_index"].orEmpty(), stringChoices, onStringIndex)
    }
    if (context.settingShouldBeShown("reference_a4_hz")) NumberField("Reference A4 (Hz)", settings["reference_a4_hz"].orEmpty(), onReferenceA4)
    if (context.settingShouldBeShown("green_zone_cents")) NumberField("In-tune zone (± cents)", settings["green_zone_cents"].orEmpty(), onGreenZone)
    if (context.settingShouldBeShown("capture_seconds")) NumberField("Automatic capture (s)", settings["capture_seconds"].orEmpty(), onCaptureSeconds)
    if (context.settingShouldBeShown("minimum_stable_ms")) NumberField("Minimum stable duration (ms)", settings["minimum_stable_ms"].orEmpty(), onMinimumStable, integerOnly = true)
    if (context.settingShouldBeShown("maximum_sd_cents")) NumberField("Maximum pitch SD (cents)", settings["maximum_sd_cents"].orEmpty(), onMaximumSdCents)
    if (context.settingShouldBeShown("minimum_pitch_confidence")) NumberField("Minimum pitch confidence", settings["minimum_pitch_confidence"].orEmpty(), onMinimumConfidence)
    if (context.settingShouldBeShown("sample_rate_hz")) ChoiceDropdown("Sample rate", settings["sample_rate_hz"].orEmpty(), listOf("44100", "48000"), onSampleRate)
}

@Composable
private fun LevelControlsAndDisplay(
    context: CapabilityScreenContext,
    settings: Map<String, String>,
    frame: AcousticsCaptureEngine.AcousticFrame?,
    accumulator: AcousticSessionAccumulator,
    captureInfo: AcousticsCaptureEngine.CaptureInfo?,
    listening: Boolean,
    onCaptureSeconds: (String) -> Unit,
    onSampleRate: (String) -> Unit,
    onCalibrationMode: (String) -> Unit,
    onCalibrationOffset: (String) -> Unit,
    onCalibrationReference: (String) -> Unit,
    onCalibrationNote: (String) -> Unit
) {
    val offset = settings.d("calibration_offset_db", 0.0)
    val calibrated = settings["calibration_mode"] == "calibrated"
    val currentDbfs = frame?.dbfs
    val displayed = currentDbfs?.let { if (calibrated) it + offset else it }
    val unit = if (calibrated) "dB SPL" else "dBFS"
    val leq = if (accumulator.frameCount > 0) accumulator.leqDbfs().let { if (calibrated) it + offset else it } else null

    BigMeasurement(
        primary = displayed?.let { "${fmt(it, 1)} $unit" } ?: "— $unit",
        secondary = leq?.let { "Leq ${fmt(it, 1)} $unit" } ?: if (listening) "Measuring level" else "Sound level"
    )
    LevelMeter(currentDbfs ?: -90.0)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricCard("Peak", frame?.let { "${fmt(if (calibrated) it.peakDbfs + offset else it.peakDbfs, 1)} $unit" } ?: "—", Modifier.weight(1f))
        MetricCard("Mode", if (calibrated) "Calibrated" else "Relative", Modifier.weight(1f))
    }
    if (!calibrated) {
        Spacer(Modifier.height(8.dp))
        Text("Uncalibrated mode reports digital level (dBFS), not physical sound-pressure level.", style = MaterialTheme.typography.bodySmall)
    }

    SettingsHeader()
    if (context.settingShouldBeShown("capture_seconds")) NumberField("Automatic capture (s)", settings["capture_seconds"].orEmpty(), onCaptureSeconds)
    if (context.settingShouldBeShown("sample_rate_hz")) ChoiceDropdown("Sample rate", settings["sample_rate_hz"].orEmpty(), listOf("44100", "48000"), onSampleRate)
    if (context.settingShouldBeShown("calibration_mode")) ChoiceDropdown("Level mode", settings["calibration_mode"].orEmpty(), listOf("uncalibrated", "calibrated"), onCalibrationMode)
    if (context.settingShouldBeShown("calibration_reference_db_spl")) NumberField("Reference meter level (dB SPL)", settings["calibration_reference_db_spl"].orEmpty(), onCalibrationReference)
    if (context.settingShouldBeShown("calibration_offset_db")) NumberField("Calibration offset (dB)", settings["calibration_offset_db"].orEmpty(), onCalibrationOffset)
    val calibrationObservedDbfs = if (accumulator.frameCount > 0) accumulator.leqDbfs() else currentDbfs
    if (calibrationObservedDbfs != null && context.settingShouldBeShown("calibration_offset_db")) {
        OutlinedButton(
            onClick = {
                val reference = settings.d("calibration_reference_db_spl", 94.0)
                onCalibrationOffset(fmt(reference - calibrationObservedDbfs, 2))
                onCalibrationMode("calibrated")
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Calibrate from current level") }
        Text("This derives offset = reference dB SPL − observed Leq dBFS over the current capture. Keep the same device, microphone route and processing path when reusing it.", style = MaterialTheme.typography.bodySmall)
    }
    if (context.settingShouldBeShown("calibration_note")) {
        OutlinedTextField(settings["calibration_note"].orEmpty(), onCalibrationNote, label = { Text("Calibration note / reference meter") }, modifier = Modifier.fillMaxWidth())
    }
    captureInfo?.let { if (!it.unprocessedUsed) Text("Calibration warning: the active ${it.audioSourceLabel} path may include device processing or gain control.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
}

@Composable
private fun CompareControlsAndDisplay(
    context: CapabilityScreenContext,
    settings: Map<String, String>,
    frame: AcousticsCaptureEngine.AcousticFrame?,
    accumulator: AcousticSessionAccumulator,
    listening: Boolean,
    onTarget: (String) -> Unit,
    onToleranceMode: (String) -> Unit,
    onToleranceValue: (String) -> Unit,
    onCaptureSeconds: (String) -> Unit,
    onMinimumStable: (String) -> Unit,
    onMaximumSdCents: (String) -> Unit,
    onMinimumConfidence: (String) -> Unit,
    onSampleRate: (String) -> Unit,
    onMinFrequency: (String) -> Unit,
    onMaxFrequency: (String) -> Unit
) {
    val target = settings.d("target_hz", 1000.0)
    val measured = frame?.frequencyHz
    val comparison = measured?.takeIf { target > 0.0 }?.let { AcousticsAlgorithms.compareTone(it, target, settings["tolerance_mode"] ?: "hz", settings.d("tolerance_value", 5.0)) }
    val stable = AcousticsAlgorithms.findStableWindow(
        accumulator.pitchObservations,
        settings.i("minimum_stable_ms", 500).toLong(),
        settings.d("maximum_sd_cents", 3.0),
        settings.d("minimum_pitch_confidence", 0.65)
    )

    BigMeasurement(
        primary = measured?.let { "${fmt(it, 2)} Hz" } ?: "— Hz",
        secondary = comparison?.let { "Δ ${signed(it.differenceHz, 2)} Hz · ${if (it.withinTolerance) "WITHIN TARGET" else "OUTSIDE TARGET"}" }
            ?: if (listening) "Listening for target ${fmt(target, 2)} Hz" else "Target ${fmt(target, 2)} Hz"
    )
    CompareGauge(comparison, settings.d("tolerance_value", 5.0))
    Text(
        if (stable != null) "Stable: ${fmt(stable.medianHz, 2)} Hz · ${stable.durationMs} ms · SD ${fmt(stable.sdCents, 2)} cents"
        else "A result is accepted only after the configured stable-tone window.",
        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall
    )

    SettingsHeader()
    if (context.settingShouldBeShown("target_hz")) NumberField("Target frequency (Hz)", settings["target_hz"].orEmpty(), onTarget)
    if (context.settingShouldBeShown("tolerance_mode")) ChoiceDropdown("Tolerance unit", settings["tolerance_mode"].orEmpty(), listOf("hz", "percent", "cents"), onToleranceMode)
    if (context.settingShouldBeShown("tolerance_value")) NumberField("Tolerance (${settings["tolerance_mode"] ?: "hz"})", settings["tolerance_value"].orEmpty(), onToleranceValue)
    if (context.settingShouldBeShown("capture_seconds")) NumberField("Automatic capture (s)", settings["capture_seconds"].orEmpty(), onCaptureSeconds)
    if (context.settingShouldBeShown("minimum_stable_ms")) NumberField("Minimum stable duration (ms)", settings["minimum_stable_ms"].orEmpty(), onMinimumStable, integerOnly = true)
    if (context.settingShouldBeShown("maximum_sd_cents")) NumberField("Maximum pitch SD (cents)", settings["maximum_sd_cents"].orEmpty(), onMaximumSdCents)
    if (context.settingShouldBeShown("minimum_pitch_confidence")) NumberField("Minimum pitch confidence", settings["minimum_pitch_confidence"].orEmpty(), onMinimumConfidence)
    if (context.settingShouldBeShown("sample_rate_hz")) ChoiceDropdown("Sample rate", settings["sample_rate_hz"].orEmpty(), listOf("44100", "48000"), onSampleRate)
    if (context.settingShouldBeShown("min_frequency_hz")) NumberField("Minimum detected pitch (Hz)", settings["min_frequency_hz"].orEmpty(), onMinFrequency)
    if (context.settingShouldBeShown("max_frequency_hz")) NumberField("Maximum detected pitch (Hz)", settings["max_frequency_hz"].orEmpty(), onMaxFrequency)
}

private fun buildAnalyseValues(
    settings: Map<String, String>,
    accumulator: AcousticSessionAccumulator,
    info: AcousticsCaptureEngine.CaptureInfo?,
    startedIso: String
): Map<String, String> {
    val stable = AcousticsAlgorithms.findStableWindow(
        accumulator.pitchObservations,
        settings.i("minimum_stable_ms", 500).toLong(),
        settings.d("maximum_sd_cents", 5.0),
        settings.d("minimum_pitch_confidence", 0.60)
    )
    val summary = stable ?: AcousticsAlgorithms.summaryWindow(accumulator.pitchObservations, settings.d("minimum_pitch_confidence", 0.60))
    val frequency = summary?.medianHz
    val note = frequency?.let { AcousticsAlgorithms.noteFromFrequency(it, settings.d("reference_a4_hz", 440.0)) }
    val speed = if (settings["speed_of_sound_mode"] == "fixed_speed") settings.d("speed_of_sound_mps", 343.0)
        else AcousticsAlgorithms.soundSpeedMps(settings.d("temperature_c", 20.0))
    val wavelength = frequency?.let { AcousticsAlgorithms.derivedWavelengthM(it, speed) }
    val last = accumulator.lastFrame
    val harmonics = if (frequency != null && last != null && info != null) {
        JSONArray(AcousticsAlgorithms.strongestHarmonics(last.spectrumDb, info.sampleRateHz, frequency).map { (hz, db) -> JSONObject().put("frequency_hz", hz).put("relative_db", db) }).toString()
    } else JSONArray().toString()
    val resultText = if (frequency != null) "${fmt(frequency, 2)} Hz${note?.let { " · ${it.note} · ${signed(it.cents, 1)} cents" }.orEmpty()}"
        else "No stable pitch · ${fmt(last?.dbfs, 1)} dBFS"
    val audit = baseAudit(As100AcousticAnalyseMethod.ID, As100AcousticAnalyseMethod.VERSION, settings, info, accumulator, startedIso)
        .put("wavelength_is_derived", true)
        .put("speed_of_sound_mps", speed)
        .put("speed_of_sound_basis", settings["speed_of_sound_mode"] ?: "temperature")
        .put("temperature_c", if (settings["speed_of_sound_mode"] == "temperature") settings.d("temperature_c", 20.0) else JSONObject.NULL)
        .put("stable_window_found", stable != null)
        .put("harmonics", JSONArray(harmonics))
    return linkedMapOf(
        AcousticAnalyseFields.RESULT to resultText,
        AcousticAnalyseFields.FREQUENCY_HZ to fmt(frequency, 4),
        AcousticAnalyseFields.NOTE to (note?.note ?: ""),
        AcousticAnalyseFields.CENTS to fmt(note?.cents, 3),
        AcousticAnalyseFields.WAVELENGTH_M to fmt(wavelength, 6),
        AcousticAnalyseFields.SPEED_OF_SOUND_MPS to fmt(speed, 3),
        AcousticAnalyseFields.RMS to fmt(last?.rms, 6),
        AcousticAnalyseFields.PEAK to fmt(accumulator.maxPeak, 6),
        AcousticAnalyseFields.DBFS to fmt(last?.dbfs, 3),
        AcousticAnalyseFields.PEAK_DBFS to fmt(AcousticsAlgorithms.dbfsFromAmplitude(accumulator.maxPeak), 3),
        AcousticAnalyseFields.PITCH_CONFIDENCE to fmt(summary?.meanConfidence, 4),
        AcousticAnalyseFields.FREQUENCY_SD_HZ to fmt(summary?.sdHz, 4),
        AcousticAnalyseFields.FREQUENCY_SD_CENTS to fmt(summary?.sdCents, 3),
        AcousticAnalyseFields.STABLE_DURATION_MS to (stable?.durationMs?.toString() ?: "0"),
        AcousticAnalyseFields.HARMONICS_JSON to harmonics,
        AcousticAnalyseFields.STATUS to "succeeded",
        AcousticAnalyseFields.AUDIT_JSON to audit.toString(),
        AcousticAnalyseFields.ERROR to ""
    )
}

private fun buildTunerValues(settings: Map<String, String>, accumulator: AcousticSessionAccumulator, info: AcousticsCaptureEngine.CaptureInfo?, startedIso: String): Map<String, String> {
    val stable = AcousticsAlgorithms.findStableWindow(accumulator.pitchObservations, settings.i("minimum_stable_ms", 500).toLong(), settings.d("maximum_sd_cents", 5.0), settings.d("minimum_pitch_confidence", 0.60))
    if (stable == null) return failedTuner(settings, info, accumulator, startedIso, "No sufficiently stable pitch was detected.")
    val measured = stable.medianHz
    val target = AcousticsAlgorithms.tuningTarget(measured, settings.d("reference_a4_hz", 440.0), settings["instrument"] ?: "chromatic", settings.i("string_index", 0))
        ?: return failedTuner(settings, info, accumulator, startedIso, "No valid tuning target could be resolved.")
    val green = settings.d("green_zone_cents", 5.0)
    val state = if (abs(target.cents) <= green) "in_tune" else if (target.cents < 0) "flat" else "sharp"
    val resultText = "${target.note} · ${fmt(measured, 2)} Hz · ${signed(target.cents, 1)} cents · ${state.replace('_', ' ')}"
    val audit = baseAudit(As100AcousticTunerMethod.ID, As100AcousticTunerMethod.VERSION, settings, info, accumulator, startedIso)
        .put("stable_window_found", true)
        .put("stable_duration_ms", stable.durationMs)
        .put("frequency_sd_cents", stable.sdCents)
    return linkedMapOf(
        AcousticTunerFields.RESULT to resultText,
        AcousticTunerFields.NOTE to target.note,
        AcousticTunerFields.TARGET_LABEL to target.label,
        AcousticTunerFields.TARGET_HZ to fmt(target.targetHz, 4),
        AcousticTunerFields.MEASURED_HZ to fmt(measured, 4),
        AcousticTunerFields.CENTS to fmt(target.cents, 3),
        AcousticTunerFields.STATE to state,
        AcousticTunerFields.CONFIDENCE to fmt(stable.meanConfidence, 4),
        AcousticTunerFields.FREQUENCY_SD_HZ to fmt(stable.sdHz, 4),
        AcousticTunerFields.STABLE_DURATION_MS to stable.durationMs.toString(),
        AcousticTunerFields.STATUS to "succeeded",
        AcousticTunerFields.AUDIT_JSON to audit.toString(),
        AcousticTunerFields.ERROR to ""
    )
}

private fun failedTuner(settings: Map<String, String>, info: AcousticsCaptureEngine.CaptureInfo?, accumulator: AcousticSessionAccumulator, startedIso: String, error: String): Map<String, String> =
    AcousticTunerFields.outputs.associateWith { "" }.toMutableMap().apply {
        this[AcousticTunerFields.RESULT] = "No stable tuning result"
        this[AcousticTunerFields.STATUS] = "failed"
        this[AcousticTunerFields.ERROR] = error
        this[AcousticTunerFields.AUDIT_JSON] = baseAudit(As100AcousticTunerMethod.ID, As100AcousticTunerMethod.VERSION, settings, info, accumulator, startedIso).put("error", error).toString()
    }

private fun buildLevelValues(settings: Map<String, String>, accumulator: AcousticSessionAccumulator, info: AcousticsCaptureEngine.CaptureInfo?, startedIso: String): Map<String, String> {
    val last = accumulator.lastFrame
    val calibrated = settings["calibration_mode"] == "calibrated"
    val offset = settings.d("calibration_offset_db", 0.0)
    val dbfs = last?.dbfs ?: -120.0
    val leqDbfs = accumulator.leqDbfs()
    val peakDbfs = AcousticsAlgorithms.dbfsFromAmplitude(accumulator.maxPeak)
    val dbSpl = if (calibrated) dbfs + offset else null
    val leqSpl = if (calibrated) leqDbfs + offset else null
    val resultText = if (calibrated) "${fmt(leqSpl, 1)} dB SPL Leq" else "${fmt(leqDbfs, 1)} dBFS Leq"
    val audit = baseAudit(As100AcousticLevelMethod.ID, As100AcousticLevelMethod.VERSION, settings, info, accumulator, startedIso)
        .put("calibrated", calibrated)
        .put("calibration_offset_db", if (calibrated) offset else JSONObject.NULL)
        .put("calibration_reference_db_spl", if (calibrated) settings.d("calibration_reference_db_spl", 94.0) else JSONObject.NULL)
        .put("calibration_note", settings["calibration_note"].orEmpty())
        .put("warning", if (calibrated && info?.unprocessedUsed != true) "Calibration applied on a microphone path that may include device processing or automatic gain." else JSONObject.NULL)
    return linkedMapOf(
        AcousticLevelFields.RESULT to resultText,
        AcousticLevelFields.DBFS to fmt(dbfs, 3),
        AcousticLevelFields.LEQ_DBFS to fmt(leqDbfs, 3),
        AcousticLevelFields.PEAK_DBFS to fmt(peakDbfs, 3),
        AcousticLevelFields.DB_SPL to fmt(dbSpl, 3),
        AcousticLevelFields.LEQ_DB_SPL to fmt(leqSpl, 3),
        AcousticLevelFields.CALIBRATED to calibrated.toString(),
        AcousticLevelFields.CALIBRATION_OFFSET_DB to if (calibrated) fmt(offset, 3) else "",
        AcousticLevelFields.CALIBRATION_REFERENCE_DB_SPL to if (calibrated) fmt(settings.d("calibration_reference_db_spl", 94.0), 3) else "",
        AcousticLevelFields.STATUS to "succeeded",
        AcousticLevelFields.AUDIT_JSON to audit.toString(),
        AcousticLevelFields.ERROR to ""
    )
}

private fun buildCompareValues(settings: Map<String, String>, accumulator: AcousticSessionAccumulator, info: AcousticsCaptureEngine.CaptureInfo?, startedIso: String): Map<String, String> {
    val stable = AcousticsAlgorithms.findStableWindow(accumulator.pitchObservations, settings.i("minimum_stable_ms", 500).toLong(), settings.d("maximum_sd_cents", 3.0), settings.d("minimum_pitch_confidence", 0.65))
    if (stable == null) return failedCompare(settings, info, accumulator, startedIso, "No sufficiently stable tone was detected.")
    val target = settings.d("target_hz", 1000.0)
    if (target <= 0.0) return failedCompare(settings, info, accumulator, startedIso, "Target frequency must be greater than zero.")
    val measured = stable.medianHz
    val comparison = AcousticsAlgorithms.compareTone(measured, target, settings["tolerance_mode"] ?: "hz", settings.d("tolerance_value", 5.0))
    val verdict = if (comparison.withinTolerance) "PASS" else "FAIL"
    val resultText = "${fmt(measured, 2)} Hz · $verdict · target ${fmt(target, 2)} Hz"
    val audit = baseAudit(As100AcousticCompareMethod.ID, As100AcousticCompareMethod.VERSION, settings, info, accumulator, startedIso)
        .put("stable_window_found", true)
        .put("stable_duration_ms", stable.durationMs)
        .put("verdict", verdict)
    return linkedMapOf(
        AcousticCompareFields.RESULT to resultText,
        AcousticCompareFields.TARGET_HZ to fmt(target, 4),
        AcousticCompareFields.MEASURED_HZ to fmt(measured, 4),
        AcousticCompareFields.DIFFERENCE_HZ to fmt(comparison.differenceHz, 4),
        AcousticCompareFields.DIFFERENCE_PERCENT to fmt(comparison.differencePercent, 5),
        AcousticCompareFields.DIFFERENCE_CENTS to fmt(comparison.differenceCents, 4),
        AcousticCompareFields.TOLERANCE_MODE to comparison.toleranceMode,
        AcousticCompareFields.TOLERANCE_VALUE to fmt(comparison.toleranceValue, 4),
        AcousticCompareFields.WITHIN_TOLERANCE to comparison.withinTolerance.toString(),
        AcousticCompareFields.FREQUENCY_SD_HZ to fmt(stable.sdHz, 4),
        AcousticCompareFields.FREQUENCY_SD_CENTS to fmt(stable.sdCents, 4),
        AcousticCompareFields.STABLE_DURATION_MS to stable.durationMs.toString(),
        AcousticCompareFields.CONFIDENCE to fmt(stable.meanConfidence, 4),
        AcousticCompareFields.STATUS to "succeeded",
        AcousticCompareFields.AUDIT_JSON to audit.toString(),
        AcousticCompareFields.ERROR to ""
    )
}

private fun failedCompare(settings: Map<String, String>, info: AcousticsCaptureEngine.CaptureInfo?, accumulator: AcousticSessionAccumulator, startedIso: String, error: String): Map<String, String> =
    AcousticCompareFields.outputs.associateWith { "" }.toMutableMap().apply {
        this[AcousticCompareFields.RESULT] = "No valid tone comparison"
        this[AcousticCompareFields.TARGET_HZ] = fmt(settings.d("target_hz", 1000.0), 4)
        this[AcousticCompareFields.TOLERANCE_MODE] = settings["tolerance_mode"] ?: "hz"
        this[AcousticCompareFields.TOLERANCE_VALUE] = fmt(settings.d("tolerance_value", 5.0), 4)
        this[AcousticCompareFields.STATUS] = "failed"
        this[AcousticCompareFields.ERROR] = error
        this[AcousticCompareFields.AUDIT_JSON] = baseAudit(As100AcousticCompareMethod.ID, As100AcousticCompareMethod.VERSION, settings, info, accumulator, startedIso).put("error", error).toString()
    }

private fun baseAudit(
    methodId: String,
    methodVersion: String,
    settings: Map<String, String>,
    info: AcousticsCaptureEngine.CaptureInfo?,
    accumulator: AcousticSessionAccumulator,
    startedIso: String
): JSONObject {
    val settingsJson = JSONObject()
    settings.forEach { (key, value) -> settingsJson.put(key, value) }
    return JSONObject()
        .put("method_id", methodId)
        .put("method_version", methodVersion)
        .put("dsp_version", AcousticsAlgorithms.DSP_VERSION)
        .put("pitch_algorithm", AcousticsAlgorithms.PITCH_ALGORITHM)
        .put("spectrum_algorithm", AcousticsAlgorithms.SPECTRUM_ALGORITHM)
        .put("started_time_iso", startedIso)
        .put("completed_time_iso", Instant.now().toString())
        .put("sample_rate_hz", info?.sampleRateHz ?: settings.i("sample_rate_hz", 48000))
        .put("frame_size_samples", info?.frameSizeSamples ?: 4096)
        .put("frame_count", accumulator.frameCount)
        .put("audio_source", info?.audioSourceLabel ?: "unknown")
        .put("unprocessed_audio_advertised", info?.unprocessedAdvertised ?: false)
        .put("unprocessed_audio_used", info?.unprocessedUsed ?: false)
        .put("device_manufacturer", Build.MANUFACTURER)
        .put("device_model", Build.MODEL)
        .put("android_sdk", Build.VERSION.SDK_INT)
        .put("raw_audio_retained", false)
        .put("waveform_retained", false)
        .put("spectrum_retained", false)
        .put("settings", settingsJson)
}

private fun executionFor(mode: AcousticMode, context: CapabilityScreenContext, values: Map<String, String>): ExecutionResult {
    return when (mode) {
        AcousticMode.ANALYSE -> {
            val method = As100AcousticAnalyseMethod
            val request = method.request(action = method.id, context = context.request.invocationContext.asMap(method.id) + context.action.settings + values, signals = emptyList(), inputs = emptyList())
            method.result(request, values, context.request.invocationContext)
        }
        AcousticMode.TUNE -> {
            val method = As100AcousticTunerMethod
            val request = method.request(action = method.id, context = context.request.invocationContext.asMap(method.id) + context.action.settings + values, signals = emptyList(), inputs = emptyList())
            method.result(request, values, context.request.invocationContext)
        }
        AcousticMode.LEVEL -> {
            val method = As100AcousticLevelMethod
            val request = method.request(action = method.id, context = context.request.invocationContext.asMap(method.id) + context.action.settings + values, signals = emptyList(), inputs = emptyList())
            method.result(request, values, context.request.invocationContext)
        }
        AcousticMode.COMPARE -> {
            val method = As100AcousticCompareMethod
            val request = method.request(action = method.id, context = context.request.invocationContext.asMap(method.id) + context.action.settings + values, signals = emptyList(), inputs = emptyList())
            method.result(request, values, context.request.invocationContext)
        }
    }
}

private fun statusField(mode: AcousticMode): String = when (mode) {
    AcousticMode.ANALYSE -> AcousticAnalyseFields.STATUS
    AcousticMode.TUNE -> AcousticTunerFields.STATUS
    AcousticMode.LEVEL -> AcousticLevelFields.STATUS
    AcousticMode.COMPARE -> AcousticCompareFields.STATUS
}

@Composable private fun SettingsHeader() {
    Spacer(Modifier.height(14.dp))
    Text("Measurement settings", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun BigMeasurement(primary: String, secondary: String) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(primary, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(secondary, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit, integerOnly: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw -> onChange(raw.filter { it.isDigit() || (!integerOnly && (it == '.' || it == '-')) }) },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun ChoiceDropdown(label: String, value: String, choices: List<String>, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Text(label, style = MaterialTheme.typography.labelMedium)
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(value.replace('_', ' '))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { choice ->
                DropdownMenuItem(text = { Text(choice.replace('_', ' ')) }, onClick = { onChange(choice); expanded = false })
            }
        }
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun WaveformView(waveform: FloatArray) {
    val line = MaterialTheme.colorScheme.primary
    val guide = MaterialTheme.colorScheme.outlineVariant
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp)) {
            Text("Oscilloscope", style = MaterialTheme.typography.labelMedium)
            Canvas(Modifier.fillMaxWidth().height(100.dp)) {
                drawLine(guide, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), 1f)
                if (waveform.size > 1) {
                    for (i in 1 until waveform.size) {
                        val x1 = (i - 1).toFloat() / (waveform.size - 1) * size.width
                        val x2 = i.toFloat() / (waveform.size - 1) * size.width
                        val y1 = size.height / 2f - waveform[i - 1] * size.height * 0.45f
                        val y2 = size.height / 2f - waveform[i] * size.height * 0.45f
                        drawLine(line, Offset(x1, y1), Offset(x2, y2), 2f, cap = StrokeCap.Round)
                    }
                }
            }
        }
    }
}

@Composable
private fun SpectrumView(spectrum: FloatArray) {
    val bar = MaterialTheme.colorScheme.secondary
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp)) {
            Text("Frequency spectrum · 0 to Nyquist", style = MaterialTheme.typography.labelMedium)
            Canvas(Modifier.fillMaxWidth().height(110.dp)) {
                if (spectrum.isNotEmpty()) {
                    val width = size.width / spectrum.size
                    spectrum.forEachIndexed { index, db ->
                        val normalized = ((db + 90f) / 90f).coerceIn(0f, 1f)
                        drawRect(bar, topLeft = Offset(index * width, size.height * (1f - normalized)), size = Size(max(1f, width), size.height * normalized))
                    }
                }
            }
        }
    }
}

@Composable
private fun TunerGauge(cents: Double, greenZoneCents: Double, active: Boolean) {
    val outline = MaterialTheme.colorScheme.outline
    val needle = MaterialTheme.colorScheme.primary
    val green = Color(0xFF2E7D32)
    Canvas(Modifier.fillMaxWidth().height(84.dp).padding(horizontal = 12.dp)) {
        val centerY = size.height * 0.58f
        drawLine(outline, Offset(0f, centerY), Offset(size.width, centerY), 2f)
        val greenHalf = (greenZoneCents / 50.0).coerceIn(0.02, 0.5).toFloat() * size.width / 2f
        drawRect(green.copy(alpha = 0.24f), Offset(size.width / 2f - greenHalf, centerY - 18f), Size(greenHalf * 2f, 36f))
        drawLine(green, Offset(size.width / 2f, centerY - 26f), Offset(size.width / 2f, centerY + 26f), 3f)
        if (active) {
            val x = size.width / 2f + (cents.coerceIn(-50.0, 50.0) / 50.0).toFloat() * size.width / 2f
            drawLine(needle, Offset(x, centerY - 32f), Offset(x, centerY + 32f), 5f, cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun LevelMeter(dbfs: Double) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val fill = MaterialTheme.colorScheme.primary
    val fraction = ((dbfs.coerceIn(-90.0, 0.0) + 90.0) / 90.0).toFloat()
    Canvas(Modifier.fillMaxWidth().height(34.dp).padding(vertical = 7.dp)) {
        drawRect(track, Offset.Zero, size)
        drawRect(fill, Offset.Zero, Size(size.width * fraction, size.height))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("−90 dBFS", style = MaterialTheme.typography.labelSmall)
        Text("0 dBFS", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun CompareGauge(comparison: AcousticsAlgorithms.ComparisonResult?, toleranceValue: Double) {
    val outline = MaterialTheme.colorScheme.outline
    val needle = MaterialTheme.colorScheme.primary
    val green = Color(0xFF2E7D32)
    Canvas(Modifier.fillMaxWidth().height(74.dp).padding(horizontal = 12.dp)) {
        val y = size.height / 2f
        drawLine(outline, Offset(0f, y), Offset(size.width, y), 2f)
        val greenWidth = size.width * 0.34f
        drawRect(green.copy(alpha = 0.22f), Offset(size.width / 2f - greenWidth / 2f, y - 16f), Size(greenWidth, 32f))
        drawLine(green, Offset(size.width / 2f, y - 24f), Offset(size.width / 2f, y + 24f), 3f)
        comparison?.let {
            val metric = when (it.toleranceMode) {
                "percent" -> it.differencePercent
                "cents" -> it.differenceCents
                else -> it.differenceHz
            }
            val denom = max(toleranceValue * 3.0, 1e-9)
            val x = size.width / 2f + (metric.coerceIn(-denom, denom) / denom).toFloat() * size.width / 2f
            drawLine(needle, Offset(x, y - 28f), Offset(x, y + 28f), 5f, cap = StrokeCap.Round)
        }
    }
}

private fun Map<String, String>.d(key: String, fallback: Double): Double = this[key]?.toDoubleOrNull() ?: fallback
private fun Map<String, String>.i(key: String, fallback: Int): Int = this[key]?.toIntOrNull() ?: fallback

private fun fmt(value: Double?, decimals: Int): String {
    if (value == null || !value.isFinite()) return ""
    return "%.${decimals}f".format(value).trimEnd('0').trimEnd('.')
}

private fun signed(value: Double, decimals: Int): String = (if (value >= 0.0) "+" else "") + fmt(value, decimals)

private fun stringMapToJson(values: Map<String, String>): String {
    val json = JSONObject()
    values.forEach { (key, value) -> json.put(key, value) }
    return json.toString()
}

private fun jsonToStringMap(raw: String): Map<String, String> {
    val json = JSONObject(raw)
    return json.keys().asSequence().associateWith { key -> if (json.isNull(key)) "" else json.optString(key, "") }
}
