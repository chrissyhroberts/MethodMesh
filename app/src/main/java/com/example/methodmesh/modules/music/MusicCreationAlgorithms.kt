package com.example.methodmesh.modules.music

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

/** Pure-Kotlin creation algorithms. No Android/audio dependencies. */
object MusicCreationAlgorithms {
    data class RhythmCapture(
        val bpm: Double,
        val pulseMs: Double,
        val stepsPerBeat: Int,
        val pattern: List<Boolean>,
        val rawOffsetsMs: List<Double>,
        val quantizedOffsetsMs: List<Double>,
        val meanQuantizationErrorMs: Double
    )

    data class StepEvent(val step: Int, val value: Int, val velocity: Double = 1.0, val probability: Double = 1.0)

    fun inferPulseMs(intervalsMs: List<Double>, minBpm: Double = 40.0, maxBpm: Double = 240.0): Double {
        val clean = intervalsMs.filter { it > 25.0 && it < 5000.0 }
        require(clean.isNotEmpty()) { "At least one valid inter-tap interval is required." }
        val candidates = clean.flatMap { interval -> (1..4).map { interval / it } }
            .filter { it in (60000.0 / maxBpm)..(60000.0 / minBpm) }
        if (candidates.isEmpty()) return clean.sorted()[clean.size / 2]
        return candidates.minBy { pulse ->
            clean.sumOf { interval ->
                val multiples = (interval / pulse).coerceIn(0.25, 8.0)
                abs(multiples - multiples.roundToInt())
            } / clean.size
        }
    }

    fun captureRhythm(
        tapTimesMs: List<Double>,
        stepsPerBeat: Int = 4,
        bars: Int = 1,
        beatsPerBar: Int = 4,
        forcedBpm: Double? = null
    ): RhythmCapture {
        require(tapTimesMs.size >= 2) { "Tap at least twice." }
        require(stepsPerBeat in 1..16)
        require(bars in 1..16 && beatsPerBar in 1..16)
        val normalized = tapTimesMs.map { it - tapTimesMs.first() }
        val intervals = normalized.zipWithNext { a, b -> b - a }
        val pulse = forcedBpm?.let { 60000.0 / it } ?: inferPulseMs(intervals)
        val bpm = 60000.0 / pulse
        val stepMs = pulse / stepsPerBeat
        val totalSteps = bars * beatsPerBar * stepsPerBeat
        val quantizedIndices = normalized.map { (it / stepMs).roundToInt().coerceIn(0, totalSteps - 1) }
        val pattern = MutableList(totalSteps) { false }
        quantizedIndices.forEach { pattern[it] = true }
        val quantized = quantizedIndices.map { it * stepMs }
        val err = normalized.zip(quantized).map { abs(it.first - it.second) }.average()
        return RhythmCapture(bpm, pulse, stepsPerBeat, pattern, normalized, quantized, err)
    }

    /** Evenly distributes pulses over steps; suitable for Euclidean rhythm generation. */
    fun euclidean(steps: Int, pulses: Int, rotation: Int = 0): List<Boolean> {
        require(steps in 1..128)
        require(pulses in 0..steps)
        if (pulses == 0) return List(steps) { false }
        if (pulses == steps) return List(steps) { true }
        val base = List(steps) { i -> ((i * pulses) % steps) < pulses }
        val shift = ((rotation % steps) + steps) % steps
        return List(steps) { i -> base[((i - shift) % steps + steps) % steps] }
    }

    fun rotate(pattern: List<Boolean>, steps: Int): List<Boolean> {
        if (pattern.isEmpty()) return pattern
        val shift = ((steps % pattern.size) + pattern.size) % pattern.size
        return List(pattern.size) { i -> pattern[((i - shift) % pattern.size + pattern.size) % pattern.size] }
    }

    fun mutate(pattern: List<Boolean>, mode: String, amount: Double = 0.25, seed: Long = 1L): List<Boolean> {
        if (pattern.isEmpty()) return pattern
        val p = amount.coerceIn(0.0, 1.0)
        val random = Random(seed)
        return when (mode) {
            "sparser" -> pattern.map { if (it && random.nextDouble() < p) false else it }
            "denser" -> pattern.map { if (!it && random.nextDouble() < p) true else it }
            "invert" -> pattern.map { !it }
            "rotate_left" -> rotate(pattern, -1)
            "rotate_right" -> rotate(pattern, 1)
            "half_time" -> pattern.mapIndexed { i, hit -> hit && i % 2 == 0 }
            "double_time" -> pattern.toMutableList().also { out ->
                pattern.forEachIndexed { i, hit -> if (hit) out[(i + 1) % out.size] = true }
            }
            else -> pattern.map { if (random.nextDouble() < p) !it else it }
        }
    }

    fun probabilityRealization(probabilities: List<Double>, seed: Long = 1L): List<Boolean> {
        val r = Random(seed)
        return probabilities.map { r.nextDouble() < it.coerceIn(0.0, 1.0) }
    }

    fun patternString(pattern: List<Boolean>, hit: String = "x", rest: String = "."): String =
        pattern.joinToString("") { if (it) hit else rest }

    fun parsePattern(text: String): List<Boolean> = text.trim().filterNot { it.isWhitespace() || it == '|' }.map {
        when (it) { 'x', 'X', '1', '*', '●' -> true else -> false }
    }

    private val scaleIntervals = mapOf(
        "major" to listOf(0,2,4,5,7,9,11),
        "natural_minor" to listOf(0,2,3,5,7,8,10),
        "minor_pentatonic" to listOf(0,3,5,7,10),
        "major_pentatonic" to listOf(0,2,4,7,9),
        "dorian" to listOf(0,2,3,5,7,9,10),
        "mixolydian" to listOf(0,2,4,5,7,9,10),
        "blues" to listOf(0,3,5,6,7,10)
    )

    fun scaleMidi(root: String, scale: String, octave: Int = 4): List<Int> {
        val pc = MusicAlgorithms.parsePitchClass(root) ?: error("Invalid root note.")
        val ints = scaleIntervals[scale] ?: error("Unsupported scale.")
        val base = (octave + 1) * 12 + pc
        return ints.map { base + it }
    }

    fun melodyFromDegrees(root: String, scale: String, degrees: List<Int>, octave: Int = 4, preferFlats: Boolean = false): List<String> {
        val notes = scaleMidi(root, scale, octave)
        return degrees.map { d ->
            val zero = d - 1
            val octaveShift = Math.floorDiv(zero, notes.size)
            val index = Math.floorMod(zero, notes.size)
            val midi = notes[index] + octaveShift * 12
            MusicAlgorithms.noteFromMidi(midi, preferFlats = preferFlats).let { "${it.name}${it.octave}" }
        }
    }

    private val romanMajor = mapOf("I" to 0, "ii" to 1, "iii" to 2, "IV" to 3, "V" to 4, "vi" to 5, "vii°" to 6)
    private val romanMinor = mapOf("i" to 0, "ii°" to 1, "III" to 2, "iv" to 3, "v" to 4, "VI" to 5, "VII" to 6)

    fun progressionFromRoman(root: String, mode: String, romans: List<String>, preferFlats: Boolean = false): List<String> {
        val minor = mode == "minor" || mode == "natural_minor"
        val triads = MusicAlgorithms.diatonicTriads(root, minor, preferFlats) ?: error("Invalid key.")
        val map = if (minor) romanMinor else romanMajor
        return romans.map { roman -> triads[map[roman.trim()] ?: error("Unsupported degree: $roman")] }
    }

    fun generateProgression(root: String, mode: String, bars: Int, seed: Long = 1L, preferFlats: Boolean = false): List<String> {
        require(bars in 1..64)
        val minor = mode == "minor" || mode == "natural_minor"
        val triads = MusicAlgorithms.diatonicTriads(root, minor, preferFlats) ?: error("Invalid key.")
        val r = Random(seed)
        val transitions = if (minor) listOf(
            listOf(3,4,5), listOf(4,6), listOf(5,3), listOf(4,0,6), listOf(0,5), listOf(3,1), listOf(0,4)
        ) else listOf(
            listOf(3,4,5), listOf(4,6), listOf(5,3), listOf(4,0,1), listOf(0,5), listOf(1,3,4), listOf(0,4)
        )
        val out = mutableListOf(0)
        while (out.size < bars) {
            val opts = transitions[out.last()]
            out += opts[r.nextInt(opts.size)]
        }
        return out.map { triads[it] }
    }

    fun chordPitchClasses(symbol: String): List<Int> {
        val rootMatch = Regex("^([A-Ga-g][#b]?)(.*)$").find(symbol.trim()) ?: return emptyList()
        val root = MusicAlgorithms.parsePitchClass(rootMatch.groupValues[1]) ?: return emptyList()
        val suffix = rootMatch.groupValues[2].substringBefore('/')
        val intervals = when {
            suffix.startsWith("m7b5") -> listOf(0,3,6,10)
            suffix.startsWith("maj7") -> listOf(0,4,7,11)
            suffix.startsWith("m7") -> listOf(0,3,7,10)
            suffix.startsWith("7") -> listOf(0,4,7,10)
            suffix.startsWith("dim") -> listOf(0,3,6)
            suffix.startsWith("aug") -> listOf(0,4,8)
            suffix.startsWith("sus2") -> listOf(0,2,7)
            suffix.startsWith("sus4") -> listOf(0,5,7)
            suffix.startsWith("m") -> listOf(0,3,7)
            else -> listOf(0,4,7)
        }
        return intervals.map { (root + it) % 12 }
    }

    fun arpeggiate(chord: String, pattern: String = "up", octaves: Int = 1, baseOctave: Int = 3, preferFlats: Boolean = false): List<String> {
        val pcs = chordPitchClasses(chord)
        require(pcs.isNotEmpty()) { "Invalid chord." }
        val rootPc = MusicAlgorithms.parsePitchClass(Regex("^[A-Ga-g][#b]?").find(chord)?.value ?: "") ?: 0
        val ordered = pcs.sortedBy { ((it - rootPc) % 12 + 12) % 12 }
        val midi = mutableListOf<Int>()
        for (o in 0 until octaves.coerceIn(1,4)) {
            ordered.forEach { pc ->
                var m = (baseOctave + 1 + o) * 12 + pc
                while (midi.isNotEmpty() && m <= midi.last()) m += 12
                midi += m
            }
        }
        val seq = when (pattern) {
            "down" -> midi.reversed()
            "up_down" -> midi + midi.drop(1).dropLast(1).reversed()
            "outside_in" -> buildList { var l=0; var h=midi.lastIndex; while(l<=h){ add(midi[l]); if(h!=l)add(midi[h]); l++; h-- } }
            else -> midi
        }
        return seq.map { MusicAlgorithms.noteFromMidi(it, preferFlats = preferFlats).let { n -> "${n.name}${n.octave}" } }
    }

    fun bassline(progression: List<String>, style: String = "roots", octave: Int = 2, preferFlats: Boolean = false): List<String> {
        return progression.flatMap { chord ->
            val rootText = Regex("^[A-Ga-g][#b]?").find(chord)?.value ?: "C"
            val rootPc = MusicAlgorithms.parsePitchClass(rootText) ?: 0
            val rootMidi = (octave + 1) * 12 + rootPc
            fun note(interval: Int): String = MusicAlgorithms.noteFromMidi(rootMidi + interval, preferFlats = preferFlats).let { "${it.name}${it.octave}" }
            when(style) {
                "root_fifth" -> listOf(note(0), note(7))
                "octaves" -> listOf(note(0), note(12))
                "walking" -> listOf(note(0), note(4), note(7), note(9))
                else -> listOf(note(0))
            }
        }
    }

    fun motifVariations(notes: List<String>, semitones: Int = 2): Map<String, List<String>> {
        val midis = notes.mapNotNull(MusicAlgorithms::parseNote)
        require(midis.size == notes.size && notes.isNotEmpty()) { "Motif notes must include octaves, e.g. C4 E4 G4." }
        val first = midis.first()
        val inversion = midis.map { first - (it - first) }
        fun names(xs: List<Int>) = xs.map { MusicAlgorithms.noteFromMidi(it).let { n -> "${n.name}${n.octave}" } }
        return linkedMapOf(
            "original" to names(midis),
            "retrograde" to names(midis.reversed()),
            "inversion" to names(inversion),
            "retrograde_inversion" to names(inversion.reversed()),
            "transposed" to names(midis.map { it + semitones })
        )
    }
}
