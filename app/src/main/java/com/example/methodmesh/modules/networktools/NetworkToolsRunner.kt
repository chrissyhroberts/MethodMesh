package com.example.methodmesh.modules.networktools

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.IDN
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.Instant
import java.util.Collections
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.math.roundToLong

internal enum class NetworkOperation(val id: String, val label: String) {
    INTERFACE_INFO("interface_info", "Interface info"),
    DNS_LOOKUP("dns_lookup", "DNS lookup"),
    REACHABILITY("ping", "Reachability"),
    TCP_TEST("tcp_test", "TCP endpoint"),
    TRACEROUTE("traceroute", "Traceroute"),
    CIDR("cidr", "IPv4 CIDR"),
    WIFI_INFO("wifi_info", "Wi-Fi info");

    companion object {
        fun from(raw: String?): NetworkOperation? = entries.firstOrNull { it.id == raw?.trim()?.lowercase() }
    }
}

internal object NetworkToolsRunner {
    private const val DEFAULT_TIMEOUT_MS = 3000
    private const val MAX_TRACEROUTE_TEXT = 16000

    fun run(settings: Map<String, String>, androidContext: Context?): Map<String, String> {
        val operationRaw = settings.value("operation") ?: NetworkOperation.INTERFACE_INFO.id
        val operation = NetworkOperation.from(operationRaw)
            ?: return failure(operationRaw, "Unknown operation '$operationRaw'.")

        return try {
            val timeoutMs = boundedInt(settings.value("timeout_ms"), "timeout_ms", DEFAULT_TIMEOUT_MS, 100, 30000)
            when (operation) {
                NetworkOperation.INTERFACE_INFO -> interfaceInfo()
                NetworkOperation.DNS_LOOKUP -> dnsLookup(requireHost(settings), timeoutMs)
                NetworkOperation.REACHABILITY -> reachability(requireHost(settings), timeoutMs)
                NetworkOperation.TCP_TEST -> tcpTest(
                    host = requireHost(settings),
                    port = boundedInt(settings.value("port"), "port", 443, 1, 65535),
                    timeoutMs = timeoutMs
                )
                NetworkOperation.TRACEROUTE -> traceroute(
                    host = requireHost(settings),
                    timeoutMs = timeoutMs,
                    maxHops = boundedInt(settings.value("traceroute_max_hops"), "traceroute_max_hops", 12, 1, 30)
                )
                NetworkOperation.CIDR -> cidr(settings.value("cidr").orEmpty())
                NetworkOperation.WIFI_INFO -> wifiInfo(androidContext)
            }
        } catch (e: NetworkToolsInputException) {
            failure(operation.id, e.message ?: "Invalid input.")
        } catch (e: SecurityException) {
            failure(operation.id, "Permission denied: ${e.message ?: "Android did not expose the requested network information."}")
        } catch (e: Exception) {
            failure(operation.id, e.message ?: e.javaClass.simpleName)
        }
    }

    private fun interfaceInfo(): Map<String, String> {
        val enumeration = NetworkInterface.getNetworkInterfaces()
        val interfaces = if (enumeration == null) emptyList() else Collections.list(enumeration)
        val jsonInterfaces = JSONArray()
        var primaryIp = ""
        var primaryInterface = ""

        interfaces.sortedBy { it.name }.forEach { iface ->
            val isUp = runCatching { iface.isUp }.getOrDefault(false)
            val isLoopback = runCatching { iface.isLoopback }.getOrDefault(false)
            val addresses = Collections.list(iface.inetAddresses)
                .mapNotNull { it.hostAddress?.substringBefore('%') }
                .distinct()
            if (isUp && !isLoopback && primaryIp.isBlank()) {
                primaryIp = addresses.firstOrNull { !it.startsWith("127.") && it != "::1" }.orEmpty()
                if (primaryIp.isNotBlank()) primaryInterface = iface.name.orEmpty()
            }
            jsonInterfaces.put(JSONObject().apply {
                put("name", iface.name.orEmpty())
                put("display_name", iface.displayName.orEmpty())
                put("up", isUp)
                put("loopback", isLoopback)
                put("mtu", runCatching { iface.mtu }.getOrNull() ?: JSONObject.NULL)
                put("addresses", JSONArray(addresses))
            })
        }

        val summary = when {
            primaryIp.isNotBlank() -> "$primaryInterface • $primaryIp"
            jsonInterfaces.length() > 0 -> "${jsonInterfaces.length()} network interface(s) detected"
            else -> "No network interfaces reported"
        }
        val json = JSONObject().apply {
            put("operation", NetworkOperation.INTERFACE_INFO.id)
            put("primary_interface", primaryInterface)
            put("primary_ip", primaryIp)
            put("interfaces", jsonInterfaces)
        }
        return success(
            operation = NetworkOperation.INTERFACE_INFO,
            value = primaryIp.ifBlank { "${jsonInterfaces.length()} interfaces" },
            summary = summary,
            resultJson = json,
            ip = primaryIp,
            interfaceName = primaryInterface
        )
    }

    private fun dnsLookup(host: String, timeoutMs: Int): Map<String, String> {
        val started = System.nanoTime()
        return try {
            val addresses = resolveAddresses(host, timeoutMs)
            val latency = elapsedMs(started)
            val ips = addresses.mapNotNull { it.hostAddress?.substringBefore('%') }.distinct()
            val firstIp = ips.firstOrNull().orEmpty()
            val json = JSONObject().apply {
                put("operation", NetworkOperation.DNS_LOOKUP.id)
                put("host", host)
                put("addresses", JSONArray(ips))
                put("latency_ms", latency)
                put("resolved", ips.isNotEmpty())
            }
            success(
                operation = NetworkOperation.DNS_LOOKUP,
                value = firstIp,
                summary = if (ips.isEmpty()) "$host did not resolve" else "$host → ${ips.joinToString(", ")}",
                resultJson = json,
                host = host,
                ip = firstIp,
                latencyMs = latency.toString(),
                detail = if (ips.size > 1) "${ips.size} addresses returned" else ""
            )
        } catch (e: UnknownHostException) {
            val latency = elapsedMs(started)
            val json = JSONObject().apply {
                put("operation", NetworkOperation.DNS_LOOKUP.id)
                put("host", host)
                put("addresses", JSONArray())
                put("latency_ms", latency)
                put("resolved", false)
                put("reason", e.message.orEmpty())
            }
            success(
                operation = NetworkOperation.DNS_LOOKUP,
                value = "unresolved",
                summary = "$host did not resolve",
                resultJson = json,
                host = host,
                latencyMs = latency.toString(),
                detail = e.message.orEmpty()
            )
        } catch (e: TimeoutException) {
            val latency = elapsedMs(started)
            val json = JSONObject().apply {
                put("operation", NetworkOperation.DNS_LOOKUP.id)
                put("host", host)
                put("addresses", JSONArray())
                put("latency_ms", latency)
                put("resolved", false)
                put("timed_out", true)
            }
            success(
                operation = NetworkOperation.DNS_LOOKUP,
                value = "timeout",
                summary = "$host DNS lookup timed out",
                resultJson = json,
                host = host,
                latencyMs = latency.toString(),
                detail = "Timed out after $timeoutMs ms"
            )
        }
    }

    private fun reachability(host: String, timeoutMs: Int): Map<String, String> {
        val started = System.nanoTime()
        val addresses = try {
            resolveAddresses(host, timeoutMs)
        } catch (e: Exception) {
            val latency = elapsedMs(started)
            val json = JSONObject().apply {
                put("operation", NetworkOperation.REACHABILITY.id)
                put("host", host)
                put("reachable", false)
                put("latency_ms", latency)
                put("reason", e.message.orEmpty())
            }
            return success(
                operation = NetworkOperation.REACHABILITY,
                value = "unreachable",
                summary = "$host is not reachable",
                resultJson = json,
                host = host,
                latencyMs = latency.toString(),
                reachable = "false",
                detail = e.message.orEmpty()
            )
        }
        val address = addresses.first()
        val remaining = remainingMs(started, timeoutMs)
        val reachable = address.isReachable(remaining)
        val latency = elapsedMs(started)
        val ip = address.hostAddress?.substringBefore('%').orEmpty()
        val json = JSONObject().apply {
            put("operation", NetworkOperation.REACHABILITY.id)
            put("host", host)
            put("ip", ip)
            put("reachable", reachable)
            put("latency_ms", latency)
            put("probe", "InetAddress.isReachable")
        }
        return success(
            operation = NetworkOperation.REACHABILITY,
            value = if (reachable) "reachable" else "unreachable",
            summary = "$host ${if (reachable) "reachable" else "not reachable"} • ${latency} ms",
            resultJson = json,
            host = host,
            ip = ip,
            latencyMs = latency.toString(),
            reachable = reachable.toString(),
            detail = "Android/Java reachability probe; not guaranteed ICMP echo"
        )
    }

    private fun tcpTest(host: String, port: Int, timeoutMs: Int): Map<String, String> {
        val started = System.nanoTime()
        val address = try {
            resolveAddresses(host, timeoutMs).first()
        } catch (e: Exception) {
            val latency = elapsedMs(started)
            val json = JSONObject().apply {
                put("operation", NetworkOperation.TCP_TEST.id)
                put("host", host)
                put("port", port)
                put("open", false)
                put("latency_ms", latency)
                put("reason", e.message.orEmpty())
            }
            return success(
                operation = NetworkOperation.TCP_TEST,
                value = "closed",
                summary = "$host:$port not reachable",
                resultJson = json,
                host = host,
                port = port.toString(),
                latencyMs = latency.toString(),
                tcpOpen = "false",
                detail = e.message.orEmpty()
            )
        }
        val ip = address.hostAddress?.substringBefore('%').orEmpty()
        var detail = ""
        val open = try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(address, port), remainingMs(started, timeoutMs))
            }
            true
        } catch (e: SocketTimeoutException) {
            detail = "Connection timed out"
            false
        } catch (e: ConnectException) {
            detail = e.message ?: "Connection refused"
            false
        } catch (e: IOException) {
            detail = e.message ?: e.javaClass.simpleName
            false
        }
        val latency = elapsedMs(started)
        val json = JSONObject().apply {
            put("operation", NetworkOperation.TCP_TEST.id)
            put("host", host)
            put("ip", ip)
            put("port", port)
            put("open", open)
            put("latency_ms", latency)
            if (detail.isNotBlank()) put("reason", detail)
        }
        return success(
            operation = NetworkOperation.TCP_TEST,
            value = if (open) "open" else "closed",
            summary = "$host:$port ${if (open) "open" else "closed/unreachable"} • ${latency} ms",
            resultJson = json,
            host = host,
            ip = ip,
            port = port.toString(),
            latencyMs = latency.toString(),
            tcpOpen = open.toString(),
            detail = detail
        )
    }

    private fun traceroute(host: String, timeoutMs: Int, maxHops: Int): Map<String, String> {
        val started = System.nanoTime()
        val args = listOf("-n", "-m", maxHops.toString(), "-w", "1", host)
        val candidates = listOf(
            listOf("/system/bin/toybox", "traceroute"),
            listOf("toybox", "traceroute"),
            listOf("/system/bin/traceroute"),
            listOf("traceroute")
        )
        var lastUnavailableReason = "No traceroute command is available on this Android build."

        candidates.forEach { prefix ->
            val process = try {
                ProcessBuilder(prefix + args)
                    .redirectErrorStream(true)
                    .start()
            } catch (e: IOException) {
                lastUnavailableReason = e.message ?: lastUnavailableReason
                return@forEach
            }

            val finished = process.waitFor(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                val latency = elapsedMs(started)
                val json = JSONObject().apply {
                    put("operation", NetworkOperation.TRACEROUTE.id)
                    put("host", host)
                    put("max_hops", maxHops)
                    put("timed_out", true)
                    put("latency_ms", latency)
                }
                return success(
                    operation = NetworkOperation.TRACEROUTE,
                    value = "timeout",
                    summary = "Traceroute to $host timed out",
                    resultJson = json,
                    host = host,
                    latencyMs = latency.toString(),
                    detail = "Bounded at $timeoutMs ms and $maxHops hops"
                )
            }

            val output = process.inputStream.bufferedReader().use { it.readText().take(MAX_TRACEROUTE_TEXT) }.trim()
            val lower = output.lowercase()
            val commandUnavailable = process.exitValue() != 0 && (
                "unknown command" in lower ||
                    "not found" in lower ||
                    "no such file" in lower ||
                    "applet not found" in lower
                )
            if (commandUnavailable) {
                lastUnavailableReason = output.ifBlank { "Traceroute command unavailable." }
                return@forEach
            }

            val hopLines = output.lineSequence()
                .map(String::trim)
                .filter { it.matches(Regex("^\\d+\\s+.*")) }
                .toList()
            val latency = elapsedMs(started)
            val json = JSONObject().apply {
                put("operation", NetworkOperation.TRACEROUTE.id)
                put("host", host)
                put("max_hops", maxHops)
                put("hop_count", hopLines.size)
                put("latency_ms", latency)
                put("command", prefix.joinToString(" "))
                put("hops", JSONArray(hopLines))
                put("raw_output", output)
                put("exit_code", process.exitValue())
            }
            return success(
                operation = NetworkOperation.TRACEROUTE,
                value = "${hopLines.size} hops",
                summary = "Traceroute to $host • ${hopLines.size} hop(s)",
                resultJson = json,
                host = host,
                latencyMs = latency.toString(),
                detail = "Best effort; bounded at $maxHops hops"
            )
        }

        return unavailable(
            operation = NetworkOperation.TRACEROUTE,
            message = lastUnavailableReason,
            host = host
        )
    }

    private fun cidr(rawCidr: String): Map<String, String> {
        val cidr = rawCidr.trim()
        if (cidr.isBlank()) throw NetworkToolsInputException("Enter an IPv4 CIDR, for example 192.168.10.0/24.")
        val parts = cidr.split('/')
        if (parts.size != 2) throw NetworkToolsInputException("CIDR must use address/prefix notation.")
        val prefix = parts[1].toIntOrNull()?.takeIf { it in 0..32 }
            ?: throw NetworkToolsInputException("IPv4 prefix length must be 0–32.")
        val addressLong = parseIpv4Literal(parts[0].trim())
        val mask = if (prefix == 0) 0L else (0xffffffffL shl (32 - prefix)) and 0xffffffffL
        val wildcard = mask.inv() and 0xffffffffL
        val network = addressLong and mask
        val broadcast = network or wildcard
        val totalAddresses = 1L shl (32 - prefix)
        val usableHosts = when (prefix) {
            32 -> 1L
            31 -> 2L
            else -> (totalAddresses - 2L).coerceAtLeast(0L)
        }
        val firstUsable = when (prefix) {
            32, 31 -> network
            else -> network + 1L
        }
        val lastUsable = when (prefix) {
            32 -> network
            31 -> broadcast
            else -> broadcast - 1L
        }
        val normalized = "${ipv4(network)}/$prefix"
        val json = JSONObject().apply {
            put("operation", NetworkOperation.CIDR.id)
            put("input_cidr", cidr)
            put("cidr", normalized)
            put("prefix_length", prefix)
            put("netmask", ipv4(mask))
            put("wildcard_mask", ipv4(wildcard))
            put("network_address", ipv4(network))
            put("broadcast_address", ipv4(broadcast))
            put("first_usable", ipv4(firstUsable))
            put("last_usable", ipv4(lastUsable))
            put("total_addresses", totalAddresses)
            put("usable_hosts", usableHosts)
        }
        return success(
            operation = NetworkOperation.CIDR,
            value = "$normalized → ${ipv4(network)}–${ipv4(broadcast)}",
            summary = "$normalized • ${ipv4(network)}–${ipv4(broadcast)} • $usableHosts usable",
            resultJson = json,
            cidr = normalized,
            detail = "Netmask ${ipv4(mask)}; usable ${ipv4(firstUsable)}–${ipv4(lastUsable)}"
        )
    }

    @Suppress("DEPRECATION")
    private fun wifiInfo(context: Context?): Map<String, String> {
        if (context == null) {
            return unavailable(
                operation = NetworkOperation.WIFI_INFO,
                message = "Wi-Fi connection details require an Android capability context."
            )
        }
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
            ?: return unavailable(NetworkOperation.WIFI_INFO, "Android ConnectivityManager is unavailable.")
        val network = connectivity.activeNetwork
            ?: return success(
                operation = NetworkOperation.WIFI_INFO,
                value = "disconnected",
                summary = "No active network",
                resultJson = JSONObject().put("operation", NetworkOperation.WIFI_INFO.id).put("connected", false),
                detail = "No active Android network"
            )
        val capabilities = connectivity.getNetworkCapabilities(network)
            ?: return unavailable(NetworkOperation.WIFI_INFO, "Active network capabilities are unavailable.")
        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return success(
                operation = NetworkOperation.WIFI_INFO,
                value = "not Wi-Fi",
                summary = "Active network is not Wi-Fi",
                resultJson = JSONObject()
                    .put("operation", NetworkOperation.WIFI_INFO.id)
                    .put("connected", true)
                    .put("wifi", false),
                detail = "The active network uses another transport"
            )
        }

        val wifiInfo: WifiInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            capabilities.transportInfo as? WifiInfo
        } else {
            (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)?.connectionInfo
        }
        val linkProperties = connectivity.getLinkProperties(network)
        val ip = linkProperties?.linkAddresses
            ?.mapNotNull { it.address?.hostAddress?.substringBefore('%') }
            ?.firstOrNull { it != "::1" && !it.startsWith("127.") }
            .orEmpty()
        val ssid = cleanSsid(wifiInfo?.ssid)
        val bssid = wifiInfo?.bssid
            ?.takeUnless { it.equals("02:00:00:00:00:00", ignoreCase = true) }
            .orEmpty()
        val gateway = linkProperties?.routes
            ?.firstOrNull { it.isDefaultRoute }
            ?.gateway
            ?.hostAddress
            ?.substringBefore('%')
            .orEmpty()
        val dns = linkProperties?.dnsServers
            ?.mapNotNull { it.hostAddress?.substringBefore('%') }
            .orEmpty()

        val json = JSONObject().apply {
            put("operation", NetworkOperation.WIFI_INFO.id)
            put("connected", true)
            put("wifi", true)
            put("ssid", if (ssid.isBlank()) JSONObject.NULL else ssid)
            put("bssid", if (bssid.isBlank()) JSONObject.NULL else bssid)
            put("ip", if (ip.isBlank()) JSONObject.NULL else ip)
            put("gateway", if (gateway.isBlank()) JSONObject.NULL else gateway)
            put("dns_servers", JSONArray(dns))
            put("rssi_dbm", wifiInfo?.rssi ?: JSONObject.NULL)
            put("link_speed_mbps", wifiInfo?.linkSpeed ?: JSONObject.NULL)
            put("frequency_mhz", wifiInfo?.frequency ?: JSONObject.NULL)
            put("metered", connectivity.isActiveNetworkMetered)
            put("internet_capability", capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            put("validated", capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
        }
        val value = ssid.ifBlank { ip.ifBlank { "Wi-Fi connected" } }
        val summary = buildString {
            append(if (ssid.isNotBlank()) ssid else "Wi-Fi")
            if (ip.isNotBlank()) append(" • $ip")
            wifiInfo?.rssi?.let { append(" • $it dBm") }
        }
        val detail = if (ssid.isBlank() || bssid.isBlank()) {
            "SSID/BSSID may be redacted by Android permission/privacy policy."
        } else ""
        return success(
            operation = NetworkOperation.WIFI_INFO,
            value = value,
            summary = summary,
            resultJson = json,
            ip = ip,
            detail = detail
        )
    }

    private fun resolveAddresses(host: String, timeoutMs: Int): List<InetAddress> {
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "methodmesh-network-dns").apply { isDaemon = true }
        }
        return try {
            executor.submit<List<InetAddress>> { InetAddress.getAllByName(host).toList() }
                .get(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
                .ifEmpty { throw UnknownHostException(host) }
        } catch (e: ExecutionException) {
            when (val cause = e.cause) {
                is UnknownHostException -> throw cause
                is Exception -> throw cause
                else -> throw IOException("DNS lookup failed.", e)
            }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun requireHost(settings: Map<String, String>): String {
        val raw = settings.value("host").orEmpty().trim()
        if (raw.isBlank()) throw NetworkToolsInputException("Enter one host name or IP address.")
        if (raw.length > 253) throw NetworkToolsInputException("Host is too long.")
        if (raw.any(Char::isWhitespace) || '/' in raw || '\\' in raw || '?' in raw || '#' in raw || '@' in raw) {
            throw NetworkToolsInputException("Enter a host name or IP address only, not a URL.")
        }
        val unbracketed = raw.removePrefix("[").removeSuffix("]")
        if (unbracketed.startsWith("-")) throw NetworkToolsInputException("Invalid host name.")
        if (':' in unbracketed) {
            try {
                InetAddress.getByName(unbracketed)
            } catch (e: Exception) {
                throw NetworkToolsInputException("Invalid IPv6 address.")
            }
            return unbracketed
        }
        return try {
            IDN.toASCII(unbracketed, IDN.USE_STD3_ASCII_RULES)
        } catch (e: IllegalArgumentException) {
            throw NetworkToolsInputException("Invalid host name.")
        }
    }

    private fun success(
        operation: NetworkOperation,
        value: String,
        summary: String,
        resultJson: JSONObject,
        latencyMs: String = "",
        host: String = "",
        ip: String = "",
        port: String = "",
        reachable: String = "",
        tcpOpen: String = "",
        interfaceName: String = "",
        cidr: String = "",
        detail: String = ""
    ): Map<String, String> = base(
        status = "succeeded",
        operation = operation.id,
        value = value,
        summary = summary,
        resultJson = resultJson.toString(),
        latencyMs = latencyMs,
        host = host,
        ip = ip,
        port = port,
        reachable = reachable,
        tcpOpen = tcpOpen,
        interfaceName = interfaceName,
        cidr = cidr,
        detail = detail,
        error = ""
    )

    private fun unavailable(operation: NetworkOperation, message: String, host: String = ""): Map<String, String> =
        base(
            status = "unavailable",
            operation = operation.id,
            value = "",
            summary = message,
            resultJson = JSONObject()
                .put("operation", operation.id)
                .put("status", "unavailable")
                .put("reason", message)
                .toString(),
            host = host,
            detail = message,
            error = message
        )

    private fun failure(operation: String, error: String): Map<String, String> = base(
        status = "failed",
        operation = operation,
        value = "",
        summary = error,
        resultJson = JSONObject()
            .put("operation", operation)
            .put("status", "failed")
            .put("error", error)
            .toString(),
        error = error
    )

    private fun base(
        status: String,
        operation: String,
        value: String,
        summary: String,
        resultJson: String,
        latencyMs: String = "",
        host: String = "",
        ip: String = "",
        port: String = "",
        reachable: String = "",
        tcpOpen: String = "",
        interfaceName: String = "",
        cidr: String = "",
        detail: String = "",
        error: String = ""
    ): Map<String, String> = linkedMapOf(
        NetworkToolsFields.VALUE to value,
        NetworkToolsFields.SUMMARY to summary,
        NetworkToolsFields.RESULT_JSON to resultJson,
        NetworkToolsFields.LATENCY_MS to latencyMs,
        NetworkToolsFields.HOST to host,
        NetworkToolsFields.IP to ip,
        NetworkToolsFields.PORT to port,
        NetworkToolsFields.REACHABLE to reachable,
        NetworkToolsFields.TCP_OPEN to tcpOpen,
        NetworkToolsFields.INTERFACE to interfaceName,
        NetworkToolsFields.CIDR to cidr,
        NetworkToolsFields.DETAIL to detail,
        NetworkToolsFields.STATUS to status,
        NetworkToolsFields.OPERATION to operation,
        NetworkToolsFields.CAPTURED_TIME_ISO to Instant.now().toString(),
        NetworkToolsFields.ERROR to error
    )


    private fun boundedInt(raw: String?, name: String, defaultValue: Int, minimum: Int, maximum: Int): Int {
        if (raw.isNullOrBlank()) return defaultValue
        val value = raw.toIntOrNull() ?: throw NetworkToolsInputException("$name must be an integer.")
        if (value !in minimum..maximum) {
            throw NetworkToolsInputException("$name must be between $minimum and $maximum.")
        }
        return value
    }

    private fun parseIpv4Literal(raw: String): Long {
        val parts = raw.split('.')
        if (parts.size != 4) throw NetworkToolsInputException("Invalid IPv4 address.")
        val octets = parts.map { part ->
            if (part.isBlank() || part.any { !it.isDigit() }) throw NetworkToolsInputException("Invalid IPv4 address.")
            part.toIntOrNull()?.takeIf { it in 0..255 }
                ?: throw NetworkToolsInputException("Invalid IPv4 address.")
        }
        return octets.fold(0L) { acc, octet -> (acc shl 8) or octet.toLong() }
    }

    private fun Map<String, String>.value(key: String): String? =
        (this[key] ?: this["input_$key"])?.takeIf { it.isNotBlank() }

    private fun remainingMs(startedNanos: Long, timeoutMs: Int): Int =
        (timeoutMs - elapsedMs(startedNanos)).coerceAtLeast(1L).coerceAtMost(timeoutMs.toLong()).toInt()

    private fun elapsedMs(startedNanos: Long): Long =
        ((System.nanoTime() - startedNanos) / 1_000_000.0).roundToLong().coerceAtLeast(0L)

    private fun ipv4(value: Long): String = listOf(24, 16, 8, 0)
        .joinToString(".") { shift -> ((value shr shift) and 0xffL).toString() }

    private fun cleanSsid(value: String?): String = value
        ?.removeSurrounding("\"")
        ?.takeUnless { it == WifiManager.UNKNOWN_SSID || it.equals("<unknown ssid>", ignoreCase = true) }
        .orEmpty()
}

private class NetworkToolsInputException(message: String) : IllegalArgumentException(message)
