package com.example.methodmesh.modules.digitalsigning

import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI
import java.util.UUID
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

object DigitalSigningCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100DigitalSigningMethod.ID
    override val title = "Digital signing"
    override val description = "Full-screen PDF signing and markup with optional RFC 3161 trusted timestamping."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val appContext = LocalContext.current
        val scope = rememberCoroutineScope()

        fun initial(key: String, default: String): String =
            context.action.settings[key]
                ?: context.action.settings["input_$key"]
                ?: default

        fun truthy(value: String): Boolean =
            value.trim().lowercase() in setOf("true", "yes", "1", "on")

        fun suppliedPdfUri(): String = listOf("pdf_uri", "source_pdf_uri", "document_uri")
            .asSequence()
            .map { key -> context.action.settings[key] ?: context.action.settings["input_$key"] }
            .firstOrNull { !it.isNullOrBlank() }
            .orEmpty()

        fun updateSetting(key: String, value: String) {
            context.onSettingsChanged(mapOf(key to value))
        }

        val isOdkLaunch = context.request.source.contains("odk", ignoreCase = true) ||
            context.request.invocationContext.caller.contains("odk", ignoreCase = true)
        // ODK file questions may expose app-private/transient attachment paths that are
        // not readable by MethodMesh (ENOENT once handed across the app boundary).
        // For ODK we therefore deliberately ignore pushed PDF values and let the
        // operator choose the PDF from Android's document picker inside this tool.
        // Non-ODK Android/protocol callers may still provide a stable content URI.
        val pushedPdfUri = if (isOdkLaunch) "" else suppliedPdfUri()
        val finaliseForced = isOdkLaunch || context.submitsImmediately

        var finalisePdf by rememberSaveable { mutableStateOf(truthy(initial("finalise_pdf", "false"))) }
        var requestTsa by rememberSaveable { mutableStateOf(truthy(initial("request_tsa", "false"))) }
        var tsaUrl by rememberSaveable {
            mutableStateOf(initial("tsa_url", DigitalSigningTsaClient.DEFAULT_TSA_URL).ifBlank { DigitalSigningTsaClient.DEFAULT_TSA_URL })
        }
        var penWidth by rememberSaveable {
            mutableStateOf(initial("pen_width_pt", "2.8").toFloatOrNull()?.coerceIn(0.8f, 12f) ?: 2.8f)
        }
        var penColor by rememberSaveable { mutableStateOf(initial("pen_color", "black").ifBlank { "black" }) }
        val effectiveFinalise = finaliseForced || finalisePdf

        var workingPdf by remember { mutableStateOf<WorkingPdf?>(null) }
        var strokes by remember { mutableStateOf<List<InkStroke>>(emptyList()) }
        var currentPage by rememberSaveable { mutableStateOf(0) }
        var renderedPage by remember { mutableStateOf<RenderedPdfPage?>(null) }
        var mode by rememberSaveable { mutableStateOf(DigitalSigningMode.Navigate) }
        var committedResult by remember { mutableStateOf<DigitalSigningCommittedResult?>(null) }
        var status by rememberSaveable { mutableStateOf("Preparing document workspace…") }
        var initialised by remember(context.action.canonicalId) { mutableStateOf(false) }
        var isCommitting by rememberSaveable { mutableStateOf(false) }
        var renderError by rememberSaveable { mutableStateOf<String?>(null) }
        var showCommitPanel by rememberSaveable { mutableStateOf(false) }
        var recoveredResultHandled by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }

        fun resultFor(committed: DigitalSigningCommittedResult): ExecutionResult {
            val values = DigitalSigningResultJson.fields(committed)
            val request = As100DigitalSigningMethod.request(
                action = capabilityId,
                context = context.request.invocationContext.asMap(capabilityId) + context.action.settings + mapOf(
                    "finalise_pdf" to committed.finalised.toString(),
                    "request_tsa" to committed.tsa.requested.toString(),
                    "tsa_url" to tsaUrl,
                    "pen_width_pt" to penWidth.toString(),
                    "pen_color" to penColor
                ),
                signals = emptyList(),
                inputs = emptyList()
            )
            return As100DigitalSigningMethod.result(request, values, context.request.invocationContext)
        }

        suspend fun finishTimestampAndBundle(
            working: WorkingPdf,
            base: DigitalSigningCommittedResult
        ): DigitalSigningCommittedResult {
            var current = base
            if (current.tsa.requested && current.tsa.status == "pending") {
                status = "Signed PDF committed · requesting trusted timestamp…"
                val tsa = withContext(Dispatchers.IO) {
                    DigitalSigningTsaClient.timestamp(tsaUrl, current.signedSha256)
                }
                current = current.copy(tsa = tsa)
                withContext(Dispatchers.IO) {
                    DigitalSigningDraftStore.saveDraft(appContext, working, strokes, currentPage, current)
                }
            }

            if (current.verificationBundle.status != "created") {
                status = if (current.tsa.status == "verified") {
                    "Timestamp verified · building verification bundle…"
                } else {
                    "Building verification bundle…"
                }
                val bundle = withContext(Dispatchers.IO) {
                    runCatching {
                        val signedFile = DigitalSigningDraftStore.resultFile(appContext, current.signedFilename)
                        val bundleFile = DigitalSigningDraftStore.nextVerificationBundleFile(appContext, current.sourceFilename)
                        val created = DigitalSigningVerificationBundle.create(signedFile, current, bundleFile)
                        val uri = FileProvider.getUriForFile(
                            appContext,
                            "${appContext.packageName}.fileprovider",
                            created.file
                        ).toString()
                        VerificationBundle.created(created.file.name, uri, created.sha256)
                    }.getOrElse { error ->
                        VerificationBundle.failed(error.message ?: "Could not create Deliverable B provenance ZIP.")
                    }
                }
                current = current.copy(verificationBundle = bundle)
                withContext(Dispatchers.IO) {
                    DigitalSigningDraftStore.saveDraft(appContext, working, strokes, currentPage, current)
                }
            }
            return current
        }

        fun adoptDraft(draft: DigitalSigningDraftStore.RestoredDraft) {
            workingPdf = draft.workingPdf
            strokes = draft.strokes
            currentPage = draft.currentPage
            committedResult = draft.committedResult
            status = when {
                draft.committedResult != null -> "Recovered committed signed PDF. Finishing result…"
                draft.strokes.isNotEmpty() -> "Recovered draft with ${draft.strokes.size} markup stroke(s)."
                else -> "Recovered PDF draft."
            }
        }

        fun importPdf(uri: Uri, origin: PdfInputOrigin) {
            scope.launch {
                isCommitting = true
                showCommitPanel = false
                status = "Opening PDF…"
                val outcome = runCatching {
                    withContext(Dispatchers.IO) {
                        DigitalSigningDraftStore.importPdf(appContext, uri, origin)
                    }
                }
                outcome.onSuccess { working ->
                    workingPdf = working
                    strokes = emptyList()
                    currentPage = 0
                    committedResult = null
                    mode = DigitalSigningMode.Navigate
                    status = "${working.displayName} · ${working.pageCount} page(s)"
                }.onFailure { error ->
                    status = "Could not open PDF: ${error.message ?: "invalid or inaccessible document"}"
                }
                isCommitting = false
            }
        }

        val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) importPdf(uri, PdfInputOrigin.FilePicker)
        }

        LaunchedEffect(context.action.canonicalId, pushedPdfUri) {
            if (initialised) return@LaunchedEffect
            initialised = true
            val restored = withContext(Dispatchers.IO) { DigitalSigningDraftStore.restore(appContext) }
            when {
                restored != null && pushedPdfUri.isBlank() -> adoptDraft(restored)
                restored != null && pushedPdfUri == restored.workingPdf.sourceUriString -> adoptDraft(restored)
                pushedPdfUri.isNotBlank() -> {
                    val origin = if (isOdkLaunch) PdfInputOrigin.Odk else PdfInputOrigin.AndroidIntent
                    runCatching {
                        withContext(Dispatchers.IO) {
                            DigitalSigningDraftStore.importPdf(appContext, Uri.parse(pushedPdfUri), origin)
                        }
                    }.onSuccess { working ->
                        workingPdf = working
                        strokes = emptyList()
                        currentPage = 0
                        status = "${working.displayName} · ${working.pageCount} page(s)"
                    }.onFailure { error ->
                        status = "Supplied PDF could not be opened: ${error.message ?: "unavailable URI"}"
                    }
                }
                restored != null -> adoptDraft(restored)
                else -> status = if (isOdkLaunch) {
                    "Choose the PDF for this ODK signing step. The document is selected inside MethodMesh rather than pushed from ODK."
                } else {
                    "Choose a PDF to sign or mark up."
                }
            }
        }

        LaunchedEffect(workingPdf?.sourceFile?.absolutePath, currentPage) {
            val working = workingPdf ?: run {
                renderedPage = null
                return@LaunchedEffect
            }
            val safePage = currentPage.coerceIn(0, (working.pageCount - 1).coerceAtLeast(0))
            if (safePage != currentPage) currentPage = safePage
            renderError = null
            runCatching {
                withContext(Dispatchers.IO) {
                    DigitalSigningPdfEngine.renderPage(working.sourceFile, safePage, targetLongSidePx = 2200)
                }
            }.onSuccess {
                renderedPage = it
            }.onFailure {
                renderedPage = null
                renderError = it.message ?: "Could not render this PDF page."
            }
        }

        LaunchedEffect(workingPdf?.sourceSha256, strokes, currentPage, committedResult) {
            val working = workingPdf ?: return@LaunchedEffect
            if (isCommitting) return@LaunchedEffect
            withContext(Dispatchers.IO) {
                DigitalSigningDraftStore.saveDraft(appContext, working, strokes, currentPage, committedResult)
            }
        }

        LaunchedEffect(initialised, committedResult?.signedSha256) {
            val working = workingPdf ?: return@LaunchedEffect
            val recovered = committedResult ?: return@LaunchedEffect
            if (recoveredResultHandled || isCommitting) return@LaunchedEffect
            recoveredResultHandled = true
            isCommitting = true
            val completed = finishTimestampAndBundle(working, recovered)
            committedResult = completed
            status = when {
                completed.verificationBundle.status == "failed" -> "Deliverable A recovered; Deliverable B provenance ZIP could not be created."
                completed.tsa.status == "verified" -> "Recovered signed PDF · trusted timestamp verified."
                completed.tsa.status == "failed" -> "Recovered signed PDF · TSA unavailable; failure metadata bundled."
                else -> "Recovered signed PDF."
            }
            isCommitting = false
        }

        fun commit() {
            val working = workingPdf ?: return
            if (strokes.isEmpty() || isCommitting) return
            scope.launch {
                isCommitting = true
                showCommitPanel = false
                mode = DigitalSigningMode.Navigate
                status = if (effectiveFinalise) "Finalising signed PDF…" else "Committing signed PDF…"

                val committed = runCatching {
                    withContext(Dispatchers.IO) {
                        val output = DigitalSigningDraftStore.nextResultFile(appContext, working.displayName)
                        val pdf = DigitalSigningPdfEngine.commit(
                            sourceFile = working.sourceFile,
                            strokes = strokes,
                            outputFile = output,
                            finalise = effectiveFinalise
                        )
                        val uri = FileProvider.getUriForFile(
                            appContext,
                            "${appContext.packageName}.fileprovider",
                            pdf.outputFile
                        ).toString()
                        DigitalSigningCommittedResult(
                            sourceOrigin = working.sourceOrigin,
                            sourceFilename = working.displayName,
                            sourceSha256 = working.sourceSha256,
                            signedFilename = pdf.outputFile.name,
                            signedPdfUri = uri,
                            signedSha256 = pdf.signedSha256,
                            pageCount = pdf.pageCount,
                            inkPresent = strokes.isNotEmpty(),
                            inkStrokeCount = strokes.size,
                            finalised = pdf.finalised,
                            finalisationMode = pdf.finalisationMode,
                            inkFlattened = pdf.inkFlattened,
                            formFieldsFlattened = pdf.formFieldsFlattened,
                            permissionRestrictionsApplied = pdf.permissionRestrictionsApplied,
                            newMarkupBlocked = pdf.newMarkupBlocked,
                            committedAtUtc = pdf.committedAtUtc,
                            tsa = if (requestTsa) TsaAttestation.pending(pdf.signedSha256) else TsaAttestation.notRequested(),
                            verificationBundle = VerificationBundle.pending()
                        )
                    }
                }

                val base = committed.getOrElse { error ->
                    status = "Commit failed: ${error.message ?: "PDF write error"}"
                    isCommitting = false
                    return@launch
                }

                // Persist the exact PDF before any network timestamping or ZIP work.
                withContext(Dispatchers.IO) {
                    DigitalSigningDraftStore.saveDraft(appContext, working, strokes, currentPage, base)
                }

                val completed = finishTimestampAndBundle(working, base)
                recoveredResultHandled = true
                committedResult = completed
                status = when {
                    completed.verificationBundle.status == "failed" -> "Deliverable A committed; Deliverable B provenance ZIP could not be created."
                    completed.tsa.status == "verified" -> "Signed PDF committed · trusted timestamp verified."
                    completed.tsa.status == "failed" -> "Signed PDF committed · TSA unavailable; failure metadata bundled."
                    else -> "Signed PDF committed."
                }
                isCommitting = false
            }
        }

        val committedResultReady = committedResult?.let { committed ->
            committed.tsa.status != "pending" && committed.verificationBundle.status != "pending"
        } == true
        val capturedResult = remember(committedResult, committedResultReady, context.action.settings, tsaUrl, penWidth, penColor) {
            committedResult.takeIf { committedResultReady }?.let(::resultFor)
        }
        val closeAction = if (context.stepNumber > 1) onBack else onCancel

        fun confirmResult() {
            val result = capturedResult ?: return
            DigitalSigningDraftStore.clear(appContext)
            onConfirmed(result)
        }

        CapabilityScreenScaffold(
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = capturedResult,
            resultPreview = committedResult?.takeIf { committedResultReady }?.let { committed ->
                // CapabilityScreenScaffold currently classifies only image/PDF-labelled
                // URI fields as shareable files. Use presentation-only keys containing
                // "pdf" for BOTH deliverables so the handler sends the real PDF + ZIP
                // as files instead of converting Deliverable B's URI into a text attachment.
                // Canonical ExecutionResult/ODK field IDs remain unchanged.
                linkedMapOf<String, Any?>(
                    "deliverable_a_signed_pdf_uri" to committed.signedPdfUri,
                    "deliverable_b_pdf_provenance_bundle_uri" to committed.verificationBundle.uri.orEmpty()
                ).filterValues { it?.toString()?.isNotBlank() == true }
            }.orEmpty(),
            onBack = onBack,
            onRetry = {
                committedResult = null
                recoveredResultHandled = false
                status = "Draft restored. Continue editing or commit another version."
            },
            onConfirm = ::confirmResult,
            onCancel = onCancel
        ) {
            val working = workingPdf
            if (working == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(440.dp)
                    .background(Color(0xFF141817))
            ) {
                EmptyFullScreenWorkspace(
                    status = status,
                    pushedPdfExpected = pushedPdfUri.isNotBlank(),
                    canChoosePdf = !context.submitsImmediately || pushedPdfUri.isBlank(),
                    onChoosePdf = { pdfPicker.launch(arrayOf("application/pdf")) },
                    onClose = closeAction
                )
                if (isCommitting) BusyOverlay(status)
            }
        } else {
            // Capability screens are hosted inside a dashboard/preset column whose
            // measured height can be only a few dozen dp. A real document editor
            // therefore lives in a platform-width Dialog, exactly like other
            // MethodMesh full-screen operator surfaces. The PDF canvas owns the
            // window; controls float over it and do not reserve document space.
            Dialog(
                onDismissRequest = closeAction,
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF141817))
                ) {
                    FullScreenSigningWorkspace(
                        workingPdf = working,
                        renderedPage = renderedPage,
                        renderError = renderError,
                        status = status,
                        currentPage = currentPage,
                        onPageChanged = { next -> currentPage = next.coerceIn(0, working.pageCount - 1) },
                        mode = mode,
                        onModeChanged = { mode = it },
                        strokes = strokes,
                        onStrokeAdded = { stroke -> strokes = strokes + stroke },
                        onEraseAt = { point, zoom ->
                            val threshold = (0.028f / zoom.coerceAtLeast(1f)).coerceAtLeast(0.006f)
                            val before = strokes.size
                            strokes = strokes.filterNot { stroke ->
                                stroke.pageIndex == currentPage && stroke.points.any { p ->
                                    val dx = p.x - point.x
                                    val dy = p.y - point.y
                                    sqrt(dx * dx + dy * dy) <= threshold
                                }
                            }
                            if (strokes.size < before) status = "Markup erased."
                        },
                        onUndo = {
                            val index = strokes.indexOfLast { it.pageIndex == currentPage }
                            if (index >= 0) {
                                strokes = strokes.toMutableList().also { it.removeAt(index) }
                                status = "Last markup stroke removed."
                            }
                        },
                        penArgb = penArgb(penColor),
                        canReplacePdf = true,
                        onReplacePdf = { pdfPicker.launch(arrayOf("application/pdf")) },
                        onCopySourceHash = {
                            appContext.getSystemService(ClipboardManager::class.java)
                                .setPrimaryClip(ClipData.newPlainText("PDF SHA-256", working.sourceSha256))
                            status = "Source SHA-256 copied."
                        },
                        penColor = penColor,
                        onPenColorChanged = {
                            penColor = it
                            updateSetting("pen_color", it)
                        },
                        showPenColor = context.settingShouldBeShown("pen_color", alwaysShow = true),
                        penWidthPt = penWidth,
                        onPenWidthChanged = {
                            penWidth = it
                            updateSetting("pen_width_pt", it.toString())
                        },
                        showPenWidth = context.settingShouldBeShown("pen_width_pt", alwaysShow = true),
                        isCommitting = isCommitting,
                        onDone = { showCommitPanel = true },
                        onClose = closeAction
                    )

                    if (showCommitPanel && !isCommitting) {
                        CommitOverlay(
                            finalisePdf = effectiveFinalise,
                            finaliseForced = finaliseForced,
                            onFinaliseChanged = {
                                finalisePdf = it
                                updateSetting("finalise_pdf", it.toString())
                            },
                            showFinaliseControl = finaliseForced || context.settingShouldBeShown("finalise_pdf"),
                            requestTsa = requestTsa,
                            onRequestTsaChanged = {
                                requestTsa = it
                                updateSetting("request_tsa", it.toString())
                            },
                            showTsaControl = context.settingShouldBeShown("request_tsa"),
                            tsaUrl = tsaUrl,
                            strokeCount = strokes.size,
                            onCommit = ::commit,
                            onDismiss = { showCommitPanel = false }
                        )
                    }

                    if (isCommitting) BusyOverlay(status)
                }
            }
        }
        }
    }
}

@Composable
private fun EmptyFullScreenWorkspace(
    status: String,
    pushedPdfExpected: Boolean,
    canChoosePdf: Boolean,
    onChoosePdf: () -> Unit,
    onClose: () -> Unit
) {
    Box(Modifier.fillMaxSize().padding(20.dp)) {
        OverlayButton("Close", onClick = onClose, modifier = Modifier.align(Alignment.TopStart))
        Surface(
            modifier = Modifier.align(Alignment.Center),
            shape = RoundedCornerShape(28.dp),
            color = Color(0xFFF4F0E8)
        ) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (pushedPdfExpected) "PDF unavailable" else "Open a PDF",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1B2422)
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF51605C)
                )
                if (canChoosePdf) {
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = onChoosePdf) { Text("Choose PDF") }
                }
            }
        }
    }
}

@Composable
private fun FullScreenSigningWorkspace(
    workingPdf: WorkingPdf,
    renderedPage: RenderedPdfPage?,
    renderError: String?,
    status: String,
    currentPage: Int,
    onPageChanged: (Int) -> Unit,
    mode: DigitalSigningMode,
    onModeChanged: (DigitalSigningMode) -> Unit,
    strokes: List<InkStroke>,
    onStrokeAdded: (InkStroke) -> Unit,
    onEraseAt: (InkPoint, Float) -> Unit,
    onUndo: () -> Unit,
    penArgb: Int,
    canReplacePdf: Boolean,
    onReplacePdf: () -> Unit,
    onCopySourceHash: () -> Unit,
    penColor: String,
    onPenColorChanged: (String) -> Unit,
    showPenColor: Boolean,
    penWidthPt: Float,
    onPenWidthChanged: (Float) -> Unit,
    showPenWidth: Boolean,
    isCommitting: Boolean,
    onDone: () -> Unit,
    onClose: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        PdfFullScreenPage(
            modifier = Modifier.fillMaxSize(),
            renderedPage = renderedPage,
            renderError = renderError,
            currentPage = currentPage,
            mode = mode,
            strokes = strokes.filter { it.pageIndex == currentPage },
            penWidthPt = penWidthPt,
            penArgb = penArgb,
            onStrokeAdded = onStrokeAdded,
            onEraseAt = onEraseAt
        )

        DocumentOverlay(
            workingPdf = workingPdf,
            status = status,
            canReplacePdf = canReplacePdf,
            onReplacePdf = onReplacePdf,
            onCopySourceHash = onCopySourceHash,
            onClose = onClose,
            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth().statusBarsPadding().padding(8.dp)
        )

        MarkupOverlay(
            mode = mode,
            onModeChanged = onModeChanged,
            onUndo = onUndo,
            canUndo = strokes.any { it.pageIndex == currentPage },
            penColor = penColor,
            onPenColorChanged = onPenColorChanged,
            showPenColor = showPenColor,
            penWidthPt = penWidthPt,
            onPenWidthChanged = onPenWidthChanged,
            showPenWidth = showPenWidth,
            enabled = !isCommitting,
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 58.dp, end = 8.dp)
        )

        PageOverlay(
            currentPage = currentPage,
            pageCount = workingPdf.pageCount,
            enabled = !isCommitting,
            onPageChanged = onPageChanged,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 10.dp)
        )

        Button(
            onClick = onDone,
            enabled = strokes.isNotEmpty() && !isCommitting,
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(10.dp)
        ) {
            Text("Done", fontWeight = FontWeight.Bold)
        }

    }
}

private data class PdfViewportLayout(
    val originX: Float,
    val originY: Float,
    val scaledWidth: Float,
    val scaledHeight: Float,
    val panX: Float,
    val panY: Float
)

private fun pdfViewportLayout(
    viewport: IntSize,
    bitmapWidth: Int,
    bitmapHeight: Int,
    zoom: Float,
    pan: Offset
): PdfViewportLayout? {
    if (viewport.width <= 0 || viewport.height <= 0 || bitmapWidth <= 0 || bitmapHeight <= 0) return null
    // Fit the complete PDF page into the available viewport on first open.
    // Subsequent pinch zoom multiplies this scale, so navigation still feels natural
    // without starting cropped/zoomed-in.
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
    return PdfViewportLayout(
        originX = viewport.width / 2f - scaledWidth / 2f + clampedPanX,
        originY = viewport.height / 2f - scaledHeight / 2f + clampedPanY,
        scaledWidth = scaledWidth,
        scaledHeight = scaledHeight,
        panX = clampedPanX,
        panY = clampedPanY
    )
}

@Composable
private fun PdfFullScreenPage(
    modifier: Modifier,
    renderedPage: RenderedPdfPage?,
    renderError: String?,
    currentPage: Int,
    mode: DigitalSigningMode,
    strokes: List<InkStroke>,
    penWidthPt: Float,
    penArgb: Int,
    onStrokeAdded: (InkStroke) -> Unit,
    onEraseAt: (InkPoint, Float) -> Unit
) {
    var zoom by rememberSaveable(currentPage) { mutableStateOf(1f) }
    var panX by rememberSaveable(currentPage) { mutableStateOf(0f) }
    var panY by rememberSaveable(currentPage) { mutableStateOf(0f) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var activePoints by remember { mutableStateOf<List<InkPoint>>(emptyList()) }
    val page = renderedPage

    Box(
        modifier = modifier
            .background(Color(0xFF0D100F))
            .onSizeChanged { viewportSize = it }
            .pointerInput(mode, currentPage, viewportSize, page?.bitmap?.width, page?.bitmap?.height) {
                val activePage = page ?: return@pointerInput
                when (mode) {
                    DigitalSigningMode.Navigate -> detectTransformGestures { _, pan, gestureZoom, _ ->
                        val newZoom = (zoom * gestureZoom).coerceIn(1f, 8f)
                        val layout = pdfViewportLayout(
                            viewportSize,
                            activePage.bitmap.width,
                            activePage.bitmap.height,
                            newZoom,
                            Offset(panX + pan.x, panY + pan.y)
                        )
                        zoom = newZoom
                        if (layout != null) {
                            panX = layout.panX
                            panY = layout.panY
                        }
                    }
                    DigitalSigningMode.Ink -> detectDragGestures(
                        onDragStart = { position ->
                            val layout = pdfViewportLayout(
                                viewportSize,
                                activePage.bitmap.width,
                                activePage.bitmap.height,
                                zoom,
                                Offset(panX, panY)
                            )
                            toNormalisedPoint(position, layout)?.let { activePoints = listOf(it) }
                        },
                        onDragEnd = {
                            if (activePoints.isNotEmpty()) {
                                onStrokeAdded(
                                    InkStroke(
                                        id = UUID.randomUUID().toString(),
                                        pageIndex = currentPage,
                                        points = activePoints,
                                        widthPt = penWidthPt,
                                        argb = penArgb
                                    )
                                )
                            }
                            activePoints = emptyList()
                        },
                        onDragCancel = { activePoints = emptyList() },
                        onDrag = { change, _ ->
                            change.consume()
                            val layout = pdfViewportLayout(
                                viewportSize,
                                activePage.bitmap.width,
                                activePage.bitmap.height,
                                zoom,
                                Offset(panX, panY)
                            )
                            toNormalisedPoint(change.position, layout)?.let { point ->
                                activePoints = activePoints + point
                            }
                        }
                    )
                    DigitalSigningMode.Erase -> detectDragGestures(
                        onDragStart = { position ->
                            val layout = pdfViewportLayout(
                                viewportSize,
                                activePage.bitmap.width,
                                activePage.bitmap.height,
                                zoom,
                                Offset(panX, panY)
                            )
                            toNormalisedPoint(position, layout)?.let { onEraseAt(it, zoom) }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val layout = pdfViewportLayout(
                                viewportSize,
                                activePage.bitmap.width,
                                activePage.bitmap.height,
                                zoom,
                                Offset(panX, panY)
                            )
                            toNormalisedPoint(change.position, layout)?.let { onEraseAt(it, zoom) }
                        }
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (page == null) {
            Text(
                renderError ?: "Rendering page…",
                color = Color.White.copy(alpha = 0.84f),
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            Canvas(Modifier.fillMaxSize()) {
                val layout = pdfViewportLayout(
                    IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    page.bitmap.width,
                    page.bitmap.height,
                    zoom,
                    Offset(panX, panY)
                ) ?: return@Canvas
                val image = page.bitmap.asImageBitmap()
                drawImage(
                    image = image,
                    dstOffset = IntOffset(layout.originX.roundToInt(), layout.originY.roundToInt()),
                    dstSize = IntSize(layout.scaledWidth.roundToInt(), layout.scaledHeight.roundToInt())
                )

                fun drawInkStroke(stroke: InkStroke) {
                    if (stroke.points.isEmpty()) return
                    val colour = Color(stroke.argb)
                    val pxWidth = (
                        stroke.widthPt * layout.scaledWidth / page.pageWidthPt.coerceAtLeast(1f)
                    ).coerceAtLeast(1.2f)
                    fun point(p: InkPoint) = Offset(
                        layout.originX + p.x * layout.scaledWidth,
                        layout.originY + p.y * layout.scaledHeight
                    )
                    if (stroke.points.size == 1) {
                        drawCircle(colour, radius = pxWidth / 2f, center = point(stroke.points.first()))
                    } else {
                        val path = androidx.compose.ui.graphics.Path()
                        val first = point(stroke.points.first())
                        path.moveTo(first.x, first.y)
                        stroke.points.drop(1).forEach { p ->
                            val mapped = point(p)
                            path.lineTo(mapped.x, mapped.y)
                        }
                        drawPath(path, colour, style = Stroke(width = pxWidth, cap = StrokeCap.Round))
                    }
                }

                strokes.forEach(::drawInkStroke)
                if (activePoints.isNotEmpty()) {
                    drawInkStroke(
                        InkStroke(
                            id = "active",
                            pageIndex = currentPage,
                            points = activePoints,
                            widthPt = penWidthPt,
                            argb = penArgb
                        )
                    )
                }
            }

            Surface(
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 8.dp),
                shape = RoundedCornerShape(50),
                color = Color.Black.copy(alpha = 0.54f)
            ) {
                Text(
                    when {
                        mode == DigitalSigningMode.Navigate && zoom <= 1.01f -> "Drag to pan · pinch to zoom"
                        mode == DigitalSigningMode.Navigate -> "Navigate · ${String.format("%.1f", zoom)}×"
                        else -> modeLabel(mode)
                    },
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

private fun toNormalisedPoint(position: Offset, layout: PdfViewportLayout?): InkPoint? {
    layout ?: return null
    if (layout.scaledWidth <= 0f || layout.scaledHeight <= 0f) return null
    val x = (position.x - layout.originX) / layout.scaledWidth
    val y = (position.y - layout.originY) / layout.scaledHeight
    if (x !in 0f..1f || y !in 0f..1f) return null
    return InkPoint(x, y)
}

@Composable
private fun DocumentOverlay(
    workingPdf: WorkingPdf,
    status: String,
    canReplacePdf: Boolean,
    onReplacePdf: () -> Unit,
    onCopySourceHash: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color(0xD91D2422),
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OverlayButton("Close", onClick = onClose)
            Column(Modifier.weight(1f)) {
                Text(
                    workingPdf.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val meta = if (
                    status.contains("failed", ignoreCase = true) ||
                    status.startsWith("Could", ignoreCase = true) ||
                    status.startsWith("Recovered", ignoreCase = true) ||
                    status.contains("copied", ignoreCase = true) ||
                    status.contains("erased", ignoreCase = true) ||
                    status.contains("removed", ignoreCase = true)
                ) status else "${workingPdf.pageCount}p · ${workingPdf.sourceOrigin.id.replace('_', ' ')}"
                Text(
                    meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.68f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            OverlayButton("SHA", onClick = onCopySourceHash)
            if (canReplacePdf) OverlayButton("Replace", onClick = onReplacePdf)
        }
    }


}

@Composable
private fun MarkupOverlay(
    mode: DigitalSigningMode,
    onModeChanged: (DigitalSigningMode) -> Unit,
    onUndo: () -> Unit,
    canUndo: Boolean,
    penColor: String,
    onPenColorChanged: (String) -> Unit,
    showPenColor: Boolean,
    penWidthPt: Float,
    onPenWidthChanged: (Float) -> Unit,
    showPenWidth: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    if (mode == DigitalSigningMode.Navigate) {
        Button(
            onClick = { onModeChanged(DigitalSigningMode.Ink) },
            enabled = enabled,
            modifier = modifier
        ) {
            Text("Ink")
        }
        return
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = Color(0xE61D2422),
        tonalElevation = 10.dp
    ) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = {
                        onModeChanged(if (mode == DigitalSigningMode.Ink) DigitalSigningMode.Navigate else DigitalSigningMode.Ink)
                    },
                    enabled = enabled
                ) { Text(if (mode == DigitalSigningMode.Ink) "Ink ON" else "Ink") }
                OutlinedButton(
                    onClick = {
                        onModeChanged(if (mode == DigitalSigningMode.Erase) DigitalSigningMode.Navigate else DigitalSigningMode.Erase)
                    },
                    enabled = enabled
                ) { Text(if (mode == DigitalSigningMode.Erase) "Erase ON" else "Erase") }
                OverlayButton("Undo", enabled = enabled && canUndo, onClick = onUndo)
            }

            if (showPenColor && mode == DigitalSigningMode.Ink) {
                Spacer(Modifier.height(7.dp))
                val colours = listOf("black", "blue", "red", "green", "purple", "orange", "teal", "magenta")
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    colours.forEach { value -> PenColourChoice(value, penColor, enabled, onPenColorChanged) }
                }
            }

            if (showPenWidth && mode == DigitalSigningMode.Ink) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RoundAction("−", enabled && penWidthPt > 0.8f) {
                        onPenWidthChanged((penWidthPt - 0.4f).coerceAtLeast(0.8f))
                    }
                    Text(
                        "${String.format("%.1f", penWidthPt)} pt",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                    RoundAction("+", enabled && penWidthPt < 12f) {
                        onPenWidthChanged((penWidthPt + 0.4f).coerceAtMost(12f))
                    }
                }
            }
        }
    }
}

@Composable
private fun PenColourChoice(value: String, selected: String, enabled: Boolean, onSelected: (String) -> Unit) {
    val selectedNow = selected == value
    Surface(
        modifier = Modifier
            .size(if (selectedNow) 28.dp else 24.dp)
            .clickable(enabled = enabled) { onSelected(value) },
        shape = CircleShape,
        color = Color(penArgb(value)),
        border = androidx.compose.foundation.BorderStroke(
            if (selectedNow) 3.dp else 1.dp,
            if (selectedNow) Color.White else Color.White.copy(alpha = 0.38f)
        )
    ) {}
}

@Composable
private fun PageOverlay(
    currentPage: Int,
    pageCount: Int,
    enabled: Boolean,
    onPageChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var pageText by rememberSaveable(pageCount) { mutableStateOf((currentPage + 1).toString()) }

    LaunchedEffect(currentPage, pageCount) {
        pageText = (currentPage + 1).coerceIn(1, pageCount.coerceAtLeast(1)).toString()
    }

    fun goToTypedPage() {
        val requested = pageText.toIntOrNull()?.coerceIn(1, pageCount.coerceAtLeast(1)) ?: return
        pageText = requested.toString()
        onPageChanged(requested - 1)
    }

    fun step(delta: Int) {
        val next = (currentPage + delta).coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        pageText = (next + 1).toString()
        onPageChanged(next)
    }

    Surface(modifier = modifier, shape = RoundedCornerShape(50), color = Color(0xE61D2422)) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoundAction("‹", enabled && currentPage > 0) { step(-1) }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.14f)
            ) {
                BasicTextField(
                    value = pageText,
                    onValueChange = { value ->
                        if (enabled) pageText = value.filter(Char::isDigit).take(5)
                    },
                    enabled = enabled,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = MaterialTheme.typography.labelLarge.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    ),
                    modifier = Modifier
                        .size(width = 42.dp, height = 32.dp)
                        .padding(horizontal = 4.dp, vertical = 7.dp)
                )
            }

            Text(
                "/ $pageCount",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            RoundAction("Go", enabled && pageText.toIntOrNull() != null, ::goToTypedPage)
            RoundAction("›", enabled && currentPage < pageCount - 1) { step(1) }
        }
    }
}

@Composable
private fun CommitOverlay(
    finalisePdf: Boolean,
    finaliseForced: Boolean,
    onFinaliseChanged: (Boolean) -> Unit,
    showFinaliseControl: Boolean,
    requestTsa: Boolean,
    onRequestTsaChanged: (Boolean) -> Unit,
    showTsaControl: Boolean,
    tsaUrl: String,
    strokeCount: Int,
    onCommit: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.36f)).clickable(onClick = onDismiss)) {
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp).clickable { },
            shape = RoundedCornerShape(26.dp),
            color = Color(0xFFF4F0E8),
            tonalElevation = 12.dp
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("Finish document", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF1B2422))
                Text(
                    "$strokeCount markup stroke(s). Deliverable A is the signed PDF; Deliverable B is its provenance ZIP. The source is never overwritten.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF51605C)
                )

                if (showFinaliseControl) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = finalisePdf,
                            onCheckedChange = if (finaliseForced) null else onFinaliseChanged,
                            enabled = true
                        )
                        Column {
                            Text("Finalise PDF", fontWeight = FontWeight.Bold, color = Color(0xFF1B2422))
                            Text(
                                if (finaliseForced) "Required for this external/ODK return." else "Flatten the existing appearance and remove editable form/annotation structure.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF51605C)
                            )
                        }
                    }
                }

                if (showTsaControl) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = requestTsa, onCheckedChange = onRequestTsaChanged)
                        Column {
                            Text("Trusted timestamp", fontWeight = FontWeight.Bold, color = Color(0xFF1B2422))
                            Text(
                                "RFC 3161 via ${tsaAuthorityLabel(tsaUrl)}. The authority is preconfigured, as in MethodMesh attestation.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF51605C)
                            )
                        }
                    }
                }

                if (requestTsa) {
                    Text(
                        "Deliverable B will contain a copy of Deliverable A, TSA JSON, the raw timestamp token, hashes and VERIFY.txt with independent verification instructions.",
                        modifier = Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF51605C)
                    )
                } else {
                    Text(
                        "Deliverable B will still contain a copy of Deliverable A, hashes, metadata and VERIFY.txt; TSA status will be recorded as not requested.",
                        modifier = Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF51605C)
                    )
                }

                Spacer(Modifier.height(14.dp))
                Button(onClick = onCommit, modifier = Modifier.fillMaxWidth().height(52.dp), enabled = strokeCount > 0) {
                    Text(
                        when {
                            finalisePdf && requestTsa -> "Commit · Finalise · Timestamp"
                            finalisePdf -> "Commit finalised PDF"
                            requestTsa -> "Commit · Timestamp"
                            else -> "Commit PDF"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Keep editing") }
            }
        }
    }
}

@Composable
private fun BusyOverlay(status: String) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.62f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(shape = RoundedCornerShape(22.dp), color = Color(0xFFF4F0E8)) {
            Column(Modifier.padding(horizontal = 28.dp, vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text(status, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF1B2422))
            }
        }
    }
}

@Composable
private fun OverlayButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(50),
        color = if (enabled) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.06f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = if (enabled) 0.28f else 0.12f))
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = if (enabled) 1f else 0.38f)
        )
    }
}

@Composable
private fun RoundAction(label: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(34.dp).clickable(enabled = enabled, onClick = onClick),
        shape = CircleShape,
        color = if (enabled) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.05f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = if (enabled) 0.26f else 0.10f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = Color.White.copy(alpha = if (enabled) 1f else 0.32f), fontWeight = FontWeight.Bold)
        }
    }
}

private fun modeLabel(mode: DigitalSigningMode): String = when (mode) {
    DigitalSigningMode.Navigate -> "Navigate"
    DigitalSigningMode.Ink -> "Ink"
    DigitalSigningMode.Erase -> "Erase"
}

private fun penArgb(name: String): Int = when (name.lowercase()) {
    "blue" -> 0xFF184F9A.toInt()
    "red" -> 0xFFC53B36.toInt()
    "green" -> 0xFF2F7D57.toInt()
    "purple" -> 0xFF6E4D9B.toInt()
    "orange" -> 0xFFD97706.toInt()
    "teal" -> 0xFF00838F.toInt()
    "magenta" -> 0xFFB83280.toInt()
    else -> 0xFF141718.toInt()
}

private fun tsaAuthorityLabel(url: String): String =
    runCatching { URI(url).host }.getOrNull().orEmpty().ifBlank { "configured TSA" }
