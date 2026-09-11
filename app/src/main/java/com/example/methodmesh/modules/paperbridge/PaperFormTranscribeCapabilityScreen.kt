package com.example.methodmesh.modules.paperbridge

import android.content.ClipData
import android.content.ClipboardManager
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.widget.Toast
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.methodmesh.MainActivity
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.protocols.PresetResultAction
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.ResultShare
import com.example.methodmesh.transport.ReturnMode
import com.example.methodmesh.transport.workflow.ui.CapabilityCompletionMode
import com.example.methodmesh.transport.workflow.ui.CapabilityPresentationMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import kotlin.math.min
import kotlin.math.roundToInt

object PaperFormTranscribeCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100PaperFormTranscribeMethod.ID
    override val title = "Paper Bridge"
    override val description = "Scan, verify and transcribe a registered paper questionnaire."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val appContext = LocalContext.current
        val scope = rememberCoroutineScope()
        val automaticReturn = context.completionMode == CapabilityCompletionMode.AutomaticReturn
        val nativeDashboard =
            context.presentationMode == CapabilityPresentationMode.Dashboard && !context.isNativePresetRun
        val finishToLauncher = context.request.settings["methodmesh_finish_to_launcher"] == "true" ||
            context.request.settings["input_methodmesh_finish_to_launcher"] == "true"
        val presetResultAction = PresetResultAction.normalize(
            context.request.settings["methodmesh_preset_result_action"]
                ?: context.request.settings["input_methodmesh_preset_result_action"]
                ?: PresetResultAction.HOME
        )

        fun initial(key: String, fallback: String = "") =
            context.action.settings[key]
                ?: context.action.settings["input_$key"]
                ?: context.request.settings[key]
                ?: context.request.settings["input_$key"]
                ?: fallback

        val explicitTemplateJson =
            context.action.settings[PaperBridgeInputs.TEMPLATE_JSON]
                ?: context.action.settings["input_${PaperBridgeInputs.TEMPLATE_JSON}"]
                ?: context.request.settings[PaperBridgeInputs.TEMPLATE_JSON]
                ?: context.request.settings["input_${PaperBridgeInputs.TEMPLATE_JSON}"]
        val dashboardTemplateFallback = if (nativeDashboard && explicitTemplateJson.isNullOrBlank()) {
            PaperBridgeWorkspace.activeTemplateJson(appContext)
        } else {
            PaperTemplateSamples.DEMO_MANIFEST_JSON
        }
        var templateJson by rememberSaveable(context.action.canonicalId) {
            mutableStateOf(
                PaperBridgeWorkspace.normalizeManifest(
                    initial(PaperBridgeInputs.TEMPLATE_JSON, dashboardTemplateFallback)
                )
            )
        }
        var inputSource by rememberSaveable(context.action.canonicalId) {
            mutableStateOf(initial(PaperBridgeInputs.INPUT_SOURCE, "camera"))
        }
        var autoAcceptOmr by rememberSaveable(context.action.canonicalId) {
            mutableStateOf(initial(PaperBridgeInputs.AUTO_ACCEPT_OMR, "true").equals("true", true))
        }
        var autoAcceptOcr by rememberSaveable(context.action.canonicalId) {
            mutableStateOf(initial(PaperBridgeInputs.AUTO_ACCEPT_OCR, "false").equals("true", true))
        }
        // Source and registered-page evidence are part of the Paper Bridge commit contract.
        // Legacy inputs remain accepted for method compatibility but can no longer disable them.
        val returnSourceImage = true
        val returnRectifiedImage = true
        val initialSourceImageUri = initial(PaperBridgeInputs.SOURCE_IMAGE_URI)
        var sourceUriText by rememberSaveable(context.action.canonicalId) {
            mutableStateOf(initialSourceImageUri)
        }
        var pendingCaptureUriText by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
        var manualQuarterTurns by rememberSaveable(context.action.canonicalId) { mutableStateOf(0) }
        var manualOverridesJson by rememberSaveable(context.action.canonicalId) { mutableStateOf("{}") }
        var naOverridesJson by rememberSaveable(context.action.canonicalId) { mutableStateOf("[]") }
        var manualAuditJson by rememberSaveable(context.action.canonicalId) { mutableStateOf("[]") }
        var session by remember { mutableStateOf<PaperExtractionSession?>(null) }
        var results by remember { mutableStateOf<List<PaperFieldResult>>(emptyList()) }
        var processing by remember { mutableStateOf(false) }
        var status by rememberSaveable(context.action.canonicalId) { mutableStateOf("Ready to scan a paper questionnaire.") }
        var scannerActive by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        var lastAcquisitionMode by rememberSaveable(context.action.canonicalId) {
            mutableStateOf(if (initialSourceImageUri.isNotBlank()) "caller_supplied" else "direct")
        }
        var sourceMenu by rememberSaveable { mutableStateOf(false) }
        var technicalOpen by rememberSaveable { mutableStateOf(false) }
        var committedResult by remember { mutableStateOf<ExecutionResult?>(null) }
        var committedStableJson by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
        var committedDynamicJson by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
        var includeFullJson by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        var exportStatus by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
        var workspaceMode by rememberSaveable(context.action.canonicalId) {
            mutableStateOf(if (nativeDashboard) "dashboard" else "scan")
        }
        var workspaceRevision by rememberSaveable(context.action.canonicalId) { mutableStateOf(0) }
        var designerSeedJson by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
        var templateEditorOpen by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        var templateDraft by rememberSaveable(context.action.canonicalId) { mutableStateOf(templateJson) }
        var dashboardMessage by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }

        val template = remember(templateJson) { runCatching { PaperTemplate.parse(templateJson) }.getOrNull() }
        val templateError = remember(templateJson) { runCatching { PaperTemplate.parse(templateJson) }.exceptionOrNull()?.message }
        val workspaceTemplates = remember(workspaceRevision, templateJson) { PaperBridgeWorkspace.templates(appContext) }
        val recentActivity = remember(workspaceRevision) { PaperBridgeWorkspace.recent(appContext) }

        LaunchedEffect(templateJson) { if (!templateEditorOpen) templateDraft = templateJson }

        LaunchedEffect(templateJson, inputSource, autoAcceptOmr, autoAcceptOcr, returnSourceImage, returnRectifiedImage) {
            context.onSettingsChanged(
                mapOf(
                    PaperBridgeInputs.TEMPLATE_JSON to templateJson,
                    PaperBridgeInputs.INPUT_SOURCE to inputSource,
                    PaperBridgeInputs.AUTO_ACCEPT_OMR to autoAcceptOmr.toString(),
                    PaperBridgeInputs.AUTO_ACCEPT_OCR to autoAcceptOcr.toString(),
                    PaperBridgeInputs.RETURN_SOURCE_IMAGE to returnSourceImage.toString(),
                    PaperBridgeInputs.RETURN_RECTIFIED_IMAGE to returnRectifiedImage.toString()
                )
            )
        }

        fun overrides(): Map<String, String> = runCatching {
            val o = JSONObject(manualOverridesJson)
            o.keys().asSequence().associateWith { key -> o.optString(key, "") }
        }.getOrDefault(emptyMap())

        fun naOverrides(): Set<String> = runCatching {
            val a = JSONArray(naOverridesJson)
            (0 until a.length()).map(a::getString).toSet()
        }.getOrDefault(emptySet())

        fun reviewStatus(current: List<PaperFieldResult>): String {
            val unresolved = current.count { !it.resolved }
            val unknown = current.count { it.logicUnknown }
            return when {
                unresolved > 0 -> "$unresolved field${if (unresolved == 1) "" else "s"} require review."
                unknown > 0 -> "All ${current.size} fields are resolved; $unknown XLSForm logic check${if (unknown == 1) "" else "s"} could not be evaluated and are flagged in the audit."
                else -> "All ${current.size} fields are ready to Commit."
            }
        }

        fun appendManualAudit(before: PaperFieldResult, after: PaperFieldResult, action: String) {
            if (action == "manual_edit" && before.finalValue == after.finalValue) return
            val events = runCatching { JSONArray(manualAuditJson) }.getOrDefault(JSONArray())
            events.put(JSONObject().apply {
                put("timestamp_iso", Instant.now().toString())
                put("field", before.spec.name)
                put("action", action)
                put("candidate", before.candidate)
                put("previous_value", before.finalValue)
                put("new_value", after.finalValue)
                put("previous_reason", before.reason)
                put("new_reason", after.reason)
                put("previous_logic_message", before.logicMessage)
                put("new_logic_message", after.logicMessage)
                put("previous_relevant", before.relevant ?: JSONObject.NULL)
                put("new_relevant", after.relevant ?: JSONObject.NULL)
                put("previous_required_now", before.requiredNow ?: JSONObject.NULL)
                put("new_required_now", after.requiredNow ?: JSONObject.NULL)
                put("previous_constraint_satisfied", before.constraintSatisfied ?: JSONObject.NULL)
                put("new_constraint_satisfied", after.constraintSatisfied ?: JSONObject.NULL)
                put("previous_logic_violation", before.logicViolation)
                put("new_logic_violation", after.logicViolation)
                put("previous_logic_unknown", before.logicUnknown)
                put("new_logic_unknown", after.logicUnknown)
                put("previous_na_override", before.naOverride)
                put("new_na_override", after.naOverride)
                put("operator_confirmed", true)
            })
            manualAuditJson = events.toString()
        }

        fun saveOverride(fieldName: String, value: String) {
            val before = results.firstOrNull { it.spec.name == fieldName } ?: return
            val provisional = PaperExtractionEngine.withManualValue(before, value)

            val objectValue = runCatching { JSONObject(manualOverridesJson) }.getOrDefault(JSONObject())
            objectValue.put(fieldName, provisional.finalValue)
            manualOverridesJson = objectValue.toString()

            val na = naOverrides().toMutableSet().apply { remove(fieldName) }
            naOverridesJson = JSONArray(na.toList()).toString()

            val recalculated = PaperXlsLogic.apply(results.map { result ->
                if (result.spec.name == fieldName) provisional else result
            })
            val after = recalculated.first { it.spec.name == fieldName }
            appendManualAudit(before, after, "manual_edit")
            results = recalculated
            status = reviewStatus(results)
        }

        fun setNaOverride(fieldName: String) {
            val before = results.firstOrNull { it.spec.name == fieldName } ?: return
            if (before.naOverride) return
            val provisional = PaperExtractionEngine.withNaOverride(before)

            val objectValue = runCatching { JSONObject(manualOverridesJson) }.getOrDefault(JSONObject())
            objectValue.put(fieldName, "na")
            manualOverridesJson = objectValue.toString()

            val na = naOverrides().toMutableSet().apply { add(fieldName) }
            naOverridesJson = JSONArray(na.toList()).toString()

            val recalculated = PaperXlsLogic.apply(results.map { result ->
                if (result.spec.name == fieldName) provisional else result
            })
            val after = recalculated.first { it.spec.name == fieldName }
            appendManualAudit(before, after, "na_override")
            results = recalculated
            status = reviewStatus(results)
        }

        fun runExtraction(
            stableUri: Uri,
            acquisitionMode: String = "direct",
            preserveExistingPreview: Boolean = false
        ) {
            val parsed = template
            if (parsed == null) {
                status = templateError ?: "Paper template is invalid."
                return
            }
            lastAcquisitionMode = acquisitionMode
            processing = true
            committedResult = null
            if (!preserveExistingPreview) {
                session = null
                results = emptyList()
            }
            status = if (acquisitionMode == "mlkit_document_scanner") {
                "Cropping to registration-centre rectangle and reading fields…"
            } else {
                "Finding registration targets and rectifying target rectangle…"
            }
            scope.launch {
                val outcome = runCatching {
                    PaperExtractionEngine.extract(
                        context = appContext,
                        sourceUri = stableUri,
                        template = parsed,
                        settings = PaperExtractionSettings(autoAcceptOmr, autoAcceptOcr),
                        manualQuarterTurns = manualQuarterTurns,
                        scanTimeIso = Instant.now().toString(),
                        acquisitionMode = acquisitionMode
                    )
                }
                processing = false
                outcome.onFailure { error ->
                    status = error.message ?: "Paper extraction failed."
                    if (!preserveExistingPreview) {
                        session = null
                        results = emptyList()
                    }
                }.onSuccess { extracted ->
                    session = extracted
                    val manual = overrides()
                    val na = naOverrides()
                    val reapplied = extracted.results.map { result ->
                        when {
                            result.spec.name in na -> PaperExtractionEngine.withNaOverride(result)
                            manual[result.spec.name] != null -> PaperExtractionEngine.withManualValue(result, manual.getValue(result.spec.name))
                            else -> result
                        }
                    }
                    results = PaperXlsLogic.apply(reapplied)
                    status = reviewStatus(results)
                }
            }
        }

        val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            val pending = pendingCaptureUriText.takeIf(String::isNotBlank)?.let(Uri::parse)
            if (ok && pending != null) {
                sourceUriText = pending.toString()
                manualOverridesJson = "{}"
                naOverridesJson = "[]"
                manualAuditJson = "[]"
                manualQuarterTurns = 0
                status = "Fallback camera capture complete. Finding registration targets…"
                runExtraction(pending, acquisitionMode = "camera")
            } else {
                status = "Camera capture cancelled."
                if (nativeDashboard && session == null) workspaceMode = "dashboard"
            }
        }
        val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) {
                status = "Image selection cancelled."
                if (nativeDashboard && session == null) workspaceMode = "dashboard"
            } else {
                status = "Preparing selected image…"
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { PaperBridgeFiles.copyIntoCache(appContext, uri, "source") } }
                        .onFailure {
                            status = it.message ?: "Could not copy selected image."
                            if (nativeDashboard && session == null) workspaceMode = "dashboard"
                        }
                        .onSuccess { stable ->
                            sourceUriText = stable.toString()
                            manualOverridesJson = "{}"
                            naOverridesJson = "[]"
                            manualAuditJson = "[]"
                            manualQuarterTurns = 0
                            workspaceMode = "scan"
                            runExtraction(stable, acquisitionMode = "file_picker")
                        }
                }
            }
        }
        val scannerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { activityResult ->
            scannerActive = false
            if (activityResult.resultCode != Activity.RESULT_OK) {
                status = "Document scan cancelled."
                if (nativeDashboard && session == null) workspaceMode = "dashboard"
                return@rememberLauncherForActivityResult
            }
            val scanResult = activityResult.data?.let { GmsDocumentScanningResult.fromActivityResultIntent(it) }
            val scannedPage = scanResult?.pages?.firstOrNull()?.imageUri
            if (scannedPage == null) {
                status = "ML Kit did not return a scanned page."
                if (nativeDashboard && session == null) workspaceMode = "dashboard"
                return@rememberLauncherForActivityResult
            }
            status = "Page cropped and straightened. Preparing template registration…"
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        PaperBridgeFiles.copyIntoCache(appContext, scannedPage, "mlkit-page")
                    }
                }.onFailure { error ->
                    status = "Could not copy ML Kit scan: ${error.message ?: "scanner output unavailable"}"
                    if (nativeDashboard && session == null) workspaceMode = "dashboard"
                }.onSuccess { stable ->
                    sourceUriText = stable.toString()
                    manualOverridesJson = "{}"
                    naOverridesJson = "[]"
                    manualAuditJson = "[]"
                    manualQuarterTurns = 0
                    workspaceMode = "scan"
                    status = "ML Kit page ready. Finding registration targets…"
                    runExtraction(stable, acquisitionMode = "mlkit_document_scanner")
                }
            }
        }

        fun launchFallbackAcquisition(source: String) {
            if (source == "file_picker") {
                pickImage.launch("image/*")
            } else {
                val uri = PaperBridgeFiles.newCaptureUri(appContext)
                pendingCaptureUriText = uri.toString()
                takePicture.launch(uri)
            }
        }

        fun startMlKitDocumentScan(source: String) {
            val activity = appContext.findPaperBridgeActivity()
            if (activity == null) {
                scannerActive = false
                status = "No Android activity was available for ML Kit scanning; using basic acquisition instead."
                launchFallbackAcquisition(source)
                return
            }
            val options = GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setPageLimit(1)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build()
            status = if (source == "file_picker") {
                "Opening ML Kit scanner — choose the page from gallery if needed…"
            } else {
                "Opening ML Kit document scanner…"
            }
            GmsDocumentScanning.getClient(options)
                .getStartScanIntent(activity)
                .addOnSuccessListener { sender ->
                    // Keep the host behind ML Kit visually blank. Some scanner builds
                    // use translucent transitions; dashboard help text must never show
                    // through the acquisition UI.
                    scannerActive = true
                    scannerLauncher.launch(IntentSenderRequest.Builder(sender).build())
                }
                .addOnFailureListener { error ->
                    scannerActive = false
                    status = "ML Kit scanner unavailable (${error.message.orEmpty()}). Using basic acquisition instead."
                    launchFallbackAcquisition(source)
                }
        }
        val pickTemplate = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                scope.launch {
                    val outcome = runCatching {
                        withContext(Dispatchers.IO) { PaperTemplateImport.importIntoWorkspace(appContext, uri) }
                    }
                    outcome.onFailure { error ->
                        dashboardMessage = "Template not imported: ${error.message ?: "invalid manifest"}"
                    }.onSuccess { imported ->
                        templateJson = imported.rawJson
                        templateDraft = imported.rawJson
                        workspaceRevision += 1
                        dashboardMessage = "Using ${imported.title}."
                    }
                }
            }
        }

        fun capture(sourceOverride: String? = null) {
            if (template == null) {
                status = templateError ?: "Paper template is invalid."
                dashboardMessage = status
                return
            }
            val source = sourceOverride ?: inputSource
            inputSource = source
            committedResult = null
            committedStableJson = ""
            committedDynamicJson = ""
            includeFullJson = false
            exportStatus = ""
            workspaceMode = "scan"
            // Stage 1: let ML Kit find the sheet, crop it and correct coarse perspective.
            // Stage 2: Paper Bridge detects the template registration system on that
            // normalised page. APRILTAG8 schemas use eight unique perimeter tags and all
            // detected tag corners to rectify once into the canonical page coordinates.
            startMlKitDocumentScan(source)
        }

        LaunchedEffect(Unit) {
            if (sourceUriText.isNotBlank() && session == null && !processing) {
                val stable = runCatching {
                    val uri = Uri.parse(sourceUriText)
                    if (uri.authority == "${appContext.packageName}.fileprovider") uri
                    else withContext(Dispatchers.IO) { PaperBridgeFiles.copyIntoCache(appContext, uri, "source") }
                }.getOrNull()
                if (stable != null) {
                    sourceUriText = stable.toString()
                    runExtraction(stable, acquisitionMode = lastAcquisitionMode)
                }
            } else if (context.startsImmediately && sourceUriText.isBlank()) {
                capture()
            }
        }

        fun rotateAndReprocess() {
            val uri = sourceUriText.takeIf(String::isNotBlank)?.let(Uri::parse) ?: return
            manualQuarterTurns = (manualQuarterTurns + 1) % 4
            // Rotation is a re-registration of the SAME acquisition. Never switch an
            // ML Kit page back into the raw-image homography path, and keep the last
            // good preview/field overlay visible while the replacement is computed.
            runExtraction(
                uri,
                acquisitionMode = lastAcquisitionMode,
                preserveExistingPreview = true
            )
        }

        fun commit() {
            val extracted = session ?: return
            if (results.any { !it.resolved }) {
                status = "Resolve every review item before Commit."
                return
            }
            val dynamic = results.associate { it.spec.name to it.finalValue }
            scope.launch {
                val stable = runCatching {
                    val sourceHash = withContext(Dispatchers.IO) { PaperBridgeFiles.sha256(appContext, extracted.sourceUri) }
                    val rectifiedHash = withContext(Dispatchers.IO) { PaperBridgeFiles.sha256(appContext, extracted.rectifiedUri) }
                    val manualEditCount = runCatching { JSONArray(manualAuditJson).length() }.getOrDefault(0)
                    val logicChecksJson = PaperXlsLogic.checksJson(results)
                    val logicViolationCount = results.count { it.logicViolation }
                    val logicUnknownCount = results.count { it.logicUnknown }
                    val naFieldsJson = JSONArray(results.filter { it.naOverride }.map { it.spec.name }).toString()
                    linkedMapOf(
                        PaperBridgeFields.STATUS to "succeeded",
                        PaperBridgeFields.TEMPLATE_ID to extracted.template.templateId,
                        PaperBridgeFields.TEMPLATE_VERSION to extracted.template.version,
                        PaperBridgeFields.VALUES_JSON to PaperExtractionEngine.valuesJson(results),
                        PaperBridgeFields.DYNAMIC_FIELDS_JSON to PaperExtractionEngine.dynamicFieldsJson(results),
                        PaperBridgeFields.FIELD_COUNT to results.size.toString(),
                        PaperBridgeFields.AUTO_ACCEPTED_COUNT to results.count { it.autoAccepted }.toString(),
                        PaperBridgeFields.REVIEWED_COUNT to results.count { it.reviewed }.toString(),
                        PaperBridgeFields.UNRESOLVED_COUNT to "0",
                        // The caller needs the complete scan bundle: original
                        // acquisition plus registered analysis page. A caller-
                        // supplied source is still part of that bundle.
                        PaperBridgeFields.SOURCE_IMAGE to extracted.sourceUri.toString(),
                        PaperBridgeFields.RECTIFIED_IMAGE to extracted.rectifiedUri.toString(),
                        PaperBridgeFields.TEMPLATE_SHA256 to PaperBridgeFiles.sha256Text(extracted.template.rawJson),
                        PaperBridgeFields.SOURCE_SHA256 to sourceHash,
                        PaperBridgeFields.RECTIFIED_SHA256 to rectifiedHash,
                        PaperBridgeFields.EXTRACTION_AUDIT_JSON to PaperExtractionEngine.auditJson(extracted, results, manualAuditJson),
                        PaperBridgeFields.MANUAL_EDITS_JSON to manualAuditJson,
                        PaperBridgeFields.MANUAL_EDIT_COUNT to manualEditCount.toString(),
                        PaperBridgeFields.LOGIC_CHECKS_JSON to logicChecksJson,
                        PaperBridgeFields.LOGIC_VIOLATION_COUNT to logicViolationCount.toString(),
                        PaperBridgeFields.LOGIC_UNKNOWN_COUNT to logicUnknownCount.toString(),
                        PaperBridgeFields.NA_FIELDS_JSON to naFieldsJson,
                        PaperBridgeFields.ATTACHMENT_METADATA_JSON to JSONObject().apply {
                            put("schema", "methodmesh.paper.attachments.v1")
                            put("source", JSONObject().apply {
                                put("sha256", sourceHash)
                                put("role", "source_scan")
                                put("returned_to_caller", !(automaticReturn && extracted.acquisitionMode == "caller_supplied"))
                            })
                            put("rectified", JSONObject().apply {
                                put("sha256", rectifiedHash)
                                put("role", "registered_analysis_page")
                                put("width_px", extracted.rectifiedBitmap.width)
                                put("height_px", extracted.rectifiedBitmap.height)
                                put("coordinate_frame", if (extracted.template.registration.type == PaperRegistrationType.APRILTAG8) "canonical_page" else "anchor_centres")
                                put("registration_type", extracted.template.registration.type.name.lowercase())
                                if (extracted.template.registration.type != PaperRegistrationType.BULLSEYE4 && extracted.template.registration.schemaKey.isNotBlank()) put("registration_schema_key", extracted.template.registration.schemaKey)
                            })
                            put("field_attachments", org.json.JSONArray().apply {
                                results.filter { it.spec.type == PaperFieldType.IMAGE && it.finalValue.startsWith("content://") }.forEach { imageResult ->
                                    val imageUri = Uri.parse(imageResult.finalValue)
                                    put(JSONObject().apply {
                                        put("field", imageResult.spec.name)
                                        put("label", imageResult.spec.label)
                                        put("sha256", withContext(Dispatchers.IO) { PaperBridgeFiles.sha256(appContext, imageUri) })
                                        put("role", "paper_roi_image")
                                    })
                                }
                            })
                            put("acquisition_mode", extracted.acquisitionMode)
                            put("registration_mode", extracted.rectification.registrationMode)
                            put("scan_time_iso", extracted.scanTimeIso)
                        }.toString(),
                        PaperBridgeFields.SCAN_TIME_ISO to extracted.scanTimeIso,
                        PaperBridgeFields.ERROR to ""
                    )
                }.getOrElse { error ->
                    status = "Commit failed: ${error.message ?: "could not construct final audit payload"}"
                    return@launch
                }
                val request = As100PaperFormTranscribeMethod.request(
                    action = As100PaperFormTranscribeMethod.ID,
                    context = context.request.invocationContext.asMap(As100PaperFormTranscribeMethod.ID) + context.action.settings,
                    signals = emptyList(),
                    inputs = emptyList()
                )
                val execution = As100PaperFormTranscribeMethod.result(request, stable, dynamic, context.request.invocationContext)
                committedResult = execution
                committedStableJson = JSONObject(stable).toString()
                committedDynamicJson = JSONObject(dynamic).toString()
                includeFullJson = false
                exportStatus = ""
                if (!automaticReturn) {
                    PaperBridgeWorkspace.recordCommit(
                        context = appContext,
                        template = extracted.template,
                        scanTimeIso = extracted.scanTimeIso,
                        fieldCount = results.size,
                        autoAcceptedCount = results.count { it.autoAccepted },
                        reviewedCount = results.count { it.reviewed }
                    )
                    workspaceRevision += 1
                }

                if (automaticReturn) {
                    status = "Committed · returning to calling workflow."
                    onConfirmed(execution)
                } else {
                    status = "Committed. Share, Save or Done when ready."
                }
            }
        }

        val unresolved = results.count { !it.resolved }
        val autoAccepted = results.count { it.autoAccepted }
        val reviewed = results.count { it.reviewed }
        fun mapFromJson(raw: String): Map<String, String> = if (raw.isBlank()) emptyMap() else runCatching {
            val obj = JSONObject(raw)
            buildMap { obj.keys().forEach { key -> put(key, obj.optString(key, "")) } }
        }.getOrDefault(emptyMap())

        val committedStable = remember(committedStableJson) { mapFromJson(committedStableJson) }
        val committedDynamic = remember(committedDynamicJson) { mapFromJson(committedDynamicJson) }
        val restoredCommittedResult = remember(committedStableJson, committedDynamicJson) {
            if (committedStable.isEmpty()) null else {
                val request = As100PaperFormTranscribeMethod.request(
                    action = As100PaperFormTranscribeMethod.ID,
                    context = context.request.invocationContext.asMap(As100PaperFormTranscribeMethod.ID) + context.action.settings,
                    signals = emptyList(),
                    inputs = emptyList()
                )
                As100PaperFormTranscribeMethod.result(request, committedStable, committedDynamic, context.request.invocationContext)
            }
        }
        val frozenResult = committedResult ?: restoredCommittedResult
        val committedFields = remember(frozenResult?.request?.id?.value, committedStableJson, committedDynamicJson) {
            frozenResult?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty()
        }
        val fullJson = remember(frozenResult?.request?.id?.value, committedStableJson, committedDynamicJson) {
            frozenResult?.let {
                OutputFormatter.format(it, ReturnMode.Json, includeProvenance = true, payloadMode = OutputFormatter.PayloadMode.FULL)
            }.orEmpty()
        }

        fun committedBeefText(): String {
            val fieldsByName = template?.fields?.associateBy { it.name }.orEmpty()
            return committedDynamic.entries
                .filter { (key, _) -> fieldsByName[key]?.type != PaperFieldType.IMAGE }
                .joinToString("\n") { (key, value) ->
                    "${fieldsByName[key]?.label.orEmpty().ifBlank { key }}: $value"
                }
        }

        fun committedAttachments(): List<PaperBridgeShareAttachment> {
            val fieldAttachments = template?.fields.orEmpty()
                .filter { it.type == PaperFieldType.IMAGE }
                .mapNotNull { field ->
                    committedDynamic[field.name]
                        ?.takeIf { ResultShare.isShareableMediaField(field.name, it) }
                        ?.let { PaperBridgeShareAttachment("field_${field.name}.jpg", it) }
                }
            val pageAttachments = listOfNotNull(
                committedStable[PaperBridgeFields.SOURCE_IMAGE]
                    ?.takeIf { ResultShare.isShareableMediaField(PaperBridgeFields.SOURCE_IMAGE, it) }
                    ?.let { PaperBridgeShareAttachment("paper_source.jpg", it) },
                committedStable[PaperBridgeFields.RECTIFIED_IMAGE]
                    ?.takeIf { ResultShare.isShareableMediaField(PaperBridgeFields.RECTIFIED_IMAGE, it) }
                    ?.let { PaperBridgeShareAttachment("paper_rectified.jpg", it) }
            )
            return (pageAttachments + fieldAttachments).distinctBy { it.uri }
        }

        fun shareCommitted() {
            runCatching {
                val attachments = committedAttachments()
                ResultShare.share(
                    context = appContext,
                    chooserTitle = "Share Paper Bridge transcription",
                    text = committedBeefText(),
                    attachments = attachments.map { ResultShare.Attachment(it.name, Uri.parse(it.uri)) },
                    jsonText = if (includeFullJson) fullJson else "",
                    fileLabel = "Paper Bridge transcription"
                )
                exportStatus = "Sharing transcription text plus ${attachments.size} media attachment${if (attachments.size == 1) "" else "s"}${if (includeFullJson) " with debug JSON text" else ""}."
            }.onFailure { exportStatus = "Share failed: ${it.message ?: "no sharing app available"}" }
        }

        fun saveCommitted() {
            val text = committedBeefText()
            val attachments = committedAttachments()
            if (text.isBlank() && attachments.isEmpty()) return
            runCatching {
                PaperBridgeResultActions.saveToFiles(
                    context = appContext,
                    collectionLabel = committedStable[PaperBridgeFields.TEMPLATE_ID].orEmpty().ifBlank { "transcription" },
                    textFileName = "transcription.txt",
                    text = text,
                    attachments = attachments,
                    jsonText = if (includeFullJson) fullJson else "",
                    entryId = frozenResult?.request?.id?.value
                )
            }.onSuccess { saved ->
                exportStatus = "Saved ${saved.fileCount} file${if (saved.fileCount == 1) "" else "s"} to MethodMesh Files."
            }.onFailure { exportStatus = "Save failed: ${it.message ?: "Files storage error"}" }
        }

        fun finishCommitted(resultValue: ExecutionResult) {
            if (!context.isNativePresetRun || !context.isLastStep) {
                onConfirmed(resultValue)
                return
            }
            when {
                presetResultAction == PresetResultAction.SAVE -> { saveCommitted(); onConfirmed(resultValue) }
                finishToLauncher -> onConfirmed(resultValue)
                else -> appContext.startActivity(Intent(appContext, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        }

        if (nativeDashboard && workspaceMode == "designer" && frozenResult == null) {
            val designerSeed = remember(designerSeedJson) {
                designerSeedJson.takeIf { it.isNotBlank() }?.let { runCatching { PaperTemplate.parse(it) }.getOrNull() }
            }
            PaperFormDesignerSurface(
                initialTemplate = designerSeed,
                onBack = { workspaceMode = "dashboard" },
                onSaved = { saved, _, _, _, _ ->
                    templateJson = saved.rawJson
                    templateDraft = saved.rawJson
                    workspaceRevision += 1
                    dashboardMessage = "Saved ${saved.title}. It is now the active Paper Bridge form."
                    workspaceMode = "dashboard"
                }
            )
        } else if (nativeDashboard && workspaceMode == "dashboard" && frozenResult == null) {
            PaperBridgeDashboard(
                template = template,
                templateError = templateError,
                templates = workspaceTemplates,
                recentActivity = recentActivity,
                hasWorkingScan = session != null || sourceUriText.isNotBlank(),
                workingUnresolved = unresolved,
                autoAcceptOmr = autoAcceptOmr,
                autoAcceptOcr = autoAcceptOcr,
                returnSourceImage = returnSourceImage,
                returnRectifiedImage = returnRectifiedImage,
                templateEditorOpen = templateEditorOpen,
                templateDraft = templateDraft,
                message = dashboardMessage,
                onCameraScan = { capture("camera") },
                onImageScan = { capture("file_picker") },
                onDesignForm = { selected ->
                    designerSeedJson = selected?.rawJson.orEmpty()
                    workspaceMode = "designer"
                },
                onResumeWorkingScan = { workspaceMode = "scan" },
                onSelectTemplate = { selected ->
                    PaperBridgeWorkspace.activateTemplate(appContext, selected)
                    templateJson = selected.rawJson
                    templateDraft = selected.rawJson
                    workspaceRevision += 1
                    dashboardMessage = "Using ${selected.title}."
                },
                onImportTemplate = {
                    pickTemplate.launch(arrayOf("application/json", "application/zip", "text/yaml", "text/plain", "application/octet-stream"))
                },
                onRemoveTemplate = { selected ->
                    PaperBridgeWorkspace.removeTemplate(appContext, selected)
                    val active = PaperBridgeWorkspace.activeTemplateJson(appContext)
                    templateJson = active
                    templateDraft = active
                    workspaceRevision += 1
                    dashboardMessage = "Schema deleted."
                },
                onDuplicateTemplate = { selected ->
                    val duplicate = PaperBridgeWorkspace.duplicateTemplate(appContext, selected)
                    templateJson = duplicate.rawJson
                    templateDraft = duplicate.rawJson
                    designerSeedJson = duplicate.rawJson
                    workspaceRevision += 1
                    dashboardMessage = "Created ${duplicate.title}."
                    workspaceMode = "designer"
                },
                onRenameTemplate = { selected, newName ->
                    runCatching { PaperBridgeWorkspace.renameTemplate(appContext, selected, newName) }
                        .onFailure { dashboardMessage = "Schema not renamed: ${it.message ?: "invalid name"}" }
                        .onSuccess { renamed ->
                            templateJson = renamed.rawJson
                            templateDraft = renamed.rawJson
                            workspaceRevision += 1
                            dashboardMessage = "Renamed schema to ${renamed.title}."
                        }
                },
                onTemplateEditorOpen = { templateEditorOpen = it },
                onTemplateDraft = { templateDraft = it },
                onSaveTemplateDraft = {
                    runCatching { PaperBridgeWorkspace.saveAndActivateTemplate(appContext, templateDraft) }
                        .onFailure { dashboardMessage = "Template not saved: ${it.message ?: "invalid manifest"}" }
                        .onSuccess { saved ->
                            templateJson = saved.rawJson
                            templateDraft = saved.rawJson
                            templateEditorOpen = false
                            workspaceRevision += 1
                            dashboardMessage = "Saved ${saved.title}."
                        }
                },
                onUseDemoTemplate = {
                    val demo = PaperTemplate.parse(PaperTemplateSamples.DEMO_MANIFEST_JSON)
                    PaperBridgeWorkspace.activateTemplate(appContext, demo)
                    templateJson = demo.rawJson
                    templateDraft = demo.rawJson
                    workspaceRevision += 1
                    dashboardMessage = "Using the bundled demo template."
                },
                onCopyTemplate = { copyText(appContext, "Paper Bridge template", templateJson) },
                onAutoAcceptOmr = { autoAcceptOmr = it },
                onAutoAcceptOcr = { autoAcceptOcr = it },
                onReturnSourceImage = { },
                onReturnRectifiedImage = { },
                onClearRecent = {
                    PaperBridgeWorkspace.clearRecent(appContext)
                    workspaceRevision += 1
                    dashboardMessage = "Recent activity cleared."
                },
                onClose = onCancel
            )
        } else {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                if (nativeDashboard) {
                    Text(
                        "‹  Dashboard",
                        modifier = Modifier.clickable {
                            committedResult = null
                            committedStableJson = ""
                            committedDynamicJson = ""
                            workspaceMode = "dashboard"
                        }.padding(vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Text("Paper Bridge", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    template?.let { "${it.title} · ${it.templateId} v${it.version}" } ?: "Template unavailable",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))

                if (frozenResult != null && !automaticReturn) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Text("Committed transcription", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(8.dp))
                            val fieldsByName = template?.fields?.associateBy { it.name }.orEmpty()
                            committedDynamic.forEach { (name, value) ->
                                val field = fieldsByName[name]
                                if (field?.type == PaperFieldType.IMAGE && value.startsWith("content://")) {
                                    PaperCommittedAttachment(
                                        context = appContext,
                                        label = field.label.ifBlank { name },
                                        uriText = value
                                    )
                                } else {
                                    TappablePaperValue(appContext, field?.label.orEmpty().ifBlank { name }, value)
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                            Text(
                                "${committedDynamic.size} fields · ${committedStable[PaperBridgeFields.AUTO_ACCEPTED_COUNT].orEmpty()} automatic · ${committedStable[PaperBridgeFields.REVIEWED_COUNT].orEmpty()} reviewed",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(modifier = Modifier.weight(1f), onClick = ::shareCommitted) { Text("Share") }
                        Button(modifier = Modifier.weight(1f), onClick = ::saveCommitted) { Text("Save") }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = includeFullJson, onCheckedChange = { includeFullJson = it })
                        Spacer(Modifier.size(8.dp))
                        Column {
                            Text("Include full JSON / audit", style = MaterialTheme.typography.labelLarge)
                            Text("Share appends debug JSON as text; Save adds metadata.json.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (exportStatus.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(exportStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(modifier = Modifier.fillMaxWidth(), onClick = {
                        val resultValue = requireNotNull(frozenResult)
                        if (nativeDashboard) {
                            committedResult = null
                            committedStableJson = ""
                            committedDynamicJson = ""
                            includeFullJson = false
                            exportStatus = ""
                            session = null
                            results = emptyList()
                            sourceUriText = ""
                            manualOverridesJson = "{}"
                            naOverridesJson = "[]"
                            manualAuditJson = "[]"
                            manualQuarterTurns = 0
                            workspaceMode = "dashboard"
                            dashboardMessage = "Committed transcription added to recent activity."
                        } else {
                            finishCommitted(resultValue)
                        }
                    }) {
                        Text(if (nativeDashboard) "Back to Paper Bridge" else "Done")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = {
                        committedResult = null
                        committedStableJson = ""
                        committedDynamicJson = ""
                        includeFullJson = false
                        exportStatus = ""
                        status = "Committed result reopened for editing."
                    }) { Text("Edit") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = {
                        copyText(appContext, "Paper Bridge JSON", fullJson)
                    }) { Text("Copy full JSON") }
                } else {
                    if (processing) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f)
                        ) {
                            Column(Modifier.padding(18.dp)) {
                                Text("Analysing paper", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(status, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    session?.let { extracted ->
                        RegisteredPaperPreview(
                            bitmap = extracted.rectifiedBitmap,
                            template = extracted.template,
                            modifier = Modifier.fillMaxWidth().height(420.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SummaryPill("Fields", results.size.toString(), Modifier.weight(1f))
                            SummaryPill("Auto", autoAccepted.toString(), Modifier.weight(1f))
                            SummaryPill("Review", unresolved.toString(), Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(14.dp))
                        results.forEach { result ->
                            PaperFieldCard(
                                result = result,
                                pageBitmap = extracted.rectifiedBitmap,
                                onManualValue = { value -> saveOverride(result.spec.name, value) },
                                onNaOverride = { setNaOverride(result.spec.name) },
                                onCopy = { copyText(appContext, result.spec.label, it) }
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                    }

                    if (session == null && !processing) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
                        ) {
                            Column(Modifier.padding(18.dp)) {
                                Text("Scan complete page", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text("ML Kit first finds and straightens the sheet. For current schemas Paper Bridge then detects the eight unique AprilTag36h11 perimeter markers, estimates one robust page homography from their tag corners, rectifies into canonical template coordinates, and only then reads saved ROIs. Legacy QR4/bullseye schemas remain supported.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    templateError?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(14.dp))

                    if (!nativeDashboard && context.settingShouldBeShown(PaperBridgeInputs.INPUT_SOURCE, alwaysShow = true)) {
                        Text("Acquisition", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { sourceMenu = true }) {
                            Text(if (inputSource == "file_picker") "Image file" else "Camera", modifier = Modifier.weight(1f))
                            Text("▼")
                        }
                        DropdownMenu(expanded = sourceMenu, onDismissRequest = { sourceMenu = false }) {
                            DropdownMenuItem(text = { Text("Camera") }, onClick = { inputSource = "camera"; sourceMenu = false })
                            DropdownMenuItem(text = { Text("Image file") }, onClick = { inputSource = "file_picker"; sourceMenu = false })
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    if (!nativeDashboard && (context.settingShouldBeShown(PaperBridgeInputs.AUTO_ACCEPT_OMR) || context.settingShouldBeShown(PaperBridgeInputs.AUTO_ACCEPT_OCR))) {
                        Text("Review policy", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        if (context.settingShouldBeShown(PaperBridgeInputs.AUTO_ACCEPT_OMR)) {
                            ToggleSettingRow("Auto-accept unambiguous marks", autoAcceptOmr) { autoAcceptOmr = it }
                        }
                        if (context.settingShouldBeShown(PaperBridgeInputs.AUTO_ACCEPT_OCR)) {
                            ToggleSettingRow("Auto-accept validated OCR", autoAcceptOcr) { autoAcceptOcr = it }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    if (!nativeDashboard) {
                        Text("Return evidence", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(
                            "Every Commit returns both the source scan and the registered analysis page, with SHA-256 and registration metadata. These attachments cannot be disabled.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 3.dp, bottom = 8.dp)
                        )
                    }
                    Button(modifier = Modifier.fillMaxWidth(), enabled = !processing && template != null, onClick = { capture() }) {
                        Text(if (session == null) "Scan page" else "Rescan page")
                    }
                    if (sourceUriText.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(modifier = Modifier.fillMaxWidth(), enabled = !processing, onClick = { rotateAndReprocess() }) {
                            Text("Rotate 90° and re-analyse")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (nativeDashboard && session != null && unresolved > 0) {
                        Text(
                            "$unresolved answer${if (unresolved == 1) "" else "s"} need checking above before you can Commit.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = session != null && !processing && unresolved == 0 && results.isNotEmpty(),
                        onClick = { commit() }
                    ) { Text(if (nativeDashboard) "Commit answers" else "Commit") }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (context.stepNumber > 1) OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Back") }
                        OutlinedButton(
                            onClick = {
                                if (nativeDashboard) {
                                    workspaceMode = "dashboard"
                                    dashboardMessage = if (session == null) "" else "Working scan kept until you start another scan."
                                } else onCancel()
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text(if (nativeDashboard) "Dashboard" else "Cancel") }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    if (technicalOpen) "Hide technical details" else "Technical details",
                    modifier = Modifier.clickable { technicalOpen = !technicalOpen }.padding(vertical = 6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge
                )
                if (technicalOpen) {
                    Text("Method: ${As100PaperFormTranscribeMethod.ID}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    Text("Maturity: ${PaperBridgeContractMetadata.MATURITY}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    Text("Connectivity: ${PaperBridgeContractMetadata.CONNECTIVITY}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    session?.let { extracted ->
                        Text("Registration: ${extracted.rectification.registrationMode}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        Text("Markers: ${extracted.rectification.anchors.joinToString { "${it.corner}=${"%.2f".format(it.score)}" }}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        Text("Registration type: ${extracted.template.registration.type.name.lowercase()}${if (extracted.template.registration.type != PaperRegistrationType.BULLSEYE4 && extracted.template.registration.schemaKey.isNotBlank()) " · ${extracted.template.registration.schemaKey}" else ""}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        if (extracted.template.registration.type == PaperRegistrationType.APRILTAG8) {
                            Text("AprilTags: ${extracted.rectification.detectedMarkerCount}/${extracted.rectification.expectedMarkerCount} · points ${extracted.rectification.correspondenceCount} · RMS ${extracted.rectification.reprojectionRmsPx?.let { String.format("%.2f", it) } ?: "–"} px · max ${extracted.rectification.reprojectionMaxPx?.let { String.format("%.2f", it) } ?: "–"} px · ${extracted.rectification.registrationQuality}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        }
                        Text("Page white: ${"%.1f".format(extracted.pageWhiteLuma)}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    }
                    if (committedFields.isNotEmpty()) Text("Committed fields: ${committedFields.size}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (scannerActive) {
            Dialog(
                onDismissRequest = { /* ML Kit owns cancellation while active. */ },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false,
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {}
            }
        }
        }
    }
}

@Composable
private fun PaperBridgeDashboard(
    template: PaperTemplate?,
    templateError: String?,
    templates: List<PaperTemplate>,
    recentActivity: List<PaperBridgeRecentActivity>,
    hasWorkingScan: Boolean,
    workingUnresolved: Int,
    autoAcceptOmr: Boolean,
    autoAcceptOcr: Boolean,
    returnSourceImage: Boolean,
    returnRectifiedImage: Boolean,
    templateEditorOpen: Boolean,
    templateDraft: String,
    message: String,
    onCameraScan: () -> Unit,
    onImageScan: () -> Unit,
    onDesignForm: (PaperTemplate?) -> Unit,
    onResumeWorkingScan: () -> Unit,
    onSelectTemplate: (PaperTemplate) -> Unit,
    onImportTemplate: () -> Unit,
    onRemoveTemplate: (PaperTemplate) -> Unit,
    onDuplicateTemplate: (PaperTemplate) -> Unit,
    onRenameTemplate: (PaperTemplate, String) -> Unit,
    onTemplateEditorOpen: (Boolean) -> Unit,
    onTemplateDraft: (String) -> Unit,
    onSaveTemplateDraft: () -> Unit,
    onUseDemoTemplate: () -> Unit,
    onCopyTemplate: () -> Unit,
    onAutoAcceptOmr: (Boolean) -> Unit,
    onAutoAcceptOcr: (Boolean) -> Unit,
    onReturnSourceImage: (Boolean) -> Unit,
    onReturnRectifiedImage: (Boolean) -> Unit,
    onClearRecent: () -> Unit,
    onClose: () -> Unit
) {
    val shape = RoundedCornerShape(28.dp)
    val primary = MaterialTheme.colorScheme.primary
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    var formSetupOpen by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var helpOpen by rememberSaveable { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<PaperTemplate?>(null) }
    var renameText by rememberSaveable { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<PaperTemplate?>(null) }

    val omrCount = template?.fields?.count {
        it.type == PaperFieldType.OMR_SINGLE || it.type == PaperFieldType.OMR_MULTIPLE
    } ?: 0
    val textCount = template?.fields?.count {
        it.type == PaperFieldType.OCR_TEXT || it.type == PaperFieldType.OCR_INTEGER || it.type == PaperFieldType.OCR_DECIMAL
    } ?: 0
    val barcodeCount = template?.fields?.count { it.type == PaperFieldType.BARCODE } ?: 0
    val imageCount = template?.fields?.count { it.type == PaperFieldType.IMAGE } ?: 0
    val bundledDemo = template?.let(PaperBridgeWorkspace::isBundled) == true

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Paper Bridge", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Schema Library", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Choose a named schema, then scan, edit, duplicate or delete it. User schemas are stored as ordinary Paper Bridge files.",
                        modifier = Modifier.padding(top = 3.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "Close",
                    modifier = Modifier.clickable(onClick = onClose).padding(10.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(12.dp))
            templates.forEach { item ->
                val selected = template?.let { PaperBridgeWorkspace.templateKey(it) == PaperBridgeWorkspace.templateKey(item) } == true
                PaperTemplateDashboardRow(
                    template = item,
                    selected = selected,
                    bundled = PaperBridgeWorkspace.isBundled(item),
                    onSelect = { onSelectTemplate(item) },
                    onEdit = { onDesignForm(item) },
                    onRename = {
                        renameTarget = item
                        renameText = item.title
                    },
                    onDuplicate = { onDuplicateTemplate(item) },
                    onRemove = { deleteTarget = item }
                )
                Spacer(Modifier.height(7.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onImportTemplate, modifier = Modifier.weight(1f)) { Text("Import schema") }
                Button(onClick = { onDesignForm(null) }, modifier = Modifier.weight(1f)) { Text("New schema") }
            }
            Spacer(Modifier.height(18.dp))
            Text("Selected schema workspace", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.surface
                            )
                        ),
                        shape
                    )
                    .padding(22.dp)
            ) {
                Canvas(Modifier.align(Alignment.TopEnd).size(108.dp)) {
                    val ink = onPrimaryContainer.copy(alpha = 0.20f)
                    val strong = primary.copy(alpha = 0.46f)
                    val left = size.width * 0.16f
                    val top = size.height * 0.08f
                    val right = size.width * 0.84f
                    val bottom = size.height * 0.92f
                    drawRect(
                        color = ink,
                        topLeft = Offset(left, top),
                        size = Size(right - left, bottom - top),
                        style = Stroke(width = 3f)
                    )
                    listOf(
                        Offset(left, top), Offset(right, top), Offset(right, bottom), Offset(left, bottom)
                    ).forEach { point -> drawCircle(strong, radius = 5.5f, center = point) }
                    repeat(4) { index ->
                        val y = top + (index + 1) * (bottom - top) / 6f
                        drawLine(ink, Offset(left + 14f, y), Offset(right - 13f, y), strokeWidth = 3f)
                    }
                    repeat(3) { index ->
                        val y = bottom - 30f - index * 17f
                        drawCircle(ink, radius = 4.2f, center = Offset(left + 18f, y))
                        drawCircle(ink, radius = 4.2f, center = Offset(left + 34f, y))
                        drawCircle(ink, radius = 4.2f, center = Offset(left + 50f, y))
                    }
                }

                Column(Modifier.fillMaxWidth().padding(end = 72.dp)) {
                    Text("Paper Bridge", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Turn a completed paper form into ODK-ready data.",
                        modifier = Modifier.padding(top = 4.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(Modifier.fillMaxWidth().align(Alignment.BottomStart).padding(top = 124.dp)) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)
                    ) {
                        Column(Modifier.padding(15.dp)) {
                            Text("SCHEMA SELECTED", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                            Text(
                                template?.title ?: "No usable form selected",
                                modifier = Modifier.padding(top = 3.dp),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                when {
                                    template == null -> "Choose or import a schema before scanning."
                                    bundledDemo -> "Tutorial seed selected · open it and work through AUTO DETECT, linkage, Commit, then the empty/data tests."
                                    else -> "${template.fields.size} fields ready to read."
                                },
                                modifier = Modifier.padding(top = 3.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (template == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                if (formSetupOpen) "Hide schema details" else "More schema tools",
                                modifier = Modifier.clickable { formSetupOpen = !formSetupOpen }.padding(top = 8.dp, bottom = 2.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (bundledDemo) {
                        Button(onClick = { template?.let(onDesignForm) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Open tutorial schema")
                        }
                        Text(
                            "The bundled example is a teaching seed, not a scan-ready schema. Commit your worked copy before operational scanning.",
                            modifier = Modifier.padding(top = 7.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Button(onClick = onCameraScan, enabled = template != null, modifier = Modifier.fillMaxWidth()) {
                            Text("Scan page with ML Kit")
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = onImageScan, enabled = template != null, modifier = Modifier.fillMaxWidth()) {
                            Text("Choose page in ML Kit scanner")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { onDesignForm(template) }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            when {
                                bundledDemo -> "Explore built-in example in Designer"
                                template != null -> "Edit this form in Designer"
                                else -> "Design a form"
                            }
                        )
                    }
                    Text(
                        "After scanning, Paper Bridge reads the page and only asks you to check uncertain answers. Press Commit when the review is complete.",
                        modifier = Modifier.padding(top = 10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            PaperDashboardFlowStrip()

            Spacer(Modifier.height(18.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.30f)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Tutorial schema", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Open the built-in tutorial from the Schema Library. It starts unresolved: view the colour template, press AUTO, inspect the guesses, fix highlighted queries, Commit, then test a blank black production form and a filled form.",
                        modifier = Modifier.padding(top = 5.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (hasWorkingScan) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onResumeWorkingScan),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
                ) {
                    Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Continue current page", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                if (workingUnresolved > 0) "$workingUnresolved answer${if (workingUnresolved == 1) "" else "s"} still need checking."
                                else "The page is ready to continue.",
                                modifier = Modifier.padding(top = 2.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text("Resume", style = MaterialTheme.typography.labelLarge, color = primary, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (message.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                ) {
                    Text(message, modifier = Modifier.padding(13.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }

            if (template == null) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("This form cannot be used", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                        Text(templateError ?: "The form definition could not be parsed.", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "Open form setup below and choose another form or import a valid MethodMesh paper form definition.",
                            modifier = Modifier.clickable { formSetupOpen = true }.padding(top = 7.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (formSetupOpen) {
                Spacer(Modifier.height(20.dp))
                DashboardSectionTitle(
                    "Schema library",
                    "Named Paper Bridge schemas live in the module Files area. Open, duplicate, edit, import or delete them here."
                )
                if (templates.isEmpty()) {
                    Text("No schemas are available.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    templates.forEach { item ->
                        val selected = template?.let { PaperBridgeWorkspace.templateKey(it) == PaperBridgeWorkspace.templateKey(item) } == true
                        PaperTemplateDashboardRow(
                            template = item,
                            selected = selected,
                            bundled = PaperBridgeWorkspace.isBundled(item),
                            onSelect = { onSelectTemplate(item) },
                            onEdit = { onDesignForm(item) },
                            onRename = {
                                renameTarget = item
                                renameText = item.title
                            },
                            onDuplicate = { onDuplicateTemplate(item) },
                            onRemove = { deleteTarget = item }
                        )
                        Spacer(Modifier.height(7.dp))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onImportTemplate, modifier = Modifier.weight(1f)) { Text("Import schema") }
                    OutlinedButton(onClick = { onDesignForm(null) }, modifier = Modifier.weight(1f)) { Text("New schema") }
                }
                Text(
                    "Open tutorial example",
                    modifier = Modifier.clickable(onClick = onUseDemoTemplate).padding(top = 8.dp, bottom = 2.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "The built-in example includes its colour-authoring template PNG and matching standard survey/choices workbook automatically. Import form accepts Paper Bridge JSON, fixed-profile YAML, or a portable .paperbridge.zip bundle. Normally an ODK launch supplies the correct form automatically, so you do not need this screen during routine data collection.",
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(8.dp))
                Text(
                    if (templateEditorOpen) "Hide developer editor" else "Developer options · edit form definition",
                    modifier = Modifier.clickable { onTemplateEditorOpen(!templateEditorOpen) }.padding(vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = primary,
                    fontWeight = FontWeight.SemiBold
                )
                if (templateEditorOpen) {
                    OutlinedTextField(
                        value = templateDraft,
                        onValueChange = onTemplateDraft,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Paper-form definition JSON") },
                        minLines = 8,
                        maxLines = 16,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onSaveTemplateDraft, modifier = Modifier.weight(1f)) { Text("Validate & save") }
                        OutlinedButton(onClick = onCopyTemplate, modifier = Modifier.weight(1f)) { Text("Copy JSON") }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { helpOpen = !helpOpen },
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
            ) {
                Column(Modifier.padding(15.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("How it works", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("Four steps from paper to structured data or the calling ODK form.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(if (helpOpen) "Hide" else "Show", style = MaterialTheme.typography.labelLarge, color = primary, fontWeight = FontWeight.Bold)
                    }
                    if (helpOpen) {
                        Spacer(Modifier.height(10.dp))
                        SimpleHelpStep("1", "Choose the form", "The selected template tells Paper Bridge where each answer is printed and which survey field or choice key it belongs to.")
                        SimpleHelpStep("2", "Scan the completed page", "Use the ML Kit document scanner (camera or gallery). Let it crop/straighten the whole sheet and keep the perimeter registration markers visible; current schemas can tolerate missing AprilTags when the remaining tags are well distributed.")
                        SimpleHelpStep("3", "Check anything uncertain", "Clear marks can pass automatically; ambiguous marks and text stay visible until you confirm them.")
                        SimpleHelpStep("4", "Commit", "Commit freezes the reviewed result and always returns the named values, source scan, registered analysis page and audit metadata. If ODK launched Paper Bridge, you land back in that form.")
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { settingsOpen = !settingsOpen },
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
            ) {
                Column(Modifier.padding(15.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Settings & advanced", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                buildString {
                                    append(if (autoAcceptOmr) "Clear marks auto" else "Review all marks")
                                    append(" · ")
                                    append(if (autoAcceptOcr) "Validated text auto" else "Review text")
                                    append(" · ")
                                    append("source + registered images")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(if (settingsOpen) "Hide" else "Open", style = MaterialTheme.typography.labelLarge, color = primary, fontWeight = FontWeight.Bold)
                    }
                    if (settingsOpen) {
                        Spacer(Modifier.height(12.dp))
                        Text("READING & REVIEW", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                        DashboardToggleRow(
                            title = "Auto-accept clear marks",
                            subtitle = "Only marks that pass the form's threshold and separation rules are accepted automatically.",
                            checked = autoAcceptOmr,
                            onCheckedChange = onAutoAcceptOmr
                        )
                        DashboardToggleRow(
                            title = "Auto-accept constrained numeric OCR",
                            subtitle = "Off by default. Integer/decimal OCR may auto-accept only when constraints pass; free text always requires human confirmation.",
                            checked = autoAcceptOcr,
                            onCheckedChange = onAutoAcceptOcr
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("RETURN WITH RESULT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                        Text(
                            "Every Commit includes both the original/source scan and the registered canonical page used for ROI reading. SHA-256 hashes, dimensions, acquisition mode and registration mode are returned as metadata.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )

                        if (template != null) {
                            Spacer(Modifier.height(10.dp))
                            Text("FORM DETAILS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                DashboardMetric("Fields", template.fields.size.toString(), Modifier.weight(1f))
                                DashboardMetric("Marks", omrCount.toString(), Modifier.weight(1f))
                                DashboardMetric("Text", textCount.toString(), Modifier.weight(1f))
                                if (barcodeCount > 0) DashboardMetric("Codes", barcodeCount.toString(), Modifier.weight(1f))
                            }
                            Text(
                                "${template.templateId} · v${template.version}",
                                modifier = Modifier.padding(top = 8.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            if (recentActivity.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                DashboardSectionTitle("Recent scans", "Non-identifying activity only; answers are not stored here.")
                recentActivity.take(3).forEachIndexed { index, item ->
                    RecentPaperActivityRow(item)
                    if (index != minOf(2, recentActivity.lastIndex)) Spacer(Modifier.height(6.dp))
                }
                Text(
                    "Clear recent activity",
                    modifier = Modifier.clickable(onClick = onClearRecent).padding(vertical = 9.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = primary
                )
            }

            Spacer(Modifier.height(14.dp))
            Text(
                "ODK note: when Collect launches Paper Bridge, the selected form and return fields are supplied automatically and this dashboard is skipped.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Close Paper Bridge") }
        }
    }


    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename schema") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Schema name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = renameText.trim()
                        if (name.isNotBlank()) {
                            onRenameTemplate(target, name)
                            renameTarget = null
                        }
                    },
                    enabled = renameText.isNotBlank()
                ) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete schema?") },
            text = { Text("Delete ‘${target.title}’ from Paper Bridge Files? This does not delete any completed ODK records.") },
            confirmButton = {
                TextButton(onClick = {
                    onRemoveTemplate(target)
                    deleteTarget = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }

}

@Composable
private fun SimpleHelpStep(number: String, title: String, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer) {
            Text(
                number,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(text, modifier = Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

}

@Composable
private fun PaperDashboardFlowStrip() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DashboardFlowStep("1 Form", Modifier.weight(1f))
        DashboardFlowStep("2 Scan", Modifier.weight(1f))
        DashboardFlowStep("3 Review", Modifier.weight(1f))
        DashboardFlowStep("4 Return", Modifier.weight(1f))
    }
}

@Composable
private fun DashboardFlowStep(label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DashboardSectionTitle(title: String, subtitle: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text(
        subtitle,
        modifier = Modifier.padding(top = 3.dp, bottom = 10.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun DashboardMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PaperTemplateDashboardRow(
    template: PaperTemplate,
    selected: Boolean,
    bundled: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onRemove: () -> Unit
) {
    val omr = template.fields.count { it.type == PaperFieldType.OMR_SINGLE || it.type == PaperFieldType.OMR_MULTIPLE }
    val ocr = template.fields.count { it.type == PaperFieldType.OCR_TEXT || it.type == PaperFieldType.OCR_INTEGER || it.type == PaperFieldType.OCR_DECIMAL }
    val media = template.fields.count { it.type == PaperFieldType.BARCODE || it.type == PaperFieldType.IMAGE }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(template.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    if (bundled) {
                        Text(
                            "  BUILT-IN EXAMPLE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (selected) {
                        Text(
                            "  ACTIVE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    if (bundled) {
                        "Tutorial seed · needs setup · open and press AUTO DETECT"
                    } else {
                        "${template.templateId} · v${template.version} · ${template.fields.size} fields · $omr marks · $ocr text · $media media/code"
                    },
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (bundled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (bundled) FontWeight.SemiBold else FontWeight.Normal
                )
                Row(Modifier.padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        if (bundled) "Open tutorial" else "Edit",
                        modifier = Modifier.clickable(onClick = onEdit).padding(vertical = 2.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (!bundled) {
                        Text(
                            "Rename",
                            modifier = Modifier.clickable(onClick = onRename).padding(vertical = 2.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (!bundled) {
                        Text(
                            "Duplicate",
                            modifier = Modifier.clickable(onClick = onDuplicate).padding(vertical = 2.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (!bundled) {
                        Text(
                            "Delete",
                            modifier = Modifier.clickable(onClick = onRemove).padding(vertical = 2.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                modifier = Modifier.padding(top = 2.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun RecentPaperActivityRow(item: PaperBridgeRecentActivity) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
    ) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.templateTitle.ifBlank { item.templateId }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    item.scanTimeIso.replace('T', ' ').replace("Z", "").take(16),
                    modifier = Modifier.padding(top = 1.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${item.fieldCount} fields", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(
                    "${item.autoAcceptedCount} auto · ${item.reviewedCount} reviewed",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ToggleSettingRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun RegisteredPaperPreview(
    bitmap: android.graphics.Bitmap,
    template: PaperTemplate,
    modifier: Modifier = Modifier
) {
    val line = MaterialTheme.colorScheme.primary
    val labelBackground = MaterialTheme.colorScheme.inverseSurface
    val labelForeground = MaterialTheme.colorScheme.inverseOnSurface
    Canvas(modifier.background(MaterialTheme.colorScheme.surface)) {
        val scale = min(
            size.width / bitmap.width.toFloat().coerceAtLeast(1f),
            size.height / bitmap.height.toFloat().coerceAtLeast(1f)
        )
        val drawWidth = bitmap.width * scale
        val drawHeight = bitmap.height * scale
        val origin = Offset((size.width - drawWidth) / 2f, (size.height - drawHeight) / 2f)
        drawImage(
            image = bitmap.asImageBitmap(),
            dstOffset = IntOffset(origin.x.roundToInt(), origin.y.roundToInt()),
            dstSize = IntSize(drawWidth.roundToInt(), drawHeight.roundToInt())
        )

        fun drawMappedRegion(roi: NormalisedRoi, label: String) {
            val left = origin.x + roi.left * drawWidth
            val top = origin.y + roi.top * drawHeight
            val right = origin.x + roi.right * drawWidth
            val bottom = origin.y + roi.bottom * drawHeight
            drawRect(
                color = line.copy(alpha = 0.78f),
                topLeft = Offset(left, top),
                size = Size(right - left, bottom - top),
                style = Stroke(width = 2.5f)
            )
            val safe = label.take(42)
            val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = labelForeground.toArgb()
                textSize = 19f
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
            }
            val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = labelBackground.copy(alpha = 0.86f).toArgb()
                style = android.graphics.Paint.Style.FILL
            }
            val textWidth = textPaint.measureText(safe)
            val labelTop = (top - 24f).coerceAtLeast(origin.y)
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawRect(left, labelTop, left + textWidth + 10f, labelTop + 24f, bgPaint)
                canvas.nativeCanvas.drawText(safe, left + 5f, labelTop + 18f, textPaint)
            }
        }

        template.fields.forEach { field ->
            if (field.type == PaperFieldType.OMR_SINGLE || field.type == PaperFieldType.OMR_MULTIPLE) {
                field.options.forEach { option -> drawMappedRegion(option.roi, "${field.name}=${option.value}") }
            } else {
                field.roi?.let { drawMappedRegion(it, field.name) }
            }
        }
    }
}

@Composable
private fun SummaryPill(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PaperFieldCard(
    result: PaperFieldResult,
    pageBitmap: android.graphics.Bitmap?,
    onManualValue: (String) -> Unit,
    onNaOverride: () -> Unit,
    onCopy: (String) -> Unit
) {
    var confirmNa by rememberSaveable(result.spec.name, result.candidate) { mutableStateOf(false) }
    var forceEdit by rememberSaveable(result.spec.name, result.finalValue) { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = if (result.resolved) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
        else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.36f)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(result.spec.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        when {
                            result.naOverride -> "NA override"
                            result.relevant == false && result.finalValue.isBlank() -> "Not relevant"
                            result.logicViolation -> "Logic/constraint review"
                            result.logicUnknown -> "Logic check incomplete"
                            result.autoAccepted -> "Accepted automatically"
                            result.reviewed -> "Confirmed"
                            else -> "Review required"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (result.resolved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
                result.finalValue.takeIf(String::isNotBlank)?.let { value ->
                    if (result.naOverride) {
                        Text("NA", modifier = Modifier.padding(6.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    } else if (result.spec.type == PaperFieldType.IMAGE) {
                        Text("IMAGE", modifier = Modifier.padding(6.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    } else {
                        Text(value, modifier = Modifier.clickable { onCopy(value) }.padding(6.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(5.dp))
            Text(result.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (result.logicMessage.isNotBlank() && result.logicMessage != result.reason) {
                Spacer(Modifier.height(3.dp))
                Text(
                    result.logicMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (result.logicViolation) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
                )
            }
            val editing = !result.resolved || forceEdit
            if (editing) {
                Spacer(Modifier.height(12.dp))
                when (result.spec.type) {
                    PaperFieldType.OMR_SINGLE -> SingleMarkReview(result, onManualValue)
                    PaperFieldType.OMR_MULTIPLE -> MultipleMarkReview(result, onManualValue)
                    PaperFieldType.OCR_TEXT, PaperFieldType.OCR_INTEGER, PaperFieldType.OCR_DECIMAL, PaperFieldType.BARCODE -> {
                        val roi = result.spec.roi
                        if (pageBitmap != null && roi != null) {
                            PaperRoiReviewPreview(pageBitmap, roi)
                            Spacer(Modifier.height(8.dp))
                        }
                        TextReview(result, onManualValue)
                    }
                    PaperFieldType.IMAGE -> {
                        val roi = result.spec.roi
                        if (pageBitmap != null && roi != null) {
                            PaperRoiReviewPreview(pageBitmap, roi)
                            Spacer(Modifier.height(8.dp))
                        }
                        Text("This region is returned as an image attachment; no OCR or barcode decoding is applied.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (!result.naOverride) {
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { confirmNa = true }) { Text("Set NA") }
                }
                if (forceEdit && result.resolved) {
                    Spacer(Modifier.height(6.dp))
                    TextButton(modifier = Modifier.fillMaxWidth(), onClick = { forceEdit = false }) { Text("Cancel edit") }
                }
            } else if (result.naOverride) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = result.candidate.isNotBlank(),
                    onClick = { onManualValue(result.candidate) }
                ) { Text("Restore detected value") }
            } else if (result.relevant != false) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (result.spec.type != PaperFieldType.IMAGE) {
                        OutlinedButton(modifier = Modifier.weight(1f), onClick = { forceEdit = true }) { Text("Edit") }
                    }
                    OutlinedButton(modifier = Modifier.weight(1f), onClick = { confirmNa = true }) { Text("Set NA") }
                }
            }
        }
    }
    if (confirmNa) {
        AlertDialog(
            onDismissRequest = { confirmNa = false },
            title = { Text("Set ${result.spec.label} to NA?") },
            text = { Text("Use this only when the paper value cannot be reconciled. It will set the Paper Bridge value to 'na', bypass the normal required/constraint check for this field, and record a confirmed NA override in the audit trail.") },
            confirmButton = {
                TextButton(onClick = { confirmNa = false; onNaOverride() }) { Text("Set NA") }
            },
            dismissButton = { TextButton(onClick = { confirmNa = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SingleMarkReview(result: PaperFieldResult, onManualValue: (String) -> Unit) {
    var selected by rememberSaveable(result.spec.name, result.candidate) { mutableStateOf(result.candidate) }
    Column {
        result.spec.options.forEach { option ->
            FilterChip(
                selected = selected == option.value,
                onClick = { selected = option.value },
                label = { Text("${option.label}${result.scores[option.value]?.let { " · ${"%.3f".format(it)}" }.orEmpty()}") }
            )
        }
        if (!result.effectiveRequired) {
            FilterChip(selected = selected.isBlank(), onClick = { selected = "" }, label = { Text("Blank") })
        }
        Spacer(Modifier.height(6.dp))
        Button(modifier = Modifier.fillMaxWidth(), enabled = selected.isNotBlank() || !result.effectiveRequired, onClick = { onManualValue(selected) }) { Text("Confirm") }
    }
}

@Composable
private fun MultipleMarkReview(result: PaperFieldResult, onManualValue: (String) -> Unit) {
    var selectedRaw by rememberSaveable(result.spec.name, result.candidate) { mutableStateOf(result.candidate) }
    val selected = selectedRaw.split(result.spec.separator).filter(String::isNotBlank).toSet()
    Column {
        result.spec.options.forEach { option ->
            FilterChip(
                selected = option.value in selected,
                onClick = {
                    val next = selected.toMutableSet().apply { if (!add(option.value)) remove(option.value) }
                    selectedRaw = result.spec.options.map { it.value }.filter { it in next }.joinToString(result.spec.separator)
                },
                label = { Text("${option.label}${result.scores[option.value]?.let { " · ${"%.3f".format(it)}" }.orEmpty()}") }
            )
        }
        Spacer(Modifier.height(6.dp))
        Button(modifier = Modifier.fillMaxWidth(), enabled = selected.isNotEmpty() || !result.effectiveRequired, onClick = { onManualValue(selectedRaw) }) { Text("Confirm selection") }
    }
}

@Composable
private fun PaperRoiReviewPreview(bitmap: android.graphics.Bitmap, roi: NormalisedRoi) {
    val crop = remember(bitmap, roi) { PaperImageEngine.cropExpanded(bitmap, roi) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Image(
            bitmap = crop.asImageBitmap(),
            contentDescription = "Paper response region",
            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp, max = 180.dp).padding(6.dp),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun TextReview(result: PaperFieldResult, onManualValue: (String) -> Unit) {
    var value by rememberSaveable(result.spec.name, result.candidate) { mutableStateOf(result.candidate) }
    OutlinedTextField(
        value = value,
        onValueChange = { value = it },
        label = { Text("Verified value") },
        supportingText = { if (result.rawText.isNotBlank()) Text("OCR/code candidate: ${result.rawText}") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = result.spec.type != PaperFieldType.OCR_TEXT
    )
    Spacer(Modifier.height(6.dp))
    Button(modifier = Modifier.fillMaxWidth(), enabled = value.isNotBlank() || !result.effectiveRequired, onClick = { onManualValue(value) }) { Text("Confirm") }
}


@Composable
private fun PaperCommittedAttachment(context: Context, label: String, uriText: String) {
    val uri = remember(uriText) { Uri.parse(uriText) }
    val mime = remember(uriText) { context.contentResolver.getType(uri).orEmpty().ifBlank { "image/*" } }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Image attachment", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, mime)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    })
                }
            }) { Text("Open") }
        }
    }
}

@Composable
private fun TappablePaperValue(context: Context, label: String, value: String) {
    Column(Modifier.fillMaxWidth().clickable(enabled = value.isNotBlank()) { copyText(context, label, value) }) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.ifBlank { "—" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

private fun copyText(context: Context, label: String, value: String) {
    if (value.isBlank()) return
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, value))
    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}


private fun Context.findPaperBridgeActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        val base = current.baseContext
        if (base === current) break
        current = base
    }
    return current as? Activity
}
