package com.example.methodmesh.modules.fieldstats

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityPresentationMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import org.json.JSONObject

sealed class FieldInput(
    val id: String,
    val label: String,
    val defaultValue: String,
    val helper: String? = null,
    val advanced: Boolean = false,
    val visibleWhen: (Map<String, String>) -> Boolean = { true }
) {
    class IntInput(
        id: String,
        label: String,
        defaultValue: String,
        helper: String? = null,
        advanced: Boolean = false,
        visibleWhen: (Map<String, String>) -> Boolean = { true }
    ) : FieldInput(id, label, defaultValue, helper, advanced, visibleWhen)

    class DoubleInput(
        id: String,
        label: String,
        defaultValue: String,
        helper: String? = null,
        advanced: Boolean = false,
        visibleWhen: (Map<String, String>) -> Boolean = { true }
    ) : FieldInput(id, label, defaultValue, helper, advanced, visibleWhen)

    class TextInput(
        id: String,
        label: String,
        defaultValue: String = "",
        val minLines: Int = 4,
        helper: String? = null,
        advanced: Boolean = false,
        visibleWhen: (Map<String, String>) -> Boolean = { true }
    ) : FieldInput(id, label, defaultValue, helper, advanced, visibleWhen)

    class BooleanInput(
        id: String,
        label: String,
        defaultValue: String = "false",
        helper: String? = null,
        advanced: Boolean = false,
        visibleWhen: (Map<String, String>) -> Boolean = { true }
    ) : FieldInput(id, label, defaultValue, helper, advanced, visibleWhen)

    class ChoiceInput(
        id: String,
        label: String,
        defaultValue: String,
        val options: (Map<String, String>) -> List<String>,
        val labels: Map<String, String> = emptyMap(),
        helper: String? = null,
        advanced: Boolean = false,
        visibleWhen: (Map<String, String>) -> Boolean = { true }
    ) : FieldInput(id, label, defaultValue, helper, advanced, visibleWhen)
}

data class CalculatorUiSpec(
    val method: FieldStatsMethod,
    val title: String,
    val description: String,
    val guidance: String,
    val fields: List<FieldInput>,
    val buttonLabel: String = "Calculate"
)

abstract class StandardFieldStatsScreen(private val spec: CalculatorUiSpec) : CapabilityScreenSpec {
    override val capabilityId = spec.method.id
    override val title = spec.title
    override val description = spec.description

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        StandardCalculatorScreen(spec, context, onBack, onConfirmed, onCancel)
    }
}

object Diagnostic2x2CapabilityScreen : StandardFieldStatsScreen(
    CalculatorUiSpec(
        As100Diagnostic2x2Method,
        "Diagnostic 2×2",
        "Evaluate a diagnostic test from four counts.",
        "Enter the observed test result against the reference standard. Predictive values can also be projected to a different prevalence.",
        listOf(
            FieldInput.IntInput("tp", "True positives", "0"),
            FieldInput.IntInput("fp", "False positives", "0"),
            FieldInput.IntInput("fn", "False negatives", "0"),
            FieldInput.IntInput("tn", "True negatives", "0"),
            FieldInput.DoubleInput("confidence", "Confidence level", "0.95", "Use 0.95 for a 95% interval.", advanced = true),
            FieldInput.BooleanInput("use_prevalence_override", "Project PPV/NPV to another prevalence", advanced = true),
            FieldInput.DoubleInput("prevalence_override", "Alternative prevalence", "0.05", "Enter as a proportion, e.g. 0.05 = 5%.", advanced = true) { it["use_prevalence_override"] == "true" }
        )
    )
)

object ProportionCapabilityScreen : StandardFieldStatsScreen(
    CalculatorUiSpec(
        As100ProportionMethod,
        "Proportion + CI",
        "Turn x/n into a percentage and confidence interval.",
        "Useful for prevalence, positivity, coverage, attack rates and other count-over-total measures.",
        listOf(
            FieldInput.IntInput("numerator", "Count of interest", "0"),
            FieldInput.IntInput("denominator", "Total observed", "100"),
            FieldInput.DoubleInput("confidence", "Confidence level", "0.95", "Use 0.95 for a 95% interval.", advanced = true)
        )
    )
)

object SampleSizeProportionCapabilityScreen : StandardFieldStatsScreen(
    CalculatorUiSpec(
        As100SampleSizeProportionMethod,
        "Sample size · one proportion",
        "Plan a prevalence, coverage or single-proportion survey.",
        "Start with the proportion you expect and the absolute precision you need. Extra design assumptions are available under Advanced.",
        listOf(
            FieldInput.DoubleInput("expected_proportion", "Expected proportion", "0.5", "If genuinely unknown, 0.5 is conservative."),
            FieldInput.DoubleInput("absolute_precision", "Absolute precision", "0.05", "0.05 means ±5 percentage points."),
            FieldInput.DoubleInput("confidence", "Confidence level", "0.95", advanced = true),
            FieldInput.IntInput("population_size", "Finite population size", "0", "Leave 0 if not using finite-population correction.", advanced = true),
            FieldInput.DoubleInput("design_effect", "Design effect", "1", "1 = simple random sampling.", advanced = true),
            FieldInput.DoubleInput("nonresponse_fraction", "Expected non-response", "0", "Enter as a proportion, e.g. 0.10 = 10%.", advanced = true)
        )
    )
)

object DetectionLimitCapabilityScreen : StandardFieldStatsScreen(
    CalculatorUiSpec(
        As100DetectionLimitMethod,
        "Detection / zero events",
        "Plan to detect at least one event, or interpret a zero-event sample.",
        "Choose the practical question first. Finite-population sampling is available when you know the complete frame size.",
        listOf(
            FieldInput.ChoiceInput(
                "mode", "What do you need?", "plan_detection",
                options = { listOf("plan_detection", "zero_events") },
                labels = mapOf("plan_detection" to "How many should I sample?", "zero_events" to "I observed zero events")
            ),
            FieldInput.DoubleInput("prevalence", "Minimum prevalence to detect", "0.01", "0.01 = 1%.") { it["mode"] != "zero_events" },
            FieldInput.IntInput("sample_size", "Sample size with zero events", "100") { it["mode"] == "zero_events" },
            FieldInput.DoubleInput("confidence", "Confidence / detection probability", "0.95", advanced = true),
            FieldInput.IntInput("population_size", "Finite population size", "0", "Leave 0 if not using a finite population.", advanced = true)
        )
    )
)

object CompareBinaryCapabilityScreen : StandardFieldStatsScreen(
    CalculatorUiSpec(
        As100CompareBinaryMethod,
        "Compare two groups",
        "Compare binary outcomes between Group A and Group B.",
        "Enter event counts and totals. The result shows absolute and relative effects, with NNT/NNH where interpretable.",
        listOf(
            FieldInput.IntInput("events_a", "Events A", "0"),
            FieldInput.IntInput("total_a", "Total A", "100"),
            FieldInput.IntInput("events_b", "Events B", "0"),
            FieldInput.IntInput("total_b", "Total B", "100"),
            FieldInput.DoubleInput("confidence", "Confidence level", "0.95", advanced = true)
        )
    )
)

object SummaryCapabilityScreen : StandardFieldStatsScreen(
    CalculatorUiSpec(
        As100SummaryMethod,
        "Quick summary",
        "Paste a column of numbers and get the essentials.",
        "New lines, spaces, commas, semicolons and pipes are accepted. Invalid tokens are counted and reported rather than converted to zero.",
        listOf(FieldInput.TextInput("values_text", "Values", minLines = 9, helper = "Paste directly from a spreadsheet column if convenient.")),
        buttonLabel = "Summarise"
    )
)

object SampleSizeTwoProportionsCapabilityScreen : StandardFieldStatsScreen(
    CalculatorUiSpec(
        As100SampleSizeTwoProportionsMethod,
        "Sample size · two proportions",
        "Plan a simple comparison of two independent proportions.",
        "Enter the two proportions you expect. Power, allocation and inflation assumptions sit under Advanced.",
        listOf(
            FieldInput.DoubleInput("proportion_a", "Expected proportion · A", "0.10"),
            FieldInput.DoubleInput("proportion_b", "Expected proportion · B", "0.15"),
            FieldInput.DoubleInput("confidence", "Confidence level", "0.95", advanced = true),
            FieldInput.DoubleInput("power", "Power", "0.80", advanced = true),
            FieldInput.DoubleInput("allocation_ratio_b_to_a", "Allocation ratio · B:A", "1", advanced = true),
            FieldInput.DoubleInput("design_effect", "Design effect", "1", advanced = true),
            FieldInput.DoubleInput("attrition_fraction", "Loss / attrition", "0", advanced = true)
        )
    )
)

object SampleSizeDiagnosticCapabilityScreen : StandardFieldStatsScreen(
    CalculatorUiSpec(
        As100SampleSizeDiagnosticMethod,
        "Diagnostic study sample size",
        "Plan recruitment around sensitivity and specificity precision.",
        "Set the performance you expect and the precision you need. The calculator works out positive cases, negative cases and total recruitment.",
        listOf(
            FieldInput.DoubleInput("expected_sensitivity", "Expected sensitivity", "0.90"),
            FieldInput.DoubleInput("expected_specificity", "Expected specificity", "0.95"),
            FieldInput.DoubleInput("sensitivity_precision", "Sensitivity precision", "0.05"),
            FieldInput.DoubleInput("specificity_precision", "Specificity precision", "0.03"),
            FieldInput.DoubleInput("prevalence", "Expected prevalence", "0.10"),
            FieldInput.DoubleInput("confidence", "Confidence level", "0.95", advanced = true),
            FieldInput.DoubleInput("unusable_fraction", "Unusable / missing fraction", "0", advanced = true)
        )
    )
)

object ProbabilityCapabilityScreen : StandardFieldStatsScreen(
    CalculatorUiSpec(
        As100ProbabilityMethod,
        "Quick probability",
        "Answer a small binomial, Poisson or normal probability question.",
        "This is deliberately a pocket calculator rather than a general statistical programming surface.",
        listOf(
            FieldInput.ChoiceInput(
                "distribution", "Distribution", "binomial",
                options = { listOf("binomial", "poisson", "normal") },
                labels = mapOf("binomial" to "Binomial", "poisson" to "Poisson", "normal" to "Normal")
            ),
            FieldInput.ChoiceInput(
                "query", "Question", "exact",
                options = { state -> when (state["distribution"] ?: "binomial") {
                    "normal" -> listOf("below", "above", "between", "quantile")
                    else -> listOf("exact", "at_most", "at_least")
                } },
                labels = mapOf(
                    "exact" to "Exactly k", "at_most" to "At most k", "at_least" to "At least k",
                    "below" to "At or below x", "above" to "At or above x", "between" to "Between two values", "quantile" to "Find a quantile"
                )
            ),
            FieldInput.IntInput("n", "Trials · n", "10") { it["distribution"] == "binomial" },
            FieldInput.DoubleInput("probability", "Event probability · p", "0.5") { it["distribution"] == "binomial" },
            FieldInput.DoubleInput("lambda", "Mean rate · λ", "1") { it["distribution"] == "poisson" },
            FieldInput.IntInput("k", "Count · k", "0") { it["distribution"] in setOf("binomial", "poisson") },
            FieldInput.DoubleInput("mean", "Mean", "0") { it["distribution"] == "normal" },
            FieldInput.DoubleInput("sd", "Standard deviation", "1") { it["distribution"] == "normal" },
            FieldInput.DoubleInput("x", "Value · x", "0") { it["distribution"] == "normal" && it["query"] in setOf("below", "above") },
            FieldInput.DoubleInput("lower", "Lower bound", "0") { it["distribution"] == "normal" && it["query"] == "between" },
            FieldInput.DoubleInput("upper", "Upper bound", "1") { it["distribution"] == "normal" && it["query"] == "between" },
            FieldInput.DoubleInput("quantile_probability", "Probability", "0.5") { it["distribution"] == "normal" && it["query"] == "quantile" }
        )
    )
)

@Composable
private fun StandardCalculatorScreen(
    spec: CalculatorUiSpec,
    context: CapabilityScreenContext,
    onBack: () -> Unit,
    onConfirmed: (ExecutionResult) -> Unit,
    onCancel: () -> Unit
) {
    fun initialState(): Map<String, String> = spec.fields.associate { field ->
        field.id to (context.action.settings[field.id] ?: context.action.settings["input_${field.id}"] ?: field.defaultValue)
    }

    var stateJson by rememberSaveable(context.action.canonicalId) { mutableStateOf(encodeMap(initialState())) }
    var valuesJson by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
    var launched by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
    var showAdvanced by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
    val state = decodeMap(stateJson)
    val values = decodeMap(valuesJson)

    fun update(id: String, value: String) {
        stateJson = encodeMap(decodeMap(stateJson) + (id to value))
    }

    fun mergedSettings(): Map<String, String> = context.action.settings + decodeMap(stateJson)

    fun requestFor(settings: Map<String, String>) = spec.method.request(
        action = spec.method.id,
        context = context.request.invocationContext.asMap(spec.method.id) + settings,
        signals = emptyList(),
        inputs = emptyList()
    )

    val result = if (values.isNotEmpty()) {
        val settings = mergedSettings()
        spec.method.result(requestFor(settings), values, context.request.invocationContext)
    } else null

    fun calculate() {
        val settings = mergedSettings()
        val calculated = runCatching { spec.method.calculate(settings) }
            .getOrElse { spec.method.failureValues(settings, it.message ?: "Calculation failed.") }
        valuesJson = encodeMap(calculated)
    }

    LaunchedEffect(stateJson) {
        context.onSettingsChanged(decodeMap(stateJson))
        if (valuesJson.isNotBlank()) calculate()
    }

    LaunchedEffect(context.presentationMode, context.completionMode, context.action.settings) {
        val genuineExternalAutoSubmit = context.submitsImmediately &&
            !context.request.source.equals("intent_test", ignoreCase = true)
        if (genuineExternalAutoSubmit && !launched) {
            launched = true
            calculate()
        }
    }

    val keepLive = context.presentationMode == CapabilityPresentationMode.Dashboard || context.isNativePresetRun
    val scaffoldResult = if (keepLive) null else result

    CapabilityScreenScaffold(
        title = spec.title,
        capabilityId = spec.method.id,
        context = context,
        canGoBack = context.stepNumber > 1,
        capturedResult = scaffoldResult,
        resultPreview = scaffoldResult?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty(),
        onBack = onBack,
        onRetry = { valuesJson = "" },
        onConfirm = { result?.let(onConfirmed) },
        onCancel = onCancel
    ) {
        IntroCard(spec.description, spec.guidance)
        Spacer(Modifier.height(12.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("Inputs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))

                val currentState = decodeMap(stateJson)
                when (spec.method.id) {
                    As100Diagnostic2x2Method.id -> DiagnosticMatrix(currentState, ::update, context)
                    As100CompareBinaryMethod.id -> BinaryGroupInputs(currentState, ::update, context)
                    else -> spec.fields.filter { !it.advanced }.forEach { field ->
                        RenderField(field, currentState, ::update, context)
                    }
                }

                val advancedFields = spec.fields.filter { it.advanced && it.visibleWhen(currentState) && context.settingShouldBeShown(it.id) }
                if (advancedFields.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    OutlinedButton(
                        onClick = { showAdvanced = !showAdvanced },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) { Text(if (showAdvanced) "Hide advanced settings" else "Advanced settings") }
                    AnimatedVisibility(showAdvanced) {
                        Column {
                            advancedFields.forEach { field -> RenderField(field, decodeMap(stateJson), ::update, context) }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Button(onClick = ::calculate, modifier = Modifier.fillMaxWidth()) {
            Text(if (values.isEmpty()) spec.buttonLabel else "Update result")
        }

        values[FieldStatsCommonFields.ERROR]?.takeIf { it.isNotBlank() }?.let {
            ErrorCard(it)
        }

        if (values.isNotEmpty() && values[FieldStatsCommonFields.ERROR].isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            ResultPanel(spec.method.id, values)
            if (keepLive && result != null) {
                Button(
                    onClick = { result.let(onConfirmed) },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                ) { Text(if (context.isNativePresetRun) "Finish" else "Use result") }
            }
        }
    }
}

@Composable
private fun IntroCard(description: String, guidance: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(description, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(guidance, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DiagnosticMatrix(
    state: Map<String, String>,
    update: (String, String) -> Unit,
    context: CapabilityScreenContext
) {
    Text("Reference standard", style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth()) {
        Spacer(Modifier.weight(0.9f))
        Text("Positive", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
        Text("Negative", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
    }
    MatrixRow("Test +", "tp", "fp", state, update, context)
    MatrixRow("Test −", "fn", "tn", state, update, context)
    Text("Enter observed counts, not percentages.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun MatrixRow(
    rowLabel: String,
    leftId: String,
    rightId: String,
    state: Map<String, String>,
    update: (String, String) -> Unit,
    context: CapabilityScreenContext
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(rowLabel, modifier = Modifier.weight(0.9f).padding(top = 18.dp), fontWeight = FontWeight.SemiBold)
        if (context.settingShouldBeShown(leftId)) {
            CompactIntegerField(state[leftId].orEmpty(), { update(leftId, it) }, Modifier.weight(1f))
        } else Spacer(Modifier.weight(1f))
        if (context.settingShouldBeShown(rightId)) {
            CompactIntegerField(state[rightId].orEmpty(), { update(rightId, it) }, Modifier.weight(1f))
        } else Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun BinaryGroupInputs(
    state: Map<String, String>,
    update: (String, String) -> Unit,
    context: CapabilityScreenContext
) {
    GroupInputCard("Group A", "events_a", "total_a", state, update, context)
    Spacer(Modifier.height(8.dp))
    GroupInputCard("Group B", "events_b", "total_b", state, update, context)
}

@Composable
private fun GroupInputCard(
    label: String,
    eventsId: String,
    totalId: String,
    state: Map<String, String>,
    update: (String, String) -> Unit,
    context: CapabilityScreenContext
) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small, tonalElevation = 1.dp) {
        Column(Modifier.padding(10.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (context.settingShouldBeShown(eventsId)) {
                    LabeledCompactIntegerField("Events", state[eventsId].orEmpty(), { update(eventsId, it) }, Modifier.weight(1f))
                }
                if (context.settingShouldBeShown(totalId)) {
                    LabeledCompactIntegerField("Total", state[totalId].orEmpty(), { update(totalId, it) }, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CompactIntegerField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.integerText()) },
        modifier = modifier,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

@Composable
private fun LabeledCompactIntegerField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.integerText()) },
        label = { Text(label) },
        modifier = modifier,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

@Composable
private fun RenderField(
    field: FieldInput,
    state: Map<String, String>,
    update: (String, String) -> Unit,
    context: CapabilityScreenContext
) {
    if (!field.visibleWhen(state) || !context.settingShouldBeShown(field.id)) return

    when (field) {
        is FieldInput.IntInput -> OutlinedTextField(
            value = state[field.id].orEmpty(),
            onValueChange = { update(field.id, it.integerText()) },
            label = { Text(field.label) },
            supportingText = field.helper?.let { helper -> { Text(helper) } },
            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        is FieldInput.DoubleInput -> OutlinedTextField(
            value = state[field.id].orEmpty(),
            onValueChange = { update(field.id, it.numericText()) },
            label = { Text(field.label) },
            supportingText = field.helper?.let { helper -> { Text(helper) } },
            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        is FieldInput.TextInput -> OutlinedTextField(
            value = state[field.id].orEmpty(),
            onValueChange = { update(field.id, it) },
            label = { Text(field.label) },
            supportingText = field.helper?.let { helper -> { Text(helper) } },
            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
            minLines = field.minLines
        )
        is FieldInput.BooleanInput -> Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(field.label, modifier = Modifier.weight(1f).padding(end = 8.dp))
                Switch(checked = state[field.id] == "true", onCheckedChange = { update(field.id, it.toString()) })
            }
            field.helper?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        is FieldInput.ChoiceInput -> FieldStatsChoiceDropdown(
            label = field.label,
            value = state[field.id].orEmpty(),
            options = field.options(state),
            labels = field.labels,
            helper = field.helper,
            onSelected = { selected ->
                var next = state + (field.id to selected)
                if (field.id == "distribution") {
                    val allowed = if (selected == "normal") setOf("below", "above", "between", "quantile") else setOf("exact", "at_most", "at_least")
                    if (next["query"] !in allowed) next = next + ("query" to allowed.first())
                }
                updateMap(next, update)
            }
        )
    }
}

private fun updateMap(next: Map<String, String>, update: (String, String) -> Unit) {
    next.forEach { (key, value) -> update(key, value) }
}

@Composable
private fun ResultPanel(methodId: String, values: Map<String, String>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text("Result", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            values[FieldStatsCommonFields.SUMMARY]?.takeIf { it.isNotBlank() }?.let {
                Text(it, modifier = Modifier.padding(top = 3.dp), style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(10.dp))
            when (methodId) {
                As100Diagnostic2x2Method.id -> {
                    MetricGrid(
                        listOf(
                            "Sensitivity" to pctText(values[DiagnosticFields.SENSITIVITY]),
                            "Specificity" to pctText(values[DiagnosticFields.SPECIFICITY]),
                            "PPV" to pctText(values[DiagnosticFields.PPV]),
                            "NPV" to pctText(values[DiagnosticFields.NPV])
                        )
                    )
                    DetailLine("LR+", values[DiagnosticFields.LR_POS])
                    DetailLine("LR−", values[DiagnosticFields.LR_NEG])
                    DetailLine("Accuracy", pctText(values[DiagnosticFields.ACCURACY]))
                    DetailLine("Prevalence", pctText(values[DiagnosticFields.PREVALENCE]))
                    values[DiagnosticFields.PREDICTIVE_PREVALENCE]?.takeIf { it.isNotBlank() }?.let {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Text("At prevalence ${pctText(it)}", fontWeight = FontWeight.SemiBold)
                        DetailLine("Projected PPV", pctText(values[DiagnosticFields.PREDICTIVE_PPV]))
                        DetailLine("Projected NPV", pctText(values[DiagnosticFields.PREDICTIVE_NPV]))
                    }
                }
                As100ProportionMethod.id -> {
                    HeroMetric(values[ProportionFields.PERCENT]?.let { "$it%" } ?: "—", "Observed proportion")
                    CiLine(values[ProportionFields.CI_LOW], values[ProportionFields.CI_HIGH], asPercent = true)
                    DetailLine("Count", "${values[ProportionFields.NUMERATOR].orEmpty()} / ${values[ProportionFields.DENOMINATOR].orEmpty()}")
                }
                As100SampleSizeProportionMethod.id -> {
                    HeroMetric(values[SampleSizeProportionFields.FINAL_N].orEmpty(), "Required sample")
                    StageLine("Base", values[SampleSizeProportionFields.BASE_N])
                    StageLine("After finite-population correction", values[SampleSizeProportionFields.FPC_N])
                    StageLine("After design effect", values[SampleSizeProportionFields.DESIGN_N])
                }
                As100DetectionLimitMethod.id -> {
                    if (values[DetectionFields.MODE] == "zero_events") {
                        HeroMetric(pctText(values[DetectionFields.UPPER_PREVALENCE]), "Upper prevalence bound")
                        DetailLine("Rule-of-three approximation", pctText(values[DetectionFields.RULE_OF_THREE]))
                    } else {
                        HeroMetric(values[DetectionFields.REQUIRED_N].orEmpty(), "Required sample")
                        DetailLine("Detection probability", pctText(values[DetectionFields.DETECTION_PROBABILITY]))
                    }
                }
                As100CompareBinaryMethod.id -> {
                    MetricGrid(listOf("Risk A" to pctText(values[CompareBinaryFields.RISK_A]), "Risk B" to pctText(values[CompareBinaryFields.RISK_B])))
                    DetailLine("Risk difference", pctPointText(values[CompareBinaryFields.RD]))
                    DetailLine("Risk ratio", values[CompareBinaryFields.RR])
                    DetailLine("Odds ratio", values[CompareBinaryFields.OR])
                    values[CompareBinaryFields.NNT_INTERPRETATION]?.takeIf { it.isNotBlank() }?.let { DetailLine(it, values[CompareBinaryFields.NNT]) }
                }
                As100SummaryMethod.id -> {
                    MetricGrid(listOf("n" to values[SummaryFields.N].orEmpty(), "Mean" to values[SummaryFields.MEAN].orEmpty(), "Median" to values[SummaryFields.MEDIAN].orEmpty(), "SD" to values[SummaryFields.SD].orEmpty()))
                    DetailLine("IQR", "${values[SummaryFields.Q1].orEmpty()} – ${values[SummaryFields.Q3].orEmpty()}")
                    DetailLine("Range", "${values[SummaryFields.MIN].orEmpty()} – ${values[SummaryFields.MAX].orEmpty()}")
                    values[SummaryFields.INVALID]?.takeIf { it != "0" && it.isNotBlank() }?.let { DetailLine("Ignored tokens", it) }
                }
                As100SampleSizeTwoProportionsMethod.id -> {
                    HeroMetric(values[TwoProportionSampleSizeFields.TOTAL].orEmpty(), "Total required")
                    MetricGrid(listOf("Group A" to values[TwoProportionSampleSizeFields.GROUP_A].orEmpty(), "Group B" to values[TwoProportionSampleSizeFields.GROUP_B].orEmpty()))
                    DetailLine("Before inflation", values[TwoProportionSampleSizeFields.UNINFLATED_TOTAL])
                }
                As100SampleSizeDiagnosticMethod.id -> {
                    HeroMetric(values[DiagnosticSampleSizeFields.FINAL].orEmpty(), "Total recruitment")
                    MetricGrid(listOf("Disease + needed" to values[DiagnosticSampleSizeFields.POSITIVE].orEmpty(), "Disease − needed" to values[DiagnosticSampleSizeFields.NEGATIVE].orEmpty()))
                    DetailLine("Before unusable/missing inflation", values[DiagnosticSampleSizeFields.BEFORE_UNUSABLE])
                }
                As100ProbabilityMethod.id -> {
                    HeroMetric(values[ProbabilityFields.VALUE].orEmpty(), values[ProbabilityFields.LABEL].orEmpty().ifBlank { "Probability" })
                }
                else -> HeroMetric(values[FieldStatsCommonFields.MAIN_VALUE].orEmpty(), "Result")
            }
        }
    }
}

@Composable
private fun MetricGrid(items: List<Pair<String, String>>) {
    items.chunked(2).forEachIndexed { index, rowItems ->
        if (index > 0) Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            rowItems.forEach { (label, value) -> MetricCard(label, value.ifBlank { "—" }, Modifier.weight(1f)) }
            if (rowItems.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun HeroMetric(value: String, label: String) {
    Text(value.ifBlank { "—" }, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun DetailLine(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StageLine(label: String, value: String?) = DetailLine(label, value)

@Composable
private fun CiLine(low: String?, high: String?, asPercent: Boolean = false) {
    if (low.isNullOrBlank() || high.isNullOrBlank()) return
    val shownLow = if (asPercent) pctText(low) else low
    val shownHigh = if (asPercent) pctText(high) else high
    DetailLine("Confidence interval", "$shownLow – $shownHigh")
}

@Composable
private fun ErrorCard(message: String) {
    Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text("Check the inputs", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

object RocCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100RocMethod.id
    override val title = "ROC threshold explorer"
    override val description = "Explore discrimination and choose a useful threshold."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        var dataText by rememberSaveable(context.action.canonicalId) {
            mutableStateOf(context.action.settings["roc_data"] ?: context.action.settings["input_roc_data"] ?: "")
        }
        var thresholdText by rememberSaveable(context.action.canonicalId) {
            mutableStateOf(context.action.settings["threshold"] ?: context.action.settings["input_threshold"] ?: "0.5")
        }
        var valuesJson by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
        var launched by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        val values = decodeMap(valuesJson)

        fun settings(): Map<String, String> = context.action.settings + mapOf("roc_data" to dataText, "threshold" to thresholdText)
        fun requestFor(activeSettings: Map<String, String>) = As100RocMethod.request(
            action = As100RocMethod.id,
            context = context.request.invocationContext.asMap(As100RocMethod.id) + activeSettings,
            signals = emptyList(),
            inputs = emptyList()
        )
        fun calculate(threshold: Double? = null) {
            if (threshold != null) thresholdText = fmt(threshold, 8)
            val active = context.action.settings + mapOf("roc_data" to dataText, "threshold" to (threshold?.let { fmt(it, 8) } ?: thresholdText))
            val calculated = runCatching { As100RocMethod.calculate(active) }
                .getOrElse { As100RocMethod.failureValues(active, it.message ?: "ROC calculation failed.") }
            valuesJson = encodeMap(calculated)
        }

        LaunchedEffect(dataText, thresholdText) {
            context.onSettingsChanged(mapOf("roc_data" to dataText, "threshold" to thresholdText))
        }

        LaunchedEffect(context.presentationMode, context.completionMode, context.action.settings) {
            val genuineExternalAutoSubmit = context.submitsImmediately && !context.request.source.equals("intent_test", ignoreCase = true)
            if (genuineExternalAutoSubmit && !launched) {
                launched = true
                calculate()
            }
        }

        val activeSettings = settings()
        val result = if (values.isNotEmpty()) As100RocMethod.result(requestFor(activeSettings), values, context.request.invocationContext) else null
        val keepLiveDashboard = context.presentationMode == CapabilityPresentationMode.Dashboard || context.isNativePresetRun
        val scaffoldResult = if (keepLiveDashboard) null else result
        val parsed = remember(dataText) { FieldStatsEngine.parseRocData(dataText) }
        val threshold = thresholdText.toDoubleOrNull() ?: 0.5
        val roc = remember(dataText, thresholdText, valuesJson) {
            runCatching { if (parsed.rows.isNotEmpty()) FieldStatsEngine.roc(parsed.rows, threshold) else null }.getOrNull()
        }

        CapabilityScreenScaffold(
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = scaffoldResult,
            resultPreview = scaffoldResult?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty(),
            onBack = onBack,
            onRetry = { valuesJson = "" },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            IntroCard(
                "Choose a threshold visually, then commit the snapshot you want to keep.",
                "Paste truth and score as two columns. Truth accepts 1/0, true/false, yes/no or positive/negative. Scores at or above the threshold are classified positive."
            )
            Spacer(Modifier.height(12.dp))
            if (context.settingShouldBeShown("roc_data")) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Data", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = dataText,
                            onValueChange = { dataText = it; valuesJson = "" },
                            label = { Text("truth, score") },
                            supportingText = { Text("One observation per line; comma, tab or whitespace separated.") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 7
                        )
                    }
                }
            }
            if (valuesJson.isBlank()) {
                Spacer(Modifier.height(10.dp))
                Button(onClick = { calculate() }, modifier = Modifier.fillMaxWidth()) { Text("Explore ROC") }
            }

            roc?.let { r ->
                Spacer(Modifier.height(12.dp))
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Selected threshold", style = MaterialTheme.typography.labelMedium)
                                Text(fmt(r.threshold), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text("AUC", style = MaterialTheme.typography.labelMedium)
                                Text(fmt(r.auc, 3), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (r.maxScore > r.minScore) {
                            Slider(
                                value = r.threshold.toFloat().coerceIn(r.minScore.toFloat(), r.maxScore.toFloat()),
                                onValueChange = { calculate(it.toDouble()) },
                                valueRange = r.minScore.toFloat()..r.maxScore.toFloat(),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        OutlinedTextField(
                            value = thresholdText,
                            onValueChange = { thresholdText = it.numericText(); it.toDoubleOrNull()?.let(::calculate) },
                            label = { Text("Threshold") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                MetricGrid(
                    listOf(
                        "Sensitivity" to pct(r.sensitivity),
                        "Specificity" to pct(r.specificity),
                        "PPV" to (r.ppv?.let(::pct) ?: "—"),
                        "NPV" to (r.npv?.let(::pct) ?: "—")
                    )
                )
                Spacer(Modifier.height(10.dp))
                RocPlot(r.points)
                Spacer(Modifier.height(8.dp))
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        DetailLine("Youden J", fmt(r.youdenJ, 3))
                        DetailLine("Confusion matrix", "TP ${r.tp} · FP ${r.fp} · FN ${r.fn} · TN ${r.tn}")
                        DetailLine("Youden-optimal threshold", fmt(r.optimalThreshold))
                        if (parsed.invalidCount > 0) DetailLine("Ignored rows", parsed.invalidCount.toString())
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { calculate(r.optimalThreshold) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Jump to Youden-optimal threshold")
                }
                if (keepLiveDashboard && result != null) {
                    Button(onClick = { result.let(onConfirmed) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text(if (context.isNativePresetRun) "Finish" else "Use this snapshot")
                    }
                }
            }
            values[FieldStatsCommonFields.ERROR]?.takeIf { it.isNotBlank() }?.let { ErrorCard(it) }
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.medium, tonalElevation = 2.dp) {
        Column(Modifier.padding(11.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RocPlot(points: List<FieldStatsEngine.RocPoint>) {
    val lineColor = MaterialTheme.colorScheme.primary
    val guideColor = MaterialTheme.colorScheme.outlineVariant
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("ROC curve", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text("Closer to the upper-left corner means better discrimination.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Canvas(Modifier.fillMaxWidth().height(190.dp).padding(top = 10.dp)) {
                drawLine(guideColor, Offset(0f, size.height), Offset(size.width, 0f), strokeWidth = 2f)
                if (points.size >= 2) {
                    points.zipWithNext().forEach { (a, b) ->
                        drawLine(
                            color = lineColor,
                            start = Offset((a.falsePositiveRate * size.width).toFloat(), ((1.0 - a.truePositiveRate) * size.height).toFloat()),
                            end = Offset((b.falsePositiveRate * size.width).toFloat(), ((1.0 - b.truePositiveRate) * size.height).toFloat()),
                            strokeWidth = 4f
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("0% false-positive", style = MaterialTheme.typography.labelSmall)
                Text("100% false-positive", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun FieldStatsChoiceDropdown(
    label: String,
    value: String,
    options: List<String>,
    labels: Map<String, String>,
    helper: String? = null,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val shown = labels[value] ?: value.ifBlank { "Choose…" }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box(Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(shown) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.distinct().forEach { option ->
                    DropdownMenuItem(
                        text = { Text(labels[option] ?: option) },
                        onClick = { expanded = false; onSelected(option) }
                    )
                }
            }
        }
        helper?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

private fun pctText(value: String?): String {
    val number = value?.toDoubleOrNull() ?: return value.orEmpty().ifBlank { "—" }
    return "${fmt(number * 100.0, 1)}%"
}

private fun pctPointText(value: String?): String {
    val number = value?.toDoubleOrNull() ?: return value.orEmpty().ifBlank { "—" }
    return "${fmt(number * 100.0, 1)} pp"
}

private fun encodeMap(values: Map<String, String>): String = JSONObject(values).toString()

private fun decodeMap(json: String): Map<String, String> {
    if (json.isBlank()) return emptyMap()
    return runCatching {
        val obj = JSONObject(json)
        obj.keys().asSequence().associateWith { key -> obj.optString(key, "") }
    }.getOrElse { emptyMap() }
}

private fun String.integerText(): String = filter(Char::isDigit)

private fun String.numericText(): String =
    filter { it.isDigit() || it == '-' || it == '.' || it == 'e' || it == 'E' || it == '+' }
