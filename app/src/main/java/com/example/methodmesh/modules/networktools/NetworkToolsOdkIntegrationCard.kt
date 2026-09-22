package com.example.methodmesh.modules.networktools

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private const val NETWORK_TOOLS_ODK_INTENT =
    "com.example.methodmesh.EXECUTE_METHOD(method_id='network.tools',input_operation='dns_lookup',input_host='example.org',input_timeout_ms='3000',input_payload_mode='FULL',return_mode='flat')"

/**
 * Module-owned projection of the canonical network.tools ODK contract.
 * Keep this aligned with NetworkToolsMethod, NetworkToolsModule and the showcase workbook.
 */
@Composable
internal fun NetworkToolsOdkIntegrationCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("ODK INTEGRATION", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            CardHeading("Capability")
            Text("Network tools")
            NetworkCopyValue("canonical method ID", As100NetworkToolsMethod.ID)

            CardHeading("Tags")
            Text("Maturity: Development")
            Text("Connectivity: Online/Offline")

            CardHeading("ODK INPUTS")
            ContractLines(
                listOf(
                    "operation | select_one/text | optional (default interface_info) | operation: ${NetworkOperation.entries.joinToString { it.id }}",
                    "host | text | required for DNS/reachability/TCP/traceroute | one host name or IP only",
                    "port | integer | required for tcp_test | one TCP port, 1–65535",
                    "timeout_ms | integer | optional, default 3000 | bounded 100–30000 ms",
                    "cidr | text | required for cidr | IPv4 address/prefix",
                    "traceroute_max_hops | integer | optional, default 12 | bounded 1–30 hops"
                )
            )
            Text(
                "Interactive acquisition: native dashboard can capture current connection and nearby Wi-Fi; ODK may invoke those same operations directly. Wi-Fi scan returns an unavailable diagnostic if Android permissions/state do not allow acquisition.",
                style = MaterialTheme.typography.bodySmall
            )

            CardHeading("INTENT CALL")
            NetworkCopyValue("canonical ODK intent", NETWORK_TOOLS_ODK_INTENT, Modifier.fillMaxWidth())

            CardHeading("MODIFIERS")
            ContractLines(
                listOf(
                    "input_payload_mode | text | FULL in showcase | asks shared transport for full audit JSON",
                    "return_mode | text | flat in showcase | projects canonical return keys into group children"
                )
            )

            CardHeading("CANONICAL RETURNS")
            ContractLines(
                listOf(
                    "methodmesh_status | text | always | shared MethodMesh execution status",
                    "network_value | text | always on handled result | primary useful result",
                    "network_summary | text | always on handled result | concise human summary",
                    "network_result_json | text/JSON | always on handled result | operation-specific structured result",
                    "network_latency_ms | text/integer | conditional | elapsed diagnostic time",
                    "network_host | text | conditional | tested host",
                    "network_ip | text | conditional | primary/resolved IP",
                    "network_port | text/integer | conditional | tested TCP port",
                    "network_reachable | text/boolean | conditional | reachability outcome",
                    "network_tcp_open | text/boolean | conditional | TCP endpoint outcome",
                    "network_interface | text | conditional | active/primary interface",
                    "network_cidr | text | conditional | normalized IPv4 CIDR",
                    "network_detail | text | conditional | useful secondary detail",
                    "network_status | text | always | succeeded / failed / unavailable",
                    "network_operation | text | always | executed operation",
                    "network_captured_time_iso | text | always | capture timestamp",
                    "network_error | text | conditional | capability error message",
                    "methodmesh_full_json | text/JSON | always | metadata/audit payload"
                )
            )

            CardHeading("RETURN FIELD PLACEMENT")
            Text(
                "Each canonical key maps to the identically named leaf inside the MethodMesh intent group. Canonical example: unprefixed return keys, one MethodMesh call, no return namespace.",
                style = MaterialTheme.typography.bodySmall
            )

            CardHeading("FILE RETURN SEMANTICS")
            Text("None. Network tools returns scalar/text/JSON values and no ODK attachment.", style = MaterialTheme.typography.bodySmall)

            CardHeading("RUNTIME")
            ContractLines(
                listOf(
                    "Inputs: operation plus only the fields required by that operation",
                    "Beef: network_value, with operation-specific scalar fields where relevant",
                    "Metadata: methodmesh_full_json is always captured by the canonical ODK showcase and remains secondary/optional in native use"
                )
            )
        }
    }
}

@Composable
private fun CardHeading(text: String) {
    Spacer(Modifier.height(10.dp))
    HorizontalDivider(Modifier.padding(bottom = 8.dp))
    Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun ContractLines(lines: List<String>) {
    lines.forEach { line ->
        Text(
            line,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(vertical = 2.dp)
        )
    }
}
