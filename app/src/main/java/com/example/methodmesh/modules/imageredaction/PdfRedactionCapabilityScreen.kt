package com.example.methodmesh.modules.imageredaction

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.protocols.PresetResultAction
import com.example.methodmesh.transport.OutputExportRepository
import com.example.methodmesh.transport.ResultShare
import com.example.methodmesh.transport.workflow.ui.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.util.UUID

object PdfRedactionCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100PdfRedactionMethod.ID
    override val title = "Redact document / PDF"
    override val description = "Scan a document or open a PDF, remove selected regions, then share the redacted copy."

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val app = LocalContext.current
        val scope = rememberCoroutineScope()
        val activity = remember(app) { app.redactionActivity() }
        var workspacePath by rememberSaveable { mutableStateOf("") }
        var outputPath by rememberSaveable { mutableStateOf("") }
        var pageCount by rememberSaveable { mutableStateOf(0) }
        var page by rememberSaveable { mutableStateOf(0) }
        var regionsJson by rememberSaveable { mutableStateOf("[]") }
        val regions = remember(regionsJson) { PdfRedactionRegions.decode(regionsJson) }
        var committed by rememberRetainedExecutionResult("${context.action.canonicalId}:pdf")
        var hadCommitted by rememberSaveable { mutableStateOf(false) }
        var initialised by rememberSaveable { mutableStateOf(false) }
        var busy by remember { mutableStateOf(false) }
        var scanPending by rememberSaveable { mutableStateOf(false) }
        var documentSource by rememberSaveable { mutableStateOf(context.action.settings["document_source"]
            ?: context.action.settings["input_document_source"] ?: "file_picker") }
        var loadedDocumentSource by rememberSaveable { mutableStateOf("file_picker") }
        var rendering by remember { mutableStateOf(false) }
        var preview by remember { mutableStateOf<Bitmap?>(null) }
        var expanded by rememberSaveable { mutableStateOf(false) }
        val viewport = rememberRedactionViewportState(workspacePath, page)
        var status by rememberSaveable { mutableStateOf("Choose a PDF, then drag boxes over the information to remove.") }
        val sourceFile = workspacePath.takeIf(String::isNotBlank)?.let { File(it, "source.pdf") }?.takeIf(File::isFile)
        val displayFile = if (committed != null) outputPath.takeIf(String::isNotBlank)?.let(::File) else sourceFile

        // The private working copy is never an output. Keep it only while this run is open.
        DisposableEffect(workspacePath, activity) {
            val ownedPath = workspacePath
            onDispose {
                if (ownedPath.isNotBlank() && activity?.isChangingConfigurations == false) File(ownedPath).deleteRecursively()
            }
        }

        fun load(uri: Uri, sourceKind: String = "file_picker") {
            if (busy || scanPending || committed != null) return
            busy = true
            status = "Opening PDF…"
            scope.launch {
                val directory = File(app.cacheDir, "pdf-redaction-drafts/${UUID.randomUUID()}")
                var adopted = false
                try {
                    val jobContext = coroutineContext
                    val document = withContext(Dispatchers.IO) {
                        PdfRedactionEngine.import(app, uri, File(directory, "source.pdf")) { jobContext.ensureActive() }
                    }
                    workspacePath = directory.absolutePath
                    pageCount = document.pageCount
                    page = 0
                    regionsJson = "[]"
                    loadedDocumentSource = sourceKind
                    adopted = true
                    status = "PDF loaded. Drag a box over each area to remove. Review every page before sharing."
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (error: Exception) {
                    status = "Could not open PDF: ${error.message ?: "unreadable or password-protected document"}"
                } finally {
                    busy = false
                    if (!adopted) directory.deleteRecursively()
                }
            }
        }

        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) load(uri) else status = "No file selected. Choose a PDF to continue."
        }
        val scanner = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { returned ->
            if (!scanPending) return@rememberLauncherForActivityResult
            scanPending = false
            if (returned.resultCode != Activity.RESULT_OK) {
                status = "Scan cancelled. No document was redacted or shared."
            } else {
                runCatching {
                    val fields = listOf("document_scan_status", "document_scan_pdf_uri", "methodmesh_status")
                        .mapNotNull { key -> returned.data?.getStringExtra(key)?.let { key to it } }.toMap()
                    Uri.parse(RedactionScanContract.pdfUri(fields))
                }.onSuccess { load(it, "document_scanner") }.onFailure { status = "Could not use scan: ${it.message ?: "no PDF returned"}" }
            }
        }
        fun startScan() {
            if (busy || rendering || scanPending || committed != null) return
            scanPending = true
            status = "Opening document scanner…"
            val intent = Intent("com.example.methodmesh.EXECUTE_METHOD").setPackage(app.packageName)
                .putExtra("method_id", RedactionScanContract.METHOD_ID).putExtra("caller", "pdf_redaction_scan")
                .putExtra("return_mode", "flat").putExtra("input_payload_mode", "FULL")
                // FULL keeps routine audit fields in JSON unless the caller declares them.
                .putExtra("document_scan_status", "").putExtra("document_scan_pdf_uri", "")
            RedactionScanContract.settings.forEach { (key, value) -> intent.putExtra("input_$key", value) }
            runCatching { scanner.launch(intent) }.onFailure {
                scanPending = false
                status = "Document scanner unavailable: ${it.message ?: "try opening an existing PDF instead"}"
            }
        }
        LaunchedEffect(Unit) {
            if (hadCommitted && committed == null) {
                hadCommitted = false
                status = "Committed result unavailable. Review your masks and create a new redacted PDF."
            } else if (status in setOf("Opening PDF…", "Creating redacted PDF…")) {
                status = "Previous operation was interrupted. Choose the PDF again or retry Create."
            } else if (workspacePath.isNotBlank() && sourceFile == null && committed == null) {
                status = "Working PDF unavailable. Choose the PDF again and review its masks."
            }
            if (!initialised) {
                initialised = true
                val uri = context.action.settings["pdf_uri"] ?: context.action.settings["input_pdf_uri"]
                if (!context.awaitingRuntimeInputs) {
                    if (documentSource == "document_scanner" && context.startsImmediately) startScan()
                    else if (!uri.isNullOrBlank()) load(Uri.parse(uri))
                    else if (context.startsImmediately) picker.launch(arrayOf("application/pdf"))
                }
            }
        }

        LaunchedEffect(displayFile?.absolutePath, page) {
            preview = null
            if (displayFile != null) {
                rendering = true
                try {
                    preview = withContext(Dispatchers.IO) { PdfRedactionEngine.preview(displayFile, page) }
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (error: Exception) { status = "Page unavailable: ${error.message ?: "choose the PDF again"}"
                } finally { rendering = false }
            }
        }

        fun create() {
            val input = sourceFile ?: return
            if (busy || rendering || scanPending || regions.isEmpty() || committed != null) return
            val masks = regions.toList()
            val acquiredSource = loadedDocumentSource
            busy = true
            status = "Creating redacted PDF…"
            scope.launch {
                val output = File(app.cacheDir, "pdf-redaction-output/redacted-${UUID.randomUUID()}.pdf")
                var published = false
                try {
                    val jobContext = coroutineContext
                    val values = withContext(Dispatchers.IO) {
                        val count = PdfRedactionEngine.redact(input, masks, output) { jobContext.ensureActive() }
                        mapOf(PdfRedactionFields.STATUS to "succeeded",
                            PdfRedactionFields.URI to FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", output).toString(),
                            PdfRedactionFields.NAME to output.name,
                            PdfRedactionFields.SHA256 to PdfRedactionEngine.sha256(output),
                            PdfRedactionFields.PAGE_COUNT to count.toString(), PdfRedactionFields.REGION_COUNT to masks.size.toString(),
                            PdfRedactionFields.REGIONS to PdfRedactionRegions.encode(masks), PdfRedactionFields.MODE to "raster_flattened",
                            PdfRedactionFields.DPI to PdfRedactionEngine.DPI.toString(), PdfRedactionFields.CREATED to Instant.now().toString(),
                            PdfRedactionFields.ERROR to "")
                    }
                    // Do not include the original URI/path/filename in exported result evidence.
                    val result = As100PdfRedactionMethod.result(As100PdfRedactionMethod.request(capabilityId,
                        context.request.invocationContext.asMap(capabilityId) + ("redaction_document_source" to acquiredSource)), values, context.request.invocationContext)
                    outputPath = output.absolutePath
                    preview = null
                    committed = result
                    hadCommitted = true
                    published = true
                    status = "Redacted copy ready. Preview the exported pages, then Share PDF. The original is not shared."
                    if (context.submitsImmediately) onConfirmed(result)
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (error: Exception) { status = "Redaction failed: ${error.message ?: "could not create PDF"}"
                } finally {
                    busy = false
                    if (!published) output.delete()
                }
            }
        }

        fun share(): Boolean {
            val fields = committed?.observations?.singleOrNull()?.values ?: return false
            return runCatching {
                check(File(outputPath).let { it.isFile && it.length() > 0 }) { "The redacted copy is unavailable. Create a new copy." }
                ResultShare.share(app, "Share redacted PDF", text = "",
                    attachments = listOf(ResultShare.Attachment(fields[PdfRedactionFields.NAME].orEmpty(), Uri.parse(fields.getValue(PdfRedactionFields.URI)))),
                    fileLabel = "Redacted PDF", recipientHint = context.request.settings["methodmesh_share_recipient"]
                        ?: context.request.settings["input_methodmesh_share_recipient"].orEmpty())
                status = "Redacted PDF sent to the share chooser."
            }.onFailure { status = "Share failed: ${it.message ?: "no sharing app available"}" }.isSuccess
        }
        fun save(): Boolean {
            val fields = committed?.observations?.singleOrNull()?.values ?: return false
            return runCatching {
                val saved = OutputExportRepository.saveToDownloads(app, "Redacted PDF", "", listOf(fields.getValue(PdfRedactionFields.URI)), "")
                status = "Saved ${saved.summary}"
            }.onFailure { status = "Save failed: ${it.message ?: "storage error"}" }.isSuccess
        }
        fun done() {
            val result = committed ?: return
            if (context.isNativePresetRun && context.isLastStep) {
                val action = PresetResultAction.normalize(context.request.settings["methodmesh_preset_result_action"]
                    ?: context.request.settings["input_methodmesh_preset_result_action"] ?: PresetResultAction.HOME)
                if (action == PresetResultAction.SHARE && !share()) return
                if (action == PresetResultAction.SAVE && !save()) return
            }
            onConfirmed(result)
        }

        CapabilityScreenScaffold(title, capabilityId, context, context.stepNumber > 1, null, emptyMap(), onBack,
            onRetry = {}, onConfirm = ::done, onCancel = onCancel) {
            Text(status)
            Spacer(Modifier.height(8.dp))
            if (committed == null) {
                val fixedPdf = sourceFile != null && loadedDocumentSource == "file_picker" && context.isNativePresetRun && context.settingIsFixedInNativePreset("pdf_uri") &&
                    !(context.action.settings["pdf_uri"] ?: context.action.settings["input_pdf_uri"]).isNullOrBlank()
                val chooseMode = context.settingShouldBeShown("document_source")
                if (!fixedPdf && (chooseMode || documentSource == "document_scanner")) {
                    Button(onClick = {
                        documentSource = "document_scanner"
                        context.onSettingsChanged(mapOf("document_source" to documentSource))
                        startScan()
                    }, enabled = !busy && !rendering && !scanPending, modifier = Modifier.fillMaxWidth()) { Text("Scan document to redact") }
                }
                if (!fixedPdf && (chooseMode || documentSource == "file_picker") && (sourceFile == null || context.settingShouldBeShown("pdf_uri"))) {
                OutlinedButton(onClick = {
                    documentSource = "file_picker"
                    context.onSettingsChanged(mapOf("document_source" to documentSource))
                    picker.launch(arrayOf("application/pdf"))
                }, enabled = !busy && !rendering && !scanPending,
                    modifier = Modifier.fillMaxWidth()) { Text(if (sourceFile == null) "Open PDF from phone" else "Choose another PDF") }
                }
                Text("A new image-based PDF is created. Searchable text, forms, links and existing digital signatures are not retained. The original stays unchanged.",
                    style = MaterialTheme.typography.bodySmall)
            } else {
                Text("$pageCount ${if (pageCount == 1) "page" else "pages"} · ${regions.size} ${if (regions.size == 1) "region" else "regions"} removed", style = MaterialTheme.typography.titleMedium)
                Button(onClick = { share() }, modifier = Modifier.fillMaxWidth()) { Text("Share PDF") }
                OutlinedButton(onClick = { save() }, modifier = Modifier.fillMaxWidth()) { Text("Save to Downloads") }
                Button(onClick = ::done, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                OutlinedButton(onClick = {
                    committed = null; hadCommitted = false; outputPath = ""
                    status = "Editing masks. Create a new redacted PDF when ready."
                }, modifier = Modifier.fillMaxWidth()) { Text("Edit masks / new copy") }
            }
            if (displayFile != null && pageCount > 0) {
                Text("Page ${page + 1} of $pageCount", modifier = Modifier.padding(top = 12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { page-- }, enabled = !busy && !rendering && !scanPending && page > 0, modifier = Modifier.weight(1f)) { Text("Previous", maxLines = 1) }
                    OutlinedButton(onClick = { page++ }, enabled = !busy && !rendering && !scanPending && page < pageCount - 1, modifier = Modifier.weight(1f)) { Text("Next", maxLines = 1) }
                }
                if (rendering) LinearProgressIndicator(Modifier.fillMaxWidth())
                preview?.let { bitmap ->
                    val addRegion: (PdfRedactionRegion) -> Unit = { region ->
                        if (regions.size < PdfRedactionRegions.MAX_REGIONS) regionsJson = PdfRedactionRegions.encode(regions + region)
                        else status = "Maximum 500 regions reached. Split the document for further redaction."
                    }
                    PdfRegionEditor(bitmap, page, if (committed == null) regions else emptyList(), viewport,
                        enabled = !busy && !scanPending, canRedact = committed == null, onRegion = addRegion,
                        modifier = Modifier.fillMaxWidth().height(620.dp))
                    OutlinedButton(onClick = { expanded = true }, enabled = !busy && !scanPending, modifier = Modifier.fillMaxWidth()) { Text("Expand page") }
                    if (expanded) Dialog(onDismissRequest = { expanded = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().padding(12.dp)) {
                            Text("Page ${page + 1}")
                            PdfRegionEditor(bitmap, page, if (committed == null) regions else emptyList(), viewport,
                                !busy && !scanPending, committed == null, addRegion, Modifier.fillMaxWidth().weight(1f))
                            Button(onClick = { expanded = false }, modifier = Modifier.fillMaxWidth()) { Text("Close expanded page") }
                        }
                    }
                }
                if (committed == null) {
                    val pageRegions = regions.count { it.page == page }
                    Text("$pageRegions ${if (pageRegions == 1) "region" else "regions"} on this page · ${regions.size} in document")
                    Row(Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { regionsJson = PdfRedactionRegions.encode(regions.dropLast(1)) }, enabled = !busy && regions.isNotEmpty()) { Text("Undo last box") }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { regionsJson = PdfRedactionRegions.encode(regions.filterNot { it.page == page }) },
                            enabled = !busy && regions.any { it.page == page }) { Text("Clear page") }
                    }
                    Button(onClick = ::create, enabled = !busy && !rendering && !scanPending && regions.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                        Text(if (busy) "Creating…" else "Create redacted PDF")
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfRegionEditor(bitmap: Bitmap, page: Int, regions: List<PdfRedactionRegion>, viewport: RedactionViewportState,
    enabled: Boolean, canRedact: Boolean, onRegion: (PdfRedactionRegion) -> Unit, modifier: Modifier) =
    RedactionViewport(bitmap, viewport, "PDF page ${page + 1} redaction canvas", RedactionMarks.Boxes(page, regions, onRegion),
        modifier, enabled, canRedact)

private tailrec fun Context.redactionActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.redactionActivity()
    else -> null
}
