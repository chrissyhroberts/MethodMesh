package com.example.methodmesh.modules.trustedtimestamp

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.methodmesh.transport.workflow.ExternalActionRequest
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

object TrustedTimestampVerificationCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100TrustedTimestampVerificationMethod.ID
    override val title = "Verify trusted timestamp"
    override val description = "Check a proof bundle against the exact source bytes and report cryptographic and configured trust status."

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val app = LocalContext.current
        val scope = rememberCoroutineScope()
        val suppliedProof = context.action.setting("proof_file") ?: context.request.setting("proof_file")
        val suppliedSourceFile = context.action.setting("source_file") ?: context.request.setting("source_file")
        val suppliedSourceText = context.action.setting("source_text") ?: context.request.setting("source_text")
        val externalRoundtrip = context.presentationMode.name == "IntentLaunch" && context.submitsImmediately
        var proofUri by rememberSaveable(context.action.canonicalId) { mutableStateOf(suppliedProof.orEmpty()) }
        var sourceUri by rememberSaveable(context.action.canonicalId) { mutableStateOf(suppliedSourceFile.orEmpty()) }
        var sourceText by rememberSaveable(context.action.canonicalId) { mutableStateOf(suppliedSourceText.orEmpty()) }
        var proofName by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
        var sourceName by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
        var status by rememberSaveable(context.action.canonicalId) { mutableStateOf("Choose a proof bundle and its source.") }
        var valuesJson by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
        var running by remember { mutableStateOf(false) }
        var launchedAutomatically by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }

        val values = remember(valuesJson) { valuesJson.toStringMap() }
        val committedResult = remember(valuesJson) {
            if (values.isEmpty()) null else As100TrustedTimestampVerificationMethod.result(
                request = As100TrustedTimestampVerificationMethod.request(
                    action = As100TrustedTimestampVerificationMethod.ID,
                    context = context.request.invocationContext.asMap(As100TrustedTimestampVerificationMethod.ID) + values
                ),
                values = values,
                invocation = context.request.invocationContext
            )
        }

        fun displayName(uri: Uri): String = app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }.orEmpty().ifBlank { uri.lastPathSegment?.substringAfterLast('/').orEmpty().ifBlank { "file" } }

        val proofPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                proofUri = uri.toString()
                proofName = displayName(uri)
                status = "Proof selected. Now select the exact source bytes."
            }
        }
        val sourcePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                sourceUri = uri.toString()
                sourceText = ""
                sourceName = displayName(uri)
                status = "Source selected. Ready to verify."
            }
        }

        fun verify() {
            if (running) return
            running = true
            valuesJson = ""
            status = "Verifying locally…"
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        require(proofUri.isNotBlank()) { "Choose a proof bundle." }
                        require(sourceUri.isNotBlank() || sourceText.isNotEmpty()) { "Choose a source file or enter source text." }
                        val proofBytes = app.contentResolver.openInputStream(Uri.parse(proofUri))?.use { it.readBytes() }
                            ?: error("Cannot read the proof bundle.")
                        val sourceBytes = if (sourceUri.isNotBlank()) {
                            app.contentResolver.openInputStream(Uri.parse(sourceUri))?.use { it.readBytes() }
                                ?: error("Cannot read the source file.")
                        } else sourceText.toByteArray(Charsets.UTF_8)
                        TrustedTimestampVerifier.verify(
                            proofBytes = proofBytes,
                            sourceBytes = sourceBytes,
                            fallbackSourceName = sourceName.ifBlank { "source" },
                            timeoutMs = context.action.setting("timeout_ms")?.toIntOrNull()?.coerceIn(1000, 30000) ?: 10000,
                            proofName = proofName.ifBlank { "trusted_timestamp_proof.zip" }
                        )
                    }
                }.onSuccess { verification ->
                    valuesJson = JSONObject().apply { verification.values.forEach { (key, value) -> put(key, value) } }.toString()
                    status = verification.values[TrustedTimestampVerificationFields.STATUS].orEmpty()
                    running = false
                    if (externalRoundtrip && context.startsImmediately) {
                        val request = As100TrustedTimestampVerificationMethod.request(
                            action = As100TrustedTimestampVerificationMethod.ID,
                            context = context.request.invocationContext.asMap(As100TrustedTimestampVerificationMethod.ID) + verification.values
                        )
                        onConfirmed(As100TrustedTimestampVerificationMethod.result(request, verification.values, context.request.invocationContext))
                    }
                }.onFailure { error ->
                    val message = error.message ?: error::class.java.simpleName
                    val failed = mapOf(
                        TrustedTimestampVerificationFields.STATUS to "failed_verification",
                        TrustedTimestampVerificationFields.ERROR to message,
                        TrustedTimestampVerificationFields.PROOF_FILENAME to proofName
                    )
                    valuesJson = JSONObject().apply { failed.forEach { (key, value) -> put(key, value) } }.toString()
                    status = "Verification failed: $message"
                    running = false
                    if (externalRoundtrip && context.startsImmediately) {
                        val request = As100TrustedTimestampVerificationMethod.request(
                            action = As100TrustedTimestampVerificationMethod.ID,
                            context = context.request.invocationContext.asMap(As100TrustedTimestampVerificationMethod.ID) + failed
                        )
                        onConfirmed(As100TrustedTimestampVerificationMethod.result(request, failed, context.request.invocationContext))
                    }
                }
            }
        }

        androidx.compose.runtime.LaunchedEffect(context.startsImmediately, externalRoundtrip, proofUri, sourceUri, sourceText) {
            if (context.startsImmediately && externalRoundtrip && !launchedAutomatically && proofUri.isNotBlank() && (sourceUri.isNotBlank() || sourceText.isNotEmpty())) {
                launchedAutomatically = true
                verify()
            }
        }

        CapabilityScreenScaffold(
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = committedResult,
            resultPreview = values.mapValues { it.value as Any? },
            onBack = onBack,
            onRetry = { verify() },
            onConfirm = { committedResult?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Text("The source is hashed on this device. The proof is checked locally; the configured trust registry may be consulted for certificate trust. Source content is not uploaded.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(14.dp))
            Text("Proof bundle", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { proofPicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }, modifier = Modifier.weight(1f)) { Text("Choose proof") }
                if (proofName.isNotBlank()) Text(proofName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(16.dp))
            Text("Exact source", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Button(onClick = { sourcePicker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) { Text(if (sourceUri.isBlank()) "Choose source file" else "Choose another source file") }
            OutlinedTextField(value = sourceText, onValueChange = { sourceText = it; sourceUri = "" }, label = { Text("Or paste exact source text") }, supportingText = { Text("Use the same UTF-8 text that was timestamped.") }, modifier = Modifier.fillMaxWidth(), minLines = 4)
            if (sourceName.isNotBlank()) Text("Selected source: $sourceName", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { verify() }, enabled = !running && proofUri.isNotBlank() && (sourceUri.isNotBlank() || sourceText.isNotEmpty()), modifier = Modifier.fillMaxWidth()) { Text(if (running) "Verifying…" else "Verify proof") }
            if (running) { Spacer(Modifier.height(8.dp)); CircularProgressIndicator() }
            Spacer(Modifier.height(8.dp))
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (values.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text("Verification result", style = MaterialTheme.typography.titleMedium)
                values.filterKeys { it != TrustedTimestampVerificationFields.FULL_JSON && it != TrustedTimestampVerificationFields.ERROR }.forEach { (key, value) ->
                    if (value.isNotBlank()) Text("${key.removePrefix("trusted_timestamp_verification_")}: $value", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                }
                values[TrustedTimestampVerificationFields.ERROR]?.takeIf { it.isNotBlank() }?.let { Text("Error: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp)) }
            }
        }
    }
}

private fun String.toStringMap(): Map<String, String> = runCatching {
    val json = JSONObject(this)
    json.keys().asSequence().associateWith { json.optString(it) }
}.getOrDefault(emptyMap())

private fun ExternalActionRequest.setting(key: String): String? =
    (settings[key] ?: settings["input_$key"])?.takeIf { it.isNotBlank() }

private fun com.example.methodmesh.transport.workflow.ExternalWorkflowRequest.setting(key: String): String? =
    (settings[key] ?: settings["input_$key"])?.takeIf { it.isNotBlank() }
