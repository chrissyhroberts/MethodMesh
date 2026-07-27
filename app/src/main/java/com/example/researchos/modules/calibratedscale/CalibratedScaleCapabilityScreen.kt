package com.example.researchos.modules.calibratedscale

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.researchos.calibration.CalibrationRepository
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
        val calibration by CalibrationRepository.calibration
        val settings = remember(action.settings) {
            SettingsState(interaction.settings).also { state ->
                applyParameters(state, interaction.settings, action.settings)
            }
        }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var status by remember { mutableStateOf("Ready to measure.") }
        var touchedValues by remember { mutableStateOf(emptySet<String>()) }

        fun captureValue(): ExecutionResult {
            val execution = As100CalibratedScaleMethod.execute(
                request = As100CalibratedScaleMethod.request(
                    action = action.canonicalId,
                    context = request.invocationContext.asMap(action.canonicalId) + mapOf(
                        "value" to settings.getFloat("value").toString(),
                        "minimum" to settings.getFloat("minimum").toString(),
                        "maximum" to settings.getFloat("maximum").toString(),
                        "use_range" to settings.getBoolean("use_range").toString(),
                        "lower_value" to settings.getFloat("lower_value").toString(),
                        "upper_value" to settings.getFloat("upper_value").toString(),
                        "vas_length_mm" to settings.getFloat("vas_length_mm").toString(),
                        "vertical_mode" to settings.getBoolean("vertical_mode").toString()
                    )
                ),
                settingsState = settings,
                transport = request.source
            ).withInvocationContext(request.invocationContext)
            result = execution
            status = "Measurement captured."
            return execution
        }

        if (context.startsImmediately) {
            FocusedCalibratedScaleCapture(
                interaction = interaction,
                settings = settings,
                touchedValues = touchedValues,
                onValueChanged = { valueId ->
                    touchedValues = touchedValues + valueId
                },
                onUseMeasurement = {
                    onConfirmed(captureValue())
                },
                onCancel = onCancel
            )
            return
        }

        CapabilityScreenScaffold(
            title = title,
            capabilityId = action.canonicalId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = result,
            resultPreview = result?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty(),
            onBack = onBack,
            onRetry = {
                result = null
                touchedValues = emptySet()
                status = "Ready to measure."
            },
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

            OutlinedTextField(
                value = settings.getString("hint"),
                onValueChange = { settings.setString("hint", it) },
                label = { Text("Participant hint") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = settings.getFloat("vas_length_mm").toString(),
                onValueChange = { value ->
                    value.toFloatOrNull()
                        ?.takeIf { it in 40f..200f }
                        ?.let { settings.setFloat("vas_length_mm", it) }
                },
                label = { Text("Scale line length (mm)") },
                supportingText = {
                    val lengthMm = settings.getFloat("vas_length_mm")
                    val dpPerMm = calibration.dpPerMm
                    Text(
                        "%.1f cm × %.2f dp/mm = %.1f dp".format(
                            lengthMm / 10f,
                            dpPerMm,
                            scaleLengthDp(lengthMm, dpPerMm)
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
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
            interaction.Render(settings) { valueId ->
                touchedValues = touchedValues + valueId
                result = null
                status = "Selection changed. Capture when ready."
            }
            Spacer(Modifier.height(10.dp))
            val requiredSelections = requiredScaleSelections(settings.getBoolean("use_range"))
            Button(
                onClick = { captureValue() },
                enabled = touchedValues.containsAll(requiredSelections)
            ) {
                Text(if (result == null) "Capture measurement" else "Measure again")
            }
            if (!touchedValues.containsAll(requiredSelections)) {
                Text(
                    if (requiredSelections.size == 1) {
                        "Move the scale marker before capturing."
                    } else {
                        "Set both range markers before capturing."
                    },
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall
                )
            }

            Spacer(Modifier.height(16.dp))
            IntentExampleDropdown(
                capabilityId = As100CalibratedScaleMethod.ID,
                examples = listOf(
                    IntentExample(
                        label = "Basic scale measurement",
                        description = "Capture a simple 0-100 scale value",
                        intentUri = "com.example.researchos.EXECUTE_METHOD(method_id='calibrated_scale',input_prompt='Rate your pain',input_hint='0 means no pain; 100 means the worst pain you can imagine')"
                    ),
                    IntentExample(
                        label = "Custom range (0-10)",
                        description = "Measure on a custom range",
                        intentUri = "com.example.researchos.EXECUTE_METHOD(method_id='calibrated_scale',input_minimum='0',input_maximum='10')"
                    ),
                    IntentExample(
                        label = "Dual range measurement",
                        description = "Capture both lower and upper values",
                        intentUri = "com.example.researchos.EXECUTE_METHOD(method_id='calibrated_scale',input_use_range='true',input_lower_label='Minimum pain',input_upper_label='Maximum pain')"
                    ),
                    IntentExample(
                        label = "Calibrated 5 cm horizontal scale",
                        description = "Display a physically calibrated 50 mm line",
                        intentUri = "com.example.researchos.EXECUTE_METHOD(method_id='calibrated_scale',input_vas_length_mm='50',input_vertical_mode='false')"
                    ),
                    IntentExample(
                        label = "Calibrated 5 cm vertical scale",
                        description = "Display the same physical length vertically",
                        intentUri = "com.example.researchos.EXECUTE_METHOD(method_id='calibrated_scale',input_vas_length_mm='50',input_vertical_mode='true')"
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

@Composable
private fun FocusedCalibratedScaleCapture(
    interaction: CalibratedScaleInteraction,
    settings: SettingsState,
    touchedValues: Set<String>,
    onValueChanged: (String) -> Unit,
    onUseMeasurement: () -> Unit,
    onCancel: () -> Unit
) {
    val requiredSelections = requiredScaleSelections(settings.getBoolean("use_range"))
    val ready = touchedValues.containsAll(requiredSelections)

    Column(Modifier.fillMaxWidth()) {
        interaction.Render(settings, onValueChanged)
        Spacer(Modifier.height(14.dp))
        Text(
            text = if (ready) {
                "Selection ready."
            } else if (requiredSelections.size == 1) {
                "Move the scale marker to record an answer."
            } else {
                "Move both scale markers to record an answer."
            },
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            OutlinedButton(onClick = onCancel) {
                Text("Cancel")
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onUseMeasurement,
                enabled = ready
            ) {
                Text("Use this measurement")
            }
        }
    }
}

internal fun requiredScaleSelections(useRange: Boolean): Set<String> =
    if (useRange) setOf("lower_value", "upper_value") else setOf("value")
