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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.researchos.core.researchos.ExecutionResult
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
    }
) {
    val isLastStep: Boolean get() = stepNumber >= totalSteps
    val startsImmediately: Boolean get() = completionMode == CapabilityCompletionMode.AutomaticReturn
}

enum class CapabilityCompletionMode {
    ManualConfirmation,
    AutomaticReturn
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
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(20.dp)) {
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
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Text(
                    text = if (capturedResult == null) "In progress" else "Ready to return",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (capturedResult == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                text = context.action.requestedId,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(18.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            ) {
                Column(Modifier.padding(16.dp)) { content() }
            }

            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (canGoBack) {
                    OutlinedButton(onClick = onBack) { Text("Back") }
                    Spacer(Modifier.width(8.dp))
                }
                OutlinedButton(onClick = onCancel) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                if (capturedResult != null) {
                    OutlinedButton(onClick = onRetry) { Text("Retry") }
                    Spacer(Modifier.width(8.dp))
                }
                Button(enabled = capturedResult != null, onClick = onConfirm) {
                    Text(if (context.isLastStep) "Use result" else "Continue")
                }
            }

            if (capturedResult != null && resultPreview.isNotEmpty()) {
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
