package com.example.methodmesh.modules.filelab

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object FileConvertCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100FileConvertMethod.ID
    override val title = "Convert file"
    override val description = "Convert between explicitly supported local formats without modifying the original."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val appContext = LocalContext.current
        val scope = rememberCoroutineScope()
        val suppliedUri = context.action.settingConvert("source_uri") ?: context.request.settingConvert("source_uri")
        val suppliedName = context.action.settingConvert("source_name") ?: context.request.settingConvert("source_name")

        var sourceUri by rememberSaveable(context.action.canonicalId) { mutableStateOf(suppliedUri.orEmpty()) }
        var sourceName by rememberSaveable(context.action.canonicalId) { mutableStateOf(suppliedName.orEmpty()) }
        var inspectionJson by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
        var targetFormat by rememberSaveable(context.action.canonicalId) { mutableStateOf(context.action.settingConvert("target_format") ?: "pdf") }
        var maxDimension by rememberSaveable(context.action.canonicalId) { mutableStateOf(context.action.settingConvert("max_dimension") ?: "2400") }
        var jpegQuality by rememberSaveable(context.action.canonicalId) { mutableStateOf(context.action.settingConvert("jpeg_quality") ?: "92") }
        var workingArtifactJson by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
        var committedValuesJson by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
        var running by remember { mutableStateOf(false) }
        var targetMenu by rememberSaveable { mutableStateOf(false) }
        var status by rememberSaveable(context.action.canonicalId) { mutableStateOf("Choose a file to convert.") }
        var pendingSaveUri by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }

        val inspection = remember(inspectionJson) { FileLabJson.inspectionFromJson(inspectionJson) }
        val workingArtifact = remember(workingArtifactJson) { workingArtifactJson.toConversionArtifact() }
        val committedValues = remember(committedValuesJson) { committedValuesJson.toConvertStringMap() }
        val committedResult = remember(committedValuesJson, context.request.invocationContext, context.action.settings) {
            if (committedValues.isEmpty()) null else {
                val request = As100FileConvertMethod.request(
                    action = As100FileConvertMethod.ID,
                    context = context.request.invocationContext.asMap(As100FileConvertMethod.ID) + context.action.settings + committedValues
                )
                As100FileConvertMethod.result(request, committedValues, committedValues[FileConvertFields.STATUS] == "succeeded")
            }
        }

        val currentSettings = mapOf(
            "target_format" to targetFormat,
            "max_dimension" to maxDimension,
            "jpeg_quality" to jpegQuality
        )
        LaunchedEffect(targetFormat, maxDimension, jpegQuality) { context.onSettingsChanged(currentSettings) }

        fun inspectSource(uriString: String, nameHint: String = sourceName) {
            if (uriString.isBlank()) return
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) { FileLabAndroid.inspect(appContext, Uri.parse(uriString), nameHint.takeIf { it.isNotBlank() }) }
                }.onSuccess { value ->
                    sourceName = value.displayName
                    inspectionJson = FileLabJson.inspectionToJson(value)
                    workingArtifactJson = ""
                    committedValuesJson = ""
                    val routes = ConversionGraph.from(value.formatId)
                    if (routes.size == 1) targetFormat = routes.first().targetFormat
                    status = if (routes.isEmpty()) "Recognised ${value.formatName}, but no conversion route is implemented." else "Ready to convert ${value.formatName}."
                }.onFailure { error ->
                    inspectionJson = ""
                    status = error.message ?: "Could not inspect the source file."
                }
            }
        }

        fun runConversion() {
            val currentInspection = inspection ?: return
            if (sourceUri.isBlank() || running) return
            val route = ConversionGraph.find(currentInspection.formatId, targetFormat)
            if (route == null) {
                status = "No executable route from ${currentInspection.formatId} to $targetFormat."
                return
            }
            val dimension = maxDimension.toIntOrNull()?.coerceIn(256, 12000) ?: 2400
            val quality = jpegQuality.toIntOrNull()?.coerceIn(1, 100) ?: 92
            running = true
            committedValuesJson = ""
            status = "Converting…"
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        FileLabAndroid.convert(appContext, Uri.parse(sourceUri), sourceName, targetFormat, dimension, quality)
                    }
                }.onSuccess { artifact ->
                    workingArtifactJson = artifact.toJson()
                    status = "Conversion ready. Verify the output, then Commit."
                }.onFailure { error ->
                    workingArtifactJson = ""
                    status = error.message ?: "Conversion failed."
                }
                running = false
            }
        }

        val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { destination: Uri? ->
            val source = pendingSaveUri.takeIf { it.isNotBlank() } ?: return@rememberLauncherForActivityResult
            if (destination == null) return@rememberLauncherForActivityResult
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { FileLabAndroid.copyUri(appContext, Uri.parse(source), destination) } }
                    .onSuccess { status = "Converted file saved." }
                    .onFailure { error -> status = error.message ?: "Could not save converted file." }
            }
        }

        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri ?: return@rememberLauncherForActivityResult
            FileLabAndroid.tryTakeReadPermission(appContext, uri)
            sourceUri = uri.toString()
            sourceName = ""
            inspectionJson = ""
            workingArtifactJson = ""
            committedValuesJson = ""
            inspectSource(sourceUri, "")
        }

        LaunchedEffect(suppliedUri) {
            if (sourceUri.isNotBlank() && inspectionJson.isBlank()) inspectSource(sourceUri, suppliedName.orEmpty())
        }

        CapabilityScreenScaffold(
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = committedResult,
            resultPreview = committedValues.mapValues { it.value as Any? },
            onBack = onBack,
            onRetry = { committedValuesJson = ""; workingArtifactJson = "" },
            onConfirm = { committedResult?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Text(
                "The original is never modified. Only routes present in the executable conversion graph are offered.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (sourceUri.isBlank()) "Choose source file" else "Choose another source")
            }

            inspection?.let { value ->
                Spacer(Modifier.height(12.dp))
                FileLabValue("Detected source", "${value.formatName} · ${value.sizeBytes} bytes")
                Spacer(Modifier.height(8.dp))
                val routes = ConversionGraph.from(value.formatId)
                if (routes.isNotEmpty()) {
                    if (context.settingShouldBeShown("target_format")) {
                        Text("Target format", style = MaterialTheme.typography.labelLarge)
                        Box {
                            OutlinedButton(onClick = { targetMenu = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(targetFormat.uppercase(Locale.ROOT))
                            }
                            DropdownMenu(expanded = targetMenu, onDismissRequest = { targetMenu = false }) {
                                routes.forEach { route ->
                                    DropdownMenuItem(
                                        text = { Text(route.targetFormat.uppercase(Locale.ROOT)) },
                                        onClick = {
                                            targetFormat = route.targetFormat
                                            targetMenu = false
                                            workingArtifactJson = ""
                                            committedValuesJson = ""
                                        }
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    if (value.formatId == "pdf" || ConversionGraph.isImageToPdf(value.formatId)) {
                        if (context.settingShouldBeShown("max_dimension")) {
                            OutlinedTextField(
                                value = maxDimension,
                                onValueChange = { maxDimension = it.filter(Char::isDigit).take(5); workingArtifactJson = ""; committedValuesJson = "" },
                                label = { Text("Maximum raster dimension (px)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        if (value.formatId == "pdf" && context.settingShouldBeShown("jpeg_quality")) {
                            OutlinedTextField(
                                value = jpegQuality,
                                onValueChange = { jpegQuality = it.filter(Char::isDigit).take(3); workingArtifactJson = ""; committedValuesJson = "" },
                                label = { Text("JPEG quality (1–100)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    Button(onClick = ::runConversion, enabled = !running, modifier = Modifier.fillMaxWidth()) {
                        Text(if (running) "Converting…" else "Convert")
                    }
                }
            }

            if (running) {
                Spacer(Modifier.height(10.dp))
                CircularProgressIndicator()
            }
            Spacer(Modifier.height(8.dp))
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            workingArtifact?.let { artifact ->
                Spacer(Modifier.height(16.dp))
                FileLabSectionTitle("Live output")
                Spacer(Modifier.height(8.dp))
                FileLabValue("Conversion", "${artifact.sourceFormat.uppercase(Locale.ROOT)} → ${artifact.targetFormat.uppercase(Locale.ROOT)}")
                Spacer(Modifier.height(6.dp))
                FileLabValue("Output", artifact.outputName)
                Spacer(Modifier.height(6.dp))
                FileLabValue("Size", "${artifact.outputSizeBytes} bytes")
                Spacer(Modifier.height(6.dp))
                FileLabValue("SHA-256", artifact.outputSha256, monospace = true)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { FileLabAndroid.openReader(appContext, Uri.parse(artifact.outputUri), artifact.outputMimeType) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Open") }
                    OutlinedButton(
                        onClick = { pendingSaveUri = artifact.outputUri; saveLauncher.launch(artifact.outputName) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Save file") }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { FileLabAndroid.shareOutput(appContext, Uri.parse(artifact.outputUri), artifact.outputMimeType) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Share") }
                    Button(
                        onClick = {
                            val values = As100FileConvertMethod.values(
                                sourceName = sourceName,
                                artifact = artifact
                            )
                            committedValuesJson = JSONObject(values).toString()
                        },
                        enabled = committedValuesJson.isBlank(),
                        modifier = Modifier.weight(1f)
                    ) { Text(if (committedValuesJson.isBlank()) "Commit" else "Committed") }
                }
            }
        }
    }
}

private fun com.example.methodmesh.transport.workflow.ExternalActionRequest.settingConvert(key: String): String? =
    (settings[key] ?: settings["input_$key"])?.takeIf { it.isNotBlank() }

private fun com.example.methodmesh.transport.workflow.ExternalWorkflowRequest.settingConvert(key: String): String? =
    (settings[key] ?: settings["input_$key"])?.takeIf { it.isNotBlank() }

private fun ConversionArtifact.toJson(): String = JSONObject().apply {
    put("source_format", sourceFormat)
    put("target_format", targetFormat)
    put("output_name", outputName)
    put("output_uri", outputUri)
    put("output_mime_type", outputMimeType)
    put("output_size_bytes", outputSizeBytes)
    put("output_sha256", outputSha256)
    put("warnings", JSONArray(warnings))
}.toString()

private fun String.toConversionArtifact(): ConversionArtifact? = if (isBlank()) null else runCatching {
    val json = JSONObject(this)
    val warningsJson = json.optJSONArray("warnings")
    val warnings = buildList {
        if (warningsJson != null) for (i in 0 until warningsJson.length()) add(warningsJson.optString(i))
    }
    ConversionArtifact(
        sourceFormat = json.optString("source_format"),
        targetFormat = json.optString("target_format"),
        outputName = json.optString("output_name"),
        outputUri = json.optString("output_uri"),
        outputMimeType = json.optString("output_mime_type"),
        outputSizeBytes = json.optLong("output_size_bytes"),
        outputSha256 = json.optString("output_sha256"),
        warnings = warnings
    )
}.getOrNull()

private fun String.toConvertStringMap(): Map<String, String> {
    if (isBlank()) return emptyMap()
    return runCatching {
        val json = JSONObject(this)
        linkedMapOf<String, String>().apply {
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                put(key, json.optString(key, ""))
            }
        }
    }.getOrDefault(emptyMap())
}
