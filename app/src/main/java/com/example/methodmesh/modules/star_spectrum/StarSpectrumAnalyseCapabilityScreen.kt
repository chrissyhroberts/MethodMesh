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
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.methodmesh.core.crypto.Digests
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
import java.io.File
import java.util.Locale

object StarSpectrumAnalyseCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100StarSpectrumAnalyseMethod.ID
    override val title = "Star spectrum analyser"
    override val description = "Draw roughly along a slitless stellar spectrum; MethodMesh refines the trace, rejects spatial contamination, calibrates against an optional saved reference star, detects features and exports analysis-ready data."

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
        var traceStartX by rememberSaveable { mutableFloatStateOf(Float.NaN) }
        var traceStartY by rememberSaveable { mutableFloatStateOf(Float.NaN) }
        var traceEndX by rememberSaveable { mutableFloatStateOf(Float.NaN) }
        var traceEndY by rememberSaveable { mutableFloatStateOf(Float.NaN) }
        var showEndpointPicker by rememberSaveable { mutableStateOf(false) }
        var openPickerWhenImageLoads by rememberSaveable { mutableStateOf(false) }
        var colorLandmarksJson by rememberSaveable { mutableStateOf("[]") }
        var extraction by remember { mutableStateOf<SpectrumExtraction?>(null) }
        var analysisArtifacts by remember { mutableStateOf<SpectrumArtifacts?>(null) }
        var bundleSourceFile by remember { mutableStateOf<File?>(null) }
        var workingValuesJson by rememberSaveable { mutableStateOf("") }
        var committedValuesJson by rememberSaveable { mutableStateOf("") }
        var shouldRestoreAnalysis by rememberSaveable { mutableStateOf(false) }
        var analysisError by rememberSaveable { mutableStateOf<String?>(null) }
        var isAnalysing by rememberSaveable { mutableStateOf(false) }
        var isPackaging by rememberSaveable { mutableStateOf(false) }
        var displaySmoothingWindow by rememberSaveable { mutableIntStateOf(1) }
        var showAdvanced by rememberSaveable { mutableStateOf(false) }
        var exportStatus by rememberSaveable { mutableStateOf<String?>(null) }
        var includeFullJson by rememberSaveable { mutableStateOf(false) }
        var selectedReferenceId by rememberSaveable {
            mutableStateOf(settings.getString("reference_id").ifBlank { contextInput(context, "reference_id") })
        }
        var showReferenceSelector by rememberSaveable {
            mutableStateOf(false)
        }
        var registrationOffset by rememberSaveable { mutableFloatStateOf(settings.getFloat("registration_offset_px")) }
        var appliedReferenceId by rememberSaveable { mutableStateOf("") }
        var appliedRegistrationOffset by rememberSaveable { mutableFloatStateOf(0f) }
        var ribbonWidth by rememberSaveable { mutableIntStateOf(settings.getInt("ribbon_half_width_px").coerceAtLeast(6)) }
        var apertureWidth by rememberSaveable { mutableIntStateOf(settings.getInt("aperture_half_width_px").coerceAtLeast(1)) }
        var backgroundGap by rememberSaveable { mutableIntStateOf(settings.getInt("background_gap_px").coerceAtLeast(1)) }
        var continuumWindow by rememberSaveable { mutableIntStateOf(settings.getInt("continuum_window").coerceAtLeast(9)) }
        var detectionSigma by rememberSaveable { mutableFloatStateOf(settings.getFloat("detection_sigma").coerceAtLeast(2f)) }
        var detectionMode by rememberSaveable { mutableStateOf(settings.getString("detection_mode").ifBlank { "both" }) }

        var references by remember {
            mutableStateOf(
                SpectrumReferenceRepository.list(appContext)
            )
        }
        val selectedReference =
            references.firstOrNull {
                it.id == selectedReferenceId
            }
        val trace = if (traceStartX.isFinite() && traceStartY.isFinite() && traceEndX.isFinite() && traceEndY.isFinite()) {
            TraceSelection(traceStartX, traceStartY, traceEndX, traceEndY)
        } else null
        val colorLandmarks =
            remember(colorLandmarksJson) {
                parseAnalysisColorTransitionLandmarks(colorLandmarksJson)
            }

        fun updateTrace(value: TraceSelection?) {
            if (value == null) {
                traceStartX = Float.NaN
                traceStartY = Float.NaN
                traceEndX = Float.NaN
                traceEndY = Float.NaN
                colorLandmarksJson = "[]"
            } else {
                traceStartX = value.startX
                traceStartY = value.startY
                traceEndX = value.endX
                traceEndY = value.endY
            }
            extraction = null
            analysisArtifacts = null
            bundleSourceFile = null
            workingValuesJson = ""
            committedValuesJson = ""
            shouldRestoreAnalysis = false
            appliedReferenceId = ""
            appliedRegistrationOffset = 0f
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
                                .put("prior_wavelength_nm", landmark.priorWavelengthNm)
                                .put("prior_sigma_nm", landmark.priorSigmaNm)
                        )
                    }
                }.toString()
            extraction = null
            analysisArtifacts = null
            bundleSourceFile = null
            workingValuesJson = ""
            committedValuesJson = ""
            shouldRestoreAnalysis = false
            appliedReferenceId = ""
            appliedRegistrationOffset = 0f
        }

        fun valuesMap(json: String): Map<String, String> = runCatching {
            val obj = JSONObject(json)
            buildMap { obj.keys().forEach { key -> put(key, obj.optString(key, "")) } }
        }.getOrDefault(emptyMap())

        fun executionFor(json: String): ExecutionResult? {
            if (json.isBlank()) return null
            val values = valuesMap(json)
            val request = As100StarSpectrumAnalyseMethod.request(
                action = capabilityId,
                context = context.request.invocationContext.asMap(capabilityId) + values,
                signals = emptyList(),
                inputs = emptyList()
            )
            return As100StarSpectrumAnalyseMethod.result(request, values, context.request.invocationContext)
        }

        val workingResult = remember(workingValuesJson, context.request.invocationContext) { executionFor(workingValuesJson) }
        val committedResult = remember(committedValuesJson, context.request.invocationContext) { executionFor(committedValuesJson) }
        val isCommitted = committedResult != null

        fun invalidateWorking() {
            if (!isCommitted) {
                extraction = null
                analysisArtifacts = null
                bundleSourceFile = null
                workingValuesJson = ""
                shouldRestoreAnalysis = false
                analysisError = null
                appliedReferenceId = ""
                appliedRegistrationOffset = 0f
            }
        }

        fun invalidateDerivedAnalysisKeepExtraction() {
            if (!isCommitted) {
                workingValuesJson = ""
                committedValuesJson = ""
                shouldRestoreAnalysis = false
                analysisError = null
            }
        }

        val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                SpectrumImageIO.takePersistableReadPermission(appContext, uri)
                sourceUri = uri.toString()
                sourceOrigin = "methodmesh"
                context.onSettingsChanged(mapOf("source_image_uri" to sourceUri))
                updateTrace(null)
                openPickerWhenImageLoads = true
                sourceError = null
            }
        }

        LaunchedEffect(sourceUri) {
            sourceBitmap = null
            if (sourceUri.isBlank()) return@LaunchedEffect
            sourceError = null
            runCatching { withContext(Dispatchers.IO) { SpectrumImageIO.decode(appContext, sourceUri) } }
                .onSuccess { sourceBitmap = it }
                .onFailure { sourceError = it.message ?: "Could not open the spectrum image." }
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
                    onDismiss = { showEndpointPicker = false },
                    onSelected = { selected, selectedLandmarks ->
                        updateTrace(selected)
                        updateColorLandmarks(selectedLandmarks)
                        showEndpointPicker = false
                    }
                )
            }
        }

        if (showReferenceSelector) {
            SpectrumReferenceSelectorDialog(
                references = references,
                selectedId = selectedReferenceId,
                onDismiss = {
                    showReferenceSelector = false
                },
                onSelected = { id ->
                    selectedReferenceId = id
                    settings.setString(
                        "reference_id",
                        id
                    )
                    context.onSettingsChanged(
                        mapOf(
                            "reference_id" to id
                        )
                    )
                    invalidateDerivedAnalysisKeepExtraction()
                    showReferenceSelector = false
                }
            )
        }

        fun currentAnalysisSettings() = SpectrumAnalysisSettings(
            ribbonHalfWidthPx = ribbonWidth,
            apertureHalfWidthPx = minOf(apertureWidth, maxOf(1, ribbonWidth - 2)),
            backgroundGapPx = minOf(backgroundGap, maxOf(1, ribbonWidth - apertureWidth - 1)),
            continuumWindow = continuumWindow,
            detectionSigma = detectionSigma.toDouble(),
            detectionMode = detectionMode
        )

        fun analyseNow(restoring: Boolean = false) {
            val bitmap = sourceBitmap ?: run {
                analysisError = "Choose a spectrum image first."
                return
            }
            val line = trace ?: run {
                analysisError = "Select the RED and VIOLET endpoints first."
                return
            }
            if (line.length < 24f) {
                analysisError = "The selected endpoints are too close together. Select the origin and far end of the visible spectrum."
                return
            }
            if (isAnalysing) return
            isAnalysing = true
            analysisError = null
            if (!restoring) committedValuesJson = ""
            val analysisSettings = currentAnalysisSettings()
            scope.launch {
                runCatching {
                    withContext(Dispatchers.Default) {
                        val calibration = selectedReference?.let {
                            SpectrumEngine.calibrationForTarget(it, bitmap.width, bitmap.height, registrationOffset.toDouble())
                        }
                        val result = SpectrumEngine.analyse(
                            bitmap = bitmap,
                            startX = line.startX.toDouble(), startY = line.startY.toDouble(),
                            endX = line.endX.toDouble(), endY = line.endY.toDouble(),
                            settings = analysisSettings,
                            calibration = calibration
                        )
                        val artifacts = SpectrumExport.writeAnalysisArtifacts(
                            appContext, bitmap, result, sourceUri.substringAfterLast('/'), analysisSettings
                        )
                        val sourceBundleFile = SpectrumImageIO.copyOriginalToCache(
                            appContext,
                            sourceUri,
                            "methodmesh_star_spectrum_source"
                        )
                        Triple(result, artifacts, sourceBundleFile)
                    }
                }.onSuccess { (result, artifacts, sourceBundleFile) ->
                    extraction = result
                    analysisArtifacts = artifacts
                    bundleSourceFile = sourceBundleFile
                    appliedReferenceId = result.calibration?.reference?.id.orEmpty()
                    appliedRegistrationOffset = result.calibration?.registrationOffsetPx?.toFloat() ?: 0f
                    val authority = "${appContext.packageName}.fileprovider"
                    val sourceReturnFile = sourceBundleFile.takeIf { sourceOrigin == "methodmesh" }
                    val sourceReturnUri = sourceReturnFile?.let { FileProvider.getUriForFile(appContext, authority, it).toString() }.orEmpty()
                    val annotatedUri = FileProvider.getUriForFile(appContext, authority, artifacts.annotatedImage).toString()
                    val dataUri = FileProvider.getUriForFile(appContext, authority, artifacts.dataCsv).toString()
                    val featuresUri = FileProvider.getUriForFile(appContext, authority, artifacts.peaksCsv).toString()
                    val metadataUri = FileProvider.getUriForFile(appContext, authority, artifacts.metadataJson).toString()
                    val wavelengths = result.distancesPx.indices.mapNotNull { result.wavelengthAt(it) }
                    val values = linkedMapOf(
                        StarSpectrumAnalyseFields.STATUS to "succeeded",
                        StarSpectrumAnalyseFields.SOURCE_IMAGE_URI to sourceReturnUri,
                        StarSpectrumAnalyseFields.SOURCE_IMAGE_SHA256 to sourceReturnFile?.let { Digests.sha256Hex(it.readBytes()) }.orEmpty(),
                        StarSpectrumAnalyseFields.ANNOTATED_IMAGE_URI to annotatedUri,
                        StarSpectrumAnalyseFields.DATA_URI to dataUri,
                        StarSpectrumAnalyseFields.FEATURES_URI to featuresUri,
                        StarSpectrumAnalyseFields.METADATA_URI to metadataUri,
                        StarSpectrumAnalyseFields.PROVENANCE_URI to "",
                        StarSpectrumAnalyseFields.MANIFEST_URI to "",
                        StarSpectrumAnalyseFields.BUNDLE_URI to "",
                        StarSpectrumAnalyseFields.ANNOTATED_IMAGE_SHA256 to Digests.sha256Hex(artifacts.annotatedImage.readBytes()),
                        StarSpectrumAnalyseFields.DATA_SHA256 to Digests.sha256Hex(artifacts.dataCsv.readBytes()),
                        StarSpectrumAnalyseFields.FEATURES_SHA256 to Digests.sha256Hex(artifacts.peaksCsv.readBytes()),
                        StarSpectrumAnalyseFields.METADATA_SHA256 to Digests.sha256Hex(artifacts.metadataJson.readBytes()),
                        StarSpectrumAnalyseFields.PROVENANCE_SHA256 to "",
                        StarSpectrumAnalyseFields.MANIFEST_SHA256 to "",
                        StarSpectrumAnalyseFields.BUNDLE_SHA256 to "",
                        StarSpectrumAnalyseFields.FEATURES_JSON to SpectrumExport.featuresJson(result.features),
                        StarSpectrumAnalyseFields.FEATURES_TEXT to SpectrumExport.featuresText(result.features),
                        StarSpectrumAnalyseFields.FEATURE_COUNT to result.features.size.toString(),
                        StarSpectrumAnalyseFields.REFERENCE_ID to result.calibration?.reference?.id.orEmpty(),
                        StarSpectrumAnalyseFields.REFERENCE_NAME to result.calibration?.reference?.name.orEmpty(),
                        StarSpectrumAnalyseFields.CALIBRATION_RMS_NM to result.calibration?.reference?.rmsNm?.toString().orEmpty(),
                        StarSpectrumAnalyseFields.CALIBRATION_CONFIDENCE to result.calibration?.confidence?.toString().orEmpty(),
                        StarSpectrumAnalyseFields.CALIBRATION_WARNING to result.calibration?.warning.orEmpty(),
                        StarSpectrumAnalyseFields.WAVELENGTH_MIN_NM to wavelengths.minOrNull()?.toString().orEmpty(),
                        StarSpectrumAnalyseFields.WAVELENGTH_MAX_NM to wavelengths.maxOrNull()?.toString().orEmpty(),
                        StarSpectrumAnalyseFields.TRACE_QUALITY to result.trace.traceQuality.toString(),
                        StarSpectrumAnalyseFields.TRACE_MEAN_CORRECTION_PX to result.trace.meanAbsoluteCorrectionPx.toString(),
                        StarSpectrumAnalyseFields.MEAN_SNR to result.meanSnr.toString(),
                        StarSpectrumAnalyseFields.CONTAMINATED_FRACTION to result.contaminatedFraction.toString(),
                        StarSpectrumAnalyseFields.SOURCE_WIDTH_PX to bitmap.width.toString(),
                        StarSpectrumAnalyseFields.SOURCE_HEIGHT_PX to bitmap.height.toString(),
                        StarSpectrumAnalyseFields.INTENSITY_SEMANTICS to "instrumental relative image signal; corrected_signal is Horne-style optimal extraction and boxcar_signal is preserved separately. No camera/grating/telescope response correction or absolute flux calibration has been applied.",
                        StarSpectrumAnalyseFields.INSTRUMENT_RESPONSE_CORRECTED to "false",
                        StarSpectrumAnalyseFields.CONTINUUM_SEMANTICS to "the broad continuum is the stellar energy distribution convolved with camera, telescope and grating response; it is not a response-corrected stellar continuum.",
                        StarSpectrumAnalyseFields.METADATA_JSON to SpectrumExport.metadataJson(result, sourceUri.substringAfterLast('/'), analysisSettings).toString(),
                        StarSpectrumAnalyseFields.ERROR to ""
                    )
                    workingValuesJson = JSONObject(values as Map<*, *>).toString()
                    shouldRestoreAnalysis = true
                }.onFailure {
                    analysisError = it.message ?: "Spectrum analysis failed."
                    if (!restoring) shouldRestoreAnalysis = false
                }
                isAnalysing = false
            }
        }

        LaunchedEffect(sourceBitmap, shouldRestoreAnalysis, extraction, committedValuesJson) {
            if (sourceBitmap != null && shouldRestoreAnalysis && extraction == null && trace != null && !isAnalysing) {
                analyseNow(restoring = true)
            }
        }

        fun commitAnalysis() {
            val spectrum = extraction ?: return
            val artifacts = analysisArtifacts ?: return
            val sourceFile = bundleSourceFile ?: return
            if (workingValuesJson.isBlank() || isPackaging) return

            isPackaging = true
            analysisError = null
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        SpectrumExport.writeAnalysisCommitPackage(
                            context = appContext,
                            sourceImage = sourceFile,
                            artifacts = artifacts,
                            extraction = spectrum,
                            settings = currentAnalysisSettings(),
                            colorLandmarks = colorLandmarks,
                            sourceLabel = sourceUri.substringAfterLast('/'),
                            sourceOrigin = sourceOrigin,
                            methodId = As100StarSpectrumAnalyseMethod.ID,
                            methodVersion = As100StarSpectrumAnalyseMethod.VERSION
                        )
                    }
                }.onSuccess { packageFiles ->
                    val authority = "${appContext.packageName}.fileprovider"
                    fun contentUri(file: File): String =
                        FileProvider.getUriForFile(appContext, authority, file).toString()

                    val values = valuesMap(workingValuesJson).toMutableMap()
                    values[StarSpectrumAnalyseFields.PROVENANCE_URI] = contentUri(packageFiles.provenanceJson)
                    values[StarSpectrumAnalyseFields.MANIFEST_URI] = contentUri(packageFiles.manifestJson)
                    values[StarSpectrumAnalyseFields.BUNDLE_URI] = contentUri(packageFiles.bundleZip)
                    values[StarSpectrumAnalyseFields.PROVENANCE_SHA256] = SpectrumExport.sha256Hex(packageFiles.provenanceJson)
                    values[StarSpectrumAnalyseFields.MANIFEST_SHA256] = SpectrumExport.sha256Hex(packageFiles.manifestJson)
                    values[StarSpectrumAnalyseFields.BUNDLE_SHA256] = SpectrumExport.sha256Hex(packageFiles.bundleZip)
                    committedValuesJson = JSONObject(values as Map<*, *>).toString()
                    val committed = executionFor(committedValuesJson)
                    if (context.submitsImmediately && committed != null) onConfirmed(committed)
                }.onFailure { error ->
                    analysisError = "Commit package failed: ${error.message ?: "unknown packaging error"}"
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
                subtitle = "trace fit · two-sided background · Horne optimal extraction · wavelength calibration",
                committed = isCommitted,
                canGoBack = context.stepNumber > 1,
                onBack = onBack,
                onCancel = onCancel
            )

            if (!isCommitted) {
                SpectrumSection(
                    "1 · Target observation",
                    "Load the target star-field or slitless-spectrum image. The preparation sequence deliberately mirrors Create spectrum reference so target and reference observations are handled the same way."
                ) {
                    if (context.settingShouldBeShown("source_image_uri")) {
                        Button(onClick = { imagePicker.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (sourceUri.isBlank()) "Upload spectrum photo" else "Replace spectrum photo")
                        }
                    }
                    if (sourceUri.isNotBlank()) {
                        Text(sourceUri.substringAfterLast('/'), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    sourceError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }

                sourceBitmap?.let { bitmap ->
                    SpectrumSection(
                        "2 · Spectrum colour geometry",
                        "One full-screen step places the required RED and VIOLET ends plus the same colour-transition boundaries used by Create spectrum reference. After the endpoints are set, MethodMesh can suggest R/Y, Y/G, G/B and B/V automatically from the photographed colour progression; every marker remains manually revisable."
                    ) {
                        SpectrumImageSurface(
                            bitmap = bitmap,
                            trace = trace,
                            onSelectEndpoints = { showEndpointPicker = true },
                            ribbonHalfWidthPx = ribbonWidth,
                            extraction = extraction,
                            colorLandmarks = colorLandmarks
                        )
                        if (trace != null) {
                            Spacer(Modifier.padding(top = 4.dp))
                            Text(
                                buildString {
                                    append("RED + VIOLET selected")
                                    if (colorLandmarks.isEmpty()) append(" · no optional colour boundaries")
                                    else append(" · ${colorLandmarks.size}/4 optional boundaries")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (colorLandmarks.isNotEmpty()) {
                                Text(
                                    colorLandmarks.joinToString(" · ") { landmark ->
                                        "${landmark.label} ≈ ${"%.0f".format(Locale.US, landmark.priorWavelengthNm)} nm"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                SpectrumSection(
                    "3 · Extract target spectrum",
                    "Run the same trace refinement, two-sided background subtraction and iterative Horne-style optimal extraction used for reference creation. Wavelength interpretation and feature review follow after extraction QA."
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showAdvanced = !showAdvanced }, modifier = Modifier.weight(1f)) {
                            Text(if (showAdvanced) "Hide controls" else "Tune trace")
                        }
                        Button(
                            onClick = { analyseNow(false) },
                            enabled = sourceBitmap != null && trace != null && !isAnalysing,
                            modifier = Modifier.weight(1f)
                        ) { Text(if (isAnalysing) "Extracting…" else "Extract") }
                    }
                    if (showAdvanced) {
                        Text("Search ribbon  $ribbonWidth px", style = MaterialTheme.typography.labelLarge)
                        Slider(value = ribbonWidth.toFloat(), onValueChange = {
                            ribbonWidth = it.toInt().coerceIn(6,120)
                            settings.setInt("ribbon_half_width_px", ribbonWidth)
                            context.onSettingsChanged(mapOf("ribbon_half_width_px" to ribbonWidth.toString()))
                            invalidateWorking()
                        }, valueRange = 6f..120f)
                        Text("Extraction aperture  ±$apertureWidth px", style = MaterialTheme.typography.labelLarge)
                        Slider(value = apertureWidth.toFloat(), onValueChange = {
                            apertureWidth = it.toInt().coerceIn(1,maxOf(1,ribbonWidth-2))
                            settings.setInt("aperture_half_width_px", apertureWidth)
                            context.onSettingsChanged(mapOf("aperture_half_width_px" to apertureWidth.toString()))
                            invalidateWorking()
                        }, valueRange = 1f..minOf(30f,maxOf(2f,ribbonWidth-2f)))
                        Text("Background gap  $backgroundGap px", style = MaterialTheme.typography.labelLarge)
                        Slider(value = backgroundGap.toFloat(), onValueChange = {
                            backgroundGap = it.toInt().coerceIn(1,30)
                            settings.setInt("background_gap_px", backgroundGap)
                            context.onSettingsChanged(mapOf("background_gap_px" to backgroundGap.toString()))
                            invalidateWorking()
                        }, valueRange = 1f..30f)
                    }
                    analysisError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }

                extraction?.let { result ->
                    val chromaticBootstrap = sourceBitmap?.let { bitmap ->
                        remember(bitmap, result, apertureWidth, colorLandmarksJson) {
                            buildChromaticBootstrap(
                                bitmap = bitmap,
                                extraction = result,
                                apertureHalfWidthPx = apertureWidth,
                                landmarks = colorLandmarks
                            )
                        }
                    }

                    SpectrumSection(
                        "4 · Extraction quality audit",
                        "The same intermediate 2-D QA sequence used during reference creation. Check trace fitting, background removal, spatial-profile modelling and outlier rejection before trusting wavelength or feature results."
                    ) { SpectrumProcessingAudit(result) }

                    sourceBitmap?.let { bitmap ->
                        SpectrumSection(
                            "5 · Extracted spectrum appearance",
                            "A horizontal photographic strip made from the optimised extraction aperture, shown in conventional VIOLET → RED order."
                        ) {
                            ExtractedSpectrumStrip(bitmap = bitmap, extraction = result, apertureHalfWidthPx = apertureWidth)
                        }
                    }

                    sourceBitmap?.let { bitmap ->
                        chromaticBootstrap?.let { bootstrap ->
                            SpectrumSection(
                                "6 · Chromatic wavelength bootstrap",
                                "The target observation now gets the same coarse colour-to-wavelength diagnostic as reference creation. The manually placed transition boundaries are broad soft priors; this is preparation for reference registration, not final calibration."
                            ) {
                                ChromaticBootstrapPanel(
                                    bitmap = bitmap,
                                    extraction = result,
                                    apertureHalfWidthPx = apertureWidth,
                                    bootstrap = bootstrap,
                                    landmarks = colorLandmarks
                                )
                            }
                        }
                    }

                    SpectrumSection(
                        "7 · Extracted spectrum trace",
                        "The extraction-only 1-D spectrum, before relying on the saved reference calibration or interpreting detected features."
                    ) { SpectrumTracePreview(extraction = result) }

                    SpectrumSection(
                        "8 · Wavelength reference and registration",
                        "Choose a saved reference after inspecting the target extraction and colour geometry. Applying a reference re-runs the wavelength/feature layer without discarding the inspected preparation stages."
                    ) {
                        if (references.isEmpty()) {
                            Text("No saved reference calibrations yet. This observation remains in pixel coordinates.")
                        } else if (context.settingShouldBeShown("reference_id")) {
                            Button(
                                onClick = {
                                    references = SpectrumReferenceRepository.list(appContext)
                                    showReferenceSelector = true
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    if (selectedReference == null) "Choose reference calibration"
                                    else "Reference calibration: ${selectedReference.name.ifBlank { selectedReference.starName.ifBlank { selectedReference.id.take(8) } }}"
                                )
                            }
                        } else {
                            Text(selectedReference?.name ?: "Uncalibrated", fontWeight = FontWeight.SemiBold)
                        }

                        if (selectedReference == null) {
                            Text(
                                "Current wavelength mode: uncalibrated · pixel/distance axis",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                "${selectedReference.starName.ifBlank { "Unnamed reference star" }} · ${selectedReference.spectralType.ifBlank { "unknown type" }} · ${if (selectedReference.polynomialOrder == 1) "Linear" else "Quadratic"} · ${selectedReference.anchors.size} anchors · RMS ${"%.3f".format(Locale.US, selectedReference.rmsNm)} nm",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (context.settingShouldBeShown("registration_offset_px")) {
                                Spacer(Modifier.padding(top = 3.dp))
                                Text("Registration offset  %.1f px".format(Locale.US, registrationOffset), style = MaterialTheme.typography.labelLarge)
                                Slider(
                                    value = registrationOffset,
                                    onValueChange = {
                                        registrationOffset = it
                                        settings.setFloat("registration_offset_px", it)
                                        context.onSettingsChanged(mapOf("registration_offset_px" to it.toString()))
                                        invalidateDerivedAnalysisKeepExtraction()
                                    },
                                    valueRange = -100f..100f
                                )
                            }
                            val calibrationPending = appliedReferenceId != selectedReference.id || kotlin.math.abs(appliedRegistrationOffset - registrationOffset) > 0.01f
                            if (calibrationPending) {
                                Text(
                                    "Reference selection or registration has changed. Apply it before Commit.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                            Button(
                                onClick = { analyseNow(false) },
                                enabled = !isAnalysing,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (isAnalysing) "Applying…" else if (calibrationPending) "Apply reference calibration" else "Re-run reference calibration")
                            }
                        }

                        result.calibration?.let { calibration ->
                            Spacer(Modifier.padding(top = 4.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MetricTile("Calibration confidence", "%.0f%%".format(Locale.US, calibration.confidence*100), modifier = Modifier.weight(1f))
                                MetricTile("Applied offset", "%.1f px".format(Locale.US, calibration.registrationOffsetPx), modifier = Modifier.weight(1f))
                            }
                            calibration.warning.takeIf { it.isNotBlank() }?.let { Text(it, color = MaterialTheme.colorScheme.tertiary) }
                        }
                    }

                    SpectrumSection(
                        "9 · Feature detection and live result",
                        "Feature detection is downstream of extraction QA and wavelength registration. Adjust these controls, refresh the analysis, then Commit."
                    ) {
                        Text(
                            "Instrumental spectrum · the continuum still includes camera, telescope and grating response. No response correction or absolute flux calibration is applied.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Text("Continuum window  $continuumWindow samples", style = MaterialTheme.typography.labelLarge)
                        Slider(value = continuumWindow.toFloat(), onValueChange = {
                            continuumWindow = it.toInt().coerceIn(9,301)
                            settings.setInt("continuum_window", continuumWindow)
                            context.onSettingsChanged(mapOf("continuum_window" to continuumWindow.toString()))
                            invalidateDerivedAnalysisKeepExtraction()
                        }, valueRange = 9f..301f)
                        Text("Feature threshold  %.2fσ".format(Locale.US,detectionSigma), style = MaterialTheme.typography.labelLarge)
                        Slider(value = detectionSigma, onValueChange = {
                            detectionSigma = it
                            settings.setFloat("detection_sigma", it)
                            context.onSettingsChanged(mapOf("detection_sigma" to it.toString()))
                            invalidateDerivedAnalysisKeepExtraction()
                        }, valueRange = 2f..8f)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("both","emission","absorption").forEach { option ->
                                FilterChip(
                                    selected = detectionMode == option,
                                    onClick = {
                                        detectionMode = option
                                        settings.setString("detection_mode", option)
                                        context.onSettingsChanged(mapOf("detection_mode" to option))
                                        invalidateDerivedAnalysisKeepExtraction()
                                    },
                                    label = { Text(option.replaceFirstChar { it.uppercase() }) }
                                )
                            }
                        }
                        OutlinedButton(onClick = { analyseNow(false) }, enabled = !isAnalysing, modifier = Modifier.fillMaxWidth()) {
                            Text(if (isAnalysing) "Refreshing…" else "Refresh analysis")
                        }

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricTile("Trace quality", "%.0f%%".format(Locale.US,result.trace.traceQuality*100), modifier = Modifier.weight(1f))
                            MetricTile("Features", result.features.size.toString(), modifier = Modifier.weight(1f))
                        }
                        Spacer(Modifier.padding(top = 4.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricTile("Contaminated", "%.1f%%".format(Locale.US,result.contaminatedFraction*100), modifier = Modifier.weight(1f))
                            MetricTile("Calibration", result.calibration?.let { "%.0f%%".format(Locale.US,it.confidence*100) } ?: "pixels", modifier = Modifier.weight(1f))
                        }
                        result.calibration?.warning?.takeIf { it.isNotBlank() }?.let { Text(it, color = MaterialTheme.colorScheme.tertiary) }
                        Spacer(Modifier.padding(top = 4.dp))
                        Text("Display smoothing", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "Low-pass view only. Detection, calibration and exported CSV remain based on the unsmoothed extraction.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(1 to "Raw", 5 to "5", 11 to "11", 21 to "21", 41 to "41").forEach { (window, label) ->
                                FilterChip(
                                    selected = displaySmoothingWindow == window,
                                    onClick = { displaySmoothingWindow = window },
                                    label = { Text(label) }
                                )
                            }
                        }
                        SpectrumChart(result, displaySmoothingWindow = displaySmoothingWindow)
                        Spacer(Modifier.padding(top = 6.dp))
                        FeatureSearchQaLegend(result)
                        Spacer(Modifier.padding(top = 6.dp))
                        Text("Detected spectral features", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Tap a feature row to copy it. Confidence describes local signal evidence and stability, not elemental-identification probability.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FeatureList(result.features)
                        Button(
                            onClick = { commitAnalysis() },
                            enabled = workingResult != null && analysisArtifacts != null && bundleSourceFile != null && !isPackaging,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (isPackaging) "Packaging…" else "Commit analysis") }
                    }
                }
            } else {
                val values = valuesMap(committedValuesJson)
                SpectrumSection("Committed analysis", "The canonical result is frozen. Save or share the artefacts, copy the detected-feature list, then finish.") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricTile("Features", values[StarSpectrumAnalyseFields.FEATURE_COUNT].orEmpty(), modifier = Modifier.weight(1f))
                        MetricTile("Trace quality", values[StarSpectrumAnalyseFields.TRACE_QUALITY]?.toDoubleOrNull()?.let { "%.0f%%".format(Locale.US, it * 100) }.orEmpty(), modifier = Modifier.weight(1f))
                    }
                    Text("Complete return package", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Source image (when MethodMesh owns it) · annotated image · spectrum CSV · features CSV · domain metadata · provenance · manifest · ZIP bundle containing the complete scientific package.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val namedAttachments = listOf(
                        "source_spectrum_image" to values[StarSpectrumAnalyseFields.SOURCE_IMAGE_URI].orEmpty(),
                        "annotated_spectrum.png" to values[StarSpectrumAnalyseFields.ANNOTATED_IMAGE_URI].orEmpty(),
                        "spectrum.csv" to values[StarSpectrumAnalyseFields.DATA_URI].orEmpty(),
                        "features.csv" to values[StarSpectrumAnalyseFields.FEATURES_URI].orEmpty(),
                        "metadata.json" to values[StarSpectrumAnalyseFields.METADATA_URI].orEmpty(),
                        "provenance.json" to values[StarSpectrumAnalyseFields.PROVENANCE_URI].orEmpty(),
                        "manifest.json" to values[StarSpectrumAnalyseFields.MANIFEST_URI].orEmpty(),
                        "star_spectrum_bundle.zip" to values[StarSpectrumAnalyseFields.BUNDLE_URI].orEmpty()
                    ).filter { it.second.isNotBlank() }
                    val saveUris = namedAttachments.map { it.second }
                    val summary = values[StarSpectrumAnalyseFields.FEATURES_TEXT].orEmpty().ifBlank { "Star spectrum analysis: ${values[StarSpectrumAnalyseFields.FEATURE_COUNT].orEmpty()} detected features" }
                    val fullJson = committedResult?.let {
                        OutputFormatter.format(it, ReturnMode.Json, includeProvenance = true, payloadMode = OutputFormatter.PayloadMode.FULL)
                    }.orEmpty()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text("Include full JSON", style = MaterialTheme.typography.labelLarge)
                            Text("Advanced provenance/audit payload", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = includeFullJson, onCheckedChange = { includeFullJson = it })
                    }
                    Button(onClick = {
                        runCatching {
                            shareSpectrumArtifacts(
                                context = appContext,
                                label = "Star spectrum analysis",
                                attachments = namedAttachments,
                                text = summary,
                                fullJson = if (includeFullJson) fullJson else ""
                            )
                        }.onFailure { exportStatus = "Share failed: ${it.message ?: "no sharing app"}" }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Share complete package") }
                    OutlinedButton(onClick = {
                        runCatching { saveSpectrumArtifactsToDownloads(appContext, "Star spectrum analysis", saveUris, summary, if (includeFullJson) fullJson else "") }
                            .onSuccess { exportStatus = "Saved $it" }
                            .onFailure { exportStatus = "Save failed: ${it.message ?: "storage error"}" }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Save complete package") }
                    OutlinedButton(onClick = { copyToClipboard(appContext, "Spectral features", values[StarSpectrumAnalyseFields.FEATURES_TEXT].orEmpty()) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Copy feature list")
                    }
                    exportStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                    Button(onClick = { committedResult?.let(onConfirmed) }, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                    OutlinedButton(onClick = { committedValuesJson = "" }, modifier = Modifier.fillMaxWidth()) { Text("Revise analysis") }
                }
            }
        }
    }
}


private fun parseAnalysisColorTransitionLandmarks(
    raw: String
): List<ColorTransitionLandmark> =
    runCatching {
        val array = JSONArray(raw.ifBlank { "[]" })
        (0 until array.length())
            .mapNotNull { index ->
                val obj = array.getJSONObject(index)
                val id = obj.optString("id")
                val definition = ColorTransitionLandmarks.definition(id) ?: return@mapNotNull null
                ColorTransitionLandmark(
                    id = id,
                    label = obj.optString("label", definition.label),
                    x = obj.optDouble("x", Double.NaN).toFloat(),
                    y = obj.optDouble("y", Double.NaN).toFloat(),
                    priorWavelengthNm = obj.optDouble("prior_wavelength_nm", definition.priorWavelengthNm),
                    priorSigmaNm = obj.optDouble("prior_sigma_nm", definition.priorSigmaNm)
                )
            }
            .filter { it.x.isFinite() && it.y.isFinite() }
    }.getOrDefault(emptyList())
