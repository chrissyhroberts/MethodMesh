package com.example.methodmesh.modules.fieldstats

import com.example.methodmesh.core.methodmesh.ArchitectureId
import com.example.methodmesh.core.methodmesh.ArchitectureRef
import com.example.methodmesh.core.methodmesh.Entity
import com.example.methodmesh.core.methodmesh.ExecutionRequest
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.InvocationContext
import com.example.methodmesh.core.methodmesh.KnowledgeObjectType
import com.example.methodmesh.core.methodmesh.MethodContract
import com.example.methodmesh.core.methodmesh.MethodDescriptor
import com.example.methodmesh.core.methodmesh.MethodObjectType
import com.example.methodmesh.core.methodmesh.Observation
import com.example.methodmesh.core.methodmesh.ProvenanceContext
import com.example.methodmesh.core.methodmesh.Signal
import com.example.methodmesh.core.methodmesh.Transformation
import com.example.methodmesh.core.methodmesh.TransformationStatus
import com.example.methodmesh.core.methodmesh.runtime.As100ExecutionEngine
import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.core.methodmesh.withInvocationContext
import com.example.methodmesh.settings.SettingsState
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs

object FieldStatsCommonFields {
    const val STATUS = "fieldstats_status"
    const val MAIN_VALUE = "fieldstats_value"
    const val SUMMARY = "fieldstats_summary"
    const val AUDIT_JSON = "fieldstats_audit_json"
    const val ERROR = "fieldstats_error"
    val outputs = listOf(STATUS, MAIN_VALUE, SUMMARY, AUDIT_JSON, ERROR)
}

object DiagnosticFields {
    const val TOTAL = "diagnostic_total"
    const val PREVALENCE = "diagnostic_prevalence"
    const val PREVALENCE_LOW = "diagnostic_prevalence_ci_low"
    const val PREVALENCE_HIGH = "diagnostic_prevalence_ci_high"
    const val SENSITIVITY = "diagnostic_sensitivity"
    const val SENSITIVITY_LOW = "diagnostic_sensitivity_ci_low"
    const val SENSITIVITY_HIGH = "diagnostic_sensitivity_ci_high"
    const val SPECIFICITY = "diagnostic_specificity"
    const val SPECIFICITY_LOW = "diagnostic_specificity_ci_low"
    const val SPECIFICITY_HIGH = "diagnostic_specificity_ci_high"
    const val PPV = "diagnostic_ppv"
    const val PPV_LOW = "diagnostic_ppv_ci_low"
    const val PPV_HIGH = "diagnostic_ppv_ci_high"
    const val NPV = "diagnostic_npv"
    const val NPV_LOW = "diagnostic_npv_ci_low"
    const val NPV_HIGH = "diagnostic_npv_ci_high"
    const val ACCURACY = "diagnostic_accuracy"
    const val FPR = "diagnostic_false_positive_rate"
    const val FNR = "diagnostic_false_negative_rate"
    const val LR_POS = "diagnostic_lr_positive"
    const val LR_POS_LOW = "diagnostic_lr_positive_ci_low"
    const val LR_POS_HIGH = "diagnostic_lr_positive_ci_high"
    const val LR_NEG = "diagnostic_lr_negative"
    const val LR_NEG_LOW = "diagnostic_lr_negative_ci_low"
    const val LR_NEG_HIGH = "diagnostic_lr_negative_ci_high"
    const val DOR = "diagnostic_odds_ratio"
    const val DOR_LOW = "diagnostic_odds_ratio_ci_low"
    const val DOR_HIGH = "diagnostic_odds_ratio_ci_high"
    const val PREDICTIVE_PREVALENCE = "diagnostic_predictive_prevalence"
    const val PREDICTIVE_PPV = "diagnostic_predictive_ppv"
    const val PREDICTIVE_NPV = "diagnostic_predictive_npv"
    const val CORRECTION = "diagnostic_continuity_correction"
    val outputs = listOf(
        TOTAL, PREVALENCE, PREVALENCE_LOW, PREVALENCE_HIGH,
        SENSITIVITY, SENSITIVITY_LOW, SENSITIVITY_HIGH,
        SPECIFICITY, SPECIFICITY_LOW, SPECIFICITY_HIGH,
        PPV, PPV_LOW, PPV_HIGH, NPV, NPV_LOW, NPV_HIGH,
        ACCURACY, FPR, FNR,
        LR_POS, LR_POS_LOW, LR_POS_HIGH, LR_NEG, LR_NEG_LOW, LR_NEG_HIGH,
        DOR, DOR_LOW, DOR_HIGH,
        PREDICTIVE_PREVALENCE, PREDICTIVE_PPV, PREDICTIVE_NPV, CORRECTION
    )
}

object ProportionFields {
    const val NUMERATOR = "proportion_numerator"
    const val DENOMINATOR = "proportion_denominator"
    const val ESTIMATE = "proportion_estimate"
    const val PERCENT = "proportion_percent"
    const val CI_LOW = "proportion_ci_low"
    const val CI_HIGH = "proportion_ci_high"
    val outputs = listOf(NUMERATOR, DENOMINATOR, ESTIMATE, PERCENT, CI_LOW, CI_HIGH)
}

object SampleSizeProportionFields {
    const val BASE_N = "sample_size_proportion_base_n"
    const val FPC_N = "sample_size_proportion_fpc_n"
    const val DESIGN_N = "sample_size_proportion_design_n"
    const val FINAL_N = "sample_size_proportion_final_n"
    val outputs = listOf(BASE_N, FPC_N, DESIGN_N, FINAL_N)
}

object DetectionFields {
    const val MODE = "detection_mode"
    const val REQUIRED_N = "detection_required_n"
    const val DETECTION_PROBABILITY = "detection_probability"
    const val UPPER_PREVALENCE = "detection_upper_prevalence"
    const val RULE_OF_THREE = "detection_rule_of_three_upper"
    const val FINITE_POPULATION = "detection_finite_population"
    const val IMPLIED_CASES = "detection_implied_cases"
    val outputs = listOf(MODE, REQUIRED_N, DETECTION_PROBABILITY, UPPER_PREVALENCE, RULE_OF_THREE, FINITE_POPULATION, IMPLIED_CASES)
}

object CompareBinaryFields {
    const val RISK_A = "binary_risk_a"
    const val RISK_A_LOW = "binary_risk_a_ci_low"
    const val RISK_A_HIGH = "binary_risk_a_ci_high"
    const val RISK_B = "binary_risk_b"
    const val RISK_B_LOW = "binary_risk_b_ci_low"
    const val RISK_B_HIGH = "binary_risk_b_ci_high"
    const val RD = "binary_risk_difference"
    const val RD_LOW = "binary_risk_difference_ci_low"
    const val RD_HIGH = "binary_risk_difference_ci_high"
    const val RR = "binary_risk_ratio"
    const val RR_LOW = "binary_risk_ratio_ci_low"
    const val RR_HIGH = "binary_risk_ratio_ci_high"
    const val OR = "binary_odds_ratio"
    const val OR_LOW = "binary_odds_ratio_ci_low"
    const val OR_HIGH = "binary_odds_ratio_ci_high"
    const val NNT = "binary_nnt_nnh"
    const val NNT_LOW = "binary_nnt_nnh_ci_low"
    const val NNT_HIGH = "binary_nnt_nnh_ci_high"
    const val NNT_INTERPRETATION = "binary_nnt_nnh_interpretation"
    const val CORRECTION = "binary_continuity_correction"
    val outputs = listOf(
        RISK_A, RISK_A_LOW, RISK_A_HIGH, RISK_B, RISK_B_LOW, RISK_B_HIGH,
        RD, RD_LOW, RD_HIGH, RR, RR_LOW, RR_HIGH, OR, OR_LOW, OR_HIGH,
        NNT, NNT_LOW, NNT_HIGH, NNT_INTERPRETATION, CORRECTION
    )
}

object SummaryFields {
    const val N = "summary_n"
    const val INVALID = "summary_invalid_count"
    const val MEAN = "summary_mean"
    const val SD = "summary_sd"
    const val MEDIAN = "summary_median"
    const val Q1 = "summary_q1"
    const val Q3 = "summary_q3"
    const val IQR = "summary_iqr"
    const val MIN = "summary_min"
    const val MAX = "summary_max"
    const val RANGE = "summary_range"
    const val CV = "summary_cv"
    const val P05 = "summary_p05"
    const val P95 = "summary_p95"
    val outputs = listOf(N, INVALID, MEAN, SD, MEDIAN, Q1, Q3, IQR, MIN, MAX, RANGE, CV, P05, P95)
}

object TwoProportionSampleSizeFields {
    const val GROUP_A = "sample_size_two_proportions_group_a"
    const val GROUP_B = "sample_size_two_proportions_group_b"
    const val TOTAL = "sample_size_two_proportions_total"
    const val UNINFLATED_A = "sample_size_two_proportions_uninflated_a"
    const val UNINFLATED_B = "sample_size_two_proportions_uninflated_b"
    const val UNINFLATED_TOTAL = "sample_size_two_proportions_uninflated_total"
    val outputs = listOf(GROUP_A, GROUP_B, TOTAL, UNINFLATED_A, UNINFLATED_B, UNINFLATED_TOTAL)
}

object DiagnosticSampleSizeFields {
    const val POSITIVE = "sample_size_diagnostic_positive_needed"
    const val NEGATIVE = "sample_size_diagnostic_negative_needed"
    const val RECRUIT_SENS = "sample_size_diagnostic_recruit_for_sensitivity"
    const val RECRUIT_SPEC = "sample_size_diagnostic_recruit_for_specificity"
    const val BEFORE_UNUSABLE = "sample_size_diagnostic_before_unusable"
    const val FINAL = "sample_size_diagnostic_final_n"
    val outputs = listOf(POSITIVE, NEGATIVE, RECRUIT_SENS, RECRUIT_SPEC, BEFORE_UNUSABLE, FINAL)
}

object RocFields {
    const val N = "roc_n"
    const val POSITIVES = "roc_positives"
    const val NEGATIVES = "roc_negatives"
    const val THRESHOLD = "roc_threshold"
    const val AUC = "roc_auc"
    const val SENSITIVITY = "roc_sensitivity"
    const val SPECIFICITY = "roc_specificity"
    const val PPV = "roc_ppv"
    const val NPV = "roc_npv"
    const val ACCURACY = "roc_accuracy"
    const val TP = "roc_tp"
    const val FP = "roc_fp"
    const val FN = "roc_fn"
    const val TN = "roc_tn"
    const val YOUDEN = "roc_youden_j"
    const val OPTIMAL_THRESHOLD = "roc_optimal_threshold"
    const val OPTIMAL_YOUDEN = "roc_optimal_youden_j"
    const val MIN_SCORE = "roc_min_score"
    const val MAX_SCORE = "roc_max_score"
    const val POINTS_JSON = "roc_points_json"
    const val INVALID_ROWS = "roc_invalid_rows"
    val outputs = listOf(
        N, POSITIVES, NEGATIVES, THRESHOLD, AUC, SENSITIVITY, SPECIFICITY, PPV, NPV, ACCURACY,
        TP, FP, FN, TN, YOUDEN, OPTIMAL_THRESHOLD, OPTIMAL_YOUDEN, MIN_SCORE, MAX_SCORE, POINTS_JSON, INVALID_ROWS
    )
}

object ProbabilityFields {
    const val DISTRIBUTION = "probability_distribution"
    const val QUERY = "probability_query"
    const val LABEL = "probability_label"
    const val VALUE = "probability_result"
    val outputs = listOf(DISTRIBUTION, QUERY, LABEL, VALUE)
}

abstract class FieldStatsMethod(
    final override val id: String,
    name: String,
    description: String,
    specificOutputs: List<String>
) : As100Method {
    val version: String = FieldStatsEngine.ENGINE_VERSION
    final override val ref = ArchitectureRef(ArchitectureId(id), "Method", name)
    final override val descriptor = MethodDescriptor(
        id = ArchitectureId(id),
        methodType = MethodObjectType.Calculation,
        name = name,
        version = version,
        description = description,
        outputs = FieldStatsCommonFields.outputs + specificOutputs,
        graphOutputs = listOf(id),
        parameters = mapOf("category" to "Field statistics", "status" to "Development")
    )
    final override val contract = MethodContract(
        method = ref,
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs,
        producedGraphOutputs = descriptor.graphOutputs
    )

    final override fun request(
        action: String,
        context: Map<String, String>,
        signals: List<Signal>,
        inputs: List<ArchitectureRef>
    ): ExecutionRequest = As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)

    final override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult {
        val invocation = InvocationContext.from(request.context)
        val values = runCatching { calculate(request.context) }
            .getOrElse { failureValues(request.context, it.message ?: "Field statistics calculation failed.") }
        return result(request, values, invocation)
    }

    abstract fun calculate(settings: Map<String, String>): Map<String, String>

    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?): ExecutionResult {
        val ok = values[FieldStatsCommonFields.STATUS] == "succeeded"
        val provenance = ProvenanceContext("methodmesh.fieldstats", id, version)
        val observation = Observation(
            phenomenon = id,
            subject = invocation?.subjectRef(),
            values = values,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        val transformation = Transformation(
            action = id,
            method = ref,
            outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)),
            status = if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        return As100ExecutionEngine.complete(
            request,
            if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed,
            entities = listOf(Entity(ArchitectureId("fieldstats:${id.substringAfterLast('.')}:${System.currentTimeMillis()}"), "FieldStatisticsResult", temporalContext = request.temporalContext)),
            observations = listOf(observation),
            transformations = listOf(transformation),
            diagnostics = if (ok) emptyMap() else mapOf(FieldStatsCommonFields.ERROR to values[FieldStatsCommonFields.ERROR].orEmpty())
        ).withInvocationContext(invocation)
    }

    fun successValues(
        settings: Map<String, String>,
        mainValue: String,
        summary: String,
        specifics: Map<String, String>,
        notes: Map<String, Any?> = emptyMap()
    ): Map<String, String> = linkedMapOf(
        FieldStatsCommonFields.STATUS to "succeeded",
        FieldStatsCommonFields.MAIN_VALUE to mainValue,
        FieldStatsCommonFields.SUMMARY to summary,
        FieldStatsCommonFields.AUDIT_JSON to auditJson(settings, notes),
        FieldStatsCommonFields.ERROR to ""
    ) + specifics

    fun failureValues(settings: Map<String, String>, error: String): Map<String, String> = linkedMapOf(
        FieldStatsCommonFields.STATUS to "failed",
        FieldStatsCommonFields.MAIN_VALUE to "",
        FieldStatsCommonFields.SUMMARY to "",
        FieldStatsCommonFields.AUDIT_JSON to auditJson(settings, mapOf("error" to error)),
        FieldStatsCommonFields.ERROR to error
    ) + descriptor.outputs.filter { it !in FieldStatsCommonFields.outputs }.associateWith { "" }

    private fun auditJson(settings: Map<String, String>, notes: Map<String, Any?>): String {
        val inputs = JSONObject()
        settings
            .filterKeys { !it.startsWith("methodmesh_") && !it.startsWith("input_methodmesh_") }
            .toSortedMap()
            .forEach { (k, v) -> inputs.put(k.removePrefix("input_"), v) }
        val extra = JSONObject()
        notes.forEach { (k, v) -> extra.put(k, v) }
        return JSONObject()
            .put("method_id", id)
            .put("method_version", version)
            .put("engine_version", FieldStatsEngine.ENGINE_VERSION)
            .put("inputs", inputs)
            .put("notes", extra)
            .toString()
    }
}

object As100Diagnostic2x2Method : FieldStatsMethod(
    id = "fieldstats.diagnostic_2x2",
    name = "Diagnostic 2×2 table",
    description = "Calculate diagnostic accuracy measures from TP, FP, FN and TN counts.",
    specificOutputs = DiagnosticFields.outputs
) {
    override fun calculate(settings: Map<String, String>): Map<String, String> {
        val tp = settings.intValue("tp", 0)
        val fp = settings.intValue("fp", 0)
        val fn = settings.intValue("fn", 0)
        val tn = settings.intValue("tn", 0)
        val confidence = settings.doubleValue("confidence", 0.95)
        val useOverride = settings.boolValue("use_prevalence_override", false)
        val override = if (useOverride) settings.doubleValue("prevalence_override", 0.05) else null
        val r = FieldStatsEngine.diagnostic2x2(tp, fp, fn, tn, confidence, override)
        val specifics = linkedMapOf<String, String>(
            DiagnosticFields.TOTAL to r.total.toString(),
            DiagnosticFields.PREVALENCE to fmt(r.prevalence.estimate),
            DiagnosticFields.PREVALENCE_LOW to fmt(r.prevalence.lower),
            DiagnosticFields.PREVALENCE_HIGH to fmt(r.prevalence.upper),
            DiagnosticFields.SENSITIVITY to fmt(r.sensitivity.estimate),
            DiagnosticFields.SENSITIVITY_LOW to fmt(r.sensitivity.lower),
            DiagnosticFields.SENSITIVITY_HIGH to fmt(r.sensitivity.upper),
            DiagnosticFields.SPECIFICITY to fmt(r.specificity.estimate),
            DiagnosticFields.SPECIFICITY_LOW to fmt(r.specificity.lower),
            DiagnosticFields.SPECIFICITY_HIGH to fmt(r.specificity.upper),
            DiagnosticFields.PPV to fmtOrBlank(r.ppv?.estimate),
            DiagnosticFields.PPV_LOW to fmtOrBlank(r.ppv?.lower),
            DiagnosticFields.PPV_HIGH to fmtOrBlank(r.ppv?.upper),
            DiagnosticFields.NPV to fmtOrBlank(r.npv?.estimate),
            DiagnosticFields.NPV_LOW to fmtOrBlank(r.npv?.lower),
            DiagnosticFields.NPV_HIGH to fmtOrBlank(r.npv?.upper),
            DiagnosticFields.ACCURACY to fmt(r.accuracy.estimate),
            DiagnosticFields.FPR to fmt(r.falsePositiveRate),
            DiagnosticFields.FNR to fmt(r.falseNegativeRate),
            DiagnosticFields.LR_POS to fmtOrBlank(r.lrPositive?.estimate),
            DiagnosticFields.LR_POS_LOW to fmtOrBlank(r.lrPositive?.lower),
            DiagnosticFields.LR_POS_HIGH to fmtOrBlank(r.lrPositive?.upper),
            DiagnosticFields.LR_NEG to fmtOrBlank(r.lrNegative?.estimate),
            DiagnosticFields.LR_NEG_LOW to fmtOrBlank(r.lrNegative?.lower),
            DiagnosticFields.LR_NEG_HIGH to fmtOrBlank(r.lrNegative?.upper),
            DiagnosticFields.DOR to fmtOrBlank(r.diagnosticOddsRatio?.estimate),
            DiagnosticFields.DOR_LOW to fmtOrBlank(r.diagnosticOddsRatio?.lower),
            DiagnosticFields.DOR_HIGH to fmtOrBlank(r.diagnosticOddsRatio?.upper),
            DiagnosticFields.PREDICTIVE_PREVALENCE to fmt(r.predictivePrevalence),
            DiagnosticFields.PREDICTIVE_PPV to fmt(r.predictivePpv),
            DiagnosticFields.PREDICTIVE_NPV to fmt(r.predictiveNpv),
            DiagnosticFields.CORRECTION to r.continuityCorrectionUsed.toString()
        )
        val main = "Sens ${pct(r.sensitivity.estimate)} · Spec ${pct(r.specificity.estimate)} · PPV ${pctOrDash(r.ppv?.estimate)} · NPV ${pctOrDash(r.npv?.estimate)}"
        val summary = buildString {
            append(main)
            append(" · prevalence ${pct(r.prevalence.estimate)}")
            if (useOverride) append(" · at ${pct(r.predictivePrevalence)} prevalence: PPV ${pct(r.predictivePpv)}, NPV ${pct(r.predictiveNpv)}")
        }
        return successValues(settings, main, summary, specifics, mapOf(
            "proportion_ci" to "Wilson score",
            "likelihood_ratio_ci" to "log method",
            "zero_cell_correction" to if (r.continuityCorrectionUsed) "Haldane-Anscombe 0.5" else "none"
        ))
    }
}

object As100ProportionMethod : FieldStatsMethod(
    id = "fieldstats.proportion",
    name = "Proportion and confidence interval",
    description = "Calculate a proportion and Wilson confidence interval from a numerator and denominator.",
    specificOutputs = ProportionFields.outputs
) {
    override fun calculate(settings: Map<String, String>): Map<String, String> {
        val x = settings.intValue("numerator", 0)
        val n = settings.intValue("denominator", 1)
        val confidence = settings.doubleValue("confidence", 0.95)
        val i = FieldStatsEngine.wilson(x, n, confidence)
        val main = "${pct(i.estimate)} (${confLabel(confidence)} CI ${pct(i.lower)}–${pct(i.upper)})"
        return successValues(settings, main, "$x/$n = $main", linkedMapOf(
            ProportionFields.NUMERATOR to x.toString(),
            ProportionFields.DENOMINATOR to n.toString(),
            ProportionFields.ESTIMATE to fmt(i.estimate),
            ProportionFields.PERCENT to fmt(i.estimate * 100.0),
            ProportionFields.CI_LOW to fmt(i.lower),
            ProportionFields.CI_HIGH to fmt(i.upper)
        ), mapOf("ci_method" to "Wilson score"))
    }
}

object As100SampleSizeProportionMethod : FieldStatsMethod(
    id = "fieldstats.sample_size_proportion",
    name = "Sample size for a proportion",
    description = "Estimate sample size for a proportion with optional finite-population, design-effect and non-response inflation.",
    specificOutputs = SampleSizeProportionFields.outputs
) {
    override fun calculate(settings: Map<String, String>): Map<String, String> {
        val p = settings.doubleValue("expected_proportion", 0.5)
        val d = settings.doubleValue("absolute_precision", 0.05)
        val confidence = settings.doubleValue("confidence", 0.95)
        val population = settings.intValue("population_size", 0).takeIf { it > 0 }
        val deff = settings.doubleValue("design_effect", 1.0)
        val nonresponse = settings.doubleValue("nonresponse_fraction", 0.0)
        val r = FieldStatsEngine.sampleSizeProportion(p, d, confidence, population, deff, nonresponse)
        val summary = "Basic ${r.baseN} · finite-population ${r.finitePopulationN} · design-adjusted ${r.designAdjustedN} · final ${r.finalN}"
        return successValues(settings, r.finalN.toString(), summary, linkedMapOf(
            SampleSizeProportionFields.BASE_N to r.baseN.toString(),
            SampleSizeProportionFields.FPC_N to r.finitePopulationN.toString(),
            SampleSizeProportionFields.DESIGN_N to r.designAdjustedN.toString(),
            SampleSizeProportionFields.FINAL_N to r.finalN.toString()
        ), mapOf("formula" to "single proportion normal approximation", "finite_population_correction" to (population != null)))
    }
}

object As100DetectionLimitMethod : FieldStatsMethod(
    id = "fieldstats.detection_limit",
    name = "Detection / zero-event bound",
    description = "Plan a sample to detect at least one event or calculate a one-sided prevalence bound after zero observed events.",
    specificOutputs = DetectionFields.outputs
) {
    override fun calculate(settings: Map<String, String>): Map<String, String> {
        val mode = settings.value("mode") ?: "plan_detection"
        val confidence = settings.doubleValue("confidence", 0.95)
        val population = settings.intValue("population_size", 0).takeIf { it > 0 }
        val r = if (mode == "zero_events") {
            FieldStatsEngine.zeroEventUpperBound(settings.intValue("sample_size", 100), confidence, population)
        } else {
            FieldStatsEngine.detectionPlan(settings.doubleValue("prevalence", 0.01), confidence, population)
        }
        val main = if (mode == "zero_events") {
            "Upper prevalence ${pct(r.upperPrevalenceBound ?: Double.NaN)}"
        } else {
            "Sample ${r.requiredN ?: 0} to detect ≥1"
        }
        val summary = if (mode == "zero_events") {
            "0/${settings.intValue("sample_size", 100)} observed · ${confLabel(confidence)} upper bound ${pct(r.upperPrevalenceBound ?: Double.NaN)} · rule-of-three ${pct(r.ruleOfThreeUpper ?: Double.NaN)}"
        } else {
            "At prevalence ${pct(settings.doubleValue("prevalence", 0.01))}, n=${r.requiredN} gives ${pct(r.detectionProbability ?: Double.NaN)} probability of detecting at least one."
        }
        return successValues(settings, main, summary, linkedMapOf(
            DetectionFields.MODE to mode,
            DetectionFields.REQUIRED_N to r.requiredN?.toString().orEmpty(),
            DetectionFields.DETECTION_PROBABILITY to fmtOrBlank(r.detectionProbability),
            DetectionFields.UPPER_PREVALENCE to fmtOrBlank(r.upperPrevalenceBound),
            DetectionFields.RULE_OF_THREE to fmtOrBlank(r.ruleOfThreeUpper),
            DetectionFields.FINITE_POPULATION to r.finitePopulation.toString(),
            DetectionFields.IMPLIED_CASES to r.impliedCasesAtUpperBound?.toString().orEmpty()
        ), mapOf(
            "sampling_assumption" to if (population == null) "independent/binomial approximation" else "simple random sample without replacement / hypergeometric",
            "upper_bound" to if (mode == "zero_events") "one-sided exact zero-event bound" else "not applicable"
        ))
    }
}

object As100CompareBinaryMethod : FieldStatsMethod(
    id = "fieldstats.compare_binary",
    name = "Compare two binary groups",
    description = "Calculate risks, risk difference, risk ratio, odds ratio and NNT/NNH for two groups.",
    specificOutputs = CompareBinaryFields.outputs
) {
    override fun calculate(settings: Map<String, String>): Map<String, String> {
        val aEvents = settings.intValue("events_a", 0)
        val aTotal = settings.intValue("total_a", 1)
        val bEvents = settings.intValue("events_b", 0)
        val bTotal = settings.intValue("total_b", 1)
        val confidence = settings.doubleValue("confidence", 0.95)
        val r = FieldStatsEngine.compareBinary(aEvents, aTotal, bEvents, bTotal, confidence)
        val main = "RR ${fmt(r.riskRatio?.estimate ?: Double.NaN)} · RD ${pp(r.riskDifference.estimate)}"
        val summary = "Risk A ${pct(r.riskA.estimate)} vs B ${pct(r.riskB.estimate)} · $main · OR ${fmt(r.oddsRatio?.estimate ?: Double.NaN)}"
        return successValues(settings, main, summary, linkedMapOf(
            CompareBinaryFields.RISK_A to fmt(r.riskA.estimate),
            CompareBinaryFields.RISK_A_LOW to fmt(r.riskA.lower),
            CompareBinaryFields.RISK_A_HIGH to fmt(r.riskA.upper),
            CompareBinaryFields.RISK_B to fmt(r.riskB.estimate),
            CompareBinaryFields.RISK_B_LOW to fmt(r.riskB.lower),
            CompareBinaryFields.RISK_B_HIGH to fmt(r.riskB.upper),
            CompareBinaryFields.RD to fmt(r.riskDifference.estimate),
            CompareBinaryFields.RD_LOW to fmt(r.riskDifference.lower),
            CompareBinaryFields.RD_HIGH to fmt(r.riskDifference.upper),
            CompareBinaryFields.RR to fmtOrBlank(r.riskRatio?.estimate),
            CompareBinaryFields.RR_LOW to fmtOrBlank(r.riskRatio?.lower),
            CompareBinaryFields.RR_HIGH to fmtOrBlank(r.riskRatio?.upper),
            CompareBinaryFields.OR to fmtOrBlank(r.oddsRatio?.estimate),
            CompareBinaryFields.OR_LOW to fmtOrBlank(r.oddsRatio?.lower),
            CompareBinaryFields.OR_HIGH to fmtOrBlank(r.oddsRatio?.upper),
            CompareBinaryFields.NNT to fmtOrBlank(r.nntPoint),
            CompareBinaryFields.NNT_LOW to fmtOrBlank(r.nntLow),
            CompareBinaryFields.NNT_HIGH to fmtOrBlank(r.nntHigh),
            CompareBinaryFields.NNT_INTERPRETATION to r.nntInterpretation,
            CompareBinaryFields.CORRECTION to r.continuityCorrectionUsed.toString()
        ), mapOf(
            "risk_ci" to "Wilson score",
            "risk_difference_ci" to "Newcombe hybrid score",
            "ratio_ci" to "log method",
            "zero_cell_correction" to if (r.continuityCorrectionUsed) "Haldane-Anscombe 0.5" else "none"
        ))
    }
}

object As100SummaryMethod : FieldStatsMethod(
    id = "fieldstats.summary",
    name = "Quick descriptive statistics",
    description = "Summarise pasted numeric values with mean, SD, median, IQR, range and percentiles.",
    specificOutputs = SummaryFields.outputs
) {
    override fun calculate(settings: Map<String, String>): Map<String, String> {
        val r = FieldStatsEngine.summarize(settings.value("values_text").orEmpty())
        val main = "n=${r.n} · median ${fmt(r.median)} · IQR ${fmt(r.q1)}–${fmt(r.q3)}"
        val summary = "$main · mean ${fmt(r.mean)}${r.sampleSd?.let { " · SD ${fmt(it)}" }.orEmpty()} · range ${fmt(r.min)}–${fmt(r.max)}"
        return successValues(settings, main, summary, linkedMapOf(
            SummaryFields.N to r.n.toString(),
            SummaryFields.INVALID to r.invalidCount.toString(),
            SummaryFields.MEAN to fmt(r.mean),
            SummaryFields.SD to fmtOrBlank(r.sampleSd),
            SummaryFields.MEDIAN to fmt(r.median),
            SummaryFields.Q1 to fmt(r.q1),
            SummaryFields.Q3 to fmt(r.q3),
            SummaryFields.IQR to fmt(r.iqr),
            SummaryFields.MIN to fmt(r.min),
            SummaryFields.MAX to fmt(r.max),
            SummaryFields.RANGE to fmt(r.range),
            SummaryFields.CV to fmtOrBlank(r.cv),
            SummaryFields.P05 to fmt(r.p05),
            SummaryFields.P95 to fmt(r.p95)
        ), mapOf("quantile_method" to "linear interpolation, type-7 equivalent", "sd" to "sample SD (n-1)"))
    }
}

object As100SampleSizeTwoProportionsMethod : FieldStatsMethod(
    id = "fieldstats.sample_size_two_proportions",
    name = "Sample size for two proportions",
    description = "Approximate two-sided sample size for comparing two independent proportions.",
    specificOutputs = TwoProportionSampleSizeFields.outputs
) {
    override fun calculate(settings: Map<String, String>): Map<String, String> {
        val r = FieldStatsEngine.sampleSizeTwoProportions(
            proportionA = settings.doubleValue("proportion_a", 0.10),
            proportionB = settings.doubleValue("proportion_b", 0.15),
            confidence = settings.doubleValue("confidence", 0.95),
            power = settings.doubleValue("power", 0.80),
            allocationRatioBtoA = settings.doubleValue("allocation_ratio_b_to_a", 1.0),
            designEffect = settings.doubleValue("design_effect", 1.0),
            attritionFraction = settings.doubleValue("attrition_fraction", 0.0)
        )
        val main = "A ${r.groupA} · B ${r.groupB} · total ${r.total}"
        val summary = "$main · uninflated total ${r.uninflatedTotal}"
        return successValues(settings, main, summary, linkedMapOf(
            TwoProportionSampleSizeFields.GROUP_A to r.groupA.toString(),
            TwoProportionSampleSizeFields.GROUP_B to r.groupB.toString(),
            TwoProportionSampleSizeFields.TOTAL to r.total.toString(),
            TwoProportionSampleSizeFields.UNINFLATED_A to r.uninflatedA.toString(),
            TwoProportionSampleSizeFields.UNINFLATED_B to r.uninflatedB.toString(),
            TwoProportionSampleSizeFields.UNINFLATED_TOTAL to r.uninflatedTotal.toString()
        ), mapOf("test" to "two-sided independent two-proportion normal approximation"))
    }
}

object As100SampleSizeDiagnosticMethod : FieldStatsMethod(
    id = "fieldstats.sample_size_diagnostic",
    name = "Diagnostic study sample size",
    description = "Plan recruitment to estimate sensitivity and specificity to target absolute precision.",
    specificOutputs = DiagnosticSampleSizeFields.outputs
) {
    override fun calculate(settings: Map<String, String>): Map<String, String> {
        val r = FieldStatsEngine.sampleSizeDiagnostic(
            expectedSensitivity = settings.doubleValue("expected_sensitivity", 0.90),
            expectedSpecificity = settings.doubleValue("expected_specificity", 0.95),
            sensitivityPrecision = settings.doubleValue("sensitivity_precision", 0.05),
            specificityPrecision = settings.doubleValue("specificity_precision", 0.03),
            prevalence = settings.doubleValue("prevalence", 0.10),
            confidence = settings.doubleValue("confidence", 0.95),
            unusableFraction = settings.doubleValue("unusable_fraction", 0.0)
        )
        val main = "Recruit ${r.totalRecruitmentFinal}"
        val summary = "$main · need ${r.positiveCasesNeeded} disease-positive and ${r.negativeCasesNeeded} disease-negative observations"
        return successValues(settings, main, summary, linkedMapOf(
            DiagnosticSampleSizeFields.POSITIVE to r.positiveCasesNeeded.toString(),
            DiagnosticSampleSizeFields.NEGATIVE to r.negativeCasesNeeded.toString(),
            DiagnosticSampleSizeFields.RECRUIT_SENS to r.impliedRecruitmentForSensitivity.toString(),
            DiagnosticSampleSizeFields.RECRUIT_SPEC to r.impliedRecruitmentForSpecificity.toString(),
            DiagnosticSampleSizeFields.BEFORE_UNUSABLE to r.totalRecruitmentBeforeUnusable.toString(),
            DiagnosticSampleSizeFields.FINAL to r.totalRecruitmentFinal.toString()
        ), mapOf("formula" to "precision of a single proportion applied separately to sensitivity and specificity"))
    }
}

object As100RocMethod : FieldStatsMethod(
    id = "fieldstats.roc",
    name = "ROC threshold explorer",
    description = "Calculate ROC AUC and diagnostic performance at a selected continuous-score threshold.",
    specificOutputs = RocFields.outputs
) {
    override fun calculate(settings: Map<String, String>): Map<String, String> {
        val parsed = FieldStatsEngine.parseRocData(settings.value("roc_data").orEmpty())
        val threshold = settings.doubleValue("threshold", 0.5)
        val r = FieldStatsEngine.roc(parsed.rows, threshold)
        val pointsJson = JSONArray().apply {
            r.points.forEach { point ->
                put(JSONObject().put("threshold", point.threshold).put("fpr", point.falsePositiveRate).put("tpr", point.truePositiveRate))
            }
        }.toString()
        val main = "AUC ${fmt(r.auc, 3)} · Sens ${pct(r.sensitivity)} · Spec ${pct(r.specificity)} @ ${fmt(r.threshold)}"
        val summary = "$main · Youden J ${fmt(r.youdenJ, 3)} · best threshold ${fmt(r.optimalThreshold)}"
        return successValues(settings, main, summary, linkedMapOf(
            RocFields.N to r.n.toString(),
            RocFields.POSITIVES to r.positives.toString(),
            RocFields.NEGATIVES to r.negatives.toString(),
            RocFields.THRESHOLD to fmt(r.threshold),
            RocFields.AUC to fmt(r.auc, 8),
            RocFields.SENSITIVITY to fmt(r.sensitivity),
            RocFields.SPECIFICITY to fmt(r.specificity),
            RocFields.PPV to fmtOrBlank(r.ppv),
            RocFields.NPV to fmtOrBlank(r.npv),
            RocFields.ACCURACY to fmt(r.accuracy),
            RocFields.TP to r.tp.toString(),
            RocFields.FP to r.fp.toString(),
            RocFields.FN to r.fn.toString(),
            RocFields.TN to r.tn.toString(),
            RocFields.YOUDEN to fmt(r.youdenJ),
            RocFields.OPTIMAL_THRESHOLD to fmt(r.optimalThreshold),
            RocFields.OPTIMAL_YOUDEN to fmt(r.optimalYoudenJ),
            RocFields.MIN_SCORE to fmt(r.minScore),
            RocFields.MAX_SCORE to fmt(r.maxScore),
            RocFields.POINTS_JSON to pointsJson,
            RocFields.INVALID_ROWS to parsed.invalidCount.toString()
        ), mapOf("auc_method" to "Mann-Whitney rank statistic with average ranks for ties", "positive_rule" to "score >= threshold"))
    }
}

object As100ProbabilityMethod : FieldStatsMethod(
    id = "fieldstats.probability",
    name = "Quick probability distributions",
    description = "Answer common binomial, Poisson and normal probability questions.",
    specificOutputs = ProbabilityFields.outputs
) {
    override fun calculate(settings: Map<String, String>): Map<String, String> {
        val distribution = settings.value("distribution") ?: "binomial"
        val query = settings.value("query") ?: if (distribution == "normal") "below" else "exact"
        val r = FieldStatsEngine.distribution(
            distribution = distribution,
            query = query,
            n = settings.intValue("n", 10),
            probability = settings.doubleValue("probability", 0.5),
            lambda = settings.doubleValue("lambda", 1.0),
            mean = settings.doubleValue("mean", 0.0),
            sd = settings.doubleValue("sd", 1.0),
            x = settings.doubleValue("x", 0.0),
            k = settings.intValue("k", 0),
            lower = settings.doubleValue("lower", 0.0),
            upper = settings.doubleValue("upper", 1.0),
            quantileProbability = settings.doubleValue("quantile_probability", 0.5)
        )
        val main = "${r.label} = ${fmt(r.value, 8)}"
        return successValues(settings, main, main, linkedMapOf(
            ProbabilityFields.DISTRIBUTION to distribution,
            ProbabilityFields.QUERY to query,
            ProbabilityFields.LABEL to r.label,
            ProbabilityFields.VALUE to fmt(r.value, 12)
        ), mapOf("distribution_engine" to "local deterministic numerical calculation"))
    }
}

internal fun Map<String, String>.value(key: String): String? =
    (this[key] ?: this["input_$key"])?.trim()?.takeIf { it.isNotBlank() }

internal fun Map<String, String>.intValue(key: String, default: Int): Int = value(key)?.toIntOrNull() ?: default
internal fun Map<String, String>.doubleValue(key: String, default: Double): Double = value(key)?.toDoubleOrNull() ?: default
internal fun Map<String, String>.boolValue(key: String, default: Boolean): Boolean = when (value(key)?.lowercase()) {
    "true", "1", "yes", "y" -> true
    "false", "0", "no", "n" -> false
    else -> default
}

internal fun fmt(value: Double, decimals: Int = 6): String {
    if (!value.isFinite()) return ""
    val raw = String.format(Locale.US, "%.${decimals}f", value)
    return raw.trimEnd('0').trimEnd('.').ifBlank { "0" }
}

internal fun fmtOrBlank(value: Double?, decimals: Int = 6): String = value?.takeIf { it.isFinite() }?.let { fmt(it, decimals) }.orEmpty()
internal fun pct(value: Double): String = if (value.isFinite()) "${fmt(value * 100.0, 1)}%" else "—"
internal fun pctOrDash(value: Double?): String = value?.takeIf { it.isFinite() }?.let(::pct) ?: "—"
internal fun pp(value: Double): String = if (value.isFinite()) "${if (value >= 0) "+" else ""}${fmt(value * 100.0, 1)} pp" else "—"
internal fun confLabel(confidence: Double): String = "${fmt(confidence * 100.0, 1)}%"
