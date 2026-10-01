package com.example.methodmesh.modules.nfc

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.methodmesh.core.timeassurance.ClockAssuranceRuntime
import com.example.methodmesh.modules.trustedtimestamp.TrustedTimestampClockAnchor
import com.example.methodmesh.platform.timestamp.TimestampSource
import com.example.methodmesh.platform.timestamp.TrustedTimestampAuthorities
import com.example.methodmesh.platform.timestamp.TrustedTimestampEngine
import com.example.methodmesh.platform.timestamp.sha256Hex
import com.example.methodmesh.platform.timestamp.toHex
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object NfcOdkFormCompilerCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100NfcOdkFormCompilerMethod.ID
    override val title = "Make ODK form NFC-compatible"
    override val description = "Map an ordinary XLSX form to a versioned MethodMesh NFC form and timestamp the result."

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (com.example.methodmesh.core.methodmesh.ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val app = LocalContext.current
        val scope = rememberCoroutineScope()
        var source by remember { mutableStateOf<Uri?>(null) }
        var mode by remember {
            mutableStateOf(
                if ((context.action.settings["nfc_mode"] ?: context.request.settings["nfc_mode"]).equals("provisioning", true)) {
                    NfcOdkFormCompiler.Mode.PROVISIONING
                } else {
                    NfcOdkFormCompiler.Mode.VERIFICATION
                }
            )
        }
        val contractVersion = context.action.settings["nfc_form_contract_version"]
            ?: context.request.settings["nfc_form_contract_version"]
            ?: NfcCredentialFormContract.CURRENT_VERSION
        var status by remember { mutableStateOf("Choose an ordinary ODK XLSX form.") }
        var result by remember { mutableStateOf<com.example.methodmesh.core.methodmesh.ExecutionResult?>(null) }
        var running by remember { mutableStateOf(false) }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            source = uri
            status = if (uri == null) "No form selected." else "Ready to map the selected XLSX."
        }

        fun compile() {
            val uri = source ?: run { status = "Choose an XLSX form first."; return }
            if (running) return
            running = true
            scope.launch {
                val built = runCatching {
                    withContext(Dispatchers.IO) {
                        val bytes = app.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Unable to read the selected XLSX.")
                        val name = app.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "odk_form.xlsx"
                        val compiled = NfcOdkFormCompiler.compile(bytes, name, mode, contractVersion)
                        val mappedFile = File(app.cacheDir, compiled.outputName).apply { writeBytes(compiled.bytes) }
                        val digest = compiled.bytes.sha256Hex()
                        val timeout = 15000
                        val endpoint = TrustedTimestampAuthorities.FREETSA.endpoint
                        val start = ClockAssuranceRuntime.monotonicSnapshot()
                        val (request, response) = TrustedTimestampEngine.requestTimestamp(digest.hexBytes(), endpoint, timeout)
                        val evidence = TrustedTimestampEngine.parseAndValidate(request, response, digest.hexBytes(), endpoint, timeout)
                        val end = ClockAssuranceRuntime.monotonicSnapshot()
                        TrustedTimestampClockAnchor.publishIfTrusted(evidence, start, end)
                        val proof = TrustedTimestampEngine.createProofZip(TimestampSource(compiled.outputName, compiled.bytes.size.toLong(), digest), evidence)
                        val proofFile = File(app.cacheDir, "${compiled.outputName}.tsa-proof.zip").apply { writeBytes(proof) }
                        val statementName = "${compiled.outputName.substringBeforeLast('.')}.compatibility.txt"
                        val deliveryName = "${compiled.outputName.substringBeforeLast('.')}.delivery.zip"
                        val delivery = createDeliveryZip(
                            compiled.outputName to compiled.bytes,
                            statementName to compiled.statement.toByteArray(Charsets.UTF_8),
                            proofFile.name to proof
                        )
                        val deliveryFile = File(app.cacheDir, deliveryName).apply { writeBytes(delivery) }
                        val mappedUri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", mappedFile).toString()
                        val proofUri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", proofFile).toString()
                        val deliveryUri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", deliveryFile).toString()
                        compiled to mapOf(
                            NfcOdkFormCompilerFields.DELIVERY_ZIP_URI to deliveryUri,
                            NfcOdkFormCompilerFields.DELIVERY_ZIP_FILENAME to deliveryName,
                            NfcOdkFormCompilerFields.MAPPED_FORM_URI to mappedUri,
                            NfcOdkFormCompilerFields.MAPPED_FORM_FILENAME to compiled.outputName,
                            NfcOdkFormCompilerFields.MAPPED_FORM_VERSION to compiled.formVersion,
                            NfcOdkFormCompilerFields.COMPATIBILITY_STATEMENT to compiled.statement,
                            NfcOdkFormCompilerFields.NFC_METHOD_ID to compiled.methodId,
                            NfcOdkFormCompilerFields.NFC_METHOD_VERSION to compiled.methodVersion,
                            NfcOdkFormCompilerFields.CREDENTIAL_FORMAT_VERSION to compiled.credentialFormatVersion,
                            NfcOdkFormCompilerFields.NFC_CONTRACT_VERSION to compiled.contractVersion,
                            NfcOdkFormCompilerFields.CHANGED_FIELDS to compiled.changedFields.joinToString(","),
                            NfcOdkFormCompilerFields.FORM_SHA256 to digest,
                            NfcOdkFormCompilerFields.TSA_PROOF_URI to proofUri,
                            NfcOdkFormCompilerFields.TSA_TIME_ISO to evidence.generationTimeIso,
                            NfcOdkFormCompilerFields.TSA_AUTHORITY to evidence.authorityName,
                            NfcOdkFormCompilerFields.TSA_TRUST_STATUS to evidence.trustStatus,
                            NfcOdkFormCompilerFields.STATUS to "succeeded"
                        )
                    }
                }
                running = false
                built.onSuccess { (_, values) ->
                    val executionRequest = As100NfcOdkFormCompilerMethod.request(
                        action = capabilityId,
                        context = context.request.invocationContext.asMap(capabilityId) + context.action.settings,
                        signals = emptyList(),
                        inputs = emptyList()
                    )
                    val execution = As100NfcOdkFormCompilerMethod.result(executionRequest, values, context.request.invocationContext, true)
                    result = execution
                    status = values[NfcOdkFormCompilerFields.COMPATIBILITY_STATEMENT].orEmpty()
                    if (context.submitsImmediately) onConfirmed(execution)
                }.onFailure { error ->
                    val values = mapOf(NfcOdkFormCompilerFields.STATUS to "failed", NfcOdkFormCompilerFields.ERROR to (error.message ?: "NFC form compilation failed."))
                    val executionRequest = As100NfcOdkFormCompilerMethod.request(
                        action = capabilityId,
                        context = context.request.invocationContext.asMap(capabilityId) + context.action.settings,
                        signals = emptyList(),
                        inputs = emptyList()
                    )
                    val execution = As100NfcOdkFormCompilerMethod.result(executionRequest, values, context.request.invocationContext, false)
                    result = execution
                    status = values[NfcOdkFormCompilerFields.ERROR].orEmpty()
                }
            }
        }

        CapabilityScreenScaffold(title, capabilityId, context, context.stepNumber > 1, result, result?.let { OutputFormatter.fields(it, false) }.orEmpty(), onBack, { result = null; status = "Choose the source form again." }, { result?.let(onConfirmed) }, onCancel) {
            Button(onClick = { picker.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel")) }, modifier = Modifier.fillMaxWidth()) { Text(if (source == null) "Choose ODK XLSX" else "Choose another XLSX") }
            Spacer(Modifier.height(12.dp))
            Text("NFC operation", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NfcOdkFormCompiler.Mode.values().forEach { candidate ->
                    OutlinedButton(onClick = { mode = candidate }, modifier = Modifier.weight(1f)) { Text(if (mode == candidate) "✓ ${candidate.name.lowercase()}" else candidate.name.lowercase()) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Contract $contractVersion is selected. The compiler delivers one ZIP containing the mapped XLSX, compatibility statement, and RFC 3161 TSA proof. Optional JSON sidecars remain separate.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { compile() }, enabled = source != null && !running, modifier = Modifier.fillMaxWidth()) { Text(if (running) "Compiling and timestamping…" else "Compile NFC-compatible form") }
            Spacer(Modifier.height(12.dp))
            Text(status, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun String.hexBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

private fun createDeliveryZip(vararg files: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { output ->
    ZipOutputStream(output).use { zip ->
        files.forEach { (name, bytes) ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(bytes)
            zip.closeEntry()
        }
    }
}.toByteArray()
