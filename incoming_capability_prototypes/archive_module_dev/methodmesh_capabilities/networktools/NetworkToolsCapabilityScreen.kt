package com.example.methodmesh.modules.networktools

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object NetworkToolsCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100NetworkToolsMethod.ID
    override val title = "Network tools"
    override val description = "Bounded diagnostics for the current device or a named target."

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val appContext = LocalContext.current.applicationContext
        val scope = rememberCoroutineScope()
        var operation by rememberSaveable { mutableStateOf(context.action.settings["operation"] ?: context.action.settings["input_operation"] ?: "interface_info") }
        var host by rememberSaveable { mutableStateOf(context.action.settings["host"] ?: context.action.settings["input_host"] ?: "") }
        var port by rememberSaveable { mutableStateOf(context.action.settings["port"] ?: context.action.settings["input_port"] ?: "443") }
        var timeout by rememberSaveable { mutableStateOf(context.action.settings["timeout_ms"] ?: context.action.settings["input_timeout_ms"] ?: "3000") }
        var cidr by rememberSaveable { mutableStateOf(context.action.settings["cidr"] ?: context.action.settings["input_cidr"] ?: "192.168.1.0/24") }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var running by rememberSaveable { mutableStateOf(false) }
        var launched by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }

        fun run() {
            if (running) return
            running = true
            scope.launch {
                val settings = mapOf("operation" to operation, "host" to host, "port" to port, "timeout_ms" to timeout, "cidr" to cidr)
                val values = withContext(Dispatchers.IO) { As100NetworkToolsMethod.run(settings, if (operation == "wifi_info") NetworkToolsAndroid.wifiInfo(appContext) else null) }
                val request = As100NetworkToolsMethod.request(capabilityId, context.request.invocationContext.asMap(capabilityId) + context.action.settings + settings, emptyList(), emptyList())
                result = As100NetworkToolsMethod.result(request, values, context.request.invocationContext)
                running = false
                if (context.submitsImmediately) result?.let(onConfirmed)
            }
        }
        LaunchedEffect(operation, host, port, timeout, cidr) { context.onSettingsChanged(mapOf("operation" to operation, "host" to host, "port" to port, "timeout_ms" to timeout, "cidr" to cidr)) }
        LaunchedEffect(context.presentationMode) { if (context.presentationMode == CapabilityPresentationMode.IntentLaunch && !launched) { launched = true; run() } }

        CapabilityScreenScaffold(title, capabilityId, context, context.stepNumber > 1, result, result?.let { OutputFormatter.fields(it, false) }.orEmpty(), onBack, { run() }, { result?.let(onConfirmed) }, onCancel) {
            Text("No broad LAN or port scanning is performed. Tests are limited to the current device, a named host/port, or an explicit CIDR calculation.")
            if (context.settingShouldBeShown("operation")) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    listOf("interface_info", "dns_lookup", "ping", "tcp_test", "traceroute", "cidr", "wifi_info").forEach { op ->
                        FilterChip(operation == op, { operation = op }, { Text(op.replace('_', ' ')) }, modifier = Modifier.padding(2.dp))
                    }
                }
            } else {
                Text("Operation: ${operation.replace('_', ' ')}", style = MaterialTheme.typography.bodySmall)
            }
            if (operation in listOf("dns_lookup", "ping", "tcp_test", "traceroute") && context.settingShouldBeShown("host")) OutlinedTextField(host, { host = it }, label = { Text("Host") }, modifier = Modifier.fillMaxWidth())
            if (operation == "tcp_test" && context.settingShouldBeShown("port")) OutlinedTextField(port, { port = it.filter(Char::isDigit) }, label = { Text("TCP port") }, modifier = Modifier.fillMaxWidth())
            if (operation in listOf("ping", "tcp_test", "traceroute") && context.settingShouldBeShown("timeout_ms")) OutlinedTextField(timeout, { timeout = it.filter(Char::isDigit) }, label = { Text("Timeout (ms)") }, modifier = Modifier.fillMaxWidth())
            if (operation == "cidr" && context.settingShouldBeShown("cidr")) OutlinedTextField(cidr, { cidr = it }, label = { Text("IPv4 CIDR") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp)); Button(onClick = ::run, enabled = !running, modifier = Modifier.fillMaxWidth()) { Text(if (running) "Running…" else "Run") }
        }
    }
}
