package com.example.methodmesh.modules.networktools

import com.example.methodmesh.core.methodmesh.*
import com.example.methodmesh.core.methodmesh.runtime.As100ExecutionEngine
import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.core.methodmesh.withInvocationContext
import com.example.methodmesh.settings.SettingsState
import org.json.JSONArray
import org.json.JSONObject
import java.net.*
import java.time.Instant
import java.util.concurrent.TimeUnit

object NetworkToolsFields {
    const val STATUS = "network_status"
    const val OPERATION = "network_operation"
    const val SUMMARY = "network_summary"
    const val RESULT_JSON = "network_result_json"
    const val LATENCY_MS = "network_latency_ms"
    const val HOST = "network_host"
    const val IP = "network_ip"
    const val CAPTURED_TIME_ISO = "network_captured_time_iso"
    const val ERROR = "network_error"
    val outputs = listOf(STATUS, OPERATION, SUMMARY, RESULT_JSON, LATENCY_MS, HOST, IP, CAPTURED_TIME_ISO, ERROR)
}

object As100NetworkToolsMethod : As100Method {
    const val ID = "network.tools"
    private const val VERSION = "0.1.0"
    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Network tools")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID), methodType = MethodObjectType.Calculation,
        name = "Network tools", version = VERSION,
        description = "Inspect interfaces, resolve DNS, test reachability/TCP endpoints, calculate IPv4 CIDRs and run best-effort traceroute.",
        outputs = NetworkToolsFields.outputs, graphOutputs = listOf(ID),
        parameters = mapOf("category" to "Network", "status" to "Development")
    )
    override val contract = MethodContract(method = ref, producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation), producedFields = descriptor.outputs, producedGraphOutputs = descriptor.graphOutputs)
    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) = As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)
    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult = result(request, run(request.context), InvocationContext.from(request.context))

    fun run(settings: Map<String, String>, androidSupplement: JSONObject? = null): Map<String, String> = runCatching {
        val operation = settings.value("operation") ?: "interface_info"
        val host = settings.value("host").orEmpty()
        val port = settings.value("port")?.toIntOrNull() ?: 443
        val timeout = settings.value("timeout_ms")?.toIntOrNull()?.coerceIn(100, 30000) ?: 3000
        val cidr = settings.value("cidr").orEmpty()
        var latency = ""
        var ip = ""
        val result = when (operation) {
            "interface_info" -> interfaceInfo()
            "dns_lookup" -> {
                require(host.isNotBlank()) { "Host is required." }
                val start = System.nanoTime(); val addresses = InetAddress.getAllByName(host); latency = ((System.nanoTime() - start) / 1_000_000L).toString(); ip = addresses.firstOrNull()?.hostAddress.orEmpty()
                JSONObject().apply { put("host", host); put("addresses", JSONArray(addresses.mapNotNull { it.hostAddress })); put("lookup_ms", latency.toLong()) }
            }
            "ping" -> {
                require(host.isNotBlank()) { "Host is required." }
                val address = InetAddress.getByName(host); ip = address.hostAddress.orEmpty(); val start = System.nanoTime(); val reachable = address.isReachable(timeout); latency = ((System.nanoTime() - start) / 1_000_000L).toString()
                JSONObject().apply { put("host", host); put("ip", ip); put("reachable", reachable); put("elapsed_ms", latency.toLong()); put("note", "InetAddress.isReachable is a reachability probe; ICMP behaviour varies by Android/device/network.") }
            }
            "tcp_test" -> {
                require(host.isNotBlank()) { "Host is required." }
                val start = System.nanoTime(); Socket().use { socket -> socket.connect(InetSocketAddress(host, port), timeout) }; latency = ((System.nanoTime() - start) / 1_000_000L).toString(); ip = InetAddress.getByName(host).hostAddress.orEmpty()
                JSONObject().apply { put("host", host); put("ip", ip); put("port", port); put("connected", true); put("elapsed_ms", latency.toLong()) }
            }
            "traceroute" -> traceroute(host, timeout)
            "cidr" -> cidrInfo(cidr)
            "wifi_info" -> androidSupplement ?: JSONObject().apply { put("available", false); put("reason", "Wi-Fi radio details require the Android capability screen and platform permissions.") }
            else -> error("Unsupported network operation: $operation")
        }
        val summary = when (operation) {
            "dns_lookup" -> "$host → ${ip.ifBlank { "no address" }}"
            "ping" -> "$host reachability probe: ${result.optBoolean("reachable", false)}"
            "tcp_test" -> "$host:$port connected"
            "cidr" -> "${result.optString("network")}/${result.optInt("prefix")} · ${result.optLong("usable_hosts")} usable hosts"
            "wifi_info" -> if (result.optBoolean("available", false)) "Wi-Fi connected · RSSI ${result.optInt("rssi_dbm")} dBm" else "Wi-Fi details unavailable"
            "traceroute" -> "Traceroute to $host: ${result.optJSONArray("lines")?.length() ?: 0} lines"
            else -> "${result.optJSONArray("interfaces")?.length() ?: 0} network interfaces"
        }
        success(operation, summary, result, latency, host, ip)
    }.getOrElse { failure(settings.value("operation") ?: "", settings.value("host").orEmpty(), it.message ?: "Network operation failed.") }

    private fun interfaceInfo(): JSONObject = JSONObject().apply {
        put("interfaces", JSONArray().apply {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().forEach { nic ->
                put(JSONObject().apply {
                    put("name", nic.name); put("display_name", nic.displayName); put("up", runCatching { nic.isUp }.getOrDefault(false)); put("loopback", runCatching { nic.isLoopback }.getOrDefault(false)); put("mtu", runCatching { nic.mtu }.getOrDefault(0))
                    put("addresses", JSONArray(nic.inetAddresses?.toList().orEmpty().mapNotNull { it.hostAddress }))
                })
            }
        })
    }

    private fun traceroute(host: String, timeoutMs: Int): JSONObject {
        require(host.isNotBlank()) { "Host is required." }
        val commands = listOf(
            listOf("/system/bin/toybox", "traceroute", "-m", "20", "-w", ((timeoutMs / 1000).coerceAtLeast(1)).toString(), host),
            listOf("traceroute", "-m", "20", host)
        )
        var lastError = "Traceroute binary unavailable."
        for (command in commands) {
            val result = runCatching {
                val process = ProcessBuilder(command).redirectErrorStream(true).start()
                val finished = process.waitFor((timeoutMs * 8L).coerceIn(5000L, 30000L), TimeUnit.MILLISECONDS)
                if (!finished) process.destroyForcibly()
                val lines = process.inputStream.bufferedReader().readLines().take(100)
                JSONObject().apply { put("command", command.joinToString(" ")); put("completed", finished); put("lines", JSONArray(lines)); put("exit_code", if (finished) process.exitValue() else -1) }
            }
            if (result.isSuccess) return result.getOrThrow()
            lastError = result.exceptionOrNull()?.message ?: lastError
        }
        return JSONObject().apply { put("completed", false); put("lines", JSONArray()); put("error", lastError) }
    }

    private fun cidrInfo(raw: String): JSONObject {
        require('/' in raw) { "CIDR must look like 192.168.1.0/24." }
        val parts = raw.trim().split('/'); val prefix = parts[1].toInt(); require(prefix in 0..32) { "CIDR prefix must be 0..32." }
        val ip = ipv4ToLong(parts[0]); val mask = if (prefix == 0) 0L else (0xffffffffL shl (32 - prefix)) and 0xffffffffL
        val network = ip and mask; val broadcast = network or (mask.inv() and 0xffffffffL)
        val total = 1L shl (32 - prefix)
        val usable = when { prefix == 32 -> 1L; prefix == 31 -> 2L; else -> (total - 2L).coerceAtLeast(0L) }
        val first = if (prefix >= 31) network else network + 1L; val last = if (prefix >= 31) broadcast else broadcast - 1L
        return JSONObject().apply { put("input", raw); put("network", longToIpv4(network)); put("broadcast", longToIpv4(broadcast)); put("first_host", longToIpv4(first)); put("last_host", longToIpv4(last)); put("prefix", prefix); put("netmask", longToIpv4(mask)); put("total_addresses", total); put("usable_hosts", usable) }
    }
    private fun ipv4ToLong(value: String): Long { val p = value.trim().split('.'); require(p.size == 4) { "Invalid IPv4 address." }; return p.fold(0L) { acc, s -> val n = s.toInt(); require(n in 0..255) { "Invalid IPv4 octet." }; (acc shl 8) or n.toLong() } }
    private fun longToIpv4(v: Long) = listOf(24, 16, 8, 0).joinToString(".") { ((v shr it) and 0xff).toString() }

    private fun success(operation: String, summary: String, result: JSONObject, latency: String, host: String, ip: String): Map<String, String> = linkedMapOf(
        NetworkToolsFields.STATUS to "succeeded", NetworkToolsFields.OPERATION to operation, NetworkToolsFields.SUMMARY to summary, NetworkToolsFields.RESULT_JSON to result.toString(), NetworkToolsFields.LATENCY_MS to latency, NetworkToolsFields.HOST to host, NetworkToolsFields.IP to ip, NetworkToolsFields.CAPTURED_TIME_ISO to Instant.now().toString(), NetworkToolsFields.ERROR to ""
    )
    private fun failure(operation: String, host: String, error: String): Map<String, String> = linkedMapOf(NetworkToolsFields.STATUS to "failed", NetworkToolsFields.OPERATION to operation, NetworkToolsFields.SUMMARY to "", NetworkToolsFields.RESULT_JSON to "{}", NetworkToolsFields.LATENCY_MS to "", NetworkToolsFields.HOST to host, NetworkToolsFields.IP to "", NetworkToolsFields.CAPTURED_TIME_ISO to Instant.now().toString(), NetworkToolsFields.ERROR to error)

    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?): ExecutionResult {
        val ok = values[NetworkToolsFields.STATUS] == "succeeded"
        val observation = Observation(phenomenon = ID, subject = null, values = values, temporalContext = request.temporalContext, provenance = ProvenanceContext("methodmesh.networktools", ID, VERSION))
        val transformation = Transformation(action = ID, method = ref, outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)), status = if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed, temporalContext = request.temporalContext, provenance = ProvenanceContext("methodmesh.networktools", ID, VERSION))
        return As100ExecutionEngine.complete(request, if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed, observations = listOf(observation), transformations = listOf(transformation), diagnostics = if (ok) emptyMap() else mapOf(NetworkToolsFields.ERROR to values[NetworkToolsFields.ERROR].orEmpty())).withInvocationContext(invocation)
    }
    private fun Map<String, String>.value(key: String) = (this[key] ?: this["input_$key"])?.takeIf { it.isNotBlank() }
}
