package com.example.methodmesh.modules.fieldstats

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.ModuleExample
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object FieldStatsModule : MethodMeshModule {
    override val moduleId = "fieldstats"
    override val displayName = "Field Statistics"
    override val summary = "Pocket statistical calculators for field epidemiology, diagnostics, surveys and quick quantitative checks."
    override val iconKey = "statistics"

    override fun as100Methods() = listOf(
        As100Diagnostic2x2Method,
        As100ProportionMethod,
        As100SampleSizeProportionMethod,
        As100DetectionLimitMethod,
        As100CompareBinaryMethod,
        As100SummaryMethod,
        As100SampleSizeTwoProportionsMethod,
        As100SampleSizeDiagnosticMethod,
        As100RocMethod,
        As100ProbabilityMethod
    )

    override fun capabilityScreens() = listOf(
        Diagnostic2x2CapabilityScreen,
        ProportionCapabilityScreen,
        SampleSizeProportionCapabilityScreen,
        DetectionLimitCapabilityScreen,
        CompareBinaryCapabilityScreen,
        SummaryCapabilityScreen,
        SampleSizeTwoProportionsCapabilityScreen,
        SampleSizeDiagnosticCapabilityScreen,
        RocCapabilityScreen,
        ProbabilityCapabilityScreen
    )

    override fun rilBindings() = listOf(
        RilBinding("diagnostic 2 by 2", As100Diagnostic2x2Method.id, "Calculate sensitivity, specificity, predictive values and likelihood ratios"),
        RilBinding("calculate proportion", As100ProportionMethod.id, "Calculate a proportion and Wilson confidence interval"),
        RilBinding("sample size for proportion", As100SampleSizeProportionMethod.id, "Plan a prevalence or coverage sample size"),
        RilBinding("detection limit", As100DetectionLimitMethod.id, "Plan detection of at least one event or bound prevalence after zero events"),
        RilBinding("compare binary groups", As100CompareBinaryMethod.id, "Calculate RD, RR, OR and NNT/NNH"),
        RilBinding("summarise numbers", As100SummaryMethod.id, "Calculate quick descriptive statistics"),
        RilBinding("sample size two proportions", As100SampleSizeTwoProportionsMethod.id, "Plan a two-group comparison of proportions"),
        RilBinding("diagnostic sample size", As100SampleSizeDiagnosticMethod.id, "Plan recruitment for sensitivity and specificity precision"),
        RilBinding("roc threshold", As100RocMethod.id, "Explore ROC AUC and threshold performance"),
        RilBinding("probability distribution", As100ProbabilityMethod.id, "Calculate binomial, Poisson or normal probabilities")
    )

    override fun capabilitySettings() = mapOf(
        As100Diagnostic2x2Method.id to listOf(
            MethodSetting.IntSetting("tp", "True positives", defaultValue = 0, minimum = 0, maximum = 100000000),
            MethodSetting.IntSetting("fp", "False positives", defaultValue = 0, minimum = 0, maximum = 100000000),
            MethodSetting.IntSetting("fn", "False negatives", defaultValue = 0, minimum = 0, maximum = 100000000),
            MethodSetting.IntSetting("tn", "True negatives", defaultValue = 0, minimum = 0, maximum = 100000000),
            fractionSetting("confidence", "Confidence level", 0.95f),
            MethodSetting.BooleanSetting("use_prevalence_override", "Calculate PPV/NPV at another prevalence", defaultValue = false),
            fractionSetting("prevalence_override", "Alternative prevalence", 0.05f)
        ),
        As100ProportionMethod.id to listOf(
            MethodSetting.IntSetting("numerator", "Numerator", defaultValue = 0, minimum = 0, maximum = 100000000),
            MethodSetting.IntSetting("denominator", "Denominator", defaultValue = 100, minimum = 1, maximum = 100000000),
            fractionSetting("confidence", "Confidence level", 0.95f)
        ),
        As100SampleSizeProportionMethod.id to listOf(
            fractionSetting("expected_proportion", "Expected proportion", 0.5f),
            fractionSetting("absolute_precision", "Absolute precision", 0.05f, minimum = 0.0001f),
            fractionSetting("confidence", "Confidence level", 0.95f),
            MethodSetting.IntSetting("population_size", "Finite population size (0 = not used)", defaultValue = 0, minimum = 0, maximum = 100000000),
            MethodSetting.FloatSetting("design_effect", "Design effect", defaultValue = 1f, minimum = 1f, maximum = 100f, step = 0.1f, decimals = 2),
            fractionSetting("nonresponse_fraction", "Expected non-response", 0f)
        ),
        As100DetectionLimitMethod.id to listOf(
            MethodSetting.ChoiceSetting("mode", "Mode", defaultValue = "plan_detection", choices = listOf("plan_detection", "zero_events")),
            fractionSetting("prevalence", "Minimum prevalence to detect", 0.01f, minimum = 0.000001f),
            MethodSetting.IntSetting("sample_size", "Sample size with zero events", defaultValue = 100, minimum = 1, maximum = 100000000),
            fractionSetting("confidence", "Confidence / detection probability", 0.95f),
            MethodSetting.IntSetting("population_size", "Finite population size (0 = not used)", defaultValue = 0, minimum = 0, maximum = 100000000)
        ),
        As100CompareBinaryMethod.id to listOf(
            MethodSetting.IntSetting("events_a", "Group A events", defaultValue = 0, minimum = 0, maximum = 100000000),
            MethodSetting.IntSetting("total_a", "Group A total", defaultValue = 100, minimum = 1, maximum = 100000000),
            MethodSetting.IntSetting("events_b", "Group B events", defaultValue = 0, minimum = 0, maximum = 100000000),
            MethodSetting.IntSetting("total_b", "Group B total", defaultValue = 100, minimum = 1, maximum = 100000000),
            fractionSetting("confidence", "Confidence level", 0.95f)
        ),
        As100SummaryMethod.id to listOf(
            MethodSetting.TextSetting("values_text", "Numbers", defaultValue = "")
        ),
        As100SampleSizeTwoProportionsMethod.id to listOf(
            fractionSetting("proportion_a", "Expected proportion A", 0.10f),
            fractionSetting("proportion_b", "Expected proportion B", 0.15f),
            fractionSetting("confidence", "Confidence level", 0.95f),
            fractionSetting("power", "Power", 0.80f, minimum = 0.5001f),
            MethodSetting.FloatSetting("allocation_ratio_b_to_a", "Allocation ratio B:A", defaultValue = 1f, minimum = 0.01f, maximum = 100f, step = 0.1f, decimals = 2),
            MethodSetting.FloatSetting("design_effect", "Design effect", defaultValue = 1f, minimum = 1f, maximum = 100f, step = 0.1f, decimals = 2),
            fractionSetting("attrition_fraction", "Loss / attrition", 0f)
        ),
        As100SampleSizeDiagnosticMethod.id to listOf(
            fractionSetting("expected_sensitivity", "Expected sensitivity", 0.90f),
            fractionSetting("expected_specificity", "Expected specificity", 0.95f),
            fractionSetting("sensitivity_precision", "Sensitivity absolute precision", 0.05f, minimum = 0.0001f),
            fractionSetting("specificity_precision", "Specificity absolute precision", 0.03f, minimum = 0.0001f),
            fractionSetting("prevalence", "Expected prevalence", 0.10f, minimum = 0.0001f, maximum = 0.9999f),
            fractionSetting("confidence", "Confidence level", 0.95f),
            fractionSetting("unusable_fraction", "Unusable / missing fraction", 0f)
        ),
        As100RocMethod.id to listOf(
            MethodSetting.TextSetting("roc_data", "Truth and score data", defaultValue = ""),
            MethodSetting.FloatSetting("threshold", "Positive threshold", defaultValue = 0.5f, step = 0.01f, decimals = 4)
        ),
        As100ProbabilityMethod.id to listOf(
            MethodSetting.ChoiceSetting("distribution", "Distribution", defaultValue = "binomial", choices = listOf("binomial", "poisson", "normal")),
            MethodSetting.ChoiceSetting("query", "Probability question", defaultValue = "exact", choices = listOf("exact", "at_most", "at_least", "below", "above", "between", "quantile")),
            MethodSetting.IntSetting("n", "Binomial n", defaultValue = 10, minimum = 0, maximum = 1000000),
            fractionSetting("probability", "Binomial probability p", 0.5f),
            MethodSetting.FloatSetting("lambda", "Poisson mean λ", defaultValue = 1f, minimum = 0f, maximum = 1000000f, step = 0.1f, decimals = 4),
            MethodSetting.IntSetting("k", "Count k", defaultValue = 0, minimum = 0, maximum = 1000000),
            MethodSetting.FloatSetting("mean", "Normal mean", defaultValue = 0f, step = 0.1f, decimals = 4),
            MethodSetting.FloatSetting("sd", "Normal SD", defaultValue = 1f, minimum = 0.000001f, step = 0.1f, decimals = 4),
            MethodSetting.FloatSetting("x", "Value x", defaultValue = 0f, step = 0.1f, decimals = 4),
            MethodSetting.FloatSetting("lower", "Lower bound", defaultValue = 0f, step = 0.1f, decimals = 4),
            MethodSetting.FloatSetting("upper", "Upper bound", defaultValue = 1f, step = 0.1f, decimals = 4),
            fractionSetting("quantile_probability", "Quantile probability", 0.5f, minimum = 0.000001f, maximum = 0.999999f)
        )
    )

    override fun examples() = listOf(
        ModuleExample("Diagnostic table", "WHAT; diagnostic 2 by 2; RESULT; return fieldstats_value, diagnostic_sensitivity, diagnostic_specificity, diagnostic_ppv, diagnostic_npv, fieldstats_audit_json; format json"),
        ModuleExample("Survey sample size", "WHAT; sample size for proportion; RESULT; return sample_size_proportion_final_n, fieldstats_audit_json; format json"),
        ModuleExample("Zero-event upper bound", "WHAT; detection limit; RESULT; return detection_upper_prevalence, fieldstats_audit_json; format json")
    )

    private fun fractionSetting(
        id: String,
        label: String,
        defaultValue: Float,
        minimum: Float = 0f,
        maximum: Float = 1f
    ) = MethodSetting.FloatSetting(
        id = id,
        label = label,
        description = "Enter as a fraction from 0 to 1.",
        defaultValue = defaultValue,
        minimum = minimum,
        maximum = maximum,
        step = 0.01f,
        decimals = 4
    )
}
