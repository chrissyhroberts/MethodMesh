package com.example.methodmesh.modules.espmesh

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec

/** Workbench control plane for the persistent mesh transport. */
object EspMeshGatewayCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100EspMeshGatewayMethod.ID
    override val title = "ESP mesh transport"
    override val description = "Configure the persistent BLE↔ESP-NOW store-and-forward transport."

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val uiContext = LocalContext.current
        val app = uiContext.applicationContext
        val provider = remember { EspMeshTransportProvider.get(app) }
        val walkie = remember { EspMeshWalkieTalkieController.get(app) }
        val status by provider.status.collectAsState()
        val voice by walkie.state.collectAsState()
        val gatewayInfo by provider.gatewayInfo.collectAsState()
        val snapshot by provider.snapshot.collectAsState()
        val lastInvalidFrame by provider.lastInvalidFrame.collectAsState()
        val candidates = remember { mutableStateListOf<EspMeshGatewayCandidate>() }
        var scanning by rememberSaveable { mutableStateOf(false) }
        var networkId by rememberSaveable { mutableStateOf("") }
        // Secrets are deliberately memory-only UI state: never SavedState/Bundle persisted.
        var networkKey by remember { mutableStateOf("") }
        var e2eGroupKey by remember { mutableStateOf("") }
        var provisioningToken by remember { mutableStateOf("") }
        var configStatus by rememberSaveable { mutableStateOf("") }
        var showGatewayDiagnostics by rememberSaveable { mutableStateOf(false) }
        var voiceChannel by rememberSaveable { mutableStateOf(voice.channel) }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }

        val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (it.values.all { granted -> granted }) {
                candidates.clear(); scanning = true
                provider.scan({ c -> if (candidates.none { it.address == c.address }) candidates += c }, { scanning = false })
            }
        }
        fun requiredPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= 31) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        fun scan() {
            val missing = requiredPermissions().filter { ContextCompat.checkSelfPermission(app, it) != android.content.pm.PackageManager.PERMISSION_GRANTED }
            if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
            else {
                candidates.clear(); scanning = true
                provider.scan({ c -> if (candidates.none { it.address == c.address }) candidates += c }, { scanning = false })
            }
        }
        fun selectGateway(candidate: EspMeshGatewayCandidate) {
            provider.provision(candidate)
            val request = As100EspMeshGatewayMethod.request(capabilityId, emptyMap(), emptyList(), emptyList())
            result = As100EspMeshGatewayMethod.execute(request, null, "espmesh")
            if (context.submitsImmediately) result?.let(onConfirmed)
        }
        fun copyKey() {
            runCatching { provider.exportE2eGroupKey() }.onSuccess { key ->
                (uiContext.getSystemService(ClipboardManager::class.java))?.setPrimaryClip(ClipData.newPlainText("MethodMesh mesh E2E group key", key))
                configStatus = "E2E group key copied. Share it only with phones joining this field mesh."
            }.onFailure { configStatus = it.message.orEmpty() }
        }

        CapabilityScreenScaffold(
            title = title, capabilityId = capabilityId, context = context, canGoBack = context.stepNumber > 1,
            capturedResult = result, resultPreview = result?.let { OutputFormatter.fields(it, false) }.orEmpty(),
            onBack = onBack, onRetry = { scan() }, onConfirm = { result?.let(onConfirmed) }, onCancel = onCancel
        ) {
            Text("Mesh transport runs independently of this screen. A configured battery-powered ESP reconnects over BLE whenever it comes back into range; USB is not required.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Background transport", style = MaterialTheme.typography.titleMedium)
                    Text(if (snapshot.enabled) "Running" else "Paused")
                    Text("${status.detail} · ${if (status.connected) "connected" else "not connected"}", style = MaterialTheme.typography.bodySmall)
                    if (snapshot.gatewayName.isNotBlank()) Text("Gateway: ${snapshot.gatewayName}", style = MaterialTheme.typography.bodySmall)
                    if (gatewayInfo.nodeId.isNotBlank()) Text("Node ${gatewayInfo.nodeId} · ${gatewayInfo.networkId.ifBlank { "not network-provisioned" }}", style = MaterialTheme.typography.bodySmall)
                    Text("Phone encrypted queues: ${snapshot.outboxPending} outbound · ${snapshot.inboxTotal} inbound · ${snapshot.delivered} end-to-end delivered", style = MaterialTheme.typography.bodySmall)
                    if (snapshot.connected) Text("BLE MTU: ${snapshot.bleMtu} · live voice: ${if (snapshot.liveVoiceReady) "ready" else "MTU too small"}", style = MaterialTheme.typography.bodySmall)
                    if (gatewayInfo.nodeId.isNotBlank()) Text("ESP persistent spool: ${gatewayInfo.pendingForRadio} to radio · ${gatewayInfo.pendingForPhone} waiting for phone", style = MaterialTheme.typography.bodySmall)
                    if (gatewayInfo.spoolError.isNotBlank()) Text("ESP spool error: ${gatewayInfo.spoolError}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Text("Last BLE: ${ageLabel(snapshot.lastBleAtMs)} · last radio: ${ageLabel(snapshot.lastRadioAtMs)}", style = MaterialTheme.typography.bodySmall)
                    Text("E2E key ID: ${snapshot.e2eKeyId.ifBlank { "not configured" }}", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (snapshot.enabled) OutlinedButton(onClick = { provider.setPersistentEnabled(false) }) { Text("Pause") }
                        else Button(onClick = { provider.setPersistentEnabled(true) }) { Text("Resume") }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Walkie-talkie", style = MaterialTheme.typography.titleMedium)
                    Text("Live voice is ephemeral priority traffic: it is E2E encrypted but never enters the durable phone or ESP spools. Muting stops this phone receiving voice; the ESP continues relaying radio traffic for the mesh.", style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text("Listen to live broadcasts")
                            Text(if (voice.listening) "On · ${voice.channel}" else "Off · broadcasts are intentionally missed", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = voice.listening, onCheckedChange = walkie::setListening)
                    }
                    OutlinedTextField(
                        value = voiceChannel,
                        onValueChange = { voiceChannel = it },
                        label = { Text("Voice channel") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(onClick = { walkie.setChannel(voiceChannel); voiceChannel = walkie.state.value.channel }) { Text("Use channel") }
                    Text(
                        "Voice: ${if (voice.transmitting) "transmitting" else if (voice.receiving) "receiving ${voice.activeSpeaker}" else "idle"} · sent ${voice.packetsSent} · received ${voice.packetsReceived} · lost/rejected ${voice.packetsDropped}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (voice.error.isNotBlank()) Text(voice.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }

            lastInvalidFrame?.let { diagnostic ->
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = { showGatewayDiagnostics = !showGatewayDiagnostics }) { Text(if (showGatewayDiagnostics) "Hide gateway diagnostics" else "Gateway diagnostics") }
                if (showGatewayDiagnostics) Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Rejected BLE notification", style = MaterialTheme.typography.titleSmall)
                        Text("Characteristic: ${diagnostic.characteristicUuid}", style = MaterialTheme.typography.bodySmall)
                        Text("Length: ${diagnostic.byteLength} bytes", style = MaterialTheme.typography.bodySmall)
                        Text("Parser: ${diagnostic.parserError}", style = MaterialTheme.typography.bodySmall)
                        Text("UTF-8", style = MaterialTheme.typography.labelMedium)
                        Text(diagnostic.utf8.ifBlank { "<empty>" }, style = MaterialTheme.typography.bodySmall)
                        Text("Hex", style = MaterialTheme.typography.labelMedium)
                        Text(diagnostic.hex.ifBlank { "<empty>" }, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(onClick = { scan() }, enabled = !scanning, modifier = Modifier.fillMaxWidth()) { Text(if (scanning) "Scanning…" else "Scan for gateways") }

            Spacer(Modifier.height(10.dp))
            Text("Network + end-to-end encryption", style = MaterialTheme.typography.titleMedium)
            Text("The network key is sent to the ESP for radio admission. The E2E group key stays on phones and encrypts payloads before BLE/ESP-NOW. Both phones must use the same E2E group key.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(networkId, { networkId = it }, label = { Text("Network ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(networkKey, { networkKey = it }, label = { Text("ESP transport/network key") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
            OutlinedTextField(e2eGroupKey, { e2eGroupKey = it }, label = { Text(if (snapshot.e2eKeyId.isNotBlank()) "E2E group key (leave blank to keep current)" else "E2E group key") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { provider.generateE2eGroupKey(); e2eGroupKey = ""; configStatus = "Generated and Keystore-wrapped a new E2E group key on this phone." }) { Text("Generate E2E key") }
                if (snapshot.e2eKeyId.isNotBlank()) OutlinedButton(onClick = { copyKey() }) { Text("Copy current key") }
            }
            OutlinedTextField(provisioningToken, { provisioningToken = it }, label = { Text("Node provisioning token (optional for a new node)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Button(
                onClick = {
                    val sent = provider.configureNetwork(networkId, networkKey, e2eGroupKey, provisioningToken)
                    configStatus = sent.detail
                    if (sent.state != com.example.methodmesh.core.transport.TransportOutboxState.FAILED_PERMANENT && sent.state != com.example.methodmesh.core.transport.TransportOutboxState.FAILED_RETRYABLE) e2eGroupKey = ""
                },
                enabled = status.connected && networkId.isNotBlank() && networkKey.isNotBlank() && (e2eGroupKey.isNotBlank() || snapshot.e2eKeyId.isNotBlank()),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Provision network") }
            if (configStatus.isNotBlank()) Text(configStatus, style = MaterialTheme.typography.bodySmall)

            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                candidates.forEach { candidate ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(candidate.name, style = MaterialTheme.typography.titleMedium)
                                Text("${candidate.address} · RSSI ${candidate.rssi}", style = MaterialTheme.typography.bodySmall)
                            }
                            OutlinedButton(onClick = { selectGateway(candidate) }) { Text("Use") }
                        }
                    }
                }
            }
        }
    }
}

private fun ageLabel(timestampMs: Long): String {
    if (timestampMs <= 0L) return "never"
    val age = (System.currentTimeMillis() - timestampMs).coerceAtLeast(0L)
    return when {
        age < 2_000L -> "now"
        age < 60_000L -> "${age / 1_000L}s ago"
        age < 3_600_000L -> "${age / 60_000L}m ago"
        else -> "${age / 3_600_000L}h ago"
    }
}
