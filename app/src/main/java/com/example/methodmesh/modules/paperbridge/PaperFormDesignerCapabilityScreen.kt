package com.example.methodmesh.modules.paperbridge

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.widget.Toast
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.methodmesh.MainActivity
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.protocols.PresetResultAction
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.ReturnMode
import com.example.methodmesh.transport.workflow.ui.CapabilityCompletionMode
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
    val requiredExpression: String? = null,
    val relevanceExpression: String? = null,
    val constraintExpression: String? = null,
    val constraintMessage: String? = null,
    val regex: String? = null,
    val minimum: Double? = null,
    val maximum: Double? = null,
    val fromOdk: Boolean = false,
    val included: Boolean = true
)

private data class PaperDesignTarget(val fieldName: String, val optionValue: String? = null)
private data class PaperDesignLooseRegion(val id: String, val roi: NormalisedRoi)

private fun designSourceStateJson(source: PaperDesignSource?): String = source?.let {
    JSONObject()
        .put("path", it.path)
        .put("mime", it.mimeType)
        .put("name", it.displayName)
        .put("registered", it.alreadyRegistered)
        .toString()
}.orEmpty()

private fun designSourceFromStateJson(raw: String): PaperDesignSource? = if (raw.isBlank()) null else runCatching {
    val o = JSONObject(raw)
    PaperDesignSource(
        path = o.getString("path"),
        mimeType = o.optString("mime", "image/*"),
        displayName = o.optString("name", "paper-form"),
        alreadyRegistered = o.optBoolean("registered", false)
    )
}.getOrNull()

private fun designFieldsStateJson(fields: List<PaperDesignFieldDraft>): String = JSONArray().apply {
    fields.forEach { field ->
        put(JSONObject().apply {
            put("name", field.name); put("label", field.label); put("type", field.type.name)
            field.roi?.let { put("roi", roiStateJson(it)) }
            put("required", field.required)
            put("required_expression", field.requiredExpression ?: JSONObject.NULL)
            put("relevance_expression", field.relevanceExpression ?: JSONObject.NULL)
            put("constraint_expression", field.constraintExpression ?: JSONObject.NULL)
            put("constraint_message", field.constraintMessage ?: JSONObject.NULL)
            put("regex", field.regex ?: JSONObject.NULL)
            field.minimum?.let { put("minimum", it) }; field.maximum?.let { put("maximum", it) }
            put("from_odk", field.fromOdk); put("included", field.included)
            put("options", JSONArray().apply { field.options.forEach { option ->
                put(JSONObject().apply { put("value", option.value); put("label", option.label); option.roi?.let { put("roi", roiStateJson(it)) } })
            } })
        })
    }
}.toString()

private fun designFieldsFromStateJson(raw: String): List<PaperDesignFieldDraft> = if (raw.isBlank()) emptyList() else runCatching {
    val a = JSONArray(raw)
    (0 until a.length()).map { index ->
        val o = a.getJSONObject(index)
        PaperDesignFieldDraft(
            name = o.getString("name"), label = o.optString("label", o.getString("name")),
            type = PaperFieldType.valueOf(o.getString("type")),
            roi = o.optJSONObject("roi")?.let(::roiFromStateJson),
            options = o.optJSONArray("options")?.let { options -> (0 until options.length()).map { oi ->
                val option = options.getJSONObject(oi)
                PaperDesignOptionDraft(option.getString("value"), option.optString("label", option.getString("value")), option.optJSONObject("roi")?.let(::roiFromStateJson))
            } }.orEmpty(),
            required = o.optBoolean("required", false),
            requiredExpression = o.optString("required_expression").takeIf { it.isNotBlank() && it != "null" },
            relevanceExpression = o.optString("relevance_expression").takeIf { it.isNotBlank() && it != "null" },
            constraintExpression = o.optString("constraint_expression").takeIf { it.isNotBlank() && it != "null" },
            constraintMessage = o.optString("constraint_message").takeIf { it.isNotBlank() && it != "null" },
            regex = o.optString("regex").takeIf { it.isNotBlank() && it != "null" },
            minimum = if (o.has("minimum")) o.optDouble("minimum") else null,
            maximum = if (o.has("maximum")) o.optDouble("maximum") else null,
            fromOdk = o.optBoolean("from_odk", false), included = o.optBoolean("included", true)
        )
    }
}.getOrDefault(emptyList())

private fun encodeLooseRegionsState(regions: List<PaperDesignLooseRegion>): String = JSONArray().apply {
    regions.forEach { put(JSONObject().put("id", it.id).put("roi", roiStateJson(it.roi))) }
}.toString()

private fun decodeLooseRegionsState(raw: String): List<PaperDesignLooseRegion> = if (raw.isBlank()) emptyList() else runCatching {
    val a = JSONArray(raw); (0 until a.length()).map { index -> val o = a.getJSONObject(index); PaperDesignLooseRegion(o.getString("id"), roiFromStateJson(o.getJSONObject("roi"))) }
}.getOrDefault(emptyList())

private fun encodeOdkSchemaState(schema: PaperOdkSchema?): String = schema?.let {
    JSONObject().put("form_id", it.formId).put("title", it.title).put("version", it.version)
        .put("warnings", JSONArray(it.warnings))
        .put("fields", JSONArray().apply { it.fields.forEach { field ->
            put(JSONObject().apply {
                put("name", field.name); put("label", field.label); put("type", field.type.name); put("required", field.required)
                put("required_expression", field.requiredExpression ?: JSONObject.NULL)
                put("relevance_expression", field.relevanceExpression ?: JSONObject.NULL)
                put("constraint_expression", field.constraintExpression ?: JSONObject.NULL)
                put("constraint_message", field.constraintMessage ?: JSONObject.NULL)
                put("regex", field.regex ?: JSONObject.NULL); field.minimum?.let { put("minimum", it) }; field.maximum?.let { put("maximum", it) }
                put("options", JSONArray().apply { field.options.forEach { pair -> put(JSONArray().put(pair.first).put(pair.second)) } })
            })
        } }).toString()
}.orEmpty()

private fun decodeOdkSchemaState(raw: String): PaperOdkSchema? = if (raw.isBlank()) null else runCatching {
    val o = JSONObject(raw); val fields = o.getJSONArray("fields")
    PaperOdkSchema(
        formId = o.optString("form_id", "paper_form"), title = o.optString("title", "Paper form"), version = o.optString("version", "1"),
        fields = (0 until fields.length()).map { index -> val f = fields.getJSONObject(index); val opts = f.optJSONArray("options")
            PaperOdkField(
                name = f.getString("name"), label = f.optString("label", f.getString("name")), type = PaperFieldType.valueOf(f.getString("type")),
                required = f.optBoolean("required", false),
                requiredExpression = f.optString("required_expression").takeIf { it.isNotBlank() && it != "null" },
                relevanceExpression = f.optString("relevance_expression").takeIf { it.isNotBlank() && it != "null" },
                constraintExpression = f.optString("constraint_expression").takeIf { it.isNotBlank() && it != "null" },
                constraintMessage = f.optString("constraint_message").takeIf { it.isNotBlank() && it != "null" },
                options = if (opts == null) emptyList() else (0 until opts.length()).map { oi -> val pair = opts.getJSONArray(oi); pair.getString(0) to pair.getString(1) },
                regex = f.optString("regex").takeIf { it.isNotBlank() && it != "null" },
                minimum = if (f.has("minimum")) f.optDouble("minimum") else null, maximum = if (f.has("maximum")) f.optDouble("maximum") else null
            )
        },
        warnings = o.optJSONArray("warnings")?.let { a -> (0 until a.length()).map(a::getString) }.orEmpty()
    )
}.getOrNull()

private fun roiStateJson(roi: NormalisedRoi): JSONObject = JSONObject()
    .put("left", roi.left.toDouble()).put("top", roi.top.toDouble()).put("right", roi.right.toDouble()).put("bottom", roi.bottom.toDouble())

private fun roiFromStateJson(o: JSONObject): NormalisedRoi = NormalisedRoi(
    o.optDouble("left").toFloat(), o.optDouble("top").toFloat(), o.optDouble("right").toFloat(), o.optDouble("bottom").toFloat()
)
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
    override val description = "Build and manage portable Paper Bridge schemas from an XLSForm-style survey workbook and a registered blank template."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val app = LocalContext.current
        val automaticReturn = context.completionMode == CapabilityCompletionMode.AutomaticReturn
        val finishToLauncher = context.request.settings["methodmesh_finish_to_launcher"] == "true" ||
            context.request.settings["input_methodmesh_finish_to_launcher"] == "true"
        val presetResultAction = PresetResultAction.normalize(
            context.request.settings["methodmesh_preset_result_action"]
                ?: context.request.settings["input_methodmesh_preset_result_action"]
                ?: PresetResultAction.HOME
        )
        var libraryRevision by rememberSaveable { mutableStateOf(0) }
        var editorTemplateJson by rememberSaveable { mutableStateOf("") }
        var editorOpen by rememberSaveable { mutableStateOf(false) }
        var committedValuesJson by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
        var committedResult by remember { mutableStateOf<ExecutionResult?>(null) }
        var includeFullJson by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        var exportStatus by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
        val schemas = remember(libraryRevision) { PaperBridgeWorkspace.templates(app) }
        val editorTemplate = remember(editorTemplateJson) {
            editorTemplateJson.takeIf { it.isNotBlank() }?.let { runCatching { PaperTemplate.parse(it) }.getOrNull() }
        }

        fun valuesFromJson(raw: String): Map<String, String> = if (raw.isBlank()) emptyMap() else runCatching {
            val o = JSONObject(raw); buildMap { o.keys().forEach { key -> put(key, o.optString(key, "")) } }
        }.getOrDefault(emptyMap())

        val committedValues = remember(committedValuesJson) { valuesFromJson(committedValuesJson) }
        val restoredCommitted = remember(committedValuesJson) {
            if (committedValues.isEmpty()) null else {
                val request = As100PaperFormDesignMethod.request(
                    action = As100PaperFormDesignMethod.ID,
                    context = context.request.invocationContext.asMap(As100PaperFormDesignMethod.ID),
                    signals = emptyList(), inputs = emptyList()
                )
                As100PaperFormDesignMethod.result(request, committedValues, context.request.invocationContext)
            }
        }
        val frozenResult = committedResult ?: restoredCommitted
        val fullJson = remember(frozenResult?.request?.id?.value, committedValuesJson) {
            frozenResult?.let { OutputFormatter.format(it, ReturnMode.Json, includeProvenance = true, payloadMode = OutputFormatter.PayloadMode.FULL) }.orEmpty()
        }

        fun copy(label: String, value: String) {
            if (value.isBlank()) return
            app.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, value))
            Toast.makeText(app, "Copied $label", Toast.LENGTH_SHORT).show()
        }

        fun designSummary(): String = buildString {
            append(committedValues[PaperDesignFields.TEMPLATE_ID].orEmpty().ifBlank { "Paper Bridge form" })
            committedValues[PaperDesignFields.TEMPLATE_VERSION]?.takeIf { it.isNotBlank() }?.let { append(" v$it") }
            committedValues[PaperDesignFields.FIELD_COUNT]?.takeIf { it.isNotBlank() }?.let { append(" · $it fields") }
        }

        fun designAttachments(): List<PaperBridgeShareAttachment> = listOfNotNull(
            committedValues[PaperDesignFields.TEMPLATE_JSON_URI]?.takeIf { it.startsWith("content://") }
                ?.let { PaperBridgeShareAttachment("template.paperbridge.json", it) },
            committedValues[PaperDesignFields.TEMPLATE_YAML_URI]?.takeIf { it.startsWith("content://") }
                ?.let { PaperBridgeShareAttachment("template.paperbridge.yaml", it) },
            committedValues[PaperDesignFields.TEMPLATE_BUNDLE_URI]?.takeIf { it.startsWith("content://") }
                ?.let { PaperBridgeShareAttachment("template.paperbridge.zip", it) },
            committedValues[PaperDesignFields.TEMPLATE_IMAGE_URI]?.takeIf { it.startsWith("content://") }
                ?.let { PaperBridgeShareAttachment("template.png", it) },
            committedValues[PaperDesignFields.PREPARED_FORM_URI]?.takeIf { it.startsWith("content://") }
                ?.let { PaperBridgeShareAttachment("prepared_form.png", it) },
            committedValues[PaperDesignFields.MARKUP_IMAGE_URI]?.takeIf { it.startsWith("content://") }
                ?.let { PaperBridgeShareAttachment("mapping.png", it) }
        ).distinctBy { it.uri }

        fun shareCommitted() {
            runCatching {
                PaperBridgeResultActions.share(
                    context = app,
                    chooserTitle = "Share Paper Bridge design",
                    text = designSummary(),
                    attachments = designAttachments(),
                    jsonText = if (includeFullJson) fullJson else ""
                )
                exportStatus = "Sharing design and ${designAttachments().size} attachment${if (designAttachments().size == 1) "" else "s"}."
            }.onFailure { exportStatus = "Share failed: ${it.message ?: "no sharing app available"}" }
        }

        fun saveCommitted() {
            runCatching {
                PaperBridgeResultActions.saveToFiles(
                    context = app,
                    collectionLabel = committedValues[PaperDesignFields.TEMPLATE_ID].orEmpty().ifBlank { "design" },
                    textFileName = "design_summary.txt",
                    text = designSummary(),
                    attachments = designAttachments(),
                    jsonText = if (includeFullJson) fullJson else "",
                    entryId = frozenResult?.request?.id?.value
                )
            }.onSuccess { saved ->
                exportStatus = "Saved ${saved.fileCount} file${if (saved.fileCount == 1) "" else "s"} to MethodMesh Files."
            }.onFailure { exportStatus = "Save failed: ${it.message ?: "Files storage error"}" }
        }

        fun finishCommitted(result: ExecutionResult) {
            if (!context.isNativePresetRun || !context.isLastStep) { onConfirmed(result); return }
            when {
                presetResultAction == PresetResultAction.SAVE -> { saveCommitted(); onConfirmed(result) }
                finishToLauncher -> onConfirmed(result)
                else -> app.startActivity(Intent(app, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        }

        fun completeDesign(
            template: PaperTemplate,
            source: PaperDesignSource,
            export: PaperDesignExport,
            issueCounts: Pair<Int, Int>,
            odkCount: Int
        ) {
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
                PaperDesignFields.TEMPLATE_YAML_URI to export.templateYamlUri,
                PaperDesignFields.TEMPLATE_BUNDLE_URI to export.bundleUri,
                PaperDesignFields.TEMPLATE_IMAGE_URI to export.templateImageUri,
                PaperDesignFields.PREPARED_FORM_URI to export.preparedFormUri.orEmpty(),
                PaperDesignFields.MARKUP_IMAGE_URI to export.markupImageUri,
                PaperDesignFields.FIELD_COUNT to template.fields.size.toString(),
                PaperDesignFields.ODK_FIELD_COUNT to odkCount.toString(),
                PaperDesignFields.ERROR_COUNT to issueCounts.first.toString(),
                PaperDesignFields.WARNING_COUNT to issueCounts.second.toString(),
                PaperDesignFields.SOURCE_TYPE to source.mimeType,
                PaperDesignFields.ERROR to ""
            )
            val result = As100PaperFormDesignMethod.result(request, values, context.request.invocationContext)
            if (automaticReturn) {
                onConfirmed(result)
            } else {
                committedResult = result
                committedValuesJson = JSONObject(values).toString()
                includeFullJson = false
                exportStatus = "Committed. Share, Save or Done when ready."
                editorOpen = false
                editorTemplateJson = template.rawJson
            }
        }

        if (frozenResult != null && !automaticReturn) {
            Dialog(onDismissRequest = { finishCommitted(frozenResult) }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
                Column(
                    Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().background(MaterialTheme.colorScheme.background)
                        .verticalScroll(rememberScrollState()).padding(20.dp)
                ) {
                    Text("Paper Form Designer", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Committed design", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(14.dp))
                    TappableDesignValue(app, "Form ID", committedValues[PaperDesignFields.TEMPLATE_ID].orEmpty())
                    TappableDesignValue(app, "Version", committedValues[PaperDesignFields.TEMPLATE_VERSION].orEmpty())
                    TappableDesignValue(app, "Mapped fields", committedValues[PaperDesignFields.FIELD_COUNT].orEmpty())
                    TappableDesignValue(app, "ODK fields", committedValues[PaperDesignFields.ODK_FIELD_COUNT].orEmpty())
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(modifier = Modifier.weight(1f), onClick = ::shareCommitted) { Text("Share") }
                        Button(modifier = Modifier.weight(1f), onClick = ::saveCommitted) { Text("Save") }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = includeFullJson, onCheckedChange = { includeFullJson = it })
                        Column(Modifier.padding(start = 8.dp)) {
                            Text("Include full JSON / audit", style = MaterialTheme.typography.labelLarge)
                            Text("Adds structured execution metadata to Share and Save.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (exportStatus.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(exportStatus, style = MaterialTheme.typography.bodySmall) }
                    Spacer(Modifier.height(12.dp))
                    Button(modifier = Modifier.fillMaxWidth(), onClick = { finishCommitted(frozenResult) }) { Text("Done") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = {
                        committedResult = null; committedValuesJson = ""; includeFullJson = false; exportStatus = ""; editorOpen = true
                    }) { Text("Edit design") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { copy("Paper Bridge template JSON", committedValues[PaperDesignFields.TEMPLATE_JSON].orEmpty()) }) { Text("Copy template JSON") }
                }
            }
            return
        }

        if (editorOpen) {
            PaperFormDesignerSurface(
                initialTemplate = editorTemplate,
                onBack = {
                    editorOpen = false
                    editorTemplateJson = ""
                    libraryRevision += 1
                },
                onSaved = { template, source, export, issueCounts, odkCount ->
                    PaperBridgeWorkspace.saveAndActivateTemplate(app, template.rawJson)
                    libraryRevision += 1
                    completeDesign(template, source, export, issueCounts, odkCount)
                }
            )
            return
        }

        PaperFormSchemaLibrary(
            schemas = schemas,
            onOpen = { schema ->
                editorTemplateJson = schema.rawJson
                editorOpen = true
            },
            onNew = {
                editorTemplateJson = ""
                editorOpen = true
            },
            onDuplicate = { schema ->
                val duplicate = PaperBridgeWorkspace.duplicateTemplate(app, schema)
                libraryRevision += 1
                editorTemplateJson = duplicate.rawJson
                editorOpen = true
            },
            onDelete = { schema ->
                PaperBridgeWorkspace.removeTemplate(app, schema)
                libraryRevision += 1
            },
            onResetExample = {
                PaperBridgeWorkspace.resetExample(app)
                libraryRevision += 1
            },
            onClose = onCancel
        )
    }
}

@Composable
private fun PaperFormSchemaLibrary(
    schemas: List<PaperTemplate>,
    onOpen: (PaperTemplate) -> Unit,
    onNew: () -> Unit,
    onDuplicate: (PaperTemplate) -> Unit,
    onDelete: (PaperTemplate) -> Unit,
    onResetExample: () -> Unit,
    onClose: () -> Unit
) {
    val app = LocalContext.current
    val scroll = rememberScrollState()
    val shape = RoundedCornerShape(22.dp)
    var query by rememberSaveable { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<PaperTemplate?>(null) }
    val visible = remember(schemas, query) {
        val q = query.trim().lowercase()
        if (q.isBlank()) schemas else schemas.filter {
            it.title.lowercase().contains(q) || it.templateId.lowercase().contains(q)
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(scroll)
                .padding(20.dp)
        ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onClose) { Text("Back") }
            Column(Modifier.padding(start = 14.dp)) {
                Text("Paper Bridge", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Schemas", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            "Choose a schema to scan or edit. A schema is a named paper-layout mapping that can have several variants for the same survey.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search schemas") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = onNew, modifier = Modifier.fillMaxWidth()) { Text("New schema") }
        Spacer(Modifier.height(18.dp))

        visible.forEach { schema ->
            val example = PaperBridgeWorkspace.isBundled(schema)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = shape,
                colors = CardDefaults.cardColors(
                    containerColor = if (example) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.34f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(17.dp)) {
                    Text(
                        if (example) "EXAMPLE · LEARN PAPER BRIDGE" else "SCHEMA",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(schema.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        if (example)
                            "Needs setup · coloured teaching template + matching survey/choices workbook"
                        else "${schema.fields.size} mapped field(s) · ${schema.templateId} · v${schema.version}",
                        modifier = Modifier.padding(top = 3.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (example) {
                        Text(
                            "Open it, inspect the colour template, press AUTO DETECT, review guesses and queries, run the empty and filled-form tests, then Commit.",
                            modifier = Modifier.padding(top = 8.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { onOpen(schema) }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (example) "Open tutorial schema" else "Open schema")
                    }
                    Spacer(Modifier.height(7.dp))
                    OutlinedButton(onClick = { onDuplicate(schema) }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (example) "Duplicate as my schema" else "Duplicate")
                    }
                    if (example) {
                        OutlinedButton(onClick = onResetExample, modifier = Modifier.fillMaxWidth().padding(top = 7.dp)) {
                            Text("Reset example")
                        }
                    } else {
                        OutlinedButton(onClick = { deleteTarget = schema }, modifier = Modifier.fillMaxWidth().padding(top = 7.dp)) {
                            Text("Delete")
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        Text(
            "Schemas are stored as named Paper Bridge files. Participant responses are not stored in this library.",
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        }
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete schema?") },
            text = { Text("Delete ‘${target.title}’ from Paper Bridge? This removes the saved schema and its bound blank-form source. It does not delete completed ODK submissions.") },
            confirmButton = {
                TextButton(onClick = { onDelete(target); deleteTarget = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }

}

@Composable
private fun TappableDesignValue(context: Context, label: String, value: String) {
    if (value.isBlank()) return
    Column(
        modifier = Modifier.fillMaxWidth().clickable {
            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, value))
            Toast.makeText(context, "Copied $label", Toast.LENGTH_SHORT).show()
        }.padding(vertical = 6.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
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
    val builtInDemo = initialTemplate?.let(PaperBridgeBuiltInExamples::isDemo) == true
    val initialSource = remember(initialTemplate?.templateId, initialTemplate?.version, builtInDemo) {
        when {
            builtInDemo -> PaperBridgeBuiltInExamples.paperSource(app)
            initialTemplate != null -> PaperDesignSourceStore.sourceFor(app, initialTemplate)
            else -> null
        }
    }
    var title by rememberSaveable {
        mutableStateOf(
            when {
                builtInDemo -> "Example — working tutorial copy"
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
    val initialRegistration = remember(initialTemplate?.templateId, initialTemplate?.version) {
        initialTemplate?.registration ?: PaperAprilTagFiducial.defaultRegistration(PaperQrFiducial.newSchemaKey())
    }
    var registrationTypeName by rememberSaveable(initialTemplate?.templateId, initialTemplate?.version) {
        mutableStateOf(initialRegistration.type.name)
    }
    var registrationSchemaKey by rememberSaveable(initialTemplate?.templateId, initialTemplate?.version) {
        mutableStateOf(initialRegistration.schemaKey.ifBlank { PaperQrFiducial.newSchemaKey() })
    }
    var registrationMarkerFraction by rememberSaveable(initialTemplate?.templateId, initialTemplate?.version) {
        // Harden early QR4 schemas when they are next saved/prepared. Marker size does
        // not alter canonical ROI coordinates; it only makes the printed fiducial easier
        // to decode. Existing printed sheets remain readable because runtime uses the
        // detected QR centres rather than the configured marker diameter.
        mutableStateOf(
            if (initialRegistration.type == PaperRegistrationType.APRILTAG8) max(initialRegistration.markerSizeFraction, 0.065f)
            else if (initialRegistration.type == PaperRegistrationType.QR4) max(initialRegistration.markerSizeFraction, 0.075f)
            else initialRegistration.markerSizeFraction
        )
    }
    val registrationCentres = remember(initialTemplate?.templateId, initialTemplate?.version) { initialRegistration.centres }
    fun currentRegistration(): PaperRegistrationSpec {
        val type = runCatching { PaperRegistrationType.valueOf(registrationTypeName) }.getOrDefault(PaperRegistrationType.APRILTAG8)
        return when (type) {
            PaperRegistrationType.APRILTAG8 -> PaperAprilTagFiducial.defaultRegistration(registrationSchemaKey).copy(
                markerSizeFraction = registrationMarkerFraction,
                centres = if (registrationCentres.size == 8) registrationCentres else PaperAprilTagFiducial.defaultRegistration(registrationSchemaKey).centres
            )
            PaperRegistrationType.QR4 -> PaperQrFiducial.defaultRegistration(registrationSchemaKey).copy(
                markerSizeFraction = registrationMarkerFraction,
                centres = if (registrationCentres.size == 4) registrationCentres else PaperQrFiducial.defaultRegistration(registrationSchemaKey).centres
            )
            PaperRegistrationType.BULLSEYE4 -> PaperRegistrationSpec(type = PaperRegistrationType.BULLSEYE4, centres = PaperAnchorSpec().targets)
        }
    }
    var sourceStateJson by rememberSaveable(initialTemplate?.templateId, initialTemplate?.version) {
        mutableStateOf(designSourceStateJson(initialSource))
    }
    var source by remember(sourceStateJson) { mutableStateOf(designSourceFromStateJson(sourceStateJson)) }
    fun setSource(next: PaperDesignSource?) {
        source = next
        sourceStateJson = designSourceStateJson(next)
    }
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var sourceStatus by rememberSaveable {
        mutableStateOf(
            if (initialSource != null) "Loading ${initialSource.displayName}…"
            else "Choose the blank questionnaire PDF or image."
        )
    }
    var anchorsOk by rememberSaveable { mutableStateOf(false) }
    var anchorMessage by rememberSaveable { mutableStateOf("Anchors not checked yet.") }
    var odkSchemaStateJson by rememberSaveable(initialTemplate?.templateId, initialTemplate?.version) { mutableStateOf("") }
    var odkSchema by remember(odkSchemaStateJson) { mutableStateOf(decodeOdkSchemaState(odkSchemaStateJson)) }
    fun setOdkSchema(next: PaperOdkSchema?) {
        odkSchema = next
        odkSchemaStateJson = encodeOdkSchemaState(next)
    }
    var odkStatus by rememberSaveable {
        mutableStateOf(
            if (builtInDemo) "Loading the built-in matching survey workbook…"
            else "Import an Excel XLSForm-style workbook (survey + choices) to provide labels, types, constraints and choice keys."
        )
    }
    val initialFields = remember(initialTemplate?.templateId, initialTemplate?.version, builtInDemo) {
        if (builtInDemo) emptyList() else initialTemplate?.fields?.map(::designFieldFromTemplate).orEmpty()
    }
    var fieldsStateJson by rememberSaveable(initialTemplate?.templateId, initialTemplate?.version) { mutableStateOf(designFieldsStateJson(initialFields)) }
    var fields by remember(fieldsStateJson) { mutableStateOf(designFieldsFromStateJson(fieldsStateJson)) }
    fun setFields(next: List<PaperDesignFieldDraft>) {
        fields = next
        fieldsStateJson = designFieldsStateJson(next)
    }
    var selectedField by rememberSaveable { mutableStateOf(fields.firstOrNull()?.name.orEmpty()) }
    var selectedOption by rememberSaveable { mutableStateOf("") }
    var looseRegionsStateJson by rememberSaveable(initialTemplate?.templateId, initialTemplate?.version) { mutableStateOf("[]") }
    var looseRegions by remember(looseRegionsStateJson) { mutableStateOf(decodeLooseRegionsState(looseRegionsStateJson)) }
    fun setLooseRegions(next: List<PaperDesignLooseRegion>) {
        looseRegions = next
        looseRegionsStateJson = encodeLooseRegionsState(next)
    }
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
    var testLines by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var saving by rememberSaveable { mutableStateOf(false) }
    var savedDesignSignature by rememberSaveable { mutableStateOf<String?>(null) }
    var blankTestSignature by rememberSaveable { mutableStateOf<String?>(null) }
    var blankTestPassed by rememberSaveable { mutableStateOf(false) }
    var dataTestSignature by rememberSaveable { mutableStateOf<String?>(null) }
    var dataTestCompleted by rememberSaveable { mutableStateOf(false) }
    var lastPreparedFormUri by rememberSaveable { mutableStateOf("") }
    var pageSize by remember { mutableStateOf(IntSize.Zero) }
    var showSetup by rememberSaveable { mutableStateOf(false) }
    var boxDetectionStatus by rememberSaveable { mutableStateOf("Waiting for a registered blank form.") }
    var colourTemplateStatus by rememberSaveable { mutableStateOf("Colour authoring: waiting for paper + survey workbook.") }
    var colourTemplateRunning by rememberSaveable { mutableStateOf(false) }
    var colourAutoMappedKey by rememberSaveable { mutableStateOf("") }
    var colourQuestionOverlays by remember { mutableStateOf<List<PaperColourTemplateEngine.QuestionProposal>>(emptyList()) }

    fun loadSource(imported: PaperDesignSource) {
        setSource(imported)
        sourceStatus = "Loading ${imported.displayName}…"
        anchorsOk = false
        anchorMessage = when {
            imported.alreadyRegistered -> "Restoring canonical registration-centre canvas…"
            currentRegistration().type == PaperRegistrationType.APRILTAG8 -> "Preparing/detecting eight AprilTag36h11 registration markers…"
            currentRegistration().type == PaperRegistrationType.QR4 -> "Preparing/detecting four legacy schema QR fiducials…"
            else -> "Checking four legacy registration targets…"
        }
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { renderDesignSource(imported) } }
                .onFailure { sourceStatus = "Could not load blank form: ${it.message ?: "unsupported file"}" }
                .onSuccess { bitmap ->
                    suspend fun installCanonical(canonical: Bitmap, registrationMessage: String) {
                        val previous = sourceBitmap
                        sourceBitmap = canonical
                        if (previous != null && previous !== canonical && !previous.isRecycled) previous.recycle()
                        sourceStatus = "${imported.displayName} · ${canonical.width}×${canonical.height} canonical canvas"
                        anchorsOk = true
                        anchorMessage = registrationMessage
                        colourQuestionOverlays = emptyList()

                        // Tutorial schemas deliberately wait for the operator to press AUTO so
                        // the colour/geometry inference is visible as a learning step. Other
                        // schemas may still recover neutral boxes automatically on load.
                        if (builtInDemo) {
                            setLooseRegions(emptyList())
                            selectedRegionId = ""
                            boxDetectionStatus = "Tutorial ready · press AUTO to detect response boxes and propose workbook linkages."
                        } else {
                            val detections = runCatching {
                                withContext(Dispatchers.Default) { PaperBoxDetectionEngine.detect(canonical, neutralOnly = true) }
                            }.getOrElse { emptyList() }
                            if (detections.isNotEmpty()) {
                                val merged = mergeDetectedPaperBoxes(fields, detections)
                                setFields(merged.fields)
                                setLooseRegions(merged.looseRegions)
                                selectedRegionId = merged.looseRegions.firstOrNull()?.id
                                    ?: designRegionViews(merged.fields, emptyList()).firstOrNull()?.id.orEmpty()
                                boxDetectionStatus = buildString {
                                    append(detections.size)
                                    append(" printed response box")
                                    if (detections.size != 1) append("es")
                                    append(" detected")
                                    if (merged.looseRegions.isNotEmpty()) append(" · ${merged.looseRegions.size} ready to link")
                                }
                            } else {
                                boxDetectionStatus = "No printed response rectangles were detected automatically. Use + BOX for exceptional regions."
                            }
                        }
                    }

                    if (imported.alreadyRegistered) {
                        // A portable Paper Bridge bundle stores the exact canonical
                        // registration-centre canvas. It must not be registered twice.
                        val canonical = initialTemplate?.let { template ->
                            val width = template.pageWidthPx.coerceIn(600, 4000)
                            val height = template.pageHeightPx.coerceIn(600, 6000)
                            if (bitmap.width == width && bitmap.height == height) bitmap
                            else Bitmap.createScaledBitmap(bitmap, width, height, true).also {
                                if (it !== bitmap && !bitmap.isRecycled) bitmap.recycle()
                            }
                        } ?: bitmap
                        installCanonical(canonical, "Canonical registration-centre canvas restored from Paper Bridge bundle · no second registration applied")
                    } else if (currentRegistration().type == PaperRegistrationType.APRILTAG8) {
                        val registration = currentRegistration()
                        val targetWidth = initialTemplate?.pageWidthPx?.coerceIn(600, 4000) ?: bitmap.width.coerceIn(600, 4000)
                        val targetHeight = initialTemplate?.pageHeightPx?.coerceIn(600, 6000) ?: bitmap.height.coerceIn(600, 6000)
                        val existing = withContext(Dispatchers.Default) { PaperAprilTagFiducial.hasExpectedMarkers(bitmap, registration) }
                        if (existing) {
                            val tempTemplateJson = JSONObject()
                                .put("schema", "methodmesh.paper.v1")
                                .put("template_id", templateId)
                                .put("version", version)
                                .put("title", title)
                                .put("coordinate_frame", "page")
                                .put("page", JSONObject().put("width_px", targetWidth).put("height_px", targetHeight))
                                .put("registration", registrationJson(registration))
                                .put("fields", JSONArray().put(JSONObject()
                                    .put("name", "_tag_registration_probe")
                                    .put("label", "probe")
                                    .put("type", "ocr_text")
                                    .put("roi", JSONArray().put(0.45).put(0.45).put(0.55).put(0.55))))
                                .toString()
                            val probeTemplate = PaperTemplate.parse(tempTemplateJson)
                            val registered = withContext(Dispatchers.Default) { PaperAprilTagFiducial.rectify(bitmap, probeTemplate, mlKitPreprocessed = true) }
                            if (registered.bitmap !== bitmap && !bitmap.isRecycled) bitmap.recycle()
                            installCanonical(registered.bitmap, "APRILTAG8 detected · ${registered.detectedMarkerCount}/8 tags · RMS ${registered.reprojectionRmsPx?.let { String.format("%.1f", it) } ?: "–"} px")
                        } else {
                            // APRILTAG8 preserves the canonical artwork 1:1. The printable export adds
                            // a dedicated registration border outside that artwork.
                            installCanonical(bitmap, "APRILTAG8 will be added to the printable form · eight unique perimeter tags · canonical artwork preserved 1:1")
                        }
                    } else if (currentRegistration().type == PaperRegistrationType.QR4) {
                        val registration = currentRegistration()
                        val targetWidth = initialTemplate?.pageWidthPx?.coerceIn(600, 4000) ?: bitmap.width.coerceIn(600, 4000)
                        val targetHeight = initialTemplate?.pageHeightPx?.coerceIn(600, 6000) ?: bitmap.height.coerceIn(600, 6000)
                        val existing = runCatching { PaperQrFiducial.hasExpectedMarkers(bitmap, registration) }.getOrDefault(false)
                        if (existing) {
                            val tempTemplateJson = JSONObject()
                                .put("schema", "methodmesh.paper.v1")
                                .put("template_id", templateId)
                                .put("version", version)
                                .put("title", title)
                                .put("coordinate_frame", "anchor_centres")
                                .put("page", JSONObject().put("width_px", targetWidth).put("height_px", targetHeight))
                                .put("registration", registrationJson(registration))
                                .put("fields", JSONArray().put(JSONObject()
                                    .put("name", "_qr_registration_probe")
                                    .put("label", "probe")
                                    .put("type", "ocr_text")
                                    .put("roi", JSONArray().put(0.45).put(0.45).put(0.55).put(0.55))))
                                .toString()
                            // The probe field name starts with underscore and is safe; it is
                            // used only to provide dimensions/registration to the rectifier.
                            val probeTemplate = PaperTemplate.parse(tempTemplateJson)
                            val registered = PaperQrFiducial.rectify(bitmap, probeTemplate, mlKitPreprocessed = true)
                            if (registered.bitmap !== bitmap && !bitmap.isRecycled) bitmap.recycle()
                            installCanonical(registered.bitmap, "QR4 fiducials detected · schema ${registration.schemaKey} · centres define the analysis frame")
                        } else {
                            // New Paper Bridge schemas do not require the author to draw
                            // fiducials. Preserve the imported artwork as the canonical
                            // content, generate a printable QR4 page around it, and recover
                            // the exact centre-to-centre canvas for mapping.
                            val prepared = withContext(Dispatchers.Default) { PaperQrFiducial.preparePage(bitmap, registration) }
                            val canonical = withContext(Dispatchers.Default) {
                                PaperQrFiducial.canonicalFromPrepared(prepared, registration, targetWidth, targetHeight)
                            }
                            if (!prepared.isRecycled) prepared.recycle()
                            if (canonical !== bitmap && !bitmap.isRecycled) bitmap.recycle()
                            installCanonical(canonical, "QR4 fiducials added automatically · schema ${registration.schemaKey} · prepared printable form will be saved with the schema")
                        }
                    } else {
                        // Legacy bullseye schemas remain supported. Their target centres
                        // still define the canonical coordinate frame.
                        val check = runCatching {
                            withContext(Dispatchers.Default) {
                                PaperImageEngine.cropFlatDesignToAnchorCentres(bitmap, PaperAnchorSpec())
                            }
                        }
                        check.onFailure { error ->
                            val previous = sourceBitmap
                            sourceBitmap = bitmap
                            if (previous != null && previous !== bitmap && !previous.isRecycled) previous.recycle()
                            sourceStatus = "${imported.displayName} · legacy targets not registered"
                            anchorsOk = false
                            anchorMessage = "Legacy registration targets not ready: ${error.message ?: "four targets not detected"}"
                        }.onSuccess { registered ->
                            val cropped = registered.bitmap
                            val canonical = initialTemplate?.let { template ->
                                val width = template.pageWidthPx.coerceIn(600, 4000)
                                val height = template.pageHeightPx.coerceIn(600, 6000)
                                if (cropped.width == width && cropped.height == height) cropped
                                else Bitmap.createScaledBitmap(cropped, width, height, true).also {
                                    if (it !== cropped && !cropped.isRecycled) cropped.recycle()
                                }
                            } ?: cropped
                            if (canonical !== bitmap && !bitmap.isRecycled) bitmap.recycle()
                            val scores = registered.anchors.joinToString(" · ") { "${it.corner} ${(it.score * 100).roundToInt()}%" }
                            installCanonical(canonical, "Legacy bullseye targets detected · $scores · canvas cropped centre-to-centre")
                        }
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
                setOdkSchema(schema)
                // The built-in example is a staged tutorial, not a pre-completed mapping.
                // Load semantics from the workbook but leave every ROI unresolved until AUTO.
                setFields(schema.fields.map(::designFieldFromOdk))
                odkStatus = "Built-in XLSForm loaded · ${schema.fields.size} compatible survey fields." +
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
            odkStatus = "Reading survey/choices workbook…"
            runCatching { withContext(Dispatchers.IO) { PaperXlsFormReader.read(app, uri) } }
                .onFailure { odkStatus = "Workbook not imported: ${it.message ?: "invalid survey/choices workbook"}" }
                .onSuccess { schema ->
                    val preservedBoxes = designRegionViews(fields, looseRegions).map { region ->
                        PaperDesignLooseRegion("loose:${UUID.randomUUID()}", region.roi)
                    }
                    setOdkSchema(schema)
                    title = schema.title
                    templateId = safeTemplateId(schema.formId)
                    version = schema.version
                    setFields(schema.fields.map(::designFieldFromOdk))
                    setLooseRegions(preservedBoxes)
                    selectedField = fields.firstOrNull()?.name.orEmpty()
                    selectedOption = ""
                    selectedRegionId = preservedBoxes.firstOrNull()?.id.orEmpty()
                    linkFieldName = ""
                    linkOptionValue = ""
                    odkStatus = "${schema.fields.size} compatible survey fields loaded. Existing boxes were kept and unlinked for remapping" +
                        if (schema.warnings.isEmpty()) "." else " · ${schema.warnings.size} import warning${if (schema.warnings.size == 1) "" else "s"}."
                    boxDetectionStatus = if (preservedBoxes.isEmpty()) {
                        "survey schema loaded. Open/register the blank paper form to auto-detect its response boxes."
                    } else {
                        "${preservedBoxes.size} paper boxes ready to link to the imported survey schema."
                    }
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
    val currentDesignSignature = remember(templateId, version, title, sourceBitmap, fields) {
        val bitmap = sourceBitmap
        if (bitmap == null) null else runCatching {
            buildPaperManifest(templateId, version, title, bitmap.width, bitmap.height, fields, currentRegistration())
        }.getOrNull()
    }
    val schemaSaved = canBuild && currentDesignSignature != null && currentDesignSignature == savedDesignSignature
    val blankTestPassedCurrent = schemaSaved && blankTestPassed && blankTestSignature == currentDesignSignature
    val dataTestCompletedCurrent = blankTestPassedCurrent && dataTestCompleted && dataTestSignature == currentDesignSignature
    val canCommit = dataTestCompletedCurrent && !saving

    var pendingTestMode by rememberSaveable { mutableStateOf("data") }

    fun runDesignerTest(uri: Uri, acquisitionMode: String, mode: String = pendingTestMode) {
        val bitmap = sourceBitmap ?: return
        val manifest = runCatching { buildPaperManifest(templateId, version, title, bitmap.width, bitmap.height, fields, currentRegistration()) }.getOrNull()
            ?: return
        scope.launch {
            testStatus = if (mode == "blank") "Testing blank production form…" else "Testing completed production form…"
            testLines = emptyList()
            runCatching {
                val stable = withContext(Dispatchers.IO) { PaperBridgeFiles.copyIntoCache(app, uri, "designer-test") }
                PaperExtractionEngine.extract(
                    context = app,
                    sourceUri = stable,
                    template = PaperTemplate.parse(manifest),
                    settings = PaperExtractionSettings(autoAcceptOmr = true, autoAcceptOcr = false),
                    manualQuarterTurns = 0,
                    scanTimeIso = java.time.Instant.now().toString(),
                    acquisitionMode = acquisitionMode
                )
            }.onFailure { testStatus = "Test failed: ${it.message ?: "could not read completed page"}" }
                .onSuccess { session ->
                    val registrationLine = designerRegistrationTestLine(session)
                    if (mode == "blank") {
                        /*
                         * TEST EMPTY establishes that the prepared production form itself does
                         * not trigger a response. Static print inside a BARCODE ROI is legitimate
                         * (for example "Affix barcode fully inside this box") and ML Kit text
                         * fallback may OCR it even though no barcode exists. Only an actually
                         * decoded barcode is a barcode false positive. IMAGE fields are likewise
                         * allowed to contain the printed blank box/background because their
                         * runtime value is the changed/captured region, not the existence of the
                         * rectangle itself.
                         */
                        val falsePositives = session.results.filter { result ->
                            when (result.spec.type) {
                                PaperFieldType.IMAGE -> false
                                PaperFieldType.BARCODE -> result.reason.startsWith("Single barcode decoded") ||
                                    result.reason.startsWith("Multiple barcodes were decoded")
                                PaperFieldType.OMR_SINGLE, PaperFieldType.OMR_MULTIPLE -> {
                                    // TEST EMPTY is a validation/calibration pass. Screen capture,
                                    // resampling and paper texture can push an unmarked box just over
                                    // the operational threshold. Only treat a blank OMR candidate as
                                    // a failure when its selected box has strong mark evidence.
                                    val selected = result.candidate
                                        .split(result.spec.separator)
                                        .map(String::trim)
                                        .filter(String::isNotBlank)
                                    val strongest = selected.mapNotNull { result.scores[it] }.maxOrNull() ?: 0f
                                    val strongBlankCutoff = max(0.28f, result.spec.threshold + 0.08f)
                                    selected.isNotEmpty() && strongest >= strongBlankCutoff
                                }
                                else -> result.candidate.isNotBlank()
                            }
                        }
                        val baselineNotes = session.results.mapNotNull { result ->
                            when {
                                result.spec.type == PaperFieldType.BARCODE &&
                                    result.candidate.isNotBlank() &&
                                    result !in falsePositives ->
                                    "${result.spec.name}: static printed content seen in blank barcode ROI · ignored for TEST EMPTY"
                                result.spec.type == PaperFieldType.IMAGE ->
                                    "${result.spec.name}: blank image ROI registered as baseline geometry"
                                result.spec.type in setOf(PaperFieldType.OMR_SINGLE, PaperFieldType.OMR_MULTIPLE) &&
                                    result.candidate.isNotBlank() && result !in falsePositives ->
                                    "${result.spec.name}: weak OMR activity on blank form · treated as scan/display baseline"
                                else -> null
                            }
                        }
                        blankTestSignature = manifest
                        blankTestPassed = falsePositives.isEmpty()
                        if (!blankTestPassed) {
                            dataTestSignature = null
                            dataTestCompleted = false
                        }
                        testStatus = if (blankTestPassed) {
                            "$registrationLine · Blank test passed ✓ · no response data detected."
                        } else {
                            "$registrationLine · Blank test found ${falsePositives.size} possible false positive${if (falsePositives.size == 1) "" else "s"}."
                        }
                        testLines = listOf(registrationLine) + baselineNotes + session.results.map { result ->
                            val shown = when {
                                result.spec.type == PaperFieldType.IMAGE -> "blank ROI"
                                result in falsePositives && result.candidate.isNotBlank() -> result.candidate
                                result.spec.type == PaperFieldType.BARCODE && result.candidate.isNotBlank() -> "static print ignored"
                                result.spec.type in setOf(PaperFieldType.OMR_SINGLE, PaperFieldType.OMR_MULTIPLE) &&
                                    result.candidate.isNotBlank() && result !in falsePositives -> "weak baseline (${result.candidate})"
                                result.candidate.isNotBlank() -> result.candidate
                                else -> "—"
                            }
                            "${result.spec.name}: $shown · ${if (result in falsePositives) "FALSE POSITIVE" else "blank baseline"}"
                        }
                    } else {
                        val unresolved = session.results.count { !it.resolved }
                        val barcodeFields = session.results.filter { it.spec.type == PaperFieldType.BARCODE }
                        val imageFields = session.results.filter { it.spec.type == PaperFieldType.IMAGE }
                        val barcodeSummary = when {
                            barcodeFields.isEmpty() -> ""
                            barcodeFields.all { it.candidate.isNotBlank() } -> " · barcode decoded"
                            else -> " · barcode needs review"
                        }
                        val imageSummary = when {
                            imageFields.isEmpty() -> ""
                            imageFields.any { it.finalValue.isNotBlank() } -> " · image ROI attached"
                            else -> " · image ROI blank"
                        }
                        dataTestSignature = manifest
                        dataTestCompleted = true
                        testStatus = if (unresolved == 0) {
                            "Data test passed: all ${session.results.size} fields resolved$barcodeSummary$imageSummary."
                        } else {
                            "Data test completed: $unresolved field${if (unresolved == 1) "" else "s"} would be sent to review$barcodeSummary$imageSummary."
                        }
                    }
                    if (mode != "blank") {
                        testLines = listOf(registrationLine) + session.results.map { result ->
                            val shown = when {
                                result.spec.type == PaperFieldType.IMAGE && result.finalValue.isNotBlank() -> "[image attachment]"
                                result.finalValue.isNotBlank() -> result.finalValue
                                result.candidate.isNotBlank() -> result.candidate
                                else -> "—"
                            }
                            "${result.spec.name}: $shown · ${if (result.resolved) "ready" else "review"}"
                        }
                    }
                }
        }
    }

    val testPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        runDesignerTest(uri, acquisitionMode = "direct")
    }

    val testScannerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { activityResult ->
        if (activityResult.resultCode != Activity.RESULT_OK) {
            testStatus = "Schema test scan cancelled."
            return@rememberLauncherForActivityResult
        }
        val result = activityResult.data?.let { GmsDocumentScanningResult.fromActivityResultIntent(it) }
        val page = result?.pages?.firstOrNull()
        if (page == null) {
            testStatus = "ML Kit returned no page for the schema test."
        } else {
            runDesignerTest(page.imageUri, acquisitionMode = "mlkit_document_scanner")
        }
    }

    fun startDesignerScanTest(mode: String) {
        if (!schemaSaved) {
            testStatus = "Save the current schema before testing it."
            return
        }
        if (mode == "data" && !blankTestPassedCurrent) {
            testStatus = "Pass TEST EMPTY for the current saved schema before testing with data."
            return
        }
        if (mode == "blank") {
            blankTestPassed = false
            blankTestSignature = null
            dataTestCompleted = false
            dataTestSignature = null
        } else {
            dataTestCompleted = false
            dataTestSignature = null
        }
        val activity = app.findDesignerActivity()
        if (activity == null) {
            testStatus = "No Android activity is available to launch ML Kit. Choose an existing test image instead."
            pendingTestMode = mode
            testPicker.launch("image/*")
            return
        }
        pendingTestMode = mode
        testStatus = if (mode == "blank") "Opening ML Kit for blank-form test…" else "Opening ML Kit for completed-form test…"
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(1)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(activity)
            .addOnSuccessListener { sender -> testScannerLauncher.launch(IntentSenderRequest.Builder(sender).build()) }
            .addOnFailureListener { error ->
                testStatus = "Could not open ML Kit scanner: ${error.message.orEmpty()}"
            }
    }

    fun updateField(name: String, transform: (PaperDesignFieldDraft) -> PaperDesignFieldDraft) {
        setFields(fields.map { if (it.name == name) transform(it) else it })
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
        else setLooseRegions(looseRegions.map { if (it.id == id) it.copy(roi = roi) else it })
    }

    fun placeLooseRegion(roi: NormalisedRoi) {
        val region = PaperDesignLooseRegion("loose:${UUID.randomUUID()}", roi)
        setLooseRegions(looseRegions + region)
        selectedRegionId = region.id
        linkFieldName = ""
        linkOptionValue = ""
    }

    fun unlinkSelectedRegion() {
        val view = designRegionViews(fields, looseRegions).firstOrNull { it.id == selectedRegionId } ?: return
        val target = view.target ?: return
        setTargetRoi(target, null)
        val loose = PaperDesignLooseRegion("loose:${UUID.randomUUID()}", view.roi)
        setLooseRegions(looseRegions + loose)
        selectedRegionId = loose.id
        linkFieldName = target.fieldName
        linkOptionValue = target.optionValue.orEmpty()
    }

    fun deleteSelectedRegion() {
        val view = designRegionViews(fields, looseRegions).firstOrNull { it.id == selectedRegionId } ?: return
        if (view.target != null) setTargetRoi(view.target, null)
        else setLooseRegions(looseRegions.filterNot { it.id == view.id })
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
        setLooseRegions(looseRegions.filterNot { it.id == view.id })
        selectedRegionId = targetRegionId(target)
    }

    fun redetectPaperBoxes() {
        val bitmap = sourceBitmap ?: return
        scope.launch {
            boxDetectionStatus = "Detecting printed response boxes…"
            val detections = runCatching {
                withContext(Dispatchers.Default) { PaperBoxDetectionEngine.detect(bitmap, neutralOnly = true) }
            }.getOrElse { error ->
                boxDetectionStatus = "Box detection failed: ${error.message ?: "image analysis error"}"
                return@launch
            }
            if (detections.isEmpty()) {
                boxDetectionStatus = "No printed response rectangles detected. Existing linked boxes were left unchanged."
                return@launch
            }
            val merged = mergeDetectedPaperBoxes(fields, detections)
            setFields(merged.fields)
            setLooseRegions(merged.looseRegions)
            selectedRegionId = merged.looseRegions.firstOrNull()?.id
                ?: designRegionViews(merged.fields, emptyList()).firstOrNull()?.id.orEmpty()
            linkFieldName = ""
            linkOptionValue = ""
            boxDetectionStatus = "${detections.size} printed response boxes detected · ${merged.snappedCount} existing mappings preserved · ${merged.looseRegions.size} unlinked."
        }
    }

    fun applyColourTemplateAnalysis() {
        val bitmap = sourceBitmap ?: run {
            colourTemplateStatus = "Load/register the colour authoring template first."
            return
        }
        val schema = odkSchema ?: run {
            colourTemplateStatus = "Import the survey/choices workbook first."
            return
        }
        if (colourTemplateRunning) return
        colourTemplateRunning = true
        colourTemplateStatus = "Reading colour envelopes, question text and answer labels…"
        scope.launch {
            val analysis = runCatching {
                withContext(Dispatchers.Default) { PaperColourTemplateEngine.analyse(bitmap, schema) }
            }.getOrElse { error ->
                colourTemplateRunning = false
                colourTemplateStatus = "Colour template analysis failed: ${error.message ?: "recognition error"}"
                return@launch
            }

            colourQuestionOverlays = analysis.questions
            var mappedQuestions = 0
            var mappedOptions = 0
            val consumed = mutableListOf<NormalisedRoi>()
            val byName = analysis.questions.filter { it.fieldName != null }.associateBy { it.fieldName!! }
            setFields(fields.map { field ->
                val proposal = byName[field.name] ?: return@map field
                when (proposal.hint) {
                    PaperColourTemplateEngine.Hint.SELECT_ONE,
                    PaperColourTemplateEngine.Hint.SELECT_MULTIPLE -> {
                        val optionByValue = proposal.options.filter { it.choiceValue != null && it.responseRoi != null }
                            .associateBy { it.choiceValue!! }
                        if (optionByValue.isEmpty()) return@map field
                        mappedQuestions++
                        val updated = field.options.map { option ->
                            val hit = optionByValue[option.value]
                            if (hit?.responseRoi != null) {
                                mappedOptions++
                                consumed += hit.responseRoi
                                option.copy(roi = hit.responseRoi)
                            } else option
                        }
                        field.copy(options = updated)
                    }
                    PaperColourTemplateEngine.Hint.SCALAR,
                    PaperColourTemplateEngine.Hint.BARCODE,
                    PaperColourTemplateEngine.Hint.IMAGE -> {
                        val roi = proposal.responseRois.maxByOrNull { roiArea(it) } ?: return@map field
                        mappedQuestions++
                        consumed += roi
                        field.copy(roi = roi)
                    }
                }
            })
            val allResponseRois = analysis.questions.flatMap { proposal ->
                if (proposal.hint == PaperColourTemplateEngine.Hint.SELECT_ONE ||
                    proposal.hint == PaperColourTemplateEngine.Hint.SELECT_MULTIPLE) {
                    proposal.options.mapNotNull { it.responseRoi }
                } else proposal.responseRois
            }
            val unresolvedRois = allResponseRois.filter { roi ->
                consumed.none { mapped -> overlapFraction(roi, mapped) > 0.70f }
            }
            val preservedLoose = looseRegions.filter { loose ->
                consumed.none { mapped -> overlapFraction(loose.roi, mapped) > 0.70f }
            }
            val newLoose = unresolvedRois.filter { roi ->
                preservedLoose.none { existing -> overlapFraction(existing.roi, roi) > 0.80f }
            }.map { roi -> PaperDesignLooseRegion("colour:${UUID.randomUUID()}", roi) }
            setLooseRegions(preservedLoose + newLoose)
            val unmatched = analysis.questions.count { it.fieldName == null }
            colourTemplateStatus = buildString {
                append("${analysis.detectedQuestionCount} colour question envelope")
                if (analysis.detectedQuestionCount != 1) append("s")
                append(" · $mappedQuestions question")
                if (mappedQuestions != 1) append("s")
                append(" matched from printed labels")
                if (mappedOptions > 0) append(" · $mappedOptions choice box${if (mappedOptions == 1) "" else "es"} wired from blue answer labels")
                if (unmatched > 0) append(" · $unmatched need manual checking")
            }
            if (analysis.warnings.isNotEmpty()) colourTemplateStatus += " · ${analysis.warnings.size} warning${if (analysis.warnings.size == 1) "" else "s"}"
            colourTemplateRunning = false
            selectedRegionId = designRegionViews(fields, looseRegions).firstOrNull()?.id.orEmpty()
        }
    }

    fun saveCurrentDesign(commit: Boolean = false) {
        val bitmap = sourceBitmap ?: return
        val currentSource = source ?: return
        if (!canBuild || saving) return
        if (commit && !canCommit) {
            testStatus = "Save the schema, pass TEST EMPTY, and complete TEST DATA before committing."
            return
        }
        saving = true
        val json = buildPaperManifest(templateId, version, title, bitmap.width, bitmap.height, fields, currentRegistration())
        runCatching {
            val template = PaperBridgeWorkspace.saveAndActivateTemplate(app, json)
            PaperDesignSourceStore.bind(app, template, currentSource)
            val export = PaperDesignExportStore.save(app, template, bitmap)
            template to export
        }.onFailure {
            saving = false
            testStatus = "Save failed: ${it.message ?: "invalid design"}"
        }.onSuccess { (saved, export) ->
            saving = false
            savedDesignSignature = json
            lastPreparedFormUri = export.preparedFormUri.orEmpty()
            if (commit) {
                onSaved(saved, currentSource, export, errors.size to warnings.size, odkSchema?.fields?.size ?: 0)
            } else {
                testStatus = when (saved.registration.type) {
                    PaperRegistrationType.APRILTAG8 -> "Schema saved. AprilTag8 prepared printable form generated. Run TEST EMPTY using the prepared form next."
                    PaperRegistrationType.QR4 -> "Schema saved. Legacy QR4 prepared printable form generated (${saved.registration.schemaKey}). Run TEST EMPTY using the prepared form next."
                    PaperRegistrationType.BULLSEYE4 -> "Schema saved. Run TEST EMPTY next."
                }
            }
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
                            "$title · ${if (anchorsOk) { if (currentRegistration().type == PaperRegistrationType.APRILTAG8) "AprilTag8 ready" else if (currentRegistration().type == PaperRegistrationType.QR4) "QR4 ready" else "anchors ready" } else "check registration"}",
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
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { saveCurrentDesign(false) },
                        enabled = canBuild && !schemaSaved && !saving,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                    ) { Text(if (saving) "SAVING…" else if (schemaSaved) "✓ SAVED" else "SAVE SCHEMA", style = MaterialTheme.typography.labelSmall) }
                    OutlinedButton(
                        onClick = { startDesignerScanTest("blank") },
                        enabled = schemaSaved,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                    ) { Text(if (blankTestPassedCurrent) "✓ EMPTY" else "TEST EMPTY", style = MaterialTheme.typography.labelSmall) }
                    OutlinedButton(
                        onClick = { startDesignerScanTest("data") },
                        enabled = blankTestPassedCurrent,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                    ) { Text(if (dataTestCompletedCurrent) "✓ DATA" else "TEST DATA", style = MaterialTheme.typography.labelSmall) }
                    Button(
                        onClick = { saveCurrentDesign(true) },
                        enabled = canCommit,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                    ) { Text("COMMIT", style = MaterialTheme.typography.labelSmall) }
                }

                if (testStatus.isNotBlank()) {
                    val lowerStatus = testStatus.lowercase()
                    val testAlert = lowerStatus.contains("failed") ||
                        lowerStatus.contains("false positive") ||
                        lowerStatus.contains("could not") ||
                        lowerStatus.contains("cancelled")
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 3.dp),
                        shape = MaterialTheme.shapes.small,
                        color = if (testAlert) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                        tonalElevation = 1.dp
                    ) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp)) {
                            Text(
                                testStatus,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (testAlert) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            val importantLines = testLines.filter {
                                it.contains("FALSE POSITIVE", ignoreCase = true) ||
                                    it.startsWith("APRILTAG8") || it.startsWith("QR4") || it.startsWith("BULLSEYE4")
                            }.distinct().take(3)
                            importantLines.forEach { line ->
                                Text(
                                    line,
                                    modifier = Modifier.padding(top = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (testAlert) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
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
                            questionOverlays = colourQuestionOverlays,
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
                            onRegionChanged = ::updateRegion,
                            onDetectBoxes = {
                                if (odkSchema != null) applyColourTemplateAnalysis() else redetectPaperBoxes()
                            }
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
                                    Text(if (odkSchema == null) "Import survey workbook" else "Replace survey workbook")
                                }
                            }
                            Text(sourceStatus, modifier = Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall)
                            Text(
                                anchorMessage,
                                modifier = Modifier.padding(top = 3.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (anchorsOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                            if (currentRegistration().type == PaperRegistrationType.APRILTAG8) {
                                Text(
                                    "Registration: APRILTAG8 · tag36h11 IDs 0–7 · eight perimeter markers contribute up to 32 geometric correspondences; ROIs are read only after robust canonical rectification.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            } else if (currentRegistration().type == PaperRegistrationType.QR4) {
                                Text(
                                    "Registration: legacy QR4 · schema key ${currentRegistration().schemaKey} · four QR centres define the runtime ROI frame.",
                                    modifier = Modifier.padding(top = 3.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (lastPreparedFormUri.isNotBlank()) {
                                    OutlinedButton(
                                        onClick = {
                                            runCatching {
                                                app.startActivity(Intent(Intent.ACTION_VIEW).apply {
                                                    setDataAndType(Uri.parse(lastPreparedFormUri), "image/png")
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                })
                                            }.onFailure { testStatus = "Could not open prepared form: ${it.message ?: "no compatible viewer"}" }
                                        },
                                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                                    ) { Text("Open printable QR-prepared form") }
                                }
                            } else {
                                Text(
                                    "Registration: legacy bullseye4 · retained for compatibility with older schemas.",
                                    modifier = Modifier.padding(top = 3.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(odkStatus, modifier = Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall)
                            odkSchema?.warnings?.forEach { warning ->
                                Text("• $warning", modifier = Modifier.padding(top = 3.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                            }
                            Surface(
                                modifier = Modifier.fillMaxWidth().padding(top = 9.dp),
                                shape = RoundedCornerShape(12.dp),
                                tonalElevation = 2.dp
                            ) {
                                Column(Modifier.padding(10.dp)) {
                                    Text("COLOUR-AUTHORED TEMPLATE", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                    Text(
                                        "Fixed syntax: solid magenta scalar · solid teal select one · solid orange select multiple · solid violet barcode · solid lime image. Keep question text/response geometry black; print choice labels in blue. Bold text is recommended in the authoring template only.",
                                        modifier = Modifier.padding(top = 4.dp),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(colourTemplateStatus, modifier = Modifier.padding(top = 5.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    OutlinedButton(
                                        onClick = ::applyColourTemplateAnalysis,
                                        enabled = sourceBitmap != null && odkSchema != null && anchorsOk && !colourTemplateRunning,
                                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                                    ) { Text(if (colourTemplateRunning) "Analysing…" else "Analyse & auto-wire colour template") }
                                }
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
                                            setFields(fields + PaperDesignFieldDraft(name, addLabel.ifBlank { name }, type, options = options))
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
                            Text("FORM DATA LINKAGE", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Text(
                                when {
                                    !canBuild -> "Resolve the blocking mapping issues below, then SAVE SCHEMA."
                                    !schemaSaved -> "Mapping is structurally complete. Use SAVE SCHEMA in the lifecycle bar above."
                                    !blankTestPassedCurrent -> "Schema saved. Next: TEST EMPTY."
                                    !dataTestCompletedCurrent -> "Blank-form test passed. Next: TEST DATA."
                                    else -> "Both tests are complete. COMMIT will return this schema to the Schema Library."
                                },
                                modifier = Modifier.padding(top = 6.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (canBuild) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                            if (errors.isNotEmpty()) {
                                errors.take(4).forEach { issue ->
                                    Text(
                                        "• ${issue.message}",
                                        modifier = Modifier.padding(top = 3.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                if (errors.size > 4) {
                                    Text(
                                        "• +${errors.size - 4} more issue${if (errors.size - 4 == 1) "" else "s"}",
                                        modifier = Modifier.padding(top = 3.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            Text(
                                boxDetectionStatus,
                                modifier = Modifier.padding(top = 3.dp, bottom = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val region = selectedRegion
                            if (region == null) {
                                Text(
                                    "Use EDIT then tap a detected box to link or inspect it. AUTO re-detects printed rectangles; + BOX is only for regions the detector cannot see.",
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
                                            Text(fields.firstOrNull { it.name == linkFieldName }?.let { "${it.label} · ${it.name}" } ?: "Choose survey field")
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
                                    if (linkField != null && linkField.type in setOf(PaperFieldType.OMR_SINGLE, PaperFieldType.OMR_MULTIPLE)) {
                                        Box(Modifier.fillMaxWidth().padding(top = 7.dp)) {
                                            OutlinedButton(onClick = { linkOptionMenu = true }, modifier = Modifier.fillMaxWidth()) {
                                                val option = linkField.options.firstOrNull { it.value == linkOptionValue }
                                                Text(option?.let { "Return ${it.label} → ${it.value}" } ?: "Choose return option")
                                            }
                                            DropdownMenu(expanded = linkOptionMenu, onDismissRequest = { linkOptionMenu = false }) {
                                                linkField.options.forEach { option ->
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
                                        Text("Link selected box")
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
                                Text("SURVEY FIELDS", modifier = Modifier.padding(top = 10.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
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
                                            setFields(fields.filterNot { it.name == field.name })
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
    return mapped + looseRegions.mapIndexed { index, region ->
        PaperDesignRegionView(
            region.id,
            region.roi,
            null,
            "BOX ${String.format("%02d", index + 1)} · UNLINKED"
        )
    }
}

private data class PaperDetectedBoxMerge(
    val fields: List<PaperDesignFieldDraft>,
    val looseRegions: List<PaperDesignLooseRegion>,
    val snappedCount: Int
)

/**
 * Keep questionnaire semantics if a template already has mappings, but source geometry
 * from the actual printed rectangles. This repairs small coordinate drift and makes old
 * demo copies usable after the target-centre coordinate-frame migration. Detections that
 * are not confidently paired remain explicit UNLINKED boxes for the operator to wire.
 */
private fun mergeDetectedPaperBoxes(
    fields: List<PaperDesignFieldDraft>,
    detections: List<PaperBoxDetectionEngine.Detection>
): PaperDetectedBoxMerge {
    // Detection must never silently rewrite an existing semantic mapping. Earlier
    // development builds attempted nearest-neighbour "snapping" and could move a
    // valid mapping onto the wrong adjacent checkbox. Preserve all linked ROIs exactly;
    // only expose genuinely new print geometry as UNLINKED candidates. Colour-assisted
    // authoring has its own explicit label/type based wiring pass.
    val occupied = designRegionViews(fields, emptyList()).map { it.roi }
    val loose = detections
        .filter { detection -> occupied.none { mapped -> overlapFraction(detection.roi, mapped) > 0.62f } }
        .map { detection -> PaperDesignLooseRegion("auto:${UUID.randomUUID()}", detection.roi) }
    return PaperDetectedBoxMerge(fields, loose, 0)
}

private fun roiArea(roi: NormalisedRoi): Float =
    (roi.right - roi.left).coerceAtLeast(0f) * (roi.bottom - roi.top).coerceAtLeast(0f)

private fun roiCentreDistance(a: NormalisedRoi, b: NormalisedRoi): Float {
    val ax = (a.left + a.right) / 2f
    val ay = (a.top + a.bottom) / 2f
    val bx = (b.left + b.right) / 2f
    val by = (b.top + b.bottom) / 2f
    val dx = ax - bx
    val dy = ay - by
    return kotlin.math.sqrt(dx * dx + dy * dy)
}

private fun roiMatchCost(a: NormalisedRoi, b: NormalisedRoi): Float {
    val centre = roiCentreDistance(a, b)
    val aw = a.right - a.left
    val ah = a.bottom - a.top
    val bw = b.right - b.left
    val bh = b.bottom - b.top
    return centre + 0.20f * kotlin.math.abs(aw - bw) + 0.20f * kotlin.math.abs(ah - bh)
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
    questionOverlays: List<PaperColourTemplateEngine.QuestionProposal>,
    selectedRegionId: String,
    onSelectedRegion: (String) -> Unit,
    onSize: (IntSize) -> Unit,
    onRegionPlaced: (NormalisedRoi) -> Unit,
    onRegionChanged: (String, NormalisedRoi) -> Unit,
    onDetectBoxes: () -> Unit
) {
    var measured by remember { mutableStateOf(IntSize.Zero) }
    // Viewport state belongs to this rendered template bitmap. Do not restore pan/zoom
    // merely because a replacement form has the same pixel dimensions: that caused a
    // stale transform to make navigation appear broken after re-registration.
    var zoom by remember(bitmap) { mutableStateOf(1f) }
    var panX by remember(bitmap) { mutableStateOf(0f) }
    var panY by remember(bitmap) { mutableStateOf(0f) }
    var modeName by remember(bitmap) { mutableStateOf(PaperDesignerCanvasMode.NAVIGATE.name) }
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
                // Tap selection must not participate in NAVIGATE: detectTapGestures
                // consumes the pointer stream and previously starved pinch/pan.
                if (measured.width <= 0 || measured.height <= 0 || mode != PaperDesignerCanvasMode.EDIT) return@pointerInput
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

            questionOverlays.forEachIndexed { index, proposal ->
                val topLeft = pagePoint(Offset(proposal.envelope.left, proposal.envelope.top))
                val bottomRight = pagePoint(Offset(proposal.envelope.right, proposal.envelope.bottom))
                val query = proposal.fieldName == null ||
                    proposal.fieldConfidence < 0.82f ||
                    ((proposal.hint == PaperColourTemplateEngine.Hint.SELECT_ONE ||
                        proposal.hint == PaperColourTemplateEngine.Hint.SELECT_MULTIPLE) &&
                        proposal.options.any { it.responseRoi != null && it.choiceValue == null })
                val overlayColor = if (query) error else primary.copy(alpha = 0.72f)
                drawRect(
                    color = overlayColor,
                    topLeft = topLeft,
                    size = Size(bottomRight.x - topLeft.x, bottomRight.y - topLeft.y),
                    style = Stroke(width = if (query) 7f else 3f)
                )
                if (query) {
                    val queryPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = error.toArgb()
                        textSize = 27f
                        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                    }
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawText("QUERY ${index + 1}", topLeft.x + 7f, (topLeft.y + 29f).coerceAtMost(bottomRight.y), queryPaint)
                    }
                }
            }

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
                val labelTop = topLeft.y - 33f
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
            modifier = Modifier.align(Alignment.TopCenter).padding(horizontal = 8.dp, vertical = 8.dp),
            shape = RoundedCornerShape(14.dp),
            tonalElevation = 5.dp,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
        ) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                DesignerToolLabel("NAV", selected = mode == PaperDesignerCanvasMode.NAVIGATE) {
                    modeName = PaperDesignerCanvasMode.NAVIGATE.name; dragStart = null; dragEnd = null
                }
                DesignerToolLabel("AUTO DETECT") {
                    modeName = PaperDesignerCanvasMode.NAVIGATE.name
                    dragStart = null
                    dragEnd = null
                    onDetectBoxes()
                }
                DesignerToolLabel("+ BOX", selected = mode == PaperDesignerCanvasMode.PLACE) {
                    modeName = PaperDesignerCanvasMode.PLACE.name; dragStart = null; dragEnd = null
                }
                DesignerToolLabel("EDIT", selected = mode == PaperDesignerCanvasMode.EDIT, enabled = regions.isNotEmpty()) {
                    modeName = PaperDesignerCanvasMode.EDIT.name; dragStart = null; dragEnd = null
                }
                Text("·", color = MaterialTheme.colorScheme.outline)
                DesignerToolLabel("−") { applyZoom(zoom / 1.8f) }
                Text("${String.format("%.1f", zoom)}×", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                DesignerToolLabel("+") { applyZoom(zoom * 1.8f) }
                DesignerToolLabel("FIT") {
                    zoom = 1f
                    panX = 0f
                    panY = 0f
                    modeName = PaperDesignerCanvasMode.NAVIGATE.name
                }
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.86f)
        ) {
            Text(
                when (mode) {
                    PaperDesignerCanvasMode.NAVIGATE -> if (zoom <= 1.01f) "Pinch to zoom · drag to pan · AUTO DETECT finds response regions" else "Navigate · ${String.format("%.1f", zoom)}×"
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
private fun DesignerToolLabel(
    text: String,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Text(
        text,
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 7.dp),
        color = when {
            !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            selected -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
        style = MaterialTheme.typography.labelMedium
    )
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
        PaperFieldType.BARCODE -> "Barcode value · returns decoded code text"
        PaperFieldType.IMAGE -> "Image attachment · returns cropped ROI media"
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
                        PaperFieldType.BARCODE -> "barcode → code text"
                        PaperFieldType.IMAGE -> "image → cropped attachment"
                    }
                    constraintSummary?.let { Text(it, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Text(if (field.fromOdk) "WORKBOOK" else "MANUAL", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            if (field.fromOdk) {
                Row(Modifier.fillMaxWidth().padding(top = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Read this survey field from paper", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
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
    requiredExpression = field.requiredExpression,
    relevanceExpression = field.relevanceExpression,
    constraintExpression = field.constraintExpression,
    constraintMessage = field.constraintMessage,
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
    requiredExpression = field.requiredExpression,
    relevanceExpression = field.relevanceExpression,
    constraintExpression = field.constraintExpression,
    constraintMessage = field.constraintMessage,
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
    PaperFieldType.IMAGE -> "Image"
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
        "${looseRegions.size} placed box${if (looseRegions.size == 1) " is" else "es are"} not linked to an survey field."
    )
    val duplicates = fields.filter { it.included }.groupBy { it.name }.filterValues { it.size > 1 }.keys
    duplicates.forEach { issues += PaperDesignIssue(PaperDesignSeverity.ERROR, "Duplicate variable '$it'.") }

    val regions = mutableListOf<Pair<String, NormalisedRoi>>()
    fields.filter { it.included }.forEach { field ->
        if (!Regex("^[A-Za-z_][A-Za-z0-9_]*$").matches(field.name)) issues += PaperDesignIssue(PaperDesignSeverity.ERROR, "${field.name}: invalid survey variable name.")
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
        // In the canonical anchor-centre coordinate frame the registration targets define
        // the canvas boundary; there is no reserved interior corner zone. A mapped ROI
        // may legitimately sit close to an edge and is validated only against 0..1 bounds.
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
    fields: List<PaperDesignFieldDraft>,
    registration: PaperRegistrationSpec
): String {
    val root = JSONObject()
        .put("schema", "methodmesh.paper.v1")
        .put("template_id", templateId)
        .put("version", version)
        .put("title", title)
        .put("coordinate_frame", if (registration.type == PaperRegistrationType.APRILTAG8) "canonical_page" else "anchor_centres")
        .put("authoring_profile", JSONObject()
            .put("schema", "methodmesh.paper.colour.v1")
            .put("scalar_envelope", "#D81B60")
            .put("select_one_envelope", "#00897B")
            .put("select_multiple_envelope", "#EF6C00")
            .put("barcode_envelope", "#5E35B1")
            .put("image_envelope", "#7CB342")
            .put("choice_label", "#1565C0")
            .put("question_text", "#000000")
            .put("response_geometry", "#000000")
            .put("authoring_text_style", "bold_recommended")
            .put("runtime_colour_required", false))
        .put("page", JSONObject().put("width_px", width.coerceIn(600, 4000)).put("height_px", height.coerceIn(600, 6000)))
        .put("registration", registrationJson(registration))
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
                PaperFieldType.IMAGE -> "image"
            })
            .put("required", field.required)
        field.requiredExpression?.takeIf(String::isNotBlank)?.let { item.put("required_expression", it) }
        field.relevanceExpression?.takeIf(String::isNotBlank)?.let { item.put("relevance", it) }
        field.constraintExpression?.takeIf(String::isNotBlank)?.let { item.put("constraint_expression", it) }
        field.constraintMessage?.takeIf(String::isNotBlank)?.let { item.put("constraint_message", it) }
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
                when (field.type) {
                    PaperFieldType.BARCODE -> item.put("auto_accept", true)
                    PaperFieldType.IMAGE -> item.put("auto_accept", true)
                    else -> item.put("normalise", "trim").put("auto_accept", false)
                }
            }
        }
        array.put(item)
    }
    root.put("fields", array)
    val json = root.toString()
    PaperTemplate.parse(json) // final schema-level assertion
    return json
}

private fun registrationJson(registration: PaperRegistrationSpec): JSONObject = JSONObject().apply {
    when (registration.type) {
        PaperRegistrationType.APRILTAG8 -> {
            put("type", "apriltag8")
            put("family", PaperAprilTagFiducial.FAMILY)
            put("schema_key", registration.schemaKey)
            put("marker_size_fraction", registration.markerSizeFraction)
            put("ids", JSONArray(registration.markerIds))
            put("centres", JSONArray().apply {
                registration.centres.forEach { (x, y) -> put(JSONArray().put(x.toDouble()).put(y.toDouble())) }
            })
        }
        PaperRegistrationType.QR4 -> {
            put("type", "qr4")
            put("schema_key", registration.schemaKey)
            put("marker_size_fraction", registration.markerSizeFraction)
            put("centres", JSONArray().apply {
                registration.centres.forEach { (x, y) -> put(JSONArray().put(x.toDouble()).put(y.toDouble())) }
            })
        }
        PaperRegistrationType.BULLSEYE4 -> put("type", "bullseye4")
    }
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


private fun designerRegistrationTestLine(session: PaperExtractionSession): String {
    return when (session.template.registration.type) {
        PaperRegistrationType.APRILTAG8 -> {
            val r = session.rectification
            "APRILTAG8 ✓ · ${r.detectedMarkerCount}/${r.expectedMarkerCount} tags · ${r.correspondenceCount} points · RMS ${r.reprojectionRmsPx?.let { String.format("%.2f", it) } ?: "–"} px · max ${r.reprojectionMaxPx?.let { String.format("%.2f", it) } ?: "–"} px · ${r.registrationQuality}"
        }
        PaperRegistrationType.QR4 -> {
            val found = session.rectification.anchors.map { anchor ->
                when (anchor.corner.lowercase()) {
                    "top-left" -> "TL"
                    "top-right" -> "TR"
                    "bottom-right" -> "BR"
                    "bottom-left" -> "BL"
                    else -> anchor.corner.uppercase()
                }
            }.toSet()
            val expected = listOf("TL", "TR", "BR", "BL")
            val detail = expected.joinToString(" ") { corner -> "$corner ${if (corner in found) "✓" else "✕"}" }
            "QR4 ✓ · $detail · schema ${session.template.registration.schemaKey}"
        }
        PaperRegistrationType.BULLSEYE4 -> "Legacy bullseye registration ✓ · four targets detected"
    }
}

private fun Context.findDesignerActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        val base = current.baseContext
        if (base === current) break
        current = base
    }
    return current as? Activity
}
