package com.example.methodmesh.modules.networktools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.example.methodmesh.transport.workflow.ui.CapabilityPresentationMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

object NetworkToolsCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100NetworkToolsMethod.ID
    override val title = "Network tools"
    override val description = "Live network dashboard with bounded one-host diagnostics for direct use, presets, protocols and ODK."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        if (context.presentationMode == CapabilityPresentationMode.Dashboard) {
            NetworkToolsDashboard(
                context = context,
                onBack = onBack,
                onConfirmed = onConfirmed,
                onCancel = onCancel
            )
            return
        }

        NetworkToolsFocusedSurface(
            context = context,
            onBack = onBack,
            onConfirmed = onConfirmed,
            onCancel = onCancel
        )
    }
}

@Composable
private fun NetworkToolsFocusedSurface(
    context: CapabilityScreenContext,
    onBack: () -> Unit,
    onConfirmed: (ExecutionResult) -> Unit,
    onCancel: () -> Unit
) {
    val androidContext = LocalContext.current
    val scope = rememberCoroutineScope()

    var operation by rememberSaveable(context.action.canonicalId) {
        mutableStateOf(context.action.setting("operation") ?: NetworkOperation.INTERFACE_INFO.id)
    }
    var host by rememberSaveable(context.action.canonicalId) { mutableStateOf(context.action.setting("host").orEmpty()) }
    var port by rememberSaveable(context.action.canonicalId) { mutableStateOf(context.action.setting("port") ?: "443") }
    var timeoutMs by rememberSaveable(context.action.canonicalId) { mutableStateOf(context.action.setting("timeout_ms") ?: "3000") }
    var cidr by rememberSaveable(context.action.canonicalId) { mutableStateOf(context.action.setting("cidr").orEmpty()) }
    var tracerouteMaxHops by rememberSaveable(context.action.canonicalId) {
        mutableStateOf(context.action.setting("traceroute_max_hops") ?: "12")
    }
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    var launched by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var resultJson by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
    var resultSettingsJson by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
    var committedResultJson by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
    var committedSettingsJson by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
    var showOdkCard by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }

    val selectedOperation = NetworkOperation.from(operation) ?: NetworkOperation.INTERFACE_INFO
    val values = remember(resultJson) { resultJson.toStringMap() }
    val committedValues = remember(committedResultJson) { committedResultJson.toStringMap() }
    val committedSettings = remember(committedSettingsJson) { committedSettingsJson.toStringMap() }
    val committedExecution = remember(committedResultJson, committedSettingsJson) {
        committedValues.takeIf { it.isNotEmpty() }?.let {
            buildNetworkExecution(context, it, committedSettings)
        }
    }

    val currentSettings = linkedMapOf(
        "operation" to operation,
        "host" to host,
        "port" to port,
        "timeout_ms" to timeoutMs,
        "cidr" to cidr,
        "traceroute_max_hops" to tracerouteMaxHops
    )
    val currentSettingsJson = currentSettings.toJsonString()

    LaunchedEffect(operation, host, port, timeoutMs, cidr, tracerouteMaxHops) {
        context.onSettingsChanged(currentSettings)
    }

    fun canRun(): Boolean = when (selectedOperation) {
        NetworkOperation.DNS_LOOKUP,
        NetworkOperation.REACHABILITY,
        NetworkOperation.TCP_TEST,
        NetworkOperation.TRACEROUTE -> host.isNotBlank()
        NetworkOperation.CIDR -> cidr.isNotBlank()
        else -> true
    }

    fun runDiagnostic() {
        if (running || !canRun()) return
        running = true
        launched = true
        val settingsSnapshot = currentSettings.toMap()
        scope.launch {
            val captured = withContext(Dispatchers.IO) {
                NetworkToolsRunner.run(settingsSnapshot, androidContext.applicationContext)
            }
            val execution = buildNetworkExecution(context, captured, settingsSnapshot)
            if (context.submitsImmediately) {
                running = false
                onConfirmed(execution)
                return@launch
            }
            resultSettingsJson = settingsSnapshot.toJsonString()
            resultJson = captured.toJsonString()
            running = false
        }
    }

    val relevantRuntimeFields = selectedOperation.relevantSettingIds()
    val hasRuntimeInputToAsk = context.runtimeInputFields.any { it in relevantRuntimeFields }

    LaunchedEffect(
        context.startsImmediately,
        context.action.canonicalId,
        operation,
        context.runtimeInputFields,
        host,
        cidr
    ) {
        if (context.startsImmediately && !hasRuntimeInputToAsk && !launched && resultJson.isBlank() && canRun()) {
            runDiagnostic()
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Network tools", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Development · Online/Offline",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "One-host diagnostics only. No LAN sweep and no port-range scanner.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (context.settingShouldBeShown("operation")) {
            Text("Operation", style = MaterialTheme.typography.labelLarge)
            Box {
                OutlinedButton(onClick = { menuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(selectedOperation.label)
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    NetworkOperation.entries.forEach { candidate ->
                        DropdownMenuItem(
                            text = { Text(candidate.label) },
                            onClick = {
                                operation = candidate.id
                                resultJson = ""
                                resultSettingsJson = ""
                                launched = false
                                menuExpanded = false
                            }
                        )
                    }
                }
            }
        }

        if (selectedOperation.needsHost() && context.settingShouldBeShown("host")) {
            OutlinedTextField(
                value = host,
                onValueChange = { host = it.take(253) },
                label = { Text("Host name or IP") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        if (selectedOperation == NetworkOperation.TCP_TEST && context.settingShouldBeShown("port")) {
            OutlinedTextField(
                value = port,
                onValueChange = { port = it.filter(Char::isDigit).take(5) },
                label = { Text("TCP port") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        if (selectedOperation.usesTimeout() && context.settingShouldBeShown("timeout_ms")) {
            OutlinedTextField(
                value = timeoutMs,
                onValueChange = { timeoutMs = it.filter(Char::isDigit).take(5) },
                label = { Text("Timeout (ms)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        if (selectedOperation == NetworkOperation.TRACEROUTE && context.settingShouldBeShown("traceroute_max_hops")) {
            OutlinedTextField(
                value = tracerouteMaxHops,
                onValueChange = { tracerouteMaxHops = it.filter(Char::isDigit).take(2) },
                label = { Text("Maximum hops") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        if (selectedOperation == NetworkOperation.CIDR && context.settingShouldBeShown("cidr")) {
            OutlinedTextField(
                value = cidr,
                onValueChange = { cidr = it.take(32) },
                label = { Text("IPv4 CIDR") },
                placeholder = { Text("192.168.10.0/24") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        when (selectedOperation) {
            NetworkOperation.REACHABILITY -> Text(
                "Reachability uses InetAddress.isReachable; Android does not guarantee an ICMP echo test.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            NetworkOperation.TRACEROUTE -> Text(
                "Traceroute is best effort and runs only when the Android build exposes a traceroute/toybox applet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            NetworkOperation.WIFI_SCAN -> Text(
                "Nearby Wi-Fi requires the app Wi-Fi scan permissions and Android location permission. Permission or platform restrictions return a diagnostic instead of crashing.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            else -> Unit
        }

        if (running) {
            CircularProgressIndicator(modifier = Modifier.padding(vertical = 6.dp))
            Text("Running ${selectedOperation.label.lowercase()}…", style = MaterialTheme.typography.bodySmall)
        } else {
            Button(onClick = { runDiagnostic() }, enabled = canRun(), modifier = Modifier.fillMaxWidth()) {
                Text(operationVerb(selectedOperation))
            }
        }

        if (values.isNotEmpty()) {
            NetworkWorkingResultCard(
                values = values,
                onCommit = {
                    committedResultJson = resultJson
                    committedSettingsJson = resultSettingsJson.ifBlank { currentSettingsJson }
                },
                committedChanged = committedResultJson.isNotBlank() &&
                    (committedResultJson != resultJson || committedSettingsJson != currentSettingsJson)
            )
        }

        committedExecution?.let { execution ->
            NetworkCommittedActions(
                context = context,
                label = "Network tools",
                result = execution,
                workingChanged = committedResultJson != resultJson || committedSettingsJson != currentSettingsJson,
                onDone = { onConfirmed(execution) }
            )
        }

        if (!context.submitsImmediately && !context.isNativePresetRun) {
            OutlinedButton(
                onClick = { showOdkCard = !showOdkCard },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (showOdkCard) "Hide ODK integration" else "ODK integration") }
            if (showOdkCard) NetworkToolsOdkIntegrationCard()
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (context.stepNumber > 1) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Back") }
            }
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun operationVerb(operation: NetworkOperation): String = when (operation) {
    NetworkOperation.CONNECTION_STATUS -> "Capture connection status"
    NetworkOperation.INTERFACE_INFO -> "Capture interface info"
    NetworkOperation.DNS_LOOKUP -> "Resolve host"
    NetworkOperation.REACHABILITY -> "Check reachability"
    NetworkOperation.TCP_TEST -> "Test TCP endpoint"
    NetworkOperation.TRACEROUTE -> "Run traceroute"
    NetworkOperation.CIDR -> "Calculate CIDR"
    NetworkOperation.WIFI_INFO -> "Capture Wi-Fi info"
    NetworkOperation.WIFI_SCAN -> "Capture nearby Wi-Fi"
}

private fun com.example.methodmesh.transport.workflow.ExternalActionRequest.setting(key: String): String? =
    (settings[key] ?: settings["input_$key"])?.takeIf { it.isNotBlank() }

internal fun NetworkOperation.needsHost(): Boolean = this in setOf(
    NetworkOperation.DNS_LOOKUP,
    NetworkOperation.REACHABILITY,
    NetworkOperation.TCP_TEST,
    NetworkOperation.TRACEROUTE
)

internal fun NetworkOperation.usesTimeout(): Boolean = this in setOf(
    NetworkOperation.DNS_LOOKUP,
    NetworkOperation.REACHABILITY,
    NetworkOperation.TCP_TEST,
    NetworkOperation.TRACEROUTE
)

internal fun NetworkOperation.relevantSettingIds(): Set<String> {
    val operation = this
    return buildSet {
        add("operation")
        if (operation.needsHost()) add("host")
        if (operation.usesTimeout()) add("timeout_ms")
        if (operation == NetworkOperation.TCP_TEST) add("port")
        if (operation == NetworkOperation.CIDR) add("cidr")
        if (operation == NetworkOperation.TRACEROUTE) add("traceroute_max_hops")
    }
}

internal fun Map<String, String>.toJsonString(): String = JSONObject().apply {
    this@toJsonString.forEach { (key, value) -> put(key, value) }
}.toString()

internal fun String.toStringMap(): Map<String, String> {
    if (isBlank()) return emptyMap()
    return runCatching {
        val objectJson = JSONObject(this)
        linkedMapOf<String, String>().apply {
            val keys = objectJson.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                put(key, objectJson.optString(key, ""))
            }
        }
    }.getOrDefault(emptyMap())
}
