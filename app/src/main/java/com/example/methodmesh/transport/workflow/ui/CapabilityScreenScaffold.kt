package com.example.methodmesh.transport.workflow.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.protocols.PresetResultAction
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.ResultShare
import com.example.methodmesh.transport.workflow.ExternalActionRequest
import com.example.methodmesh.transport.workflow.ExternalWorkflowRequest
import com.example.methodmesh.modules.MethodMeshModuleRegistry
import com.example.methodmesh.ui.components.VersionAndMaturityBadges

private fun isDashboardRequest(action: ExternalActionRequest, request: ExternalWorkflowRequest): Boolean {
    val caller = request.invocationContext.caller.lowercase()
    val source = request.source.lowercase()
    return source == "dashboard" ||
        source == "controller" ||
        request.settings["methodmesh_dashboard"].orEmpty().equals("true", ignoreCase = true) ||
        request.settings["methodmesh_controller"].orEmpty().equals("true", ignoreCase = true) ||
        action.settings["source"].orEmpty().equals("dashboard", ignoreCase = true) ||
        action.settings["source"].orEmpty().equals("controller", ignoreCase = true) ||
        caller == "dashboard" ||
        caller == "controller" ||
        caller.endsWith("_dashboard") ||
        caller.endsWith("_controller") ||
        caller.endsWith(" dashboard")
}

data class CapabilityScreenContext(
    val action: ExternalActionRequest,
    val request: ExternalWorkflowRequest,
    val stepNumber: Int,
    val totalSteps: Int,
    val completionMode: CapabilityCompletionMode = if (isDashboardRequest(action, request)) {
        CapabilityCompletionMode.ManualConfirmation
    } else {
        CapabilityCompletionMode.AutomaticReturn
    },
    val presentationMode: CapabilityPresentationMode = if (isDashboardRequest(action, request)) {
        CapabilityPresentationMode.Dashboard
    } else {
        CapabilityPresentationMode.IntentLaunch
    },
    val hostOwnsNativePresetCloseout: Boolean = false,
    val onSettingsChanged: (Map<String, String>) -> Unit = {}
) {
    val isLastStep: Boolean get() = stepNumber >= totalSteps
    val isNativePresetRun: Boolean get() =
        request.settings["methodmesh_native_preset_run"] == "true" ||
            request.settings["input_methodmesh_native_preset_run"] == "true"
    val startsImmediately: Boolean get() =
        (completionMode == CapabilityCompletionMode.AutomaticReturn || isNativePresetRun) &&
            !awaitingRuntimeInputs
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

    /** Native preset runs wait for their declared Ask when run values. */
    val awaitingRuntimeInputs: Boolean get() =
        isNativePresetRun && runtimeInputFields.isNotEmpty()

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

/*
 * Describes how much of the MethodMesh host surface a capability wants.
 *
 * This is deliberately capability-agnostic. The runtime knows only the
 * requested presentation, never which capability is requesting it.
 */
enum class CapabilityHostPresentation {
    Standard,
    Immersive
}

interface CapabilityScreenSpec {
    val capabilityId: String
    val title: String
    val description: String

    val hostPresentation: CapabilityHostPresentation
        get() = CapabilityHostPresentation.Standard

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
    val capabilityMetadata = remember(capabilityId) {
        runCatching { MethodMeshModuleRegistry.capabilityMetadata(capabilityId) }.getOrNull()
    }
    val intentPresentation = context.presentationMode == CapabilityPresentationMode.IntentLaunch
    val automaticReturn = context.completionMode == CapabilityCompletionMode.AutomaticReturn
    val nativePresetRun = context.isNativePresetRun && !automaticReturn
    val presetResultAction = PresetResultAction.normalize(
        context.request.settings["methodmesh_preset_result_action"]
            ?: context.request.settings["input_methodmesh_preset_result_action"]
            ?: PresetResultAction.HOME
    )
    val finishToLauncher = context.request.settings["methodmesh_finish_to_launcher"] == "true" ||
        context.request.settings["input_methodmesh_finish_to_launcher"] == "true"
    var showDetails by rememberSaveable { mutableStateOf(false) }
    var actionsBusy by remember(capturedResult?.request?.id?.value) { mutableStateOf(false) }
    var automaticDeliveryId by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
    val userResultPreview = remember(capturedResult?.request?.id?.value, resultPreview) {
        OutputFormatter.projectFields(resultPreview, OutputFormatter.PayloadMode.CORE, capturedResult?.status)
    }
    val resultDetailPreview = remember(capturedResult?.request?.id?.value, resultPreview, userResultPreview) {
        resultPreview.filterKeys { it !in userResultPreview.keys }
    }
    val showResultScreen = capturedResult != null && !automaticReturn
    val mediaResultUris = remember(capturedResult?.request?.id?.value, userResultPreview, resultDetailPreview) {
        shareableResultUris(userResultPreview + resultDetailPreview)
    }
    val finishButtonLabel = if (nativePresetRun && context.isLastStep) {
        when (presetResultAction) {
            PresetResultAction.SAVE -> "Save and finish"
            PresetResultAction.SHARE -> "Share and finish"
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
        if (capturedResult != null && automaticReturn && automaticDeliveryId != capturedResult.request.id.value) {
            automaticDeliveryId = capturedResult.request.id.value
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
            capabilityMetadata?.let { metadata ->
                Spacer(Modifier.height(8.dp))
                VersionAndMaturityBadges(
                    version = metadata.version,
                    maturity = metadata.maturity
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${metadata.module.name} · module v${metadata.module.version}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
                    CanonicalCommittedResultActions(
                        result = checkNotNull(capturedResult), label = title, screenContext = context,
                        primaryText = humanShareText(userResultPreview),
                        // Keep the existing domain delivery-bundle selection; never fan out a compiler ZIP.
                        attachments = mediaResultUris.map { uri ->
                            ResultShare.Attachment(uri.lastPathSegment.orEmpty().ifBlank { "attachment" }, uri)
                        },
                        completionLabel = finishButtonLabel,
                        shareButtonLabel = if (mediaResultUris.isNotEmpty()) shareMediaLabel(mediaResultUris) else "Share result",
                        onDone = onConfirm,
                        onBusyChanged = { actionsBusy = it }
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (canGoBack) {
                            OutlinedButton(onClick = { if (!actionsBusy) onBack() }, enabled = !actionsBusy,
                                modifier = Modifier.weight(1f)) { Text("Back") }
                        }
                        OutlinedButton(onClick = { if (!actionsBusy) onRetry() }, enabled = !actionsBusy,
                            modifier = Modifier.weight(1f)) { Text("Retry") }
                        OutlinedButton(onClick = { if (!actionsBusy) onCancel() }, enabled = !actionsBusy,
                            modifier = Modifier.weight(1f)) { Text("Cancel") }
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
                CopyableResultValue(
                    key = key,
                    value = value,
                    textStyle = MaterialTheme.typography.bodyMedium
                )
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
        val copyText = clipboardValueForResult(key, valueText)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = copyText.isNotBlank()) {
                    copyResultValue(context, friendlyResultLabel(key), copyText)
                }
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = friendlyResultLabel(key),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp),
                contentScale = ContentScale.Fit
            )
            if (copyText.isNotBlank()) {
                Text(
                    text = copyText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Tap to copy",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    } else {
        CopyableResultValue(
            key = key,
            value = value,
            textStyle = if (valueText.length > 120) {
                MaterialTheme.typography.bodyLarge
            } else {
                MaterialTheme.typography.headlineSmall
            }
        )
    }
}

@Composable
private fun CopyableResultValue(
    key: String,
    value: Any?,
    textStyle: androidx.compose.ui.text.TextStyle
) {
    val context = LocalContext.current
    val rawValueText = value?.toString().orEmpty()
    val copyText = clipboardValueForResult(key, rawValueText)
    val displayText = if (rawValueText.startsWith("content://") || rawValueText.startsWith("file://")) {
        copyText
    } else {
        rawValueText
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = copyText.isNotBlank()) {
                copyResultValue(context, friendlyResultLabel(key), copyText)
            }
            .padding(vertical = 2.dp)
    ) {
        Text(text = displayText, style = textStyle)
        if (copyText.isNotBlank()) {
            Text(
                text = "Tap to copy",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun clipboardValueForResult(key: String, valueText: String): String {
    if (valueText.isBlank()) return ""
    if (valueText.startsWith("content://") || valueText.startsWith("file://")) {
        val parsed = runCatching { Uri.parse(valueText) }.getOrNull()
        val friendly = parsed?.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        return friendly ?: friendlyResultLabel(key)
    }
    return valueText
}

private fun copyResultValue(
    context: android.content.Context,
    label: String,
    value: String
) {
    if (value.isBlank()) return
    context.getSystemService(ClipboardManager::class.java)
        .setPrimaryClip(ClipData.newPlainText(label, value))
    Toast.makeText(context, "Copied $label", Toast.LENGTH_SHORT).show()
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

/** A compiler delivery bundle is the handoff artifact; do not fan it back out
 * into its constituent XLSX/proof files when the operator presses Share. */
private fun shareableResultUris(fields: Map<String, Any?>): List<Uri> {
    val delivery = fields.entries.firstOrNull { (key, value) ->
        key.equals("delivery_zip_uri", ignoreCase = true) &&
            value?.toString()?.trim()?.let { ResultShare.isShareableMediaField(key, it) } == true
    }?.value?.toString()?.trim()
    return if (!delivery.isNullOrBlank()) listOf(Uri.parse(delivery)) else ResultShare.shareableMediaUris(fields)
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
    return ResultShare.isShareableMediaField(key, value)
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
