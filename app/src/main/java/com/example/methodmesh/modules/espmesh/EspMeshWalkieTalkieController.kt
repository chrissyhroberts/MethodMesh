package com.example.methodmesh.modules.espmesh

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.security.SecureRandom
import java.util.TreeMap

/**
 * Phone-side ephemeral walkie-talkie engine layered on the persistent mesh.
 * Audio is never written to disk. Each 20 ms mu-law frame is E2E encrypted
 * before it enters BLE; the ESP only relays opaque compact ciphertext packets.
 */
data class EspMeshVoiceState(
    val listening: Boolean = true,
    val channel: String = "field-group",
    val transmitting: Boolean = false,
    val receiving: Boolean = false,
    val activeSpeaker: String = "",
    val packetsSent: Long = 0,
    val packetsReceived: Long = 0,
    val packetsDropped: Long = 0,
    val lastVoiceAtMs: Long = 0L,
    val error: String = ""
)

class EspMeshWalkieTalkieController private constructor(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val provider = EspMeshTransportProvider.get(context)
    private val crypto = EspMeshCryptoManager(context)
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableState = MutableStateFlow(
        EspMeshVoiceState(
            listening = provider.isVoiceListening(),
            channel = normalizeChannel(prefs.getString(KEY_CHANNEL, DEFAULT_CHANNEL).orEmpty())
        )
    )
    val state: StateFlow<EspMeshVoiceState> = mutableState

    private data class QueuedVoiceFrame(val frame: EspMeshDecryptedVoiceFrame, val epoch: Long)

    private val playbackFrames = Channel<QueuedVoiceFrame>(
        capacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val random = SecureRandom()
    private var started = false
    @Volatile private var transmitRequested = false
    @Volatile private var listenEpoch = 0L
    private var transmitJob: Job? = null
    @Volatile private var playbackTrack: AudioTrack? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private val playbackLock = Any()
    @Volatile private var playbackLastPacketAtMs = 0L

    @Synchronized
    fun start() {
        if (started) return
        started = true
        scope.launch {
            provider.voiceListening.collectLatest { enabled ->
                mutableState.update { it.copy(listening = enabled) }
                if (!enabled) stopPlaybackNow()
            }
        }
        scope.launch {
            provider.liveVoicePackets.collect { packet -> handleIncomingPacket(packet) }
        }
        scope.launch { playbackLoop() }
        scope.launch {
            while (isActive) {
                delay(250)
                val now = System.currentTimeMillis()
                if (mutableState.value.receiving && now - playbackLastPacketAtMs > RECEIVE_TIMEOUT_MS) {
                    stopPlaybackNow()
                }
            }
        }
    }

    fun setListening(enabled: Boolean) {
        if (enabled != mutableState.value.listening) listenEpoch++
        provider.setVoiceListening(enabled)
        mutableState.update { it.copy(listening = enabled, error = "") }
        if (!enabled) stopPlaybackNow()
    }

    fun toggleListening() = setListening(!mutableState.value.listening)

    fun setChannel(channel: String) {
        val normalized = normalizeChannel(channel)
        prefs.edit().putString(KEY_CHANNEL, normalized).apply()
        listenEpoch++
        mutableState.update { it.copy(channel = normalized, error = "") }
        stopPlaybackNow()
    }

    fun hasMicrophonePermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    /** Start press-and-hold transmission. Returns an explanation on failure. */
    fun startTransmit(): Result<Unit> {
        start()
        if (transmitRequested || mutableState.value.transmitting) return Result.success(Unit)
        if (!hasMicrophonePermission()) return Result.failure(IllegalStateException("Microphone permission is required for push-to-talk"))
        val transport = provider.snapshot.value
        if (!transport.enabled || !transport.connected) return Result.failure(IllegalStateException("Mesh gateway is not connected"))
        if (!transport.liveVoiceReady) return Result.failure(IllegalStateException("Gateway BLE MTU ${transport.bleMtu} is too small for low-latency voice; reconnect the gateway"))
        if (!provider.hasE2eKey()) return Result.failure(IllegalStateException("Mesh E2E key is not configured"))
        val current = mutableState.value
        if (current.receiving && System.currentTimeMillis() - current.lastVoiceAtMs < FLOOR_BUSY_MS) {
            return Result.failure(IllegalStateException("Voice channel is busy"))
        }

        transmitRequested = true
        mutableState.update { it.copy(transmitting = true, error = "") }
        transmitJob = scope.launch { captureAndTransmit() }
        return Result.success(Unit)
    }

    fun stopTransmit() {
        transmitRequested = false
    }

    private suspend fun captureAndTransmit() {
        val channel = mutableState.value.channel
        var sessionId = random.nextLong()
        if (sessionId == 0L) sessionId = 1L
        var sequence = 0
        var sentAny = false
        var recorder: AudioRecord? = null
        try {
            val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            require(minBuffer > 0) { "This phone does not expose an 8 kHz mono microphone path" }
            recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer, FRAME_SAMPLES * 2 * 6)
            )
            require(recorder.state == AudioRecord.STATE_INITIALIZED) { "Could not initialise the microphone" }
            recorder.startRecording()
            val frame = ShortArray(FRAME_SAMPLES)
            while (transmitRequested && scope.isActive) {
                var filled = 0
                while (filled < FRAME_SAMPLES && transmitRequested) {
                    val read = recorder.read(frame, filled, FRAME_SAMPLES - filled)
                    if (read <= 0) throw IllegalStateException("Microphone read failed ($read)")
                    filled += read
                }
                if (!transmitRequested || filled != FRAME_SAMPLES) break
                val flags = if (!sentAny) EspMeshLiveVoicePacket.FLAG_START else 0
                val packet = crypto.encryptVoiceFrame(
                    channel = channel,
                    phoneId = provider.phoneId(),
                    sessionId = sessionId,
                    sequence = sequence,
                    flags = flags,
                    audioMuLaw = EspMeshMuLawCodec.encode(frame)
                )
                if (!provider.sendLiveVoicePacket(packet)) throw IllegalStateException("Live voice lost the local mesh gateway")
                sentAny = true
                sequence++
                mutableState.update { it.copy(packetsSent = it.packetsSent + 1, lastVoiceAtMs = System.currentTimeMillis()) }
            }
        } catch (error: Throwable) {
            mutableState.update { it.copy(error = error.message.orEmpty()) }
        } finally {
            transmitRequested = false
            runCatching { recorder?.stop() }
            runCatching { recorder?.release() }
            if (sentAny && provider.snapshot.value.connected && provider.hasE2eKey()) {
                runCatching {
                    val endPacket = crypto.encryptVoiceFrame(
                        channel = channel,
                        phoneId = provider.phoneId(),
                        sessionId = sessionId,
                        sequence = sequence,
                        flags = EspMeshLiveVoicePacket.FLAG_END,
                        audioMuLaw = byteArrayOf()
                    )
                    provider.sendLiveVoicePacket(endPacket)
                }
            }
            mutableState.update { it.copy(transmitting = false) }
        }
    }

    private fun handleIncomingPacket(packetBytes: ByteArray) {
        if (!mutableState.value.listening || mutableState.value.transmitting || !provider.hasE2eKey()) return
        val packet = runCatching { EspMeshLiveVoicePacket.decode(packetBytes) }.getOrElse {
            mutableState.update { state -> state.copy(packetsDropped = state.packetsDropped + 1, error = "Rejected malformed live-voice packet") }
            return
        }
        val channelTag = runCatching { crypto.voiceChannelTag(mutableState.value.channel) }.getOrNull() ?: return
        if (packet.channelTag != channelTag) return
        val ownTag = runCatching { crypto.voiceSourceTag(provider.phoneId()) }.getOrNull()
        if (ownTag != null && packet.sourceTag == ownTag) return
        val frame = runCatching { crypto.decryptVoiceFrame(packetBytes) }.getOrElse {
            mutableState.update { state -> state.copy(packetsDropped = state.packetsDropped + 1, error = "Rejected unauthenticated live voice") }
            return
        }
        playbackLastPacketAtMs = System.currentTimeMillis()
        mutableState.update {
            it.copy(
                receiving = true,
                activeSpeaker = "%08x".format(frame.sourceTag),
                packetsReceived = it.packetsReceived + 1,
                lastVoiceAtMs = playbackLastPacketAtMs,
                error = ""
            )
        }
        if (!playbackFrames.trySend(QueuedVoiceFrame(frame, listenEpoch)).isSuccess) {
            mutableState.update { it.copy(packetsDropped = it.packetsDropped + 1) }
        }
    }

    private suspend fun playbackLoop() {
        var currentSession: Long? = null
        var currentSource = 0
        var expectedSequence = 0
        var startedPlayback = false
        val buffer = TreeMap<Int, EspMeshDecryptedVoiceFrame>()

        for (queued in playbackFrames) {
            if (queued.epoch != listenEpoch || !mutableState.value.listening) continue
            val frame = queued.frame
            val now = System.currentTimeMillis()
            val stale = !mutableState.value.receiving || now - playbackLastPacketAtMs > RECEIVE_TIMEOUT_MS
            if (currentSession == null || stale || (frame.isStart && frame.sessionId != currentSession)) {
                finishPlaybackSession()
                currentSession = frame.sessionId
                currentSource = frame.sourceTag
                expectedSequence = frame.sequence
                startedPlayback = false
                buffer.clear()
                mutableState.update { it.copy(receiving = true, activeSpeaker = "%08x".format(currentSource), lastVoiceAtMs = now) }
            }
            if (frame.sessionId != currentSession || frame.sourceTag != currentSource) {
                mutableState.update { it.copy(packetsDropped = it.packetsDropped + 1) }
                continue
            }
            if (frame.sequence < expectedSequence) continue
            buffer[frame.sequence] = frame

            if (!startedPlayback && (buffer.size >= JITTER_FRAMES || frame.isEnd)) {
                ensurePlaybackTrack()
                startedPlayback = true
            }
            if (!startedPlayback) continue

            while (buffer.isNotEmpty()) {
                val next = buffer[expectedSequence]
                if (next != null) {
                    buffer.remove(expectedSequence)
                    if (next.audioMuLaw.isNotEmpty()) playMuLaw(next.audioMuLaw)
                    expectedSequence++
                    if (next.isEnd) {
                        finishPlaybackSession()
                        currentSession = null
                        buffer.clear()
                        startedPlayback = false
                        break
                    }
                    continue
                }
                val first = buffer.firstKey()
                if (first > expectedSequence && buffer.size >= MAX_JITTER_FRAMES) {
                    // Do not retransmit old speech. Conceal a missing 20 ms frame with silence.
                    playSilence()
                    expectedSequence++
                    mutableState.update { it.copy(packetsDropped = it.packetsDropped + 1) }
                    continue
                }
                break
            }
        }
    }

    private fun ensurePlaybackTrack() {
        synchronized(playbackLock) {
            val existing = playbackTrack
            if (existing != null && existing.state == AudioTrack.STATE_INITIALIZED) return
            requestAudioFocus()
            val min = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(maxOf(min, FRAME_SAMPLES * 2 * 8))
                .build()
            require(track.state == AudioTrack.STATE_INITIALIZED) { "Could not initialise voice playback" }
            track.play()
            playbackTrack = track
        }
    }

    private fun playMuLaw(bytes: ByteArray) {
        if (!mutableState.value.listening) return
        val samples = EspMeshMuLawCodec.decode(bytes)
        val track = playbackTrack ?: return
        val written = track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
        if (written < samples.size) mutableState.update { it.copy(packetsDropped = it.packetsDropped + 1) }
    }

    private fun playSilence() {
        val track = playbackTrack ?: return
        val silence = ShortArray(FRAME_SAMPLES)
        track.write(silence, 0, silence.size, AudioTrack.WRITE_BLOCKING)
    }

    private fun stopPlaybackNow() {
        synchronized(playbackLock) {
            val track = playbackTrack
            playbackTrack = null
            runCatching { track?.pause() }
            runCatching { track?.flush() }
            runCatching { track?.release() }
            abandonAudioFocus()
        }
        mutableState.update { it.copy(receiving = false, activeSpeaker = "") }
    }

    private fun finishPlaybackSession() = stopPlaybackNow()

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= 26) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .build()
            audioFocusRequest = request
            audioManager?.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= 26) {
            audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(null)
        }
    }

    companion object {
        private const val PREFS = "methodmesh_espmesh_voice"
        private const val KEY_CHANNEL = "voice_channel"
        private const val DEFAULT_CHANNEL = "field-group"
        private const val SAMPLE_RATE = 8_000
        private const val FRAME_SAMPLES = 160
        private const val JITTER_FRAMES = 3
        private const val MAX_JITTER_FRAMES = 5
        private const val FLOOR_BUSY_MS = 650L
        private const val RECEIVE_TIMEOUT_MS = 900L
        @Volatile private var instance: EspMeshWalkieTalkieController? = null

        fun get(context: Context): EspMeshWalkieTalkieController = instance ?: synchronized(this) {
            instance ?: EspMeshWalkieTalkieController(context.applicationContext).also {
                instance = it
                it.start()
            }
        }

        private fun normalizeChannel(channel: String): String = channel.trim().lowercase().ifBlank { DEFAULT_CHANNEL }.take(64)
    }
}
