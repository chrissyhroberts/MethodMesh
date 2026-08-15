package com.example.researchos.modules.randomnumber

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import com.example.researchos.core.researchos.ExecutionResult
import com.example.researchos.transport.OutputFormatter
import com.example.researchos.transport.workflow.ui.CapabilityScreenContext
import com.example.researchos.transport.workflow.ui.CapabilityScreenScaffold
import com.example.researchos.transport.workflow.ui.CapabilityScreenSpec
import com.example.researchos.transport.workflow.ui.IntentExample
import com.example.researchos.transport.workflow.ui.IntentExampleDropdown

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
        var status by remember { mutableStateOf("Configure and generate.") }

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

        LaunchedEffect(context.startsImmediately) {
            if (context.startsImmediately && !launched) {
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
            OutlinedTextField(value = count, onValueChange = { count = it.filter(Char::isDigit).take(5) }, label = { Text("Count") }, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth()) {
                OutlinedTextField(value = min, onValueChange = { min = it.numericText() }, label = { Text("Min") }, modifier = Modifier.weight(1f))
                Spacer(Modifier.padding(4.dp))
                OutlinedTextField(value = max, onValueChange = { max = it.numericText() }, label = { Text("Max") }, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(value = step, onValueChange = { step = it.numericText(allowNegative = false) }, label = { Text("Step") }, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = { seedMode = if (seedMode == "secure_random") "fixed_seed" else "secure_random" }, modifier = Modifier.fillMaxWidth()) {
                Text("Seed mode: $seedMode")
            }
            if (seedMode == "fixed_seed") {
                OutlinedTextField(value = seed, onValueChange = { seed = it }, label = { Text("Fixed seed") }, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = { generate() }, modifier = Modifier.fillMaxWidth()) { Text("Generate") }
            Text(status)
            IntentExampleDropdown(
                capabilityId = capabilityId,
                examples = listOf(
                    IntentExample("One secure random integer", "Generate a number from 1 to 100.", "com.example.researchos.EXECUTE_METHOD(method_id='random.number.generate',input_min='1',input_max='100',input_step='1',return_mode='flat')"),
                    IntentExample("Fixed-seed allocation", "Generate reproducible numbers from a fixed seed.", "com.example.researchos.EXECUTE_METHOD(method_id='random.number.generate',input_count='5',input_min='1',input_max='10',input_step='1',input_seed_mode='fixed_seed',input_seed='study001',return_mode='flat')")
                )
            )
        }
    }
}

private fun String.numericText(allowNegative: Boolean = true): String =
    filterIndexed { index, char -> char.isDigit() || char == '.' || (allowNegative && char == '-' && index == 0) }
