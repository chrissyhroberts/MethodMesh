package com.example.methodmesh.modules.espmesh

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.transport.MethodMeshTransportEnvelope
import com.example.methodmesh.core.transport.MethodMeshTransportRuntime
import com.example.methodmesh.core.transport.TransportEndpoint
import com.example.methodmesh.core.transport.TransportOutboxState
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Guided Workbench control plane for the persistent mesh transport. */
object EspMeshGatewayCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100EspMeshGatewayMethod.ID
    override val title = "Set up ESP mesh"
    override val description = "Connect a node, create or join a mesh, share its QR, and test delivery."

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val uiContext = LocalContext.current
        val app = uiContext.applicationContext
        val provider = remember { EspMeshTransportProvider.get(app) }
        val status by provider.status.collectAsState()
        val provisioning by provider.provisioning.collectAsState()
        val gateway by provider.gatewayInfo.collectAsState()
        val setupNetworkKey by provider.setupNetworkKey.collectAsState()
        val snapshot by provider.snapshot.collectAsState()
        val lastInvalidFrame by provider.lastInvalidFrame.collectAsState()
        val candidates = remember { mutableStateListOf<EspMeshGatewayCandidate>() }
        var scanning by rememberSaveable { mutableStateOf(false) }
        var autoSelecting by rememberSaveable { mutableStateOf(false) }
        var setupMode by rememberSaveable { mutableStateOf("") }
        var networkName by rememberSaveable { mutableStateOf("") }
        var networkKey by remember { mutableStateOf("") }
        var e2eGroupKey by remember { mutableStateOf("") }
        var provisioningToken by remember { mutableStateOf("") }
        var setupStatus by rememberSaveable { mutableStateOf("") }
        var joinQrBitmap by remember { mutableStateOf<Bitmap?>(null) }
        var showAdvanced by rememberSaveable { mutableStateOf(false) }
        var showInvalidFrame by rememberSaveable { mutableStateOf(false) }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }

        val effectiveNetworkName = networkName.ifBlank { gateway.networkId }
        val effectiveNetworkKey = networkKey.ifBlank { setupNetworkKey }
        val confirmedReady = provisioning.acknowledgement != null && gateway.provisioned && gateway.networkId == provisioning.networkId
        val liveConfigurationMatches = status.connected && gateway.provisioned && gateway.networkId.isNotBlank() &&
            gateway.networkKeyId.isNotBlank() && gateway.networkKeyId == provider.setupNetworkKeyId() && snapshot.e2eKeyId.isNotBlank()
        val meshOperational = confirmedReady || liveConfigurationMatches

        val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants.values.all { it }) {
                candidates.clear(); scanning = true; autoSelecting = false
                provider.scan({ candidate -> if (candidates.none { it.address == candidate.address }) candidates += candidate }, { scanning = false })
            }
        }
        fun requiredPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= 31) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        fun scan() {
            val missing = requiredPermissions().filter { ContextCompat.checkSelfPermission(app, it) != android.content.pm.PackageManager.PERMISSION_GRANTED }
            if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
            else {
                candidates.clear(); scanning = true; autoSelecting = false
                provider.scan({ candidate -> if (candidates.none { it.address == candidate.address }) candidates += candidate }, { scanning = false })
            }
        }
        fun selectGateway(candidate: EspMeshGatewayCandidate) {
            autoSelecting = true
            provider.provision(candidate)
            result = null
            setupStatus = "Connecting to the node…"
        }
        fun configureMesh() {
            val name = networkName.trim()
            val key = effectiveNetworkKey.ifBlank { provider.generateSetupNetworkKey() }
            if (setupMode == "create" && snapshot.e2eKeyId.isBlank()) provider.generateE2eGroupKey()
            result = null
            val sent = provider.configureNetwork(name, key, e2eGroupKey, provisioningToken)
            setupStatus = sent.detail
            if (sent.state !in setOf(TransportOutboxState.FAILED_PERMANENT, TransportOutboxState.FAILED_RETRYABLE)) e2eGroupKey = ""
        }
        fun createJoinQr() {
            joinQrBitmap = runCatching {
                val payload = EspMeshJoinBundle(effectiveNetworkName, effectiveNetworkKey, provider.exportE2eGroupKey()).encode()
                val size = 768
                val matrix = MultiFormatWriter().encode(payload, BarcodeFormat.QR_CODE, size, size)
                Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bitmap ->
                    for (x in 0 until size) for (y in 0 until size) bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }.onFailure { setupStatus = it.message ?: "Could not create the join QR." }.getOrNull()
        }

        val joinQrScanner = rememberLauncherForActivityResult(ScanContract()) { scanResult ->
            val payload = scanResult.contents
            if (payload.isNullOrBlank()) return@rememberLauncherForActivityResult
            runCatching { EspMeshJoinBundle.decode(payload) }
                .onSuccess { bundle ->
                    setupMode = "join"
                    networkName = bundle.networkId
                    networkKey = ""
                    provider.setSetupNetworkKey(bundle.networkKey)
                    e2eGroupKey = bundle.e2eGroupKey
                    joinQrBitmap = null
                    setupStatus = "Join QR accepted. Connect a fresh node, then tap Join mesh."
                    if (!status.connected) scan()
                }
                .onFailure { setupStatus = it.message ?: "Could not read the mesh join QR." }
        }

        LaunchedEffect(Unit) {
            if (!status.connected && snapshot.gatewayAddress.isBlank()) scan()
        }
        LaunchedEffect(candidates.size, status.connected) {
            if (candidates.size == 1 && !status.connected && !autoSelecting) {
                delay(750)
                if (candidates.size == 1 && !status.connected && !autoSelecting) selectGateway(candidates.first())
            }
        }
        LaunchedEffect(provisioning) {
            result = provisioning.acknowledgement?.takeUnless { provisioning.pending }?.let { acknowledgement ->
                val request = As100EspMeshGatewayMethod.request(capabilityId, emptyMap(), emptyList(), emptyList())
                As100EspMeshGatewayMethod.confirmed(request, acknowledgement)
            }
            if (context.submitsImmediately) result?.let(onConfirmed)
        }

        CapabilityScreenScaffold(
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = result,
            resultPreview = result?.let { OutputFormatter.fields(it, false) }.orEmpty(),
            onBack = onBack,
            onRetry = ::scan,
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Text("Flash an ESP as an ESP-NOW mesh node first. Then complete these steps in order; MethodMesh creates and protects the keys automatically.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))

            SetupCard("1", "Connect this phone to a node") {
                if (status.connected) {
                    Text("Connected", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                    Text(gateway.nodeId.ifBlank { snapshot.gatewayName.ifBlank { "MethodMesh mesh node" } })
                    Text("Firmware ${gateway.firmware.ifBlank { "checking…" }}", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text(if (scanning) "Looking for a nearby MethodMesh node…" else "Power the freshly flashed node and keep it near this phone.")
                    Button(onClick = ::scan, enabled = !scanning, modifier = Modifier.fillMaxWidth()) {
                        Text(if (scanning) "Looking for node…" else "Find node")
                    }
                }
                if (!status.connected && candidates.isNotEmpty()) {
                    candidates.forEach { candidate ->
                        OutlinedButton(onClick = { selectGateway(candidate) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Use ${candidate.name} · ${candidate.address}")
                        }
                    }
                }
            }

            SetupCard("2", "Create or join a mesh") {
                if (setupMode.isBlank() && !meshOperational) {
                    Button(onClick = {
                        setupMode = "create"
                        networkName = ""
                        networkKey = ""
                        e2eGroupKey = ""
                        provider.generateSetupNetworkKey()
                        if (snapshot.e2eKeyId.isBlank()) provider.generateE2eGroupKey()
                        setupStatus = "Keys created securely. Give the mesh a name."
                    }, modifier = Modifier.fillMaxWidth()) { Text("Create a new mesh") }
                    OutlinedButton(onClick = {
                        joinQrScanner.launch(ScanOptions().apply {
                            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            setPrompt("Scan the join QR shown on the other phone")
                            setBeepEnabled(false); setOrientationLocked(false); setBarcodeImageEnabled(false)
                        })
                    }, modifier = Modifier.fillMaxWidth()) { Text("Join with QR") }
                } else if (meshOperational) {
                    Text(if (confirmedReady) "Mesh ready" else "Connected to configured mesh", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                    Text(gateway.networkId)
                    Text("Node ${gateway.nodeId}", style = MaterialTheme.typography.bodySmall)
                } else {
                    OutlinedTextField(
                        value = networkName,
                        onValueChange = { if (setupMode == "create") networkName = it },
                        label = { Text("Mesh name") },
                        readOnly = setupMode == "join",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(if (setupMode == "join") "The QR supplied the mesh name and keys." else "Keys are generated and stored automatically.", style = MaterialTheme.typography.bodySmall)
                    Button(
                        onClick = ::configureMesh,
                        enabled = status.connected && networkName.isNotBlank() && !provisioning.pending && effectiveNetworkKey.isNotBlank() && (setupMode == "create" || e2eGroupKey.isNotBlank()),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (provisioning.pending) "Waiting for node…" else if (setupMode == "join") "Join mesh" else "Create mesh") }
                    OutlinedButton(onClick = { setupMode = ""; networkName = ""; e2eGroupKey = ""; setupStatus = "" }, modifier = Modifier.fillMaxWidth()) { Text("Start over") }
                }
                if (setupStatus.isNotBlank()) Text(setupStatus, style = MaterialTheme.typography.bodySmall, color = if (setupStatus.contains("failed", true) || setupStatus.contains("error", true)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            }

            SetupCard("3", "Add the other phone") {
                if (!meshOperational) {
                    Text("Available after this node confirms the mesh settings.", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("If the other phone already shows this mesh, continue to step 4. Otherwise show it this QR; no keys need to be typed or copied.")
                    Button(onClick = ::createJoinQr, enabled = effectiveNetworkName.isNotBlank() && effectiveNetworkKey.isNotBlank() && snapshot.e2eKeyId.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Show join QR") }
                    joinQrBitmap?.let { bitmap ->
                        Text("Secret enrollment code — hide it when the other phone has joined.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        Image(bitmap.asImageBitmap(), "Secret MethodMesh mesh join QR", modifier = Modifier.fillMaxWidth())
                        OutlinedButton(onClick = { joinQrBitmap = null }, modifier = Modifier.fillMaxWidth()) { Text("Hide QR") }
                    }
                }
            }

            SetupCard("4", "Test the mesh") {
                if (!meshOperational) Text("Available when this node is ready.", style = MaterialTheme.typography.bodySmall)
                else GuidedMeshTest(provider, gateway)
            }

            OutlinedButton(onClick = { showAdvanced = !showAdvanced }, modifier = Modifier.fillMaxWidth()) { Text(if (showAdvanced) "Hide advanced recovery" else "Advanced recovery and diagnostics") }
            if (showAdvanced) {
                AdvancedMeshControls(
                    provider = provider,
                    status = status,
                    snapshot = snapshot,
                    gateway = gateway,
                    networkName = networkName,
                    onNetworkName = { networkName = it },
                    networkKey = networkKey,
                    onNetworkKey = { networkKey = it; provider.setSetupNetworkKey(it) },
                    e2eGroupKey = e2eGroupKey,
                    onE2eGroupKey = { e2eGroupKey = it },
                    provisioningToken = provisioningToken,
                    onProvisioningToken = { provisioningToken = it },
                    pending = provisioning.pending,
                    onApply = ::configureMesh,
                    onScan = ::scan,
                    lastInvalidFrame = lastInvalidFrame,
                    showInvalidFrame = showInvalidFrame,
                    onToggleInvalidFrame = { showInvalidFrame = !showInvalidFrame }
                )
            }
        }
    }
}

@Composable
private fun SetupCard(number: String, title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("$number. $title", style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun GuidedMeshTest(provider: EspMeshTransportProvider, gateway: EspMeshGatewayInfo) {
    val app = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val snapshot by provider.snapshot.collectAsState()
    val inbound by provider.recentInbound.collectAsState()
    var message by rememberSaveable { mutableStateOf("") }
    var sendStatus by rememberSaveable { mutableStateOf("") }
    val active = snapshot.lastRadioAtMs > 0L || snapshot.delivered > 0 || inbound.isNotEmpty()
    val radioReady = gateway.radioChannel > 0 && gateway.radioStartError.isBlank()

    Text(
        when {
            !radioReady -> "Radio is not running on this node. Reflash it with the current mesh firmware before testing."
            active -> "Mesh active · radio traffic confirmed"
            else -> "Send from either phone. The other phone will display the received text here."
        },
        color = when { !radioReady -> MaterialTheme.colorScheme.error; active -> MaterialTheme.colorScheme.primary; else -> MaterialTheme.colorScheme.onSurface }
    )
    OutlinedTextField(message, { message = it }, label = { Text("Test message") }, modifier = Modifier.fillMaxWidth())
    Button(onClick = {
        val envelope = MethodMeshTransportEnvelope(
            source = TransportEndpoint("mesh-phone", provider.phoneId()),
            destination = TransportEndpoint("logical", "field-group"),
            messageType = "TEXT",
            moduleId = "espmesh",
            capabilityId = As100EspMeshMessageMethod.ID,
            payloadType = "text/plain",
            payload = message
        )
        scope.launch {
            sendStatus = "Sending securely…"
            val sent = withContext(Dispatchers.IO) { MethodMeshTransportRuntime.get(app).send(envelope, EspMeshTransportProvider.TRANSPORT_ID) }
            sendStatus = sent.detail.ifBlank { sent.state.name }
        }
    }, enabled = message.isNotBlank() && snapshot.connected && radioReady, modifier = Modifier.fillMaxWidth()) { Text("Send test packet") }
    if (sendStatus.isNotBlank()) Text(sendStatus, style = MaterialTheme.typography.bodySmall)
    Text("Pending on phone: ${snapshot.outboxPending} · ESP waiting for radio: ${gateway.pendingForRadio}", style = MaterialTheme.typography.bodySmall)
    inbound.take(5).forEach { received -> Text("Received: ${received.payload}", color = MaterialTheme.colorScheme.primary) }
}

@Composable
private fun AdvancedMeshControls(
    provider: EspMeshTransportProvider,
    status: com.example.methodmesh.core.transport.TransportStatus,
    snapshot: EspMeshTransportSnapshot,
    gateway: EspMeshGatewayInfo,
    networkName: String,
    onNetworkName: (String) -> Unit,
    networkKey: String,
    onNetworkKey: (String) -> Unit,
    e2eGroupKey: String,
    onE2eGroupKey: (String) -> Unit,
    provisioningToken: String,
    onProvisioningToken: (String) -> Unit,
    pending: Boolean,
    onApply: () -> Unit,
    onScan: () -> Unit,
    lastInvalidFrame: EspMeshGatewayFrameDiagnostic?,
    showInvalidFrame: Boolean,
    onToggleInvalidFrame: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Transport status", style = MaterialTheme.typography.titleMedium)
            Text("${if (snapshot.serviceRunning) "Running" else "Stopped"} · ${status.detail}")
            Text("Node ${gateway.nodeId.ifBlank { "unknown" }} · network ${gateway.networkId.ifBlank { "not confirmed" }}")
            Text("Firmware ${gateway.firmware.ifBlank { "unknown" }} · radio channel ${gateway.radioChannel.takeIf { it > 0 } ?: "not running"}")
            Text("ESP key ID ${gateway.networkKeyId.ifBlank { "unavailable" }} · E2E key ID ${snapshot.e2eKeyId.ifBlank { "unavailable" }}", style = MaterialTheme.typography.bodySmall)
            Text("Radio buffer ${gateway.radioRxBuffer.takeIf { it > 0 } ?: "unavailable"} bytes · packets received ${gateway.radioRxPackets}", style = MaterialTheme.typography.bodySmall)
            Text("Radio startup ${gateway.radioStartError.ifBlank { "OK" }} · send error ${gateway.radioSendError.ifBlank { "none" }}", style = MaterialTheme.typography.bodySmall, color = if (gateway.radioStartError.isBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
            Text("Phone queue ${snapshot.outboxPending} · ESP to radio ${gateway.pendingForRadio} · ESP to phone ${gateway.pendingForPhone}", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onScan) { Text("Scan again") }
                if (snapshot.enabled) OutlinedButton(onClick = { provider.setPersistentEnabled(false) }) { Text("Pause") }
                else Button(onClick = { provider.setPersistentEnabled(true) }) { Text("Resume") }
            }
            Text("Manual recovery", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(networkName, onNetworkName, label = { Text("Mesh name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(networkKey, onNetworkKey, label = { Text("ESP network key") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
            OutlinedTextField(e2eGroupKey, onE2eGroupKey, label = { Text("E2E group key") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
            OutlinedTextField(provisioningToken, onProvisioningToken, label = { Text("Provisioning token for a configured node") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Button(onClick = onApply, enabled = status.connected && networkName.isNotBlank() && !pending, modifier = Modifier.fillMaxWidth()) { Text(if (pending) "Waiting for confirmation…" else "Apply advanced settings") }
            Text("A freshly flashed node does not need a token. Reconfiguring a node that already has settings requires the token printed over USB serial.", style = MaterialTheme.typography.bodySmall)
            lastInvalidFrame?.let { diagnostic ->
                OutlinedButton(onClick = onToggleInvalidFrame) { Text(if (showInvalidFrame) "Hide rejected BLE frame" else "Show rejected BLE frame") }
                if (showInvalidFrame) {
                    Text("${diagnostic.parserError} · ${diagnostic.byteLength} bytes", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Text(diagnostic.utf8.ifBlank { diagnostic.hex }, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
