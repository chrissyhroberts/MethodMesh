package com.example.methodmesh.modules.music

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import org.json.JSONArray
import org.json.JSONObject

internal object EuclideanRhythmCapabilityScreen : SimpleMusicScreen(As100EuclideanRhythmMethod, "Euclidean rhythm", "Distribute hits evenly over a step grid.", listOf(InputSpec("steps", "Steps", "16"), InputSpec("pulses", "Hits", "5"), InputSpec("rotation", "Rotation", "0")))
internal object PatternMutationCapabilityScreen : SimpleMusicScreen(As100PatternMutationMethod, "Pattern mutation", "Make reproducible rhythmic variations.", listOf(InputSpec("pattern", "Pattern", "x...x...x...x..."), InputSpec("mode", "Mutation", "random", listOf("random", "sparser", "denser", "invert", "rotate_left", "rotate_right", "half_time", "double_time")), InputSpec("amount", "Amount 0–1", "0.25"), InputSpec("seed", "Seed", "1")))
internal object ProbabilityPatternCapabilityScreen : SimpleMusicScreen(As100ProbabilityPatternMethod, "Probability sequencer", "Turn trigger probabilities into a reproducible evolving rhythm.", listOf(InputSpec("probabilities", "Step probabilities", "1,0,0.4,0,1,0,0.25,0"), InputSpec("seed", "Seed", "1")))
internal object ChordProgressionCapabilityScreen : SimpleMusicScreen(As100ChordProgressionMethod, "Chord progression", "Build from Roman numerals or generate a diatonic progression.", listOf(InputSpec("root", "Root", "C"), InputSpec("mode", "Mode", "major", listOf("major", "minor")), InputSpec("roman", "Roman numerals (optional)", "I V vi IV"), InputSpec("bars", "Generated bars", "4"), InputSpec("seed", "Seed", "1"), InputSpec("prefer_flats", "Accidentals", "false", listOf("false", "true"))))
internal object ArpeggiatorCapabilityScreen : SimpleMusicScreen(As100ArpeggiatorMethod, "Arpeggiator", "Turn a chord into a note sequence.", listOf(InputSpec("chord", "Chord", "Am"), InputSpec("pattern", "Pattern", "up", listOf("up", "down", "up_down", "outside_in")), InputSpec("octaves", "Octaves", "2"), InputSpec("base_octave", "Base octave", "3"), InputSpec("prefer_flats", "Accidentals", "false", listOf("false", "true"))))
internal object BasslineCapabilityScreen : SimpleMusicScreen(As100BasslineMethod, "Bassline generator", "Generate a simple bassline from chord symbols.", listOf(InputSpec("progression", "Chords", "Am F C G"), InputSpec("style", "Style", "root_fifth", listOf("roots", "root_fifth", "octaves", "walking")), InputSpec("octave", "Octave", "2"), InputSpec("prefer_flats", "Accidentals", "false", listOf("false", "true"))))
internal object MelodySequencerCapabilityScreen : SimpleMusicScreen(As100MelodySequencerMethod, "Scale-locked melody", "Map scale degrees to notes in a chosen key.", listOf(InputSpec("root", "Root", "C"), InputSpec("scale", "Scale", "minor_pentatonic", listOf("major", "natural_minor", "minor_pentatonic", "major_pentatonic", "dorian", "mixolydian", "blues")), InputSpec("degrees", "Scale degrees", "1,3,4,5,3,2,1"), InputSpec("octave", "Octave", "4"), InputSpec("prefer_flats", "Accidentals", "false", listOf("false", "true"))))
internal object MotifGeneratorCapabilityScreen : SimpleMusicScreen(As100MotifGeneratorMethod, "Motif variations", "Retrograde, invert and transpose a short motif.", listOf(InputSpec("notes", "Motif notes", "C4 E4 G4 A4"), InputSpec("transpose_semitones", "Transpose semitones", "2")))
internal object SongSketchCapabilityScreen : SimpleMusicScreen(As100SongSketchMethod, "Song sketch", "Bundle tempo, harmony, rhythm and structure into a compact composition sketch.", listOf(InputSpec("title", "Title", "Untitled sketch"), InputSpec("bpm", "BPM", "100"), InputSpec("root", "Root", "C"), InputSpec("mode", "Mode", "major", listOf("major", "minor")), InputSpec("progression", "Progression", "C | G | Am | F"), InputSpec("beat", "Beat", "x...x...x...x..."), InputSpec("bassline", "Bassline", ""), InputSpec("melody", "Melody", ""), InputSpec("structure", "Structure", "Intro 4; Verse 8; Chorus 8")))

internal object RhythmCaptureCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100RhythmCaptureMethod.ID
    override val title = "Tap a rhythm"
    override val description = "Tap a rhythm, infer its pulse, and retain both raw and quantised timing."
    @Composable override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) = RhythmCaptureUi(context, onBack, onConfirmed, onCancel)
}

internal object DrumMachineCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100DrumMachineMethod.ID
    override val title = "Drum machine"
    override val description = "Four-lane synthesized step sequencer with tempo and swing."
    @Composable override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) = DrumMachineUi(context, onBack, onConfirmed, onCancel)
}

internal object BeatPadsCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100BeatPadsMethod.ID
    override val title = "Beat pads"
    override val description = "Finger-drum synthesized percussion and capture the hit sequence."
    @Composable override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) = BeatPadsUi(context, onBack, onConfirmed, onCancel)
}

internal object LiveLooperCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100LiveLooperMethod.ID
    override val title = "Live looper"
    override val description = "Record a master phrase, then overdub independently mutable microphone layers."
    @Composable override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) = LiveLooperUi(context, onBack, onConfirmed, onCancel)
}

@Composable
private fun RhythmCaptureUi(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
    var tapsCsv by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
    var steps by rememberSaveable(context.action.canonicalId) { mutableStateOf(context.action.settings["steps_per_beat"] ?: context.action.settings["input_steps_per_beat"] ?: "4") }
    var bars by rememberSaveable(context.action.canonicalId) { mutableStateOf(context.action.settings["bars"] ?: context.action.settings["input_bars"] ?: "1") }
    var beatsPerBar by rememberSaveable(context.action.canonicalId) { mutableStateOf(context.action.settings["beats_per_bar"] ?: context.action.settings["input_beats_per_bar"] ?: "4") }
    var forcedBpm by rememberSaveable(context.action.canonicalId) { mutableStateOf(context.action.settings["bpm"] ?: context.action.settings["input_bpm"] ?: "0") }
    var committedJson by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
    var autoReturned by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }

    val supplied = context.action.settings["tap_times_ms"] ?: context.action.settings["input_tap_times_ms"]
    val taps = tapsCsv.split(',').mapNotNull { it.toDoubleOrNull() }
    val activeTapTimes = if (taps.size >= 2) taps.joinToString(",") else supplied.orEmpty()
    val live = if (activeTapTimes.isNotBlank()) As100RhythmCaptureMethod.calculate(
        context.action.settings + mapOf("tap_times_ms" to activeTapTimes, "steps_per_beat" to steps, "bars" to bars, "beats_per_bar" to beatsPerBar, "bpm" to forcedBpm)
    ) else null
    val committedValues = committedJson?.let { MusicV105.valuesFromJson(As100RhythmCaptureMethod.fields, it) }
    val committedExecution = committedValues?.let { creationExecution(As100RhythmCaptureMethod, context, it) }

    LaunchedEffect(context.startsImmediately, supplied, autoReturned, steps, bars, beatsPerBar, forcedBpm) {
        if (context.startsImmediately && MusicV105.shouldReturnImmediately(context) && !autoReturned && !supplied.isNullOrBlank()) {
            val values = As100RhythmCaptureMethod.calculate(context.action.settings + mapOf("tap_times_ms" to supplied, "steps_per_beat" to steps, "bars" to bars, "beats_per_bar" to beatsPerBar, "bpm" to forcedBpm))
            if (values[As100RhythmCaptureMethod.fields.status] == "succeeded") {
                autoReturned = true
                onConfirmed(creationExecution(As100RhythmCaptureMethod, context, values))
            }
        }
    }

    CapabilityScreenScaffold(
        title = "Tap a rhythm", capabilityId = As100RhythmCaptureMethod.ID, context = context, canGoBack = context.stepNumber > 1,
        capturedResult = null, resultPreview = emptyMap<String, String>(), onBack = onBack, onRetry = { committedJson = null; tapsCsv = "" },
        onConfirm = { committedExecution?.let(onConfirmed) }, onCancel = onCancel,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (committedValues == null || committedExecution == null) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val liveBpm = live?.get(As100RhythmCaptureMethod.fields.field("bpm")).orEmpty()
                        val livePattern = live?.get(As100RhythmCaptureMethod.fields.field("pattern")).orEmpty()
                        if (liveBpm.isNotBlank()) {
                            CopyableMusicText(
                                value = liveBpm,
                                label = "BPM",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        } else {
                            Text("Tap your rhythm", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        }
                        if (livePattern.isNotBlank()) {
                            CopyableMusicText(value = livePattern, label = "rhythm pattern", style = MaterialTheme.typography.titleMedium)
                        } else {
                            Text("The first tap becomes step zero.", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                Button(onClick = { val now = SystemClock.elapsedRealtime().toDouble(); tapsCsv = (taps + now).takeLast(64).joinToString(",") }, modifier = Modifier.fillMaxWidth().height(170.dp)) { Text("TAP", style = MaterialTheme.typography.displayMedium) }
                if (context.settingShouldBeShown("steps_per_beat")) OutlinedTextField(steps, { steps = it }, label = { Text("Steps / beat") }, modifier = Modifier.fillMaxWidth())
                if (context.settingShouldBeShown("bars")) OutlinedTextField(bars, { bars = it }, label = { Text("Bars") }, modifier = Modifier.fillMaxWidth())
                if (context.settingShouldBeShown("beats_per_bar")) OutlinedTextField(beatsPerBar, { beatsPerBar = it }, label = { Text("Beats / bar") }, modifier = Modifier.fillMaxWidth())
                if (context.settingShouldBeShown("bpm")) OutlinedTextField(forcedBpm, { forcedBpm = it }, label = { Text("Forced BPM (0 = infer)") }, modifier = Modifier.fillMaxWidth())
                live?.let { MusicWorkingResult(As100RhythmCaptureMethod, it, heading = "Captured rhythm", secondaryLimit = 3) }
                OutlinedButton(onClick = { tapsCsv = "" }, modifier = Modifier.fillMaxWidth()) { Text("Reset taps") }
                Button(
                    enabled = live?.get(As100RhythmCaptureMethod.fields.status) == "succeeded",
                    onClick = {
                        live?.let {
                            val execution = creationExecution(As100RhythmCaptureMethod, context, it)
                            if (MusicV105.shouldReturnImmediately(context)) onConfirmed(execution)
                            else committedJson = MusicV105.valuesToJson(it)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Commit") }
            } else {
                MusicCommittedPanel(As100RhythmCaptureMethod, committedValues, committedExecution, onDone = onConfirmed, onEdit = { committedJson = null })
            }
        }
    }
}

@Composable
private fun DrumMachineUi(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
    var bpm by rememberSaveable(context.action.canonicalId) { mutableStateOf(context.action.settings["bpm"] ?: context.action.settings["input_bpm"] ?: "110") }
    var steps by rememberSaveable(context.action.canonicalId) { mutableStateOf(context.action.settings["steps"] ?: context.action.settings["input_steps"] ?: "16") }
    var swing by rememberSaveable(context.action.canonicalId) { mutableStateOf((context.action.settings["swing"] ?: context.action.settings["input_swing"] ?: "0").toFloatOrNull() ?: 0f) }
    var kick by rememberSaveable(context.action.canonicalId) { mutableStateOf(context.action.settings["kick"] ?: context.action.settings["input_kick"] ?: "x...x...x...x...") }
    var snare by rememberSaveable(context.action.canonicalId) { mutableStateOf(context.action.settings["snare"] ?: context.action.settings["input_snare"] ?: "....x.......x...") }
    var hat by rememberSaveable(context.action.canonicalId) { mutableStateOf(context.action.settings["hat"] ?: context.action.settings["input_hat"] ?: "x.x.x.x.x.x.x.x.") }
    var clap by rememberSaveable(context.action.canonicalId) { mutableStateOf(context.action.settings["clap"] ?: context.action.settings["input_clap"] ?: "................") }
    var playing by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
    var committedJson by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
    var autoReturned by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
    val engine = remember { DrumSynthEngine() }
    DisposableEffect(Unit) { onDispose { engine.stop() } }

    val stepCount = steps.toIntOrNull()?.coerceIn(4, 64) ?: 16
    val patterns = mapOf("kick" to kick, "snare" to snare, "hat" to hat, "clap" to clap)
    fun normalized(value: String): String = value.padEnd(stepCount, '.').take(stepCount)
    fun setPattern(voice: String, value: String) {
        when (voice) { "kick" -> kick = value; "snare" -> snare = value; "hat" -> hat = value; "clap" -> clap = value }
    }
    fun toggle(voice: String, index: Int) {
        val chars = normalized(patterns[voice].orEmpty()).toCharArray()
        chars[index] = if (chars[index] == 'x' || chars[index] == 'X') '.' else 'x'
        setPattern(voice, String(chars))
    }
    val workingValues = As100DrumMachineMethod.calculate(mapOf("bpm" to bpm, "steps" to stepCount.toString(), "swing" to swing.toString()) + patterns.mapValues { normalized(it.value) })
    val committedValues = committedJson?.let { MusicV105.valuesFromJson(As100DrumMachineMethod.fields, it) }
    val committedExecution = committedValues?.let { creationExecution(As100DrumMachineMethod, context, it) }

    LaunchedEffect(playing, bpm, steps, swing, kick, snare, hat, clap) {
        if (playing) engine.playPattern(bpm.toDoubleOrNull() ?: 110.0, stepCount, swing.toDouble(), patterns.mapValues { MusicCreationAlgorithms.parsePattern(normalized(it.value)) })
        else engine.stop()
    }

    LaunchedEffect(context.startsImmediately, context.submitsImmediately, autoReturned, bpm, steps, swing, kick, snare, hat, clap) {
        if (context.startsImmediately && MusicV105.shouldReturnImmediately(context) && !autoReturned && workingValues[As100DrumMachineMethod.fields.status] == "succeeded") {
            autoReturned = true
            onConfirmed(creationExecution(As100DrumMachineMethod, context, workingValues))
        }
    }

    CapabilityScreenScaffold(
        title = "Drum machine", capabilityId = As100DrumMachineMethod.ID, context = context, canGoBack = context.stepNumber > 1,
        capturedResult = null, resultPreview = emptyMap<String, String>(), onBack = { engine.stop(); onBack() }, onRetry = { committedJson = null },
        onConfirm = { committedExecution?.let(onConfirmed) }, onCancel = { engine.stop(); onCancel() },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (committedValues == null || committedExecution == null) {
                MusicWorkingResult(As100DrumMachineMethod, workingValues, heading = "Current beat", secondaryLimit = 2)
                if (context.settingShouldBeShown("bpm")) OutlinedTextField(bpm, { bpm = it }, label = { Text("BPM") }, modifier = Modifier.fillMaxWidth())
                if (context.settingShouldBeShown("steps")) OutlinedTextField(steps, { steps = it }, label = { Text("Steps (4–64)") }, modifier = Modifier.fillMaxWidth())
                if (context.settingShouldBeShown("swing")) {
                    Column(Modifier.fillMaxWidth()) {
                        Text("Swing ${"%.0f".format(swing * 100)}%")
                        Slider(swing, { swing = it }, valueRange = 0f..0.5f)
                    }
                }
                patterns.keys.forEach { voice ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(voice.uppercase())
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            MusicCreationAlgorithms.parsePattern(normalized(patterns[voice].orEmpty())).take(stepCount).forEachIndexed { i, on ->
                                Card(Modifier.width(32.dp).height(38.dp).clickable { toggle(voice, i) }) {
                                    Spacer(Modifier.fillMaxWidth().height(38.dp).background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant))
                                }
                            }
                        }
                    }
                }
                Button(onClick = { playing = !playing }, modifier = Modifier.fillMaxWidth()) { Text(if (playing) "STOP" else "PLAY") }
                OutlinedButton(onClick = { val empty = ".".repeat(stepCount); kick = empty; snare = empty; hat = empty; clap = empty }, modifier = Modifier.fillMaxWidth()) { Text("Clear pattern") }
                Button(
                    enabled = workingValues[As100DrumMachineMethod.fields.status] == "succeeded",
                    onClick = {
                        engine.stop(); playing = false
                        val execution = creationExecution(As100DrumMachineMethod, context, workingValues)
                        if (MusicV105.shouldReturnImmediately(context)) onConfirmed(execution)
                        else committedJson = MusicV105.valuesToJson(workingValues)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Commit") }
            } else {
                MusicCommittedPanel(As100DrumMachineMethod, committedValues, committedExecution, onDone = { engine.stop(); onConfirmed(it) }, onEdit = { committedJson = null })
            }
        }
    }
}

@Composable
private fun BeatPadsUi(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
    val engine = remember { DrumSynthEngine() }
    DisposableEffect(Unit) { onDispose { engine.stop() } }
    var hitsJson by rememberSaveable(context.action.canonicalId) { mutableStateOf(context.action.settings["hits_json"] ?: context.action.settings["input_hits_json"] ?: "[]") }
    var recording by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
    var startedAt by rememberSaveable(context.action.canonicalId) { mutableStateOf(0L) }
    var committedJson by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
    var autoReturned by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }

    fun hit(voice: String) {
        engine.playVoice(voice)
        if (recording) {
            if (startedAt == 0L) startedAt = SystemClock.elapsedRealtime()
            val arr = runCatching { JSONArray(hitsJson) }.getOrDefault(JSONArray())
            arr.put(JSONObject().put("voice", voice).put("at_ms", SystemClock.elapsedRealtime() - startedAt))
            hitsJson = arr.toString()
        }
    }
    val workingValues = As100BeatPadsMethod.calculate(mapOf("hits_json" to hitsJson))
    val committedValues = committedJson?.let { MusicV105.valuesFromJson(As100BeatPadsMethod.fields, it) }
    val committedExecution = committedValues?.let { creationExecution(As100BeatPadsMethod, context, it) }
    val hitCount = runCatching { JSONArray(hitsJson).length() }.getOrDefault(0)

    LaunchedEffect(context.startsImmediately, context.submitsImmediately, autoReturned, hitsJson) {
        if (context.startsImmediately && MusicV105.shouldReturnImmediately(context) && !autoReturned && hitCount > 0) {
            autoReturned = true
            onConfirmed(creationExecution(As100BeatPadsMethod, context, workingValues))
        }
    }

    CapabilityScreenScaffold(
        title = "Beat pads", capabilityId = As100BeatPadsMethod.ID, context = context, canGoBack = context.stepNumber > 1,
        capturedResult = null, resultPreview = emptyMap<String, String>(), onBack = { engine.stop(); onBack() }, onRetry = { committedJson = null; hitsJson = "[]" },
        onConfirm = { committedExecution?.let(onConfirmed) }, onCancel = { engine.stop(); onCancel() },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (committedValues == null || committedExecution == null) {
                listOf(listOf("kick", "snare", "hat"), listOf("clap", "tom", "click")).forEach { row ->
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { voice -> Button(onClick = { hit(voice) }, modifier = Modifier.width(104.dp).height(92.dp)) { Text(voice.uppercase()) } }
                    }
                }
                Button(
                    onClick = {
                        recording = !recording
                        if (recording) { hitsJson = "[]"; startedAt = 0L }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (recording) "STOP CAPTURE" else "CAPTURE") }
                OutlinedButton(onClick = { hitsJson = "[]"; startedAt = 0L }, modifier = Modifier.fillMaxWidth()) { Text("Clear") }
                MusicWorkingResult(As100BeatPadsMethod, workingValues, heading = "Captured sequence", secondaryLimit = 2)
                Button(
                    enabled = hitCount > 0,
                    onClick = {
                        recording = false
                        val execution = creationExecution(As100BeatPadsMethod, context, workingValues)
                        if (MusicV105.shouldReturnImmediately(context)) onConfirmed(execution)
                        else committedJson = MusicV105.valuesToJson(workingValues)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Commit") }
            } else {
                MusicCommittedPanel(As100BeatPadsMethod, committedValues, committedExecution, onDone = { engine.stop(); onConfirmed(it) }, onEdit = { committedJson = null })
            }
        }
    }
}

@Composable
private fun LiveLooperUi(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
    val androidContext = LocalContext.current
    val sessionKey = context.action.canonicalId.ifBlank { As100LiveLooperMethod.ID }
    val engine = remember(sessionKey) { MusicAudioSessionStore.looper(sessionKey) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var revision by rememberSaveable(sessionKey) { mutableStateOf(0) }
    var permission by remember { mutableStateOf(ContextCompat.checkSelfPermission(androidContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    var committedJson by rememberSaveable(sessionKey) { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permission = it }

    val layers = remember(revision) { engine.layerSnapshot() }
    fun snapshot(): Map<String, String> {
        val arr = JSONArray()
        engine.layerSnapshot().forEach { arr.put(JSONObject().put("id", it.id).put("muted", it.muted)) }
        return As100LiveLooperMethod.calculate(mapOf("loop_ms" to engine.loopMs.toString(), "layer_count" to engine.layerCount.toString(), "playing" to engine.isPlaying().toString(), "recording" to engine.isRecording().toString(), "layers_json" to arr.toString()))
    }
    val workingValues = snapshot()
    val committedValues = committedJson?.let { MusicV105.valuesFromJson(As100LiveLooperMethod.fields, it) }
    val committedExecution = committedValues?.let { creationExecution(As100LiveLooperMethod, context, it) }

    CapabilityScreenScaffold(
        title = "Live looper", capabilityId = As100LiveLooperMethod.ID, context = context, canGoBack = context.stepNumber > 1,
        capturedResult = null, resultPreview = emptyMap<String, String>(),
        onBack = { engine.clear(); MusicAudioSessionStore.remove(sessionKey); onBack() },
        onRetry = { committedJson = null },
        onConfirm = { committedExecution?.let(onConfirmed) },
        onCancel = { engine.clear(); MusicAudioSessionStore.remove(sessionKey); onCancel() },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (committedValues == null || committedExecution == null) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (engine.layerCount == 0) {
                            Text("Ready for first loop", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        } else {
                            CopyableMusicText(
                                value = engine.layerCount.toString(),
                                label = "layer count",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            CopyableMusicText(
                                value = engine.loopMs.toString(),
                                label = "loop duration milliseconds",
                            )
                        }
                        Text(if (engine.isRecording()) "Recording…" else if (engine.isPlaying()) "Playing" else "Stopped")
                    }
                }
                MusicWorkingResult(As100LiveLooperMethod, workingValues, heading = "Current loop", secondaryLimit = 3)
                if (!permission) Button(onClick = { launcher.launch(Manifest.permission.RECORD_AUDIO) }, modifier = Modifier.fillMaxWidth()) { Text("Allow microphone") }
                Button(
                    enabled = permission,
                    onClick = {
                        if (engine.isRecording()) {
                            engine.stopRecording(); revision++
                        } else {
                            engine.startRecording { mainHandler.post { revision++; if (engine.layerCount == 1 && !engine.isPlaying()) engine.startPlayback() } }
                            revision++
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(90.dp),
                ) { Text(if (engine.isRecording()) "CLOSE LAYER" else if (engine.layerCount == 0) "RECORD FIRST LOOP" else "OVERDUB") }
                Button(enabled = engine.layerCount > 0, onClick = { if (engine.isPlaying()) engine.stopPlayback() else engine.startPlayback(); revision++ }, modifier = Modifier.fillMaxWidth()) { Text(if (engine.isPlaying()) "STOP" else "PLAY") }
                OutlinedButton(enabled = engine.layerCount > 0, onClick = { engine.undo(); revision++ }, modifier = Modifier.fillMaxWidth()) { Text("Undo last layer") }
                LazyColumn(Modifier.height(170.dp)) {
                    itemsIndexed(layers) { _, layer ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                CopyableMusicText(value = layer.id.toString(), label = "layer id", fontWeight = FontWeight.Bold)
                                OutlinedButton(onClick = { engine.toggleMute(layer.id); revision++ }, modifier = Modifier.fillMaxWidth()) { Text(if (layer.muted) "Unmute" else "Mute") }
                            }
                        }
                    }
                }
                OutlinedButton(onClick = { engine.clear(); revision++; committedJson = null }, modifier = Modifier.fillMaxWidth()) { Text("Clear all") }
                Button(
                    enabled = engine.layerCount > 0 && !engine.isRecording(),
                    onClick = {
                        engine.stopPlayback()
                        val values = snapshot()
                        val execution = creationExecution(As100LiveLooperMethod, context, values)
                        if (MusicV105.shouldReturnImmediately(context)) {
                            MusicAudioSessionStore.remove(sessionKey)
                            onConfirmed(execution)
                        } else committedJson = MusicV105.valuesToJson(values)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Commit") }
                Text("Active loop PCM is retained across configuration changes in the current process. Process death still ends the unsaved audio session; committed output is the declared loop snapshot, not an audio-file export.", style = MaterialTheme.typography.bodySmall)
            } else {
                MusicCommittedPanel(
                    As100LiveLooperMethod,
                    committedValues,
                    committedExecution,
                    onDone = { MusicAudioSessionStore.remove(sessionKey); onConfirmed(it) },
                    onEdit = { committedJson = null },
                )
            }
        }
    }
}

private fun creationExecution(method: MusicPureMethod, context: CapabilityScreenContext, values: Map<String, String>): ExecutionResult {
    val request = method.request(action = method.id, context = context.request.invocationContext.asMap(method.id) + context.action.settings + values, signals = emptyList(), inputs = emptyList())
    return method.result(request, values, context.request.invocationContext)
}
