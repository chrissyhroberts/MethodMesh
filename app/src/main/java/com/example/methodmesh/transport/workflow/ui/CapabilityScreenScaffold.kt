package com.example.methodmesh.transport.workflow.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.methodmesh.MainActivity
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.protocols.PresetResultAction
import com.example.methodmesh.transport.OutputExportRepository
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.ReturnMode
import com.example.methodmesh.transport.workflow.ExternalActionRequest
import com.example.methodmesh.transport.workflow.ExternalWorkflowRequest
import java.io.File

data class CapabilityScreenContext(
    val action: ExternalActionRequest,
    val request: ExternalWorkflowRequest,
    val stepNumber: Int,
    val totalSteps: Int,
    val completionMode: CapabilityCompletionMode = if (request.source.equals("dashboard", ignoreCase = true)) {
        CapabilityCompletionMode.ManualConfirmation
    } else {
        CapabilityCompletionMode.AutomaticReturn
    },
    val presentationMode: CapabilityPresentationMode = if (request.source.equals("dashboard", ignoreCase = true)) {
        CapabilityPresentationMode.Dashboard
    } else {
        CapabilityPresentationMode.IntentLaunch
    },
    val onSettingsChanged: (Map<String, String>) -> Unit = {}
) {
    val isLastStep: Boolean get() = stepNumber >= totalSteps
    val isNativePresetRun: Boolean get() =
        request.settings["methodmesh_native_preset_run"] == "true" ||
            request.settings["input_methodmesh_native_preset_run"] == "true"
    val startsImmediately: Boolean get() =
        completionMode == CapabilityCompletionMode.AutomaticReturn ||
            isNativePresetRun
    val submitsImmediately: Boolean get() = completionMode == CapabilityCompletionMode.AutomaticReturn

    val runtimeInputFields: Set<String> get() =
        (action.settings["methodmesh_runtime_fields"]
            ?: action.settings["input_methodmesh_runtime_fields"]
            ?: request.settings["methodmesh_runtime_fields"]
            ?: request.settings["input_methodmesh_runtime_fields"])
            .orEmpty()
            .split(',', '|', ';', '\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()

    fun settingIsRuntimeInput(settingId: String): Boolean =
        settingId in runtimeInputFields

    fun settingIsFixedInNativePreset(settingId: String): Boolean =
        isNativePresetRun && settingId !in runtimeInputFields

    fun settingShouldBeShown(settingId: String, alwaysShow: Boolean = false): Boolean =
        alwaysShow || !settingIsFixedInNativePreset(settingId)
}

enum class CapabilityCompletionMode {
    ManualConfirmation,
    AutomaticReturn
}

enum class CapabilityPresentationMode {
    Dashboard,
    IntentLaunch
}

interface CapabilityScreenSpec {
    val capabilityId: String
    val title: String
    val description: String

    @Composable
    fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    )
}

/**
 * Shared execution surface for intent-invoked capabilities.
 *
 * Capability implementations start their work on entry. The shared frame never
 * presents a second "start" gate; Retry is shown only after an attempt has
 * completed or when the operator needs to repeat capture.
 */
@Composable
fun CapabilityScreenScaffold(
    title: String,
    capabilityId: String,
    context: CapabilityScreenContext,
    canGoBack: Boolean,
    capturedResult: ExecutionResult?,
    resultPreview: Map<String, Any?>,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    content: @Composable () -> Unit
) {
    val appContext = LocalContext.current
    val intentPresentation = context.presentationMode == CapabilityPresentationMode.IntentLaunch
    val automaticReturn = context.completionMode == CapabilityCompletionMode.AutomaticReturn
    val nativePresetRun = intentPresentation && !automaticReturn &&
        (context.request.settings["methodmesh_native_preset_run"] == "true" ||
            context.request.settings["input_methodmesh_native_preset_run"] == "true")
    val presetResultAction = PresetResultAction.normalize(
        context.request.settings["methodmesh_preset_result_action"]
            ?: context.request.settings["input_methodmesh_preset_result_action"]
            ?: PresetResultAction.HOME
    )
    val finishToLauncher = context.request.settings["methodmesh_finish_to_launcher"] == "true" ||
        context.request.settings["input_methodmesh_finish_to_launcher"] == "true"
    val allowManualExport = !context.request.source.equals("dashboard", ignoreCase = true) &&
        !context.request.source.equals("intent_test", ignoreCase = true)
    var exportPackage by remember(capturedResult?.request?.id?.value) { mutableStateOf<OutputExportRepository.ExportPackage?>(null) }
    var exportStatus by rememberSaveable(capturedResult?.request?.id?.value) { mutableStateOf<String?>(null) }
    var shareStatus by rememberSaveable(capturedResult?.request?.id?.value) { mutableStateOf<String?>(null) }
    var showDetails by rememberSaveable { mutableStateOf(false) }
    var includeFullJson by rememberSaveable(capturedResult?.request?.id?.value) { mutableStateOf(false) }
    val userResultPreview = remember(capturedResult?.request?.id?.value, resultPreview) {
        OutputFormatter.projectFields(resultPreview, OutputFormatter.PayloadMode.CORE, capturedResult?.status)
    }
    val resultDetailPreview = remember(capturedResult?.request?.id?.value, resultPreview, userResultPreview) {
        resultPreview.filterKeys { it !in userResultPreview.keys }
    }
    val showResultScreen = capturedResult != null && !automaticReturn && userResultPreview.isNotEmpty()
    val mediaResultUris = remember(capturedResult?.request?.id?.value, userResultPreview, resultDetailPreview) {
        (userResultPreview + resultDetailPreview)
            .filter { (key, value) -> looksLikeShareableMediaUri(key, value?.toString().orEmpty()) }
            .mapNotNull { (_, value) -> value?.toString()?.let(Uri::parse) }
    }
    val fullJsonText = remember(capturedResult?.request?.id?.value) {
        capturedResult?.let {
            OutputFormatter.format(
                result = it,
                returnMode = ReturnMode.Json,
                includeProvenance = true,
                payloadMode = OutputFormatter.PayloadMode.FULL
            )
        }.orEmpty()
    }
    fun finishNativePreset() {
        if (presetResultAction == PresetResultAction.SAVE) {
            runCatching {
                OutputExportRepository.saveToDownloads(
                    context = appContext,
                    label = title,
                    text = humanShareText(userResultPreview),
                    mediaUris = mediaResultUris.map(Uri::toString)
                )
            }
                .onSuccess {
                    exportStatus = "Saved ${it.summary}"
                }
                .onFailure { exportStatus = "Downloads save failed: ${it.message ?: "storage error"}" }
            onConfirm()
            return
        }
        if (finishToLauncher) {
            onConfirm()
            return
        }
        appContext.startActivity(
            Intent(appContext, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
    val finishButtonLabel = if (nativePresetRun && context.isLastStep) {
        when (presetResultAction) {
            PresetResultAction.SAVE -> "Save and finish"
            PresetResultAction.SHARE -> if (finishToLauncher) "Done" else "Home"
            else -> if (finishToLauncher) "Done" else "Home"
        }
    } else if (context.isLastStep) {
        "Done"
    } else {
        "Continue"
    }

    // One completion rule for every capability: dashboard/debug captures wait
    // for an explicit Use result; external and dependency captures return as
    // soon as a result exists. The execution ID keys this effect so a result is
    // never delivered twice during recomposition.
    LaunchedEffect(context.completionMode, capturedResult?.request?.id?.value) {
        if (capturedResult != null && context.completionMode == CapabilityCompletionMode.AutomaticReturn) {
            onConfirm()
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (intentPresentation) 20.dp else 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (intentPresentation) {
                MaterialTheme.colorScheme.background
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(Modifier.padding(if (intentPresentation) 24.dp else 20.dp)) {
            if (!intentPresentation || context.totalSteps > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "Step ${context.stepNumber} of ${context.totalSteps}",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Text(
                        text = if (capturedResult == null) "In progress" else if (automaticReturn) "Returning…" else "Ready",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (capturedResult == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                    )
                }
                Spacer(Modifier.height(if (intentPresentation) 20.dp else 16.dp))
            }

            Text(
                title,
                style = if (intentPresentation) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = context.action.requestedId,
                style = if (intentPresentation) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(if (intentPresentation) 24.dp else 18.dp))

            if (showResultScreen) {
                ClearResultPanel(
                    fields = userResultPreview,
                    detailFields = resultDetailPreview,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(if (intentPresentation) 18.dp else 14.dp),
                    color = if (intentPresentation) {
                        MaterialTheme.colorScheme.surface
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
                    }
                ) {
                    Column(Modifier.padding(if (intentPresentation) 20.dp else 16.dp)) { content() }
                }
            }

            Spacer(Modifier.height(12.dp))
            if (showResultScreen) {
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Include full JSON", style = MaterialTheme.typography.titleSmall)
                        Switch(
                            checked = includeFullJson,
                            onCheckedChange = { includeFullJson = it },
                            enabled = fullJsonText.isNotBlank()
                        )
                    }
                    Text(
                        if (includeFullJson) "Share, copy and downloads will include metadata JSON." else "Share, copy and downloads use only the main result.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            runCatching {
                                shareResultBundle(
                                    context = appContext,
                                    text = humanShareText(userResultPreview),
                                    mediaUris = mediaResultUris,
                                    jsonText = if (includeFullJson) fullJsonText else ""
                                )
                            }
                                .onSuccess { shareStatus = "Sharing result…" }
                                .onFailure { shareStatus = "Share failed: ${it.message ?: "no sharing app available"}" }
                        }
                    ) { Text(if (mediaResultUris.isNotEmpty()) shareMediaLabel(mediaResultUris) else "Share result") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            runCatching {
                                val text = humanShareText(userResultPreview) + if (includeFullJson && fullJsonText.isNotBlank()) {
                                    "\n\nmetadata.json\n$fullJsonText"
                                } else {
                                    ""
                                }
                                if (text.isBlank()) throw IllegalStateException("No text result to copy.")
                                appContext.getSystemService(ClipboardManager::class.java)
                                    .setPrimaryClip(ClipData.newPlainText("MethodMesh result", text))
                            }
                                .onSuccess { shareStatus = "Copied result." }
                                .onFailure { shareStatus = "Copy failed: ${it.message ?: "no text result"}" }
                        },
                        enabled = userResultPreview.any { (key, value) -> !looksLikeShareableMediaUri(key, value?.toString().orEmpty()) }
                    ) { Text("Copy") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            runCatching {
                                OutputExportRepository.saveToDownloads(
                                    context = appContext,
                                    label = title,
                                    text = humanShareText(userResultPreview),
                                    mediaUris = mediaResultUris.map(Uri::toString),
                                    jsonText = if (includeFullJson) fullJsonText else ""
                                )
                            }
                                .onSuccess { exportStatus = "Saved ${it.summary}" }
                                .onFailure { exportStatus = "Downloads save failed: ${it.message ?: "storage error"}" }
                        },
                        enabled = userResultPreview.isNotEmpty() || mediaResultUris.isNotEmpty()
                    ) { Text("Save to Downloads") }
                    Spacer(Modifier.height(8.dp))
                    if (nativePresetRun && context.isLastStep) {
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { finishNativePreset() }
                        ) { Text(finishButtonLabel) }
                        Spacer(Modifier.height(8.dp))
                    } else {
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onConfirm
                        ) { Text(finishButtonLabel) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (canGoBack) {
                            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Back") }
                        }
                        OutlinedButton(onClick = onRetry, modifier = Modifier.weight(1f)) { Text("Retry") }
                        OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    }
                    shareStatus?.let {
                        Text(it, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (intentPresentation) Arrangement.Start else Arrangement.End
                ) {
                    if (canGoBack) {
                        OutlinedButton(onClick = onBack) { Text("Back") }
                        Spacer(Modifier.width(8.dp))
                    }
                    OutlinedButton(onClick = onCancel) { Text("Cancel") }
                    if (!intentPresentation) {
                        Spacer(Modifier.width(8.dp))
                        if (capturedResult != null) {
                            OutlinedButton(onClick = onRetry) { Text("Retry") }
                            Spacer(Modifier.width(8.dp))
                        }
                        Button(enabled = capturedResult != null, onClick = onConfirm) {
                            Text(if (context.isLastStep) "Done" else "Continue")
                        }
                    } else if (capturedResult != null && automaticReturn) {
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Returning to calling app…",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else if (capturedResult != null) {
                        Spacer(Modifier.width(8.dp))
                        Button(enabled = true, onClick = onConfirm) {
                            Text(if (context.isLastStep) "Done" else "Continue")
                        }
                    }
                }
            }

            if (exportStatus != null || exportPackage != null) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        exportStatus?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        exportPackage?.let { pkg ->
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "JSON: ${pkg.json.filename}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            if (pkg.attachments.isNotEmpty()) {
                                Text(
                                    "Attachments: ${pkg.attachments.size}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {
                                    runCatching { OutputExportRepository.openLocation(appContext, pkg) }
                                        .onFailure { exportStatus = "Open failed: ${it.message ?: "no file app available"}" }
                                }) { Text(if (pkg.folderUri != null) "Open folder" else "Open file") }
                                Button(onClick = {
                                    runCatching { OutputExportRepository.share(appContext, pkg) }
                                        .onFailure { exportStatus = "Share failed: ${it.message ?: "no sharing app available"}" }
                                }) { Text("Share") }
                                if (pkg.attachments.any { it.mimeType.startsWith("image/") || it.mimeType.startsWith("audio/") || it.mimeType.startsWith("video/") || it.mimeType == "application/pdf" }) {
                                    OutlinedButton(onClick = {
                                        runCatching { OutputExportRepository.shareMedia(appContext, pkg) }
                                            .onFailure { exportStatus = "Media share failed: ${it.message ?: "no media available"}" }
                                    }) { Text("Share media") }
                                }
                            }
                        }
                    }
                }
            }

            if (!intentPresentation) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = if (showDetails) "Hide technical details" else "Technical details",
                    modifier = Modifier.clickable { showDetails = !showDetails }.padding(vertical = 6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge
                )
                AnimatedVisibility(showDetails) {
                    Column {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Text("Capability: $capabilityId", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        Text("Subject: ${context.request.invocationContext.canonicalEntityId}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        Text("Source: ${context.request.source}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun ClearResultPanel(
    fields: Map<String, Any?>,
    detailFields: Map<String, Any?>,
    modifier: Modifier = Modifier
) {
    var showAll by rememberSaveable(fields.hashCode()) { mutableStateOf(false) }
    var showInput by rememberSaveable(fields.hashCode(), detailFields.hashCode()) { mutableStateOf(false) }
    var showSettings by rememberSaveable(fields.hashCode(), detailFields.hashCode()) { mutableStateOf(false) }
    val visibleFields = if (showAll) fields.entries.toList() else fields.entries.take(8)
    val inputFields = detailFields.filterKeys(::isInputDetailField)
    val settingsFields = detailFields.filterKeys { !isInputDetailField(it) }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    ) {
        Column(
            Modifier
                .padding(20.dp)
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Result", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            visibleFields.forEach { (key, value) ->
                ResultField(key = key, value = value)
                Spacer(Modifier.height(12.dp))
            }
            if (fields.size > 8) {
                Text(
                    text = if (showAll) "Show less" else "Show ${fields.size - 8} more",
                    modifier = Modifier
                        .clickable { showAll = !showAll }
                        .padding(top = 2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge
                )
            }
            if (inputFields.isNotEmpty()) {
                ResultDetailsToggle(
                    title = "Original input",
                    expanded = showInput,
                    onToggle = { showInput = !showInput },
                    fields = inputFields
                )
            }
            if (settingsFields.isNotEmpty()) {
                ResultDetailsToggle(
                    title = "Settings and details",
                    expanded = showSettings,
                    onToggle = { showSettings = !showSettings },
                    fields = settingsFields
                )
            }
        }
    }
}

@Composable
private fun ResultDetailsToggle(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    fields: Map<String, Any?>
) {
    Spacer(Modifier.height(4.dp))
    Text(
        text = if (expanded) "▼ $title" else "▶ $title",
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 8.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
    )
    AnimatedVisibility(expanded) {
        Column {
            fields.forEach { (key, value) ->
                Text(
                    text = friendlyResultLabel(key),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                SelectionContainer {
                    Text(
                        text = value?.toString().orEmpty(),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun ResultField(key: String, value: Any?) {
    val valueText = value?.toString().orEmpty()
    val context = LocalContext.current
    val bitmap = remember(key, valueText) {
        if (!looksLikeImageUri(key, valueText)) {
            null
        } else {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(valueText))?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }.getOrNull()
        }
    }
    Text(
        text = friendlyResultLabel(key),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(Modifier.height(4.dp))
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = friendlyResultLabel(key),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 340.dp),
            contentScale = ContentScale.Fit
        )
        Text(
            text = valueText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        SelectionContainer {
            Text(
                text = valueText,
                style = if (valueText.length > 120) {
                    MaterialTheme.typography.bodyLarge
                } else {
                    MaterialTheme.typography.headlineSmall
                }
            )
        }
    }
}

private fun looksLikeImageUri(key: String, value: String): Boolean {
    if (!(value.startsWith("content://") || value.startsWith("file://"))) return false
    val lower = value.lowercase()
    return key.contains("image", ignoreCase = true) ||
        lower.endsWith(".jpg") ||
        lower.endsWith(".jpeg") ||
        lower.endsWith(".png") ||
        lower.endsWith(".webp")
}

private fun isInputDetailField(key: String): Boolean =
    key.endsWith("_input_text") ||
        key.contains("_input_", ignoreCase = true) ||
        key.endsWith("_source_text") ||
        key.endsWith("_source_uri") ||
        key.endsWith("_source") ||
        key.endsWith("_original_text") ||
        key.endsWith("_original_uri")

private fun shareResultBundle(
    context: android.content.Context,
    text: String,
    mediaUris: List<Uri>,
    jsonText: String
) {
    val shareable = ArrayList(mediaUris.map { shareableUri(context, it) })
    if (jsonText.isNotBlank()) shareable += temporaryJsonShareUri(context, jsonText)
    if (shareable.isEmpty() && text.isBlank()) throw IllegalStateException("No shareable result.")
    val intent = if (shareable.isEmpty()) {
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
    } else if (shareable.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            type = mediaMimeType(shareable.first().toString())
            putExtra(Intent.EXTRA_STREAM, shareable.first())
            if (text.isNotBlank()) putExtra(Intent.EXTRA_TEXT, text)
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, shareable)
            if (text.isNotBlank()) putExtra(Intent.EXTRA_TEXT, text)
        }
    }.apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share result").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun temporaryJsonShareUri(context: android.content.Context, jsonText: String): Uri {
    val folder = File(context.cacheDir, "methodmesh_share").apply { mkdirs() }
    val file = File(folder, "metadata_${System.currentTimeMillis()}.json")
    file.writeText(jsonText, Charsets.UTF_8)
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

private fun shareMediaLabel(uris: List<Uri>): String {
    if (uris.size > 1) return "Share files"
    val value = uris.firstOrNull()?.toString().orEmpty().lowercase()
    return when {
        value.endsWith(".pdf") || value.contains("pdf") -> "Share document"
        value.endsWith(".jpg") || value.endsWith(".jpeg") || value.endsWith(".png") || value.endsWith(".webp") || value.contains("image") -> "Share image"
        else -> "Share file"
    }
}

private fun humanShareText(fields: Map<String, Any?>): String {
    listOf("barcode_payload", "plus_code").forEach { key ->
        fields[key]?.toString()?.takeIf { it.isNotBlank() }?.let { return it }
    }
    val textLike = fields.entries
        .filterNot { (key, value) -> looksLikeShareableMediaUri(key, value?.toString().orEmpty()) }
        .filterNot { (key, _) -> key.endsWith("_name") || key.endsWith("_filename") }
    if (textLike.size == 1) return textLike.first().value?.toString().orEmpty()
    return textLike.joinToString("\n") { (key, value) -> "${friendlyResultLabel(key)}: ${value?.toString().orEmpty()}" }
}

private fun looksLikeShareableMediaUri(key: String, value: String): Boolean {
    if (!(value.startsWith("content://") || value.startsWith("file://") || value.startsWith("/"))) return false
    val lower = value.lowercase()
    return key.contains("image", ignoreCase = true) ||
        key.contains("photo", ignoreCase = true) ||
        key.contains("pdf", ignoreCase = true) ||
        lower.endsWith(".jpg") ||
        lower.endsWith(".jpeg") ||
        lower.endsWith(".png") ||
        lower.endsWith(".webp") ||
        lower.endsWith(".pdf")
}

private fun shareableUri(context: android.content.Context, uri: Uri): Uri {
    val value = uri.toString()
    return when {
        value.startsWith("content://") -> uri
        value.startsWith("file://") -> File(value.removePrefix("file://")).let { file ->
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
        else -> File(value).let { file ->
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
    }
}

private fun mediaMimeType(value: String): String = when (value.substringAfterLast('.', "").lowercase()) {
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "webp" -> "image/webp"
    "pdf" -> "application/pdf"
    else -> "*/*"
}

private fun friendlyResultLabel(key: String): String {
    val cleaned = key
        .removePrefix("methodmesh_")
        .removePrefix("sensor_")
        .removePrefix("mlkit_translate_")
        .removePrefix("image_redaction_")
        .removePrefix("image_highlight_")
        .replace("_pct", " %")
        .replace("_c", " °C")
        .replace("_cm", " cm")
        .replace("_uri", "")
        .replace('_', ' ')
        .trim()
    return cleaned.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}
