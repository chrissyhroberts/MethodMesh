package com.example.researchos.modules.attestation

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.researchos.core.researchos.ExecutionResult
import com.example.researchos.core.researchos.withInvocationContext
import com.example.researchos.platform.BiometricAuthHelper
import com.example.researchos.transport.OutputFormatter
import com.example.researchos.transport.workflow.ui.CapabilityScreenContext
import com.example.researchos.transport.workflow.ui.CapabilityScreenScaffold
import com.example.researchos.transport.workflow.ui.CapabilityScreenSpec
import com.example.researchos.transport.workflow.ui.CapabilityDependencyScreen
import com.example.researchos.transport.workflow.ui.IntentExample
import com.example.researchos.transport.workflow.ui.IntentExampleDropdown

object AttestationCreateCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId: String = As100CreateAttestationMethod.ID
    override val title: String = "Signed event attestation"
    override val description: String = "Sign an event hash with the phone attestation key and add it to the local hash chain."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val androidContext = LocalContext.current
        // Merge external invocation values without allowing blank return fields or
        // generic invocation defaults to erase explicit caller controls. ODK group
        // intents may include blank child fields with the same names as intent
        // parameters, so ordinary Map.plus() is not safe here.
        val supplied = remember(
            context.action.settings,
            context.request.settings,
            context.request.invocationContext
        ) {
            buildMap<String, String> {
                fun mergeNonBlank(values: Map<String, String>) {
                    values.forEach { (key, value) ->
                        if (value.isNotBlank() || key !in this) put(key, value)
                    }
                }
                mergeNonBlank(context.action.settings)
                mergeNonBlank(context.request.settings)
                mergeNonBlank(context.request.invocationContext.asMap(context.action.canonicalId))
            }
        }
        val external = context.startsImmediately
        var studyId by remember { mutableStateOf(supplied["study_id"].orEmpty().ifBlank { if (external) "" else "study_demo" }) }
        var operatorId by remember { mutableStateOf(supplied["operator_id"].orEmpty().ifBlank { if (external) "" else "operator_001" }) }
        var subjectRef by remember {
            mutableStateOf(
                (supplied["subject_ref"] ?: supplied["subject_id"] ?: supplied["entity_id"]
                    ?: context.request.invocationContext.subjectRef().id.value)
                    .orEmpty().ifBlank { if (external) "" else "participant/P001" }
            )
        }
        var eventType by remember { mutableStateOf(supplied["event_type"].orEmpty().ifBlank { if (external) "" else "field_event" }) }
        var eventPayloadHash by remember {
            mutableStateOf(initialAttestationPayloadHash(supplied["event_payload_hash"], external))
        }
        var evidence by remember { mutableStateOf(supplied["verification_evidence"].orEmpty()) }
        var method by remember {
            mutableStateOf(
                AttestationVerificationMethod.values().firstOrNull {
                    it.name.equals(supplied["verification_method"], ignoreCase = true) ||
                        it.label.equals(supplied["verification_method"], ignoreCase = true)
                } ?: if (external) AttestationVerificationMethod.Fingerprint else AttestationVerificationMethod.Pin
            )
        }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var status by remember { mutableStateOf("Ready to create a signed attestation.") }
        var activeDependency by remember { mutableStateOf<String?>(null) }

        fun contextMapFor(selectedMethod: AttestationVerificationMethod, selectedEvidence: AttestationEvidence): Map<String, String> =
            supplied.filterKeys { it != "verification_evidence" } + buildMap {
                put("study_id", studyId)
                put("operator_id", operatorId)
                put("subject_ref", subjectRef)
                put("event_type", eventType)
                put("event_payload_hash", eventPayloadHash)
                put("verification_method", selectedMethod.name)
                put("verification_evidence_format", selectedEvidence.format)
                put("verification_evidence_hash", selectedEvidence.hash)
            }

        fun signAttestation(selectedMethod: AttestationVerificationMethod, selectedEvidence: AttestationEvidence) {
            val execution = As100CreateAttestationMethod.execute(
                request = As100CreateAttestationMethod.request(
                    action = context.action.canonicalId,
                    context = contextMapFor(selectedMethod, selectedEvidence)
                ),
                settingsState = null,
                transport = context.request.source
            ).withInvocationContext(context.request.invocationContext)
            result = execution
            val fields = OutputFormatter.fields(execution, includeProvenance = false)
            val timestampStatus = when (fields["trusted_timestamp_status"]?.toString()) {
                "rfc3161_verified" -> " Trusted RFC 3161 timestamp obtained."
                "unavailable" -> " Timestamp requested but unavailable; local chain retained."
                "disabled" -> " Server-side timestamping disabled by the request."
                "required_failed" -> " Required timestamp unavailable; no attestation created."
                else -> ""
            }
            val hash = fields["attestation_hash"]?.toString().orEmpty()
            status = if (hash.isNotBlank()) {
                "Attestation signed: ${hash.take(18)}…$timestampStatus"
            } else {
                execution.diagnostics["reason"] ?: "Attestation failed.$timestampStatus"
            }
        }

        fun startSigning() {
            activeDependency = null
            when (method) {
                AttestationVerificationMethod.Fingerprint -> {
                    BiometricAuthHelper.authenticate(
                        context = androidContext,
                        title = "Authorise attestation",
                        subtitle = "Use fingerprint or biometric unlock",
                        description = "The biometric itself is not stored. ResearchOS records only that Android accepted local biometric authentication.",
                        cancelText = "Cancel",
                        confirmationRequired = true,
                        allowDeviceCredential = false,
                        onSuccess = { authMethod ->
                            signAttestation(method, AttestationEvidenceFactory.biometric(authMethod))
                        },
                        onFailure = { message -> status = message; result = null }
                    )
                }

                AttestationVerificationMethod.Pin -> {
                    BiometricAuthHelper.authenticateDeviceCredential(
                        context = androidContext,
                        title = "Authorise attestation",
                        subtitle = "Use the phone PIN, pattern or password",
                        description = "ResearchOS does not store the credential. It records only that Android accepted the device credential before signing.",
                        confirmationRequired = true,
                        onSuccess = { authMethod ->
                            signAttestation(method, AttestationEvidenceFactory.deviceCredential(authMethod))
                        },
                        onFailure = { message -> status = message; result = null }
                    )
                }

                AttestationVerificationMethod.Qr -> {
                    result = null
                    status = "Opening QR verification capability…"
                    activeDependency = "qr.scan"
                }

                AttestationVerificationMethod.Nfc -> {
                    activeDependency = "nfc_tag_read"
                    result = null
                    status = "Waiting for NFC tag via the existing NFC capability…"
                }

                AttestationVerificationMethod.Password -> {
                    runCatching { AttestationEvidenceFactory.studyToken(evidence) }
                        .onSuccess { signAttestation(method, it) }
                        .onFailure { status = it.message ?: "Invalid study token"; result = null }
                }
            }
        }

        LaunchedEffect(context.startsImmediately) {
            if (context.startsImmediately) startSigning()
        }

        activeDependency?.let { dependencyId ->
            CapabilityDependencyScreen(
                capabilityId = dependencyId,
                parentContext = context,
                settings = supplied,
                onResult = { dependencyResult ->
                    activeDependency = null
                    val fields = OutputFormatter.fields(dependencyResult, includeProvenance = false)
                    runCatching {
                        AttestationEvidence(
                            format = fields["verification_evidence_format"]?.toString().orEmpty(),
                            hash = fields["verification_evidence_hash"]?.toString().orEmpty().lowercase()
                        )
                    }.onSuccess { dependencyEvidence ->
                        signAttestation(method, dependencyEvidence)
                    }.onFailure {
                        status = "Verification dependency returned no valid canonical evidence."
                        result = null
                    }
                },
                onCancel = { activeDependency = null; status = "Verification cancelled." }
            )
            return
        }

        CapabilityScreenScaffold(
            title = title,
            capabilityId = context.action.canonicalId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = result,
            resultPreview = result?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty(),
            onBack = onBack,
            onRetry = { result = null; status = "Ready to create a signed attestation." },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Text("Creates a signed, hash-chained proof that this phone attested a specific event under a declared verification method.")
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(studyId, { studyId = it }, label = { Text("Study ID") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(operatorId, { operatorId = it }, label = { Text("Operator ID") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(subjectRef, { subjectRef = it }, label = { Text("Subject / event reference") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(eventType, { eventType = it }, label = { Text("Event type") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(eventPayloadHash, { eventPayloadHash = it }, label = { Text("Event payload SHA-256 (hex)") }, modifier = Modifier.fillMaxWidth())
            if (!external && eventPayloadHash == MANUAL_DEBUG_EVENT_PAYLOAD_HASH) {
                Text(
                    "Manual test placeholder: SHA-256 of “researchos-manual-debug-event-v1”. Replace it when testing a specific payload.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.height(8.dp))
            Text("Verification method", fontWeight = FontWeight.SemiBold)
            AttestationVerificationMethod.values().forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .clickable {
                            method = option
                            activeDependency = null
                            status = when (option) {
                                AttestationVerificationMethod.Nfc -> "NFC evidence will be captured through the NFC capability dependency."
                                AttestationVerificationMethod.Qr -> "QR evidence will be captured through the QR capability dependency."
                                AttestationVerificationMethod.Pin -> "Phone PIN, pattern or password will be requested through Android device credential."
                                AttestationVerificationMethod.Fingerprint -> "Fingerprint/biometric will be requested through Android biometric prompt."
                                AttestationVerificationMethod.Password -> "Enter a study password token below."
                            }
                        }
                ) {
                    Text(if (method == option) "●" else "○", modifier = Modifier.padding(end = 8.dp))
                    Text(option.label)
                }
            }
            if (method == AttestationVerificationMethod.Password) {
                OutlinedTextField(
                    evidence,
                    { evidence = it },
                    label = { Text("Study password token") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (method == AttestationVerificationMethod.Nfc) {
                Text("NFC status: $status")
            } else {
                Text(status)
            }
            Row(modifier = Modifier.padding(top = 8.dp)) {
                Button(onClick = { startSigning() }) { Text(if (result == null) "Sign attestation" else "Sign another") }
                Spacer(Modifier.padding(4.dp))
                if (activeDependency != null) {
                    OutlinedButton(onClick = { activeDependency = null; status = "Dependency capture stopped." }) { Text("Stop capture") }
                } else {
                    OutlinedButton(onClick = { result = null; status = "Ready to create a signed attestation." }) { Text("Clear") }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("Dependency rule: attestation consumes NFC and QR capability evidence; it does not reimplement those capabilities.", style = MaterialTheme.typography.bodySmall)
            AttestationKeySummary()

            Spacer(Modifier.height(16.dp))
            IntentExampleDropdown(
                capabilityId = As100CreateAttestationMethod.ID,
                examples = listOf(
                    IntentExample(
                        label = "Basic attestation",
                        description = "Sign an event with fingerprint verification",
                        intentUri = "com.example.researchos.EXECUTE_METHOD(method_id='${As100CreateAttestationMethod.ID}',event_payload_hash='0000000000000000000000000000000000000000000000000000000000000000',verification_method='Fingerprint',trusted_timestamp='preferred',return_mode='flat')"
                    ),
                    IntentExample(
                        label = "With event context",
                        description = "Include study, operator, and event information",
                        intentUri = "com.example.researchos.EXECUTE_METHOD(method_id='${As100CreateAttestationMethod.ID}',event_payload_hash='0000000000000000000000000000000000000000000000000000000000000000',study_id='study_01',operator_id='op_001',event_type='form_submission',verification_method='Fingerprint',trusted_timestamp='preferred',return_mode='flat')"
                    ),
                    IntentExample(
                        label = "With NFC verification",
                        description = "Use NFC tag reading for verification",
                        intentUri = "com.example.researchos.EXECUTE_METHOD(method_id='${As100CreateAttestationMethod.ID}',event_payload_hash='0000000000000000000000000000000000000000000000000000000000000000',verification_method='Nfc',trusted_timestamp='preferred',return_mode='flat')"
                    )
                )
            )
        }
    }
}

private const val MANUAL_DEBUG_EVENT_PAYLOAD = "researchos-manual-debug-event-v1"
internal val MANUAL_DEBUG_EVENT_PAYLOAD_HASH: String =
    AttestationCrypto.sha256Hex(MANUAL_DEBUG_EVENT_PAYLOAD)

internal fun initialAttestationPayloadHash(
    suppliedHash: String?,
    startsImmediately: Boolean
): String = suppliedHash.orEmpty().ifBlank {
    if (startsImmediately) "" else MANUAL_DEBUG_EVENT_PAYLOAD_HASH
}

object AttestationAnchorCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId: String = As100CreateAttestationAnchorMethod.ID
    override val title: String = "Nightly ODK chain anchor"
    override val description: String = "Create ODK fields that externally anchor the current attestation-chain head."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        var studyId by remember { mutableStateOf(context.action.settings["study_id"] ?: "study_demo") }
        var operatorId by remember { mutableStateOf(context.request.invocationContext.operatorId.orEmpty().ifBlank { "operator_001" }) }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var status by remember { mutableStateOf("Ready to create a nightly anchor bundle.") }

        fun createBundle() {
            val execution = As100CreateAttestationAnchorMethod.execute(
                request = As100CreateAttestationAnchorMethod.request(
                    action = context.action.canonicalId,
                    context = context.request.invocationContext.asMap(context.action.canonicalId) + mapOf(
                        "study_id" to studyId,
                        "operator_id" to operatorId
                    )
                ),
                settingsState = null,
                transport = context.request.source
            ).withInvocationContext(context.request.invocationContext)
            result = execution
            status = "Anchor bundle created. Submit these fields through the nightly ODK form to get the server receipt timestamp."
        }

        LaunchedEffect(context.startsImmediately) {
            if (context.startsImmediately) createBundle()
        }

        CapabilityScreenScaffold(
            title = title,
            capabilityId = context.action.canonicalId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = result,
            resultPreview = result?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty(),
            onBack = onBack,
            onRetry = { createBundle() },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Text("Offline events are signed immediately. This creates the nightly ODK payload that proves the signed chain existed by the ODK Central receipt time.")
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(studyId, { studyId = it }, label = { Text("Study ID") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(operatorId, { operatorId = it }, label = { Text("Operator ID") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text("Local records: ${AttestationRepository.allRecords().size}")
            Text("Last anchor: ${AttestationRepository.lastAnchorHash().take(24)}")
            Text("Current chain head: ${AttestationRepository.headHash().take(24)}")
            Text(status)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { createBundle() }) { Text(if (result == null) "Create ODK anchor bundle" else "Create new bundle") }
            Spacer(Modifier.height(10.dp))
            AttestationKeySummary()

            Spacer(Modifier.height(16.dp))
            IntentExampleDropdown(
                capabilityId = As100CreateAttestationAnchorMethod.ID,
                examples = listOf(
                    IntentExample(
                        label = "Create nightly anchor",
                        description = "Create ODK payload anchoring the attestation chain",
                        intentUri = "com.example.researchos.EXECUTE_METHOD(method_id='${As100CreateAttestationAnchorMethod.ID}')"
                    ),
                    IntentExample(
                        label = "With study context",
                        description = "Include study and operator information",
                        intentUri = "com.example.researchos.EXECUTE_METHOD(method_id='${As100CreateAttestationAnchorMethod.ID}',study_id='study_01',operator_id='op_001')"
                    )
                )
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AttestationKeySummary() {
    val context = LocalContext.current
    val clipboard = remember(context) { context.getSystemService(ClipboardManager::class.java) }
    var copied by remember { mutableStateOf(false) }
    ElevatedCard(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(Modifier.padding(10.dp)) {
            val publicKeyId = runCatching { AttestationCrypto.publicKeyId() }.getOrElse { "not generated" }
            val publicKey = runCatching { AttestationCrypto.publicKeyBase64() }.getOrElse { "" }
            Text("Device attestation key", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
            Text("Public key ID: $publicKeyId", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
            if (publicKey.isNotBlank()) {
                Text(
                    text = if (copied) "Public key copied" else "Public key export: ${publicKey.take(64)}… (hold to copy)",
                    modifier = Modifier.combinedClickable(
                        onClick = { },
                        onLongClick = {
                            clipboard.setPrimaryClip(ClipData.newPlainText("ResearchOS public attestation key", publicKey))
                            copied = true
                        }
                    ),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
