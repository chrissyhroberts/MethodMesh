package com.example.methodmesh.modules.networktools

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import org.json.JSONObject

object NetworkToolsAndroid {
    fun wifiInfo(context: Context): JSONObject = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return JSONObject().apply { put("available", false); put("reason", "No active network.") }
        val caps = cm.getNetworkCapabilities(network) ?: return JSONObject().apply { put("available", false); put("reason", "No network capabilities.") }
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return JSONObject().apply { put("available", false); put("reason", "Active network is not Wi-Fi.") }
        val wifi = caps.transportInfo as? WifiInfo
        val link = cm.getLinkProperties(network)
        JSONObject().apply {
            put("available", wifi != null)
            put("rssi_dbm", wifi?.rssi ?: 0)
            put("link_speed_mbps", wifi?.linkSpeed ?: 0)
            put("frequency_mhz", wifi?.frequency ?: 0)
            put("ssid", wifi?.ssid.orEmpty())
            put("bssid", wifi?.bssid.orEmpty())
            put("interface_name", link?.interfaceName.orEmpty())
            put("addresses", org.json.JSONArray(link?.linkAddresses?.map { it.address.hostAddress } ?: emptyList<String>()))
            put("dns_servers", org.json.JSONArray(link?.dnsServers?.mapNotNull { it.hostAddress } ?: emptyList<String>()))
        }
    }.getOrElse { JSONObject().apply { put("available", false); put("reason", it.message ?: "Wi-Fi information unavailable.") } }
}
