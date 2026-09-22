package com.example.methodmesh.modules.networktools

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object NetworkToolsModule : MethodMeshModule {
    override val moduleId = "networktools"
    override val displayName = "Network tools"
    override val summary = "Live network dashboard with connection status, nearby Wi-Fi, bounded one-host diagnostics and IPv4 CIDR tools."
    override val iconKey = "tool"

    override fun as100Methods() = listOf(As100NetworkToolsMethod)

    override fun rilBindings() = listOf(
        RilBinding("network tools", As100NetworkToolsMethod.ID, "Run a bounded network diagnostic"),
        RilBinding("network status", As100NetworkToolsMethod.ID, "Capture current active-network status"),
        RilBinding("nearby wifi", As100NetworkToolsMethod.ID, "Capture the latest nearby Wi-Fi scan environment"),
        RilBinding("dns lookup", As100NetworkToolsMethod.ID, "Resolve one host name or address"),
        RilBinding("test tcp endpoint", As100NetworkToolsMethod.ID, "Test one TCP host and port"),
        RilBinding("calculate cidr", As100NetworkToolsMethod.ID, "Calculate an IPv4 CIDR range")
    )

    override fun capabilityScreens() = listOf(NetworkToolsCapabilityScreen)

    override fun capabilitySettings() = mapOf(
        As100NetworkToolsMethod.ID to listOf(
            MethodSetting.ChoiceSetting(
                "operation",
                "Operation",
                defaultValue = NetworkOperation.INTERFACE_INFO.id,
                choices = NetworkOperation.entries.map { it.id }
            ),
            MethodSetting.TextSetting("host", "Host", defaultValue = ""),
            MethodSetting.IntSetting("port", "TCP port", defaultValue = 443, minimum = 1, maximum = 65535),
            MethodSetting.IntSetting("timeout_ms", "Timeout (ms)", defaultValue = 3000, minimum = 100, maximum = 30000),
            MethodSetting.TextSetting("cidr", "IPv4 CIDR", defaultValue = ""),
            MethodSetting.IntSetting("traceroute_max_hops", "Traceroute max hops", defaultValue = 12, minimum = 1, maximum = 30)
        )
    )
}
