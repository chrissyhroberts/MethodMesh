package com.example.methodmesh.modules.livestreamtranslate

import android.content.Context
import android.os.Build
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.audio.AudioSource
import com.google.mlkit.genai.speechrecognition.SpeechRecognition
import com.google.mlkit.genai.speechrecognition.SpeechRecognizer
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerOptions
import com.google.mlkit.genai.speechrecognition.SpeechRecognizerResponse
import com.google.mlkit.genai.speechrecognition.speechRecognizerOptions
import com.google.mlkit.genai.speechrecognition.speechRecognizerRequest
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** ML Kit alpha streaming recognizer provider (Basic or Advanced/GenAI mode). */
internal class MlKitSpeechRecognitionProvider(
    context: Context,
    override val engine: LiveSpeechEngine,
    private val callbacks: LiveSpeechCallbacks
) : LiveSpeechRecognitionProvider {
    init {
        require(engine == LiveSpeechEngine.MLKIT_BASIC || engine == LiveSpeechEngine.MLKIT_GENAI)
    }

    override val engineDetail: String
        get() = if (engine == LiveSpeechEngine.MLKIT_GENAI) "advanced" else "basic"

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var recognizer: SpeechRecognizer? = null
    private var recognitionJob: Job? = null
    private var config: LiveRecognitionConfig? = null
    private var running = false
    private var destroyed = false
    private var utteranceActive = false

    override fun isPotentiallyAvailable(config: LiveRecognitionConfig): Boolean =
        Build.VERSION.SDK_INT >= 31 && config.mode == LiveStreamMode.FIXED

    override fun start(config: LiveRecognitionConfig) {
        this.config = config
        running = true
        destroyed = false
        utteranceActive = false
        recognitionJob?.cancel()
        closeRecognizer()
        recognizer = createRecognizer(config)
        recognitionJob = scope.launch { prepareAndRecognize() }
    }

    override fun pause() {
        running = false
        utteranceActive = false
        recognitionJob?.cancel()
        recognitionJob = null
        scope.launch { runCatching { recognizer?.stopRecognition() } }
    }

    override fun resume(config: LiveRecognitionConfig?) {
        start(config ?: this.config ?: return)
    }

    override fun stop() {
        running = false
        utteranceActive = false
        recognitionJob?.cancel()
        recognitionJob = null
        scope.launch { runCatching { recognizer?.stopRecognition() } }
    }

    override fun destroy() {
        destroyed = true
        running = false
        utteranceActive = false
        recognitionJob?.cancel()
        recognitionJob = null
        scope.launch { runCatching { recognizer?.stopRecognition() } }
        closeRecognizer()
        scope.cancel()
    }

    private fun createRecognizer(config: LiveRecognitionConfig): SpeechRecognizer {
        val locale = Locale.forLanguageTag(config.fixedSourceLocale)
        val mode = if (engine == LiveSpeechEngine.MLKIT_GENAI) {
            SpeechRecognizerOptions.Mode.MODE_ADVANCED
        } else {
            SpeechRecognizerOptions.Mode.MODE_BASIC
        }
        return SpeechRecognition.getClient(
            speechRecognizerOptions {
                this.locale = locale
                preferredMode = mode
            }
        )
    }

    private suspend fun prepareAndRecognize() {
        val client = recognizer ?: return
        if (Build.VERSION.SDK_INT < 31) {
            callbacks.onFatalError("ML Kit microphone speech recognition requires Android 12 (API 31) or later.")
            return
        }

        when (val status = runCatching { client.checkStatus() }.getOrElse {
            callbacks.onFatalError("Could not check ${engine.displayName} availability: ${it.message.orEmpty().ifBlank { it.javaClass.simpleName }}")
            return
        }) {
            FeatureStatus.AVAILABLE -> Unit
            FeatureStatus.DOWNLOADABLE -> {
                callbacks.onStatus("Downloading ${engine.displayName} speech model…")
                var completed = false
                var downloadFailure: String? = null
                client.download().collect { download ->
                    when (download) {
                        is DownloadStatus.DownloadStarted -> callbacks.onStatus(
                            "Downloading ${engine.displayName} speech model (${download.bytesToDownload / (1024 * 1024)} MB)…"
                        )
                        is DownloadStatus.DownloadProgress -> callbacks.onStatus(
                            "Downloading ${engine.displayName} speech model… ${download.totalBytesDownloaded / (1024 * 1024)} MB"
                        )
                        is DownloadStatus.DownloadCompleted -> completed = true
                        is DownloadStatus.DownloadFailed -> {
                            downloadFailure =
                                "${engine.displayName} model download failed: ${download.e.message.orEmpty().ifBlank { download.e.javaClass.simpleName }}"
                        }
                    }
                }
                if (downloadFailure != null) {
                    callbacks.onFatalError(downloadFailure!!)
                    return
                }
                if (!completed) {
                    callbacks.onFatalError("${engine.displayName} speech model was not prepared.")
                    return
                }
            }
            FeatureStatus.DOWNLOADING -> {
                callbacks.onStatus("${engine.displayName} speech model is already downloading…")
                var ready = false
                for (attempt in 0 until 90) {
                    delay(1000)
                    if (!running || destroyed) return
                    if (client.checkStatus() == FeatureStatus.AVAILABLE) {
                        ready = true
                        break
                    }
                }
                if (!ready) {
                    callbacks.onFatalError("${engine.displayName} speech model is still unavailable after download preparation.")
                    return
                }
            }
            FeatureStatus.UNAVAILABLE -> {
                callbacks.onFatalError("${engine.displayName} is not available for this locale/device.")
                return
            }
            else -> {
                callbacks.onFatalError("${engine.displayName} returned unsupported feature status $status.")
                return
            }
        }

        if (!running || destroyed) return
        callbacks.onReady()
        callbacks.onStatus("Listening · ${engine.displayName}…")
        recognizeContinuously(client)
    }

    private suspend fun recognizeContinuously(client: SpeechRecognizer) {
        while (running && !destroyed) {
            var completedNormally = false
            val request = speechRecognizerRequest { audioSource = AudioSource.fromMic() }
            val failure = runCatching {
                client.startRecognition(request).collect { response ->
                    if (!running || destroyed) return@collect
                    when (response) {
                        is SpeechRecognizerResponse.PartialTextResponse -> {
                            if (!utteranceActive && response.text.isNotBlank()) {
                                utteranceActive = true
                                callbacks.onSpeechStarted()
                            }
                            callbacks.onPartial(response.text.trim())
                        }
                        is SpeechRecognizerResponse.FinalTextResponse -> {
                            val text = response.text.trim()
                            if (text.isNotBlank()) {
                                callbacks.onFinal(
                                    LiveRecognitionResult(
                                        text = text,
                                        confidence = null,
                                        engine = engine,
                                        engineDetail = engineDetail
                                    )
                                )
                            }
                            utteranceActive = false
                            callbacks.onPartial("")
                        }
                        is SpeechRecognizerResponse.CompletedResponse -> {
                            utteranceActive = false
                            completedNormally = true
                        }
                        is SpeechRecognizerResponse.ErrorResponse -> {
                            utteranceActive = false
                            running = false
                            callbacks.onFatalError(
                                "${engine.displayName} failed: ${response.e.message.orEmpty().ifBlank { "error ${response.e.errorCode}" }}"
                            )
                        }
                    }
                }
            }.exceptionOrNull()

            if (failure != null && running && !destroyed) {
                running = false
                callbacks.onFatalError(
                    "${engine.displayName} recognition failed: ${failure.message.orEmpty().ifBlank { failure.javaClass.simpleName }}"
                )
                return
            }
            if (!running || destroyed) return
            if (completedNormally) {
                callbacks.onStatus("Restarting ${engine.displayName} stream…")
                delay(180)
            } else {
                return
            }
        }
    }

    private fun closeRecognizer() {
        runCatching { recognizer?.close() }
        recognizer = null
    }
}
