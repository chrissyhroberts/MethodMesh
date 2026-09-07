package com.example.methodmesh.modules.fieldstats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Slider
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
    val visibleWhen: (Map<String, String>) -> Boolean = { true }
) {
    class IntInput(id: String, label: String, defaultValue: String, visibleWhen: (Map<String, String>) -> Boolean = { true }) : FieldInput(id, label, defaultValue, visibleWhen)
    class DoubleInput(id: String, label: String, defaultValue: String, visibleWhen: (Map<String, String>) -> Boolean = { true }) : FieldInput(id, label, defaultValue, visibleWhen)
    class TextInput(id: String, label: String, defaultValue: String = "", val minLines: Int = 4, visibleWhen: (Map<String, String>) -> Boolean = { true }) : FieldInput(id, label, defaultValue, visibleWhen)
    class BooleanInput(id: String, label: String, defaultValue: String = "false", visibleWhen: (Map<String, String>) -> Boolean = { true }) : FieldInput(id, label, defaultValue, visibleWhen)
    class ChoiceInput(
        id: String,
        label: String,
        defaultValue: String,
        val options: (Map<String, String>) -> List<String>,
        val labels: Map<String, String> = emptyMap(),
        visibleWhen: (Map<String, String>) -> Boolean = { true }
    ) : FieldInput(id, label, defaultValue, visibleWhen)
}

data class CalculatorUiSpec(
    val method: FieldStatsMethod,
    val title: String,
    val description: String,
    val guidance: String,
    val fields: List<FieldInput>,
    val buttonLabel: String = "Calculate"
)

abstract class StandardFieldStatsScreen(
    private val spec: CalculatorUiSpec
) : CapabilityScreenSpec {
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
        "Diagnostic 2×2 table",
        "Sensitivity, specificity, PPV, NPV, likelihood ratios and accuracy.",
        "Enter the four cells. Proportion confidence intervals use Wilson score intervals. If a cell is zero, ratio estimates use a documented 0.5 continuity correction.",
        listOf(
            FieldInput.IntInput("tp", "True positives", "0"),
            FieldInput.IntInput("fp", "False positives", "0"),
            FieldInput.IntInput("fn", "False negatives", "0"),
            FieldInput.IntInput("tn", "True negatives", "0"),
            FieldInput.DoubleInput("confidence", "Confidence level (0–1)", "0.95"),
            FieldInput.BooleanInput("use_prevalence_override", "Also calculate predictive values at another prevalence"),
            FieldInput.DoubleInput("prevalence_override", "Alternative prevalence (0–1)", "0.05") { it["use_prevalence_override"] == "true" }
        )
    )
)

object ProportionCapabilityScreen : StandardFieldStatsScreen(
    CalculatorUiSpec(
        As100ProportionMethod,
        "Proportion + CI",
        "Calculate a proportion with a Wilson confidence interval.",
        "Useful for prevalence, attack rate, positivity, coverage and other x/n quantities.",
        listOf(
            FieldInput.IntInput("numerator", "Numerator", "0"),
            FieldInput.IntInput("denominator", "Denominator", "100"),
            FieldInput.DoubleInput("confidence", "Confidence level (0–1)", "0.95")
        )
    )
)

object SampleSizeProportionCapabilityScreen : StandardFieldStatsScreen(
    CalculatorUiSpec(
        As100SampleSizeProportionMethod,
        "Sample size for a proportion",
        "Plan a prevalence, coverage or single-proportion survey.",
        "Population size 0 means no finite-population correction. Design effect and non-response inflation are shown as separate stages.",
        listOf(
            FieldInput.DoubleInput("expected_proportion", "Expected proportion (0–1)", "0.5"),
            FieldInput.DoubleInput("absolute_precision", "Absolute precision (0–1)", "0.05"),
            FieldInput.DoubleInput("confidence", "Confidence level (0–1)", "0.95"),
            FieldInput.IntInput("population_size", "Finite population size · 0 = not used", "0"),
            FieldInput.DoubleInput("design_effect", "Design effect", "1"),
            FieldInput.DoubleInput("nonresponse_fraction", "Expected non-response (0–1)", "0")
        )
    )
)

object DetectionLimitCapabilityScreen : StandardFieldStatsScreen(
    CalculatorUiSpec(
        As100DetectionLimitMethod,
        "Detection / zero events",
        "Plan to detect at least one event or interpret a zero-event sample.",
        "With a finite population, calculations use sampling without replacement. Otherwise the usual independent/binomial formulation is used.",
        listOf(
            FieldInput.ChoiceInput(
                "mode", "Question", "plan_detection",
                options = { listOf("plan_detection", "zero_events") },
                labels = mapOf("plan_detection" to "How many to detect ≥1?", "zero_events" to "We observed zero events")
            ),
            FieldInput.DoubleInput("prevalence", "Minimum prevalence to detect (0–1)", "0.01") { it["mode"] != "zero_events" },
            FieldInput.IntInput("sample_size", "Sample size with zero events", "100") { it["mode"] == "zero_events" },
            FieldInput.DoubleInput("confidence", "Confidence / detection probability (0–1)", "0.95"),
            FieldInput.IntInput("population_size", "Finite population size · 0 = not used", "0")
        )
    )
)

object CompareBinaryCapabilityScreen : StandardFieldStatsScreen(
    CalculatorUiSpec(
        As100CompareBinaryMethod,
        "Compare two binary groups",
        "Risk difference, risk ratio, odds ratio and NNT/NNH.",
        "Group A is compared against Group B. Risk-difference confidence limits use the Newcombe hybrid-score method.",
        listOf(
            FieldInput.IntInput("events_a", "Group A events", "0"),
            FieldInput.IntInput("total_a", "Group A total", "100"),
            FieldInput.IntInput("events_b", "Group B events", "0"),
            FieldInput.IntInput("total_b", "Group B total", "100"),
            FieldInput.DoubleInput("confidence", "Confidence level (0–1)", "0.95")
        )
    )
)

object SummaryCapabilityScreen : StandardFieldStatsScreen(
    CalculatorUiSpec(
        As100SummaryMethod,
        "Quick descriptive statistics",
        "Paste numbers and get a compact descriptive summary.",
        "Separate values with new lines, spaces, commas, semicolons or pipes. Non-numeric tokens are counted rather than silently becoming zero.",
        listOf(FieldInput.TextInput("values_text", "Numbers", minLines = 8)),
        buttonLabel = "Summarise"
    )
)

object SampleSizeTwoProportionsCapabilityScreen : StandardFieldStatsScreen(
    CalculatorUiSpec(
        As100SampleSizeTwoProportionsMethod,
        "Sample size: two proportions",
        "Approximate two-sided sample size for two independent proportions.",
        "This is a pocket planning calculation, not a replacement for a design-specific trial power analysis. Allocation ratio is B:A.",
        listOf(
            FieldInput.DoubleInput("proportion_a", "Expected proportion A (0–1)", "0.10"),
            FieldInput.DoubleInput("proportion_b", "Expected proportion B (0–1)", "0.15"),
            FieldInput.DoubleInput("confidence", "Confidence level (0–1)", "0.95"),
            FieldInput.DoubleInput("power", "Power (0–1)", "0.80"),
            FieldInput.DoubleInput("allocation_ratio_b_to_a", "Allocation ratio B:A", "1"),
            FieldInput.DoubleInput("design_effect", "Design effect", "1"),
            FieldInput.DoubleInput("attrition_fraction", "Loss / attrition (0–1)", "0")
        )
    )
)

object SampleSizeDiagnosticCapabilityScreen : StandardFieldStatsScreen(
    CalculatorUiSpec(
        As100SampleSizeDiagnosticMethod,
        "Diagnostic study sample size",
        "Plan recruitment for sensitivity and specificity precision.",
        "The method separately determines the required disease-positive and disease-negative observations, then uses expected prevalence to estimate total recruitment.",
        listOf(
            FieldInput.DoubleInput("expected_sensitivity", "Expected sensitivity (0–1)", "0.90"),
            FieldInput.DoubleInput("expected_specificity", "Expected specificity (0–1)", "0.95"),
            FieldInput.DoubleInput("sensitivity_precision", "Sensitivity absolute precision", "0.05"),
            FieldInput.DoubleInput("specificity_precision", "Specificity absolute precision", "0.03"),
            FieldInput.DoubleInput("prevalence", "Expected prevalence (0–1)", "0.10"),
            FieldInput.DoubleInput("confidence", "Confidence level (0–1)", "0.95"),
            FieldInput.DoubleInput("unusable_fraction", "Unusable / missing fraction (0–1)", "0")
        )
    )
)

object ProbabilityCapabilityScreen : StandardFieldStatsScreen(
    CalculatorUiSpec(
        As100ProbabilityMethod,
        "Quick distributions",
        "Pocket binomial, Poisson and normal probability calculations.",
        "Choose a distribution and a simple probability question. This intentionally avoids becoming a general statistical programming surface.",
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
                    "below" to "Below / equal x", "above" to "Above / equal x", "between" to "Between bounds", "quantile" to "Find a quantile"
                )
            ),
            FieldInput.IntInput("n", "Binomial n", "10") { it["distribution"] == "binomial" },
            FieldInput.DoubleInput("probability", "Binomial p (0–1)", "0.5") { it["distribution"] == "binomial" },
            FieldInput.DoubleInput("lambda", "Poisson mean λ", "1") { it["distribution"] == "poisson" },
            FieldInput.IntInput("k", "Count k", "0") { it["distribution"] in setOf("binomial", "poisson") },
            FieldInput.DoubleInput("mean", "Normal mean", "0") { it["distribution"] == "normal" },
            FieldInput.DoubleInput("sd", "Normal SD", "1") { it["distribution"] == "normal" },
            FieldInput.DoubleInput("x", "Value x", "0") { it["distribution"] == "normal" && it["query"] in setOf("below", "above") },
            FieldInput.DoubleInput("lower", "Lower bound", "0") { it["distribution"] == "normal" && it["query"] == "between" },
            FieldInput.DoubleInput("upper", "Upper bound", "1") { it["distribution"] == "normal" && it["query"] == "between" },
            FieldInput.DoubleInput("quantile_probability", "Quantile probability (0–1)", "0.5") { it["distribution"] == "normal" && it["query"] == "quantile" }
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
    val state = decodeMap(stateJson)
    val values = decodeMap(valuesJson)

    fun update(id: String, value: String) {
        stateJson = encodeMap(state + (id to value))
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
    }

    LaunchedEffect(context.presentationMode, context.completionMode, context.action.settings) {
        val genuineExternalAutoSubmit = context.submitsImmediately &&
            !context.request.source.equals("intent_test", ignoreCase = true)
        if (genuineExternalAutoSubmit && !launched) {
            launched = true
            calculate()
        }
    }

    CapabilityScreenScaffold(
        title = spec.title,
        capabilityId = spec.method.id,
        context = context,
        canGoBack = context.stepNumber > 1,
        capturedResult = result,
        resultPreview = result?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty(),
        onBack = onBack,
        onRetry = { valuesJson = "" },
        onConfirm = { result?.let(onConfirmed) },
        onCancel = onCancel
    ) {
        Text(spec.guidance, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(10.dp))
        spec.fields.forEach { field ->
            val currentState = decodeMap(stateJson)
            if (field.visibleWhen(currentState) && context.settingShouldBeShown(field.id)) {
                when (field) {
                    is FieldInput.IntInput -> OutlinedTextField(
                        value = currentState[field.id].orEmpty(),
                        onValueChange = { update(field.id, it.integerText()) },
                        label = { Text(field.label) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        singleLine = true
                    )
                    is FieldInput.DoubleInput -> OutlinedTextField(
                        value = currentState[field.id].orEmpty(),
                        onValueChange = { update(field.id, it.numericText()) },
                        label = { Text(field.label) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        singleLine = true
                    )
                    is FieldInput.TextInput -> OutlinedTextField(
                        value = currentState[field.id].orEmpty(),
                        onValueChange = { update(field.id, it) },
                        label = { Text(field.label) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        minLines = field.minLines
                    )
                    is FieldInput.BooleanInput -> Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(field.label, modifier = Modifier.weight(1f).padding(end = 8.dp))
                        Switch(
                            checked = currentState[field.id] == "true",
                            onCheckedChange = { update(field.id, it.toString()) }
                        )
                    }
                    is FieldInput.ChoiceInput -> FieldStatsChoiceDropdown(
                        label = field.label,
                        value = currentState[field.id].orEmpty(),
                        options = field.options(currentState),
                        labels = field.labels,
                        onSelected = { selected ->
                            var next = currentState + (field.id to selected)
                            if (field.id == "distribution") {
                                val allowed = if (selected == "normal") setOf("below", "above", "between", "quantile") else setOf("exact", "at_most", "at_least")
                                if (next["query"] !in allowed) next = next + ("query" to allowed.first())
                            }
                            stateJson = encodeMap(next)
                        }
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = ::calculate, modifier = Modifier.fillMaxWidth()) { Text(spec.buttonLabel) }
        values[FieldStatsCommonFields.ERROR]?.takeIf { it.isNotBlank() }?.let {
            Text(it, modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.error)
        }
    }
}

object RocCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100RocMethod.id
    override val title = "ROC threshold explorer"
    override val description = "Explore AUC and the sensitivity/specificity trade-off at a selected threshold."

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
            val genuineExternalAutoSubmit = context.submitsImmediately &&
                !context.request.source.equals("intent_test", ignoreCase = true)
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
            Text("Paste two columns: truth and score. Truth accepts 1/0, true/false, yes/no or positive/negative. Scores at or above the threshold are classified positive.")
            Spacer(Modifier.height(8.dp))
            if (context.settingShouldBeShown("roc_data")) {
                OutlinedTextField(
                    value = dataText,
                    onValueChange = { dataText = it; valuesJson = "" },
                    label = { Text("truth,score data") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 7
                )
            }
            if (valuesJson.isBlank()) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = { calculate() }, modifier = Modifier.fillMaxWidth()) { Text("Load / calculate ROC") }
            }

            roc?.let { r ->
                Spacer(Modifier.height(12.dp))
                Text("Threshold ${fmt(r.threshold)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricCard("AUC", fmt(r.auc, 3), Modifier.weight(1f))
                    MetricCard("Sensitivity", pct(r.sensitivity), Modifier.weight(1f))
                    MetricCard("Specificity", pct(r.specificity), Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricCard("PPV", r.ppv?.let(::pct) ?: "—", Modifier.weight(1f))
                    MetricCard("NPV", r.npv?.let(::pct) ?: "—", Modifier.weight(1f))
                    MetricCard("Youden J", fmt(r.youdenJ, 3), Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                RocPlot(r.points)
                Spacer(Modifier.height(6.dp))
                Text(
                    "TP ${r.tp} · FP ${r.fp} · FN ${r.fn} · TN ${r.tn} · Youden-optimal threshold ${fmt(r.optimalThreshold)} (J ${fmt(r.optimalYoudenJ, 3)})",
                    style = MaterialTheme.typography.bodySmall
                )
                if (parsed.invalidCount > 0) Text("Ignored ${parsed.invalidCount} invalid row(s).", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { calculate(r.optimalThreshold) }, modifier = Modifier.fillMaxWidth()) { Text("Use Youden-optimal threshold") }
                if (keepLiveDashboard && result != null) {
                    Button(
                        onClick = { result.let(onConfirmed) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) { Text(if (context.isNativePresetRun) "Finish" else "Use this snapshot") }
                }
            }
            values[FieldStatsCommonFields.ERROR]?.takeIf { it.isNotBlank() }?.let {
                Text(it, modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RocPlot(points: List<FieldStatsEngine.RocPoint>) {
    val lineColor = MaterialTheme.colorScheme.primary
    val guideColor = MaterialTheme.colorScheme.outlineVariant
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            Text("ROC curve", style = MaterialTheme.typography.labelMedium)
            Canvas(Modifier.fillMaxWidth().height(180.dp).padding(8.dp)) {
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
            Text("False-positive rate →   ·   true-positive rate ↑", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun FieldStatsChoiceDropdown(
    label: String,
    value: String,
    options: List<String>,
    labels: Map<String, String>,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val shown = labels[value] ?: value.ifBlank { "Choose…" }
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
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
    }
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
