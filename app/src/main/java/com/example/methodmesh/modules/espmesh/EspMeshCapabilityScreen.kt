package com.example.methodmesh.modules.espmesh

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.transport.MethodMeshTransportEnvelope
import com.example.methodmesh.core.transport.MethodMeshTransportRuntime
import com.example.methodmesh.core.transport.TransportEndpoint
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import org.json.JSONObject
import kotlinx.coroutines.launch

object EspMeshCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100EspMeshMessageMethod.ID
    override val title = "ESP mesh message"
    override val description = "Queue a MethodMesh message for a provisioned field gateway."

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val app = LocalContext.current.applicationContext
        val scope = rememberCoroutineScope()
        var destination by rememberSaveable { mutableStateOf(context.action.settings["destination"] ?: "field-group") }
        var messageType by rememberSaveable { mutableStateOf(context.action.settings["message_type"] ?: "TEXT") }
        var payload by rememberSaveable { mutableStateOf(context.action.settings["payload"] ?: "") }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        fun queue() {
            val envelope = MethodMeshTransportEnvelope(
                source = TransportEndpoint("installation", "local"),
                destination = TransportEndpoint("logical", destination.trim().ifBlank { "field-group" }),
                messageType = messageType.trim().uppercase().ifBlank { "TEXT" },
                moduleId = "espmesh", capabilityId = capabilityId,
                payloadType = "text/plain", payload = payload
            )
            val request = As100EspMeshMessageMethod.request(capabilityId, emptyMap(), emptyList(), emptyList())
            result = As100EspMeshMessageMethod.execute(request, null, "espmesh")
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                MethodMeshTransportRuntime.get(app).send(envelope, EspMeshTransportProvider.TRANSPORT_ID)
            }
            if (context.submitsImmediately) result?.let(onConfirmed)
        }
        CapabilityScreenScaffold(
            title = title, capabilityId = capabilityId, context = context, canGoBack = context.stepNumber > 1,
            capturedResult = result,
            resultPreview = result?.let { OutputFormatter.fields(it, false) }.orEmpty(),
            onBack = onBack, onRetry = { queue() }, onConfirm = { result?.let(onConfirmed) }, onCancel = onCancel
        ) {
            Text("Messages are opaque to core and remain queued if the gateway is unavailable.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(destination, { destination = it }, label = { Text("Logical destination") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(messageType, { messageType = it }, label = { Text("Message type") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(payload, { payload = it }, label = { Text("Message") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Button(onClick = { queue() }, modifier = Modifier.fillMaxWidth()) { Text("Queue message") }
            Text("Gateway status is managed by the ESP mesh transport provider.", modifier = Modifier.fillMaxWidth())
        }
    }
}
