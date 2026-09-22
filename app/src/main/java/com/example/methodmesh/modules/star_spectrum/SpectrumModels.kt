package com.example.methodmesh.modules.star_spectrum

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

internal data class SpectrumPoint(
    val index: Int,
    val distancePx: Double,
    val traceX: Double,
    val traceY: Double,
    val wavelengthNm: Double?,
    val rawSignal: Double,
    val backgroundSignal: Double,
    val correctedSignal: Double,
    val continuum: Double,
    val residual: Double,
    val localNoise: Double,
    val localSnr: Double,
    val contaminationFraction: Double,
    val contaminationProbability: Double,
    val valid: Boolean
)

internal data class SpectralFeature(
    val id: String,
    val index: Int,
    val distancePx: Double,
    val wavelengthNm: Double?,
    val type: FeatureType,
    val signal: Double,
    val continuum: Double,
    val amplitude: Double,
    val prominence: Double,
    val fwhmPx: Double,
    val fwhmNm: Double?,
    val snr: Double,
    val stability: Double,
    val contaminationProbability: Double,
    val confidence: Double,
    val searchZone: FeatureSearchZone = FeatureSearchZone.Uncalibrated,
    val detectionMethod: FeatureDetectionMethod = FeatureDetectionMethod.Narrow,
    val detectionScalePx: Double = 0.0,
    val supportingScalesPx: List<Double> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        fun putFinite(key: String, value: Double) {
            if (value.isFinite()) put(key, value)
        }

        put("id", id)
        put("index", index)
        putFinite("distance_px", distancePx)
        wavelengthNm?.takeIf { it.isFinite() }?.let { put("wavelength_nm", it) }
        put("type", type.wireValue)
        putFinite("signal", signal)
        putFinite("continuum", continuum)
        putFinite("amplitude", amplitude)
        putFinite("prominence", prominence)
        putFinite("fwhm_px", fwhmPx)
        fwhmNm?.takeIf { it.isFinite() }?.let { put("fwhm_nm", it) }
        putFinite("snr", snr)
        putFinite("stability", stability)
        putFinite("contamination_probability", contaminationProbability)
        putFinite("confidence", confidence)
        put("feature_search_zone", searchZone.wireValue)
        put("detection_method", detectionMethod.wireValue)
        putFinite("detection_scale_px", detectionScalePx)
        put(
            "supporting_scales_px",
            JSONArray().apply {
                supportingScalesPx
                    .filter { it.isFinite() }
                    .distinct()
                    .sorted()
                    .forEach(::put)
            }
        )
    }
}

internal enum class FeatureDetectionMethod(
    val wireValue: String
) {
    Narrow("narrow"),
    BroadMultiScale("broad_multiscale"),
    NarrowAndBroad("narrow_and_broad")
}

internal enum class FeatureType(val wireValue: String) {
    Emission("emission"),
    Absorption("absorption")
}

internal enum class FeatureSearchZone(val wireValue: String) {
    AnchorBracketed("anchor_bracketed"),
    Extrapolated("extrapolated"),
    EndpointGuard("endpoint_guard"),
    Uncalibrated("uncalibrated")
}

internal data class SpectrumTrace(
    val userStartX: Double,
    val userStartY: Double,
    val userEndX: Double,
    val userEndY: Double,
    val sampleX: DoubleArray,
    val sampleY: DoubleArray,
    val distancesPx: DoubleArray,
    val traceQuality: Double,
    val meanAbsoluteCorrectionPx: Double,
    val apertureHalfWidthPx: Int,
    val ribbonHalfWidthPx: Int
)


internal data class SpectrumExtractionDiagnostics(
    val width: Int,
    val height: Int,
    val rawRectified: DoubleArray,
    val backgroundModel: DoubleArray,
    val backgroundSubtracted: DoubleArray,
    val spatialProfileWeights: DoubleArray,
    val fittedTargetModel: DoubleArray,
    val residualImage: DoubleArray,
    val standardizedResidualImage: DoubleArray,
    val cleanedRectified: DoubleArray,
    val rejectedMask: BooleanArray,
    val boxcarSignal: DoubleArray,
    val optimalSignal: DoubleArray,
    val optimalVariance: DoubleArray,
    val traceOffsetsPx: DoubleArray,
    val traceFitRmsPx: Double,
    val rejectedPixelFraction: Double,
    val residualAbove3SigmaFraction: Double,
    val residualAbove5SigmaFraction: Double,
    val maxAbsStandardizedResidual: Double,
    val profileUpdateIterations: Int,
    val rejectionSigma: Double,
    val extractionIterations: Int,
    val extractionMethod: String,
    val varianceSemantics: String
) {
    fun index(x: Int, y: Int): Int = y * width + x
}

internal data class SpectrumExtraction(
    val trace: SpectrumTrace,
    val distancesPx: DoubleArray,
    val raw: DoubleArray,
    val background: DoubleArray,
    val corrected: DoubleArray,
    val contaminationFraction: DoubleArray,
    val contaminationProbability: DoubleArray,
    val valid: BooleanArray,
    val localNoise: DoubleArray,
    val localSnr: DoubleArray,
    val continuum: DoubleArray,
    val residual: DoubleArray,
    val features: List<SpectralFeature>,
    val calibration: AppliedCalibration?,
    val diagnostics: SpectrumExtractionDiagnostics
) {
    val meanSnr: Double
        get() = localSnr.filter { it.isFinite() }.let { values -> if (values.isEmpty()) 0.0 else values.average() }

    val contaminatedFraction: Double
        get() = if (contaminationProbability.isEmpty()) 0.0 else contaminationProbability.count { it >= 0.5 }.toDouble() / contaminationProbability.size

    fun wavelengthAt(index: Int): Double? = calibration?.wavelength(distancesPx[index])

    fun points(): List<SpectrumPoint> = distancesPx.indices.map { i ->
        SpectrumPoint(
            index = i,
            distancePx = distancesPx[i],
            traceX = trace.sampleX[i],
            traceY = trace.sampleY[i],
            wavelengthNm = wavelengthAt(i),
            rawSignal = raw[i],
            backgroundSignal = background[i],
            correctedSignal = corrected[i],
            continuum = continuum[i],
            residual = residual[i],
            localNoise = localNoise[i],
            localSnr = localSnr[i],
            contaminationFraction = contaminationFraction[i],
            contaminationProbability = contaminationProbability[i],
            valid = valid[i]
        )
    }
}

internal object SpectrumFeatureSearchQa {
    fun endpointGuardSamples(sampleCount: Int): Int {
        if (sampleCount <= 4) return 0

        val proposed =
            maxOf(
                12,
                minOf(
                    40,
                    sampleCount / 25
                )
            )

        return proposed.coerceAtMost(
            maxOf(
                1,
                (sampleCount - 2) / 3
            )
        )
    }

    fun zoneFor(
        extraction: SpectrumExtraction,
        index: Int
    ): FeatureSearchZone {
        if (
            extraction.distancesPx.isEmpty() ||
            index !in extraction.distancesPx.indices
        ) {
            return FeatureSearchZone.Uncalibrated
        }

        val guard =
            endpointGuardSamples(
                extraction.distancesPx.size
            )

        if (
            index < guard ||
            index > extraction.distancesPx.lastIndex - guard
        ) {
            return FeatureSearchZone.EndpointGuard
        }

        val calibration =
            extraction.calibration
                ?: return FeatureSearchZone.Uncalibrated

        val wavelength =
            extraction.wavelengthAt(index)
                ?: return FeatureSearchZone.Uncalibrated

        val anchors =
            calibration.reference.anchors

        if (anchors.isEmpty()) {
            return FeatureSearchZone.Extrapolated
        }

        val minAnchor =
            anchors.minOf {
                it.wavelengthNm
            }
        val maxAnchor =
            anchors.maxOf {
                it.wavelengthNm
            }

        return if (
            wavelength >= minAnchor &&
            wavelength <= maxAnchor
        ) {
            FeatureSearchZone.AnchorBracketed
        } else {
            FeatureSearchZone.Extrapolated
        }
    }
}

internal data class CalibrationAnchor(
    val distancePx: Double,
    val wavelengthNm: Double,
    val label: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("distance_px", distancePx)
        put("wavelength_nm", wavelengthNm)
        put("label", label)
    }

    companion object {
        fun fromJson(obj: JSONObject): CalibrationAnchor = CalibrationAnchor(
            distancePx = obj.getDouble("distance_px"),
            wavelengthNm = obj.getDouble("wavelength_nm"),
            label = obj.optString("label")
        )
    }
}

internal data class SpectrumReference(
    val id: String,
    val name: String,
    val starName: String,
    val spectralType: String,
    val createdTimeIso: String,
    val imageWidthPx: Int,
    val imageHeightPx: Int,
    val traceStartX: Double,
    val traceStartY: Double,
    val traceEndX: Double,
    val traceEndY: Double,
    val polynomialOrder: Int,
    val coefficients: DoubleArray,
    val rmsNm: Double,
    val anchors: List<CalibrationAnchor>,
    val validMinDistancePx: Double,
    val validMaxDistancePx: Double,
    val anchorSource: String = "manual",
    val notes: String = ""
) {
    fun wavelength(distancePx: Double): Double {
        var power = 1.0
        var out = 0.0
        for (coefficient in coefficients) {
            out += coefficient * power
            power *= distancePx
        }
        return out
    }

    fun derivative(distancePx: Double): Double {
        if (coefficients.size < 2) return 0.0
        var power = 1.0
        var out = 0.0
        for (i in 1 until coefficients.size) {
            out += i * coefficients[i] * power
            power *= distancePx
        }
        return out
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("schema", "methodmesh.star_spectrum.reference.v1")
        put("id", id)
        put("name", name)
        put("star_name", starName)
        put("spectral_type", spectralType)
        put("created_time_iso", createdTimeIso)
        put("image_width_px", imageWidthPx)
        put("image_height_px", imageHeightPx)
        put("trace_start_x", traceStartX)
        put("trace_start_y", traceStartY)
        put("trace_end_x", traceEndX)
        put("trace_end_y", traceEndY)
        put("polynomial_order", polynomialOrder)
        put("coefficients", JSONArray().apply { coefficients.forEach(::put) })
        put("rms_nm", rmsNm)
        put("anchors", JSONArray().apply { anchors.forEach { put(it.toJson()) } })
        put("valid_min_distance_px", validMinDistancePx)
        put("valid_max_distance_px", validMaxDistancePx)
        put("anchor_source", anchorSource)
        put("notes", notes)
    }

    companion object {
        fun fromJson(obj: JSONObject): SpectrumReference {
            val coefficientArray = obj.getJSONArray("coefficients")
            val coefficients = DoubleArray(coefficientArray.length()) { coefficientArray.getDouble(it) }
            val anchorArray = obj.getJSONArray("anchors")
            val anchors = (0 until anchorArray.length()).map { CalibrationAnchor.fromJson(anchorArray.getJSONObject(it)) }
            return SpectrumReference(
                id = obj.getString("id"),
                name = obj.optString("name"),
                starName = obj.optString("star_name"),
                spectralType = obj.optString("spectral_type", "A0V"),
                createdTimeIso = obj.optString("created_time_iso"),
                imageWidthPx = obj.optInt("image_width_px"),
                imageHeightPx = obj.optInt("image_height_px"),
                traceStartX = obj.optDouble("trace_start_x"),
                traceStartY = obj.optDouble("trace_start_y"),
                traceEndX = obj.optDouble("trace_end_x"),
                traceEndY = obj.optDouble("trace_end_y"),
                polynomialOrder = obj.optInt("polynomial_order", coefficients.size - 1),
                coefficients = coefficients,
                rmsNm = obj.optDouble("rms_nm"),
                anchors = anchors,
                validMinDistancePx = obj.optDouble("valid_min_distance_px", anchors.minOfOrNull { it.distancePx } ?: 0.0),
                validMaxDistancePx = obj.optDouble("valid_max_distance_px", anchors.maxOfOrNull { it.distancePx } ?: 0.0),
                anchorSource = obj.optString("anchor_source", "manual"),
                notes = obj.optString("notes")
            )
        }
    }
}

internal data class AppliedCalibration(
    val reference: SpectrumReference,
    val distanceScale: Double,
    val registrationOffsetPx: Double,
    val confidence: Double,
    val warning: String = ""
) {
    fun referenceDistance(targetDistancePx: Double): Double = targetDistancePx / distanceScale + registrationOffsetPx
    fun wavelength(targetDistancePx: Double): Double = reference.wavelength(referenceDistance(targetDistancePx))
    fun dispersionNmPerPx(targetDistancePx: Double): Double = abs(reference.derivative(referenceDistance(targetDistancePx)) / distanceScale)
}

internal data class CalibrationFit(
    val order: Int,
    val coefficients: DoubleArray,
    val rmsNm: Double,
    val predictedNm: DoubleArray
)

internal data class SpectrumAnalysisSettings(
    val ribbonHalfWidthPx: Int = 28,
    val apertureHalfWidthPx: Int = 5,
    val backgroundGapPx: Int = 4,
    val continuumWindow: Int = 51,
    val detectionSigma: Double = 3.5,
    val detectionMode: String = "both"
)

internal object HydrogenBalmerLines {
    val standard = listOf(
        CalibrationLine("Hδ", 410.17),
        CalibrationLine("Hγ", 434.05),
        CalibrationLine("Hβ", 486.13),
        CalibrationLine("Hα", 656.28)
    )
}

internal data class CalibrationLine(val label: String, val wavelengthNm: Double)
