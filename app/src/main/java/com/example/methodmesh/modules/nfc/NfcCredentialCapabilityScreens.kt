package com.example.methodmesh.modules.nfc

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.shape.RoundedCornerShape
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
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

private enum class CredentialProvisioningStage {
    Setup,
    ScanCard,
    EnterPin,
    ConfirmPin,
    Write,
    Verify,
    Result
}

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
        val suppliedValidUntilDate = sequenceOf(
            supplied["valid_until_date"],
            supplied["input_valid_until_date"],
            supplied["credential_valid_until_date"],
            supplied["input_credential_valid_until_date"]
        ).map { it?.trim().orEmpty() }.firstOrNull(String::isNotBlank).orEmpty()
        var validUntilDate by remember { mutableStateOf(suppliedValidUntilDate) }
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
        var readerArmToken by remember { mutableIntStateOf(0) }
        var pin by remember { mutableStateOf("") }
        var confirmPin by remember { mutableStateOf("") }
        var stage by remember { mutableStateOf(CredentialProvisioningStage.Setup) }
        var firstWriteValues by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
        val initialStatus = rememberNfcAvailabilityMessage()
        var status by remember { mutableStateOf(initialStatus) }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }

        LaunchedEffect(subjectId, credentialId, validUntilDate, pinLength, overwritePolicy) {
            context.onSettingsChanged(
                mapOf(
                    NfcProvisionFields.CREDENTIAL_SUBJECT_ID to subjectId,
                    NfcProvisionFields.CREDENTIAL_ID to credentialId,
                    "valid_until_date" to validUntilDate,
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
            firstWriteValues = emptyMap()
            stage = CredentialProvisioningStage.ScanCard
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
                    val validUntil = runCatching {
                        // The supplied calendar date is inclusive. Store the next
                        // UTC midnight as an exclusive instant so the credential
                        // remains valid for the whole stated day.
                        LocalDate.parse(validUntilDate).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
                    }.getOrElse {
                        status = "Enter valid_until_date as an ISO date (YYYY-MM-DD)."
                        return@launch
                    }
                    status = "Preparing the encrypted credential…"
                    val credential = runCatching {
                        withContext(Dispatchers.Default) {
                            NfcPortableCredentialFormat.provision(
                                credentialSubjectId = subjectId,
                                credentialId = credentialId,
                                pin = pin.toCharArray(),
                                signer = AndroidNfcCredentialSigner,
                                validUntil = validUntil
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
                    stage = CredentialProvisioningStage.Write
                    status = "PIN confirmed. Tap the same card to write the credential."
                }
            }
        }

        LaunchedEffect(context.startsImmediately) {
            if (context.startsImmediately) startFirstScan()
        }

        NfcDeviceServiceEffect(
            enabled = stage == CredentialProvisioningStage.ScanCard ||
                stage == CredentialProvisioningStage.Write ||
                stage == CredentialProvisioningStage.Verify,
            operationKey = "provisioning-$stage-$readerArmToken",
            holdReaderMode = true,
            onStatus = { status = it },
            onSignal = { tagSignal ->
                scope.launch {
                    val uid = NfcTagRepository.tagUidHex(tagSignal.androidTag)
                    if (stage == CredentialProvisioningStage.Verify) {
                        val credential = pendingCredential
                        if (uid != firstTagUid) {
                            readerArmToken += 1
                            status = "That is a different card. Tap the card that was just provisioned."
                        } else if (credential == null) {
                            stage = CredentialProvisioningStage.Result
                            status = "Prepared credential is unavailable. Start again."
                        } else {
                            status = "Reading back and verifying the credential…"
                            val execution = withContext(Dispatchers.IO) {
                                As100NfcCredentialProvisioningMethod.confirmReadBack(
                                    tagSignal = tagSignal,
                                    credential = credential,
                                    overwritePolicy = overwritePolicy,
                                    previousValues = firstWriteValues,
                                    invocationContext = context.request.invocationContext
                                )
                            }
                            pendingCredential = null
                            result = execution
                            stage = CredentialProvisioningStage.Result
                            status = OutputFormatter.fields(execution, false)[NfcProvisionFields.PROVISION_MESSAGE]
                                ?.toString()
                                ?: "Provisioning verification finished."
                            if (context.submitsImmediately) onConfirmed(execution)
                        }
                    } else if (stage == CredentialProvisioningStage.ScanCard) {
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
                            readerArmToken += 1
                            stage = CredentialProvisioningStage.EnterPin
                            status = "Card accepted. Create a $pinLength-digit PIN."
                        }
                    } else if (stage == CredentialProvisioningStage.Write) {
                        val credential = pendingCredential
                        if (uid != firstTagUid) {
                            pendingCredential = null
                            stage = CredentialProvisioningStage.Result
                            status = "That is a different card. Provisioning cancelled before writing."
                        } else if (credential == null) {
                            stage = CredentialProvisioningStage.Result
                            status = "Prepared credential is unavailable. Start again."
                        } else {
                            status = "Writing the credential. Keep the card against the phone…"
                            val execution = withContext(Dispatchers.IO) {
                                As100NfcCredentialProvisioningMethod.provision(
                                    tagSignal = tagSignal,
                                    credential = credential,
                                    writeRequest = NfcWriteRequest(
                                        recordType = "external",
                                        value = credential.envelope,
                                        mimeType = NfcPortableCredentialFormat.MIME_TYPE,
                                        overwritePolicy = overwritePolicy,
                                        expectedCurrentHash = supplied["expected_current_hash"]?.takeIf(String::isNotBlank),
                                        verifyAfterWrite = false
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
                                stage = CredentialProvisioningStage.Verify
                                status = "Write complete. Tap the same card again to verify the credential."
                            } else {
                                result = execution
                                pendingCredential = null
                                stage = CredentialProvisioningStage.Result
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
            resultPreview = result?.let { provisioningHumanResult(it) }.orEmpty(),
            onBack = onBack,
            onRetry = { startFirstScan() },
        onConfirm = { result?.let(onConfirmed) },
        onCancel = onCancel
        ) {
            when (stage) {
                CredentialProvisioningStage.Setup -> {
                    OutlinedTextField(
                        value = subjectId,
                        onValueChange = { subjectId = it },
                        label = { Text("Credential subject ID") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = credentialId,
                        onValueChange = { credentialId = it },
                        label = { Text("Credential ID") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = validUntilDate,
                        onValueChange = { validUntilDate = it },
                        label = { Text("Valid until (YYYY-MM-DD)") },
                        supportingText = { Text("Inclusive date; the credential expires after this UTC day.") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth()) {
                        listOf(4, 6).forEach { length ->
                            OutlinedButton(
                                onClick = { pinLength = length },
                                modifier = Modifier.weight(1f)
                            ) { Text(if (pinLength == length) "✓ $length-digit PIN" else "$length-digit PIN") }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Existing card content", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = { overwritePolicyExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(overwritePolicy.label, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                        Text("▼")
                    }
                    DropdownMenu(
                        expanded = overwritePolicyExpanded,
                        onDismissRequest = { overwritePolicyExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        listOf(NfcOverwritePolicy.EmptyOnly, NfcOverwritePolicy.Replace).forEach { policy ->
                            DropdownMenuItem(
                                text = { Text(policy.label) },
                                onClick = { overwritePolicy = policy; overwritePolicyExpanded = false }
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (overwritePolicy == NfcOverwritePolicy.EmptyOnly) {
                            "Safe default: provisioning stops if the card already contains NDEF data."
                        } else {
                            "Existing NDEF content will be replaced."
                        },
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { startFirstScan() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Start provisioning")
                    }
                }
                CredentialProvisioningStage.ScanCard -> ProvisioningStep(
                    number = 1,
                    title = "Prepare the card",
                    instruction = "Tap the NFC card and hold it still."
                )
                CredentialProvisioningStage.EnterPin -> {
                    ProvisioningStep(2, "Create a PIN", "Choose a $pinLength-digit PIN for this credential.")
                    PinField(pin, {}, "Create PIN", pinLength)
                    PinPad(pin, { pin = it }, pinLength)
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { if (pin.length == pinLength) { confirmPin = ""; stage = CredentialProvisioningStage.ConfirmPin; status = "Confirm the PIN." } else status = "Enter all $pinLength digits." },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Continue") }
                }
                CredentialProvisioningStage.ConfirmPin -> {
                    ProvisioningStep(3, "Confirm the PIN", "Enter the same PIN again.")
                    PinField(confirmPin, {}, "Confirm PIN", pinLength)
                    PinPad(confirmPin, { confirmPin = it }, pinLength)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { beginPreparedWrite() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Confirm PIN")
                    }
                }
                CredentialProvisioningStage.Write -> ProvisioningStep(
                    number = 4,
                    title = "Write the credential",
                    instruction = "Tap the same card and keep it against the phone until the write completes."
                )
                CredentialProvisioningStage.Verify -> ProvisioningStep(
                    number = 5,
                    title = "Verify the card",
                    instruction = "Tap the same card again to confirm the credential was stored correctly."
                )
                CredentialProvisioningStage.Result -> {
                    Text(status, style = MaterialTheme.typography.bodyLarge)
                    if (result == null) {
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { startFirstScan() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Try again")
                        }
                    }
                }
            }
            if (stage != CredentialProvisioningStage.Result) {
                Spacer(Modifier.height(12.dp))
                Text(status, style = MaterialTheme.typography.bodyMedium)
            }
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
        val trustedIssuers = remember(supplied) {
            sequenceOf(supplied["trusted_issuer_key_id"], supplied["trusted_issuer_key_ids"])
                .filterNotNull()
                .flatMap { it.split(',').asSequence() }
                .map(String::trim)
                .filter(String::isNotBlank)
                .toSet()
        }
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
                    NfcPortableCredentialFormat.verify(envelope, enteredPin, trustedIssuers)
                }
                if (!verified.verified) {
                    val failure = As100NfcCredentialVerificationMethod.failed(
                        tagSignal = signal,
                        capturedTagValues = capturedTagValues,
                        credential = verified,
                        invocationContext = context.request.invocationContext
                    )
                    result = failure
                    attempts += 1
                    val retryable = verified.message == "PIN is incorrect."
                    if (retryable && attempts < 5) {
                        status = "${verified.message} ${5 - attempts} attempts remain."
                        delay(attempts * 500L)
                    } else {
                        status = if (retryable) {
                            "Verification failed five times. ${verified.message}"
                        } else {
                            verified.message
                        }
                        if (context.submitsImmediately) onConfirmed(failure)
                    }
                    return@launch
                }
                val execution = As100NfcCredentialVerificationMethod.verified(
                    tagSignal = signal,
                    capturedTagValues = capturedTagValues,
                    credential = verified,
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
            holdReaderMode = true,
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
            if (tagSignal == null && !active && result == null) {
                Button(onClick = { startScan() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Scan credential")
                }
            }
            if (tagSignal != null && result == null) {
                PinField(pin, { pin = it }, "Enter PIN", expectedPinLength, autoFocus = true)
                PinPad(pin, { pin = it }, expectedPinLength)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (attempts == 0) {
                        "5 attempts available before the card must be scanned again."
                    } else {
                        "${5 - attempts} attempt${if (5 - attempts == 1) "" else "s"} remaining before re-scan."
                    },
                    style = MaterialTheme.typography.labelSmall
                )
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
                        description = "Scan the card, then enter its PIN inside MethodMesh.",
                        intentUri = "com.example.methodmesh.EXECUTE_METHOD(method_id='$capabilityId',return_mode='flat')"
                    )
                )
            )
        }
    }
}

@Composable
private fun PinField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    pinLength: Int,
    autoFocus: Boolean = false
) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(pinLength) { index ->
                Surface(
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(
                        1.dp,
                        if (index == value.length && value.length < pinLength) {
                            MaterialTheme.colorScheme.primary
                        } else MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(if (index < value.length) "●" else "", style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun PinPad(value: String, onValueChange: (String) -> Unit, pinLength: Int) {
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    if (key.isBlank()) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        OutlinedButton(
                            onClick = {
                                onValueChange(
                                    if (key == "⌫") value.dropLast(1)
                                    else digitsOnly(value + key, pinLength)
                                )
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            enabled = key == "⌫" || value.length < pinLength
                        ) { Text(key, style = MaterialTheme.typography.titleMedium) }
                    }
                }
            }
        }
        OutlinedButton(onClick = { onValueChange("") }, modifier = Modifier.fillMaxWidth()) {
            Text("Clear")
        }
    }
}

@Composable
private fun ProvisioningStep(number: Int, title: String, instruction: String) {
    Text("Step $number of 5", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(6.dp))
    Text(title, style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(8.dp))
    Text(instruction, style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(14.dp))
}

private fun provisioningHumanResult(result: ExecutionResult): Map<String, Any?> {
    val fields = OutputFormatter.fields(result, false)
    return linkedMapOf(
        "provision_success" to fields[NfcProvisionFields.PROVISION_SUCCESS],
        "provision_message" to fields[NfcProvisionFields.PROVISION_MESSAGE],
        "credential_id" to fields[NfcProvisionFields.CREDENTIAL_ID],
        "credential_subject_id" to fields[NfcProvisionFields.CREDENTIAL_SUBJECT_ID],
        "write_verified" to fields[NfcWriteFields.WRITE_VERIFIED]
    ).filterValues { it?.toString().orEmpty().isNotBlank() }
}

private fun digitsOnly(value: String, maxLength: Int): String =
    value.filter(Char::isDigit).take(maxLength)
