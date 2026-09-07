# Network tools

Status: **Development**  
Method ID: `network.tools`  
Module ID: `networktools`  
Icon key: `tool`

## Purpose

`network.tools` provides small, bounded network diagnostics that are useful on a phone in the field without turning MethodMesh into a network scanner.

Supported operations:

- `interface_info` — inspect local network interfaces and addresses;
- `dns_lookup` — resolve one named host or IP;
- `ping` — bounded Java/Android reachability probe;
- `tcp_test` — test one explicitly supplied TCP host and port;
- `traceroute` — best-effort bounded traceroute when the Android build exposes a traceroute/toybox applet;
- `cidr` — calculate an IPv4 CIDR range locally;
- `wifi_info` — inspect the active Wi-Fi connection where Android exposes the information.

The module deliberately does **not** provide:

- LAN/subnet discovery;
- broad host enumeration;
- port-range scanning;
- service fingerprinting;
- packet capture;
- passive traffic inspection.

That boundary is intentional.

## Development-lane handoff

This package is a source handoff. It should first be reviewed under:

```text
incoming_capability_prototypes/networktools/
```

before admission to:

```text
app/src/main/java/com/example/methodmesh/modules/networktools/
```

The capability remains `Development` until the Android build, native UX, preset behaviour, ODK example, orientation behaviour and result actions have been exercised in the real repository.

## Native UX

The native screen is one compact diagnostic surface:

1. choose the operation;
2. enter only the fields relevant to that operation;
3. press **Run diagnostic**;
4. receive one primary `network_value` result;
5. copy/share/save the primary result through the shared MethodMesh result scaffold;
6. use full JSON only when explicitly requested.

Operation-specific controls are hidden when irrelevant. Fixed preset values are hidden during native preset runs via `CapabilityScreenContext.settingShouldBeShown(...)`.

The screen does not automatically run a normal dashboard diagnostic. External intent calls and fully fixed native presets can start immediately. Native presets with runtime fields wait for those runtime values.

### Beef-first result

The original concept exposed `network_summary` as a core value. The current shared `OutputFormatter` intentionally filters fields containing `summary` from CORE presentation. To preserve the MethodMesh “beef first” rule without adding a capability-specific shared-UI exception, this module adds:

- `network_value` — the single primary native result.

Examples:

- DNS: first resolved IP;
- reachability: `reachable` / `unreachable`;
- TCP endpoint: `open` / `closed`;
- CIDR: normalized network/range;
- interface info: primary local IP where available;
- Wi-Fi info: SSID, otherwise local IP, otherwise connection state;
- traceroute: hop count.

`network_summary` and the structured result remain available for ODK/audit use.

## Preset behaviour

Declared settings:

| Setting | Type | Purpose |
|---|---|---|
| `operation` | choice | one of the seven bounded operations |
| `host` | text | one host name, IPv4 or IPv6 literal |
| `port` | integer | one TCP port, 1–65535 |
| `timeout_ms` | integer | bounded 100–30,000 ms |
| `cidr` | text | IPv4 CIDR calculation input |
| `traceroute_max_hops` | integer | bounded 1–30 hops |

Preset authors may mark values fixed or runtime in the normal MethodMesh preset flow. The module does not invent its own preset storage.

## Android intent / ODK

Intent action:

```text
com.example.methodmesh.EXECUTE_METHOD
```

DNS example:

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='network.tools',input_operation='dns_lookup',input_host='example.org',input_timeout_ms='3000',input_payload_mode='FULL',return_mode='flat')
```

TCP example:

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='network.tools',input_operation='tcp_test',input_host='example.org',input_port='443',input_timeout_ms='3000',input_payload_mode='FULL',return_mode='flat')
```

CIDR example:

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='network.tools',input_operation='cidr',input_cidr='192.168.10.0/24',input_payload_mode='FULL',return_mode='flat')
```

ODK calls should place the intent on a **group**, with group children used as return fields. Input question names in the supplied workbook are deliberately different from output field names so blank return placeholders cannot overwrite request parameters.

The supplied form:

```text
docs/example_odk_network.tools.xlsx
```

lets an operator select an operation, supplies only the relevant input values, and receives the normal core/audit outputs plus `methodmesh_full_json`.

## Inputs

The runner accepts either canonical keys or Android-intent-style `input_*` keys.

- `operation` / `input_operation`
- `host` / `input_host`
- `port` / `input_port`
- `timeout_ms` / `input_timeout_ms`
- `cidr` / `input_cidr`
- `traceroute_max_hops` / `input_traceroute_max_hops`

Host input is treated as a host name or IP literal, **not** as a URL or shell command. Whitespace, URL paths and URL punctuation are rejected. Traceroute uses `ProcessBuilder` argument arrays rather than a shell command string.

## Outputs

### Primary/core result

- `network_value` — the principal useful result for native copy/share.

### Structured/core fields

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

`network_result_json` describes only the selected diagnostic. It is not a LAN inventory.

### Audit/error fields

- `network_status` — `succeeded`, `failed` or `unavailable`;
- `network_operation`;
- `network_captured_time_iso`;
- `network_error`.

When `input_payload_mode='FULL'`, the shared transport also returns `methodmesh_full_json`.

## Operation semantics

### `interface_info`

Uses `java.net.NetworkInterface` and returns local interface/address information. The primary value is the first active non-loopback address found.

No network request is made.

### `dns_lookup`

Resolves exactly one host with `InetAddress.getAllByName`. The caller is bounded by `timeout_ms`; the resolver work runs on a daemon executor so the MethodMesh flow is not left waiting indefinitely if the platform resolver ignores interruption.

An unresolved or timed-out host is a valid diagnostic outcome rather than an application crash.

### `ping`

Uses `InetAddress.isReachable(timeout)`. Android implementations vary and this is **not described as guaranteed ICMP echo**. The module calls it a reachability probe in the UI and metadata.

### `tcp_test`

Resolves one host and attempts one TCP connection to one supplied port. No port ranges are accepted. Connection refused, timeout and similar endpoint outcomes return `network_tcp_open=false` with a detail string; malformed capability input returns failure.

### `traceroute`

Best-effort only. The runner tries Android/toybox traceroute executables without invoking a shell. It is bounded by:

- `timeout_ms` wall-clock limit;
- `traceroute_max_hops`, 1–30;
- a capped captured-text length.

If the device has no traceroute applet, the operation returns `unavailable` explicitly.

### `cidr`

Pure local IPv4 calculation. Returns:

- normalized CIDR;
- netmask;
- wildcard mask;
- network address;
- broadcast address;
- first/last usable address;
- total addresses;
- usable host count.

`/31` is treated as two usable point-to-point addresses; `/32` as one usable host address.

IPv6 CIDR calculation is not included in v0.1.0.

### `wifi_info`

Uses Android `ConnectivityManager`, `NetworkCapabilities`, `LinkProperties` and `WifiInfo` where available. The operation can return:

- SSID/BSSID where Android exposes them;
- local IP;
- gateway;
- DNS servers;
- RSSI;
- link speed;
- frequency;
- metered state;
- internet/validated capability state.

Android may redact SSID/BSSID under its privacy and permission rules. The module reports this as unavailable/redacted information rather than failing the app.

The current application manifest already provides `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION`. It does **not** currently declare `ACCESS_WIFI_STATE`. This module therefore does not promise SSID/BSSID on all Android builds. If MethodMesh later decides those values must be guaranteed where the OS permits, `ACCESS_WIFI_STATE` should be reviewed as a generic app-platform permission rather than hidden inside capability-specific UI code.

## Offline / online behaviour

- `cidr` — fully offline.
- `interface_info` — local only.
- `wifi_info` — local Android state only.
- `dns_lookup`, `ping`, `tcp_test`, `traceroute` — require a functioning network path and may contact the supplied endpoint or infrastructure needed to resolve/reach it.

The capability does not contact a MethodMesh-owned remote service and does not disclose GPS/location coordinates.

## Storage

The module has no repository or persistent store. It does not auto-save diagnostic results. Native save/share is handled only when the user invokes the shared MethodMesh result actions. ODK owns ODK submission persistence.

## Permissions and privacy

Network diagnostic targets can themselves be sensitive operational information. They are returned to the caller but are not stored by the module.

No credentials are accepted. No API keys are embedded. No packet contents are captured.

## Dependencies / attribution

No third-party network library or external data provider is introduced. The implementation uses Android platform APIs, Java/Kotlin networking classes and `org.json`, already available in the app/runtime.

There is therefore no new third-party attribution requirement for v0.1.0.

## Known limitations

- Android/Java reachability behaviour varies by device/network.
- DNS timeout bounds the MethodMesh caller but cannot guarantee cancellation of every platform resolver implementation.
- Traceroute is unavailable on Android builds without a usable traceroute/toybox applet.
- Wi-Fi identity fields may be OS-redacted; current manifest lacks `ACCESS_WIFI_STATE`.
- Headless `As100Method.execute(...)` has no Android `Context`; `wifi_info` therefore returns unavailable when executed through a runtime path that does not provide the capability screen/context boundary. Other operations remain headless-capable.
- IPv6 address resolution and TCP/reachability targets are accepted, but CIDR calculation is IPv4-only in v0.1.0.

## Promotion criteria

Do not mark this capability Production until the checks in `VALIDATION.md` pass on the real MethodMesh project and at least one physical Android device.
