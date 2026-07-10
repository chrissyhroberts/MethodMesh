package com.example.researchos.modules.nfc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.researchos.core.researchos.ExecutionResult
import com.example.researchos.core.researchos.withInvocationContext
import com.example.researchos.transport.OutputFormatter
import com.example.researchos.transport.workflow.ui.CapabilityScreenContext
import com.example.researchos.transport.workflow.ui.CapabilityScreenScaffold
import com.example.researchos.transport.workflow.ui.CapabilityScreenSpec

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
        val initialStatus = rememberNfcAvailabilityMessage()
        var active by remember { mutableStateOf(false) }
        var status by remember { mutableStateOf(initialStatus) }
        var bundle by remember { mutableStateOf<NfcReadEvidenceBundle?>(null) }
        val execution = bundle?.executionResult?.withInvocationContext(request.invocationContext)

        fun startCapture() {
            active = true
            bundle = null
            status = "Waiting for NFC tag…"
        }

        LaunchedEffect(Unit) { startCapture() }

        NfcDeviceServiceEffect(
            enabled = active,
            onStatus = { status = it },
            onSignal = { tagSignal ->
                val read = As100NfcReadMethod.readBundle(tagSignal, request.invocationContext)
                bundle = read
                active = false
                status = "Tag captured: ${read.evidence.values[NfcEvidenceFields.TAG_UID_HEX].orEmpty()}"
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
            Text("Tap an NFC tag to capture its UID and payload evidence.")
            Text(status)
            Spacer(Modifier.height(10.dp))
            if (active) {
                OutlinedButton(onClick = { active = false; status = "NFC capture stopped." }) { Text("Stop scan") }
            }
        }
    }
}
