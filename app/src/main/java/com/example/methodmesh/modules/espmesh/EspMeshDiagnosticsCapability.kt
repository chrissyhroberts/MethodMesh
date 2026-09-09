package com.example.methodmesh.modules.espmesh

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.transport.MethodMeshTransportRuntime
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec

object EspMeshDiagnosticsCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100EspMeshDiagnosticsMethod.ID
    override val title = "ESP mesh diagnostics"
    override val description = "Inspect gateway and durable transport state without exposing keys."

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val app = LocalContext.current.applicationContext
        val provider = remember { EspMeshTransportProvider.get(app) }
        var diagnostics by remember { mutableStateOf(MethodMeshTransportRuntime.get(app).diagnostics()) }
        CapabilityScreenScaffold(
            title = title, capabilityId = capabilityId, context = context, canGoBack = context.stepNumber > 1,
            capturedResult = null, resultPreview = emptyMap(), onBack = onBack,
            onRetry = { diagnostics = MethodMeshTransportRuntime.get(app).diagnostics() },
            onConfirm = {}, onCancel = onCancel
        ) {
            Text("Transport state", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text("Registered providers: ${diagnostics.registeredTransports.joinToString().ifBlank { "none" }}")
            val gateway = provider.gatewayInfo.value
            if (gateway.nodeId.isNotBlank()) {
                Text("Gateway node: ${gateway.nodeId}")
                Text("Firmware: ${gateway.firmware.ifBlank { "unknown" }}")
                Text("Network: ${gateway.networkId.ifBlank { "not provisioned" }}")
            }
            diagnostics.transportStatuses.forEach { (id, status) ->
                Text("$id · available=${status.available} · connected=${status.connected}")
                Text(status.detail, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            Text("Inbox records: ${diagnostics.inboxCount}")
            Text("Outbox records: ${diagnostics.outboxCount}")
            Text("Pending outbound: ${diagnostics.pendingOutboxCount}")
            Spacer(Modifier.height(10.dp))
            Button(onClick = { diagnostics = MethodMeshTransportRuntime.get(app).diagnostics() }, modifier = Modifier.fillMaxWidth()) { Text("Refresh diagnostics") }
            Text("Keys and opaque payloads are not shown here.", modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
}
