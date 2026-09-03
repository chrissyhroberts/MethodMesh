package com.example.methodmesh.modules.soundgenerator

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.roundToInt

/** Android audio boundary. Signal generation remains in [SoundSynthesis]. */
data class AudioOutputDevice(
    val id: Int,
    val name: String,
    val type: Int,
    val typeLabel: String,
    val address: String
) {
    val displayLabel: String
        get() = buildString {
            append(name.ifBlank { typeLabel })
            append(" · ")
            append(typeLabel)
            append(" · ID ")
            append(id)
        }
}

data class PlaybackSettings(
    val requestedDeviceId: String = "",
    val systemVolumePolicy: String = "preserve",
    val systemVolumePercent: Int = 50
)

data class PlaybackOutcome(
    val status: String,
    val startedTimeIso: String,
    val finishedTimeIso: String,
    val requestedDevice: String,
    val routedDevice: String,
    val routedDeviceId: String,
    val routedDeviceType: String,
    val mediaVolumeBefore: Int,
    val mediaVolumeDuring: Int,
    val mediaVolumeAfter: Int,
    val mediaVolumeMax: Int,
    val mediaVolumeTarget: Int,
    val framesWritten: Int,
    val framesPlayed: Int,
    val writtenPcmSha256: String,
    val audioFocusGranted: Boolean,
    val audioFocusInterrupted: Boolean,
    val preferredRouteAccepted: Boolean,
    val error: String = ""
)

class AndroidSoundPlayer(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val stopRequested = AtomicBoolean(false)
    private val userStopped = AtomicBoolean(false)
    private val focusInterrupted = AtomicBoolean(false)

    @Volatile private var currentTrack: AudioTrack? = null
    @Volatile private var playing = false

    fun isPlaying(): Boolean = playing

    fun currentMediaVolume(): Pair<Int, Int> {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) to max
    }

    fun outputDevices(): List<AudioOutputDevice> =
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .map { it.toSummary() }
            .sortedWith(compareBy<AudioOutputDevice> { it.typeLabel }.thenBy { it.name.lowercase() })

    fun stop() {
        userStopped.set(true)
        stopRequested.set(true)
        runCatching { currentTrack?.pause() }
        runCatching { currentTrack?.flush() }
    }

    fun play(
        spec: SoundSpec,
        settings: PlaybackSettings,
        onComplete: (RenderedSound?, PlaybackOutcome) -> Unit
    ) {
        if (playing) {
            onComplete(
                null,
                failedOutcome(
                    settings = settings,
                    error = "A sound is already playing."
                )
            )
            return
        }
        playing = true
        stopRequested.set(false)
        userStopped.set(false)
        focusInterrupted.set(false)

        thread(name = "MethodMeshSoundPlayback", isDaemon = true) {
            var rendered: RenderedSound? = null
            val outcome = runCatching {
                rendered = SoundSynthesis.render(spec)
                runPlayback(rendered!!, settings)
            }.getOrElse { error ->
                failedOutcome(settings = settings, error = error.message ?: error::class.java.simpleName)
            }
            playing = false
            currentTrack = null
            mainHandler.post { onComplete(rendered, outcome) }
        }
    }

    private fun runPlayback(rendered: RenderedSound, settings: PlaybackSettings): PlaybackOutcome {
        val started = Instant.now().toString()
        val stream = AudioManager.STREAM_MUSIC
        val maxVolume = audioManager.getStreamMaxVolume(stream).coerceAtLeast(1)
        val beforeVolume = audioManager.getStreamVolume(stream)
        val targetVolume = ((settings.systemVolumePercent.coerceIn(0, 100) / 100.0) * maxVolume).roundToInt().coerceIn(0, maxVolume)
        var duringVolume = beforeVolume
        var afterVolume = beforeVolume
        var audioFocusGranted = false
        var focusRequest: AudioFocusRequest? = null
        var preferredRouteAccepted = settings.requestedDeviceId.isBlank()
        var requestedDeviceLabel = "System default"
        var routedDeviceLabel = "Unknown"
        var routedDeviceId = ""
        var routedDeviceType = ""
        var framesWritten = 0
        var framesPlayed = 0
        val writtenDigest = MessageDigest.getInstance("SHA-256")
        var track: AudioTrack? = null
        var outcome: PlaybackOutcome? = null

        try {
            when (settings.systemVolumePolicy) {
                "preserve" -> Unit
                "require_percent" -> {
                    if (beforeVolume != targetVolume) {
                        error("Media volume is $beforeVolume/$maxVolume; this run requires $targetVolume/$maxVolume (${settings.systemVolumePercent}%).")
                    }
                }
                "temporary_set_percent" -> {
                    if (audioManager.isVolumeFixed) error("This device uses fixed system volume and cannot be set temporarily.")
                    audioManager.setStreamVolume(stream, targetVolume, 0)
                    duringVolume = audioManager.getStreamVolume(stream)
                    if (duringVolume != targetVolume) {
                        error("Android did not apply the requested media volume. Requested $targetVolume/$maxVolume; actual $duringVolume/$maxVolume.")
                    }
                }
                else -> error("Unknown system volume policy: ${settings.systemVolumePolicy}")
            }
            duringVolume = audioManager.getStreamVolume(stream)

            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                    focusInterrupted.set(true)
                    stopRequested.set(true)
                }
            }
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(attributes)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener(focusListener)
                .build()
            val focusResult = audioManager.requestAudioFocus(focusRequest)
            audioFocusGranted = focusResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            if (!audioFocusGranted) error("Exclusive transient audio focus was not granted.")

            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(rendered.spec.sampleRateHz)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build()
            val minBuffer = AudioTrack.getMinBufferSize(
                rendered.spec.sampleRateHz,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuffer <= 0) error("Android reported an invalid AudioTrack buffer size ($minBuffer).")
            val chunkFrames = 2048
            val chunkBytes = chunkFrames * rendered.channelCount * 2
            track = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(maxOf(minBuffer, chunkBytes * 4))
                .build()
            if (track.state != AudioTrack.STATE_INITIALIZED) error("AudioTrack could not be initialised.")
            currentTrack = track

            val requestedDevice = resolveRequestedDevice(settings.requestedDeviceId)
            if (settings.requestedDeviceId.isNotBlank()) {
                if (requestedDevice == null) error("Requested output device '${settings.requestedDeviceId}' is not currently available.")
                requestedDeviceLabel = requestedDevice.toSummary().displayLabel
                preferredRouteAccepted = track.setPreferredDevice(requestedDevice)
                if (!preferredRouteAccepted) error("Android rejected the requested audio output device.")
            }

            track.play()
            var offset = 0
            val totalShorts = rendered.pcm.size
            while (offset < totalShorts && !stopRequested.get()) {
                val count = minOf(chunkFrames * rendered.channelCount, totalShorts - offset)
                val written = track.write(rendered.pcm, offset, count, AudioTrack.WRITE_BLOCKING)
                if (written < 0) error("AudioTrack write failed with code $written.")
                if (written == 0) continue
                updateDigestWithShorts(writtenDigest, rendered.pcm, offset, written)
                offset += written
                framesWritten = offset / rendered.channelCount
                val routed = track.routedDevice
                if (routed != null) {
                    val summary = routed.toSummary()
                    routedDeviceLabel = summary.displayLabel
                    routedDeviceId = summary.id.toString()
                    routedDeviceType = summary.typeLabel
                }
            }

            if (!stopRequested.get()) {
                while (track.playbackHeadPosition < rendered.frameCount && !stopRequested.get()) {
                    Thread.sleep(10)
                }
            }
            framesPlayed = track.playbackHeadPosition.coerceAtMost(rendered.frameCount)

            val finalRoute = track.routedDevice
            if (finalRoute != null) {
                val summary = finalRoute.toSummary()
                routedDeviceLabel = summary.displayLabel
                routedDeviceId = summary.id.toString()
                routedDeviceType = summary.typeLabel
            }

            val status = when {
                focusInterrupted.get() -> "interrupted"
                userStopped.get() -> "stopped"
                framesPlayed >= rendered.frameCount -> "played"
                else -> "stopped"
            }
            outcome = PlaybackOutcome(
                status = status,
                startedTimeIso = started,
                finishedTimeIso = Instant.now().toString(),
                requestedDevice = requestedDeviceLabel,
                routedDevice = routedDeviceLabel,
                routedDeviceId = routedDeviceId,
                routedDeviceType = routedDeviceType,
                mediaVolumeBefore = beforeVolume,
                mediaVolumeDuring = duringVolume,
                mediaVolumeAfter = afterVolume,
                mediaVolumeMax = maxVolume,
                mediaVolumeTarget = targetVolume,
                framesWritten = framesWritten,
                framesPlayed = framesPlayed,
                writtenPcmSha256 = writtenDigest.digest().toHex(),
                audioFocusGranted = audioFocusGranted,
                audioFocusInterrupted = focusInterrupted.get(),
                preferredRouteAccepted = preferredRouteAccepted,
                error = ""
            )
        } catch (error: Throwable) {
            framesPlayed = runCatching { track?.playbackHeadPosition ?: 0 }.getOrDefault(0)
            outcome = PlaybackOutcome(
                status = "failed",
                startedTimeIso = started,
                finishedTimeIso = Instant.now().toString(),
                requestedDevice = requestedDeviceLabel,
                routedDevice = routedDeviceLabel,
                routedDeviceId = routedDeviceId,
                routedDeviceType = routedDeviceType,
                mediaVolumeBefore = beforeVolume,
                mediaVolumeDuring = duringVolume,
                mediaVolumeAfter = afterVolume,
                mediaVolumeMax = maxVolume,
                mediaVolumeTarget = targetVolume,
                framesWritten = framesWritten,
                framesPlayed = framesPlayed,
                writtenPcmSha256 = writtenDigest.digest().toHex(),
                audioFocusGranted = audioFocusGranted,
                audioFocusInterrupted = focusInterrupted.get(),
                preferredRouteAccepted = preferredRouteAccepted,
                error = error.message ?: error::class.java.simpleName
            )
        } finally {
            runCatching { track?.stop() }
            runCatching { track?.flush() }
            runCatching { track?.release() }
            if (focusRequest != null) runCatching { audioManager.abandonAudioFocusRequest(focusRequest) }
            if (settings.systemVolumePolicy == "temporary_set_percent") {
                runCatching { audioManager.setStreamVolume(stream, beforeVolume, 0) }
            }
            afterVolume = runCatching { audioManager.getStreamVolume(stream) }.getOrDefault(beforeVolume)
        }
        return requireNotNull(outcome).copy(
            mediaVolumeAfter = afterVolume,
            finishedTimeIso = Instant.now().toString()
        )
    }

    private fun resolveRequestedDevice(raw: String): AudioDeviceInfo? {
        val value = raw.trim()
        if (value.isBlank() || value.equals("default", true)) return null
        val id = value.toIntOrNull()
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { device ->
            (id != null && device.id == id) ||
                device.productName?.toString()?.equals(value, true) == true ||
                device.safeAddress().equals(value, true)
        }
    }

    private fun failedOutcome(settings: PlaybackSettings, error: String): PlaybackOutcome {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        return PlaybackOutcome(
            status = "failed",
            startedTimeIso = Instant.now().toString(),
            finishedTimeIso = Instant.now().toString(),
            requestedDevice = settings.requestedDeviceId.ifBlank { "System default" },
            routedDevice = "Unknown",
            routedDeviceId = "",
            routedDeviceType = "",
            mediaVolumeBefore = current,
            mediaVolumeDuring = current,
            mediaVolumeAfter = current,
            mediaVolumeMax = max,
            mediaVolumeTarget = ((settings.systemVolumePercent.coerceIn(0, 100) / 100.0) * max).roundToInt(),
            framesWritten = 0,
            framesPlayed = 0,
            writtenPcmSha256 = "",
            audioFocusGranted = false,
            audioFocusInterrupted = false,
            preferredRouteAccepted = false,
            error = error
        )
    }
}


private fun AudioDeviceInfo.safeAddress(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) address.orEmpty() else ""

private fun AudioDeviceInfo.toSummary(): AudioOutputDevice = AudioOutputDevice(
    id = id,
    name = productName?.toString().orEmpty(),
    type = type,
    typeLabel = audioDeviceTypeLabel(type),
    address = safeAddress()
)

private fun audioDeviceTypeLabel(type: Int): String = when (type) {
    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Built-in earpiece"
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Built-in speaker"
    AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headset"
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired headphones"
    AudioDeviceInfo.TYPE_LINE_ANALOG -> "Analog line"
    AudioDeviceInfo.TYPE_LINE_DIGITAL -> "Digital line"
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO"
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth A2DP"
    AudioDeviceInfo.TYPE_HDMI -> "HDMI"
    AudioDeviceInfo.TYPE_USB_DEVICE -> "USB audio device"
    AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB accessory"
    AudioDeviceInfo.TYPE_DOCK -> "Dock"
    AudioDeviceInfo.TYPE_FM -> "FM"
    AudioDeviceInfo.TYPE_AUX_LINE -> "Aux line"
    AudioDeviceInfo.TYPE_IP -> "IP audio"
    AudioDeviceInfo.TYPE_BUS -> "Audio bus"
    AudioDeviceInfo.TYPE_USB_HEADSET -> "USB headset"
    AudioDeviceInfo.TYPE_HEARING_AID -> "Hearing aid"
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE -> "Safe speaker"
    AudioDeviceInfo.TYPE_BLE_HEADSET -> "BLE headset"
    AudioDeviceInfo.TYPE_BLE_SPEAKER -> "BLE speaker"
    AudioDeviceInfo.TYPE_BLE_BROADCAST -> "BLE broadcast"
    else -> "Audio device type $type"
}

private fun updateDigestWithShorts(digest: MessageDigest, values: ShortArray, offset: Int, count: Int) {
    val bytes = ByteArray(count * 2)
    var out = 0
    for (i in offset until offset + count) {
        val value = values[i].toInt()
        bytes[out++] = (value and 0xff).toByte()
        bytes[out++] = ((value ushr 8) and 0xff).toByte()
    }
    digest.update(bytes)
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
