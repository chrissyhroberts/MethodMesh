package com.example.methodmesh.modules.music

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
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
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

internal object SingingRangeCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100SingingRangeMethod.ID
    override val title = "Singing range"
    override val description = "Find the lowest and highest stable notes you can sing."

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val android = LocalContext.current
        val engine = remember { MusicPitchCaptureEngine(android.applicationContext) }
        val referenceA4 = context.action.settings["reference_a4_hz"] ?: context.action.settings["input_reference_a4_hz"] ?: "440"
        val minimumConfidence = (context.action.settings["minimum_confidence"] ?: context.action.settings["input_minimum_confidence"] ?: "0.70").toDoubleOrNull()?.coerceIn(0.1, 1.0) ?: 0.70
        var permission by remember { mutableStateOf(ContextCompat.checkSelfPermission(android, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
        var running by rememberSaveable { mutableStateOf(false) }
        var status by rememberSaveable { mutableStateOf("Ready to listen.") }
        var currentNote by rememberSaveable { mutableStateOf("—") }
        var currentFrequency by rememberSaveable { mutableStateOf("—") }
        var lowestMidi by rememberSaveable { mutableStateOf<Int?>(null) }
        var highestMidi by rememberSaveable { mutableStateOf<Int?>(null) }
        var lowestHz by rememberSaveable { mutableStateOf<Double?>(null) }
        var highestHz by rememberSaveable { mutableStateOf<Double?>(null) }
        var acceptedSamples by rememberSaveable { mutableStateOf(0) }
        var startedAt by rememberSaveable { mutableStateOf(0L) }
        var observed by remember { mutableStateOf(setOf<Int>()) }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var resultJson by rememberSaveable { mutableStateOf<String?>(null) }

        fun start() {
            if (!permission) return
            lowestMidi = null; highestMidi = null; lowestHz = null; highestHz = null; acceptedSamples = 0; observed = emptySet(); result = null; resultJson = null
            startedAt = System.currentTimeMillis(); running = true; status = "Sing a comfortable note, then explore gently from low to high."
            engine.start(
                onFrame = { frame ->
                    val note = MusicAlgorithms.nearestNote(frame.frequencyHz, referenceA4.toDoubleOrNull() ?: 440.0)
                    currentNote = "${note.name}${note.octave}"; currentFrequency = "%.1f Hz · %+.0f cents · %.0f%% confidence".format(Locale.US, frame.frequencyHz, note.centsFromInput, frame.confidence * 100.0)
                    if (frame.confidence >= minimumConfidence && kotlin.math.abs(note.centsFromInput) <= 45.0) {
                        lowestMidi = minOf(lowestMidi ?: note.midi, note.midi); highestMidi = maxOf(highestMidi ?: note.midi, note.midi)
                        lowestHz = minOf(lowestHz ?: frame.frequencyHz, frame.frequencyHz); highestHz = maxOf(highestHz ?: frame.frequencyHz, frame.frequencyHz)
                        acceptedSamples += 1; observed = observed + note.midi
                        status = "Good pitch · keep exploring the range."
                    }
                },
                onError = { running = false; status = it }
            )
        }

        fun finish() {
            engine.stop(); running = false
            val low = lowestMidi; val high = highestMidi
            if (low == null || high == null) { status = "No confidence-qualified singing range was captured."; return }
            val a4 = referenceA4.toDoubleOrNull() ?: 440.0
            val lowNote = MusicAlgorithms.noteFromMidi(low, a4, false); val highNote = MusicAlgorithms.noteFromMidi(high, a4, false)
            val span = high - low
            val observedJson = JSONArray(observed.sorted().map { MusicAlgorithms.noteFromMidi(it, a4, false).let { note -> "${note.name}${note.octave}" } }).toString()
            val values = MusicMethodSupport.ok(
                MusicFieldSet("music_singing_range", listOf("lowest_note", "highest_note", "lowest_midi", "highest_midi", "semitone_span", "octave_span", "lowest_frequency_hz", "highest_frequency_hz", "observed_notes_json", "sample_count", "duration_ms", "reference_a4_hz")),
                mapOf("lowest_note" to "${lowNote.name}${lowNote.octave}", "highest_note" to "${highNote.name}${highNote.octave}", "lowest_midi" to low, "highest_midi" to high, "semitone_span" to span, "octave_span" to "%.2f".format(Locale.US, span / 12.0), "lowest_frequency_hz" to lowestHz, "highest_frequency_hz" to highestHz, "observed_notes_json" to observedJson, "sample_count" to acceptedSamples, "duration_ms" to (System.currentTimeMillis() - startedAt).coerceAtLeast(0L), "reference_a4_hz" to a4),
                "${lowNote.name}${lowNote.octave}–${highNote.name}${highNote.octave} · ${"%.1f".format(Locale.US, span / 12.0)} octaves"
            )
            resultJson = JSONObject(values).toString()
            val request = As100SingingRangeMethod.request(As100SingingRangeMethod.ID, context.request.invocationContext.asMap(As100SingingRangeMethod.ID) + context.action.settings + values, emptyList(), emptyList())
            result = As100SingingRangeMethod.result(request, values, context.request.invocationContext)
            status = "Range captured."
        }

        val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> permission = granted; if (granted) start() else status = "Microphone permission is required." }
        LaunchedEffect(context.submitsImmediately) { if (context.submitsImmediately && permission && !running) start() }
        DisposableEffect(Unit) { onDispose { engine.stop() } }

        CapabilityScreenScaffold(
            title = title, capabilityId = capabilityId, context = context, canGoBack = context.stepNumber > 1,
            capturedResult = result, resultPreview = result?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty(),
            onBack = onBack, onRetry = { start() }, onConfirm = { result?.let(onConfirmed) }, onCancel = { engine.stop(); onCancel() }
        ) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    Text(currentNote, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Text(currentFrequency, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RangeMetric("Lowest", lowestMidi?.let { MusicAlgorithms.noteFromMidi(it, referenceA4.toDoubleOrNull() ?: 440.0, false).let { n -> "${n.name}${n.octave}" } } ?: "—", Modifier.weight(1f))
                RangeMetric("Highest", highestMidi?.let { MusicAlgorithms.noteFromMidi(it, referenceA4.toDoubleOrNull() ?: 440.0, false).let { n -> "${n.name}${n.octave}" } } ?: "—", Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Text("Accepted notes: $acceptedSamples · ${observed.size} distinct", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text("${if (running) "Listening continuously" else "Not listening"} · $status", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            if (!permission) Button(onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }, modifier = Modifier.fillMaxWidth()) { Text("Allow microphone") }
            else if (!running) Button(onClick = { start() }, modifier = Modifier.fillMaxWidth()) { Text("Start range session") }
            else {
                Button(onClick = { finish() }, modifier = Modifier.fillMaxWidth()) { Text("Finish and return range") }
                OutlinedButton(onClick = { engine.stop(); running = false; status = "Session stopped without a result." }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) { Text("Stop without result") }
            }
        }
    }
}

@Composable private fun RangeMetric(label: String, value: String, modifier: Modifier) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceVariant) { Column(Modifier.padding(10.dp)) { Text(label, style = MaterialTheme.typography.labelSmall); Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) } }
}
