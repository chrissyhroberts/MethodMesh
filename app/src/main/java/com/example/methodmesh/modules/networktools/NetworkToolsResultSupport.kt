package com.example.methodmesh.modules.networktools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.methodmesh.MainActivity
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.protocols.PresetResultAction
import com.example.methodmesh.transport.OutputExportRepository
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.ResultShare
import com.example.methodmesh.transport.ReturnMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext

internal fun buildNetworkExecution(
    context: CapabilityScreenContext,
    values: Map<String, String>,
    settings: Map<String, String>
): ExecutionResult {
    val request = As100NetworkToolsMethod.request(
        action = As100NetworkToolsMethod.ID,
        context = context.request.invocationContext.asMap(As100NetworkToolsMethod.ID) +
            context.action.settings + settings + values,
        signals = emptyList(),
        inputs = emptyList()
    )
    return As100NetworkToolsMethod.result(request, values, context.request.invocationContext)
}

@Composable
internal fun NetworkCopyValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    prominent: Boolean = false
) {
    if (value.isBlank()) return
    val androidContext = LocalContext.current
    Column(
        modifier = modifier
            .clickable {
                androidContext.getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText(label, value))
                Toast.makeText(androidContext, "Copied $label", Toast.LENGTH_SHORT).show()
            }
            .padding(vertical = 2.dp)
    ) {
        Text(
            value,
            style = if (prominent) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyMedium,
            fontWeight = if (prominent) FontWeight.SemiBold else FontWeight.Normal
        )
        Text("Tap to copy", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
internal fun NetworkWorkingResultCard(
    values: Map<String, String>,
    title: String = "Current result",
    onCommit: (() -> Unit)? = null,
    committedChanged: Boolean = false
) {
    val primary = values[NetworkToolsFields.VALUE].orEmpty().ifBlank {
        values[NetworkToolsFields.SUMMARY].orEmpty()
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.30f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            NetworkCopyValue("network result", primary, Modifier.fillMaxWidth(), prominent = true)
            values[NetworkToolsFields.SUMMARY]
                ?.takeIf { it.isNotBlank() && it != primary }
                ?.let { NetworkCopyValue("network summary", it, Modifier.fillMaxWidth()) }
            values[NetworkToolsFields.DETAIL]
                ?.takeIf { it.isNotBlank() }
                ?.let { NetworkCopyValue("network detail", it, Modifier.fillMaxWidth()) }
            values[NetworkToolsFields.IP]
                ?.takeIf { it.isNotBlank() }
                ?.let { NetworkCopyValue("IP address", it, Modifier.fillMaxWidth()) }
            values[NetworkToolsFields.LATENCY_MS]
                ?.takeIf { it.isNotBlank() }
                ?.let { NetworkCopyValue("latency", "$it ms", Modifier.fillMaxWidth()) }
            values[NetworkToolsFields.STATUS]
                ?.takeIf { it.isNotBlank() }
                ?.let { NetworkCopyValue("status", it, Modifier.fillMaxWidth()) }
            if (committedChanged) {
                Text(
                    "Working inputs/result have changed since the last Commit.",
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            if (onCommit != null) {
                Spacer(Modifier.height(10.dp))
                Button(onClick = onCommit, modifier = Modifier.fillMaxWidth()) { Text("Commit") }
            }
        }
    }
}

/** Same-screen post-Commit actions, matching the current MethodMesh live-result -> Commit contract. */
@Composable
internal fun NetworkCommittedActions(
    context: CapabilityScreenContext,
    label: String,
    result: ExecutionResult,
    workingChanged: Boolean,
    onDone: () -> Unit
) {
    val androidContext = LocalContext.current
    var includeFullJson by rememberSaveable(result.request.id.value) { mutableStateOf(false) }
    var actionStatus by rememberSaveable(result.request.id.value) { mutableStateOf("") }

    val coreFields = OutputFormatter.projectFields(
        OutputFormatter.fields(result, includeProvenance = false),
        OutputFormatter.PayloadMode.CORE,
        result.status
    )
    val primary = coreFields[NetworkToolsFields.VALUE]?.toString().orEmpty().ifBlank {
        coreFields[NetworkToolsFields.SUMMARY]?.toString().orEmpty()
    }
    val fullJson = OutputFormatter.format(
        result = result,
        returnMode = ReturnMode.Json,
        includeProvenance = true,
        payloadMode = OutputFormatter.PayloadMode.FULL
    )
    val presetAction = PresetResultAction.normalize(
        context.request.settings["methodmesh_preset_result_action"]
            ?: context.request.settings["input_methodmesh_preset_result_action"]
            ?: PresetResultAction.HOME
    )
    val finishToLauncher =
        context.request.settings["methodmesh_finish_to_launcher"] == "true" ||
            context.request.settings["input_methodmesh_finish_to_launcher"] == "true"

    fun finishNativePresetHome() {
        if (finishToLauncher) {
            onDone()
            return
        }
        androidContext.startActivity(
            Intent(androidContext, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    fun copyResult() {
        val text = ResultShare.buildShareText(primary, if (includeFullJson) fullJson else "")
        if (text.isBlank()) return
        androidContext.getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("$label result", text))
        actionStatus = "Copied committed result."
    }

    fun shareResult() {
        runCatching {
            ResultShare.share(
                context = androidContext,
                chooserTitle = "Share $label result",
                text = primary,
                attachments = emptyList(),
                jsonText = if (includeFullJson) fullJson else "",
                fileLabel = label
            )
        }.onSuccess {
            actionStatus = "Sharing committed result…"
        }.onFailure {
            actionStatus = "Share failed: ${it.message ?: "no sharing app available"}"
        }
    }

    fun saveResult() {
        runCatching {
            OutputExportRepository.saveToDownloads(
                context = androidContext,
                label = label,
                text = primary,
                mediaUris = emptyList(),
                jsonText = if (includeFullJson) fullJson else ""
            )
        }.onSuccess {
            actionStatus = "Saved ${it.summary}"
        }.onFailure {
            actionStatus = "Downloads save failed: ${it.message ?: "storage error"}"
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.32f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("COMMITTED", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            NetworkCopyValue("committed network result", primary, Modifier.fillMaxWidth(), prominent = true)
            Text(
                "This frozen payload is the result that will be returned or exported.",
                style = MaterialTheme.typography.bodySmall
            )
            if (workingChanged) {
                Text(
                    "The working dashboard has changed since Commit.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Include full JSON", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = includeFullJson, onCheckedChange = { includeFullJson = it })
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = ::copyResult, modifier = Modifier.weight(1f)) { Text("Copy") }
                OutlinedButton(onClick = ::shareResult, modifier = Modifier.weight(1f)) { Text("Share") }
                OutlinedButton(onClick = ::saveResult, modifier = Modifier.weight(1f)) { Text("Save") }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (!context.isNativePresetRun) {
                        onDone()
                    } else {
                        when (presetAction) {
                            PresetResultAction.SAVE -> {
                                saveResult()
                                onDone()
                            }
                            PresetResultAction.SHARE -> {
                                shareResult()
                                finishNativePresetHome()
                            }
                            else -> finishNativePresetHome()
                        }
                    }
                }
            ) {
                Text(
                    if (context.isNativePresetRun && presetAction == PresetResultAction.SAVE) "Save and finish"
                    else if (context.isNativePresetRun && presetAction == PresetResultAction.SHARE) "Share and finish"
                    else "Done"
                )
            }
            if (actionStatus.isNotBlank()) {
                Text(
                    actionStatus,
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
