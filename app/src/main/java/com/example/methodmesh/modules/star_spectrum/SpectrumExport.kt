package com.example.methodmesh.modules.star_spectrum

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

internal data class SpectrumArtifacts(
    val annotatedImage: File,
    val dataCsv: File,
    val peaksCsv: File,
    val metadataJson: File
)

internal data class SpectrumCommitPackage(
    val sourceImage: File?,
    val featuresJson: File,
    val provenanceJson: File,
    val manifestJson: File,
    val bundleZip: File
)

internal data class SpectrumReferencePackage(
    val sourceImage: File?,
    val annotatedImage: File,
    val spectrumCsv: File,
    val anchorsCsv: File,
    val referenceJson: File,
    val provenanceJson: File,
    val manifestJson: File,
    val bundleZip: File
)

internal object SpectrumExport {
    private val stampFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

    fun writeAnalysisArtifacts(
        context: Context,
        source: Bitmap,
        extraction: SpectrumExtraction,
        sourceLabel: String,
        settings: SpectrumAnalysisSettings
    ): SpectrumArtifacts {
        val stamp = stampFormatter.format(Instant.now())
        val base = "methodmesh_star_spectrum_$stamp"
        val image = File(context.cacheDir, "${base}_annotated.png")
        val data = File(context.cacheDir, "${base}_spectrum.csv")
        val peaks = File(context.cacheDir, "${base}_features.csv")
        val metadata = File(context.cacheDir, "${base}_metadata.json")
        writeAnnotatedImage(source, extraction, image)
        writeSpectrumCsv(extraction, data)
        writeFeaturesCsv(extraction.features, peaks)
        metadata.writeText(metadataJson(extraction, sourceLabel, settings).toString(2))
        return SpectrumArtifacts(image, data, peaks, metadata)
    }

    fun writeAnalysisCommitPackage(
        context: Context,
        sourceImage: File?,
        artifacts: SpectrumArtifacts,
        extraction: SpectrumExtraction,
        settings: SpectrumAnalysisSettings,
        colorLandmarks: List<ColorTransitionLandmark>,
        sourceLabel: String,
        sourceOrigin: String,
        methodId: String,
        methodVersion: String
    ): SpectrumCommitPackage {
        val stamp = stampFormatter.format(Instant.now())
        val base = "methodmesh_star_spectrum_${stamp}_commit"
        val featuresJsonFile = File(context.cacheDir, "${base}_features.json")
        val provenanceFile = File(context.cacheDir, "${base}_provenance.json")
        val manifestFile = File(context.cacheDir, "${base}_manifest.json")
        val bundleFile = File(context.cacheDir, "${base}_bundle.zip")

        featuresJsonFile.writeText(
            JSONObject().apply {
                put("schema", "methodmesh.star_spectrum.features.v1")
                put("feature_count", extraction.features.size)
                put("features", JSONArray().apply { extraction.features.forEach { put(it.toJson()) } })
            }.toString(2)
        )

        val provenance = JSONObject().apply {
            put("schema", "methodmesh.star_spectrum.provenance.v1")
            put("module_id", "star_spectrum")
            put("method_id", methodId)
            put("method_version", methodVersion)
            put("created_time_iso", Instant.now().toString())
            put("source_label", sourceLabel)
            put("source_origin", sourceOrigin)
            put("source_image_sha256", sourceImage?.let(::sha256Hex).orEmpty())
            put("analysis_settings", analysisSettingsJson(settings))
            put("colour_transition_landmarks", JSONArray().apply {
                colorLandmarks.forEach { landmark ->
                    put(JSONObject().apply {
                        put("id", landmark.id)
                        put("label", landmark.label)
                        put("x", finiteOrNull(landmark.x.toDouble()))
                        put("y", finiteOrNull(landmark.y.toDouble()))
                        put("prior_wavelength_nm", finiteOrNull(landmark.priorWavelengthNm))
                        put("prior_sigma_nm", finiteOrNull(landmark.priorSigmaNm))
                    })
                }
            })
            put("extraction", JSONObject().apply {
                put("method", extraction.diagnostics.extractionMethod)
                put("trace_quality", finiteOrNull(extraction.trace.traceQuality))
                put("trace_fit_rms_px", finiteOrNull(extraction.diagnostics.traceFitRmsPx))
                put("rejected_pixel_fraction", finiteOrNull(extraction.diagnostics.rejectedPixelFraction))
                put("profile_update_iterations", extraction.diagnostics.profileUpdateIterations)
                put("optimal_extraction_iterations", extraction.diagnostics.extractionIterations)
            })
            put("calibration", extraction.calibration?.let { calibration ->
                JSONObject().apply {
                    put("reference_id", calibration.reference.id)
                    put("reference_name", calibration.reference.name)
                    put("reference_star_name", calibration.reference.starName)
                    put("reference_spectral_type", calibration.reference.spectralType)
                    put("reference_rms_nm", finiteOrNull(calibration.reference.rmsNm))
                    put("distance_scale", finiteOrNull(calibration.distanceScale))
                    put("registration_offset_px", finiteOrNull(calibration.registrationOffsetPx))
                    put("confidence", finiteOrNull(calibration.confidence))
                    put("warning", calibration.warning)
                }
            } ?: JSONObject.NULL)
            put("instrument_response_corrected", false)
            put(
                "intensity_semantics",
                "Instrumental relative image signal. The Horne-style extraction is background-corrected, but no camera/grating/telescope spectral-response correction or absolute flux calibration has been applied."
            )
            put(
                "continuum_semantics",
                "The broad continuum includes the source spectral-energy distribution convolved with camera, telescope and grating response. It must not be interpreted as a response-corrected stellar continuum."
            )
            put("display_smoothing_semantics", "Any UI low-pass/smoothing view is non-destructive. Canonical CSV and feature detection remain based on the unsmoothed extracted signal unless explicitly documented otherwise.")
        }
        provenanceFile.writeText(provenance.toString(2))

        val entries = mutableListOf<BundleEntry>()
        sourceImage?.let {
            entries += BundleEntry(
                role = "source_image",
                file = it,
                bundleName = "source.${extensionOf(it)}",
                mimeType = mimeForFile(it)
            )
        }
        entries += BundleEntry("annotated_image", artifacts.annotatedImage, "annotated_spectrum.png", "image/png")
        entries += BundleEntry("spectrum_csv", artifacts.dataCsv, "spectrum.csv", "text/csv")
        entries += BundleEntry("features_csv", artifacts.peaksCsv, "features.csv", "text/csv")
        entries += BundleEntry("features_json", featuresJsonFile, "features.json", "application/json")
        entries += BundleEntry("domain_metadata", artifacts.metadataJson, "metadata.json", "application/json")
        entries += BundleEntry("provenance", provenanceFile, "provenance.json", "application/json")

        writeManifest(
            file = manifestFile,
            schema = "methodmesh.star_spectrum.bundle_manifest.v1",
            methodId = methodId,
            methodVersion = methodVersion,
            entries = entries
        )
        val allEntries = entries + BundleEntry("manifest", manifestFile, "manifest.json", "application/json")
        writeZip(bundleFile, allEntries)

        return SpectrumCommitPackage(
            sourceImage = sourceImage,
            featuresJson = featuresJsonFile,
            provenanceJson = provenanceFile,
            manifestJson = manifestFile,
            bundleZip = bundleFile
        )
    }

    fun writeReferenceCommitPackage(
        context: Context,
        source: Bitmap,
        sourceImage: File?,
        extraction: SpectrumExtraction,
        reference: SpectrumReference,
        colorLandmarks: List<ColorTransitionLandmark>,
        methodId: String,
        methodVersion: String
    ): SpectrumReferencePackage {
        val stamp = stampFormatter.format(Instant.now())
        val base = "methodmesh_star_spectrum_reference_${stamp}_commit"
        val annotated = File(context.cacheDir, "${base}_annotated.png")
        val spectrumCsv = File(context.cacheDir, "${base}_spectrum.csv")
        val anchorsCsv = File(context.cacheDir, "${base}_anchors.csv")
        val referenceJson = File(context.cacheDir, "${base}_reference.json")
        val provenanceFile = File(context.cacheDir, "${base}_provenance.json")
        val manifestFile = File(context.cacheDir, "${base}_manifest.json")
        val bundleFile = File(context.cacheDir, "${base}_bundle.zip")

        writeReferenceAnnotatedImage(source, extraction, reference, annotated)
        writeReferenceSpectrumCsv(extraction, reference, spectrumCsv)
        writeAnchorsCsv(reference, anchorsCsv)
        referenceJson.writeText(reference.toJson().toString(2))

        provenanceFile.writeText(
            JSONObject().apply {
                put("schema", "methodmesh.star_spectrum.reference_provenance.v1")
                put("module_id", "star_spectrum")
                put("method_id", methodId)
                put("method_version", methodVersion)
                put("created_time_iso", reference.createdTimeIso)
                put("reference_id", reference.id)
                put("reference_name", reference.name)
                put("star_name", reference.starName)
                put("spectral_type", reference.spectralType)
                put("source_image_sha256", sourceImage?.let(::sha256Hex).orEmpty())
                put("image_width_px", reference.imageWidthPx)
                put("image_height_px", reference.imageHeightPx)
                put("trace", JSONObject().apply {
                    put("start_x", finiteOrNull(reference.traceStartX))
                    put("start_y", finiteOrNull(reference.traceStartY))
                    put("end_x", finiteOrNull(reference.traceEndX))
                    put("end_y", finiteOrNull(reference.traceEndY))
                    put("trace_quality", finiteOrNull(extraction.trace.traceQuality))
                    put("trace_fit_rms_px", finiteOrNull(extraction.diagnostics.traceFitRmsPx))
                })
                put("colour_transition_landmarks", JSONArray().apply {
                    colorLandmarks.forEach { landmark ->
                        put(JSONObject().apply {
                            put("id", landmark.id)
                            put("label", landmark.label)
                            put("x", finiteOrNull(landmark.x.toDouble()))
                            put("y", finiteOrNull(landmark.y.toDouble()))
                            put("prior_wavelength_nm", finiteOrNull(landmark.priorWavelengthNm))
                            put("prior_sigma_nm", finiteOrNull(landmark.priorSigmaNm))
                        })
                    }
                })
                put("calibration", JSONObject().apply {
                    put("polynomial_order", reference.polynomialOrder)
                    put("coefficients", JSONArray().apply { reference.coefficients.forEach { put(finiteOrNull(it)) } })
                    put("rms_nm", finiteOrNull(reference.rmsNm))
                    put("valid_min_distance_px", finiteOrNull(reference.validMinDistancePx))
                    put("valid_max_distance_px", finiteOrNull(reference.validMaxDistancePx))
                    put("anchor_source", reference.anchorSource)
                    put("anchors", JSONArray().apply { reference.anchors.forEach { put(it.toJson()) } })
                })
                put("instrument_response_corrected", false)
                put("zero_order_required_for_saved_solution", false)
                put("notes", reference.notes)
            }.toString(2)
        )

        val entries = mutableListOf<BundleEntry>()
        sourceImage?.let {
            entries += BundleEntry("source_image", it, "source.${extensionOf(it)}", mimeForFile(it))
        }
        entries += BundleEntry("annotated_reference", annotated, "annotated_reference.png", "image/png")
        entries += BundleEntry("reference_spectrum_csv", spectrumCsv, "reference_spectrum.csv", "text/csv")
        entries += BundleEntry("calibration_anchors_csv", anchorsCsv, "calibration_anchors.csv", "text/csv")
        entries += BundleEntry("reference_json", referenceJson, "reference.json", "application/json")
        entries += BundleEntry("provenance", provenanceFile, "provenance.json", "application/json")

        writeManifest(
            file = manifestFile,
            schema = "methodmesh.star_spectrum.reference_bundle_manifest.v1",
            methodId = methodId,
            methodVersion = methodVersion,
            entries = entries
        )
        val allEntries = entries + BundleEntry("manifest", manifestFile, "manifest.json", "application/json")
        writeZip(bundleFile, allEntries)

        return SpectrumReferencePackage(
            sourceImage = sourceImage,
            annotatedImage = annotated,
            spectrumCsv = spectrumCsv,
            anchorsCsv = anchorsCsv,
            referenceJson = referenceJson,
            provenanceJson = provenanceFile,
            manifestJson = manifestFile,
            bundleZip = bundleFile
        )
    }

    private data class BundleEntry(
        val role: String,
        val file: File,
        val bundleName: String,
        val mimeType: String
    )

    private fun writeManifest(
        file: File,
        schema: String,
        methodId: String,
        methodVersion: String,
        entries: List<BundleEntry>
    ) {
        file.writeText(
            JSONObject().apply {
                put("schema", schema)
                put("method_id", methodId)
                put("method_version", methodVersion)
                put("created_time_iso", Instant.now().toString())
                put("entry_count", entries.size)
                put("entries", JSONArray().apply {
                    entries.forEach { entry ->
                        put(JSONObject().apply {
                            put("role", entry.role)
                            put("filename", entry.bundleName)
                            put("mime_type", entry.mimeType)
                            put("bytes", entry.file.length())
                            put("sha256", sha256Hex(entry.file))
                        })
                    }
                })
            }.toString(2)
        )
    }

    private fun writeZip(file: File, entries: List<BundleEntry>) {
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            entries.forEach { entry ->
                zip.putNextEntry(ZipEntry(entry.bundleName))
                entry.file.inputStream().buffered().use { input -> input.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    private fun writeReferenceSpectrumCsv(
        extraction: SpectrumExtraction,
        reference: SpectrumReference,
        file: File
    ) {
        file.bufferedWriter().use { writer ->
            writer.appendLine("index,distance_px,wavelength_nm,trace_x_px,trace_y_px,raw_signal,background_signal,boxcar_signal,optimal_signal,optimal_variance,continuum,residual,local_noise,local_snr,rejected_fraction,contamination_probability,valid")
            extraction.points().forEach { point ->
                writer.append(point.index.toString()).append(',')
                writer.append(format(point.distancePx)).append(',')
                writer.append(format(reference.wavelength(point.distancePx))).append(',')
                writer.append(format(point.traceX)).append(',')
                writer.append(format(point.traceY)).append(',')
                writer.append(format(point.rawSignal)).append(',')
                writer.append(format(point.backgroundSignal)).append(',')
                writer.append(format(extraction.diagnostics.boxcarSignal[point.index])).append(',')
                writer.append(format(point.correctedSignal)).append(',')
                writer.append(format(extraction.diagnostics.optimalVariance[point.index])).append(',')
                writer.append(format(point.continuum)).append(',')
                writer.append(format(point.residual)).append(',')
                writer.append(format(point.localNoise)).append(',')
                writer.append(format(point.localSnr)).append(',')
                writer.append(format(point.contaminationFraction)).append(',')
                writer.append(format(point.contaminationProbability)).append(',')
                writer.append(point.valid.toString()).appendLine()
            }
        }
    }

    private fun writeAnchorsCsv(reference: SpectrumReference, file: File) {
        file.bufferedWriter().use { writer ->
            writer.appendLine("label,distance_px,known_wavelength_nm,fitted_wavelength_nm,residual_nm")
            reference.anchors.forEach { anchor ->
                val fitted = reference.wavelength(anchor.distancePx)
                writer.append(csv(anchor.label)).append(',')
                writer.append(format(anchor.distancePx)).append(',')
                writer.append(format(anchor.wavelengthNm)).append(',')
                writer.append(format(fitted)).append(',')
                writer.append(format(fitted - anchor.wavelengthNm)).appendLine()
            }
        }
    }

    private fun writeReferenceAnnotatedImage(
        source: Bitmap,
        extraction: SpectrumExtraction,
        reference: SpectrumReference,
        file: File
    ) {
        val out = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val scale = max(1f, minOf(out.width, out.height) / 900f)
        val tracePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(55, 220, 190)
            style = Paint.Style.STROKE
            strokeWidth = 3.0f * scale
        }
        val anchorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 215, 80)
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * scale
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 18f * scale
            setShadowLayer(4f * scale, 1f, 1f, Color.BLACK)
        }

        val tracePath = Path()
        extraction.trace.sampleX.indices.forEach { index ->
            val x = extraction.trace.sampleX[index].toFloat()
            val y = extraction.trace.sampleY[index].toFloat()
            if (index == 0) tracePath.moveTo(x, y) else tracePath.lineTo(x, y)
        }
        canvas.drawPath(tracePath, tracePaint)

        reference.anchors.forEach { anchor ->
            val index = extraction.distancesPx.indices.minByOrNull { i -> abs(extraction.distancesPx[i] - anchor.distancePx) } ?: return@forEach
            val x = extraction.trace.sampleX[index].toFloat()
            val y = extraction.trace.sampleY[index].toFloat()
            canvas.drawCircle(x, y, 7f * scale, anchorPaint)
            canvas.drawText(
                "${anchor.label} %.2f nm".format(Locale.US, anchor.wavelengthNm),
                x + 10f * scale,
                y - 8f * scale,
                textPaint
            )
        }

        file.outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
        out.recycle()
    }

    private fun analysisSettingsJson(settings: SpectrumAnalysisSettings): JSONObject = JSONObject().apply {
        put("ribbon_half_width_px", settings.ribbonHalfWidthPx)
        put("aperture_half_width_px", settings.apertureHalfWidthPx)
        put("background_gap_px", settings.backgroundGapPx)
        put("continuum_window", settings.continuumWindow)
        put("detection_sigma", finiteOrNull(settings.detectionSigma))
        put("detection_mode", settings.detectionMode)
    }

    private fun finiteOrNull(value: Double): Any = if (value.isFinite()) value else JSONObject.NULL

    private fun extensionOf(file: File): String = file.extension.lowercase().takeIf { it.isNotBlank() } ?: "bin"

    private fun mimeForFile(file: File): String = when (extensionOf(file)) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "csv" -> "text/csv"
        "json" -> "application/json"
        "zip" -> "application/zip"
        else -> "application/octet-stream"
    }

    fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(Locale.US, it) }
    }

    fun featuresJson(features: List<SpectralFeature>): String = JSONArray().apply {
        features.forEach { put(it.toJson()) }
    }.toString()

    fun featuresText(features: List<SpectralFeature>): String = features.joinToString("\n") { feature ->
        val position = feature.wavelengthNm?.let { "%.2f nm".format(Locale.US, it) }
            ?: "%.1f px".format(Locale.US, feature.distancePx)
        "$position · ${feature.type.wireValue} · ${feature.detectionMethod.wireValue} · scale %.1f px · FWHM %.1f px · S/N %.1f · confidence %.2f · ${feature.searchZone.wireValue}".format(
            Locale.US,
            feature.detectionScalePx,
            feature.fwhmPx,
            feature.snr,
            feature.confidence
        )
    }

    fun metadataJson(extraction: SpectrumExtraction, sourceLabel: String, settings: SpectrumAnalysisSettings): JSONObject = JSONObject().apply {
        put("schema", "methodmesh.star_spectrum.analysis.v1")
        put("source", sourceLabel)
        put("created_time_iso", Instant.now().toString())
        put("sample_count", extraction.distancesPx.size)
        put("trace_quality", extraction.trace.traceQuality)
        put("trace_mean_absolute_correction_px", extraction.trace.meanAbsoluteCorrectionPx)
        put("ribbon_half_width_px", extraction.trace.ribbonHalfWidthPx)
        put("aperture_half_width_px", extraction.trace.apertureHalfWidthPx)
        put("continuum_window", settings.continuumWindow)
        put("detection_sigma", settings.detectionSigma)
        put("detection_mode", settings.detectionMode)
        put("mean_snr", extraction.meanSnr)
        put("contaminated_fraction", extraction.contaminatedFraction)
        put("extraction_method", extraction.diagnostics.extractionMethod)
        put("trace_fit_rms_px", extraction.diagnostics.traceFitRmsPx)
        put("rejected_pixel_fraction", extraction.diagnostics.rejectedPixelFraction)
        put("residual_above_3sigma_fraction", extraction.diagnostics.residualAbove3SigmaFraction)
        put("residual_above_5sigma_fraction", extraction.diagnostics.residualAbove5SigmaFraction)
        put("max_abs_standardized_residual", extraction.diagnostics.maxAbsStandardizedResidual)
        put("profile_update_iterations", extraction.diagnostics.profileUpdateIterations)
        put("optimal_extraction_iterations", extraction.diagnostics.extractionIterations)
        put("variance_semantics", extraction.diagnostics.varianceSemantics)
        put("feature_count", extraction.features.size)
        put(
            "feature_detection_methods",
            "narrow residual extrema + broad multi-scale local-shoulder matched response"
        )
        put(
            "feature_broad_nominal_core_widths_samples",
            JSONArray().apply {
                listOf(7, 13, 21, 33, 49).forEach(::put)
            }
        )
        put(
            "feature_endpoint_guard_samples",
            SpectrumFeatureSearchQa.endpointGuardSamples(extraction.distancesPx.size)
        )
        extraction.calibration?.reference?.anchors?.takeIf { it.isNotEmpty() }?.let { anchors ->
            put("feature_anchor_bracket_min_nm", anchors.minOf { it.wavelengthNm })
            put("feature_anchor_bracket_max_nm", anchors.maxOf { it.wavelengthNm })
        }
        put(
            "feature_detection_semantics",
            "Endpoint-guard samples remain visible/exported but are excluded from automatic feature detection. Narrow residual extrema are combined with a multi-scale broad-feature detector using local shoulder subtraction at several physical widths. Calibrated features outside the outer confirmed reference anchors are marked extrapolated and confidence-down-weighted."
        )
        put("features", JSONArray().apply { extraction.features.forEach { put(it.toJson()) } })
        put("calibrated", extraction.calibration != null)
        extraction.calibration?.let { calibration ->
            put("reference_id", calibration.reference.id)
            put("reference_name", calibration.reference.name)
            put("calibration_rms_nm", calibration.reference.rmsNm)
            put("calibration_confidence", calibration.confidence)
            put("calibration_distance_scale", calibration.distanceScale)
            put("calibration_registration_offset_px", calibration.registrationOffsetPx)
            put("calibration_warning", calibration.warning)
        }
        put("instrument_response_corrected", false)
        put("intensity_semantics", "Instrumental relative image signal. corrected_signal is the Horne-style optimally extracted target signal and boxcar_signal is the simple background-subtracted aperture sum. No camera/grating/telescope spectral-response correction or absolute flux calibration has been applied.")
        put("continuum_semantics", "The broad continuum includes the source spectral-energy distribution convolved with camera, telescope and grating response; it is not a response-corrected stellar continuum.")
        put("display_smoothing_semantics", "Any UI smoothing is non-destructive and does not replace the canonical unsmoothed CSV.")
        put("contamination_semantics", "iterative residual rejection against an empirical cross-dispersion spatial profile. Rejected pixels are preserved in QA diagnostics and affected columns carry contamination/rejection fractions.")
    }

    private fun writeSpectrumCsv(extraction: SpectrumExtraction, file: File) {
        file.bufferedWriter().use { writer ->
            writer.appendLine("index,distance_px,trace_x_px,trace_y_px,wavelength_nm,raw_signal,background_signal,boxcar_signal,optimal_signal,optimal_variance,continuum,residual,local_noise,local_snr,rejected_fraction,contamination_probability,valid,feature_search_zone,is_feature,feature_id,feature_detection_method,feature_scale_px,feature_fwhm_px")
            val featureByIndex = extraction.features.associateBy { it.index }
            extraction.points().forEach { point ->
                val feature = featureByIndex[point.index]
                writer.append(point.index.toString()).append(',')
                writer.append(format(point.distancePx)).append(',')
                writer.append(format(point.traceX)).append(',')
                writer.append(format(point.traceY)).append(',')
                writer.append(point.wavelengthNm?.let(::format).orEmpty()).append(',')
                writer.append(format(point.rawSignal)).append(',')
                writer.append(format(point.backgroundSignal)).append(',')
                writer.append(format(extraction.diagnostics.boxcarSignal[point.index])).append(',')
                writer.append(format(point.correctedSignal)).append(',')
                writer.append(format(extraction.diagnostics.optimalVariance[point.index])).append(',')
                writer.append(format(point.continuum)).append(',')
                writer.append(format(point.residual)).append(',')
                writer.append(format(point.localNoise)).append(',')
                writer.append(format(point.localSnr)).append(',')
                writer.append(format(point.contaminationFraction)).append(',')
                writer.append(format(point.contaminationProbability)).append(',')
                writer.append(point.valid.toString()).append(',')
                writer.append(
                    SpectrumFeatureSearchQa
                        .zoneFor(extraction, point.index)
                        .wireValue
                ).append(',')
                writer.append((feature != null).toString()).append(',')
                writer.append(feature?.id.orEmpty()).append(',')
                writer.append(feature?.detectionMethod?.wireValue.orEmpty()).append(',')
                writer.append(feature?.detectionScalePx?.let(::format).orEmpty()).append(',')
                writer.append(feature?.fwhmPx?.let(::format).orEmpty()).appendLine()
            }
        }
    }

    private fun writeFeaturesCsv(features: List<SpectralFeature>, file: File) {
        file.bufferedWriter().use { writer ->
            writer.appendLine("feature_id,index,distance_px,wavelength_nm,type,signal,continuum,amplitude,prominence,fwhm_px,fwhm_nm,snr,stability,contamination_probability,confidence,feature_search_zone,detection_method,detection_scale_px,supporting_scales_px")
            features.forEach { feature ->
                writer.append(csv(feature.id)).append(',')
                writer.append(feature.index.toString()).append(',')
                writer.append(format(feature.distancePx)).append(',')
                writer.append(feature.wavelengthNm?.let(::format).orEmpty()).append(',')
                writer.append(feature.type.wireValue).append(',')
                writer.append(format(feature.signal)).append(',')
                writer.append(format(feature.continuum)).append(',')
                writer.append(format(feature.amplitude)).append(',')
                writer.append(format(feature.prominence)).append(',')
                writer.append(format(feature.fwhmPx)).append(',')
                writer.append(feature.fwhmNm?.let(::format).orEmpty()).append(',')
                writer.append(format(feature.snr)).append(',')
                writer.append(format(feature.stability)).append(',')
                writer.append(format(feature.contaminationProbability)).append(',')
                writer.append(format(feature.confidence)).append(',')
                writer.append(feature.searchZone.wireValue).append(',')
                writer.append(feature.detectionMethod.wireValue).append(',')
                writer.append(format(feature.detectionScalePx)).append(',')
                writer.append(
                    csv(
                        feature.supportingScalesPx
                            .distinct()
                            .sorted()
                            .joinToString(";") {
                                format(it)
                            }
                    )
                ).appendLine()
            }
        }
    }

    private fun writeAnnotatedImage(source: Bitmap, extraction: SpectrumExtraction, file: File) {
        val out = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val scale = max(1f, minOf(out.width, out.height) / 900f)
        val userPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(210, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 2.2f * scale
        }
        val tracePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(55, 220, 190)
            style = Paint.Style.STROKE
            strokeWidth = 3.2f * scale
        }
        val ribbonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(130, 60, 180, 255)
            style = Paint.Style.STROKE
            strokeWidth = 1.4f * scale
        }
        val contaminatedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 120, 70)
            style = Paint.Style.FILL
        }
        val featurePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 220, 80)
            style = Paint.Style.FILL
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 19f * scale
            setShadowLayer(4f * scale, 1f, 1f, Color.BLACK)
        }

        canvas.drawLine(
            extraction.trace.userStartX.toFloat(), extraction.trace.userStartY.toFloat(),
            extraction.trace.userEndX.toFloat(), extraction.trace.userEndY.toFloat(), userPaint
        )
        val tracePath = Path()
        val upper = Path()
        val lowerPoints = mutableListOf<Pair<Float, Float>>()
        for (i in extraction.trace.sampleX.indices) {
            val x = extraction.trace.sampleX[i]
            val y = extraction.trace.sampleY[i]
            if (i == 0) tracePath.moveTo(x.toFloat(), y.toFloat()) else tracePath.lineTo(x.toFloat(), y.toFloat())
            val previous = max(0, i - 1)
            val next = minOf(extraction.trace.sampleX.lastIndex, i + 1)
            val tx = extraction.trace.sampleX[next] - extraction.trace.sampleX[previous]
            val ty = extraction.trace.sampleY[next] - extraction.trace.sampleY[previous]
            val norm = hypot(tx, ty).coerceAtLeast(1e-6)
            val nx = -ty / norm
            val ny = tx / norm
            val r = extraction.trace.ribbonHalfWidthPx.toDouble()
            val ux = (x + nx * r).toFloat()
            val uy = (y + ny * r).toFloat()
            val lx = (x - nx * r).toFloat()
            val ly = (y - ny * r).toFloat()
            if (i == 0) upper.moveTo(ux, uy) else upper.lineTo(ux, uy)
            lowerPoints += lx to ly
        }
        for (i in lowerPoints.indices.reversed()) upper.lineTo(lowerPoints[i].first, lowerPoints[i].second)
        upper.close()
        canvas.drawPath(upper, ribbonPaint)
        canvas.drawPath(tracePath, tracePaint)

        val markerStride = max(1, extraction.distancesPx.size / 400)
        for (i in extraction.distancesPx.indices step markerStride) {
            if (extraction.contaminationProbability[i] >= 0.5) {
                val radius = (2.5f + 4f * extraction.contaminationProbability[i].toFloat()) * scale
                canvas.drawCircle(extraction.trace.sampleX[i].toFloat(), extraction.trace.sampleY[i].toFloat(), radius, contaminatedPaint)
            }
        }
        extraction.features.filter { it.confidence >= 0.35 }.take(20).forEachIndexed { ordinal, feature ->
            val i = feature.index.coerceIn(0, extraction.trace.sampleX.lastIndex)
            val x = extraction.trace.sampleX[i].toFloat()
            val y = extraction.trace.sampleY[i].toFloat()
            canvas.drawCircle(x, y, 5.2f * scale, featurePaint)
            if (ordinal < 10) {
                val label = feature.wavelengthNm?.let { "${feature.id} %.1f nm".format(Locale.US, it) }
                    ?: "${feature.id} %.0f px".format(Locale.US, feature.distancePx)
                canvas.drawText(label, x + 8f * scale, y - 8f * scale, textPaint)
            }
        }
        file.outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
        out.recycle()
    }

    private fun format(value: Double): String = if (value.isFinite()) "%.8f".format(Locale.US, value).trimEnd('0').trimEnd('.') else ""
    private fun csv(value: String): String = if (value.contains(',') || value.contains('"') || value.contains('\n')) "\"${value.replace("\"", "\"\"")}\"" else value
}
