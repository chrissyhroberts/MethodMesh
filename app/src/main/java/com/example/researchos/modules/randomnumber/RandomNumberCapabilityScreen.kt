package com.example.researchos.modules.randomnumber

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.researchos.core.researchos.ExecutionResult
import com.example.researchos.transport.OutputFormatter
import com.example.researchos.transport.workflow.ui.CapabilityScreenContext
import com.example.researchos.transport.workflow.ui.CapabilityScreenScaffold
import com.example.researchos.transport.workflow.ui.CapabilityScreenSpec
import com.example.researchos.transport.workflow.ui.CapabilityPresentationMode

object RandomNumberCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100RandomNumberMethod.ID
    override val title = "Random number generator"
    override val description = "Generate secure or reproducible random numbers."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val count = context.action.settings["count"] ?: context.action.settings["input_count"] ?: "1"
        val min = context.action.settings["min"] ?: context.action.settings["input_min"] ?: "0"
        val max = context.action.settings["max"] ?: context.action.settings["input_max"] ?: "1"
        val step = context.action.settings["step"] ?: context.action.settings["input_step"] ?: "1"
        val seedMode = context.action.settings["seed_mode"] ?: context.action.settings["input_seed_mode"] ?: "secure_random"
        val seed = context.action.settings["seed"] ?: context.action.settings["input_seed"] ?: ""
        var launched by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var status by remember { mutableStateOf("Ready to generate.") }

        fun generate() {
            val settings = mapOf("count" to count, "min" to min, "max" to max, "step" to step, "seed_mode" to seedMode, "seed" to seed)
            val request = As100RandomNumberMethod.request(
                action = As100RandomNumberMethod.ID,
                context = context.request.invocationContext.asMap(As100RandomNumberMethod.ID) + context.action.settings + settings,
                signals = emptyList(),
                inputs = emptyList()
            )
            val execution = As100RandomNumberMethod.result(request, As100RandomNumberMethod.generate(settings), context.request.invocationContext)
            result = execution
            status = OutputFormatter.fields(execution, false)[RandomNumberFields.STATUS]?.toString().orEmpty().ifBlank { "Generated." }
            if (context.startsImmediately) onConfirmed(execution)
        }

        LaunchedEffect(context.presentationMode, context.action.settings) {
            if (context.presentationMode == CapabilityPresentationMode.IntentLaunch && !launched) {
                launched = true
                generate()
            }
        }

        CapabilityScreenScaffold(
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = result,
            resultPreview = result?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty(),
            onBack = onBack,
            onRetry = { generate() },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Text("Generate random values from the current capability settings.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Configured: count $count, range $min to $max, step $step, seed mode $seedMode${if (seedMode == "fixed_seed" && seed.isNotBlank()) ", seed supplied" else ""}.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(10.dp))
            Button(onClick = { generate() }, modifier = Modifier.fillMaxWidth()) { Text(if (result == null) "Generate" else "Generate again") }
            Text(status, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
