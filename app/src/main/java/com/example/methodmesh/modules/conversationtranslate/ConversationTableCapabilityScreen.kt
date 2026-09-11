package com.example.methodmesh.modules.conversationtranslate

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import java.time.Instant
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

private data class TableSeat(
    val id: String,
    val language: String,
    val flag: String,
    val speechLocale: String,
    val arabicVariant: String,
    val voicePreference: String
)

private data class TableTurn(
    val sourceSeat: String,
    val sourceLanguage: String,
    val originalText: String,
    val translations: LinkedHashMap<String, String>,
    val timeIso: String
)

private data class TableSpeechRequest(
    val text: String,
    val targetSeatId: String,
    val speakerId: String,
    val onDone: () -> Unit = {}
)

object ConversationTranslateTableCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100ConversationTranslateTableMethod.ID
    override val title = "Four-person conversation translator"
    override val description = "Put the device on the table. Each person chooses their language and presses their own Talk button."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val androidContext = LocalContext.current
        fun setting(name: String, fallback: String): String =
            context.action.settings[name] ?: context.action.settings["input_$name"] ?: fallback

        var languageA by rememberSaveable { mutableStateOf(MlKitLanguageCatalog.canonicalCode(setting("language_a", "en"), "en")) }
        var languageB by rememberSaveable { mutableStateOf(MlKitLanguageCatalog.canonicalCode(setting("language_b", "fr"), "fr")) }
        var languageC by rememberSaveable { mutableStateOf(MlKitLanguageCatalog.canonicalCode(setting("language_c", "de"), "de")) }
        var languageD by rememberSaveable { mutableStateOf(MlKitLanguageCatalog.canonicalCode(setting("language_d", "ko"), "ko")) }
        var flagA by rememberSaveable { mutableStateOf(defaultFlagForLanguage(languageA)) }
        var flagB by rememberSaveable { mutableStateOf(defaultFlagForLanguage(languageB)) }
        var flagC by rememberSaveable { mutableStateOf(defaultFlagForLanguage(languageC)) }
        var flagD by rememberSaveable { mutableStateOf(defaultFlagForLanguage(languageD)) }
        var speechLocaleA by rememberSaveable { mutableStateOf(setting("speech_locale_a", "")) }
        var speechLocaleB by rememberSaveable { mutableStateOf(setting("speech_locale_b", "")) }
        var speechLocaleC by rememberSaveable { mutableStateOf(setting("speech_locale_c", "")) }
        var speechLocaleD by rememberSaveable { mutableStateOf(setting("speech_locale_d", "")) }
        var arabicVariantA by rememberSaveable { mutableStateOf(setting("arabic_variant_a", ARABIC_VARIANT_GULF)) }
        var arabicVariantB by rememberSaveable { mutableStateOf(setting("arabic_variant_b", ARABIC_VARIANT_GULF)) }
        var arabicVariantC by rememberSaveable { mutableStateOf(setting("arabic_variant_c", ARABIC_VARIANT_GULF)) }
        var arabicVariantD by rememberSaveable { mutableStateOf(setting("arabic_variant_d", ARABIC_VARIANT_GULF)) }
        var voicePreferenceA by rememberSaveable { mutableStateOf(normalizeConversationVoicePreference(setting("voice_a", CONVERSATION_VOICE_FEMALE))) }
        var voicePreferenceB by rememberSaveable { mutableStateOf(normalizeConversationVoicePreference(setting("voice_b", CONVERSATION_VOICE_FEMALE))) }
        var voicePreferenceC by rememberSaveable { mutableStateOf(normalizeConversationVoicePreference(setting("voice_c", CONVERSATION_VOICE_FEMALE))) }
        var voicePreferenceD by rememberSaveable { mutableStateOf(normalizeConversationVoicePreference(setting("voice_d", CONVERSATION_VOICE_FEMALE))) }
        var spokenOutput by rememberSaveable { mutableStateOf(setting("spoken_output", "true").equals("true", true)) }
        var preferOffline by rememberSaveable { mutableStateOf(setting("prefer_offline", "false").equals("true", true)) }
        var transcriptEnabled by rememberSaveable { mutableStateOf(setting("transcript_on_start", "true").equals("true", true)) }
        var transcriptEventsJson by rememberSaveable(context.action.canonicalId) { mutableStateOf("[]") }
        var turnsJson by rememberSaveable(context.action.canonicalId) { mutableStateOf("[]") }
        var conversationHasOccurred by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        var unrecordedSpeechSinceMarker by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        var transcriptPausedByUser by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        var latestA by rememberSaveable { mutableStateOf("") }
        var latestB by rememberSaveable { mutableStateOf("") }
        var latestC by rememberSaveable { mutableStateOf("") }
        var latestD by rememberSaveable { mutableStateOf("") }
        var latestVoiceA by rememberSaveable { mutableStateOf(CONVERSATION_VOICE_FEMALE) }
        var latestVoiceB by rememberSaveable { mutableStateOf(CONVERSATION_VOICE_FEMALE) }
        var latestVoiceC by rememberSaveable { mutableStateOf(CONVERSATION_VOICE_FEMALE) }
        var latestVoiceD by rememberSaveable { mutableStateOf(CONVERSATION_VOICE_FEMALE) }
        var latestSpeakerA by rememberSaveable { mutableStateOf("a") }
        var latestSpeakerB by rememberSaveable { mutableStateOf("b") }
        var latestSpeakerC by rememberSaveable { mutableStateOf("c") }
        var latestSpeakerD by rememberSaveable { mutableStateOf("d") }
        var spokenProgressA by rememberSaveable { mutableStateOf(0) }
        var spokenProgressB by rememberSaveable { mutableStateOf(0) }
        var spokenProgressC by rememberSaveable { mutableStateOf(0) }
        var spokenProgressD by rememberSaveable { mutableStateOf(0) }
        var status by rememberSaveable { mutableStateOf("Each person can choose a flag, then press Talk.") }
        var listeningSeat by rememberSaveable { mutableStateOf<String?>(null) }
        var hasAudioPermission by remember {
            mutableStateOf(ContextCompat.checkSelfPermission(androidContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
        }
        var advertisedSpeechLocalesPacked by rememberSaveable { mutableStateOf("") }
        val advertisedSpeechLocales = remember(advertisedSpeechLocalesPacked) {
            advertisedSpeechLocalesPacked.split('\u001F').map { it.trim() }.filter { it.isNotBlank() }.toSet()
        }
        var startedAt by rememberSaveable(context.action.canonicalId) { mutableStateOf(Instant.now().toString()) }
        var resultValuesJson by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var tableOpen by rememberSaveable(context.action.canonicalId) { mutableStateOf(context.startsImmediately) }
        var ttsReady by remember { mutableStateOf(false) }
        val tts = remember {
            TextToSpeech(androidContext.applicationContext) { state -> ttsReady = state == TextToSpeech.SUCCESS }
        }
        val supportedCodes = remember { MlKitLanguageCatalog.supportedCodes() }
        val requiredCodes = listOf(languageA, languageB, languageC, languageD).distinct()
        val unsupported = requiredCodes.filter { it !in supportedCodes }

        fun seats(): List<TableSeat> = listOf(
            TableSeat("a", languageA, flagA, speechLocaleA, arabicVariantA, voicePreferenceA),
            TableSeat("b", languageB, flagB, speechLocaleB, arabicVariantB, voicePreferenceB),
            TableSeat("c", languageC, flagC, speechLocaleC, arabicVariantC, voicePreferenceC),
            TableSeat("d", languageD, flagD, speechLocaleD, arabicVariantD, voicePreferenceD)
        )

        fun currentVoicePreference(speakerId: String): String = when (speakerId) {
            "a" -> voicePreferenceA
            "b" -> voicePreferenceB
            "c" -> voicePreferenceC
            "d" -> voicePreferenceD
            else -> CONVERSATION_VOICE_FEMALE
        }

        fun updateSpeechProgressForLanguage(language: String, endCharacter: Int) {
            if (languageA == language) spokenProgressA = endCharacter
            if (languageB == language) spokenProgressB = endCharacter
            if (languageC == language) spokenProgressC = endCharacter
            if (languageD == language) spokenProgressD = endCharacter
        }

        val mainHandler = remember { Handler(Looper.getMainLooper()) }
        val speechQueue = remember { java.util.ArrayDeque<TableSpeechRequest>() }
        var activeSpeechRequest by remember { mutableStateOf<TableSpeechRequest?>(null) }
        var activeUtteranceId by remember { mutableStateOf<String?>(null) }
        lateinit var startNextSpeech: () -> Unit

        fun completeActiveSpeech() {
            val completed = activeSpeechRequest
            activeSpeechRequest = null
            activeUtteranceId = null
            completed?.onDone?.invoke()
            startNextSpeech()
        }

        startNextSpeech = startNextSpeech@{
            if (activeSpeechRequest != null) return@startNextSpeech
            val request = speechQueue.pollFirst() ?: return@startNextSpeech
            if (!spokenOutput || request.text.isBlank() || !ttsReady) {
                request.onDone()
                startNextSpeech()
                return@startNextSpeech
            }
            val targetSeat = seats().firstOrNull { it.id == request.targetSeatId }
            if (targetSeat == null) {
                request.onDone()
                startNextSpeech()
                return@startNextSpeech
            }
            val localeTag = ConversationLanguageSupport.speechLocaleTag(
                targetSeat.language,
                advertisedSpeechLocales,
                localeOverride = targetSeat.speechLocale,
                arabicVariant = targetSeat.arabicVariant
            )
            val locale = Locale.forLanguageTag(localeTag)
            if (tts.setLanguage(locale) < TextToSpeech.LANG_AVAILABLE) {
                request.onDone()
                startNextSpeech()
                return@startNextSpeech
            }

            // Voice selection is deliberately resolved immediately before every
            // utterance. TTS voice/language are engine-global state, so serialising
            // speech prevents a later participant from changing a queued turn's
            // voice before synthesis begins.
            tts.applyConversationVoicePreference(
                locale = locale,
                preference = currentVoicePreference(request.speakerId),
                speakerKey = request.speakerId
            )
            val utteranceId = "methodmesh-table-${request.targetSeatId}-${System.currentTimeMillis()}"
            activeSpeechRequest = request
            activeUtteranceId = utteranceId
            updateSpeechProgressForLanguage(targetSeat.language, 0)
            val speakResult = tts.speak(request.text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            if (speakResult == TextToSpeech.ERROR) {
                activeSpeechRequest = null
                activeUtteranceId = null
                request.onDone()
                startNextSpeech()
            }
        }

        fun enqueueSpeech(request: TableSpeechRequest) {
            speechQueue.addLast(request)
            startNextSpeech()
        }

        fun updateChoice(id: String, choice: ParticipantLanguageChoice) {
            val language = MlKitLanguageCatalog.canonicalCode(choice.language, "en")
            val flag = choice.flag.ifBlank { defaultFlagForLanguage(language) }
            when (id) {
                "a" -> { languageA = language; flagA = flag; speechLocaleA = choice.speechLocaleOverride; if (choice.arabicVariant.isNotBlank()) arabicVariantA = choice.arabicVariant }
                "b" -> { languageB = language; flagB = flag; speechLocaleB = choice.speechLocaleOverride; if (choice.arabicVariant.isNotBlank()) arabicVariantB = choice.arabicVariant }
                "c" -> { languageC = language; flagC = flag; speechLocaleC = choice.speechLocaleOverride; if (choice.arabicVariant.isNotBlank()) arabicVariantC = choice.arabicVariant }
                "d" -> { languageD = language; flagD = flag; speechLocaleD = choice.speechLocaleOverride; if (choice.arabicVariant.isNotBlank()) arabicVariantD = choice.arabicVariant }
            }
        }

        fun updateArabicVariant(id: String, variant: String) {
            when (id) {
                "a" -> { arabicVariantA = variant; speechLocaleA = "" }
                "b" -> { arabicVariantB = variant; speechLocaleB = "" }
                "c" -> { arabicVariantC = variant; speechLocaleC = "" }
                "d" -> { arabicVariantD = variant; speechLocaleD = "" }
            }
        }

        fun toggleVoicePreference(id: String) {
            val next = when (id) {
                "a" -> toggleConversationVoicePreference(voicePreferenceA)
                "b" -> toggleConversationVoicePreference(voicePreferenceB)
                "c" -> toggleConversationVoicePreference(voicePreferenceC)
                else -> toggleConversationVoicePreference(voicePreferenceD)
            }
            when (id) {
                "a" -> voicePreferenceA = next
                "b" -> voicePreferenceB = next
                "c" -> voicePreferenceC = next
                "d" -> voicePreferenceD = next
            }
            // Replays of an existing turn from this speaker should immediately
            // reflect the newly selected voice profile.
            if (latestSpeakerA == id) latestVoiceA = next
            if (latestSpeakerB == id) latestVoiceB = next
            if (latestSpeakerC == id) latestVoiceC = next
            if (latestSpeakerD == id) latestVoiceD = next
        }

        fun updateLatestForLanguage(language: String, text: String, speakerVoice: String, speakerId: String) {
            if (languageA == language) { latestA = text; latestVoiceA = speakerVoice; latestSpeakerA = speakerId }
            if (languageB == language) { latestB = text; latestVoiceB = speakerVoice; latestSpeakerB = speakerId }
            if (languageC == language) { latestC = text; latestVoiceC = speakerVoice; latestSpeakerC = speakerId }
            if (languageD == language) { latestD = text; latestVoiceD = speakerVoice; latestSpeakerD = speakerId }
        }

        fun latestForSeat(id: String): String = when (id) {
            "a" -> latestA
            "b" -> latestB
            "c" -> latestC
            else -> latestD
        }

        fun latestVoiceForSeat(id: String): String = when (id) {
            "a" -> latestVoiceA
            "b" -> latestVoiceB
            "c" -> latestVoiceC
            else -> latestVoiceD
        }

        fun latestSpeakerForSeat(id: String): String = when (id) {
            "a" -> latestSpeakerA
            "b" -> latestSpeakerB
            "c" -> latestSpeakerC
            else -> latestSpeakerD
        }

        fun setTranscriptRecording(enabled: Boolean) {
            if (enabled == transcriptEnabled) return
            val now = Instant.now().toString()
            if (enabled) {
                if (transcriptPausedByUser || (conversationHasOccurred && unrecordedSpeechSinceMarker)) {
                    transcriptEventsJson = appendTableMarker(
                        transcriptEventsJson,
                        if (!transcriptPausedByUser && tableTurns(turnsJson).isEmpty()) "started_after_gap" else "resumed",
                        now,
                        when {
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
                transcriptEventsJson = appendTableMarker(
                    transcriptEventsJson,
                    "paused",
                    now,
                    "Transcript paused. Conversation and translation continue without recording."
                )
                transcriptPausedByUser = true
                status = "Transcript off · translation continues."
            }
            transcriptEnabled = enabled
        }

        fun speakForSeat(
            text: String,
            targetSeatId: String,
            speakerId: String,
            onDone: () -> Unit = {}
        ) {
            enqueueSpeech(
                TableSpeechRequest(
                    text = text,
                    targetSeatId = targetSeatId,
                    speakerId = speakerId,
                    onDone = onDone
                )
            )
        }

        fun finishConversation(state: String = "succeeded", error: String = "") {
            val values = tableValues(
                turnsJson = turnsJson,
                eventsJson = transcriptEventsJson,
                transcriptEnabled = transcriptEnabled,
                seats = seats(),
                spokenOutput = spokenOutput,
                preferOffline = preferOffline,
                startedAt = startedAt,
                status = state,
                error = error
            )
            val request = As100ConversationTranslateTableMethod.request(
                action = As100ConversationTranslateTableMethod.ID,
                context = context.request.invocationContext.asMap(As100ConversationTranslateTableMethod.ID) + context.action.settings,
                signals = emptyList(),
                inputs = emptyList()
            )
            val execution = As100ConversationTranslateTableMethod.result(request, values, context.request.invocationContext)
            result = execution
            resultValuesJson = JSONObject().apply { values.forEach { (key, value) -> put(key, value) } }.toString()
            tableOpen = false
            if (context.submitsImmediately && state == "succeeded") onConfirmed(execution)
        }

        fun recordCompletedTurn(sourceSeat: TableSeat, original: String, translations: LinkedHashMap<String, String>) {
            val turn = TableTurn(sourceSeat.id, sourceSeat.language, original, translations, Instant.now().toString())
            conversationHasOccurred = true
            if (transcriptEnabled) {
                turnsJson = appendTableTurn(turnsJson, turn)
                transcriptEventsJson = appendTableTurnEvent(transcriptEventsJson, turn)
            } else {
                unrecordedSpeechSinceMarker = true
            }
            status = if (transcriptEnabled) "Ready · transcript on." else "Ready · transcript off."
        }

        fun translateTurn(sourceSeatId: String, text: String) {
            val currentSeats = seats()
            val sourceIndex = currentSeats.indexOfFirst { it.id == sourceSeatId }
            if (sourceIndex < 0 || text.isBlank()) {
                status = "No speech detected."
                return
            }
            val sourceSeat = currentSeats[sourceIndex]
            updateLatestForLanguage(sourceSeat.language, text, currentVoicePreference(sourceSeat.id), sourceSeat.id)

            val clockwise = (1..3).map { step -> currentSeats[(sourceIndex + step) % currentSeats.size] }
            val distinctTargets = clockwise
                .filter { it.language != sourceSeat.language }
                .distinctBy { it.language }

            if (distinctTargets.isEmpty()) {
                recordCompletedTurn(sourceSeat, text, linkedMapOf())
                status = "Shared language · no translation needed."
                return
            }

            val translations = linkedMapOf<String, String>()
            lateinit var translateNext: (Int) -> Unit
            translateNext = { index ->
                if (index >= distinctTargets.size) {
                    recordCompletedTurn(sourceSeat, text, translations)
                } else {
                    val targetSeat = distinctTargets[index]
                    status = "Translating ${nativeConversationLanguageLabel(targetSeat.language)} ${index + 1}/${distinctTargets.size}…"
                    val translator = Translation.getClient(
                        TranslatorOptions.Builder()
                            .setSourceLanguage(sourceSeat.language)
                            .setTargetLanguage(targetSeat.language)
                            .build()
                    )
                    translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
                        .addOnSuccessListener {
                            translator.translate(text)
                                .addOnSuccessListener { translated ->
                                    translations[targetSeat.language] = translated
                                    updateLatestForLanguage(targetSeat.language, translated, currentVoicePreference(sourceSeat.id), sourceSeat.id)
                                    translator.close()
                                    speakForSeat(
                                        text = translated,
                                        targetSeatId = targetSeat.id,
                                        speakerId = sourceSeat.id,
                                        onDone = { translateNext(index + 1) }
                                    )
                                }
                                .addOnFailureListener { error ->
                                    translator.close()
                                    status = "Translation to ${nativeConversationLanguageLabel(targetSeat.language)} failed: ${error.message.orEmpty()}"
                                }
                        }
                        .addOnFailureListener { error ->
                            translator.close()
                            status = "Translation model unavailable for ${nativeConversationLanguageLabel(targetSeat.language)}: ${error.message.orEmpty()}"
                        }
                }
            }
            translateNext(0)
        }

        val recognizer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { activityResult ->
            val sourceSeat = listeningSeat
            listeningSeat = null
            if (activityResult.resultCode != Activity.RESULT_OK || sourceSeat == null) {
                status = "Listening cancelled."
                return@rememberLauncherForActivityResult
            }
            val text = activityResult.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                .orEmpty()
                .firstOrNull()
                .orEmpty()
            translateTurn(sourceSeat, text)
        }

        val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasAudioPermission = granted
            status = if (granted) "Microphone ready." else "Microphone permission is needed."
        }

        fun listen(seatId: String) {
            if (!hasAudioPermission) {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                return
            }
            if (unsupported.isNotEmpty()) {
                status = "Translation language unavailable: ${unsupported.joinToString { MlKitLanguageCatalog.info(it).name }}"
                return
            }
            val seat = seats().first { it.id == seatId }
            if (advertisedSpeechLocales.isNotEmpty() && !ConversationLanguageSupport.isAdvertisedByRecognizer(seat.language, advertisedSpeechLocales)) {
                status = "This Android speech recogniser does not advertise ${MlKitLanguageCatalog.info(seat.language).name}."
                return
            }
            val speechLocale = ConversationLanguageSupport.speechLocaleTag(
                seat.language,
                advertisedSpeechLocales,
                localeOverride = seat.speechLocale,
                arabicVariant = seat.arabicVariant
            )
            listeningSeat = seatId
            status = "Listening…"
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, speechLocale)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, speechLocale)
                putExtra(RecognizerIntent.EXTRA_PROMPT, ConversationLanguageSupport.initialInstruction(seat.language))
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            try {
                recognizer.launch(intent)
            } catch (_: ActivityNotFoundException) {
                listeningSeat = null
                status = "Android speech recognition is not available on this device."
            }
        }

        LaunchedEffect(Unit) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: android.content.Context?, intent: Intent?) {
                    val supported = getResultExtras(false)
                        ?.getStringArrayList(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES)
                        .orEmpty()
                    if (supported.isNotEmpty()) advertisedSpeechLocalesPacked = supported.distinct().joinToString("\u001F")
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

        LaunchedEffect(languageA, languageB, languageC, languageD, arabicVariantA, arabicVariantB, arabicVariantC, arabicVariantD, voicePreferenceA, voicePreferenceB, voicePreferenceC, voicePreferenceD, spokenOutput, preferOffline, transcriptEnabled) {
            context.onSettingsChanged(
                mapOf(
                    "language_a" to languageA,
                    "language_b" to languageB,
                    "language_c" to languageC,
                    "language_d" to languageD,
                    "arabic_variant_a" to arabicVariantA,
                    "arabic_variant_b" to arabicVariantB,
                    "arabic_variant_c" to arabicVariantC,
                    "arabic_variant_d" to arabicVariantD,
                    "voice_a" to voicePreferenceA,
                    "voice_b" to voicePreferenceB,
                    "voice_c" to voicePreferenceC,
                    "voice_d" to voicePreferenceD,
                    "spoken_output" to spokenOutput.toString(),
                    "prefer_offline" to preferOffline.toString(),
                    "transcript_on_start" to transcriptEnabled.toString()
                )
            )
        }

        DisposableEffect(tts) {
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                    val id = utteranceId ?: return
                    mainHandler.post {
                        if (activeUtteranceId != id) return@post
                        val request = activeSpeechRequest ?: return@post
                        val targetLanguage = seats().firstOrNull { it.id == request.targetSeatId }?.language ?: return@post
                        updateSpeechProgressForLanguage(targetLanguage, end)
                    }
                }

                override fun onDone(utteranceId: String?) {
                    val id = utteranceId ?: return
                    mainHandler.post {
                        if (activeUtteranceId != id) return@post
                        val request = activeSpeechRequest
                        val targetLanguage = request?.let { req -> seats().firstOrNull { it.id == req.targetSeatId }?.language }
                        if (request != null && targetLanguage != null) {
                            updateSpeechProgressForLanguage(targetLanguage, request.text.length)
                        }
                        completeActiveSpeech()
                    }
                }

                @Deprecated("Deprecated in Android")
                override fun onError(utteranceId: String?) {
                    val id = utteranceId ?: return
                    mainHandler.post { if (activeUtteranceId == id) completeActiveSpeech() }
                }

                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    val id = utteranceId ?: return
                    mainHandler.post { if (activeUtteranceId == id) completeActiveSpeech() }
                }
            })
            onDispose {
                speechQueue.clear()
                activeSpeechRequest = null
                activeUtteranceId = null
                mainHandler.removeCallbacksAndMessages(null)
                tts.stop()
                tts.shutdown()
            }
        }

        val capturedResult = result ?: remember(resultValuesJson) {
            resultValuesJson?.let { json ->
                val root = JSONObject(json)
                val values: Map<String, String> = buildMap { root.keys().forEach { key -> put(key, root.optString(key)) } }
                As100ConversationTranslateTableMethod.result(
                    request = As100ConversationTranslateTableMethod.request(
                        action = As100ConversationTranslateTableMethod.ID,
                        context = context.request.invocationContext.asMap(As100ConversationTranslateTableMethod.ID) + context.action.settings + values,
                        signals = emptyList(),
                        inputs = emptyList()
                    ),
                    values = values,
                    invocation = context.request.invocationContext
                )
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
                    ConversationTableFields.ARABIC_VARIANT_A, ConversationTableFields.ARABIC_VARIANT_B,
                    ConversationTableFields.ARABIC_VARIANT_C, ConversationTableFields.ARABIC_VARIANT_D,
                    ConversationTableFields.SPEECH_LOCALE_A, ConversationTableFields.SPEECH_LOCALE_B,
                    ConversationTableFields.SPEECH_LOCALE_C, ConversationTableFields.SPEECH_LOCALE_D,
                    ConversationTableFields.VOICE_A, ConversationTableFields.VOICE_B,
                    ConversationTableFields.VOICE_C, ConversationTableFields.VOICE_D
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
                latestA = ""; latestB = ""; latestC = ""; latestD = ""
                latestVoiceA = CONVERSATION_VOICE_FEMALE; latestVoiceB = CONVERSATION_VOICE_FEMALE
                latestVoiceC = CONVERSATION_VOICE_FEMALE; latestVoiceD = CONVERSATION_VOICE_FEMALE
                latestSpeakerA = "a"; latestSpeakerB = "b"; latestSpeakerC = "c"; latestSpeakerD = "d"
                spokenProgressA = 0; spokenProgressB = 0; spokenProgressC = 0; spokenProgressD = 0
                startedAt = Instant.now().toString()
                tableOpen = context.startsImmediately
                status = "Conversation cleared."
            },
            onConfirm = { capturedResult?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            if (capturedResult == null && !tableOpen) {
                Text("Put the device in the middle of the table. Anyone can choose their language by flag, then press their own Talk button.")
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { spokenOutput = !spokenOutput }, modifier = Modifier.weight(1f)) {
                        Text(if (spokenOutput) "Spoken output ON" else "Spoken output OFF")
                    }
                    OutlinedButton(onClick = { transcriptEnabled = !transcriptEnabled }, modifier = Modifier.weight(1f)) {
                        Text(if (transcriptEnabled) "Transcript starts ON" else "Transcript starts OFF")
                    }
                }
                Spacer(Modifier.height(10.dp))
                Button(onClick = { tableOpen = true }, modifier = Modifier.fillMaxWidth().height(60.dp)) {
                    Text("Open four-person table")
                }
            }
        }

        if (capturedResult == null && tableOpen) {
            Dialog(
                onDismissRequest = { if (!context.startsImmediately) tableOpen = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                FourSeatTableSurface(
                    seats = seats(),
                    latestTexts = mapOf("a" to latestA, "b" to latestB, "c" to latestC, "d" to latestD),
                    spokenProgress = mapOf("a" to spokenProgressA, "b" to spokenProgressB, "c" to spokenProgressC, "d" to spokenProgressD),
                    transcriptEnabled = transcriptEnabled,
                    status = status,
                    onTranscriptToggle = { setTranscriptRecording(!transcriptEnabled) },
                    onLanguageChoice = ::updateChoice,
                    onArabicVariant = ::updateArabicVariant,
                    onVoiceToggle = ::toggleVoicePreference,
                    onTalk = ::listen,
                    onReplay = { seatId ->
                        val seat = seats().first { it.id == seatId }
                        speakForSeat(
                            text = latestForSeat(seatId),
                            targetSeatId = seat.id,
                            speakerId = latestSpeakerForSeat(seatId)
                        )
                    },
                    onEnd = { finishConversation() }
                )
            }
        }
    }
}

@Composable
private fun FourSeatTableSurface(
    seats: List<TableSeat>,
    latestTexts: Map<String, String>,
    spokenProgress: Map<String, Int>,
    transcriptEnabled: Boolean,
    status: String,
    onTranscriptToggle: () -> Unit,
    onLanguageChoice: (String, ParticipantLanguageChoice) -> Unit,
    onArabicVariant: (String, String) -> Unit,
    onVoiceToggle: (String) -> Unit,
    onTalk: (String) -> Unit,
    onReplay: (String) -> Unit,
    onEnd: () -> Unit
) {
    val surface = MaterialTheme.colorScheme.surface
    val topColor = lerp(surface, MaterialTheme.colorScheme.primaryContainer, 0.72f)
    val leftColor = lerp(surface, MaterialTheme.colorScheme.secondaryContainer, 0.82f)
    val rightColor = lerp(surface, MaterialTheme.colorScheme.tertiaryContainer, 0.82f)
    val bottomColor = lerp(surface, MaterialTheme.colorScheme.primaryContainer, 0.48f)
    val dividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.78f)
    val byId = seats.associateBy { it.id }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Surface(
                    modifier = Modifier.fillMaxWidth().weight(0.27f),
                    color = topColor
                ) {
                    byId["c"]?.let { seat ->
                        TableSeatPanel(
                            seat = seat,
                            latestText = latestTexts["c"].orEmpty(),
                            spokenProgress = spokenProgress["c"] ?: 0,
                            rotationDegrees = 180f,
                            onLanguageChoice = { onLanguageChoice("c", it) },
                            onArabicVariant = { onArabicVariant("c", it) },
                            onVoiceToggle = { onVoiceToggle("c") },
                            onTalk = { onTalk("c") },
                            onReplay = { onReplay("c") }
                        )
                    }
                }

                Surface(Modifier.fillMaxWidth().height(2.dp), color = dividerColor) {}

                Row(Modifier.fillMaxWidth().weight(0.46f)) {
                    Surface(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        color = leftColor
                    ) {
                        byId["d"]?.let { seat ->
                            TableSeatPanel(
                                seat = seat,
                                latestText = latestTexts["d"].orEmpty(),
                                spokenProgress = spokenProgress["d"] ?: 0,
                                rotationDegrees = 90f,
                                onLanguageChoice = { onLanguageChoice("d", it) },
                                onArabicVariant = { onArabicVariant("d", it) },
                                onVoiceToggle = { onVoiceToggle("d") },
                                onTalk = { onTalk("d") },
                                onReplay = { onReplay("d") }
                            )
                        }
                    }

                    Surface(Modifier.width(2.dp).fillMaxHeight(), color = dividerColor) {}

                    Surface(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        color = rightColor
                    ) {
                        byId["b"]?.let { seat ->
                            TableSeatPanel(
                                seat = seat,
                                latestText = latestTexts["b"].orEmpty(),
                                spokenProgress = spokenProgress["b"] ?: 0,
                                rotationDegrees = -90f,
                                onLanguageChoice = { onLanguageChoice("b", it) },
                                onArabicVariant = { onArabicVariant("b", it) },
                                onVoiceToggle = { onVoiceToggle("b") },
                                onTalk = { onTalk("b") },
                                onReplay = { onReplay("b") }
                            )
                        }
                    }
                }

                Surface(Modifier.fillMaxWidth().height(2.dp), color = dividerColor) {}

                Surface(
                    modifier = Modifier.fillMaxWidth().weight(0.27f),
                    color = bottomColor
                ) {
                    byId["a"]?.let { seat ->
                        TableSeatPanel(
                            seat = seat,
                            latestText = latestTexts["a"].orEmpty(),
                            spokenProgress = spokenProgress["a"] ?: 0,
                            rotationDegrees = 0f,
                            onLanguageChoice = { onLanguageChoice("a", it) },
                            onArabicVariant = { onArabicVariant("a", it) },
                            onVoiceToggle = { onVoiceToggle("a") },
                            onTalk = { onTalk("a") },
                            onReplay = { onReplay("a") }
                        )
                    }
                }
            }

            val showCentralStatus = status.isNotBlank() &&
                !status.startsWith("Each person") &&
                !status.startsWith("Ready") &&
                !status.startsWith("Conversation cleared")
            if (showCentralStatus) {
                Surface(
                    modifier = Modifier.align(Alignment.Center),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
                    tonalElevation = 2.dp
                ) {
                    Text(
                        text = status,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                        maxLines = 2
                    )
                }
            }

            OutlinedButton(
                onClick = onEnd,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).height(36.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            ) {
                Text("End", style = MaterialTheme.typography.labelMedium)
            }

            Surface(
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                shape = MaterialTheme.shapes.small,
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text("Transcript", style = MaterialTheme.typography.labelSmall)
                    Switch(
                        checked = transcriptEnabled,
                        onCheckedChange = { onTranscriptToggle() },
                        modifier = Modifier.scale(0.72f)
                    )
                }
            }
        }
    }
}

@Composable
private fun TableSeatPanel(
    seat: TableSeat,
    latestText: String,
    spokenProgress: Int,
    rotationDegrees: Float,
    onLanguageChoice: (ParticipantLanguageChoice) -> Unit,
    onArabicVariant: (String) -> Unit,
    onVoiceToggle: () -> Unit,
    onTalk: () -> Unit,
    onReplay: () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sideways = rotationDegrees == 90f || rotationDegrees == -90f
        val unrotatedModifier = if (sideways) {
            Modifier.width(maxHeight).height(maxWidth)
        } else {
            Modifier.width(maxWidth).height(maxHeight)
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            TableSeatControls(
                seat = seat,
                latestText = latestText,
                spokenProgress = spokenProgress,
                rotationDegrees = rotationDegrees,
                modifier = unrotatedModifier,
                onLanguageChoice = onLanguageChoice,
                onArabicVariant = onArabicVariant,
                onVoiceToggle = onVoiceToggle,
                onTalk = onTalk,
                onReplay = onReplay
            )
        }
    }
}

@Composable
private fun TableSeatControls(
    seat: TableSeat,
    latestText: String,
    spokenProgress: Int,
    rotationDegrees: Float,
    modifier: Modifier,
    onLanguageChoice: (ParticipantLanguageChoice) -> Unit,
    onArabicVariant: (String) -> Unit,
    onVoiceToggle: () -> Unit,
    onTalk: () -> Unit,
    onReplay: () -> Unit
) {
    Column(
        modifier = modifier.rotate(rotationDegrees).padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // In participant coordinates this is the inner/table boundary. Keeping
        // language here leaves the central body of each tile for translated text.
        ParticipantLanguageButton(
            selectedLanguage = seat.language,
            selectedFlag = seat.flag,
            rotationDegrees = rotationDegrees,
            modifier = Modifier.fillMaxWidth(),
            onSelected = onLanguageChoice
        )
        if (seat.language == "ar") {
            Spacer(Modifier.height(3.dp))
            ArabicVariantButton(
                language = seat.language,
                variant = seat.arabicVariant,
                rotationDegrees = rotationDegrees,
                onVariantSelected = onArabicVariant
            )
        }

        SpokenTextScroller(
            text = latestText.ifBlank { ConversationLanguageSupport.initialInstruction(seat.language) },
            isLiveTranslation = latestText.isNotBlank(),
            spokenProgress = spokenProgress,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp)
        )

        // The action strip is the outer edge of every participant tile after
        // rotation. Talk stays one line high; voice and replay remain discrete.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            OutlinedButton(
                onClick = onVoiceToggle,
                modifier = Modifier.width(44.dp).height(48.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    conversationVoiceSymbol(seat.voicePreference),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Button(
                onClick = onTalk,
                modifier = Modifier.weight(1f).height(50.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    ConversationLanguageSupport.pressToSpeak(seat.language),
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
            OutlinedButton(
                onClick = onReplay,
                enabled = latestText.isNotBlank(),
                modifier = Modifier.width(44.dp).height(48.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("↻", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SpokenTextScroller(
    text: String,
    isLiveTranslation: Boolean,
    spokenProgress: Int,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(text) {
        scrollState.scrollTo(0)
    }
    LaunchedEffect(spokenProgress, scrollState.maxValue, text) {
        if (!isLiveTranslation || text.isBlank() || scrollState.maxValue <= 0) return@LaunchedEffect
        val fraction = (spokenProgress.toFloat() / text.length.coerceAtLeast(1)).coerceIn(0f, 1f)
        scrollState.animateScrollTo((scrollState.maxValue * fraction).toInt())
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth().verticalScroll(scrollState),
            textAlign = TextAlign.Center,
            style = if (isLiveTranslation) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.labelMedium,
            fontWeight = if (isLiveTranslation) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

private fun appendTableTurn(json: String, turn: TableTurn): String {
    val array = JSONArray(json.ifBlank { "[]" })
    array.put(tableTurnJson(turn))
    return array.toString()
}

private fun appendTableTurnEvent(json: String, turn: TableTurn): String {
    val array = JSONArray(json.ifBlank { "[]" })
    array.put(tableTurnJson(turn).put("event_type", "turn"))
    return array.toString()
}

private fun appendTableMarker(json: String, type: String, timeIso: String, note: String): String {
    val array = JSONArray(json.ifBlank { "[]" })
    array.put(JSONObject().put("event_type", type).put("time_iso", timeIso).put("note", note))
    return array.toString()
}

private fun tableTurnJson(turn: TableTurn): JSONObject = JSONObject()
    .put("source_seat", turn.sourceSeat)
    .put("source_language", turn.sourceLanguage)
    .put("original_text", turn.originalText)
    .put("translations", JSONObject().apply { turn.translations.forEach { (language, text) -> put(language, text) } })
    .put("time_iso", turn.timeIso)

private fun tableTurns(json: String): List<TableTurn> = runCatching {
    val array = JSONArray(json.ifBlank { "[]" })
    (0 until array.length()).map { index ->
        val item = array.getJSONObject(index)
        val translationsJson = item.optJSONObject("translations") ?: JSONObject()
        val translations = linkedMapOf<String, String>()
        translationsJson.keys().forEach { key -> translations[key] = translationsJson.optString(key) }
        TableTurn(
            sourceSeat = item.optString("source_seat"),
            sourceLanguage = item.optString("source_language"),
            originalText = item.optString("original_text"),
            translations = translations,
            timeIso = item.optString("time_iso")
        )
    }
}.getOrDefault(emptyList())

private fun tableTranscript(eventsJson: String, turnsJson: String): String = runCatching {
    val events = JSONArray(eventsJson.ifBlank { "[]" })
    if (events.length() == 0) return@runCatching tableTurns(turnsJson).joinToString("\n\n", transform = ::tableTurnText)
    buildList {
        for (index in 0 until events.length()) {
            val item = events.getJSONObject(index)
            if (item.optString("event_type") == "turn") {
                val translationsJson = item.optJSONObject("translations") ?: JSONObject()
                val translations = linkedMapOf<String, String>()
                translationsJson.keys().forEach { key -> translations[key] = translationsJson.optString(key) }
                add(tableTurnText(TableTurn(item.optString("source_seat"), item.optString("source_language"), item.optString("original_text"), translations, item.optString("time_iso"))))
            } else {
                add("[${item.optString("time_iso")}] ${item.optString("note")}")
            }
        }
    }.joinToString("\n\n")
}.getOrElse { tableTurns(turnsJson).joinToString("\n\n", transform = ::tableTurnText) }

private fun tableTurnText(turn: TableTurn): String = buildString {
    append("${nativeConversationLanguageLabel(turn.sourceLanguage)}: ${turn.originalText}")
    turn.translations.forEach { (language, text) -> append("\n${nativeConversationLanguageLabel(language)}: $text") }
}

private fun tableValues(
    turnsJson: String,
    eventsJson: String,
    transcriptEnabled: Boolean,
    seats: List<TableSeat>,
    spokenOutput: Boolean,
    preferOffline: Boolean,
    startedAt: String,
    status: String,
    error: String
): Map<String, String> {
    val byId = seats.associateBy { it.id }
    fun lang(id: String) = byId[id]?.language.orEmpty()
    fun variant(id: String) = byId[id]?.let { if (it.language == "ar") it.arabicVariant else "" }.orEmpty()
    fun locale(id: String) = byId[id]?.speechLocale.orEmpty()
    fun voice(id: String) = byId[id]?.voicePreference.orEmpty()
    return linkedMapOf(
        ConversationTableFields.TRANSCRIPT to tableTranscript(eventsJson, turnsJson),
        ConversationTableFields.TURNS_JSON to turnsJson,
        ConversationTableFields.EVENTS_JSON to eventsJson,
        ConversationTableFields.LANGUAGE_A to lang("a"),
        ConversationTableFields.LANGUAGE_B to lang("b"),
        ConversationTableFields.LANGUAGE_C to lang("c"),
        ConversationTableFields.LANGUAGE_D to lang("d"),
        ConversationTableFields.ARABIC_VARIANT_A to variant("a"),
        ConversationTableFields.ARABIC_VARIANT_B to variant("b"),
        ConversationTableFields.ARABIC_VARIANT_C to variant("c"),
        ConversationTableFields.ARABIC_VARIANT_D to variant("d"),
        ConversationTableFields.SPEECH_LOCALE_A to locale("a"),
        ConversationTableFields.SPEECH_LOCALE_B to locale("b"),
        ConversationTableFields.SPEECH_LOCALE_C to locale("c"),
        ConversationTableFields.SPEECH_LOCALE_D to locale("d"),
        ConversationTableFields.VOICE_A to voice("a"),
        ConversationTableFields.VOICE_B to voice("b"),
        ConversationTableFields.VOICE_C to voice("c"),
        ConversationTableFields.VOICE_D to voice("d"),
        ConversationTableFields.SPOKEN_OUTPUT to spokenOutput.toString(),
        ConversationTableFields.PREFER_OFFLINE to preferOffline.toString(),
        ConversationTableFields.TRANSCRIPT_ENABLED_AT_END to transcriptEnabled.toString(),
        ConversationTableFields.TURN_COUNT to tableTurns(turnsJson).size.toString(),
        ConversationTableFields.STARTED_TIME_ISO to startedAt,
        ConversationTableFields.FINISHED_TIME_ISO to Instant.now().toString(),
        ConversationTableFields.STATUS to status,
        ConversationTableFields.ERROR to error
    )
}
