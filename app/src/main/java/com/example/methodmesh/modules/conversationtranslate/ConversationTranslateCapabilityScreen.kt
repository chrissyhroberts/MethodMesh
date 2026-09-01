package com.example.methodmesh.modules.conversationtranslate

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
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
        var languageA by rememberSaveable { mutableStateOf(context.action.settings["language_a"] ?: context.action.settings["input_language_a"] ?: "en") }
        var languageB by rememberSaveable { mutableStateOf(context.action.settings["language_b"] ?: context.action.settings["input_language_b"] ?: "es") }
        var labelA by rememberSaveable { mutableStateOf(context.action.settings["label_a"] ?: context.action.settings["input_label_a"] ?: defaultButtonLabel(languageA)) }
        var labelB by rememberSaveable { mutableStateOf(context.action.settings["label_b"] ?: context.action.settings["input_label_b"] ?: defaultButtonLabel(languageB)) }
        var spokenOutput by rememberSaveable { mutableStateOf((context.action.settings["spoken_output"] ?: context.action.settings["input_spoken_output"] ?: "true").equals("true", true)) }
        var preferOffline by rememberSaveable { mutableStateOf((context.action.settings["prefer_offline"] ?: context.action.settings["input_prefer_offline"] ?: "true").equals("true", true)) }
        var hasAudioPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(androidContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
        var status by rememberSaveable { mutableStateOf("Choose who is speaking.") }
        var listeningSide by rememberSaveable { mutableStateOf<String?>(null) }
        var latestTranslated by rememberSaveable { mutableStateOf("") }
        var latestOriginal by rememberSaveable { mutableStateOf("") }
        var latestTextA by rememberSaveable { mutableStateOf("") }
        var latestTextB by rememberSaveable { mutableStateOf("") }
        var operatorFacing by rememberSaveable { mutableStateOf(false) }
        var conversationOpen by rememberSaveable(context.action.canonicalId) { mutableStateOf(context.startsImmediately) }
        var turnsJson by rememberSaveable(context.action.canonicalId) { mutableStateOf("[]") }
        var startedAt by rememberSaveable(context.action.canonicalId) { mutableStateOf(Instant.now().toString()) }
        var resultValuesJson by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var downloadedModelCodes by rememberSaveable { mutableStateOf("") }
        var modelStatus by rememberSaveable { mutableStateOf("Checking language packs…") }
        var busyLanguageCode by rememberSaveable { mutableStateOf<String?>(null) }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var ttsReady by remember { mutableStateOf(false) }
        val supportedLanguageCodes = remember { MlKitLanguageCatalog.supportedCodes() }
        val downloadedLanguages = remember(downloadedModelCodes) {
            downloadedModelCodes.split(',').map { it.trim() }.filter { it.isNotBlank() }.toSet()
        }
        val requiredTranslationLanguages = remember(languageA, languageB) { listOf(languageA, languageB).distinct() }
        val unsupportedTranslationLanguages = requiredTranslationLanguages.filter { it !in supportedLanguageCodes }
        val missingTranslationLanguages = requiredTranslationLanguages.filter { it in supportedLanguageCodes && it !in downloadedLanguages }
        val canTranslateConversation = missingTranslationLanguages.isEmpty() && unsupportedTranslationLanguages.isEmpty()
        val tts = remember {
            TextToSpeech(androidContext.applicationContext) { state ->
                ttsReady = state == TextToSpeech.SUCCESS
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
                            context = context.request.invocationContext.asMap(As100ConversationTranslateMethod.ID) + context.action.settings + values
                        ),
                        values = values,
                        invocation = context.request.invocationContext
                    )
                }
        }

        LaunchedEffect(languageA, languageB, labelA, labelB, spokenOutput, preferOffline) {
            context.onSettingsChanged(
                mapOf(
                    "language_a" to languageA,
                    "language_b" to languageB,
                    "label_a" to labelA,
                    "label_b" to labelB,
                    "spoken_output" to spokenOutput.toString(),
                    "prefer_offline" to preferOffline.toString()
                )
            )
        }

        fun refreshLanguagePacks() {
            modelStatus = "Checking language packs…"
            RemoteModelManager.getInstance()
                .getDownloadedModels(TranslateRemoteModel::class.java)
                .addOnSuccessListener { models ->
                    downloadedModelCodes = models.mapNotNull { it.language }.sorted().joinToString(",")
                    modelStatus = if (downloadedModelCodes.isBlank()) {
                        "No language packs downloaded."
                    } else {
                        "Language packs ready."
                    }
                }
                .addOnFailureListener { error ->
                    modelStatus = "Could not check language packs: ${error.message.orEmpty()}"
                }
        }

        fun downloadLanguagePack(code: String) {
            if (code !in supportedLanguageCodes) {
                modelStatus = "${languageLabel(code)} is not available in ML Kit on this device."
                return
            }
            busyLanguageCode = code
            modelStatus = "Downloading ${languageLabel(code)}…"
            RemoteModelManager.getInstance()
                .download(TranslateRemoteModel.Builder(code).build(), DownloadConditions.Builder().build())
                .addOnSuccessListener {
                    busyLanguageCode = null
                    modelStatus = "${languageLabel(code)} downloaded."
                    refreshLanguagePacks()
                }
                .addOnFailureListener { error ->
                    busyLanguageCode = null
                    modelStatus = "Download failed for ${languageLabel(code)}: ${error.message.orEmpty()}"
                }
        }

        LaunchedEffect(languageA, languageB, conversationOpen) {
            if (conversationOpen) refreshLanguagePacks()
        }

        fun finishConversation(state: String = "succeeded", error: String = "") {
            val values = conversationValues(
                turnsJson = turnsJson,
                languageA = languageA,
                languageB = languageB,
                labelA = labelA,
                labelB = labelB,
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

        fun speak(text: String, language: String) {
            if (!spokenOutput || text.isBlank() || !ttsReady) return
            tts.language = localeFor(language)
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "methodmesh-conversation-${System.currentTimeMillis()}")
        }

        fun addTurn(side: String, original: String, translated: String) {
            val source = if (side == "a") languageA else languageB
            val target = if (side == "a") languageB else languageA
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
            turnsJson = appendTurn(turnsJson, turn)
            latestOriginal = original
            latestTranslated = translated
            if (side == "a") {
                latestTextA = original
                latestTextB = translated
            } else {
                latestTextA = translated
                latestTextB = original
            }
            status = "Translated."
            speak(translated, target)
        }

        fun translateSpeech(side: String, text: String) {
            val source = if (side == "a") languageA else languageB
            val target = if (side == "a") languageB else languageA
            if (text.isBlank()) {
                status = "No speech detected."
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
            val source = if (side == "a") languageA else languageB
            val prompt = "${if (side == "a") labelA else labelB}: speak now"
            listeningSide = side
            status = "Listening…"
            recognizer.launch(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, speechLocaleTagFor(source))
                    putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                }
            )
        }

        CapabilityScreenScaffold(
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = capturedResult,
            resultPreview = capturedResult?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty(),
            onBack = onBack,
            onRetry = {
                result = null
                resultValuesJson = null
                turnsJson = "[]"
                latestOriginal = ""
                latestTranslated = ""
                latestTextA = ""
                latestTextB = ""
                conversationOpen = context.startsImmediately
                startedAt = Instant.now().toString()
                status = "Conversation cleared."
            },
            onConfirm = { capturedResult?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            if (capturedResult == null) {
                if (!context.startsImmediately) {
                    Text("Configure a language pair, then let either person press their own button whenever they speak.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    ConversationLanguagePicker("First language", languageA, onSelected = { languageA = it; labelA = defaultButtonLabel(it) })
                    Spacer(Modifier.height(8.dp))
                    ConversationLanguagePicker("Second language", languageB, onSelected = { languageB = it; labelB = defaultButtonLabel(it) })
                    Spacer(Modifier.height(8.dp))
                    ToggleRow("Speak translations aloud", spokenOutput) { spokenOutput = it }
                    ToggleRow("Prefer offline speech recognition", preferOffline) { preferOffline = it }
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
                val transcript = transcriptFromTurns(turnsJson)
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
                            labelA = labelA,
                            labelB = labelB,
                            textA = latestTextA,
                            textB = latestTextB,
                            status = status,
                            modelStatus = modelStatus,
                            missingLanguages = missingTranslationLanguages,
                            unsupportedLanguages = unsupportedTranslationLanguages,
                            busyLanguageCode = busyLanguageCode,
                            operatorFacing = operatorFacing,
                            spokenOutput = spokenOutput,
                            hasTurns = turns(turnsJson).isNotEmpty(),
                            onOperatorFacingChanged = { operatorFacing = it },
                            onDownloadLanguage = ::downloadLanguagePack,
                            onRefreshLanguagePacks = ::refreshLanguagePacks,
                            onListenA = { listen("a") },
                            onListenB = { listen("b") },
                            onReplayA = { speak(latestTextA, languageA) },
                            onReplayB = { speak(latestTextB, languageB) },
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
    labelA: String,
    labelB: String,
    textA: String,
    textB: String,
    status: String,
    modelStatus: String,
    missingLanguages: List<String>,
    unsupportedLanguages: List<String>,
    busyLanguageCode: String?,
    operatorFacing: Boolean,
    spokenOutput: Boolean,
    hasTurns: Boolean,
    onOperatorFacingChanged: (Boolean) -> Unit,
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
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ConversationPersonPanel(
                language = languageB,
                buttonLabel = labelB.ifBlank { languageLabel(languageB) },
                text = textB.ifBlank { "Ready for ${languageLabel(languageB)}" },
                rotated = !operatorFacing,
                spokenOutput = spokenOutput,
                listenEnabled = canListen,
                modifier = Modifier.weight(1f),
                onListen = onListenB,
                onReplay = onReplayB
            )
            Spacer(Modifier.height(6.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(status, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                    Text("Operator view", style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.width(6.dp))
                    Switch(checked = operatorFacing, onCheckedChange = onOperatorFacingChanged)
                }
            }
            if (!canListen) {
                Spacer(Modifier.height(6.dp))
                MissingLanguagePackPanel(
                    modelStatus = modelStatus,
                    missingLanguages = missingLanguages,
                    unsupportedLanguages = unsupportedLanguages,
                    busyLanguageCode = busyLanguageCode,
                    onDownloadLanguage = onDownloadLanguage,
                    onRefreshLanguagePacks = onRefreshLanguagePacks
                )
            }
            Spacer(Modifier.height(6.dp))
            ConversationPersonPanel(
                language = languageA,
                buttonLabel = labelA.ifBlank { languageLabel(languageA) },
                text = textA.ifBlank { "Ready for ${languageLabel(languageA)}" },
                rotated = false,
                spokenOutput = spokenOutput,
                listenEnabled = canListen,
                modifier = Modifier.weight(1f),
                onListen = onListenA,
                onReplay = onReplayA
            )
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = onEnd,
                enabled = hasTurns,
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
    buttonLabel: String,
    text: String,
    rotated: Boolean,
    spokenOutput: Boolean,
    listenEnabled: Boolean,
    modifier: Modifier = Modifier,
    onListen: () -> Unit,
    onReplay: () -> Unit
) {
    val rotateModifier = if (rotated) Modifier.rotate(180f) else Modifier
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(rotateModifier),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onListen,
                    enabled = listenEnabled,
                    modifier = Modifier.weight(1f).height(42.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(buttonLabel.uppercase(Locale.ROOT), textAlign = TextAlign.Center)
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onReplay,
                    enabled = spokenOutput && !text.startsWith("Ready for"),
                    modifier = Modifier.height(42.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Replay")
                }
            }
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = text,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(languageLabel(language), modifier = Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun MissingLanguagePackPanel(
    modelStatus: String,
    missingLanguages: List<String>,
    unsupportedLanguages: List<String>,
    busyLanguageCode: String?,
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
                    "Downloading ${languageLabel(code)}. Keep this screen open.",
                    style = MaterialTheme.typography.bodySmall
                )
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
    languageA: String,
    languageB: String,
    labelA: String,
    labelB: String,
    spokenOutput: Boolean,
    preferOffline: Boolean,
    startedAt: String,
    status: String,
    error: String
): Map<String, String> = linkedMapOf(
    ConversationTranslateFields.TRANSCRIPT to transcriptFromTurns(turnsJson),
    ConversationTranslateFields.TURNS_JSON to turnsJson,
    ConversationTranslateFields.LANGUAGE_A to languageA,
    ConversationTranslateFields.LANGUAGE_B to languageB,
    ConversationTranslateFields.LABEL_A to labelA,
    ConversationTranslateFields.LABEL_B to labelB,
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
        "$speaker (${turn.sourceLanguage}): ${turn.originalText}\n${languageLabel(turn.targetLanguage)}: ${turn.translatedText}"
    }

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

private fun defaultButtonLabel(language: String): String = when (language) {
    "es" -> "Habla"
    "fr" -> "Parlez"
    "pt" -> "Fale"
    "sw" -> "Ongea"
    else -> "Speak"
}

private fun speechLocaleTagFor(language: String): String = when (language) {
    "en" -> "en-US"
    "es" -> "es-ES"
    "fr" -> "fr-FR"
    "pt" -> "pt-PT"
    "de" -> "de-DE"
    "it" -> "it-IT"
    "sw" -> "sw-KE"
    "zh" -> "zh-CN"
    else -> language
}

private fun localeFor(language: String): Locale = Locale.forLanguageTag(speechLocaleTagFor(language))
