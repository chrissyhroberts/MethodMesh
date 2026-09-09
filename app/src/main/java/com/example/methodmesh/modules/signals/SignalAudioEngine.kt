package com.example.methodmesh.modules.signals

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

object SignalAudioOutput {
    const val SAMPLE_RATE_HZ = 48_000


    /** Persistent keyed tone session used by Morse so dots/dashes are sustained beeps rather than AudioTrack start/stop clicks. */
    class ToneSession internal constructor(
        private val frequencyHz: Double,
        private val amplitude: Double
    ) : AutoCloseable {
        private val minBuffer = AudioTrack.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)
        private val track = newTrack(minBuffer)
        private var phase = 0.0
        private var closed = false

        init { track.play() }

        fun mark(durationMs: Long) {
            if (closed || durationMs <= 0) return
            phase = writeToneSamplesRamped(
                track = track,
                frequencyHz = frequencyHz,
                count = (SAMPLE_RATE_HZ * durationMs / 1000.0).toInt().coerceAtLeast(1),
                amplitude = amplitude,
                startPhase = phase,
                attackSamples = (SAMPLE_RATE_HZ * 0.004).toInt(),
                releaseSamples = (SAMPLE_RATE_HZ * 0.004).toInt()
            )
        }

        fun gap(durationMs: Long) {
            if (closed || durationMs <= 0) return
            writeSilence(track, (SAMPLE_RATE_HZ * durationMs / 1000.0).toInt().coerceAtLeast(1))
        }

        override fun close() {
            if (closed) return
            closed = true
            runCatching { track.stop() }
            track.release()
        }
    }

    fun openToneSession(frequencyHz: Double, amplitude: Double = 0.65): ToneSession {
        require(frequencyHz in 100.0..22_000.0) { "Tone frequency must be between 100 and 22000 Hz." }
        return ToneSession(frequencyHz, amplitude.coerceIn(0.0, 1.0))
    }

    fun playToneBlocking(frequencyHz: Double, durationMs: Long, amplitude: Double = 0.65) {
        require(frequencyHz in 100.0..22_000.0) { "Tone frequency must be between 100 and 22000 Hz." }
        if (durationMs <= 0) return
        val samples = samplesForTone(frequencyHz, durationMs, amplitude)
        val track = newTrack(max(samples.size * 2, 8192))
        try {
            track.play()
            writeFully(track, samples)
            track.stop()
        } finally {
            track.release()
        }
    }

    fun playOokFramesBlocking(
        frames: List<String>,
        carrierHz: Double,
        bitMs: Int,
        cycles: Int,
        onProgress: (cycle: Int, frameIndex: Int, totalFrames: Int) -> Unit = { _, _, _ -> },
        onBitProgress: (cycle: Int, frameIndex: Int, bitIndex: Int, totalBits: Int) -> Unit = { _, _, _, _ -> },
        shouldContinue: () -> Boolean = { true }
    ) {
        require(carrierHz in 60.0..1000.0) { "Surface carrier must be between 60 and 1000 Hz." }
        val safeBitMs = bitMs.coerceIn(20, 2000)
        val samplesPerBit = (SAMPLE_RATE_HZ * safeBitMs / 1000.0).toInt().coerceAtLeast(32)
        val minBuffer = AudioTrack.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        require(minBuffer > 0) { "No usable audio-output buffer size was reported." }
        val track = newTrack(max(minBuffer, samplesPerBit * 3))
        var phase = 0.0
        try {
            track.play()
            repeat(cycles.coerceIn(1, 100)) { cycleIndex ->
                if (!shouldContinue()) return
                frames.forEachIndexed { frameIndex, frame ->
                    if (!shouldContinue()) return
                    onProgress(cycleIndex + 1, frameIndex + 1, frames.size)
                    val physicalBits = FskPhysicalCodec.encodeFrame(frame)
                    physicalBits.forEachIndexed { bitIndex, bit ->
                        if (!shouldContinue()) return
                        onBitProgress(cycleIndex + 1, frameIndex + 1, bitIndex + 1, physicalBits.size)
                        if (bit) {
                            val previousOn = bitIndex > 0 && physicalBits[bitIndex - 1]
                            val nextOn = bitIndex + 1 < physicalBits.size && physicalBits[bitIndex + 1]
                            phase = writeToneSamplesRamped(
                                track = track,
                                frequencyHz = carrierHz,
                                count = samplesPerBit,
                                amplitude = 0.48,
                                startPhase = phase,
                                attackSamples = if (previousOn) 0 else (SAMPLE_RATE_HZ * 0.004).toInt(),
                                releaseSamples = if (nextOn) 0 else (SAMPLE_RATE_HZ * 0.004).toInt()
                            )
                        } else {
                            writeSilence(track, samplesPerBit)
                        }
                    }
                    writeSilence(track, (SAMPLE_RATE_HZ * 0.25).toInt())
                }
            }
            track.stop()
        } finally {
            track.release()
        }
    }

    fun playFskFramesBlocking(
        frames: List<String>,
        markHz: Double,
        spaceHz: Double,
        bitMs: Int,
        pttLeadMs: Int,
        cycles: Int,
        onProgress: (cycle: Int, frameIndex: Int, totalFrames: Int) -> Unit = { _, _, _ -> },
        shouldContinue: () -> Boolean = { true }
    ) {
        require(markHz in 100.0..22_000.0 && spaceHz in 100.0..22_000.0) {
            "Carrier frequencies must be between 100 and 22000 Hz."
        }
        require(abs(markHz - spaceHz) >= 80.0) { "FSK carriers are too close together." }
        val safeBitMs = bitMs.coerceIn(5, 1000)
        val safeCycles = cycles.coerceIn(1, 100)
        val samplesPerBit = (SAMPLE_RATE_HZ * safeBitMs / 1000.0).toInt().coerceAtLeast(32)
        val minBuffer = AudioTrack.getMinBufferSize(
            SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        require(minBuffer > 0) { "No usable audio-output buffer size was reported." }
        val track = newTrack(max(minBuffer, samplesPerBit * 4))
        // Avoid driving phone speakers/microphones into clipping. The audible channel
        // proved reliable at lower amplitude in device tests; high-band gets a little
        // more drive to compensate for handset roll-off.
        val driveAmplitude = if (max(markHz, spaceHz) >= 10_000.0) 0.34 else 0.28
        val leadAmplitude = driveAmplitude * 0.78
        var phase = 0.0
        try {
            track.play()
            repeat(safeCycles) { cycleIndex ->
                if (!shouldContinue()) return
                frames.forEachIndexed { frameIndex, frame ->
                    if (!shouldContinue()) return
                    onProgress(cycleIndex + 1, frameIndex + 1, frames.size)
                    var carrierPrimed = false
                    if (pttLeadMs > 0) {
                        // A gently attacked mark carrier, not silence, gives VOX/squelch time to open
                        // without the broadband click caused by an instantaneous waveform edge.
                        phase = writeToneSamplesRamped(
                            track = track,
                            frequencyHz = markHz,
                            count = (SAMPLE_RATE_HZ * pttLeadMs / 1000.0).toInt().coerceAtLeast(1),
                            amplitude = leadAmplitude,
                            startPhase = phase,
                            attackSamples = (SAMPLE_RATE_HZ * 0.004).toInt(),
                            releaseSamples = 0
                        )
                        carrierPrimed = true
                    }
                    val physicalBits = FskPhysicalCodec.encodeFrame(frame)
                    var lastFrequency = markHz
                    physicalBits.forEachIndexed { bitIndex, bit ->
                        if (!shouldContinue()) return
                        val frequency = if (bit) markHz else spaceHz
                        lastFrequency = frequency
                        phase = if (!carrierPrimed && bitIndex == 0) {
                            writeToneSamplesRamped(
                                track, frequency, samplesPerBit, driveAmplitude, phase,
                                attackSamples = (SAMPLE_RATE_HZ * 0.004).toInt(),
                                releaseSamples = 0
                            )
                        } else {
                            writeToneSamples(track, frequency, samplesPerBit, driveAmplitude, phase)
                        }
                        carrierPrimed = true
                    }
                    phase = writeToneSamplesRamped(
                        track, lastFrequency, (SAMPLE_RATE_HZ * 0.004).toInt(), driveAmplitude, phase,
                        attackSamples = 0,
                        releaseSamples = (SAMPLE_RATE_HZ * 0.004).toInt()
                    )
                    writeSilence(track, (SAMPLE_RATE_HZ * 0.18).toInt())
                }
            }
            track.stop()
        } finally {
            track.release()
        }
    }

    private fun newTrack(bufferBytes: Int): AudioTrack = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE_HZ)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
        )
        .setTransferMode(AudioTrack.MODE_STREAM)
        .setBufferSizeInBytes(bufferBytes.coerceAtLeast(4096))
        .build()

    private fun samplesForTone(frequencyHz: Double, durationMs: Long, amplitude: Double): ShortArray {
        val count = (SAMPLE_RATE_HZ * durationMs / 1000.0).toInt().coerceAtLeast(1)
        val out = ShortArray(count)
        var phase = 0.0
        val phaseStep = 2.0 * PI * frequencyHz / SAMPLE_RATE_HZ
        val scale = 32767.0 * amplitude.coerceIn(0.0, 1.0)
        for (i in out.indices) {
            out[i] = (sin(phase) * scale).toInt().coerceIn(-32767, 32767).toShort()
            phase += phaseStep
            if (phase > 2.0 * PI) phase -= 2.0 * PI
        }
        return out
    }

    private fun writeTone(
        track: AudioTrack,
        frequencyHz: Double,
        durationMs: Int,
        amplitude: Double,
        phase: Double
    ): Double {
        val count = (SAMPLE_RATE_HZ * durationMs / 1000.0).toInt().coerceAtLeast(1)
        return writeToneSamples(track, frequencyHz, count, amplitude, phase)
    }

    private fun writeToneSamples(
        track: AudioTrack,
        frequencyHz: Double,
        count: Int,
        amplitude: Double,
        startPhase: Double
    ): Double {
        val chunk = ShortArray(minOf(2048, count.coerceAtLeast(1)))
        var remaining = count
        var phase = startPhase
        val phaseStep = 2.0 * PI * frequencyHz / SAMPLE_RATE_HZ
        val scale = 32767.0 * amplitude.coerceIn(0.0, 1.0)
        while (remaining > 0) {
            val n = minOf(chunk.size, remaining)
            for (i in 0 until n) {
                chunk[i] = (sin(phase) * scale).toInt().coerceIn(-32767, 32767).toShort()
                phase += phaseStep
                if (phase > 2.0 * PI) phase -= 2.0 * PI
            }
            writeFully(track, chunk, n)
            remaining -= n
        }
        return phase
    }

    private fun writeToneSamplesRamped(
        track: AudioTrack,
        frequencyHz: Double,
        count: Int,
        amplitude: Double,
        startPhase: Double,
        attackSamples: Int,
        releaseSamples: Int
    ): Double {
        val chunk = ShortArray(minOf(2048, count.coerceAtLeast(1)))
        var remaining = count
        var written = 0
        var phase = startPhase
        val phaseStep = 2.0 * PI * frequencyHz / SAMPLE_RATE_HZ
        val baseScale = 32767.0 * amplitude.coerceIn(0.0, 1.0)
        while (remaining > 0) {
            val n = minOf(chunk.size, remaining)
            for (i in 0 until n) {
                val absolute = written + i
                val attack = if (attackSamples <= 0) 1.0 else (absolute.toDouble() / attackSamples.toDouble()).coerceIn(0.0, 1.0)
                val samplesFromEnd = count - 1 - absolute
                val release = if (releaseSamples <= 0) 1.0 else (samplesFromEnd.toDouble() / releaseSamples.toDouble()).coerceIn(0.0, 1.0)
                val envelope = minOf(attack, release)
                chunk[i] = (sin(phase) * baseScale * envelope).toInt().coerceIn(-32767, 32767).toShort()
                phase += phaseStep
                if (phase > 2.0 * PI) phase -= 2.0 * PI
            }
            writeFully(track, chunk, n)
            written += n
            remaining -= n
        }
        return phase
    }

    private fun writeSilence(track: AudioTrack, count: Int) {
        if (count <= 0) return
        val chunk = ShortArray(minOf(2048, count))
        var remaining = count
        while (remaining > 0) {
            val n = minOf(chunk.size, remaining)
            writeFully(track, chunk, n)
            remaining -= n
        }
    }

    private fun writeFully(track: AudioTrack, values: ShortArray, length: Int = values.size) {
        var offset = 0
        while (offset < length) {
            val wrote = track.write(values, offset, length - offset, AudioTrack.WRITE_BLOCKING)
            if (wrote <= 0) error("Audio output failed with code $wrote")
            offset += wrote
        }
    }
}

/** Shared module-local microphone boundary. Raw PCM is never retained. */
private class SignalAudioInput(private val appContext: Context) {
    data class Opened(val record: AudioRecord, val source: Int, val sourceLabel: String)

    fun open(sampleRate: Int, windowSamples: Int, preferUnprocessed: Boolean): Opened? {
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) return null

        val candidates = buildList {
            if (preferUnprocessed && supportsUnprocessed()) add(MediaRecorder.AudioSource.UNPROCESSED)
            // MIC normally preserves non-speech carriers better than voice-recognition DSP.
            add(MediaRecorder.AudioSource.MIC)
            add(MediaRecorder.AudioSource.VOICE_RECOGNITION)
        }.distinct()

        candidates.forEach { source ->
            val record = runCatching {
                AudioRecord.Builder()
                    .setAudioSource(source)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(max(minBuffer, windowSamples * 2 * 8))
                    .build()
            }.getOrNull() ?: return@forEach
            if (record.state == AudioRecord.STATE_INITIALIZED) {
                return Opened(record, source, sourceLabel(source))
            }
            record.release()
        }
        return null
    }

    private fun supportsUnprocessed(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val manager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        return manager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)
            ?.equals("true", ignoreCase = true) == true
    }

    private fun sourceLabel(source: Int): String = when (source) {
        MediaRecorder.AudioSource.UNPROCESSED -> "UNPROCESSED"
        MediaRecorder.AudioSource.MIC -> "MIC"
        MediaRecorder.AudioSource.VOICE_RECOGNITION -> "VOICE_RECOGNITION"
        else -> source.toString()
    }
}

/** Low-latency detector for the amplitude envelope of one Morse audio carrier. */
class SignalToneReceiverEngine(private val appContext: Context) {
    data class ToneWindow(
        val timestampMs: Long,
        val isOn: Boolean,
        val dbfs: Double,
        val targetPower: Double,
        val guardPower: Double,
        val audioSource: String
    )

    private val running = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var record: AudioRecord? = null
    private var worker: Thread? = null

    fun start(
        toneHz: Double,
        toleranceHz: Double,
        minimumDbfs: Double,
        onWindow: (ToneWindow) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!running.compareAndSet(false, true)) return
        val sampleRate = SignalAudioOutput.SAMPLE_RATE_HZ
        val windowSamples = 960 // 20 ms
        val opened = SignalAudioInput(appContext).open(sampleRate, windowSamples, preferUnprocessed = false)
        if (opened == null) {
            running.set(false)
            onError("No usable microphone input could be opened.")
            return
        }
        val audioRecord = opened.record
        record = audioRecord
        val sourceLabel = opened.sourceLabel
        val guardOffset = max(40.0, toleranceHz.coerceAtLeast(10.0))

        worker = Thread({
            try {
                audioRecord.startRecording()
                val shorts = ShortArray(windowSamples)
                while (running.get()) {
                    val read = audioRecord.read(shorts, 0, shorts.size, AudioRecord.READ_BLOCKING)
                    if (read <= 0) continue
                    val samples = DoubleArray(read)
                    var sum2 = 0.0
                    for (i in 0 until read) {
                        val value = shorts[i] / 32768.0
                        samples[i] = value
                        sum2 += value * value
                    }
                    val rms = sqrt(sum2 / read.coerceAtLeast(1))
                    val dbfs = if (rms <= 1e-12) -120.0 else 20.0 * log10(rms)
                    val target = goertzelPower(samples, sampleRate, toneHz)
                    val low = goertzelPower(samples, sampleRate, (toneHz - guardOffset).coerceAtLeast(80.0))
                    val high = goertzelPower(samples, sampleRate, (toneHz + guardOffset).coerceAtMost(22_000.0))
                    val guard = max(low, high)
                    val ratio = target / guard.coerceAtLeast(1e-12)
                    val on = dbfs >= minimumDbfs && ratio >= 1.55
                    val window = ToneWindow(
                        timestampMs = System.currentTimeMillis(),
                        isOn = on,
                        dbfs = dbfs,
                        targetPower = target,
                        guardPower = guard,
                        audioSource = sourceLabel
                    )
                    mainHandler.post { if (running.get()) onWindow(window) }
                }
            } catch (security: SecurityException) {
                mainHandler.post { onError("Microphone permission was denied or revoked.") }
            } catch (error: Throwable) {
                if (running.get()) mainHandler.post { onError(error.message ?: "Morse microphone receiver failed.") }
            } finally {
                runCatching { audioRecord.stop() }
                audioRecord.release()
                record = null
                running.set(false)
            }
        }, "MethodMesh-Signals-Morse").also { it.start() }
    }

    fun stop() {
        running.set(false)
        runCatching { record?.stop() }
        worker?.interrupt()
        worker = null
        record = null
    }
}

/** Low-latency two-carrier detector for FSK demodulation. */
class SignalFskReceiverEngine(private val appContext: Context) {
    data class ToneWindow(
        val timestampMs: Long,
        /** true = mark, false = space, null = uncertain/silent */
        val tone: Boolean?,
        val dbfs: Double,
        val markPower: Double,
        val spacePower: Double,
        val audioSource: String,
        val decisionConfidenceDb: Double = 0.0,
        val peakHz: Double = 0.0,
        val spectrum: FloatArray? = null,
        val spectrumMinHz: Double = 0.0,
        val spectrumMaxHz: Double = 0.0
    )

    private val running = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var record: AudioRecord? = null
    private var worker: Thread? = null

    fun start(
        markHz: Double,
        spaceHz: Double,
        minimumDbfs: Double,
        onWindow: (ToneWindow) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!running.compareAndSet(false, true)) return
        val sampleRate = SignalAudioOutput.SAMPLE_RATE_HZ
        val windowSamples = 480 // 10 ms at 48 kHz; several windows form one configured bit.
        val opened = SignalAudioInput(appContext).open(
            sampleRate,
            windowSamples,
            preferUnprocessed = max(markHz, spaceHz) >= 12_000.0
        )
        if (opened == null) {
            running.set(false)
            onError("No usable microphone input could be opened.")
            return
        }
        val audioRecord = opened.record
        val sourceLabel = opened.sourceLabel
        record = audioRecord

        worker = Thread({
            try {
                audioRecord.startRecording()
                val shorts = ShortArray(windowSamples)
                var spectrumTick = 0
                while (running.get()) {
                    val read = audioRecord.read(shorts, 0, shorts.size, AudioRecord.READ_BLOCKING)
                    if (read <= 0) continue
                    val samples = DoubleArray(read)
                    var sum2 = 0.0
                    for (i in 0 until read) {
                        val value = shorts[i] / 32768.0
                        samples[i] = value
                        sum2 += value * value
                    }
                    val rms = sqrt(sum2 / read.coerceAtLeast(1))
                    val dbfs = if (rms <= 1e-12) -120.0 else 20.0 * log10(rms)
                    val markPower = goertzelPower(samples, sampleRate, markHz)
                    val spacePower = goertzelPower(samples, sampleRate, spaceHz)
                    val stronger = max(markPower, spacePower)
                    val weaker = minOf(markPower, spacePower)
                    val ratio = if (weaker <= 1e-12) Double.POSITIVE_INFINITY else stronger / weaker
                    val tone = when {
                        dbfs < minimumDbfs -> null
                        ratio < 1.45 -> null
                        markPower > spacePower -> true
                        else -> false
                    }
                    spectrumTick++
                    val confidenceDb = if (stronger <= 1e-12 || weaker <= 1e-12) 60.0 else (10.0 * log10(stronger / weaker)).coerceIn(0.0, 60.0)
                    val bandMin = if (max(markHz, spaceHz) >= 10_000.0) 11_000.0 else 200.0
                    val bandMax = if (max(markHz, spaceHz) >= 10_000.0) 17_000.0 else 8_000.0
                    val spectrumResult = if (spectrumTick % 5 == 0) signalSpectrum(samples, sampleRate, bandMin, bandMax, 40) else null
                    val window = ToneWindow(
                        System.currentTimeMillis(),
                        tone,
                        dbfs,
                        markPower,
                        spacePower,
                        sourceLabel,
                        decisionConfidenceDb = confidenceDb,
                        peakHz = spectrumResult?.second ?: 0.0,
                        spectrum = spectrumResult?.first,
                        spectrumMinHz = bandMin,
                        spectrumMaxHz = bandMax
                    )
                    mainHandler.post { if (running.get()) onWindow(window) }
                }
            } catch (security: SecurityException) {
                mainHandler.post { onError("Microphone permission was denied or revoked.") }
            } catch (error: Throwable) {
                if (running.get()) mainHandler.post { onError(error.message ?: "FSK microphone receiver failed.") }
            } finally {
                runCatching { audioRecord.stop() }
                audioRecord.release()
                record = null
                running.set(false)
            }
        }, "MethodMesh-Signals-FSK").also { it.start() }
    }

    fun stop() {
        running.set(false)
        runCatching { record?.stop() }
        worker?.interrupt()
        worker = null
        record = null
    }
}

private fun signalSpectrum(samples: DoubleArray, sampleRate: Int, minHz: Double, maxHz: Double, bins: Int): Pair<FloatArray, Double> {
    val safeBins = bins.coerceIn(8, 96)
    val powers = DoubleArray(safeBins)
    var peakIndex = 0
    var peakPower = -1.0
    for (i in 0 until safeBins) {
        val fraction = if (safeBins == 1) 0.0 else i.toDouble() / (safeBins - 1).toDouble()
        val frequency = minHz + (maxHz - minHz) * fraction
        val power = goertzelPower(samples, sampleRate, frequency).coerceAtLeast(1e-18)
        powers[i] = power
        if (power > peakPower) { peakPower = power; peakIndex = i }
    }
    val peakDb = 10.0 * log10(peakPower.coerceAtLeast(1e-18))
    val normalized = FloatArray(safeBins) { i ->
        val db = 10.0 * log10(powers[i])
        ((db - (peakDb - 45.0)) / 45.0).coerceIn(0.0, 1.0).toFloat()
    }
    val peakFraction = if (safeBins == 1) 0.0 else peakIndex.toDouble() / (safeBins - 1).toDouble()
    return normalized to (minHz + (maxHz - minHz) * peakFraction)
}

private fun goertzelPower(samples: DoubleArray, sampleRate: Int, targetHz: Double): Double {
    if (samples.isEmpty() || targetHz <= 0.0 || targetHz >= sampleRate / 2.0) return 0.0
    val omega = 2.0 * PI * targetHz / sampleRate
    val coeff = 2.0 * cos(omega)
    var s0: Double
    var s1 = 0.0
    var s2 = 0.0
    samples.forEach { sample ->
        s0 = sample + coeff * s1 - s2
        s2 = s1
        s1 = s0
    }
    return s1 * s1 + s2 * s2 - coeff * s1 * s2
}
