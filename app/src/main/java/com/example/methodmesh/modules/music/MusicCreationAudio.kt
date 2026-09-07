package com.example.methodmesh.modules.music

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/** Lightweight asset-free synthesized percussion for the music creation tools. */
internal class DrumSynthEngine(private val sampleRate: Int = 44100) {
    private var track: AudioTrack? = null

    fun stop() {
        runCatching { track?.stop() }
        runCatching { track?.release() }
        track = null
    }

    fun playVoice(voice: String) {
        val pcm = synthVoice(voice, 0.22)
        val one = newStaticTrack(pcm)
        one.setNotificationMarkerPosition(pcm.size)
        one.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(t: AudioTrack?) { runCatching { t?.release() } }
            override fun onPeriodicNotification(t: AudioTrack?) = Unit
        })
        one.play()
    }

    fun playPattern(bpm: Double, steps: Int, swing: Double, lanes: Map<String, List<Boolean>>) {
        stop()
        val stepSeconds = 60.0 / bpm / 4.0
        val nominal = (stepSeconds * sampleRate).toInt().coerceAtLeast(1)
        val offsets = IntArray(steps + 1)
        for (i in 0 until steps) {
            val swingFactor = if (i % 2 == 1) 1.0 + swing.coerceIn(0.0, 0.75) else 1.0 - swing.coerceIn(0.0, 0.75)
            offsets[i + 1] = offsets[i] + (nominal * swingFactor).toInt().coerceAtLeast(1)
        }
        val pcm = ShortArray(offsets.last())
        lanes.forEach { (voice, pattern) ->
            pattern.take(steps).forEachIndexed { i, hit -> if (hit) mixAt(pcm, offsets[i], synthVoice(voice, 0.20)) }
        }
        track = newStaticTrack(pcm).also {
            it.setLoopPoints(0, pcm.size, -1)
            it.play()
        }
    }

    private fun newStaticTrack(pcm: ShortArray): AudioTrack = AudioTrack.Builder()
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
        .setAudioFormat(AudioFormat.Builder().setSampleRate(sampleRate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
        .setBufferSizeInBytes(max(pcm.size * 2, 4096))
        .setTransferMode(AudioTrack.MODE_STATIC)
        .build().apply { write(pcm, 0, pcm.size) }

    private fun mixAt(dst: ShortArray, start: Int, src: ShortArray) {
        val n = minOf(src.size, dst.size - start)
        for (i in 0 until n) {
            val mixed = dst[start + i].toInt() + src[i].toInt()
            dst[start + i] = mixed.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    private fun synthVoice(voice: String, seconds: Double): ShortArray {
        val n = (seconds * sampleRate).toInt()
        val random = Random(voice.hashCode() xor System.nanoTime().toInt())
        return ShortArray(n) { i ->
            val t = i.toDouble() / sampleRate
            val amp = when (voice) {
                "kick" -> sin(2 * PI * (95.0 - 55.0 * (t / seconds)) * t) * exp(-t * 18.0)
                "snare" -> (random.nextDouble(-1.0, 1.0) * 0.75 + sin(2 * PI * 180.0 * t) * 0.25) * exp(-t * 24.0)
                "hat" -> random.nextDouble(-1.0, 1.0) * exp(-t * 55.0) * if (i % 2 == 0) 1.0 else -1.0
                "clap" -> random.nextDouble(-1.0, 1.0) * exp(-t * 30.0) * (if ((t * 45).toInt() % 2 == 0) 1.0 else 0.45)
                "tom" -> sin(2 * PI * 145.0 * t) * exp(-t * 16.0)
                else -> sin(2 * PI * 440.0 * t) * exp(-t * 35.0)
            }
            (amp * 15000.0).toInt().coerceIn(-32767, 32767).toShort()
        }
    }
}

/**
 * Development PCM phrase looper. The first recording fixes loop length; later recordings are
 * trimmed/padded to that length and mixed only at playback, so undo/mute remain cheap.
 */
internal class PcmLooperEngine(private val sampleRate: Int = 44100) {
    data class Layer(val id: Int, val pcm: ShortArray, var muted: Boolean = false)

    private val lock = Any()
    private val layers = mutableListOf<Layer>()
    private var recording = false
    private var playing = false
    private var recorder: AudioRecord? = null
    private var player: AudioTrack? = null
    private var recordingThread: Thread? = null
    private var playbackThread: Thread? = null
    private var nextId = 1

    val layerCount: Int get() = synchronized(lock) { layers.size }
    val loopSamples: Int get() = synchronized(lock) { layers.firstOrNull()?.pcm?.size ?: 0 }
    val loopMs: Long get() = (loopSamples * 1000L) / sampleRate
    fun layerSnapshot(): List<Layer> = synchronized(lock) { layers.map { Layer(it.id, ShortArray(0), it.muted) } }

    fun startRecording(onFinished: (Boolean) -> Unit) {
        if (recording) return
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(4096)
        val audioRecord = AudioRecord(MediaRecorder.AudioSource.DEFAULT, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuffer * 2)
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) { audioRecord.release(); onFinished(false); return }
        recorder = audioRecord
        recording = true
        recordingThread = thread(name = "methodmesh-music-loop-record") {
            val chunks = ArrayList<ShortArray>()
            var total = 0
            val buffer = ShortArray(minBuffer / 2)
            runCatching { audioRecord.startRecording() }
            while (recording) {
                val n = audioRecord.read(buffer, 0, buffer.size)
                if (n > 0) { chunks += buffer.copyOf(n); total += n }
            }
            runCatching { audioRecord.stop() }; audioRecord.release(); recorder = null
            if (total > 0) {
                val captured = ShortArray(total); var pos = 0
                chunks.forEach { c -> c.copyInto(captured, pos); pos += c.size }
                synchronized(lock) {
                    val target = layers.firstOrNull()?.pcm?.size ?: captured.size
                    val normalized = ShortArray(target)
                    captured.copyInto(normalized, endIndex = minOf(captured.size, target))
                    layers += Layer(nextId++, normalized)
                }
                onFinished(true)
            } else onFinished(false)
        }
    }

    fun stopRecording() { recording = false }

    fun toggleMute(id: Int) { synchronized(lock) { layers.firstOrNull { it.id == id }?.let { it.muted = !it.muted } } }
    fun undo() { synchronized(lock) { if (layers.isNotEmpty()) layers.removeLast() } }
    fun clear() { stopRecording(); stopPlayback(); synchronized(lock) { layers.clear() } }

    fun startPlayback() {
        if (playing || loopSamples <= 0) return
        playing = true
        val min = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(4096)
        val out = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(sampleRate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(min * 2).setTransferMode(AudioTrack.MODE_STREAM).build()
        player = out
        playbackThread = thread(name = "methodmesh-music-loop-play") {
            out.play()
            while (playing) {
                val mixed = synchronized(lock) {
                    val active = layers.filterNot { it.muted }
                    val n = layers.firstOrNull()?.pcm?.size ?: 0
                    ShortArray(n) { i ->
                        var sum = 0
                        active.forEach { sum += it.pcm[i].toInt() }
                        sum.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    }
                }
                if (mixed.isEmpty()) break
                var pos = 0
                while (playing && pos < mixed.size) {
                    val written = out.write(mixed, pos, mixed.size - pos)
                    if (written <= 0) break
                    pos += written
                }
            }
            runCatching { out.stop() }; out.release(); player = null
        }
    }

    fun stopPlayback() { playing = false }
    fun isPlaying() = playing
    fun isRecording() = recording
}

/**
 * Process-local active audio sessions. This keeps a live loop alive across Activity recreation/
 * rotation without serialising PCM through Bundle state. Sessions are explicitly removed on
 * cancel, back, or final Done. Process death intentionally ends an uncommitted audio session.
 */
internal object MusicAudioSessionStore {
    private val loopers = mutableMapOf<String, PcmLooperEngine>()

    @Synchronized
    fun looper(key: String): PcmLooperEngine = loopers.getOrPut(key) { PcmLooperEngine() }

    @Synchronized
    fun remove(key: String) {
        loopers.remove(key)?.clear()
    }
}
