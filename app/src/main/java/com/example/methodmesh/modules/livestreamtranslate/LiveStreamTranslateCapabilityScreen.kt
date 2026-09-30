package com.example.methodmesh.modules.livestreamtranslate

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.platform.translation.MlKitLanguageCatalog
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

object LiveStreamTranslateFixedCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100LiveStreamTranslateFixedMethod.ID
    override val title = "Live translation · fixed language"
    override val description = "Robust meeting mode: listen continuously in one selected source language and translate into your target language."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) = LiveStreamTranslateScreen(
        mode = LiveStreamMode.FIXED,
        context = context,
        onBack = onBack,
        onConfirmed = onConfirmed,
        onCancel = onCancel
    )
}

object LiveStreamTranslateAutoCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100LiveStreamTranslateAutoMethod.ID
    override val title = "Live translation · detect language"
    override val description = "Meeting mode: detect/switch the current spoken language and translate it into your target language."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) = LiveStreamTranslateScreen(
        mode = LiveStreamMode.AUTO,
        context = context,
        onBack = onBack,
        onConfirmed = onConfirmed,
        onCancel = onCancel
    )
}

object LiveStreamTranslateStreamingCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100LiveStreamTranslateStreamingMethod.ID
    override val title = "Live translation · streaming"
    override val description = "Experimental low-latency mode: translate ML Kit's continuously revised speech hypothesis while speech is still in progress."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) = LiveStreamTranslateScreen(
        mode = LiveStreamMode.STREAMING,
        context = context,
        onBack = onBack,
        onConfirmed = onConfirmed,
        onCancel = onCancel
    )
}

private data class LiveTranslationSegment(
    val id: String,
    val timeIso: String,
    val sourceLanguage: String,
    val sourceLanguageTag: String,
    val targetLanguage: String,
    val originalText: String,
    val translatedText: String,
    val recognitionConfidence: Float?,
    val recognitionEngine: String,
    val recognitionEngineDetail: String,
    val languageConfidence: Int?,
    val translationState: String,
    val error: String,
    val recorded: Boolean
)

@Composable
private fun LiveStreamTranslateScreen(
    mode: LiveStreamMode,
    context: CapabilityScreenContext,
    onBack: () -> Unit,
    onConfirmed: (ExecutionResult) -> Unit,
    onCancel: () -> Unit
) {
    val androidContext = LocalContext.current
    val defaultSource = if (mode != LiveStreamMode.AUTO) "fr" else ""
    var sourceLanguage by rememberSaveable {
        mutableStateOf(
            MlKitLanguageCatalog.canonicalCode(
                context.action.settings["source_language"] ?: context.action.settings["input_source_language"] ?: defaultSource,
                defaultSource.ifBlank { "en" }
            )
        )
    }
    var targetLanguage by rememberSaveable {
        mutableStateOf(
            MlKitLanguageCatalog.canonicalCode(
                context.action.settings["target_language"] ?: context.action.settings["input_target_language"] ?: "en",
                "en"
            )
        )
    }
    var allowedLanguagesText by rememberSaveable {
        mutableStateOf(context.action.settings["allowed_languages"] ?: context.action.settings["input_allowed_languages"] ?: "")
    }
    var switchSensitivity by rememberSaveable {
        mutableStateOf(context.action.settings["switch_sensitivity"] ?: context.action.settings["input_switch_sensitivity"] ?: "balanced")
    }
    var streamResponse by rememberSaveable {
        mutableStateOf(context.action.settings["stream_response"] ?: context.action.settings["input_stream_response"] ?: "balanced")
    }
    var speechEnginePreference by rememberSaveable {
        mutableStateOf(
            LiveSpeechEngine.fromWire(
                context.action.settings["speech_engine"] ?: context.action.settings["input_speech_engine"] ?: "auto"
            ).wireValue
        )
    }
    var activeSpeechEngine by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
    var activeSpeechEngineDetail by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
    var engineSwitchCount by rememberSaveable(context.action.canonicalId) { mutableStateOf(0) }
    var preferOffline by rememberSaveable {
        mutableStateOf((context.action.settings["prefer_offline"] ?: context.action.settings["input_prefer_offline"] ?: "false").equals("true", true))
    }
    var transcriptEnabled by rememberSaveable {
        mutableStateOf((context.action.settings["transcript_on_start"] ?: context.action.settings["input_transcript_on_start"] ?: "true").equals("true", true))
    }
    var segmentsJson by rememberSaveable(context.action.canonicalId) { mutableStateOf("[]") }
    var transcriptMarkersJson by rememberSaveable(context.action.canonicalId) { mutableStateOf("[]") }
    var partialText by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
    var streamTranslatedText by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
    var streamWorkingTranslating by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
    var currentDetectedLanguageTag by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
    var currentLanguageConfidence by rememberSaveable(context.action.canonicalId) { mutableStateOf<Int?>(null) }
    var lastDetectedLanguage by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
    var status by rememberSaveable(context.action.canonicalId) { mutableStateOf("Ready.") }
    var modelStatus by rememberSaveable(context.action.canonicalId) { mutableStateOf("Language models not checked yet.") }
    var liveOpen by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
    var running by remember(context.action.canonicalId) { mutableStateOf(false) }
    var pausedByUser by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
    var startedAt by rememberSaveable(context.action.canonicalId) { mutableStateOf(Instant.now().toString()) }
    var resultValuesJson by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<ExecutionResult?>(null) }
    var modelReady by remember { mutableStateOf(false) }
    var modelPreparing by remember { mutableStateOf(false) }
    var pendingTranslations by remember { mutableStateOf(0) }
    var finishRequested by remember { mutableStateOf(false) }
    var fatalError by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
    var showOdkCard by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
    var pendingOpenAfterPermission by rememberSaveable { mutableStateOf(false) }
    var hasAudioPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(androidContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }

    val runtimeSettingsVisible = when (mode) {
        LiveStreamMode.FIXED -> listOf("source_language", "target_language", "speech_engine", "prefer_offline", "transcript_on_start")
        LiveStreamMode.AUTO -> listOf("target_language", "allowed_languages", "switch_sensitivity", "speech_engine", "prefer_offline", "transcript_on_start")
        LiveStreamMode.STREAMING -> listOf("source_language", "target_language", "speech_engine", "stream_response", "transcript_on_start")
    }.any(context::settingIsRuntimeInput)

    LaunchedEffect(Unit) {
        if (context.startsImmediately && !runtimeSettingsVisible) liveOpen = true
    }

    val allowedLanguageCodes = remember(allowedLanguagesText) {
        allowedLanguagesText
            .split(',', ';', ' ', '\n', '\t')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { raw -> canonicalSpeechLanguage(raw) }
            .filter { it in MlKitLanguageCatalog.supportedCodes() }
            .distinct()
    }
    val allowedSpeechLocales = remember(allowedLanguageCodes) {
        allowedLanguageCodes.map { code -> LiveStreamLanguageSupport.defaultSpeechLocaleTag(code) }
    }

    val translationEngine = remember { LiveStreamTranslationEngine() }
    val streamingPipeline = remember {
        StreamingTranslationPipeline(
            translationEngine = translationEngine,
            onProjection = { projection ->
                if (mode == LiveStreamMode.STREAMING) {
                    when {
                        projection.sourceText.isBlank() -> streamTranslatedText = ""
                        projection.translatedText.isNotBlank() -> streamTranslatedText = projection.translatedText
                    }
                    streamWorkingTranslating = projection.translating
                }
            },
            onStatus = { message -> if (mode == LiveStreamMode.STREAMING) status = message }
        )
    }

    fun recognitionConfig() = LiveRecognitionConfig(
        mode = mode,
        fixedSourceLocale = when (mode) {
            LiveStreamMode.AUTO -> ""
            LiveStreamMode.STREAMING -> LiveStreamLanguageSupport.streamingSpeechLocaleTag(sourceLanguage)
            LiveStreamMode.FIXED -> LiveStreamLanguageSupport.defaultSpeechLocaleTag(sourceLanguage)
        },
        allowedLanguageLocales = allowedSpeechLocales,
        preferOffline = preferOffline,
        switchSensitivity = switchSensitivity,
        enginePreference = LiveSpeechEngine.fromWire(speechEnginePreference)
    )

    fun appendMarker(type: String, note: String) {
        transcriptMarkersJson = appendMarkerJson(transcriptMarkersJson, type, Instant.now().toString(), note)
    }

    fun recordTranscriptChange(enabled: Boolean) {
        if (enabled == transcriptEnabled) return
        transcriptEnabled = enabled
        appendMarker(if (enabled) "resume" else "pause", if (enabled) "Transcript recording resumed" else "Transcript recording paused")
    }

    fun appendRecognizedSegment(recognition: LiveRecognitionResult) {
        val text = recognition.text
        val detectionMissing = mode == LiveStreamMode.AUTO && currentDetectedLanguageTag.isBlank()
        val sourceTag = when (mode) {
            LiveStreamMode.FIXED -> LiveStreamLanguageSupport.defaultSpeechLocaleTag(sourceLanguage)
            LiveStreamMode.STREAMING -> LiveStreamLanguageSupport.streamingSpeechLocaleTag(sourceLanguage)
            LiveStreamMode.AUTO -> if (detectionMissing) "" else currentDetectedLanguageTag
        }
        val sourceCode = when {
            mode != LiveStreamMode.AUTO -> sourceLanguage
            detectionMissing -> ""
            else -> canonicalSpeechLanguage(sourceTag)
        }
        val sourceSupported = sourceCode in MlKitLanguageCatalog.supportedCodes()
        val segmentError = when {
            detectionMissing -> "The active recognizer returned text but did not report a detected language. Use fixed-language mode or Android language switching in detect-language mode."
            !sourceSupported -> "Detected language '${sourceTag.ifBlank { sourceCode }}' is not supported by the on-device ML Kit translation set."
            else -> ""
        }
        val state = when {
            segmentError.isNotBlank() -> "error"
            sourceCode == targetLanguage -> "ready"
            else -> "translating"
        }
        val id = "seg-${System.currentTimeMillis()}-${segmentsFromJson(segmentsJson).size + 1}"
        val segment = LiveTranslationSegment(
            id = id,
            timeIso = Instant.now().toString(),
            sourceLanguage = sourceCode,
            sourceLanguageTag = sourceTag,
            targetLanguage = targetLanguage,
            originalText = text,
            translatedText = if (sourceCode == targetLanguage) text else "",
            recognitionConfidence = recognition.confidence,
            recognitionEngine = recognition.engine.wireValue,
            recognitionEngineDetail = recognition.engineDetail,
            languageConfidence = currentLanguageConfidence,
            translationState = state,
            error = segmentError,
            recorded = transcriptEnabled
        )
        segmentsJson = appendSegmentJson(segmentsJson, segment)
        partialText = ""
        if (sourceCode.isNotBlank()) lastDetectedLanguage = sourceCode
        currentDetectedLanguageTag = ""
        currentLanguageConfidence = null

        if (segmentError.isNotBlank()) {
            status = segmentError
            return
        }
        if (sourceCode == targetLanguage) {
            status = "Listening…"
            return
        }
        pendingTranslations += 1
        translationEngine.translate(sourceCode, targetLanguage, text) { translated ->
            translated
                .onSuccess { translatedText ->
                    segmentsJson = updateSegmentJson(segmentsJson, id) {
                        it.copy(translatedText = translatedText, translationState = "ready", error = "")
                    }
                    if (!finishRequested) status = "Listening…"
                }
                .onFailure { error ->
                    segmentsJson = updateSegmentJson(segmentsJson, id) {
                        it.copy(
                            translationState = "error",
                            error = error.message.orEmpty().ifBlank { error.javaClass.simpleName }
                        )
                    }
                    if (!finishRequested) status = "Translation failed for ${languageLabel(sourceCode)}. The source text is preserved."
                }
            pendingTranslations = (pendingTranslations - 1).coerceAtLeast(0)
        }
    }

    val recognizer = remember {
        LiveMeetingSpeechRecognizer(
            context = androidContext,
            onReady = {
                running = true
                status = "Listening…"
            },
            onSpeechStarted = {
                partialText = ""
                streamTranslatedText = ""
                streamWorkingTranslating = false
                if (mode == LiveStreamMode.STREAMING) streamingPipeline.clearWorkingProjection()
                currentDetectedLanguageTag = ""
                currentLanguageConfidence = null
            },
            onPartial = { text ->
                partialText = text
                if (mode == LiveStreamMode.STREAMING) {
                    streamingPipeline.submit(
                        sourceLanguage = sourceLanguage,
                        targetLanguage = targetLanguage,
                        sourceText = text,
                        responseDelayMs = streamingResponseDelayMs(streamResponse)
                    )
                }
            },
            onFinal = { recognition ->
                if (mode == LiveStreamMode.STREAMING) {
                    streamingPipeline.clearWorkingProjection()
                    streamTranslatedText = ""
                    streamWorkingTranslating = false
                }
                appendRecognizedSegment(recognition)
            },
            onLanguage = { detected ->
                currentDetectedLanguageTag = detected.languageTag
                currentLanguageConfidence = detected.confidenceLevel
                lastDetectedLanguage = canonicalSpeechLanguage(detected.languageTag)
                status = "${languageLabel(lastDetectedLanguage)} detected · listening…"
            },
            onStatus = { status = it },
            onEngineChanged = { engine, detail ->
                if (activeSpeechEngine.isNotBlank() && activeSpeechEngine != engine.wireValue) engineSwitchCount += 1
                activeSpeechEngine = engine.wireValue
                activeSpeechEngineDetail = detail
            },
            onEngineSwitchQueued = { engine -> status = "Switching recognition to ${engine.displayName} after this utterance…" },
            onFatalError = {
                fatalError = it
                status = it
                running = false
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            recognizer.destroy()
            streamingPipeline.close()
            translationEngine.close()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasAudioPermission = granted
        if (granted && pendingOpenAfterPermission) {
            pendingOpenAfterPermission = false
            liveOpen = true
        } else if (!granted) {
            pendingOpenAfterPermission = false
            liveOpen = false
            status = "Microphone permission is required for live translation."
        }
    }

    LaunchedEffect(liveOpen, hasAudioPermission) {
        if (liveOpen && !hasAudioPermission) {
            pendingOpenAfterPermission = true
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun openLive() {
        fatalError = ""
        if (!recognizer.isAvailable(recognitionConfig())) {
            status = "No compatible speech-recognition engine is available for these settings on this device."
            fatalError = status
            return
        }
        if (mode == LiveStreamMode.AUTO && Build.VERSION.SDK_INT < 34) {
            status = "Automatic language switching requires Android 14 or later. Use fixed-language mode on this device."
            fatalError = status
            return
        }
        if (!hasAudioPermission) {
            pendingOpenAfterPermission = true
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        liveOpen = true
    }

    fun prepareModelsAndStart() {
        if (modelPreparing || running || !liveOpen || !hasAudioPermission) return
        if (!recognizer.isAvailable(recognitionConfig())) {
            fatalError = "No compatible speech-recognition engine is available for these settings on this device."
            status = fatalError
            return
        }
        if (mode == LiveStreamMode.AUTO && Build.VERSION.SDK_INT < 34) {
            fatalError = "Automatic language switching requires Android 14 or later. Use fixed-language mode on this device."
            status = fatalError
            return
        }
        modelPreparing = true
        modelReady = false
        modelStatus = when (mode) {
            LiveStreamMode.FIXED, LiveStreamMode.STREAMING -> "Preparing ${languageLabel(sourceLanguage)} and ${languageLabel(targetLanguage)} translation models…"
            LiveStreamMode.AUTO -> "Preparing ${languageLabel(targetLanguage)} translation model…"
        }
        val onPrepared: (Result<Unit>) -> Unit = { prepared ->
            modelPreparing = false
            prepared.onSuccess {
                modelReady = true
                modelStatus = "Translation models ready."
                if (!pausedByUser && liveOpen) {
                    recognizer.start(recognitionConfig())
                    running = true
                    status = "Listening…"
                }
            }.onFailure {
                modelReady = false
                fatalError = "Could not prepare translation model: ${it.message.orEmpty().ifBlank { it.javaClass.simpleName }}"
                modelStatus = fatalError
                status = fatalError
            }
        }
        if (mode != LiveStreamMode.AUTO) {
            translationEngine.ensurePair(sourceLanguage, targetLanguage, onPrepared)
        } else {
            translationEngine.ensureModel(targetLanguage, onPrepared)
        }
    }

    LaunchedEffect(liveOpen, hasAudioPermission, sourceLanguage, targetLanguage, mode) {
        if (liveOpen && hasAudioPermission) {
            modelReady = false
            prepareModelsAndStart()
        }
    }

    LaunchedEffect(sourceLanguage, targetLanguage, allowedLanguagesText, switchSensitivity, streamResponse, speechEnginePreference, preferOffline, transcriptEnabled) {
        context.onSettingsChanged(
            buildMap {
                if (mode != LiveStreamMode.AUTO) put("source_language", sourceLanguage)
                put("target_language", targetLanguage)
                if (mode == LiveStreamMode.AUTO) {
                    put("allowed_languages", allowedLanguagesText)
                    put("switch_sensitivity", switchSensitivity)
                }
                if (mode == LiveStreamMode.STREAMING) put("stream_response", streamResponse)
                put("speech_engine", speechEnginePreference)
                if (mode != LiveStreamMode.STREAMING) put("prefer_offline", preferOffline.toString())
                put("transcript_on_start", transcriptEnabled.toString())
            }
        )
    }

    fun finalizeSession(state: String = "succeeded", error: String = "") {
        recognizer.stop()
        running = false
        partialText = ""
        streamTranslatedText = ""
        streamWorkingTranslating = false
        if (mode == LiveStreamMode.STREAMING) streamingPipeline.clearWorkingProjection()
        val values = liveTranslationValues(
            mode = mode,
            allSegmentsJson = segmentsJson,
            markersJson = transcriptMarkersJson,
            transcriptEnabledAtEnd = transcriptEnabled,
            sourceLanguage = sourceLanguage,
            targetLanguage = targetLanguage,
            allowedLanguages = allowedLanguageCodes,
            switchSensitivity = switchSensitivity,
            speechEngineRequested = speechEnginePreference,
            lastSpeechEngine = activeSpeechEngine,
            engineSwitchCount = engineSwitchCount,
            preferOffline = preferOffline,
            lastDetectedLanguage = lastDetectedLanguage,
            startedAt = startedAt,
            status = state,
            error = error
        )
        val request = when (mode) {
            LiveStreamMode.FIXED -> As100LiveStreamTranslateFixedMethod.request(
                action = As100LiveStreamTranslateFixedMethod.ID,
                context = context.request.invocationContext.asMap(As100LiveStreamTranslateFixedMethod.ID) + context.action.settings,
                signals = emptyList(), inputs = emptyList()
            )
            LiveStreamMode.AUTO -> As100LiveStreamTranslateAutoMethod.request(
                action = As100LiveStreamTranslateAutoMethod.ID,
                context = context.request.invocationContext.asMap(As100LiveStreamTranslateAutoMethod.ID) + context.action.settings,
                signals = emptyList(), inputs = emptyList()
            )
            LiveStreamMode.STREAMING -> As100LiveStreamTranslateStreamingMethod.request(
                action = As100LiveStreamTranslateStreamingMethod.ID,
                context = context.request.invocationContext.asMap(As100LiveStreamTranslateStreamingMethod.ID) + context.action.settings,
                signals = emptyList(), inputs = emptyList()
            )
        }
        val execution = when (mode) {
            LiveStreamMode.FIXED -> As100LiveStreamTranslateFixedMethod.result(request, values, context.request.invocationContext)
            LiveStreamMode.AUTO -> As100LiveStreamTranslateAutoMethod.result(request, values, context.request.invocationContext)
            LiveStreamMode.STREAMING -> As100LiveStreamTranslateStreamingMethod.result(request, values, context.request.invocationContext)
        }
        result = execution
        resultValuesJson = valuesToJson(values)
        liveOpen = false
        status = if (state == "succeeded") "Live translation ready to commit." else error.ifBlank { "Live translation failed." }
        if (context.submitsImmediately && state == "succeeded") onConfirmed(execution)
    }

    fun requestFinish() {
        recognizer.stop()
        running = false
        pausedByUser = true
        partialText = ""
        if (mode == LiveStreamMode.STREAMING) streamingPipeline.clearWorkingProjection()
        if (pendingTranslations == 0) {
            finalizeSession()
        } else {
            finishRequested = true
            status = "Finishing ${pendingTranslations} translation${if (pendingTranslations == 1) "" else "s"}…"
        }
    }

    LaunchedEffect(finishRequested, pendingTranslations) {
        if (finishRequested && pendingTranslations == 0) {
            finishRequested = false
            finalizeSession()
        }
    }

    val capturedResult = result ?: remember(resultValuesJson) {
        resultValuesJson?.let(::valuesFromJson)?.let { values ->
            val request = when (mode) {
                LiveStreamMode.FIXED -> As100LiveStreamTranslateFixedMethod.request(
                    action = As100LiveStreamTranslateFixedMethod.ID,
                    context = context.request.invocationContext.asMap(As100LiveStreamTranslateFixedMethod.ID) + context.action.settings + values,
                    signals = emptyList(), inputs = emptyList()
                )
                LiveStreamMode.AUTO -> As100LiveStreamTranslateAutoMethod.request(
                    action = As100LiveStreamTranslateAutoMethod.ID,
                    context = context.request.invocationContext.asMap(As100LiveStreamTranslateAutoMethod.ID) + context.action.settings + values,
                    signals = emptyList(), inputs = emptyList()
                )
                LiveStreamMode.STREAMING -> As100LiveStreamTranslateStreamingMethod.request(
                    action = As100LiveStreamTranslateStreamingMethod.ID,
                    context = context.request.invocationContext.asMap(As100LiveStreamTranslateStreamingMethod.ID) + context.action.settings + values,
                    signals = emptyList(), inputs = emptyList()
                )
            }
            when (mode) {
                LiveStreamMode.FIXED -> As100LiveStreamTranslateFixedMethod.result(request, values, context.request.invocationContext)
                LiveStreamMode.AUTO -> As100LiveStreamTranslateAutoMethod.result(request, values, context.request.invocationContext)
                LiveStreamMode.STREAMING -> As100LiveStreamTranslateStreamingMethod.result(request, values, context.request.invocationContext)
            }
        }
    }

    CapabilityScreenScaffold(
        title = when (mode) {
            LiveStreamMode.FIXED -> "Live translation · fixed language"
            LiveStreamMode.AUTO -> "Live translation · detect language"
            LiveStreamMode.STREAMING -> "Live translation · streaming"
        },
        capabilityId = when (mode) {
            LiveStreamMode.FIXED -> As100LiveStreamTranslateFixedMethod.ID
            LiveStreamMode.AUTO -> As100LiveStreamTranslateAutoMethod.ID
            LiveStreamMode.STREAMING -> As100LiveStreamTranslateStreamingMethod.ID
        },
        context = context,
        canGoBack = context.stepNumber > 1,
        capturedResult = capturedResult,
        resultPreview = capturedResult?.let { OutputFormatter.fields(it, includeProvenance = false) }
            ?.filterKeys { key -> key !in setOf(LiveStreamTranslateFields.SEGMENTS_JSON) }
            .orEmpty(),
        onBack = onBack,
        onRetry = {
            result = null
            resultValuesJson = null
            segmentsJson = "[]"
            transcriptMarkersJson = "[]"
            partialText = ""
            streamTranslatedText = ""
            streamWorkingTranslating = false
            if (mode == LiveStreamMode.STREAMING) streamingPipeline.clearWorkingProjection()
            currentDetectedLanguageTag = ""
            currentLanguageConfidence = null
            lastDetectedLanguage = ""
            activeSpeechEngine = ""
            activeSpeechEngineDetail = ""
            engineSwitchCount = 0
            fatalError = ""
            startedAt = Instant.now().toString()
            running = false
            pausedByUser = false
            pendingTranslations = 0
            finishRequested = false
            status = "Ready."
            liveOpen = context.startsImmediately && !runtimeSettingsVisible
        },
        onConfirm = { capturedResult?.let(onConfirmed) },
        onCancel = onCancel
    ) {
        if (capturedResult == null) {
            Column(Modifier.fillMaxWidth()) {
                if (mode != LiveStreamMode.AUTO && !context.settingIsFixedInNativePreset("source_language")) {
                    LanguageSelector(
                        label = "Source language",
                        selected = sourceLanguage,
                        choices = if (mode == LiveStreamMode.STREAMING) LiveStreamLanguageSupport.streamingSpeechLanguageCodes else MlKitLanguageCatalog.supportedCodes().toList()
                    ) { sourceLanguage = it }
                    Spacer(Modifier.height(10.dp))
                }
                if (!context.settingIsFixedInNativePreset("target_language")) {
                    LanguageSelector("Translate into", targetLanguage) { targetLanguage = it }
                    Spacer(Modifier.height(10.dp))
                }
                if (mode == LiveStreamMode.AUTO) {
                    if (!context.settingIsFixedInNativePreset("allowed_languages")) {
                        OutlinedTextField(
                            value = allowedLanguagesText,
                            onValueChange = { allowedLanguagesText = it },
                            label = { Text("Likely spoken languages · optional") },
                            supportingText = { Text("Comma-separated codes, e.g. en,fr,es. A smaller set improves switching accuracy when supported.") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    if (!context.settingIsFixedInNativePreset("switch_sensitivity")) {
                        ChoiceSelector(
                            label = "Switch sensitivity",
                            selected = switchSensitivity,
                            choices = listOf("high_precision", "balanced", "quick_response"),
                            labels = mapOf(
                                "high_precision" to "High precision",
                                "balanced" to "Balanced",
                                "quick_response" to "Quick response"
                            ),
                            onSelected = { switchSensitivity = it }
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    if (Build.VERSION.SDK_INT < 34) {
                        WarningCard("This device is below Android 14. Automatic language switching cannot be requested from SpeechRecognizer; use fixed-language mode.")
                        Spacer(Modifier.height(10.dp))
                    }
                }
                if (mode == LiveStreamMode.STREAMING && !context.settingIsFixedInNativePreset("stream_response")) {
                    ChoiceSelector(
                        label = "Streaming response",
                        selected = streamResponse,
                        choices = listOf("fast", "balanced", "stable"),
                        labels = mapOf("fast" to "Fast", "balanced" to "Balanced", "stable" to "Stable"),
                        onSelected = { streamResponse = it }
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Partial speech is working state: MethodMesh translates the newest revisable hypothesis, discards stale translation callbacks, and commits only final recognition segments.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                }
                if (!context.settingIsFixedInNativePreset("speech_engine")) {
                    ChoiceSelector(
                        label = "Speech recognition",
                        selected = speechEnginePreference,
                        choices = speechEngineChoices(mode).map { it.wireValue },
                        labels = speechEngineChoices(mode).associate { it.wireValue to it.displayName },
                        onSelected = { speechEnginePreference = it }
                    )
                    Spacer(Modifier.height(10.dp))
                }
                if (mode != LiveStreamMode.STREAMING && !context.settingIsFixedInNativePreset("prefer_offline")) {
                    ToggleRow("Prefer offline Android recognition", preferOffline) { preferOffline = it }
                }
                if (!context.settingIsFixedInNativePreset("transcript_on_start")) {
                    ToggleRow("Record transcript at start", transcriptEnabled) { transcriptEnabled = it }
                }
                OutlinedButton(onClick = { showOdkCard = !showOdkCard }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (showOdkCard) "Hide ODK integration" else "ODK integration")
                }
                if (showOdkCard) {
                    Spacer(Modifier.height(8.dp))
                    LiveStreamTranslateOdkIntegrationCard(mode)
                }
                Spacer(Modifier.height(14.dp))
                Button(onClick = ::openLive, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Text(if (mode == LiveStreamMode.STREAMING) "Start streaming translation" else "Start live translation")
                }
                Spacer(Modifier.height(8.dp))
                if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodySmall)
                if (fatalError.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    WarningCard(fatalError)
                }
                val previousSegments = segmentsFromJson(segmentsJson)
                if (previousSegments.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Text("Current session feed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    previousSegments.takeLast(5).forEach { segment ->
                        FeedSegmentCard(segment = segment, compact = true)
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }
    }

    if (capturedResult == null && liveOpen) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                LiveMeetingSurface(
                    mode = mode,
                    sourceLanguage = sourceLanguage,
                    targetLanguage = targetLanguage,
                    allowedLanguageCodes = allowedLanguageCodes,
                    segments = segmentsFromJson(segmentsJson),
                    partialText = partialText,
                    streamTranslatedText = streamTranslatedText,
                    streamWorkingTranslating = streamWorkingTranslating,
                    streamResponse = streamResponse,
                    currentDetectedLanguageTag = currentDetectedLanguageTag,
                    speechEnginePreference = speechEnginePreference,
                    activeSpeechEngine = activeSpeechEngine,
                    activeSpeechEngineDetail = activeSpeechEngineDetail,
                    status = status,
                    modelStatus = modelStatus,
                    modelPreparing = modelPreparing,
                    modelReady = modelReady,
                    running = running,
                    transcriptEnabled = transcriptEnabled,
                    fatalError = fatalError,
                    finishing = finishRequested,
                    onTranscriptChanged = ::recordTranscriptChange,
                    onSpeechEngineSelected = { selected ->
                        speechEnginePreference = selected.wireValue
                        recognizer.requestEngine(selected)
                    },
                    onPauseResume = {
                        if (!finishRequested) {
                            if (running) {
                                recognizer.pause()
                                running = false
                                pausedByUser = true
                                partialText = ""
                                if (mode == LiveStreamMode.STREAMING) streamingPipeline.clearWorkingProjection()
                            } else if (modelReady) {
                                pausedByUser = false
                                fatalError = ""
                                recognizer.resume(recognitionConfig())
                                running = true
                            } else {
                                pausedByUser = false
                                prepareModelsAndStart()
                            }
                        }
                    },
                    onEnd = { requestFinish() },
                    onBackToSetup = {
                        if (!finishRequested) {
                            recognizer.stop()
                            running = false
                            pausedByUser = false
                            if (mode == LiveStreamMode.STREAMING) streamingPipeline.clearWorkingProjection()
                            liveOpen = false
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun LiveMeetingSurface(
    mode: LiveStreamMode,
    sourceLanguage: String,
    targetLanguage: String,
    allowedLanguageCodes: List<String>,
    segments: List<LiveTranslationSegment>,
    partialText: String,
    streamTranslatedText: String,
    streamWorkingTranslating: Boolean,
    streamResponse: String,
    currentDetectedLanguageTag: String,
    speechEnginePreference: String,
    activeSpeechEngine: String,
    activeSpeechEngineDetail: String,
    status: String,
    modelStatus: String,
    modelPreparing: Boolean,
    modelReady: Boolean,
    running: Boolean,
    transcriptEnabled: Boolean,
    fatalError: String,
    finishing: Boolean,
    onTranscriptChanged: (Boolean) -> Unit,
    onSpeechEngineSelected: (LiveSpeechEngine) -> Unit,
    onPauseResume: () -> Unit,
    onEnd: () -> Unit,
    onBackToSetup: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.large
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (mode == LiveStreamMode.AUTO) "Detect language → ${languageLabel(targetLanguage)}" else "${languageLabel(sourceLanguage)} → ${languageLabel(targetLanguage)}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (mode == LiveStreamMode.AUTO && allowedLanguageCodes.isNotEmpty()) {
                            Text(
                                "Expected: ${allowedLanguageCodes.joinToString { languageLabel(it) }}",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Text(if (running) "● LIVE" else "○ PAUSED", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(6.dp))
                Text(status, style = MaterialTheme.typography.bodyMedium)
                if (modelPreparing) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Spacer(Modifier.height(4.dp))
                    Text(modelStatus, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        EngineSwitcher(
            mode = mode,
            selected = LiveSpeechEngine.fromWire(speechEnginePreference),
            active = LiveSpeechEngine.fromWire(activeSpeechEngine.ifBlank { speechEnginePreference }),
            detail = activeSpeechEngineDetail,
            enabled = !finishing && !modelPreparing,
            onSelected = onSpeechEngineSelected
        )


        Spacer(Modifier.height(8.dp))
        if (mode != LiveStreamMode.STREAMING && (partialText.isNotBlank() || currentDetectedLanguageTag.isNotBlank())) {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        if (currentDetectedLanguageTag.isNotBlank()) "Hearing ${languageLabel(canonicalSpeechLanguage(currentDetectedLanguageTag))}…" else "Hearing…",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (partialText.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(partialText, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (mode == LiveStreamMode.STREAMING) {
                StreamingTranscriptFeed(
                    segments = segments,
                    partialText = partialText,
                    streamTranslatedText = streamTranslatedText,
                    streamWorkingTranslating = streamWorkingTranslating,
                    streamResponse = streamResponse
                )
            } else if (segments.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("The translated meeting feed will appear here.", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Completed utterances are translated; partial speech stays at the top while someone is talking.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(segments.asReversed(), key = { it.id }) { segment ->
                        FeedSegmentCard(segment)
                    }
                }
            }
        }

        if (fatalError.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            WarningCard(fatalError)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { onTranscriptChanged(!transcriptEnabled) }, modifier = Modifier.weight(1f)) {
                Text(if (transcriptEnabled) "Transcript ON" else "Transcript OFF")
            }
            OutlinedButton(onClick = onPauseResume, modifier = Modifier.weight(1f), enabled = !modelPreparing && !finishing) {
                Text(if (running) "Pause" else if (finishing) "Finishing…" else "Resume")
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onEnd, modifier = Modifier.fillMaxWidth().height(50.dp), enabled = !finishing) {
            Text(if (finishing) "Finishing translations…" else "End session")
        }
        Spacer(Modifier.height(6.dp))
        OutlinedButton(onClick = onBackToSetup, modifier = Modifier.fillMaxWidth(), enabled = !finishing) {
            Text("Back to setup")
        }
    }
}

@Composable
private fun StreamingTranscriptFeed(
    segments: List<LiveTranslationSegment>,
    partialText: String,
    streamTranslatedText: String,
    streamWorkingTranslating: Boolean,
    streamResponse: String
) {
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()
    var followTail by rememberSaveable { mutableStateOf(true) }
    val workingText = streamTranslatedText.ifBlank { partialText }
    val hasWorkingLine = partialText.isNotBlank() || streamTranslatedText.isNotBlank()
    val totalItems = segments.size + if (hasWorkingLine) 1 else 0

    val userScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) {
                    followTail = false
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(totalItems, workingText, streamWorkingTranslating, followTail) {
        if (followTail && totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Transcript",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                streamResponse.uppercase(Locale.ROOT),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!followTail) {
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { followTail = true },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                ) {
                    Text("Follow live ↓", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        if (totalItems == 0) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Listening for speech…", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Translation will accumulate here from top to bottom.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().nestedScroll(userScrollConnection).padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(segments, key = { it.id }) { segment ->
                    val translated = segment.translatedText.ifBlank { segment.originalText }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (translated.isNotBlank()) clipboard.setText(AnnotatedString(translated))
                            }
                    ) {
                        Text(
                            translated,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (segment.translationState == "error") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground
                        )
                        if (segment.translationState == "translating") {
                            Text(
                                "translating…",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (hasWorkingLine) {
                    item(key = "working-stream-line") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    streamTranslatedText.takeIf { it.isNotBlank() }?.let {
                                        clipboard.setText(AnnotatedString(it))
                                    }
                                }
                                .padding(bottom = 8.dp)
                        ) {
                            Text(
                                workingText.ifBlank { "…" },
                                style = MaterialTheme.typography.bodyLarge,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.86f)
                            )
                            if (streamWorkingTranslating) {
                                Text(
                                    "updating…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedSegmentCard(segment: LiveTranslationSegment, compact: Boolean = false) {
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    Card(
        modifier = Modifier.fillMaxWidth().clickable {
            val copyText = segment.translatedText.ifBlank { segment.originalText }
            if (copyText.isNotBlank()) clipboard.setText(AnnotatedString(copyText))
        },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
    ) {
        Column(Modifier.padding(if (compact) 10.dp else 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    buildString {
                        append(languageLabel(segment.sourceLanguage)).append(" → ").append(languageLabel(segment.targetLanguage))
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(shortTime(segment.timeIso), style = MaterialTheme.typography.labelSmall)
            }
            if (segment.recognitionEngine.isNotBlank()) {
                Text(
                    "Recognition · ${speechEngineLabel(segment.recognitionEngine)}${segment.recognitionEngineDetail.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(5.dp))
            Text(segment.originalText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            when (segment.translationState) {
                "translating" -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Spacer(Modifier.height(4.dp))
                    Text("Translating…", style = MaterialTheme.typography.bodySmall)
                }
                "error" -> {
                    Text("Translation unavailable", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (segment.error.isNotBlank()) Text(segment.error, style = MaterialTheme.typography.bodySmall)
                }
                else -> Text(
                    segment.translatedText.ifBlank { segment.originalText },
                    style = if (compact) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (!compact) {
                Spacer(Modifier.height(5.dp))
                Text("Tap card to copy translation", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun WarningCard(message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth()) {
        Text(message, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(10.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun LanguageSelector(
    label: String,
    selected: String,
    choices: List<String> = MlKitLanguageCatalog.supportedCodes().toList(),
    onSelected: (String) -> Unit
) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label · ${languageLabel(selected)}")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            choices
                .sortedBy { languageLabel(it).lowercase(Locale.ROOT) }
                .forEach { code ->
                    DropdownMenuItem(
                        text = { Text(languageLabel(code)) },
                        onClick = { onSelected(code); open = false }
                    )
                }
        }
    }
}

private fun speechEngineChoices(mode: LiveStreamMode): List<LiveSpeechEngine> = when (mode) {
    LiveStreamMode.FIXED -> listOf(
        LiveSpeechEngine.AUTO,
        LiveSpeechEngine.ANDROID,
        LiveSpeechEngine.MLKIT_BASIC,
        LiveSpeechEngine.MLKIT_GENAI
    )
    LiveStreamMode.AUTO -> listOf(LiveSpeechEngine.AUTO, LiveSpeechEngine.ANDROID)
    LiveStreamMode.STREAMING -> listOf(LiveSpeechEngine.AUTO, LiveSpeechEngine.MLKIT_BASIC, LiveSpeechEngine.MLKIT_GENAI)
}

private fun speechEngineLabel(wireValue: String): String =
    LiveSpeechEngine.fromWire(wireValue).displayName

@Composable
private fun EngineSwitcher(
    mode: LiveStreamMode,
    selected: LiveSpeechEngine,
    active: LiveSpeechEngine,
    detail: String,
    enabled: Boolean,
    onSelected: (LiveSpeechEngine) -> Unit
) {
    var open by rememberSaveable { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text("Recognition", style = MaterialTheme.typography.labelMedium)
                Text(
                    "${active.displayName}${detail.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (selected == LiveSpeechEngine.AUTO) {
                    Text("Automatic routing", style = MaterialTheme.typography.labelSmall)
                }
            }
            Box {
                OutlinedButton(onClick = { open = true }, enabled = enabled) {
                    Text(if (selected == LiveSpeechEngine.AUTO) "Auto" else "Change")
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    speechEngineChoices(mode).forEach { engine ->
                        DropdownMenuItem(
                            text = { Text(engine.displayName) },
                            onClick = {
                                open = false
                                onSelected(engine)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceSelector(
    label: String,
    selected: String,
    choices: List<String>,
    labels: Map<String, String> = emptyMap(),
    onSelected: (String) -> Unit
) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label · ${labels[selected] ?: selected}")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(labels[choice] ?: choice) },
                    onClick = { onSelected(choice); open = false }
                )
            }
        }
    }
}

private fun liveTranslationValues(
    mode: LiveStreamMode,
    allSegmentsJson: String,
    markersJson: String,
    transcriptEnabledAtEnd: Boolean,
    sourceLanguage: String,
    targetLanguage: String,
    allowedLanguages: List<String>,
    switchSensitivity: String,
    speechEngineRequested: String,
    lastSpeechEngine: String,
    engineSwitchCount: Int,
    preferOffline: Boolean,
    lastDetectedLanguage: String,
    startedAt: String,
    status: String,
    error: String
): Map<String, String> {
    val segments = segmentsFromJson(allSegmentsJson)
    val recordedSegments = segments.filter { it.recorded }
    return linkedMapOf(
        LiveStreamTranslateFields.TRANSCRIPT to transcriptFromSegments(recordedSegments, markersJson),
        LiveStreamTranslateFields.SEGMENTS_JSON to segmentsToJson(recordedSegments),
        LiveStreamTranslateFields.MODE to mode.wireValue,
        LiveStreamTranslateFields.SOURCE_LANGUAGE to if (mode == LiveStreamMode.AUTO) "auto" else sourceLanguage,
        LiveStreamTranslateFields.TARGET_LANGUAGE to targetLanguage,
        LiveStreamTranslateFields.AUTO_ALLOWED_LANGUAGES to allowedLanguages.joinToString(","),
        LiveStreamTranslateFields.SWITCH_SENSITIVITY to if (mode == LiveStreamMode.AUTO) switchSensitivity else "",
        LiveStreamTranslateFields.SPEECH_ENGINE_REQUESTED to speechEngineRequested,
        LiveStreamTranslateFields.LAST_SPEECH_ENGINE to lastSpeechEngine,
        LiveStreamTranslateFields.ENGINE_SWITCH_COUNT to engineSwitchCount.toString(),
        LiveStreamTranslateFields.PREFER_OFFLINE to preferOffline.toString(),
        LiveStreamTranslateFields.TRANSCRIPT_ENABLED_AT_END to transcriptEnabledAtEnd.toString(),
        LiveStreamTranslateFields.LAST_DETECTED_LANGUAGE to lastDetectedLanguage,
        LiveStreamTranslateFields.SEGMENT_COUNT to recordedSegments.size.toString(),
        LiveStreamTranslateFields.STARTED_TIME_ISO to startedAt,
        LiveStreamTranslateFields.FINISHED_TIME_ISO to Instant.now().toString(),
        LiveStreamTranslateFields.STATUS to status,
        LiveStreamTranslateFields.ERROR to error
    )
}

private fun appendSegmentJson(json: String, segment: LiveTranslationSegment): String {
    val array = runCatching { JSONArray(json) }.getOrElse { JSONArray() }
    array.put(segmentToJson(segment))
    return array.toString()
}

private fun updateSegmentJson(
    json: String,
    id: String,
    transform: (LiveTranslationSegment) -> LiveTranslationSegment
): String {
    val array = runCatching { JSONArray(json) }.getOrElse { JSONArray() }
    val rebuilt = JSONArray()
    for (i in 0 until array.length()) {
        val obj = array.optJSONObject(i) ?: continue
        val segment = segmentFromJson(obj)
        rebuilt.put(segmentToJson(if (segment.id == id) transform(segment) else segment))
    }
    return rebuilt.toString()
}

private fun segmentsFromJson(json: String): List<LiveTranslationSegment> = runCatching {
    val array = JSONArray(json)
    buildList {
        for (i in 0 until array.length()) {
            array.optJSONObject(i)?.let { add(segmentFromJson(it)) }
        }
    }
}.getOrDefault(emptyList())

private fun segmentToJson(segment: LiveTranslationSegment) = JSONObject().apply {
    put("id", segment.id)
    put("time_iso", segment.timeIso)
    put("source_language", segment.sourceLanguage)
    put("source_language_tag", segment.sourceLanguageTag)
    put("target_language", segment.targetLanguage)
    put("original_text", segment.originalText)
    put("translated_text", segment.translatedText)
    if (segment.recognitionConfidence != null) put("recognition_confidence", segment.recognitionConfidence)
    put("recognition_engine", segment.recognitionEngine)
    put("recognition_engine_detail", segment.recognitionEngineDetail)
    if (segment.languageConfidence != null) put("language_confidence", segment.languageConfidence)
    put("translation_state", segment.translationState)
    put("error", segment.error)
    put("recorded", segment.recorded)
}

private fun segmentFromJson(obj: JSONObject) = LiveTranslationSegment(
    id = obj.optString("id"),
    timeIso = obj.optString("time_iso"),
    sourceLanguage = obj.optString("source_language"),
    sourceLanguageTag = obj.optString("source_language_tag"),
    targetLanguage = obj.optString("target_language"),
    originalText = obj.optString("original_text"),
    translatedText = obj.optString("translated_text"),
    recognitionConfidence = if (obj.has("recognition_confidence")) obj.optDouble("recognition_confidence").toFloat() else null,
    recognitionEngine = obj.optString("recognition_engine", "android"),
    recognitionEngineDetail = obj.optString("recognition_engine_detail"),
    languageConfidence = if (obj.has("language_confidence")) obj.optInt("language_confidence") else null,
    translationState = obj.optString("translation_state", "ready"),
    error = obj.optString("error"),
    recorded = if (obj.has("recorded")) obj.optBoolean("recorded") else true
)

private fun segmentsToJson(segments: List<LiveTranslationSegment>): String = JSONArray().apply {
    segments.forEach { put(segmentToJson(it)) }
}.toString()

private fun appendMarkerJson(json: String, type: String, timeIso: String, note: String): String {
    val array = runCatching { JSONArray(json) }.getOrElse { JSONArray() }
    array.put(JSONObject().apply {
        put("type", type)
        put("time_iso", timeIso)
        put("note", note)
    })
    return array.toString()
}

private fun transcriptFromSegments(segments: List<LiveTranslationSegment>, markersJson: String): String {
    val entries = mutableListOf<Pair<String, String>>()
    segments.forEach { segment ->
        val text = segment.translatedText.ifBlank {
            if (segment.translationState == "error") "[translation unavailable]" else segment.originalText
        }
        if (text.isNotBlank()) entries += segment.timeIso to text
    }
    runCatching { JSONArray(markersJson) }.getOrNull()?.let { markers ->
        for (i in 0 until markers.length()) {
            val marker = markers.optJSONObject(i) ?: continue
            val time = marker.optString("time_iso")
            val note = marker.optString("note")
            if (note.isNotBlank()) entries += time to "— $note —"
        }
    }
    return entries.sortedBy { it.first }.joinToString("\n\n") { it.second }
}

private fun valuesToJson(values: Map<String, String>): String = JSONObject(values).toString()

private fun valuesFromJson(json: String): Map<String, String> = runCatching {
    val obj = JSONObject(json)
    buildMap {
        obj.keys().forEach { key -> put(key, obj.optString(key)) }
    }
}.getOrDefault(emptyMap())

private fun canonicalSpeechLanguage(tagOrCode: String): String {
    val raw = tagOrCode.trim().replace('_', '-')
    val language = runCatching { Locale.forLanguageTag(raw).language }.getOrDefault("").ifBlank {
        raw.substringBefore('-')
    }.lowercase(Locale.ROOT)
    val canonical = when (language) {
        "iw" -> "he"
        "in" -> "id"
        "fil" -> "tl"
        "nb", "nn" -> "no"
        "cmn" -> "zh"
        else -> language
    }
    return MlKitLanguageCatalog.canonicalCode(canonical, canonical)
}

private fun languageLabel(code: String): String =
    if (code.isBlank()) "Unknown" else MlKitLanguageCatalog.label(MlKitLanguageCatalog.canonicalCode(code, code))

private fun shortTime(timeIso: String): String = runCatching {
    Instant.parse(timeIso).atZone(ZoneId.systemDefault()).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
}.getOrElse {
    runCatching { LocalTime.parse(timeIso).format(DateTimeFormatter.ofPattern("HH:mm:ss")) }.getOrDefault("")
}
