package com.example.methodmesh.modules.nfc

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.withInvocationContext
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import com.example.methodmesh.transport.workflow.ui.IntentExample
import com.example.methodmesh.transport.workflow.ui.IntentExampleDropdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object NfcReadCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId: String = As100NfcReadMethod.ID
    override val title: String = "NFC tag read"
    override val description: String = "Capture an NFC tag, review the evidence, then confirm or retry."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val request = context.request
        val scope = rememberCoroutineScope()
        val initialStatus = rememberNfcAvailabilityMessage()
        var active by remember { mutableStateOf(false) }
        var status by remember { mutableStateOf(initialStatus) }
        var execution by remember { mutableStateOf<ExecutionResult?>(null) }

        fun startCapture() {
            active = true
            execution = null
            status = "Waiting for NFC tag…"
        }

        LaunchedEffect(context.startsImmediately) {
            if (context.startsImmediately) startCapture()
        }

        NfcDeviceServiceEffect(
            enabled = active,
            onStatus = { status = it },
            onSignal = { tagSignal ->
                scope.launch {
                    try {
                        status = "Reading tag…"
                        val read = withContext(Dispatchers.IO) {
                            As100NfcReadMethod.read(tagSignal, request.invocationContext)
                        }
                        execution = read.withInvocationContext(request.invocationContext)
                        active = false
                        status = "Tag captured."
                    } catch (e: Exception) {
                        active = false
                        status = "Read error: ${e.message ?: "Unknown error"}"
                    }
                }
            }
        )

        CapabilityScreenScaffold(
            title = title,
            capabilityId = context.action.canonicalId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = execution,
            resultPreview = execution?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty(),
            onBack = onBack,
            onRetry = { startCapture() },
            onConfirm = { execution?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            if (execution == null) {
                Text(status)
                Spacer(Modifier.height(10.dp))
                if (!active) {
                    Button(onClick = { startCapture() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Scan tag")
                    }
                } else {
                    OutlinedButton(
                        onClick = { active = false; status = "NFC capture stopped." },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Stop scan")
                    }
                }
            } else {
                val v = As100NfcReadMethod.observationValues(execution!!)
                Text("Tag captured", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))

                // Primary payload — the most likely thing the user cares about
                val ndefText = v[NfcEvidenceFields.NDEF_TEXT].orEmpty()
                val ndefUri  = v[NfcEvidenceFields.NDEF_URI].orEmpty()
                val ndefPayload = v[NfcEvidenceFields.NDEF_FIRST_PAYLOAD_UTF8].orEmpty()
                val mimeTypes = v[NfcEvidenceFields.NDEF_MIME_TYPES].orEmpty()
                val externalTypes = v[NfcEvidenceFields.NDEF_EXTERNAL_TYPES].orEmpty()

                if (ndefText.isNotBlank() || ndefUri.isNotBlank() || ndefPayload.isNotBlank()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Payload", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            if (ndefText.isNotBlank()) {
                                SelectionContainer {
                                    Text(ndefText, fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            if (ndefUri.isNotBlank()) {
                                SelectionContainer {
                                    Text(ndefUri, fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            if (ndefText.isBlank() && ndefUri.isBlank() && ndefPayload.isNotBlank()) {
                                SelectionContainer {
                                    Text(ndefPayload, fontFamily = FontFamily.Monospace,
                                        style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            if (mimeTypes.isNotBlank()) {
                                Text("Type: $mimeTypes",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp))
                            }
                            if (externalTypes.isNotBlank()) {
                                Text("External type: $externalTypes",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                } else {
                    Text("No NDEF payload on this tag.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                }

                // Tag identity and capacity
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Tag details", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        TagDetailRow("UID", v[NfcEvidenceFields.TAG_UID_HEX])
                        TagDetailRow("Technologies", v[NfcEvidenceFields.TECH_LIST])
                        TagDetailRow("NDEF supported", v[NfcEvidenceFields.NDEF_SUPPORTED])
                        TagDetailRow("Writable", v[NfcEvidenceFields.NDEF_IS_WRITABLE])
                        val usedBytes = v[NfcEvidenceFields.NDEF_MESSAGE_SIZE_BYTES]
                        val maxBytes  = v[NfcEvidenceFields.NDEF_MAX_SIZE_BYTES]
                        if (!usedBytes.isNullOrBlank() && !maxBytes.isNullOrBlank()) {
                            TagDetailRow("Capacity", "$usedBytes / $maxBytes bytes used")
                        }
                        TagDetailRow("Records", v[NfcEvidenceFields.NDEF_RECORD_COUNT])
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            IntentExampleDropdown(
                capabilityId = As100NfcReadMethod.ID,
                examples = listOf(
                    IntentExample(
                        label = "Basic NFC read",
                        description = "Simple intent to capture an NFC tag",
                        intentUri = "com.example.methodmesh.EXECUTE_METHOD(method_id='${As100NfcReadMethod.ID}')"
                    ),
                    IntentExample(
                        label = "With study context",
                        description = "Include study and operator information",
                        intentUri = "com.example.methodmesh.EXECUTE_METHOD(method_id='${As100NfcReadMethod.ID}',input_study_id='study_01',operator_id='operator_001')"
                    )
                )
            )
        }
    }

    @Composable
    private fun TagDetailRow(label: String, value: String?) {
        if (value.isNullOrBlank()) return
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
            Text(
                text = "$label: ",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(0.4f)
            )
            SelectionContainer(modifier = Modifier.weight(0.6f)) {
                Text(
                    text = value,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
