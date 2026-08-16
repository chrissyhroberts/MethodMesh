package com.example.researchos.modules.mlkitvision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
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
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant

object MlKitVisionCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100MlKitVisionMethod.ID
    override val title = "ML Kit vision"
    override val description = "OCR and barcode detection using ML Kit."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val appContext = LocalContext.current
        var mode by rememberSaveable { mutableStateOf(context.action.settings["mlkit_mode"] ?: context.action.settings["input_mlkit_mode"] ?: "ocr_and_barcodes") }
        var inputSource by rememberSaveable { mutableStateOf(context.action.settings["input_source"] ?: context.action.settings["input_input_source"] ?: "camera") }
        var returnPdf by rememberSaveable { mutableStateOf((context.action.settings["return_pdf"] ?: context.action.settings["input_return_pdf"] ?: "true").equals("true", true)) }
        var returnTextFile by rememberSaveable { mutableStateOf((context.action.settings["return_text_file"] ?: context.action.settings["input_return_text_file"] ?: "true").equals("true", true)) }
        var modeMenuOpen by rememberSaveable { mutableStateOf(false) }
        var sourceMenuOpen by rememberSaveable { mutableStateOf(false) }
        var status by rememberSaveable { mutableStateOf("Choose image source and analysis mode.") }
        var pendingPhotoUri by rememberSaveable { mutableStateOf<Uri?>(null) }
        var launched by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }

        LaunchedEffect(mode, inputSource, returnPdf, returnTextFile) {
            context.onSettingsChanged(
                mapOf(
                    "mlkit_mode" to mode,
                    "input_source" to inputSource,
                    "return_pdf" to returnPdf.toString(),
                    "return_text_file" to returnTextFile.toString()
                )
            )
        }

        fun complete(values: Map<String, String>, succeeded: Boolean) {
            val request = As100MlKitVisionMethod.request(
                action = As100MlKitVisionMethod.ID,
                context = context.request.invocationContext.asMap(As100MlKitVisionMethod.ID) + context.action.settings,
                signals = emptyList(),
                inputs = emptyList()
            )
            val execution = As100MlKitVisionMethod.result(request, values, context.request.invocationContext)
            result = execution
            status = if (succeeded) "ML Kit analysis complete." else values[MlKitVisionFields.ERROR] ?: "ML Kit analysis failed."
            if (context.startsImmediately && succeeded) onConfirmed(execution)
        }

        fun analyse(uri: Uri) {
            status = "Analysing image with ML Kit…"
            runCatching { InputImage.fromFilePath(appContext, uri) }
                .onFailure { error ->
                    complete(failureValues(mode, uri.toString(), "Could not load image: ${error.message.orEmpty()}"), false)
                }
                .onSuccess { image ->
                    analyseImage(appContext, image, mode, uri.toString(), returnPdf, returnTextFile, ::complete)
                }
        }

        val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            val uri = pendingPhotoUri
            if (ok && uri != null) analyse(uri) else {
                status = "Camera capture cancelled."
                if (context.startsImmediately) onCancel()
            }
        }
        val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) analyse(uri) else {
                status = "Image selection cancelled."
                if (context.startsImmediately) onCancel()
            }
        }

        fun start() {
            result = null
            if (inputSource == "file_picker") {
                status = "Choose an image."
                pickImage.launch("image/*")
            } else {
                val file = File(appContext.cacheDir, "researchos-mlkit-${System.currentTimeMillis()}.jpg")
                val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
                pendingPhotoUri = uri
                status = "Capture image for ML Kit analysis."
                takePicture.launch(uri)
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
            Text("Run OCR and/or barcode detection with ML Kit. The Latin text and barcode models are bundled for on-device processing.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            Text("Analysis mode", fontWeight = FontWeight.SemiBold)
            OutlinedButton(onClick = { modeMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                Text(mode, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                Text("▼")
            }
            DropdownMenu(expanded = modeMenuOpen, onDismissRequest = { modeMenuOpen = false }, modifier = Modifier.fillMaxWidth(.9f)) {
                listOf("ocr", "barcodes", "ocr_and_barcodes").forEach { option ->
                    DropdownMenuItem(text = { Text(option) }, onClick = { mode = option; modeMenuOpen = false })
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Input source", fontWeight = FontWeight.SemiBold)
            OutlinedButton(onClick = { sourceMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                Text(inputSource, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                Text("▼")
            }
            DropdownMenu(expanded = sourceMenuOpen, onDismissRequest = { sourceMenuOpen = false }, modifier = Modifier.fillMaxWidth(.9f)) {
                listOf("camera", "file_picker").forEach { option ->
                    DropdownMenuItem(text = { Text(option) }, onClick = { inputSource = option; sourceMenuOpen = false })
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { returnPdf = !returnPdf }, modifier = Modifier.weight(1f)) { Text("PDF: ${if (returnPdf) "on" else "off"}") }
                Spacer(Modifier.padding(4.dp))
                OutlinedButton(onClick = { returnTextFile = !returnTextFile }, modifier = Modifier.weight(1f)) { Text("Text file: ${if (returnTextFile) "on" else "off"}") }
            }
            Spacer(Modifier.height(12.dp))
            Row {
                Button(onClick = { start() }, modifier = Modifier.weight(1f)) { Text("Analyse image") }
            }
            Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
            IntentExampleDropdown(
                capabilityId = capabilityId,
                examples = listOf(
                    IntentExample("OCR from camera", "Capture an image and return recognised text plus PDF/text attachments", "com.example.researchos.EXECUTE_METHOD(method_id='mlkit.vision.analyze',input_mlkit_mode='ocr',input_source='camera',input_return_pdf='true',input_return_text_file='true',return_mode='flat')"),
                    IntentExample("Barcode detection", "Capture an image and detect QR/Data Matrix/1D codes with ML Kit", "com.example.researchos.EXECUTE_METHOD(method_id='mlkit.vision.analyze',input_mlkit_mode='barcodes',input_source='camera',return_mode='flat')"),
                    IntentExample("OCR and codes", "Run both OCR and barcode detection", "com.example.researchos.EXECUTE_METHOD(method_id='mlkit.vision.analyze',input_mlkit_mode='ocr_and_barcodes',input_source='camera',input_return_pdf='true',input_return_text_file='true',return_mode='flat')")
                )
            )
        }
    }
}

private fun analyseImage(
    context: Context,
    image: InputImage,
    mode: String,
    sourceUri: String,
    returnPdf: Boolean,
    returnTextFile: Boolean,
    complete: (Map<String, String>, Boolean) -> Unit
) {
    val values = linkedMapOf(
        MlKitVisionFields.MODE to mode,
        MlKitVisionFields.SOURCE_URI to sourceUri,
        MlKitVisionFields.IMAGE_URI to sourceUri,
        MlKitVisionFields.PDF_URI to "",
        MlKitVisionFields.TEXT_FILE_URI to "",
        MlKitVisionFields.ANALYSED_TIME_ISO to Instant.now().toString()
    )
    var pending = 0
    var failed = false
    fun done() {
        pending -= 1
        if (pending <= 0 && !failed) {
            if (returnPdf) values[MlKitVisionFields.PDF_URI] = createImagePdf(context, sourceUri, values[MlKitVisionFields.TEXT].orEmpty()).orEmpty()
            if (returnTextFile && values[MlKitVisionFields.TEXT].orEmpty().isNotBlank()) values[MlKitVisionFields.TEXT_FILE_URI] = createTextFile(context, values[MlKitVisionFields.TEXT].orEmpty()).orEmpty()
            complete(values + (MlKitVisionFields.STATUS to "succeeded") + (MlKitVisionFields.ERROR to ""), true)
        }
    }
    fun fail(message: String) {
        if (!failed) {
            failed = true
            complete(failureValues(mode, sourceUri, message), false)
        }
    }
    if (mode == "ocr" || mode == "ocr_and_barcodes") {
        pending += 1
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            .process(image)
            .addOnSuccessListener { recognised ->
                values[MlKitVisionFields.TEXT] = recognised.text
                values[MlKitVisionFields.TEXT_BLOCK_COUNT] = recognised.textBlocks.size.toString()
                done()
            }
            .addOnFailureListener { fail("OCR failed: ${it.message.orEmpty()}") }
    } else {
        values[MlKitVisionFields.TEXT] = ""
        values[MlKitVisionFields.TEXT_BLOCK_COUNT] = "0"
    }
    if (mode == "barcodes" || mode == "ocr_and_barcodes") {
        pending += 1
        BarcodeScanning.getClient()
            .process(image)
            .addOnSuccessListener { barcodes ->
                values[MlKitVisionFields.BARCODES_JSON] = barcodeJson(barcodes)
                values[MlKitVisionFields.BARCODE_COUNT] = barcodes.size.toString()
                values[MlKitVisionFields.FIRST_BARCODE_RAW_VALUE] = barcodes.firstOrNull()?.rawValue.orEmpty()
                values[MlKitVisionFields.FIRST_BARCODE_FORMAT] = barcodes.firstOrNull()?.formatName().orEmpty()
                done()
            }
            .addOnFailureListener { fail("Barcode detection failed: ${it.message.orEmpty()}") }
    } else {
        values[MlKitVisionFields.BARCODES_JSON] = "[]"
        values[MlKitVisionFields.BARCODE_COUNT] = "0"
        values[MlKitVisionFields.FIRST_BARCODE_RAW_VALUE] = ""
        values[MlKitVisionFields.FIRST_BARCODE_FORMAT] = ""
    }
    if (pending == 0) complete(values + (MlKitVisionFields.STATUS to "succeeded") + (MlKitVisionFields.ERROR to ""), true)
}

private fun failureValues(mode: String, sourceUri: String, message: String): Map<String, String> = linkedMapOf(
    MlKitVisionFields.MODE to mode,
    MlKitVisionFields.SOURCE_URI to sourceUri,
    MlKitVisionFields.IMAGE_URI to sourceUri,
    MlKitVisionFields.PDF_URI to "",
    MlKitVisionFields.TEXT_FILE_URI to "",
    MlKitVisionFields.STATUS to "failed",
    MlKitVisionFields.TEXT to "",
    MlKitVisionFields.TEXT_BLOCK_COUNT to "0",
    MlKitVisionFields.BARCODES_JSON to "[]",
    MlKitVisionFields.BARCODE_COUNT to "0",
    MlKitVisionFields.FIRST_BARCODE_RAW_VALUE to "",
    MlKitVisionFields.FIRST_BARCODE_FORMAT to "",
    MlKitVisionFields.ANALYSED_TIME_ISO to Instant.now().toString(),
    MlKitVisionFields.ERROR to message
)

private fun barcodeJson(barcodes: List<Barcode>): String = JSONArray().apply {
    barcodes.forEach { barcode ->
        put(JSONObject().apply {
            put("raw_value", barcode.rawValue.orEmpty())
            put("display_value", barcode.displayValue.orEmpty())
            put("format", barcode.formatName())
            put("value_type", barcode.valueType)
            put("bounding_box", barcode.boundingBox?.let { "${it.left},${it.top},${it.right},${it.bottom}" }.orEmpty())
        })
    }
}.toString()

private fun Barcode.formatName(): String = when (format) {
    Barcode.FORMAT_QR_CODE -> "QR_CODE"
    Barcode.FORMAT_DATA_MATRIX -> "DATA_MATRIX"
    Barcode.FORMAT_AZTEC -> "AZTEC"
    Barcode.FORMAT_PDF417 -> "PDF417"
    Barcode.FORMAT_CODE_128 -> "CODE_128"
    Barcode.FORMAT_CODE_39 -> "CODE_39"
    Barcode.FORMAT_CODE_93 -> "CODE_93"
    Barcode.FORMAT_CODABAR -> "CODABAR"
    Barcode.FORMAT_EAN_13 -> "EAN_13"
    Barcode.FORMAT_EAN_8 -> "EAN_8"
    Barcode.FORMAT_ITF -> "ITF"
    Barcode.FORMAT_UPC_A -> "UPC_A"
    Barcode.FORMAT_UPC_E -> "UPC_E"
    else -> "UNKNOWN"
}

private fun createTextFile(context: Context, text: String): String? = runCatching {
    val file = File(context.cacheDir, "researchos-mlkit-ocr-${System.currentTimeMillis()}.txt")
    file.writeText(text)
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file).toString()
}.getOrNull()

private fun createImagePdf(context: Context, imageUri: String, ocrText: String): String? = runCatching {
    val bitmap = context.contentResolver.openInputStream(Uri.parse(imageUri))?.use { BitmapFactory.decodeStream(it) } ?: return null
    val maxWidth = 1200
    val scaled = scaleBitmap(bitmap, maxWidth)
    val file = File(context.cacheDir, "researchos-mlkit-document-${System.currentTimeMillis()}.pdf")
    val pdf = PdfDocument()
    val imagePage = pdf.startPage(PdfDocument.PageInfo.Builder(scaled.width, scaled.height, 1).create())
    imagePage.canvas.drawBitmap(scaled, 0f, 0f, null)
    pdf.finishPage(imagePage)
    if (ocrText.isNotBlank()) {
        val pageWidth = 595
        val pageHeight = 842
        val textPage = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 2).create())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f }
        var y = 40f
        wrapText(ocrText, 85).forEach { line ->
            if (y > pageHeight - 40) return@forEach
            textPage.canvas.drawText(line, 40f, y, paint)
            y += 16f
        }
        pdf.finishPage(textPage)
    }
    file.outputStream().use { pdf.writeTo(it) }
    pdf.close()
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file).toString()
}.getOrNull()

private fun scaleBitmap(bitmap: Bitmap, maxWidth: Int): Bitmap {
    if (bitmap.width <= maxWidth) return bitmap
    val ratio = maxWidth.toFloat() / bitmap.width.toFloat()
    return Bitmap.createScaledBitmap(bitmap, maxWidth, (bitmap.height * ratio).toInt().coerceAtLeast(1), true)
}

private fun wrapText(text: String, width: Int): List<String> =
    text.lineSequence().flatMap { paragraph ->
        if (paragraph.length <= width) sequenceOf(paragraph) else paragraph.chunked(width).asSequence()
    }.toList()
