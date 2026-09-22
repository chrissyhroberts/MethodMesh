package com.example.methodmesh.modules.networktools

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build

internal data class NetworkSnapshot(
    val stateLabel: String,
    val transportLabel: String,
    val internetCapable: Boolean,
    val validated: Boolean,
    val captivePortal: Boolean,
    val metered: Boolean,
    val interfaceName: String,
    val localAddresses: List<String>,
    val gateway: String,
    val dnsServers: List<String>,
    val ssid: String,
    val bssid: String,
    val rssi: Int?,
    val frequencyMhz: Int?,
    val linkSpeedMbps: Int?,
    val wifiStandard: String,
    val wifiPermissionNote: String?
)

internal data class WifiNetworkRow(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val frequencyMhz: Int,
    val channel: Int?,
    val band: String,
    val security: String,
    val accessPointCount: Int = 1,
    val connected: Boolean = false
)

internal data class WifiEnvironmentSnapshot(
    val networks: List<WifiNetworkRow>,
    val capturedAtMs: Long,
    val scanStarted: Boolean,
    val message: String? = null,
    val missingManifestPermissions: List<String> = emptyList(),
    val needsLocationPermission: Boolean = false
)

internal object NetworkSnapshotRepository {
    private val wifiManifestPermissions = listOf(
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.CHANGE_WIFI_STATE
    )

    /**
     * Dashboard-facing snapshot. This boundary is deliberately no-throw: a vendor Wi-Fi
     * implementation, redacted WifiInfo getter, or missing permission must degrade to a
     * visible unavailable state rather than crash Compose while the capability is opening.
     */
    fun current(context: Context): NetworkSnapshot = runCatching {
        currentUnsafe(context.applicationContext)
    }.getOrElse { error ->
        NetworkSnapshot(
            stateLabel = "Network state unavailable",
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
            wifiPermissionNote = error.message ?: error.javaClass.simpleName
        )
    }

    private fun currentUnsafe(app: Context): NetworkSnapshot {
        val connectivity = app.getSystemService(ConnectivityManager::class.java)
        val active = connectivity?.activeNetwork
        val caps = active?.let { network -> runCatching { connectivity.getNetworkCapabilities(network) }.getOrNull() }
        val link = active?.let { network -> runCatching { connectivity.getLinkProperties(network) }.getOrNull() }

        val transport = when {
            caps == null -> "None"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth"
            else -> "Other"
        }
        val internetCapable = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        val captivePortal = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) == true
        val metered = caps != null && !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        val stateLabel = when {
            active == null -> "No active network"
            captivePortal -> "Captive portal"
            validated -> "Online"
            internetCapable -> "Connected · internet not validated"
            else -> "Local network only"
        }

        val localAddresses = link?.linkAddresses
            ?.mapNotNull { runCatching { it.address.hostAddress?.substringBefore('%') }.getOrNull() }
            ?.distinct()
            .orEmpty()
        val gateway = link?.routes
            ?.firstOrNull { route -> runCatching { route.isDefaultRoute && route.gateway != null }.getOrDefault(false) }
            ?.gateway?.hostAddress?.substringBefore('%').orEmpty()
        val dns = link?.dnsServers
            ?.mapNotNull { server -> runCatching { server.hostAddress?.substringBefore('%') }.getOrNull() }
            ?.distinct()
            .orEmpty()

        val manifestMissing = missingManifestPermissions(app, listOf(Manifest.permission.ACCESS_WIFI_STATE))
        val locationGranted = app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val wifiPermissionNote = when {
            transport != "Wi-Fi" && caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true -> null
            manifestMissing.isNotEmpty() -> "Wi-Fi identifiers are limited because ACCESS_WIFI_STATE is not declared."
            !locationGranted -> "Android may redact SSID/BSSID until location permission is granted."
            else -> null
        }

        val info = wifiInfo(caps, app, manifestMissing.isEmpty())
        return NetworkSnapshot(
            stateLabel = stateLabel,
            transportLabel = transport,
            internetCapable = internetCapable,
            validated = validated,
            captivePortal = captivePortal,
            metered = metered,
            interfaceName = link?.interfaceName.orEmpty(),
            localAddresses = localAddresses,
            gateway = gateway,
            dnsServers = dns,
            ssid = info.safeSsid(),
            bssid = info.safeBssid(),
            rssi = info.safeInt { rssi }?.takeIf { it in -126..0 },
            frequencyMhz = info.safeInt { frequency }?.takeIf { it > 0 },
            linkSpeedMbps = info.safeInt { linkSpeed }?.takeIf { it > 0 },
            wifiStandard = wifiStandardLabel(info),
            wifiPermissionNote = wifiPermissionNote
        )
    }

    /**
     * Returns the latest scan environment. The operation is also no-throw because Wi-Fi scan
     * permissions and OEM behaviour vary substantially across Android releases.
     */
    fun wifiEnvironment(context: Context, requestFreshScan: Boolean): WifiEnvironmentSnapshot = runCatching {
        wifiEnvironmentUnsafe(context.applicationContext, requestFreshScan)
    }.getOrElse { error ->
        WifiEnvironmentSnapshot(
            networks = emptyList(),
            capturedAtMs = System.currentTimeMillis(),
            scanStarted = false,
            message = "Nearby Wi-Fi is unavailable: ${error.message ?: error.javaClass.simpleName}"
        )
    }

    @Suppress("DEPRECATION")
    private fun wifiEnvironmentUnsafe(app: Context, requestFreshScan: Boolean): WifiEnvironmentSnapshot {
        val missing = missingManifestPermissions(app, wifiManifestPermissions)
        val locationGranted = app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (missing.isNotEmpty()) {
            return WifiEnvironmentSnapshot(
                networks = emptyList(),
                capturedAtMs = System.currentTimeMillis(),
                scanStarted = false,
                message = "Nearby Wi-Fi requires ${missing.joinToString()} in the app manifest.",
                missingManifestPermissions = missing,
                needsLocationPermission = !locationGranted
            )
        }
        if (!locationGranted) {
            return WifiEnvironmentSnapshot(
                networks = emptyList(),
                capturedAtMs = System.currentTimeMillis(),
                scanStarted = false,
                message = "Grant location permission to let Android expose nearby Wi-Fi scan results.",
                needsLocationPermission = true
            )
        }

        val wifi = app.getSystemService(WifiManager::class.java)
            ?: return WifiEnvironmentSnapshot(emptyList(), System.currentTimeMillis(), false, "Wi-Fi service is unavailable.")
        val scanStarted = if (requestFreshScan) runCatching { wifi.startScan() }.getOrDefault(false) else false
        val connectedBssid = runCatching {
            val connectivity = app.getSystemService(ConnectivityManager::class.java)
            val caps = connectivity?.activeNetwork?.let { connectivity.getNetworkCapabilities(it) }
            wifiInfo(caps, app, true).safeBssid()
        }.getOrDefault("")

        val results = runCatching { wifi.scanResults.orEmpty() }
            .getOrElse { error ->
                return WifiEnvironmentSnapshot(
                    emptyList(),
                    System.currentTimeMillis(),
                    scanStarted,
                    "Android did not expose Wi-Fi scan results: ${error.message ?: error.javaClass.simpleName}"
                )
            }

        val grouped = results
            .mapNotNull { row ->
                val ssid = row.SSID.cleanedSsid()
                ssid.takeIf { it.isNotBlank() }?.let { it to row }
            }
            .groupBy(keySelector = { it.first }, valueTransform = { it.second })
            .map { (ssid, rows) ->
                val strongest = rows.maxByOrNull(ScanResult::level) ?: rows.first()
                WifiNetworkRow(
                    ssid = ssid,
                    bssid = strongest.BSSID.cleanedBssid(),
                    rssi = strongest.level,
                    frequencyMhz = strongest.frequency,
                    channel = wifiChannel(strongest.frequency),
                    band = wifiBand(strongest.frequency),
                    security = wifiSecurity(strongest.capabilities.orEmpty()),
                    accessPointCount = rows.size,
                    connected = connectedBssid.isNotBlank() && rows.any {
                        it.BSSID.cleanedBssid().equals(connectedBssid, ignoreCase = true)
                    }
                )
            }
            .sortedByDescending { it.rssi }

        return WifiEnvironmentSnapshot(
            networks = grouped,
            capturedAtMs = System.currentTimeMillis(),
            scanStarted = scanStarted,
            message = if (requestFreshScan && !scanStarted) {
                "Android declined a fresh scan (often throttling, Wi-Fi/location state, or device policy); showing the latest cached results."
            } else null
        )
    }

    @Suppress("DEPRECATION")
    fun missingManifestPermissions(context: Context, permissions: List<String>): List<String> {
        val requested = runCatching {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
                .requestedPermissions?.toSet().orEmpty()
        }.getOrDefault(emptySet())
        return permissions.filterNot { it in requested }.map { it.substringAfterLast('.') }
    }

    private fun wifiInfo(caps: NetworkCapabilities?, context: Context, allowLegacy: Boolean): WifiInfo? {
        val fromCapabilities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { caps?.transportInfo as? WifiInfo }.getOrNull()
        } else null
        if (fromCapabilities != null) return fromCapabilities
        if (!allowLegacy) return null
        @Suppress("DEPRECATION")
        return runCatching { context.getSystemService(WifiManager::class.java)?.connectionInfo }.getOrNull()
    }

    private fun WifiInfo?.safeSsid(): String = runCatching { this?.ssid.cleanedSsid() }.getOrDefault("")
    private fun WifiInfo?.safeBssid(): String = runCatching { this?.bssid.cleanedBssid() }.getOrDefault("")
    private inline fun WifiInfo?.safeInt(getter: WifiInfo.() -> Int): Int? {
        val info = this ?: return null
        return runCatching { info.getter() }.getOrNull()
    }

    private fun String?.cleanedSsid(): String = this
        ?.removePrefix("\"")
        ?.removeSuffix("\"")
        ?.takeUnless { it.equals(WifiManager.UNKNOWN_SSID, ignoreCase = true) }
        ?.takeUnless { it.equals("<unknown ssid>", ignoreCase = true) }
        .orEmpty()

    private fun String?.cleanedBssid(): String = this
        ?.takeUnless { it == "02:00:00:00:00:00" }
        .orEmpty()

    private fun wifiStandardLabel(info: WifiInfo?): String {
        if (info == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return ""
        val standard = runCatching { info.wifiStandard }.getOrNull() ?: return ""
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && standard == ScanResult.WIFI_STANDARD_11BE) {
            return "Wi-Fi 7"
        }
        return when (standard) {
            ScanResult.WIFI_STANDARD_LEGACY -> "Legacy"
            ScanResult.WIFI_STANDARD_11N -> "Wi-Fi 4"
            ScanResult.WIFI_STANDARD_11AC -> "Wi-Fi 5"
            ScanResult.WIFI_STANDARD_11AX -> "Wi-Fi 6"
            ScanResult.WIFI_STANDARD_11AD -> "WiGig"
            else -> ""
        }
    }

    internal fun wifiBand(frequency: Int): String = when (frequency) {
        in 2400..2500 -> "2.4 GHz"
        in 4900..5900 -> "5 GHz"
        in 5925..7125 -> "6 GHz"
        else -> "${frequency} MHz"
    }

    internal fun wifiChannel(frequency: Int): Int? = when {
        frequency == 2484 -> 14
        frequency in 2412..2472 -> (frequency - 2407) / 5
        frequency in 5000..5895 -> (frequency - 5000) / 5
        frequency in 5955..7115 -> (frequency - 5950) / 5
        else -> null
    }

    private fun wifiSecurity(capabilities: String): String {
        val upper = capabilities.uppercase()
        return when {
            "WPA3" in upper || "SAE" in upper -> "WPA3"
            "OWE" in upper -> "Enhanced open"
            "WPA2" in upper && "EAP" in upper -> "WPA2 Enterprise"
            "WPA2" in upper || "RSN" in upper -> "WPA2"
            "WPA" in upper && "EAP" in upper -> "WPA Enterprise"
            "WPA" in upper -> "WPA"
            "WEP" in upper -> "WEP"
            else -> "Open"
        }
    }
}
