package com.example.methodmesh.modules.fieldstats

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** Pure, offline statistical calculations for the Field Statistics capability family. */
object FieldStatsEngine {
    const val ENGINE_VERSION = "0.1.0"

    data class Interval(val estimate: Double, val lower: Double, val upper: Double)

    data class DiagnosticResult(
        val total: Int,
        val prevalence: Interval,
        val sensitivity: Interval,
        val specificity: Interval,
        val ppv: Interval?,
        val npv: Interval?,
        val accuracy: Interval,
        val falsePositiveRate: Double,
        val falseNegativeRate: Double,
        val lrPositive: Interval?,
        val lrNegative: Interval?,
        val diagnosticOddsRatio: Interval?,
        val predictivePrevalence: Double,
        val predictivePpv: Double,
        val predictiveNpv: Double,
        val continuityCorrectionUsed: Boolean
    )

    data class SampleSizeProportionResult(
        val baseN: Int,
        val finitePopulationN: Int,
        val designAdjustedN: Int,
        val finalN: Int
    )

    data class DetectionResult(
        val requiredN: Int? = null,
        val detectionProbability: Double? = null,
        val upperPrevalenceBound: Double? = null,
        val ruleOfThreeUpper: Double? = null,
        val finitePopulation: Boolean = false,
        val impliedCasesAtUpperBound: Int? = null
    )

    data class BinaryComparisonResult(
        val riskA: Interval,
        val riskB: Interval,
        val riskDifference: Interval,
        val riskRatio: Interval?,
        val oddsRatio: Interval?,
        val nntPoint: Double?,
        val nntLow: Double?,
        val nntHigh: Double?,
        val nntInterpretation: String,
        val continuityCorrectionUsed: Boolean
    )

    data class SummaryResult(
        val n: Int,
        val invalidCount: Int,
        val mean: Double,
        val sampleSd: Double?,
        val median: Double,
        val q1: Double,
        val q3: Double,
        val iqr: Double,
        val min: Double,
        val max: Double,
        val range: Double,
        val cv: Double?,
        val p05: Double,
        val p95: Double
    )

    data class TwoProportionSampleSizeResult(
        val groupA: Int,
        val groupB: Int,
        val total: Int,
        val uninflatedA: Int,
        val uninflatedB: Int,
        val uninflatedTotal: Int
    )

    data class DiagnosticSampleSizeResult(
        val positiveCasesNeeded: Int,
        val negativeCasesNeeded: Int,
        val impliedRecruitmentForSensitivity: Int,
        val impliedRecruitmentForSpecificity: Int,
        val totalRecruitmentBeforeUnusable: Int,
        val totalRecruitmentFinal: Int
    )

    data class RocPoint(val threshold: Double, val falsePositiveRate: Double, val truePositiveRate: Double)

    data class RocResult(
        val n: Int,
        val positives: Int,
        val negatives: Int,
        val threshold: Double,
        val auc: Double,
        val sensitivity: Double,
        val specificity: Double,
        val ppv: Double?,
        val npv: Double?,
        val accuracy: Double,
        val tp: Int,
        val fp: Int,
        val fn: Int,
        val tn: Int,
        val youdenJ: Double,
        val optimalThreshold: Double,
        val optimalYoudenJ: Double,
        val minScore: Double,
        val maxScore: Double,
        val points: List<RocPoint>
    )

    data class ParsedRoc(val rows: List<Pair<Boolean, Double>>, val invalidCount: Int)

    data class DistributionResult(
        val value: Double,
        val secondary: Double? = null,
        val label: String
    )

    fun wilson(successes: Int, total: Int, confidence: Double = 0.95): Interval {
        require(total > 0) { "Denominator must be greater than zero." }
        require(successes in 0..total) { "Numerator must be between zero and denominator." }
        val p = successes.toDouble() / total
        val z = zForConfidence(confidence)
        val z2 = z * z
        val denominator = 1.0 + z2 / total
        val center = (p + z2 / (2.0 * total)) / denominator
        val half = z * sqrt((p * (1.0 - p) / total) + z2 / (4.0 * total * total)) / denominator
        return Interval(p, max(0.0, center - half), min(1.0, center + half))
    }

    fun diagnostic2x2(
        tp: Int,
        fp: Int,
        fn: Int,
        tn: Int,
        confidence: Double = 0.95,
        prevalenceOverride: Double? = null
    ): DiagnosticResult {
        require(tp >= 0 && fp >= 0 && fn >= 0 && tn >= 0) { "All cell counts must be non-negative." }
        val total = tp + fp + fn + tn
        require(total > 0) { "At least one observation is required." }
        require(tp + fn > 0) { "Sensitivity is undefined because there are no disease-positive observations." }
        require(tn + fp > 0) { "Specificity is undefined because there are no disease-negative observations." }
        val sensitivity = wilson(tp, tp + fn, confidence)
        val specificity = wilson(tn, tn + fp, confidence)
        val ppv = if (tp + fp > 0) wilson(tp, tp + fp, confidence) else null
        val npv = if (tn + fn > 0) wilson(tn, tn + fn, confidence) else null
        val prevalence = wilson(tp + fn, total, confidence)
        val accuracy = wilson(tp + tn, total, confidence)
        val fpr = 1.0 - specificity.estimate
        val fnr = 1.0 - sensitivity.estimate

        val corrected = tp == 0 || fp == 0 || fn == 0 || tn == 0
        val ctp = tp + if (corrected) 0.5 else 0.0
        val cfp = fp + if (corrected) 0.5 else 0.0
        val cfn = fn + if (corrected) 0.5 else 0.0
        val ctn = tn + if (corrected) 0.5 else 0.0
        val pos = ctp + cfn
        val neg = ctn + cfp
        val sensC = ctp / pos
        val specC = ctn / neg
        val z = zForConfidence(confidence)

        val lrPlus = if (1.0 - specC > 0.0) {
            val value = sensC / (1.0 - specC)
            val se = sqrt(max(0.0, 1.0 / ctp - 1.0 / pos + 1.0 / cfp - 1.0 / neg))
            logInterval(value, se, z)
        } else null
        val lrMinus = if (specC > 0.0) {
            val value = (1.0 - sensC) / specC
            val se = sqrt(max(0.0, 1.0 / cfn - 1.0 / pos + 1.0 / ctn - 1.0 / neg))
            logInterval(value, se, z)
        } else null
        val dor = run {
            val value = (ctp * ctn) / (cfp * cfn)
            val se = sqrt(1.0 / ctp + 1.0 / cfp + 1.0 / cfn + 1.0 / ctn)
            logInterval(value, se, z)
        }

        val predPrev = prevalenceOverride?.also { require(it in 0.0..1.0) { "Predictive prevalence must be between 0 and 1." } }
            ?: prevalence.estimate
        val predPpvDen = sensitivity.estimate * predPrev + (1.0 - specificity.estimate) * (1.0 - predPrev)
        val predNpvDen = specificity.estimate * (1.0 - predPrev) + (1.0 - sensitivity.estimate) * predPrev
        val predPpv = if (predPpvDen == 0.0) Double.NaN else sensitivity.estimate * predPrev / predPpvDen
        val predNpv = if (predNpvDen == 0.0) Double.NaN else specificity.estimate * (1.0 - predPrev) / predNpvDen

        return DiagnosticResult(
            total = total,
            prevalence = prevalence,
            sensitivity = sensitivity,
            specificity = specificity,
            ppv = ppv,
            npv = npv,
            accuracy = accuracy,
            falsePositiveRate = fpr,
            falseNegativeRate = fnr,
            lrPositive = lrPlus,
            lrNegative = lrMinus,
            diagnosticOddsRatio = dor,
            predictivePrevalence = predPrev,
            predictivePpv = predPpv,
            predictiveNpv = predNpv,
            continuityCorrectionUsed = corrected
        )
    }

    fun sampleSizeProportion(
        expectedProportion: Double,
        absolutePrecision: Double,
        confidence: Double = 0.95,
        populationSize: Int? = null,
        designEffect: Double = 1.0,
        nonresponseFraction: Double = 0.0
    ): SampleSizeProportionResult {
        require(expectedProportion in 0.0..1.0) { "Expected proportion must be between 0 and 1." }
        require(absolutePrecision > 0.0 && absolutePrecision < 1.0) { "Absolute precision must be between 0 and 1." }
        require(designEffect >= 1.0) { "Design effect must be at least 1." }
        require(nonresponseFraction in 0.0..<1.0) { "Non-response fraction must be at least 0 and below 1." }
        val z = zForConfidence(confidence)
        val n0 = z * z * expectedProportion * (1.0 - expectedProportion) / (absolutePrecision * absolutePrecision)
        val base = ceil(n0).toInt().coerceAtLeast(1)
        val fpc = populationSize?.takeIf { it > 0 }?.let { nPopulation ->
            ceil(n0 / (1.0 + (n0 - 1.0) / nPopulation)).toInt().coerceIn(1, nPopulation)
        } ?: base
        val designAdjusted = ceil(fpc * designEffect).toInt().coerceAtLeast(1)
        val finalN = ceil(designAdjusted / (1.0 - nonresponseFraction)).toInt().coerceAtLeast(designAdjusted)
        return SampleSizeProportionResult(base, fpc, designAdjusted, finalN)
    }

    fun detectionPlan(
        prevalence: Double,
        confidence: Double = 0.95,
        populationSize: Int? = null
    ): DetectionResult {
        require(prevalence > 0.0 && prevalence <= 1.0) { "Prevalence must be greater than 0 and at most 1." }
        require(confidence > 0.0 && confidence < 1.0) { "Confidence must be between 0 and 1." }
        val finiteN = populationSize?.takeIf { it > 0 } ?: run {
            val required = ceil(ln(1.0 - confidence) / ln(1.0 - prevalence)).toInt().coerceAtLeast(1)
            val achieved = 1.0 - (1.0 - prevalence).pow(required)
            return DetectionResult(requiredN = required, detectionProbability = achieved, finitePopulation = false)
        }
        val cases = ceil(prevalence * finiteN).toInt().coerceIn(1, finiteN)
        var low = 1
        var high = finiteN
        while (low < high) {
            val mid = (low + high) / 2
            val detection = 1.0 - hypergeometricZeroProbability(finiteN, cases, mid)
            if (detection >= confidence) high = mid else low = mid + 1
        }
        val achieved = 1.0 - hypergeometricZeroProbability(finiteN, cases, low)
        return DetectionResult(
            requiredN = low,
            detectionProbability = achieved,
            finitePopulation = true,
            impliedCasesAtUpperBound = cases
        )
    }

    fun zeroEventUpperBound(
        sampleSize: Int,
        confidence: Double = 0.95,
        populationSize: Int? = null
    ): DetectionResult {
        require(sampleSize > 0) { "Sample size must be greater than zero." }
        require(confidence > 0.0 && confidence < 1.0) { "Confidence must be between 0 and 1." }
        val alpha = 1.0 - confidence
        val ruleThree = min(1.0, 3.0 / sampleSize)
        val finiteN = populationSize?.takeIf { it > 0 } ?: run {
            val upper = 1.0 - alpha.pow(1.0 / sampleSize)
            return DetectionResult(upperPrevalenceBound = upper, ruleOfThreeUpper = ruleThree, finitePopulation = false)
        }
        require(sampleSize <= finiteN) { "Sample size cannot exceed finite population size." }
        var lowCases = 0
        var highCases = finiteN
        while (lowCases < highCases) {
            val mid = (lowCases + highCases + 1) / 2
            val pZero = hypergeometricZeroProbability(finiteN, mid, sampleSize)
            if (pZero >= alpha) lowCases = mid else highCases = mid - 1
        }
        val upper = lowCases.toDouble() / finiteN
        return DetectionResult(
            upperPrevalenceBound = upper,
            ruleOfThreeUpper = ruleThree,
            finitePopulation = true,
            impliedCasesAtUpperBound = lowCases
        )
    }

    fun compareBinary(
        eventsA: Int,
        totalA: Int,
        eventsB: Int,
        totalB: Int,
        confidence: Double = 0.95
    ): BinaryComparisonResult {
        require(totalA > 0 && totalB > 0) { "Both group totals must be greater than zero." }
        require(eventsA in 0..totalA && eventsB in 0..totalB) { "Events must lie between zero and the group total." }
        val pA = eventsA.toDouble() / totalA
        val pB = eventsB.toDouble() / totalB
        val iA = wilson(eventsA, totalA, confidence)
        val iB = wilson(eventsB, totalB, confidence)
        val rd = pA - pB
        val rdLower = rd - sqrt((pA - iA.lower).pow(2) + (iB.upper - pB).pow(2))
        val rdUpper = rd + sqrt((iA.upper - pA).pow(2) + (pB - iB.lower).pow(2))
        val rdInterval = Interval(rd, max(-1.0, rdLower), min(1.0, rdUpper))

        val nonEventsA = totalA - eventsA
        val nonEventsB = totalB - eventsB
        val corrected = eventsA == 0 || eventsB == 0 || nonEventsA == 0 || nonEventsB == 0
        val a = eventsA + if (corrected) 0.5 else 0.0
        val b = nonEventsA + if (corrected) 0.5 else 0.0
        val c = eventsB + if (corrected) 0.5 else 0.0
        val d = nonEventsB + if (corrected) 0.5 else 0.0
        val nA = a + b
        val nB = c + d
        val z = zForConfidence(confidence)

        val rrValue = (a / nA) / (c / nB)
        val rrSe = sqrt(max(0.0, 1.0 / a - 1.0 / nA + 1.0 / c - 1.0 / nB))
        val rr = logInterval(rrValue, rrSe, z)
        val orValue = (a * d) / (b * c)
        val orSe = sqrt(1.0 / a + 1.0 / b + 1.0 / c + 1.0 / d)
        val odds = logInterval(orValue, orSe, z)

        val nntPoint = if (rd == 0.0) null else 1.0 / abs(rd)
        val sameSign = rdInterval.lower > 0.0 || rdInterval.upper < 0.0
        val nntLow: Double?
        val nntHigh: Double?
        val interpretation: String
        if (rd == 0.0) {
            nntLow = null
            nntHigh = null
            interpretation = "No absolute risk difference."
        } else if (!sameSign) {
            nntLow = null
            nntHigh = null
            interpretation = if (rd < 0) "NNT for benefit; confidence interval includes infinity." else "NNH; confidence interval includes infinity."
        } else {
            val bounds = listOf(1.0 / abs(rdInterval.lower), 1.0 / abs(rdInterval.upper)).sorted()
            nntLow = bounds.first()
            nntHigh = bounds.last()
            interpretation = if (rd < 0) "NNT for benefit (Group A has lower risk)." else "NNH (Group A has higher risk)."
        }
        return BinaryComparisonResult(iA, iB, rdInterval, rr, odds, nntPoint, nntLow, nntHigh, interpretation, corrected)
    }

    fun summarize(valuesText: String): SummaryResult {
        val tokens = valuesText
            .replace('\t', ' ')
            .split(Regex("[\\s,;|]+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val parsed = tokens.mapNotNull { it.toDoubleOrNull() }
        val invalid = tokens.size - parsed.size
        require(parsed.isNotEmpty()) { "No numeric values were found." }
        val sorted = parsed.sorted()
        val n = sorted.size
        val mean = sorted.sum() / n
        val sd = if (n >= 2) sqrt(sorted.sumOf { (it - mean).pow(2) } / (n - 1)) else null
        val q1 = quantile(sorted, 0.25)
        val q3 = quantile(sorted, 0.75)
        val med = quantile(sorted, 0.5)
        val minV = sorted.first()
        val maxV = sorted.last()
        val cv = if (sd != null && mean != 0.0) sd / abs(mean) else null
        return SummaryResult(
            n = n,
            invalidCount = invalid,
            mean = mean,
            sampleSd = sd,
            median = med,
            q1 = q1,
            q3 = q3,
            iqr = q3 - q1,
            min = minV,
            max = maxV,
            range = maxV - minV,
            cv = cv,
            p05 = quantile(sorted, 0.05),
            p95 = quantile(sorted, 0.95)
        )
    }

    fun sampleSizeTwoProportions(
        proportionA: Double,
        proportionB: Double,
        confidence: Double = 0.95,
        power: Double = 0.80,
        allocationRatioBtoA: Double = 1.0,
        designEffect: Double = 1.0,
        attritionFraction: Double = 0.0
    ): TwoProportionSampleSizeResult {
        require(proportionA in 0.0..1.0 && proportionB in 0.0..1.0) { "Expected proportions must be between 0 and 1." }
        require(proportionA != proportionB) { "The two expected proportions must differ." }
        require(power > 0.5 && power < 1.0) { "Power must be above 0.5 and below 1." }
        require(allocationRatioBtoA > 0.0) { "Allocation ratio must be greater than zero." }
        require(designEffect >= 1.0) { "Design effect must be at least 1." }
        require(attritionFraction in 0.0..<1.0) { "Attrition fraction must be at least 0 and below 1." }

        val r = allocationRatioBtoA
        val zAlpha = zForConfidence(confidence)
        val zBeta = inverseNormal(power)
        val weightedP = (proportionA + r * proportionB) / (1.0 + r)
        val nullVariance = weightedP * (1.0 - weightedP) * (1.0 + 1.0 / r)
        val altVariance = proportionA * (1.0 - proportionA) + proportionB * (1.0 - proportionB) / r
        val diff = abs(proportionA - proportionB)
        val nAraw = ((zAlpha * sqrt(nullVariance) + zBeta * sqrt(altVariance)) / diff).pow(2)
        val uninflatedA = ceil(nAraw).toInt().coerceAtLeast(2)
        val uninflatedB = ceil(uninflatedA * r).toInt().coerceAtLeast(2)
        val aDesign = ceil(uninflatedA * designEffect).toInt()
        val bDesign = ceil(uninflatedB * designEffect).toInt()
        val aFinal = ceil(aDesign / (1.0 - attritionFraction)).toInt()
        val bFinal = ceil(bDesign / (1.0 - attritionFraction)).toInt()
        return TwoProportionSampleSizeResult(aFinal, bFinal, aFinal + bFinal, uninflatedA, uninflatedB, uninflatedA + uninflatedB)
    }

    fun sampleSizeDiagnostic(
        expectedSensitivity: Double,
        expectedSpecificity: Double,
        sensitivityPrecision: Double,
        specificityPrecision: Double,
        prevalence: Double,
        confidence: Double = 0.95,
        unusableFraction: Double = 0.0
    ): DiagnosticSampleSizeResult {
        require(expectedSensitivity in 0.0..1.0 && expectedSpecificity in 0.0..1.0) { "Sensitivity and specificity must be between 0 and 1." }
        require(sensitivityPrecision > 0.0 && sensitivityPrecision < 1.0) { "Sensitivity precision must be between 0 and 1." }
        require(specificityPrecision > 0.0 && specificityPrecision < 1.0) { "Specificity precision must be between 0 and 1." }
        require(prevalence > 0.0 && prevalence < 1.0) { "Expected prevalence must be above 0 and below 1." }
        require(unusableFraction in 0.0..<1.0) { "Unusable fraction must be at least 0 and below 1." }
        val z = zForConfidence(confidence)
        val positive = ceil(z * z * expectedSensitivity * (1.0 - expectedSensitivity) / sensitivityPrecision.pow(2)).toInt().coerceAtLeast(1)
        val negative = ceil(z * z * expectedSpecificity * (1.0 - expectedSpecificity) / specificityPrecision.pow(2)).toInt().coerceAtLeast(1)
        val recruitmentSens = ceil(positive / prevalence).toInt()
        val recruitmentSpec = ceil(negative / (1.0 - prevalence)).toInt()
        val before = max(recruitmentSens, recruitmentSpec)
        val final = ceil(before / (1.0 - unusableFraction)).toInt()
        return DiagnosticSampleSizeResult(positive, negative, recruitmentSens, recruitmentSpec, before, final)
    }

    fun parseRocData(text: String): ParsedRoc {
        val rows = mutableListOf<Pair<Boolean, Double>>()
        var invalid = 0
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isBlank()) return@forEach
            val parts = line.split(Regex("[,;\\t\\s]+"), limit = 3).filter { it.isNotBlank() }
            if (parts.size < 2) {
                invalid++
                return@forEach
            }
            val truth = parseTruth(parts[0])
            val score = parts[1].toDoubleOrNull()
            if (truth == null || score == null || !score.isFinite()) {
                // Allow one common header row without counting it as bad input.
                val headerLike = parts[0].lowercase() in setOf("truth", "outcome", "status", "class", "label") &&
                    parts[1].lowercase() in setOf("score", "value", "probability", "risk", "marker")
                if (!headerLike) invalid++
            } else {
                rows += truth to score
            }
        }
        return ParsedRoc(rows, invalid)
    }

    fun roc(rows: List<Pair<Boolean, Double>>, threshold: Double): RocResult {
        require(rows.isNotEmpty()) { "No valid ROC rows were supplied." }
        val positives = rows.count { it.first }
        val negatives = rows.size - positives
        require(positives > 0 && negatives > 0) { "ROC analysis requires at least one positive and one negative observation." }
        val minScore = rows.minOf { it.second }
        val maxScore = rows.maxOf { it.second }
        val auc = aucByRanks(rows)
        val current = confusion(rows, threshold)
        val thresholds = rows.map { it.second }.distinct().sortedDescending()
        var bestThreshold = thresholds.first()
        var bestJ = Double.NEGATIVE_INFINITY
        val points = mutableListOf<RocPoint>()
        val startThreshold = if (maxScore.isFinite()) Math.nextUp(maxScore) else maxScore
        val start = confusion(rows, startThreshold)
        points += RocPoint(startThreshold, 1.0 - start.specificity, start.sensitivity)
        thresholds.forEach { t ->
            val c = confusion(rows, t)
            val j = c.sensitivity + c.specificity - 1.0
            points += RocPoint(t, 1.0 - c.specificity, c.sensitivity)
            if (j > bestJ || (j == bestJ && t > bestThreshold)) {
                bestJ = j
                bestThreshold = t
            }
        }
        val endThreshold = if (minScore.isFinite()) Math.nextDown(minScore) else minScore
        val end = confusion(rows, endThreshold)
        points += RocPoint(endThreshold, 1.0 - end.specificity, end.sensitivity)
        return RocResult(
            n = rows.size,
            positives = positives,
            negatives = negatives,
            threshold = threshold,
            auc = auc,
            sensitivity = current.sensitivity,
            specificity = current.specificity,
            ppv = current.ppv,
            npv = current.npv,
            accuracy = current.accuracy,
            tp = current.tp,
            fp = current.fp,
            fn = current.fn,
            tn = current.tn,
            youdenJ = current.sensitivity + current.specificity - 1.0,
            optimalThreshold = bestThreshold,
            optimalYoudenJ = bestJ,
            minScore = minScore,
            maxScore = maxScore,
            points = points
        )
    }

    fun distribution(
        distribution: String,
        query: String,
        n: Int = 1,
        probability: Double = 0.5,
        lambda: Double = 1.0,
        mean: Double = 0.0,
        sd: Double = 1.0,
        x: Double = 0.0,
        k: Int = 0,
        lower: Double = 0.0,
        upper: Double = 1.0,
        quantileProbability: Double = 0.5
    ): DistributionResult = when (distribution.lowercase()) {
        "binomial" -> {
            require(n >= 0) { "Binomial n must be non-negative." }
            require(probability in 0.0..1.0) { "Binomial p must be between 0 and 1." }
            require(k >= 0) { "k must be non-negative." }
            val v = when (query) {
                "exact" -> binomialPmf(n, k, probability)
                "at_most" -> binomialCdf(n, k, probability)
                "at_least" -> if (k <= 0) 1.0 else 1.0 - binomialCdf(n, k - 1, probability)
                else -> error("Unsupported binomial query: $query")
            }
            DistributionResult(v, label = when (query) {
                "exact" -> "P(X = $k)"
                "at_most" -> "P(X ≤ $k)"
                else -> "P(X ≥ $k)"
            })
        }
        "poisson" -> {
            require(lambda >= 0.0) { "Poisson lambda must be non-negative." }
            require(k >= 0) { "k must be non-negative." }
            val v = when (query) {
                "exact" -> poissonPmf(lambda, k)
                "at_most" -> poissonCdf(lambda, k)
                "at_least" -> if (k <= 0) 1.0 else 1.0 - poissonCdf(lambda, k - 1)
                else -> error("Unsupported Poisson query: $query")
            }
            DistributionResult(v, label = when (query) {
                "exact" -> "P(X = $k)"
                "at_most" -> "P(X ≤ $k)"
                else -> "P(X ≥ $k)"
            })
        }
        "normal" -> {
            require(sd > 0.0) { "Normal SD must be greater than zero." }
            when (query) {
                "below" -> DistributionResult(normalCdf((x - mean) / sd), label = "P(X ≤ x)")
                "above" -> DistributionResult(1.0 - normalCdf((x - mean) / sd), label = "P(X ≥ x)")
                "between" -> {
                    require(upper >= lower) { "Upper bound must be at least the lower bound." }
                    DistributionResult(normalCdf((upper - mean) / sd) - normalCdf((lower - mean) / sd), label = "P(lower ≤ X ≤ upper)")
                }
                "quantile" -> {
                    require(quantileProbability > 0.0 && quantileProbability < 1.0) { "Quantile probability must be between 0 and 1." }
                    DistributionResult(mean + sd * inverseNormal(quantileProbability), label = "Normal quantile")
                }
                else -> error("Unsupported normal query: $query")
            }
        }
        else -> error("Unsupported distribution: $distribution")
    }

    fun zForConfidence(confidence: Double): Double {
        require(confidence > 0.0 && confidence < 1.0) { "Confidence must be between 0 and 1." }
        return inverseNormal(0.5 + confidence / 2.0)
    }

    /** Acklam's rational approximation to the inverse standard normal CDF. */
    fun inverseNormal(p: Double): Double {
        require(p > 0.0 && p < 1.0) { "Probability must be strictly between 0 and 1." }
        val a = doubleArrayOf(-3.969683028665376e+01, 2.209460984245205e+02, -2.759285104469687e+02, 1.383577518672690e+02, -3.066479806614716e+01, 2.506628277459239e+00)
        val b = doubleArrayOf(-5.447609879822406e+01, 1.615858368580409e+02, -1.556989798598866e+02, 6.680131188771972e+01, -1.328068155288572e+01)
        val c = doubleArrayOf(-7.784894002430293e-03, -3.223964580411365e-01, -2.400758277161838e+00, -2.549732539343734e+00, 4.374664141464968e+00, 2.938163982698783e+00)
        val d = doubleArrayOf(7.784695709041462e-03, 3.224671290700398e-01, 2.445134137142996e+00, 3.754408661907416e+00)
        val pLow = 0.02425
        val pHigh = 1.0 - pLow
        return when {
            p < pLow -> {
                val q = sqrt(-2.0 * ln(p))
                (((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) /
                    ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1.0)
            }
            p <= pHigh -> {
                val q = p - 0.5
                val r = q * q
                (((((a[0] * r + a[1]) * r + a[2]) * r + a[3]) * r + a[4]) * r + a[5]) * q /
                    (((((b[0] * r + b[1]) * r + b[2]) * r + b[3]) * r + b[4]) * r + 1.0)
            }
            else -> {
                val q = sqrt(-2.0 * ln(1.0 - p))
                -(((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) /
                    ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1.0)
            }
        }
    }

    private data class Confusion(
        val tp: Int,
        val fp: Int,
        val fn: Int,
        val tn: Int,
        val sensitivity: Double,
        val specificity: Double,
        val ppv: Double?,
        val npv: Double?,
        val accuracy: Double
    )

    private fun confusion(rows: List<Pair<Boolean, Double>>, threshold: Double): Confusion {
        var tp = 0
        var fp = 0
        var fn = 0
        var tn = 0
        rows.forEach { (truth, score) ->
            val positive = score >= threshold
            when {
                truth && positive -> tp++
                !truth && positive -> fp++
                truth -> fn++
                else -> tn++
            }
        }
        val sensitivity = tp.toDouble() / (tp + fn)
        val specificity = tn.toDouble() / (tn + fp)
        val ppv = (tp + fp).takeIf { it > 0 }?.let { tp.toDouble() / it }
        val npv = (tn + fn).takeIf { it > 0 }?.let { tn.toDouble() / it }
        val accuracy = (tp + tn).toDouble() / rows.size
        return Confusion(tp, fp, fn, tn, sensitivity, specificity, ppv, npv, accuracy)
    }

    private fun aucByRanks(rows: List<Pair<Boolean, Double>>): Double {
        val sorted = rows.sortedBy { it.second }
        var rankSumPositive = 0.0
        var i = 0
        while (i < sorted.size) {
            var j = i + 1
            while (j < sorted.size && sorted[j].second == sorted[i].second) j++
            val averageRank = ((i + 1) + j).toDouble() / 2.0
            for (k in i until j) if (sorted[k].first) rankSumPositive += averageRank
            i = j
        }
        val p = rows.count { it.first }.toDouble()
        val n = rows.size - p
        return (rankSumPositive - p * (p + 1.0) / 2.0) / (p * n)
    }

    private fun parseTruth(value: String): Boolean? = when (value.trim().lowercase()) {
        "1", "true", "yes", "y", "positive", "pos", "case", "disease", "d+" -> true
        "0", "false", "no", "n", "negative", "neg", "control", "noncase", "d-" -> false
        else -> null
    }

    private fun logInterval(value: Double, logSe: Double, z: Double): Interval {
        if (!value.isFinite() || value <= 0.0 || !logSe.isFinite()) return Interval(value, Double.NaN, Double.NaN)
        val logValue = ln(value)
        return Interval(value, exp(logValue - z * logSe), exp(logValue + z * logSe))
    }

    private fun quantile(sorted: List<Double>, probability: Double): Double {
        if (sorted.size == 1) return sorted.first()
        val position = probability.coerceIn(0.0, 1.0) * (sorted.size - 1)
        val lo = floor(position).toInt()
        val hi = ceil(position).toInt()
        if (lo == hi) return sorted[lo]
        val fraction = position - lo
        return sorted[lo] + fraction * (sorted[hi] - sorted[lo])
    }

    private fun hypergeometricZeroProbability(population: Int, cases: Int, sample: Int): Double {
        if (sample <= 0) return 1.0
        if (cases <= 0) return 1.0
        if (cases > population || sample > population) return 0.0
        val nonCases = population - cases
        if (sample > nonCases) return 0.0
        val logP = logChoose(nonCases, sample) - logChoose(population, sample)
        return exp(logP).coerceIn(0.0, 1.0)
    }

    private fun logChoose(n: Int, k: Int): Double {
        if (k < 0 || k > n) return Double.NEGATIVE_INFINITY
        val kk = min(k, n - k)
        if (kk == 0) return 0.0
        return logGamma(n + 1.0) - logGamma(kk + 1.0) - logGamma(n - kk + 1.0)
    }

    // Lanczos approximation, sufficient for stable probability ratios used here.
    private fun logGamma(z: Double): Double {
        val p = doubleArrayOf(
            676.5203681218851,
            -1259.1392167224028,
            771.32342877765313,
            -176.61502916214059,
            12.507343278686905,
            -0.13857109526572012,
            9.9843695780195716e-6,
            1.5056327351493116e-7
        )
        if (z < 0.5) return ln(PI) - ln(kotlin.math.sin(PI * z)) - logGamma(1.0 - z)
        var x = 0.99999999999980993
        val zz = z - 1.0
        for (i in p.indices) x += p[i] / (zz + i + 1.0)
        val t = zz + p.size - 0.5
        return 0.5 * ln(2.0 * PI) + (zz + 0.5) * ln(t) - t + ln(x)
    }

    private fun binomialPmf(n: Int, k: Int, p: Double): Double {
        if (k !in 0..n) return 0.0
        if (p == 0.0) return if (k == 0) 1.0 else 0.0
        if (p == 1.0) return if (k == n) 1.0 else 0.0
        return exp(logChoose(n, k) + k * ln(p) + (n - k) * ln(1.0 - p))
    }

    private fun binomialCdf(n: Int, k: Int, p: Double): Double {
        if (k < 0) return 0.0
        if (k >= n) return 1.0
        var sum = 0.0
        for (i in 0..k) sum += binomialPmf(n, i, p)
        return sum.coerceIn(0.0, 1.0)
    }

    private fun poissonPmf(lambda: Double, k: Int): Double {
        if (k < 0) return 0.0
        if (lambda == 0.0) return if (k == 0) 1.0 else 0.0
        return exp(-lambda + k * ln(lambda) - logGamma(k + 1.0))
    }

    private fun poissonCdf(lambda: Double, k: Int): Double {
        if (k < 0) return 0.0
        if (lambda == 0.0) return 1.0
        var term = exp(-lambda)
        var sum = term
        for (i in 1..k) {
            term *= lambda / i
            sum += term
        }
        return sum.coerceIn(0.0, 1.0)
    }

    private fun normalCdf(z: Double): Double = 0.5 * (1.0 + erf(z / sqrt(2.0)))

    private fun erf(x: Double): Double {
        val sign = if (x < 0) -1.0 else 1.0
        val ax = abs(x)
        val t = 1.0 / (1.0 + 0.3275911 * ax)
        val y = 1.0 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * exp(-ax * ax)
        return sign * y
    }
}
