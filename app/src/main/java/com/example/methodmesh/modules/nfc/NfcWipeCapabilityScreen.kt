package com.example.methodmesh.modules.nfc

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
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

object NfcWipeCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100NfcWipeMethod.ID
    override val title = "NFC tag wipe"
    override val description = "Remove NDEF user content and verify an empty NDEF message."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val scope = rememberCoroutineScope()
        val initialStatus = rememberNfcAvailabilityMessage()
        var active by remember { mutableStateOf(false) }
        var status by remember { mutableStateOf(initialStatus) }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var awaitingReadBack by remember { mutableStateOf(false) }
        var firstTagUid by remember { mutableStateOf("") }
        var firstWipeValues by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

        fun startWipe() {
            result = null
            awaitingReadBack = false
            firstTagUid = ""
            firstWipeValues = emptyMap()
            active = true
            status = "Tap the NFC tag and keep it against the phone until the empty message is verified."
        }

        LaunchedEffect(context.startsImmediately) {
            if (context.startsImmediately) startWipe()
        }

        NfcDeviceServiceEffect(
            enabled = active,
            onStatus = { status = it },
            onSignal = { signal ->
                scope.launch {
                    val uid = NfcTagRepository.tagUidHex(signal.androidTag)
                    if (awaitingReadBack) {
                        if (uid != firstTagUid) {
                            active = false
                            status = "That is a different tag. Tap the tag that was just wiped."
                            delay(150)
                            active = true
                        } else {
                            status = "Verifying that the tag is empty…"
                            val execution = withContext(Dispatchers.IO) {
                                As100NfcWipeMethod.confirmEmpty(
                                    signal,
                                    firstWipeValues,
                                    context.request.invocationContext
                                )
                            }
                            active = false
                            awaitingReadBack = false
                            result = execution
                            val fields = OutputFormatter.fields(execution, false)
                            status = fields[NfcWipeFields.WIPE_MESSAGE]?.toString() ?: "Wipe verification finished."
                            if (context.startsImmediately) onConfirmed(execution)
                        }
                    } else {
                        firstTagUid = uid
                        status = "Removing NDEF content and verifying the tag…"
                        val execution = withContext(Dispatchers.IO) {
                            As100NfcWipeMethod.wipe(signal, context.request.invocationContext)
                        }
                        val fields = OutputFormatter.fields(execution, false)
                        val verified = fields[NfcWriteFields.WRITE_VERIFIED]?.toString() == "true"
                        val writeCompleted = requiresFreshNdefReadBack(
                            fields[NfcWipeFields.WIPE_MESSAGE]?.toString().orEmpty()
                        )
                        if (!verified && writeCompleted) {
                            firstWipeValues = fields.mapValues { it.value.toString() }
                            active = false
                            awaitingReadBack = true
                            delay(150)
                            active = true
                            status = "The tag reset after wiping. Tap the same tag once more to verify that it is empty."
                        } else {
                            active = false
                            result = execution
                            status = fields[NfcWipeFields.WIPE_MESSAGE]?.toString() ?: "Wipe finished."
                            if (context.startsImmediately) onConfirmed(execution)
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
            onRetry = { startWipe() },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Text(
                "This replaces the NDEF message with an empty record. It does not claim forensic erasure of the chip's physical memory.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(12.dp))
            Text(status)
            if (!context.startsImmediately && !active && result == null) {
                Spacer(Modifier.height(10.dp))
                Button(onClick = { startWipe() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Wipe NDEF content")
                }
            }
            Spacer(Modifier.height(16.dp))
            IntentExampleDropdown(
                capabilityId = capabilityId,
                examples = listOf(
                    IntentExample(
                        label = "Wipe NDEF content",
                        description = "Replace the current NDEF message with a verified empty record.",
                        intentUri = "com.example.methodmesh.EXECUTE_METHOD(method_id='$capabilityId',return_mode='flat')"
                    )
                )
            )
        }
    }
}
