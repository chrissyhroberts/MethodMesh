package com.example.researchos.modules.nfc

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.researchos.core.researchos.ExecutionResult
import com.example.researchos.transport.OutputFormatter
import com.example.researchos.transport.workflow.ui.CapabilityScreenContext
import com.example.researchos.transport.workflow.ui.CapabilityScreenScaffold
import com.example.researchos.transport.workflow.ui.CapabilityScreenSpec
import com.example.researchos.transport.workflow.ui.IntentExample
import com.example.researchos.transport.workflow.ui.IntentExampleDropdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object NfcWriteCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId: String = As100NfcWriteMethod.ID
    override val title: String = "NFC tag write"
    override val description: String = "Write data to an NFC tag and verify the write with a post-write read."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val request = context.request
        val action = context.action
        val scope = rememberCoroutineScope()
        val initialStatus = rememberNfcAvailabilityMessage()

        var recordType by remember { mutableStateOf(action.settings["record_type"] ?: "text/plain") }
        var dataToWrite by remember { mutableStateOf(action.settings["value"] ?: "") }
        var active by remember { mutableStateOf(false) }
        var status by remember { mutableStateOf(initialStatus) }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var recordTypeExpanded by remember { mutableStateOf(false) }

        fun startWrite() {
            if (dataToWrite.isBlank()) {
                status = "Error: No data to write. Please enter data."
                return
            }
            active = true
            result = null
            status = "Ready to write. Tap an NFC tag to begin..."
        }

        LaunchedEffect(context.isExternalInvocation, dataToWrite) {
            if (context.isExternalInvocation && dataToWrite.isNotBlank()) startWrite()
        }

        NfcDeviceServiceEffect(
            enabled = active,
            onStatus = { status = it },
            onSignal = { tagSignal ->
                scope.launch {
                    status = "Writing tag…"
                    try {
                        val writeRequest = NfcWriteRequest(
                            recordType = recordType,
                            value = dataToWrite,
                            mimeType = recordType
                        )

                        val outcome = withContext(Dispatchers.IO) {
                            As100NfcWriteMethod.write(
                                tagSignal = tagSignal,
                                writeRequest = writeRequest,
                                invocationContext = request.invocationContext
                            )
                        }

                        val writeBundle = outcome.evidence
                        result = outcome.executionResult
                        active = false
                        status = if (writeBundle.writeSuccess) {
                            "Write successful. Wrote ${writeBundle.writeSizeBytes} bytes to tag (${writeBundle.writeMessage})."
                        } else {
                            "Write failed: ${writeBundle.writeMessage}"
                        }
                    } catch (e: Exception) {
                        result = null
                        active = false
                        status = "Write error: ${e.message ?: "Unknown error"}"
                    }
                }
            }
        )

        CapabilityScreenScaffold(
            title = title,
            capabilityId = action.canonicalId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = result,
            resultPreview = result?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty(),
            onBack = onBack,
            onRetry = { result = null; status = "Ready to write. Tap an NFC tag to begin..." },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Text("Configure the data to write, then tap an NFC tag.")
            Spacer(Modifier.height(12.dp))

            // Record type dropdown
            Column {
                Text("Record Type", modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { recordTypeExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(recordType, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                    Text("▼")
                }
                DropdownMenu(
                    expanded = recordTypeExpanded,
                    onDismissRequest = { recordTypeExpanded = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    val recordTypes = listOf(
                        "text/plain" to "Plain text",
                        "application/json" to "JSON data",
                        "application/x-studyid" to "Study ID",
                        "application/x-participantid" to "Participant ID"
                    )
                    recordTypes.forEach { (type, label) ->
                        DropdownMenuItem(
                            text = { Column {
                                Text(label)
                                Text(type, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                            } },
                            onClick = {
                                recordType = type
                                recordTypeExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Data to write
            OutlinedTextField(
                value = dataToWrite,
                onValueChange = { dataToWrite = it },
                label = { Text("Data to write to tag") },
                placeholder = { Text("e.g., study_01 or participant data") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Characters: ${dataToWrite.length}",
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall
            )

            Spacer(Modifier.height(12.dp))
            Text(status)
            Spacer(Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { startWrite() },
                    enabled = !active,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (result == null) "Write to tag" else "Write again")
                }
                if (active) {
                    Spacer(Modifier.weight(0.1f))
                    OutlinedButton(
                        onClick = { active = false; status = "Write cancelled." },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            IntentExampleDropdown(
                capabilityId = As100NfcWriteMethod.ID,
                examples = listOf(
                    IntentExample(
                        label = "Write study ID",
                        description = "Write a study identifier to a tag",
                        intentUri = "com.example.researchos.EXECUTE_METHOD(method_id='${As100NfcWriteMethod.ID}',value='study_01',record_type='application/x-studyid')"
                    ),
                    IntentExample(
                        label = "Write participant ID",
                        description = "Write participant information to a tag",
                        intentUri = "com.example.researchos.EXECUTE_METHOD(method_id='${As100NfcWriteMethod.ID}',value='participant_P001',record_type='application/x-participantid')"
                    ),
                    IntentExample(
                        label = "Write JSON data",
                        description = "Write structured JSON to a tag",
                        intentUri = "com.example.researchos.EXECUTE_METHOD(method_id='${As100NfcWriteMethod.ID}',value='{\"study\":\"study_01\",\"event\":\"enrollment\"}',record_type='application/json')"
                    ),
                    IntentExample(
                        label = "Write plain text",
                        description = "Write simple text data to a tag",
                        intentUri = "com.example.researchos.EXECUTE_METHOD(method_id='${As100NfcWriteMethod.ID}',value='Hello NFC tag',record_type='text/plain')"
                    )
                )
            )
        }
    }
}
