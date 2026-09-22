package com.example.methodmesh.modules.star_spectrum

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Stellar-spectrum extraction.
 *
 * The core extraction path follows the established astronomical pattern used by
 * Astropy specreduce / Horne-style optimal extraction:
 *
 * 1. refine a trace from the user's initial RED -> VIOLET geometry;
 * 2. rectify a cross-dispersion ribbon around that fitted trace;
 * 3. estimate a sigma-clipped two-sided local background;
 * 4. estimate a smoothly varying empirical spatial profile;
 * 5. perform iterative variance-weighted optimal extraction with bad-pixel /
 *    overlap rejection;
 * 6. preserve a simple boxcar extraction alongside the optimal result.
 *
 * For processed RGB/JPEG input, variance is necessarily an empirical proxy.
 * RAW/FITS support can later provide detector-calibrated variance.
 */
internal object SpectrumEngine {
    private data class BackgroundFit(
        val intercept: Double,
        val slope: Double,
        val sigma: Double
    )

    private data class HorneResult(
        val optimal: DoubleArray,
        val variance: DoubleArray,
        val rejected: BooleanArray,
        val cleaned: DoubleArray,
        val model: DoubleArray,
        val finalProfile: DoubleArray,
        val standardizedResidual: DoubleArray,
        val valid: BooleanArray,
        val rejectedFractionByColumn: DoubleArray,
        val iterations: Int,
        val profileUpdateIterations: Int
    )

    fun analyse(
        bitmap: Bitmap,
        startX: Double,
        startY: Double,
        endX: Double,
        endY: Double,
        settings: SpectrumAnalysisSettings,
        calibration: AppliedCalibration? = null
    ): SpectrumExtraction {
        require(bitmap.width > 2 && bitmap.height > 2) {
            "Image is too small for spectral extraction."
        }

        val dx = endX - startX
        val dy = endY - startY
        val length = hypot(dx, dy)
        require(length >= 24.0) {
            "Select RED and VIOLET endpoints farther apart along the dispersed spectrum."
        }

        val ux = dx / length
        val uy = dy / length
        val nx = -uy
        val ny = ux

        val step = max(1.0, length / 4095.0)
        val sampleCount = (floor(length / step).toInt() + 1).coerceIn(24, 4096)
        val distances = DoubleArray(sampleCount) { min(length, it * step) }

        val raster = GrayRaster(bitmap)
        val ribbon = settings.ribbonHalfWidthPx.coerceIn(6, 160)
        val aperture = settings.apertureHalfWidthPx.coerceIn(1, max(1, ribbon - 2))
        val bgGap = settings.backgroundGapPx.coerceIn(
            1,
            max(1, ribbon - aperture - 1)
        )

        // -----------------------------------------------------------------
        // Stage 1: trace refinement, conceptually equivalent to FitTrace.
        // -----------------------------------------------------------------
        val rawOffsets = DoubleArray(sampleCount)
        val centroidConfidence = DoubleArray(sampleCount)
        val searchHalfWidth = min(ribbon, max(12, aperture * 3))

        for (i in distances.indices) {
            val s = distances[i]
            val baseX = startX + ux * s
            val baseY = startY + uy * s

            val profile = DoubleArray(ribbon * 2 + 1) { k ->
                val off = k - ribbon
                raster.sample(
                    baseX + nx * off,
                    baseY + ny * off
                )
            }
            val offsets = IntArray(profile.size) { it - ribbon }
            val bgFit = fitTwoSidedBackground(
                values = profile,
                offsets = offsets,
                apertureHalfWidth = aperture,
                gap = bgGap
            )

            var weightSum = 0.0
            var weightedOffset = 0.0
            var peak = 0.0

            for (k in profile.indices) {
                val off = offsets[k]
                if (abs(off) > searchHalfWidth) continue

                val bg = bgFit.intercept + bgFit.slope * off
                val signal = max(0.0, profile[k] - bg)
                val weight = signal.pow(1.25)

                weightSum += weight
                weightedOffset += weight * off
                peak = max(peak, signal)
            }

            rawOffsets[i] =
                if (weightSum > 1e-12) weightedOffset / weightSum else 0.0

            centroidConfidence[i] =
                if (peak <= 1e-12) 0.0
                else (weightSum / (peak + 1e-12) / max(1, searchHalfWidth * 2 + 1))
                    .coerceIn(0.0, 1.0)
        }

        val smoothedOffsets = movingAverage(
            rollingMedian(rawOffsets, 9),
            5
        )

        val normalizedDistance = DoubleArray(sampleCount) { i ->
            if (sampleCount <= 1) 0.0
            else -1.0 + 2.0 * i / (sampleCount - 1).toDouble()
        }

        val traceCoefficients = robustPolynomialFit(
            x = normalizedDistance,
            y = smoothedOffsets,
            degree = 2,
            clipSigma = 3.0,
            iterations = 4
        )

        val fittedOffsets = DoubleArray(sampleCount) { i ->
            polynomial(traceCoefficients, normalizedDistance[i])
                .coerceIn(-ribbon * 0.90, ribbon * 0.90)
        }

        val traceResidual = DoubleArray(sampleCount) {
            smoothedOffsets[it] - fittedOffsets[it]
        }
        val traceRms = sqrt(
            traceResidual.map { it * it }.average().coerceAtLeast(0.0)
        )

        val traceX = DoubleArray(sampleCount)
        val traceY = DoubleArray(sampleCount)

        for (i in distances.indices) {
            val s = distances[i]
            traceX[i] =
                startX + ux * s + nx * fittedOffsets[i]
            traceY[i] =
                startY + uy * s + ny * fittedOffsets[i]
        }

        val meanCorrection =
            fittedOffsets.map(::abs).average()

        val inBoundsFraction =
            traceX.indices.count {
                raster.inside(
                    traceX[it],
                    traceY[it],
                    min(ribbon, max(aperture + bgGap + 3, 4))
                )
            }.toDouble() / sampleCount

        val traceResidualScore =
            exp(-traceRms / max(1.0, aperture.toDouble()))

        val traceQuality = (
            0.55 * inBoundsFraction +
                0.25 * traceResidualScore +
                0.20 * centroidConfidence.average()
            ).coerceIn(0.0, 1.0)

        val trace = SpectrumTrace(
            userStartX = startX,
            userStartY = startY,
            userEndX = endX,
            userEndY = endY,
            sampleX = traceX,
            sampleY = traceY,
            distancesPx = distances,
            traceQuality = traceQuality,
            meanAbsoluteCorrectionPx = meanCorrection,
            apertureHalfWidthPx = aperture,
            ribbonHalfWidthPx = ribbon
        )

        // -----------------------------------------------------------------
        // Stage 2: rectify a 2-D ribbon around the fitted trace.
        // -----------------------------------------------------------------
        val profileRadius = min(
            ribbon,
            max(aperture + bgGap + 8, aperture * 4)
        )
        val height = profileRadius * 2 + 1
        val width = sampleCount
        val offsets = IntArray(height) { it - profileRadius }

        val rawRectified = DoubleArray(width * height)
        for (x in 0 until width) {
            for (y in 0 until height) {
                val off = offsets[y]
                rawRectified[y * width + x] = raster.sample(
                    traceX[x] + nx * off,
                    traceY[x] + ny * off
                )
            }
        }

        // -----------------------------------------------------------------
        // Stage 3: sigma-clipped two-sided background model.
        // -----------------------------------------------------------------
        val backgroundModel = DoubleArray(rawRectified.size)
        val backgroundSubtracted = DoubleArray(rawRectified.size)
        val backgroundSigma = DoubleArray(width)
        val rawAperture = DoubleArray(width)
        val backgroundAperture = DoubleArray(width)
        val boxcar = DoubleArray(width)

        val apertureMask = BooleanArray(height) {
            abs(offsets[it]) <= aperture
        }

        for (x in 0 until width) {
            val column = DoubleArray(height) { y ->
                rawRectified[y * width + x]
            }

            val bgFit = fitTwoSidedBackground(
                values = column,
                offsets = offsets,
                apertureHalfWidth = aperture,
                gap = bgGap
            )

            backgroundSigma[x] = bgFit.sigma.coerceAtLeast(1e-7)

            var rawSum = 0.0
            var bgSum = 0.0
            var boxSum = 0.0

            for (y in 0 until height) {
                val bg = bgFit.intercept + bgFit.slope * offsets[y]
                val index = y * width + x
                backgroundModel[index] = bg
                backgroundSubtracted[index] = rawRectified[index] - bg

                if (apertureMask[y]) {
                    rawSum += rawRectified[index]
                    bgSum += bg
                    boxSum += backgroundSubtracted[index]
                }
            }

            rawAperture[x] = rawSum
            backgroundAperture[x] = bgSum
            boxcar[x] = boxSum
        }

        // -----------------------------------------------------------------
        // Stage 4: empirical, smoothly varying spatial profile.
        // This mirrors the "interpolated_profile" idea used in modern
        // Horne-style extractors, without assuming a fixed Gaussian PSF.
        // -----------------------------------------------------------------
        val profileSupportHalfWidth = min(
            profileRadius,
            max(aperture + 3, aperture * 2)
        )

        val spatialProfile = buildInterpolatedSpatialProfile(
            backgroundSubtracted = backgroundSubtracted,
            width = width,
            height = height,
            offsets = offsets,
            supportHalfWidth = profileSupportHalfWidth,
            binCount = min(20, max(8, width / 55))
        )

        // -----------------------------------------------------------------
        // Stage 5: variance proxy + iterative Horne optimal extraction.
        // -----------------------------------------------------------------
        val medianBackgroundSigma = median(backgroundSigma)
            .coerceAtLeast(1e-7)

        val varianceProxy = DoubleArray(rawRectified.size)
        for (x in 0 until width) {
            val sigma = backgroundSigma[x].coerceAtLeast(1e-7)
            for (y in 0 until height) {
                val index = y * width + x
                val positiveSource =
                    max(0.0, backgroundSubtracted[index])

                // For JPEG/PNG this is a relative-noise proxy, not detector
                // Poisson variance. It preserves the key Horne weighting
                // behaviour while avoiding false precision.
                varianceProxy[index] =
                    sigma * sigma +
                        positiveSource *
                        max(sigma, medianBackgroundSigma) *
                        0.50 +
                        1e-10
            }
        }

        val rejectionSigma = 5.0

        val horne = horneOptimalExtract(
            signal = backgroundSubtracted,
            variance = varianceProxy,
            profile = spatialProfile,
            width = width,
            height = height,
            offsets = offsets,
            supportHalfWidth = profileSupportHalfWidth,
            rejectionSigma = rejectionSigma,
            iterations = 4
        )

        val corrected = horne.optimal
        val contaminationFraction =
            horne.rejectedFractionByColumn.copyOf()

        val contaminationProbability = DoubleArray(width) { i ->
            // A single deviant pixel should be visible but not treated as
            // catastrophic. Sustained multi-pixel disagreement rapidly rises.
            (contaminationFraction[i] / 0.30).coerceIn(0.0, 1.0)
        }

        val valid = BooleanArray(width) { i ->
            horne.valid[i] &&
                contaminationFraction[i] < 0.55 &&
                corrected[i].isFinite()
        }

        val cleanSignal = corrected.copyOf()
        interpolateInvalid(cleanSignal, valid)

        val continuumWindow =
            forceOdd(settings.continuumWindow.coerceIn(9, 401))
        val continuum =
            rollingMedian(cleanSignal, continuumWindow)
        val residual = DoubleArray(width) {
            cleanSignal[it] - continuum[it]
        }

        val spectralNoise =
            rollingMad(
                residual,
                max(11, continuumWindow / 2)
            )

        val localNoise = DoubleArray(width) { i ->
            max(
                1e-7,
                max(
                    sqrt(horne.variance[i].coerceAtLeast(0.0)),
                    spectralNoise[i]
                )
            )
        }

        val localSnr = DoubleArray(width) { i ->
            abs(residual[i]) /
                localNoise[i].coerceAtLeast(1e-7)
        }

        val rejectedCount =
            horne.rejected.count { it }
        val supportPixelCount =
            width *
                offsets.count {
                    abs(it) <= profileSupportHalfWidth
                }
        val rejectedPixelFraction =
            if (supportPixelCount <= 0) 0.0
            else rejectedCount.toDouble() / supportPixelCount

        val residualImage = DoubleArray(backgroundSubtracted.size) { index ->
            backgroundSubtracted[index] - horne.model[index]
        }

        val supportIndices = backgroundSubtracted.indices.filter { index ->
            val y = index / width
            abs(offsets[y]) <= profileSupportHalfWidth &&
                horne.standardizedResidual[index].isFinite()
        }

        val residualAbove3SigmaFraction =
            if (supportIndices.isEmpty()) 0.0
            else supportIndices.count {
                abs(horne.standardizedResidual[it]) > 3.0
            }.toDouble() / supportIndices.size

        val residualAbove5SigmaFraction =
            if (supportIndices.isEmpty()) 0.0
            else supportIndices.count {
                abs(horne.standardizedResidual[it]) > 5.0
            }.toDouble() / supportIndices.size

        val maxAbsStandardizedResidual =
            supportIndices.maxOfOrNull {
                abs(horne.standardizedResidual[it])
            } ?: 0.0

        val diagnostics = SpectrumExtractionDiagnostics(
            width = width,
            height = height,
            rawRectified = rawRectified,
            backgroundModel = backgroundModel,
            backgroundSubtracted = backgroundSubtracted,
            spatialProfileWeights = horne.finalProfile.copyOf(),
            fittedTargetModel = horne.model,
            residualImage = residualImage,
            standardizedResidualImage = horne.standardizedResidual.copyOf(),
            cleanedRectified = horne.cleaned,
            rejectedMask = horne.rejected,
            boxcarSignal = boxcar,
            optimalSignal = corrected.copyOf(),
            optimalVariance = horne.variance.copyOf(),
            traceOffsetsPx = fittedOffsets,
            traceFitRmsPx = traceRms,
            rejectedPixelFraction = rejectedPixelFraction,
            residualAbove3SigmaFraction = residualAbove3SigmaFraction,
            residualAbove5SigmaFraction = residualAbove5SigmaFraction,
            maxAbsStandardizedResidual = maxAbsStandardizedResidual,
            profileUpdateIterations = horne.profileUpdateIterations,
            rejectionSigma = rejectionSigma,
            extractionIterations = horne.iterations,
            extractionMethod = "Iterative Horne-style optimal extraction with re-estimated empirical spatial profile",
            varianceSemantics =
                "Empirical relative variance proxy for processed raster input; not detector-calibrated uncertainty."
        )

        val provisional = SpectrumExtraction(
            trace = trace,
            distancesPx = distances,
            raw = rawAperture,
            background = backgroundAperture,
            corrected = corrected,
            contaminationFraction = contaminationFraction,
            contaminationProbability = contaminationProbability,
            valid = valid,
            localNoise = localNoise,
            localSnr = localSnr,
            continuum = continuum,
            residual = residual,
            features = emptyList(),
            calibration = calibration,
            diagnostics = diagnostics
        )

        val features =
            detectFeatures(provisional, settings)

        return provisional.copy(features = features)
    }

    /**
     * Robust degree-2 trace fit, using repeated MAD clipping. The x coordinate
     * should already be normalized to roughly [-1, 1].
     */
    private fun robustPolynomialFit(
        x: DoubleArray,
        y: DoubleArray,
        degree: Int,
        clipSigma: Double,
        iterations: Int
    ): DoubleArray {
        require(x.size == y.size)
        val use = BooleanArray(x.size) {
            x[it].isFinite() && y[it].isFinite()
        }

        var coefficients = doubleArrayOf(0.0)

        repeat(iterations.coerceAtLeast(1)) {
            val indices =
                x.indices.filter { use[it] }

            if (indices.size < degree + 1) {
                return@repeat
            }

            val n = degree + 1
            val normal = Array(n) { DoubleArray(n) }
            val rhs = DoubleArray(n)

            for (index in indices) {
                val powers =
                    DoubleArray(n) { p -> x[index].pow(p) }
                for (r in 0 until n) {
                    rhs[r] += powers[r] * y[index]
                    for (c in 0 until n) {
                        normal[r][c] +=
                            powers[r] * powers[c]
                    }
                }
            }

            coefficients =
                runCatching {
                    solveLinearSystem(normal, rhs)
                }.getOrElse {
                    doubleArrayOf(median(y))
                }

            val residuals =
                indices.map {
                    y[it] -
                        polynomial(coefficients, x[it])
                }.toDoubleArray()

            val centre = median(residuals)
            val sigma =
                (1.4826 * mad(residuals, centre))
                    .coerceAtLeast(0.25)

            val limit =
                max(1.25, clipSigma * sigma)

            for (index in indices) {
                val residual =
                    y[index] -
                        polynomial(coefficients, x[index])
                if (abs(residual - centre) > limit) {
                    use[index] = false
                }
            }
        }

        return coefficients
    }

    /**
     * Two side windows, robustly clipped, with a linear cross-dispersion
     * background. This is the same geometry used by standard astronomical
     * two-sided local-background extraction.
     */
    private fun fitTwoSidedBackground(
        values: DoubleArray,
        offsets: IntArray,
        apertureHalfWidth: Int,
        gap: Int
    ): BackgroundFit {
        val sideStart =
            apertureHalfWidth + gap + 1

        val candidateIndices =
            values.indices.filter {
                abs(offsets[it]) >= sideStart &&
                    values[it].isFinite()
            }

        if (candidateIndices.isEmpty()) {
            val med = median(values)
            return BackgroundFit(
                intercept = med,
                slope = 0.0,
                sigma = (1.4826 * mad(values, med))
                    .coerceAtLeast(1e-7)
            )
        }

        val keep = BooleanArray(values.size)
        candidateIndices.forEach { keep[it] = true }

        var intercept =
            median(
                candidateIndices
                    .map { values[it] }
                    .toDoubleArray()
            )
        var slope = 0.0

        repeat(4) {
            val indices =
                candidateIndices.filter { keep[it] }

            if (indices.size >= 2) {
                var sx = 0.0
                var sy = 0.0
                var sxx = 0.0
                var sxy = 0.0

                for (index in indices) {
                    val x = offsets[index].toDouble()
                    val y = values[index]
                    sx += x
                    sy += y
                    sxx += x * x
                    sxy += x * y
                }

                val n = indices.size.toDouble()
                val denominator =
                    n * sxx - sx * sx

                if (abs(denominator) > 1e-12) {
                    slope =
                        (n * sxy - sx * sy) /
                            denominator
                    intercept =
                        (sy - slope * sx) /
                            n
                } else {
                    intercept =
                        sy / n
                    slope = 0.0
                }
            }

            val residuals =
                candidateIndices
                    .filter { keep[it] }
                    .map {
                        values[it] -
                            (intercept +
                                slope * offsets[it])
                    }
                    .toDoubleArray()

            if (residuals.isEmpty()) {
                return@repeat
            }

            val centre = median(residuals)
            val sigma =
                (1.4826 * mad(residuals, centre))
                    .coerceAtLeast(1e-7)

            for (index in candidateIndices) {
                if (!keep[index]) continue
                val residual =
                    values[index] -
                        (intercept +
                            slope * offsets[index])
                if (abs(residual - centre) > 3.0 * sigma) {
                    keep[index] = false
                }
            }
        }

        val finalResiduals =
            candidateIndices
                .filter { keep[it] }
                .map {
                    values[it] -
                        (intercept +
                            slope * offsets[it])
                }
                .toDoubleArray()

        val finalCentre =
            median(finalResiduals)
        val finalSigma =
            (1.4826 *
                mad(finalResiduals, finalCentre))
                .coerceAtLeast(1e-7)

        return BackgroundFit(
            intercept = intercept,
            slope = slope,
            sigma = finalSigma
        )
    }

    /**
     * Build an empirical spatial profile in a small number of dispersion bins,
     * then linearly interpolate it for every column. Each column profile is
     * normalized to unit integral, as required by optimal extraction.
     */
    private fun buildInterpolatedSpatialProfile(
        backgroundSubtracted: DoubleArray,
        width: Int,
        height: Int,
        offsets: IntArray,
        supportHalfWidth: Int,
        binCount: Int,
        usableMask: BooleanArray? = null
    ): DoubleArray {
        val bins =
            binCount.coerceIn(2, max(2, width))
        val binProfiles =
            Array(bins) { DoubleArray(height) }
        val binCentres =
            DoubleArray(bins)

        for (bin in 0 until bins) {
            val from =
                floor(bin * width / bins.toDouble())
                    .toInt()
                    .coerceIn(0, width - 1)
            val toExclusive =
                ceil((bin + 1) * width / bins.toDouble())
                    .toInt()
                    .coerceIn(from + 1, width)

            binCentres[bin] =
                (from + toExclusive - 1) / 2.0

            val normalizedColumns =
                mutableListOf<DoubleArray>()

            for (x in from until toExclusive) {
                val positive =
                    DoubleArray(height)

                var total = 0.0
                var usableCount = 0

                for (y in 0 until height) {
                    if (abs(offsets[y]) > supportHalfWidth) continue

                    val index = y * width + x
                    if (usableMask != null && !usableMask[index]) continue

                    val value =
                        max(
                            0.0,
                            backgroundSubtracted[index]
                        )
                    positive[y] = value
                    total += value
                    usableCount++
                }

                if (total > 1e-10 && usableCount >= 3) {
                    normalizedColumns +=
                        DoubleArray(height) {
                            positive[it] / total
                        }
                }
            }

            val profile =
                if (normalizedColumns.isNotEmpty()) {
                    DoubleArray(height) { y ->
                        median(
                            normalizedColumns
                                .map { it[y] }
                                .toDoubleArray()
                        )
                    }
                } else {
                    val sigma =
                        max(
                            1.5,
                            supportHalfWidth / 2.5
                        )
                    DoubleArray(height) { y ->
                        if (abs(offsets[y]) <= supportHalfWidth) {
                            exp(
                                -0.5 *
                                    (offsets[y] / sigma)
                                        .pow(2.0)
                            )
                        } else {
                            0.0
                        }
                    }
                }

            // Smooth only across the spatial axis. The wavelength dependence is
            // represented by separate bin profiles and interpolation between them.
            val smoothed =
                movingAverage(profile, 3)

            var total = 0.0
            for (y in 0 until height) {
                if (abs(offsets[y]) > supportHalfWidth) {
                    smoothed[y] = 0.0
                } else {
                    smoothed[y] =
                        max(0.0, smoothed[y])
                    total += smoothed[y]
                }
            }

            if (total <= 1e-12) {
                val centre =
                    offsets.indices.minByOrNull {
                        abs(offsets[it])
                    } ?: height / 2
                smoothed[centre] = 1.0
                total = 1.0
            }

            for (y in 0 until height) {
                binProfiles[bin][y] =
                    smoothed[y] / total
            }
        }

        val out =
            DoubleArray(width * height)

        for (x in 0 until width) {
            var leftBin = 0
            while (
                leftBin < bins - 1 &&
                binCentres[leftBin + 1] <= x
            ) {
                leftBin++
            }
            val rightBin =
                min(bins - 1, leftBin + 1)

            val leftCentre =
                binCentres[leftBin]
            val rightCentre =
                binCentres[rightBin]

            val t =
                if (
                    rightBin == leftBin ||
                    abs(rightCentre - leftCentre) < 1e-12
                ) {
                    0.0
                } else {
                    ((x - leftCentre) /
                        (rightCentre - leftCentre))
                        .coerceIn(0.0, 1.0)
                }

            var sum = 0.0
            for (y in 0 until height) {
                val value =
                    binProfiles[leftBin][y] *
                        (1.0 - t) +
                        binProfiles[rightBin][y] *
                        t
                out[y * width + x] =
                    value
                sum += value
            }

            if (sum > 1e-12) {
                for (y in 0 until height) {
                    out[y * width + x] /= sum
                }
            }
        }

        return out
    }


    /**
     * Horne-style variance-weighted optimal extraction.
     *
     * F_x = sum(P_xy D_xy / V_xy) / sum(P_xy^2 / V_xy)
     *
     * The worst residual in each column is rejected per iteration if it exceeds
     * rejectionSigma, then the flux is re-estimated. This is the mechanism used
     * to suppress hot pixels, cosmic-ray-like artefacts and narrow overlapping
     * field-star contamination that does not follow the target spatial profile.
     */
    private fun horneOptimalExtract(
        signal: DoubleArray,
        variance: DoubleArray,
        profile: DoubleArray,
        width: Int,
        height: Int,
        offsets: IntArray,
        supportHalfWidth: Int,
        rejectionSigma: Double,
        iterations: Int
    ): HorneResult {
        val baseUsable =
            BooleanArray(width * height) { index ->
                val y = index / width
                abs(offsets[y]) <= supportHalfWidth &&
                    signal[index].isFinite() &&
                    variance[index].isFinite() &&
                    variance[index] > 0.0
            }

        val rejected =
            BooleanArray(width * height)

        val optimal =
            DoubleArray(width)
        val fluxVariance =
            DoubleArray(width) {
                Double.POSITIVE_INFINITY
            }
        val valid =
            BooleanArray(width)

        val runIterations =
            iterations.coerceIn(2, 8)

        var currentProfile =
            profile.copyOf()
        var profileUpdateIterations = 0

        fun usable(index: Int): Boolean {
            return baseUsable[index] &&
                !rejected[index] &&
                currentProfile[index].isFinite() &&
                currentProfile[index] >= 0.0
        }

        fun extractFlux() {
            for (x in 0 until width) {
                var numerator = 0.0
                var denominator = 0.0
                var goodPixels = 0

                for (y in 0 until height) {
                    val index = y * width + x
                    if (!usable(index)) continue

                    val p = currentProfile[index]
                    val v = variance[index]
                    numerator +=
                        p * signal[index] / v
                    denominator +=
                        p * p / v
                    goodPixels++
                }

                if (
                    denominator > 1e-14 &&
                    goodPixels >= 3
                ) {
                    optimal[x] =
                        numerator / denominator
                    fluxVariance[x] =
                        1.0 / denominator
                    valid[x] = true
                } else {
                    optimal[x] = 0.0
                    fluxVariance[x] =
                        Double.POSITIVE_INFINITY
                    valid[x] = false
                }
            }
        }

        repeat(runIterations) { iteration ->
            extractFlux()

            if (iteration >= runIterations - 1) {
                return@repeat
            }

            var newRejections = 0

            // Standard Horne-style deviant-pixel step: reject at most the
            // strongest residual in a column on each pass, then rebuild the
            // spatial profile from the surviving data before re-extracting.
            for (x in 0 until width) {
                if (!valid[x]) continue

                var worstIndex = -1
                var worstZ = rejectionSigma

                for (y in 0 until height) {
                    val index = y * width + x
                    if (!usable(index)) continue

                    val model =
                        optimal[x] * currentProfile[index]
                    val z =
                        abs(signal[index] - model) /
                            sqrt(
                                variance[index]
                                    .coerceAtLeast(1e-12)
                            )

                    if (z > worstZ) {
                        worstZ = z
                        worstIndex = index
                    }
                }

                if (worstIndex >= 0) {
                    rejected[worstIndex] = true
                    newRejections++
                }
            }

            val surviving =
                BooleanArray(width * height) { index ->
                    baseUsable[index] && !rejected[index]
                }

            val updatedProfile =
                buildInterpolatedSpatialProfile(
                    backgroundSubtracted = signal,
                    width = width,
                    height = height,
                    offsets = offsets,
                    supportHalfWidth = supportHalfWidth,
                    binCount = min(20, max(8, width / 55)),
                    usableMask = surviving
                )

            var profileDelta = 0.0
            var profileCount = 0
            for (index in updatedProfile.indices) {
                if (
                    updatedProfile[index].isFinite() &&
                    currentProfile[index].isFinite()
                ) {
                    profileDelta +=
                        abs(
                            updatedProfile[index] -
                                currentProfile[index]
                        )
                    profileCount++
                }
            }

            val meanProfileDelta =
                if (profileCount <= 0) 0.0
                else profileDelta / profileCount

            currentProfile =
                updatedProfile
            profileUpdateIterations++

            // If no outlier was added and the empirical profile has essentially
            // stopped moving, there is no value in changing the data further.
            // We still allow subsequent fixed-loop passes to re-extract with the
            // updated profile, but no extra rejection is forced.
            if (
                newRejections == 0 &&
                meanProfileDelta < 1e-5
            ) {
                // Intentionally no threshold lowering: stable residual structure
                // below the declared sigma threshold remains visible in QA.
            }
        }

        // Final extraction with the last re-estimated profile.
        extractFlux()

        val model =
            DoubleArray(width * height)
        val standardizedResidual =
            DoubleArray(width * height) {
                Double.NaN
            }
        val cleaned =
            signal.copyOf()
        val rejectedFraction =
            DoubleArray(width)

        for (x in 0 until width) {
            var supportCount = 0
            var rejectedCount = 0

            for (y in 0 until height) {
                val index = y * width + x

                model[index] =
                    optimal[x] *
                        currentProfile[index]

                if (
                    baseUsable[index] &&
                    abs(offsets[y]) <= supportHalfWidth
                ) {
                    standardizedResidual[index] =
                        (signal[index] - model[index]) /
                            sqrt(
                                variance[index]
                                    .coerceAtLeast(1e-12)
                            )
                }

                if (abs(offsets[y]) <= supportHalfWidth) {
                    supportCount++
                    if (rejected[index]) {
                        rejectedCount++
                        cleaned[index] =
                            model[index]
                    }
                }
            }

            rejectedFraction[x] =
                if (supportCount == 0) 0.0
                else rejectedCount.toDouble() /
                    supportCount
        }

        return HorneResult(
            optimal = optimal,
            variance = fluxVariance,
            rejected = rejected,
            cleaned = cleaned,
            model = model,
            finalProfile = currentProfile,
            standardizedResidual =
                standardizedResidual,
            valid = valid,
            rejectedFractionByColumn =
                rejectedFraction,
            iterations = runIterations,
            profileUpdateIterations =
                profileUpdateIterations
        )
    }


    fun fitCalibration(anchors: List<CalibrationAnchor>, requestedOrder: Int): CalibrationFit {
        val order = requestedOrder.coerceIn(1, 2)
        require(anchors.size >= order + 1) { "A polynomial of order $order needs at least ${order + 1} calibration anchors." }
        val n = order + 1
        val normal = Array(n) { DoubleArray(n) }
        val rhs = DoubleArray(n)
        for (anchor in anchors) {
            val powers = DoubleArray(n) { p -> anchor.distancePx.pow(p) }
            for (r in 0 until n) {
                rhs[r] += powers[r] * anchor.wavelengthNm
                for (c in 0 until n) normal[r][c] += powers[r] * powers[c]
            }
        }
        val coefficients = solveLinearSystem(normal, rhs)
        val predicted = anchors.map { anchor -> polynomial(coefficients, anchor.distancePx) }.toDoubleArray()
        val rms = sqrt(anchors.indices.map { i -> (predicted[i] - anchors[i].wavelengthNm).pow(2.0) }.average())
        return CalibrationFit(order, coefficients, rms, predicted)
    }

    fun calibrationForTarget(
        reference: SpectrumReference,
        targetWidthPx: Int,
        targetHeightPx: Int,
        registrationOffsetPx: Double = 0.0
    ): AppliedCalibration {
        val scaleX = targetWidthPx.toDouble() / reference.imageWidthPx.coerceAtLeast(1)
        val scaleY = targetHeightPx.toDouble() / reference.imageHeightPx.coerceAtLeast(1)
        val aspectPenalty = abs(scaleX - scaleY) / max(scaleX, scaleY).coerceAtLeast(1e-9)
        val scale = (scaleX + scaleY) / 2.0
        val rmsScore = exp(-reference.rmsNm.coerceAtLeast(0.0) / 2.0)
        val dimensionScore = exp(-aspectPenalty * 8.0)
        val confidence = (rmsScore * dimensionScore).coerceIn(0.0, 1.0)
        val warning = when {
            aspectPenalty > 0.03 -> "Image aspect ratio differs from the stored reference; wavelength calibration may be biased."
            abs(scale - 1.0) > 0.05 -> "Image dimensions differ from the stored reference; MethodMesh rescaled the pixel solution."
            else -> ""
        }
        return AppliedCalibration(reference, scale.coerceAtLeast(1e-6), registrationOffsetPx, confidence, warning)
    }

    private data class BroadScaleScan(
        val scaleSamples: Int,
        val scalePx: Double,
        val response: DoubleArray,
        val noise: DoubleArray
    )

    private data class BroadCandidate(
        val index: Int,
        val type: FeatureType,
        val scaleSamples: Int,
        val scalePx: Double,
        val response: Double,
        val snr: Double,
        val thresholdStability: Double
    )

    private data class BroadWindowMean(
        val value: Double,
        val count: Int
    )

    private fun detectFeatures(
        extraction: SpectrumExtraction,
        settings: SpectrumAnalysisSettings
    ): List<SpectralFeature> {
        if (extraction.residual.size < 9) {
            return emptyList()
        }

        val detectionValid =
            featureDetectionValidMask(
                extraction
            )

        val narrow =
            detectNarrowFeatures(
                extraction = extraction,
                settings = settings,
                detectionValid = detectionValid
            )

        val broad =
            detectBroadFeatures(
                extraction = extraction,
                settings = settings,
                detectionValid = detectionValid
            )

        return mergeFeatureDetections(
            narrow = narrow,
            broad = broad
        )
    }

    private fun featureDetectionValidMask(
        extraction: SpectrumExtraction
    ): BooleanArray {
        val detectionValid =
            extraction.valid.copyOf()
        val endpointGuard =
            SpectrumFeatureSearchQa
                .endpointGuardSamples(
                    extraction.residual.size
                )

        if (endpointGuard > 0) {
            for (i in detectionValid.indices) {
                if (
                    i < endpointGuard ||
                    i >
                    detectionValid.lastIndex -
                        endpointGuard
                ) {
                    detectionValid[i] = false
                }
            }
        }

        return detectionValid
    }

    private fun detectNarrowFeatures(
        extraction: SpectrumExtraction,
        settings: SpectrumAnalysisSettings,
        detectionValid: BooleanArray
    ): List<SpectralFeature> {
        val baseSigma =
            settings.detectionSigma
                .coerceIn(
                    2.0,
                    10.0
                )
        val thresholds =
            listOf(
                max(
                    2.0,
                    baseSigma - 0.5
                ),
                baseSigma,
                baseSigma + 0.5
            )
        val candidateSets =
            thresholds.map {
                    threshold ->
                findCandidateIndices(
                    extraction.residual,
                    extraction.localNoise,
                    detectionValid,
                    extraction
                        .contaminationProbability,
                    threshold,
                    settings.detectionMode
                )
            }
        val primary =
            candidateSets[1]
        val sampleSpacing =
            meanSampleSpacingPx(
                extraction.distancesPx
            )
        val tolerancePx =
            max(
                2.0,
                sampleSpacing * 2.5
            )

        return primary.mapIndexed {
                ordinal,
                index ->
            val amplitude =
                extraction.residual[index]
            val type =
                if (amplitude >= 0.0) {
                    FeatureType.Emission
                } else {
                    FeatureType.Absorption
                }
            val prominence =
                localProminence(
                    extraction.residual,
                    index,
                    type
                )
            val snr =
                abs(amplitude) /
                    extraction
                        .localNoise[index]
                        .coerceAtLeast(
                            1e-6
                        )
            val stability =
                candidateSets.count {
                        set ->
                    set.any {
                            other ->
                        abs(
                            extraction
                                .distancesPx[other] -
                                extraction
                                    .distancesPx[index]
                        ) <= tolerancePx
                    }
                }.toDouble() /
                    candidateSets.size
            val fwhmPx =
                estimateFwhm(
                    extraction.residual,
                    extraction.distancesPx,
                    index
                )
            val wavelength =
                extraction.wavelengthAt(
                    index
                )
            val fwhmNm =
                extraction.calibration
                    ?.let {
                        calibration ->
                        fwhmPx *
                            calibration
                                .dispersionNmPerPx(
                                    extraction
                                        .distancesPx[index]
                                )
                    }
            val normalizedSnr =
                (
                    1.0 -
                        exp(
                            -max(
                                0.0,
                                snr - 1.0
                            ) /
                                3.0
                        )
                    )
                    .coerceIn(
                        0.0,
                        1.0
                    )
            val prominenceScore =
                (
                    prominence /
                        (
                            abs(amplitude) +
                                extraction
                                    .localNoise[index]
                            )
                    )
                    .coerceIn(
                        0.0,
                        1.0
                    )
            val contaminationClean =
                1.0 -
                    extraction
                        .contaminationProbability[
                            index
                        ]
            val searchZone =
                SpectrumFeatureSearchQa
                    .zoneFor(
                        extraction,
                        index
                    )
            val confidence =
                featureConfidence(
                    normalizedSnr =
                        normalizedSnr,
                    stability =
                        stability,
                    prominenceScore =
                        prominenceScore,
                    contaminationClean =
                        contaminationClean,
                    searchZone =
                        searchZone
                )

            SpectralFeature(
                id = "N${ordinal + 1}",
                index = index,
                distancePx =
                    extraction
                        .distancesPx[index],
                wavelengthNm =
                    wavelength,
                type = type,
                signal =
                    extraction.corrected[
                        index
                    ],
                continuum =
                    extraction.continuum[
                        index
                    ],
                amplitude =
                    amplitude,
                prominence =
                    prominence,
                fwhmPx =
                    fwhmPx,
                fwhmNm =
                    fwhmNm,
                snr = snr,
                stability =
                    stability,
                contaminationProbability =
                    extraction
                        .contaminationProbability[
                            index
                        ],
                confidence =
                    confidence,
                searchZone =
                    searchZone,
                detectionMethod =
                    FeatureDetectionMethod
                        .Narrow,
                detectionScalePx =
                    sampleSpacing,
                supportingScalesPx =
                    listOf(
                        sampleSpacing
                    )
            )
        }
    }

    private fun detectBroadFeatures(
        extraction: SpectrumExtraction,
        settings: SpectrumAnalysisSettings,
        detectionValid: BooleanArray
    ): List<SpectralFeature> {
        val sampleCount =
            extraction.corrected.size
        if (sampleCount < 80) {
            return emptyList()
        }

        val endpointGuard =
            SpectrumFeatureSearchQa
                .endpointGuardSamples(
                    sampleCount
                )
        // These are core-widths, not arbitrary lower significance
        // thresholds. Averaging over the feature width raises the
        // significance of broad coherent structure while retaining the
        // same user-selected sigma criterion.
        val scaleSamples =
            listOf(
                7,
                13,
                21,
                33,
                49
            )
                .filter {
                    scale ->
                    sampleCount >
                        2 *
                        (
                            endpointGuard +
                                scale * 2
                            ) +
                        4
                }

        if (scaleSamples.isEmpty()) {
            return emptyList()
        }

        val scans =
            scaleSamples.mapNotNull {
                    scale ->
                broadScaleScan(
                    extraction =
                        extraction,
                    detectionValid =
                        detectionValid,
                    scaleSamples =
                        scale
                )
            }

        if (scans.isEmpty()) {
            return emptyList()
        }

        val baseSigma =
            settings.detectionSigma
                .coerceIn(
                    2.0,
                    10.0
                )
        val thresholds =
            listOf(
                max(
                    2.0,
                    baseSigma - 0.5
                ),
                baseSigma,
                baseSigma + 0.5
            )

        val candidates =
            mutableListOf<
                BroadCandidate
                >()

        scans.forEach {
                scan ->
            val thresholdSets =
                thresholds.map {
                        threshold ->
                    findBroadCandidateIndices(
                        scan = scan,
                        detectionValid =
                            detectionValid,
                        contamination =
                            extraction
                                .contaminationProbability,
                        threshold =
                            threshold,
                        mode =
                            settings
                                .detectionMode
                    )
                }

            val primary =
                thresholdSets[1]

            for (index in primary) {
                val response =
                    scan.response[index]
                val type =
                    if (response >= 0.0) {
                        FeatureType.Emission
                    } else {
                        FeatureType.Absorption
                    }
                val snr =
                    abs(response) /
                        scan.noise[index]
                            .coerceAtLeast(
                                1e-6
                            )
                val tolerance =
                    max(
                        2,
                        scan.scaleSamples /
                            3
                    )
                val stability =
                    thresholdSets.count {
                            set ->
                        set.any {
                            other ->
                            abs(
                                other -
                                    index
                            ) <= tolerance
                        }
                    }.toDouble() /
                        thresholdSets.size

                candidates +=
                    BroadCandidate(
                        index = index,
                        type = type,
                        scaleSamples =
                            scan.scaleSamples,
                        scalePx =
                            scan.scalePx,
                        response =
                            response,
                        snr = snr,
                        thresholdStability =
                            stability
                    )
            }
        }

        if (candidates.isEmpty()) {
            return emptyList()
        }

        val clusters =
            mutableListOf<
                MutableList<
                    BroadCandidate
                    >
                >()

        for (
            candidate in
            candidates.sortedBy {
                extraction
                    .distancesPx[
                        it.index
                    ]
            }
        ) {
            val candidateDistance =
                extraction
                    .distancesPx[
                        candidate.index
                    ]

            val existing =
                clusters.firstOrNull {
                        cluster ->
                    cluster.any {
                            member ->
                        if (
                            member.type !=
                            candidate.type
                        ) {
                            false
                        } else {
                            val memberDistance =
                                extraction
                                    .distancesPx[
                                        member.index
                                    ]
                            val tolerancePx =
                                max(
                                    3.0,
                                    min(
                                        member.scalePx,
                                        candidate.scalePx
                                    ) *
                                        0.75
                                )

                            abs(
                                memberDistance -
                                    candidateDistance
                            ) <= tolerancePx
                        }
                    }
                }

            if (existing == null) {
                clusters +=
                    mutableListOf(
                        candidate
                    )
            } else {
                existing +=
                    candidate
            }
        }

        val out =
            mutableListOf<
                SpectralFeature
                >()

        for (cluster in clusters) {
            val supportingScales =
                cluster
                    .map {
                        it.scalePx
                    }
                    .distinct()
                    .sorted()

            val strongest =
                cluster.maxByOrNull {
                    it.snr
                }
                    ?: continue

            // Require genuine multi-scale support at ordinary
            // significance. A single-scale feature is retained only when
            // it clears the user's threshold by a large margin.
            if (
                supportingScales.size < 2 &&
                !(
                    strongest.thresholdStability >= 0.99 &&
                        strongest.snr >=
                        baseSigma + 0.5
                    )
            ) {
                continue
            }

            val scan =
                scans.firstOrNull {
                    it.scaleSamples ==
                        strongest
                            .scaleSamples
                }
                    ?: continue

            val index =
                strongest.index
            val type =
                strongest.type
            val fwhmPx =
                estimateFwhm(
                    scan.response,
                    extraction
                        .distancesPx,
                    index
                )
                    .coerceAtLeast(
                        strongest
                            .scalePx *
                            0.5
                    )
            val wavelength =
                extraction.wavelengthAt(
                    index
                )
            val fwhmNm =
                extraction.calibration
                    ?.let {
                        calibration ->
                        fwhmPx *
                            calibration
                                .dispersionNmPerPx(
                                    extraction
                                        .distancesPx[
                                            index
                                        ]
                                )
                    }
            val prominence =
                localProminence(
                    scan.response,
                    index,
                    type
                )
            val scaleSupport =
                (
                    supportingScales.size /
                        min(
                            3.0,
                            scans.size
                                .toDouble()
                        )
                    )
                    .coerceIn(
                        0.0,
                        1.0
                    )
            val thresholdStability =
                cluster.map {
                    it.thresholdStability
                }.average()
            val stability =
                (
                    0.55 *
                        thresholdStability +
                        0.45 *
                        scaleSupport
                    )
                    .coerceIn(
                        0.0,
                        1.0
                    )
            val normalizedSnr =
                (
                    1.0 -
                        exp(
                            -max(
                                0.0,
                                strongest.snr -
                                    1.0
                            ) /
                                3.0
                        )
                    )
                    .coerceIn(
                        0.0,
                        1.0
                    )
            val prominenceScore =
                (
                    prominence /
                        (
                            abs(
                                strongest
                                    .response
                            ) +
                                scan.noise[index]
                            )
                    )
                    .coerceIn(
                        0.0,
                        1.0
                    )
            val contaminationClean =
                1.0 -
                    extraction
                        .contaminationProbability[
                            index
                        ]
            val searchZone =
                SpectrumFeatureSearchQa
                    .zoneFor(
                        extraction,
                        index
                    )
            val confidence =
                featureConfidence(
                    normalizedSnr =
                        normalizedSnr,
                    stability =
                        stability,
                    prominenceScore =
                        prominenceScore,
                    contaminationClean =
                        contaminationClean,
                    searchZone =
                        searchZone
                )

            if (
                !strongest.response.isFinite() ||
                !strongest.snr.isFinite() ||
                !prominence.isFinite() ||
                !fwhmPx.isFinite() ||
                !stability.isFinite() ||
                !confidence.isFinite()
            ) {
                continue
            }

            out +=
                SpectralFeature(
                    id =
                        "B${out.size + 1}",
                    index =
                        index,
                    distancePx =
                        extraction
                            .distancesPx[
                                index
                            ],
                    wavelengthNm =
                        wavelength,
                    type =
                        type,
                    signal =
                        extraction
                            .corrected[
                                index
                            ],
                    continuum =
                        extraction
                            .continuum[
                                index
                            ],
                    amplitude =
                        strongest
                            .response,
                    prominence =
                        prominence,
                    fwhmPx =
                        fwhmPx,
                    fwhmNm =
                        fwhmNm,
                    snr =
                        strongest.snr,
                    stability =
                        stability,
                    contaminationProbability =
                        extraction
                            .contaminationProbability[
                                index
                            ],
                    confidence =
                        confidence,
                    searchZone =
                        searchZone,
                    detectionMethod =
                        FeatureDetectionMethod
                            .BroadMultiScale,
                    detectionScalePx =
                        strongest
                            .scalePx,
                    supportingScalesPx =
                        supportingScales
                )
        }

        return out
    }

    private fun broadScaleScan(
        extraction: SpectrumExtraction,
        detectionValid: BooleanArray,
        scaleSamples: Int
    ): BroadScaleScan? {
        if (
            scaleSamples < 3 ||
            extraction.corrected.size !=
            detectionValid.size
        ) {
            return null
        }

        val n =
            extraction.corrected.size
        val response =
            DoubleArray(n) {
                Double.NaN
            }
        val propagatedNoise =
            DoubleArray(n) {
                Double.NaN
            }

        val half =
            scaleSamples / 2
        val gap =
            max(
                1,
                scaleSamples / 4
            )

        fun meanInRange(
            from: Int,
            to: Int
        ): BroadWindowMean? {
            if (
                from < 0 ||
                to >= n ||
                from > to
            ) {
                return null
            }

            var sum = 0.0
            var count = 0

            for (i in from..to) {
                if (
                    !detectionValid[i] ||
                    extraction
                        .contaminationProbability[
                            i
                        ] >= 0.80
                ) {
                    continue
                }

                val value =
                    extraction.corrected[i]
                if (!value.isFinite()) {
                    continue
                }

                sum += value
                count++
            }

            val requested =
                to -
                    from +
                    1
            if (
                count <
                max(
                    2,
                    (
                        requested *
                            0.72
                        )
                        .toInt()
                )
            ) {
                return null
            }

            return BroadWindowMean(
                value =
                    sum /
                        count
                            .coerceAtLeast(
                                1
                            ),
                count =
                    count
            )
        }

        fun localNoiseMedian(
            from: Int,
            to: Int
        ): Double {
            if (
                from < 0 ||
                to >= n ||
                from > to
            ) {
                return Double.NaN
            }

            val values =
                mutableListOf<
                    Double
                    >()
            for (i in from..to) {
                if (!detectionValid[i]) {
                    continue
                }
                val value =
                    extraction
                        .localNoise[i]
                if (
                    value.isFinite() &&
                    value > 0.0
                ) {
                    values += value
                }
            }

            return median(
                values.toDoubleArray()
            )
        }

        for (i in 0 until n) {
            val coreStart =
                i - half
            val coreEnd =
                i + half

            val leftEnd =
                coreStart -
                    gap -
                    1
            val leftStart =
                leftEnd -
                    scaleSamples +
                    1

            val rightStart =
                coreEnd +
                    gap +
                    1
            val rightEnd =
                rightStart +
                    scaleSamples -
                    1

            if (
                leftStart < 0 ||
                rightEnd >= n
            ) {
                continue
            }

            val core =
                meanInRange(
                    coreStart,
                    coreEnd
                ) ?: continue
            val left =
                meanInRange(
                    leftStart,
                    leftEnd
                ) ?: continue
            val right =
                meanInRange(
                    rightStart,
                    rightEnd
                ) ?: continue

            val localBaseline =
                (
                    left.value +
                        right.value
                    ) /
                    2.0
            response[i] =
                core.value -
                    localBaseline

            val noiseMedian =
                localNoiseMedian(
                    leftStart,
                    rightEnd
                )

            if (
                noiseMedian.isFinite() &&
                noiseMedian >
                0.0
            ) {
                val propagated =
                    noiseMedian *
                        1.35 *
                        sqrt(
                            1.0 /
                                core.count
                                    .coerceAtLeast(
                                        1
                                    ) +
                                0.25 *
                                (
                                    1.0 /
                                        left.count
                                            .coerceAtLeast(
                                                1
                                            ) +
                                        1.0 /
                                        right.count
                                            .coerceAtLeast(
                                                1
                                            )
                                    )
                        )

                propagatedNoise[i] =
                    propagated
                        .coerceAtLeast(
                            1e-6
                        )
            }
        }

        val empiricalNoise =
            rollingMad(
                response,
                max(
                    31,
                    scaleSamples * 7
                )
            )
        val noise =
            DoubleArray(n) {
                    index ->
                val propagated =
                    propagatedNoise[
                        index
                    ]
                val empirical =
                    empiricalNoise[
                        index
                    ]

                when {
                    propagated
                        .isFinite() &&
                        empirical
                            .isFinite() ->
                        max(
                            propagated,
                            empirical
                        )

                    propagated
                        .isFinite() ->
                        propagated

                    empirical
                        .isFinite() ->
                        empirical

                    else ->
                        1e-6
                }
                    .coerceAtLeast(
                        1e-6
                    )
            }

        return BroadScaleScan(
            scaleSamples =
                scaleSamples,
            scalePx =
                scaleSamples *
                    meanSampleSpacingPx(
                        extraction
                            .distancesPx
                    ),
            response =
                response,
            noise =
                noise
        )
    }

    private fun findBroadCandidateIndices(
        scan: BroadScaleScan,
        detectionValid: BooleanArray,
        contamination: DoubleArray,
        threshold: Double,
        mode: String
    ): List<Int> {
        val out =
            mutableListOf<Int>()
        val radius =
            max(
                2,
                scan.scaleSamples /
                    4
            )
        val minSeparation =
            max(
                3,
                scan.scaleSamples /
                    2
            )

        for (
            i in
            radius until
                scan.response.size -
                radius
        ) {
            if (
                !detectionValid[i] ||
                contamination[i] >=
                0.80
            ) {
                continue
            }

            val response =
                scan.response[i]
            val noise =
                scan.noise[i]

            if (
                !response.isFinite() ||
                !noise.isFinite() ||
                noise <= 0.0
            ) {
                continue
            }

            val z =
                response /
                    noise

            val emissionAllowed =
                mode != "absorption"
            val absorptionAllowed =
                mode != "emission"

            val localValues =
                (
                    i -
                        radius..
                        i +
                        radius
                    )
                    .filter {
                        it != i &&
                            scan.response[
                                it
                            ].isFinite()
                    }

            if (localValues.isEmpty()) {
                continue
            }

            val emission =
                emissionAllowed &&
                    z >= threshold &&
                    localValues.all {
                        other ->
                        response >=
                            scan.response[
                                other
                            ]
                    }

            val absorption =
                absorptionAllowed &&
                    z <=
                    -threshold &&
                    localValues.all {
                        other ->
                        response <=
                            scan.response[
                                other
                            ]
                    }

            if (
                !emission &&
                !absorption
            ) {
                continue
            }

            if (
                out.isNotEmpty() &&
                i -
                    out.last() <=
                minSeparation
            ) {
                val previous =
                    out.last()
                val previousZ =
                    scan.response[
                        previous
                    ] /
                        scan.noise[
                            previous
                        ]
                            .coerceAtLeast(
                                1e-6
                            )

                if (
                    abs(z) >
                    abs(previousZ)
                ) {
                    out[
                        out.lastIndex
                    ] =
                        i
                }
            } else {
                out += i
            }
        }

        return out
    }

    private fun featureConfidence(
        normalizedSnr: Double,
        stability: Double,
        prominenceScore: Double,
        contaminationClean: Double,
        searchZone: FeatureSearchZone
    ): Double {
        val zoneWeight =
            when (searchZone) {
                FeatureSearchZone
                    .AnchorBracketed ->
                    1.0

                FeatureSearchZone
                    .Extrapolated ->
                    0.72

                FeatureSearchZone
                    .EndpointGuard ->
                    0.0

                FeatureSearchZone
                    .Uncalibrated ->
                    0.90
            }

        return (
            (
                0.42 *
                    normalizedSnr +
                    0.28 *
                    stability +
                    0.18 *
                    prominenceScore +
                    0.12 *
                    contaminationClean
                ) *
                zoneWeight
            )
            .coerceIn(
                0.0,
                1.0
            )
    }

    private fun mergeFeatureDetections(
        narrow: List<SpectralFeature>,
        broad: List<SpectralFeature>
    ): List<SpectralFeature> {
        val merged =
            mutableListOf<
                SpectralFeature
                >()

        val ordered =
            (
                narrow +
                    broad
                )
                .sortedByDescending {
                    it.confidence
                }

        for (candidate in ordered) {
            val duplicateIndex =
                merged.indexOfFirst {
                        existing ->
                    if (
                        existing.type !=
                        candidate.type
                    ) {
                        false
                    } else if (
                        existing.detectionMethod ==
                        FeatureDetectionMethod
                            .Narrow &&
                        candidate.detectionMethod ==
                        FeatureDetectionMethod
                            .Narrow
                    ) {
                        false
                    } else {
                        val tolerance =
                            max(
                                3.0,
                                0.35 *
                                    (
                                        existing
                                            .fwhmPx
                                            .coerceAtLeast(
                                                1.0
                                            ) +
                                            candidate
                                                .fwhmPx
                                                .coerceAtLeast(
                                                    1.0
                                                )
                                        )
                            )

                        abs(
                            existing
                                .distancePx -
                                candidate
                                    .distancePx
                        ) <= tolerance
                    }
                }

            if (duplicateIndex < 0) {
                merged +=
                    candidate
                continue
            }

            val existing =
                merged[
                    duplicateIndex
                ]
            val best =
                if (
                    candidate.confidence >
                    existing.confidence
                ) {
                    candidate
                } else {
                    existing
                }
            val broadEvidence =
                listOf(
                    existing,
                    candidate
                )
                    .firstOrNull {
                        it.detectionMethod !=
                            FeatureDetectionMethod
                                .Narrow
                    }
            val supportingScales =
                (
                    existing
                        .supportingScalesPx +
                        candidate
                            .supportingScalesPx
                    )
                    .filter {
                        it.isFinite() &&
                            it > 0.0
                    }
                    .distinct()
                    .sorted()

            merged[
                duplicateIndex
            ] =
                best.copy(
                    detectionMethod =
                        FeatureDetectionMethod
                            .NarrowAndBroad,
                    detectionScalePx =
                        broadEvidence
                            ?.detectionScalePx
                            ?: best
                                .detectionScalePx,
                    supportingScalesPx =
                        supportingScales,
                    fwhmPx =
                        broadEvidence
                            ?.fwhmPx
                            ?: best.fwhmPx,
                    fwhmNm =
                        broadEvidence
                            ?.fwhmNm
                            ?: best.fwhmNm,
                    snr =
                        max(
                            existing.snr,
                            candidate.snr
                        ),
                    stability =
                        max(
                            existing
                                .stability,
                            candidate
                                .stability
                        ),
                    confidence =
                        (
                            max(
                                existing
                                    .confidence,
                                candidate
                                    .confidence
                            ) +
                                0.08 *
                                (
                                    1.0 -
                                        max(
                                            existing
                                                .confidence,
                                            candidate
                                                .confidence
                                        )
                                    )
                            )
                            .coerceIn(
                                0.0,
                                1.0
                            )
                )
        }

        return merged
            .filter {
                it.searchZone !=
                    FeatureSearchZone
                        .EndpointGuard
            }
            .sortedBy {
                it.distancePx
            }
            .mapIndexed {
                    index,
                    feature ->
                feature.copy(
                    id =
                        "F${index + 1}"
                )
            }
    }

    private fun meanSampleSpacingPx(
        distances: DoubleArray
    ): Double {
        if (distances.size < 2) {
            return 1.0
        }

        val steps =
            mutableListOf<Double>()

        for (
            i in
            1 until distances.size
        ) {
            val step =
                abs(
                    distances[i] -
                        distances[i - 1]
                )
            if (
                step.isFinite() &&
                step > 0.0
            ) {
                steps += step
            }
        }

        return if (steps.isEmpty()) {
            1.0
        } else {
            median(
                steps.toDoubleArray()
            )
                .coerceAtLeast(
                    1e-6
                )
        }
    }

    private fun findCandidateIndices(
        residual: DoubleArray,
        noise: DoubleArray,
        valid: BooleanArray,
        contamination: DoubleArray,
        threshold: Double,
        mode: String
    ): List<Int> {
        val out = mutableListOf<Int>()
        val minSeparation = 3
        for (i in 2 until residual.size - 2) {
            if (!valid[i] || contamination[i] >= 0.80) continue
            val z = residual[i] / noise[i].coerceAtLeast(1e-6)
            val emission = mode != "absorption" && z >= threshold && residual[i] >= residual[i - 1] && residual[i] > residual[i + 1]
            val absorption = mode != "emission" && z <= -threshold && residual[i] <= residual[i - 1] && residual[i] < residual[i + 1]
            if (!emission && !absorption) continue
            if (out.isNotEmpty() && i - out.last() <= minSeparation) {
                val previous = out.last()
                if (abs(z) > abs(residual[previous] / noise[previous].coerceAtLeast(1e-6))) out[out.lastIndex] = i
            } else {
                out += i
            }
        }
        return out
    }

    private fun localProminence(residual: DoubleArray, index: Int, type: FeatureType): Double {
        if (index !in residual.indices || !residual[index].isFinite()) return 0.0

        val radius = 12
        val from = max(0, index - radius)
        val to = min(residual.lastIndex, index + radius)
        val left = if (index > from) {
            residual.sliceArray(from until index).filter { it.isFinite() }
        } else {
            emptyList()
        }
        val right = if (index < to) {
            residual.sliceArray(index + 1..to).filter { it.isFinite() }
        } else {
            emptyList()
        }

        if (left.isEmpty() || right.isEmpty()) return 0.0

        return when (type) {
            FeatureType.Emission ->
                max(0.0, residual[index] - max(left.minOrNull() ?: residual[index], right.minOrNull() ?: residual[index]))

            FeatureType.Absorption ->
                max(0.0, min(left.maxOrNull() ?: residual[index], right.maxOrNull() ?: residual[index]) - residual[index])
        }
    }

    private fun estimateFwhm(residual: DoubleArray, distances: DoubleArray, index: Int): Double {
        val peak = residual[index]
        val half = peak / 2.0
        var left = index
        var right = index
        if (peak >= 0) {
            while (left > 0 && residual[left] > half) left--
            while (right < residual.lastIndex && residual[right] > half) right++
        } else {
            while (left > 0 && residual[left] < half) left--
            while (right < residual.lastIndex && residual[right] < half) right++
        }
        return max(0.0, distances[right] - distances[left])
    }

    private fun rollingMedian(values: DoubleArray, rawWindow: Int): DoubleArray {
        if (values.isEmpty()) return values
        val window = forceOdd(rawWindow.coerceAtLeast(3))
        val half = window / 2
        return DoubleArray(values.size) { i ->
            val from = max(0, i - half)
            val to = min(values.lastIndex, i + half)
            median(values.sliceArray(from..to))
        }
    }

    private fun rollingMad(values: DoubleArray, rawWindow: Int): DoubleArray {
        val window = forceOdd(rawWindow.coerceAtLeast(5))
        val half = window / 2
        return DoubleArray(values.size) { i ->
            val from = max(0, i - half)
            val to = min(values.lastIndex, i + half)
            val slice = values.sliceArray(from..to)
            val centre = median(slice)
            (1.4826 * mad(slice, centre)).coerceAtLeast(1e-6)
        }
    }

    private fun movingAverage(values: DoubleArray, rawWindow: Int): DoubleArray {
        if (values.isEmpty()) return values
        val window = forceOdd(rawWindow.coerceAtLeast(1))
        val half = window / 2
        return DoubleArray(values.size) { i ->
            val from = max(0, i - half)
            val to = min(values.lastIndex, i + half)
            var sum = 0.0
            for (j in from..to) sum += values[j]
            sum / (to - from + 1)
        }
    }

    private fun interpolateInvalid(values: DoubleArray, valid: BooleanArray) {
        for (i in values.indices) {
            if (valid[i]) continue
            var left = i - 1
            while (left >= 0 && !valid[left]) left--
            var right = i + 1
            while (right < values.size && !valid[right]) right++
            values[i] = when {
                left >= 0 && right < values.size -> {
                    val t = (i - left).toDouble() / (right - left)
                    values[left] * (1.0 - t) + values[right] * t
                }
                left >= 0 -> values[left]
                right < values.size -> values[right]
                else -> 0.0
            }
        }
    }

    private fun median(values: DoubleArray): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.filter { it.isFinite() }.sorted()
        if (sorted.isEmpty()) return 0.0
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2.0 else sorted[mid]
    }

    private fun mad(values: DoubleArray, centre: Double): Double = median(DoubleArray(values.size) { abs(values[it] - centre) })

    private fun forceOdd(value: Int): Int = if (value % 2 == 0) value + 1 else value

    private fun polynomial(coefficients: DoubleArray, x: Double): Double {
        var power = 1.0
        var out = 0.0
        for (coefficient in coefficients) {
            out += coefficient * power
            power *= x
        }
        return out
    }

    private fun solveLinearSystem(a: Array<DoubleArray>, b: DoubleArray): DoubleArray {
        val n = b.size
        val m = Array(n) { r -> DoubleArray(n + 1) { c -> if (c < n) a[r][c] else b[r] } }
        for (col in 0 until n) {
            var pivot = col
            for (r in col + 1 until n) if (abs(m[r][col]) > abs(m[pivot][col])) pivot = r
            require(abs(m[pivot][col]) > 1e-12) { "Calibration anchors do not span enough distinct pixel positions." }
            val tmp = m[col]; m[col] = m[pivot]; m[pivot] = tmp
            val divisor = m[col][col]
            for (c in col until n + 1) m[col][c] /= divisor
            for (r in 0 until n) {
                if (r == col) continue
                val factor = m[r][col]
                for (c in col until n + 1) m[r][c] -= factor * m[col][c]
            }
        }
        return DoubleArray(n) { m[it][n] }
    }

    private class GrayRaster(bitmap: Bitmap) {
        private val width = bitmap.width
        private val height = bitmap.height
        private val pixels = IntArray(width * height).also { bitmap.getPixels(it, 0, width, 0, 0, width, height) }

        fun inside(x: Double, y: Double, margin: Int = 0): Boolean =
            x >= margin && y >= margin && x < width - margin && y < height - margin

        fun sample(x: Double, y: Double): Double {
            if (x < 0.0 || y < 0.0 || x >= width - 1.0 || y >= height - 1.0) return 0.0
            val x0 = floor(x).toInt()
            val y0 = floor(y).toInt()
            val x1 = x0 + 1
            val y1 = y0 + 1
            val tx = x - x0
            val ty = y - y0
            val a = luminance(pixels[y0 * width + x0])
            val b = luminance(pixels[y0 * width + x1])
            val c = luminance(pixels[y1 * width + x0])
            val d = luminance(pixels[y1 * width + x1])
            val top = a * (1.0 - tx) + b * tx
            val bottom = c * (1.0 - tx) + d * tx
            return top * (1.0 - ty) + bottom * ty
        }

        private fun luminance(argb: Int): Double {
            /*
             * This is intentionally NOT display luminance.
             *
             * A stellar spectrum changes hue along the dispersion axis, so BT.709
             * perceptual weights (0.2126 R + 0.7152 G + 0.0722 B) manufacture a
             * large green-biased continuum shape. For spectral extraction we first
             * approximately undo sRGB/JPEG gamma, then sum the three linear-light
             * camera channels. The result is still relative image signal, not
             * radiometrically calibrated flux, but it preserves absorption structure
             * far better across colour transitions.
             */
            fun linearChannel(value: Int): Double {
                val s = value.coerceIn(0, 255) / 255.0
                return if (s <= 0.04045) {
                    s / 12.92
                } else {
                    ((s + 0.055) / 1.055).pow(2.4)
                }
            }

            val r = linearChannel(argb shr 16 and 0xff)
            val g = linearChannel(argb shr 8 and 0xff)
            val b = linearChannel(argb and 0xff)
            return (r + g + b) / 3.0
        }
    }
}
