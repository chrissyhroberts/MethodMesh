package com.example.methodmesh.modules.randomnumber

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import com.example.methodmesh.transport.workflow.ui.CapabilityPresentationMode

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
        var count by rememberSaveable { mutableStateOf(context.action.settings["count"] ?: context.action.settings["input_count"] ?: "1") }
        var min by rememberSaveable { mutableStateOf(context.action.settings["min"] ?: context.action.settings["input_min"] ?: "0") }
        var max by rememberSaveable { mutableStateOf(context.action.settings["max"] ?: context.action.settings["input_max"] ?: "1") }
        var step by rememberSaveable { mutableStateOf(context.action.settings["step"] ?: context.action.settings["input_step"] ?: "1") }
        var seedMode by rememberSaveable { mutableStateOf(context.action.settings["seed_mode"] ?: context.action.settings["input_seed_mode"] ?: "secure_random") }
        var seed by rememberSaveable { mutableStateOf(context.action.settings["seed"] ?: context.action.settings["input_seed"] ?: "") }
        var launched by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var status by remember { mutableStateOf("Ready to generate.") }

        LaunchedEffect(count, min, max, step, seedMode, seed) {
            context.onSettingsChanged(
                mapOf(
                    "count" to count,
                    "min" to min,
                    "max" to max,
                    "step" to step,
                    "seed_mode" to seedMode,
                    "seed" to seed
                )
            )
        }

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
            OutlinedTextField(count, { count = it.filter(Char::isDigit).ifBlank { "" } }, label = { Text("How many numbers") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(min, { min = it.numericText() }, label = { Text("Minimum") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(max, { max = it.numericText() }, label = { Text("Maximum") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(step, { step = it.numericText() }, label = { Text("Step") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            SeedModeChooser(seedMode = seedMode, onSeedModeSelected = { seedMode = it })
            if (seedMode == "fixed_seed") {
                OutlinedTextField(seed, { seed = it }, label = { Text("Fixed seed") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
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

@Composable
private fun SeedModeChooser(seedMode: String, onSeedModeSelected: (String) -> Unit) {
    Text("Seed mode", style = MaterialTheme.typography.bodySmall)
    Row(Modifier.fillMaxWidth()) {
        if (seedMode == "secure_random") {
            Button(onClick = { onSeedModeSelected("secure_random") }, modifier = Modifier.weight(1f).padding(2.dp)) { Text("✓ Secure random") }
        } else {
            OutlinedButton(onClick = { onSeedModeSelected("secure_random") }, modifier = Modifier.weight(1f).padding(2.dp)) { Text("Secure random") }
        }
        if (seedMode == "fixed_seed") {
            Button(onClick = { onSeedModeSelected("fixed_seed") }, modifier = Modifier.weight(1f).padding(2.dp)) { Text("✓ Fixed seed") }
        } else {
            OutlinedButton(onClick = { onSeedModeSelected("fixed_seed") }, modifier = Modifier.weight(1f).padding(2.dp)) { Text("Fixed seed") }
        }
    }
}

private fun String.numericText(): String =
    filter { it.isDigit() || it == '-' || it == '.' }.let { filtered ->
        val minus = if (filtered.startsWith("-")) "-" else ""
        val body = filtered.removePrefix("-")
        minus + body.split('.').let { parts ->
            parts.first() + if (parts.size > 1) "." + parts.drop(1).joinToString("") else ""
        }
    }
