package com.example.researchos.modules.documentscanner

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.researchos.core.researchos.ExecutionResult
import com.example.researchos.transport.OutputFormatter
import com.example.researchos.transport.workflow.ui.CapabilityScreenContext
import com.example.researchos.transport.workflow.ui.CapabilityScreenScaffold
import com.example.researchos.transport.workflow.ui.CapabilityScreenSpec
import com.example.researchos.transport.workflow.ui.IntentExample
import com.example.researchos.transport.workflow.ui.IntentExampleDropdown
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.json.JSONArray
import java.io.File
import java.time.Instant
import kotlin.math.min

object DocumentScannerCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100DocumentScannerMethod.ID
    override val title = "Document scanner"
    override val description = "Scan paper pages and return PDF/OCR attachments."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val appContext = LocalContext.current
        var pageLimitText by rememberSaveable { mutableStateOf(context.action.settings["page_limit"] ?: context.action.settings["input_page_limit"] ?: "10") }
        var scannerMode by rememberSaveable { mutableStateOf(context.action.settings["scanner_mode"] ?: context.action.settings["input_scanner_mode"] ?: "full") }
        var allowGallery by rememberSaveable { mutableStateOf((context.action.settings["allow_gallery_import"] ?: context.action.settings["input_allow_gallery_import"] ?: "true").equals("true", true)) }
        var runOcr by rememberSaveable { mutableStateOf((context.action.settings["run_ocr"] ?: context.action.settings["input_run_ocr"] ?: "true").equals("true", true)) }
        var returnSearchablePdf by rememberSaveable { mutableStateOf((context.action.settings["return_searchable_pdf"] ?: context.action.settings["input_return_searchable_pdf"] ?: "true").equals("true", true)) }
        var returnTextFile by rememberSaveable { mutableStateOf((context.action.settings["return_text_file"] ?: context.action.settings["input_return_text_file"] ?: "true").equals("true", true)) }
        var modeMenuOpen by rememberSaveable { mutableStateOf(false) }
        var status by rememberSaveable { mutableStateOf("Ready to scan a paper document.") }
        var launched by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }

        fun complete(values: Map<String, String>, succeeded: Boolean) {
            val request = As100DocumentScannerMethod.request(
                action = As100DocumentScannerMethod.ID,
                context = context.request.invocationContext.asMap(As100DocumentScannerMethod.ID) + context.action.settings,
                signals = emptyList(),
                inputs = emptyList()
            )
            val execution = As100DocumentScannerMethod.result(request, values, context.request.invocationContext)
            result = execution
            status = if (succeeded) "Document scan complete." else values[DocumentScannerFields.ERROR] ?: "Document scan failed."
            if (context.startsImmediately && succeeded) onConfirmed(execution)
        }

        val scannerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { activityResult ->
            if (activityResult.resultCode != Activity.RESULT_OK) {
                val values = failureValues(pageLimitText, scannerMode, allowGallery, "Document scan was cancelled.")
                complete(values, false)
                if (context.startsImmediately) onCancel()
                return@rememberLauncherForActivityResult
            }
            val scanResult = activityResult.data?.let { GmsDocumentScanningResult.fromActivityResultIntent(it) }
            if (scanResult == null) {
                complete(failureValues(pageLimitText, scannerMode, allowGallery, "No document scanner result was returned."), false)
            } else {
                status = "Copying scan outputs…"
                handleScanResult(appContext, scanResult, pageLimitText, scannerMode, allowGallery, runOcr, returnSearchablePdf, returnTextFile, ::complete)
            }
        }

        fun start() {
            result = null
            val activity = appContext.findActivity()
            if (activity == null) {
                complete(failureValues(pageLimitText, scannerMode, allowGallery, "No Android activity was available to launch the scanner."), false)
                return
            }
            val pageLimit = pageLimitText.toIntOrNull()?.coerceIn(1, 50) ?: 10
            val options = GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(allowGallery)
                .setPageLimit(pageLimit)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG, GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
                .setScannerMode(scannerMode.toScannerMode())
                .build()
            status = "Opening document scanner…"
            GmsDocumentScanning.getClient(options)
                .getStartScanIntent(activity)
                .addOnSuccessListener { sender ->
                    scannerLauncher.launch(IntentSenderRequest.Builder(sender).build())
                }
                .addOnFailureListener { error ->
                    complete(failureValues(pageLimitText, scannerMode, allowGallery, "Could not open ML Kit document scanner: ${error.message.orEmpty()}"), false)
                }
        }

        LaunchedEffect(context.startsImmediately) {
            if (context.startsImmediately && !launched) {
                launched = true
                start()
            }
        }

        CapabilityScreenScaffold(
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = result,
            resultPreview = result?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty(),
            onBack = onBack,
            onRetry = { start() },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Text("Scan one or more paper pages. ML Kit handles page detection, crop and alignment; ResearchOS copies the outputs and can OCR them.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(value = pageLimitText, onValueChange = { pageLimitText = it.filter(Char::isDigit).take(2) }, label = { Text("Maximum pages") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text("Scanner mode", fontWeight = FontWeight.SemiBold)
            OutlinedButton(onClick = { modeMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                Text(scannerMode, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                Text("▼")
            }
            DropdownMenu(expanded = modeMenuOpen, onDismissRequest = { modeMenuOpen = false }, modifier = Modifier.fillMaxWidth(.9f)) {
                listOf("full", "base_with_filter", "base").forEach { option ->
                    DropdownMenuItem(text = { Text(option) }, onClick = { scannerMode = option; modeMenuOpen = false })
                }
            }
            Spacer(Modifier.height(8.dp))
            ToggleRow("Gallery import", allowGallery) { allowGallery = !allowGallery }
            ToggleRow("Run OCR", runOcr) { runOcr = !runOcr }
            ToggleRow("Searchable PDF", returnSearchablePdf) { returnSearchablePdf = !returnSearchablePdf }
            ToggleRow("OCR text file", returnTextFile) { returnTextFile = !returnTextFile }
            Spacer(Modifier.height(12.dp))
            Button(onClick = { start() }, modifier = Modifier.fillMaxWidth()) { Text("Scan document") }
            Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
            IntentExampleDropdown(
                capabilityId = capabilityId,
                examples = listOf(
                    IntentExample("Scan document to searchable PDF", "Scan paper pages and return PDF/OCR attachments.", "com.example.researchos.EXECUTE_METHOD(method_id='document.scan',input_page_limit='10',input_scanner_mode='full',input_run_ocr='true',input_return_searchable_pdf='true',return_mode='flat')"),
                    IntentExample("Quick PDF scan", "Scan pages without OCR.", "com.example.researchos.EXECUTE_METHOD(method_id='document.scan',input_page_limit='5',input_run_ocr='false',input_return_searchable_pdf='false',return_mode='flat')")
                )
            )
        }
    }
}

@Composable
private fun ToggleRow(label: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text("$label: ${if (enabled) "on" else "off"}")
    }
}

private fun handleScanResult(
    context: Context,
    result: GmsDocumentScanningResult,
    pageLimitText: String,
    scannerMode: String,
    allowGallery: Boolean,
    runOcr: Boolean,
    returnSearchablePdf: Boolean,
    returnTextFile: Boolean,
    complete: (Map<String, String>, Boolean) -> Unit
) {
    val scanId = System.currentTimeMillis().toString()
    val copiedPageUris = result.pages.orEmpty().mapIndexedNotNull { index, page ->
        copyUriToCache(context, page.imageUri, "researchos-document-$scanId-page-${index + 1}.jpg")
    }
    val scannerPdfUri = result.pdf?.uri?.let { copyUriToCache(context, it, "researchos-document-$scanId-scanner.pdf") }.orEmpty()
    val baseValues = linkedMapOf(
        DocumentScannerFields.STATUS to "succeeded",
        DocumentScannerFields.PAGE_COUNT to copiedPageUris.size.toString(),
        DocumentScannerFields.PAGE_IMAGE_URIS_JSON to JSONArray(copiedPageUris).toString(),
        DocumentScannerFields.SCANNER_PDF_URI to scannerPdfUri,
        DocumentScannerFields.SEARCHABLE_PDF_URI to "",
        DocumentScannerFields.OCR_TEXT to "",
        DocumentScannerFields.OCR_TEXT_FILE_URI to "",
        DocumentScannerFields.OCR_PAGE_COUNT to "0",
        DocumentScannerFields.SCANNER_MODE to scannerMode,
        DocumentScannerFields.GALLERY_IMPORT_ALLOWED to allowGallery.toString(),
        DocumentScannerFields.PAGE_LIMIT to pageLimitText,
        DocumentScannerFields.SCANNED_TIME_ISO to Instant.now().toString(),
        DocumentScannerFields.ERROR to ""
    )
    if (copiedPageUris.isEmpty()) {
        complete(baseValues + (DocumentScannerFields.STATUS to "failed") + (DocumentScannerFields.ERROR to "No page images were returned by the scanner."), false)
        return
    }
    if (!runOcr) {
        val searchable = if (returnSearchablePdf) createSearchablePdf(context, copiedPageUris, emptyList(), scanId).orEmpty() else ""
        complete(baseValues + (DocumentScannerFields.SEARCHABLE_PDF_URI to searchable), true)
        return
    }
    ocrPages(context, copiedPageUris) { pageTexts, error ->
        if (error != null) {
            complete(baseValues + (DocumentScannerFields.STATUS to "failed") + (DocumentScannerFields.ERROR to error), false)
            return@ocrPages
        }
        val combinedText = pageTexts.mapIndexed { index, text -> "Page ${index + 1}\n$text" }.joinToString("\n\n")
        val textFileUri = if (returnTextFile) createTextFile(context, combinedText, scanId).orEmpty() else ""
        val searchable = if (returnSearchablePdf) createSearchablePdf(context, copiedPageUris, pageTexts, scanId).orEmpty() else ""
        complete(
            baseValues + mapOf(
                DocumentScannerFields.SEARCHABLE_PDF_URI to searchable,
                DocumentScannerFields.OCR_TEXT to combinedText,
                DocumentScannerFields.OCR_TEXT_FILE_URI to textFileUri,
                DocumentScannerFields.OCR_PAGE_COUNT to pageTexts.size.toString()
            ),
            true
        )
    }
}

private fun ocrPages(context: Context, pageUris: List<String>, done: (List<String>, String?) -> Unit) {
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    val texts = MutableList(pageUris.size) { "" }
    var remaining = pageUris.size
    var failed = false
    pageUris.forEachIndexed { index, uri ->
        val image = runCatching { InputImage.fromFilePath(context, Uri.parse(uri)) }.getOrElse { error ->
            if (!failed) {
                failed = true
                done(emptyList(), "Could not load scanned page for OCR: ${error.message.orEmpty()}")
            }
            return@forEachIndexed
        }
        recognizer.process(image)
            .addOnSuccessListener { result ->
                texts[index] = result.text
                remaining -= 1
                if (remaining == 0 && !failed) {
                    recognizer.close()
                    done(texts, null)
                }
            }
            .addOnFailureListener { error ->
                if (!failed) {
                    failed = true
                    recognizer.close()
                    done(emptyList(), "OCR failed: ${error.message.orEmpty()}")
                }
            }
    }
}

private fun copyUriToCache(context: Context, source: Uri, filename: String): String? = runCatching {
    val file = File(context.cacheDir, filename)
    context.contentResolver.openInputStream(source)?.use { input ->
        file.outputStream().use { output -> input.copyTo(output) }
    } ?: return null
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file).toString()
}.getOrNull()

private fun createTextFile(context: Context, text: String, scanId: String): String? = runCatching {
    val file = File(context.cacheDir, "researchos-document-$scanId-ocr.txt")
    file.writeText(text)
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file).toString()
}.getOrNull()

private fun createSearchablePdf(context: Context, imageUris: List<String>, pageTexts: List<String>, scanId: String): String? = runCatching {
    val file = File(context.cacheDir, "researchos-document-$scanId-searchable.pdf")
    val pdf = PdfDocument()
    imageUris.forEachIndexed { index, imageUri ->
        val bitmap = context.contentResolver.openInputStream(Uri.parse(imageUri))?.use { BitmapFactory.decodeStream(it) } ?: return@forEachIndexed
        val pageWidth = 1240
        val pageHeight = (pageWidth.toFloat() / bitmap.width.toFloat() * bitmap.height.toFloat()).toInt().coerceAtLeast(1)
        val page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create())
        val target = RectF(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat())
        page.canvas.drawBitmap(bitmap.fitWidth(pageWidth), null, target, null)
        val text = pageTexts.getOrNull(index).orEmpty()
        if (text.isNotBlank()) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 6f
                alpha = 1
            }
            var y = 8f
            text.lineSequence().flatMap { it.chunked(180).asSequence() }.take(120).forEach { line ->
                page.canvas.drawText(line, 4f, y, paint)
                y += 7f
            }
        }
        pdf.finishPage(page)
    }
    if (pageTexts.any { it.isNotBlank() }) {
        val appendixWidth = 595
        val appendixHeight = 842
        var pageNumber = imageUris.size + 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(appendixWidth, appendixHeight, pageNumber).create())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f }
        var y = 40f
        pageTexts.mapIndexed { index, text -> "Page ${index + 1}\n$text" }
            .joinToString("\n\n")
            .lineSequence()
            .flatMap { it.chunked(85).asSequence() }
            .forEach { line ->
                if (y > appendixHeight - 40) {
                    pdf.finishPage(page)
                    pageNumber += 1
                    page = pdf.startPage(PdfDocument.PageInfo.Builder(appendixWidth, appendixHeight, pageNumber).create())
                    y = 40f
                }
                page.canvas.drawText(line, 40f, y, paint)
                y += 16f
            }
        pdf.finishPage(page)
    }
    file.outputStream().use { pdf.writeTo(it) }
    pdf.close()
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file).toString()
}.getOrNull()

private fun Bitmap.fitWidth(width: Int): Bitmap {
    if (this.width == width) return this
    val ratio = width.toFloat() / this.width.toFloat()
    val height = (this.height * ratio).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(this, width, height, true)
}

private fun String.toScannerMode(): Int = when (this) {
    "base" -> GmsDocumentScannerOptions.SCANNER_MODE_BASE
    "base_with_filter" -> GmsDocumentScannerOptions.SCANNER_MODE_BASE_WITH_FILTER
    else -> GmsDocumentScannerOptions.SCANNER_MODE_FULL
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun failureValues(pageLimitText: String, scannerMode: String, allowGallery: Boolean, message: String): Map<String, String> = linkedMapOf(
    DocumentScannerFields.STATUS to "failed",
    DocumentScannerFields.PAGE_COUNT to "0",
    DocumentScannerFields.PAGE_IMAGE_URIS_JSON to "[]",
    DocumentScannerFields.SCANNER_PDF_URI to "",
    DocumentScannerFields.SEARCHABLE_PDF_URI to "",
    DocumentScannerFields.OCR_TEXT to "",
    DocumentScannerFields.OCR_TEXT_FILE_URI to "",
    DocumentScannerFields.OCR_PAGE_COUNT to "0",
    DocumentScannerFields.SCANNER_MODE to scannerMode,
    DocumentScannerFields.GALLERY_IMPORT_ALLOWED to allowGallery.toString(),
    DocumentScannerFields.PAGE_LIMIT to pageLimitText,
    DocumentScannerFields.SCANNED_TIME_ISO to Instant.now().toString(),
    DocumentScannerFields.ERROR to message
)
