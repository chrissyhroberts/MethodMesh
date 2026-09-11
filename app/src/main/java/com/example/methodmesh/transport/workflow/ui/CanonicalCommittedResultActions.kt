package com.example.methodmesh.transport.workflow.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.OutputExportRepository
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.ResultShare
import com.example.methodmesh.transport.ReturnMode

/**
 * Canonical post-Commit actions for capability-specific screens that do not use
 * [CapabilityScreenScaffold]. This keeps exceptional UIs on the same native
 * Share/Save contract instead of making each module reconstruct Android intents.
 */
@Composable
internal fun CanonicalCommittedResultActions(
    result: ExecutionResult,
    label: String,
    onDone: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val appContext = LocalContext.current
    val projection = NativeCommittedResultProjection.from(result)
    var includeFullJson by rememberSaveable(result.request.id.value) { mutableStateOf(false) }
    var status by rememberSaveable(result.request.id.value) { mutableStateOf("") }
    val copiedText = ResultShare.buildShareText(
        projection.text,
        if (includeFullJson) projection.fullJson else ""
    )

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text("Include full JSON / audit", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (includeFullJson) {
                        "Share/copy append debug JSON text; Save adds metadata.json."
                    } else {
                        "Off by default for manual runs."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = includeFullJson, onCheckedChange = { includeFullJson = it })
        }

        Button(
            onClick = {
                runCatching {
                    ResultShare.share(
                        context = appContext,
                        chooserTitle = "Share $label",
                        text = projection.text,
                        attachments = projection.attachments.map { (name, uri) ->
                            ResultShare.Attachment(name, uri)
                        },
                        jsonText = if (includeFullJson) projection.fullJson else "",
                        fileLabel = label
                    )
                }.onSuccess { status = "Sharing committed result…" }
                    .onFailure { status = "Share failed: ${it.message ?: "no sharing app available"}" }
            },
            enabled = projection.text.isNotBlank() || projection.attachments.isNotEmpty() ||
                (includeFullJson && projection.fullJson.isNotBlank()),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Share") }

        OutlinedButton(
            onClick = {
                runCatching {
                    if (copiedText.isBlank()) error("No text result to copy.")
                    appContext.getSystemService(ClipboardManager::class.java)
                        .setPrimaryClip(ClipData.newPlainText("MethodMesh result", copiedText))
                }.onSuccess { status = "Copied committed result." }
                    .onFailure { status = "Copy failed: ${it.message ?: "no text result"}" }
            },
            enabled = copiedText.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Copy") }

        OutlinedButton(
            onClick = {
                runCatching {
                    OutputExportRepository.saveToDownloads(
                        context = appContext,
                        label = label,
                        text = projection.text,
                        mediaUris = projection.attachments.map { it.second.toString() },
                        jsonText = if (includeFullJson) projection.fullJson else ""
                    )
                }.onSuccess { status = "Saved ${it.summary}" }
                    .onFailure { status = "Save failed: ${it.message ?: "storage error"}" }
            },
            enabled = projection.text.isNotBlank() || projection.attachments.isNotEmpty() ||
                (includeFullJson && projection.fullJson.isNotBlank()),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save to Downloads") }

        if (status.isNotBlank()) {
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (onDone != null || onEdit != null) Spacer(Modifier.height(2.dp))
        if (onDone != null) {
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
        }
        if (onEdit != null) {
            OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text("Edit / new run") }
        }
    }
}

internal data class NativeCommittedResultProjection(
    val text: String,
    val attachments: List<Pair<String, Uri>>,
    val fullJson: String
) {
    companion object {
        fun from(result: ExecutionResult): NativeCommittedResultProjection {
            val coreFields = OutputFormatter.projectFields(
                OutputFormatter.fields(result, includeProvenance = false),
                OutputFormatter.PayloadMode.CORE,
                result.status
            )
            val attachments = coreFields.entries.mapNotNull { (key, value) ->
                val raw = value?.toString().orEmpty()
                if (!ResultShare.isShareableMediaField(key, raw)) return@mapNotNull null
                ResultShare.attachmentName(key, raw) to Uri.parse(raw)
            }.distinctBy { it.second.toString() }
            val text = humanText(coreFields)
            val fullJson = OutputFormatter.format(
                result = result,
                returnMode = ReturnMode.Json,
                includeProvenance = true,
                payloadMode = OutputFormatter.PayloadMode.FULL
            )
            return NativeCommittedResultProjection(text, attachments, fullJson)
        }

        private fun humanText(fields: Map<String, Any?>): String {
            listOf("barcode_payload", "plus_code").forEach { key ->
                fields[key]?.toString()?.takeIf { it.isNotBlank() }?.let { return it }
            }
            val textLike = fields.entries
                .filterNot { (key, value) -> ResultShare.isShareableMediaField(key, value?.toString().orEmpty()) }
                .filterNot { (key, _) -> key.endsWith("_name") || key.endsWith("_filename") }
            if (textLike.size == 1) return textLike.first().value?.toString().orEmpty()
            return textLike.joinToString("\n") { (key, value) ->
                "${friendlyLabel(key)}: ${value?.toString().orEmpty()}"
            }
        }

        private fun friendlyLabel(key: String): String = key
            .removePrefix("methodmesh_")
            .replace("_pct", " %")
            .replace("_c", " °C")
            .replace("_cm", " cm")
            .replace("_uri", "")
            .replace('_', ' ')
            .trim()
            .split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
    }
}
