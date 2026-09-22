package com.example.methodmesh.modules.networktools

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun NetworkToolsDashboard(
    context: CapabilityScreenContext,
    onBack: () -> Unit,
    onConfirmed: (ExecutionResult) -> Unit,
    onCancel: () -> Unit
) {
    val androidContext = LocalContext.current
    val scope = rememberCoroutineScope()

    var snapshot by remember {
        mutableStateOf(
            NetworkSnapshot(
                stateLabel = "Checking network…",
                transportLabel = "Unknown",
                internetCapable = false,
                validated = false,
                captivePortal = false,
                metered = false,
                interfaceName = "",
                localAddresses = emptyList(),
                gateway = "",
                dnsServers = emptyList(),
                ssid = "",
                bssid = "",
                rssi = null,
                frequencyMhz = null,
                linkSpeedMbps = null,
                wifiStandard = "",
                wifiPermissionNote = null
            )
        )
    }
    var wifiEnvironment by remember {
        mutableStateOf(
            WifiEnvironmentSnapshot(
                networks = emptyList(),
                capturedAtMs = 0L,
                scanStarted = false,
                message = "Checking nearby Wi-Fi availability…"
            )
        )
    }
    var wifiRefreshing by remember { mutableStateOf(false) }
    var host by rememberSaveable { mutableStateOf(context.action.setting("host").orEmpty()) }
    var port by rememberSaveable { mutableStateOf(context.action.setting("port") ?: "443") }
    var cidr by rememberSaveable { mutableStateOf(context.action.setting("cidr") ?: "192.168.1.0/24") }
    var runningTool by rememberSaveable { mutableStateOf("") }
    var resultJson by rememberSaveable { mutableStateOf("") }
    var resultOperation by rememberSaveable { mutableStateOf("") }
    var resultSettingsJson by rememberSaveable { mutableStateOf("") }
    var committedResultJson by rememberSaveable { mutableStateOf("") }
    var committedSettingsJson by rememberSaveable { mutableStateOf("") }
    var showOdkCard by rememberSaveable { mutableStateOf(false) }

    val result = remember(resultJson) { resultJson.toStringMap() }
    val committedValues = remember(committedResultJson) { committedResultJson.toStringMap() }
    val committedSettings = remember(committedSettingsJson) { committedSettingsJson.toStringMap() }
    val committedExecution = remember(committedResultJson, committedSettingsJson) {
        committedValues.takeIf { it.isNotEmpty() }?.let { values ->
            buildNetworkExecution(context, values, committedSettings)
        }
    }

    fun refreshConnection() {
        scope.launch {
            snapshot = withContext(Dispatchers.IO) { NetworkSnapshotRepository.current(androidContext) }
            wifiEnvironment = withContext(Dispatchers.IO) {
                NetworkSnapshotRepository.wifiEnvironment(androidContext, false)
            }
        }
    }

    fun refreshWifi() {
        if (wifiRefreshing) return
        wifiRefreshing = true
        scope.launch {
            try {
                val first = withContext(Dispatchers.IO) {
                    NetworkSnapshotRepository.wifiEnvironment(androidContext, true)
                }
                wifiEnvironment = first
                if (first.scanStarted) {
                    delay(1800)
                    wifiEnvironment = withContext(Dispatchers.IO) {
                        NetworkSnapshotRepository.wifiEnvironment(androidContext, false)
                    }
                }
            } finally {
                wifiRefreshing = false
            }
        }
    }

    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        scope.launch {
            wifiEnvironment = withContext(Dispatchers.IO) {
                NetworkSnapshotRepository.wifiEnvironment(androidContext, false)
            }
            if (granted) refreshWifi()
        }
    }

    fun settingsFor(operation: NetworkOperation): Map<String, String> = linkedMapOf(
        "operation" to operation.id,
        "host" to host.trim(),
        "port" to port,
        "timeout_ms" to "3000",
        "cidr" to cidr.trim(),
        "traceroute_max_hops" to "12"
    )

    fun runTool(operation: NetworkOperation) {
        if (runningTool.isNotBlank()) return
        runningTool = operation.id
        val settings = settingsFor(operation)
        scope.launch {
            try {
                val values = withContext(Dispatchers.IO) {
                    NetworkToolsRunner.run(settings, androidContext.applicationContext)
                }
                resultOperation = operation.id
                resultSettingsJson = settings.toJsonString()
                resultJson = values.toJsonString()
            } finally {
                runningTool = ""
            }
        }
    }

    LaunchedEffect(Unit) {
        snapshot = withContext(Dispatchers.IO) { NetworkSnapshotRepository.current(androidContext) }
        wifiEnvironment = withContext(Dispatchers.IO) {
            NetworkSnapshotRepository.wifiEnvironment(androidContext, false)
        }
        while (isActive) {
            delay(5000)
            snapshot = withContext(Dispatchers.IO) { NetworkSnapshotRepository.current(androidContext) }
        }
    }

    val currentWorkingSettingsJson = NetworkOperation.from(resultOperation)
        ?.let { settingsFor(it).toJsonString() }
        .orEmpty()

    // The Standard MethodMesh dashboard host already owns vertical scrolling. Adding another
    // root verticalScroll here causes an infinite-height Compose measurement crash.
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("Network tools", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Live connection dashboard · Development · Online/Offline",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(onClick = ::refreshConnection) { Text("Refresh") }
        }

        ConnectionCard(
            snapshot = snapshot,
            capturing = runningTool == NetworkOperation.CONNECTION_STATUS.id,
            onCapture = { runTool(NetworkOperation.CONNECTION_STATUS) }
        )

        WifiEnvironmentCard(
            environment = wifiEnvironment,
            refreshing = wifiRefreshing,
            capturing = runningTool == NetworkOperation.WIFI_SCAN.id,
            onRefresh = ::refreshWifi,
            onCapture = { runTool(NetworkOperation.WIFI_SCAN) },
            onRequestLocation = {
                if (androidContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            }
        )

        DiagnosticToolsCard(
            host = host,
            onHostChange = { host = it.take(253) },
            port = port,
            onPortChange = { port = it.filter(Char::isDigit).take(5) },
            runningTool = runningTool,
            onRun = ::runTool
        )

        CidrCard(
            cidr = cidr,
            onCidrChange = { cidr = it.take(32) },
            running = runningTool == NetworkOperation.CIDR.id,
            onCalculate = { runTool(NetworkOperation.CIDR) }
        )

        if (result.isNotEmpty()) {
            NetworkWorkingResultCard(
                values = result,
                title = "Working result",
                onCommit = {
                    committedResultJson = resultJson
                    committedSettingsJson = resultSettingsJson
                },
                committedChanged = committedResultJson.isNotBlank() &&
                    (committedResultJson != resultJson || committedSettingsJson != currentWorkingSettingsJson)
            )
        }

        committedExecution?.let { execution ->
            NetworkCommittedActions(
                context = context,
                label = "Network tools",
                result = execution,
                workingChanged = committedResultJson != resultJson || committedSettingsJson != currentWorkingSettingsJson,
                onDone = { onConfirmed(execution) }
            )
        }

        OutlinedButton(
            onClick = { showOdkCard = !showOdkCard },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (showOdkCard) "Hide ODK integration" else "ODK integration") }
        if (showOdkCard) NetworkToolsOdkIntegrationCard()

        Text(
            "Nearby Wi-Fi is local environmental context. Capture status/list turns the current view into the same canonical network.tools result that presets, protocols and ODK can invoke. Host diagnostics remain bounded to one host; this module does not perform LAN sweeps or port-range scans.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (context.stepNumber > 1) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Back") }
            }
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Close") }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ConnectionCard(
    snapshot: NetworkSnapshot,
    capturing: Boolean,
    onCapture: () -> Unit
) {
    DashboardCard(title = "Current connection") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                NetworkCopyValue("connection state", snapshot.stateLabel, Modifier.fillMaxWidth(), prominent = true)
                val transport = buildString {
                    append(snapshot.transportLabel)
                    if (snapshot.ssid.isNotBlank()) append(" · ${snapshot.ssid}")
                }
                NetworkCopyValue("network transport", transport)
            }
            StatusPill(
                text = when {
                    snapshot.captivePortal -> "Sign-in"
                    snapshot.validated -> "Healthy"
                    snapshot.internetCapable -> "Unverified"
                    else -> "Offline"
                }
            )
        }
        Spacer(Modifier.height(10.dp))
        MetricGrid(
            listOf(
                "Local IP" to snapshot.localAddresses.joinToString("\n").ifBlank { "—" },
                "Gateway" to snapshot.gateway.ifBlank { "—" },
                "DNS" to snapshot.dnsServers.joinToString("\n").ifBlank { "—" },
                "Interface" to snapshot.interfaceName.ifBlank { "—" },
                "Metered" to if (snapshot.metered) "Yes" else "No",
                "Signal" to snapshot.rssi?.let { "$it dBm · ${signalLabel(it)}" }.orEmpty().ifBlank { "—" }
            )
        )
        if (snapshot.transportLabel == "Wi-Fi" || snapshot.ssid.isNotBlank()) {
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            val wifiDetail = listOfNotNull(
                snapshot.frequencyMhz?.let { "${NetworkSnapshotRepository.wifiBand(it)} · ${it} MHz${NetworkSnapshotRepository.wifiChannel(it)?.let { ch -> " · ch $ch" }.orEmpty()}" },
                snapshot.linkSpeedMbps?.let { "$it Mbps link" },
                snapshot.wifiStandard.takeIf { it.isNotBlank() }
            ).joinToString(" · ")
            if (wifiDetail.isNotBlank()) NetworkCopyValue("Wi-Fi details", wifiDetail)
            if (snapshot.bssid.isNotBlank()) NetworkCopyValue("BSSID", snapshot.bssid)
            snapshot.wifiPermissionNote?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onCapture, enabled = !capturing, modifier = Modifier.fillMaxWidth()) {
            Text(if (capturing) "Capturing…" else "Capture connection status")
        }
    }
}

@Composable
private fun WifiEnvironmentCard(
    environment: WifiEnvironmentSnapshot,
    refreshing: Boolean,
    capturing: Boolean,
    onRefresh: () -> Unit,
    onCapture: () -> Unit,
    onRequestLocation: () -> Unit
) {
    DashboardCard(title = "Nearby Wi-Fi") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                if (environment.networks.isEmpty()) "No networks visible" else "${environment.networks.size} network${if (environment.networks.size == 1) "" else "s"}",
                style = MaterialTheme.typography.titleMedium
            )
            OutlinedButton(onClick = onRefresh, enabled = !refreshing) {
                Text(if (refreshing) "Scanning…" else "Refresh")
            }
        }
        environment.message?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (environment.needsLocationPermission && environment.missingManifestPermissions.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Button(onClick = onRequestLocation, modifier = Modifier.fillMaxWidth()) { Text("Allow Wi-Fi scan access") }
        }
        if (environment.missingManifestPermissions.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "App integration required: ${environment.missingManifestPermissions.joinToString()} must be declared before nearby Wi-Fi scanning can work.",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        environment.networks.take(12).forEachIndexed { index, network ->
            if (index == 0) Spacer(Modifier.height(8.dp)) else HorizontalDivider()
            WifiNetworkRowView(network)
        }
        if (environment.networks.size > 12) {
            Text(
                "+ ${environment.networks.size - 12} more networks",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onCapture,
            enabled = !capturing && environment.missingManifestPermissions.isEmpty() && !environment.needsLocationPermission,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (capturing) "Capturing…" else "Capture nearby Wi-Fi list") }
    }
}

@Composable
private fun WifiNetworkRowView(network: WifiNetworkRow) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NetworkCopyValue("SSID", network.ssid, Modifier.weight(1f))
                if (network.connected) StatusPill("Connected")
            }
            val detail = buildString {
                append(network.security)
                append(" · ${network.band}")
                network.channel?.let { append(" · ch $it") }
                if (network.accessPointCount > 1) append(" · ${network.accessPointCount} APs")
            }
            NetworkCopyValue("Wi-Fi network details", detail)
            if (network.bssid.isNotBlank()) NetworkCopyValue("BSSID", network.bssid)
        }
        Column(Modifier.padding(start = 8.dp)) {
            Text(signalBars(network.rssi), style = MaterialTheme.typography.bodyLarge)
            NetworkCopyValue("Wi-Fi signal", "${network.rssi} dBm")
        }
    }
}

@Composable
private fun DiagnosticToolsCard(
    host: String,
    onHostChange: (String) -> Unit,
    port: String,
    onPortChange: (String) -> Unit,
    runningTool: String,
    onRun: (NetworkOperation) -> Unit
) {
    DashboardCard(title = "Host diagnostics") {
        Text(
            "Run one bounded check against a specific host.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = host,
            onValueChange = onHostChange,
            label = { Text("Host name or IP") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToolButton("DNS", NetworkOperation.DNS_LOOKUP, runningTool, host.isNotBlank(), Modifier.weight(1f), onRun)
            ToolButton("Reachability", NetworkOperation.REACHABILITY, runningTool, host.isNotBlank(), Modifier.weight(1f), onRun)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = port,
                onValueChange = onPortChange,
                label = { Text("TCP port") },
                modifier = Modifier.weight(0.42f),
                singleLine = true
            )
            ToolButton("Test TCP", NetworkOperation.TCP_TEST, runningTool, host.isNotBlank() && port.isNotBlank(), Modifier.weight(0.58f), onRun)
        }
        Spacer(Modifier.height(8.dp))
        ToolButton("Traceroute", NetworkOperation.TRACEROUTE, runningTool, host.isNotBlank(), Modifier.fillMaxWidth(), onRun)
    }
}

@Composable
private fun CidrCard(
    cidr: String,
    onCidrChange: (String) -> Unit,
    running: Boolean,
    onCalculate: () -> Unit
) {
    DashboardCard(title = "IPv4 CIDR calculator") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = cidr,
                onValueChange = onCidrChange,
                label = { Text("CIDR") },
                placeholder = { Text("192.168.1.0/24") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Button(onClick = onCalculate, enabled = !running && cidr.isNotBlank()) {
                Text(if (running) "…" else "Calculate")
            }
        }
    }
}

@Composable
private fun ToolButton(
    label: String,
    operation: NetworkOperation,
    runningTool: String,
    enabled: Boolean,
    modifier: Modifier,
    onRun: (NetworkOperation) -> Unit
) {
    OutlinedButton(
        onClick = { onRun(operation) },
        enabled = enabled && runningTool.isBlank(),
        modifier = modifier
    ) { Text(if (runningTool == operation.id) "Running…" else label) }
}

@Composable
private fun DashboardCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun MetricGrid(items: List<Pair<String, String>>) {
    items.chunked(2).forEachIndexed { rowIndex, row ->
        if (rowIndex > 0) Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            row.forEach { (label, value) ->
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (value == "—") {
                        Text(value, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        NetworkCopyValue(label, value, Modifier.fillMaxWidth())
                    }
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatusPill(text: String) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(50)) {
        Text(text, modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
    }
}

private fun signalLabel(rssi: Int): String = when {
    rssi >= -50 -> "Excellent"
    rssi >= -60 -> "Good"
    rssi >= -70 -> "Fair"
    else -> "Weak"
}

private fun signalBars(rssi: Int): String = when {
    rssi >= -50 -> "▮▮▮▮"
    rssi >= -60 -> "▮▮▮▯"
    rssi >= -70 -> "▮▮▯▯"
    else -> "▮▯▯▯"
}

private fun com.example.methodmesh.transport.workflow.ExternalActionRequest.setting(key: String): String? =
    (settings[key] ?: settings["input_$key"])?.takeIf { it.isNotBlank() }
