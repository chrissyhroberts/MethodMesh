package com.example.methodmesh.modules.networktools

import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

object NetworkToolsCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100NetworkToolsMethod.ID
    override val title = "Network tools"
    override val description = "Run a bounded diagnostic against one endpoint or inspect the current connection."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val androidContext = LocalContext.current
        val scope = rememberCoroutineScope()

        var operation by rememberSaveable {
            mutableStateOf(context.action.setting("operation") ?: NetworkOperation.INTERFACE_INFO.id)
        }
        var host by rememberSaveable { mutableStateOf(context.action.setting("host").orEmpty()) }
        var port by rememberSaveable { mutableStateOf(context.action.setting("port") ?: "443") }
        var timeoutMs by rememberSaveable { mutableStateOf(context.action.setting("timeout_ms") ?: "3000") }
        var cidr by rememberSaveable { mutableStateOf(context.action.setting("cidr").orEmpty()) }
        var tracerouteMaxHops by rememberSaveable {
            mutableStateOf(context.action.setting("traceroute_max_hops") ?: "12")
        }
        var menuExpanded by rememberSaveable { mutableStateOf(false) }
        var launched by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        var running by remember { mutableStateOf(false) }
        var resultJson by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }

        val selectedOperation = NetworkOperation.from(operation) ?: NetworkOperation.INTERFACE_INFO
        val values = remember(resultJson) { resultJson.toStringMap() }
        val executionResult = remember(resultJson) {
            if (values.isEmpty()) null else {
                val request = As100NetworkToolsMethod.request(
                    action = As100NetworkToolsMethod.ID,
                    context = context.request.invocationContext.asMap(As100NetworkToolsMethod.ID) + context.action.settings + values,
                    signals = emptyList(),
                    inputs = emptyList()
                )
                As100NetworkToolsMethod.result(request, values, context.request.invocationContext)
            }
        }

        val currentSettings = mapOf(
            "operation" to operation,
            "host" to host,
            "port" to port,
            "timeout_ms" to timeoutMs,
            "cidr" to cidr,
            "traceroute_max_hops" to tracerouteMaxHops
        )

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
            scope.launch {
                val captured = withContext(Dispatchers.IO) {
                    NetworkToolsRunner.run(currentSettings, androidContext.applicationContext)
                }
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

        val resultPreview = if (values.isEmpty()) emptyMap() else linkedMapOf<String, Any?>(
            NetworkToolsFields.VALUE to values[NetworkToolsFields.VALUE].orEmpty(),
            NetworkToolsFields.SUMMARY to values[NetworkToolsFields.SUMMARY].orEmpty(),
            NetworkToolsFields.STATUS to values[NetworkToolsFields.STATUS].orEmpty(),
            NetworkToolsFields.RESULT_JSON to values[NetworkToolsFields.RESULT_JSON].orEmpty(),
            NetworkToolsFields.CAPTURED_TIME_ISO to values[NetworkToolsFields.CAPTURED_TIME_ISO].orEmpty(),
            NetworkToolsFields.ERROR to values[NetworkToolsFields.ERROR].orEmpty()
        )

        CapabilityScreenScaffold(
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = executionResult,
            resultPreview = resultPreview,
            onBack = onBack,
            onRetry = { runDiagnostic() },
            onConfirm = { executionResult?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Text(
                "One-host diagnostics only. This module does not scan LANs or port ranges.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(10.dp))

            if (context.settingShouldBeShown("operation")) {
                Text("Operation", style = MaterialTheme.typography.labelLarge)
                Box {
                    OutlinedButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(selectedOperation.label) }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        NetworkOperation.entries.forEach { candidate ->
                            DropdownMenuItem(
                                text = { Text(candidate.label) },
                                onClick = {
                                    operation = candidate.id
                                    resultJson = ""
                                    launched = false
                                    menuExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (selectedOperation.needsHost() && context.settingShouldBeShown("host")) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it.take(253) },
                    label = { Text("Host name or IP") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
            }

            if (selectedOperation == NetworkOperation.TCP_TEST && context.settingShouldBeShown("port")) {
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter(Char::isDigit).take(5) },
                    label = { Text("TCP port") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
            }

            if (selectedOperation.usesTimeout() && context.settingShouldBeShown("timeout_ms")) {
                OutlinedTextField(
                    value = timeoutMs,
                    onValueChange = { timeoutMs = it.filter(Char::isDigit).take(5) },
                    label = { Text("Timeout (ms)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
            }

            if (selectedOperation == NetworkOperation.TRACEROUTE && context.settingShouldBeShown("traceroute_max_hops")) {
                OutlinedTextField(
                    value = tracerouteMaxHops,
                    onValueChange = { tracerouteMaxHops = it.filter(Char::isDigit).take(2) },
                    label = { Text("Maximum hops") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
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
                Spacer(Modifier.height(8.dp))
            }

            if (selectedOperation == NetworkOperation.REACHABILITY) {
                Text(
                    "Reachability uses Android/Java InetAddress.isReachable; it is not guaranteed to be an ICMP echo test.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
            }
            if (selectedOperation == NetworkOperation.TRACEROUTE) {
                Text(
                    "Traceroute is best effort and only runs when the Android build exposes a traceroute/toybox applet.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
            }

            if (running) {
                CircularProgressIndicator(modifier = Modifier.padding(vertical = 8.dp))
                Text("Running ${selectedOperation.label.lowercase()}…", style = MaterialTheme.typography.bodySmall)
            } else {
                Button(
                    onClick = { runDiagnostic() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canRun()
                ) { Text("Run diagnostic") }
            }
        }
    }
}

private fun com.example.methodmesh.transport.workflow.ExternalActionRequest.setting(key: String): String? =
    (settings[key] ?: settings["input_$key"])?.takeIf { it.isNotBlank() }

private fun NetworkOperation.needsHost(): Boolean = this in setOf(
    NetworkOperation.DNS_LOOKUP,
    NetworkOperation.REACHABILITY,
    NetworkOperation.TCP_TEST,
    NetworkOperation.TRACEROUTE
)

private fun NetworkOperation.usesTimeout(): Boolean = this in setOf(
    NetworkOperation.DNS_LOOKUP,
    NetworkOperation.REACHABILITY,
    NetworkOperation.TCP_TEST,
    NetworkOperation.TRACEROUTE
)

private fun NetworkOperation.relevantSettingIds(): Set<String> {
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

private fun Map<String, String>.toJsonString(): String {
    val values = this
    return JSONObject().apply {
        values.forEach { (key, value) -> put(key, value) }
    }.toString()
}

private fun String.toStringMap(): Map<String, String> {
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
