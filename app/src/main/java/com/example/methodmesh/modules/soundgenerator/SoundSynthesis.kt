package com.example.methodmesh.modules.soundgenerator

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin

/** Pure, Android-free stimulus synthesis so the signal-generation contract can be unit tested. */
data class SoundSpec(
    val stimulusType: String = "tone",
    val waveform: String = "sine",
    val frequencyHz: Double = 1000.0,
    val noiseType: String = "white",
    val sweepStartHz: Double = 250.0,
    val sweepEndHz: Double = 8000.0,
    val sweepScale: String = "logarithmic",
    val levelDbfs: Double = -30.0,
    val durationMs: Int = 1000,
    val channel: String = "both",
    val fadeMs: Int = 10,
    val gateMode: String = "steady",
    val pulseOnMs: Int = 500,
    val pulseOffMs: Int = 500,
    val sampleRateHz: Int = 48000,
    val noiseSeedMode: String = "secure_random",
    val noiseSeed: String = ""
)

data class RenderedSound(
    val spec: SoundSpec,
    val pcm: ShortArray,
    val frameCount: Int,
    val channelCount: Int,
    val amplitudeLinear: Double,
    val noiseSeedUsed: String,
    val pcmSha256: String,
    val algorithmId: String,
    val algorithmVersion: String = SoundSynthesis.ALGORITHM_VERSION
)

object SoundSynthesis {
    const val ALGORITHM_VERSION = "1.0.0"
    private const val MAX_DURATION_MS = 60_000

    fun render(raw: SoundSpec): RenderedSound {
        val spec = validate(raw)
        val channels = 2
        val frames = ((spec.durationMs.toLong() * spec.sampleRateHz) / 1000L).toInt().coerceAtLeast(1)
        val pcm = ShortArray(frames * channels)
        val amplitude = 10.0.pow(spec.levelDbfs / 20.0)
        val seedUsed = when {
            spec.stimulusType != "noise" -> ""
            spec.noiseSeedMode == "fixed_seed" -> spec.noiseSeed.ifBlank { "methodmesh-sound" }
            else -> secureSeedHex()
        }
        val rng = if (spec.stimulusType == "noise") StableNoise(seedUsed) else null
        val pink = PinkNoiseState()
        var brown = 0.0
        var phase = 0.0
        val fadeFrames = ((spec.fadeMs.toLong() * spec.sampleRateHz) / 1000L).toInt()
        val onFrames = ((spec.pulseOnMs.toLong() * spec.sampleRateHz) / 1000L).toInt().coerceAtLeast(1)
        val offFrames = ((spec.pulseOffMs.toLong() * spec.sampleRateHz) / 1000L).toInt().coerceAtLeast(0)
        val pulseCycle = onFrames + offFrames

        for (frame in 0 until frames) {
            val progress = if (frames <= 1) 0.0 else frame.toDouble() / (frames - 1).toDouble()
            val rawValue = when (spec.stimulusType) {
                "noise" -> {
                    val white = rng!!.nextSignedUnit()
                    when (spec.noiseType) {
                        "white" -> white
                        "pink" -> pink.next(white)
                        "brown" -> {
                            brown = (brown + 0.02 * white) / 1.02
                            (brown * 3.5).coerceIn(-1.0, 1.0)
                        }
                        else -> error("Unsupported noise type: ${spec.noiseType}")
                    }
                }
                "sweep" -> {
                    val frequency = when (spec.sweepScale) {
                        "linear" -> spec.sweepStartHz + (spec.sweepEndHz - spec.sweepStartHz) * progress
                        "logarithmic" -> spec.sweepStartHz * (spec.sweepEndHz / spec.sweepStartHz).pow(progress)
                        else -> error("Unsupported sweep scale: ${spec.sweepScale}")
                    }
                    val value = waveformValue(spec.waveform, phase)
                    phase = wrapPhase(phase + (2.0 * PI * frequency / spec.sampleRateHz.toDouble()))
                    value
                }
                else -> {
                    val value = waveformValue(spec.waveform, phase)
                    phase = wrapPhase(phase + (2.0 * PI * spec.frequencyHz / spec.sampleRateHz.toDouble()))
                    value
                }
            }

            val globalEnvelope = finiteEnvelope(frame, frames, fadeFrames)
            val pulseEnvelope = if (spec.gateMode == "pulsed") {
                pulseEnvelope(frame, pulseCycle, onFrames, fadeFrames)
            } else 1.0
            val sample = (rawValue * amplitude * globalEnvelope * pulseEnvelope).coerceIn(-1.0, 1.0)
            val shortValue = normalizedToShort(sample)
            val base = frame * channels
            when (spec.channel) {
                "left" -> {
                    pcm[base] = shortValue
                    pcm[base + 1] = 0
                }
                "right" -> {
                    pcm[base] = 0
                    pcm[base + 1] = shortValue
                }
                else -> {
                    pcm[base] = shortValue
                    pcm[base + 1] = shortValue
                }
            }
        }

        return RenderedSound(
            spec = spec,
            pcm = pcm,
            frameCount = frames,
            channelCount = channels,
            amplitudeLinear = amplitude,
            noiseSeedUsed = seedUsed,
            pcmSha256 = sha256ShortsLittleEndian(pcm),
            algorithmId = algorithmId(spec)
        )
    }

    fun validate(spec: SoundSpec): SoundSpec {
        require(spec.stimulusType in setOf("tone", "noise", "sweep")) { "Sound type must be tone, noise or sweep." }
        require(spec.waveform in setOf("sine", "square", "triangle", "sawtooth")) { "Unsupported waveform." }
        require(spec.noiseType in setOf("white", "pink", "brown")) { "Unsupported noise type." }
        require(spec.sweepScale in setOf("linear", "logarithmic")) { "Sweep scale must be linear or logarithmic." }
        require(spec.channel in setOf("both", "left", "right")) { "Channel must be both, left or right." }
        require(spec.gateMode in setOf("steady", "pulsed")) { "Gate mode must be steady or pulsed." }
        require(spec.noiseSeedMode in setOf("secure_random", "fixed_seed")) { "Seed mode must be secure_random or fixed_seed." }
        require(spec.sampleRateHz in setOf(44100, 48000)) { "Sample rate must be 44100 or 48000 Hz in v0.1." }
        require(spec.durationMs in 20..MAX_DURATION_MS) { "Duration must be between 20 ms and 60000 ms." }
        require(spec.levelDbfs.isFinite() && spec.levelDbfs in -80.0..0.0) { "Digital level must be between -80 and 0 dBFS." }
        require(spec.fadeMs in 0..5000) { "Fade must be between 0 and 5000 ms." }
        require(spec.fadeMs * 2 <= spec.durationMs) { "Fade cannot exceed half the stimulus duration." }
        require(spec.pulseOnMs in 5..60_000) { "Pulse on-time must be at least 5 ms." }
        require(spec.pulseOffMs in 0..60_000) { "Pulse off-time cannot be negative." }
        if (spec.stimulusType == "tone") validateFrequency(spec.frequencyHz, spec.sampleRateHz, "Tone frequency")
        if (spec.stimulusType == "sweep") {
            validateFrequency(spec.sweepStartHz, spec.sampleRateHz, "Sweep start frequency")
            validateFrequency(spec.sweepEndHz, spec.sampleRateHz, "Sweep end frequency")
            require(spec.sweepEndHz != spec.sweepStartHz) { "Sweep start and end frequencies must differ." }
            if (spec.sweepScale == "logarithmic") require(spec.sweepStartHz > 0.0 && spec.sweepEndHz > 0.0) {
                "Logarithmic sweeps require positive frequencies."
            }
        }
        return spec
    }

    private fun validateFrequency(value: Double, sampleRate: Int, label: String) {
        require(value.isFinite() && value >= 1.0) { "$label must be at least 1 Hz." }
        require(value < sampleRate / 2.0) { "$label must be below the Nyquist frequency (${sampleRate / 2} Hz)." }
    }

    private fun waveformValue(waveform: String, phase: Double): Double {
        val cycle = wrapPhase(phase) / (2.0 * PI)
        return when (waveform) {
            "sine" -> sin(phase)
            "square" -> if (sin(phase) >= 0.0) 1.0 else -1.0
            "triangle" -> 4.0 * abs(cycle - floor(cycle + 0.5)) - 1.0
            "sawtooth" -> 2.0 * (cycle - floor(cycle + 0.5))
            else -> error("Unsupported waveform: $waveform")
        }
    }

    private fun finiteEnvelope(frame: Int, totalFrames: Int, fadeFrames: Int): Double {
        if (fadeFrames <= 0) return 1.0
        val attack = (frame.toDouble() / fadeFrames.toDouble()).coerceIn(0.0, 1.0)
        val release = ((totalFrames - 1 - frame).toDouble() / fadeFrames.toDouble()).coerceIn(0.0, 1.0)
        return minOf(attack, release, 1.0)
    }

    private fun pulseEnvelope(frame: Int, cycleFrames: Int, onFrames: Int, fadeFrames: Int): Double {
        if (cycleFrames <= 0) return 1.0
        val pos = frame % cycleFrames
        if (pos >= onFrames) return 0.0
        val localFade = minOf(fadeFrames, (onFrames / 2).coerceAtLeast(0))
        if (localFade <= 0) return 1.0
        val attack = (pos.toDouble() / localFade.toDouble()).coerceIn(0.0, 1.0)
        val release = ((onFrames - 1 - pos).toDouble() / localFade.toDouble()).coerceIn(0.0, 1.0)
        return minOf(attack, release, 1.0)
    }

    private fun normalizedToShort(value: Double): Short = when {
        value >= 1.0 -> Short.MAX_VALUE
        value <= -1.0 -> Short.MIN_VALUE
        else -> (value * Short.MAX_VALUE.toDouble()).toInt().toShort()
    }

    private fun wrapPhase(value: Double): Double {
        val twoPi = 2.0 * PI
        var wrapped = value % twoPi
        if (wrapped < 0.0) wrapped += twoPi
        return wrapped
    }

    private fun algorithmId(spec: SoundSpec): String = when (spec.stimulusType) {
        "noise" -> "methodmesh.noise.${spec.noiseType}"
        "sweep" -> "methodmesh.sweep.${spec.sweepScale}.${spec.waveform}"
        else -> "methodmesh.tone.${spec.waveform}"
    }

    private fun secureSeedHex(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun sha256ShortsLittleEndian(values: ShortArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteBuffer.allocate(8192).order(ByteOrder.LITTLE_ENDIAN)
        values.forEach { value ->
            if (buffer.remaining() < 2) {
                digest.update(buffer.array(), 0, buffer.position())
                buffer.clear()
            }
            buffer.putShort(value)
        }
        if (buffer.position() > 0) digest.update(buffer.array(), 0, buffer.position())
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

private class StableNoise(seedText: String) {
    private var state: Long = seedToLong(seedText).let { if (it == 0L) 0x6a09e667f3bcc909L else it }

    fun nextSignedUnit(): Double {
        var x = state
        x = x xor (x ushr 12)
        x = x xor (x shl 25)
        x = x xor (x ushr 27)
        state = x
        val value = x * 2685821657736338717L
        val top53 = (value ushr 11) and ((1L shl 53) - 1L)
        return (top53.toDouble() / (1L shl 53).toDouble()) * 2.0 - 1.0
    }

    companion object {
        private fun seedToLong(seed: String): Long {
            val bytes = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray(Charsets.UTF_8))
            return ByteBuffer.wrap(bytes, 0, 8).order(ByteOrder.BIG_ENDIAN).long
        }
    }
}

private class PinkNoiseState {
    private var b0 = 0.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var b3 = 0.0
    private var b4 = 0.0
    private var b5 = 0.0
    private var b6 = 0.0

    fun next(white: Double): Double {
        b0 = 0.99886 * b0 + white * 0.0555179
        b1 = 0.99332 * b1 + white * 0.0750759
        b2 = 0.96900 * b2 + white * 0.1538520
        b3 = 0.86650 * b3 + white * 0.3104856
        b4 = 0.55000 * b4 + white * 0.5329522
        b5 = -0.7616 * b5 - white * 0.0168980
        val pink = b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362
        b6 = white * 0.115926
        return (pink * 0.11).coerceIn(-1.0, 1.0)
    }
}
