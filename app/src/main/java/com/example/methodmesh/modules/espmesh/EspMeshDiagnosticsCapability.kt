package com.example.methodmesh.modules.espmesh

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec

object EspMeshDiagnosticsCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100EspMeshDiagnosticsMethod.ID
    override val title = "ESP mesh transport diagnostics"
    override val description = "Inspect persistent service, encrypted queues and gateway spool state without exposing keys or plaintext."

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val app = LocalContext.current.applicationContext
        val provider = remember { EspMeshTransportProvider.get(app) }
        val walkie = remember { EspMeshWalkieTalkieController.get(app) }
        val snapshot by provider.snapshot.collectAsState()
        val voice by walkie.state.collectAsState()
        val status by provider.status.collectAsState()
        val gateway by provider.gatewayInfo.collectAsState()
        var queue by remember { mutableStateOf(provider.queueSnapshot()) }
        CapabilityScreenScaffold(
            title = title, capabilityId = capabilityId, context = context, canGoBack = context.stepNumber > 1,
            capturedResult = null, resultPreview = emptyMap(), onBack = onBack,
            onRetry = { queue = provider.queueSnapshot() }, onConfirm = {}, onCancel = onCancel
        ) {
            Text("Persistent transport", style = MaterialTheme.typography.titleMedium)
            Text("Enabled: ${snapshot.enabled} · connected: ${snapshot.connected}")
            Text(status.detail, style = MaterialTheme.typography.bodySmall)
            Text("Phone ID: ${snapshot.phoneId}", style = MaterialTheme.typography.bodySmall)
            Text("E2E key ID: ${snapshot.e2eKeyId.ifBlank { "not configured" }}", style = MaterialTheme.typography.bodySmall)
            if (gateway.nodeId.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text("Gateway", style = MaterialTheme.typography.titleMedium)
                Text("${gateway.nodeId} · ${gateway.firmware.ifBlank { "unknown firmware" }}")
                Text("Network: ${gateway.networkId.ifBlank { "not provisioned" }}")
                Text("ESP spool → radio: ${gateway.pendingForRadio} · → phone: ${gateway.pendingForPhone}")
                if (gateway.spoolError.isNotBlank()) Text("ESP spool error: ${gateway.spoolError}", color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(8.dp))
            Text("Live voice", style = MaterialTheme.typography.titleMedium)
            Text("Listening: ${voice.listening} · channel: ${voice.channel}")
            Text("BLE MTU: ${snapshot.bleMtu} · low-latency voice ready: ${snapshot.liveVoiceReady}", style = MaterialTheme.typography.bodySmall)
            Text("State: ${if (voice.transmitting) "transmitting" else if (voice.receiving) "receiving ${voice.activeSpeaker}" else "idle"}", style = MaterialTheme.typography.bodySmall)
            Text("Packets sent: ${voice.packetsSent} · received: ${voice.packetsReceived} · lost/rejected: ${voice.packetsDropped}", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Text("Encrypted phone queues", style = MaterialTheme.typography.titleMedium)
            Text("Pending outbound: ${queue.outboundPending} / ${queue.outboundTotal}")
            Text("Encrypted inbound: ${queue.inboundTotal} · failed authentication/decrypt: ${queue.inboundFailed}")
            Text("End-to-end delivered: ${queue.delivered}")
            Spacer(Modifier.height(10.dp))
            Button(onClick = { queue = provider.queueSnapshot() }, modifier = Modifier.fillMaxWidth()) { Text("Refresh diagnostics") }
            Text("Diagnostics deliberately omit group/network keys, message plaintext and voice audio.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
