package com.example.researchos.modules.speechtranscription

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import com.example.researchos.core.researchos.ExecutionResult
import com.example.researchos.transport.OutputFormatter
import com.example.researchos.transport.workflow.ui.CapabilityScreenContext
import com.example.researchos.transport.workflow.ui.CapabilityScreenScaffold
import com.example.researchos.transport.workflow.ui.CapabilityScreenSpec
import com.example.researchos.transport.workflow.ui.IntentExample
import com.example.researchos.transport.workflow.ui.IntentExampleDropdown
import org.json.JSONArray
import java.time.Instant

object SpeechTranscriptionCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100SpeechTranscriptionMethod.ID
    override val title = "Speech transcription"
    override val description = "Capture speech and return recognised text."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        var language by rememberSaveable { mutableStateOf(context.action.settings["speech_language"] ?: context.action.settings["input_speech_language"] ?: "en-GB") }
        var prompt by rememberSaveable { mutableStateOf(context.action.settings["speech_prompt"] ?: context.action.settings["input_speech_prompt"] ?: "Speak now") }
        var preferOffline by rememberSaveable { mutableStateOf((context.action.settings["speech_prefer_offline"] ?: context.action.settings["input_speech_prefer_offline"] ?: "true").equals("true", true)) }
        var status by rememberSaveable { mutableStateOf("Ready to capture speech.") }
        var launched by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }

        LaunchedEffect(language, prompt, preferOffline) {
            context.onSettingsChanged(
                mapOf(
                    "speech_language" to language,
                    "speech_prompt" to prompt,
                    "speech_prefer_offline" to preferOffline.toString()
                )
            )
        }

        fun complete(values: Map<String, String>, succeeded: Boolean) {
            val request = As100SpeechTranscriptionMethod.request(
                action = As100SpeechTranscriptionMethod.ID,
                context = context.request.invocationContext.asMap(As100SpeechTranscriptionMethod.ID) + context.action.settings,
                signals = emptyList(),
                inputs = emptyList()
            )
            val execution = As100SpeechTranscriptionMethod.result(request, values, context.request.invocationContext)
            result = execution
            status = if (succeeded) "Speech captured." else values[SpeechTranscriptionFields.ERROR] ?: "Speech capture failed."
            if (context.startsImmediately && succeeded) onConfirmed(execution)
        }

        val recognizer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { activityResult ->
            if (activityResult.resultCode != Activity.RESULT_OK) {
                val values = speechValues(language, prompt, preferOffline, "", emptyList(), "cancelled", "Speech capture was cancelled.")
                complete(values, false)
                if (context.startsImmediately) onCancel()
                return@rememberLauncherForActivityResult
            }
            val matches = activityResult.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS).orEmpty()
            complete(speechValues(language, prompt, preferOffline, matches.firstOrNull().orEmpty(), matches, "succeeded", ""), true)
        }

        fun start() {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            }
            status = "Listening…"
            recognizer.launch(intent)
        }

        LaunchedEffect(context.startsImmediately) {
            if (context.startsImmediately && !launched) {
                launched = true
                start()
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
            onRetry = { start() },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Text("Uses the Android speech recognizer. Offline mode depends on the recognizer/language packs installed on the device.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            SpeechLanguageChooser(language, onSelected = { language = it })
            OutlinedTextField(value = prompt, onValueChange = { prompt = it }, label = { Text("Prompt") }, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = { preferOffline = !preferOffline }, modifier = Modifier.fillMaxWidth()) {
                Text("Prefer offline: ${if (preferOffline) "on" else "off"}")
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = { start() }, modifier = Modifier.fillMaxWidth()) { Text("Start speech capture") }
            Text(status, style = MaterialTheme.typography.bodySmall)
            IntentExampleDropdown(
                capabilityId = capabilityId,
                examples = listOf(
                    IntentExample("Speech to text", "Open speech capture and return the transcript.", "com.example.researchos.EXECUTE_METHOD(method_id='speech.transcribe',input_speech_language='en-GB',input_speech_prompt='Please speak now',input_speech_prefer_offline='true',return_mode='flat')")
                )
            )
        }
    }
}

private fun speechValues(
    language: String,
    prompt: String,
    preferOffline: Boolean,
    text: String,
    alternatives: List<String>,
    status: String,
    error: String
): Map<String, String> = linkedMapOf(
    SpeechTranscriptionFields.LANGUAGE to language,
    SpeechTranscriptionFields.PROMPT to prompt,
    SpeechTranscriptionFields.PREFER_OFFLINE to preferOffline.toString(),
    SpeechTranscriptionFields.TEXT to text,
    SpeechTranscriptionFields.ALTERNATIVES_JSON to JSONArray(alternatives).toString(),
    SpeechTranscriptionFields.STATUS to status,
    SpeechTranscriptionFields.ERROR to error,
    SpeechTranscriptionFields.TRANSCRIBED_TIME_ISO to Instant.now().toString()
)

@Composable
private fun SpeechLanguageChooser(
    selected: String,
    onSelected: (String) -> Unit
) {
    Text("Recognition language", style = MaterialTheme.typography.labelLarge)
    SpeechLanguagePreset.options.forEach { option ->
        val active = selected.equals(option.tag, ignoreCase = true)
        if (active) {
            Button(
                onClick = { onSelected(option.tag) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("✓ ${option.label}") }
        } else {
            OutlinedButton(
                onClick = { onSelected(option.tag) },
                modifier = Modifier.fillMaxWidth()
            ) { Text(option.label) }
        }
        Spacer(Modifier.height(4.dp))
    }
}

private data class SpeechLanguagePreset(val tag: String, val label: String) {
    companion object {
        val options = listOf(
            SpeechLanguagePreset("en-GB", "English (UK)"),
            SpeechLanguagePreset("en-US", "English (US)"),
            SpeechLanguagePreset("fr-FR", "French"),
            SpeechLanguagePreset("es-ES", "Spanish"),
            SpeechLanguagePreset("pt-PT", "Portuguese"),
            SpeechLanguagePreset("sw-KE", "Swahili")
        )
    }
}
