# Network tools

Status: **Development**

Bounded device/network diagnostics. The module deliberately does **not** perform broad LAN scanning or port-range scanning.

## Capabilities

- `network.tools` — current interface information, DNS lookup, reachability probe, TCP endpoint test, best-effort traceroute, IPv4 CIDR calculation and Wi-Fi connection information.

## Android intent

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='network.tools',input_operation='dns_lookup',input_host='example.org',return_mode='flat')
```

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='network.tools',input_operation='tcp_test',input_host='example.org',input_port='443',input_timeout_ms='3000',return_mode='flat')
```

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='network.tools',input_operation='cidr',input_cidr='192.168.10.0/24',return_mode='flat')
```

## Inputs

- `input_operation` — `interface_info`, `dns_lookup`, `ping`, `tcp_test`, `traceroute`, `cidr`, `wifi_info`.
- `input_host` — one named host for DNS/ping/TCP/traceroute.
- `input_port` — one TCP port, default `443`.
- `input_timeout_ms` — bounded 100–30,000 ms.
- `input_cidr` — IPv4 CIDR for calculation only.

## Outputs

Core outputs:

- `network_summary`
- `network_result_json`
- `network_latency_ms`
- `network_host`
- `network_ip`

Audit-priority outputs:

- `network_status`
- `network_operation`
- `network_captured_time_iso`
- `network_error`

`network_result_json` is the structured result for the chosen diagnostic rather than a general LAN inventory.

## ODK example

`example_odk_network.tools.xlsx` demonstrates a named DNS lookup and receives the core/audit fields.

## Permissions, services and online behaviour

The current MethodMesh manifest already declares `INTERNET`, `ACCESS_NETWORK_STATE` and location permissions. DNS, reachability, TCP and traceroute require network access. CIDR calculation is offline. Wi-Fi radio details are obtained from Android `NetworkCapabilities`/`WifiInfo` where the OS and permission policy expose them.

Some Android builds may additionally require `android.permission.ACCESS_WIFI_STATE` for useful SSID/BSSID/RSSI values. v0.1.0 catches denied/unavailable access and returns an explicit unavailable reason rather than failing the whole app. If the project chooses to guarantee these fields, add that permission at the app manifest boundary as a generic platform permission.

## Known limitations

- `ping` uses `InetAddress.isReachable`; Android/network ICMP behaviour varies, so this is described as a reachability probe rather than guaranteed ICMP echo.
- Traceroute is best-effort using an available Android/toybox traceroute command; unsupported builds return a structured unavailable result.
- No mDNS/Bonjour discovery is included in v0.1.0; it can be added later without introducing broad scanning.
- IPv6 subnet calculation is not yet included.
