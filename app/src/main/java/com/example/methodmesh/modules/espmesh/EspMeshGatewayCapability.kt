package com.example.methodmesh.modules.espmesh

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec

object EspMeshGatewayCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100EspMeshGatewayMethod.ID
    override val title = "ESP mesh gateway"
    override val description = "Discover and provision a nearby MethodMesh BLE gateway."

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val app = LocalContext.current.applicationContext
        val provider = remember { EspMeshTransportProvider.get(app) }
        val status by provider.status.collectAsState()
        val candidates = remember { mutableStateListOf<EspMeshGatewayCandidate>() }
        var scanning by rememberSaveable { mutableStateOf(false) }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (it.values.all { granted -> granted }) {
                candidates.clear(); scanning = true
                provider.scan({ candidate -> if (candidates.none { it.address == candidate.address }) candidates += candidate }, { scanning = false })
            }
        }
        fun requiredPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= 31) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        fun scan() {
            val missing = requiredPermissions().filter { ContextCompat.checkSelfPermission(app, it) != android.content.pm.PackageManager.PERMISSION_GRANTED }
            if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
            else { candidates.clear(); scanning = true; provider.scan({ candidate -> if (candidates.none { it.address == candidate.address }) candidates += candidate }, { scanning = false }) }
        }
        fun provision(candidate: EspMeshGatewayCandidate) {
            provider.provision(candidate)
            val request = As100EspMeshGatewayMethod.request(capabilityId, emptyMap(), emptyList(), emptyList())
            result = As100EspMeshGatewayMethod.execute(request, null, "espmesh")
            if (context.submitsImmediately) result?.let(onConfirmed)
        }
        CapabilityScreenScaffold(
            title = title, capabilityId = capabilityId, context = context, canGoBack = context.stepNumber > 1,
            capturedResult = result, resultPreview = result?.let { OutputFormatter.fields(it, false) }.orEmpty(),
            onBack = onBack, onRetry = { scan() }, onConfirm = { result?.let(onConfirmed) }, onCancel = onCancel
        ) {
            Text("Choose a nearby gateway. Bluetooth addresses remain transport details; MethodMesh uses the gateway identity reported by the protocol.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Text("${status.detail} · ${if (status.connected) "connected" else "not connected"}", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { scan() }, enabled = !scanning, modifier = Modifier.fillMaxWidth()) { Text(if (scanning) "Scanning…" else "Scan for gateways") }
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(candidates, key = { it.address }) { candidate ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(candidate.name, style = MaterialTheme.typography.titleMedium)
                                Text("${candidate.address} · RSSI ${candidate.rssi}", style = MaterialTheme.typography.bodySmall)
                            }
                            OutlinedButton(onClick = { provision(candidate) }) { Text("Use") }
                        }
                    }
                }
            }
        }
    }
}
