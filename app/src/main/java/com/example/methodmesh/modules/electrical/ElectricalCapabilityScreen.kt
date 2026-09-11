package com.example.methodmesh.modules.electrical

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.ResultShare
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec

object ElectricalWorkbenchCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100ElectricalWorkbenchMethod.ID
    override val title = "Electrical"
    override val description = "Fast electrical calculations for field and bench work. Tap the result to copy it."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) = ElectricalWorkbenchUi(context, onBack, onConfirmed, onCancel)
}

private data class ToolDef(val id: String, val label: String, val hint: String)
private val tools = listOf(
    ToolDef("ohms_law", "Ohm / power", "Enter any two values"),
    ToolDef("ac_power", "AC / three-phase", "Real, apparent and reactive power"),
    ToolDef("voltage_drop", "Voltage drop", "Copper/aluminium conductor estimate"),
    ToolDef("network", "Series / parallel", "Resistance or capacitance"),
    ToolDef("energy", "Power / energy", "W, Wh, Ah and runtime"),
    ToolDef("rc", "RC time constant", "τ and cutoff frequency"),
    ToolDef("rl", "RL time constant", "τ = L/R"),
    ToolDef("resistor_code", "Resistor bands", "4- or 5-band colour code")
)

@Composable
private fun ElectricalWorkbenchUi(
    context: CapabilityScreenContext,
    onBack: () -> Unit,
    onConfirmed: (ExecutionResult) -> Unit,
    onCancel: () -> Unit
) {
    fun initial(key: String, fallback: String = "") = context.action.settings[key]
        ?: context.action.settings["input_$key"] ?: fallback

    var tool by rememberSaveable { mutableStateOf(initial("tool", "ohms_law")) }
    var voltage by rememberSaveable { mutableStateOf(initial("voltage_v")) }
    var current by rememberSaveable { mutableStateOf(initial("current_a")) }
    var resistance by rememberSaveable { mutableStateOf(initial("resistance_ohm")) }
    var power by rememberSaveable { mutableStateOf(initial("power_w")) }
    var phase by rememberSaveable { mutableStateOf(initial("phase", "single_phase")) }
    var powerFactor by rememberSaveable { mutableStateOf(initial("power_factor", "1")) }
    var length by rememberSaveable { mutableStateOf(initial("length_m")) }
    var area by rememberSaveable { mutableStateOf(initial("conductor_area_mm2")) }
    var material by rememberSaveable { mutableStateOf(initial("material", "copper")) }
    var temperature by rememberSaveable { mutableStateOf(initial("temperature_c", "20")) }
    var arrangement by rememberSaveable { mutableStateOf(initial("arrangement", "series")) }
    var networkType by rememberSaveable { mutableStateOf(initial("network_type", "resistance")) }
    var values by rememberSaveable { mutableStateOf(initial("values")) }
    var duration by rememberSaveable { mutableStateOf(initial("duration_hours")) }
    var capacityAh by rememberSaveable { mutableStateOf(initial("capacity_ah")) }
    var capacitance by rememberSaveable { mutableStateOf(initial("capacitance_f")) }
    var inductance by rememberSaveable { mutableStateOf(initial("inductance_h")) }
    var bands by rememberSaveable { mutableStateOf(initial("bands", "brown,black,red,gold")) }
    var result by remember { mutableStateOf<ExecutionResult?>(null) }
    var resultText by rememberSaveable { mutableStateOf("") }
    var errorText by rememberSaveable { mutableStateOf("") }

    val androidContext = LocalContext.current
    val clipboard = LocalClipboardManager.current

    fun settings() = linkedMapOf(
        "tool" to tool, "voltage_v" to voltage, "current_a" to current,
        "resistance_ohm" to resistance, "power_w" to power, "phase" to phase,
        "power_factor" to powerFactor, "length_m" to length,
        "conductor_area_mm2" to area, "material" to material, "temperature_c" to temperature,
        "arrangement" to arrangement, "network_type" to networkType, "values" to values,
        "duration_hours" to duration, "capacity_ah" to capacityAh, "capacitance_f" to capacitance,
        "inductance_h" to inductance, "bands" to bands
    )

    fun calculate() {
        val request = As100ElectricalWorkbenchMethod.request(
            action = As100ElectricalWorkbenchMethod.ID,
            context = settings(),
            signals = emptyList(),
            inputs = emptyList()
        )
        val execution = As100ElectricalWorkbenchMethod.execute(request, null, "native")
        result = execution
        val valuesMap = execution.observations.lastOrNull()?.values.orEmpty()
        resultText = valuesMap[ElectricalFields.RESULT].orEmpty()
        errorText = valuesMap[ElectricalFields.ERROR].orEmpty()
    }

    CapabilityScreenScaffold(
        title = "Electrical",
        capabilityId = As100ElectricalWorkbenchMethod.ID,
        context = context,
        canGoBack = context.stepNumber > 1,
        capturedResult = result,
        resultPreview = result?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty(),
        onBack = onBack,
        onRetry = { calculate() },
        onConfirm = { result?.let(onConfirmed) },
        onCancel = onCancel
    ) {
        ToolPicker(tool = tool, onTool = { tool = it; result = null; resultText = ""; errorText = "" })
        Spacer(Modifier.height(12.dp))
        when (tool) {
            "ohms_law" -> {
                NumberField("Voltage (V)", voltage) { voltage = it }
                NumberField("Current (A)", current) { current = it }
                NumberField("Resistance (Ω)", resistance) { resistance = it }
                NumberField("Power (W)", power) { power = it }
                Text("Leave unknown values blank. Any two are enough.", style = MaterialTheme.typography.bodySmall)
            }
            "ac_power" -> {
                ChoiceField("System", phase, listOf("single_phase", "three_phase")) { phase = it }
                NumberField("Voltage (V)", voltage) { voltage = it }
                NumberField("Current (A)", current) { current = it }
                NumberField("Power factor", powerFactor) { powerFactor = it }
            }
            "voltage_drop" -> {
                ChoiceField("System", phase, listOf("single_phase", "three_phase")) { phase = it }
                ChoiceField("Conductor", material, listOf("copper", "aluminium")) { material = it }
                NumberField("Supply voltage (V)", voltage) { voltage = it }
                NumberField("Load current (A)", current) { current = it }
                NumberField("One-way route length (m)", length) { length = it }
                NumberField("Conductor area (mm²)", area) { area = it }
                NumberField("Conductor temperature (°C)", temperature) { temperature = it }
                NumberField("Power factor", powerFactor) { powerFactor = it }
                SafetyCard("Resistance-only engineering estimate, not a cable-selection or BS 7671 compliance decision. Conductor reactance, installation method, grouping, ambient conditions and protective-device requirements are not inferred.")
            }
            "network" -> {
                ChoiceField("Component", networkType, listOf("resistance", "capacitance")) { networkType = it }
                ChoiceField("Arrangement", arrangement, listOf("series", "parallel")) { arrangement = it }
                TextField("Values", values, "e.g. 100, 220, 470") { values = it }
                Text("Enter base SI units: Ω for resistance, F for capacitance.", style = MaterialTheme.typography.bodySmall)
            }
            "energy" -> {
                NumberField("Voltage (V)", voltage) { voltage = it }
                NumberField("Current (A)", current) { current = it }
                NumberField("Power (W)", power) { power = it }
                NumberField("Duration (hours)", duration) { duration = it }
                NumberField("Battery capacity (Ah)", capacityAh) { capacityAh = it }
            }
            "rc" -> {
                NumberField("Resistance (Ω)", resistance) { resistance = it }
                NumberField("Capacitance (F)", capacitance) { capacitance = it }
            }
            "rl" -> {
                NumberField("Resistance (Ω)", resistance) { resistance = it }
                NumberField("Inductance (H)", inductance) { inductance = it }
            }
            "resistor_code" -> {
                TextField("Bands", bands, "brown, black, red, gold") { bands = it }
                Text("Colours in reading order; four or five bands.", style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(12.dp))
        Button(onClick = { calculate() }, modifier = Modifier.fillMaxWidth()) { Text("Calculate") }

        if (resultText.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth().clickable { clipboard.setText(AnnotatedString(resultText)) }
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Result", style = MaterialTheme.typography.labelMedium)
                    Text(resultText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text("Tap result to copy", style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { clipboard.setText(AnnotatedString(resultText)) }, modifier = Modifier.weight(1f)
                ) { Text("Copy") }
                OutlinedButton(
                    onClick = {
                        ResultShare.share(
                            context = androidContext,
                            chooserTitle = "Share electrical result",
                            text = resultText,
                            attachments = emptyList()
                        )
                    }, modifier = Modifier.weight(1f)
                ) { Text("Share") }
            }
        }
        if (errorText.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(errorText, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ToolPicker(tool: String, onTool: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = tools.firstOrNull { it.id == tool } ?: tools.first()
    Column {
        Text("Tool", style = MaterialTheme.typography.labelMedium)
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                Text(selected.label, fontWeight = FontWeight.SemiBold)
                Text(selected.hint, style = MaterialTheme.typography.bodySmall)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            tools.forEach { item ->
                DropdownMenuItem(text = { Column { Text(item.label); Text(item.hint, style = MaterialTheme.typography.bodySmall) } }, onClick = {
                    expanded = false; onTool(item.id)
                })
            }
        }
    }
}

@Composable private fun NumberField(label: String, value: String, onValue: (String) -> Unit) =
    OutlinedTextField(value = value, onValueChange = onValue, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp))

@Composable private fun TextField(label: String, value: String, placeholder: String, onValue: (String) -> Unit) =
    OutlinedTextField(value = value, onValueChange = onValue, label = { Text(label) }, placeholder = { Text(placeholder) }, modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp))

@Composable private fun ChoiceField(label: String, value: String, choices: List<String>, onValue: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(value.replace('_', ' ')) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { choice -> DropdownMenuItem(text = { Text(choice.replace('_', ' ')) }, onClick = { expanded = false; onValue(choice) }) }
        }
    }
}

@Composable private fun SafetyCard(text: String) {
    Card(Modifier.fillMaxWidth().padding(top = 4.dp)) { Text(text, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall) }
}
