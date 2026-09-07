package com.example.methodmesh.modules.networktools

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object NetworkToolsModule : MethodMeshModule {
    override val moduleId = "networktools"
    override val displayName = "Network tools"
    override val summary = "Interface info, DNS, reachability, TCP endpoint tests, traceroute, IPv4 CIDR and Wi-Fi information."
    override val iconKey = "network"
    override fun as100Methods() = listOf(As100NetworkToolsMethod)
    override fun rilBindings() = listOf(RilBinding("test network endpoint", As100NetworkToolsMethod.ID, "Run a bounded network diagnostic"))
    override fun capabilityScreens() = listOf(NetworkToolsCapabilityScreen)
    override fun capabilitySettings() = mapOf(
        As100NetworkToolsMethod.ID to listOf(
            MethodSetting.ChoiceSetting("operation", "Operation", defaultValue = "interface_info", choices = listOf("interface_info", "dns_lookup", "ping", "tcp_test", "traceroute", "cidr", "wifi_info")),
            MethodSetting.TextSetting("host", "Host", defaultValue = ""),
            MethodSetting.IntSetting("port", "TCP port", defaultValue = 443, minimum = 1, maximum = 65535),
            MethodSetting.IntSetting("timeout_ms", "Timeout (ms)", defaultValue = 3000, minimum = 100, maximum = 30000),
            MethodSetting.TextSetting("cidr", "IPv4 CIDR", defaultValue = "192.168.1.0/24")
        )
    )
}
