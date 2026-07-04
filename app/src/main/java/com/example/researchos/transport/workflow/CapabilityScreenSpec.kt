package com.example.researchos.transport.workflow

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
import com.example.researchos.core.researchos.InvocationContext

/**
 * Canonical UI contract for a ResearchOS capability when it is run as part of an
 * externally requested workflow.
 *
 * The dashboard can still render compact cards and debugging panels, but any
 * capability that is callable from ODK or another third-party app should expose
 * one focused screen through this interface. The workflow runner owns sequence,
 * back/cancel behaviour, graph recording and final return. The capability screen
 * owns only capture/retry/review/confirm for its own action.
 */
interface CapabilityScreenSpec {
    val capabilityId: String
    val title: String
    val description: String

    @Composable
    fun Render(
        context: InvocationContext,
        stepContext: WorkflowStepContext,
        action: ExternalActionRequest,
        request: ExternalWorkflowRequest,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    )
}

data class WorkflowStepContext(
    val stepIndex: Int,
    val stepCount: Int,
    val confirmedStepCount: Int
) {
    val displayStep: Int get() = stepIndex + 1
    val isFirst: Boolean get() = stepIndex == 0
    val isLast: Boolean get() = stepIndex == stepCount - 1
}

enum class CapabilityScreenPhase {
    Idle,
    Capturing,
    Captured,
    Failed
}

/**
 * Shared outer frame used by capability-specific screens. This gives every
 * capability the same operational structure: context, focused capture body,
 * result preview, and workflow navigation managed by the runner.
 */
@Composable
fun CapabilityScreenFrame(
    spec: CapabilityScreenSpec,
    context: InvocationContext,
    stepContext: WorkflowStepContext,
    action: ExternalActionRequest,
    onBack: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Step ${stepContext.displayStep} of ${stepContext.stepCount}",
                fontWeight = FontWeight.SemiBold
            )
            Text(spec.title, fontWeight = FontWeight.Bold)
            Text(spec.description)
            Spacer(Modifier.height(8.dp))
            Text("Action: ${action.requestedId}", fontFamily = FontFamily.Monospace)
            Text("Capability: ${action.canonicalId}", fontFamily = FontFamily.Monospace)
            Text("Subject: ${context.canonicalEntityId}", fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(14.dp))
            content()
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onBack != null) {
                    OutlinedButton(onClick = onBack, enabled = !stepContext.isFirst) { Text("Back") }
                }
                if (onCancel != null) {
                    OutlinedButton(onClick = onCancel) { Text("Cancel") }
                }
            }
        }
    }
}
