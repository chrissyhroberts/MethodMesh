package com.example.researchos.transport.workflow.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.researchos.core.researchos.ExecutionResult
import com.example.researchos.modules.ResearchOSModuleRegistry
import com.example.researchos.transport.workflow.ExternalActionRequest

/** Generic module-to-module invocation boundary; it contains no capability knowledge. */
@Composable
fun CapabilityDependencyScreen(
    capabilityId: String,
    parentContext: CapabilityScreenContext,
    settings: Map<String, String> = emptyMap(),
    onResult: (ExecutionResult) -> Unit,
    onCancel: () -> Unit
) {
    val screen = ResearchOSModuleRegistry.screenFor(capabilityId)
    if (screen == null) {
        Text("Required capability is not installed: $capabilityId")
        return
    }
    screen.Render(
        context = parentContext.copy(
            action = ExternalActionRequest(capabilityId, capabilityId, settings),
            stepNumber = 1,
            totalSteps = 1,
            completionMode = CapabilityCompletionMode.AutomaticReturn
        ),
        onBack = onCancel,
        onConfirmed = onResult,
        onCancel = onCancel
    )
}
