package com.example.methodmesh.modules.music

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import kotlinx.coroutines.delay
import org.json.JSONObject

internal data class InputSpec(
    val key: String,
    val label: String,
    val default: String,
    val choices: List<String> = emptyList(),
    val hint: String = "",
)

private val subdivisions = listOf(
    "whole", "half", "quarter", "eighth", "sixteenth", "thirty_second",
    "dotted_half", "dotted_quarter", "dotted_eighth",
    "quarter_triplet", "eighth_triplet", "sixteenth_triplet",
)
private val chordQualities = listOf("", "m", "dim", "aug", "sus2", "sus4", "6", "m6", "7", "maj7", "m7", "mMaj7", "dim7", "m7b5", "add9", "9", "maj9", "m9")

internal object TapTempoCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100TapTempoMethod.ID
    override val title = "Tap tempo"
    override val description = "Tap the large pad repeatedly to estimate BPM and timing stability."
    @Composable override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) =
        TapTempoUi(context, onBack, onConfirmed, onCancel)
}

internal object MetronomeCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100MetronomeMethod.ID
    override val title = "Metronome"
    override val description = "Audio, visual and optional haptic metronome."
    @Composable override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) =
        MetronomeUi(context, onBack, onConfirmed, onCancel)
}

internal abstract class SimpleMusicScreen(
    private val method: MusicPureMethod,
    override val title: String,
    override val description: String,
    private val inputs: List<InputSpec>,
) : CapabilityScreenSpec {
    override val capabilityId = method.id
    @Composable override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) =
        SimpleMusicUi(method, title, context, inputs, onBack, onConfirmed, onCancel)
}

internal object NoteFrequencyCapabilityScreen : SimpleMusicScreen(As100NoteFrequencyMethod, "Note ↔ frequency", "Convert note names and frequencies.", listOf(
    InputSpec("mode", "Conversion", "note_to_frequency", listOf("note_to_frequency", "frequency_to_note")), InputSpec("note", "Note", "A4"), InputSpec("frequency_hz", "Frequency (Hz)", "440"), InputSpec("reference_a4_hz", "Reference A4 (Hz)", "440"), InputSpec("prefer_flats", "Accidentals", "false", listOf("false", "true"))))
internal object TransposeCapabilityScreen : SimpleMusicScreen(As100TransposeMethod, "Transpose", "Transpose notes or chord symbols.", listOf(
    InputSpec("input", "Notes / chords", "C G Am F"), InputSpec("input_type", "Input type", "chords", listOf("chords", "notes")), InputSpec("semitones", "Semitones", "0"), InputSpec("from_key", "From key (optional)", ""), InputSpec("to_key", "To key (optional)", ""), InputSpec("prefer_flats", "Accidentals", "false", listOf("false", "true"))))
internal object IntervalCapabilityScreen : SimpleMusicScreen(As100IntervalMethod, "Interval identifier", "Identify the interval between two notes.", listOf(InputSpec("note_a", "First note", "C4"), InputSpec("note_b", "Second note", "G4")))
internal object ChordCapabilityScreen : SimpleMusicScreen(As100ChordMethod, "Chord builder / identifier", "Build chord tones or identify exact chord matches.", listOf(
    InputSpec("mode", "Mode", "build", listOf("build", "identify")), InputSpec("root", "Root", "C"), InputSpec("quality", "Quality", "", chordQualities), InputSpec("notes", "Notes", "C E G"), InputSpec("prefer_flats", "Accidentals", "false", listOf("false", "true"))))
internal object ChordGuideCapabilityScreen : SimpleMusicScreen(As100ChordGuideMethod, "Chord / tab guide", "Show compact guitar chord fingering.", listOf(InputSpec("instrument", "Instrument", "guitar", listOf("guitar")), InputSpec("chord", "Chord", "C")))
internal object TempoConvertCapabilityScreen : SimpleMusicScreen(As100TempoConvertMethod, "Tempo converter", "Convert BPM to note/subdivision durations.", listOf(InputSpec("bpm", "Tempo (BPM)", "120"), InputSpec("subdivision", "Subdivision", "quarter", subdivisions)))
internal object DelayTimeCapabilityScreen : SimpleMusicScreen(As100DelayTimeMethod, "Delay-time calculator", "Calculate tempo-synchronised effect delay.", listOf(InputSpec("bpm", "Tempo (BPM)", "120"), InputSpec("subdivision", "Subdivision", "dotted_eighth", subdivisions)))
internal object TemperamentCapabilityScreen : SimpleMusicScreen(As100TemperamentMethod, "Temperament calculator", "Calculate EDO step size and frequency.", listOf(InputSpec("divisions", "Divisions of octave", "12"), InputSpec("step", "Step", "0"), InputSpec("reference_hz", "Reference frequency (Hz)", "440"), InputSpec("reference_step", "Reference step", "0")))
internal object PolyrhythmCapabilityScreen : SimpleMusicScreen(As100PolyrhythmMethod, "Polyrhythm", "Generate pulse positions for an A:B rhythm.", listOf(InputSpec("ratio_a", "A pulses", "3"), InputSpec("ratio_b", "B pulses", "2"), InputSpec("bpm", "Cycle tempo (BPM)", "60"), InputSpec("audio_enabled", "Audio", "true", listOf("true", "false")), InputSpec("haptic_enabled", "Haptics", "false", listOf("true", "false"))))
internal object TempoTrainerCapabilityScreen : SimpleMusicScreen(As100TempoTrainerMethod, "Tempo trainer", "Build a stepped practice tempo progression.", listOf(InputSpec("start_bpm", "Start BPM", "80"), InputSpec("target_bpm", "Target BPM", "120"), InputSpec("increment_bpm", "Increment BPM", "2"), InputSpec("bars_per_step", "Bars per step", "4")))
internal object HarmonicsCapabilityScreen : SimpleMusicScreen(As100HarmonicsMethod, "Harmonic series", "Explore harmonics and nearest tempered pitches.", listOf(InputSpec("fundamental_hz", "Fundamental (Hz)", "110"), InputSpec("count", "Harmonic count", "8"), InputSpec("reference_a4_hz", "Reference A4 (Hz)", "440"), InputSpec("prefer_flats", "Accidentals", "false", listOf("false", "true"))))
internal object SetListTimingCapabilityScreen : SimpleMusicScreen(As100SetListTimingMethod, "Set-list timing", "Add song durations and gaps.", listOf(InputSpec("durations", "Song durations", "3:30,4:00,3:45", hint = "Comma-separated mm:ss"), InputSpec("gaps", "Gaps", "0:20,0:20"), InputSpec("start_time", "Start time (optional)", "")))
internal object MusicReferenceCapabilityScreen : SimpleMusicScreen(As100MusicReferenceMethod, "Music reference", "Scale notes and diatonic chords for a key.", listOf(InputSpec("root", "Root", "C"), InputSpec("scale", "Scale", "major", listOf("major", "natural_minor", "harmonic_minor", "melodic_minor", "major_pentatonic", "minor_pentatonic", "blues", "dorian", "mixolydian")), InputSpec("prefer_flats", "Accidentals", "false", listOf("false", "true"))))

@Composable
private fun SimpleMusicUi(
    method: MusicPureMethod,
    title: String,
    context: CapabilityScreenContext,
    specs: List<InputSpec>,
    onBack: () -> Unit,
    onConfirmed: (ExecutionResult) -> Unit,
    onCancel: () -> Unit,
) {
    val initial = remember(context.action.canonicalId) {
        JSONObject().apply {
            specs.forEach { spec -> put(spec.key, context.action.settings[spec.key] ?: context.action.settings["input_${spec.key}"] ?: spec.default) }
        }.toString()
    }
    var stateJson by rememberSaveable(context.action.canonicalId) { mutableStateOf(initial) }
    var committedJson by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
    var autoReturned by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }

    val state = remember(stateJson) {
        val o = JSONObject(stateJson)
        specs.associate { it.key to o.optString(it.key, it.default) }
    }
    fun update(key: String, value: String) {
        stateJson = JSONObject(stateJson).put(key, value).toString()
    }

    val workingValues = method.calculate(state)
    val committedValues = committedJson?.let { MusicV105.valuesFromJson(method.fields, it) }
    val committedExecution = committedValues?.let { executionFor(method, context, it) }

    LaunchedEffect(context.startsImmediately, context.submitsImmediately, autoReturned, stateJson) {
        if (context.startsImmediately && MusicV105.shouldReturnImmediately(context) && !autoReturned && workingValues[method.fields.status] == "succeeded") {
            autoReturned = true
            onConfirmed(executionFor(method, context, workingValues))
        }
    }

    CapabilityScreenScaffold(
        title = title,
        capabilityId = method.id,
        context = context,
        canGoBack = context.stepNumber > 1,
        capturedResult = null,
        resultPreview = emptyMap<String, String>(),
        onBack = onBack,
        onRetry = { committedJson = null },
        onConfirm = { committedExecution?.let(onConfirmed) },
        onCancel = onCancel,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (committedValues == null || committedExecution == null) {
                specs.forEach { spec ->
                    if (context.settingShouldBeShown(spec.key)) {
                        if (spec.choices.isNotEmpty()) {
                            ChoiceInput(spec.label, state[spec.key].orEmpty(), spec.choices) { update(spec.key, it) }
                        } else {
                            OutlinedTextField(
                                value = state[spec.key].orEmpty(),
                                onValueChange = { update(spec.key, it) },
                                label = { Text(spec.label) },
                                supportingText = if (spec.hint.isNotBlank()) ({ Text(spec.hint) }) else null,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                MusicWorkingResult(method, workingValues)
                Button(
                    enabled = workingValues[method.fields.status] == "succeeded",
                    onClick = {
                        val execution = executionFor(method, context, workingValues)
                        if (MusicV105.shouldReturnImmediately(context)) onConfirmed(execution)
                        else committedJson = MusicV105.valuesToJson(workingValues)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Commit") }
            } else {
                MusicCommittedPanel(method, committedValues, committedExecution, onDone = onConfirmed, onEdit = { committedJson = null })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceInput(label: String, selected: String, choices: List<String>, onSelected: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            choices.forEach { value ->
                val display = when {
                    value.isBlank() -> "major"
                    label == "Accidentals" && value == "false" -> "Sharps"
                    label == "Accidentals" && value == "true" -> "Flats"
                    else -> value.replace('_', ' ')
                }
                if (selected == value) Button(onClick = { onSelected(value) }) { Text(display) }
                else OutlinedButton(onClick = { onSelected(value) }) { Text(display) }
            }
        }
    }
}

@Composable
private fun TapTempoUi(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
    var tapsCsv by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
    var committedJson by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
    var autoReturned by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
    val suppliedIntervals = context.action.settings["tap_intervals_ms"] ?: context.action.settings["input_tap_intervals_ms"]
    val taps = tapsCsv.split(',').mapNotNull { it.toLongOrNull() }
    val live = when {
        taps.size >= 2 -> As100TapTempoMethod.calculate(mapOf("tap_times_ms" to taps.joinToString(",")))
        !suppliedIntervals.isNullOrBlank() -> As100TapTempoMethod.calculate(mapOf("tap_intervals_ms" to suppliedIntervals))
        else -> null
    }
    val committedValues = committedJson?.let { MusicV105.valuesFromJson(As100TapTempoMethod.fields, it) }
    val committedExecution = committedValues?.let { executionFor(As100TapTempoMethod, context, it) }

    LaunchedEffect(context.startsImmediately, suppliedIntervals, autoReturned) {
        if (context.startsImmediately && MusicV105.shouldReturnImmediately(context) && !autoReturned && !suppliedIntervals.isNullOrBlank()) {
            val values = As100TapTempoMethod.calculate(mapOf("tap_intervals_ms" to suppliedIntervals))
            if (values[As100TapTempoMethod.fields.status] == "succeeded") {
                autoReturned = true
                onConfirmed(executionFor(As100TapTempoMethod, context, values))
            }
        }
    }

    CapabilityScreenScaffold(
        title = "Tap tempo", capabilityId = As100TapTempoMethod.ID, context = context, canGoBack = context.stepNumber > 1,
        capturedResult = null, resultPreview = emptyMap<String, String>(), onBack = onBack, onRetry = { committedJson = null; tapsCsv = "" },
        onConfirm = { committedExecution?.let(onConfirmed) }, onCancel = onCancel,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (committedValues == null || committedExecution == null) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val bpm = live?.get(As100TapTempoMethod.fields.field("bpm"))
                        if (bpm != null) {
                            CopyableMusicText(
                                value = bpm,
                                label = "BPM",
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        } else {
                            Text("— BPM", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                        }
                        if (live == null) {
                            Text("Tap at least twice")
                        } else {
                            val tapCount = live[As100TapTempoMethod.fields.field("tap_count")].orEmpty()
                            val cv = live[As100TapTempoMethod.fields.field("cv")].orEmpty()
                            CopyableMusicText(value = tapCount, label = "tap count")
                            CopyableMusicText(value = cv, label = "CV")
                        }
                    }
                }
                Button(
                    onClick = { val now = SystemClock.elapsedRealtime(); tapsCsv = (taps + now).takeLast(16).joinToString(",") },
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                ) { Text("TAP", style = MaterialTheme.typography.displayMedium) }
                live?.let { MusicWorkingResult(As100TapTempoMethod, it, heading = "Tempo") }
                OutlinedButton(onClick = { tapsCsv = "" }, modifier = Modifier.fillMaxWidth()) { Text("Reset") }
                Button(
                    enabled = live?.get(As100TapTempoMethod.fields.status) == "succeeded",
                    onClick = {
                        live?.let {
                            val execution = executionFor(As100TapTempoMethod, context, it)
                            if (MusicV105.shouldReturnImmediately(context)) onConfirmed(execution)
                            else committedJson = MusicV105.valuesToJson(it)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Commit") }
            } else {
                MusicCommittedPanel(As100TapTempoMethod, committedValues, committedExecution, onDone = onConfirmed, onEdit = { committedJson = null })
            }
        }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun MetronomeUi(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
    val androidContext = LocalContext.current
    var bpm by rememberSaveable(context.action.canonicalId) { mutableStateOf(context.action.settings["bpm"] ?: context.action.settings["input_bpm"] ?: "120") }
    var beats by rememberSaveable(context.action.canonicalId) { mutableStateOf(context.action.settings["beats_per_bar"] ?: context.action.settings["input_beats_per_bar"] ?: "4") }
    var subdivision by rememberSaveable(context.action.canonicalId) { mutableStateOf(context.action.settings["subdivision"] ?: context.action.settings["input_subdivision"] ?: "quarter") }
    var accentPattern by rememberSaveable(context.action.canonicalId) { mutableStateOf(context.action.settings["accent_pattern"] ?: context.action.settings["input_accent_pattern"] ?: "1") }
    var audioEnabled by rememberSaveable(context.action.canonicalId) { mutableStateOf((context.action.settings["audio_enabled"] ?: "true").toBoolean()) }
    var visualEnabled by rememberSaveable(context.action.canonicalId) { mutableStateOf((context.action.settings["visual_enabled"] ?: "true").toBoolean()) }
    var hapticEnabled by rememberSaveable(context.action.canonicalId) { mutableStateOf((context.action.settings["haptic_enabled"] ?: "false").toBoolean()) }
    var running by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
    var pulse by remember { mutableStateOf(false) }
    var beatIndex by rememberSaveable(context.action.canonicalId) { mutableStateOf(0) }
    var committedJson by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
    var autoReturned by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }

    val tone = remember { ToneGenerator(AudioManager.STREAM_MUSIC, 75) }
    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= 31) (androidContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        else androidContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    DisposableEffect(Unit) { onDispose { tone.release() } }

    val settings = mapOf(
        "bpm" to bpm,
        "beats_per_bar" to beats,
        "subdivision" to subdivision,
        "accent_pattern" to accentPattern,
        "audio_enabled" to audioEnabled.toString(),
        "visual_enabled" to visualEnabled.toString(),
        "haptic_enabled" to hapticEnabled.toString(),
    )
    val workingValues = As100MetronomeMethod.calculate(settings)
    val committedValues = committedJson?.let { MusicV105.valuesFromJson(As100MetronomeMethod.fields, it) }
    val committedExecution = committedValues?.let { executionFor(As100MetronomeMethod, context, it) }

    LaunchedEffect(running, bpm, subdivision, beats, audioEnabled, visualEnabled, hapticEnabled) {
        while (running) {
            pulse = visualEnabled
            beatIndex = (beatIndex % (beats.toIntOrNull() ?: 4).coerceAtLeast(1)) + 1
            if (audioEnabled) tone.startTone(if (beatIndex == 1) ToneGenerator.TONE_PROP_BEEP2 else ToneGenerator.TONE_PROP_BEEP, 35)
            if (hapticEnabled && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(if (beatIndex == 1) 45 else 22, VibrationEffect.DEFAULT_AMPLITUDE))
                else vibrator.vibrate(if (beatIndex == 1) 45 else 22)
            }
            delay(55)
            pulse = false
            val interval = runCatching { MusicAlgorithms.delayMs(bpm.toDouble(), subdivision).toLong() }.getOrDefault(500L)
            delay((interval - 55).coerceAtLeast(10))
        }
    }

    LaunchedEffect(context.startsImmediately, context.submitsImmediately, autoReturned, bpm, beats, subdivision) {
        if (context.startsImmediately && MusicV105.shouldReturnImmediately(context) && !autoReturned && workingValues[As100MetronomeMethod.fields.status] == "succeeded") {
            autoReturned = true
            onConfirmed(executionFor(As100MetronomeMethod, context, workingValues))
        }
    }

    CapabilityScreenScaffold(
        title = "Metronome", capabilityId = As100MetronomeMethod.ID, context = context, canGoBack = context.stepNumber > 1,
        capturedResult = null, resultPreview = emptyMap<String, String>(), onBack = { running = false; onBack() }, onRetry = { committedJson = null },
        onConfirm = { committedExecution?.let(onConfirmed) }, onCancel = { running = false; onCancel() },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (committedValues == null || committedExecution == null) {
                Surface(tonalElevation = if (pulse) 12.dp else 1.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Beat ${if (beatIndex == 0) 1 else beatIndex}", style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        MusicWorkingResult(As100MetronomeMethod, workingValues, heading = "Current metronome", secondaryLimit = 3)
                    }
                }
                if (context.settingShouldBeShown("bpm")) OutlinedTextField(bpm, { bpm = it }, label = { Text("BPM") }, modifier = Modifier.fillMaxWidth())
                if (context.settingShouldBeShown("beats_per_bar")) OutlinedTextField(beats, { beats = it }, label = { Text("Beats per bar") }, modifier = Modifier.fillMaxWidth())
                if (context.settingShouldBeShown("subdivision")) ChoiceInput("Subdivision", subdivision, subdivisions) { subdivision = it }
                if (context.settingShouldBeShown("accent_pattern")) OutlinedTextField(accentPattern, { accentPattern = it }, label = { Text("Accent beats") }, modifier = Modifier.fillMaxWidth())
                if (context.settingShouldBeShown("audio_enabled")) ChoiceInput("Audio click", audioEnabled.toString(), listOf("true", "false")) { audioEnabled = it.toBoolean() }
                if (context.settingShouldBeShown("visual_enabled")) ChoiceInput("Visual pulse", visualEnabled.toString(), listOf("true", "false")) { visualEnabled = it.toBoolean() }
                if (context.settingShouldBeShown("haptic_enabled")) ChoiceInput("Haptic pulse", hapticEnabled.toString(), listOf("true", "false")) { hapticEnabled = it.toBoolean() }
                Button(onClick = { running = !running }, modifier = Modifier.fillMaxWidth().height(72.dp)) { Text(if (running) "STOP" else "START") }
                Button(
                    enabled = workingValues[As100MetronomeMethod.fields.status] == "succeeded",
                    onClick = {
                        running = false
                        val execution = executionFor(As100MetronomeMethod, context, workingValues)
                        if (MusicV105.shouldReturnImmediately(context)) onConfirmed(execution)
                        else committedJson = MusicV105.valuesToJson(workingValues)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Commit") }
            } else {
                MusicCommittedPanel(As100MetronomeMethod, committedValues, committedExecution, onDone = { running = false; onConfirmed(it) }, onEdit = { committedJson = null })
            }
        }
    }
}

private fun executionFor(method: MusicPureMethod, context: CapabilityScreenContext, values: Map<String, String>): ExecutionResult {
    val request = method.request(
        action = method.id,
        context = context.request.invocationContext.asMap(method.id) + context.action.settings + values,
        signals = emptyList(),
        inputs = emptyList(),
    )
    return method.result(request, values, context.request.invocationContext)
}
