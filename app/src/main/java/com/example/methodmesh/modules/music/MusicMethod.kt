package com.example.methodmesh.modules.music

import com.example.methodmesh.core.methodmesh.ArchitectureId
import com.example.methodmesh.core.methodmesh.ArchitectureRef
import com.example.methodmesh.core.methodmesh.Entity
import com.example.methodmesh.core.methodmesh.ExecutionRequest
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.InvocationContext
import com.example.methodmesh.core.methodmesh.KnowledgeObjectType
import com.example.methodmesh.core.methodmesh.MethodContract
import com.example.methodmesh.core.methodmesh.MethodDescriptor
import com.example.methodmesh.core.methodmesh.MethodObjectType
import com.example.methodmesh.core.methodmesh.Observation
import com.example.methodmesh.core.methodmesh.ProvenanceContext
import com.example.methodmesh.core.methodmesh.Signal
import com.example.methodmesh.core.methodmesh.Transformation
import com.example.methodmesh.core.methodmesh.TransformationStatus
import com.example.methodmesh.core.methodmesh.runtime.As100ExecutionEngine
import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.core.methodmesh.withInvocationContext
import com.example.methodmesh.settings.SettingsState
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

data class MusicFieldSet(val prefix: String, val core: List<String> = emptyList()) {
    val result = "${prefix}_result"
    val status = "${prefix}_status"
    val json = "${prefix}_audit_json"
    val error = "${prefix}_error"
    fun field(name: String) = "${prefix}_${name}"
    val outputs = listOf(result) + core.map(::field) + listOf(status, json, error)
}

internal object MusicMethodSupport {
    fun request(action: String, method: ArchitectureRef, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) =
        As100ExecutionEngine.request(action = action, method = method, context = context, signals = signals, inputs = inputs)

    fun setting(settings: Map<String, String>, key: String, default: String = ""): String =
        settings[key] ?: settings["input_$key"] ?: default

    fun ok(fields: MusicFieldSet, core: Map<String, Any?>, result: String): Map<String, String> {
        val values = linkedMapOf<String, String>()
        values[fields.result] = result
        core.forEach { (k, v) -> values[fields.field(k)] = v?.toString().orEmpty() }
        values[fields.status] = "succeeded"
        values[fields.error] = ""
        values[fields.json] = JSONObject().apply {
            put("schema", "methodmesh.music.audit.v1"); put("generated_at_epoch_ms", System.currentTimeMillis()); put("result", result); put("status", "succeeded")
            core.forEach { (k, v) -> put(k, v ?: JSONObject.NULL) }
        }.toString()
        return fields.outputs.associateWith { values[it].orEmpty() }
    }

    fun fail(fields: MusicFieldSet, message: String): Map<String, String> =
        fields.outputs.associateWith { "" }.toMutableMap().apply {
            this[fields.status] = "failed"; this[fields.error] = message
            this[fields.json] = JSONObject(mapOf("schema" to "methodmesh.music.audit.v1", "generated_at_epoch_ms" to System.currentTimeMillis(), "status" to "failed", "error" to message)).toString()
        }

    fun complete(request: ExecutionRequest, invocation: InvocationContext?, methodId: String, version: String, ref: ArchitectureRef, fields: MusicFieldSet, values: Map<String, String>): ExecutionResult {
        val succeeded = values[fields.status] == "succeeded"
        val entity = Entity(
            id = ArchitectureId("music:${methodId.substringAfterLast('.')}:${System.currentTimeMillis()}"),
            entityType = "MusicCalculation",
            attributes = values,
            temporalContext = request.temporalContext
        )
        val provenance = ProvenanceContext("methodmesh.music", methodId, version)
        val observation = Observation(
            phenomenon = methodId,
            subject = ArchitectureRef(entity.id, entity.objectType, "Music result"),
            values = values,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        val transformation = Transformation(
            action = methodId,
            method = ref,
            outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)),
            status = if (succeeded) TransformationStatus.Succeeded else TransformationStatus.Failed,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        return As100ExecutionEngine.complete(
            request = request,
            status = if (succeeded) TransformationStatus.Succeeded else TransformationStatus.Failed,
            entities = listOf(entity), observations = listOf(observation), transformations = listOf(transformation),
            diagnostics = if (succeeded) emptyMap() else mapOf(fields.error to values[fields.error].orEmpty())
        ).withInvocationContext(invocation)
    }
}

abstract class MusicPureMethod(
    override val id: String,
    name: String,
    description: String,
    val fields: MusicFieldSet,
    inputs: List<String>,
    graphOutput: String,
    methodType: MethodObjectType = MethodObjectType.Calculation
) : As100Method {
    val version = "0.3.0"
    override val ref = ArchitectureRef(ArchitectureId(id), "Method", name)
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(id), methodType = methodType, name = name, version = version,
        description = description, inputs = inputs, outputs = fields.outputs, graphOutputs = listOf(graphOutput),
        parameters = mapOf("category" to "Development", "status" to "Development", "offline" to "true", "methodmesh_standard" to "1.05", "commit_lifecycle" to "working-result->commit")
    )
    override val contract = MethodContract(ref, producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation), producedFields = descriptor.outputs, producedGraphOutputs = descriptor.graphOutputs)
    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) = MusicMethodSupport.request(action, ref, context, signals, inputs)
    abstract fun calculate(settings: Map<String, String>): Map<String, String>
    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult =
        result(request, calculate(request.context), InvocationContext.from(request.context))
    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?) =
        MusicMethodSupport.complete(request, invocation, id, version, ref, fields, values)
}

object As100TapTempoMethod : MusicPureMethod(
    "music.tap_tempo", "Tap tempo", "Estimate BPM from a series of tap timestamps or inter-tap intervals.",
    MusicFieldSet("music_tap_tempo", listOf("bpm", "mean_interval_ms", "sd_interval_ms", "cv", "tap_count")),
    listOf("tap_times_ms", "tap_intervals_ms"), "music.tempo.observation"
) {
    const val ID = "music.tap_tempo"
    override fun calculate(settings: Map<String, String>): Map<String, String> = runCatching {
        val intervalsRaw = MusicMethodSupport.setting(settings, "tap_intervals_ms")
        val timesRaw = MusicMethodSupport.setting(settings, "tap_times_ms")
        val intervals = if (intervalsRaw.isNotBlank()) intervalsRaw.split(",", ";", " ").mapNotNull { it.trim().toDoubleOrNull() }
        else timesRaw.split(",", ";", " ").mapNotNull { it.trim().toDoubleOrNull() }.zipWithNext { a, b -> b - a }
        require(intervals.isNotEmpty()) { "Provide at least two taps or one tap interval." }
        val clean = intervals.filter { it > 0.0 }
        require(clean.isNotEmpty()) { "Tap intervals must be positive." }
        val mean = clean.average(); val bpm = 60000.0 / mean
        val sd = sqrt(clean.map { (it - mean) * (it - mean) }.average())
        val cv = if (mean == 0.0) 0.0 else sd / mean
        MusicMethodSupport.ok(fields, mapOf("bpm" to "%.2f".format(Locale.US, bpm), "mean_interval_ms" to "%.2f".format(Locale.US, mean), "sd_interval_ms" to "%.2f".format(Locale.US, sd), "cv" to "%.4f".format(Locale.US, cv), "tap_count" to (clean.size + 1)), "%.1f BPM".format(Locale.US, bpm))
    }.getOrElse { MusicMethodSupport.fail(fields, it.message ?: "Unable to calculate tap tempo.") }
}

object As100MetronomeMethod : MusicPureMethod(
    "music.metronome", "Metronome", "Describe a metronome pulse pattern for native audio, visual and haptic playback.",
    MusicFieldSet("music_metronome", listOf("bpm", "beat_interval_ms", "beats_per_bar", "subdivision", "subdivision_interval_ms", "accent_pattern", "audio_enabled", "visual_enabled", "haptic_enabled")),
    listOf("bpm", "beats_per_bar", "subdivision", "accent_pattern", "audio_enabled", "visual_enabled", "haptic_enabled"), "music.metronome.pattern"
) {
    const val ID = "music.metronome"
    override fun calculate(settings: Map<String, String>) = runCatching {
        val bpm = MusicMethodSupport.setting(settings, "bpm", "120").toDouble(); require(bpm > 0)
        val beats = MusicMethodSupport.setting(settings, "beats_per_bar", "4").toInt(); require(beats > 0)
        val subdivision = MusicMethodSupport.setting(settings, "subdivision", "quarter")
        val accent = MusicMethodSupport.setting(settings, "accent_pattern", "1")
        val audioEnabled = MusicMethodSupport.setting(settings, "audio_enabled", "true").toBoolean()
        val visualEnabled = MusicMethodSupport.setting(settings, "visual_enabled", "true").toBoolean()
        val hapticEnabled = MusicMethodSupport.setting(settings, "haptic_enabled", "false").toBoolean()
        val beatMs = MusicAlgorithms.tempoIntervalMs(bpm)
        val subMs = MusicAlgorithms.delayMs(bpm, subdivision)
        MusicMethodSupport.ok(fields, mapOf("bpm" to bpm, "beat_interval_ms" to "%.3f".format(Locale.US, beatMs), "beats_per_bar" to beats, "subdivision" to subdivision, "subdivision_interval_ms" to "%.3f".format(Locale.US, subMs), "accent_pattern" to accent, "audio_enabled" to audioEnabled, "visual_enabled" to visualEnabled, "haptic_enabled" to hapticEnabled), "${"%.1f".format(Locale.US, bpm)} BPM · $beats/4 · $subdivision")
    }.getOrElse { MusicMethodSupport.fail(fields, "BPM and beats per bar must be positive.") }
}

object As100NoteFrequencyMethod : MusicPureMethod(
    "music.note_frequency", "Note ↔ frequency", "Convert a scientific-pitch note to frequency or frequency to the nearest note and cents offset.",
    MusicFieldSet("music_note_frequency", listOf("note", "midi", "frequency_hz", "cents", "reference_a4_hz")),
    listOf("mode", "note", "frequency_hz", "reference_a4_hz", "prefer_flats"), "music.pitch.conversion"
) {
    const val ID = "music.note_frequency"
    override fun calculate(settings: Map<String, String>) = runCatching {
        val mode = MusicMethodSupport.setting(settings, "mode", "note_to_frequency")
        val a4 = MusicMethodSupport.setting(settings, "reference_a4_hz", "440").toDouble(); val flats = MusicMethodSupport.setting(settings, "prefer_flats", "false").toBoolean()
        if (mode == "frequency_to_note") {
            val hz = MusicMethodSupport.setting(settings, "frequency_hz").toDouble(); val n = MusicAlgorithms.nearestNote(hz, a4, flats)
            MusicMethodSupport.ok(fields, mapOf("note" to "${n.name}${n.octave}", "midi" to n.midi, "frequency_hz" to "%.6f".format(Locale.US, hz), "cents" to "%.3f".format(Locale.US, n.centsFromInput), "reference_a4_hz" to a4), "${n.name}${n.octave} · ${"%.2f".format(Locale.US, n.centsFromInput)} cents")
        } else {
            val text = MusicMethodSupport.setting(settings, "note"); val midi = MusicAlgorithms.parseNote(text) ?: error("Use a note with octave, e.g. A4 or C#3.")
            require(Regex(".*-?\\d+\\s*$").matches(text)) { "Note-to-frequency requires an octave, e.g. A4." }
            val n = MusicAlgorithms.noteFromMidi(midi, a4, flats)
            MusicMethodSupport.ok(fields, mapOf("note" to "${n.name}${n.octave}", "midi" to n.midi, "frequency_hz" to "%.6f".format(Locale.US, n.frequencyHz), "cents" to "0", "reference_a4_hz" to a4), "${n.name}${n.octave} = ${"%.3f".format(Locale.US, n.frequencyHz)} Hz")
        }
    }.getOrElse { MusicMethodSupport.fail(fields, it.message ?: "Invalid pitch input.") }
}

object As100TransposeMethod : MusicPureMethod(
    "music.transpose", "Transpose", "Transpose notes or chord symbols by a fixed semitone interval or between two keys.",
    MusicFieldSet("music_transpose", listOf("input", "output", "semitones", "from_key", "to_key")),
    listOf("input", "semitones", "from_key", "to_key", "input_type", "prefer_flats"), "music.transposition"
) {
    const val ID = "music.transpose"
    override fun calculate(settings: Map<String, String>) = runCatching {
        val input = MusicMethodSupport.setting(settings, "input"); require(input.isNotBlank())
        val from = MusicMethodSupport.setting(settings, "from_key"); val to = MusicMethodSupport.setting(settings, "to_key")
        val semitones = if (from.isNotBlank() && to.isNotBlank()) {
            val a = MusicAlgorithms.parsePitchClass(from) ?: error("Invalid from key"); val b = MusicAlgorithms.parsePitchClass(to) ?: error("Invalid to key"); ((b - a) % 12 + 12) % 12
        } else MusicMethodSupport.setting(settings, "semitones", "0").toInt()
        val flats = MusicMethodSupport.setting(settings, "prefer_flats", "false").toBoolean()
        val type = MusicMethodSupport.setting(settings, "input_type", "chords")
        val tokens = input.split(Regex("(\\s+|,)+")).filter { it.isNotBlank() }
        val out = tokens.joinToString(" ") { token ->
            if (type == "notes") MusicAlgorithms.transposeNote(token, semitones, preferFlats = flats)?.let { if (Regex(".*\\d+$").matches(token)) "${it.name}${it.octave}" else it.name } ?: token
            else MusicAlgorithms.transposeChordSymbol(token, semitones, flats)
        }
        MusicMethodSupport.ok(fields, mapOf("input" to input, "output" to out, "semitones" to semitones, "from_key" to from, "to_key" to to), out)
    }.getOrElse { MusicMethodSupport.fail(fields, it.message ?: "Unable to transpose input.") }
}

object As100IntervalMethod : MusicPureMethod(
    "music.interval", "Interval identifier", "Identify the chromatic interval between two notes.",
    MusicFieldSet("music_interval", listOf("note_a", "note_b", "semitones", "interval_name")),
    listOf("note_a", "note_b"), "music.interval.identification"
) {
    const val ID = "music.interval"
    override fun calculate(settings: Map<String, String>) = runCatching {
        val a = MusicMethodSupport.setting(settings, "note_a"); val b = MusicMethodSupport.setting(settings, "note_b")
        val interval = MusicAlgorithms.intervalBetween(a, b) ?: error("Invalid notes.")
        MusicMethodSupport.ok(fields, mapOf("note_a" to a, "note_b" to b, "semitones" to interval.first, "interval_name" to interval.second), "${interval.second} · ${interval.first} semitone${if (abs(interval.first) == 1) "" else "s"}")
    }.getOrElse { MusicMethodSupport.fail(fields, it.message ?: "Unable to identify interval.") }
}

object As100ChordMethod : MusicPureMethod(
    "music.chord", "Chord builder / identifier", "Build chord tones from root and quality or identify exact chord matches from supplied notes.",
    MusicFieldSet("music_chord", listOf("mode", "symbol", "notes", "matches_json")),
    listOf("mode", "root", "quality", "notes", "prefer_flats"), "music.chord.identification"
) {
    const val ID = "music.chord"
    override fun calculate(settings: Map<String, String>) = runCatching {
        val mode = MusicMethodSupport.setting(settings, "mode", "build"); val flats = MusicMethodSupport.setting(settings, "prefer_flats", "false").toBoolean()
        if (mode == "identify") {
            val input = MusicMethodSupport.setting(settings, "notes"); val notes = input.split(",", " ", ";").filter { it.isNotBlank() }
            val matches = MusicAlgorithms.identifyChord(notes, flats); val arr = JSONArray(matches.map { it.symbol })
            val first = matches.firstOrNull()?.symbol ?: "No exact chord match"
            MusicMethodSupport.ok(fields, mapOf("mode" to mode, "symbol" to first, "notes" to notes.joinToString(" "), "matches_json" to arr.toString()), first)
        } else {
            val root = MusicMethodSupport.setting(settings, "root", "C"); val quality = MusicMethodSupport.setting(settings, "quality", "")
            val notes = MusicAlgorithms.chordNotes(root, quality, flats) ?: error("Unsupported root or chord quality.")
            val symbol = root + quality
            MusicMethodSupport.ok(fields, mapOf("mode" to mode, "symbol" to symbol, "notes" to notes.joinToString(" "), "matches_json" to JSONArray(listOf(symbol)).toString()), "$symbol: ${notes.joinToString(" · ")}")
        }
    }.getOrElse { MusicMethodSupport.fail(fields, it.message ?: "Unable to calculate chord.") }
}

object As100ChordGuideMethod : MusicPureMethod(
    "music.chord_guide", "Chord / tab guide", "Return a compact guitar chord fingering using six-character low-E-to-high-E fret notation.",
    MusicFieldSet("music_chord_guide", listOf("instrument", "chord", "shape", "format")),
    listOf("instrument", "chord"), "music.chord.fingering"
) {
    const val ID = "music.chord_guide"
    override fun calculate(settings: Map<String, String>) = runCatching {
        val instrument = MusicMethodSupport.setting(settings, "instrument", "guitar"); require(instrument == "guitar") { "Built-in chord shapes currently cover guitar only." }
        val chord = MusicMethodSupport.setting(settings, "chord", "C"); val shape = MusicAlgorithms.guitarChordShape(chord) ?: error("No built-in shape for $chord.")
        MusicMethodSupport.ok(fields, mapOf("instrument" to instrument, "chord" to chord, "shape" to shape, "format" to "low-E-to-high-E; x=mute; 0=open"), "$chord · $shape")
    }.getOrElse { MusicMethodSupport.fail(fields, it.message ?: "Chord shape unavailable.") }
}

object As100TempoConvertMethod : MusicPureMethod(
    "music.tempo_convert", "Tempo / subdivision converter", "Convert BPM into beat and note-subdivision durations.",
    MusicFieldSet("music_tempo_convert", listOf("bpm", "subdivision", "milliseconds", "seconds")),
    listOf("bpm", "subdivision"), "music.tempo.conversion"
) {
    const val ID = "music.tempo_convert"
    override fun calculate(settings: Map<String, String>) = runCatching {
        val bpm = MusicMethodSupport.setting(settings, "bpm", "120").toDouble(); require(bpm > 0); val sub = MusicMethodSupport.setting(settings, "subdivision", "quarter")
        val ms = MusicAlgorithms.delayMs(bpm, sub)
        MusicMethodSupport.ok(fields, mapOf("bpm" to bpm, "subdivision" to sub, "milliseconds" to "%.3f".format(Locale.US, ms), "seconds" to "%.6f".format(Locale.US, ms / 1000.0)), "$sub = ${"%.2f".format(Locale.US, ms)} ms at ${"%.1f".format(Locale.US, bpm)} BPM")
    }.getOrElse { MusicMethodSupport.fail(fields, "BPM must be positive.") }
}

object As100DelayTimeMethod : MusicPureMethod(
    "music.delay_time", "Delay-time calculator", "Calculate tempo-synchronised delay time for common straight, dotted and triplet note values.",
    MusicFieldSet("music_delay_time", listOf("bpm", "subdivision", "delay_ms")),
    listOf("bpm", "subdivision"), "music.effect.delay_time"
) {
    const val ID = "music.delay_time"
    override fun calculate(settings: Map<String, String>) = runCatching {
        val bpm = MusicMethodSupport.setting(settings, "bpm", "120").toDouble(); require(bpm > 0); val sub = MusicMethodSupport.setting(settings, "subdivision", "dotted_eighth")
        val ms = MusicAlgorithms.delayMs(bpm, sub)
        MusicMethodSupport.ok(fields, mapOf("bpm" to bpm, "subdivision" to sub, "delay_ms" to "%.3f".format(Locale.US, ms)), "${"%.1f".format(Locale.US, ms)} ms")
    }.getOrElse { MusicMethodSupport.fail(fields, "BPM must be positive.") }
}

object As100TemperamentMethod : MusicPureMethod(
    "music.temperament", "Temperament calculator", "Calculate equal-division-of-the-octave step size and frequency relative to a reference.",
    MusicFieldSet("music_temperament", listOf("divisions", "step", "cents_per_step", "frequency_hz", "reference_hz")),
    listOf("divisions", "step", "reference_hz", "reference_step"), "music.temperament.calculation"
) {
    const val ID = "music.temperament"
    override fun calculate(settings: Map<String, String>) = runCatching {
        val n = MusicMethodSupport.setting(settings, "divisions", "12").toInt(); require(n > 0); val step = MusicMethodSupport.setting(settings, "step", "0").toInt(); val ref = MusicMethodSupport.setting(settings, "reference_hz", "440").toDouble(); val refStep = MusicMethodSupport.setting(settings, "reference_step", "0").toInt()
        val cents = MusicAlgorithms.centsForEdoStep(n); val hz = MusicAlgorithms.edoFrequency(step, n, ref, refStep)
        MusicMethodSupport.ok(fields, mapOf("divisions" to n, "step" to step, "cents_per_step" to "%.6f".format(Locale.US, cents), "frequency_hz" to "%.6f".format(Locale.US, hz), "reference_hz" to ref), "$n-EDO step $step = ${"%.3f".format(Locale.US, hz)} Hz")
    }.getOrElse { MusicMethodSupport.fail(fields, it.message ?: "Invalid temperament inputs.") }
}

object As100PolyrhythmMethod : MusicPureMethod(
    "music.polyrhythm", "Polyrhythm generator", "Generate the pulse positions for an A:B polyrhythm over one common cycle.",
    MusicFieldSet("music_polyrhythm", listOf("ratio_a", "ratio_b", "bpm", "audio_enabled", "haptic_enabled", "pulse_count", "pulses_json")),
    listOf("ratio_a", "ratio_b", "bpm", "audio_enabled", "haptic_enabled"), "music.rhythm.polyrhythm"
) {
    const val ID = "music.polyrhythm"
    override fun calculate(settings: Map<String, String>) = runCatching {
        val a = MusicMethodSupport.setting(settings, "ratio_a", "3").toInt()
        val b = MusicMethodSupport.setting(settings, "ratio_b", "2").toInt()
        val bpm = MusicMethodSupport.setting(settings, "bpm", "60").toDouble()
        require(bpm > 0)
        val audioEnabled = MusicMethodSupport.setting(settings, "audio_enabled", "true").toBoolean()
        val hapticEnabled = MusicMethodSupport.setting(settings, "haptic_enabled", "false").toBoolean()
        val pulses = MusicAlgorithms.polyrhythm(a, b)
        val arr = JSONArray(); pulses.forEach { arr.put(JSONObject(mapOf("fraction" to it.fraction, "side" to it.side, "index" to it.index))) }
        MusicMethodSupport.ok(fields, mapOf("ratio_a" to a, "ratio_b" to b, "bpm" to bpm, "audio_enabled" to audioEnabled, "haptic_enabled" to hapticEnabled, "pulse_count" to pulses.size, "pulses_json" to arr.toString()), "$a:$b polyrhythm · ${pulses.size} pulse events")
    }.getOrElse { MusicMethodSupport.fail(fields, "Polyrhythm ratios must be positive integers.") }
}

object As100TempoTrainerMethod : MusicPureMethod(
    "music.tempo_trainer", "Tempo trainer", "Generate a stepped practice-tempo progression from a start BPM to a target BPM.",
    MusicFieldSet("music_tempo_trainer", listOf("start_bpm", "target_bpm", "increment_bpm", "bars_per_step", "step_count", "sequence_json")),
    listOf("start_bpm", "target_bpm", "increment_bpm", "bars_per_step"), "music.practice.tempo_progression"
) {
    const val ID = "music.tempo_trainer"
    override fun calculate(settings: Map<String, String>) = runCatching {
        val start = MusicMethodSupport.setting(settings, "start_bpm", "80").toDouble(); val target = MusicMethodSupport.setting(settings, "target_bpm", "120").toDouble(); val inc = MusicMethodSupport.setting(settings, "increment_bpm", "2").toDouble(); require(inc != 0.0); val bars = MusicMethodSupport.setting(settings, "bars_per_step", "4").toInt(); require(bars > 0)
        val sequence = MusicAlgorithms.tempoTrainerSequence(start, target, inc); val json = JSONArray(sequence).toString()
        MusicMethodSupport.ok(fields, mapOf("start_bpm" to start, "target_bpm" to target, "increment_bpm" to inc, "bars_per_step" to bars, "step_count" to sequence.size, "sequence_json" to json), sequence.joinToString(" → ") { "%.0f".format(Locale.US, it) } + " BPM")
    }.getOrElse { MusicMethodSupport.fail(fields, it.message ?: "Invalid tempo progression.") }
}

object As100HarmonicsMethod : MusicPureMethod(
    "music.harmonics", "Harmonic-series explorer", "Calculate harmonics of a fundamental and their nearest 12-TET notes/cents offsets.",
    MusicFieldSet("music_harmonics", listOf("fundamental_hz", "count", "harmonics_json")),
    listOf("fundamental_hz", "count", "reference_a4_hz", "prefer_flats"), "music.harmonic_series"
) {
    const val ID = "music.harmonics"
    override fun calculate(settings: Map<String, String>) = runCatching {
        val fundamental = MusicMethodSupport.setting(settings, "fundamental_hz", "110").toDouble(); require(fundamental > 0); val count = MusicMethodSupport.setting(settings, "count", "8").toInt().coerceIn(1, 64); val a4 = MusicMethodSupport.setting(settings, "reference_a4_hz", "440").toDouble(); val flats = MusicMethodSupport.setting(settings, "prefer_flats", "false").toBoolean()
        val harmonics = MusicAlgorithms.harmonicSeries(fundamental, count, a4, flats); val arr = JSONArray(); harmonics.forEach { (n, hz, note) -> arr.put(JSONObject(mapOf("harmonic" to n, "frequency_hz" to hz, "nearest_note" to "${note.name}${note.octave}", "cents" to note.centsFromInput))) }
        MusicMethodSupport.ok(fields, mapOf("fundamental_hz" to fundamental, "count" to count, "harmonics_json" to arr.toString()), "$count harmonics from ${"%.2f".format(Locale.US, fundamental)} Hz")
    }.getOrElse { MusicMethodSupport.fail(fields, "Fundamental frequency must be positive.") }
}

object As100SetListTimingMethod : MusicPureMethod(
    "music.setlist_timing", "Set-list timing", "Calculate planned set duration from comma-separated song durations and optional gaps.",
    MusicFieldSet("music_setlist_timing", listOf("song_count", "song_seconds", "gap_seconds", "total_seconds", "total_formatted", "end_time_note")),
    listOf("durations", "gaps", "start_time"), "music.performance.setlist_timing"
) {
    const val ID = "music.setlist_timing"
    override fun calculate(settings: Map<String, String>) = runCatching {
        val durations = MusicMethodSupport.setting(settings, "durations").split(",", ";").filter { it.isNotBlank() }.map { MusicAlgorithms.parseDuration(it) ?: error("Invalid duration: $it") }
        require(durations.isNotEmpty()) { "Provide at least one duration." }
        val gaps = MusicMethodSupport.setting(settings, "gaps").split(",", ";").filter { it.isNotBlank() }.map { MusicAlgorithms.parseDuration(it) ?: error("Invalid gap: $it") }
        val total = MusicAlgorithms.setListDurationSeconds(durations, gaps)
        MusicMethodSupport.ok(fields, mapOf("song_count" to durations.size, "song_seconds" to durations.sum(), "gap_seconds" to gaps.sum(), "total_seconds" to total, "total_formatted" to MusicAlgorithms.formatDuration(total), "end_time_note" to MusicMethodSupport.setting(settings, "start_time")), "${durations.size} songs · ${MusicAlgorithms.formatDuration(total)}")
    }.getOrElse { MusicMethodSupport.fail(fields, it.message ?: "Invalid set-list timings.") }
}

object As100MusicReferenceMethod : MusicPureMethod(
    "music.reference", "Music reference", "Return scale notes and diatonic triads for a selected key and scale.",
    MusicFieldSet("music_reference", listOf("root", "scale", "notes", "triads", "relative_note")),
    listOf("root", "scale", "prefer_flats"), "music.reference.key"
) {
    const val ID = "music.reference"
    override fun calculate(settings: Map<String, String>) = runCatching {
        val root = MusicMethodSupport.setting(settings, "root", "C"); val scale = MusicMethodSupport.setting(settings, "scale", "major"); val flats = MusicMethodSupport.setting(settings, "prefer_flats", "false").toBoolean()
        val notes = MusicAlgorithms.scaleNotes(root, scale, flats) ?: error("Unsupported key or scale.")
        val triads = if (scale == "major" || scale == "natural_minor") MusicAlgorithms.diatonicTriads(root, scale == "natural_minor", flats).orEmpty() else emptyList()
        val relative = when (scale) { "major" -> notes.getOrNull(5).orEmpty(); "natural_minor" -> notes.getOrNull(2).orEmpty(); else -> "" }
        MusicMethodSupport.ok(fields, mapOf("root" to root, "scale" to scale, "notes" to notes.joinToString(" "), "triads" to triads.joinToString(" "), "relative_note" to relative), "$root ${scale.replace('_', ' ')} · ${notes.joinToString(" ")}")
    }.getOrElse { MusicMethodSupport.fail(fields, it.message ?: "Unable to build music reference.") }
}
