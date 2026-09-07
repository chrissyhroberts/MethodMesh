package com.example.methodmesh.modules.trustedtimestamp

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityPresentationMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

object TrustedTimestampCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100TrustedTimestampMethod.ID
    override val title = "Trusted timestamp"
    override val description =
        "Create portable proof that exact content existed no later than a trusted time."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val androidContext = LocalContext.current
        val resolver = androidContext.contentResolver
        val scope = rememberCoroutineScope()

        val suppliedText =
            context.action.settings["input_text"]
                ?: context.action.settings["input_input_text"]
                ?: context.action.settings["text"]
                ?: ""

        var sourceMode by rememberSaveable {
            mutableStateOf(if (suppliedText.isNotEmpty()) "text" else "file")
        }
        var selectedUriString by rememberSaveable { mutableStateOf("") }
        var selectedName by rememberSaveable { mutableStateOf("") }
        var text by rememberSaveable { mutableStateOf(suppliedText) }
        var tsaUrl by rememberSaveable {
            mutableStateOf(
                context.action.settings["tsa_url"]
                    ?: context.action.settings["input_tsa_url"]
                    ?: TrustedTimestampAuthorities.FREETSA.endpoint
            )
        }
        var timeoutMs by rememberSaveable {
            mutableStateOf(
                context.action.settings["timeout_ms"]
                    ?: context.action.settings["input_timeout_ms"]
                    ?: "10000"
            )
        }
        var status by rememberSaveable { mutableStateOf("Choose a file or enter text.") }
        var fileActionStatus by rememberSaveable { mutableStateOf("") }
        var busy by rememberSaveable { mutableStateOf(false) }
        var proofFileName by rememberSaveable { mutableStateOf("") }
        var proofUri by rememberSaveable { mutableStateOf("") }
        var proofCachePath by rememberSaveable { mutableStateOf("") }
        var savedResultValuesJson by rememberSaveable { mutableStateOf("") }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var launchedAutomatically by rememberSaveable(context.action.canonicalId) {
            mutableStateOf(false)
        }

        val includeFullJson =
            (
                context.action.settings["include_full_json"]
                    ?: context.action.settings["input_include_full_json"]
                    ?: "false"
                ).equals("true", ignoreCase = true)

        val selectedUri = selectedUriString
            .takeIf { it.isNotBlank() }
            ?.let(Uri::parse)

        fun executionRequest() =
            As100TrustedTimestampMethod.request(
                action = As100TrustedTimestampMethod.ID,
                context =
                    context.request.invocationContext.asMap(As100TrustedTimestampMethod.ID) +
                        context.action.settings +
                        mapOf(
                            "input_text" to text,
                            "tsa_url" to tsaUrl,
                            "timeout_ms" to timeoutMs,
                            "include_full_json" to includeFullJson.toString()
                        ),
                signals = emptyList(),
                inputs = emptyList()
            )

        fun restoreResultFromJson(json: String): ExecutionResult? =
            runCatching {
                val obj = JSONObject(json)
                val values = linkedMapOf<String, String>()
                TrustedTimestampFields.outputs.forEach { key ->
                    if (obj.has(key)) {
                        values[key] = obj.optString(key, "")
                    }
                }
                As100TrustedTimestampMethod.result(
                    executionRequest(),
                    values,
                    context.request.invocationContext
                )
            }.getOrNull()

        LaunchedEffect(savedResultValuesJson) {
            if (result == null && savedResultValuesJson.isNotBlank()) {
                result = restoreResultFromJson(savedResultValuesJson)
            }
        }

        LaunchedEffect(text, tsaUrl, timeoutMs) {
            context.onSettingsChanged(
                mapOf(
                    "input_text" to text,
                    "tsa_url" to tsaUrl,
                    "timeout_ms" to timeoutMs
                )
            )
        }

        val filePicker =
            rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                selectedUriString = uri?.toString().orEmpty()
                selectedName =
                    uri?.let {
                        resolver.query(
                            it,
                            arrayOf(OpenableColumns.DISPLAY_NAME),
                            null,
                            null,
                            null
                        )?.use { cursor ->
                            if (cursor.moveToFirst()) cursor.getString(0) else null
                        }
                    }.orEmpty()

                status =
                    if (uri == null) {
                        "No file selected."
                    } else {
                        "Ready to timestamp $selectedName."
                    }
            }

        val zipSaver =
            rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/zip")
            ) { destination ->
                if (destination != null && proofCachePath.isNotBlank()) {
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                val cachedProof = File(proofCachePath)
                                require(cachedProof.exists()) {
                                    "The temporary proof file is no longer available."
                                }
                                resolver.openOutputStream(destination, "w").use { output ->
                                    requireNotNull(output) {
                                        "Unable to open the selected destination."
                                    }
                                    cachedProof.inputStream().use { input ->
                                        input.copyTo(output)
                                    }
                                }
                            }
                        }.onSuccess {
                            status = "Proof package saved as $proofFileName."
                        }.onFailure { error ->
                            status =
                                "Could not save proof package: " +
                                    (error.message ?: error::class.java.simpleName)
                        }
                    }
                }
            }

        fun currentProofFile(): File {
            require(proofCachePath.isNotBlank()) { "No proof ZIP has been created yet." }
            val file = File(proofCachePath)
            require(file.exists()) { "The temporary proof ZIP is no longer available." }
            return file
        }

        fun shareProofZip() {
            runCatching {
                val file = currentProofFile()
                val uri = FileProvider.getUriForFile(
                    androidContext,
                    "${androidContext.packageName}.fileprovider",
                    file
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TITLE, proofFileName)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    clipData = android.content.ClipData.newRawUri(proofFileName, uri)
                }
                androidContext.startActivity(
                    Intent.createChooser(intent, "Share proof ZIP")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.onSuccess {
                fileActionStatus = "Sharing $proofFileName."
            }.onFailure { error ->
                fileActionStatus = "Share failed: ${error.message ?: "no sharing app available"}"
            }
        }

        fun exportProofZipToDownloads() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                // On Android 8/9, use the Storage Access Framework instead of
                // requesting broad legacy storage permission.
                fileActionStatus = "Choose where to save the proof ZIP."
                zipSaver.launch(proofFileName)
                return
            }

            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        val source = currentProofFile()
                        val values = ContentValues().apply {
                            put(MediaStore.Downloads.DISPLAY_NAME, proofFileName)
                            put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                            put(
                                MediaStore.Downloads.RELATIVE_PATH,
                                Environment.DIRECTORY_DOWNLOADS + "/MethodMesh"
                            )
                            put(MediaStore.Downloads.IS_PENDING, 1)
                        }

                        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                        val destination = resolver.insert(collection, values)
                            ?: error("Android could not create the Downloads file.")

                        try {
                            resolver.openOutputStream(destination, "w").use { output ->
                                requireNotNull(output) { "Could not open the Downloads file." }
                                source.inputStream().use { input ->
                                    input.copyTo(output)
                                }
                            }
                            values.clear()
                            values.put(MediaStore.Downloads.IS_PENDING, 0)
                            resolver.update(destination, values, null, null)
                            destination
                        } catch (error: Throwable) {
                            resolver.delete(destination, null, null)
                            throw error
                        }
                    }
                }.onSuccess {
                    fileActionStatus =
                        "Exported $proofFileName to Downloads/MethodMesh."
                }.onFailure { error ->
                    fileActionStatus =
                        "Export failed: ${error.message ?: "storage error"}"
                }
            }
        }

        fun createProof() {
            if (busy) return

            busy = true
            status = "Hashing locally…"

            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        val sourceBundle =
                            if (sourceMode == "text") {
                                require(text.isNotEmpty()) {
                                    "Enter text to timestamp."
                                }
                                val textBytes = text.toByteArray(Charsets.UTF_8)
                                val (size, digest) =
                                    TrustedTimestampEngine.hashTextUtf8(text)
                                SourceBundle(
                                    name = "timestamped-text.txt",
                                    size = size,
                                    digest = digest,
                                    textBytes = textBytes
                                )
                            } else {
                                val uri =
                                    requireNotNull(selectedUri) {
                                        "Choose a file to timestamp."
                                    }
                                val (size, digest) =
                                    TrustedTimestampEngine.hashUri(resolver, uri)
                                SourceBundle(
                                    name = selectedName.ifBlank { "content" },
                                    size = size,
                                    digest = digest,
                                    textBytes = null
                                )
                            }

                        val source =
                            TimestampSource(
                                displayName = sourceBundle.name,
                                sizeBytes = sourceBundle.size,
                                sha256 = sourceBundle.digest.toHex()
                            )

                        val timeout =
                            timeoutMs.toIntOrNull()
                                ?.coerceIn(1000, 30000)
                                ?: 10000

                        val (request, responseDer) =
                            TrustedTimestampEngine.requestTimestamp(
                                digest = sourceBundle.digest,
                                authorityUrl = tsaUrl.trim(),
                                timeoutMs = timeout
                            )

                        val evidence =
                            TrustedTimestampEngine.parseAndValidate(
                                request = request,
                                responseDer = responseDer,
                                digest = sourceBundle.digest,
                                authorityUrl = tsaUrl.trim(),
                                timeoutMs = timeout
                            )

                        val proofBytes =
                            TrustedTimestampEngine.createProofZip(
                                source = source,
                                evidence = evidence,
                                originalTextUtf8 = sourceBundle.textBytes
                            )

                        val fileName = proofZipName(source.displayName)
                        val cacheFile = File(androidContext.cacheDir, fileName)
                        cacheFile.outputStream().use { it.write(proofBytes) }

                        val uri =
                            FileProvider.getUriForFile(
                                androidContext,
                                "${androidContext.packageName}.fileprovider",
                                cacheFile
                            ).toString()

                        BuiltProof(
                            source = source,
                            evidence = evidence,
                            fileName = fileName,
                            cachePath = cacheFile.absolutePath,
                            uri = uri
                        )
                    }
                }.onSuccess { built ->
                    proofFileName = built.fileName
                    proofCachePath = built.cachePath
                    proofUri = built.uri

                    val values =
                        linkedMapOf(
                            TrustedTimestampFields.STATUS to "succeeded",
                            TrustedTimestampFields.ORIGINAL_NAME to built.source.displayName,
                            TrustedTimestampFields.SIZE_BYTES to built.source.sizeBytes.toString(),
                            TrustedTimestampFields.SHA256 to built.source.sha256,
                            TrustedTimestampFields.TSA to built.evidence.authorityName,
                            TrustedTimestampFields.TIME_ISO to built.evidence.generationTimeIso,
                            TrustedTimestampFields.SERIAL to built.evidence.serialNumber,
                            TrustedTimestampFields.POLICY_OID to built.evidence.policyOid,
                            TrustedTimestampFields.TOKEN_SHA256 to built.evidence.tokenSha256,
                            TrustedTimestampFields.TRUST_STATUS to built.evidence.trustStatus,
                            TrustedTimestampFields.PROOF_FILENAME to built.fileName,
                            TrustedTimestampFields.PROOF_URI to built.uri,
                            TrustedTimestampFields.FULL_JSON to
                                if (includeFullJson) {
                                    ProofText.callerJson(
                                        built.source,
                                        built.evidence,
                                        built.fileName,
                                        built.uri
                                    )
                                } else {
                                    ""
                                },
                            TrustedTimestampFields.ERROR to ""
                        )

                    savedResultValuesJson =
                        JSONObject(values as Map<*, *>).toString()

                    result =
                        As100TrustedTimestampMethod.result(
                            executionRequest(),
                            values,
                            context.request.invocationContext
                        )

                    status = "Proof created: ${built.fileName}"
                    busy = false
                }.onFailure { error ->
                    val message =
                        error.message ?: error::class.java.simpleName
                    status = "Timestamp failed: $message"
                    busy = false

                    val values =
                        linkedMapOf(
                            TrustedTimestampFields.STATUS to "failed",
                            TrustedTimestampFields.ERROR to message
                        )
                    savedResultValuesJson =
                        JSONObject(values as Map<*, *>).toString()
                    result =
                        As100TrustedTimestampMethod.result(
                            executionRequest(),
                            values,
                            context.request.invocationContext
                        )
                }
            }
        }

        LaunchedEffect(
            context.startsImmediately,
            context.presentationMode,
            suppliedText
        ) {
            val hasExternalText = suppliedText.isNotEmpty()
            if (
                context.startsImmediately &&
                hasExternalText &&
                !launchedAutomatically
            ) {
                launchedAutomatically = true
                createProof()
            }
        }

        CapabilityScreenScaffold(
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = result,
            resultPreview = emptyMap(),
            onBack = onBack,
            onRetry = {
                result = null
                savedResultValuesJson = ""
                createProof()
            },
            onConfirm = {
                result?.let(onConfirmed)
            },
            onCancel = onCancel
        ) {
            Text(
                "The source stays on this device. Only its SHA-256 digest and RFC 3161 protocol metadata are sent to the Timestamp Authority.",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(10.dp))

            if (sourceMode == "file") {
                Button(
                    onClick = {
                        filePicker.launch(arrayOf("*/*"))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (selectedName.isBlank()) {
                            "Choose file"
                        } else {
                            "Choose another file"
                        }
                    )
                }

                if (selectedName.isNotBlank()) {
                    Text(
                        selectedName,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                OutlinedButton(
                    onClick = {
                        sourceMode = "text"
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Timestamp pasted text instead")
                }
            } else {
                if (context.settingShouldBeShown("input_text")) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = {
                            Text("Exact text to timestamp")
                        },
                        supportingText = {
                            Text(
                                "Timestamped as exact UTF-8 bytes; no Unicode normalization is applied."
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 6
                    )
                } else {
                    Text(
                        "Text supplied by preset.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (context.presentationMode != CapabilityPresentationMode.IntentLaunch) {
                    OutlinedButton(
                        onClick = {
                            sourceMode = "file"
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Timestamp a file instead")
                    }
                }
            }

            if (context.settingShouldBeShown("tsa_url")) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = tsaUrl,
                    onValueChange = { tsaUrl = it },
                    label = {
                        Text("Timestamp Authority URL")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            Spacer(Modifier.height(10.dp))

            Button(
                onClick = { createProof() },
                enabled =
                    !busy &&
                        (
                            (sourceMode == "text" && text.isNotEmpty()) ||
                                (sourceMode == "file" && selectedUri != null)
                            ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (busy) {
                        "Creating proof…"
                    } else {
                        "Create proof of existence"
                    }
                )
            }

            if (proofCachePath.isNotBlank() && File(proofCachePath).exists()) {
                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = { shareProofZip() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Share proof ZIP")
                }

                OutlinedButton(
                    onClick = { zipSaver.launch(proofFileName) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save proof ZIP…")
                }

                OutlinedButton(
                    onClick = { exportProofZipToDownloads() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Export ZIP to Downloads")
                }

                Text(
                    "Proof file: $proofFileName",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                status,
                style = MaterialTheme.typography.bodySmall
            )

            if (fileActionStatus.isNotBlank()) {
                Text(
                    fileActionStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private data class SourceBundle(
    val name: String,
    val size: Long,
    val digest: ByteArray,
    val textBytes: ByteArray?
)

private data class BuiltProof(
    val source: TimestampSource,
    val evidence: TrustedTimestampEvidence,
    val fileName: String,
    val cachePath: String,
    val uri: String
)
