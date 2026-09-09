package com.example.methodmesh.modules.paperbridge

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal data class PaperDesignOptionDraft(
    val value: String,
    val label: String,
    val roi: NormalisedRoi? = null
)

internal data class PaperDesignFieldDraft(
    val name: String,
    val label: String,
    val type: PaperFieldType,
    val roi: NormalisedRoi? = null,
    val options: List<PaperDesignOptionDraft> = emptyList(),
    val required: Boolean = false,
    val regex: String? = null,
    val minimum: Double? = null,
    val maximum: Double? = null,
    val fromOdk: Boolean = false,
    val included: Boolean = true
)

private data class PaperDesignTarget(val fieldName: String, val optionValue: String? = null)
private data class PaperDesignLooseRegion(val id: String, val roi: NormalisedRoi)
private data class PaperDesignRegionView(
    val id: String,
    val roi: NormalisedRoi,
    val target: PaperDesignTarget?,
    val label: String
)
private enum class PaperDesignSeverity { ERROR, WARNING }
private data class PaperDesignIssue(val severity: PaperDesignSeverity, val message: String)

object PaperFormDesignCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100PaperFormDesignMethod.ID
    override val title = "Design a paper form"
    override val description = "Place, move and resize persistent page regions, then link them to ODK-safe fields and return both the JSON definition and an annotated mapping image."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val app = LocalContext.current
        val builtInExample = remember { PaperTemplate.parse(PaperTemplateSamples.DEMO_MANIFEST_JSON) }
        PaperFormDesignerSurface(
            initialTemplate = builtInExample,
            onBack = onCancel,
            onSaved = { template, source, export, issueCounts, odkCount ->
                val request = As100PaperFormDesignMethod.request(
                    action = As100PaperFormDesignMethod.ID,
                    context = context.request.invocationContext.asMap(As100PaperFormDesignMethod.ID),
                    signals = emptyList(),
                    inputs = emptyList()
                )
                val values = linkedMapOf(
                    PaperDesignFields.STATUS to "succeeded",
                    PaperDesignFields.TEMPLATE_JSON to template.rawJson,
                    PaperDesignFields.TEMPLATE_ID to template.templateId,
                    PaperDesignFields.TEMPLATE_VERSION to template.version,
                    PaperDesignFields.TEMPLATE_JSON_URI to export.templateJsonUri,
                    PaperDesignFields.MARKUP_IMAGE_URI to export.markupImageUri,
                    PaperDesignFields.FIELD_COUNT to template.fields.size.toString(),
                    PaperDesignFields.ODK_FIELD_COUNT to odkCount.toString(),
                    PaperDesignFields.ERROR_COUNT to issueCounts.first.toString(),
                    PaperDesignFields.WARNING_COUNT to issueCounts.second.toString(),
                    PaperDesignFields.SOURCE_TYPE to source.mimeType,
                    PaperDesignFields.ERROR to ""
                )
                onConfirmed(As100PaperFormDesignMethod.result(request, values, context.request.invocationContext))
            }
        )
    }
}

@Composable
internal fun PaperFormDesignerSurface(
    initialTemplate: PaperTemplate?,
    onBack: () -> Unit,
    onSaved: (PaperTemplate, PaperDesignSource, PaperDesignExport, Pair<Int, Int>, Int) -> Unit
) {
    val app = LocalContext.current
    val scope = rememberCoroutineScope()
    val initialSource = remember(initialTemplate?.templateId, initialTemplate?.version) {
        initialTemplate?.let { PaperDesignSourceStore.sourceFor(app, it) }
    }
    val builtInDemo = initialTemplate?.let(PaperBridgeBuiltInExamples::isDemo) == true
    var title by rememberSaveable {
        mutableStateOf(
            when {
                builtInDemo -> "${initialTemplate?.title ?: "Paper Bridge example"} copy"
                initialTemplate != null -> initialTemplate.title
                else -> "New paper form"
            }
        )
    }
    var templateId by rememberSaveable {
        mutableStateOf(
            when {
                builtInDemo -> "${initialTemplate?.templateId ?: PaperBridgeBuiltInExamples.DEMO_TEMPLATE_ID}_copy"
                initialTemplate != null -> initialTemplate.templateId
                else -> "paper_form"
            }
        )
    }
    var version by rememberSaveable { mutableStateOf(initialTemplate?.version ?: "1") }
    var source by remember { mutableStateOf(initialSource) }
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var sourceStatus by rememberSaveable {
        mutableStateOf(
            if (initialSource != null) "Loading ${initialSource.displayName}…"
            else "Choose the blank questionnaire PDF or image."
        )
    }
    var anchorsOk by rememberSaveable { mutableStateOf(false) }
    var anchorMessage by rememberSaveable { mutableStateOf("Anchors not checked yet.") }
    var odkSchema by remember { mutableStateOf<PaperOdkSchema?>(null) }
    var odkStatus by rememberSaveable {
        mutableStateOf(
            if (builtInDemo) "Loading the built-in matching XLSForm…"
            else "Optional: import the matching XLSForm to fill variable names, types and choices automatically."
        )
    }
    var fields by remember { mutableStateOf(initialTemplate?.fields?.map(::designFieldFromTemplate).orEmpty()) }
    var selectedField by rememberSaveable { mutableStateOf(fields.firstOrNull()?.name.orEmpty()) }
    var selectedOption by rememberSaveable { mutableStateOf("") }
    var looseRegions by remember { mutableStateOf<List<PaperDesignLooseRegion>>(emptyList()) }
    var selectedRegionId by rememberSaveable { mutableStateOf("") }
    var linkFieldName by rememberSaveable { mutableStateOf("") }
    var linkOptionValue by rememberSaveable { mutableStateOf("") }
    var linkFieldMenu by rememberSaveable { mutableStateOf(false) }
    var linkOptionMenu by rememberSaveable { mutableStateOf(false) }
    var addOpen by rememberSaveable { mutableStateOf(false) }
    var addName by rememberSaveable { mutableStateOf("") }
    var addLabel by rememberSaveable { mutableStateOf("") }
    var addType by rememberSaveable { mutableStateOf(PaperFieldType.OCR_TEXT.name) }
    var addTypeMenu by rememberSaveable { mutableStateOf(false) }
    var addChoices by rememberSaveable { mutableStateOf("yes|Yes\nno|No") }
    var testStatus by rememberSaveable { mutableStateOf("") }
    var testLines by remember { mutableStateOf(emptyList<String>()) }
    var saving by rememberSaveable { mutableStateOf(false) }
    var pageSize by remember { mutableStateOf(IntSize.Zero) }
    var showSetup by rememberSaveable { mutableStateOf(false) }

    fun loadSource(imported: PaperDesignSource) {
        source = imported
        sourceStatus = "Loading ${imported.displayName}…"
        anchorsOk = false
        anchorMessage = "Checking four registration targets…"
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { renderDesignSource(imported) } }
                .onFailure { sourceStatus = "Could not load blank form: ${it.message ?: "unsupported file"}" }
                .onSuccess { bitmap ->
                    sourceBitmap = bitmap
                    sourceStatus = "${imported.displayName} · ${bitmap.width}×${bitmap.height} design canvas"
                    val check = runCatching {
                        withContext(Dispatchers.Default) {
                            val probe = PaperTemplate(
                                schema = "methodmesh.paper.v1",
                                templateId = "anchor_probe",
                                version = "1",
                                title = "Anchor probe",
                                pageWidthPx = 800,
                                pageHeightPx = (800f * bitmap.height.toFloat() / bitmap.width.toFloat().coerceAtLeast(1f))
                                    .roundToInt()
                                    .coerceIn(600, 6000),
                                anchors = PaperAnchorSpec(),
                                fields = emptyList(),
                                rawJson = "{}"
                            )
                            PaperImageEngine.rectify(bitmap, probe)
                        }
                    }
                    check.onFailure {
                        anchorsOk = false
                        anchorMessage = "Registration targets not ready: ${it.message ?: "four targets not detected"}"
                    }.onSuccess { rectified ->
                        anchorsOk = true
                        val scores = rectified.anchors.joinToString(" · ") { "${it.corner} ${(it.score * 100).roundToInt()}%" }
                        anchorMessage = "Four registration targets detected · $scores"
                        if (!rectified.bitmap.isRecycled) rectified.bitmap.recycle()
                    }
                }
        }
    }

    LaunchedEffect(source?.path) {
        val current = source
        if (current != null && sourceBitmap == null) loadSource(current)
    }

    LaunchedEffect(initialTemplate?.templateId, initialTemplate?.version) {
        if (!builtInDemo || odkSchema != null) return@LaunchedEffect
        runCatching { withContext(Dispatchers.IO) { PaperBridgeBuiltInExamples.odkSchema() } }
            .onFailure { odkStatus = "Built-in XLSForm could not be loaded: ${it.message ?: "invalid example"}" }
            .onSuccess { schema ->
                odkSchema = schema
                val existing = fields.associateBy { it.name }
                fields = schema.fields.map { odkField ->
                    val base = designFieldFromOdk(odkField)
                    val prior = existing[odkField.name]
                    if (prior == null) base else base.copy(
                        roi = prior.roi,
                        options = base.options.map { option ->
                            option.copy(roi = prior.options.firstOrNull { it.value == option.value }?.roi)
                        }
                    )
                }
                odkStatus = "Built-in XLSForm loaded · ${schema.fields.size} compatible ODK fields." +
                    if (schema.warnings.isEmpty()) "" else " · ${schema.warnings.size} import warning${if (schema.warnings.size == 1) "" else "s"}."
            }
    }

    val sourcePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { PaperDesignSourceStore.import(app, uri) } }
                .onFailure { sourceStatus = "Import failed: ${it.message ?: "storage error"}" }
                .onSuccess { imported -> loadSource(imported) }
        }
    }
    val xlsPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            odkStatus = "Reading XLSForm…"
            runCatching { withContext(Dispatchers.IO) { PaperXlsFormReader.read(app, uri) } }
                .onFailure { odkStatus = "XLSForm not imported: ${it.message ?: "invalid XLSForm"}" }
                .onSuccess { schema ->
                    val preservedBoxes = designRegionViews(fields, looseRegions).map { region ->
                        PaperDesignLooseRegion("loose:${UUID.randomUUID()}", region.roi)
                    }
                    odkSchema = schema
                    title = schema.title
                    templateId = safeTemplateId(schema.formId)
                    version = schema.version
                    fields = schema.fields.map(::designFieldFromOdk)
                    looseRegions = preservedBoxes
                    selectedField = fields.firstOrNull()?.name.orEmpty()
                    selectedOption = ""
                    selectedRegionId = preservedBoxes.firstOrNull()?.id.orEmpty()
                    linkFieldName = ""
                    linkOptionValue = ""
                    odkStatus = "${schema.fields.size} compatible ODK fields loaded. Existing boxes were kept and unlinked for remapping" +
                        if (schema.warnings.isEmpty()) "." else " · ${schema.warnings.size} import warning${if (schema.warnings.size == 1) "" else "s"}."
                }
        }
    }

    val regionViews = remember(fields, looseRegions) { designRegionViews(fields, looseRegions) }
    val selectedRegion = regionViews.firstOrNull { it.id == selectedRegionId }
    val issues = remember(source?.path, anchorsOk, anchorMessage, fields, looseRegions, templateId, title, version) {
        validatePaperDesign(source, anchorsOk, anchorMessage, fields, looseRegions, templateId, title, version)
    }
    val errors = issues.filter { it.severity == PaperDesignSeverity.ERROR }
    val warnings = issues.filter { it.severity == PaperDesignSeverity.WARNING }
    val canBuild = errors.isEmpty() && sourceBitmap != null && source != null

    val testPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val bitmap = sourceBitmap ?: return@rememberLauncherForActivityResult
        val manifest = runCatching { buildPaperManifest(templateId, version, title, bitmap.width, bitmap.height, fields) }.getOrNull()
            ?: return@rememberLauncherForActivityResult
        scope.launch {
            testStatus = "Testing extraction…"
            testLines = emptyList()
            runCatching {
                val stable = withContext(Dispatchers.IO) { PaperBridgeFiles.copyIntoCache(app, uri, "designer-test") }
                PaperExtractionEngine.extract(
                    context = app,
                    sourceUri = stable,
                    template = PaperTemplate.parse(manifest),
                    settings = PaperExtractionSettings(autoAcceptOmr = true, autoAcceptOcr = false),
                    manualQuarterTurns = 0,
                    scanTimeIso = java.time.Instant.now().toString()
                )
            }.onFailure { testStatus = "Test failed: ${it.message ?: "could not read completed page"}" }
                .onSuccess { session ->
                    val unresolved = session.results.count { !it.resolved }
                    testStatus = if (unresolved == 0) "Test passed: all ${session.results.size} fields resolved." else "Test completed: $unresolved field${if (unresolved == 1) "" else "s"} would be sent to review."
                    testLines = session.results.map { result ->
                        val candidate = result.finalValue?.takeIf { it.isNotBlank() }
                            ?: result.candidate.takeIf { it.isNotBlank() }
                            ?: "—"
                        "${result.spec.name}: $candidate · ${if (result.resolved) "ready" else "review"}"
                    }
                }
        }
    }

    fun updateField(name: String, transform: (PaperDesignFieldDraft) -> PaperDesignFieldDraft) {
        fields = fields.map { if (it.name == name) transform(it) else it }
    }

    fun setTargetRoi(target: PaperDesignTarget, roi: NormalisedRoi?) {
        updateField(target.fieldName) { field ->
            if (target.optionValue == null) field.copy(roi = roi)
            else field.copy(options = field.options.map { option ->
                if (option.value == target.optionValue) option.copy(roi = roi) else option
            })
        }
    }

    fun updateRegion(id: String, roi: NormalisedRoi) {
        val view = designRegionViews(fields, looseRegions).firstOrNull { it.id == id } ?: return
        if (view.target != null) setTargetRoi(view.target, roi)
        else looseRegions = looseRegions.map { if (it.id == id) it.copy(roi = roi) else it }
    }

    fun placeLooseRegion(roi: NormalisedRoi) {
        val region = PaperDesignLooseRegion("loose:${UUID.randomUUID()}", roi)
        looseRegions = looseRegions + region
        selectedRegionId = region.id
        linkFieldName = ""
        linkOptionValue = ""
    }

    fun unlinkSelectedRegion() {
        val view = designRegionViews(fields, looseRegions).firstOrNull { it.id == selectedRegionId } ?: return
        val target = view.target ?: return
        setTargetRoi(target, null)
        val loose = PaperDesignLooseRegion("loose:${UUID.randomUUID()}", view.roi)
        looseRegions = looseRegions + loose
        selectedRegionId = loose.id
        linkFieldName = target.fieldName
        linkOptionValue = target.optionValue.orEmpty()
    }

    fun deleteSelectedRegion() {
        val view = designRegionViews(fields, looseRegions).firstOrNull { it.id == selectedRegionId } ?: return
        if (view.target != null) setTargetRoi(view.target, null)
        else looseRegions = looseRegions.filterNot { it.id == view.id }
        selectedRegionId = ""
    }

    fun linkSelectedLooseRegion() {
        val view = designRegionViews(fields, looseRegions).firstOrNull { it.id == selectedRegionId && it.target == null } ?: return
        val field = fields.firstOrNull { it.name == linkFieldName && it.included } ?: return
        val target = if (field.type in setOf(PaperFieldType.OMR_SINGLE, PaperFieldType.OMR_MULTIPLE)) {
            val option = field.options.firstOrNull { it.value == linkOptionValue } ?: return
            if (option.roi != null) return
            PaperDesignTarget(field.name, option.value)
        } else {
            if (field.roi != null) return
            PaperDesignTarget(field.name)
        }
        setTargetRoi(target, view.roi)
        looseRegions = looseRegions.filterNot { it.id == view.id }
        selectedRegionId = targetRegionId(target)
    }

    fun saveCurrentDesign() {
        val bitmap = sourceBitmap ?: return
        val currentSource = source ?: return
        if (!canBuild || saving) return
        saving = true
        runCatching {
            val json = buildPaperManifest(templateId, version, title, bitmap.width, bitmap.height, fields)
            val template = PaperBridgeWorkspace.saveAndActivateTemplate(app, json)
            PaperDesignSourceStore.bind(app, template, currentSource)
            val export = PaperDesignExportStore.save(app, template, bitmap)
            template to export
        }.onFailure {
            saving = false
            testStatus = "Save failed: ${it.message ?: "invalid design"}"
        }.onSuccess { (saved, export) ->
            saving = false
            onSaved(saved, currentSource, export, errors.size to warnings.size, odkSchema?.fields?.size ?: 0)
        }
    }

    Dialog(
        onDismissRequest = onBack,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Back",
                        modifier = Modifier.clickable(onClick = onBack).padding(horizontal = 6.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Column(Modifier.weight(1f)) {
                        Text("Paper Form Designer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "$title · ${if (anchorsOk) "anchors ready" else "check anchors"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (anchorsOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                    Text(
                        if (showSetup) "MAP" else "SETUP",
                        modifier = Modifier.clickable { showSetup = !showSetup }.padding(horizontal = 7.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        "TEST",
                        modifier = Modifier.clickable(enabled = canBuild) { if (canBuild) testPicker.launch("image/*") }
                            .padding(horizontal = 7.dp, vertical = 8.dp),
                        color = if (canBuild) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        if (saving) "SAVING" else "SAVE",
                        modifier = Modifier.clickable(enabled = canBuild && !saving) { saveCurrentDesign() }
                            .padding(horizontal = 7.dp, vertical = 8.dp),
                        color = if (canBuild && !saving) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(2f)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f))
                ) {
                    val bitmap = sourceBitmap
                    if (bitmap == null) {
                        Column(
                            modifier = Modifier.align(Alignment.Center).padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Open the blank paper questionnaire", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(sourceStatus, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(
                                onClick = { sourcePicker.launch(arrayOf("application/pdf", "image/*")) },
                                modifier = Modifier.padding(top = 16.dp)
                            ) { Text("Choose PDF or image") }
                        }
                    } else {
                        DesignerPageCanvas(
                            bitmap = bitmap,
                            regions = regionViews,
                            selectedRegionId = selectedRegionId,
                            onSelectedRegion = { id ->
                                selectedRegionId = id
                                regionViews.firstOrNull { it.id == id }?.target?.let { target ->
                                    selectedField = target.fieldName
                                    selectedOption = target.optionValue.orEmpty()
                                }
                            },
                            onSize = { pageSize = it },
                            onRegionPlaced = ::placeLooseRegion,
                            onRegionChanged = ::updateRegion
                        )
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    tonalElevation = 5.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        if (showSetup) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("FORM SETUP", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text(
                                    when {
                                        errors.isNotEmpty() -> "${errors.size} ERROR${if (errors.size == 1) "" else "S"}"
                                        warnings.isNotEmpty() -> "${warnings.size} WARNING${if (warnings.size == 1) "" else "S"}"
                                        else -> "READY"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (errors.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                            }
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { sourcePicker.launch(arrayOf("application/pdf", "image/*")) }, modifier = Modifier.weight(1f)) {
                                    Text(if (source == null) "Choose paper" else "Replace paper")
                                }
                                OutlinedButton(onClick = { xlsPicker.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) }, modifier = Modifier.weight(1f)) {
                                    Text(if (odkSchema == null) "Import XLSForm" else "Replace XLSForm")
                                }
                            }
                            Text(sourceStatus, modifier = Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall)
                            Text(
                                anchorMessage,
                                modifier = Modifier.padding(top = 3.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (anchorsOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                            Text(odkStatus, modifier = Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall)
                            odkSchema?.warnings?.forEach { warning ->
                                Text("• $warning", modifier = Modifier.padding(top = 3.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                            }
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(title, { title = it }, label = { Text("Form name") }, modifier = Modifier.weight(1f), singleLine = true)
                                OutlinedTextField(version, { version = it }, label = { Text("Version") }, modifier = Modifier.weight(0.42f), singleLine = true)
                            }
                            OutlinedTextField(templateId, { templateId = safeTemplateId(it) }, label = { Text("Form ID") }, modifier = Modifier.fillMaxWidth().padding(top = 7.dp), singleLine = true)

                            if (issues.isNotEmpty()) {
                                Text("DESIGN CHECKS", modifier = Modifier.padding(top = 10.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                issues.forEach { issue ->
                                    Text(
                                        "${if (issue.severity == PaperDesignSeverity.ERROR) "✕" else "!"} ${issue.message}",
                                        modifier = Modifier.padding(top = 3.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (issue.severity == PaperDesignSeverity.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }

                            OutlinedButton(onClick = { addOpen = !addOpen }, modifier = Modifier.fillMaxWidth().padding(top = 9.dp)) {
                                Text(if (addOpen) "Hide manual field" else "Add field manually")
                            }
                            if (addOpen) {
                                OutlinedTextField(addName, { addName = safeVariableName(it) }, label = { Text("Variable name") }, modifier = Modifier.fillMaxWidth().padding(top = 7.dp), singleLine = true)
                                OutlinedTextField(addLabel, { addLabel = it }, label = { Text("Question label") }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), singleLine = true)
                                Box(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                                    OutlinedButton(onClick = { addTypeMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("Type: ${prettyType(PaperFieldType.valueOf(addType))}") }
                                    DropdownMenu(expanded = addTypeMenu, onDismissRequest = { addTypeMenu = false }) {
                                        PaperFieldType.values().forEach { type ->
                                            DropdownMenuItem(text = { Text(prettyType(type)) }, onClick = { addType = type.name; addTypeMenu = false })
                                        }
                                    }
                                }
                                if (PaperFieldType.valueOf(addType) in setOf(PaperFieldType.OMR_SINGLE, PaperFieldType.OMR_MULTIPLE)) {
                                    OutlinedTextField(
                                        value = addChoices,
                                        onValueChange = { addChoices = it },
                                        label = { Text("Return options · one value|label per line") },
                                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                        minLines = 3
                                    )
                                }
                                Button(
                                    onClick = {
                                        val name = addName.trim()
                                        if (name.isNotBlank()) {
                                            val type = PaperFieldType.valueOf(addType)
                                            val options = if (type in setOf(PaperFieldType.OMR_SINGLE, PaperFieldType.OMR_MULTIPLE)) parseManualOptions(addChoices) else emptyList()
                                            fields = fields + PaperDesignFieldDraft(name, addLabel.ifBlank { name }, type, options = options)
                                            selectedField = name
                                            addName = ""
                                            addLabel = ""
                                            addOpen = false
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
                                    enabled = addName.isNotBlank() && fields.none { it.name == addName.trim() }
                                ) { Text("Add field") }
                            }
                        } else {
                            Text("ODK DATA LINKAGE", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            val region = selectedRegion
                            if (region == null) {
                                Text(
                                    "Tap any box on the page to inspect its ODK mapping, or use + BOX in the sidebar to place a new region.",
                                    modifier = Modifier.padding(top = 5.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Row(Modifier.fillMaxWidth().padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(if (region.target == null) "Unlinked paper box" else region.label, fontWeight = FontWeight.Bold)
                                        Text(
                                            "ROI ${String.format("%.3f", region.roi.left)}, ${String.format("%.3f", region.roi.top)} → ${String.format("%.3f", region.roi.right)}, ${String.format("%.3f", region.roi.bottom)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    OutlinedButton(onClick = ::deleteSelectedRegion) { Text("Delete") }
                                }

                                if (region.target == null) {
                                    Box(Modifier.fillMaxWidth().padding(top = 7.dp)) {
                                        OutlinedButton(onClick = { linkFieldMenu = true }, modifier = Modifier.fillMaxWidth()) {
                                            Text(fields.firstOrNull { it.name == linkFieldName }?.let { "${it.label} · ${it.name}" } ?: "Choose ODK field")
                                        }
                                        DropdownMenu(expanded = linkFieldMenu, onDismissRequest = { linkFieldMenu = false }) {
                                            fields.filter { it.included }.forEach { field ->
                                                DropdownMenuItem(
                                                    text = { Text("${field.label} (${field.name})") },
                                                    onClick = {
                                                        linkFieldName = field.name
                                                        selectedField = field.name
                                                        linkOptionValue = ""
                                                        linkFieldMenu = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    val linkField = fields.firstOrNull { it.name == linkFieldName && it.included }
                                    linkField?.let { DesignerOdkFieldDefinition(it, linkOptionValue) }
                                    linkField?.takeIf { it.type in setOf(PaperFieldType.OMR_SINGLE, PaperFieldType.OMR_MULTIPLE) }?.let { field ->
                                        Box(Modifier.fillMaxWidth().padding(top = 7.dp)) {
                                            OutlinedButton(onClick = { linkOptionMenu = true }, modifier = Modifier.fillMaxWidth()) {
                                                val option = field.options.firstOrNull { it.value == linkOptionValue }
                                                Text(option?.let { "Return ${it.label} → ${it.value}" } ?: "Choose return option")
                                            }
                                            DropdownMenu(expanded = linkOptionMenu, onDismissRequest = { linkOptionMenu = false }) {
                                                field.options.forEach { option ->
                                                    DropdownMenuItem(
                                                        text = { Text("${option.label} → ${option.value}${if (option.roi != null) " · already linked" else ""}") },
                                                        enabled = option.roi == null,
                                                        onClick = { linkOptionValue = option.value; linkOptionMenu = false }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    val canLink = when {
                                        linkField == null -> false
                                        linkField.type in setOf(PaperFieldType.OMR_SINGLE, PaperFieldType.OMR_MULTIPLE) -> linkField.options.any { it.value == linkOptionValue && it.roi == null }
                                        else -> linkField.roi == null
                                    }
                                    Button(onClick = ::linkSelectedLooseRegion, enabled = canLink, modifier = Modifier.fillMaxWidth().padding(top = 7.dp)) {
                                        Text("Link selected box to ODK")
                                    }
                                } else {
                                    val mappedField = fields.firstOrNull { it.name == region.target.fieldName }
                                    if (mappedField != null) DesignerOdkFieldDefinition(mappedField, region.target.optionValue.orEmpty())
                                    Row(Modifier.fillMaxWidth().padding(top = 7.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = ::unlinkSelectedRegion, modifier = Modifier.weight(1f)) { Text("Unlink") }
                                        OutlinedButton(
                                            onClick = {
                                                selectedField = region.target.fieldName
                                                selectedOption = region.target.optionValue.orEmpty()
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) { Text("Show field") }
                                    }
                                }
                            }

                            if (fields.isNotEmpty()) {
                                Text("ODK FIELDS", modifier = Modifier.padding(top = 10.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                fields.forEach { field ->
                                    DesignerFieldCard(
                                        field = field,
                                        selected = selectedField == field.name,
                                        onSelect = { selectedField = field.name; selectedOption = "" },
                                        onSelectTarget = { target ->
                                            selectedField = field.name
                                            selectedOption = target.optionValue.orEmpty()
                                            selectedRegionId = targetRegionId(target)
                                        },
                                        onRequired = { required -> updateField(field.name) { it.copy(required = required) } },
                                        onIncluded = { included ->
                                            updateField(field.name) { it.copy(included = included) }
                                            if (!included && regionViews.any { it.id == selectedRegionId && it.target?.fieldName == field.name }) selectedRegionId = ""
                                        },
                                        onDelete = {
                                            fields = fields.filterNot { it.name == field.name }
                                            if (selectedField == field.name) selectedField = fields.firstOrNull()?.name.orEmpty()
                                            if (regionViews.any { it.id == selectedRegionId && it.target?.fieldName == field.name }) selectedRegionId = ""
                                        }
                                    )
                                }
                            }
                            if (testStatus.isNotBlank()) {
                                Text(testStatus, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                testLines.forEach { Text(it, modifier = Modifier.padding(top = 2.dp), style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace) }
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class PaperDesignerCanvasMode { NAVIGATE, PLACE, EDIT }
private enum class PaperDesignResizeHandle { TOP_LEFT, TOP_RIGHT, BOTTOM_RIGHT, BOTTOM_LEFT }

private data class PaperDesignViewportLayout(
    val originX: Float,
    val originY: Float,
    val scaledWidth: Float,
    val scaledHeight: Float,
    val panX: Float,
    val panY: Float
)

private fun paperDesignViewportLayout(
    viewport: IntSize,
    bitmapWidth: Int,
    bitmapHeight: Int,
    zoom: Float,
    pan: Offset
): PaperDesignViewportLayout? {
    if (viewport.width <= 0 || viewport.height <= 0 || bitmapWidth <= 0 || bitmapHeight <= 0) return null
    val baseScale = min(
        viewport.width.toFloat() / bitmapWidth.toFloat(),
        viewport.height.toFloat() / bitmapHeight.toFloat()
    )
    val scaledWidth = bitmapWidth * baseScale * zoom.coerceAtLeast(1f)
    val scaledHeight = bitmapHeight * baseScale * zoom.coerceAtLeast(1f)
    val maxPanX = ((scaledWidth - viewport.width) / 2f).coerceAtLeast(0f)
    val maxPanY = ((scaledHeight - viewport.height) / 2f).coerceAtLeast(0f)
    val clampedPanX = pan.x.coerceIn(-maxPanX, maxPanX)
    val clampedPanY = pan.y.coerceIn(-maxPanY, maxPanY)
    return PaperDesignViewportLayout(
        originX = viewport.width / 2f - scaledWidth / 2f + clampedPanX,
        originY = viewport.height / 2f - scaledHeight / 2f + clampedPanY,
        scaledWidth = scaledWidth,
        scaledHeight = scaledHeight,
        panX = clampedPanX,
        panY = clampedPanY
    )
}

private fun toPaperNormalisedPoint(position: Offset, layout: PaperDesignViewportLayout?): Offset? {
    layout ?: return null
    if (layout.scaledWidth <= 0f || layout.scaledHeight <= 0f) return null
    val x = (position.x - layout.originX) / layout.scaledWidth
    val y = (position.y - layout.originY) / layout.scaledHeight
    if (x !in 0f..1f || y !in 0f..1f) return null
    return Offset(x, y)
}

private fun targetRegionId(target: PaperDesignTarget): String =
    if (target.optionValue == null) "field:${target.fieldName}" else "option:${target.fieldName}:${target.optionValue}"

private fun designRegionViews(
    fields: List<PaperDesignFieldDraft>,
    looseRegions: List<PaperDesignLooseRegion>
): List<PaperDesignRegionView> {
    val mapped = fields.filter { it.included }.flatMap { field ->
        if (field.type in setOf(PaperFieldType.OMR_SINGLE, PaperFieldType.OMR_MULTIPLE)) {
            field.options.mapNotNull { option ->
                option.roi?.let { roi ->
                    val target = PaperDesignTarget(field.name, option.value)
                    PaperDesignRegionView(targetRegionId(target), roi, target, "${field.name} = ${option.value}")
                }
            }
        } else {
            field.roi?.let { roi ->
                val target = PaperDesignTarget(field.name)
                listOf(PaperDesignRegionView(targetRegionId(target), roi, target, field.name))
            }.orEmpty()
        }
    }
    return mapped + looseRegions.map { PaperDesignRegionView(it.id, it.roi, null, "UNLINKED") }
}

private fun roiContains(roi: NormalisedRoi, point: Offset): Boolean =
    point.x in roi.left..roi.right && point.y in roi.top..roi.bottom

private fun movedRoi(initial: NormalisedRoi, dx: Float, dy: Float): NormalisedRoi {
    val width = initial.right - initial.left
    val height = initial.bottom - initial.top
    val left = (initial.left + dx).coerceIn(0f, 1f - width)
    val top = (initial.top + dy).coerceIn(0f, 1f - height)
    return NormalisedRoi(left, top, left + width, top + height)
}

private fun resizedRoi(initial: NormalisedRoi, handle: PaperDesignResizeHandle, point: Offset): NormalisedRoi {
    val minSize = 0.004f
    return when (handle) {
        PaperDesignResizeHandle.TOP_LEFT -> NormalisedRoi(
            point.x.coerceIn(0f, initial.right - minSize),
            point.y.coerceIn(0f, initial.bottom - minSize),
            initial.right,
            initial.bottom
        )
        PaperDesignResizeHandle.TOP_RIGHT -> NormalisedRoi(
            initial.left,
            point.y.coerceIn(0f, initial.bottom - minSize),
            point.x.coerceIn(initial.left + minSize, 1f),
            initial.bottom
        )
        PaperDesignResizeHandle.BOTTOM_RIGHT -> NormalisedRoi(
            initial.left,
            initial.top,
            point.x.coerceIn(initial.left + minSize, 1f),
            point.y.coerceIn(initial.top + minSize, 1f)
        )
        PaperDesignResizeHandle.BOTTOM_LEFT -> NormalisedRoi(
            point.x.coerceIn(0f, initial.right - minSize),
            initial.top,
            initial.right,
            point.y.coerceIn(initial.top + minSize, 1f)
        )
    }
}

@Composable
private fun DesignerPageCanvas(
    bitmap: Bitmap,
    regions: List<PaperDesignRegionView>,
    selectedRegionId: String,
    onSelectedRegion: (String) -> Unit,
    onSize: (IntSize) -> Unit,
    onRegionPlaced: (NormalisedRoi) -> Unit,
    onRegionChanged: (String, NormalisedRoi) -> Unit
) {
    var measured by remember { mutableStateOf(IntSize.Zero) }
    var zoom by rememberSaveable(bitmap.width, bitmap.height) { mutableStateOf(1f) }
    var panX by rememberSaveable(bitmap.width, bitmap.height) { mutableStateOf(0f) }
    var panY by rememberSaveable(bitmap.width, bitmap.height) { mutableStateOf(0f) }
    var modeName by rememberSaveable(bitmap.width, bitmap.height) { mutableStateOf(PaperDesignerCanvasMode.NAVIGATE.name) }
    val mode = PaperDesignerCanvasMode.valueOf(modeName)
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragEnd by remember { mutableStateOf<Offset?>(null) }
    var activeRegionId by remember { mutableStateOf<String?>(null) }
    var activeHandle by remember { mutableStateOf<PaperDesignResizeHandle?>(null) }
    var activeInitialRoi by remember { mutableStateOf<NormalisedRoi?>(null) }
    var activeStartPoint by remember { mutableStateOf<Offset?>(null) }
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.tertiary
    val error = MaterialTheme.colorScheme.error
    val surface = MaterialTheme.colorScheme.surface
    val inverseSurface = MaterialTheme.colorScheme.inverseSurface
    val inverseOnSurface = MaterialTheme.colorScheme.inverseOnSurface
    val currentRegions by rememberUpdatedState(regions)
    val currentSelectedRegionId by rememberUpdatedState(selectedRegionId)

    fun clampViewport(targetZoom: Float, requestedPan: Offset): PaperDesignViewportLayout? =
        paperDesignViewportLayout(
            measured,
            bitmap.width,
            bitmap.height,
            targetZoom.coerceIn(1f, 48f),
            requestedPan
        )

    fun applyZoom(targetZoom: Float) {
        val nextZoom = targetZoom.coerceIn(1f, 48f)
        val layout = clampViewport(nextZoom, Offset(panX, panY))
        zoom = nextZoom
        if (layout != null) {
            panX = layout.panX
            panY = layout.panY
        }
    }

    fun regionAt(point: Offset): PaperDesignRegionView? = currentRegions
        .filter { roiContains(it.roi, point) }
        .minByOrNull { region ->
            (region.roi.right - region.roi.left).coerceAtLeast(0f) *
                (region.roi.bottom - region.roi.top).coerceAtLeast(0f)
        }

    Box(
        Modifier
            .fillMaxSize()
            .background(surface)
            .onSizeChanged { measured = it; onSize(it) }
            .pointerInput(modeName, measured, bitmap.width, bitmap.height) {
                if (measured.width <= 0 || measured.height <= 0 || mode == PaperDesignerCanvasMode.PLACE) return@pointerInput
                detectTapGestures { position ->
                    val layout = paperDesignViewportLayout(measured, bitmap.width, bitmap.height, zoom, Offset(panX, panY))
                    val point = toPaperNormalisedPoint(position, layout)
                    val hit = point?.let(::regionAt)
                    onSelectedRegion(hit?.id.orEmpty())
                    if (hit != null) modeName = PaperDesignerCanvasMode.EDIT.name
                }
            }
            .pointerInput(modeName, measured, bitmap.width, bitmap.height) {
                if (measured.width <= 0 || measured.height <= 0) return@pointerInput
                when (mode) {
                    PaperDesignerCanvasMode.NAVIGATE -> detectTransformGestures { _, pan, gestureZoom, _ ->
                        val newZoom = (zoom * gestureZoom).coerceIn(1f, 48f)
                        val layout = paperDesignViewportLayout(
                            measured,
                            bitmap.width,
                            bitmap.height,
                            newZoom,
                            Offset(panX + pan.x, panY + pan.y)
                        )
                        zoom = newZoom
                        if (layout != null) {
                            panX = layout.panX
                            panY = layout.panY
                        }
                    }
                    PaperDesignerCanvasMode.PLACE -> detectDragGestures(
                        onDragStart = { position ->
                            val layout = paperDesignViewportLayout(measured, bitmap.width, bitmap.height, zoom, Offset(panX, panY))
                            toPaperNormalisedPoint(position, layout)?.let { point ->
                                dragStart = point
                                dragEnd = point
                            }
                        },
                        onDragEnd = {
                            val start = dragStart
                            val end = dragEnd
                            if (start != null && end != null) {
                                val left = min(start.x, end.x).coerceIn(0f, 1f)
                                val right = max(start.x, end.x).coerceIn(0f, 1f)
                                val top = min(start.y, end.y).coerceIn(0f, 1f)
                                val bottom = max(start.y, end.y).coerceIn(0f, 1f)
                                if (right - left > 0.0015f && bottom - top > 0.0015f) {
                                    onRegionPlaced(NormalisedRoi(left, top, right, bottom))
                                    modeName = PaperDesignerCanvasMode.EDIT.name
                                }
                            }
                            dragStart = null
                            dragEnd = null
                        },
                        onDragCancel = { dragStart = null; dragEnd = null },
                        onDrag = { change, _ ->
                            change.consume()
                            val layout = paperDesignViewportLayout(measured, bitmap.width, bitmap.height, zoom, Offset(panX, panY))
                            toPaperNormalisedPoint(change.position, layout)?.let { dragEnd = it }
                        }
                    )
                    PaperDesignerCanvasMode.EDIT -> detectDragGestures(
                        onDragStart = { position ->
                            val layout = paperDesignViewportLayout(measured, bitmap.width, bitmap.height, zoom, Offset(panX, panY))
                                ?: return@detectDragGestures
                            val point = toPaperNormalisedPoint(position, layout) ?: return@detectDragGestures
                            val selected = currentRegions.firstOrNull { it.id == currentSelectedRegionId }
                            fun screenPoint(x: Float, y: Float) = Offset(
                                layout.originX + x * layout.scaledWidth,
                                layout.originY + y * layout.scaledHeight
                            )
                            fun dist2(a: Offset, b: Offset): Float {
                                val dx = a.x - b.x
                                val dy = a.y - b.y
                                return dx * dx + dy * dy
                            }
                            val handleThreshold2 = 48f * 48f
                            val handle = selected?.let { region ->
                                listOf(
                                    PaperDesignResizeHandle.TOP_LEFT to screenPoint(region.roi.left, region.roi.top),
                                    PaperDesignResizeHandle.TOP_RIGHT to screenPoint(region.roi.right, region.roi.top),
                                    PaperDesignResizeHandle.BOTTOM_RIGHT to screenPoint(region.roi.right, region.roi.bottom),
                                    PaperDesignResizeHandle.BOTTOM_LEFT to screenPoint(region.roi.left, region.roi.bottom)
                                ).minByOrNull { (_, screen) -> dist2(position, screen) }
                                    ?.takeIf { (_, screen) -> dist2(position, screen) <= handleThreshold2 }
                                    ?.first
                            }
                            val hit = if (handle != null && selected != null) selected else regionAt(point)
                            if (hit != null) {
                                onSelectedRegion(hit.id)
                                activeRegionId = hit.id
                                activeHandle = if (hit.id == currentSelectedRegionId) handle else null
                                activeInitialRoi = hit.roi
                                activeStartPoint = point
                            } else {
                                onSelectedRegion("")
                                activeRegionId = null
                                activeHandle = null
                                activeInitialRoi = null
                                activeStartPoint = null
                            }
                        },
                        onDragEnd = {
                            activeRegionId = null
                            activeHandle = null
                            activeInitialRoi = null
                            activeStartPoint = null
                        },
                        onDragCancel = {
                            activeRegionId = null
                            activeHandle = null
                            activeInitialRoi = null
                            activeStartPoint = null
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val id = activeRegionId ?: return@detectDragGestures
                            val initial = activeInitialRoi ?: return@detectDragGestures
                            val start = activeStartPoint ?: return@detectDragGestures
                            val layout = paperDesignViewportLayout(measured, bitmap.width, bitmap.height, zoom, Offset(panX, panY))
                            val current = toPaperNormalisedPoint(change.position, layout) ?: return@detectDragGestures
                            val next = activeHandle?.let { handle -> resizedRoi(initial, handle, current) }
                                ?: movedRoi(initial, current.x - start.x, current.y - start.y)
                            onRegionChanged(id, next)
                        }
                    )
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val viewport = IntSize(size.width.roundToInt(), size.height.roundToInt())
            val layout = paperDesignViewportLayout(viewport, bitmap.width, bitmap.height, zoom, Offset(panX, panY))
                ?: return@Canvas
            drawImage(
                image = bitmap.asImageBitmap(),
                dstOffset = IntOffset(layout.originX.roundToInt(), layout.originY.roundToInt()),
                dstSize = IntSize(layout.scaledWidth.roundToInt(), layout.scaledHeight.roundToInt())
            )

            fun pagePoint(p: Offset) = Offset(
                layout.originX + p.x * layout.scaledWidth,
                layout.originY + p.y * layout.scaledHeight
            )

            regions.forEach { region ->
                val selected = region.id == selectedRegionId
                val color = when {
                    selected -> secondary
                    region.target == null -> error
                    else -> primary.copy(alpha = 0.90f)
                }
                val topLeft = pagePoint(Offset(region.roi.left, region.roi.top))
                val bottomRight = pagePoint(Offset(region.roi.right, region.roi.bottom))
                drawRect(
                    color = color,
                    topLeft = topLeft,
                    size = Size(bottomRight.x - topLeft.x, bottomRight.y - topLeft.y),
                    style = Stroke(width = if (selected) 6f else 3f)
                )

                val label = region.label.take(56)
                val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = inverseOnSurface.toArgb()
                    textSize = 25f
                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
                }
                val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = inverseSurface.copy(alpha = 0.90f).toArgb()
                    style = android.graphics.Paint.Style.FILL
                }
                val textWidth = textPaint.measureText(label)
                val labelTop = (topLeft.y - 33f).coerceAtLeast(0f)
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawRect(topLeft.x, labelTop, topLeft.x + textWidth + 16f, labelTop + 33f, bgPaint)
                    canvas.nativeCanvas.drawText(label, topLeft.x + 8f, labelTop + 24f, textPaint)
                }

                if (selected) {
                    val handleRadius = 12f
                    listOf(
                        topLeft,
                        Offset(bottomRight.x, topLeft.y),
                        bottomRight,
                        Offset(topLeft.x, bottomRight.y)
                    ).forEach { handle ->
                        drawCircle(color = secondary, radius = handleRadius + 5f, center = handle)
                        drawCircle(color = surface, radius = handleRadius, center = handle)
                    }
                }
            }

            val start = dragStart
            val end = dragEnd
            if (start != null && end != null) {
                val a = pagePoint(start)
                val b = pagePoint(end)
                drawRect(
                    color = secondary,
                    topLeft = Offset(min(a.x, b.x), min(a.y, b.y)),
                    size = Size(kotlin.math.abs(b.x - a.x), kotlin.math.abs(b.y - a.y)),
                    style = Stroke(width = 5f)
                )
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp),
            shape = RoundedCornerShape(18.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                FilterChip(
                    selected = mode == PaperDesignerCanvasMode.NAVIGATE,
                    onClick = { modeName = PaperDesignerCanvasMode.NAVIGATE.name; dragStart = null; dragEnd = null },
                    label = { Text("NAV") }
                )
                FilterChip(
                    selected = mode == PaperDesignerCanvasMode.PLACE,
                    onClick = { modeName = PaperDesignerCanvasMode.PLACE.name; dragStart = null; dragEnd = null },
                    label = { Text("+ BOX") }
                )
                FilterChip(
                    selected = mode == PaperDesignerCanvasMode.EDIT,
                    enabled = regions.isNotEmpty(),
                    onClick = { modeName = PaperDesignerCanvasMode.EDIT.name; dragStart = null; dragEnd = null },
                    label = { Text("EDIT") }
                )
                Text(
                    "+",
                    modifier = Modifier.clickable { applyZoom(zoom * 1.8f) }.padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "−",
                    modifier = Modifier.clickable { applyZoom(zoom / 1.8f) }.padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "FIT",
                    modifier = Modifier.clickable {
                        zoom = 1f
                        panX = 0f
                        panY = 0f
                        modeName = PaperDesignerCanvasMode.NAVIGATE.name
                    }.padding(horizontal = 8.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.86f)
        ) {
            Text(
                when (mode) {
                    PaperDesignerCanvasMode.NAVIGATE -> if (zoom <= 1.01f) "Pinch or + to zoom · drag to pan" else "Navigate · ${String.format("%.1f", zoom)}×"
                    PaperDesignerCanvasMode.PLACE -> "Drag once to place a box · ${String.format("%.1f", zoom)}×"
                    PaperDesignerCanvasMode.EDIT -> "Tap box · drag to move · corner handle to resize"
                },
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                color = MaterialTheme.colorScheme.inverseOnSurface,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun DesignerOdkFieldDefinition(
    field: PaperDesignFieldDraft,
    selectedOptionValue: String = ""
) {
    val constraintText = when (field.type) {
        PaperFieldType.OCR_TEXT -> field.regex?.takeIf { it.isNotBlank() }?.let { "Regex: $it" } ?: "Text · no imported regex constraint"
        PaperFieldType.OCR_INTEGER, PaperFieldType.OCR_DECIMAL -> when {
            field.minimum != null && field.maximum != null -> "Range: ${field.minimum} to ${field.maximum}"
            field.minimum != null -> "Minimum: ${field.minimum}"
            field.maximum != null -> "Maximum: ${field.maximum}"
            else -> "No imported numeric range constraint"
        }
        PaperFieldType.OMR_SINGLE -> "Select one · ${field.options.size} return option${if (field.options.size == 1) "" else "s"}"
        PaperFieldType.OMR_MULTIPLE -> "Select multiple · ${field.options.size} return option${if (field.options.size == 1) "" else "s"}"
        PaperFieldType.BARCODE -> "Barcode value"
    }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
    ) {
        Column(Modifier.padding(11.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(field.label, fontWeight = FontWeight.Bold)
                    Text(field.name, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace)
                }
                Text(prettyType(field.type), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                "${if (field.required) "Required" else "Optional"} · $constraintText",
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (field.type in setOf(PaperFieldType.OMR_SINGLE, PaperFieldType.OMR_MULTIPLE)) {
                Text(
                    field.options.joinToString("   ") { option ->
                        val marker = if (option.value == selectedOptionValue) "●" else "•"
                        "$marker ${option.label} → ${option.value}"
                    },
                    modifier = Modifier.padding(top = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun DesignerFieldCard(
    field: PaperDesignFieldDraft,
    selected: Boolean,
    onSelect: () -> Unit,
    onSelectTarget: (PaperDesignTarget) -> Unit,
    onRequired: (Boolean) -> Unit,
    onIncluded: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val mapped = if (field.type in setOf(PaperFieldType.OMR_SINGLE, PaperFieldType.OMR_MULTIPLE)) field.options.count { it.roi != null } else if (field.roi != null) 1 else 0
    val needed = if (field.type in setOf(PaperFieldType.OMR_SINGLE, PaperFieldType.OMR_MULTIPLE)) field.options.size else 1
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 7.dp).clickable(onClick = onSelect),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(field.label, fontWeight = FontWeight.SemiBold)
                    Text("${field.name} · ${prettyType(field.type)} · $mapped/$needed linked", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val constraintSummary = when (field.type) {
                        PaperFieldType.OCR_TEXT -> field.regex?.takeIf { it.isNotBlank() }?.let { "regex: $it" }
                        PaperFieldType.OCR_INTEGER, PaperFieldType.OCR_DECIMAL -> when {
                            field.minimum != null && field.maximum != null -> "range: ${field.minimum}–${field.maximum}"
                            field.minimum != null -> "minimum: ${field.minimum}"
                            field.maximum != null -> "maximum: ${field.maximum}"
                            else -> null
                        }
                        PaperFieldType.OMR_SINGLE -> "select one: ${field.options.joinToString { it.value }}"
                        PaperFieldType.OMR_MULTIPLE -> "select multiple: ${field.options.joinToString { it.value }}"
                        PaperFieldType.BARCODE -> "barcode"
                    }
                    constraintSummary?.let { Text(it, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Text(if (field.fromOdk) "ODK" else "MANUAL", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            if (field.fromOdk) {
                Row(Modifier.fillMaxWidth().padding(top = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Read this ODK field from paper", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Switch(checked = field.included, onCheckedChange = onIncluded)
                }
            }
            if (field.included) {
                Row(Modifier.fillMaxWidth().padding(top = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Required", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Switch(checked = field.required, onCheckedChange = onRequired)
                }
            }
            if (field.included && field.type in setOf(PaperFieldType.OMR_SINGLE, PaperFieldType.OMR_MULTIPLE)) {
                field.options.forEach { option ->
                    Row(Modifier.fillMaxWidth().padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(option.label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            Text("return: ${option.value}", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                        }
                        if (option.roi != null) {
                            OutlinedButton(onClick = { onSelectTarget(PaperDesignTarget(field.name, option.value)) }) { Text("Select box") }
                        } else {
                            Text("Not linked", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            } else if (field.included) {
                if (field.roi != null) {
                    OutlinedButton(
                        onClick = { onSelectTarget(PaperDesignTarget(field.name)) },
                        modifier = Modifier.fillMaxWidth().padding(top = 7.dp)
                    ) { Text("Select box") }
                } else {
                    Text(
                        "Not linked. Place a box on the page, then link it to this field.",
                        modifier = Modifier.padding(top = 7.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                Text("Excluded from the paper mapping.", modifier = Modifier.padding(top = 7.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!field.fromOdk) {
                Text("Delete field", modifier = Modifier.clickable(onClick = onDelete).padding(top = 8.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun designFieldFromOdk(field: PaperOdkField): PaperDesignFieldDraft = PaperDesignFieldDraft(
    name = field.name,
    label = field.label,
    type = field.type,
    options = field.options.map { (value, label) -> PaperDesignOptionDraft(value, label) },
    required = field.required,
    regex = field.regex,
    minimum = field.minimum,
    maximum = field.maximum,
    fromOdk = true
)

private fun designFieldFromTemplate(field: PaperFieldSpec): PaperDesignFieldDraft = PaperDesignFieldDraft(
    name = field.name,
    label = field.label,
    type = field.type,
    roi = field.roi,
    options = field.options.map { PaperDesignOptionDraft(it.value, it.label, it.roi) },
    required = field.required,
    regex = field.regex,
    minimum = field.minimum,
    maximum = field.maximum,
    fromOdk = false
)

private fun parseManualOptions(raw: String): List<PaperDesignOptionDraft> = raw.lines().mapNotNull { line ->
    val cleaned = line.trim()
    if (cleaned.isBlank()) return@mapNotNull null
    val value = cleaned.substringBefore('|').trim()
    if (value.isBlank()) return@mapNotNull null
    val label = cleaned.substringAfter('|', value).trim().ifBlank { value }
    PaperDesignOptionDraft(value, label)
}

private fun safeVariableName(raw: String): String = raw.trim().replace(Regex("[^A-Za-z0-9_]+"), "_").trim('_').let { value ->
    when {
        value.isBlank() -> ""
        value.first().isDigit() -> "v_$value"
        else -> value
    }
}

private fun safeTemplateId(raw: String): String = safeVariableName(raw.lowercase()).ifBlank { "paper_form" }

private fun prettyType(type: PaperFieldType): String = when (type) {
    PaperFieldType.OMR_SINGLE -> "Single choice"
    PaperFieldType.OMR_MULTIPLE -> "Multiple choice"
    PaperFieldType.OCR_TEXT -> "Text"
    PaperFieldType.OCR_INTEGER -> "Integer"
    PaperFieldType.OCR_DECIMAL -> "Decimal"
    PaperFieldType.BARCODE -> "Barcode"
}

private fun validatePaperDesign(
    source: PaperDesignSource?,
    anchorsOk: Boolean,
    anchorMessage: String,
    fields: List<PaperDesignFieldDraft>,
    looseRegions: List<PaperDesignLooseRegion>,
    templateId: String,
    title: String,
    version: String
): List<PaperDesignIssue> {
    val issues = mutableListOf<PaperDesignIssue>()
    if (source == null) issues += PaperDesignIssue(PaperDesignSeverity.ERROR, "Choose the blank paper questionnaire.")
    if (source != null && !anchorsOk) issues += PaperDesignIssue(PaperDesignSeverity.ERROR, anchorMessage)
    if (title.isBlank()) issues += PaperDesignIssue(PaperDesignSeverity.ERROR, "Form name is blank.")
    if (!Regex("^[A-Za-z_][A-Za-z0-9_]*$").matches(templateId)) issues += PaperDesignIssue(PaperDesignSeverity.ERROR, "Form ID must be a safe identifier.")
    if (version.isBlank()) issues += PaperDesignIssue(PaperDesignSeverity.ERROR, "Version is blank.")
    if (fields.none { it.included }) issues += PaperDesignIssue(PaperDesignSeverity.ERROR, "Include at least one field in the paper mapping.")
    if (looseRegions.isNotEmpty()) issues += PaperDesignIssue(
        PaperDesignSeverity.ERROR,
        "${looseRegions.size} placed box${if (looseRegions.size == 1) " is" else "es are"} not linked to an ODK field."
    )
    val duplicates = fields.filter { it.included }.groupBy { it.name }.filterValues { it.size > 1 }.keys
    duplicates.forEach { issues += PaperDesignIssue(PaperDesignSeverity.ERROR, "Duplicate variable '$it'.") }

    val regions = mutableListOf<Pair<String, NormalisedRoi>>()
    fields.filter { it.included }.forEach { field ->
        if (!Regex("^[A-Za-z_][A-Za-z0-9_]*$").matches(field.name)) issues += PaperDesignIssue(PaperDesignSeverity.ERROR, "${field.name}: invalid ODK variable name.")
        if (field.name.startsWith("paper_") || field.name.startsWith("methodmesh_")) issues += PaperDesignIssue(PaperDesignSeverity.ERROR, "${field.name}: reserved MethodMesh return prefix.")
        when (field.type) {
            PaperFieldType.OMR_SINGLE, PaperFieldType.OMR_MULTIPLE -> {
                if (field.options.isEmpty()) issues += PaperDesignIssue(PaperDesignSeverity.ERROR, "${field.name}: add return options.")
                val duplicateOptions = field.options.groupBy { it.value }.filterValues { it.size > 1 }.keys
                duplicateOptions.forEach { value -> issues += PaperDesignIssue(PaperDesignSeverity.ERROR, "${field.name}: duplicate return option '$value'.") }
                field.options.forEach { option ->
                    val roi = option.roi
                    if (roi == null) issues += PaperDesignIssue(PaperDesignSeverity.ERROR, "${field.name} = ${option.value}: link a placed box to this return option.")
                    else regions += "${field.name}=${option.value}" to roi
                }
            }
            else -> {
                val roi = field.roi
                if (roi == null) issues += PaperDesignIssue(PaperDesignSeverity.ERROR, "${field.name}: link a placed box to this field.")
                else regions += field.name to roi
            }
        }
    }
    regions.forEach { (label, roi) ->
        if (!validRoi(roi)) issues += PaperDesignIssue(PaperDesignSeverity.ERROR, "$label: region is outside the page or inverted.")
        val area = (roi.right - roi.left) * (roi.bottom - roi.top)
        if (area in 0f..0.00025f) issues += PaperDesignIssue(PaperDesignSeverity.WARNING, "$label: region is very small; recognition may be fragile.")
        if (touchesAnchorZone(roi)) issues += PaperDesignIssue(PaperDesignSeverity.ERROR, "$label: region intrudes into a reserved corner-anchor zone.")
    }
    for (i in regions.indices) for (j in i + 1 until regions.size) {
        val a = regions[i]
        val b = regions[j]
        val overlap = overlapFraction(a.second, b.second)
        if (overlap > 0.35f) issues += PaperDesignIssue(PaperDesignSeverity.ERROR, "${a.first} overlaps ${b.first}; redraw one of the regions.")
    }
    return issues.distinctBy { it.severity to it.message }
}

private fun validRoi(roi: NormalisedRoi): Boolean =
    roi.left in 0f..1f && roi.right in 0f..1f && roi.top in 0f..1f && roi.bottom in 0f..1f && roi.left < roi.right && roi.top < roi.bottom

private fun touchesAnchorZone(roi: NormalisedRoi): Boolean {
    val zones = listOf(
        NormalisedRoi(0f, 0f, 0.13f, 0.13f),
        NormalisedRoi(0.87f, 0f, 1f, 0.13f),
        NormalisedRoi(0.87f, 0.87f, 1f, 1f),
        NormalisedRoi(0f, 0.87f, 0.13f, 1f)
    )
    return zones.any { overlapArea(roi, it) > 0f }
}

private fun overlapFraction(a: NormalisedRoi, b: NormalisedRoi): Float {
    val intersection = overlapArea(a, b)
    if (intersection <= 0f) return 0f
    val aArea = (a.right - a.left) * (a.bottom - a.top)
    val bArea = (b.right - b.left) * (b.bottom - b.top)
    return intersection / min(aArea, bArea).coerceAtLeast(0.000001f)
}

private fun overlapArea(a: NormalisedRoi, b: NormalisedRoi): Float {
    val width = (min(a.right, b.right) - max(a.left, b.left)).coerceAtLeast(0f)
    val height = (min(a.bottom, b.bottom) - max(a.top, b.top)).coerceAtLeast(0f)
    return width * height
}

private fun buildPaperManifest(
    templateId: String,
    version: String,
    title: String,
    width: Int,
    height: Int,
    fields: List<PaperDesignFieldDraft>
): String {
    val root = JSONObject()
        .put("schema", "methodmesh.paper.v1")
        .put("template_id", templateId)
        .put("version", version)
        .put("title", title)
        .put("page", JSONObject().put("width_px", width.coerceIn(600, 4000)).put("height_px", height.coerceIn(600, 6000)))
        .put("anchors", JSONObject().put("search_fraction", 0.24).put("threshold_luma", 150).put("minimum_score", 0.28))
    val array = JSONArray()
    fields.filter { it.included }.forEach { field ->
        val item = JSONObject()
            .put("name", field.name)
            .put("label", field.label)
            .put("type", when (field.type) {
                PaperFieldType.OMR_SINGLE -> "omr_single"
                PaperFieldType.OMR_MULTIPLE -> "omr_multiple"
                PaperFieldType.OCR_TEXT -> "ocr_text"
                PaperFieldType.OCR_INTEGER -> "ocr_integer"
                PaperFieldType.OCR_DECIMAL -> "ocr_decimal"
                PaperFieldType.BARCODE -> "barcode"
            })
            .put("required", field.required)
        field.regex?.takeIf(String::isNotBlank)?.let { item.put("regex", it) }
        field.minimum?.let { item.put("min", it) }
        field.maximum?.let { item.put("max", it) }
        when (field.type) {
            PaperFieldType.OMR_SINGLE, PaperFieldType.OMR_MULTIPLE -> {
                item.put("threshold", 0.18).put("min_separation", 0.06)
                    .put("allowed_values", JSONArray().apply { field.options.forEach { put(it.value) } })
                val options = JSONArray()
                field.options.forEach { option ->
                    val roi = requireNotNull(option.roi) { "${field.name}=${option.value} is not mapped." }
                    options.put(JSONObject().put("value", option.value).put("label", option.label).put("roi", roiJson(roi)))
                }
                item.put("options", options)
            }
            else -> {
                val roi = requireNotNull(field.roi) { "${field.name} is not mapped." }
                item.put("roi", roiJson(roi))
                if (field.type != PaperFieldType.BARCODE) item.put("normalise", "trim").put("auto_accept", false)
            }
        }
        array.put(item)
    }
    root.put("fields", array)
    val json = root.toString()
    PaperTemplate.parse(json) // final schema-level assertion
    return json
}

private fun roiJson(roi: NormalisedRoi): JSONArray = JSONArray().put(roi.left.toDouble()).put(roi.top.toDouble()).put(roi.right.toDouble()).put(roi.bottom.toDouble())

private fun renderDesignSource(source: PaperDesignSource): Bitmap {
    val file = File(source.path)
    require(file.exists()) { "Blank form source is no longer available." }
    if (source.mimeType == "application/pdf" || file.extension.equals("pdf", true)) {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                require(renderer.pageCount > 0) { "PDF has no pages." }
                val page = renderer.openPage(0)
                try {
                    val targetWidth = 1600
                    val scale = targetWidth.toFloat() / page.width.toFloat().coerceAtLeast(1f)
                    val targetHeight = (page.height * scale).roundToInt().coerceAtLeast(600)
                    val bitmap = Bitmap.createBitmap(targetWidth, targetHeight.coerceAtMost(6000), Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    return bitmap
                } finally {
                    page.close()
                }
            }
        }
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Image could not be decoded." }
    require(bounds.outWidth <= 30_000 && bounds.outHeight <= 30_000) { "Image dimensions are too large for the designer." }

    var sample = 1
    while (max(bounds.outWidth / sample, bounds.outHeight / sample) > 3200) sample *= 2
    val options = BitmapFactory.Options().apply {
        inSampleSize = sample.coerceAtLeast(1)
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val decoded = BitmapFactory.decodeFile(file.absolutePath, options)
        ?: throw IllegalArgumentException("Image could not be decoded.")
    val longest = max(decoded.width, decoded.height)
    val scale = when {
        longest < 1200 -> 1200f / longest.toFloat().coerceAtLeast(1f)
        longest > 2400 -> 2400f / longest.toFloat()
        else -> 1f
    }
    if (kotlin.math.abs(scale - 1f) < 0.01f) return decoded
    val targetWidth = (decoded.width * scale).roundToInt().coerceAtLeast(1)
    val targetHeight = (decoded.height * scale).roundToInt().coerceAtLeast(1)
    val scaled = Bitmap.createScaledBitmap(decoded, targetWidth, targetHeight, true)
    if (scaled !== decoded) decoded.recycle()
    return scaled
}
