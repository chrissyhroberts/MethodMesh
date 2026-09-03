package com.example.methodmesh.modules.acoustics

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Pure, Android-independent DSP and acoustic maths used by the Acoustics module. */
object AcousticsAlgorithms {
    const val DSP_VERSION = "1.0.0"
    const val PITCH_ALGORITHM = "yin_cmnd"
    const val SPECTRUM_ALGORITHM = "radix2_fft_hann"

    private const val EPS = 1e-12
    private val noteNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    data class PitchEstimate(
        val frequencyHz: Double?,
        val confidence: Double,
        val periodSamples: Double? = null,
        val cmndMinimum: Double? = null
    )

    data class NoteEstimate(
        val note: String,
        val midi: Int,
        val targetHz: Double,
        val cents: Double
    )

    data class TuningTarget(
        val label: String,
        val note: String,
        val midi: Int,
        val targetHz: Double,
        val cents: Double
    )

    data class ComparisonResult(
        val targetHz: Double,
        val measuredHz: Double,
        val differenceHz: Double,
        val differencePercent: Double,
        val differenceCents: Double,
        val toleranceMode: String,
        val toleranceValue: Double,
        val withinTolerance: Boolean
    )

    data class FrequencyObservation(
        val timestampMs: Long,
        val frequencyHz: Double,
        val confidence: Double
    )

    data class StableWindow(
        val observations: List<FrequencyObservation>,
        val durationMs: Long,
        val meanHz: Double,
        val medianHz: Double,
        val sdHz: Double,
        val sdCents: Double,
        val meanConfidence: Double
    )

    data class InstrumentString(val label: String, val midi: Int)

    val instrumentPresets: Map<String, List<InstrumentString>> = linkedMapOf(
        "guitar" to listOf(
            InstrumentString("6 · E2", 40),
            InstrumentString("5 · A2", 45),
            InstrumentString("4 · D3", 50),
            InstrumentString("3 · G3", 55),
            InstrumentString("2 · B3", 59),
            InstrumentString("1 · E4", 64)
        ),
        "guitar_drop_d" to listOf(
            InstrumentString("6 · D2", 38),
            InstrumentString("5 · A2", 45),
            InstrumentString("4 · D3", 50),
            InstrumentString("3 · G3", 55),
            InstrumentString("2 · B3", 59),
            InstrumentString("1 · E4", 64)
        ),
        "ukulele_high_g" to listOf(
            InstrumentString("4 · G4", 67),
            InstrumentString("3 · C4", 60),
            InstrumentString("2 · E4", 64),
            InstrumentString("1 · A4", 69)
        ),
        "ukulele_low_g" to listOf(
            InstrumentString("4 · G3", 55),
            InstrumentString("3 · C4", 60),
            InstrumentString("2 · E4", 64),
            InstrumentString("1 · A4", 69)
        ),
        "violin" to listOf(
            InstrumentString("G3", 55),
            InstrumentString("D4", 62),
            InstrumentString("A4", 69),
            InstrumentString("E5", 76)
        ),
        "viola" to listOf(
            InstrumentString("C3", 48),
            InstrumentString("G3", 55),
            InstrumentString("D4", 62),
            InstrumentString("A4", 69)
        ),
        "cello" to listOf(
            InstrumentString("C2", 36),
            InstrumentString("G2", 43),
            InstrumentString("D3", 50),
            InstrumentString("A3", 57)
        ),
        "bass" to listOf(
            InstrumentString("E1", 28),
            InstrumentString("A1", 33),
            InstrumentString("D2", 38),
            InstrumentString("G2", 43)
        ),
        "mandolin" to listOf(
            InstrumentString("G3", 55),
            InstrumentString("D4", 62),
            InstrumentString("A4", 69),
            InstrumentString("E5", 76)
        )
    )

    fun rms(samples: FloatArray): Double {
        if (samples.isEmpty()) return 0.0
        var sum = 0.0
        for (sample in samples) {
            val v = sample.toDouble()
            sum += v * v
        }
        return sqrt(sum / samples.size)
    }

    fun peak(samples: FloatArray): Double {
        var peak = 0.0
        for (sample in samples) peak = max(peak, abs(sample.toDouble()))
        return peak
    }

    fun dbfsFromAmplitude(amplitude: Double): Double =
        if (amplitude <= EPS) -120.0 else (20.0 * log10(amplitude)).coerceAtLeast(-120.0)

    fun leqDbfs(frameRmsValues: List<Double>): Double {
        if (frameRmsValues.isEmpty()) return -120.0
        val meanSquare = frameRmsValues.sumOf { it * it } / frameRmsValues.size
        return if (meanSquare <= EPS) -120.0 else (10.0 * log10(meanSquare)).coerceAtLeast(-120.0)
    }

    /**
     * YIN pitch detector using the cumulative mean normalized difference function.
     * A fixed half-window is used so each candidate lag is evaluated over the same number of samples.
     */
    fun estimatePitchYin(
        samples: FloatArray,
        sampleRateHz: Int,
        minFrequencyHz: Double = 40.0,
        maxFrequencyHz: Double = 5000.0,
        threshold: Double = 0.15
    ): PitchEstimate {
        if (samples.size < 64 || sampleRateHz <= 0 || minFrequencyHz <= 0.0 || maxFrequencyHz <= minFrequencyHz) {
            return PitchEstimate(null, 0.0)
        }

        val maxTauByFrequency = floor(sampleRateHz / minFrequencyHz).toInt()
        val maxTau = min(samples.size / 2 - 1, maxTauByFrequency)
        val minTau = max(2, floor(sampleRateHz / maxFrequencyHz).toInt())
        if (maxTau <= minTau + 1) return PitchEstimate(null, 0.0)

        val window = samples.size - maxTau - 1
        if (window <= 8) return PitchEstimate(null, 0.0)

        val difference = DoubleArray(maxTau + 1)
        for (tau in 1..maxTau) {
            var sum = 0.0
            var i = 0
            while (i < window) {
                val delta = samples[i].toDouble() - samples[i + tau].toDouble()
                sum += delta * delta
                i++
            }
            difference[tau] = sum
        }

        val cmnd = DoubleArray(maxTau + 1) { 1.0 }
        var running = 0.0
        for (tau in 1..maxTau) {
            running += difference[tau]
            cmnd[tau] = if (running <= EPS) 1.0 else difference[tau] * tau / running
        }

        var candidate = -1
        var tau = minTau
        while (tau <= maxTau) {
            if (cmnd[tau] < threshold) {
                while (tau + 1 <= maxTau && cmnd[tau + 1] < cmnd[tau]) tau++
                candidate = tau
                break
            }
            tau++
        }

        if (candidate < 0) {
            var best = minTau
            for (i in minTau + 1..maxTau) if (cmnd[i] < cmnd[best]) best = i
            if (cmnd[best] > 0.45) return PitchEstimate(null, (1.0 - cmnd[best]).coerceIn(0.0, 1.0), cmndMinimum = cmnd[best])
            candidate = best
        }

        val refinedTau = parabolicMinimum(cmnd, candidate)
        if (!refinedTau.isFinite() || refinedTau <= 0.0) return PitchEstimate(null, 0.0)
        val frequency = sampleRateHz / refinedTau
        if (frequency !in minFrequencyHz..maxFrequencyHz) return PitchEstimate(null, 0.0)

        val confidence = (1.0 - cmnd[candidate]).coerceIn(0.0, 1.0)
        return PitchEstimate(frequency, confidence, refinedTau, cmnd[candidate])
    }

    private fun parabolicMinimum(values: DoubleArray, index: Int): Double {
        if (index <= 0 || index >= values.lastIndex) return index.toDouble()
        val left = values[index - 1]
        val center = values[index]
        val right = values[index + 1]
        val denominator = left - 2.0 * center + right
        if (abs(denominator) < EPS) return index.toDouble()
        val delta = 0.5 * (left - right) / denominator
        return index + delta.coerceIn(-1.0, 1.0)
    }

    fun noteFromFrequency(frequencyHz: Double, referenceA4Hz: Double = 440.0): NoteEstimate? {
        if (frequencyHz <= 0.0 || referenceA4Hz <= 0.0) return null
        val midiFloat = 69.0 + 12.0 * log2(frequencyHz / referenceA4Hz)
        val midi = midiFloat.roundToInt()
        val targetHz = midiToFrequency(midi, referenceA4Hz)
        val cents = centsDifference(frequencyHz, targetHz)
        val noteIndex = ((midi % 12) + 12) % 12
        val octave = floor(midi / 12.0).toInt() - 1
        return NoteEstimate("${noteNames[noteIndex]}$octave", midi, targetHz, cents)
    }

    fun tuningTarget(
        frequencyHz: Double,
        referenceA4Hz: Double,
        instrument: String,
        stringIndex: Int = 0
    ): TuningTarget? {
        if (frequencyHz <= 0.0) return null
        if (instrument == "chromatic") {
            val note = noteFromFrequency(frequencyHz, referenceA4Hz) ?: return null
            return TuningTarget(note.note, note.note, note.midi, note.targetHz, note.cents)
        }

        val strings = instrumentPresets[instrument] ?: return null
        val target = if (stringIndex in 1..strings.size) {
            strings[stringIndex - 1]
        } else {
            strings.minByOrNull { abs(centsDifference(frequencyHz, midiToFrequency(it.midi, referenceA4Hz))) }
        } ?: return null

        val targetHz = midiToFrequency(target.midi, referenceA4Hz)
        val note = midiToNoteName(target.midi)
        return TuningTarget(target.label, note, target.midi, targetHz, centsDifference(frequencyHz, targetHz))
    }

    fun midiToFrequency(midi: Int, referenceA4Hz: Double = 440.0): Double =
        referenceA4Hz * 2.0.pow((midi - 69) / 12.0)

    fun midiToNoteName(midi: Int): String {
        val noteIndex = ((midi % 12) + 12) % 12
        val octave = floor(midi / 12.0).toInt() - 1
        return "${noteNames[noteIndex]}$octave"
    }

    fun centsDifference(measuredHz: Double, targetHz: Double): Double =
        if (measuredHz <= 0.0 || targetHz <= 0.0) Double.NaN else 1200.0 * log2(measuredHz / targetHz)

    fun compareTone(measuredHz: Double, targetHz: Double, toleranceMode: String, toleranceValue: Double): ComparisonResult {
        val differenceHz = measuredHz - targetHz
        val differencePercent = if (targetHz == 0.0) Double.NaN else differenceHz / targetHz * 100.0
        val differenceCents = centsDifference(measuredHz, targetHz)
        val metric = when (toleranceMode) {
            "percent" -> abs(differencePercent)
            "cents" -> abs(differenceCents)
            else -> abs(differenceHz)
        }
        return ComparisonResult(
            targetHz = targetHz,
            measuredHz = measuredHz,
            differenceHz = differenceHz,
            differencePercent = differencePercent,
            differenceCents = differenceCents,
            toleranceMode = toleranceMode,
            toleranceValue = toleranceValue,
            withinTolerance = metric.isFinite() && metric <= toleranceValue
        )
    }

    /** Approximation for dry air near normal atmospheric pressure; humidity is not modelled. */
    fun soundSpeedMps(temperatureC: Double): Double = 331.3 + 0.606 * temperatureC

    fun derivedWavelengthM(frequencyHz: Double, speedOfSoundMps: Double): Double? =
        if (frequencyHz <= 0.0 || speedOfSoundMps <= 0.0) null else speedOfSoundMps / frequencyHz

    /** Return a display spectrum in dB relative to the strongest FFT bin, from 0 Hz to Nyquist. */
    fun spectrumDb(samples: FloatArray, outputBins: Int = 256): FloatArray {
        if (samples.isEmpty() || outputBins <= 0) return FloatArray(0)
        val n = highestPowerOfTwoAtMost(samples.size)
        if (n < 32) return FloatArray(0)

        val real = DoubleArray(n)
        val imag = DoubleArray(n)
        for (i in 0 until n) {
            val hann = 0.5 - 0.5 * cos(2.0 * PI * i / (n - 1))
            real[i] = samples[i] * hann
        }
        fftInPlace(real, imag)

        val half = n / 2
        val magnitudes = DoubleArray(half)
        var maxMagnitude = EPS
        for (i in 0 until half) {
            val mag = sqrt(real[i] * real[i] + imag[i] * imag[i])
            magnitudes[i] = mag
            if (mag > maxMagnitude) maxMagnitude = mag
        }

        val bins = min(outputBins, half)
        val result = FloatArray(bins)
        for (out in 0 until bins) {
            val start = out * half / bins
            val end = max(start + 1, (out + 1) * half / bins)
            var localMax = EPS
            for (i in start until min(end, half)) localMax = max(localMax, magnitudes[i])
            result[out] = (20.0 * log10(localMax / maxMagnitude)).coerceIn(-90.0, 0.0).toFloat()
        }
        return result
    }

    fun waveformForDisplay(samples: FloatArray, outputPoints: Int = 256): FloatArray {
        if (samples.isEmpty() || outputPoints <= 0) return FloatArray(0)
        val points = min(outputPoints, samples.size)
        val result = FloatArray(points)
        for (out in 0 until points) {
            val start = out * samples.size / points
            val end = max(start + 1, (out + 1) * samples.size / points)
            var selected = samples[start]
            for (i in start until min(end, samples.size)) if (abs(samples[i]) > abs(selected)) selected = samples[i]
            result[out] = selected
        }
        return result
    }

    fun strongestHarmonics(
        spectrumDb: FloatArray,
        sampleRateHz: Int,
        fundamentalHz: Double,
        harmonicCount: Int = 5
    ): List<Pair<Double, Double>> {
        if (spectrumDb.isEmpty() || fundamentalHz <= 0.0 || sampleRateHz <= 0) return emptyList()
        val nyquist = sampleRateHz / 2.0
        return (1..harmonicCount).mapNotNull { harmonic ->
            val frequency = fundamentalHz * harmonic
            if (frequency >= nyquist) return@mapNotNull null
            val index = ((frequency / nyquist) * spectrumDb.size).roundToInt().coerceIn(0, spectrumDb.lastIndex)
            val start = max(0, index - 1)
            val end = min(spectrumDb.lastIndex, index + 1)
            val level = (start..end).maxOf { spectrumDb[it].toDouble() }
            frequency to level
        }
    }

    /**
     * Finds the longest contiguous pitch window meeting duration and cents-SD criteria.
     * This lets comparison/tuning reject a tone that only happened to cross the target instantaneously.
     */
    fun findStableWindow(
        observations: List<FrequencyObservation>,
        minimumDurationMs: Long,
        maximumSdCents: Double,
        minimumConfidence: Double = 0.60,
        maximumGapMs: Long = 300L
    ): StableWindow? {
        val valid = observations
            .filter { it.frequencyHz > 0.0 && it.confidence >= minimumConfidence }
            .sortedBy { it.timestampMs }
        if (valid.isEmpty()) return null

        var best: StableWindow? = null
        var start = 0
        while (start < valid.size) {
            var end = start
            while (end < valid.size) {
                if (end > start && valid[end].timestampMs - valid[end - 1].timestampMs > maximumGapMs) break
                val duration = valid[end].timestampMs - valid[start].timestampMs
                if (duration >= minimumDurationMs) {
                    val segment = valid.subList(start, end + 1)
                    val stats = stableStats(segment)
                    if (stats.sdCents <= maximumSdCents) {
                        if (best == null || stats.durationMs > best.durationMs ||
                            (stats.durationMs == best.durationMs && segment.last().timestampMs > best.observations.last().timestampMs)
                        ) {
                            best = stats
                        }
                    }
                }
                end++
            }
            start++
        }
        return best
    }

    fun summaryWindow(observations: List<FrequencyObservation>, minimumConfidence: Double = 0.60): StableWindow? {
        val valid = observations.filter { it.frequencyHz > 0.0 && it.confidence >= minimumConfidence }.sortedBy { it.timestampMs }
        if (valid.isEmpty()) return null
        return stableStats(valid)
    }

    private fun stableStats(segment: List<FrequencyObservation>): StableWindow {
        val frequencies = segment.map { it.frequencyHz }
        val mean = frequencies.average()
        val median = median(frequencies)
        val varianceHz = frequencies.sumOf { (it - mean) * (it - mean) } / frequencies.size
        val logValues = frequencies.map { log2(it) }
        val meanLog = logValues.average()
        val varianceLog = logValues.sumOf { (it - meanLog) * (it - meanLog) } / logValues.size
        return StableWindow(
            observations = segment.toList(),
            durationMs = segment.last().timestampMs - segment.first().timestampMs,
            meanHz = mean,
            medianHz = median,
            sdHz = sqrt(max(0.0, varianceHz)),
            sdCents = 1200.0 * sqrt(max(0.0, varianceLog)),
            meanConfidence = segment.map { it.confidence }.average()
        )
    }

    fun median(values: List<Double>): Double {
        if (values.isEmpty()) return Double.NaN
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2.0
    }

    fun format(value: Double?, decimals: Int = 2): String {
        if (value == null || !value.isFinite()) return ""
        return "%.${decimals}f".format(value).trimEnd('0').trimEnd('.')
    }

    private fun log2(value: Double): Double = ln(value) / ln(2.0)

    private fun highestPowerOfTwoAtMost(value: Int): Int {
        var power = 1
        while (power <= value / 2) power *= 2
        return power
    }

    private fun fftInPlace(real: DoubleArray, imag: DoubleArray) {
        val n = real.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tmpR = real[i]
                real[i] = real[j]
                real[j] = tmpR
                val tmpI = imag[i]
                imag[i] = imag[j]
                imag[j] = tmpI
            }
        }

        var len = 2
        while (len <= n) {
            val angle = -2.0 * PI / len
            val wLenR = cos(angle)
            val wLenI = sin(angle)
            var i = 0
            while (i < n) {
                var wR = 1.0
                var wI = 0.0
                for (k in 0 until len / 2) {
                    val uR = real[i + k]
                    val uI = imag[i + k]
                    val vR = real[i + k + len / 2] * wR - imag[i + k + len / 2] * wI
                    val vI = real[i + k + len / 2] * wI + imag[i + k + len / 2] * wR
                    real[i + k] = uR + vR
                    imag[i + k] = uI + vI
                    real[i + k + len / 2] = uR - vR
                    imag[i + k + len / 2] = uI - vI
                    val nextWR = wR * wLenR - wI * wLenI
                    wI = wR * wLenI + wI * wLenR
                    wR = nextWR
                }
                i += len
            }
            len = len shl 1
        }
    }
}
