package com.example.methodmesh.modules.music

import android.os.SystemClock
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import org.json.JSONObject

internal object JamDashboardCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100JamDashboardMethod.ID
    override val title = "Jam / creation"
    override val description = "Live beat and rhythm scratchpad for making an idea quickly."
    @Composable override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) = JamDashboardUi(context, onBack, onConfirmed, onCancel)
}

internal object SongSketchDashboardCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100SongSketchDashboardMethod.ID
    override val title = "Song sketch"
    override val description = "Persistent composition board for harmony, rhythm, bass, melody and structure."
    @Composable override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) = SongSketchDashboardUi(context, onBack, onConfirmed, onCancel)
}

@Composable
private fun JamDashboardUi(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
    val androidContext = LocalContext.current
    val repo = remember(androidContext) { MusicCreationRepository(androidContext.applicationContext) }
    val stored = remember { repo.loadJam() }
    val engine = remember { DrumSynthEngine() }
    DisposableEffect(Unit) { onDispose { engine.stop() } }

    var bpm by rememberSaveable(context.action.canonicalId) { mutableStateOf(context.action.settings["bpm"] ?: context.action.settings["input_bpm"] ?: stored["bpm"].takeUnless { it.isNullOrBlank() } ?: "110") }
    var kick by rememberSaveable(context.action.canonicalId) { mutableStateOf(stored["kick"].takeUnless { it.isNullOrBlank() } ?: "x...x...x...x...") }
    var snare by rememberSaveable(context.action.canonicalId) { mutableStateOf(stored["snare"].takeUnless { it.isNullOrBlank() } ?: "....x.......x...") }
    var hat by rememberSaveable(context.action.canonicalId) { mutableStateOf(stored["hat"].takeUnless { it.isNullOrBlank() } ?: "x.x.x.x.x.x.x.x.") }
    var clap by rememberSaveable(context.action.canonicalId) { mutableStateOf(stored["clap"].takeUnless { it.isNullOrBlank() } ?: "................") }
    var playing by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
    var tapsCsv by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
    var committedJson by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
    var autoReturned by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }

    val taps = tapsCsv.split(',').mapNotNull { it.toDoubleOrNull() }
    val captured = if (taps.size >= 2) runCatching { MusicCreationAlgorithms.captureRhythm(taps) }.getOrNull() else null
    val capturedPattern = captured?.let { MusicCreationAlgorithms.patternString(it.pattern) } ?: stored["captured_pattern"].orEmpty()
    val patterns = mapOf("kick" to kick, "snare" to snare, "hat" to hat, "clap" to clap)
    fun setPattern(voice: String, value: String) {
        when (voice) { "kick" -> kick = value; "snare" -> snare = value; "hat" -> hat = value; "clap" -> clap = value }
    }
    fun toggle(voice: String, index: Int) {
        val chars = patterns[voice].orEmpty().padEnd(16, '.').take(16).toCharArray()
        chars[index] = if (chars[index] == 'x' || chars[index] == 'X') '.' else 'x'
        setPattern(voice, String(chars))
    }
    val drumJson = JSONObject().put("kick", kick).put("snare", snare).put("hat", hat).put("clap", clap).toString()
    val workingValues = As100JamDashboardMethod.calculate(mapOf("bpm" to bpm, "drum_pattern_json" to drumJson, "loop_ms" to "0", "loop_layers" to "0", "captured_pattern" to capturedPattern))
    val committedValues = committedJson?.let { MusicV105.valuesFromJson(As100JamDashboardMethod.fields, it) }
    val committedExecution = committedValues?.let { creationDashboardExecution(As100JamDashboardMethod, context, it) }

    LaunchedEffect(bpm, kick, snare, hat, clap, capturedPattern) {
        repo.saveJam(mapOf("bpm" to bpm, "kick" to kick, "snare" to snare, "hat" to hat, "clap" to clap, "captured_pattern" to capturedPattern))
    }
    LaunchedEffect(playing, bpm, kick, snare, hat, clap) {
        if (playing) engine.playPattern(bpm.toDoubleOrNull() ?: 110.0, 16, 0.0, patterns.mapValues { MusicCreationAlgorithms.parsePattern(it.value) })
        else engine.stop()
    }
    LaunchedEffect(context.startsImmediately, context.submitsImmediately, autoReturned) {
        if (context.startsImmediately && MusicV105.shouldReturnImmediately(context) && !autoReturned && workingValues[As100JamDashboardMethod.fields.status] == "succeeded") {
            autoReturned = true
            onConfirmed(creationDashboardExecution(As100JamDashboardMethod, context, workingValues))
        }
    }

    CapabilityScreenScaffold(
        title = "Jam / creation", capabilityId = As100JamDashboardMethod.ID, context = context, canGoBack = context.stepNumber > 1,
        capturedResult = null, resultPreview = emptyMap<String, String>(), onBack = { engine.stop(); onBack() }, onRetry = { committedJson = null },
        onConfirm = { committedExecution?.let(onConfirmed) }, onCancel = { engine.stop(); onCancel() },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (committedValues == null || committedExecution == null) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        CopyableMusicText(
                            value = bpm,
                            label = "BPM",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        if (capturedPattern.isNotBlank()) {
                            CopyableMusicText(value = capturedPattern, label = "captured pattern")
                        } else {
                            Text("Tap an idea or build the beat below")
                        }
                    }
                }
                MusicWorkingResult(As100JamDashboardMethod, workingValues, heading = "Jam snapshot", secondaryLimit = 3)
                if (context.settingShouldBeShown("bpm")) OutlinedTextField(bpm, { bpm = it }, label = { Text("Tempo") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = { val now = SystemClock.elapsedRealtime().toDouble(); tapsCsv = (taps + now).takeLast(64).joinToString(",") }, modifier = Modifier.fillMaxWidth().height(92.dp)) { Text("TAP RHYTHM") }
                if (captured != null) {
                    CopyableMusicValue("Captured pattern", capturedPattern)
                    OutlinedButton(onClick = { kick = capturedPattern.padEnd(16, '.').take(16) }, modifier = Modifier.fillMaxWidth()) { Text("Send rhythm to kick") }
                    OutlinedButton(onClick = { tapsCsv = "" }, modifier = Modifier.fillMaxWidth()) { Text("Clear taps") }
                }
                patterns.keys.forEach { voice ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(voice.uppercase())
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            MusicCreationAlgorithms.parsePattern(patterns[voice].orEmpty()).take(16).forEachIndexed { i, on ->
                                Card(Modifier.width(32.dp).height(36.dp).clickable { toggle(voice, i) }) {
                                    Spacer(Modifier.fillMaxWidth().height(36.dp).background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant))
                                }
                            }
                        }
                    }
                }
                Button(onClick = { playing = !playing }, modifier = Modifier.fillMaxWidth()) { Text(if (playing) "STOP BEAT" else "PLAY BEAT") }
                OutlinedButton(
                    onClick = {
                        kick = MusicCreationAlgorithms.patternString(MusicCreationAlgorithms.euclidean(16, 4, 0))
                        snare = MusicCreationAlgorithms.patternString(MusicCreationAlgorithms.euclidean(16, 2, 4))
                        hat = MusicCreationAlgorithms.patternString(MusicCreationAlgorithms.euclidean(16, 8, 0))
                        clap = MusicCreationAlgorithms.patternString(MusicCreationAlgorithms.euclidean(16, 3, 0))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Euclidean beat") }
                Text("Live looper recording remains its own capability so microphone permission and audio-session state stay explicit and composable.", style = MaterialTheme.typography.bodySmall)
                Button(
                    enabled = workingValues[As100JamDashboardMethod.fields.status] == "succeeded",
                    onClick = {
                        engine.stop(); playing = false
                        val execution = creationDashboardExecution(As100JamDashboardMethod, context, workingValues)
                        if (MusicV105.shouldReturnImmediately(context)) onConfirmed(execution)
                        else committedJson = MusicV105.valuesToJson(workingValues)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Commit snapshot") }
            } else {
                MusicCommittedPanel(As100JamDashboardMethod, committedValues, committedExecution, onDone = { engine.stop(); onConfirmed(it) }, onEdit = { committedJson = null })
            }
        }
    }
}

@Composable
private fun SongSketchDashboardUi(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
    val androidContext = LocalContext.current
    val repo = remember(androidContext) { MusicCreationRepository(androidContext.applicationContext) }
    val stored = remember { repo.loadSketch() }
    var title by rememberSaveable(context.action.canonicalId) { mutableStateOf(context.action.settings["title"] ?: context.action.settings["input_title"] ?: stored["title"].takeUnless { it.isNullOrBlank() } ?: "Untitled sketch") }
    var bpm by rememberSaveable(context.action.canonicalId) { mutableStateOf(context.action.settings["bpm"] ?: context.action.settings["input_bpm"] ?: stored["bpm"].takeUnless { it.isNullOrBlank() } ?: "100") }
    var key by rememberSaveable(context.action.canonicalId) { mutableStateOf(context.action.settings["key"] ?: context.action.settings["input_key"] ?: stored["key"].takeUnless { it.isNullOrBlank() } ?: "C major") }
    var progression by rememberSaveable(context.action.canonicalId) { mutableStateOf(context.action.settings["progression"] ?: context.action.settings["input_progression"] ?: stored["progression"].takeUnless { it.isNullOrBlank() } ?: "C | G | Am | F") }
    var beat by rememberSaveable(context.action.canonicalId) { mutableStateOf(context.action.settings["beat"] ?: context.action.settings["input_beat"] ?: stored["beat"].takeUnless { it.isNullOrBlank() } ?: "x...x...x...x...") }
    var bass by rememberSaveable(context.action.canonicalId) { mutableStateOf(context.action.settings["bassline"] ?: context.action.settings["input_bassline"] ?: stored["bassline"].takeUnless { it.isNullOrBlank() } ?: "") }
    var melody by rememberSaveable(context.action.canonicalId) { mutableStateOf(context.action.settings["melody"] ?: context.action.settings["input_melody"] ?: stored["melody"].takeUnless { it.isNullOrBlank() } ?: "") }
    var structure by rememberSaveable(context.action.canonicalId) { mutableStateOf(context.action.settings["structure"] ?: context.action.settings["input_structure"] ?: stored["structure"].takeUnless { it.isNullOrBlank() } ?: "Intro 4; Verse 8; Chorus 8") }
    var committedJson by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
    var autoReturned by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }

    fun current() = mapOf("title" to title, "bpm" to bpm, "key" to key, "progression" to progression, "beat" to beat, "bassline" to bass, "melody" to melody, "structure" to structure)
    val workingValues = As100SongSketchDashboardMethod.calculate(current())
    val committedValues = committedJson?.let { MusicV105.valuesFromJson(As100SongSketchDashboardMethod.fields, it) }
    val committedExecution = committedValues?.let { creationDashboardExecution(As100SongSketchDashboardMethod, context, it) }

    LaunchedEffect(title, bpm, key, progression, beat, bass, melody, structure) { repo.saveSketch(current()) }
    LaunchedEffect(context.startsImmediately, context.submitsImmediately, autoReturned) {
        if (context.startsImmediately && MusicV105.shouldReturnImmediately(context) && !autoReturned && workingValues[As100SongSketchDashboardMethod.fields.status] == "succeeded") {
            autoReturned = true
            onConfirmed(creationDashboardExecution(As100SongSketchDashboardMethod, context, workingValues))
        }
    }

    CapabilityScreenScaffold(
        title = "Song sketch", capabilityId = As100SongSketchDashboardMethod.ID, context = context, canGoBack = context.stepNumber > 1,
        capturedResult = null, resultPreview = emptyMap<String, String>(), onBack = onBack, onRetry = { committedJson = null },
        onConfirm = { committedExecution?.let(onConfirmed) }, onCancel = onCancel,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (committedValues == null || committedExecution == null) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        CopyableMusicText(value = title, label = "title", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        CopyableMusicText(value = bpm, label = "BPM")
                        CopyableMusicText(value = key, label = "key")
                        if (progression.isNotBlank()) CopyableMusicText(value = progression, label = "chord progression", style = MaterialTheme.typography.titleMedium)
                    }
                }
                MusicWorkingResult(As100SongSketchDashboardMethod, workingValues, heading = "Song sketch", secondaryLimit = 4)
                if (context.settingShouldBeShown("title")) OutlinedTextField(title, { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                if (context.settingShouldBeShown("bpm")) OutlinedTextField(bpm, { bpm = it }, label = { Text("BPM") }, modifier = Modifier.fillMaxWidth())
                if (context.settingShouldBeShown("key")) OutlinedTextField(key, { key = it }, label = { Text("Key") }, modifier = Modifier.fillMaxWidth())
                if (context.settingShouldBeShown("progression")) OutlinedTextField(progression, { progression = it }, label = { Text("Chord progression") }, modifier = Modifier.fillMaxWidth())
                OutlinedButton(
                    onClick = {
                        val parts = key.split(" ")
                        val root = parts.firstOrNull() ?: "C"
                        val mode = if (parts.getOrNull(1)?.contains("minor", true) == true) "minor" else "major"
                        progression = MusicCreationAlgorithms.generateProgression(root, mode, 4, System.currentTimeMillis()).joinToString(" | ")
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Generate chords") }
                OutlinedButton(onClick = { bass = MusicCreationAlgorithms.bassline(progression.split("|").map { it.trim() }.filter { it.isNotBlank() }, "root_fifth").joinToString(" ") }, modifier = Modifier.fillMaxWidth()) { Text("Make bass") }
                if (context.settingShouldBeShown("beat")) OutlinedTextField(beat, { beat = it }, label = { Text("Beat pattern") }, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { beat = MusicCreationAlgorithms.patternString(MusicCreationAlgorithms.euclidean(16, 5, (0..15).random())) }, modifier = Modifier.fillMaxWidth()) { Text("Generate rhythm") }
                if (context.settingShouldBeShown("bassline")) OutlinedTextField(bass, { bass = it }, label = { Text("Bassline") }, modifier = Modifier.fillMaxWidth())
                if (context.settingShouldBeShown("melody")) OutlinedTextField(melody, { melody = it }, label = { Text("Melody / motif") }, modifier = Modifier.fillMaxWidth())
                if (context.settingShouldBeShown("structure")) OutlinedTextField(structure, { structure = it }, label = { Text("Structure") }, modifier = Modifier.fillMaxWidth())
                OutlinedButton(
                    onClick = {
                        val parts = key.split(" ")
                        val root = parts.firstOrNull() ?: "C"
                        val scale = if (parts.getOrNull(1)?.contains("minor", true) == true) "natural_minor" else "major"
                        melody = MusicCreationAlgorithms.melodyFromDegrees(root, scale, listOf(1, 3, 5, 6, 5, 3, 2, 1)).joinToString(" ")
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Make melody") }
                OutlinedButton(onClick = { title = "Untitled sketch"; progression = ""; beat = ""; bass = ""; melody = ""; structure = "" }, modifier = Modifier.fillMaxWidth()) { Text("New sketch") }
                Button(
                    enabled = workingValues[As100SongSketchDashboardMethod.fields.status] == "succeeded",
                    onClick = {
                        val execution = creationDashboardExecution(As100SongSketchDashboardMethod, context, workingValues)
                        if (MusicV105.shouldReturnImmediately(context)) onConfirmed(execution)
                        else committedJson = MusicV105.valuesToJson(workingValues)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Commit snapshot") }
            } else {
                MusicCommittedPanel(As100SongSketchDashboardMethod, committedValues, committedExecution, onDone = onConfirmed, onEdit = { committedJson = null })
            }
        }
    }
}

private fun creationDashboardExecution(method: MusicPureMethod, context: CapabilityScreenContext, values: Map<String, String>): ExecutionResult {
    val request = method.request(action = method.id, context = context.request.invocationContext.asMap(method.id) + context.action.settings + values, signals = emptyList(), inputs = emptyList())
    return method.result(request, values, context.request.invocationContext)
}
