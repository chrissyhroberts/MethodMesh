package com.example.methodmesh.modules.labbench

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
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
        var liveResult by remember(context.action.canonicalId) { mutableStateOf<ExecutionResult?>(null) }
        var launched by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        val definition = LabBenchDefinitions.byKey.getValue(selectedKey)
        val values = remember(dashboardStateJson, selectedKey) { dashboardToolState(dashboardStateJson, definition) }
        val reconstructedResult = remember(resultValuesJson) {
            if (resultValuesJson.isBlank()) null else executionFor(LabBenchMethods.Dashboard, context, jsonToMap(resultValuesJson), mapOf("calculator" to selectedKey) + values)
        }
        val result = liveResult ?: reconstructedResult
        val keepLiveDashboard = context.presentationMode == CapabilityPresentationMode.Dashboard || context.isNativePresetRun || context.request.source.equals("intent_test", ignoreCase = true)
        val scaffoldResult = if (keepLiveDashboard) null else result

        LaunchedEffect(selectedKey, dashboardStateJson) {
            context.onSettingsChanged(mapOf("calculator" to selectedKey) + values)
        }

        fun calculate(autoSubmit: Boolean = false) {
            val settings = mapOf("calculator" to selectedKey) + values
            val calculated = LabBenchMethods.Dashboard.calculate(settings)
            resultValuesJson = mapToJson(calculated)
            val execution = executionFor(LabBenchMethods.Dashboard, context, calculated, settings)
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
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = scaffoldResult,
            resultPreview = scaffoldResult?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty(),
            onBack = onBack,
            onRetry = {
                resultValuesJson = ""
                liveResult = null
            },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Text("Choose a bench calculation. Values are kept while you switch tools during this dashboard session.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            if (context.settingShouldBeShown("calculator")) {
                DashboardChooser(selectedKey) { next ->
                    selectedKey = next
                    resultValuesJson = ""
                    liveResult = null
                }
                Spacer(Modifier.height(12.dp))
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(definition.title, style = MaterialTheme.typography.titleMedium)
                    Text(definition.description, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    CalculatorInputs(definition, values, context, alwaysShow = false) { id, value ->
                        dashboardStateJson = updateDashboardValue(dashboardStateJson, selectedKey, id, value)
                        resultValuesJson = ""
                        liveResult = null
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { calculate() }, modifier = Modifier.fillMaxWidth()) { Text(if (result == null) "Calculate" else "Recalculate") }
                    val preview = resultValuesJson.takeIf { it.isNotBlank() }?.let(::jsonToMap).orEmpty()
                    val instruction = preview["labbench_dashboard_instruction"].orEmpty()
                    val error = preview["labbench_dashboard_error"].orEmpty()
                    if (instruction.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text("Result", style = MaterialTheme.typography.labelLarge)
                        Text(instruction, style = MaterialTheme.typography.bodyLarge)
                    }
                    if (error.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(error, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (keepLiveDashboard && result != null) {
                Spacer(Modifier.height(12.dp))
                Button(onClick = { result?.let(onConfirmed) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (context.isNativePresetRun) "Finish" else "Use this calculation")
                }
            }
        }
    }
}

@Composable
private fun DashboardChooser(selectedKey: String, onSelected: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LabBenchDefinitions.all.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pair.forEach { definition ->
                    val selected = selectedKey == definition.key
                    if (selected) {
                        Button(onClick = { onSelected(definition.key) }, modifier = Modifier.weight(1f)) { Text("✓ ${definition.title}") }
                    } else {
                        OutlinedButton(onClick = { onSelected(definition.key) }, modifier = Modifier.weight(1f)) { Text(definition.title) }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
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
            LabInputKind.CHOICE -> ChoiceInput(input, values[input.id].orEmpty().ifBlank { input.defaultValue }) { onValueChange(input.id, it) }
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
private fun ChoiceInput(input: LabInputDef, value: String, onValueChange: (String) -> Unit) {
    var expanded by remember(input.id) { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
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
