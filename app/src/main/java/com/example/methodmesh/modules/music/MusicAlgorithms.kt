package com.example.methodmesh.modules.music

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

object MusicAlgorithms {
    private val sharpNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val flatNames = arrayOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")
    private val pitchClass = mapOf(
        "C" to 0, "B#" to 0, "C#" to 1, "DB" to 1, "D" to 2, "D#" to 3, "EB" to 3,
        "E" to 4, "FB" to 4, "E#" to 5, "F" to 5, "F#" to 6, "GB" to 6, "G" to 7,
        "G#" to 8, "AB" to 8, "A" to 9, "A#" to 10, "BB" to 10, "B" to 11, "CB" to 11
    )

    data class Note(val midi: Int, val name: String, val octave: Int, val frequencyHz: Double, val centsFromInput: Double = 0.0)
    data class ChordMatch(val symbol: String, val root: Int, val intervals: Set<Int>, val inversionBass: Int?)
    data class PolyPulse(val fraction: Double, val side: String, val index: Int)

    fun midiToFrequency(midi: Int, a4Hz: Double = 440.0): Double = a4Hz * 2.0.pow((midi - 69) / 12.0)

    fun frequencyToMidi(frequencyHz: Double, a4Hz: Double = 440.0): Double =
        69.0 + 12.0 * log2(frequencyHz / a4Hz)

    fun nearestNote(frequencyHz: Double, a4Hz: Double = 440.0, preferFlats: Boolean = false): Note {
        require(frequencyHz > 0.0)
        val midiExact = frequencyToMidi(frequencyHz, a4Hz)
        val midi = midiExact.roundToInt()
        val cents = (midiExact - midi) * 100.0
        return noteFromMidi(midi, a4Hz, preferFlats).copy(centsFromInput = cents)
    }

    fun noteFromMidi(midi: Int, a4Hz: Double = 440.0, preferFlats: Boolean = false): Note {
        val names = if (preferFlats) flatNames else sharpNames
        val pc = ((midi % 12) + 12) % 12
        val octave = floor(midi / 12.0).toInt() - 1
        return Note(midi, names[pc], octave, midiToFrequency(midi, a4Hz))
    }

    fun parseNote(note: String): Int? {
        val m = Regex("^\\s*([A-Ga-g])([#bB]?)(-?\\d+)?\\s*$").matchEntire(note) ?: return null
        val key = (m.groupValues[1].uppercase() + m.groupValues[2].uppercase())
        val pc = pitchClass[key] ?: return null
        val octave = m.groupValues[3].takeIf { it.isNotBlank() }?.toIntOrNull() ?: return pc
        return (octave + 1) * 12 + pc
    }

    fun parsePitchClass(note: String): Int? {
        val parsed = parseNote(note) ?: return null
        return ((parsed % 12) + 12) % 12
    }

    fun pitchClassName(pc: Int, preferFlats: Boolean = false): String =
        (if (preferFlats) flatNames else sharpNames)[((pc % 12) + 12) % 12]

    fun transposeNote(note: String, semitones: Int, a4Hz: Double = 440.0, preferFlats: Boolean = false): Note? {
        val midi = parseNote(note) ?: return null
        if (!Regex(".*-?\\d+\\s*$").matches(note)) {
            val pc = ((midi + semitones) % 12 + 12) % 12
            return Note(pc, pitchClassName(pc, preferFlats), 0, 0.0)
        }
        return noteFromMidi(midi + semitones, a4Hz, preferFlats)
    }

    fun transposeChordSymbol(symbol: String, semitones: Int, preferFlats: Boolean = false): String {
        val m = Regex("^\\s*([A-Ga-g])([#b]?)([^/]*)?(?:/([A-Ga-g])([#b]?))?\\s*$").matchEntire(symbol) ?: return symbol
        val root = parsePitchClass(m.groupValues[1] + m.groupValues[2]) ?: return symbol
        val suffix = m.groupValues[3]
        val bass = if (m.groupValues[4].isNotBlank()) parsePitchClass(m.groupValues[4] + m.groupValues[5]) else null
        val newRoot = pitchClassName(root + semitones, preferFlats)
        val newBass = bass?.let { "/${pitchClassName(it + semitones, preferFlats)}" }.orEmpty()
        return newRoot + suffix + newBass
    }

    fun intervalName(semitones: Int): String = when (((semitones % 12) + 12) % 12) {
        0 -> "Perfect unison / octave"
        1 -> "Minor second"
        2 -> "Major second"
        3 -> "Minor third"
        4 -> "Major third"
        5 -> "Perfect fourth"
        6 -> "Tritone"
        7 -> "Perfect fifth"
        8 -> "Minor sixth"
        9 -> "Major sixth"
        10 -> "Minor seventh"
        else -> "Major seventh"
    }

    fun intervalBetween(noteA: String, noteB: String): Pair<Int, String>? {
        val a = parseNote(noteA) ?: return null
        val b = parseNote(noteB) ?: return null
        val semitones = if (Regex(".*\\d+\\s*$").matches(noteA) && Regex(".*\\d+\\s*$").matches(noteB)) b - a else ((b - a) % 12 + 12) % 12
        val simple = ((semitones % 12) + 12) % 12
        return semitones to intervalName(simple)
    }

    private val chordPatterns = linkedMapOf(
        "" to setOf(0, 4, 7), "m" to setOf(0, 3, 7), "dim" to setOf(0, 3, 6), "aug" to setOf(0, 4, 8),
        "sus2" to setOf(0, 2, 7), "sus4" to setOf(0, 5, 7), "6" to setOf(0, 4, 7, 9), "m6" to setOf(0, 3, 7, 9),
        "7" to setOf(0, 4, 7, 10), "maj7" to setOf(0, 4, 7, 11), "m7" to setOf(0, 3, 7, 10),
        "mMaj7" to setOf(0, 3, 7, 11), "dim7" to setOf(0, 3, 6, 9), "m7b5" to setOf(0, 3, 6, 10),
        "add9" to setOf(0, 2, 4, 7), "9" to setOf(0, 2, 4, 7, 10), "maj9" to setOf(0, 2, 4, 7, 11), "m9" to setOf(0, 2, 3, 7, 10)
    )

    fun chordNotes(root: String, quality: String, preferFlats: Boolean = false): List<String>? {
        val pc = parsePitchClass(root) ?: return null
        val pattern = chordPatterns[quality] ?: return null
        return pattern.sorted().map { pitchClassName(pc + it, preferFlats) }
    }

    fun identifyChord(notes: List<String>, preferFlats: Boolean = false): List<ChordMatch> {
        val pcsOrdered = notes.mapNotNull(::parsePitchClass)
        if (pcsOrdered.size < 2) return emptyList()
        val pcs = pcsOrdered.toSet()
        val bass = pcsOrdered.firstOrNull()
        val matches = mutableListOf<ChordMatch>()
        for (root in 0..11) {
            val rel = pcs.map { ((it - root) % 12 + 12) % 12 }.toSet()
            chordPatterns.forEach { (quality, pattern) ->
                if (rel == pattern) {
                    val symbol = pitchClassName(root, preferFlats) + quality + if (bass != null && bass != root) "/${pitchClassName(bass, preferFlats)}" else ""
                    matches += ChordMatch(symbol, root, pattern, bass)
                }
            }
        }
        return matches
    }


    private val scalePatterns = mapOf(
        "major" to listOf(0, 2, 4, 5, 7, 9, 11),
        "natural_minor" to listOf(0, 2, 3, 5, 7, 8, 10),
        "harmonic_minor" to listOf(0, 2, 3, 5, 7, 8, 11),
        "melodic_minor" to listOf(0, 2, 3, 5, 7, 9, 11),
        "major_pentatonic" to listOf(0, 2, 4, 7, 9),
        "minor_pentatonic" to listOf(0, 3, 5, 7, 10),
        "blues" to listOf(0, 3, 5, 6, 7, 10),
        "dorian" to listOf(0, 2, 3, 5, 7, 9, 10),
        "mixolydian" to listOf(0, 2, 4, 5, 7, 9, 10)
    )

    fun scaleNotes(root: String, scale: String, preferFlats: Boolean = false): List<String>? {
        val pc = parsePitchClass(root) ?: return null
        val pattern = scalePatterns[scale] ?: return null
        return pattern.map { pitchClassName(pc + it, preferFlats) }
    }

    fun diatonicTriads(root: String, minor: Boolean = false, preferFlats: Boolean = false): List<String>? {
        val pcs = scaleNotes(root, if (minor) "natural_minor" else "major", preferFlats)?.mapNotNull(::parsePitchClass) ?: return null
        val qualities = if (minor) listOf("m", "dim", "", "m", "m", "", "") else listOf("", "m", "m", "", "", "m", "dim")
        return pcs.mapIndexed { i, pc -> pitchClassName(pc, preferFlats) + qualities[i] }
    }

    fun guitarChordShape(symbol: String): String? = mapOf(
        "C" to "x32010", "Cm" to "x35543", "D" to "xx0232", "Dm" to "xx0231",
        "E" to "022100", "Em" to "022000", "F" to "133211", "Fm" to "133111",
        "G" to "320003", "Gm" to "355333", "A" to "x02220", "Am" to "x02210",
        "B" to "x24442", "Bm" to "x24432", "C7" to "x32310", "D7" to "xx0212",
        "E7" to "020100", "G7" to "320001", "A7" to "x02020", "B7" to "x21202"
    )[symbol.trim()]

    fun tempoIntervalMs(bpm: Double, beats: Double = 1.0): Double = 60000.0 * beats / bpm

    fun subdivisionBeats(name: String): Double = when (name.lowercase()) {
        "whole" -> 4.0; "half" -> 2.0; "quarter" -> 1.0; "eighth" -> 0.5; "sixteenth" -> 0.25; "thirty_second" -> 0.125
        "dotted_half" -> 3.0; "dotted_quarter" -> 1.5; "dotted_eighth" -> 0.75; "quarter_triplet" -> 2.0 / 3.0; "eighth_triplet" -> 1.0 / 3.0; "sixteenth_triplet" -> 1.0 / 6.0
        else -> 1.0
    }

    fun delayMs(bpm: Double, subdivision: String): Double = tempoIntervalMs(bpm, subdivisionBeats(subdivision))

    fun edoFrequency(step: Int, divisions: Int, referenceHz: Double = 440.0, referenceStep: Int = 0): Double =
        referenceHz * 2.0.pow((step - referenceStep).toDouble() / divisions.toDouble())

    fun centsForEdoStep(divisions: Int): Double = 1200.0 / divisions

    fun polyrhythm(a: Int, b: Int): List<PolyPulse> {
        require(a > 0 && b > 0)
        val pulses = mutableListOf<PolyPulse>()
        for (i in 0 until a) pulses += PolyPulse(i.toDouble() / a, "A", i + 1)
        for (i in 0 until b) pulses += PolyPulse(i.toDouble() / b, "B", i + 1)
        return pulses.sortedWith(compareBy<PolyPulse> { it.fraction }.thenBy { it.side })
    }

    fun harmonicSeries(fundamentalHz: Double, count: Int, a4Hz: Double = 440.0, preferFlats: Boolean = false): List<Triple<Int, Double, Note>> =
        (1..count).map { n -> Triple(n, fundamentalHz * n, nearestNote(fundamentalHz * n, a4Hz, preferFlats)) }

    fun tempoTrainerSequence(startBpm: Double, targetBpm: Double, increment: Double): List<Double> {
        if (increment == 0.0) return listOf(startBpm)
        val direction = if (targetBpm >= startBpm) 1 else -1
        val step = abs(increment) * direction
        val out = mutableListOf<Double>()
        var x = startBpm
        var guard = 0
        while ((direction > 0 && x <= targetBpm + 1e-9) || (direction < 0 && x >= targetBpm - 1e-9)) {
            out += x
            x += step
            guard++
            if (guard > 1000) break
        }
        if (out.isEmpty() || abs(out.last() - targetBpm) > 1e-9) out += targetBpm
        return out.distinct()
    }

    fun setListDurationSeconds(durations: List<Int>, gaps: List<Int>): Int = durations.sum() + gaps.sum()

    fun formatDuration(seconds: Int): String {
        val s = seconds.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }

    fun parseDuration(text: String): Int? {
        val parts = text.trim().split(":").mapNotNull { it.toIntOrNull() }
        if (parts.isEmpty()) return text.trim().toIntOrNull()
        return when (parts.size) {
            1 -> parts[0]
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> null
        }
    }
}
