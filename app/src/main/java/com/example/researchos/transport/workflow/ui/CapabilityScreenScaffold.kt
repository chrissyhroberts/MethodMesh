package com.example.researchos.transport.workflow.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.researchos.core.researchos.ExecutionResult
import com.example.researchos.transport.OutputExportRepository
import com.example.researchos.transport.workflow.ExternalActionRequest
import com.example.researchos.transport.workflow.ExternalWorkflowRequest

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
    }
) {
    val isLastStep: Boolean get() = stepNumber >= totalSteps
    val startsImmediately: Boolean get() = completionMode == CapabilityCompletionMode.AutomaticReturn
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
    val allowManualExport = !context.request.source.equals("dashboard", ignoreCase = true) &&
        !context.request.source.equals("intent_test", ignoreCase = true)
    var exportPackage by remember(capturedResult?.request?.id?.value) { mutableStateOf<OutputExportRepository.ExportPackage?>(null) }
    var exportStatus by rememberSaveable(capturedResult?.request?.id?.value) { mutableStateOf<String?>(null) }
    var showDetails by rememberSaveable { mutableStateOf(false) }
    var showAllResultFields by rememberSaveable(capturedResult?.request?.id?.value) { mutableStateOf(false) }

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
        shape = RoundedCornerShape(if (intentPresentation) 28.dp else 24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (intentPresentation) 0.dp else 5.dp),
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
                        color = MaterialTheme.colorScheme.secondaryContainer
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

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(if (intentPresentation) 24.dp else 18.dp),
                color = if (intentPresentation) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                }
            ) {
                Column(Modifier.padding(if (intentPresentation) 20.dp else 16.dp)) { content() }
            }

            Spacer(Modifier.height(12.dp))
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
                        if (allowManualExport) {
                            OutlinedButton(onClick = {
                                runCatching { OutputExportRepository.exportPackage(appContext, capturedResult) }
                                    .onSuccess {
                                        exportPackage = it
                                        exportStatus = "Exported ${it.summary}"
                                        OutputExportRepository.notifySaved(appContext, it)
                                    }
                                    .onFailure { exportStatus = "Export failed: ${it.message ?: "storage error"}" }
                            }) { Text("Export") }
                            Spacer(Modifier.width(8.dp))
                        }
                    }
                    Button(enabled = capturedResult != null, onClick = onConfirm) {
                        Text(if (context.isLastStep) "Use result" else "Continue")
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
                        Text(if (context.isLastStep) "Use result" else "Continue")
                    }
                }
            }

            if (!intentPresentation && capturedResult != null && resultPreview.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Captured result", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        val visibleFields = if (showAllResultFields) resultPreview.entries else resultPreview.entries.take(8)
                        SelectionContainer {
                            Column {
                                visibleFields.forEach { (key, value) ->
                                    Text(
                                        "$key = ${value?.toString().orEmpty()}",
                                        fontFamily = FontFamily.Monospace,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                        if (resultPreview.size > 8) {
                            Text(
                                text = if (showAllResultFields) "▲ Show fewer fields" else "▼ ${resultPreview.size - 8} more fields",
                                modifier = Modifier
                                    .clickable { showAllResultFields = !showAllResultFields }
                                    .padding(top = 8.dp),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelLarge
                            )
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
