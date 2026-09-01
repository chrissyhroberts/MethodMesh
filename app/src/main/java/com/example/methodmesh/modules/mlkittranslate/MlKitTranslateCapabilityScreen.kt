package com.example.methodmesh.modules.mlkittranslate

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
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
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityCompletionMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityPresentationMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import com.example.methodmesh.transport.workflow.ui.IntentExample
import com.example.methodmesh.transport.workflow.ui.IntentExampleDropdown
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
        var text by rememberSaveable { mutableStateOf(context.action.settings["input_text"] ?: context.action.settings["input_input_text"] ?: "") }
        val action = context.action.settings["model_action"] ?: context.action.settings["input_model_action"] ?: "translate"
        val modelLanguage = context.action.settings["model_language"] ?: context.action.settings["input_model_language"]
        val runtimeFields = (context.action.settings["methodmesh_runtime_fields"] ?: context.action.settings["input_methodmesh_runtime_fields"]).orEmpty()
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
        val nativePresetRun = (context.action.settings["methodmesh_native_preset_run"] ?: context.action.settings["input_methodmesh_native_preset_run"]) == "true"
        val hasSuppliedText = remember(context.action.settings, context.request.settings) {
            listOf("input_text", "input_input_text", "text")
                .any { key -> context.action.settings[key].orEmpty().isNotBlank() || context.request.settings[key].orEmpty().isNotBlank() }
        }
        val needsRuntimeText = action == "translate" && (text.isBlank() || "input_text" in runtimeFields || "text" in runtimeFields)
        val needsRuntimeLanguages = "source_language" in runtimeFields || "target_language" in runtimeFields
        val compactInputOnly = context.presentationMode == CapabilityPresentationMode.IntentLaunch && needsRuntimeText && !needsRuntimeLanguages
        var sourceMenuOpen by rememberSaveable { mutableStateOf(false) }
        var targetMenuOpen by rememberSaveable { mutableStateOf(false) }
        var status by rememberSaveable { mutableStateOf(if (needsRuntimeText) "Enter text to translate." else "Ready.") }
        var downloaded by rememberSaveable { mutableStateOf("") }
        var busyModelCode by rememberSaveable { mutableStateOf<String?>(null) }
        var launched by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }

        LaunchedEffect(source, target, text, action, modelLanguage) {
            context.onSettingsChanged(
                buildMap {
                    put("source_language", source)
                    put("target_language", target)
                    put("input_text", text)
                    put("model_action", action)
                    modelLanguage?.takeIf { it.isNotBlank() }?.let { put("model_language", it) }
                }
            )
        }

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
            if (context.completionMode == CapabilityCompletionMode.AutomaticReturn && succeeded && (!needsRuntimeText || hasSuppliedText)) onConfirmed(execution)
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
            busyModelCode = code
            status = "Downloading ${languageLabel(code)}…"
            val model = TranslateRemoteModel.Builder(code).build()
            RemoteModelManager.getInstance()
                .download(model, DownloadConditions.Builder().build())
                .addOnSuccessListener {
                    busyModelCode = null
                    refreshModels()
                    complete(values("download"), true)
                }
                .addOnFailureListener { error ->
                    busyModelCode = null
                    complete(values("download", error = "Download failed: ${error.message.orEmpty()}", state = "failed"), false)
                }
        }

        fun delete(code: String) {
            busyModelCode = code
            status = "Removing $code model…"
            val model = TranslateRemoteModel.Builder(code).build()
            RemoteModelManager.getInstance()
                .deleteDownloadedModel(model)
                .addOnSuccessListener {
                    busyModelCode = null
                    refreshModels()
                    complete(values("delete"), true)
                }
                .addOnFailureListener { error ->
                    busyModelCode = null
                    complete(values("delete", error = "Delete failed: ${error.message.orEmpty()}", state = "failed"), false)
                }
        }

        fun translate() {
            if (text.isBlank()) {
                status = "Enter text to translate."
                return
            }
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

        LaunchedEffect(context.startsImmediately, nativePresetRun, hasSuppliedText, needsRuntimeText, needsRuntimeLanguages) {
            val shouldAutoRun = context.startsImmediately || (nativePresetRun && !needsRuntimeText && !needsRuntimeLanguages)
            if (shouldAutoRun && !launched && (!needsRuntimeText || hasSuppliedText)) {
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
            Text(
                if (needsRuntimeText) "Enter text to translate. Powered by Google ML Kit." else "Translation runs on device. Powered by Google ML Kit.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(10.dp))
            if (!compactInputOnly || needsRuntimeLanguages) {
                LanguagePicker("Source language", source, sourceMenuOpen, { sourceMenuOpen = it }) { source = it }
                Spacer(Modifier.height(8.dp))
                LanguagePicker("Target language", target, targetMenuOpen, { targetMenuOpen = it }) { target = it }
                Spacer(Modifier.height(8.dp))
            } else {
                Text("${languageLabel(source)} → ${languageLabel(target)}", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
            }
            OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Text to translate") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                Button(onClick = { translate() }, modifier = Modifier.weight(1f), enabled = text.isNotBlank()) { Text("Translate") }
            }
            if (!compactInputOnly) {
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
            }
            Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
            busyModelCode?.let { code ->
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                Text("Working on ${languageLabel(code)}. Keep this screen open.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
            }
            result?.let { execution ->
                val translated = OutputFormatter.fields(
                    execution,
                    includeProvenance = false,
                    payloadMode = OutputFormatter.PayloadMode.CORE
                )[MlKitTranslateFields.TRANSLATED_TEXT]
                    ?.toString()
                    .orEmpty()
                if (translated.isNotBlank()) {
                    Text("Translated text", fontWeight = FontWeight.SemiBold)
                    Text(translated, style = MaterialTheme.typography.titleMedium)
                }
            }
            if (downloaded.isNotBlank()) {
                Text("Downloaded: $downloaded", style = MaterialTheme.typography.bodySmall)
            }
            if (!compactInputOnly) {
                IntentExampleDropdown(
                    capabilityId = capabilityId,
                    examples = listOf(
                        IntentExample("Translate text", "Translate text on device, downloading models if needed.", "com.example.methodmesh.EXECUTE_METHOD(method_id='mlkit.translate',input_source_language='en',input_target_language='fr',input_text='hello world',return_mode='flat')"),
                        IntentExample("List language models", "Return installed ML Kit translation languages.", "com.example.methodmesh.EXECUTE_METHOD(method_id='mlkit.translate',input_model_action='list',return_mode='flat')"),
                        IntentExample("Download one language", "Download a translation model for later offline use.", "com.example.methodmesh.EXECUTE_METHOD(method_id='mlkit.translate',input_model_action='download',input_model_language='fr',return_mode='flat')")
                    )
                )
            }
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
    MlKitLanguageCatalog.supportedLanguages().map { it.code }

private fun availableLanguagesText(): String = mlKitLanguages().joinToString(",")

private fun languageLabel(code: String): String = MlKitLanguageCatalog.label(code)
