# Network tools

Method ID: `network.tools`<br>
Module ID: `networktools`<br>
Version: **0.3.0**<br>
Maturity: **Development**<br>
Connectivity: **Online/Offline**<br>
Icon key: `tool`

## Purpose

Network Tools is a bounded field network-diagnostics capability with a live native dashboard and one stable external contract. It answers practical questions such as:

- what network is this device using now?;
- does Android consider the network validated, metered or captive?;
- what local addresses, gateway and DNS servers are in use?;
- what Wi-Fi networks are visible nearby?;
- does one named host resolve or appear reachable?;
- can one explicitly selected TCP endpoint be reached?;
- what does a bounded traceroute show?;
- what range does an IPv4 CIDR describe?

It deliberately does **not** implement LAN sweeps, automatic host inventories, port-range scanning, service fingerprinting, vulnerability probing or packet capture.

## Canonical capability contract

`network.tools` remains the single stable capability/method ID. The `operation` input selects one of nine declared modes:

| Operation | Meaning | Connectivity |
|---|---|---|
| `connection_status` | Capture current Android active-network state | Offline/local |
| `interface_info` | Enumerate local network interfaces/addresses | Offline/local |
| `dns_lookup` | Resolve one host | Network path required |
| `ping` | Bounded Java/Android reachability probe | Network path required |
| `tcp_test` | Test one explicit TCP host/port | Network path required |
| `traceroute` | Best-effort bounded traceroute | Network path required |
| `cidr` | Calculate one IPv4 CIDR locally | Offline |
| `wifi_info` | Capture active Wi-Fi/link information | Offline/local |
| `wifi_scan` | Capture latest nearby Wi-Fi environment | Offline/local radio + Android permission/state |

These are modes of the same canonical capability, not dashboard-only private actions. Every mode can be selected in a preset/protocol and supplied through ODK using `input_operation`.

## Native dashboard

Direct native use opens a live dashboard rather than an operation-form/result-page sequence.

The dashboard contains:

1. **Current connection** — transport, Android validation/captive-portal state, metering, interface, local addresses, gateway, DNS and Wi-Fi metrics where available.
2. **Nearby Wi-Fi** — visible network names, signal, band/channel, security and AP count, with explicit refresh because Android throttles scans.
3. **Host diagnostics** — DNS, reachability, one TCP endpoint and traceroute against one explicitly entered host.
4. **IPv4 CIDR calculator** — local calculation.

Live connection/Wi-Fi context is presentation state. Pressing **Capture connection status** or **Capture nearby Wi-Fi list** invokes the same canonical `network.tools` operations exposed to presets/protocols/ODK.

All meaningful displayed scalar/text results are tap-to-copy.

### Working result → Commit

Native execution follows the MethodMesh lifecycle:

```text
configure/interact -> live working result -> Commit -> copy/share/save/Done
```

Running a diagnostic updates the **working result** in place. It does not navigate to a generic result page.

**Commit** freezes both:

- the exact result payload; and
- the settings that produced it.

Changing the host, port, CIDR or operation afterwards does not mutate the committed result. Copy/share/save/Done act on the committed payload only.

## Presets, protocols and schedules

Settings are declared through `MethodSetting`:

| Setting | Type | Range/meaning |
|---|---|---|
| `operation` | choice | one of the nine operations above |
| `host` | text | one host name or IP; no URL/shell syntax |
| `port` | integer | 1–65535 |
| `timeout_ms` | integer | 100–30000 ms |
| `cidr` | text | IPv4 CIDR |
| `traceroute_max_hops` | integer | 1–30 |

Fixed preset values are hidden during a native preset run. Declared runtime fields remain visible. A fully fixed preset may start automatically. Protocol/preset closeout uses the normal MethodMesh result envelope.

Android-context operations (`connection_status`, `wifi_info`, `wifi_scan`) require an Android capability context. A headless execution path that lacks one returns a clean `unavailable` diagnostic rather than inventing data or crashing.

## Result contract

### Beef

`network_value` is the primary native result.

Examples include a resolved IP, `reachable`, `open`, a normalized CIDR/range, current connection label, current SSID/IP, or nearby network count.

### Declared outputs

- `network_value`
- `network_summary`
- `network_result_json`
- `network_latency_ms`
- `network_host`
- `network_ip`
- `network_port`
- `network_reachable`
- `network_tcp_open`
- `network_interface`
- `network_cidr`
- `network_detail`
- `network_status`
- `network_operation`
- `network_captured_time_iso`
- `network_error`

The shared transport additionally projects `methodmesh_status` and, for full payload requests, `methodmesh_full_json`.

`network_result_json` is operation-specific structured detail. It is not a LAN inventory.

## Operation notes

### DNS

Uses `InetAddress.getAllByName` in a bounded executor. Platform DNS itself is not guaranteed to honour interruption, so the MethodMesh caller is timed out and released even if an underlying resolver thread is slow.

### Reachability (`ping`)

Uses `InetAddress.isReachable`. The UI deliberately calls this **reachability** because Android/Java does not guarantee ICMP echo semantics.

### TCP endpoint

Attempts exactly one host/port connection. Port-range syntax is not accepted.

### Traceroute

Best effort only. The runner tries Android/toybox traceroute executables with `ProcessBuilder` argument arrays, never a shell command string. Execution is bounded by `timeout_ms` and `traceroute_max_hops`. Output is drained concurrently to avoid process-pipe deadlock and retained text is capped.

If no traceroute applet is available, the operation returns `unavailable`.

### CIDR

Pure local IPv4 calculation. `/31` is treated as two point-to-point addresses and `/32` as one host address. IPv6 CIDR is not implemented in v0.3.0.

### Wi-Fi

Android can redact or withhold Wi-Fi identifiers and scan results depending on permission, location state, OS version, device policy and scan throttling. The repository boundary is deliberately **no-throw**: these states become explicit `unavailable`/informational results rather than capability crashes.

## Android permissions

MethodMesh already declares `INTERNET`, `ACCESS_NETWORK_STATE` and location permissions. Nearby Wi-Fi scanning additionally requires app-manifest integration for:

```xml
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
```

See `MANIFEST_INTEGRATION.md`.

The dashboard requests `ACCESS_FINE_LOCATION` at runtime when needed for Wi-Fi scan results. Android may still return cached results or decline a fresh scan because of throttling or system/location state.

## ODK Integration Card

```text
ODK INTEGRATION

Capability
Network tools
network.tools

Tags
Maturity: Development
Connectivity: Online/Offline

ODK INPUTS
operation | select_one/text | optional (default interface_info) | connection_status, interface_info, dns_lookup, ping, tcp_test, traceroute, cidr, wifi_info, wifi_scan
host | text | required for DNS/reachability/TCP/traceroute | one host name or IP only
port | integer | required for tcp_test | one TCP port, 1-65535
timeout_ms | integer | optional, default 3000 | bounded 100-30000 ms
cidr | text | required for cidr | IPv4 address/prefix
traceroute_max_hops | integer | optional, default 12 | bounded 1-30 hops
Interactive acquisition:
Native dashboard may acquire current connection/nearby Wi-Fi. ODK invokes the same operations directly; unavailable Android permission/state returns a diagnostic.

INTENT CALL
com.example.methodmesh.EXECUTE_METHOD(method_id='network.tools',input_operation='dns_lookup',input_host='example.org',input_timeout_ms='3000',input_payload_mode='FULL',return_mode='flat')

MODIFIERS
input_payload_mode | text | FULL in showcase | shared transport full/audit projection
return_mode | text | flat in showcase | canonical flat return projection

CANONICAL RETURNS
methodmesh_status | text | always | shared MethodMesh execution status
network_value | text | always on handled result | primary useful result
network_summary | text | always on handled result | concise human summary
network_result_json | text/JSON | always on handled result | operation-specific structured result
network_latency_ms | integer/text | conditional | diagnostic elapsed time
network_host | text | conditional | tested host
network_ip | text | conditional | primary/resolved IP
network_port | integer/text | conditional | tested TCP port
network_reachable | boolean/text | conditional | reachability outcome
network_tcp_open | boolean/text | conditional | TCP endpoint outcome
network_interface | text | conditional | active/primary interface
network_cidr | text | conditional | normalized IPv4 CIDR
network_detail | text | conditional | useful secondary detail
network_status | text | always | succeeded / failed / unavailable
network_operation | text | always | executed operation
network_captured_time_iso | text | always | capture timestamp
network_error | text | conditional | capability error
methodmesh_full_json | text/JSON | always | metadata/audit payload

RETURN FIELD PLACEMENT
Each canonical key -> identically named child leaf in the MethodMesh intent group
Canonical example: unprefixed return keys, one MethodMesh call, no return namespace

FILE RETURN SEMANTICS
None. No file/media attachment is produced.

RUNTIME
Inputs: operation plus operation-relevant inputs
Beef: network_value plus relevant scalar outputs
Metadata: methodmesh_full_json available secondarily; canonical ODK showcase always captures it
```

The same card is available from the native capability UI under **ODK integration**.

## Canonical XLSForm

`docs/example_odk_showcase_network_tools.xlsx`

The workbook contains exactly one MethodMesh invocation, uses unprefixed canonical return keys, includes `methodmesh_full_json`, and demonstrates all nine operation choices through the same `network.tools` call.

## Privacy and external data

The module sends only the explicitly supplied host to DNS/network routing infrastructure required by the selected host diagnostic. It does not upload nearby Wi-Fi lists, local interface data or CIDR calculations to a MethodMesh server.

Nearby SSID/BSSID display is local Android device information. No credentials or secrets are embedded in results.

## Validation

See `VALIDATION.md` and `REVIEW_REPORT_v0.3.0.md`.

The module remains **Development** until the current MethodMesh checkout passes Gradle build/tests plus physical-device and ODK Collect checks.
