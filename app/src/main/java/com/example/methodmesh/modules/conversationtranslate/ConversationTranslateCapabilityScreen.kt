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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
        var turnsJson by rememberSaveable(context.action.canonicalId) { mutableStateOf("[]") }
        var startedAt by rememberSaveable(context.action.canonicalId) { mutableStateOf(Instant.now().toString()) }
        var resultValuesJson by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var ttsReady by remember { mutableStateOf(false) }
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
                }
                if (latestTranslated.isNotBlank()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Text(latestTranslated, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text("Original: $latestOriginal", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { listen("a") }, modifier = Modifier.weight(1f).height(72.dp)) {
                        Text(labelA.ifBlank { languageLabel(languageA) }, textAlign = TextAlign.Center)
                    }
                    Button(onClick = { listen("b") }, modifier = Modifier.weight(1f).height(72.dp)) {
                        Text(labelB.ifBlank { languageLabel(languageB) }, textAlign = TextAlign.Center)
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { finishConversation() },
                    enabled = turns(turnsJson).isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("End conversation") }
                Spacer(Modifier.height(8.dp))
                Text(status, style = MaterialTheme.typography.bodySmall)
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
