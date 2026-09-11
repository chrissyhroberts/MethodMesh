package com.example.methodmesh.modules.livestreamtranslate

import android.content.Context
import android.os.Build

internal enum class LiveSpeechEngine(val wireValue: String, val displayName: String) {
    AUTO("auto", "Automatic"),
    ANDROID("android", "Android"),
    MLKIT_BASIC("mlkit_basic", "ML Kit Basic"),
    MLKIT_GENAI("mlkit_genai", "ML Kit GenAI");

    companion object {
        fun fromWire(value: String?): LiveSpeechEngine = entries.firstOrNull { it.wireValue == value } ?: AUTO
    }
}

internal data class LiveRecognitionConfig(
    val mode: LiveStreamMode,
    val fixedSourceLocale: String,
    val allowedLanguageLocales: List<String>,
    val preferOffline: Boolean,
    val switchSensitivity: String,
    val enginePreference: LiveSpeechEngine
)

internal data class LiveDetectedLanguage(
    val languageTag: String,
    val confidenceLevel: Int,
    val switchResult: Int
)

internal data class LiveRecognitionResult(
    val text: String,
    val confidence: Float?,
    val engine: LiveSpeechEngine,
    val engineDetail: String
)

internal data class LiveSpeechCallbacks(
    val onReady: () -> Unit,
    val onSpeechStarted: () -> Unit,
    val onPartial: (String) -> Unit,
    val onFinal: (LiveRecognitionResult) -> Unit,
    val onLanguage: (LiveDetectedLanguage) -> Unit,
    val onStatus: (String) -> Unit,
    val onFatalError: (String) -> Unit
)

internal interface LiveSpeechRecognitionProvider {
    val engine: LiveSpeechEngine
    val engineDetail: String

    fun isPotentiallyAvailable(config: LiveRecognitionConfig): Boolean
    fun start(config: LiveRecognitionConfig)
    fun pause()
    fun resume(config: LiveRecognitionConfig? = null)
    fun stop()
    fun destroy()
}

internal enum class LiveEngineSwitchResult {
    SWITCHED,
    QUEUED,
    UNCHANGED,
    UNSUPPORTED
}

/**
 * Session-level speech recognition controller.
 *
 * The meeting owns this object; concrete recognizers are replaceable underneath it.
 * Engine changes are immediate while idle, or queued until the current utterance has
 * emitted a final result. This prevents a hot-switch from duplicating/truncating an
 * utterance while keeping the session/feed itself continuous.
 */
internal class LiveMeetingSpeechRecognizer(
    context: Context,
    private val onReady: () -> Unit,
    private val onSpeechStarted: () -> Unit,
    private val onPartial: (String) -> Unit,
    private val onFinal: (LiveRecognitionResult) -> Unit,
    private val onLanguage: (LiveDetectedLanguage) -> Unit,
    private val onStatus: (String) -> Unit,
    private val onEngineChanged: (LiveSpeechEngine, String) -> Unit,
    private val onEngineSwitchQueued: (LiveSpeechEngine) -> Unit,
    private val onFatalError: (String) -> Unit
) {
    private val appContext = context.applicationContext
    private var provider: LiveSpeechRecognitionProvider? = null
    private var config: LiveRecognitionConfig? = null
    private var running = false
    private var speechActive = false
    private var pendingEngine: LiveSpeechEngine? = null
    private var autoFallbackIndex = 0

    private val providerCallbacks = LiveSpeechCallbacks(
        onReady = {
            speechActive = false
            provider?.let { onEngineChanged(it.engine, it.engineDetail) }
            onReady()
        },
        onSpeechStarted = {
            speechActive = true
            onSpeechStarted()
        },
        onPartial = {
            if (it.isNotBlank()) speechActive = true
            onPartial(it)
        },
        onFinal = { result ->
            onFinal(result)
            speechActive = false
            val pending = pendingEngine
            if (pending != null) {
                pendingEngine = null
                activateRequestedEngine(pending, "Hot-switched after utterance")
            }
        },
        onLanguage = onLanguage,
        onStatus = onStatus,
        onFatalError = { message -> handleProviderFailure(message) }
    )

    fun isAvailable(newConfig: LiveRecognitionConfig): Boolean {
        val candidates = candidatesFor(newConfig.enginePreference, newConfig)
        return candidates.any { engine -> potentialAvailability(engine, newConfig) }
    }

    fun start(newConfig: LiveRecognitionConfig) {
        config = newConfig
        running = true
        speechActive = false
        pendingEngine = null
        autoFallbackIndex = 0
        activateRequestedEngine(newConfig.enginePreference, "Started")
    }

    fun pause() {
        running = false
        speechActive = false
        pendingEngine = null
        provider?.pause()
        onStatus("Paused")
    }

    fun resume(newConfig: LiveRecognitionConfig? = null) {
        val activeConfig = newConfig ?: config ?: return
        config = activeConfig
        running = true
        speechActive = false
        pendingEngine = null
        autoFallbackIndex = 0
        activateRequestedEngine(activeConfig.enginePreference, "Resumed")
    }

    fun requestEngine(engine: LiveSpeechEngine): LiveEngineSwitchResult {
        val current = config ?: return LiveEngineSwitchResult.UNSUPPORTED

        if (current.mode == LiveStreamMode.AUTO && engine !in setOf(LiveSpeechEngine.AUTO, LiveSpeechEngine.ANDROID)) {
            onStatus("${engine.displayName} uses a fixed recognition locale and cannot provide automatic language switching. Use Automatic or Android in detect-language mode.")
            return LiveEngineSwitchResult.UNSUPPORTED
        }

        val updated = current.copy(enginePreference = engine)
        config = updated
        val resolvedNow = resolveInitialEngine(engine, updated)
        if (provider?.engine == resolvedNow && engine != LiveSpeechEngine.AUTO) {
            return LiveEngineSwitchResult.UNCHANGED
        }

        if (speechActive) {
            pendingEngine = engine
            onEngineSwitchQueued(engine)
            onStatus("Switching recognition to ${engine.displayName} after this utterance…")
            return LiveEngineSwitchResult.QUEUED
        }

        autoFallbackIndex = 0
        activateRequestedEngine(engine, "Hot-switched")
        return LiveEngineSwitchResult.SWITCHED
    }

    fun stop() {
        running = false
        speechActive = false
        pendingEngine = null
        provider?.stop()
        onStatus("Stopped")
    }

    fun destroy() {
        running = false
        speechActive = false
        pendingEngine = null
        provider?.destroy()
        provider = null
    }

    fun activeEngine(): LiveSpeechEngine? = provider?.engine
    fun activeEngineDetail(): String = provider?.engineDetail.orEmpty()

    private fun activateRequestedEngine(requested: LiveSpeechEngine, reason: String) {
        val activeConfig = config ?: return
        if (!running) return

        val candidates = candidatesFor(requested, activeConfig)
        autoFallbackIndex = 0
        val engine = candidates.firstOrNull { potentialAvailability(it, activeConfig) }
        if (engine == null) {
            running = false
            onFatalError("No compatible speech-recognition engine is available for this mode on this device.")
            return
        }
        activateConcreteEngine(engine, activeConfig, reason)
    }

    private fun activateConcreteEngine(engine: LiveSpeechEngine, activeConfig: LiveRecognitionConfig, reason: String) {
        if (!running) return
        provider?.stop()
        provider?.destroy()
        provider = createProvider(engine)
        speechActive = false
        onStatus("${reason}: preparing ${engine.displayName}…")
        provider?.start(activeConfig)
    }

    private fun handleProviderFailure(message: String) {
        val activeConfig = config ?: run {
            onFatalError(message)
            return
        }
        speechActive = false

        if (activeConfig.enginePreference == LiveSpeechEngine.AUTO) {
            val candidates = candidatesFor(LiveSpeechEngine.AUTO, activeConfig)
            val active = provider?.engine
            val currentIndex = candidates.indexOf(active).takeIf { it >= 0 } ?: autoFallbackIndex
            val next = candidates.drop(currentIndex + 1).firstOrNull { potentialAvailability(it, activeConfig) }
            if (next != null && running) {
                autoFallbackIndex = candidates.indexOf(next)
                onStatus("${active?.displayName ?: "Recognizer"} unavailable (${message.take(100)}). Falling back to ${next.displayName}…")
                activateConcreteEngine(next, activeConfig, "Automatic fallback")
                return
            }
        }

        running = false
        onFatalError(message)
    }

    private fun createProvider(engine: LiveSpeechEngine): LiveSpeechRecognitionProvider = when (engine) {
        LiveSpeechEngine.ANDROID -> AndroidSpeechRecognitionProvider(appContext, providerCallbacks)
        LiveSpeechEngine.MLKIT_BASIC -> MlKitSpeechRecognitionProvider(appContext, LiveSpeechEngine.MLKIT_BASIC, providerCallbacks)
        LiveSpeechEngine.MLKIT_GENAI -> MlKitSpeechRecognitionProvider(appContext, LiveSpeechEngine.MLKIT_GENAI, providerCallbacks)
        LiveSpeechEngine.AUTO -> error("AUTO must be resolved before provider creation")
    }

    private fun candidatesFor(requested: LiveSpeechEngine, activeConfig: LiveRecognitionConfig): List<LiveSpeechEngine> {
        if (activeConfig.mode == LiveStreamMode.AUTO) return listOf(LiveSpeechEngine.ANDROID)
        return when (requested) {
            LiveSpeechEngine.AUTO -> listOf(
                LiveSpeechEngine.MLKIT_GENAI,
                LiveSpeechEngine.MLKIT_BASIC,
                LiveSpeechEngine.ANDROID
            )
            else -> listOf(requested)
        }
    }

    private fun resolveInitialEngine(requested: LiveSpeechEngine, activeConfig: LiveRecognitionConfig): LiveSpeechEngine =
        candidatesFor(requested, activeConfig).firstOrNull { potentialAvailability(it, activeConfig) }
            ?: LiveSpeechEngine.ANDROID

    private fun potentialAvailability(engine: LiveSpeechEngine, activeConfig: LiveRecognitionConfig): Boolean = when (engine) {
        LiveSpeechEngine.AUTO -> false
        LiveSpeechEngine.ANDROID -> AndroidSpeechRecognitionProvider.isAvailable(appContext) &&
            (activeConfig.mode != LiveStreamMode.AUTO || Build.VERSION.SDK_INT >= 34)
        LiveSpeechEngine.MLKIT_BASIC,
        LiveSpeechEngine.MLKIT_GENAI -> activeConfig.mode == LiveStreamMode.FIXED && Build.VERSION.SDK_INT >= 31
    }
}
