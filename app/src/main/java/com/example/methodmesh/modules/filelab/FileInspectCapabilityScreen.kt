package com.example.methodmesh.modules.filelab

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

object FileInspectCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100FileInspectMethod.ID
    override val title = "Inspect file"
    override val description = "Identify a file by content, explain what the format is for, inspect safe metadata and calculate SHA-256."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val appContext = LocalContext.current
        val scope = rememberCoroutineScope()
        val suppliedUri = context.action.setting("source_uri") ?: context.request.setting("source_uri")
        val suppliedName = context.action.setting("source_name") ?: context.request.setting("source_name")

        var sourceUri by rememberSaveable(context.action.canonicalId) { mutableStateOf(suppliedUri.orEmpty()) }
        var sourceName by rememberSaveable(context.action.canonicalId) { mutableStateOf(suppliedName.orEmpty()) }
        var acquiredSource by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        var workingJson by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
        var committedValuesJson by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
        var running by remember { mutableStateOf(false) }
        var status by rememberSaveable(context.action.canonicalId) { mutableStateOf("Choose a file to inspect.") }

        val working = remember(workingJson) { FileLabJson.inspectionFromJson(workingJson) }
        val committedValues = remember(committedValuesJson) { committedValuesJson.toStringMap() }
        val committedResult = remember(committedValuesJson, context.request.invocationContext, context.action.settings) {
            if (committedValues.isEmpty()) null else {
                val request = As100FileInspectMethod.request(
                    action = As100FileInspectMethod.ID,
                    context = context.request.invocationContext.asMap(As100FileInspectMethod.ID) + context.action.settings + committedValues
                )
                As100FileInspectMethod.result(request, committedValues, committedValues[FileInspectFields.STATUS] == "succeeded")
            }
        }

        fun inspectCurrent(uriString: String, nameHint: String = sourceName) {
            if (uriString.isBlank() || running) return
            running = true
            status = "Inspecting…"
            committedValuesJson = ""
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        FileLabAndroid.inspect(appContext, Uri.parse(uriString), nameHint.takeIf { it.isNotBlank() })
                    }
                }.onSuccess { inspection ->
                    sourceName = inspection.displayName
                    workingJson = FileLabJson.inspectionToJson(inspection)
                    status = "Inspection ready. Review the live result, then Commit."
                }.onFailure { error ->
                    workingJson = ""
                    status = error.message ?: "Inspection failed."
                }
                running = false
            }
        }

        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri ?: return@rememberLauncherForActivityResult
            FileLabAndroid.tryTakeReadPermission(appContext, uri)
            sourceUri = uri.toString()
            sourceName = ""
            acquiredSource = true
            workingJson = ""
            committedValuesJson = ""
            inspectCurrent(sourceUri, "")
        }

        LaunchedEffect(suppliedUri) {
            if (sourceUri.isNotBlank() && workingJson.isBlank() && !running) inspectCurrent(sourceUri, suppliedName.orEmpty())
        }

        val resultPreview = committedValues.mapValues { it.value as Any? }
        CapabilityScreenScaffold(
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = committedResult,
            resultPreview = resultPreview,
            onBack = onBack,
            onRetry = {
                committedValuesJson = ""
                if (sourceUri.isNotBlank()) inspectCurrent(sourceUri)
            },
            onConfirm = { committedResult?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Text(
                "Content-first inspection. File Lab reads the source but never modifies it.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) {
                    Text(if (sourceUri.isBlank()) "Choose file" else "Choose another")
                }
                if (sourceUri.isNotBlank()) {
                    OutlinedButton(onClick = { inspectCurrent(sourceUri) }, enabled = !running, modifier = Modifier.weight(1f)) {
                        Text("Re-inspect")
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            if (running) {
                CircularProgressIndicator()
                Spacer(Modifier.height(8.dp))
            }
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            working?.let { inspection ->
                Spacer(Modifier.height(16.dp))
                FileLabSectionTitle("Live inspection")
                Spacer(Modifier.height(8.dp))
                FileLabValue("Format", inspection.formatName)
                Spacer(Modifier.height(6.dp))
                FileLabValue("File", inspection.displayName)
                Spacer(Modifier.height(6.dp))
                FileLabValue("Size", "${inspection.sizeBytes} bytes")
                Spacer(Modifier.height(6.dp))
                FileLabValue("SHA-256", inspection.sha256, monospace = true)
                Spacer(Modifier.height(6.dp))
                FileLabValue("Detection confidence", "${inspection.confidence}% · ${inspection.inspectionDepth.name.lowercase(Locale.ROOT)} inspection")

                inspection.knowledge?.let { knowledge ->
                    Spacer(Modifier.height(16.dp))
                    FileLabSectionTitle("What is this format?")
                    Spacer(Modifier.height(6.dp))
                    Text(knowledge.description, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    FileLabValue("Category", knowledge.category)
                    Spacer(Modifier.height(6.dp))
                    FileLabValue("Technical identity", knowledge.technicalIdentity)
                    Spacer(Modifier.height(6.dp))
                    FileLabValue("Typical uses", knowledge.typicalUses)
                    if (knowledge.commonProducers.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        FileLabValue("Common producers", knowledge.commonProducers.joinToString(", "))
                    }
                    Spacer(Modifier.height(6.dp))
                    FileLabValue("File Lab support", knowledge.fileLabSupport)
                    if (knowledge.typicalSoftware.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        FileLabValue("Often opened with", knowledge.typicalSoftware.joinToString(", "))
                    }
                    if (knowledge.relatedFormats.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        FileLabValue("Related formats", knowledge.relatedFormats.joinToString(", "))
                    }
                    if (knowledge.caution.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text("Note: ${knowledge.caution}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    if (sourceUri.isNotBlank()) {
                        val installedHandlers = remember(sourceUri, inspection.mimeType) {
                            FileLabAndroid.installedOpenWithLabels(appContext, Uri.parse(sourceUri), inspection.mimeType)
                        }
                        Spacer(Modifier.height(12.dp))
                        FileLabSectionTitle("Open with")
                        if (installedHandlers.isEmpty()) {
                            Text("No installed app currently advertises support for this file type.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            Text(installedHandlers.joinToString(", "), style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { FileLabAndroid.openWithChooser(appContext, Uri.parse(sourceUri), inspection.mimeType) },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Open with…") }
                        }
                    }
                }

                if (inspection.facts.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    FileLabSectionTitle("Format facts")
                    Spacer(Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        inspection.facts.take(24).forEach { fact -> FileLabValue(fact.key, fact.value) }
                    }
                }
                if (inspection.warnings.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    FileLabSectionTitle("Warnings")
                    inspection.warnings.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                }
                if ("read" in inspection.availableActions && sourceUri.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { FileLabAndroid.openReader(appContext, Uri.parse(sourceUri), inspection.mimeType) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Open in reader") }
                    Text("KOReader is preferred when installed; otherwise Android's normal reader chooser is used.", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        val values = As100FileInspectMethod.values(
                            inspection = inspection,
                            acquiredSourceUri = if (acquiredSource) sourceUri else ""
                        )
                        committedValuesJson = JSONObject(values).toString()
                    },
                    enabled = committedValuesJson.isBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (committedValuesJson.isBlank()) "Commit inspection" else "Committed") }
            }
        }
    }
}

private fun com.example.methodmesh.transport.workflow.ExternalActionRequest.setting(key: String): String? =
    (settings[key] ?: settings["input_$key"])?.takeIf { it.isNotBlank() }

private fun com.example.methodmesh.transport.workflow.ExternalWorkflowRequest.setting(key: String): String? =
    (settings[key] ?: settings["input_$key"])?.takeIf { it.isNotBlank() }

private fun String.toStringMap(): Map<String, String> {
    if (isBlank()) return emptyMap()
    return runCatching {
        val json = JSONObject(this)
        linkedMapOf<String, String>().apply {
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                put(key, json.optString(key, ""))
            }
        }
    }.getOrDefault(emptyMap())
}
