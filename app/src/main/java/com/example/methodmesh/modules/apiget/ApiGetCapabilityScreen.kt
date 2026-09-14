package com.example.methodmesh.modules.apiget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.onlinedata.ApiDefinitionRepository
import com.example.methodmesh.core.onlinedata.LocationDisclosureMode
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityPresentationMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
object ApiGetCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100ApiGetMethod.ID
    override val title = "Online API GET"
    override val description = "Run a declared online API link."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val androidContext = LocalContext.current
        val scope = rememberCoroutineScope()
        val definitions = remember { ApiDefinitionRepository.all(androidContext) }
        var definitionId by rememberSaveable {
            mutableStateOf(context.action.settings.value("definition_id").ifBlank { definitions.firstOrNull()?.id.orEmpty() })
        }
        val selectedDefinition = definitions.firstOrNull { it.id == definitionId } ?: definitions.firstOrNull()
        val inputValues = remember { mutableStateMapOf<String, String>() }
        var resultPathsRaw by rememberSaveable {
            mutableStateOf(
                context.action.settings.value("result_paths")
                    .ifBlank { context.action.settings.value("api_result_paths") }
                    .ifBlank { context.action.settings.value("result_path") }
            )
        }
        var fallbackValue by rememberSaveable {
            mutableStateOf(context.action.settings.value("fallback_value"))
        }
        var currencySearchFrom by rememberSaveable { mutableStateOf("") }
        var currencySearchTo by rememberSaveable { mutableStateOf("") }
        var amount by rememberSaveable { mutableStateOf(context.action.settings.value("amount")) }
        var launched by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        var running by rememberSaveable { mutableStateOf(false) }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var status by rememberSaveable { mutableStateOf("Ready.") }
        var expandedInputId by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(selectedDefinition?.id) {
            selectedDefinition?.inputs.orEmpty().forEach { input ->
                inputValues.putIfAbsent(
                    input.id,
                    context.action.settings.value(input.id).ifBlank { input.defaultValue }
                )
            }
            if (resultPathsRaw.isBlank() && selectedDefinition != null) {
                resultPathsRaw = As100ApiGetMethod.defaultResultPaths(selectedDefinition).joinToString("|")
            }
            if (selectedDefinition?.id == "frankfurter.latest_rates") {
                inputValues.putIfAbsent("base", context.action.settings.value("base").ifBlank { "GBP" })
                inputValues.putIfAbsent("target_currency", context.action.settings.value("target_currency").ifBlank { "USD" })
                inputValues["symbols"] = inputValues["target_currency"].orEmpty().ifBlank { "USD" }
            }
        }

        LaunchedEffect(definitionId, resultPathsRaw, fallbackValue, amount, inputValues.toMap()) {
            context.onSettingsChanged(
                mapOf(
                    "definition_id" to definitionId,
                    "result_paths" to resultPathsRaw,
                    "fallback_value" to fallbackValue,
                    "amount" to amount
                ) + inputValues.toMap()
            )
        }

        fun runApi() {
            val definition = selectedDefinition ?: return
            val selectedPaths = resultPathsRaw.pathSet().ifEmpty {
                As100ApiGetMethod.defaultResultPaths(definition).toSet()
            }
            val settings = mapOf(
                "definition_id" to definition.id,
                "result_path" to selectedPaths.firstOrNull().orEmpty().ifBlank { As100ApiGetMethod.defaultResultPath(definition) },
                "result_paths" to selectedPaths.joinToString("|"),
                "fallback_value" to fallbackValue,
                "amount" to amount
            ) + inputValues.toMap()
            running = true
            status = "Sending request…"
            scope.launch {
                val execution = withContext(Dispatchers.IO) {
                    val request = As100ApiGetMethod.request(
                        action = As100ApiGetMethod.ID,
                        context = context.request.invocationContext.asMap(As100ApiGetMethod.ID) + context.action.settings + settings,
                        signals = emptyList(),
                        inputs = emptyList()
                    )
                    As100ApiGetMethod.result(request, As100ApiGetMethod.runApi(settings), context.request.invocationContext)
                }
                result = execution
                val fields = OutputFormatter.fields(execution, includeProvenance = false)
                status = fields[ApiGetFields.VALUE]?.toString()?.takeIf { it.isNotBlank() }
                    ?: fields[ApiGetFields.ERROR]?.toString()?.takeIf { it.isNotBlank() }
                    ?: execution.status.name
                running = false
                if (context.submitsImmediately) onConfirmed(execution)
            }
        }

        LaunchedEffect(context.presentationMode, context.action.settings, selectedDefinition?.id) {
            if ((context.startsImmediately || context.presentationMode == CapabilityPresentationMode.IntentLaunch) && !launched && selectedDefinition != null) {
                launched = true
                runApi()
            }
        }

        val preview = result?.let { execution ->
            val fields = OutputFormatter.fields(execution, includeProvenance = false)
            linkedMapOf<String, Any?>().apply {
                putAll(apiValuesForPreview(fields[ApiGetFields.VALUES_JSON].stringOrEmpty()))
                if (isEmpty()) {
                    put(fields[ApiGetFields.LABEL]?.toString().orEmpty().ifBlank { "API value" }, fields[ApiGetFields.VALUE].stringOrEmpty())
                }
                if (fields[ApiGetFields.EXCHANGE_RATE].stringOrEmpty().isNotBlank()) {
                    put("Exchange rate", "1 ${inputValues["base"].orEmpty()} = ${fields[ApiGetFields.EXCHANGE_RATE]} ${inputValues["target_currency"].orEmpty().ifBlank { inputValues["symbols"].orEmpty() }}")
                }
                if (fields[ApiGetFields.EXCHANGE_CONVERTED].stringOrEmpty().isNotBlank()) {
                    put("Converted", "${fields[ApiGetFields.EXCHANGE_AMOUNT]} ${inputValues["base"].orEmpty()} = ${fields[ApiGetFields.EXCHANGE_CONVERTED]} ${inputValues["target_currency"].orEmpty().ifBlank { inputValues["symbols"].orEmpty() }}")
                }
                put(
                    "Updated",
                    ageLabel(fields[ApiGetFields.DATA_AGE_HOURS].stringOrEmpty(), fields[ApiGetFields.RETRIEVED_TIME_ISO].stringOrEmpty())
                )
            }.filterValues { it.toString().isNotBlank() }.ifEmpty {
                mapOf(
                    "Status" to fields[ApiGetFields.STATUS].stringOrEmpty(),
                    "Error" to fields[ApiGetFields.ERROR].stringOrEmpty()
                )
            }
        }.orEmpty()

        CapabilityScreenScaffold(
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = result,
            resultPreview = preview,
            onBack = onBack,
            onRetry = { runApi() },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Text("Run a declared data link and return the selected value.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            if (!context.settingShouldBeShown("definition_id")) {
                selectedDefinition?.let {
                    Text(it.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
            } else {
                Text("API link", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                definitions.forEach { definition ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                definitionId = definition.id
                                resultPathsRaw = As100ApiGetMethod.defaultResultPaths(definition).joinToString("|")
                                status = "Ready."
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = definition.id == selectedDefinition?.id,
                            onClick = {
                                definitionId = definition.id
                                resultPathsRaw = As100ApiGetMethod.defaultResultPaths(definition).joinToString("|")
                                status = "Ready."
                            }
                        )
                        Column(Modifier.weight(1f)) {
                            Text(definition.name)
                            Text(definition.attribution.providerName, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            selectedDefinition?.let { definition ->
                if (definition.privacy.sendsLocation) {
                    Text(
                        apiLocationDisclosure(definition.attribution.providerName, definition.privacy.locationMode, definition.privacy.roundedLocationRadiusMeters),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                val isExchangeRate = definition.id == "frankfurter.latest_rates"
                if (isExchangeRate) {
                    CurrencySearchPicker(
                        title = "From",
                        query = currencySearchFrom,
                        onQueryChange = { currencySearchFrom = it },
                        selectedCode = inputValues["base"].orEmpty().ifBlank { "GBP" },
                        choices = As100ApiGetMethod.inputChoices(definition, "base"),
                        onSelected = { inputValues["base"] = it }
                    )
                    CurrencySearchPicker(
                        title = "To",
                        query = currencySearchTo,
                        onQueryChange = { currencySearchTo = it },
                        selectedCode = inputValues["target_currency"].orEmpty().ifBlank { "USD" },
                        choices = As100ApiGetMethod.inputChoices(definition, "symbols"),
                        onSelected = {
                            inputValues["target_currency"] = it
                            inputValues["symbols"] = it
                            resultPathsRaw = "rates.$it|date|base"
                        }
                    )
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it.filter { char -> char.isDigit() || char == '.' } },
                        label = { Text("Amount, optional") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                definition.inputs.forEach { input ->
                    if (!isExchangeRate && context.settingShouldBeShown(input.id)) {
                        val choices = As100ApiGetMethod.inputChoices(definition, input.id)
                        when {
                            choices.isNotEmpty() && As100ApiGetMethod.inputIsMultiChoice(definition, input.id) -> {
                                val selected = inputValues[input.id].orEmpty().pathSet().ifEmpty {
                                    input.defaultValue.pathSet()
                                }
                                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                    Text(input.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                    choices.forEach { choice ->
                                        val checked = choice.value in selected
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    inputValues[input.id] = selected.toggled(choice.value).joinToString(",")
                                                },
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = checked,
                                                onCheckedChange = {
                                                    inputValues[input.id] = selected.toggled(choice.value).joinToString(",")
                                                }
                                            )
                                            Text(choice.label, modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                            choices.isNotEmpty() -> {
                                val expanded = expandedInputId == input.id
                                val value = inputValues[input.id].orEmpty().ifBlank { input.defaultValue }
                                val label = choices.firstOrNull { it.value == value }?.label ?: value
                                ExposedDropdownMenuBox(
                                    expanded = expanded,
                                    onExpandedChange = { expandedInputId = if (expanded) null else input.id },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedTextField(
                                        value = label,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text(input.name) },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                                        modifier = Modifier
                                            .menuAnchor()
                                            .fillMaxWidth(),
                                        singleLine = true
                                    )
                                    ExposedDropdownMenu(
                                        expanded = expanded,
                                        onDismissRequest = { expandedInputId = null }
                                    ) {
                                        choices.forEach { choice ->
                                            DropdownMenuItem(
                                                text = { Text(choice.label) },
                                                onClick = {
                                                    inputValues[input.id] = choice.value
                                                    expandedInputId = null
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            else -> {
                                OutlinedTextField(
                                    value = inputValues[input.id].orEmpty(),
                                    onValueChange = { inputValues[input.id] = it },
                                    label = { Text(input.name) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                            }
                        }
                    }
                }
                if (!isExchangeRate && context.settingShouldBeShown("result_paths")) {
                    val pathOptions = As100ApiGetMethod.resultPathOptions(definition)
                    val selectedPaths = resultPathsRaw.pathSet().ifEmpty {
                        As100ApiGetMethod.defaultResultPaths(definition).toSet()
                    }
                    if (pathOptions.isNotEmpty()) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Text("Return fields", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            pathOptions.forEach { option ->
                                val checked = option.path in selectedPaths
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            resultPathsRaw = selectedPaths.toggled(option.path).joinToString("|")
                                        },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = {
                                            resultPathsRaw = selectedPaths.toggled(option.path).joinToString("|")
                                        }
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(option.label)
                                        Text(option.path, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = resultPathsRaw,
                            onValueChange = { resultPathsRaw = it },
                            label = { Text("API result paths") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
                if (!isExchangeRate && context.settingShouldBeShown("fallback_value")) {
                    OutlinedTextField(
                        value = fallbackValue,
                        onValueChange = { fallbackValue = it },
                        label = { Text("Fallback value") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Button(onClick = { runApi() }, modifier = Modifier.fillMaxWidth(), enabled = !running && selectedDefinition != null) {
                Text(if (running) "Sending…" else "Run API")
            }
            Text(status, modifier = Modifier.padding(top = 8.dp))
        }
    }

    @Composable
    private fun CurrencySearchPicker(
        title: String,
        query: String,
        onQueryChange: (String) -> Unit,
        selectedCode: String,
        choices: List<ApiInputChoice>,
        onSelected: (String) -> Unit
    ) {
        val selectedLabel = choices.firstOrNull { it.value == selectedCode }?.label ?: selectedCode
        val filtered = choices
            .filter { choice ->
                query.isBlank() ||
                    choice.label.contains(query, ignoreCase = true) ||
                    choice.value.contains(query, ignoreCase = true)
            }
            .take(8)
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text("$title: $selectedLabel", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                label = { Text("Search country or currency") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            filtered.forEach { choice ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelected(choice.value)
                            onQueryChange("")
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = choice.value == selectedCode,
                        onClick = {
                            onSelected(choice.value)
                            onQueryChange("")
                        }
                    )
                    Text(choice.label, modifier = Modifier.weight(1f))
                }
            }
        }
    }

    private fun Map<String, String>.value(key: String): String =
        (this[key] ?: this["input_$key"]).orEmpty()

    private fun apiLocationDisclosure(provider: String, mode: LocationDisclosureMode, radiusMeters: Int): String =
        when (mode) {
            LocationDisclosureMode.ROUNDED -> "Sends rounded location to $provider, about ${radiusMeters / 1_000} km precision."
            LocationDisclosureMode.EXACT -> "Sends exact location to $provider."
            LocationDisclosureMode.MANUAL -> "Sends manually entered location to $provider."
            LocationDisclosureMode.DISABLED -> ""
        }

    private fun Any?.stringOrEmpty(): String = this?.toString().orEmpty()

    private fun apiValuesForPreview(valuesJson: String): Map<String, String> =
        runCatching {
            val json = JSONObject(valuesJson)
            json.keys().asSequence().associate { path ->
                val item = json.optJSONObject(path)
                val label = item?.optString("label")?.takeIf { it.isNotBlank() } ?: path
                val value = item?.optString("value").orEmpty()
                label to value
            }
        }.getOrDefault(emptyMap())

    private fun String.pathSet(): Set<String> =
        split('|', ',', ';', '\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toCollection(linkedSetOf())

    private fun Set<String>.toggled(path: String): Set<String> =
        toMutableSet().apply {
            if (path in this) remove(path) else add(path)
        }

    private fun ageLabel(ageHours: String, retrievedAt: String): String {
        val age = ageHours.toDoubleOrNull() ?: return retrievedAt
        val ageText = when {
            age < 0.02 -> "just now"
            age < 1.0 -> "${(age * 60).toInt().coerceAtLeast(1)} min ago"
            else -> "%.1f h ago".format(java.util.Locale.US, age)
        }
        return if (retrievedAt.isBlank()) ageText else "$ageText · $retrievedAt"
    }
}
