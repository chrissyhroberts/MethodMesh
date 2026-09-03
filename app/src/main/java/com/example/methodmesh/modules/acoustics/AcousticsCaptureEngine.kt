package com.example.methodmesh.modules.acoustics

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

/** One microphone capture session. It does not persist raw audio. */
class AcousticsCaptureEngine(private val appContext: Context) {

    data class CaptureInfo(
        val sampleRateHz: Int,
        val frameSizeSamples: Int,
        val audioSource: Int,
        val audioSourceLabel: String,
        val unprocessedAdvertised: Boolean,
        val unprocessedUsed: Boolean
    )

    data class AcousticFrame(
        val timestampMs: Long,
        val frequencyHz: Double?,
        val pitchConfidence: Double,
        val rms: Double,
        val peak: Double,
        val dbfs: Double,
        val peakDbfs: Double,
        val waveform: FloatArray,
        val spectrumDb: FloatArray
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)
    private var audioRecord: AudioRecord? = null
    private var worker: Thread? = null

    fun isRunning(): Boolean = running.get()

    fun start(
        sampleRateHz: Int = 48_000,
        frameSizeSamples: Int = 4096,
        minFrequencyHz: Double = 40.0,
        maxFrequencyHz: Double = 5000.0,
        onInfo: (CaptureInfo) -> Unit,
        onFrame: (AcousticFrame) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!running.compareAndSet(false, true)) return

        val frameSize = nextPowerOfTwo(frameSizeSamples.coerceIn(1024, 8192))
        val unprocessedAdvertised = supportsUnprocessedAudio(appContext)
        val candidates = buildList {
            if (unprocessedAdvertised && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) add(MediaRecorder.AudioSource.UNPROCESSED)
            add(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            add(MediaRecorder.AudioSource.MIC)
        }.distinct()

        val recordAndSource = runCatching {
            candidates.firstNotNullOfOrNull { source ->
                createRecorder(source, sampleRateHz, frameSize)?.let { it to source }
            } ?: error("No usable microphone input could be opened.")
        }.getOrElse { error ->
            running.set(false)
            mainHandler.post { onError(error.message ?: "Unable to open microphone input.") }
            return
        }

        val record = recordAndSource.first
        val source = recordAndSource.second
        audioRecord = record
        mainHandler.post {
            onInfo(
                CaptureInfo(
                    sampleRateHz = sampleRateHz,
                    frameSizeSamples = frameSize,
                    audioSource = source,
                    audioSourceLabel = audioSourceLabel(source),
                    unprocessedAdvertised = unprocessedAdvertised,
                    unprocessedUsed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && source == MediaRecorder.AudioSource.UNPROCESSED
                )
            )
        }

        worker = Thread({
            try {
                record.startRecording()
                val shorts = ShortArray(frameSize)
                while (running.get()) {
                    val read = record.read(shorts, 0, shorts.size, AudioRecord.READ_BLOCKING)
                    if (read <= 0) {
                        if (read < 0) throw IllegalStateException("Microphone read failed with AudioRecord code $read.")
                        continue
                    }
                    if (read < 64) continue

                    val samples = FloatArray(read)
                    var i = 0
                    while (i < read) {
                        samples[i] = shorts[i] / 32768.0f
                        i++
                    }
                    val rms = AcousticsAlgorithms.rms(samples)
                    val peak = AcousticsAlgorithms.peak(samples)
                    val pitch = AcousticsAlgorithms.estimatePitchYin(
                        samples = samples,
                        sampleRateHz = sampleRateHz,
                        minFrequencyHz = minFrequencyHz,
                        maxFrequencyHz = maxFrequencyHz
                    )
                    val frame = AcousticFrame(
                        timestampMs = System.currentTimeMillis(),
                        frequencyHz = pitch.frequencyHz,
                        pitchConfidence = pitch.confidence,
                        rms = rms,
                        peak = peak,
                        dbfs = AcousticsAlgorithms.dbfsFromAmplitude(rms),
                        peakDbfs = AcousticsAlgorithms.dbfsFromAmplitude(peak),
                        waveform = AcousticsAlgorithms.waveformForDisplay(samples, 256),
                        spectrumDb = AcousticsAlgorithms.spectrumDb(samples, 256)
                    )
                    mainHandler.post { if (running.get()) onFrame(frame) }
                }
            } catch (security: SecurityException) {
                mainHandler.post { onError("Microphone permission was denied or revoked.") }
            } catch (error: Throwable) {
                if (running.get()) mainHandler.post { onError(error.message ?: "Microphone capture failed.") }
            } finally {
                runCatching { record.stop() }
                record.release()
                audioRecord = null
                running.set(false)
            }
        }, "MethodMesh-Acoustics").also { it.start() }
    }

    fun stop() {
        running.set(false)
        runCatching { audioRecord?.stop() }
        worker?.interrupt()
        worker = null
        audioRecord = null
    }

    private fun createRecorder(source: Int, sampleRateHz: Int, frameSizeSamples: Int): AudioRecord? {
        val minBufferBytes = AudioRecord.getMinBufferSize(
            sampleRateHz,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufferBytes <= 0) return null
        val bufferBytes = max(minBufferBytes, frameSizeSamples * 2 * 2)

        val record = runCatching {
            AudioRecord.Builder()
                .setAudioSource(source)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRateHz)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferBytes)
                .build()
        }.getOrNull() ?: return null

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return null
        }
        return record
    }

    private fun supportsUnprocessedAudio(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        return manager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)
            ?.equals("true", ignoreCase = true) == true
    }

    private fun audioSourceLabel(source: Int): String = when (source) {
        MediaRecorder.AudioSource.UNPROCESSED -> "UNPROCESSED"
        MediaRecorder.AudioSource.VOICE_RECOGNITION -> "VOICE_RECOGNITION"
        MediaRecorder.AudioSource.MIC -> "MIC"
        else -> source.toString()
    }

    private fun nextPowerOfTwo(value: Int): Int {
        var n = 1
        while (n < value) n = n shl 1
        return n
    }
}
