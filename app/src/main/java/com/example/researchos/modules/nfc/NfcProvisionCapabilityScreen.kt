package com.example.researchos.modules.nfc

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
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

object NfcProvisionCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId: String = As100NfcProvisionMethod.ID
    override val title: String = "Provision NFC credential"
    override val description: String =
        "Write a credential, read it back, and return a registry record compatible with NFC attestation."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val scope = rememberCoroutineScope()
        val supplied = remember(context.action.settings, context.request.settings) {
            context.action.settings + context.request.settings.filterValues(String::isNotBlank)
        }
        val requestedPolicy = supplied["overwrite_policy"]
        var credentialId by remember { mutableStateOf(supplied[NfcProvisionFields.CREDENTIAL_ID].orEmpty()) }
        var credentialValue by remember {
            mutableStateOf(supplied["value"].orEmpty().ifBlank { supplied["credential_value"].orEmpty() })
        }
        var overwritePolicy by remember {
            mutableStateOf(NfcOverwritePolicy.parse(requestedPolicy) ?: NfcOverwritePolicy.EmptyOnly)
        }
        var expectedCurrentHash by remember { mutableStateOf(supplied["expected_current_hash"].orEmpty()) }
        var policyExpanded by remember { mutableStateOf(false) }
        val initialStatus = rememberNfcAvailabilityMessage()
        var active by remember { mutableStateOf(false) }
        var status by remember { mutableStateOf(initialStatus) }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }

        fun startProvisioning() {
            when {
                requestedPolicy != null && NfcOverwritePolicy.parse(requestedPolicy) == null -> {
                    status = "Unknown overwrite_policy '$requestedPolicy'."
                }
                credentialId.isBlank() -> status = "credential_id is required."
                credentialValue.isBlank() -> status = "value is required."
                else -> {
                    active = true
                    result = null
                    status = "Hold the credential tag against the phone until write and read-back verification finish…"
                }
            }
        }

        LaunchedEffect(context.startsImmediately, credentialId, credentialValue) {
            if (context.startsImmediately && credentialId.isNotBlank() && credentialValue.isNotBlank()) {
                startProvisioning()
            }
        }

        NfcDeviceServiceEffect(
            enabled = active,
            onStatus = { status = it },
            onSignal = { tagSignal ->
                scope.launch {
                    status = "Writing credential and verifying the read-back…"
                    try {
                        val execution = withContext(Dispatchers.IO) {
                            As100NfcProvisionMethod.provision(
                                tagSignal = tagSignal,
                                credentialId = credentialId,
                                writeRequest = NfcWriteRequest(
                                    recordType = supplied["record_type"].orEmpty().ifBlank { "external" },
                                    value = credentialValue,
                                    mimeType = supplied["mime_type"].orEmpty().ifBlank { "researchos:credential" },
                                    languageCode = supplied["language_code"].orEmpty().ifBlank { "en" },
                                    overwritePolicy = overwritePolicy,
                                    expectedCurrentHash = expectedCurrentHash.takeIf(String::isNotBlank)
                                ),
                                invocationContext = context.request.invocationContext
                            )
                        }
                        result = execution
                        active = false
                        val fields = OutputFormatter.fields(execution, includeProvenance = false)
                        status = if (fields[NfcProvisionFields.PROVISION_SUCCESS]?.toString() == "true") {
                            "Credential provisioned and verified. The returned UID, payload bytes, and evidence hash form the registry record."
                        } else {
                            fields[NfcProvisionFields.PROVISION_MESSAGE]?.toString()
                                ?: "Credential provisioning failed."
                        }
                    } catch (error: Exception) {
                        active = false
                        result = null
                        status = error.message ?: "Credential provisioning failed."
                    }
                }
            }
        )

        CapabilityScreenScaffold(
            title = title,
            capabilityId = context.action.canonicalId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = result,
            resultPreview = result?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty(),
            onBack = onBack,
            onRetry = { startProvisioning() },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Text(
                "The safe default writes only blank tags. Replacement must be explicitly selected.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = credentialId,
                onValueChange = { credentialId = it },
                label = { Text("Credential ID") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = credentialValue,
                onValueChange = { credentialValue = it },
                label = { Text("Credential value written to tag") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4
            )
            Spacer(Modifier.height(12.dp))
            Column {
                Text("Existing tag content")
                OutlinedButton(
                    onClick = { policyExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(overwritePolicy.label, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                    Text("▼")
                }
                DropdownMenu(
                    expanded = policyExpanded,
                    onDismissRequest = { policyExpanded = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    NfcOverwritePolicy.entries.forEach { policy ->
                        DropdownMenuItem(
                            text = { Text("${policy.label} (${policy.wireValue})") },
                            onClick = {
                                overwritePolicy = policy
                                policyExpanded = false
                            }
                        )
                    }
                }
            }
            if (overwritePolicy == NfcOverwritePolicy.CompareAndReplace) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = expectedCurrentHash,
                    onValueChange = { expectedCurrentHash = it },
                    label = { Text("Expected current NDEF SHA-256") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(status)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                Button(
                    onClick = { startProvisioning() },
                    enabled = !active,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Write and verify")
                }
                if (active) {
                    Spacer(Modifier.weight(0.08f))
                    OutlinedButton(
                        onClick = {
                            active = false
                            status = "Provisioning cancelled."
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            IntentExampleDropdown(
                capabilityId = As100NfcProvisionMethod.ID,
                examples = listOf(
                    IntentExample(
                        label = "Provision a blank credential",
                        description = "Write and verify a ResearchOS external-type credential record.",
                        intentUri = "com.example.researchos.EXECUTE_METHOD(method_id='${As100NfcProvisionMethod.ID}',credential_id='credential_001',value='replace-with-random-secret',record_type='external',mime_type='researchos:credential',overwrite_policy='empty_only')"
                    ),
                    IntentExample(
                        label = "Controlled replacement",
                        description = "Replace only the NDEF message whose current hash is supplied.",
                        intentUri = "com.example.researchos.EXECUTE_METHOD(method_id='${As100NfcProvisionMethod.ID}',credential_id='credential_001',value='replacement-secret',record_type='external',mime_type='researchos:credential',overwrite_policy='compare_and_replace',expected_current_hash='<64-character SHA-256>')"
                    )
                )
            )
        }
    }
}
