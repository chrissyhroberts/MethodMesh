package com.example.methodmesh.modules.conversions

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.*

object ConversionsCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100ConversionsMethod.ID
    override val title = "Conversions / General Calculator"
    override val description = "Offline conversions and lightweight calculations."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        var category by rememberSaveable { mutableStateOf(context.action.settings["category"] ?: context.action.settings["input_category"] ?: "length") }
        var value by rememberSaveable { mutableStateOf(context.action.settings["value"] ?: context.action.settings["input_value"] ?: "") }
        var value2 by rememberSaveable { mutableStateOf(context.action.settings["value2"] ?: context.action.settings["input_value2"] ?: "") }
        var value3 by rememberSaveable { mutableStateOf(context.action.settings["value3"] ?: context.action.settings["input_value3"] ?: "") }
        var from by rememberSaveable { mutableStateOf(context.action.settings["from_unit"] ?: context.action.settings["input_from_unit"] ?: "") }
        var to by rememberSaveable { mutableStateOf(context.action.settings["to_unit"] ?: context.action.settings["input_to_unit"] ?: "") }
        var operation by rememberSaveable { mutableStateOf(context.action.settings["operation"] ?: context.action.settings["input_operation"] ?: "") }
        var date1 by rememberSaveable { mutableStateOf(context.action.settings["date1"] ?: context.action.settings["input_date1"] ?: "") }
        var date2 by rememberSaveable { mutableStateOf(context.action.settings["date2"] ?: context.action.settings["input_date2"] ?: "") }
        var shape by rememberSaveable { mutableStateOf(context.action.settings["shape"] ?: context.action.settings["input_shape"] ?: "rectangle") }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var launched by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }

        val unitChoices = when (category) {
            "temperature" -> listOf("C", "F", "K")
            else -> As100ConversionsMethod.unitsByCategory[category]?.keys?.toList().orEmpty()
        }

        LaunchedEffect(category) {
            if (unitChoices.isNotEmpty()) {
                if (from !in unitChoices) from = unitChoices.first()
                if (to !in unitChoices) to = unitChoices.getOrElse(1) { unitChoices.first() }
            }
            val validOperations = operationsFor(category, shape)
            if (validOperations.isNotEmpty() && operation !in validOperations) operation = validOperations.first()
        }
        LaunchedEffect(shape) {
            if (category == "geometry") {
                val valid = operationsFor(category, shape)
                if (operation !in valid) operation = valid.first()
            }
        }

        fun run() {
            val settings = mapOf(
                "category" to category,
                "value" to value,
                "value2" to value2,
                "value3" to value3,
                "from_unit" to from,
                "to_unit" to to,
                "operation" to operation,
                "date1" to date1,
                "date2" to date2,
                "shape" to shape
            )
            val request = As100ConversionsMethod.request(
                action = capabilityId,
                context = context.request.invocationContext.asMap(capabilityId) + context.action.settings + settings,
                signals = emptyList(),
                inputs = emptyList()
            )
            result = As100ConversionsMethod.result(
                request,
                As100ConversionsMethod.calculate(settings),
                context.request.invocationContext
            )
            if (context.submitsImmediately) result?.let(onConfirmed)
        }

        LaunchedEffect(category, value, value2, value3, from, to, operation, date1, date2, shape) {
            context.onSettingsChanged(
                mapOf(
                    "category" to category,
                    "value" to value,
                    "value2" to value2,
                    "value3" to value3,
                    "from_unit" to from,
                    "to_unit" to to,
                    "operation" to operation,
                    "date1" to date1,
                    "date2" to date2,
                    "shape" to shape
                )
            )
        }
        LaunchedEffect(context.presentationMode) {
            if (context.presentationMode == CapabilityPresentationMode.IntentLaunch && !launched) {
                launched = true
                run()
            }
        }

        CapabilityScreenScaffold(
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = result,
            resultPreview = result?.let { OutputFormatter.fields(it, false) }.orEmpty(),
            onBack = onBack,
            onRetry = { run() },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Text("All calculations are local/offline.")
            Spacer(Modifier.height(8.dp))

            if (context.settingShouldBeShown("category")) {
                ChoiceChips(
                    "Calculation",
                    listOf("length", "area", "volume", "mass", "temperature", "speed", "pressure", "energy", "power", "angle", "data_size", "percentage", "ratio", "date_difference", "date_arithmetic", "age", "geometry"),
                    category
                ) { category = it }
            } else {
                Text("Calculation: ${category.replace('_', ' ')}", style = MaterialTheme.typography.bodySmall)
            }

            when (category) {
                "date_difference", "age" -> {
                    if (context.settingShouldBeShown("date1")) {
                        OutlinedTextField(
                            date1,
                            { date1 = it },
                            label = { Text(if (category == "age") "Birth date (YYYY-MM-DD)" else "Date 1 (YYYY-MM-DD)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (context.settingShouldBeShown("date2")) {
                        OutlinedTextField(
                            date2,
                            { date2 = it },
                            label = { Text(if (category == "age") "At date (optional)" else "Date 2 (YYYY-MM-DD)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                "date_arithmetic" -> {
                    if (context.settingShouldBeShown("date1")) {
                        OutlinedTextField(date1, { date1 = it }, label = { Text("Date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
                    }
                    if (context.settingShouldBeShown("value")) {
                        OutlinedTextField(value, { value = signedIntegerText(it) }, label = { Text("Amount") }, modifier = Modifier.fillMaxWidth())
                    }
                    if (context.settingShouldBeShown("operation")) {
                        ChoiceChips("Operation", operationsFor(category, shape), operation) { operation = it }
                    }
                }

                else -> {
                    if (context.settingShouldBeShown("value")) {
                        OutlinedTextField(
                            value,
                            { value = numericText(it) },
                            label = { Text(if (category == "geometry" && shape == "circle") "Radius" else "Value / A") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (category in listOf("percentage", "ratio", "geometry") && !(category == "geometry" && shape == "circle") && context.settingShouldBeShown("value2")) {
                        OutlinedTextField(value2, { value2 = numericText(it) }, label = { Text("Second value / B") }, modifier = Modifier.fillMaxWidth())
                    }
                    if (category == "ratio" && operation == "solve_proportion" && context.settingShouldBeShown("value3")) {
                        OutlinedTextField(value3, { value3 = numericText(it) }, label = { Text("Third value / C (A:B = C:X)") }, modifier = Modifier.fillMaxWidth())
                    }

                    if (unitChoices.isNotEmpty()) {
                        if (context.settingShouldBeShown("from_unit")) ChoiceChips("From unit", unitChoices, from) { from = it }
                        if (context.settingShouldBeShown("to_unit")) ChoiceChips("To unit", unitChoices, to) { to = it }
                    }

                    if (category == "geometry" && context.settingShouldBeShown("shape")) {
                        ChoiceChips("Shape", listOf("rectangle", "triangle", "circle"), shape) { shape = it }
                    }
                    val operationChoices = operationsFor(category, shape)
                    if (operationChoices.isNotEmpty() && context.settingShouldBeShown("operation")) {
                        ChoiceChips("Operation", operationChoices, operation) { operation = it }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(onClick = ::run, modifier = Modifier.fillMaxWidth()) { Text("Calculate") }
        }
    }
}

private fun operationsFor(category: String, shape: String): List<String> = when (category) {
    "percentage" -> listOf("percent_of", "what_percent", "percent_change", "increase_by_percent", "decrease_by_percent")
    "ratio" -> listOf("a_to_b", "solve_proportion")
    "date_arithmetic" -> listOf("add_days", "add_weeks", "add_months", "add_years", "subtract_days")
    "geometry" -> when (shape) {
        "rectangle" -> listOf("area", "perimeter")
        "triangle" -> listOf("area")
        "circle" -> listOf("area", "circumference")
        else -> listOf("area")
    }
    else -> emptyList()
}

@Composable
private fun ChoiceChips(label: String, choices: List<String>, selected: String, onSelect: (String) -> Unit) {
    Text(label, style = MaterialTheme.typography.bodySmall)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        choices.forEach { choice ->
            FilterChip(
                selected = selected == choice,
                onClick = { onSelect(choice) },
                label = { Text(choice.replace('_', ' ')) },
                modifier = Modifier.padding(2.dp)
            )
        }
    }
}

private fun numericText(value: String): String = value.filter { it.isDigit() || it == '-' || it == '.' }
private fun signedIntegerText(value: String): String = value.filter { it.isDigit() || it == '-' }
