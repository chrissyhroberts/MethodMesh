package com.example.methodmesh.modules.imageredaction

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.settings.MethodSetting
import com.example.methodmesh.settings.SettingsState
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import com.example.methodmesh.transport.workflow.ui.IntentExample
import com.example.methodmesh.transport.workflow.ui.IntentExampleDropdown
import com.example.methodmesh.transport.workflow.ui.rememberRetainedExecutionResult
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant

object ImageRedactionCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100ImageRedactionMethod.ID
    override val title = "Redact photo"
    override val description = "Take a new photo or choose a saved photo, then share only the redacted copy."

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
                imageRedactionInputSettings(context.action.settings).forEach { (key, value) ->
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
        var loadedPhotoSource by rememberSaveable { mutableStateOf("camera") }
        var bitmap by remember { mutableStateOf<Bitmap?>(null) }
        var selectedValue by rememberSaveable { mutableStateOf("") }
        val selected = remember(selectedValue) { selectedValue.split('|').filter(String::isNotBlank).toSet() }
        var result by rememberRetainedExecutionResult("${context.action.canonicalId}:photo")
        var hadResult by rememberSaveable { mutableStateOf(false) }
        var status by rememberSaveable { mutableStateOf("Choose or capture an image, then mark regions to remove.") }
        var pendingCameraUriString by rememberSaveable { mutableStateOf<String?>(null) }
        var captureStarted by rememberSaveable { mutableStateOf(false) }
        val viewport = rememberRedactionViewportState(sourceUriString)
        var expandedPhoto by rememberSaveable(sourceUriString) { mutableStateOf(false) }
        val runtimeSettingsVisible = listOf(
            "input_source",
            "source",
            "grid_rows",
            "grid_columns",
            "redaction_style"
        ).any(context::settingIsRuntimeInput)

        fun load(uri: Uri, source: String) {
            runCatching {
                decodeOrientedBitmap(appContext, uri)
                    ?: error("Could not decode image")
            }.onSuccess {
                sourceUriString = uri.toString()
                bitmap = it
                loadedPhotoSource = source
                selectedValue = ""
                result = null
                hadResult = false
                status = "Image loaded. Tap or swipe cells to toggle redaction."
            }.onFailure {
                status = "Image load failed: ${it.message ?: "unknown error"}"
            }
        }

        LaunchedEffect(sourceUriString) {
            if (hadResult && result == null) {
                hadResult = false
                status = "Committed result unavailable. Review the mask and create a new redacted photo."
            }
            if (result == null && bitmap == null && !sourceUriString.isNullOrBlank()) {
                runCatching { decodeOrientedBitmap(appContext, Uri.parse(sourceUriString)) }
                    .onSuccess { bitmap = it }
            }
        }

        val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) load(uri, "file_picker") else status = "No saved photo selected."
        }
        val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            val uri = pendingCameraUriString?.let(Uri::parse)
            if (ok && uri != null) load(uri, "camera") else status = "Camera capture cancelled."
        }

        fun launchCameraCapture() {
            runCatching {
                val file = File(appContext.cacheDir, "methodmesh-redaction-source-${System.currentTimeMillis()}.jpg")
                val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
                pendingCameraUriString = uri.toString()
                camera.launch(uri)
            }.onFailure { status = "Camera unavailable: ${it.message ?: "try choosing a saved photo"}" }
        }
        val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) launchCameraCapture()
            else status = "Camera permission denied. Allow camera access and retry, or use a saved photo."
        }
        fun startCamera() {
            captureStarted = true
            if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchCameraCapture()
            else cameraPermission.launch(Manifest.permission.CAMERA)
        }

        fun startConfiguredSource() {
            captureStarted = true
            when (settings.getString("input_source")) {
                "file_picker" -> {
                    status = "Choose an image to redact."
                    picker.launch("image/*")
                }
                "camera" -> {
                    status = "Opening camera…"
                    startCamera()
                }
                else -> status = "Unknown photo source. Choose a new or saved photo."
            }
        }

        LaunchedEffect(context.startsImmediately, runtimeSettingsVisible) {
            if (context.startsImmediately && !runtimeSettingsVisible && !captureStarted && bitmap == null && result == null) {
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
            val finalJpegBytes = ByteArrayOutputStream().use { stream ->
                out.compress(Bitmap.CompressFormat.JPEG, 95, stream)
                stream.toByteArray()
            }
            val finalJpegSha256 = ImageRedactionHash.sha256Hex(finalJpegBytes)
            val file = File(appContext.cacheDir, "methodmesh-redacted-${System.currentTimeMillis()}.jpg")
            file.writeBytes(finalJpegBytes)
            val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file).toString()
            val maskJson = JSONArray(selected.toList().sorted()).toString()
            val createdAt = Instant.now().toString()
            val values = linkedMapOf(
                ImageRedactionFields.STATUS to "succeeded",
                ImageRedactionFields.REDACTED_IMAGE_URI to uri,
                ImageRedactionFields.REDACTED_IMAGE_NAME to file.name,
                ImageRedactionFields.REDACTED_IMAGE_SHA256 to finalJpegSha256,
                ImageRedactionFields.MASK_JSON to maskJson,
                ImageRedactionFields.SELECTED_CELLS to selected.size.toString(),
                ImageRedactionFields.GRID_ROWS to rows.toString(),
                ImageRedactionFields.GRID_COLUMNS to cols.toString(),
                ImageRedactionFields.STYLE to settings.getString("redaction_style"),
                ImageRedactionFields.SOURCE to loadedPhotoSource,
                ImageRedactionFields.CREATED_TIME_ISO to createdAt,
                ImageRedactionFields.ERROR to ""
            )
            result = As100ImageRedactionMethod.result(
                As100ImageRedactionMethod.request(capabilityId, context.request.invocationContext.asMap(capabilityId) + values),
                values, context.request.invocationContext)
            hadResult = true
            status = "Redacted image created. Original image is not returned."
        }

        CapabilityScreenScaffold(
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = result,
            // The photo is the practical result. Passing the hash here makes generic Share
            // manufacture a hash-only text sidecar and turns a JPEG into a mixed-file share.
            // Keep complete evidence in the unchanged execution/FULL JSON, not default Share.
            resultPreview = result?.observations?.singleOrNull()?.values
                ?.get(ImageRedactionFields.REDACTED_IMAGE_URI)
                ?.let { mapOf(ImageRedactionFields.REDACTED_IMAGE_URI to it) }.orEmpty(),
            onBack = onBack,
            onRetry = {
                bitmap = null
                sourceUriString = null
                selectedValue = ""
                result = null
                hadResult = false
                captureStarted = false
                status = "Choose or capture an image, then mark regions to remove."
                if (context.startsImmediately && !runtimeSettingsVisible) startConfiguredSource()
            },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Text(status, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            if (bitmap == null && result == null && captureStarted) {
                OutlinedButton(onClick = ::startConfiguredSource, modifier = Modifier.fillMaxWidth()) { Text("Try photo again") }
            }
            if (!context.startsImmediately || runtimeSettingsVisible) {
                if (!context.settingIsFixedInNativePreset("input_source") || context.settingIsRuntimeInput("source")) {
                    Button(onClick = { settings.setString("input_source", "camera"); startCamera() }, modifier = Modifier.fillMaxWidth()) { Text("Take new photo") }
                    OutlinedButton(onClick = { settings.setString("input_source", "file_picker"); picker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) { Text("Choose saved photo") }
                }
                if (!context.settingIsFixedInNativePreset("grid_rows") || !context.settingIsFixedInNativePreset("grid_columns")) {
                Row(Modifier.fillMaxWidth()) {
                    if (!context.settingIsFixedInNativePreset("grid_rows")) {
                    OutlinedTextField(
                        value = settings.getInt("grid_rows").toString(),
                        onValueChange = { it.toIntOrNull()?.let { value -> settings.setInt("grid_rows", value.coerceIn(1, 50)); selectedValue = "" } },
                        label = { Text("Rows") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    }
                    if (!context.settingIsFixedInNativePreset("grid_rows") && !context.settingIsFixedInNativePreset("grid_columns")) {
                    Spacer(Modifier.padding(4.dp))
                    }
                    if (!context.settingIsFixedInNativePreset("grid_columns")) {
                    OutlinedTextField(
                        value = settings.getInt("grid_columns").toString(),
                        onValueChange = { it.toIntOrNull()?.let { value -> settings.setInt("grid_columns", value.coerceIn(1, 50)); selectedValue = "" } },
                        label = { Text("Columns") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    }
                }
                }
                if (!context.settingIsFixedInNativePreset("redaction_style")) {
                OutlinedButton(onClick = { settings.setString("redaction_style", if (settings.getString("redaction_style") == "black") "white" else "black") }, modifier = Modifier.fillMaxWidth()) {
                    Text("Mask style: ${settings.getString("redaction_style")}")
                }
                }
                if (context.awaitingRuntimeInputs && runtimeSettingsVisible && bitmap == null &&
                    context.settingIsFixedInNativePreset("input_source") && !context.settingIsRuntimeInput("source")) {
                    Button(onClick = {
                        captureStarted = true
                        startConfiguredSource()
                    }, modifier = Modifier.fillMaxWidth()) { Text("Open image") }
                }
            }
            bitmap?.let { image ->
                val marks = RedactionMarks.Grid(settings.getInt("grid_rows").coerceIn(1, 50), settings.getInt("grid_columns").coerceIn(1, 50), selected) {
                    selectedValue = it.toList().sorted().joinToString("|")
                }
                RedactionViewport(image, viewport, "Image to redact", marks, Modifier.fillMaxWidth().height(600.dp))
                OutlinedButton(onClick = { expandedPhoto = true }, modifier = Modifier.fillMaxWidth()) { Text("Expand photo") }
                if (expandedPhoto) Dialog(onDismissRequest = { expandedPhoto = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().padding(12.dp)) {
                        RedactionViewport(image, viewport, "Image to redact", marks, Modifier.fillMaxWidth().weight(1f))
                        Button(onClick = { expandedPhoto = false }, modifier = Modifier.fillMaxWidth()) { Text("Close expanded photo") }
                    }
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
