package com.example.researchos.modules.calibratedscale

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import com.example.researchos.core.researchos.ExecutionResult
import com.example.researchos.core.researchos.withInvocationContext
import com.example.researchos.settings.MethodSetting
import com.example.researchos.settings.SettingsState
import com.example.researchos.transport.OutputFormatter
import com.example.researchos.transport.workflow.ui.CapabilityScreenContext
import com.example.researchos.transport.workflow.ui.CapabilityScreenScaffold
import com.example.researchos.transport.workflow.ui.CapabilityScreenSpec
import com.example.researchos.transport.workflow.ui.IntentExample
import com.example.researchos.transport.workflow.ui.IntentExampleDropdown

object CalibratedScaleCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId: String = As100CalibratedScaleMethod.ID
    override val title: String = "Calibrated scale measurement"
    override val description: String = "Measure a scalar or range value using a calibrated visual analogue scale."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val action = context.action
        val request = context.request
        val interaction = remember { CalibratedScaleInteraction() }
        val settings = remember(action.settings) {
            SettingsState(interaction.settings).also { state ->
                applyParameters(state, interaction.settings, action.settings)
            }
        }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var status by remember { mutableStateOf("Ready to measure.") }

        fun captureValue() {
            val execution = As100CalibratedScaleMethod.execute(
                request = As100CalibratedScaleMethod.request(
                    action = action.canonicalId,
                    context = request.invocationContext.asMap(action.canonicalId) + mapOf(
                        "value" to settings.getFloat("value").toString(),
                        "minimum" to settings.getFloat("minimum").toString(),
                        "maximum" to settings.getFloat("maximum").toString(),
                        "use_range" to settings.getBoolean("use_range").toString(),
                        "lower_value" to settings.getFloat("lower_value").toString(),
                        "upper_value" to settings.getFloat("upper_value").toString()
                    )
                ),
                settingsState = settings,
                transport = request.source
            ).withInvocationContext(request.invocationContext)
            result = execution
            status = "Measurement captured."
        }

        LaunchedEffect(context.isExternalInvocation) {
            if (context.isExternalInvocation) captureValue()
        }

        CapabilityScreenScaffold(
            title = title,
            capabilityId = action.canonicalId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = result,
            resultPreview = result?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty(),
            onBack = onBack,
            onRetry = { result = null; status = "Ready to measure." },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Text(status)
            Spacer(Modifier.height(10.dp))

            // Settings section
            Text("Configuration", modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = settings.getString("prompt"),
                onValueChange = { settings.setString("prompt", it) },
                label = { Text("Prompt") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Minimum value", modifier = Modifier.weight(1f))
                OutlinedTextField(
                    value = settings.getFloat("minimum").toString(),
                    onValueChange = { it.toFloatOrNull()?.let { f -> settings.setFloat("minimum", f) } },
                    modifier = Modifier.weight(0.5f),
                    singleLine = true
                )
            }
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Maximum value", modifier = Modifier.weight(1f))
                OutlinedTextField(
                    value = settings.getFloat("maximum").toString(),
                    onValueChange = { it.toFloatOrNull()?.let { f -> settings.setFloat("maximum", f) } },
                    modifier = Modifier.weight(0.5f),
                    singleLine = true
                )
            }
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Use range (two scales)", modifier = Modifier.weight(1f))
                Checkbox(
                    checked = settings.getBoolean("use_range"),
                    onCheckedChange = { settings.setBoolean("use_range", it) }
                )
            }
            Spacer(Modifier.height(8.dp))

            if (settings.getBoolean("use_range")) {
                OutlinedTextField(
                    value = settings.getString("lower_label"),
                    onValueChange = { settings.setString("lower_label", it) },
                    label = { Text("Lower scale label") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = settings.getString("upper_label"),
                    onValueChange = { settings.setString("upper_label", it) },
                    label = { Text("Upper scale label") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Vertical mode", modifier = Modifier.weight(1f))
                Checkbox(
                    checked = settings.getBoolean("vertical_mode"),
                    onCheckedChange = { settings.setBoolean("vertical_mode", it) }
                )
            }
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Show endpoint labels", modifier = Modifier.weight(1f))
                Checkbox(
                    checked = settings.getBoolean("show_endpoint_labels"),
                    onCheckedChange = { settings.setBoolean("show_endpoint_labels", it) }
                )
            }
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Show current value", modifier = Modifier.weight(1f))
                Checkbox(
                    checked = settings.getBoolean("show_current_score"),
                    onCheckedChange = { settings.setBoolean("show_current_score", it) }
                )
            }
            Spacer(Modifier.height(12.dp))

            Text("Scale Preview", modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            interaction.Render(settings)
            Spacer(Modifier.height(10.dp))
            Button(onClick = { captureValue() }) {
                Text(if (result == null) "Capture measurement" else "Measure again")
            }

            Spacer(Modifier.height(16.dp))
            IntentExampleDropdown(
                capabilityId = As100CalibratedScaleMethod.ID,
                examples = listOf(
                    IntentExample(
                        label = "Basic scale measurement",
                        description = "Capture a simple 0-100 scale value",
                        intentUri = "com.example.researchos.EXECUTE_METHOD(method_id='calibrated_scale')"
                    ),
                    IntentExample(
                        label = "Custom range (0-10)",
                        description = "Measure on a custom range",
                        intentUri = "com.example.researchos.EXECUTE_METHOD(method_id='calibrated_scale',minimum='0',maximum='10')"
                    ),
                    IntentExample(
                        label = "Dual range measurement",
                        description = "Capture both lower and upper values",
                        intentUri = "com.example.researchos.EXECUTE_METHOD(method_id='calibrated_scale',use_range='true',lower_label='Minimum pain',upper_label='Maximum pain')"
                    ),
                    IntentExample(
                        label = "Vertical mode",
                        description = "Display scale vertically",
                        intentUri = "com.example.researchos.EXECUTE_METHOD(method_id='calibrated_scale',vertical_mode='true')"
                    )
                )
            )
        }
    }

    private fun applyParameters(settingsState: SettingsState, settings: List<MethodSetting>, parameters: Map<String, String>) {
        settings.forEach { setting ->
            val raw = parameters[setting.id] ?: return@forEach
            when (setting) {
                is MethodSetting.BooleanSetting -> settingsState.setBoolean(setting.id, raw.toBooleanStrictOrNull() ?: (raw == "1"))
                is MethodSetting.IntSetting -> raw.toIntOrNull()?.let { settingsState.setInt(setting.id, it) }
                is MethodSetting.FloatSetting -> raw.toFloatOrNull()?.let { settingsState.setFloat(setting.id, it) }
                is MethodSetting.TextSetting -> settingsState.setString(setting.id, raw)
                is MethodSetting.ChoiceSetting -> settingsState.setString(setting.id, raw)
            }
        }
    }
}
