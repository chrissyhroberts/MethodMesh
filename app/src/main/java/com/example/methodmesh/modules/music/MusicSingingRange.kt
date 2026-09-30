package com.example.methodmesh.modules.music

import android.Manifest
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.core.methodmesh.ExecutionRequest
import com.example.methodmesh.core.methodmesh.InvocationContext
import com.example.methodmesh.core.methodmesh.ArchitectureId
import com.example.methodmesh.core.methodmesh.ArchitectureRef
import com.example.methodmesh.core.methodmesh.MethodContract
import com.example.methodmesh.core.methodmesh.MethodDescriptor
import com.example.methodmesh.core.methodmesh.MethodObjectType
import com.example.methodmesh.core.methodmesh.KnowledgeObjectType
import com.example.methodmesh.core.methodmesh.Signal
import com.example.methodmesh.core.methodmesh.TransformationStatus
import com.example.methodmesh.core.methodmesh.runtime.As100ExecutionEngine
import com.example.methodmesh.settings.SettingsState
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

object SingingRangeFields {
    const val RESULT = "music_singing_range_result"
    const val LOWEST_NOTE = "music_singing_range_lowest_note"
    const val HIGHEST_NOTE = "music_singing_range_highest_note"
    const val LOWEST_MIDI = "music_singing_range_lowest_midi"
    const val HIGHEST_MIDI = "music_singing_range_highest_midi"
    const val SEMITONE_SPAN = "music_singing_range_semitone_span"
    const val OCTAVE_SPAN = "music_singing_range_octave_span"
    const val LOWEST_FREQUENCY_HZ = "music_singing_range_lowest_frequency_hz"
    const val HIGHEST_FREQUENCY_HZ = "music_singing_range_highest_frequency_hz"
    const val OBSERVED_NOTES_JSON = "music_singing_range_observed_notes_json"
    const val SAMPLE_COUNT = "music_singing_range_sample_count"
    const val DURATION_MS = "music_singing_range_duration_ms"
    const val REFERENCE_A4_HZ = "music_singing_range_reference_a4_hz"
    const val STATUS = "music_singing_range_status"
    const val AUDIT_JSON = "music_singing_range_audit_json"
    const val ERROR = "music_singing_range_error"
    val outputs = listOf(RESULT, LOWEST_NOTE, HIGHEST_NOTE, LOWEST_MIDI, HIGHEST_MIDI, SEMITONE_SPAN, OCTAVE_SPAN, LOWEST_FREQUENCY_HZ, HIGHEST_FREQUENCY_HZ, OBSERVED_NOTES_JSON, SAMPLE_COUNT, DURATION_MS, REFERENCE_A4_HZ, STATUS, AUDIT_JSON, ERROR)
}

object As100SingingRangeMethod : As100Method {
    const val ID = "music.singing_range"
    const val VERSION = "0.1.0"
    private val fields = MusicFieldSet("music_singing_range", listOf("lowest_note", "highest_note", "lowest_midi", "highest_midi", "semitone_span", "octave_span", "lowest_frequency_hz", "highest_frequency_hz", "observed_notes_json", "sample_count", "duration_ms", "reference_a4_hz"))
    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Singing range")
    override val descriptor = MethodDescriptor(ArchitectureId(ID), MethodObjectType.SignalInterpreter, "Singing range", VERSION, "Listen continuously and return the lowest and highest confidence-qualified notes demonstrated by a singer.", inputs = listOf("reference_a4_hz", "minimum_confidence", "minimum_note_hold_ms"), outputs = SingingRangeFields.outputs, graphOutputs = listOf("music.singing.range"), parameters = mapOf("category" to "Music", "status" to "Development", "offline" to "true"))
    override val contract = MethodContract(ref, producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation), producedFields = descriptor.outputs, producedGraphOutputs = descriptor.graphOutputs)
    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) = MusicMethodSupport.request(action, ref, context, signals, inputs)
    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult = MusicMethodSupport.complete(request, InvocationContext.from(request.context), ID, VERSION, ref, fields, MusicMethodSupport.fail(fields, "Singing range requires the interactive microphone session."))
    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?) = MusicMethodSupport.complete(request, invocation, ID, VERSION, ref, fields, values)
}

internal data class SingingPitchFrame(val frequencyHz: Double, val confidence: Double, val timestampMs: Long)

internal class MusicPitchCaptureEngine(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)
    private var recorder: AudioRecord? = null
    private var worker: Thread? = null
    fun isRunning() = running.get()
    fun start(onFrame: (SingingPitchFrame) -> Unit, onError: (String) -> Unit) {
        if (!running.compareAndSet(false, true)) return
        val sampleRate = 48_000
        val size = 4096
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(size * 2)
        val record = runCatching { AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuffer * 2) }.getOrNull()
        if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
            record?.release(); running.set(false); handler.post { onError("No usable microphone input could be opened.") }; return
        }
        recorder = record
        worker = Thread({
            try {
                record.startRecording()
                val raw = ShortArray(size)
                while (running.get()) {
                    val read = record.read(raw, 0, raw.size, AudioRecord.READ_BLOCKING)
                    if (read < 128) continue
                    val samples = FloatArray(read) { raw[it] / 32768f }
                    val estimate = MusicPitchAlgorithms.estimate(samples, sampleRate)
                    estimate.first?.let { hz -> handler.post { if (running.get()) onFrame(SingingPitchFrame(hz, estimate.second, System.currentTimeMillis())) } }
                }
            } catch (_: SecurityException) { handler.post { onError("Microphone permission was denied or revoked.") } }
            catch (error: Throwable) { if (running.get()) handler.post { onError(error.message ?: "Microphone capture failed.") } }
            finally { runCatching { record.stop() }; record.release(); recorder = null; running.set(false) }
        }, "MethodMesh-Music-Range").also { it.start() }
    }
    fun stop() { running.set(false); runCatching { recorder?.stop() }; worker?.interrupt(); worker = null; recorder = null }
}

internal object MusicPitchAlgorithms {
    fun estimate(samples: FloatArray, sampleRate: Int): Pair<Double?, Double> {
        val minHz = 50.0
        val maxHz = 1200.0
        val maxTau = min(samples.size / 2 - 1, floor(sampleRate / minHz).toInt())
        val minTau = max(2, floor(sampleRate / maxHz).toInt())
        if (maxTau <= minTau + 1) return null to 0.0
        val window = samples.size - maxTau - 1
        val difference = DoubleArray(maxTau + 1)
        for (tau in 1..maxTau) { var sum = 0.0; for (i in 0 until window) { val d = samples[i].toDouble() - samples[i + tau].toDouble(); sum += d * d }; difference[tau] = sum }
        val cmnd = DoubleArray(maxTau + 1) { 1.0 }; var running = 0.0
        for (tau in 1..maxTau) { running += difference[tau]; cmnd[tau] = if (running <= 1e-12) 1.0 else difference[tau] * tau / running }
        var best = minTau
        for (tau in minTau + 1..maxTau) if (cmnd[tau] < cmnd[best]) best = tau
        if (cmnd[best] > 0.45) return null to (1.0 - cmnd[best]).coerceIn(0.0, 1.0)
        val left = cmnd.getOrElse(best - 1) { cmnd[best] }; val center = cmnd[best]; val right = cmnd.getOrElse(best + 1) { center }
        val denominator = left - 2.0 * center + right
        val refined = if (abs(denominator) < 1e-12) best.toDouble() else best + (0.5 * (left - right) / denominator).coerceIn(-1.0, 1.0)
        return (sampleRate / refined) to (1.0 - cmnd[best]).coerceIn(0.0, 1.0)
    }
}
