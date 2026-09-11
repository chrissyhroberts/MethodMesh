package com.example.methodmesh.modules.livestreamtranslate

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale
import kotlin.math.min

/** Platform Android recognizer provider. It chains short recognizer sessions to act like a meeting feed. */
internal class AndroidSpeechRecognitionProvider(
    context: Context,
    private val callbacks: LiveSpeechCallbacks
) : LiveSpeechRecognitionProvider {
    override val engine: LiveSpeechEngine = LiveSpeechEngine.ANDROID
    override val engineDetail: String
        get() = if (
            config?.preferOffline == true &&
            Build.VERSION.SDK_INT >= 31 &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)
        ) "on_device" else "platform_service"

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var config: LiveRecognitionConfig? = null
    private var running = false
    private var destroyed = false
    private var startGeneration = 0
    private var consecutiveErrors = 0

    override fun isPotentiallyAvailable(config: LiveRecognitionConfig): Boolean = isAvailable(appContext)

    override fun start(config: LiveRecognitionConfig) {
        val recognizerNeedsRecreate = this.config?.preferOffline != config.preferOffline
        this.config = config
        running = true
        consecutiveErrors = 0
        if (recognizerNeedsRecreate) {
            runCatching { recognizer?.destroy() }
            recognizer = null
        }
        ensureRecognizer(config)
        scheduleStart(0)
    }

    override fun pause() {
        running = false
        startGeneration += 1
        runCatching { recognizer?.cancel() }
    }

    override fun resume(config: LiveRecognitionConfig?) {
        val activeConfig = config ?: this.config ?: return
        start(activeConfig)
    }

    override fun stop() {
        running = false
        startGeneration += 1
        runCatching { recognizer?.cancel() }
    }

    override fun destroy() {
        destroyed = true
        running = false
        startGeneration += 1
        handler.removeCallbacksAndMessages(null)
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private fun ensureRecognizer(activeConfig: LiveRecognitionConfig) {
        if (recognizer != null || destroyed) return
        val created = if (
            activeConfig.preferOffline &&
            Build.VERSION.SDK_INT >= 31 &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)
        ) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
        } else {
            SpeechRecognizer.createSpeechRecognizer(appContext)
        }
        recognizer = created.also { speechRecognizer ->
            speechRecognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    callbacks.onReady()
                    callbacks.onStatus("Listening · Android…")
                }

                override fun onBeginningOfSpeech() {
                    callbacks.onSpeechStarted()
                    callbacks.onStatus("Hearing speech · Android…")
                }

                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() { callbacks.onStatus("Transcribing · Android…") }

                override fun onError(error: Int) {
                    if (!running || destroyed) return
                    val recoverable = error == SpeechRecognizer.ERROR_NO_MATCH ||
                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                        error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                        error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT ||
                        error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED
                    if (!recoverable) {
                        running = false
                        callbacks.onFatalError(errorMessage(error))
                        return
                    }
                    consecutiveErrors += 1
                    val backoff = when (error) {
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 900L
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> 1400L
                        else -> min(2500L, 220L * (1 shl min(consecutiveErrors, 3)))
                    }
                    callbacks.onStatus(
                        if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == SpeechRecognizer.ERROR_NO_MATCH)
                            "Listening · Android…" else "Android recognizer recovering…"
                    )
                    scheduleStart(backoff)
                }

                override fun onResults(results: Bundle?) {
                    if (!running || destroyed) return
                    consecutiveErrors = 0
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                        .trim()
                    val confidence = results
                        ?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                        ?.firstOrNull()
                        ?.takeIf { it >= 0f }
                    if (text.isNotBlank()) {
                        callbacks.onFinal(
                            LiveRecognitionResult(
                                text = text,
                                confidence = confidence,
                                engine = engine,
                                engineDetail = engineDetail
                            )
                        )
                    }
                    scheduleStart(220L)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    if (!running || destroyed) return
                    val text = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                        .trim()
                    callbacks.onPartial(text)
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit

                override fun onLanguageDetection(results: Bundle) {
                    if (Build.VERSION.SDK_INT < 34) return
                    val language = results.getString(SpeechRecognizer.DETECTED_LANGUAGE).orEmpty()
                    if (language.isBlank()) return
                    callbacks.onLanguage(
                        LiveDetectedLanguage(
                            languageTag = language,
                            confidenceLevel = results.getInt(
                                SpeechRecognizer.LANGUAGE_DETECTION_CONFIDENCE_LEVEL,
                                SpeechRecognizer.LANGUAGE_DETECTION_CONFIDENCE_LEVEL_UNKNOWN
                            ),
                            switchResult = results.getInt(
                                SpeechRecognizer.LANGUAGE_SWITCH_RESULT,
                                SpeechRecognizer.LANGUAGE_SWITCH_RESULT_NOT_ATTEMPTED
                            )
                        )
                    )
                }
            })
        }
    }

    private fun scheduleStart(delayMs: Long) {
        if (!running || destroyed) return
        val generation = ++startGeneration
        handler.postDelayed({
            if (!running || destroyed || generation != startGeneration) return@postDelayed
            val activeConfig = config ?: return@postDelayed
            val intent = recognitionIntent(activeConfig)
            runCatching { recognizer?.startListening(intent) }
                .onFailure {
                    consecutiveErrors += 1
                    if (consecutiveErrors >= 4) {
                        running = false
                        callbacks.onFatalError("Could not restart Android speech recognition: ${it.message.orEmpty().ifBlank { it.javaClass.simpleName }}")
                    } else {
                        scheduleStart(750L)
                    }
                }
        }, delayMs)
    }

    private fun recognitionIntent(config: LiveRecognitionConfig): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, config.preferOffline)
            if (Build.VERSION.SDK_INT >= 33) {
                putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING, RecognizerIntent.FORMATTING_OPTIMIZE_LATENCY)
                putExtra(RecognizerIntent.EXTRA_HIDE_PARTIAL_TRAILING_PUNCTUATION, true)
            }
            when (config.mode) {
                LiveStreamMode.FIXED -> {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, config.fixedSourceLocale)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, config.fixedSourceLocale)
                }
                LiveStreamMode.AUTO -> {
                    val initialLocale = config.allowedLanguageLocales.firstOrNull()
                        ?: Locale.getDefault().toLanguageTag()
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, initialLocale)
                    if (Build.VERSION.SDK_INT >= 34) {
                        putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true)
                        putExtra(
                            RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH,
                            when (config.switchSensitivity) {
                                "high_precision" -> RecognizerIntent.LANGUAGE_SWITCH_HIGH_PRECISION
                                "quick_response" -> RecognizerIntent.LANGUAGE_SWITCH_QUICK_RESPONSE
                                else -> RecognizerIntent.LANGUAGE_SWITCH_BALANCED
                            }
                        )
                        if (config.allowedLanguageLocales.isNotEmpty()) {
                            putStringArrayListExtra(
                                RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES,
                                ArrayList(config.allowedLanguageLocales)
                            )
                            putStringArrayListExtra(
                                RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES,
                                ArrayList(config.allowedLanguageLocales)
                            )
                        }
                    }
                }
            }
        }

    private fun errorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Android speech recognizer audio error."
        SpeechRecognizer.ERROR_CLIENT -> "Android speech recognizer client error."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required for live translation."
        SpeechRecognizer.ERROR_NETWORK -> "Android speech recognizer network error. Try offline recognition if an offline model is installed."
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Android speech recognizer network timeout."
        SpeechRecognizer.ERROR_NO_MATCH -> "Android speech recognizer could not match the speech."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Android speech recognizer is busy."
        SpeechRecognizer.ERROR_SERVER -> "Android speech recognizer service error."
        SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "Android speech recognizer service disconnected."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech was heard."
        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "Android speech recognizer is rate-limiting requests."
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "The selected speech-recognition language is not supported by the Android recognition service."
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "The selected Android speech-recognition language model is unavailable on this device."
        else -> "Android speech recognition failed (error $error)."
    }

    companion object {
        fun isAvailable(context: Context): Boolean = SpeechRecognizer.isRecognitionAvailable(context)
    }
}
