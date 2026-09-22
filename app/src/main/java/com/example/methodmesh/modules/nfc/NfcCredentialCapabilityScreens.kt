package com.example.methodmesh.modules.nfc

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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.platform.nfc.NfcTagSignal
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import com.example.methodmesh.transport.workflow.ui.IntentExample
import com.example.methodmesh.transport.workflow.ui.IntentExampleDropdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

object NfcCredentialProvisioningCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100NfcCredentialProvisioningMethod.ID
    override val title = "NFC credential provisioning"
    override val description =
        "Create a portable signed credential protected by a 4- or 6-digit PIN."

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
        var subjectId by remember {
            mutableStateOf(
                supplied[NfcProvisionFields.CREDENTIAL_SUBJECT_ID]
                    .orEmpty()
                    .ifBlank { supplied["subject_id"].orEmpty() }
            )
        }
        var credentialId by remember {
            mutableStateOf(
                supplied[NfcProvisionFields.CREDENTIAL_ID]
                    .orEmpty()
                    .ifBlank { "cred_${UUID.randomUUID().toString().replace("-", "").take(16)}" }
            )
        }
        var pinLength by remember {
            mutableIntStateOf(supplied[NfcProvisionFields.PIN_LENGTH]?.toIntOrNull()?.takeIf { it == 4 || it == 6 } ?: 6)
        }
        val requestedOverwritePolicy = supplied[NfcWriteFields.OVERWRITE_POLICY]
        val invalidRequestedOverwritePolicy =
            !requestedOverwritePolicy.isNullOrBlank() &&
                NfcOverwritePolicy.parse(requestedOverwritePolicy) == null
        var overwritePolicy by remember {
            mutableStateOf(
                NfcOverwritePolicy.parse(requestedOverwritePolicy)
                    ?: NfcOverwritePolicy.EmptyOnly
            )
        }
        var overwritePolicyExpanded by remember { mutableStateOf(false) }
        var firstTag by remember { mutableStateOf<NfcTagSignal?>(null) }
        var firstTagUid by remember { mutableStateOf("") }
        var pin by remember { mutableStateOf("") }
        var confirmPin by remember { mutableStateOf("") }
        var active by remember { mutableStateOf(false) }
        var writing by remember { mutableStateOf(false) }
        var awaitingReadBack by remember { mutableStateOf(false) }
        var firstWriteValues by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
        val initialStatus = rememberNfcAvailabilityMessage()
        var status by remember { mutableStateOf(initialStatus) }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }

        LaunchedEffect(subjectId, credentialId, pinLength, overwritePolicy) {
            context.onSettingsChanged(
                mapOf(
                    NfcProvisionFields.CREDENTIAL_SUBJECT_ID to subjectId,
                    NfcProvisionFields.CREDENTIAL_ID to credentialId,
                    NfcProvisionFields.PIN_LENGTH to pinLength.toString(),
                    NfcWriteFields.OVERWRITE_POLICY to overwritePolicy.wireValue
                )
            )
        }

        fun startFirstScan() {
            if (invalidRequestedOverwritePolicy) {
                status =
                    "Unknown overwrite_policy '$requestedOverwritePolicy'. Use empty_only or replace."
                return
            }
            if (subjectId.isBlank()) {
                status = "credential_subject_id is required."
                return
            }
            result = null
            firstTag = null
            firstTagUid = ""
            writing = false
            awaitingReadBack = false
            firstWriteValues = emptyMap()
            active = true
            status = "Tap the NFC card to inspect it before provisioning."
        }

        var pendingCredential by remember {
            mutableStateOf<NfcPortableCredentialFormat.ProvisionedCredential?>(null)
        }
        fun beginPreparedWrite() {
            when {
                firstTag == null -> status = "Scan the card first."
                pin.length != pinLength || pin.any { !it.isDigit() } ->
                    status = "Enter exactly $pinLength digits."
                pin != confirmPin -> status = "The PIN entries do not match."
                else -> scope.launch {
                    status = "Preparing the encrypted credential…"
                    val credential = runCatching {
                        withContext(Dispatchers.Default) {
                            NfcPortableCredentialFormat.provision(
                                credentialSubjectId = subjectId,
                                credentialId = credentialId,
                                pin = pin.toCharArray(),
                                signer = AndroidNfcCredentialSigner
                            )
                        }
                    }.getOrElse {
                        pin = ""
                        confirmPin = ""
                        status = it.message ?: "Credential preparation failed."
                        return@launch
                    }
                    pin = ""
                    confirmPin = ""
                    pendingCredential = credential
                    writing = true
                    active = false
                    delay(150)
                    active = true
                    status = "Tap the same card again and hold it in place while MethodMesh writes and verifies it."
                }
            }
        }

        LaunchedEffect(context.startsImmediately) {
            if (context.startsImmediately) startFirstScan()
        }

        NfcDeviceServiceEffect(
            enabled = active,
            onStatus = { status = it },
            onSignal = { tagSignal ->
                scope.launch {
                    val uid = NfcTagRepository.tagUidHex(tagSignal.androidTag)
                    if (awaitingReadBack) {
                        val credential = pendingCredential
                        if (uid != firstTagUid) {
                            active = false
                            status = "That is a different card. Tap the card that was just provisioned."
                            delay(150)
                            active = true
                        } else if (credential == null) {
                            active = false
                            awaitingReadBack = false
                            status = "Prepared credential is unavailable. Start again."
                        } else {
                            status = "Verifying the credential written to the card…"
                            val execution = withContext(Dispatchers.IO) {
                                As100NfcCredentialProvisioningMethod.confirmReadBack(
                                    tagSignal = tagSignal,
                                    credential = credential,
                                    overwritePolicy = overwritePolicy,
                                    previousValues = firstWriteValues,
                                    invocationContext = context.request.invocationContext
                                )
                            }
                            active = false
                            awaitingReadBack = false
                            writing = false
                            pendingCredential = null
                            result = execution
                            status = OutputFormatter.fields(execution, false)[NfcProvisionFields.PROVISION_MESSAGE]
                                ?.toString()
                                ?: "Provisioning verification finished."
                            if (context.submitsImmediately) onConfirmed(execution)
                        }
                    } else if (!writing) {
                        active = false
                        val tagValues = withContext(Dispatchers.IO) {
                            NfcTagRepository.readTag(tagSignal.androidTag)
                        }
                        val hasContent =
                            tagValues[NfcEvidenceFields.NDEF_HAS_MEANINGFUL_CONTENT] == "true"
                        if (overwritePolicy == NfcOverwritePolicy.EmptyOnly && hasContent) {
                            status = "This card already contains NDEF data. Select Replace existing content, then scan it again."
                        } else {
                            firstTag = tagSignal
                            firstTagUid = uid
                            status = "Card accepted. Create the $pinLength-digit PIN, then tap the card again to write."
                        }
                    } else {
                        val credential = pendingCredential
                        if (uid != firstTagUid) {
                            active = false
                            writing = false
                            pendingCredential = null
                            status = "That is a different card. Provisioning cancelled before writing."
                        } else if (credential == null) {
                            active = false
                            writing = false
                            status = "Prepared credential is unavailable. Start again."
                        } else {
                            status = "Writing and verifying the credential…"
                            val execution = withContext(Dispatchers.IO) {
                                As100NfcCredentialProvisioningMethod.provision(
                                    tagSignal = tagSignal,
                                    credential = credential,
                                    writeRequest = NfcWriteRequest(
                                        recordType = "external",
                                        value = credential.envelope,
                                        mimeType = NfcPortableCredentialFormat.MIME_TYPE,
                                        overwritePolicy = overwritePolicy,
                                        expectedCurrentHash = supplied["expected_current_hash"]?.takeIf(String::isNotBlank)
                                    ),
                                    invocationContext = context.request.invocationContext
                                )
                            }
                            val fields = OutputFormatter.fields(execution, false)
                            val verified = fields[NfcWriteFields.WRITE_VERIFIED]?.toString() == "true"
                            val writeCompleted = requiresFreshNdefReadBack(
                                fields[NfcProvisionFields.PROVISION_MESSAGE]?.toString().orEmpty()
                            )
                            if (!verified && writeCompleted) {
                                firstWriteValues = fields.mapValues { it.value.toString() }
                                writing = false
                                awaitingReadBack = true
                                active = false
                                delay(150)
                                active = true
                                status = "The card reset after writing. Tap the same card once more to verify the stored credential."
                            } else {
                                active = false
                                result = execution
                                pendingCredential = null
                                writing = false
                                status = fields[NfcProvisionFields.PROVISION_MESSAGE]?.toString()
                                    ?: "Provisioning finished."
                                if (context.submitsImmediately) onConfirmed(execution)
                            }
                        }
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
            resultPreview = result?.let { OutputFormatter.fields(it, false) }.orEmpty(),
            onBack = onBack,
            onRetry = { startFirstScan() },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            OutlinedTextField(
                value = subjectId,
                onValueChange = { subjectId = it },
                label = { Text("Credential subject ID") },
                enabled = firstTag == null && !active,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = credentialId,
                onValueChange = { credentialId = it },
                label = { Text("Credential ID") },
                enabled = firstTag == null && !active,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                listOf(4, 6).forEach { length ->
                    OutlinedButton(
                        onClick = { pinLength = length },
                        enabled = firstTag == null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (pinLength == length) "✓ $length-digit PIN" else "$length-digit PIN")
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Existing card content", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            if (context.startsImmediately) {
                Text(
                    overwritePolicy.label,
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                OutlinedButton(
                    onClick = { overwritePolicyExpanded = true },
                    enabled = firstTag == null && !active && !writing && !awaitingReadBack,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        overwritePolicy.label,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Start
                    )
                    Text("▼")
                }
                DropdownMenu(
                    expanded = overwritePolicyExpanded,
                    onDismissRequest = { overwritePolicyExpanded = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    listOf(
                        NfcOverwritePolicy.EmptyOnly,
                        NfcOverwritePolicy.Replace
                    ).forEach { policy ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(policy.label)
                                    Text(
                                        policy.wireValue,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            },
                            onClick = {
                                overwritePolicy = policy
                                overwritePolicyExpanded = false
                                if (policy == NfcOverwritePolicy.Replace) {
                                    status =
                                        "Replacement enabled. Existing NDEF content will be overwritten after you scan the card again."
                                }
                            }
                        )
                    }
                }
            }
            Text(
                when (overwritePolicy) {
                    NfcOverwritePolicy.EmptyOnly ->
                        "Safe default: provisioning stops if the card already contains NDEF data."
                    NfcOverwritePolicy.Replace ->
                        "Existing NDEF content will be replaced. The previous message hash is retained in the result."
                    NfcOverwritePolicy.CompareAndReplace ->
                        "Compare-and-replace is not exposed by credential provisioning."
                },
                style = MaterialTheme.typography.labelSmall
            )
            if (firstTag != null && result == null && !awaitingReadBack) {
                Spacer(Modifier.height(12.dp))
                PinField(pin, { pin = digitsOnly(it, pinLength) }, "PIN")
                Spacer(Modifier.height(8.dp))
                PinField(confirmPin, { confirmPin = digitsOnly(it, pinLength) }, "Confirm PIN")
                Spacer(Modifier.height(8.dp))
                Button(onClick = { beginPreparedWrite() }, enabled = !active, modifier = Modifier.fillMaxWidth()) {
                    Text("Continue to write")
                }
            } else if (!active && result == null) {
                Spacer(Modifier.height(12.dp))
                Button(onClick = { startFirstScan() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Scan card to provision")
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(status, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            IntentExampleDropdown(
                capabilityId = capabilityId,
                examples = listOf(
                    IntentExample(
                        label = "Provision a new credential",
                        description = "Safe default for an empty card. The PIN is entered only inside MethodMesh and is never returned to ODK.",
                        intentUri = "com.example.methodmesh.EXECUTE_METHOD(method_id='$capabilityId',input_credential_subject_id='operator_001',input_pin_length='6',input_overwrite_policy='empty_only')"
                    ),
                    IntentExample(
                        label = "Replace an existing credential",
                        description = "Explicitly replace existing writable NDEF content and retain the previous message hash in the result.",
                        intentUri = "com.example.methodmesh.EXECUTE_METHOD(method_id='$capabilityId',input_credential_subject_id='operator_001',input_pin_length='6',input_overwrite_policy='replace')"
                    )
                )
            )
        }
    }
}

object NfcCredentialVerificationCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100NfcCredentialVerificationMethod.ID
    override val title = "NFC credential verification"
    override val description = "Read a portable credential and verify its PIN and issuer signature."

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
        val trustedIssuerFingerprintsRaw = remember(supplied) {
            sequenceOf(
                supplied["trusted_issuer_fingerprint_sha256"],
                supplied["trusted_issuer_fingerprints_sha256"]
            ).filterNotNull().filter(String::isNotBlank).joinToString(",")
        }
        val trustedIssuerFingerprints = remember(trustedIssuerFingerprintsRaw) {
            splitIssuerTrustValues(trustedIssuerFingerprintsRaw)
                .map { it.lowercase() }
                .toSet()
        }
        val invalidTrustedIssuerFingerprints = remember(trustedIssuerFingerprints) {
            trustedIssuerFingerprints.filterNot(::isSha256Hex)
        }
        val trustedIssuerKeyIds = remember(supplied) {
            sequenceOf(supplied["trusted_issuer_key_id"], supplied["trusted_issuer_key_ids"])
                .filterNotNull()
                .flatMap { splitIssuerTrustValues(it).asSequence() }
                .map { it.lowercase() }
                .toSet()
        }
        val issuerTrustSetId = supplied["issuer_trust_set_id"].orEmpty()
        val issuerTrustSetVersion = supplied["issuer_trust_set_version"].orEmpty()
        var tagSignal by remember { mutableStateOf<NfcTagSignal?>(null) }
        var capturedTagValues by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
        var envelope by remember { mutableStateOf("") }
        var expectedPinLength by remember { mutableIntStateOf(6) }
        var pin by remember { mutableStateOf("") }
        var attempts by remember { mutableIntStateOf(0) }
        var active by remember { mutableStateOf(false) }
        val initialStatus = rememberNfcAvailabilityMessage()
        var status by remember { mutableStateOf(initialStatus) }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }

        fun startScan() {
            if (invalidTrustedIssuerFingerprints.isNotEmpty()) {
                status = "Invalid trusted issuer fingerprint. Use full 64-character SHA-256 hexadecimal fingerprints."
                return
            }
            tagSignal = null
            capturedTagValues = emptyMap()
            envelope = ""
            pin = ""
            attempts = 0
            result = null
            active = true
            status = "Tap the credential card."
        }

        fun verifyPin() {
            val signal = tagSignal ?: return
            if (pin.length != expectedPinLength) {
                status = "Enter exactly $expectedPinLength digits."
                return
            }
            if (attempts >= 5) {
                status = "Too many failed attempts. Scan the card again to restart."
                return
            }
            scope.launch {
                status = "Verifying PIN and credential signature…"
                val enteredPin = pin.toCharArray()
                pin = ""
                val verified = withContext(Dispatchers.Default) {
                    NfcPortableCredentialFormat.verify(
                        envelope = envelope,
                        pin = enteredPin,
                        trustedIssuerFingerprintsSha256 = trustedIssuerFingerprints,
                        trustedIssuerKeyIds = trustedIssuerKeyIds
                    )
                }
                if (!verified.verified && verified.pinRejected) {
                    attempts += 1
                    if (attempts >= 5) {
                        status = "Verification failed five times. Scan the card again to restart."
                    } else {
                        status = "${verified.message} ${5 - attempts} attempts remain."
                        delay(attempts * 500L)
                    }
                    return@launch
                }
                val execution = As100NfcCredentialVerificationMethod.result(
                    tagSignal = signal,
                    capturedTagValues = capturedTagValues,
                    credential = verified,
                    issuerTrustSetId = issuerTrustSetId,
                    issuerTrustSetVersion = issuerTrustSetVersion,
                    invocationContext = context.request.invocationContext
                )
                result = execution
                status = verified.message
                if (context.submitsImmediately) onConfirmed(execution)
            }
        }

        LaunchedEffect(context.startsImmediately) {
            if (context.startsImmediately) startScan()
        }

        NfcDeviceServiceEffect(
            enabled = active,
            onStatus = { status = it },
            onSignal = { signal ->
                val tagValues = NfcTagRepository.readTag(signal.androidTag)
                active = false
                val candidate = NfcPortableCredentialFormat.extractEnvelope(
                    listOf(
                        tagValues[NfcEvidenceFields.NDEF_FIRST_PAYLOAD_UTF8].orEmpty(),
                        tagValues[NfcEvidenceFields.NDEF_PAYLOAD_UTF8_ALL].orEmpty(),
                        tagValues[NfcEvidenceFields.NDEF_TEXT].orEmpty()
                    )
                )
                if (candidate == null) {
                    val recordCount = tagValues[NfcEvidenceFields.NDEF_RECORD_COUNT].orEmpty().ifBlank { "0" }
                    val recordTypes = sequenceOf(
                        tagValues[NfcEvidenceFields.NDEF_EXTERNAL_TYPES],
                        tagValues[NfcEvidenceFields.NDEF_MIME_TYPES]
                    ).filterNotNull().filter(String::isNotBlank).joinToString(", ").ifBlank { "unknown" }
                    status = "The tag contains $recordCount NDEF record(s), but none is a supported MethodMesh portable credential (record type: $recordTypes)."
                    return@NfcDeviceServiceEffect
                }
                val parsed = NfcPortableCredentialFormat.parse(candidate)
                tagSignal = signal
                capturedTagValues = tagValues
                envelope = candidate
                expectedPinLength = parsed.pinLength
                status = "Credential detected. Enter its ${parsed.pinLength}-digit PIN."
            }
        )

        CapabilityScreenScaffold(
            title = title,
            capabilityId = context.action.canonicalId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = result,
            resultPreview = result?.let { OutputFormatter.fields(it, false) }.orEmpty(),
            onBack = onBack,
            onRetry = { startScan() },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Text(
                when {
                    trustedIssuerFingerprints.isNotEmpty() -> {
                        val label = listOfNotNull(
                            issuerTrustSetId.takeIf(String::isNotBlank),
                            issuerTrustSetVersion.takeIf(String::isNotBlank)?.let { "v$it" }
                        ).joinToString(" · ").ifBlank { "configured study trust set" }
                        "Issuer trust: $label · ${trustedIssuerFingerprints.size} full SHA-256 fingerprint(s)"
                    }
                    trustedIssuerKeyIds.isNotEmpty() ->
                        "Issuer trust: legacy short-key-ID allow-list (${trustedIssuerKeyIds.size})"
                    else ->
                        "Issuer trust: not configured. Signature and PIN can be checked, but issuer trust will be reported as not_checked."
                },
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(Modifier.height(8.dp))
            if (tagSignal == null && !active && result == null) {
                Button(onClick = { startScan() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Scan credential")
                }
            }
            if (tagSignal != null && result == null) {
                PinField(pin, { pin = digitsOnly(it, expectedPinLength) }, "$expectedPinLength-digit PIN")
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { verifyPin() },
                    enabled = attempts < 5,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Verify credential")
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(status, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            IntentExampleDropdown(
                capabilityId = capabilityId,
                examples = listOf(
                    IntentExample(
                        label = "Verify a credential",
                        description = "Scan the card, enter its PIN inside MethodMesh, and optionally supply an offline study trust set of full SHA-256 issuer fingerprints.",
                        intentUri = "com.example.methodmesh.EXECUTE_METHOD(method_id='$capabilityId',input_payload_mode='FULL',return_mode='flat')"
                    )
                )
            )
        }
    }
}

@Composable
private fun PinField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

private fun digitsOnly(value: String, maxLength: Int): String =
    value.filter(Char::isDigit).take(maxLength)


private fun splitIssuerTrustValues(value: String): List<String> =
    value.split(',', ';', '\n', '\r', ' ')
        .map(String::trim)
        .filter(String::isNotBlank)

private fun isSha256Hex(value: String): Boolean =
    value.length == 64 && value.all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }
