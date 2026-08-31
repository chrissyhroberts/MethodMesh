package com.example.methodmesh.modules.imageredaction

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.settings.MethodSetting
import com.example.methodmesh.settings.SettingsState
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import com.example.methodmesh.transport.workflow.ui.IntentExample
import com.example.methodmesh.transport.workflow.ui.IntentExampleDropdown
import org.json.JSONArray
import java.io.File
import java.time.Instant

object ImageRedactionCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100ImageRedactionMethod.ID
    override val title = "Image redaction"
    override val description = "Mask selected regions and return only the redacted image."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val appContext = LocalContext.current
        val settings = remember(context.action.settings) {
            SettingsState(ImageRedactionModule.capabilitySettings()[capabilityId].orEmpty()) { key, value ->
                context.onSettingsChanged(mapOf(key to value.toString()))
            }.also { state ->
                val definitions = ImageRedactionModule.capabilitySettings()[capabilityId].orEmpty().associateBy { it.id }
                context.action.settings.forEach { (key, value) ->
                    val canonicalKey = listOf(key, key.removePrefix("input_"))
                        .firstOrNull { it in definitions }
                        ?: key
                    when (definitions[canonicalKey]) {
                        is MethodSetting.IntSetting -> value.toIntOrNull()?.let { state.setInt(canonicalKey, it) }
                        else -> state.setString(canonicalKey, value)
                    }
                }
            }
        }
        var sourceUriString by rememberSaveable { mutableStateOf<String?>(null) }
        var bitmap by remember { mutableStateOf<Bitmap?>(null) }
        var selectedValue by rememberSaveable { mutableStateOf("") }
        val selected = remember(selectedValue) { selectedValue.split('|').filter(String::isNotBlank).toSet() }
        var redactedImageUri by rememberSaveable { mutableStateOf<String?>(null) }
        var redactedImageName by rememberSaveable { mutableStateOf<String?>(null) }
        var redactionMaskJson by rememberSaveable { mutableStateOf<String?>(null) }
        var redactionCellCount by rememberSaveable { mutableStateOf<String?>(null) }
        var redactionRows by rememberSaveable { mutableStateOf<String?>(null) }
        var redactionColumns by rememberSaveable { mutableStateOf<String?>(null) }
        var redactionStyle by rememberSaveable { mutableStateOf<String?>(null) }
        var redactionSource by rememberSaveable { mutableStateOf<String?>(null) }
        var redactionTimeIso by rememberSaveable { mutableStateOf<String?>(null) }
        var status by rememberSaveable { mutableStateOf("Choose or capture an image, then mark regions to remove.") }
        var pendingCameraUriString by rememberSaveable { mutableStateOf<String?>(null) }
        var captureStarted by rememberSaveable { mutableStateOf(false) }
        val result = remember(
            redactedImageUri,
            redactedImageName,
            redactionMaskJson,
            redactionCellCount,
            redactionRows,
            redactionColumns,
            redactionStyle,
            redactionSource,
            redactionTimeIso
        ) {
            val uri = redactedImageUri
            if (uri.isNullOrBlank()) {
                null
            } else {
                val values = linkedMapOf(
                    ImageRedactionFields.STATUS to "succeeded",
                    ImageRedactionFields.REDACTED_IMAGE_URI to uri,
                    ImageRedactionFields.REDACTED_IMAGE_NAME to redactedImageName.orEmpty(),
                    ImageRedactionFields.MASK_JSON to redactionMaskJson.orEmpty(),
                    ImageRedactionFields.SELECTED_CELLS to redactionCellCount.orEmpty(),
                    ImageRedactionFields.GRID_ROWS to redactionRows.orEmpty(),
                    ImageRedactionFields.GRID_COLUMNS to redactionColumns.orEmpty(),
                    ImageRedactionFields.STYLE to redactionStyle.orEmpty(),
                    ImageRedactionFields.SOURCE to redactionSource.orEmpty(),
                    ImageRedactionFields.CREATED_TIME_ISO to redactionTimeIso.orEmpty(),
                    ImageRedactionFields.ERROR to ""
                )
                As100ImageRedactionMethod.result(
                    As100ImageRedactionMethod.request(capabilityId, context.request.invocationContext.asMap(capabilityId) + values),
                    values,
                    context.request.invocationContext
                )
            }
        }

        fun load(uri: Uri) {
            runCatching {
                decodeOrientedBitmap(appContext, uri)
                    ?: error("Could not decode image")
            }.onSuccess {
                sourceUriString = uri.toString()
                bitmap = it
                selectedValue = ""
                redactedImageUri = null
                redactedImageName = null
                redactionMaskJson = null
                redactionCellCount = null
                redactionRows = null
                redactionColumns = null
                redactionStyle = null
                redactionSource = null
                redactionTimeIso = null
                status = "Image loaded. Tap or swipe cells to toggle redaction."
            }.onFailure {
                status = "Image load failed: ${it.message ?: "unknown error"}"
            }
        }

        LaunchedEffect(sourceUriString) {
            if (bitmap == null && !sourceUriString.isNullOrBlank()) {
                runCatching { decodeOrientedBitmap(appContext, Uri.parse(sourceUriString)) }
                    .onSuccess { bitmap = it }
            }
        }

        val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let(::load)
        }
        val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            val uri = pendingCameraUriString?.let(Uri::parse)
            if (ok && uri != null) load(uri) else status = "Camera capture cancelled."
        }

        fun startCamera() {
            val file = File(appContext.cacheDir, "methodmesh-redaction-source-${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
            pendingCameraUriString = uri.toString()
            camera.launch(uri)
        }

        fun startConfiguredSource() {
            captureStarted = true
            when (settings.getString("input_source")) {
                "file_picker" -> {
                    status = "Choose an image to redact."
                    picker.launch("image/*")
                }
                else -> {
                    status = "Opening camera…"
                    startCamera()
                }
            }
        }

        LaunchedEffect(context.startsImmediately) {
            if (context.startsImmediately && !captureStarted && bitmap == null && result == null) {
                startConfiguredSource()
            }
        }

        fun finish() {
            val source = bitmap ?: return
            val rows = settings.getInt("grid_rows").coerceIn(1, 50)
            val cols = settings.getInt("grid_columns").coerceIn(1, 50)
            val out = source.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = AndroidCanvas(out)
            val color = if (settings.getString("redaction_style") == "white") AndroidColor.WHITE else AndroidColor.BLACK
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; this.color = color }
            val cw = out.width.toFloat() / cols
            val ch = out.height.toFloat() / rows
            selected.forEach { id ->
                val r = id.substringAfter('r').substringBefore('c').toIntOrNull()?.minus(1) ?: return@forEach
                val c = id.substringAfter('c').toIntOrNull()?.minus(1) ?: return@forEach
                canvas.drawRect(c * cw, r * ch, (c + 1) * cw, (r + 1) * ch, fill)
            }
            val file = File(appContext.cacheDir, "methodmesh-redacted-${System.currentTimeMillis()}.jpg")
            file.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file).toString()
            val maskJson = JSONArray(selected.toList().sorted()).toString()
            val createdAt = Instant.now().toString()
            val values = linkedMapOf(
                ImageRedactionFields.STATUS to "succeeded",
                ImageRedactionFields.REDACTED_IMAGE_URI to uri,
                ImageRedactionFields.REDACTED_IMAGE_NAME to file.name,
                ImageRedactionFields.MASK_JSON to maskJson,
                ImageRedactionFields.SELECTED_CELLS to selected.size.toString(),
                ImageRedactionFields.GRID_ROWS to rows.toString(),
                ImageRedactionFields.GRID_COLUMNS to cols.toString(),
                ImageRedactionFields.STYLE to settings.getString("redaction_style"),
                ImageRedactionFields.SOURCE to settings.getString("input_source"),
                ImageRedactionFields.CREATED_TIME_ISO to createdAt,
                ImageRedactionFields.ERROR to ""
            )
            redactedImageUri = uri
            redactedImageName = file.name
            redactionMaskJson = maskJson
            redactionCellCount = selected.size.toString()
            redactionRows = rows.toString()
            redactionColumns = cols.toString()
            redactionStyle = settings.getString("redaction_style")
            redactionSource = settings.getString("input_source")
            redactionTimeIso = createdAt
            status = "Redacted image created. Original image is not returned."
            if (context.submitsImmediately) {
                onConfirmed(
                    As100ImageRedactionMethod.result(
                        As100ImageRedactionMethod.request(capabilityId, context.request.invocationContext.asMap(capabilityId) + values),
                        values,
                        context.request.invocationContext
                    )
                )
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
            onRetry = {
                bitmap = null
                sourceUriString = null
                selectedValue = ""
                redactedImageUri = null
                redactedImageName = null
                redactionMaskJson = null
                redactionCellCount = null
                redactionRows = null
                redactionColumns = null
                redactionStyle = null
                redactionSource = null
                redactionTimeIso = null
                captureStarted = false
                status = "Choose or capture an image, then mark regions to remove."
                if (context.startsImmediately) startConfiguredSource()
            },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Text(status, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            if (!context.startsImmediately) {
                Row(Modifier.fillMaxWidth()) {
                    Button(onClick = { settings.setString("input_source", "camera"); startCamera() }, modifier = Modifier.weight(1f)) { Text("Camera") }
                    Spacer(Modifier.padding(4.dp))
                    OutlinedButton(onClick = { settings.setString("input_source", "file_picker"); picker.launch("image/*") }, modifier = Modifier.weight(1f)) { Text("Pick image") }
                }
                Row(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = settings.getInt("grid_rows").toString(),
                        onValueChange = { it.toIntOrNull()?.let { value -> settings.setInt("grid_rows", value.coerceIn(1, 50)) } },
                        label = { Text("Rows") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(Modifier.padding(4.dp))
                    OutlinedTextField(
                        value = settings.getInt("grid_columns").toString(),
                        onValueChange = { it.toIntOrNull()?.let { value -> settings.setInt("grid_columns", value.coerceIn(1, 50)) } },
                        label = { Text("Columns") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                OutlinedButton(onClick = { settings.setString("redaction_style", if (settings.getString("redaction_style") == "black") "white" else "black") }, modifier = Modifier.fillMaxWidth()) {
                    Text("Mask style: ${settings.getString("redaction_style")}")
                }
            }
            bitmap?.let { image ->
                RedactionGrid(image, settings.getInt("grid_rows").coerceIn(1, 50), settings.getInt("grid_columns").coerceIn(1, 50), selected) {
                    selectedValue = it.toList().sorted().joinToString("|")
                }
                Button(onClick = ::finish, enabled = selected.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Create redacted image") }
            }
            Spacer(Modifier.height(12.dp))
            IntentExampleDropdown(
                capabilityId = capabilityId,
                examples = listOf(
                    IntentExample("Redact from camera", "Capture an image and mask selected cells.", "com.example.methodmesh.EXECUTE_METHOD(method_id='image.redact',input_source='camera',input_grid_rows='10',input_grid_columns='10',return_mode='flat')"),
                    IntentExample("Redact picked image", "Pick an image and mask selected cells.", "com.example.methodmesh.EXECUTE_METHOD(method_id='image.redact',input_source='file_picker',input_grid_rows='10',input_grid_columns='10',return_mode='flat')")
                )
            )
        }
    }
}

private fun decodeOrientedBitmap(context: android.content.Context, uri: Uri): Bitmap? {
    val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
    val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, options)
    } ?: return null
    val orientation = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    return bitmap.orientedByExif(orientation)
}

private fun Bitmap.orientedByExif(orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.postRotate(90f)
            matrix.preScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.postRotate(270f)
            matrix.preScale(-1f, 1f)
        }
        else -> return this
    }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

@Composable
private fun RedactionGrid(
    bitmap: Bitmap,
    rows: Int,
    columns: Int,
    selected: Set<String>,
    onChanged: (Set<String>) -> Unit
) {
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    Box(Modifier.fillMaxWidth().height(440.dp)) {
        Image(bitmap.asImageBitmap(), "Image to redact", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        Canvas(Modifier.fillMaxSize()) {
            val image = fittedImageRect(size.width, size.height, bitmap.width, bitmap.height)
            val cellWidth = image.width / columns.coerceAtLeast(1)
            val cellHeight = image.height / rows.coerceAtLeast(1)
            for (r in 0 until rows) {
                for (c in 0 until columns) {
                    val id = "r${r + 1}c${c + 1}"
                    val topLeft = Offset(image.left + c * cellWidth, image.top + r * cellHeight)
                    if (id in selected) drawRect(Color(0xAA000000), topLeft = topLeft, size = androidx.compose.ui.geometry.Size(cellWidth, cellHeight))
                    drawRect(Color.White, topLeft = topLeft, size = androidx.compose.ui.geometry.Size(cellWidth, cellHeight), style = Stroke(1.5f))
                }
            }
        }
        Box(Modifier.fillMaxSize().onSizeChanged { boxSize = it }.pointerInput(rows, columns, selected, boxSize) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var updated = toggleCell(selected, cellAt(down.position, boxSize, rows, columns, bitmap))
                var dragged = false
                drag(down.id) { change ->
                    dragged = true
                    change.consume()
                    cellAt(change.position, boxSize, rows, columns, bitmap)?.let { updated = updated + it }
                }
                onChanged(updated)
            }
        })
    }
}

private fun toggleCell(current: Set<String>, cell: String?): Set<String> =
    if (cell == null) current else if (cell in current) current - cell else current + cell

private fun fittedImageRect(width: Float, height: Float, imageWidth: Int, imageHeight: Int): Rect {
    if (width <= 0f || height <= 0f || imageWidth <= 0 || imageHeight <= 0) return Rect.Zero
    val scale = minOf(width / imageWidth.toFloat(), height / imageHeight.toFloat())
    val fittedWidth = imageWidth * scale
    val fittedHeight = imageHeight * scale
    return Rect((width - fittedWidth) / 2f, (height - fittedHeight) / 2f, (width + fittedWidth) / 2f, (height + fittedHeight) / 2f)
}

private fun cellAt(position: Offset, size: IntSize, rows: Int, columns: Int, bitmap: Bitmap): String? {
    if (size.width <= 0 || size.height <= 0) return null
    val image = fittedImageRect(size.width.toFloat(), size.height.toFloat(), bitmap.width, bitmap.height)
    if (!image.contains(position)) return null
    val column = ((position.x - image.left) / (image.width / columns.coerceAtLeast(1))).toInt().coerceIn(0, columns - 1)
    val row = ((position.y - image.top) / (image.height / rows.coerceAtLeast(1))).toInt().coerceIn(0, rows - 1)
    return "r${row + 1}c${column + 1}"
}
