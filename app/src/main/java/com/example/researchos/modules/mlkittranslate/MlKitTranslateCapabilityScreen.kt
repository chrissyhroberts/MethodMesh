package com.example.researchos.modules.mlkittranslate

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.researchos.core.researchos.ExecutionResult
import com.example.researchos.transport.OutputFormatter
import com.example.researchos.transport.workflow.ui.CapabilityScreenContext
import com.example.researchos.transport.workflow.ui.CapabilityScreenScaffold
import com.example.researchos.transport.workflow.ui.CapabilityScreenSpec
import com.example.researchos.transport.workflow.ui.IntentExample
import com.example.researchos.transport.workflow.ui.IntentExampleDropdown
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import java.time.Instant

object MlKitTranslateCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100MlKitTranslateMethod.ID
    override val title = "ML Kit translation"
    override val description = "Download language models and translate text on device."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        var source by rememberSaveable { mutableStateOf(context.action.settings["source_language"] ?: context.action.settings["input_source_language"] ?: "en") }
        var target by rememberSaveable { mutableStateOf(context.action.settings["target_language"] ?: context.action.settings["input_target_language"] ?: "fr") }
        var text by rememberSaveable { mutableStateOf(context.action.settings["input_text"] ?: context.action.settings["input_input_text"] ?: "hello world") }
        val action = context.action.settings["model_action"] ?: context.action.settings["input_model_action"] ?: "translate"
        val modelLanguage = context.action.settings["model_language"] ?: context.action.settings["input_model_language"]
        var sourceMenuOpen by rememberSaveable { mutableStateOf(false) }
        var targetMenuOpen by rememberSaveable { mutableStateOf(false) }
        var status by rememberSaveable { mutableStateOf("Choose languages. Downloaded models can be managed here.") }
        var downloaded by rememberSaveable { mutableStateOf("") }
        var launched by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }

        fun complete(values: Map<String, String>, succeeded: Boolean) {
            val request = As100MlKitTranslateMethod.request(
                action = As100MlKitTranslateMethod.ID,
                context = context.request.invocationContext.asMap(As100MlKitTranslateMethod.ID) + context.action.settings,
                signals = emptyList(),
                inputs = emptyList()
            )
            val execution = As100MlKitTranslateMethod.result(request, values, context.request.invocationContext)
            result = execution
            status = if (succeeded) "ML Kit translation action complete." else values[MlKitTranslateFields.ERROR] ?: "Translation failed."
            if (context.startsImmediately && succeeded) onConfirmed(execution)
        }

        fun values(action: String, translated: String = "", error: String = "", state: String = "succeeded") = linkedMapOf(
            MlKitTranslateFields.SOURCE_LANGUAGE to source,
            MlKitTranslateFields.TARGET_LANGUAGE to target,
            MlKitTranslateFields.INPUT_TEXT to text,
            MlKitTranslateFields.TRANSLATED_TEXT to translated,
            MlKitTranslateFields.MODEL_ACTION to action,
            MlKitTranslateFields.DOWNLOADED_MODELS to downloaded,
            MlKitTranslateFields.AVAILABLE_LANGUAGES to availableLanguagesText(),
            MlKitTranslateFields.STATUS to state,
            MlKitTranslateFields.ERROR to error,
            MlKitTranslateFields.TRANSLATED_TIME_ISO to Instant.now().toString()
        )

        fun refreshModels(thenComplete: Boolean = false) {
            status = "Checking downloaded language models…"
            RemoteModelManager.getInstance()
                .getDownloadedModels(TranslateRemoteModel::class.java)
                .addOnSuccessListener { models ->
                    downloaded = models.mapNotNull { it.language }.sorted().joinToString(",")
                    status = if (downloaded.isBlank()) "No translation models downloaded yet." else "Downloaded models: $downloaded"
                    if (thenComplete) complete(values("list"), true)
                }
                .addOnFailureListener { error ->
                    complete(values("list", error = "Could not list models: ${error.message.orEmpty()}", state = "failed"), false)
                }
        }

        fun download(code: String) {
            status = "Downloading $code model…"
            val model = TranslateRemoteModel.Builder(code).build()
            RemoteModelManager.getInstance()
                .download(model, DownloadConditions.Builder().build())
                .addOnSuccessListener {
                    refreshModels()
                    complete(values("download"), true)
                }
                .addOnFailureListener { error ->
                    complete(values("download", error = "Download failed: ${error.message.orEmpty()}", state = "failed"), false)
                }
        }

        fun delete(code: String) {
            status = "Removing $code model…"
            val model = TranslateRemoteModel.Builder(code).build()
            RemoteModelManager.getInstance()
                .deleteDownloadedModel(model)
                .addOnSuccessListener {
                    refreshModels()
                    complete(values("delete"), true)
                }
                .addOnFailureListener { error ->
                    complete(values("delete", error = "Delete failed: ${error.message.orEmpty()}", state = "failed"), false)
                }
        }

        fun translate() {
            status = "Preparing translation models…"
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(source)
                .setTargetLanguage(target)
                .build()
            val translator = Translation.getClient(options)
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
                .addOnSuccessListener {
                    translator.translate(text)
                        .addOnSuccessListener { translated ->
                            refreshModels()
                            complete(values("translate", translated = translated), true)
                            translator.close()
                        }
                        .addOnFailureListener { error ->
                            complete(values("translate", error = "Translation failed: ${error.message.orEmpty()}", state = "failed"), false)
                            translator.close()
                        }
                }
                .addOnFailureListener { error ->
                    complete(values("translate", error = "Model download failed: ${error.message.orEmpty()}", state = "failed"), false)
                    translator.close()
                }
        }

        LaunchedEffect(context.startsImmediately) {
            if (context.startsImmediately && !launched) {
                launched = true
                when (action) {
                    "list" -> refreshModels(thenComplete = true)
                    "download", "download_target" -> download(modelLanguage ?: target)
                    "download_source" -> download(modelLanguage ?: source)
                    "delete", "delete_target" -> delete(modelLanguage ?: target)
                    "delete_source" -> delete(modelLanguage ?: source)
                    else -> translate()
                }
            }
        }

        CapabilityScreenScaffold(
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = result,
            resultPreview = result?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty(),
            onBack = onBack,
            onRetry = { refreshModels(thenComplete = true) },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Text("Translation models are downloaded to the device and can be removed again here.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            LanguagePicker("Source language", source, sourceMenuOpen, { sourceMenuOpen = it }) { source = it }
            Spacer(Modifier.height(8.dp))
            LanguagePicker("Target language", target, targetMenuOpen, { targetMenuOpen = it }) { target = it }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Text") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                Button(onClick = { translate() }, modifier = Modifier.weight(1f)) { Text("Translate") }
            }
            Row(Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { download(source) }, modifier = Modifier.weight(1f)) { Text("Download source") }
                Spacer(Modifier.padding(4.dp))
                OutlinedButton(onClick = { download(target) }, modifier = Modifier.weight(1f)) { Text("Download target") }
            }
            Row(Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { delete(source) }, modifier = Modifier.weight(1f)) { Text("Remove source") }
                Spacer(Modifier.padding(4.dp))
                OutlinedButton(onClick = { refreshModels(thenComplete = true) }, modifier = Modifier.weight(1f)) { Text("List models") }
            }
            Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
            if (downloaded.isNotBlank()) {
                Text("Downloaded: $downloaded", style = MaterialTheme.typography.bodySmall)
            }
            IntentExampleDropdown(
                capabilityId = capabilityId,
                examples = listOf(
                    IntentExample("Translate text", "Translate text on device, downloading models if needed.", "com.example.researchos.EXECUTE_METHOD(method_id='mlkit.translate',input_source_language='en',input_target_language='fr',input_text='hello world',return_mode='flat')"),
                    IntentExample("List language models", "Return installed ML Kit translation languages.", "com.example.researchos.EXECUTE_METHOD(method_id='mlkit.translate',input_model_action='list',return_mode='flat')"),
                    IntentExample("Download one language", "Download a translation model for later offline use.", "com.example.researchos.EXECUTE_METHOD(method_id='mlkit.translate',input_model_action='download',input_model_language='fr',return_mode='flat')")
                )
            )
        }
    }
}

@Composable
private fun LanguagePicker(label: String, selected: String, expanded: Boolean, setExpanded: (Boolean) -> Unit, onSelected: (String) -> Unit) {
    Column {
        Text(label, fontWeight = FontWeight.SemiBold)
        OutlinedButton(onClick = { setExpanded(true) }, modifier = Modifier.fillMaxWidth()) {
            Text(languageLabel(selected), modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
            Text("▼")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { setExpanded(false) }, modifier = Modifier.fillMaxWidth(.9f)) {
            mlKitLanguages().forEach { code ->
                DropdownMenuItem(text = { Text(languageLabel(code)) }, onClick = { onSelected(code); setExpanded(false) })
            }
        }
    }
}

private fun mlKitLanguages(): List<String> =
    runCatching { TranslateLanguage.getAllLanguages().sorted() }.getOrElse { commonMlKitLanguageCodes }

private fun availableLanguagesText(): String = mlKitLanguages().joinToString(",")

private fun languageLabel(code: String): String = "$code${languageName(code)?.let { " · $it" }.orEmpty()}"

private fun languageName(code: String): String? = when (code) {
    "en" -> "English"
    "fr" -> "French"
    "es" -> "Spanish"
    "pt" -> "Portuguese"
    "de" -> "German"
    "it" -> "Italian"
    "ar" -> "Arabic"
    "hi" -> "Hindi"
    "sw" -> "Swahili"
    "zh" -> "Chinese"
    "ja" -> "Japanese"
    "ko" -> "Korean"
    "ru" -> "Russian"
    "uk" -> "Ukrainian"
    else -> null
}
