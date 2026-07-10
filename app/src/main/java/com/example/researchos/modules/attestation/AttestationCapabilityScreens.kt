package com.example.researchos.modules.attestation

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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.researchos.core.researchos.ExecutionResult
import com.example.researchos.core.researchos.withInvocationContext
import com.example.researchos.modules.nfc.As100NfcReadMethod
import com.example.researchos.modules.nfc.NfcDeviceServiceEffect
import com.example.researchos.modules.nfc.NfcEvidenceFields
import com.example.researchos.modules.nfc.rememberNfcAvailabilityMessage
import com.example.researchos.modules.qrcode.rememberQrCapabilityInvocation
import com.example.researchos.platform.BiometricAuthHelper
import com.example.researchos.transport.OutputFormatter
import com.example.researchos.transport.workflow.ui.CapabilityScreenContext
import com.example.researchos.transport.workflow.ui.CapabilityScreenScaffold
import com.example.researchos.transport.workflow.ui.CapabilityScreenSpec

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
        val nfcAvailability = rememberNfcAvailabilityMessage()
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
        val external = context.isExternalInvocation
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
        var eventPayloadHash by remember { mutableStateOf(supplied["event_payload_hash"].orEmpty()) }
        var eventPayload by remember { mutableStateOf(supplied["event_payload"].orEmpty().ifBlank { if (external) "" else "event payload to be hashed" }) }
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
        var nfcActive by remember { mutableStateOf(false) }

        fun contextMapFor(selectedMethod: AttestationVerificationMethod, selectedEvidence: String): Map<String, String> =
            supplied + buildMap {
                put("study_id", studyId)
                put("operator_id", operatorId)
                put("subject_ref", subjectRef)
                put("event_type", eventType)
                if (eventPayloadHash.isNotBlank()) {
                    put("event_payload_hash", eventPayloadHash)
                    remove("event_payload")
                } else {
                    put("event_payload", eventPayload)
                }
                put("verification_method", selectedMethod.name)
                put("verification_evidence", selectedEvidence.ifBlank { selectedMethod.name })
            }

        fun signAttestation(selectedMethod: AttestationVerificationMethod, selectedEvidence: String) {
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
            if (context.isExternalInvocation && execution.status == com.example.researchos.core.researchos.TransformationStatus.Succeeded) {
                onConfirmed(execution)
            }
        }

        val launchQrDependency = rememberQrCapabilityInvocation(
            context = context,
            sourceLabel = "attestation_dependency",
            onResult = { qrExecution ->
                val qrFields = OutputFormatter.fields(qrExecution, includeProvenance = false)
                val qrPayload = qrFields["qr_payload"]?.toString().orEmpty()
                val qrHash = qrFields["qr_payload_hash"]?.toString().orEmpty()
                val calculatedHash = AttestationCrypto.sha256Hex(qrPayload)
                if (qrPayload.isBlank() || qrHash.isBlank()) {
                    status = qrExecution.diagnostics["reason"] ?: "QR evidence was not captured."
                    result = null
                } else if (!calculatedHash.equals(qrHash, ignoreCase = true)) {
                    status = "QR evidence failed integrity validation and was not attested."
                    result = null
                } else {
                    signAttestation(AttestationVerificationMethod.Qr, qrPayload)
                }
            },
            onCancel = {
                status = "QR verification cancelled."
                result = null
            },
            onError = { message ->
                status = message
                result = null
            }
        )

        fun startSigning() {
            nfcActive = false
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
                        onSuccess = { authMethod -> signAttestation(method, authMethod) },
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
                        onSuccess = { authMethod -> signAttestation(method, authMethod) },
                        onFailure = { message -> status = message; result = null }
                    )
                }

                AttestationVerificationMethod.Qr -> {
                    result = null
                    status = "Opening QR verification capability…"
                    launchQrDependency()
                }

                AttestationVerificationMethod.Nfc -> {
                    nfcActive = true
                    result = null
                    status = "Waiting for NFC tag via the existing NFC capability…"
                }

                AttestationVerificationMethod.Password -> signAttestation(method, evidence)
            }
        }

        NfcDeviceServiceEffect(
            enabled = nfcActive,
            onStatus = { status = it },
            onSignal = { tagSignal ->
                val read = As100NfcReadMethod.readBundle(tagSignal, context.request.invocationContext)
                nfcActive = false
                val tagUid = read.evidence.values[NfcEvidenceFields.TAG_UID_HEX].orEmpty()
                val nfcPayload = read.evidence.values[NfcEvidenceFields.NDEF_FIRST_PAYLOAD_UTF8].orEmpty()
                    .ifBlank { read.evidence.values[NfcEvidenceFields.NDEF_FIRST_PAYLOAD_HEX].orEmpty() }
                val nfcPayloadHash = AttestationCrypto.sha256Hex(nfcPayload.ifBlank { tagUid })
                val nfcEvidence = listOf(
                    "nfc_uid=$tagUid",
                    "nfc_payload_hash=$nfcPayloadHash"
                ).joinToString(";")
                signAttestation(AttestationVerificationMethod.Nfc, nfcEvidence)
            }
        )

        LaunchedEffect(context.isExternalInvocation) {
            if (context.isExternalInvocation) startSigning()
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
            OutlinedTextField(eventPayload, { eventPayload = it }, label = { Text("Event payload or external record ID") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text("Verification method", fontWeight = FontWeight.SemiBold)
            AttestationVerificationMethod.values().forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .clickable {
                            method = option
                            nfcActive = false
                            status = when (option) {
                                AttestationVerificationMethod.Nfc -> nfcAvailability
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
                if (nfcActive) {
                    OutlinedButton(onClick = { nfcActive = false; status = "NFC capture stopped." }) { Text("Stop NFC") }
                } else {
                    OutlinedButton(onClick = { result = null; status = "Ready to create a signed attestation." }) { Text("Clear") }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("Dependency rule: attestation consumes NFC and QR capability evidence; it does not reimplement those capabilities.", style = MaterialTheme.typography.bodySmall)
            AttestationKeySummary()
        }
    }
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

        LaunchedEffect(Unit) { createBundle() }

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
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AttestationKeySummary() {
    val clipboard = LocalClipboardManager.current
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
                            clipboard.setText(AnnotatedString(publicKey))
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
