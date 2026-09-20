package com.example.methodmesh.modules.star_spectrum

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.settings.SettingsState
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.ReturnMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

object StarSpectrumReferenceCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100StarSpectrumReferenceMethod.ID
    override val title = "Create spectrum reference"
    override val description = "Trace an A-type reference star, automatically solve the Balmer-line wavelength pattern with a weak colour prior, review the solution, and keep that calibration for future observations."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val appContext = LocalContext.current
        val scope = rememberCoroutineScope()
        val definitions = remember { StarSpectrumModule.capabilitySettings()[capabilityId].orEmpty() }
        val settings = remember(context.action.settings) {
            SettingsState(definitions) { key, value -> context.onSettingsChanged(mapOf(key to value.toString())) }
                .also { initialiseSpectrumSettings(it, definitions, context) }
        }

        val initialSourceUri = contextInput(context, "source_image_uri")
        var sourceUri by rememberSaveable { mutableStateOf(initialSourceUri) }
        var sourceOrigin by rememberSaveable { mutableStateOf(if (initialSourceUri.isBlank()) "methodmesh" else "caller") }
        var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
        var sourceError by rememberSaveable { mutableStateOf<String?>(null) }
        var referenceName by rememberSaveable { mutableStateOf(settings.getString("reference_name").ifBlank { "A-star reference" }) }
        var starName by rememberSaveable { mutableStateOf(settings.getString("star_name")) }
        var spectralType by rememberSaveable { mutableStateOf(settings.getString("spectral_type").ifBlank { "A0V" }) }
        var polynomialOrder by rememberSaveable { mutableIntStateOf(settings.getString("polynomial_order").toIntOrNull()?.coerceIn(1, 2) ?: 1) }
        var ribbonWidth by rememberSaveable { mutableIntStateOf(settings.getInt("ribbon_half_width_px").coerceAtLeast(6)) }
        var apertureWidth by rememberSaveable { mutableIntStateOf(settings.getInt("aperture_half_width_px").coerceAtLeast(1)) }
        var backgroundGap by rememberSaveable { mutableIntStateOf(settings.getInt("background_gap_px").coerceAtLeast(1)) }
        var traceStartX by rememberSaveable { mutableFloatStateOf(Float.NaN) }
        var traceStartY by rememberSaveable { mutableFloatStateOf(Float.NaN) }
        var traceEndX by rememberSaveable { mutableFloatStateOf(Float.NaN) }
        var traceEndY by rememberSaveable { mutableFloatStateOf(Float.NaN) }
        var showEndpointPicker by rememberSaveable { mutableStateOf(false) }
        var openPickerWhenImageLoads by rememberSaveable { mutableStateOf(false) }
        var colorLandmarksJson by rememberSaveable { mutableStateOf("[]") }
        var extraction by remember { mutableStateOf<SpectrumExtraction?>(null) }
        var anchorsJson by rememberSaveable {
            mutableStateOf(contextInput(context, "calibration_anchors_json").takeIf { it.trim().startsWith("[") } ?: "[]")
        }
        var selectedBalmerLabel by rememberSaveable { mutableStateOf("Hβ") }
        var anchorSource by rememberSaveable { mutableStateOf("manual") }
        var selectedChartIndex by rememberSaveable { mutableIntStateOf(-1) }
        var extractionError by rememberSaveable { mutableStateOf<String?>(null) }
        var isExtracting by rememberSaveable { mutableStateOf(false) }
        var isPackaging by rememberSaveable { mutableStateOf(false) }
        var exportStatus by rememberSaveable { mutableStateOf<String?>(null) }
        var includeFullJson by rememberSaveable { mutableStateOf(false) }
        var showAdvanced by rememberSaveable { mutableStateOf(false) }
        var committedValuesJson by rememberSaveable { mutableStateOf("") }
        var shouldRestoreExtraction by rememberSaveable { mutableStateOf(false) }

        val trace = if (traceStartX.isFinite() && traceStartY.isFinite() && traceEndX.isFinite() && traceEndY.isFinite()) {
            TraceSelection(traceStartX, traceStartY, traceEndX, traceEndY)
        } else null
        val anchors = remember(anchorsJson) { parseAnchors(anchorsJson) }
        val colorLandmarks =
            remember(colorLandmarksJson) {
                parseColorTransitionLandmarks(
                    colorLandmarksJson
                )
            }
        val linearFit =
            remember(anchorsJson) {
                if (anchors.size >= 2) {
                    runCatching {
                        SpectrumEngine.fitCalibration(
                            anchors,
                            1
                        )
                    }.getOrNull()
                } else {
                    null
                }
            }
        val quadraticFit =
            remember(anchorsJson) {
                if (anchors.size >= 3) {
                    runCatching {
                        SpectrumEngine.fitCalibration(
                            anchors,
                            2
                        )
                    }.getOrNull()
                } else {
                    null
                }
            }
        val lineIdentityLinearRmsLimitNm = 3.0
        val lineIdentityPlausible =
            anchors.size < 3 ||
                (linearFit?.rmsNm?.isFinite() == true &&
                    linearFit.rmsNm <= lineIdentityLinearRmsLimitNm)
        val quadraticEligible = anchors.size >= 4 && lineIdentityPlausible

        LaunchedEffect(quadraticEligible, polynomialOrder) {
            if (polynomialOrder == 2 && !quadraticEligible) {
                polynomialOrder = 1
                settings.setString("polynomial_order", "1")
                context.onSettingsChanged(mapOf("polynomial_order" to "1"))
            }
        }

        val fit =
            if (polynomialOrder == 2 && quadraticEligible) {
                quadraticFit
            } else {
                linearFit
            }

        val anchorResidualsNm =
            remember(
                anchorsJson,
                polynomialOrder
            ) {
                val selectedFit = fit
                if (
                    selectedFit == null ||
                    selectedFit.predictedNm.size !=
                    anchors.size
                ) {
                    emptyMap()
                } else {
                    anchors
                        .mapIndexed {
                                index,
                                anchor ->
                            anchor.label to
                                (
                                    selectedFit
                                        .predictedNm[index] -
                                        anchor
                                            .wavelengthNm
                                    )
                        }
                        .toMap()
                }
            }

        fun updateTrace(value: TraceSelection?) {
            if (value == null) {
                traceStartX = Float.NaN; traceStartY = Float.NaN; traceEndX = Float.NaN; traceEndY = Float.NaN
            } else {
                traceStartX = value.startX; traceStartY = value.startY; traceEndX = value.endX; traceEndY = value.endY
            }
            extraction = null
            shouldRestoreExtraction = false
            anchorSource = "manual"
            committedValuesJson = ""
        }

        fun updateAnchors(values: List<CalibrationAnchor>) {
            anchorsJson = JSONArray().apply { values.sortedBy { it.distancePx }.forEach { put(it.toJson()) } }.toString()
            settings.setString("calibration_anchors_json", anchorsJson)
            context.onSettingsChanged(mapOf("calibration_anchors_json" to anchorsJson))
            committedValuesJson = ""
        }

        fun updateColorLandmarks(values: List<ColorTransitionLandmark>) {
            colorLandmarksJson =
                JSONArray().apply {
                    values.forEach { landmark ->
                        put(
                            JSONObject()
                                .put("id", landmark.id)
                                .put("label", landmark.label)
                                .put("x", landmark.x)
                                .put("y", landmark.y)
                                .put(
                                    "prior_wavelength_nm",
                                    landmark.priorWavelengthNm
                                )
                                .put(
                                    "prior_sigma_nm",
                                    landmark.priorSigmaNm
                                )
                        )
                    }
                }.toString()
            committedValuesJson = ""
        }

        fun executionFor(json: String): ExecutionResult? {
            if (json.isBlank()) return null
            val obj = JSONObject(json)
            val values = buildMap<String, String> { obj.keys().forEach { key -> put(key, obj.optString(key, "")) } }
            val request = As100StarSpectrumReferenceMethod.request(
                action = capabilityId,
                context = context.request.invocationContext.asMap(capabilityId) + values,
                signals = emptyList(),
                inputs = emptyList()
            )
            return As100StarSpectrumReferenceMethod.result(request, values, context.request.invocationContext)
        }

        val committedResult = remember(committedValuesJson, context.request.invocationContext) { executionFor(committedValuesJson) }
        val isCommitted = committedResult != null

        val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                SpectrumImageIO.takePersistableReadPermission(appContext, uri)
                sourceUri = uri.toString()
                sourceOrigin = "methodmesh"
                context.onSettingsChanged(mapOf("source_image_uri" to sourceUri))
                sourceError = null
                updateTrace(null)
                updateAnchors(emptyList())
                updateColorLandmarks(emptyList())
                openPickerWhenImageLoads = true
            }
        }

        LaunchedEffect(sourceUri) {
            sourceBitmap = null
            if (sourceUri.isBlank()) return@LaunchedEffect
            sourceError = null
            runCatching { withContext(Dispatchers.IO) { SpectrumImageIO.decode(appContext, sourceUri) } }
                .onSuccess { sourceBitmap = it }
                .onFailure { sourceError = it.message ?: "Could not open the reference-star image." }
        }

        DisposableEffect(sourceBitmap) {
            val bitmap = sourceBitmap
            onDispose { bitmap?.takeUnless { it.isRecycled }?.recycle() }
        }

        LaunchedEffect(sourceBitmap, openPickerWhenImageLoads) {
            if (sourceBitmap != null && openPickerWhenImageLoads) {
                openPickerWhenImageLoads = false
                kotlinx.coroutines.delay(250)
                showEndpointPicker = true
            }
        }

        sourceBitmap?.let { bitmap ->
            if (showEndpointPicker) {
                SpectrumEndpointPickerDialog(
                    bitmap = bitmap,
                    initialTrace = trace,
                    initialLandmarks = colorLandmarks,
                    allowColorBoundaries = true,
                    onDismiss = {
                        showEndpointPicker = false
                    },
                    onSelected = {
                            selectedTrace,
                            selectedLandmarks ->
                        updateTrace(selectedTrace)
                        updateColorLandmarks(
                            selectedLandmarks
                        )
                        showEndpointPicker = false
                    }
                )
            }
        }

        fun extractReference(restoring: Boolean = false) {
            val bitmap = sourceBitmap ?: run { extractionError = "Choose a reference-star image first."; return }
            val line = trace ?: run { extractionError = "Select the reference RED and VIOLET endpoints first."; return }
            if (line.length < 24f) { extractionError = "The selected endpoints are too close together."; return }
            if (isExtracting) return
            isExtracting = true
            extractionError = null
            val analysisSettings = SpectrumAnalysisSettings(
                ribbonHalfWidthPx = ribbonWidth,
                apertureHalfWidthPx = minOf(apertureWidth, maxOf(1, ribbonWidth - 2)),
                backgroundGapPx = minOf(backgroundGap, maxOf(1, ribbonWidth - apertureWidth - 1)),
                continuumWindow = 101,
                detectionSigma = 3.5,
                detectionMode = "absorption"
            )
            scope.launch {
                runCatching {
                    withContext(Dispatchers.Default) {
                        SpectrumEngine.analyse(
                            bitmap,
                            line.startX.toDouble(), line.startY.toDouble(),
                            line.endX.toDouble(), line.endY.toDouble(),
                            analysisSettings,
                            calibration = null
                        )
                    }
                }.onSuccess {
                    extraction = it
                    shouldRestoreExtraction = true
                }.onFailure {
                    extractionError = it.message ?: "Could not extract the reference spectrum."
                    if (!restoring) shouldRestoreExtraction = false
                }
                isExtracting = false
            }
        }

        LaunchedEffect(sourceBitmap, shouldRestoreExtraction, extraction) {
            if (sourceBitmap != null && shouldRestoreExtraction && extraction == null && trace != null && !isExtracting) extractReference(true)
        }

        fun commitReference() {
            val bitmap = sourceBitmap ?: return
            val line = trace ?: return
            val spectrum = extraction ?: return

            if (anchors.size < 3) {
                extractionError =
                    "At least three supported Balmer anchors are required before saving a wavelength reference. Two anchors are preliminary guidance only."
                return
            }

            val identityFit = linearFit
            if (identityFit == null || !identityFit.rmsNm.isFinite() ||
                identityFit.rmsNm > lineIdentityLinearRmsLimitNm
            ) {
                extractionError =
                    "These Balmer assignments are not coherent under one linear wavelength solution (linear RMS must be ≤ %.1f nm). Review the line identities before committing; a low quadratic RMS cannot rescue a bad line assignment."
                        .format(Locale.US, lineIdentityLinearRmsLimitNm)
                return
            }
            if (polynomialOrder == 2 && !quadraticEligible) {
                extractionError =
                    "Quadratic refinement is available only after four anchors already form a plausible linear wavelength solution."
                return
            }

            val calibrationFit = fit ?: run {
                extractionError = "Place enough wavelength anchors to fit this calibration model."
                return
            }
            val id = "sref-${UUID.randomUUID()}"
            val created = Instant.now().toString()
            val chromaticAtCommit =
                runCatching {
                    buildChromaticBootstrap(
                        bitmap = bitmap,
                        extraction = spectrum,
                        apertureHalfWidthPx = apertureWidth,
                        landmarks = colorLandmarks
                    )
                }.getOrNull()
            val automaticAtCommit =
                if (anchorSource.startsWith("automatic_consensus")) {
                    runCatching {
                        SpectrumCalibrationSolver.solveAStarReference(
                            extraction = spectrum,
                            chromaticWavelengthByIndex = chromaticAtCommit?.wavelengthByIndex
                        ).best
                    }.getOrNull()
                } else {
                    null
                }

            val reference = SpectrumReference(
                id = id,
                name = referenceName.ifBlank { starName.ifBlank { "A-star reference" } },
                starName = starName,
                spectralType = spectralType.ifBlank { "A0V" },
                createdTimeIso = created,
                imageWidthPx = bitmap.width,
                imageHeightPx = bitmap.height,
                traceStartX = line.startX.toDouble(),
                traceStartY = line.startY.toDouble(),
                traceEndX = line.endX.toDouble(),
                traceEndY = line.endY.toDouble(),
                polynomialOrder = calibrationFit.order,
                coefficients = calibrationFit.coefficients,
                rmsNm = calibrationFit.rmsNm,
                anchors = anchors,
                validMinDistancePx = anchors.minOf { it.distancePx },
                validMaxDistancePx = anchors.maxOf { it.distancePx },
                anchorSource = anchorSource,
                notes = buildString {
                    append("Created locally by MethodMesh Star spectrum analyser")
                    append("; calibration anchors $anchorSource")
                    automaticAtCommit?.let { automatic ->
                        append(
                            "; automatic consensus ${automatic.anchorCount}/4 lines, linear RMS %.3f nm, evidence %.0f%%, separation %.2f"
                                .format(
                                    Locale.US,
                                    automatic.rmsNm,
                                    automatic.confidence * 100.0,
                                    automatic.scoreMargin
                                )
                        )
                        val recoveredLabels =
                            automatic.matches
                                .filter { it.candidate.source == "targeted_recovery" }
                                .map { it.line.label }
                        if (recoveredLabels.isNotEmpty()) {
                            append(
                                "; targeted missing-line recovery ${recoveredLabels.joinToString(",")}"
                            )
                        }
                        automatic.chromaticRmsNm?.let { priorRms ->
                            append(
                                "; automatic weak-colour-prior RMS %.1f nm"
                                    .format(Locale.US, priorRms)
                            )
                        }
                    }
                    linearFit?.let {
                        append(
                            "; linear RMS %.3f nm"
                                .format(
                                    Locale.US,
                                    it.rmsNm
                                )
                        )
                    }
                    quadraticFit?.let {
                        append(
                            "; quadratic RMS %.3f nm"
                                .format(
                                    Locale.US,
                                    it.rmsNm
                                )
                        )
                    }
                    anchorResidualsNm
                        .values
                        .maxOfOrNull {
                            kotlin.math.abs(
                                it
                            )
                        }
                        ?.let {
                            append(
                                "; selected-model max |residual| %.3f nm"
                                    .format(
                                        Locale.US,
                                        it
                                    )
                            )
                        }
                    chromaticAtCommit?.let { chromatic ->
                        append(
                            "; chromatic bootstrap %.0f–%.0f nm, fit %.0f%%, useful colour %.0f%%, boundaries %d, boundary agreement %.0f%%"
                                .format(
                                    Locale.US,
                                    min(chromatic.violetNm, chromatic.redNm),
                                    max(chromatic.violetNm, chromatic.redNm),
                                    chromatic.fitQuality * 100.0,
                                    chromatic.reliableColourFraction * 100.0,
                                    chromatic.landmarkCount,
                                    chromatic.landmarkAgreement * 100.0
                                )
                        )
                    }
                }
            )
            if (isPackaging) return
            isPackaging = true
            extractionError = null
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        val sourceBundleFile = SpectrumImageIO.copyOriginalToCache(
                            appContext,
                            sourceUri,
                            "methodmesh_star_spectrum_reference_source"
                        )
                        val packageFiles = SpectrumExport.writeReferenceCommitPackage(
                            context = appContext,
                            source = bitmap,
                            sourceImage = sourceBundleFile,
                            extraction = spectrum,
                            reference = reference,
                            colorLandmarks = colorLandmarks,
                            methodId = As100StarSpectrumReferenceMethod.ID,
                            methodVersion = As100StarSpectrumReferenceMethod.VERSION
                        )
                        sourceBundleFile to packageFiles
                    }
                }.onSuccess { (sourceBundleFile, packageFiles) ->
                    SpectrumReferenceRepository.save(appContext, reference)
                    val authority = "${appContext.packageName}.fileprovider"
                    fun contentUri(file: java.io.File): String =
                        FileProvider.getUriForFile(appContext, authority, file).toString()
                    val sourceReturnFile = sourceBundleFile.takeIf { sourceOrigin == "methodmesh" }
                    val sourceReturnUri = sourceReturnFile?.let(::contentUri).orEmpty()
                    val midpoint = (reference.validMinDistancePx + reference.validMaxDistancePx) / 2.0
                    val values = linkedMapOf(
                        StarSpectrumReferenceFields.STATUS to "succeeded",
                        StarSpectrumReferenceFields.ID to reference.id,
                        StarSpectrumReferenceFields.SOURCE_IMAGE_URI to sourceReturnUri,
                        StarSpectrumReferenceFields.SOURCE_IMAGE_SHA256 to sourceReturnFile?.let { SpectrumExport.sha256Hex(it) }.orEmpty(),
                        StarSpectrumReferenceFields.ANNOTATED_IMAGE_URI to contentUri(packageFiles.annotatedImage),
                        StarSpectrumReferenceFields.SPECTRUM_URI to contentUri(packageFiles.spectrumCsv),
                        StarSpectrumReferenceFields.ANCHORS_URI to contentUri(packageFiles.anchorsCsv),
                        StarSpectrumReferenceFields.REFERENCE_JSON_URI to contentUri(packageFiles.referenceJson),
                        StarSpectrumReferenceFields.PROVENANCE_URI to contentUri(packageFiles.provenanceJson),
                        StarSpectrumReferenceFields.MANIFEST_URI to contentUri(packageFiles.manifestJson),
                        StarSpectrumReferenceFields.BUNDLE_URI to contentUri(packageFiles.bundleZip),
                        StarSpectrumReferenceFields.ANNOTATED_IMAGE_SHA256 to SpectrumExport.sha256Hex(packageFiles.annotatedImage),
                        StarSpectrumReferenceFields.SPECTRUM_SHA256 to SpectrumExport.sha256Hex(packageFiles.spectrumCsv),
                        StarSpectrumReferenceFields.ANCHORS_SHA256 to SpectrumExport.sha256Hex(packageFiles.anchorsCsv),
                        StarSpectrumReferenceFields.REFERENCE_JSON_SHA256 to SpectrumExport.sha256Hex(packageFiles.referenceJson),
                        StarSpectrumReferenceFields.PROVENANCE_SHA256 to SpectrumExport.sha256Hex(packageFiles.provenanceJson),
                        StarSpectrumReferenceFields.MANIFEST_SHA256 to SpectrumExport.sha256Hex(packageFiles.manifestJson),
                        StarSpectrumReferenceFields.BUNDLE_SHA256 to SpectrumExport.sha256Hex(packageFiles.bundleZip),
                        StarSpectrumReferenceFields.NAME to reference.name,
                        StarSpectrumReferenceFields.STAR_NAME to reference.starName,
                        StarSpectrumReferenceFields.SPECTRAL_TYPE to reference.spectralType,
                        StarSpectrumReferenceFields.ANCHOR_COUNT to reference.anchors.size.toString(),
                        StarSpectrumReferenceFields.ANCHOR_SOURCE to reference.anchorSource,
                        StarSpectrumReferenceFields.POLYNOMIAL_ORDER to reference.polynomialOrder.toString(),
                        StarSpectrumReferenceFields.COEFFICIENTS_JSON to JSONArray().apply { reference.coefficients.forEach(::put) }.toString(),
                        StarSpectrumReferenceFields.RMS_NM to reference.rmsNm.toString(),
                        StarSpectrumReferenceFields.DISPERSION_NM_PER_PX to reference.derivative(midpoint).toString(),
                        StarSpectrumReferenceFields.WAVELENGTH_MIN_NM to reference.wavelength(reference.validMinDistancePx).toString(),
                        StarSpectrumReferenceFields.WAVELENGTH_MAX_NM to reference.wavelength(reference.validMaxDistancePx).toString(),
                        StarSpectrumReferenceFields.TRACE_QUALITY to spectrum.trace.traceQuality.toString(),
                        StarSpectrumReferenceFields.CREATED_TIME_ISO to reference.createdTimeIso,
                        StarSpectrumReferenceFields.REFERENCE_JSON to reference.toJson().toString(),
                        StarSpectrumReferenceFields.ERROR to ""
                    )
                    committedValuesJson = JSONObject(values as Map<*, *>).toString()
                    val result = executionFor(committedValuesJson)
                    if (context.submitsImmediately && result != null) onConfirmed(result)
                }.onFailure { error ->
                    extractionError = "Could not create the reference package: ${error.message ?: "packaging failed"}"
                }
                isPackaging = false
            }
        }

        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SpectrumToolHeader(
                title = title,
                subtitle = "Store your own instrument/reference-star wavelength solution once; reuse it offline on later observations",
                committed = isCommitted,
                canGoBack = context.stepNumber > 1,
                onBack = onBack,
                onCancel = onCancel
            )

            if (!isCommitted) {
                SpectrumSection("1 · Reference observation", "A-type stars are convenient because the Balmer absorption series gives strong visible anchors. Use the same camera/grating geometry you plan to reuse.") {
                    if (context.settingShouldBeShown("source_image_uri")) {
                        Button(onClick = { imagePicker.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (sourceUri.isBlank()) "Upload reference photo" else "Replace reference photo")
                        }
                    }
                    sourceError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (sourceUri.isNotBlank()) Text(sourceUri.substringAfterLast('/'), style = MaterialTheme.typography.bodySmall)
                    if (context.settingShouldBeShown("reference_name")) {
                        OutlinedTextField(
                            value = referenceName,
                            onValueChange = { referenceName = it; settings.setString("reference_name", it); context.onSettingsChanged(mapOf("reference_name" to it)) },
                            label = { Text("Reference name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    if (context.settingShouldBeShown("star_name")) {
                        OutlinedTextField(
                            value = starName,
                            onValueChange = { starName = it; settings.setString("star_name", it); context.onSettingsChanged(mapOf("star_name" to it)) },
                            label = { Text("Star name") },
                            placeholder = { Text("e.g. Vega") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    if (context.settingShouldBeShown("spectral_type")) {
                        OutlinedTextField(
                            value = spectralType,
                            onValueChange = { spectralType = it; settings.setString("spectral_type", it); context.onSettingsChanged(mapOf("spectral_type" to it)) },
                            label = { Text("Spectral type") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }

                sourceBitmap?.let { bitmap ->
                    SpectrumSection(
                        "2 · Spectrum colour geometry",
                        "Place the required RED and VIOLET ends. MethodMesh then auto-proposes R/Y, Y/G, G/B and B/V boundaries with the same shared detector used by target analysis; review or edit them if needed. No line drawing is required."
                    ) {
                        SpectrumImageSurface(
                            bitmap = bitmap,
                            trace = trace,
                            onSelectEndpoints = {
                                showEndpointPicker = true
                            },
                            ribbonHalfWidthPx = ribbonWidth,
                            extraction = extraction,
                            colorLandmarks = colorLandmarks
                        )

                        if (trace != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                buildString {
                                    append("RED + VIOLET selected")
                                    if (colorLandmarks.isEmpty()) {
                                        append(" · automatic boundaries unavailable; endpoints remain sufficient")
                                    } else {
                                        append(" · ${colorLandmarks.size}/4 colour boundaries")
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (colorLandmarks.isNotEmpty()) {
                                Text(
                                    colorLandmarks.joinToString(" · ") {
                                        "${it.label} ≈ ${"%.0f".format(Locale.US, it.priorWavelengthNm)} nm"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                SpectrumSection("3 · Extract reference spectrum") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showAdvanced = !showAdvanced }, modifier = Modifier.weight(1f)) { Text(if (showAdvanced) "Hide controls" else "Tune trace") }
                        Button(onClick = { extractReference(false) }, enabled = sourceBitmap != null && trace != null && !isExtracting, modifier = Modifier.weight(1f)) {
                            Text(if (isExtracting) "Extracting…" else "Extract")
                        }
                    }
                    if (showAdvanced) {
                        Text("Search ribbon  $ribbonWidth px", style = MaterialTheme.typography.labelLarge)
                        Slider(value = ribbonWidth.toFloat(), onValueChange = { ribbonWidth = it.toInt().coerceIn(6, 120); settings.setInt("ribbon_half_width_px", ribbonWidth); context.onSettingsChanged(mapOf("ribbon_half_width_px" to ribbonWidth.toString())); extraction = null; shouldRestoreExtraction = false }, valueRange = 6f..120f)
                        Text("Extraction aperture  ±$apertureWidth px", style = MaterialTheme.typography.labelLarge)
                        Slider(value = apertureWidth.toFloat(), onValueChange = { apertureWidth = it.toInt().coerceIn(1, maxOf(1, ribbonWidth - 2)); settings.setInt("aperture_half_width_px", apertureWidth); context.onSettingsChanged(mapOf("aperture_half_width_px" to apertureWidth.toString())); extraction = null; shouldRestoreExtraction = false }, valueRange = 1f..minOf(30f, maxOf(2f, ribbonWidth - 2f)))
                        Text("Background gap  $backgroundGap px", style = MaterialTheme.typography.labelLarge)
                        Slider(value = backgroundGap.toFloat(), onValueChange = { backgroundGap = it.toInt().coerceIn(1, 30); settings.setInt("background_gap_px", backgroundGap); context.onSettingsChanged(mapOf("background_gap_px" to backgroundGap.toString())); extraction = null; shouldRestoreExtraction = false }, valueRange = 1f..30f)
                    }
                    extractionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }

                extraction?.let { spectrum ->
                    val chromaticBootstrap =
                        sourceBitmap?.let { bitmap ->
                            remember(
                                bitmap,
                                spectrum,
                                apertureWidth
                            ) {
                                buildChromaticBootstrap(
                                    bitmap = bitmap,
                                    extraction = spectrum,
                                    apertureHalfWidthPx = apertureWidth,
                                    landmarks = colorLandmarks
                                )
                            }
                        }

                    SpectrumSection(
                        "4 · Extraction quality audit",
                        "Each strip is an intermediate 2-D product from the same Horne-style extraction. Use these views to check that trace fitting, background removal, spatial-profile modelling and outlier rejection are behaving sensibly before wavelength calibration."
                    ) {
                        SpectrumProcessingAudit(spectrum)
                    }

                    sourceBitmap?.let { bitmap ->
                        SpectrumSection(
                            "5 · Extracted spectrum appearance",
                            "A horizontal photographic strip made from the optimised extraction aperture. It is shown in conventional VIOLET → RED order."
                        ) {
                            ExtractedSpectrumStrip(
                                bitmap = bitmap,
                                extraction = spectrum,
                                apertureHalfWidthPx = apertureWidth
                            )
                        }
                    }

                    sourceBitmap?.let { bitmap ->
                        chromaticBootstrap?.let { bootstrap ->
                            SpectrumSection(
                                "6 · Chromatic wavelength bootstrap",
                                "Use the photographed VIOLET → RED colour progression as a coarse wavelength prior before Balmer-line refinement. This is an initialization aid, not the final calibration."
                            ) {
                                ChromaticBootstrapPanel(
                                    bitmap = bitmap,
                                    extraction = spectrum,
                                    apertureHalfWidthPx = apertureWidth,
                                    bootstrap = bootstrap,
                                    landmarks = colorLandmarks
                                )
                            }
                        }
                    }

                    SpectrumSection(
                        "7 · Extracted spectrum trace",
                        "This is the spectrum produced by the extraction itself. It appears before final wavelength calibration or Balmer anchors are added."
                    ) {
                        SpectrumTracePreview(
                            extraction = spectrum
                        )
                    }

                    val automaticCalibration =
                        remember(spectrum, chromaticBootstrap) {
                            SpectrumCalibrationSolver.solveAStarReference(
                                extraction = spectrum,
                                chromaticWavelengthByIndex = chromaticBootstrap?.wavelengthByIndex
                            )
                        }

                    SpectrumSection(
                        "8 · Automatic wavelength calibration",
                        "MethodMesh searches many possible assignments between observed absorption troughs and the Balmer pattern, then prefers the simplest linear mapping supported by the most independent lines. Colour geometry is a weak prior only; it cannot create a match. Manual placement remains available for review or fallback."
                    ) {
                        val bestAutomatic = automaticCalibration.best
                        if (bestAutomatic == null) {
                            Text(
                                "No automatic Balmer solution has enough structure yet. Review the extracted trace and use the manual calibration controls below.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        } else {
                            Text(
                                "Automatic consensus proposal",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MetricTile(
                                    "Supported lines",
                                    if (bestAutomatic.recoveredLineCount > 0) {
                                        "${bestAutomatic.anchorCount}/4 · ${bestAutomatic.directMatchCount} direct"
                                    } else {
                                        "${bestAutomatic.anchorCount}/4"
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                MetricTile(
                                    "Linear RMS",
                                    "%.3f nm".format(Locale.US, bestAutomatic.rmsNm),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MetricTile(
                                    "Evidence",
                                    "%.0f%%".format(Locale.US, bestAutomatic.confidence * 100.0),
                                    modifier = Modifier.weight(1f)
                                )
                                MetricTile(
                                    "Alternatives",
                                    automaticCalibration.solutions.drop(1).size.toString(),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            bestAutomatic.chromaticRmsNm?.let { priorRms ->
                                Text(
                                    "Weak colour-prior RMS %.1f nm · solution separation %.2f".format(
                                        Locale.US,
                                        priorRms,
                                        bestAutomatic.scoreMargin
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (bestAutomatic.ambiguous) {
                                Text(
                                    "Automatic solution is ambiguous. Treat it as a proposal and inspect the matched troughs before accepting it.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else {
                                Text(
                                    "The leading solution has independent support beyond the two points needed to define a line.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (bestAutomatic.recoveredLineCount > 0) {
                                Text(
                                    "${bestAutomatic.recoveredLineCount} missing catalogue line${if (bestAutomatic.recoveredLineCount == 1) " was" else "s were"} recovered by targeted local search after the global solution already had ≥3 independent matches.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            bestAutomatic.matches.forEach { match ->
                                val sourceLabel =
                                    when (match.candidate.source) {
                                        "targeted_recovery" -> "predicted-neighbourhood recovery"
                                        "multiscale" -> "multiscale"
                                        else -> match.candidate.source
                                    }
                                Text(
                                    "${match.line.label} ${"%.2f".format(Locale.US, match.line.wavelengthNm)} nm  ←  ${"%.1f".format(Locale.US, match.candidate.distancePx)} px  ·  Δλ ${"%+.2f".format(Locale.US, match.residualNm)} nm  ·  $sourceLabel",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Button(
                                onClick = {
                                    anchorSource = "automatic_consensus"
                                    updateAnchors(bestAutomatic.anchors)
                                    polynomialOrder = 1
                                    settings.setString("polynomial_order", "1")
                                    context.onSettingsChanged(
                                        mapOf("polynomial_order" to "1")
                                    )
                                },
                                enabled = bestAutomatic.directMatchCount >= 3,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    if (bestAutomatic.directMatchCount >= 3) {
                                        "Use automatic solution"
                                    } else {
                                        "Need three direct lines"
                                    }
                                )
                            }

                            if (automaticCalibration.solutions.size > 1) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Leading alternatives",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                automaticCalibration.solutions.drop(1).take(3).forEachIndexed { index, alternative ->
                                    Text(
                                        buildString {
                                            append("${index + 2}. ${alternative.anchorCount}/4 lines")
                                            if (alternative.recoveredLineCount > 0) {
                                                append(" (${alternative.directMatchCount} direct + ${alternative.recoveredLineCount} recovered)")
                                            }
                                            append(" · RMS ${"%.2f".format(Locale.US, alternative.rmsNm)} nm · ")
                                            append(alternative.matches.joinToString(" · ") { it.line.label })
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Manual review / fallback",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Use these controls to inspect, fine-tune or replace the automatic proposal. Manual anchors are no longer required when a well-supported automatic solution is accepted.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val recommendedOrder =
                                listOf("Hβ", "Hγ", "Hα", "Hδ")

                            HydrogenBalmerLines.standard
                                .sortedBy {
                                    recommendedOrder
                                        .indexOf(it.label)
                                        .let { index ->
                                            if (index < 0) {
                                                Int.MAX_VALUE
                                            } else {
                                                index
                                            }
                                        }
                                }
                                .forEach { line ->
                                    val placed = anchors.any { it.label == line.label }
                                    FilterChip(
                                        selected = selectedBalmerLabel == line.label,
                                        onClick = { selectedBalmerLabel = line.label },
                                        label = {
                                            Text(
                                                "${line.label} ${"%.1f".format(Locale.US, line.wavelengthNm)}" +
                                                    if (placed) " ✓" else ""
                                            )
                                        }
                                    )
                                }
                        }

                        Spacer(Modifier.height(8.dp))

                        BalmerAnchorChart(
                            extraction = spectrum,
                            anchors = anchors,
                            activeLabel = selectedBalmerLabel,
                            requestedOrder = polynomialOrder,
                            chromaticBootstrap = chromaticBootstrap,
                            consensusGuideAnchors = automaticCalibration.best
                                ?.takeIf { it.directMatchCount >= 3 }
                                ?.anchors
                                .orEmpty(),
                            consensusGuideDirectCount = automaticCalibration.best
                                ?.takeIf { it.directMatchCount >= 3 }
                                ?.directMatchCount
                                ?: 0,
                            onPlaceIndex = { index ->
                                selectedChartIndex = index
                                val line = HydrogenBalmerLines.standard.first {
                                    it.label == selectedBalmerLabel
                                }

                                val updated =
                                    anchors.filterNot { it.label == line.label } +
                                        CalibrationAnchor(
                                            distancePx = spectrum.distancesPx[index],
                                            wavelengthNm = line.wavelengthNm,
                                            label = line.label
                                        )

                                anchorSource =
                                    if (anchorSource.startsWith("automatic_consensus")) {
                                        "automatic_consensus_reviewed"
                                    } else {
                                        "manual"
                                    }
                                updateAnchors(updated)

                                val ordered = listOf("Hβ", "Hγ", "Hα", "Hδ")
                                val current = ordered.indexOf(line.label)
                                if (current >= 0) {
                                    val nextUnplaced = ordered
                                        .drop(current + 1)
                                        .firstOrNull { candidate ->
                                            updated.none { it.label == candidate }
                                        }
                                    if (nextUnplaced != null) {
                                        selectedBalmerLabel = nextUnplaced
                                    }
                                }
                            }
                        )

                        anchors
                            .sortedByDescending { it.wavelengthNm }
                            .forEach { anchor ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier =
                                            Modifier.weight(
                                                1f
                                            )
                                    ) {
                                        Text(
                                            "${anchor.label}  %.2f nm · %.1f px".format(
                                                Locale.US,
                                                anchor.wavelengthNm,
                                                anchor.distancePx
                                            ),
                                            fontWeight =
                                                FontWeight.SemiBold
                                        )

                                        anchorResidualsNm[
                                            anchor.label
                                        ]?.let {
                                                residual ->
                                            val magnitude =
                                                kotlin.math.abs(
                                                    residual
                                                )
                                            val severity =
                                                when {
                                                    magnitude >
                                                        3.0 ->
                                                        "OUTLIER"

                                                    magnitude >
                                                        2.0 ->
                                                        "WARN"

                                                    else ->
                                                        ""
                                                }
                                            val residualText =
                                                buildString {
                                                    append(
                                                        "Δλ %+.3f nm"
                                                            .format(
                                                                Locale.US,
                                                                residual
                                                            )
                                                    )
                                                    if (
                                                        severity
                                                            .isNotBlank()
                                                    ) {
                                                        append(
                                                            " · $severity"
                                                        )
                                                    }
                                                }

                                            Text(
                                                residualText,
                                                style =
                                                    MaterialTheme
                                                        .typography
                                                        .labelSmall,
                                                color =
                                                    if (
                                                        magnitude >
                                                        2.0
                                                    ) {
                                                        MaterialTheme
                                                            .colorScheme
                                                            .error
                                                    } else {
                                                        MaterialTheme
                                                            .colorScheme
                                                            .onSurfaceVariant
                                                    },
                                                fontWeight =
                                                    if (
                                                        magnitude >
                                                        3.0
                                                    ) {
                                                        FontWeight.Bold
                                                    } else {
                                                        FontWeight.Normal
                                                    }
                                            )
                                        }
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                selectedBalmerLabel = anchor.label
                                            }
                                        ) {
                                            Text("Fine tune")
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                anchorSource =
                                                    if (anchorSource.startsWith("automatic_consensus")) {
                                                        "automatic_consensus_reviewed"
                                                    } else {
                                                        "manual"
                                                    }
                                                updateAnchors(
                                                    anchors.filterNot {
                                                        it.label == anchor.label
                                                    }
                                                )
                                                selectedBalmerLabel = anchor.label
                                            }
                                        ) {
                                            Text("Remove")
                                        }
                                    }
                                }
                            }
                    }

                    SpectrumSection("9 · Wavelength model") {
                        if (context.settingShouldBeShown("polynomial_order")) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = polynomialOrder == 1, onClick = { polynomialOrder = 1; settings.setString("polynomial_order", "1"); context.onSettingsChanged(mapOf("polynomial_order" to "1")); committedValuesJson = "" }, label = { Text("Linear") })
                                FilterChip(
                                    selected = polynomialOrder == 2,
                                    enabled = quadraticEligible,
                                    onClick = { polynomialOrder = 2; settings.setString("polynomial_order", "2"); context.onSettingsChanged(mapOf("polynomial_order" to "2")); committedValuesJson = "" },
                                    label = { Text("Quadratic") }
                                )
                            }
                        }
                        if (fit == null) {
                            Text(
                                if (polynomialOrder == 2) {
                                    "Quadratic calibration needs at least 3 Balmer anchors."
                                } else {
                                    "Add at least 2 anchors for a preliminary linear solution. A saved reference still requires at least 3 supported Balmer anchors."
                                }
                            )
                        } else {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MetricTile(
                                    "Balmer anchors",
                                    "${anchors.size}/4",
                                    modifier = Modifier.weight(1f)
                                )
                                MetricTile(
                                    "Selected model",
                                    if (polynomialOrder == 1) {
                                        "Linear"
                                    } else {
                                        "Quadratic"
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MetricTile(
                                    "Linear RMS",
                                    linearFit
                                        ?.let {
                                            "%.3f nm%s".format(
                                                Locale.US,
                                                it.rmsNm,
                                                if (
                                                    polynomialOrder ==
                                                    1
                                                ) {
                                                    " · selected"
                                                } else {
                                                    ""
                                                }
                                            )
                                        }
                                        ?: "—",
                                    modifier = Modifier.weight(1f)
                                )
                                MetricTile(
                                    "Quadratic RMS",
                                    quadraticFit
                                        ?.let {
                                            "%.3f nm%s".format(
                                                Locale.US,
                                                it.rmsNm,
                                                if (
                                                    polynomialOrder ==
                                                    2
                                                ) {
                                                    " · selected"
                                                } else {
                                                    ""
                                                }
                                            )
                                        }
                                        ?: "—",
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Text(
                                "Anchor source: ${anchorSource.replace('_', ' ')}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Text(
                                "Line identity is established under the linear model first. Quadratic is a later refinement only; a low quadratic RMS cannot validate an implausible set of line assignments.",
                                style =
                                    MaterialTheme
                                        .typography
                                        .labelSmall,
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .onSurfaceVariant
                            )

                            if (anchors.size >= 3 && !lineIdentityPlausible) {
                                Text(
                                    "Calibration blocked: the current Balmer assignments have linear RMS ${linearFit?.rmsNm?.let { "%.3f".format(Locale.US, it) } ?: "—"} nm. Review the line identities until the linear RMS is ≤ %.1f nm. Quadratic fitting is disabled for this assignment."
                                        .format(Locale.US, lineIdentityLinearRmsLimitNm),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )
                            } else if (!quadraticEligible) {
                                Text(
                                    "Quadratic refinement unlocks only after four anchors already agree with a plausible linear calibration.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (
                                polynomialOrder == 2 &&
                                anchors.size == 3
                            ) {
                                Text(
                                    "A three-anchor quadratic can fit almost exactly by construction; confirm the fourth Balmer line when observable to obtain an independent residual check.",
                                    style =
                                        MaterialTheme
                                            .typography
                                            .bodySmall,
                                    color =
                                        MaterialTheme
                                            .colorScheme
                                            .onSurfaceVariant
                                )
                            } else if (
                                polynomialOrder == 2 &&
                                anchors.size >= 4
                            ) {
                                Text(
                                    "Four supported Balmer lines overdetermine the quadratic fit. Use it only as a refinement after the same anchors already pass the linear line-identity check.",
                                    style =
                                        MaterialTheme
                                            .typography
                                            .bodySmall,
                                    color =
                                        MaterialTheme
                                            .colorScheme
                                            .onSurfaceVariant
                                )
                            }

                            val maxResidual =
                                anchorResidualsNm
                                    .values
                                    .maxOfOrNull {
                                        kotlin.math.abs(
                                            it
                                        )
                                    }

                            if (
                                maxResidual != null &&
                                maxResidual > 2.0
                            ) {
                                Text(
                                    if (maxResidual > 3.0) {
                                        "Calibration QA: at least one anchor is >3 nm from the selected model. Review the flagged line before committing."
                                    } else {
                                        "Calibration QA: at least one anchor is >2 nm from the selected model. Review the warning before committing."
                                    },
                                    style =
                                        MaterialTheme
                                            .typography
                                            .bodySmall,
                                    color =
                                        MaterialTheme
                                            .colorScheme
                                            .error,
                                    fontWeight =
                                        if (
                                            maxResidual >
                                            3.0
                                        ) {
                                            FontWeight.Bold
                                        } else {
                                            FontWeight.SemiBold
                                        }
                                )
                            }

                            Text(
                                "λ = " + fit.coefficients.mapIndexed { index, value ->
                                    when (index) {
                                        0 -> "%.5f".format(Locale.US, value)
                                        1 -> "%+.7f·p".format(Locale.US, value)
                                        else -> "%+.10f·p²".format(Locale.US, value)
                                    }
                                }.joinToString(" "),
                                style = MaterialTheme.typography.bodySmall
                            )

                            if (anchors.size < 3) {
                                Text(
                                    "Preliminary solution only. Add at least one more supported Balmer line before saving this reference.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                if (anchors.size == 3) {
                                    Text(
                                        "Three lines are sufficient to save this reference; confirming the fourth Balmer line is recommended when it is observable.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Button(
                                    onClick = ::commitReference,
                                    enabled = !isPackaging && lineIdentityPlausible &&
                                        (polynomialOrder != 2 || quadraticEligible),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(if (isPackaging) "Packaging…" else "Commit reference")
                                }
                            }
                        }
                    }
                }
            } else {
                val obj =
                    JSONObject(
                        committedValuesJson
                    )
                val referenceId =
                    obj.optString(
                        StarSpectrumReferenceFields.ID
                    )
                val referenceJson =
                    obj.optString(
                        StarSpectrumReferenceFields.REFERENCE_JSON
                    )
                val committedReference =
                    remember(
                        referenceJson
                    ) {
                        runCatching {
                            SpectrumReference.fromJson(
                                JSONObject(
                                    referenceJson
                                )
                            )
                        }.getOrNull()
                    }
                val committedLinearFit =
                    remember(
                        referenceJson
                    ) {
                        committedReference
                            ?.anchors
                            ?.takeIf {
                                it.size >= 2
                            }
                            ?.let {
                                runCatching {
                                    SpectrumEngine
                                        .fitCalibration(
                                            it,
                                            1
                                        )
                                }.getOrNull()
                            }
                    }
                val committedQuadraticFit =
                    remember(
                        referenceJson
                    ) {
                        committedReference
                            ?.anchors
                            ?.takeIf {
                                it.size >= 3
                            }
                            ?.let {
                                runCatching {
                                    SpectrumEngine
                                        .fitCalibration(
                                            it,
                                            2
                                        )
                                }.getOrNull()
                            }
                    }

                SpectrumSection(
                    "Reference saved",
                    "This calibration is now in the local Reference Spectra Library and will appear automatically in future Star spectrum analyser runs."
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                8.dp
                            )
                    ) {
                        MetricTile(
                            "Reference",
                            obj.optString(
                                StarSpectrumReferenceFields.NAME
                            ),
                            modifier =
                                Modifier.weight(
                                    1f
                                )
                        )
                        MetricTile(
                            "Selected RMS",
                            obj.optDouble(
                                StarSpectrumReferenceFields.RMS_NM
                            ).let {
                                "%.3f nm".format(
                                    Locale.US,
                                    it
                                )
                            },
                            modifier =
                                Modifier.weight(
                                    1f
                                )
                        )
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                8.dp
                            )
                    ) {
                        MetricTile(
                            "Linear RMS",
                            committedLinearFit
                                ?.let {
                                    "%.3f nm".format(
                                        Locale.US,
                                        it.rmsNm
                                    )
                                }
                                ?: "—",
                            modifier =
                                Modifier.weight(
                                    1f
                                )
                        )
                        MetricTile(
                            "Quadratic RMS",
                            committedQuadraticFit
                                ?.let {
                                    "%.3f nm".format(
                                        Locale.US,
                                        it.rmsNm
                                    )
                                }
                                ?: "—",
                            modifier =
                                Modifier.weight(
                                    1f
                                )
                        )
                    }

                    MetricTile(
                        "Anchor source",
                        committedReference?.anchorSource?.replace('_', ' ') ?: obj.optString(StarSpectrumReferenceFields.ANCHOR_SOURCE),
                        modifier = Modifier.fillMaxWidth()
                    )

                    MetricTile(
                        "Reference ID",
                        referenceId,
                        copyValue =
                            referenceId,
                        modifier =
                            Modifier.fillMaxWidth()
                    )
                }

                val committedQaExtraction =
                    extraction
                if (
                    committedReference != null &&
                    committedQaExtraction != null
                ) {
                    SpectrumSection(
                        "Committed calibration QA",
                        "The final calibrated spectrum remains visible with every accepted Balmer anchor overlaid. Shaded edge regions are excluded from Balmer finding but remain part of the stored trace geometry."
                    ) {
                        ReferenceCalibrationQaChart(
                            extraction =
                                committedQaExtraction,
                            reference =
                                committedReference
                        )

                        val selectedResiduals =
                            committedReference
                                .anchors
                                .associate {
                                        anchor ->
                                    anchor.label to
                                        (
                                            committedReference
                                                .wavelength(
                                                    anchor.distancePx
                                                ) -
                                                anchor.wavelengthNm
                                            )
                                }

                        committedReference
                            .anchors
                            .sortedByDescending {
                                it.wavelengthNm
                            }
                            .forEach {
                                    anchor ->
                                val residual =
                                    selectedResiduals[
                                        anchor.label
                                    ]
                                        ?: 0.0
                                val magnitude =
                                    kotlin.math.abs(
                                        residual
                                    )
                                val severity =
                                    when {
                                        magnitude >
                                            3.0 ->
                                            "OUTLIER"

                                        magnitude >
                                            2.0 ->
                                            "WARN"

                                        else ->
                                            "OK"
                                    }

                                Text(
                                    "${anchor.label}  %.2f nm · %.1f px · Δλ %+.3f nm · %s"
                                        .format(
                                            Locale.US,
                                            anchor.wavelengthNm,
                                            anchor.distancePx,
                                            residual,
                                            severity
                                        ),
                                    style =
                                        MaterialTheme
                                            .typography
                                            .bodySmall,
                                    color =
                                        if (
                                            magnitude >
                                            2.0
                                        ) {
                                            MaterialTheme
                                                .colorScheme
                                                .error
                                        } else {
                                            MaterialTheme
                                                .colorScheme
                                                .onSurfaceVariant
                                        },
                                    fontWeight =
                                        if (
                                            magnitude >
                                            3.0
                                        ) {
                                            FontWeight.Bold
                                        } else {
                                            FontWeight.Normal
                                        }
                                )
                            }
                    }
                }

                SpectrumSection(
                    "Reference actions"
                ) {
                    Text(
                        "Complete return package",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Source image (when MethodMesh owns it) · annotated reference image · calibrated reference spectrum CSV · anchor CSV · reusable reference JSON · provenance · manifest · ZIP bundle.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val namedAttachments = listOf(
                        "reference_source_image" to obj.optString(StarSpectrumReferenceFields.SOURCE_IMAGE_URI),
                        "annotated_reference.png" to obj.optString(StarSpectrumReferenceFields.ANNOTATED_IMAGE_URI),
                        "reference_spectrum.csv" to obj.optString(StarSpectrumReferenceFields.SPECTRUM_URI),
                        "calibration_anchors.csv" to obj.optString(StarSpectrumReferenceFields.ANCHORS_URI),
                        "reference.json" to obj.optString(StarSpectrumReferenceFields.REFERENCE_JSON_URI),
                        "provenance.json" to obj.optString(StarSpectrumReferenceFields.PROVENANCE_URI),
                        "manifest.json" to obj.optString(StarSpectrumReferenceFields.MANIFEST_URI),
                        "star_spectrum_reference_bundle.zip" to obj.optString(StarSpectrumReferenceFields.BUNDLE_URI)
                    ).filter { it.second.isNotBlank() }

                    val fullJson =
                        committedResult
                            ?.let {
                                OutputFormatter.format(
                                    it,
                                    ReturnMode.Json,
                                    includeProvenance = true,
                                    payloadMode = OutputFormatter.PayloadMode.FULL
                                )
                            }
                            .orEmpty()

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Include full JSON", style = MaterialTheme.typography.labelLarge)
                            Text(
                                "Advanced MethodMesh provenance/audit payload",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = includeFullJson,
                            onCheckedChange = { includeFullJson = it }
                        )
                    }

                    Button(
                        onClick = {
                            runCatching {
                                shareSpectrumArtifacts(
                                    context = appContext,
                                    label = "Spectrum reference",
                                    attachments = namedAttachments,
                                    text = "${obj.optString(StarSpectrumReferenceFields.NAME)} · ${obj.optString(StarSpectrumReferenceFields.STAR_NAME)} · ${obj.optString(StarSpectrumReferenceFields.RMS_NM)} nm RMS",
                                    fullJson = if (includeFullJson) fullJson else ""
                                )
                            }.onFailure {
                                exportStatus = "Share failed: ${it.message ?: "no sharing app"}"
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Share complete package")
                    }

                    OutlinedButton(
                        onClick = {
                            runCatching {
                                saveSpectrumArtifactsToDownloads(
                                    context = appContext,
                                    label = "Spectrum reference",
                                    uris = namedAttachments.map { it.second },
                                    summary = "${obj.optString(StarSpectrumReferenceFields.NAME)} · ${obj.optString(StarSpectrumReferenceFields.STAR_NAME)} · ${obj.optString(StarSpectrumReferenceFields.RMS_NM)} nm RMS",
                                    fullJson = if (includeFullJson) fullJson else ""
                                )
                            }.onSuccess {
                                exportStatus = "Saved $it"
                            }.onFailure {
                                exportStatus = "Save failed: ${it.message ?: "storage error"}"
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save complete package")
                    }

                    OutlinedButton(
                        onClick = {
                            copyToClipboard(
                                appContext,
                                "Spectrum reference JSON",
                                referenceJson
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Copy reference JSON")
                    }

                    OutlinedButton(
                        onClick = {
                            copyToClipboard(
                                appContext,
                                "MethodMesh full JSON",
                                fullJson
                            )
                        },
                        enabled = fullJson.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Copy full JSON")
                    }

                    exportStatus?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Button(
                        onClick = {
                            committedResult?.let(onConfirmed)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Done")
                    }
                    OutlinedButton(
                        onClick = { committedValuesJson = "" },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Revise reference")
                    }
                }
            }
        }
    }
}

private fun parseAnchors(raw: String): List<CalibrationAnchor> = runCatching {
    val array = JSONArray(raw.ifBlank { "[]" })
    (0 until array.length()).map { CalibrationAnchor.fromJson(array.getJSONObject(it)) }
}.getOrDefault(emptyList())


private fun parseColorTransitionLandmarks(
    raw: String
): List<ColorTransitionLandmark> =
    runCatching {
        val array =
            JSONArray(
                raw.ifBlank {
                    "[]"
                }
            )

        (0 until array.length())
            .mapNotNull { index ->
                val obj =
                    array.getJSONObject(
                        index
                    )
                val id =
                    obj.optString(
                        "id"
                    )
                val definition =
                    ColorTransitionLandmarks
                        .definition(id)
                        ?: return@mapNotNull null

                ColorTransitionLandmark(
                    id = id,
                    label =
                        obj.optString(
                            "label",
                            definition.label
                        ),
                    x =
                        obj.optDouble(
                            "x",
                            Double.NaN
                        ).toFloat(),
                    y =
                        obj.optDouble(
                            "y",
                            Double.NaN
                        ).toFloat(),
                    priorWavelengthNm =
                        obj.optDouble(
                            "prior_wavelength_nm",
                            definition.priorWavelengthNm
                        ),
                    priorSigmaNm =
                        obj.optDouble(
                            "prior_sigma_nm",
                            definition.priorSigmaNm
                        )
                )
            }
            .filter {
                it.x.isFinite() &&
                    it.y.isFinite()
            }
    }.getOrDefault(
        emptyList()
    )
