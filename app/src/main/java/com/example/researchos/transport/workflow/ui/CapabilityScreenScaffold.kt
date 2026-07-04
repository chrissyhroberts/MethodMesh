package com.example.researchos.transport.workflow.ui

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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.researchos.core.researchos.ExecutionResult
import com.example.researchos.transport.workflow.ExternalActionRequest
import com.example.researchos.transport.workflow.ExternalWorkflowRequest

/**
 * Context passed to any focused capability execution screen.
 *
 * The workflow runner owns sequence, graph recording and return transport. The
 * capability screen only owns its own capture/retry/review/confirm interaction.
 */
data class CapabilityScreenContext(
    val action: ExternalActionRequest,
    val request: ExternalWorkflowRequest,
    val stepNumber: Int,
    val totalSteps: Int
) {
    val isFirstStep: Boolean get() = stepNumber <= 1
    val isLastStep: Boolean get() = stepNumber >= totalSteps
}

/**
 * Minimal base contract for capability screens. A production capability should
 * expose one focused implementation of this contract, even if it is also shown
 * elsewhere in dashboard/debug mode.
 */
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
 * Shared frame for all externally invoked capability screens.
 *
 * This standardises the operator experience: every action shows the same
 * workflow context, capability body, result preview and Back / Retry / Confirm /
 * Cancel controls. Capability-specific code supplies only the capture body and
 * the current ExecutionResult.
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Step ${context.stepNumber} of ${context.totalSteps}", fontWeight = FontWeight.SemiBold)
            Text(title, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Action: ${context.action.requestedId}", fontFamily = FontFamily.Monospace)
            Text("Capability: $capabilityId", fontFamily = FontFamily.Monospace)
            Text("Subject: ${context.request.invocationContext.canonicalEntityId}", fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(14.dp))

            content()

            Spacer(Modifier.height(14.dp))
            Text("Result preview", fontWeight = FontWeight.SemiBold)
            if (capturedResult == null || resultPreview.isEmpty()) {
                Text("No confirmed capture yet.")
            } else {
                resultPreview.entries.take(18).forEach { (key, value) ->
                    Text("$key = ${value?.toString().orEmpty()}", fontFamily = FontFamily.Monospace)
                }
                if (resultPreview.size > 18) {
                    Text("… ${resultPreview.size - 18} more fields")
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = canGoBack, onClick = onBack) { Text("Back") }
                OutlinedButton(onClick = onCancel) { Text("Cancel") }
                OutlinedButton(onClick = onRetry) { Text(if (capturedResult == null) "Start / Retry" else "Retry") }
                Button(enabled = capturedResult != null, onClick = onConfirm) { Text("Confirm") }
            }
        }
    }
}
