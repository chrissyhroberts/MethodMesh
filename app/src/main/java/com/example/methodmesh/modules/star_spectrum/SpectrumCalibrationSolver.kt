package com.example.methodmesh.modules.star_spectrum

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Explainable wavelength calibration by consensus search.
 *
 * This is deliberately not a classifier. It generates many affine pixel→wavelength
 * hypotheses from candidate absorption troughs and a small physical line catalogue,
 * then asks which simple mapping explains the largest mutually-consistent set of
 * observed troughs. A chromatic wavelength bootstrap, when available, contributes
 * only a weak prior and can never create a line match on its own.
 *
 * The design is conceptually similar to RANSAC / pattern-matching wavelength
 * calibration approaches such as RASCAL, but this implementation is local to
 * MethodMesh and does not copy or depend on third-party source code.
 */
internal object SpectrumCalibrationSolver {

    data class ObservedCandidate(
        val index: Int,
        val distancePx: Double,
        val evidence: Double,
        val localSnr: Double,
        val scaleSamples: Int,
        val source: String
    )

    data class Match(
        val line: CalibrationLine,
        val candidate: ObservedCandidate,
        val predictedWavelengthNm: Double,
        val residualNm: Double,
        val toleranceNm: Double
    ) {
        val normalizedResidual: Double
            get() = abs(residualNm) / toleranceNm.coerceAtLeast(1e-9)

        fun anchor(): CalibrationAnchor = CalibrationAnchor(
            distancePx = candidate.distancePx,
            wavelengthNm = line.wavelengthNm,
            label = line.label
        )
    }

    data class Solution(
        val interceptNm: Double,
        val slopeNmPerPx: Double,
        val matches: List<Match>,
        val rmsNm: Double,
        val chromaticRmsNm: Double?,
        val descriptionLength: Double,
        val supportScore: Double,
        val confidence: Double,
        val directMatchCount: Int = matches.size,
        val scoreMargin: Double = 0.0,
        val ambiguous: Boolean = false
    ) {
        val anchorCount: Int get() = matches.size
        val recoveredLineCount: Int
            get() = matches.count { it.candidate.source == "targeted_recovery" }
        val anchors: List<CalibrationAnchor> get() = matches.map { it.anchor() }
        fun wavelength(distancePx: Double): Double = interceptNm + slopeNmPerPx * distancePx
    }

    private data class RecoveryContext(
        val smoothedByScale: Map<Int, DoubleArray>,
        val distancePerSamplePx: Double
    )

    private data class PriorGeometry(
        val slopeNmPerPx: Double
    )

    private data class RecoveryProposal(
        val line: CalibrationLine,
        val candidate: ObservedCandidate
    )

    data class Result(
        val candidates: List<ObservedCandidate>,
        val solutions: List<Solution>,
        val catalogueName: String,
        val usedChromaticPrior: Boolean
    ) {
        val best: Solution? get() = solutions.firstOrNull()
    }

    /**
     * Current A-star catalogue (introduced in v0.2.22): the four Balmer lines already
     * used by the reference workflow. The search machinery is catalogue-agnostic; more
     * line families can be added after validation on independent observations.
     */
    val aStarBalmerCatalogue: List<CalibrationLine> = HydrogenBalmerLines.standard

    fun solveAStarReference(
        extraction: SpectrumExtraction,
        chromaticWavelengthByIndex: DoubleArray? = null,
        maxSolutions: Int = 5
    ): Result {
        val candidates = absorptionCandidates(extraction)
        val solutions = solve(
            candidates = candidates,
            catalogue = aStarBalmerCatalogue,
            extraction = extraction,
            chromaticWavelengthByIndex = chromaticWavelengthByIndex,
            maxSolutions = maxSolutions
        )
        return Result(
            candidates = candidates,
            solutions = solutions,
            catalogueName = "Balmer Hδ/Hγ/Hβ/Hα",
            usedChromaticPrior = chromaticWavelengthByIndex != null
        )
    }

    private fun solve(
        candidates: List<ObservedCandidate>,
        catalogue: List<CalibrationLine>,
        extraction: SpectrumExtraction,
        chromaticWavelengthByIndex: DoubleArray?,
        maxSolutions: Int
    ): List<Solution> {
        if (candidates.size < 2 || catalogue.size < 2) return emptyList()

        val hypotheses = mutableListOf<Solution>()
        val orderedCandidates = candidates.sortedBy { it.distancePx }
        val orderedLines = catalogue.sortedByDescending { it.wavelengthNm }
        val priorGeometry = chromaticPriorGeometry(extraction, chromaticWavelengthByIndex)

        for (i in 0 until orderedCandidates.lastIndex) {
            for (j in i + 1 until orderedCandidates.size) {
                val c1 = orderedCandidates[i]
                val c2 = orderedCandidates[j]
                val dx = c2.distancePx - c1.distancePx
                if (abs(dx) < 2.0) continue

                for (a in 0 until orderedLines.lastIndex) {
                    for (b in a + 1 until orderedLines.size) {
                        val l1 = orderedLines[a]
                        val l2 = orderedLines[b]
                        val slope = (l2.wavelengthNm - l1.wavelengthNm) / dx
                        if (!slope.isFinite()) continue

                        // RED→VIOLET trace selection means wavelength should fall with
                        // increasing distance. Keep the bound very broad so colour
                        // geometry is guidance, not a hard instrument model.
                        if (slope >= -0.01 || abs(slope) > 10.0) continue
                        if (!slopeCompatibleWithPrior(slope, priorGeometry)) continue

                        var intercept = l1.wavelengthNm - slope * c1.distancePx
                        var fittedSlope = slope
                        var matched = matchCatalogue(
                            candidates = orderedCandidates,
                            catalogue = catalogue,
                            interceptNm = intercept,
                            slopeNmPerPx = fittedSlope
                        )
                        if (matched.size < 2) continue

                        // Once a hypothesis has three or more independent members, fit
                        // the affine model to all of them before scoring. This prevents a
                        // lucky seed pair from making an internally inconsistent triplet
                        // look better than it is.
                        if (matched.size >= 3) {
                            val fitted = fitAffineMatches(matched) ?: continue
                            intercept = fitted.first
                            fittedSlope = fitted.second
                            if (!slopeCompatibleWithPrior(fittedSlope, priorGeometry)) continue
                            matched = rebuildAssignedMatches(matched, intercept, fittedSlope)
                            if (!affinePatternPlausible(matched)) continue
                        }

                        val rms = sqrt(matched.map { it.residualNm * it.residualNm }.average())
                        val chromaticRms = chromaticRms(
                            interceptNm = intercept,
                            slopeNmPerPx = fittedSlope,
                            extraction = extraction,
                            prior = chromaticWavelengthByIndex
                        )
                        val descriptionLength = descriptionLength(
                            matches = matched,
                            catalogueSize = catalogue.size,
                            chromaticRmsNm = chromaticRms
                        )
                        val supportScore = supportScore(matched, catalogue.size)
                        val confidence = provisionalConfidence(
                            matches = matched,
                            catalogueSize = catalogue.size,
                            rmsNm = rms,
                            chromaticRmsNm = chromaticRms
                        )

                        hypotheses += Solution(
                            interceptNm = intercept,
                            slopeNmPerPx = fittedSlope,
                            matches = matched,
                            rmsNm = rms,
                            chromaticRmsNm = chromaticRms,
                            descriptionLength = descriptionLength,
                            supportScore = supportScore,
                            confidence = confidence,
                            directMatchCount = matched.size
                        )
                    }
                }
            }
        }

        if (hypotheses.isEmpty()) return emptyList()

        // First rank and collapse the ordinary v0.2.22 hypotheses. Targeted
        // missing-line recovery is deliberately allowed only on this small leading
        // set, never on every arbitrary two-point seed. This keeps recovery from
        // manufacturing apparent four-line support for a poor alias.
        val initialSorted = hypotheses.sortedWith(
            compareByDescending<Solution> { it.anchorCount }
                .thenBy { it.descriptionLength }
                .thenByDescending { it.supportScore }
        )
        val preliminary = dedupeSolutions(
            initialSorted,
            maxOf(12, maxSolutions.coerceAtLeast(1) * 4)
        )
        val recoveryContext = buildRecoveryContext(extraction)
        val completed = preliminary.map { solution ->
            completeMissingCatalogueLines(
                solution = solution,
                catalogue = catalogue,
                extraction = extraction,
                chromaticWavelengthByIndex = chromaticWavelengthByIndex,
                recoveryContext = recoveryContext,
                priorGeometry = priorGeometry
            )
        }

        // Direct first-pass support remains the primary evidence. A recovered line
        // can complete a well-supported pattern, but it cannot promote a weaker
        // two-/three-point alias above a solution with more independently detected
        // members.
        val finalSorted = completed.sortedWith(
            compareByDescending<Solution> { it.directMatchCount }
                .thenBy { it.descriptionLength }
                .thenByDescending { it.anchorCount }
                .thenByDescending { it.supportScore }
        )
        val deduped = dedupeSolutions(finalSorted, maxSolutions.coerceAtLeast(1))

        if (deduped.isEmpty()) return emptyList()
        val bestSolution = deduped.first()
        val bestDescription = bestSolution.descriptionLength
        val second = deduped.getOrNull(1)
        val margin = when {
            second == null -> 8.0
            bestSolution.anchorCount > second.anchorCount ->
                4.0 + 2.0 * (bestSolution.anchorCount - second.anchorCount) +
                    max(0.0, second.descriptionLength - bestDescription)
            else -> max(0.0, second.descriptionLength - bestDescription)
        }

        return deduped.mapIndexed { index, solution ->
            val localMargin = if (index == 0) margin else max(0.0, solution.descriptionLength - bestDescription)
            val ambiguity = index == 0 && (
                solution.directMatchCount < 3 ||
                    margin < 1.5 ||
                    solution.confidence < 0.45
                )
            solution.copy(
                scoreMargin = localMargin,
                ambiguous = ambiguity
            )
        }
    }

    private fun dedupeSolutions(
        sorted: List<Solution>,
        limit: Int
    ): List<Solution> {
        val deduped = mutableListOf<Solution>()
        for (candidate in sorted) {
            val duplicate = deduped.any { existing ->
                abs(existing.slopeNmPerPx - candidate.slopeNmPerPx) <=
                    max(0.002, abs(existing.slopeNmPerPx) * 0.015) &&
                    abs(existing.interceptNm - candidate.interceptNm) <= 3.0
            }
            if (!duplicate) deduped += candidate
            if (deduped.size >= limit.coerceAtLeast(1)) break
        }
        return deduped
    }

    /**
     * Complete a globally supported line-family hypothesis by looking only in the
     * predicted neighbourhoods of missing catalogue members. Recovery is attempted
     * only after at least three lines were found independently by the ordinary
     * candidate generator. The wavelength model narrows the search; the observed
     * 1-D residual must still contain a credible absorption trough.
     */
    private fun completeMissingCatalogueLines(
        solution: Solution,
        catalogue: List<CalibrationLine>,
        extraction: SpectrumExtraction,
        chromaticWavelengthByIndex: DoubleArray?,
        recoveryContext: RecoveryContext,
        priorGeometry: PriorGeometry?
    ): Solution {
        if (solution.directMatchCount < 3 || solution.anchorCount >= catalogue.size) return solution
        if (!affinePatternPlausible(solution.matches) || !slopeCompatibleWithPrior(solution.slopeNmPerPx, priorGeometry)) return solution

        var current = solution
        repeat(2) {
            val proposals = recoverMissingCatalogueCandidates(
                extraction = extraction,
                catalogue = catalogue,
                existingMatches = current.matches,
                interceptNm = current.interceptNm,
                slopeNmPerPx = current.slopeNmPerPx,
                recoveryContext = recoveryContext
            )
            if (proposals.isEmpty()) return current

            var changed = false
            for (proposal in proposals) {
                if (current.matches.any { it.line.label == proposal.line.label }) continue
                if (current.matches.any { abs(it.candidate.distancePx - proposal.candidate.distancePx) < 1e-6 }) continue

                val provisionalResidual =
                    current.wavelength(proposal.candidate.distancePx) - proposal.line.wavelengthNm
                val provisionalTolerance = lineToleranceNm(proposal.candidate, current.slopeNmPerPx)
                if (abs(provisionalResidual) > provisionalTolerance) continue

                val provisional = current.matches + Match(
                    line = proposal.line,
                    candidate = proposal.candidate,
                    predictedWavelengthNm = current.wavelength(proposal.candidate.distancePx),
                    residualNm = provisionalResidual,
                    toleranceNm = provisionalTolerance
                )
                val fitted = fitAffineMatches(provisional) ?: continue
                if (!slopeCompatibleWithPrior(fitted.second, priorGeometry)) continue
                val assigned = rebuildAssignedMatches(provisional, fitted.first, fitted.second)
                if (!affinePatternPlausible(assigned)) continue

                val previousRms = current.rmsNm
                val nextRms = sqrt(assigned.map { it.residualNm * it.residualNm }.average())
                // A targeted line must strengthen the same affine solution rather than
                // force a new one. A modest RMS increase is allowed because a fourth
                // point is an independent check, but a large degradation is rejected.
                val allowedRms = max(1.25, previousRms + 0.85)
                if (nextRms > allowedRms) continue

                val next = scoredSolution(
                    interceptNm = fitted.first,
                    slopeNmPerPx = fitted.second,
                    matches = assigned,
                    directMatchCount = solution.directMatchCount,
                    catalogueSize = catalogue.size,
                    extraction = extraction,
                    chromaticWavelengthByIndex = chromaticWavelengthByIndex
                ) ?: continue

                current = next
                changed = true
            }
            if (!changed || current.anchorCount >= catalogue.size) return current
        }
        return current
    }

    private fun scoredSolution(
        interceptNm: Double,
        slopeNmPerPx: Double,
        matches: List<Match>,
        directMatchCount: Int,
        catalogueSize: Int,
        extraction: SpectrumExtraction,
        chromaticWavelengthByIndex: DoubleArray?
    ): Solution? {
        if (matches.size < 2 || !interceptNm.isFinite() || !slopeNmPerPx.isFinite()) return null
        if (slopeNmPerPx >= -0.01 || abs(slopeNmPerPx) > 10.0) return null
        val rms = sqrt(matches.map { it.residualNm * it.residualNm }.average())
        val chromaticRms = chromaticRms(
            interceptNm = interceptNm,
            slopeNmPerPx = slopeNmPerPx,
            extraction = extraction,
            prior = chromaticWavelengthByIndex
        )
        val description = descriptionLength(
            matches = matches,
            catalogueSize = catalogueSize,
            chromaticRmsNm = chromaticRms
        )
        val support = supportScore(matches, catalogueSize)
        val confidence = provisionalConfidence(
            matches = matches,
            catalogueSize = catalogueSize,
            rmsNm = rms,
            chromaticRmsNm = chromaticRms
        )
        return Solution(
            interceptNm = interceptNm,
            slopeNmPerPx = slopeNmPerPx,
            matches = matches,
            rmsNm = rms,
            chromaticRmsNm = chromaticRms,
            descriptionLength = description,
            supportScore = support,
            confidence = confidence,
            directMatchCount = directMatchCount
        )
    }

    private fun fitAffineMatches(matches: List<Match>): Pair<Double, Double>? {
        if (matches.size < 2) return null
        val meanX = matches.map { it.candidate.distancePx }.average()
        val meanY = matches.map { it.line.wavelengthNm }.average()
        var numerator = 0.0
        var denominator = 0.0
        matches.forEach { match ->
            val dx = match.candidate.distancePx - meanX
            numerator += dx * (match.line.wavelengthNm - meanY)
            denominator += dx * dx
        }
        if (!denominator.isFinite() || denominator <= 1e-9) return null
        val slope = numerator / denominator
        val intercept = meanY - slope * meanX
        if (!slope.isFinite() || !intercept.isFinite()) return null
        return intercept to slope
    }

    private fun buildRecoveryContext(extraction: SpectrumExtraction): RecoveryContext {
        val n = extraction.distancesPx.size
        val scales = intArrayOf(5, 9, 15, 23, 35, 49).filter { it < max(6, n / 2) }
        val smoothed = scales.associateWith { scale ->
            movingAverage(extraction.residual, extraction.valid, scale)
        }
        val steps = mutableListOf<Double>()
        for (index in 0 until extraction.distancesPx.lastIndex) {
            val step = abs(extraction.distancesPx[index + 1] - extraction.distancesPx[index])
            if (step.isFinite() && step > 1e-6) steps += step
        }
        steps.sort()
        val distancePerSample = if (steps.isEmpty()) 1.0 else steps[steps.size / 2]
        return RecoveryContext(
            smoothedByScale = smoothed,
            distancePerSamplePx = distancePerSample.coerceAtLeast(1e-6)
        )
    }

    private fun recoverMissingCatalogueCandidates(
        extraction: SpectrumExtraction,
        catalogue: List<CalibrationLine>,
        existingMatches: List<Match>,
        interceptNm: Double,
        slopeNmPerPx: Double,
        recoveryContext: RecoveryContext
    ): List<RecoveryProposal> {
        if (existingMatches.size < 3 || !slopeNmPerPx.isFinite() || abs(slopeNmPerPx) < 1e-9) return emptyList()
        val n = extraction.distancesPx.size
        if (n < 12) return emptyList()
        val guard = SpectrumFeatureSearchQa.endpointGuardSamples(n)
        val first = guard.coerceAtMost(n - 1)
        val last = (n - 1 - guard).coerceAtLeast(first)
        val alreadyMatched = existingMatches.map { it.line.label }.toSet()
        val recovered = mutableListOf<RecoveryProposal>()

        catalogue.filterNot { it.label in alreadyMatched }.forEach { line ->
            val predictedDistance = (line.wavelengthNm - interceptNm) / slopeNmPerPx
            if (!predictedDistance.isFinite()) return@forEach
            val minDistance = extraction.distancesPx.getOrNull(first) ?: return@forEach
            val maxDistance = extraction.distancesPx.getOrNull(last) ?: return@forEach
            val low = min(minDistance, maxDistance)
            val high = max(minDistance, maxDistance)
            if (predictedDistance !in low..high) return@forEach

            val centre = nearestDistanceIndex(extraction.distancesPx, predictedDistance)
            if (centre !in first..last) return@forEach

            // Search a model-derived neighbourhood around the missing line. Balmer
            // troughs are broad at this resolution, so use roughly ±8 nm but keep
            // only broad instrument-agnostic pixel caps. The current wavelength
            // hypothesis determines the window; no star/device pixel coordinate is
            // hard-coded.
            val halfWindowDistancePx = (8.0 / abs(slopeNmPerPx)).coerceIn(8.0, 36.0)
            val halfWindowSamples = (halfWindowDistancePx / recoveryContext.distancePerSamplePx)
                .toInt()
                .coerceIn(6, 44)
            val from = max(first, centre - halfWindowSamples)
            val to = min(last, centre + halfWindowSamples)
            if (to - from < 8) return@forEach

            data class RecoveryProbe(
                val index: Int,
                val evidence: Double,
                val snr: Double,
                val scale: Int,
                val score: Double
            )

            val probes = mutableListOf<RecoveryProbe>()
            recoveryContext.smoothedByScale.forEach scaleLoop@ { (scale, smoothed) ->
                // A real absorption trough can sit on a positive broad continuum.
                // Therefore recovery must not require the globally signed residual
                // to be below zero. Recreate the same scientific idea used by the
                // manual local view: fit a simple baseline from the two shoulders of
                // the predicted window, subtract it, and look for a downward local
                // excursion relative to that baseline.
                val localResidual = shoulderFittedResidual(
                    values = smoothed,
                    valid = extraction.valid,
                    from = from,
                    to = to
                )
                val searchFrom = max(from + 1, 1)
                val searchTo = min(to - 1, n - 2)
                if (searchTo < searchFrom) return@scaleLoop
                for (i in searchFrom..searchTo) {
                    val value = localResidual.getOrNull(i) ?: continue
                    if (!value.isFinite() || value >= 0.0) continue
                    if (value > localResidual[i - 1] || value > localResidual[i + 1]) continue

                    val localNoise = robustLocalScale(
                        localResidual,
                        i,
                        max(7, min(scale * 2, to - from + 1))
                    ).takeIf { it.isFinite() && it > 1e-9 }
                        ?: extraction.localNoise.getOrNull(i)
                            ?.takeIf { it.isFinite() && it > 1e-9 }
                        ?: continue

                    val snr = -value / localNoise
                    val shoulder = shoulderProminence(localResidual, i, max(3, scale))
                    val prominenceScore = shoulder / localNoise
                    if (snr < 0.85 || prominenceScore < 0.20) continue

                    val observedDistance = extraction.distancesPx[i]
                    val proximity = (abs(observedDistance - predictedDistance) / halfWindowDistancePx)
                        .coerceIn(0.0, 1.5)
                    val evidence = (0.70 * snr + 0.30 * prominenceScore).coerceAtLeast(0.0)
                    if (evidence < 0.72) continue
                    probes += RecoveryProbe(
                        index = i,
                        evidence = evidence,
                        snr = snr,
                        scale = scale,
                        score = evidence - 0.25 * proximity
                    )
                }
            }

            // A missing Balmer member should persist as a trough across more than
            // one smoothing scale. This rejects isolated noise/narrow artefacts while
            // remaining independent of any particular star image or pixel position.
            val ranked = probes.sortedByDescending { it.score }
            var accepted: ObservedCandidate? = null
            for (probe in ranked) {
                val cluster = probes.filter { abs(it.index - probe.index) <= 4 }
                val distinctScales = cluster.map { it.scale }.distinct()
                if (distinctScales.size < 2 || distinctScales.maxOrNull() ?: 0 < 15) continue
                val representative = cluster.maxByOrNull { it.score } ?: continue
                val combinedEvidence = (
                    cluster
                        .groupBy { it.scale }
                        .values
                        .map { values -> values.maxOf { it.evidence } }
                        .sortedDescending()
                        .take(4)
                        .average() +
                        0.12 * (distinctScales.size - 1).coerceAtLeast(0)
                    )
                if (!combinedEvidence.isFinite() || combinedEvidence < 0.85) continue
                accepted = ObservedCandidate(
                    index = representative.index,
                    distancePx = extraction.distancesPx[representative.index],
                    evidence = combinedEvidence,
                    localSnr = representative.snr,
                    scaleSamples = distinctScales.maxOrNull() ?: representative.scale,
                    source = "targeted_recovery"
                )
                break
            }

            accepted?.let { candidate ->
                recovered += RecoveryProposal(line = line, candidate = candidate)
            }
        }
        return recovered
    }

    private fun rebuildAssignedMatches(
        matches: List<Match>,
        interceptNm: Double,
        slopeNmPerPx: Double
    ): List<Match> = matches
        .map { existing ->
            val predicted = interceptNm + slopeNmPerPx * existing.candidate.distancePx
            existing.copy(
                predictedWavelengthNm = predicted,
                residualNm = predicted - existing.line.wavelengthNm,
                toleranceNm = lineToleranceNm(existing.candidate, slopeNmPerPx)
            )
        }
        .sortedByDescending { it.line.wavelengthNm }

    /**
     * A Balmer identity hypothesis must already make sense under one affine mapping.
     * Quadratic curvature is never allowed to establish line identity. The generous
     * thresholds here are much wider than the good Vega regression (<1 nm) but reject
     * the catastrophic aliases seen on-device where a quadratic could hide a >20 nm
     * linear mismatch.
     */
    private fun affinePatternPlausible(matches: List<Match>): Boolean {
        if (matches.size < 3) return true
        val rms = sqrt(matches.map { it.residualNm * it.residualNm }.average())
        val normalizedRms = sqrt(matches.map { it.normalizedResidual * it.normalizedResidual }.average())
        return rms.isFinite() && normalizedRms.isFinite() &&
            rms <= 3.0 && normalizedRms <= 0.80
    }

    /**
     * RED/VIOLET colour geometry is deliberately only a coarse scale prior. It does
     * not identify any line, but it can reject a wavelength solution whose dispersion
     * differs by an order of magnitude from the colour progression in the same image.
     * A 4× envelope in either direction is intentionally permissive.
     */
    private fun slopeCompatibleWithPrior(
        slopeNmPerPx: Double,
        priorGeometry: PriorGeometry?
    ): Boolean {
        val priorSlope = priorGeometry?.slopeNmPerPx ?: return true
        if (!slopeNmPerPx.isFinite() || !priorSlope.isFinite()) return false
        if (slopeNmPerPx * priorSlope <= 0.0) return false
        val ratio = abs(slopeNmPerPx / priorSlope)
        return ratio in 0.25..4.0
    }

    private fun chromaticPriorGeometry(
        extraction: SpectrumExtraction,
        prior: DoubleArray?
    ): PriorGeometry? {
        if (prior == null || prior.size != extraction.distancesPx.size || prior.size < 6) return null
        val guard = SpectrumFeatureSearchQa.endpointGuardSamples(prior.size)
        val first = guard.coerceAtMost(prior.lastIndex)
        val last = (prior.lastIndex - guard).coerceAtLeast(first)
        if (last - first < 4) return null

        val pairs = mutableListOf<Pair<Double, Double>>()
        val sampleCount = min(21, last - first + 1).coerceAtLeast(5)
        for (k in 0 until sampleCount) {
            val fraction = k.toDouble() / (sampleCount - 1).toDouble()
            val index = (first + fraction * (last - first)).toInt().coerceIn(first, last)
            val x = extraction.distancesPx[index]
            val y = prior[index]
            if (x.isFinite() && y.isFinite()) pairs += x to y
        }
        if (pairs.size < 5) return null
        val meanX = pairs.map { it.first }.average()
        val meanY = pairs.map { it.second }.average()
        var numerator = 0.0
        var denominator = 0.0
        pairs.forEach { (x, y) ->
            val dx = x - meanX
            numerator += dx * (y - meanY)
            denominator += dx * dx
        }
        if (denominator <= 1e-9) return null
        val slope = numerator / denominator
        if (!slope.isFinite() || abs(slope) < 1e-6) return null
        return PriorGeometry(slopeNmPerPx = slope)
    }

    private fun nearestDistanceIndex(distances: DoubleArray, target: Double): Int {
        if (distances.isEmpty()) return -1
        var bestIndex = 0
        var bestDistance = Double.POSITIVE_INFINITY
        distances.forEachIndexed { index, value ->
            val delta = abs(value - target)
            if (delta < bestDistance) {
                bestDistance = delta
                bestIndex = index
            }
        }
        return bestIndex
    }

    private fun mergeCandidates(
        original: List<ObservedCandidate>,
        recovered: List<ObservedCandidate>
    ): List<ObservedCandidate> {
        if (recovered.isEmpty()) return original
        val merged = original.toMutableList()
        recovered.forEach { candidate ->
            val nearbyIndex = merged.indexOfFirst { abs(it.index - candidate.index) <= 2 }
            if (nearbyIndex < 0) {
                merged += candidate
            } else if (candidate.evidence > merged[nearbyIndex].evidence) {
                merged[nearbyIndex] = candidate
            }
        }
        return merged.sortedBy { it.distancePx }
    }

    private fun matchCatalogue(
        candidates: List<ObservedCandidate>,
        catalogue: List<CalibrationLine>,
        interceptNm: Double,
        slopeNmPerPx: Double
    ): List<Match> {
        data class Pair(
            val line: CalibrationLine,
            val candidate: ObservedCandidate,
            val residualNm: Double,
            val toleranceNm: Double,
            val cost: Double
        )

        val pairs = mutableListOf<Pair>()
        catalogue.forEach { line ->
            candidates.forEach { candidate ->
                val predicted = interceptNm + slopeNmPerPx * candidate.distancePx
                val residual = predicted - line.wavelengthNm
                val tolerance = lineToleranceNm(candidate, slopeNmPerPx)
                val normalized = abs(residual) / tolerance
                if (normalized <= 1.0) {
                    // Prefer small wavelength residuals, but let strong stable troughs
                    // win close contests between nearby candidates.
                    val evidenceBonus = 0.20 * candidate.evidence.coerceIn(0.0, 4.0)
                    pairs += Pair(
                        line = line,
                        candidate = candidate,
                        residualNm = residual,
                        toleranceNm = tolerance,
                        cost = normalized - evidenceBonus
                    )
                }
            }
        }

        val usedLines = mutableSetOf<String>()
        val usedCandidates = mutableSetOf<Int>()
        val matched = mutableListOf<Match>()
        pairs.sortedBy { it.cost }.forEach { pair ->
            if (pair.line.label in usedLines || pair.candidate.index in usedCandidates) return@forEach
            usedLines += pair.line.label
            usedCandidates += pair.candidate.index
            matched += Match(
                line = pair.line,
                candidate = pair.candidate,
                predictedWavelengthNm = interceptNm + slopeNmPerPx * pair.candidate.distancePx,
                residualNm = pair.residualNm,
                toleranceNm = pair.toleranceNm
            )
        }
        return matched.sortedByDescending { it.line.wavelengthNm }
    }

    private fun lineToleranceNm(candidate: ObservedCandidate, slopeNmPerPx: Double): Double {
        // Broad Balmer troughs can have uncertain centres at this resolution. A
        // scale-aware component keeps the tolerance proportional to the structure
        // that generated the candidate without making it so broad that aliases win.
        val scaleNm = abs(slopeNmPerPx) * candidate.scaleSamples.coerceAtLeast(3) * 0.35
        return max(2.0, min(8.0, scaleNm + 1.5))
    }

    private fun chromaticRms(
        interceptNm: Double,
        slopeNmPerPx: Double,
        extraction: SpectrumExtraction,
        prior: DoubleArray?
    ): Double? {
        if (prior == null || prior.size != extraction.distancesPx.size || prior.isEmpty()) return null
        val guard = SpectrumFeatureSearchQa.endpointGuardSamples(prior.size)
        val first = guard.coerceAtMost(prior.lastIndex)
        val last = (prior.lastIndex - guard).coerceAtLeast(first)
        if (last <= first) return null

        val sampleCount = min(17, last - first + 1).coerceAtLeast(3)
        val residuals = mutableListOf<Double>()
        for (k in 0 until sampleCount) {
            val fraction = if (sampleCount == 1) 0.0 else k.toDouble() / (sampleCount - 1)
            val index = (first + fraction * (last - first)).toInt().coerceIn(first, last)
            val priorNm = prior[index]
            if (!priorNm.isFinite()) continue
            val modelNm = interceptNm + slopeNmPerPx * extraction.distancesPx[index]
            if (modelNm.isFinite()) residuals += modelNm - priorNm
        }
        if (residuals.size < 3) return null
        return sqrt(residuals.map { it * it }.average())
    }

    private fun descriptionLength(
        matches: List<Match>,
        catalogueSize: Int,
        chromaticRmsNm: Double?
    ): Double {
        if (matches.isEmpty()) return Double.POSITIVE_INFINITY
        val residualCost = matches.sumOf { match ->
            val z = match.normalizedResidual.coerceAtMost(4.0)
            z * z
        }
        val unexplainedLinePenalty = (catalogueSize - matches.size).coerceAtLeast(0) * 2.4
        val modelComplexityPenalty = 2.0 * ln(max(2, matches.size).toDouble()) // intercept + slope
        val chromaticPenalty = chromaticRmsNm?.let { rms ->
            // The colour bootstrap is intentionally weak: ~25 nm disagreement costs
            // about one unit, enough to break aliases but not override line evidence.
            (rms / 25.0).let { it * it }
        } ?: 0.0
        return residualCost + unexplainedLinePenalty + modelComplexityPenalty + chromaticPenalty
    }

    private fun supportScore(matches: List<Match>, catalogueSize: Int): Double {
        if (matches.isEmpty()) return 0.0
        val coverage = matches.size.toDouble() / catalogueSize.coerceAtLeast(1)
        val evidence = matches.map { it.candidate.evidence.coerceIn(0.0, 4.0) / 4.0 }.average()
        val residualQuality = exp(-0.5 * matches.map { it.normalizedResidual * it.normalizedResidual }.average())
        return (0.55 * coverage + 0.25 * evidence + 0.20 * residualQuality).coerceIn(0.0, 1.0)
    }

    private fun provisionalConfidence(
        matches: List<Match>,
        catalogueSize: Int,
        rmsNm: Double,
        chromaticRmsNm: Double?
    ): Double {
        val coverage = matches.size.toDouble() / catalogueSize.coerceAtLeast(1)
        val rmsScore = exp(-rmsNm.coerceAtLeast(0.0) / 2.5)
        val chromaticScore = chromaticRmsNm?.let { exp(-it.coerceAtLeast(0.0) / 45.0) } ?: 0.65
        val evidence = matches.map { (it.candidate.evidence / 3.0).coerceIn(0.0, 1.0) }.average()
        return (0.45 * coverage + 0.25 * rmsScore + 0.15 * chromaticScore + 0.15 * evidence).coerceIn(0.0, 1.0)
    }

    private fun absorptionCandidates(extraction: SpectrumExtraction): List<ObservedCandidate> {
        val n = extraction.distancesPx.size
        if (n < 12) return emptyList()
        val guard = SpectrumFeatureSearchQa.endpointGuardSamples(n)
        val first = guard.coerceAtMost(n - 1)
        val last = (n - 1 - guard).coerceAtLeast(first)
        if (last - first < 6) return emptyList()

        data class RawCandidate(
            val index: Int,
            val evidence: Double,
            val snr: Double,
            val scale: Int,
            val source: String
        )

        val raw = mutableListOf<RawCandidate>()

        // Existing detector output is useful evidence when it is available.
        extraction.features
            .filter { it.type == FeatureType.Absorption }
            .filter { it.index in first..last }
            .forEach { feature ->
                raw += RawCandidate(
                    index = feature.index,
                    evidence = (feature.snr / 3.0 + feature.confidence * 2.0).coerceAtLeast(0.0),
                    snr = feature.snr,
                    scale = feature.detectionScalePx.toInt().coerceAtLeast(5),
                    source = "detector"
                )
            }

        // Add deliberately permissive multi-scale minima. These are calibration
        // hypotheses only; they are not returned as scientific feature detections.
        // Consensus across known line spacing is what decides whether they matter.
        val scales = intArrayOf(5, 9, 15, 23, 35, 49).filter { it < n / 2 }
        scales.forEach { window ->
            val smoothed = movingAverage(extraction.residual, extraction.valid, window)
            val half = max(2, window / 3)
            for (i in max(first, half) .. min(last, n - 1 - half)) {
                val value = smoothed[i]
                if (!value.isFinite() || value >= 0.0) continue
                if (value > smoothed[i - 1] || value > smoothed[i + 1]) continue

                val localNoise = extraction.localNoise.getOrNull(i)?.takeIf { it.isFinite() && it > 1e-9 }
                    ?: robustLocalScale(smoothed, i, max(7, window * 2))
                if (!localNoise.isFinite() || localNoise <= 1e-9) continue
                val snr = -value / localNoise
                if (snr < 1.10) continue

                val shoulder = shoulderProminence(smoothed, i, max(3, window))
                val prominenceScore = if (localNoise > 0.0) shoulder / localNoise else 0.0
                val evidence = (0.70 * snr + 0.30 * prominenceScore).coerceAtLeast(0.0)
                raw += RawCandidate(i, evidence, snr, window, "multiscale")
            }
        }

        if (raw.isEmpty()) return emptyList()

        // Cluster repeated detections of the same trough across scales.
        val sorted = raw.sortedByDescending { it.evidence }
        val selected = mutableListOf<RawCandidate>()
        for (candidate in sorted) {
            val tooClose = selected.any { abs(it.index - candidate.index) <= max(3, min(it.scale, candidate.scale) / 3) }
            if (!tooClose) selected += candidate
            if (selected.size >= 18) break
        }

        return selected
            .map { candidate ->
                ObservedCandidate(
                    index = candidate.index,
                    distancePx = extraction.distancesPx[candidate.index],
                    evidence = candidate.evidence,
                    localSnr = candidate.snr,
                    scaleSamples = candidate.scale,
                    source = candidate.source
                )
            }
            .sortedBy { it.distancePx }
    }

    private fun movingAverage(values: DoubleArray, valid: BooleanArray, window: Int): DoubleArray {
        if (values.isEmpty()) return DoubleArray(0)
        val radius = max(1, window / 2)
        return DoubleArray(values.size) { index ->
            var sum = 0.0
            var count = 0
            val from = max(0, index - radius)
            val to = min(values.lastIndex, index + radius)
            for (j in from..to) {
                val value = values[j]
                if (value.isFinite() && valid.getOrElse(j) { true }) {
                    sum += value
                    count++
                }
            }
            if (count == 0) Double.NaN else sum / count
        }
    }

    /**
     * Return a signed local residual after subtracting a straight baseline fitted
     * from the left and right shoulders of a bounded search window. Values outside
     * the window remain NaN. This intentionally mirrors the manual review view and
     * makes trough recovery invariant to the broad continuum level.
     */
    private fun shoulderFittedResidual(
        values: DoubleArray,
        valid: BooleanArray,
        from: Int,
        to: Int
    ): DoubleArray {
        val out = DoubleArray(values.size) { Double.NaN }
        if (values.isEmpty()) return out
        val low = from.coerceIn(0, values.lastIndex)
        val high = to.coerceIn(low, values.lastIndex)
        val span = high - low
        if (span < 6) return out

        val shoulderWidth = max(3, span / 5)
        fun medianIn(start: Int, end: Int): Double {
            val sample = mutableListOf<Double>()
            for (index in start.coerceAtLeast(low)..end.coerceAtMost(high)) {
                val value = values.getOrNull(index) ?: continue
                if (value.isFinite() && valid.getOrElse(index) { true }) sample += value
            }
            if (sample.isEmpty()) return Double.NaN
            sample.sort()
            return if (sample.size % 2 == 1) {
                sample[sample.size / 2]
            } else {
                0.5 * (sample[sample.size / 2 - 1] + sample[sample.size / 2])
            }
        }

        val left = medianIn(low, low + shoulderWidth)
        val right = medianIn(high - shoulderWidth, high)
        if (!left.isFinite() || !right.isFinite()) return out

        for (index in low..high) {
            val value = values[index]
            if (!value.isFinite() || !valid.getOrElse(index) { true }) continue
            val fraction = (index - low).toDouble() / span.toDouble()
            val baseline = left * (1.0 - fraction) + right * fraction
            out[index] = value - baseline
        }
        return out
    }

    private fun robustLocalScale(values: DoubleArray, index: Int, window: Int): Double {
        val radius = max(3, window / 2)
        val sample = mutableListOf<Double>()
        for (j in max(0, index - radius)..min(values.lastIndex, index + radius)) {
            val v = values[j]
            if (v.isFinite()) sample += v
        }
        if (sample.size < 5) return Double.NaN
        sample.sort()
        val median = sample[sample.size / 2]
        val deviations = sample.map { abs(it - median) }.sorted()
        return (1.4826 * deviations[deviations.size / 2]).coerceAtLeast(1e-9)
    }

    private fun shoulderProminence(values: DoubleArray, index: Int, window: Int): Double {
        val half = max(2, window / 2)
        val left = mutableListOf<Double>()
        val right = mutableListOf<Double>()
        for (j in max(0, index - half * 2)..max(0, index - half)) {
            values.getOrNull(j)?.takeIf { it.isFinite() }?.let(left::add)
        }
        for (j in min(values.lastIndex, index + half)..min(values.lastIndex, index + half * 2)) {
            values.getOrNull(j)?.takeIf { it.isFinite() }?.let(right::add)
        }
        if (left.isEmpty() || right.isEmpty() || !values[index].isFinite()) return 0.0
        val shoulder = 0.5 * (left.average() + right.average())
        return (shoulder - values[index]).coerceAtLeast(0.0)
    }
}
