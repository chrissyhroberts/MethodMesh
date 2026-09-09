package com.example.methodmesh.modules.paperbridge

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.methodmesh.MainActivity
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.protocols.PresetResultAction
import com.example.methodmesh.transport.OutputExportRepository
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.ReturnMode
import com.example.methodmesh.transport.workflow.ui.CapabilityCompletionMode
import com.example.methodmesh.transport.workflow.ui.CapabilityPresentationMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Instant

object PaperFormTranscribeCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100PaperFormTranscribeMethod.ID
    override val title = "Paper Bridge"
    override val description = "Scan, verify and transcribe an anchored paper questionnaire."

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
            mutableStateOf(initial(PaperBridgeInputs.TEMPLATE_JSON, dashboardTemplateFallback))
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
        var returnSourceImage by rememberSaveable(context.action.canonicalId) {
            mutableStateOf(initial(PaperBridgeInputs.RETURN_SOURCE_IMAGE, "true").equals("true", true))
        }
        var returnRectifiedImage by rememberSaveable(context.action.canonicalId) {
            mutableStateOf(initial(PaperBridgeInputs.RETURN_RECTIFIED_IMAGE, "true").equals("true", true))
        }
        var sourceUriText by rememberSaveable(context.action.canonicalId) {
            mutableStateOf(initial(PaperBridgeInputs.SOURCE_IMAGE_URI))
        }
        var pendingCaptureUriText by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
        var manualQuarterTurns by rememberSaveable(context.action.canonicalId) { mutableStateOf(0) }
        var manualOverridesJson by rememberSaveable(context.action.canonicalId) { mutableStateOf("{}") }
        var session by remember { mutableStateOf<PaperExtractionSession?>(null) }
        var results by remember { mutableStateOf<List<PaperFieldResult>>(emptyList()) }
        var processing by remember { mutableStateOf(false) }
        var status by rememberSaveable(context.action.canonicalId) { mutableStateOf("Ready to scan a paper questionnaire.") }
        var sourceMenu by rememberSaveable { mutableStateOf(false) }
        var technicalOpen by rememberSaveable { mutableStateOf(false) }
        var committedResult by remember { mutableStateOf<ExecutionResult?>(null) }
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

        fun saveOverride(fieldName: String, value: String) {
            val objectValue = runCatching { JSONObject(manualOverridesJson) }.getOrDefault(JSONObject())
            objectValue.put(fieldName, value)
            manualOverridesJson = objectValue.toString()
            results = results.map { result ->
                if (result.spec.name == fieldName) PaperExtractionEngine.withManualValue(result, value) else result
            }
        }

        fun runExtraction(stableUri: Uri) {
            val parsed = template
            if (parsed == null) {
                status = templateError ?: "Paper template is invalid."
                return
            }
            processing = true
            committedResult = null
            session = null
            results = emptyList()
            status = "Finding anchors and rectifying page…"
            scope.launch {
                val outcome = runCatching {
                    PaperExtractionEngine.extract(
                        context = appContext,
                        sourceUri = stableUri,
                        template = parsed,
                        settings = PaperExtractionSettings(autoAcceptOmr, autoAcceptOcr),
                        manualQuarterTurns = manualQuarterTurns,
                        scanTimeIso = Instant.now().toString()
                    )
                }
                processing = false
                outcome.onFailure { error ->
                    status = error.message ?: "Paper extraction failed."
                    session = null
                    results = emptyList()
                }.onSuccess { extracted ->
                    session = extracted
                    val manual = overrides()
                    results = extracted.results.map { result ->
                        manual[result.spec.name]?.let { PaperExtractionEngine.withManualValue(result, it) } ?: result
                    }
                    val unresolved = results.count { !it.resolved }
                    status = if (unresolved == 0) "All ${results.size} fields are ready to Commit." else "$unresolved field${if (unresolved == 1) "" else "s"} require review."
                }
            }
        }

        val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            val pending = pendingCaptureUriText.takeIf(String::isNotBlank)?.let(Uri::parse)
            if (ok && pending != null) {
                sourceUriText = pending.toString()
                manualOverridesJson = "{}"
                manualQuarterTurns = 0
                runExtraction(pending)
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
                            manualQuarterTurns = 0
                            workspaceMode = "scan"
                            runExtraction(stable)
                        }
                }
            }
        }
        val pickTemplate = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                scope.launch {
                    val outcome = runCatching {
                        withContext(Dispatchers.IO) {
                            appContext.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                                ?: error("Could not read template file.")
                        }
                    }.mapCatching { raw -> PaperBridgeWorkspace.saveAndActivateTemplate(appContext, raw) }
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
            workspaceMode = "scan"
            if (source == "file_picker") {
                pickImage.launch("image/*")
            } else {
                val uri = PaperBridgeFiles.newCaptureUri(appContext)
                pendingCaptureUriText = uri.toString()
                takePicture.launch(uri)
            }
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
                    runExtraction(stable)
                }
            } else if (context.startsImmediately && sourceUriText.isBlank()) {
                capture()
            }
        }

        fun rotateAndReprocess() {
            val uri = sourceUriText.takeIf(String::isNotBlank)?.let(Uri::parse) ?: return
            manualQuarterTurns = (manualQuarterTurns + 1) % 4
            manualOverridesJson = "{}"
            runExtraction(uri)
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
                        PaperBridgeFields.SOURCE_IMAGE to if (returnSourceImage) extracted.sourceUri.toString() else "",
                        PaperBridgeFields.RECTIFIED_IMAGE to if (returnRectifiedImage) extracted.rectifiedUri.toString() else "",
                        PaperBridgeFields.TEMPLATE_SHA256 to PaperBridgeFiles.sha256Text(extracted.template.rawJson),
                        PaperBridgeFields.SOURCE_SHA256 to sourceHash,
                        PaperBridgeFields.RECTIFIED_SHA256 to rectifiedHash,
                        PaperBridgeFields.EXTRACTION_AUDIT_JSON to PaperExtractionEngine.auditJson(extracted, results),
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
                PaperBridgeWorkspace.recordCommit(
                    context = appContext,
                    template = extracted.template,
                    scanTimeIso = extracted.scanTimeIso,
                    fieldCount = results.size,
                    autoAcceptedCount = results.count { it.autoAccepted },
                    reviewedCount = results.count { it.reviewed }
                )
                workspaceRevision += 1
                status = "Committed."
                if (automaticReturn) onConfirmed(execution)
            }
        }

        val unresolved = results.count { !it.resolved }
        val autoAccepted = results.count { it.autoAccepted }
        val reviewed = results.count { it.reviewed }
        val committedFields = remember(committedResult?.request?.id?.value) {
            committedResult?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty()
        }
        val fullJson = remember(committedResult?.request?.id?.value) {
            committedResult?.let {
                OutputFormatter.format(it, ReturnMode.Json, includeProvenance = true, payloadMode = OutputFormatter.PayloadMode.FULL)
            }.orEmpty()
        }

        if (nativeDashboard && workspaceMode == "designer" && committedResult == null) {
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
        } else if (nativeDashboard && workspaceMode == "dashboard" && committedResult == null) {
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
                    pickTemplate.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                },
                onRemoveTemplate = { selected ->
                    PaperBridgeWorkspace.removeTemplate(appContext, selected)
                    val active = PaperBridgeWorkspace.activeTemplateJson(appContext)
                    templateJson = active
                    templateDraft = active
                    workspaceRevision += 1
                    dashboardMessage = "Template removed."
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
                onReturnSourceImage = { returnSourceImage = it },
                onReturnRectifiedImage = { returnRectifiedImage = it },
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

                if (committedResult != null && !automaticReturn) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Text("Committed transcription", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(8.dp))
                            results.forEach { result ->
                                TappablePaperValue(appContext, result.spec.label, result.finalValue)
                                Spacer(Modifier.height(8.dp))
                            }
                            Text(
                                "${results.size} fields · $autoAccepted automatic · $reviewed reviewed",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(modifier = Modifier.fillMaxWidth(), onClick = {
                        val resultValue = requireNotNull(committedResult)
                        if (nativeDashboard) {
                            committedResult = null
                            session = null
                            results = emptyList()
                            sourceUriText = ""
                            manualOverridesJson = "{}"
                            manualQuarterTurns = 0
                            workspaceMode = "dashboard"
                            dashboardMessage = "Committed transcription added to recent activity."
                        } else {
                            if (context.isNativePresetRun && presetResultAction == PresetResultAction.SAVE) {
                                runCatching {
                                    OutputExportRepository.saveToDownloads(
                                        context = appContext,
                                        label = "paper_transcription",
                                        text = results.joinToString("\n") { "${it.spec.label}: ${it.finalValue}" },
                                        mediaUris = listOfNotNull(
                                            committedFields[PaperBridgeFields.SOURCE_IMAGE]?.toString()?.takeIf(String::isNotBlank),
                                            committedFields[PaperBridgeFields.RECTIFIED_IMAGE]?.toString()?.takeIf(String::isNotBlank)
                                        ),
                                        jsonText = ""
                                    )
                                }.onFailure { status = "Save failed: ${it.message ?: "storage error"}" }
                            }
                            if (context.isNativePresetRun && !finishToLauncher) {
                                appContext.startActivity(Intent(appContext, MainActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                                })
                            } else {
                                onConfirmed(resultValue)
                            }
                        }
                    }) {
                        Text(when {
                            nativeDashboard -> "Back to Paper Bridge"
                            context.isNativePresetRun && presetResultAction == PresetResultAction.SAVE -> "Save and finish"
                            finishToLauncher -> "Done"
                            else -> "Done"
                        })
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = {
                        committedResult = null
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
                        Image(
                            bitmap = extracted.rectifiedBitmap.asImageBitmap(),
                            contentDescription = "Rectified paper questionnaire",
                            modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                            contentScale = ContentScale.Fit
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
                                onManualValue = { value -> saveOverride(result.spec.name, value) },
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
                                Text("Keep all four anchor symbols visible. Paper Bridge will not infer a page when anchor geometry is uncertain.", style = MaterialTheme.typography.bodySmall)
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
                    if (!nativeDashboard && (context.settingShouldBeShown(PaperBridgeInputs.RETURN_SOURCE_IMAGE) || context.settingShouldBeShown(PaperBridgeInputs.RETURN_RECTIFIED_IMAGE))) {
                        Text("Return attachments", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        if (context.settingShouldBeShown(PaperBridgeInputs.RETURN_SOURCE_IMAGE)) {
                            ToggleSettingRow("Source photograph", returnSourceImage) { returnSourceImage = it }
                        }
                        if (context.settingShouldBeShown(PaperBridgeInputs.RETURN_RECTIFIED_IMAGE)) {
                            ToggleSettingRow("Rectified page", returnRectifiedImage) { returnRectifiedImage = it }
                        }
                        Spacer(Modifier.height(8.dp))
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
                        Text("Anchors: ${extracted.rectification.anchors.joinToString { "${it.corner}=${"%.2f".format(it.score)}" }}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        Text("Page white: ${"%.1f".format(extracted.pageWhiteLuma)}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    }
                    if (committedFields.isNotEmpty()) Text("Committed fields: ${committedFields.size}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
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

    val omrCount = template?.fields?.count {
        it.type == PaperFieldType.OMR_SINGLE || it.type == PaperFieldType.OMR_MULTIPLE
    } ?: 0
    val textCount = template?.fields?.count {
        it.type == PaperFieldType.OCR_TEXT || it.type == PaperFieldType.OCR_INTEGER || it.type == PaperFieldType.OCR_DECIMAL
    } ?: 0
    val barcodeCount = template?.fields?.count { it.type == PaperFieldType.BARCODE } ?: 0
    val bundledDemo = template?.let(PaperBridgeWorkspace::isBundled) == true

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
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
                            Text("FORM IN USE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                            Text(
                                template?.title ?: "No usable form selected",
                                modifier = Modifier.padding(top = 3.dp),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                when {
                                    template == null -> "Choose or import a form before scanning."
                                    bundledDemo -> "Demo form selected · use this to test Paper Bridge, or choose your real form below."
                                    else -> "${template.fields.size} fields ready to read."
                                },
                                modifier = Modifier.padding(top = 3.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (template == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                if (formSetupOpen) "Hide form setup" else "Change form",
                                modifier = Modifier.clickable { formSetupOpen = !formSetupOpen }.padding(top = 8.dp, bottom = 2.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onCameraScan, enabled = template != null, modifier = Modifier.fillMaxWidth()) {
                        Text("Scan page")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onImageScan, enabled = template != null, modifier = Modifier.fillMaxWidth()) {
                        Text("Use photo from device")
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
            DashboardSectionTitle(
                "Built-in examples",
                "Ready-made fixtures shipped with Paper Bridge. Open one to learn the designer, or use it as a scanner test without importing anything."
            )
            val builtInExample = templates.firstOrNull(PaperBridgeWorkspace::isBundled)
                ?: runCatching { PaperTemplate.parse(PaperTemplateSamples.DEMO_MANIFEST_JSON) }.getOrNull()
            if (builtInExample != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.34f)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    builtInExample.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "BUILT-IN EXAMPLE · ${builtInExample.fields.size} mapped fields",
                                    modifier = Modifier.padding(top = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (bundledDemo) {
                                Text(
                                    "IN USE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(
                            "Includes its matching blank paper PDF and ODK XLSForm. It demonstrates text, integer and decimal OCR, single- and multiple-choice marks, and a barcode region.",
                            modifier = Modifier.padding(top = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onDesignForm(builtInExample) },
                                modifier = Modifier.weight(1f)
                            ) { Text("Open in Designer") }
                            OutlinedButton(
                                onClick = onUseDemoTemplate,
                                modifier = Modifier.weight(1f)
                            ) { Text(if (bundledDemo) "Example in use" else "Use for scan") }
                        }
                    }
                }
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                ) {
                    Text(
                        "Built-in example could not be loaded.",
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
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
                    "Form setup",
                    "Choose the paper questionnaire layout that matches the page you are about to scan."
                )
                if (templates.isEmpty()) {
                    Text("No forms are available.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    templates.forEach { item ->
                        val selected = template?.let { PaperBridgeWorkspace.templateKey(it) == PaperBridgeWorkspace.templateKey(item) } == true
                        PaperTemplateDashboardRow(
                            template = item,
                            selected = selected,
                            bundled = PaperBridgeWorkspace.isBundled(item),
                            onSelect = { onSelectTemplate(item) },
                            onEdit = { onDesignForm(item) },
                            onRemove = { onRemoveTemplate(item) }
                        )
                        Spacer(Modifier.height(7.dp))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onImportTemplate, modifier = Modifier.weight(1f)) { Text("Import form") }
                    OutlinedButton(onClick = { onDesignForm(null) }, modifier = Modifier.weight(1f)) { Text("New form") }
                }
                Text(
                    "Reset to built-in example",
                    modifier = Modifier.clickable(onClick = onUseDemoTemplate).padding(top = 8.dp, bottom = 2.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "The built-in example includes its matching paper PDF and ODK XLSForm automatically. Import form accepts an existing MethodMesh paper-form definition. Normally an ODK launch supplies the correct form automatically, so you do not need this screen during routine data collection.",
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
                            Text("Four steps from paper to the calling ODK form.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(if (helpOpen) "Hide" else "Show", style = MaterialTheme.typography.labelLarge, color = primary, fontWeight = FontWeight.Bold)
                    }
                    if (helpOpen) {
                        Spacer(Modifier.height(10.dp))
                        SimpleHelpStep("1", "Choose the form", "The selected form tells Paper Bridge where each answer is printed and which ODK field it belongs to.")
                        SimpleHelpStep("2", "Scan the completed page", "Use the camera or an existing photo. Keep all four registration targets visible.")
                        SimpleHelpStep("3", "Check anything uncertain", "Clear marks can pass automatically; ambiguous marks and text stay visible until you confirm them.")
                        SimpleHelpStep("4", "Commit", "Commit freezes the reviewed result and returns the named values and selected page images to the caller. If ODK launched Paper Bridge, you land back in that ODK form.")
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
                                    val attachments = listOfNotNull(
                                        "source".takeIf { returnSourceImage },
                                        "rectified".takeIf { returnRectifiedImage }
                                    )
                                    append(if (attachments.isEmpty()) "no page images" else attachments.joinToString(" + ") + " image")
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
                            title = "Auto-accept validated text",
                            subtitle = "Off by default. Text recognition normally remains visible for human confirmation.",
                            checked = autoAcceptOcr,
                            onCheckedChange = onAutoAcceptOcr
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("RETURN WITH RESULT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                        DashboardToggleRow(
                            title = "Original page photo",
                            subtitle = "Return the captured page with the committed values.",
                            checked = returnSourceImage,
                            onCheckedChange = onReturnSourceImage
                        )
                        DashboardToggleRow(
                            title = "Rectified page",
                            subtitle = "Return the perspective-corrected page used for reading.",
                            checked = returnRectifiedImage,
                            onCheckedChange = onReturnRectifiedImage
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
    onRemove: () -> Unit
) {
    val omr = template.fields.count { it.type == PaperFieldType.OMR_SINGLE || it.type == PaperFieldType.OMR_MULTIPLE }
    val ocr = template.fields.count { it.type == PaperFieldType.OCR_TEXT || it.type == PaperFieldType.OCR_INTEGER || it.type == PaperFieldType.OCR_DECIMAL }
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
                    "${template.templateId} · v${template.version} · ${template.fields.size} fields · $omr marks · $ocr text",
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (bundled) "Open matched PDF + XLSForm" else "Edit in Designer",
                    modifier = Modifier.clickable(onClick = onEdit).padding(top = 7.dp, bottom = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (!bundled) {
                Text(
                    "Remove",
                    modifier = Modifier.clickable(onClick = onRemove).padding(8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
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
    onManualValue: (String) -> Unit,
    onCopy: (String) -> Unit
) {
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
                            result.autoAccepted -> "Accepted automatically"
                            result.reviewed -> "Confirmed"
                            else -> "Review required"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (result.resolved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
                result.finalValue.takeIf(String::isNotBlank)?.let { value ->
                    Text(value, modifier = Modifier.clickable { onCopy(value) }.padding(6.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(5.dp))
            Text(result.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!result.resolved) {
                Spacer(Modifier.height(12.dp))
                when (result.spec.type) {
                    PaperFieldType.OMR_SINGLE -> SingleMarkReview(result, onManualValue)
                    PaperFieldType.OMR_MULTIPLE -> MultipleMarkReview(result, onManualValue)
                    PaperFieldType.OCR_TEXT, PaperFieldType.OCR_INTEGER, PaperFieldType.OCR_DECIMAL, PaperFieldType.BARCODE -> TextReview(result, onManualValue)
                }
            }
        }
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
        if (!result.spec.required) {
            FilterChip(selected = selected.isBlank(), onClick = { selected = "" }, label = { Text("Blank") })
        }
        Spacer(Modifier.height(6.dp))
        Button(modifier = Modifier.fillMaxWidth(), enabled = selected.isNotBlank() || !result.spec.required, onClick = { onManualValue(selected) }) { Text("Confirm") }
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
        Button(modifier = Modifier.fillMaxWidth(), enabled = selected.isNotEmpty() || !result.spec.required, onClick = { onManualValue(selectedRaw) }) { Text("Confirm selection") }
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
    Button(modifier = Modifier.fillMaxWidth(), enabled = value.isNotBlank() || !result.spec.required, onClick = { onManualValue(value) }) { Text("Confirm") }
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
