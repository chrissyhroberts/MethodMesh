package com.example.methodmesh.modules.espmesh

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.transport.MethodMeshTransportEnvelope
import com.example.methodmesh.core.transport.MethodMeshTransportRuntime
import com.example.methodmesh.core.transport.TransportEndpoint
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Backwards-compatible Workbench test harness; transport itself runs in the service. */
object EspMeshCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100EspMeshMessageMethod.ID
    override val title = "ESP mesh transport test"
    override val description = "Send a test envelope through the persistent encrypted field transport."

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val app = LocalContext.current.applicationContext
        val scope = rememberCoroutineScope()
        val provider = remember { EspMeshTransportProvider.get(app) }
        val inbound by provider.recentInbound.collectAsState()
        val snapshot by provider.snapshot.collectAsState()
        var destination by rememberSaveable { mutableStateOf(context.action.settings["destination"] ?: "field-group") }
        var messageType by rememberSaveable { mutableStateOf(context.action.settings["message_type"] ?: "TEXT") }
        var payload by rememberSaveable { mutableStateOf(context.action.settings["payload"] ?: "") }
        var sendStatus by rememberSaveable { mutableStateOf("") }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }

        fun queue() {
            val envelope = MethodMeshTransportEnvelope(
                source = TransportEndpoint("mesh-phone", provider.phoneId()),
                destination = TransportEndpoint("logical", destination.trim().ifBlank { "field-group" }),
                messageType = messageType.trim().uppercase().ifBlank { "TEXT" },
                moduleId = "espmesh", capabilityId = capabilityId,
                payloadType = "text/plain", payload = payload
            )
            val request = As100EspMeshMessageMethod.request(capabilityId, emptyMap(), emptyList(), emptyList())
            result = As100EspMeshMessageMethod.execute(request, null, "espmesh")
            scope.launch {
                sendStatus = "Encrypting and adding to durable outbox…"
                val sent = withContext(Dispatchers.IO) { MethodMeshTransportRuntime.get(app).send(envelope, EspMeshTransportProvider.TRANSPORT_ID) }
                sendStatus = sent.detail.ifBlank { sent.state.name }
            }
            if (context.submitsImmediately) result?.let(onConfirmed)
        }

        CapabilityScreenScaffold(
            title = title, capabilityId = capabilityId, context = context, canGoBack = context.stepNumber > 1,
            capturedResult = result, resultPreview = result?.let { OutputFormatter.fields(it, false) }.orEmpty(),
            onBack = onBack, onRetry = { queue() }, onConfirm = { result?.let(onConfirmed) }, onCancel = onCancel
        ) {
            Text("This is a diagnostic/test surface. The mesh service continues listening, retrying and reconciling queues when this screen is closed.", style = MaterialTheme.typography.bodyMedium)
            Text("Transport: ${if (snapshot.enabled) "running" else "paused"} · ${if (snapshot.connected) "gateway connected" else "gateway unavailable"} · ${snapshot.outboxPending} pending", style = MaterialTheme.typography.bodySmall)
            Text("E2E key: ${snapshot.e2eKeyId.ifBlank { "not configured" }}", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(destination, { destination = it }, label = { Text("Logical destination") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(messageType, { messageType = it }, label = { Text("Message type") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(payload, { payload = it }, label = { Text("Message") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Button(onClick = { queue() }, enabled = snapshot.e2eKeyId.isNotBlank() && payload.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Send test message") }
            if (sendStatus.isNotBlank()) Text(sendStatus, style = MaterialTheme.typography.bodySmall)
            if (inbound.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Recently received by background transport", style = MaterialTheme.typography.titleMedium)
                inbound.take(10).forEach { message ->
                    Text("${message.source.id} → ${message.destination.id}: ${message.payload}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
