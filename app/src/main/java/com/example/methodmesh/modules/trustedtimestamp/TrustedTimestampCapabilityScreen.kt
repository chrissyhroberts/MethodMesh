package com.example.methodmesh.modules.trustedtimestamp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.methodmesh.core.methodmesh.ExecutionResult
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
        val externalRoundtrip =
            context.presentationMode == CapabilityPresentationMode.IntentLaunch

        // Capture launch-supplied inputs once. Interactive edits are written back
        // through onSettingsChanged, so re-reading the mutable action settings
        // would incorrectly reclassify a MethodMesh-acquired source as caller-supplied.
        val suppliedText by rememberSaveable(context.action.canonicalId) {
            mutableStateOf(
                firstSetting(
                    context,
                    "input_text",
                    "input_input_text",
                    "text"
                ).orEmpty()
            )
        }

        val suppliedFile by rememberSaveable(context.action.canonicalId) {
            mutableStateOf(
                firstSetting(
                    context,
                    "input_file",
                    "input_input_file",
                    "file",
                    "source_file"
                ).orEmpty()
            )
        }

        val suppliedTsaUrl by rememberSaveable(context.action.canonicalId) {
            mutableStateOf(
                firstSetting(
                    context,
                    "tsa_url",
                    "input_tsa_url"
                ).orEmpty()
            )
        }

        val suppliedTimeout by rememberSaveable(context.action.canonicalId) {
            mutableStateOf(
                firstSetting(
                    context,
                    "timeout_ms",
                    "input_timeout_ms"
                ).orEmpty()
            )
        }

        val hasSuppliedText = suppliedText.isNotEmpty()
        val hasSuppliedFile = suppliedFile.isNotBlank()
        val hasDirectSource = hasSuppliedText || hasSuppliedFile

        var sourceMode by rememberSaveable(context.action.canonicalId) {
            mutableStateOf(if (hasSuppliedText) "text" else "file")
        }
        var selectedUriString by rememberSaveable(context.action.canonicalId) {
            mutableStateOf(suppliedFile)
        }
        var selectedName by rememberSaveable(context.action.canonicalId) {
            mutableStateOf("")
        }
        var text by rememberSaveable(context.action.canonicalId) {
            mutableStateOf(suppliedText)
        }
        var tsaUrl by rememberSaveable(context.action.canonicalId) {
            mutableStateOf(
                suppliedTsaUrl.ifBlank { TrustedTimestampAuthorities.FREETSA.endpoint }
            )
        }
        var timeoutMs by rememberSaveable(context.action.canonicalId) {
            mutableStateOf(suppliedTimeout.ifBlank { "10000" })
        }
        var status by rememberSaveable(context.action.canonicalId) {
            mutableStateOf("Choose a file or enter text.")
        }
        var actionStatus by rememberSaveable(context.action.canonicalId) {
            mutableStateOf("")
        }
        var busy by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        var proofFileName by rememberSaveable(context.action.canonicalId) {
            mutableStateOf("")
        }
        var proofCachePath by rememberSaveable(context.action.canonicalId) {
            mutableStateOf("")
        }
        var savedResultValuesJson by rememberSaveable(context.action.canonicalId) {
            mutableStateOf("")
        }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var launchedAutomatically by rememberSaveable(context.action.canonicalId) {
            mutableStateOf(false)
        }
        var showDetails by rememberSaveable(context.action.canonicalId) {
            mutableStateOf(false)
        }
        var showOdkCard by rememberSaveable(context.action.canonicalId) {
            mutableStateOf(false)
        }

        val includeFullJson =
            (
                firstSetting(
                    context,
                    "include_full_json",
                    "input_include_full_json"
                ) ?: "false"
                ).equals("true", ignoreCase = true)

        val selectedUri = selectedUriString
            .takeIf { it.isNotBlank() }
            ?.let(::inputUri)

        val capturedValues = remember(savedResultValuesJson) {
            parseSavedValues(savedResultValuesJson)
        }

        fun invalidateWorkingProof(message: String? = null) {
            if (proofCachePath.isNotBlank()) {
                runCatching { File(proofCachePath).delete() }
            }
            proofFileName = ""
            proofCachePath = ""
            savedResultValuesJson = ""
            result = null
            if (message != null) status = message
        }

        fun executionRequest() =
            As100TrustedTimestampMethod.request(
                action = As100TrustedTimestampMethod.ID,
                context =
                    context.request.invocationContext.asMap(As100TrustedTimestampMethod.ID) +
                        context.action.settings +
                        mapOf(
                            "input_text" to text,
                            "input_file" to selectedUriString,
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

        LaunchedEffect(text, selectedUriString, tsaUrl, timeoutMs) {
            context.onSettingsChanged(
                mapOf(
                    "input_text" to text,
                    "input_file" to selectedUriString,
                    "tsa_url" to tsaUrl,
                    "timeout_ms" to timeoutMs
                )
            )
        }

        fun resolveDisplayName(uri: Uri): String =
            resolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }.orEmpty().ifBlank {
                uri.lastPathSegment?.substringAfterLast('/').orEmpty().ifBlank { "content" }
            }

        val filePicker =
            rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri == null) {
                    status = "No file selected."
                    return@rememberLauncherForActivityResult
                }

                invalidateWorkingProof()
                runCatching {
                    resolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                selectedUriString = uri.toString()
                selectedName = resolveDisplayName(uri)
                sourceMode = "file"
                status = "Ready to timestamp $selectedName."
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
                            actionStatus = "Saved $proofFileName."
                        }.onFailure { error ->
                            actionStatus =
                                "Save failed: ${error.message ?: error::class.java.simpleName}"
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
                    clipData = ClipData.newRawUri(proofFileName, uri)
                }
                androidContext.startActivity(
                    Intent.createChooser(intent, "Share proof ZIP")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.onSuccess {
                actionStatus = "Sharing $proofFileName."
            }.onFailure { error ->
                actionStatus = "Share failed: ${error.message ?: "no sharing app available"}"
            }
        }

        fun shareSourceFile() {
            val uri = selectedUri ?: return
            runCatching {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = resolver.getType(uri) ?: "*/*"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    clipData = ClipData.newRawUri(selectedName.ifBlank { "source" }, uri)
                }
                androidContext.startActivity(
                    Intent.createChooser(intent, "Share source file")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.onSuccess {
                actionStatus = "Sharing source file."
            }.onFailure { error ->
                actionStatus = "Share failed: ${error.message ?: "no sharing app available"}"
            }
        }

        fun shareSourceText() {
            if (text.isEmpty()) return
            runCatching {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                androidContext.startActivity(
                    Intent.createChooser(intent, "Share timestamped text")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.onSuccess {
                actionStatus = "Sharing timestamped text."
            }.onFailure { error ->
                actionStatus = "Share failed: ${error.message ?: "no sharing app available"}"
            }
        }

        fun exportProofZipToDownloads() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                actionStatus = "Choose where to save the proof ZIP."
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
                        val destination = requireNotNull(
                            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ) { "Unable to create a Downloads entry." }
                        try {
                            resolver.openOutputStream(destination, "w").use { output ->
                                requireNotNull(output) { "Unable to open Downloads output." }
                                source.inputStream().use { input -> input.copyTo(output) }
                            }
                            val ready = ContentValues().apply {
                                put(MediaStore.Downloads.IS_PENDING, 0)
                            }
                            resolver.update(destination, ready, null, null)
                        } catch (error: Throwable) {
                            resolver.delete(destination, null, null)
                            throw error
                        }
                    }
                }.onSuccess {
                    actionStatus = "Exported $proofFileName to Downloads/MethodMesh."
                }.onFailure { error ->
                    actionStatus =
                        "Export failed: ${error.message ?: error::class.java.simpleName}"
                }
            }
        }

        fun createProof() {
            if (busy) return

            busy = true
            actionStatus = ""
            status = "Hashing locally…"

            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        require(!(hasSuppliedText && hasSuppliedFile)) {
                            "Provide either input_text or input_file, not both."
                        }

                        val sourceWasSupplied =
                            (sourceMode == "text" && hasSuppliedText) ||
                                (sourceMode == "file" && hasSuppliedFile)
                        val sourceReturnedToCaller = externalRoundtrip && !sourceWasSupplied

                        val sourceBundle =
                            if (sourceMode == "text") {
                                require(text.isNotEmpty()) { "Enter text to timestamp." }
                                val (size, digest) = TrustedTimestampEngine.hashTextUtf8(text)
                                SourceBundle(
                                    name = "timestamped-text.txt",
                                    size = size,
                                    digest = digest,
                                    sourceUri = null,
                                    origin = if (sourceWasSupplied) {
                                        "supplied_text"
                                    } else {
                                        "methodmesh_text_entry"
                                    },
                                    returnSourceToCaller = sourceReturnedToCaller
                                )
                            } else {
                                val uri = requireNotNull(selectedUri) {
                                    "Choose a file to timestamp."
                                }
                                val (size, digest) = TrustedTimestampEngine.hashUri(resolver, uri)
                                val name = selectedName.ifBlank { resolveDisplayName(uri) }
                                SourceBundle(
                                    name = name,
                                    size = size,
                                    digest = digest,
                                    sourceUri = uri.toString(),
                                    origin = if (sourceWasSupplied) {
                                        "supplied_file"
                                    } else {
                                        "methodmesh_file_picker"
                                    },
                                    returnSourceToCaller = sourceReturnedToCaller
                                )
                            }

                        val source = TimestampSource(
                            displayName = sourceBundle.name,
                            sizeBytes = sourceBundle.size,
                            sha256 = sourceBundle.digest.toHex()
                        )

                        val timeout = timeoutMs.toIntOrNull()
                            ?.coerceIn(1000, 30000)
                            ?: 10000
                        val endpoint = tsaUrl.trim().ifBlank {
                            TrustedTimestampAuthorities.FREETSA.endpoint
                        }

                        val (request, responseDer) = TrustedTimestampEngine.requestTimestamp(
                            digest = sourceBundle.digest,
                            authorityUrl = endpoint,
                            timeoutMs = timeout
                        )

                        val evidence = TrustedTimestampEngine.parseAndValidate(
                            request = request,
                            responseDer = responseDer,
                            digest = sourceBundle.digest,
                            authorityUrl = endpoint,
                            timeoutMs = timeout
                        )

                        val proofBytes = TrustedTimestampEngine.createProofZip(
                            source = source,
                            evidence = evidence
                        )

                        val fileName = proofZipName(source.displayName)
                        val cacheFile = File(androidContext.cacheDir, fileName)
                        cacheFile.outputStream().use { it.write(proofBytes) }

                        val proofUri = FileProvider.getUriForFile(
                            androidContext,
                            "${androidContext.packageName}.fileprovider",
                            cacheFile
                        ).toString()

                        BuiltProof(
                            source = source,
                            evidence = evidence,
                            fileName = fileName,
                            cachePath = cacheFile.absolutePath,
                            proofUri = proofUri,
                            sourceUri = sourceBundle.sourceUri,
                            sourceText = if (sourceMode == "text") text else null,
                            sourceOrigin = sourceBundle.origin,
                            returnSourceToCaller = sourceBundle.returnSourceToCaller
                        )
                    }
                }.onSuccess { built ->
                    proofFileName = built.fileName
                    proofCachePath = built.cachePath

                    val values = linkedMapOf(
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
                        TrustedTimestampFields.SOURCE_URI to
                            if (built.returnSourceToCaller) built.sourceUri.orEmpty() else "",
                        TrustedTimestampFields.SOURCE_TEXT to
                            if (built.returnSourceToCaller) built.sourceText.orEmpty() else "",
                        TrustedTimestampFields.PROOF_FILENAME to built.fileName,
                        TrustedTimestampFields.PROOF_URI to built.proofUri,
                        TrustedTimestampFields.FULL_JSON to
                            if (includeFullJson) {
                                ProofText.callerJson(
                                    source = built.source,
                                    evidence = built.evidence,
                                    proofFileName = built.fileName,
                                    sourceOrigin = built.sourceOrigin,
                                    sourceReturnedToCaller = built.returnSourceToCaller
                                )
                            } else {
                                ""
                            },
                        TrustedTimestampFields.ERROR to ""
                    )

                    savedResultValuesJson = JSONObject(values as Map<*, *>).toString()
                    val executionResult = As100TrustedTimestampMethod.result(
                        executionRequest(),
                        values,
                        context.request.invocationContext
                    )
                    result = executionResult
                    status = "Proof created. Review the source and proof, then Commit."
                    busy = false

                    if (externalRoundtrip && context.startsImmediately && hasDirectSource) {
                        onConfirmed(executionResult)
                    }
                }.onFailure { error ->
                    val message = error.message ?: error::class.java.simpleName
                    status = "Timestamp failed: $message"
                    busy = false

                    val values = linkedMapOf(
                        TrustedTimestampFields.STATUS to "failed",
                        TrustedTimestampFields.ERROR to message
                    )
                    savedResultValuesJson = JSONObject(values as Map<*, *>).toString()
                    val executionResult = As100TrustedTimestampMethod.result(
                        executionRequest(),
                        values,
                        context.request.invocationContext
                    )
                    result = executionResult

                    if (externalRoundtrip && context.startsImmediately && hasDirectSource) {
                        onConfirmed(executionResult)
                    }
                }
            }
        }

        LaunchedEffect(
            context.startsImmediately,
            context.presentationMode,
            hasDirectSource
        ) {
            if (
                context.startsImmediately &&
                hasDirectSource &&
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
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Text(
                "${TrustedTimestampContractMetadata.MATURITY} · ${TrustedTimestampContractMetadata.CONNECTIVITY}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(6.dp))

            Text(
                "The source stays on this device. Only its SHA-256 digest and RFC 3161 protocol metadata are sent to the Timestamp Authority.",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(16.dp))
            SectionHeading("1", "Source")

            if (sourceMode == "file") {
                Button(
                    onClick = { filePicker.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (selectedUriString.isBlank()) "Choose file" else "Choose another file")
                }

                if (selectedUriString.isNotBlank()) {
                    val sourceLabel = selectedName.ifBlank {
                        if (hasSuppliedFile) "File supplied by caller" else "Selected file"
                    }
                    CopyableValue(
                        label = "Source file",
                        value = sourceLabel,
                        onCopy = {
                            copyText(androidContext, "Source file", sourceLabel)
                            actionStatus = "Source filename copied."
                        }
                    )
                    if (!externalRoundtrip) {
                        OutlinedButton(
                            onClick = { shareSourceFile() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Share source file")
                        }
                    }
                }

                if (!externalRoundtrip || !hasSuppliedFile) {
                    OutlinedButton(
                        onClick = {
                            invalidateWorkingProof("Enter exact text to timestamp.")
                            sourceMode = "text"
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Use text instead")
                    }
                }
            } else {
                if (context.settingShouldBeShown("input_text") || !hasSuppliedText) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = {
                            if (it != text) invalidateWorkingProof()
                            text = it
                        },
                        label = { Text("Exact text to timestamp") },
                        supportingText = {
                            Text("Exact UTF-8 bytes are timestamped; no Unicode normalisation is applied.")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 5
                    )
                } else {
                    Text("Text supplied by caller/preset.", style = MaterialTheme.typography.bodySmall)
                }

                if (text.isNotEmpty() && result != null) {
                    CopyableValue(
                        label = "Timestamped text",
                        value = text,
                        maxLines = 4,
                        onCopy = {
                            copyText(androidContext, "Timestamped text", text)
                            actionStatus = "Timestamped text copied."
                        }
                    )
                    if (!externalRoundtrip) {
                        OutlinedButton(
                            onClick = { shareSourceText() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Share source text")
                        }
                    }
                }

                if (!externalRoundtrip || !hasSuppliedText) {
                    OutlinedButton(
                        onClick = {
                            invalidateWorkingProof("Choose a source file to timestamp.")
                            sourceMode = "file"
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Use a file instead")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            SectionHeading("", "Timestamp authority")

            if (context.settingShouldBeShown("tsa_url")) {
                OutlinedTextField(
                    value = tsaUrl,
                    onValueChange = {
                        if (it != tsaUrl) invalidateWorkingProof()
                        tsaUrl = it
                    },
                    label = { Text("TSA URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            } else {
                CopyableValue(
                    label = "TSA URL",
                    value = tsaUrl,
                    onCopy = {
                        copyText(androidContext, "TSA URL", tsaUrl)
                        actionStatus = "TSA URL copied."
                    }
                )
            }

            if (context.settingShouldBeShown("timeout_ms")) {
                OutlinedTextField(
                    value = timeoutMs,
                    onValueChange = {
                        if (it != timeoutMs) invalidateWorkingProof()
                        timeoutMs = it.filter { ch -> ch.isDigit() }
                    },
                    label = { Text("Timeout (ms)") },
                    supportingText = { Text("1000–30000 ms") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            Spacer(Modifier.height(12.dp))

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
                Text(if (busy) "Creating proof…" else "Create proof of existence")
            }

            if (proofCachePath.isNotBlank() && File(proofCachePath).exists()) {
                Spacer(Modifier.height(18.dp))
                HorizontalDivider()
                Spacer(Modifier.height(14.dp))
                SectionHeading("2", "Trusted timestamp proof")

                CopyableValue(
                    label = "Proof ZIP",
                    value = proofFileName,
                    onCopy = {
                        copyText(androidContext, "Proof ZIP", proofFileName)
                        actionStatus = "Proof filename copied."
                    }
                )

                if (!externalRoundtrip) {
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
                } else {
                    Text(
                        "Commit returns the proof ZIP to ODK as a real attachment. If MethodMesh acquired the source, that source is returned too.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { showDetails = !showDetails },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (showDetails) "Hide technical details" else "Technical details")
                }

                if (showDetails) {
                    val detailRows = listOf(
                        "Trusted time" to capturedValues[TrustedTimestampFields.TIME_ISO].orEmpty(),
                        "Source SHA-256" to capturedValues[TrustedTimestampFields.SHA256].orEmpty(),
                        "Source bytes" to capturedValues[TrustedTimestampFields.SIZE_BYTES].orEmpty(),
                        "Timestamp authority" to capturedValues[TrustedTimestampFields.TSA].orEmpty(),
                        "Trust status" to capturedValues[TrustedTimestampFields.TRUST_STATUS].orEmpty(),
                        "RFC 3161 serial" to capturedValues[TrustedTimestampFields.SERIAL].orEmpty(),
                        "Policy OID" to capturedValues[TrustedTimestampFields.POLICY_OID].orEmpty(),
                        "Token SHA-256" to capturedValues[TrustedTimestampFields.TOKEN_SHA256].orEmpty()
                    )
                    detailRows.forEach { (label, value) ->
                        if (value.isNotBlank()) {
                            CopyableValue(
                                label = label,
                                value = value,
                                onCopy = {
                                    copyText(androidContext, label, value)
                                    actionStatus = "$label copied."
                                }
                            )
                        }
                    }

                    capturedValues[TrustedTimestampFields.FULL_JSON]
                        ?.takeIf { it.isNotBlank() }
                        ?.let { json ->
                            CopyableValue(
                                label = "Metadata JSON",
                                value = json,
                                maxLines = 4,
                                onCopy = {
                                    copyText(androidContext, "Trusted timestamp metadata JSON", json)
                                    actionStatus = "Metadata JSON copied."
                                }
                            )
                        }
                }
            }

            Spacer(Modifier.height(10.dp))
            if (result == null) {
                Text(status, style = MaterialTheme.typography.bodySmall)
            } else {
                val canonicalStatus =
                    capturedValues[TrustedTimestampFields.STATUS].orEmpty().ifBlank { status }
                CopyableValue(
                    label = "Status",
                    value = canonicalStatus,
                    onCopy = {
                        copyText(androidContext, "Trusted timestamp status", canonicalStatus)
                        actionStatus = "Status copied."
                    }
                )
                capturedValues[TrustedTimestampFields.ERROR]
                    ?.takeIf { it.isNotBlank() }
                    ?.let { errorText ->
                        CopyableValue(
                            label = "Error",
                            value = errorText,
                            maxLines = 4,
                            onCopy = {
                                copyText(androidContext, "Trusted timestamp error", errorText)
                                actionStatus = "Error copied."
                            }
                        )
                    }
            }

            if (actionStatus.isNotBlank()) {
                Text(
                    actionStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = { showOdkCard = !showOdkCard },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (showOdkCard) "Hide ODK integration" else "ODK integration")
            }

            if (showOdkCard) {
                OdkIntegrationCard(
                    onCopied = { actionStatus = it },
                    androidContext = androidContext
                )
            }
        }
    }
}

@Composable
private fun SectionHeading(index: String, title: String) {
    Text(
        if (index.isBlank()) title else "$index · $title",
        style = MaterialTheme.typography.titleMedium
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun CopyableValue(
    label: String,
    value: String,
    maxLines: Int = 2,
    onCopy: () -> Unit
) {
    if (value.isBlank()) return
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCopy),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "Tap to copy",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun OdkIntegrationCard(
    androidContext: Context,
    onCopied: (String) -> Unit
) {
    Spacer(Modifier.height(8.dp))
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("ODK INTEGRATION", style = MaterialTheme.typography.titleSmall)
            Text("integrity.trusted_timestamp", style = MaterialTheme.typography.bodyMedium)
            Text(
                "${TrustedTimestampContractMetadata.MATURITY} · ${TrustedTimestampContractMetadata.CONNECTIVITY}",
                style = MaterialTheme.typography.labelMedium
            )

            Spacer(Modifier.height(10.dp))
            Text("ODK inputs", style = MaterialTheme.typography.labelLarge)
            Text("• input_text — exact text; OR")
            Text("• input_file — file from an earlier ODK step")
            Text("• input_tsa_url — optional TSA override")
            Text("• input_timeout_ms — optional timeout, 1000–30000 ms")
            Text("• leave input_text/input_file blank to use MethodMesh file picker or text entry")

            Spacer(Modifier.height(10.dp))
            Text("Intent calls · tap to copy", style = MaterialTheme.typography.labelLarge)
            IntegrationIntentRow(
                label = "Direct text",
                value = TrustedTimestampContractMetadata.directTextIntent,
                androidContext = androidContext,
                onCopied = onCopied
            )
            IntegrationIntentRow(
                label = "Direct file",
                value = TrustedTimestampContractMetadata.directFileIntent,
                androidContext = androidContext,
                onCopied = onCopied
            )
            IntegrationIntentRow(
                label = "Interactive acquisition",
                value = TrustedTimestampContractMetadata.interactiveIntent,
                androidContext = androidContext,
                onCopied = onCopied
            )

            Spacer(Modifier.height(10.dp))
            Text("Modifiers", style = MaterialTheme.typography.labelLarge)
            Text("• input_tsa_url — TSA endpoint override")
            Text("• input_timeout_ms — network timeout")
            Text("• methodmesh_return_namespace — optional for flat result collision avoidance")
            Text("• input_payload_mode='FULL' — request complete shared return envelope")

            Spacer(Modifier.height(10.dp))
            Text("Returned to ODK", style = MaterialTheme.typography.labelLarge)
            Text("Always on handled success:")
            Text("• trusted_timestamp_proof_uri — ZIP attachment (historical key name; ODK receives the file)")
            Text("• methodmesh_status — shared transport status")
            Text("• methodmesh_full_json — metadata/audit JSON")
            Text("When MethodMesh acquired the source:")
            Text("• trusted_timestamp_source_uri — source file attachment, when picker used")
            Text("• trusted_timestamp_source_text — entered source text, when text entry used")
            Text("Not returned: a duplicate source when ODK supplied input_text or input_file.")

            Spacer(Modifier.height(10.dp))
            Text("Canonical supporting returns", style = MaterialTheme.typography.labelLarge)
            Text("trusted_timestamp_time_iso, trusted_timestamp_sha256, trusted_timestamp_authority, trusted_timestamp_trust_status, trusted_timestamp_serial, trusted_timestamp_policy_oid, trusted_timestamp_token_sha256, trusted_timestamp_original_name, trusted_timestamp_size_bytes, trusted_timestamp_status, trusted_timestamp_full_json, trusted_timestamp_error")

            Spacer(Modifier.height(10.dp))
            Text("XLSForm field placement", style = MaterialTheme.typography.labelLarge)
            Text("direct_text_result/trusted_timestamp_proof_uri")
            Text("direct_text_result/methodmesh_status")
            Text("direct_text_result/methodmesh_full_json")
            Text("direct_file_result/trusted_timestamp_proof_uri")
            Text("interactive_result/trusted_timestamp_proof_uri")
            Text("interactive_result/trusted_timestamp_source_uri or trusted_timestamp_source_text")
            Text("Canonical leaf names may be reused in separate intent groups. A return namespace is optional unless results are projected into a flat key space.")

            Spacer(Modifier.height(10.dp))
            Text("Runtime", style = MaterialTheme.typography.labelLarge)
            Text("Inputs: file or exact text; TSA URL; timeout.")
            Text("Beef: Part 1 source, Part 2 proof ZIP. Supporting timestamp/hash/trust values remain available and tappable to copy.")
            Text("Metadata: capability JSON is optional in native presentation; ODK still receives methodmesh_full_json.")
        }
    }
}

@Composable
private fun IntegrationIntentRow(
    label: String,
    value: String,
    androidContext: Context,
    onCopied: (String) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                copyText(androidContext, "$label ODK intent", value)
                onCopied("$label ODK intent copied.")
            },
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.small
    ) {
        Column(Modifier.padding(8.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
    Spacer(Modifier.height(6.dp))
}

private fun firstSetting(
    context: CapabilityScreenContext,
    vararg keys: String
): String? = keys.firstNotNullOfOrNull { key -> context.action.settings[key] }

private fun inputUri(raw: String): Uri {
    val parsed = Uri.parse(raw)
    return if (parsed.scheme.isNullOrBlank()) Uri.fromFile(File(raw)) else parsed
}

private fun parseSavedValues(json: String): Map<String, String> =
    if (json.isBlank()) {
        emptyMap()
    } else {
        runCatching {
            val obj = JSONObject(json)
            buildMap {
                TrustedTimestampFields.outputs.forEach { key ->
                    if (obj.has(key)) put(key, obj.optString(key, ""))
                }
            }
        }.getOrDefault(emptyMap())
    }

private fun copyText(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
}

private data class SourceBundle(
    val name: String,
    val size: Long,
    val digest: ByteArray,
    val sourceUri: String?,
    val origin: String,
    val returnSourceToCaller: Boolean
)

private data class BuiltProof(
    val source: TimestampSource,
    val evidence: TrustedTimestampEvidence,
    val fileName: String,
    val cachePath: String,
    val proofUri: String,
    val sourceUri: String?,
    val sourceText: String?,
    val sourceOrigin: String,
    val returnSourceToCaller: Boolean
)
