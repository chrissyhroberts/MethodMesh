package com.example.methodmesh.modules.labbench

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityPresentationMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import org.json.JSONObject

class LabBenchAtomicCapabilityScreen(private val definition: LabCalculatorDefinition) : CapabilityScreenSpec {
    override val capabilityId = definition.method.id
    override val title = definition.title
    override val description = definition.description

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        LabBenchCalculatorScreen(definition, context, onBack, onConfirmed, onCancel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LabBenchCalculatorScreen(
    definition: LabCalculatorDefinition,
    context: CapabilityScreenContext,
    onBack: () -> Unit,
    onConfirmed: (ExecutionResult) -> Unit,
    onCancel: () -> Unit
) {
    var stateJson by rememberSaveable(context.action.canonicalId) {
        mutableStateOf(initialStateJson(definition.inputs, context.action.settings))
    }
    var resultValuesJson by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
    var liveResult by remember(context.action.canonicalId) { mutableStateOf<ExecutionResult?>(null) }
    var launched by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
    val values = remember(stateJson) { jsonToMap(stateJson) }
    val reconstructedResult = remember(resultValuesJson) {
        if (resultValuesJson.isBlank()) null else executionFor(definition.method, context, jsonToMap(resultValuesJson), values)
    }
    val result = liveResult ?: reconstructedResult

    LaunchedEffect(stateJson) { context.onSettingsChanged(values) }

    fun calculate(autoSubmit: Boolean = false) {
        val calculated = definition.method.calculate(values)
        resultValuesJson = mapToJson(calculated)
        val execution = executionFor(definition.method, context, calculated, values)
        liveResult = execution
        if (autoSubmit) onConfirmed(execution)
    }

    val genuineExternalAutoSubmit = context.submitsImmediately && !context.request.source.equals("intent_test", ignoreCase = true)
    LaunchedEffect(context.presentationMode, context.action.settings) {
        if (context.presentationMode == CapabilityPresentationMode.IntentLaunch && genuineExternalAutoSubmit && !launched) {
            launched = true
            calculate(autoSubmit = true)
        }
    }

    CapabilityScreenScaffold(
        title = titleFor(definition),
        capabilityId = definition.method.id,
        context = context,
        canGoBack = context.stepNumber > 1,
        capturedResult = result,
        resultPreview = result?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty(),
        onBack = onBack,
        onRetry = {
            resultValuesJson = ""
            liveResult = null
        },
        onConfirm = { result?.let(onConfirmed) },
        onCancel = onCancel
    ) {
        Text(definition.description, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        CalculatorInputs(definition, values, context) { id, value ->
            stateJson = updateJsonValue(stateJson, id, value)
            resultValuesJson = ""
            liveResult = null
        }
        Spacer(Modifier.height(10.dp))
        Button(onClick = { calculate() }, modifier = Modifier.fillMaxWidth()) { Text("Calculate") }
    }
}

object LabBenchDashboardCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = LabBenchMethods.Dashboard.id
    override val title = "Lab Bench"
    override val description = "Persistent offline laboratory calculator dashboard."

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        var selectedKey by rememberSaveable(context.action.canonicalId) {
            mutableStateOf(context.action.settings["calculator"] ?: context.action.settings["input_calculator"] ?: "dilution")
        }
        if (selectedKey !in LabBenchDefinitions.byKey) selectedKey = "dilution"
        var dashboardStateJson by rememberSaveable(context.action.canonicalId) { mutableStateOf(initialDashboardStateJson(context.action.settings)) }
        var resultValuesJson by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
        var toolResultValuesJson by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
        var liveResult by remember(context.action.canonicalId) { mutableStateOf<ExecutionResult?>(null) }
        var launched by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        val definition = LabBenchDefinitions.byKey.getValue(selectedKey)
        val values = remember(dashboardStateJson, selectedKey) { dashboardToolState(dashboardStateJson, definition) }
        val toolResultValues = remember(toolResultValuesJson) { jsonToMap(toolResultValuesJson) }
        val reconstructedResult = remember(resultValuesJson) {
            if (resultValuesJson.isBlank()) null else executionFor(LabBenchMethods.Dashboard, context, jsonToMap(resultValuesJson), mapOf("calculator" to selectedKey) + values)
        }
        val result = liveResult ?: reconstructedResult
        val keepLiveDashboard = context.presentationMode == CapabilityPresentationMode.Dashboard || context.isNativePresetRun || context.request.source.equals("intent_test", ignoreCase = true)
        val scaffoldResult = if (keepLiveDashboard) null else result

        fun calculate(autoSubmit: Boolean = false) {
            val settings = mapOf("calculator" to selectedKey) + values
            val toolValues = definition.method.calculate(values)
            toolResultValuesJson = mapToJson(toolValues)
            val calculated = LabBenchMethods.Dashboard.calculate(settings)
            resultValuesJson = mapToJson(calculated)
            val execution = executionFor(LabBenchMethods.Dashboard, context, calculated, settings)
            liveResult = execution
            if (autoSubmit) onConfirmed(execution)
        }

        LaunchedEffect(selectedKey, dashboardStateJson) {
            context.onSettingsChanged(mapOf("calculator" to selectedKey) + values)
            if (keepLiveDashboard) calculate()
        }

        val genuineExternalAutoSubmit = context.submitsImmediately && !context.request.source.equals("intent_test", ignoreCase = true)
        LaunchedEffect(context.presentationMode, context.action.settings) {
            if (context.presentationMode == CapabilityPresentationMode.IntentLaunch && genuineExternalAutoSubmit && !launched) {
                launched = true
                calculate(autoSubmit = true)
            }
        }

        CapabilityScreenScaffold(
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = scaffoldResult,
            resultPreview = scaffoldResult?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty(),
            onBack = onBack,
            onRetry = {
                resultValuesJson = ""
                toolResultValuesJson = ""
                liveResult = null
            },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            LabBenchDashboardHeader()
            Spacer(Modifier.height(12.dp))

            if (context.settingShouldBeShown("calculator")) {
                LabBenchToolShelf(selectedKey = selectedKey, onSelected = { next ->
                    selectedKey = next
                    resultValuesJson = ""
                    toolResultValuesJson = ""
                    liveResult = null
                })
                Spacer(Modifier.height(14.dp))
            }

            ActiveInstrumentCard(
                definition = definition,
                values = values,
                context = context,
                onValueChange = { id, value ->
                    dashboardStateJson = updateDashboardValue(dashboardStateJson, selectedKey, id, value)
                    resultValuesJson = ""
                    toolResultValuesJson = ""
                    liveResult = null
                },
                onReset = {
                    dashboardStateJson = resetDashboardToolState(dashboardStateJson, definition)
                    resultValuesJson = ""
                    toolResultValuesJson = ""
                    liveResult = null
                }
            )

            val status = toolResultValues.entries.firstOrNull { it.key.endsWith("_status") }?.value.orEmpty()
            val instruction = toolResultValues.entries.firstOrNull { it.key.endsWith("_instruction") }?.value.orEmpty()
            val error = toolResultValues.entries.firstOrNull { it.key.endsWith("_error") }?.value.orEmpty()
            if (status == "succeeded" && instruction.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                ResultDock(definition.key, toolResultValues, instruction)
            } else if (error.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Check the inputs", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Text(error, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            if (!keepLiveDashboard) {
                Spacer(Modifier.height(12.dp))
                Button(onClick = { calculate() }, modifier = Modifier.fillMaxWidth()) { Text("Calculate") }
            }

            if (keepLiveDashboard && result != null && toolResultValues.entries.any { it.key.endsWith("_status") && it.value == "succeeded" }) {
                Spacer(Modifier.height(12.dp))
                Button(onClick = { result?.let(onConfirmed) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (context.isNativePresetRun) "Finish" else "Use this calculation")
                }
            }
        }
    }
}

@Composable
private fun LabBenchDashboardHeader() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("LAB BENCH", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text("Offline laboratory calculations", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Fast bench calculations with explicit units, conservative assumptions and auditable outputs.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private data class LabBenchToolGroup(val id: String, val label: String, val keys: List<String>)

private val labBenchToolGroups = listOf(
    LabBenchToolGroup("prepare", "Prepare", listOf("dilution", "molar_solution", "reconstitute", "serial_dilution", "master_mix", "percent_solution")),
    LabBenchToolGroup("measure", "Measure & convert", listOf("centrifuge", "concentration", "nucleic_acid")),
    LabBenchToolGroup("samples", "Cells & samples", listOf("cell_dilution", "hemocytometer", "aliquot"))
)

@Composable
private fun LabBenchToolShelf(selectedKey: String, onSelected: (String) -> Unit) {
    var selectedGroup by rememberSaveable(selectedKey) {
        mutableStateOf(labBenchToolGroups.firstOrNull { selectedKey in it.keys }?.id ?: "prepare")
    }
    val activeGroup = labBenchToolGroups.firstOrNull { it.id == selectedGroup } ?: labBenchToolGroups.first()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            labBenchToolGroups.forEach { group ->
                FilterChip(
                    selected = selectedGroup == group.id,
                    onClick = { selectedGroup = group.id },
                    label = { Text(group.label) }
                )
            }
        }
        activeGroup.keys.mapNotNull(LabBenchDefinitions.byKey::get).chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { definition ->
                    ToolShelfCard(
                        definition = definition,
                        selected = selectedKey == definition.key,
                        onClick = {
                            selectedGroup = activeGroup.id
                            onSelected(definition.key)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ToolShelfCard(definition: LabCalculatorDefinition, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 2.dp else 0.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(definition.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(toolCue(definition.key), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ActiveInstrumentCard(
    definition: LabCalculatorDefinition,
    values: Map<String, String>,
    context: CapabilityScreenContext,
    onValueChange: (String, String) -> Unit,
    onReset: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(definition.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(toolCue(definition.key), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                OutlinedButton(onClick = onReset) { Text("Reset") }
            }
            Spacer(Modifier.height(4.dp))
            Text(definition.description, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            ProCalculatorInputs(definition, values, context, onValueChange)
        }
    }
}

@Composable
private fun ProCalculatorInputs(
    definition: LabCalculatorDefinition,
    values: Map<String, String>,
    context: CapabilityScreenContext,
    onValueChange: (String, String) -> Unit
) {
    val visible = definition.inputs.filter { context.settingShouldBeShown(it.id) && inputVisible(definition, it, values) }
    var index = 0
    while (index < visible.size) {
        val input = visible[index]
        val next = visible.getOrNull(index + 1)
        val pairable = next != null && next.kind == LabInputKind.CHOICE && next.id == "${input.id}_unit" && input.kind != LabInputKind.MULTILINE
        if (pairable) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                BenchInput(input, values[input.id].orEmpty(), Modifier.weight(1.35f)) { onValueChange(input.id, it) }
                BenchInput(next!!, values[next.id].orEmpty().ifBlank { next.defaultValue }, Modifier.weight(1f)) { onValueChange(next.id, it) }
            }
            index += 2
        } else {
            BenchInput(input, values[input.id].orEmpty().ifBlank { input.defaultValue }, Modifier.fillMaxWidth()) { onValueChange(input.id, it) }
            index += 1
        }
    }
}

@Composable
private fun BenchInput(input: LabInputDef, value: String, modifier: Modifier, onValueChange: (String) -> Unit) {
    when (input.kind) {
        LabInputKind.CHOICE -> ChoiceInput(input, value, onValueChange, modifier)
        LabInputKind.MULTILINE -> OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(input.label) },
            supportingText = input.description.takeIf { it.isNotBlank() }?.let { description -> { Text(description) } },
            modifier = modifier.padding(vertical = 4.dp),
            minLines = 4
        )
        LabInputKind.TEXT -> OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(input.label) },
            modifier = modifier.padding(vertical = 4.dp)
        )
        LabInputKind.INTEGER, LabInputKind.NUMBER -> OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(numericText(it, input.kind == LabInputKind.INTEGER)) },
            label = { Text(input.label) },
            supportingText = input.description.takeIf { it.isNotBlank() }?.let { description -> { Text(description) } },
            keyboardOptions = KeyboardOptions(keyboardType = if (input.kind == LabInputKind.INTEGER) KeyboardType.Number else KeyboardType.Decimal),
            modifier = modifier.padding(vertical = 4.dp),
            singleLine = true
        )
    }
}

@Composable
private fun ResultDock(key: String, values: Map<String, String>, instruction: String) {
    val metrics = primaryMetrics(key, values)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("RESULT", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            if (metrics.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                metrics.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { metric ->
                            Column(Modifier.weight(1f)) {
                                Text(metric.first, style = MaterialTheme.typography.labelSmall)
                                Text(metric.second, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                }
                HorizontalDivider()
                Spacer(Modifier.height(10.dp))
            }
            Text(instruction, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        }
    }
}

private fun primaryMetrics(key: String, v: Map<String, String>): List<Pair<String, String>> = when (key) {
    "dilution" -> listOf(
        "Stock" to amount(v, "lab_dilution_stock_volume", "lab_dilution_stock_volume_unit"),
        "Diluent" to amount(v, "lab_dilution_diluent_volume", "lab_dilution_diluent_volume_unit"),
        "Factor" to v["lab_dilution_factor"].orEmpty().let { if (it.isBlank()) "" else "${it}×" }
    )
    "molar_solution" -> listOf(
        "Mass" to amount(v, "lab_molar_solution_mass", "lab_molar_solution_mass_unit"),
        "Molarity" to amount(v, "lab_molar_solution_molarity", "lab_molar_solution_molarity_unit"),
        "Volume" to amount(v, "lab_molar_solution_volume", "lab_molar_solution_volume_unit")
    )
    "reconstitute" -> listOf("Final volume" to amount(v, "lab_reconstitute_final_volume", "lab_reconstitute_final_volume_unit"))
    "serial_dilution" -> listOf(
        "Levels" to v["lab_serial_dilution_step_count"].orEmpty(),
        "Transfer" to amount(v, "lab_serial_dilution_transfer_volume", "lab_serial_dilution_transfer_volume_unit"),
        "Diluent / step" to amount(v, "lab_serial_dilution_diluent_per_step", "lab_serial_dilution_diluent_per_step_unit")
    )
    "master_mix" -> listOf(
        "Effective reactions" to v["lab_master_mix_effective_reaction_count"].orEmpty(),
        "Master mix total" to amount(v, "lab_master_mix_master_mix_total", "lab_master_mix_component_volume_unit"),
        "Per reaction" to amount(v, "lab_master_mix_master_mix_per_reaction", "lab_master_mix_component_volume_unit")
    )
    "centrifuge" -> listOf("RPM" to v["lab_centrifuge_rpm"].orEmpty(), "RCF" to v["lab_centrifuge_rcf"].orEmpty().let { if (it.isBlank()) "" else "$it ×g" })
    "concentration" -> listOf("Converted" to amount(v, "lab_concentration_value", "lab_concentration_unit"))
    "nucleic_acid" -> listOf(
        "Molarity" to amount(v, "lab_nucleic_acid_molarity", "lab_nucleic_acid_molarity_unit"),
        "Mass concentration" to amount(v, "lab_nucleic_acid_mass_concentration", "lab_nucleic_acid_mass_concentration_unit"),
        "Copies" to v["lab_nucleic_acid_copy_number"].orEmpty()
    )
    "cell_dilution" -> listOf(
        "Sample" to amount(v, "lab_cell_dilution_sample_volume", "lab_cell_dilution_sample_volume_unit"),
        "Medium" to amount(v, "lab_cell_dilution_medium_volume", "lab_cell_dilution_medium_volume_unit")
    )
    "hemocytometer" -> listOf(
        "Cells / mL" to v["lab_hemocytometer_cells_per_ml"].orEmpty(),
        "Viability" to v["lab_hemocytometer_viability_percent"].orEmpty().let { if (it.isBlank()) "" else "$it%" }
    )
    "aliquot" -> listOf(
        "Aliquots" to v["lab_aliquot_count"].orEmpty(),
        "Aliquot size" to amount(v, "lab_aliquot_aliquot_volume", "lab_aliquot_aliquot_volume_unit"),
        "Remainder" to amount(v, "lab_aliquot_remainder", "lab_aliquot_remainder_unit")
    )
    "percent_solution" -> listOf(
        "Component" to amount(v, "lab_percent_solution_component_amount", "lab_percent_solution_component_unit"),
        "Remainder" to amount(v, "lab_percent_solution_remainder_amount", "lab_percent_solution_remainder_unit")
    )
    else -> emptyList()
}.filter { it.second.isNotBlank() }

private fun amount(values: Map<String, String>, valueKey: String, unitKey: String): String =
    listOf(values[valueKey].orEmpty(), choiceLabel(values[unitKey].orEmpty())).filter { it.isNotBlank() }.joinToString(" ")

private fun toolCue(key: String): String = when (key) {
    "dilution" -> "C₁V₁ = C₂V₂"
    "molar_solution" -> "Mass · molarity · volume"
    "reconstitute" -> "Vial / standard / oligo"
    "serial_dilution" -> "Series planning"
    "master_mix" -> "Scale reaction mixes"
    "centrifuge" -> "RCF ↔ RPM"
    "concentration" -> "Unit conversion"
    "nucleic_acid" -> "DNA / RNA mass · molarity · copies"
    "cell_dilution" -> "Cells / OD suspension"
    "hemocytometer" -> "Count · concentration · viability"
    "aliquot" -> "Split material safely"
    "percent_solution" -> "w/v · v/v · w/w"
    else -> "Laboratory calculation"
}

private fun resetDashboardToolState(rootJson: String, definition: LabCalculatorDefinition): String {
    val root = runCatching { JSONObject(rootJson) }.getOrElse { JSONObject() }
    root.put(definition.key, JSONObject(initialStateJson(definition.inputs, emptyMap())))
    return root.toString()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalculatorInputs(
    definition: LabCalculatorDefinition,
    values: Map<String, String>,
    context: CapabilityScreenContext,
    alwaysShow: Boolean = false,
    onValueChange: (String, String) -> Unit
) {
    definition.inputs.forEach { input ->
        if ((!alwaysShow && !context.settingShouldBeShown(input.id)) || !inputVisible(definition, input, values)) return@forEach
        when (input.kind) {
            LabInputKind.CHOICE -> ChoiceInput(
                input = input,
                value = values[input.id].orEmpty().ifBlank { input.defaultValue },
                onValueChange = { selected -> onValueChange(input.id, selected) }
            )
            LabInputKind.MULTILINE -> OutlinedTextField(
                value = values[input.id].orEmpty(),
                onValueChange = { onValueChange(input.id, it) },
                label = { Text(input.label) },
                supportingText = input.description.takeIf { it.isNotBlank() }?.let { description -> { Text(description) } },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                minLines = 4
            )
            LabInputKind.TEXT -> OutlinedTextField(
                value = values[input.id].orEmpty(), onValueChange = { onValueChange(input.id, it) },
                label = { Text(input.label) }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            )
            LabInputKind.INTEGER, LabInputKind.NUMBER -> OutlinedTextField(
                value = values[input.id].orEmpty(),
                onValueChange = { onValueChange(input.id, numericText(it, input.kind == LabInputKind.INTEGER)) },
                label = { Text(input.label) },
                supportingText = input.description.takeIf { it.isNotBlank() }?.let { description -> { Text(description) } },
                keyboardOptions = KeyboardOptions(keyboardType = if (input.kind == LabInputKind.INTEGER) KeyboardType.Number else KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                singleLine = true
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceInput(input: LabInputDef, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier.fillMaxWidth()) {
    var expanded by remember(input.id) { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }, modifier = modifier.padding(vertical = 4.dp)) {
        OutlinedTextField(
            value = choiceLabel(value), onValueChange = {}, readOnly = true, label = { Text(input.label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            input.choices.forEach { choice ->
                DropdownMenuItem(text = { Text(choiceLabel(choice)) }, onClick = { onValueChange(choice); expanded = false })
            }
        }
    }
}


private fun inputVisible(definition: LabCalculatorDefinition, input: LabInputDef, values: Map<String, String>): Boolean {
    val mode = values["mode"].orEmpty().ifBlank { definition.inputs.firstOrNull { it.id == "mode" }?.defaultValue.orEmpty() }
    return when (definition.key) {
        "molar_solution" -> when (mode) {
            "mass_required" -> input.id !in setOf("mass", "mass_unit")
            "molarity_from_mass" -> input.id !in setOf("molarity", "molarity_unit")
            "volume_from_mass" -> input.id !in setOf("volume", "volume_unit")
            else -> true
        }
        "centrifuge" -> when (mode) {
            "rpm_to_rcf" -> input.id != "rcf"
            else -> input.id != "rpm"
        }
        "aliquot" -> when (mode) {
            "count_to_size" -> input.id != "aliquot_volume"
            else -> input.id != "number_of_aliquots"
        }
        "nucleic_acid" -> when (mode) {
            "mass_conc_to_molarity", "copies_per_ul" -> input.id !in setOf("molarity", "molarity_unit", "mass", "mass_unit", "target_molarity", "target_molarity_unit")
            "molarity_to_mass_conc" -> input.id !in setOf("mass_concentration", "mass_concentration_unit", "mass", "mass_unit", "target_molarity", "target_molarity_unit")
            "mass_to_copies" -> input.id !in setOf("mass_concentration", "mass_concentration_unit", "molarity", "molarity_unit", "target_molarity", "target_molarity_unit")
            "reconstitution" -> input.id !in setOf("mass_concentration", "mass_concentration_unit", "molarity", "molarity_unit")
            else -> true
        }
        else -> true
    }
}

private fun executionFor(method: LabBenchMethod, context: CapabilityScreenContext, calculated: Map<String, String>, settings: Map<String, String>): ExecutionResult {
    val request = method.request(
        action = method.id,
        context = context.request.invocationContext.asMap(method.id) + context.action.settings + settings,
        signals = emptyList(),
        inputs = emptyList()
    )
    return method.result(request, calculated, context.request.invocationContext)
}

private fun initialStateJson(inputs: List<LabInputDef>, supplied: Map<String, String>): String {
    val obj = JSONObject()
    inputs.forEach { input ->
        obj.put(input.id, supplied[input.id] ?: supplied["input_${input.id}"] ?: input.defaultValue)
    }
    return obj.toString()
}

private fun initialDashboardStateJson(supplied: Map<String, String>): String {
    val root = JSONObject()
    LabBenchDefinitions.all.forEach { definition -> root.put(definition.key, JSONObject(initialStateJson(definition.inputs, if ((supplied["calculator"] ?: supplied["input_calculator"]) == definition.key) supplied else emptyMap()))) }
    return root.toString()
}

private fun dashboardToolState(rootJson: String, definition: LabCalculatorDefinition): Map<String, String> {
    val root = runCatching { JSONObject(rootJson) }.getOrElse { JSONObject() }
    val obj = root.optJSONObject(definition.key) ?: JSONObject(initialStateJson(definition.inputs, emptyMap()))
    return jsonObjectToMap(obj)
}

private fun updateDashboardValue(rootJson: String, key: String, id: String, value: String): String {
    val root = runCatching { JSONObject(rootJson) }.getOrElse { JSONObject() }
    val obj = root.optJSONObject(key) ?: JSONObject()
    obj.put(id, value)
    root.put(key, obj)
    return root.toString()
}

private fun updateJsonValue(json: String, id: String, value: String): String {
    val obj = runCatching { JSONObject(json) }.getOrElse { JSONObject() }
    obj.put(id, value)
    return obj.toString()
}

private fun jsonToMap(json: String): Map<String, String> = runCatching { jsonObjectToMap(JSONObject(json)) }.getOrElse { emptyMap() }
private fun jsonObjectToMap(obj: JSONObject): Map<String, String> = obj.keys().asSequence().associateWith { key -> obj.optString(key, "") }
private fun mapToJson(values: Map<String, String>): String = JSONObject(values).toString()

private fun numericText(value: String, integerOnly: Boolean): String {
    val filtered = value.filter { it.isDigit() || it == '-' || (!integerOnly && (it == '.' || it == 'e' || it == 'E' || it == '+')) }
    return if (integerOnly) {
        val minus = if (filtered.startsWith("-")) "-" else ""
        minus + filtered.removePrefix("-").filter(Char::isDigit)
    } else filtered
}

private fun choiceLabel(value: String): String = when (value) {
    "uL" -> "µL"; "uM" -> "µM"; "umol" -> "µmol"; "ug" -> "µg"; "ng/uL" -> "ng/µL"; "cells/uL" -> "cells/µL"
    "percent_wv" -> "% w/v"; "percent_ww" -> "% w/w"; "percent_vv" -> "% v/v"
    "w_v" -> "% w/v"; "v_v" -> "% v/v"; "w_w" -> "% w/w"
    "mass_required" -> "Mass required"; "molarity_from_mass" -> "Molarity from mass"; "volume_from_mass" -> "Volume from mass"
    "rcf_to_rpm" -> "RCF → RPM"; "rpm_to_rcf" -> "RPM → RCF"
    "mass_conc_to_molarity" -> "Mass concentration → molarity"; "molarity_to_mass_conc" -> "Molarity → mass concentration"
    "mass_to_copies" -> "Mass → molecule count"; "copies_per_ul" -> "Mass concentration → copies/µL"; "reconstitution" -> "Reconstitution volume"
    "size_to_count" -> "Aliquot size → count"; "count_to_size" -> "Count → maximum aliquot size"
    "extra_reactions" -> "Extra reactions"; "percent" -> "Percent"
    else -> value
}

private fun titleFor(definition: LabCalculatorDefinition) = "Lab Bench · ${definition.title}"
