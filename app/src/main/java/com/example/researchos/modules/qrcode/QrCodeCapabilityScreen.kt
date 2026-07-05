package com.example.researchos.modules.qrcode

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.researchos.core.researchos.ExecutionResult
import com.example.researchos.core.researchos.withInvocationContext
import com.example.researchos.transport.OutputFormatter
import com.example.researchos.transport.workflow.ui.CapabilityScreenContext
import com.example.researchos.transport.workflow.ui.CapabilityScreenScaffold
import com.example.researchos.transport.workflow.ui.CapabilityScreenSpec

object QrScanCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId: String = As100QrScanMethod.ID
    override val title: String = "QR token scan"
    override val description: String = "Capture QR token evidence for workflows and dependent capabilities."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        var payload by remember { mutableStateOf(context.action.settings["qr_payload"].orEmpty()) }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var status by remember { mutableStateOf("Ready to capture QR token evidence.") }

        fun capture() {
            val execution = As100QrScanMethod.execute(
                request = As100QrScanMethod.request(
                    action = context.action.canonicalId,
                    context = context.request.invocationContext.asMap(context.action.canonicalId) + context.action.settings + mapOf(
                        "qr_payload" to payload,
                        "qr_source" to "qr_capability_screen"
                    )
                ),
                settingsState = null,
                transport = context.request.source
            ).withInvocationContext(context.request.invocationContext)
            result = execution
            val fields = OutputFormatter.fields(execution, includeProvenance = false)
            status = fields["qr_payload_hash"]?.toString()?.let { "QR token captured: ${it.take(18)}…" }
                ?: "QR token capture failed or no payload was supplied."
        }

        CapabilityScreenScaffold(
            title = title,
            capabilityId = context.action.canonicalId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = result,
            resultPreview = result?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty(),
            onBack = onBack,
            onRetry = { result = null; status = "Ready to capture QR token evidence." },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Text("QR is a standalone capability. Attestation can depend on this result instead of implementing its own scanner.")
            Spacer(Modifier.height(8.dp))
            Text("Current implementation accepts a QR payload from the UI or an external scanner handoff. Camera decoding can be added inside this QR module without changing attestation.", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = payload,
                onValueChange = { payload = it },
                label = { Text("QR payload / token") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Text(status)
            Row(modifier = Modifier.padding(top = 8.dp)) {
                Button(onClick = { capture() }) { Text(if (result == null) "Capture QR evidence" else "Capture again") }
                Spacer(Modifier.padding(4.dp))
                OutlinedButton(onClick = { result = null; status = "Ready to capture QR token evidence." }) { Text("Clear") }
            }
        }
    }
}
