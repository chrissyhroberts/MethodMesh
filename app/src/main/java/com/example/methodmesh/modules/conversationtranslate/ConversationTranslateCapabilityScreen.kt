package com.example.methodmesh.modules.conversationtranslate

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.modules.mlkittranslate.MlKitLanguageCatalog
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.TranslatorOptions
import java.time.Instant
import java.util.Locale
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

object ConversationTranslateCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100ConversationTranslateMethod.ID
    override val title = "Conversation translator"
    override val description = "Translate a live conversation and keep a bilingual transcript."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val androidContext = LocalContext.current
        var languageA by rememberSaveable {
            mutableStateOf(MlKitLanguageCatalog.canonicalCode(context.action.settings["language_a"] ?: context.action.settings["input_language_a"], "en"))
        }
        var languageB by rememberSaveable {
            mutableStateOf(MlKitLanguageCatalog.canonicalCode(context.action.settings["language_b"] ?: context.action.settings["input_language_b"], "es"))
        }
        var arabicVariantA by rememberSaveable { mutableStateOf(context.action.settings["arabic_variant_a"] ?: context.action.settings["input_arabic_variant_a"] ?: ARABIC_VARIANT_GULF) }
        var arabicVariantB by rememberSaveable { mutableStateOf(context.action.settings["arabic_variant_b"] ?: context.action.settings["input_arabic_variant_b"] ?: ARABIC_VARIANT_GULF) }
        var speechLocaleA by rememberSaveable { mutableStateOf(context.action.settings["speech_locale_a"] ?: context.action.settings["input_speech_locale_a"] ?: "") }
        var speechLocaleB by rememberSaveable { mutableStateOf(context.action.settings["speech_locale_b"] ?: context.action.settings["input_speech_locale_b"] ?: "") }
        var flagA by rememberSaveable { mutableStateOf(defaultFlagForLanguage(languageA)) }
        var flagB by rememberSaveable { mutableStateOf(defaultFlagForLanguage(languageB)) }
        var labelA by rememberSaveable { mutableStateOf(normalizeCustomButtonLabel(context.action.settings["label_a"] ?: context.action.settings["input_label_a"], languageA)) }
        var labelB by rememberSaveable { mutableStateOf(normalizeCustomButtonLabel(context.action.settings["label_b"] ?: context.action.settings["input_label_b"], languageB)) }
        var voicePreferenceA by rememberSaveable { mutableStateOf(normalizeConversationVoicePreference(context.action.settings["voice_a"] ?: context.action.settings["input_voice_a"] ?: CONVERSATION_VOICE_FEMALE)) }
        var voicePreferenceB by rememberSaveable { mutableStateOf(normalizeConversationVoicePreference(context.action.settings["voice_b"] ?: context.action.settings["input_voice_b"] ?: CONVERSATION_VOICE_FEMALE)) }
        var spokenOutput by rememberSaveable { mutableStateOf((context.action.settings["spoken_output"] ?: context.action.settings["input_spoken_output"] ?: "true").equals("true", true)) }
        var preferOffline by rememberSaveable { mutableStateOf((context.action.settings["prefer_offline"] ?: context.action.settings["input_prefer_offline"] ?: "false").equals("true", true)) }
        var transcriptEnabled by rememberSaveable { mutableStateOf((context.action.settings["transcript_on_start"] ?: context.action.settings["input_transcript_on_start"] ?: "true").equals("true", true)) }
        var transcriptEventsJson by rememberSaveable(context.action.canonicalId) { mutableStateOf("[]") }
        var conversationHasOccurred by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        var unrecordedSpeechSinceMarker by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        var transcriptPausedByUser by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        var hasAudioPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(androidContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
        var status by rememberSaveable { mutableStateOf("Ready.") }
        var listeningSide by rememberSaveable { mutableStateOf<String?>(null) }
        var latestTranslated by rememberSaveable { mutableStateOf("") }
        var latestOriginal by rememberSaveable { mutableStateOf("") }
        var latestTextA by rememberSaveable { mutableStateOf("") }
        var latestTextB by rememberSaveable { mutableStateOf("") }
        var latestVoiceA by rememberSaveable { mutableStateOf(CONVERSATION_VOICE_FEMALE) }
        var latestVoiceB by rememberSaveable { mutableStateOf(CONVERSATION_VOICE_FEMALE) }
        var latestSpeakerA by rememberSaveable { mutableStateOf("a") }
        var latestSpeakerB by rememberSaveable { mutableStateOf("b") }
        var operatorFacing by rememberSaveable { mutableStateOf(false) }
        val runtimeSettingsVisible = listOf(
            "language_a",
            "language_b",
            "label_a",
            "label_b",
            "spoken_output",
            "prefer_offline",
            "transcript_on_start",
            "arabic_variant_a",
            "arabic_variant_b",
            "voice_a",
            "voice_b"
        ).any(context::settingIsRuntimeInput)
        var conversationOpen by rememberSaveable(context.action.canonicalId) { mutableStateOf(context.startsImmediately && !runtimeSettingsVisible) }
        var turnsJson by rememberSaveable(context.action.canonicalId) { mutableStateOf("[]") }
        var startedAt by rememberSaveable(context.action.canonicalId) { mutableStateOf(Instant.now().toString()) }
        var resultValuesJson by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var downloadedModelCodes by rememberSaveable { mutableStateOf("") }
        var modelStatus by rememberSaveable { mutableStateOf("Checking language packs…") }
        var busyLanguageCode by rememberSaveable { mutableStateOf<String?>(null) }
        var busyLanguageSeconds by rememberSaveable { mutableStateOf(0) }
        var modelDebugLog by rememberSaveable { mutableStateOf(timestampedLog("Opened conversation language check.")) }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var ttsReady by remember { mutableStateOf(false) }
        var advertisedSpeechLocalesPacked by rememberSaveable { mutableStateOf("") }
        val advertisedSpeechLocales = remember(advertisedSpeechLocalesPacked) {
            advertisedSpeechLocalesPacked.split('\u001F').map { it.trim() }.filter { it.isNotBlank() }.toSet()
        }
        val supportedLanguageCodes = remember { MlKitLanguageCatalog.supportedCodes() }
        val downloadedLanguages = remember(downloadedModelCodes) {
            downloadedModelCodes.split(',').map { it.trim() }.filter { it.isNotBlank() }.toSet()
        }
        val requiredTranslationLanguages = remember(languageA, languageB) {
            if (languageA == languageB) emptyList() else listOf(languageA, languageB).distinct()
        }
        val unsupportedTranslationLanguages = requiredTranslationLanguages.filter { it !in supportedLanguageCodes }
        val missingTranslationLanguages = requiredTranslationLanguages.filter { it in supportedLanguageCodes && it !in downloadedLanguages }
        val canTranslateConversation = missingTranslationLanguages.isEmpty() && unsupportedTranslationLanguages.isEmpty()
        val tts = remember {
            TextToSpeech(androidContext.applicationContext) { state ->
                ttsReady = state == TextToSpeech.SUCCESS
            }
        }

        LaunchedEffect(Unit) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: android.content.Context?, intent: Intent?) {
                    val supported = getResultExtras(false)
                        ?.getStringArrayList(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES)
                        .orEmpty()
                    if (supported.isNotEmpty()) {
                        advertisedSpeechLocalesPacked = supported.distinct().joinToString("\u001F")
                    }
                }
            }
            runCatching {
                androidContext.sendOrderedBroadcast(
                    Intent(RecognizerIntent.ACTION_GET_LANGUAGE_DETAILS),
                    null,
                    receiver,
                    null,
                    Activity.RESULT_OK,
                    null,
                    null
                )
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                tts.stop()
                tts.shutdown()
            }
        }

        val capturedResult = result ?: remember(resultValuesJson) {
            resultValuesJson
                ?.let(::conversationValuesFromJson)
                ?.let { values ->
                    As100ConversationTranslateMethod.result(
                        request = As100ConversationTranslateMethod.request(
                            action = As100ConversationTranslateMethod.ID,
                            context = context.request.invocationContext.asMap(As100ConversationTranslateMethod.ID) + context.action.settings + values,
                            signals = emptyList(),
                            inputs = emptyList()
                        ),
                        values = values,
                        invocation = context.request.invocationContext
                    )
                }
        }

        LaunchedEffect(languageA, languageB, labelA, labelB, voicePreferenceA, voicePreferenceB, spokenOutput, preferOffline, transcriptEnabled, arabicVariantA, arabicVariantB, speechLocaleA, speechLocaleB) {
            context.onSettingsChanged(
                mapOf(
                    "language_a" to languageA,
                    "language_b" to languageB,
                    "label_a" to labelA,
                    "label_b" to labelB,
                    "arabic_variant_a" to arabicVariantA,
                    "arabic_variant_b" to arabicVariantB,
                    "speech_locale_a" to speechLocaleA,
                    "speech_locale_b" to speechLocaleB,
                    "voice_a" to voicePreferenceA,
                    "voice_b" to voicePreferenceB,
                    "transcript_on_start" to transcriptEnabled.toString(),
                    "spoken_output" to spokenOutput.toString(),
                    "prefer_offline" to preferOffline.toString()
                )
            )
        }

        fun refreshLanguagePacks() {
            modelStatus = "Checking language packs…"
            modelDebugLog = prependDebugLog(modelDebugLog, "Refresh requested. ${languagePackConnectivityStatus(androidContext)}")
            RemoteModelManager.getInstance()
                .getDownloadedModels(TranslateRemoteModel::class.java)
                .addOnSuccessListener { models ->
                    downloadedModelCodes = models.mapNotNull { it.language }
                        .map { MlKitLanguageCatalog.canonicalCode(it, it) }
                        .distinct()
                        .sortedBy { MlKitLanguageCatalog.label(it).lowercase() }
                        .joinToString(",")
                    modelStatus = if (downloadedModelCodes.isBlank()) {
                        "No language packs downloaded."
                    } else {
                        "Language packs ready."
                    }
                    modelDebugLog = prependDebugLog(modelDebugLog, "Downloaded models: ${downloadedModelCodes.ifBlank { "none" }}")
                }
                .addOnFailureListener { error ->
                    modelStatus = "Could not check language packs: ${error.message.orEmpty()}"
                    modelDebugLog = prependDebugLog(modelDebugLog, "Refresh failed: ${error.message.orEmpty().ifBlank { error.javaClass.simpleName }}")
                }
        }

        fun downloadLanguagePack(code: String) {
            val canonical = MlKitLanguageCatalog.canonicalCode(code)
            if (canonical !in supportedLanguageCodes) {
                modelStatus = "${languageLabel(code)} is not available in ML Kit translation."
                return
            }
            busyLanguageCode = canonical
            busyLanguageSeconds = 0
            modelStatus = "Downloading ${languageLabel(canonical)}…"
            val model = TranslateRemoteModel.Builder(canonical).build()
            modelDebugLog = prependDebugLog(modelDebugLog, "Download started via RemoteModelManager: ${languageLabel(canonical)}. ${languagePackConnectivityStatus(androidContext)}")
            RemoteModelManager.getInstance()
                .download(model, DownloadConditions.Builder().build())
                .addOnSuccessListener {
                    busyLanguageCode = null
                    modelStatus = "${languageLabel(canonical)} downloaded."
                    modelDebugLog = prependDebugLog(modelDebugLog, "Download callback succeeded: ${languageLabel(canonical)}.")
                    refreshLanguagePacks()
                }
                .addOnFailureListener { error ->
                    busyLanguageCode = null
                    modelStatus = "Download failed for ${languageLabel(canonical)}: ${error.message.orEmpty()}"
                    modelDebugLog = prependDebugLog(modelDebugLog, "Download failed for ${languageLabel(canonical)}: ${error.message.orEmpty().ifBlank { error.javaClass.simpleName }}")
                }
        }

        LaunchedEffect(languageA, languageB, conversationOpen) {
            if (conversationOpen) refreshLanguagePacks()
        }
        LaunchedEffect(busyLanguageCode) {
            val active = busyLanguageCode ?: return@LaunchedEffect
            while (busyLanguageCode == active) {
                delay(1000)
                busyLanguageSeconds += 1
                if (busyLanguageSeconds > 0 && busyLanguageSeconds % 10 == 0) {
                    val diagnosis = if (busyLanguageSeconds >= 120) {
                        "Likely blocked by network, Google Play Services, or model delivery."
                    } else {
                        "Waiting for ML Kit callback."
                    }
                    modelDebugLog = prependDebugLog(modelDebugLog, "Still waiting for ${languageLabel(active)} download callback at ${busyLanguageSeconds}s. $diagnosis ${languagePackConnectivityStatus(androidContext)}")
                }
            }
        }

        fun finishConversation(state: String = "succeeded", error: String = "") {
            val values = conversationValues(
                turnsJson = turnsJson,
                transcriptEventsJson = transcriptEventsJson,
                transcriptEnabled = transcriptEnabled,
                languageA = languageA,
                languageB = languageB,
                labelA = labelA,
                labelB = labelB,
                arabicVariantA = arabicVariantA,
                arabicVariantB = arabicVariantB,
                speechLocaleA = speechLocaleA,
                speechLocaleB = speechLocaleB,
                voicePreferenceA = voicePreferenceA,
                voicePreferenceB = voicePreferenceB,
                spokenOutput = spokenOutput,
                preferOffline = preferOffline,
                startedAt = startedAt,
                status = state,
                error = error
            )
            val request = As100ConversationTranslateMethod.request(
                action = As100ConversationTranslateMethod.ID,
                context = context.request.invocationContext.asMap(As100ConversationTranslateMethod.ID) + context.action.settings,
                signals = emptyList(),
                inputs = emptyList()
            )
            val execution = As100ConversationTranslateMethod.result(request, values, context.request.invocationContext)
            result = execution
            resultValuesJson = conversationValuesToJson(values)
            status = if (state == "succeeded") "Conversation ready to share." else error.ifBlank { "Conversation failed." }
            conversationOpen = false
            if (context.submitsImmediately && state == "succeeded") onConfirmed(execution)
        }

        fun speechSettingsForSide(side: String): Pair<String, String> =
            if (side == "a") speechLocaleA to arabicVariantA else speechLocaleB to arabicVariantB

        fun speak(text: String, language: String, side: String, voicePreference: String, speakerId: String) {
            if (!spokenOutput || text.isBlank() || !ttsReady) return
            val (localeOverride, variant) = speechSettingsForSide(side)
            val locale = localeFor(language, advertisedSpeechLocales, localeOverride, variant)
            val liveVoicePreference = when (speakerId) {
                "a" -> voicePreferenceA
                "b" -> voicePreferenceB
                else -> voicePreference
            }
            when (tts.setLanguage(locale)) {
                TextToSpeech.LANG_MISSING_DATA -> status = "Translated. ${languageLabel(language)} text-to-speech data is not installed on this device."
                TextToSpeech.LANG_NOT_SUPPORTED -> status = "Translated. ${languageLabel(language)} text-to-speech is not supported by the current Android voice engine."
                else -> {
                    // Re-resolve the selected voice immediately before every
                    // vocalisation, including replays. Android TTS language and
                    // voice are global engine state and must not be assumed to
                    // persist correctly across turn-taking.
                    tts.applyConversationVoicePreference(locale, liveVoicePreference, speakerId)
                    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "methodmesh-conversation-${System.currentTimeMillis()}")
                }
            }
        }

        fun setTranscriptRecording(enabled: Boolean) {
            if (enabled == transcriptEnabled) return
            val now = Instant.now().toString()
            if (enabled) {
                if (transcriptPausedByUser || (conversationHasOccurred && unrecordedSpeechSinceMarker)) {
                    transcriptEventsJson = appendTranscriptMarker(
                        transcriptEventsJson,
                        type = if (!transcriptPausedByUser && turns(turnsJson).isEmpty()) "started_after_gap" else "resumed",
                        timeIso = now,
                        note = when {
                            transcriptPausedByUser && unrecordedSpeechSinceMarker -> "Transcript resumed; conversation during the break was not transcribed."
                            transcriptPausedByUser -> "Transcript resumed after a recording break."
                            else -> "Transcript started; earlier conversation was not transcribed."
                        }
                    )
                }
                transcriptPausedByUser = false
                unrecordedSpeechSinceMarker = false
                status = "Transcript on."
            } else {
                transcriptEventsJson = appendTranscriptMarker(
                    transcriptEventsJson,
                    type = "paused",
                    timeIso = now,
                    note = "Transcript paused. Conversation can continue without being recorded."
                )
                transcriptPausedByUser = true
                status = "Transcript off. Translation continues."
            }
            transcriptEnabled = enabled
        }

        fun addTurn(side: String, original: String, translated: String, translationPerformed: Boolean = true) {
            val source = MlKitLanguageCatalog.canonicalCode(if (side == "a") languageA else languageB)
            val target = MlKitLanguageCatalog.canonicalCode(if (side == "a") languageB else languageA)
            val targetSide = if (side == "a") "b" else "a"
            val speaker = if (side == "a") labelA else labelB
            val turn = ConversationTurn(
                side = side,
                speaker = speaker,
                sourceLanguage = source,
                targetLanguage = target,
                originalText = original,
                translatedText = translated,
                timeIso = Instant.now().toString()
            )
            conversationHasOccurred = true
            if (transcriptEnabled) {
                turnsJson = appendTurn(turnsJson, turn)
                transcriptEventsJson = appendTranscriptTurn(transcriptEventsJson, turn)
            } else {
                unrecordedSpeechSinceMarker = true
            }
            latestOriginal = original
            latestTranslated = if (translationPerformed) translated else ""
            val targetDisplay = if (translationPerformed) translated else original
            val sourceVoice = if (side == "a") voicePreferenceA else voicePreferenceB
            if (side == "a") {
                latestTextA = original
                latestTextB = targetDisplay
            } else {
                latestTextA = targetDisplay
                latestTextB = original
            }
            latestVoiceA = sourceVoice
            latestVoiceB = sourceVoice
            latestSpeakerA = side
            latestSpeakerB = side
            status = if (translationPerformed) {
                if (transcriptEnabled) "Translated · transcript on." else "Translated · transcript off."
            } else {
                if (transcriptEnabled) "Shared language · no translation needed · transcript on." else "Shared language · no translation needed · transcript off."
            }
            if (translationPerformed) speak(translated, target, targetSide, sourceVoice, side)
        }

        fun translateSpeech(side: String, text: String) {
            val source = if (side == "a") languageA else languageB
            val target = if (side == "a") languageB else languageA
            if (text.isBlank()) {
                status = "No speech detected."
                return
            }
            if (source == target) {
                addTurn(side, text, "", translationPerformed = false)
                return
            }
            if (!canTranslateConversation) {
                status = missingLanguageStatus(missingTranslationLanguages, unsupportedTranslationLanguages)
                return
            }
            status = "Translating…"
            val translator = Translation.getClient(
                TranslatorOptions.Builder()
                    .setSourceLanguage(source)
                    .setTargetLanguage(target)
                    .build()
            )
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
                .addOnSuccessListener {
                    translator.translate(text)
                        .addOnSuccessListener { translated ->
                            addTurn(side, text, translated)
                            translator.close()
                        }
                        .addOnFailureListener { error ->
                            status = "Translation failed: ${error.message.orEmpty()}"
                            translator.close()
                        }
                }
                .addOnFailureListener { error ->
                    status = "Translation model unavailable: ${error.message.orEmpty()}"
                    translator.close()
                }
        }

        val recognizer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { activityResult ->
            val side = listeningSide
            listeningSide = null
            if (activityResult.resultCode != Activity.RESULT_OK || side == null) {
                status = "Listening cancelled."
                return@rememberLauncherForActivityResult
            }
            val text = activityResult.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                .orEmpty()
                .firstOrNull()
                .orEmpty()
            translateSpeech(side, text)
        }

        val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasAudioPermission = granted
            status = if (granted) "Microphone ready." else "Microphone permission is needed."
        }

        fun listen(side: String) {
            if (!hasAudioPermission) {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                return
            }
            result = null
            resultValuesJson = null
            if (!canTranslateConversation) {
                status = missingLanguageStatus(missingTranslationLanguages, unsupportedTranslationLanguages)
                return
            }
            val source = MlKitLanguageCatalog.canonicalCode(if (side == "a") languageA else languageB)
            if (advertisedSpeechLocales.isNotEmpty() && !ConversationLanguageSupport.isAdvertisedByRecognizer(source, advertisedSpeechLocales)) {
                status = "Android speech recognition on this device does not advertise ${languageLabel(source)}. The ML Kit translation pack and Android speech pack are separate."
                return
            }
            val prompt = ConversationLanguageSupport.initialInstruction(source)
            val (localeOverride, variant) = speechSettingsForSide(side)
            listeningSide = side
            status = "Listening…"
            val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                val speechLocale = ConversationLanguageSupport.speechLocaleTag(
                    source,
                    advertisedSpeechLocales,
                    localeOverride = localeOverride,
                    arabicVariant = variant
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, speechLocale)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, speechLocale)
                putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            try {
                recognizer.launch(recognizerIntent)
            } catch (_: ActivityNotFoundException) {
                listeningSide = null
                status = "Android speech recognition is not available for ${languageLabel(source)} on this device. The ML Kit translation pack is separate from the speech recogniser."
            }
        }

        CapabilityScreenScaffold(
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = capturedResult,
            resultPreview = capturedResult
                ?.let { OutputFormatter.fields(it, includeProvenance = false) }
                ?.filterValues { value -> value?.toString()?.isNotBlank() == true }
                ?.filterKeys { key -> key !in setOf(
                    ConversationTranslateFields.ARABIC_VARIANT_A,
                    ConversationTranslateFields.ARABIC_VARIANT_B,
                    ConversationTranslateFields.SPEECH_LOCALE_A,
                    ConversationTranslateFields.SPEECH_LOCALE_B,
                    ConversationTranslateFields.VOICE_A,
                    ConversationTranslateFields.VOICE_B
                ) }
                .orEmpty(),
            onBack = onBack,
            onRetry = {
                result = null
                resultValuesJson = null
                turnsJson = "[]"
                transcriptEventsJson = "[]"
                conversationHasOccurred = false
                unrecordedSpeechSinceMarker = false
                transcriptPausedByUser = false
                latestOriginal = ""
                latestTranslated = ""
                latestTextA = ""
                latestTextB = ""
                latestVoiceA = CONVERSATION_VOICE_FEMALE
                latestVoiceB = CONVERSATION_VOICE_FEMALE
                latestSpeakerA = "a"
                latestSpeakerB = "b"
                conversationOpen = context.startsImmediately && !runtimeSettingsVisible
                startedAt = Instant.now().toString()
                status = "Conversation cleared."
            },
            onConfirm = { capturedResult?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            if (capturedResult == null) {
                if (!context.startsImmediately || runtimeSettingsVisible) {
                    Text("Configure a language pair, then let either person press their own button whenever they speak.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    if (!context.settingIsFixedInNativePreset("language_a")) {
                    ConversationLanguagePicker("First language", languageA, onSelected = {
                        languageA = MlKitLanguageCatalog.canonicalCode(it, "en")
                    })
                    Spacer(Modifier.height(8.dp))
                    }
                    if (!context.settingIsFixedInNativePreset("language_b")) {
                    ConversationLanguagePicker("Second language", languageB, onSelected = {
                        languageB = MlKitLanguageCatalog.canonicalCode(it, "es")
                    })
                    Spacer(Modifier.height(8.dp))
                    }
                    if (!context.settingIsFixedInNativePreset("spoken_output")) {
                    ToggleRow("Speak translations aloud", spokenOutput) { spokenOutput = it }
                    }
                    if (!context.settingIsFixedInNativePreset("prefer_offline")) {
                    ToggleRow("Prefer offline speech recognition", preferOffline) { preferOffline = it }
                    }
                    if (!context.settingIsFixedInNativePreset("transcript_on_start")) {
                    ToggleRow("Transcript on when conversation starts", transcriptEnabled) { transcriptEnabled = it }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { conversationOpen = true },
                        modifier = Modifier.fillMaxWidth().height(58.dp)
                    ) {
                        Text("Start conversation")
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(status, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))
                val transcript = transcriptFromEvents(transcriptEventsJson, turnsJson)
                if (transcript.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp).heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                            Text("Transcript", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            Text(transcript, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        if (capturedResult == null && conversationOpen) {
            Dialog(
                onDismissRequest = { if (!context.startsImmediately) conversationOpen = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ConversationSharedSurface(
                            languageA = languageA,
                            languageB = languageB,
                            flagA = flagA,
                            flagB = flagB,
                            arabicVariantA = arabicVariantA,
                            arabicVariantB = arabicVariantB,
                            labelA = labelA,
                            labelB = labelB,
                            textA = latestTextA,
                            textB = latestTextB,
                            status = status,
                            modelStatus = modelStatus,
                            modelDebugLog = modelDebugLog,
                            missingLanguages = missingTranslationLanguages,
                            unsupportedLanguages = unsupportedTranslationLanguages,
                            busyLanguageCode = busyLanguageCode,
                            busyLanguageSeconds = busyLanguageSeconds,
                            operatorFacing = operatorFacing,
                            spokenOutput = spokenOutput,
                            transcriptEnabled = transcriptEnabled,
                            voicePreferenceA = voicePreferenceA,
                            voicePreferenceB = voicePreferenceB,
                            onOperatorFacingChanged = { operatorFacing = it },
                            onTranscriptChanged = ::setTranscriptRecording,
                            onLanguageAChanged = { choice ->
                                languageA = MlKitLanguageCatalog.canonicalCode(choice.language, "en")
                                speechLocaleA = choice.speechLocaleOverride
                                if (choice.arabicVariant.isNotBlank()) arabicVariantA = choice.arabicVariant
                                flagA = choice.flag.ifBlank { defaultFlagForLanguage(languageA) }
                            },
                            onLanguageBChanged = { choice ->
                                languageB = MlKitLanguageCatalog.canonicalCode(choice.language, "es")
                                speechLocaleB = choice.speechLocaleOverride
                                if (choice.arabicVariant.isNotBlank()) arabicVariantB = choice.arabicVariant
                                flagB = choice.flag.ifBlank { defaultFlagForLanguage(languageB) }
                            },
                            onArabicVariantAChanged = { arabicVariantA = it; speechLocaleA = "" },
                            onArabicVariantBChanged = { arabicVariantB = it; speechLocaleB = "" },
                            onVoiceAChanged = {
                                val next = toggleConversationVoicePreference(voicePreferenceA)
                                voicePreferenceA = next
                                if (latestSpeakerA == "a") latestVoiceA = next
                                if (latestSpeakerB == "a") latestVoiceB = next
                            },
                            onVoiceBChanged = {
                                val next = toggleConversationVoicePreference(voicePreferenceB)
                                voicePreferenceB = next
                                if (latestSpeakerA == "b") latestVoiceA = next
                                if (latestSpeakerB == "b") latestVoiceB = next
                            },
                            onDownloadLanguage = ::downloadLanguagePack,
                            onRefreshLanguagePacks = ::refreshLanguagePacks,
                            onListenA = { listen("a") },
                            onListenB = { listen("b") },
                            onReplayA = { speak(latestTextA, languageA, "a", latestVoiceA, latestSpeakerA) },
                            onReplayB = { speak(latestTextB, languageB, "b", latestVoiceB, latestSpeakerB) },
                            onEnd = { finishConversation() }
                        )
                        if (!context.startsImmediately) {
                            OutlinedButton(
                                onClick = { conversationOpen = false },
                                modifier = Modifier.fillMaxWidth().height(44.dp).padding(top = 6.dp)
                            ) {
                                Text("Back to setup")
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class ConversationTurn(
    val side: String,
    val speaker: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    val originalText: String,
    val translatedText: String,
    val timeIso: String
)

@Composable
private fun ConversationSharedSurface(
    languageA: String,
    languageB: String,
    flagA: String,
    flagB: String,
    arabicVariantA: String,
    arabicVariantB: String,
    labelA: String,
    labelB: String,
    textA: String,
    textB: String,
    status: String,
    modelStatus: String,
    modelDebugLog: String,
    missingLanguages: List<String>,
    unsupportedLanguages: List<String>,
    busyLanguageCode: String?,
    busyLanguageSeconds: Int,
    operatorFacing: Boolean,
    spokenOutput: Boolean,
    transcriptEnabled: Boolean,
    voicePreferenceA: String,
    voicePreferenceB: String,
    onOperatorFacingChanged: (Boolean) -> Unit,
    onTranscriptChanged: (Boolean) -> Unit,
    onLanguageAChanged: (ParticipantLanguageChoice) -> Unit,
    onLanguageBChanged: (ParticipantLanguageChoice) -> Unit,
    onArabicVariantAChanged: (String) -> Unit,
    onArabicVariantBChanged: (String) -> Unit,
    onVoiceAChanged: () -> Unit,
    onVoiceBChanged: () -> Unit,
    onDownloadLanguage: (String) -> Unit,
    onRefreshLanguagePacks: () -> Unit,
    onListenA: () -> Unit,
    onListenB: () -> Unit,
    onReplayA: () -> Unit,
    onReplayB: () -> Unit,
    onEnd: () -> Unit
) {
    val canListen = missingLanguages.isEmpty() && unsupportedLanguages.isEmpty() && busyLanguageCode == null
    Surface(
        modifier = Modifier.fillMaxWidth().fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ConversationPersonPanel(
                language = languageB,
                flag = flagB,
                arabicVariant = arabicVariantB,
                voicePreference = voicePreferenceB,
                buttonLabel = labelB,
                text = textB,
                rotated = !operatorFacing,
                spokenOutput = spokenOutput,
                listenEnabled = canListen,
                modifier = Modifier.weight(1f),
                onLanguageChanged = onLanguageBChanged,
                onArabicVariantChanged = onArabicVariantBChanged,
                onVoiceToggle = onVoiceBChanged,
                onListen = onListenB,
                onReplay = onReplayB
            )
            Spacer(Modifier.height(6.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(status, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                        Text("Operator view", style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.width(6.dp))
                        Switch(checked = operatorFacing, onCheckedChange = onOperatorFacingChanged)
                    }
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = { onTranscriptChanged(!transcriptEnabled) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (transcriptEnabled) "● Transcript ON · tap to pause" else "○ Transcript OFF · tap to resume")
                    }
                }
            }
            if (!canListen) {
                Spacer(Modifier.height(6.dp))
                MissingLanguagePackPanel(
                    modelStatus = modelStatus,
                    modelDebugLog = modelDebugLog,
                    missingLanguages = missingLanguages,
                    unsupportedLanguages = unsupportedLanguages,
                    busyLanguageCode = busyLanguageCode,
                    busyLanguageSeconds = busyLanguageSeconds,
                    onDownloadLanguage = onDownloadLanguage,
                    onRefreshLanguagePacks = onRefreshLanguagePacks
                )
            }
            Spacer(Modifier.height(6.dp))
            ConversationPersonPanel(
                language = languageA,
                flag = flagA,
                arabicVariant = arabicVariantA,
                voicePreference = voicePreferenceA,
                buttonLabel = labelA,
                text = textA,
                rotated = false,
                spokenOutput = spokenOutput,
                listenEnabled = canListen,
                modifier = Modifier.weight(1f),
                onLanguageChanged = onLanguageAChanged,
                onArabicVariantChanged = onArabicVariantAChanged,
                onVoiceToggle = onVoiceAChanged,
                onListen = onListenA,
                onReplay = onReplayA
            )
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = onEnd,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("End conversation")
            }
        }
    }
}

@Composable
private fun ConversationPersonPanel(
    language: String,
    flag: String,
    arabicVariant: String,
    voicePreference: String,
    buttonLabel: String,
    text: String,
    rotated: Boolean,
    spokenOutput: Boolean,
    listenEnabled: Boolean,
    modifier: Modifier = Modifier,
    onLanguageChanged: (ParticipantLanguageChoice) -> Unit,
    onArabicVariantChanged: (String) -> Unit,
    onVoiceToggle: () -> Unit,
    onListen: () -> Unit,
    onReplay: () -> Unit
) {
    val rotation = if (rotated) 180f else 0f
    val rotateModifier = if (rotated) Modifier.rotate(180f) else Modifier
    val displayButtonLabel = buttonLabel.ifBlank { ConversationLanguageSupport.pressToSpeak(language) }
    val displayText = text.ifBlank { ConversationLanguageSupport.initialInstruction(language) }
    Surface(
        modifier = modifier.fillMaxWidth().then(rotateModifier),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ParticipantLanguageButton(
                selectedLanguage = language,
                selectedFlag = flag,
                rotationDegrees = rotation,
                prominent = true,
                onSelected = onLanguageChanged
            )
            if (MlKitLanguageCatalog.canonicalCode(language, language) == "ar") {
                Spacer(Modifier.height(6.dp))
                ArabicVariantButton(
                    language = language,
                    variant = arabicVariant,
                    rotationDegrees = rotation,
                    onVariantSelected = onArabicVariantChanged
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = onVoiceToggle,
                    modifier = Modifier.width(46.dp).height(58.dp),
                    contentPadding = PaddingValues(0.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.88f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)
                ) {
                    Text(conversationVoiceSymbol(voicePreference), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onListen,
                    enabled = listenEnabled,
                    modifier = Modifier.weight(1f).height(58.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(displayButtonLabel, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
                }
                OutlinedButton(
                    onClick = onReplay,
                    enabled = spokenOutput && text.isNotBlank(),
                    modifier = Modifier.width(46.dp).height(58.dp),
                    contentPadding = PaddingValues(0.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.66f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.34f)
                    )
                ) {
                    Text("↻", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(6.dp))
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = displayText,
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(nativeConversationLanguageLabel(language), modifier = Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun MissingLanguagePackPanel(
    modelStatus: String,
    modelDebugLog: String,
    missingLanguages: List<String>,
    unsupportedLanguages: List<String>,
    busyLanguageCode: String?,
    busyLanguageSeconds: Int,
    onDownloadLanguage: (String) -> Unit,
    onRefreshLanguagePacks: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.64f),
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Text("Language pack needed", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("Translation is powered by Google ML Kit.", style = MaterialTheme.typography.bodySmall)
            if (missingLanguages.isNotEmpty()) {
                Text("Download ${missingLanguages.joinToString { languageLabel(it) }} to translate this conversation.", style = MaterialTheme.typography.bodySmall)
            }
            if (unsupportedLanguages.isNotEmpty()) {
                Text("Not available on this device: ${unsupportedLanguages.joinToString { languageLabel(it) }}.", style = MaterialTheme.typography.bodySmall)
            }
            if (modelStatus.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(modelStatus, style = MaterialTheme.typography.bodySmall)
            }
            busyLanguageCode?.let { code ->
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                Text(
                    "Downloading ${languageLabel(code)} · ${busyLanguageSeconds}s elapsed. Keep this screen open.",
                    style = MaterialTheme.typography.bodySmall
                )
                if (busyLanguageSeconds >= 120) {
                    Text(
                        "No ML Kit callback after 2 minutes. This usually means the device cannot reach Google’s model delivery service, or Google Play Services is unavailable/stuck.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            SelectionContainer {
                Text("Download log\n$modelDebugLog", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                missingLanguages.take(2).forEach { code ->
                    Button(
                        onClick = { onDownloadLanguage(code) },
                        enabled = busyLanguageCode == null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (busyLanguageCode == code) "Downloading…" else "Download ${MlKitLanguageCatalog.info(code).name}")
                    }
                }
                if (missingLanguages.isEmpty()) {
                    OutlinedButton(onClick = onRefreshLanguagePacks, modifier = Modifier.weight(1f)) { Text("Refresh") }
                }
            }
            if (missingLanguages.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = onRefreshLanguagePacks,
                    enabled = busyLanguageCode == null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Refresh language packs")
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ConversationLanguagePicker(label: String, selected: String, onSelected: (String) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column {
        Text(label, fontWeight = FontWeight.SemiBold)
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(languageLabel(selected), modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
            Text("▼")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.fillMaxWidth(.9f)) {
            conversationLanguages().forEach { code ->
                DropdownMenuItem(text = { Text(languageLabel(code)) }, onClick = { onSelected(code); expanded = false })
            }
        }
    }
}

private fun conversationLanguages(): List<String> =
    MlKitLanguageCatalog.supportedLanguages().map { it.code }

private fun conversationValues(
    turnsJson: String,
    transcriptEventsJson: String,
    transcriptEnabled: Boolean,
    languageA: String,
    languageB: String,
    labelA: String,
    labelB: String,
    arabicVariantA: String,
    arabicVariantB: String,
    speechLocaleA: String,
    speechLocaleB: String,
    voicePreferenceA: String,
    voicePreferenceB: String,
    spokenOutput: Boolean,
    preferOffline: Boolean,
    startedAt: String,
    status: String,
    error: String
): Map<String, String> = linkedMapOf(
    ConversationTranslateFields.TRANSCRIPT to transcriptFromEvents(transcriptEventsJson, turnsJson),
    ConversationTranslateFields.TURNS_JSON to turnsJson,
    ConversationTranslateFields.LANGUAGE_A to languageA,
    ConversationTranslateFields.LANGUAGE_B to languageB,
    ConversationTranslateFields.LABEL_A to labelA,
    ConversationTranslateFields.LABEL_B to labelB,
    ConversationTranslateFields.ARABIC_VARIANT_A to if (languageA == "ar") arabicVariantA else "",
    ConversationTranslateFields.ARABIC_VARIANT_B to if (languageB == "ar") arabicVariantB else "",
    ConversationTranslateFields.SPEECH_LOCALE_A to speechLocaleA,
    ConversationTranslateFields.SPEECH_LOCALE_B to speechLocaleB,
    ConversationTranslateFields.VOICE_A to voicePreferenceA,
    ConversationTranslateFields.VOICE_B to voicePreferenceB,
    ConversationTranslateFields.TRANSCRIPT_EVENTS_JSON to transcriptEventsJson,
    ConversationTranslateFields.TRANSCRIPT_ENABLED_AT_END to transcriptEnabled.toString(),
    ConversationTranslateFields.SPOKEN_OUTPUT to spokenOutput.toString(),
    ConversationTranslateFields.PREFER_OFFLINE to preferOffline.toString(),
    ConversationTranslateFields.TURN_COUNT to turns(turnsJson).size.toString(),
    ConversationTranslateFields.STARTED_TIME_ISO to startedAt,
    ConversationTranslateFields.FINISHED_TIME_ISO to Instant.now().toString(),
    ConversationTranslateFields.STATUS to status,
    ConversationTranslateFields.ERROR to error
)

private fun appendTurn(json: String, turn: ConversationTurn): String {
    val array = JSONArray(json.ifBlank { "[]" })
    array.put(
        JSONObject()
            .put("side", turn.side)
            .put("speaker", turn.speaker)
            .put("source_language", turn.sourceLanguage)
            .put("target_language", turn.targetLanguage)
            .put("original_text", turn.originalText)
            .put("translated_text", turn.translatedText)
            .put("time_iso", turn.timeIso)
    )
    return array.toString()
}

private fun appendTranscriptTurn(json: String, turn: ConversationTurn): String {
    val array = JSONArray(json.ifBlank { "[]" })
    array.put(
        JSONObject()
            .put("event_type", "turn")
            .put("time_iso", turn.timeIso)
            .put("side", turn.side)
            .put("speaker", turn.speaker)
            .put("source_language", turn.sourceLanguage)
            .put("target_language", turn.targetLanguage)
            .put("original_text", turn.originalText)
            .put("translated_text", turn.translatedText)
    )
    return array.toString()
}

private fun appendTranscriptMarker(json: String, type: String, timeIso: String, note: String): String {
    val array = JSONArray(json.ifBlank { "[]" })
    array.put(
        JSONObject()
            .put("event_type", type)
            .put("time_iso", timeIso)
            .put("note", note)
    )
    return array.toString()
}

private fun turns(json: String): List<ConversationTurn> = runCatching {
    val array = JSONArray(json.ifBlank { "[]" })
    (0 until array.length()).map { index ->
        val item = array.getJSONObject(index)
        ConversationTurn(
            side = item.optString("side"),
            speaker = item.optString("speaker"),
            sourceLanguage = item.optString("source_language"),
            targetLanguage = item.optString("target_language"),
            originalText = item.optString("original_text"),
            translatedText = item.optString("translated_text"),
            timeIso = item.optString("time_iso")
        )
    }
}.getOrDefault(emptyList())

private fun transcriptFromTurns(json: String): String =
    turns(json).joinToString("\n\n") { turn ->
        val speaker = turn.speaker.ifBlank { languageLabel(turn.sourceLanguage) }
        if (turn.sourceLanguage == turn.targetLanguage || turn.translatedText.isBlank()) {
            "$speaker (${turn.sourceLanguage}): ${turn.originalText}"
        } else {
            "$speaker (${turn.sourceLanguage}): ${turn.originalText}\n${languageLabel(turn.targetLanguage)}: ${turn.translatedText}"
        }
    }

private fun transcriptFromEvents(eventsJson: String, legacyTurnsJson: String): String = runCatching {
    val array = JSONArray(eventsJson.ifBlank { "[]" })
    if (array.length() == 0) return@runCatching transcriptFromTurns(legacyTurnsJson)
    buildList {
        for (index in 0 until array.length()) {
            val item = array.getJSONObject(index)
            when (item.optString("event_type")) {
                "turn" -> {
                    val source = item.optString("source_language")
                    val target = item.optString("target_language")
                    val speaker = item.optString("speaker").ifBlank { languageLabel(source) }
                    val original = item.optString("original_text")
                    val translated = item.optString("translated_text")
                    add(
                        if (source == target || translated.isBlank()) {
                            "$speaker ($source): $original"
                        } else {
                            "$speaker ($source): $original\n${languageLabel(target)}: $translated"
                        }
                    )
                }
                "paused", "resumed", "started_after_gap" -> add("[${item.optString("time_iso")}] ${item.optString("note")}")
            }
        }
    }.joinToString("\n\n")
}.getOrElse { transcriptFromTurns(legacyTurnsJson) }

private fun conversationValuesToJson(values: Map<String, String>): String =
    JSONObject().apply { values.toSortedMap().forEach { (key, value) -> put(key, value) } }.toString()

private fun conversationValuesFromJson(json: String): Map<String, String> = runCatching {
    val root = JSONObject(json.ifBlank { "{}" })
    buildMap {
        root.keys().forEach { key -> put(key, root.optString(key)) }
    }
}.getOrDefault(emptyMap())

private fun languageLabel(code: String): String = MlKitLanguageCatalog.label(code)

private fun missingLanguageStatus(missing: List<String>, unsupported: List<String>): String {
    val needed = missing.joinToString { languageLabel(it) }
    val unavailable = unsupported.joinToString { languageLabel(it) }
    return when {
        unavailable.isNotBlank() -> "Language not available on this device: $unavailable"
        needed.isNotBlank() -> "Download language pack: $needed"
        else -> "Language packs ready."
    }
}

private fun timestampedLog(message: String): String =
    "${Instant.now()}  $message"

private fun prependDebugLog(existing: String, message: String): String =
    (timestampedLog(message) + "\n" + existing)
        .lineSequence()
        .take(30)
        .joinToString("\n")

private fun languagePackConnectivityStatus(context: android.content.Context): String {
    val connectivity = context.getSystemService(ConnectivityManager::class.java)
    val network = connectivity?.activeNetwork
    val capabilities = network?.let { connectivity.getNetworkCapabilities(it) }
    val hasInternet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    val validated = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    val transport = when {
        capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "wifi"
        capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "cellular"
        capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "ethernet"
        capabilities != null -> "other"
        else -> "none"
    }
    val playServices = runCatching {
        val info = context.packageManager.getPackageInfo("com.google.android.gms", 0)
        "play_services=${info.versionName ?: info.longVersionCode.toString()}"
    }.getOrDefault("play_services=not_found")
    return "network=$transport internet=$hasInternet validated=$validated $playServices"
}

private fun normalizeCustomButtonLabel(value: String?, language: String): String {
    val trimmed = value.orEmpty().trim()
    if (trimmed.isBlank()) return ""
    // v0.1 defaults were fixed English/Spanish labels and therefore defeated localisation.
    if (trimmed == "Speak") return ""
    if (trimmed == "Habla" && MlKitLanguageCatalog.canonicalCode(language, language) == "es") return ""
    return trimmed
}

private fun localeFor(
    language: String,
    advertisedLocales: Set<String> = emptySet(),
    localeOverride: String = "",
    arabicVariant: String = ""
): Locale = Locale.forLanguageTag(
    ConversationLanguageSupport.speechLocaleTag(
        language,
        advertisedLocales,
        localeOverride = localeOverride,
        arabicVariant = arabicVariant
    )
)

private fun nativeLanguageLabel(language: String): String {
    val locale = localeFor(language)
    val native = locale.getDisplayLanguage(locale).trim()
    return native.ifBlank { languageLabel(language) }
}
