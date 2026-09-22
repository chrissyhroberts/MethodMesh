# Network tools validation — v0.3.0

Maturity: **Development**

## 1. Integration build

Place/replace the folder at:

```text
app/src/main/java/com/example/methodmesh/modules/networktools/
```

Apply `MANIFEST_INTEGRATION.md`, then run:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Do not promote to Production unless both complete successfully.

## 2. Regression: capability-open crash

- [ ] Open Network Tools directly from Capabilities.
- [ ] Dashboard renders without an immediate crash.
- [ ] Scroll the complete dashboard using the MethodMesh host.
- [ ] Rotate portrait/landscape and scroll again.
- [ ] There is no `Vertically scrollable component was measured with an infinity maximum height` / nested-scroll crash.
- [ ] No Android network/Wi-Fi system call executes synchronously from the initial Compose state constructor.

## 3. Architecture / Master Book

- [ ] `NetworkToolsModule` is auto-discovered; no central registration exists.
- [ ] No `HomeScreen` or shared UI contains Network Tools special casing.
- [ ] Descriptor projects Maturity = Development and Connectivity = Online/Offline.
- [ ] All settings use `MethodSetting`.
- [ ] Direct dashboard, focused preset/intent surface and ODK invoke the same `network.tools` method/operation semantics.
- [ ] `connection_status` and `wifi_scan` are not dashboard-only logic.
- [ ] Fixed preset settings are hidden through `settingShouldBeShown`.
- [ ] Runtime preset inputs remain visible.
- [ ] Fully fixed native preset may auto-run.
- [ ] Dashboard/focused runs display a working result in place.
- [ ] Commit freezes both result and producing settings.
- [ ] Editing inputs after Commit does not mutate the committed payload.
- [ ] Copy/share/save/Done operate on committed result only.
- [ ] Meaningful displayed scalar/text result values are tap-to-copy.
- [ ] App/native Done returns through the normal MethodMesh dashboard closeout.
- [ ] No automatic internal archive/save occurs.

## 4. Pure/function checks

### CIDR

| Input | Expected network | Expected broadcast | Usable |
|---|---|---|---:|
| `192.168.10.15/24` | `192.168.10.0` | `192.168.10.255` | 254 |
| `10.0.0.7/32` | `10.0.0.7` | `10.0.0.7` | 1 |
| `10.0.0.6/31` | `10.0.0.6` | `10.0.0.7` | 2 |
| `0.0.0.0/0` | `0.0.0.0` | `255.255.255.255` | 4294967294 |

- [ ] malformed CIDR fails cleanly;
- [ ] IPv6 CIDR returns a clear unsupported/invalid result rather than crashing.

### Host validation

- [ ] `example.org` accepted;
- [ ] IPv4 literal accepted;
- [ ] IPv6 literal accepted for host operations;
- [ ] `https://example.org/path` rejected;
- [ ] whitespace/shell-like URL input rejected;
- [ ] traceroute uses `ProcessBuilder` argument arrays only;
- [ ] no `sh -c`, shell interpolation, LAN sweep or port-range path exists.

### Bounds

- [ ] timeout accepts 100–30000 ms only;
- [ ] TCP port accepts 1–65535 only;
- [ ] traceroute max hops accepts 1–30 only;
- [ ] traceroute child is forcibly terminated on timeout;
- [ ] traceroute stdout is drained concurrently and retained output remains capped.

## 5. Active network / Wi-Fi device checks

Test at least one physical device; two Android/OEM combinations are preferable.

### Current connection

- [ ] Wi-Fi connected: correct transport/state and plausible local IP/gateway/DNS/interface;
- [ ] cellular active: clean cellular state;
- [ ] VPN active: clean VPN state;
- [ ] no active network: `No active network`, and **Metered is not incorrectly Yes**;
- [ ] captive portal state displays without crash where reproducible;
- [ ] denied/redacted Wi-Fi information produces an explanatory state, not an exception.

### Nearby Wi-Fi

- [ ] missing `ACCESS_WIFI_STATE`/`CHANGE_WIFI_STATE` is explained without crash;
- [ ] runtime location permission request works after manifest integration;
- [ ] permission denial leaves capability usable;
- [ ] location/Wi-Fi service disabled leaves capability usable;
- [ ] scan throttling/fresh-scan refusal falls back to latest cached results;
- [ ] visible network rows show SSID, RSSI, band/channel/security/AP count where available;
- [ ] Wi-Fi 4/5/6/7 mapping does not crash across supported Android API levels;
- [ ] **Capture nearby Wi-Fi list** returns canonical `wifi_scan` output.

## 6. Diagnostics

### DNS

- [ ] known host resolves;
- [ ] nonexistent host returns `unresolved` cleanly;
- [ ] timeout releases the MethodMesh flow.

### Reachability

- [ ] reachable target produces plausible result where platform permits;
- [ ] unreachable target does not crash;
- [ ] UI calls it reachability and does not promise ICMP ping.

### TCP

- [ ] known open endpoint returns `network_tcp_open=true`;
- [ ] refused/closed endpoint returns false + detail;
- [ ] only one supplied port is tested.

### Traceroute

- [ ] supported device returns bounded output;
- [ ] unsupported applet returns `unavailable`;
- [ ] verbose output cannot deadlock the capability;
- [ ] wall-clock timeout does not leave a child process/UI stuck.

## 7. Working-result / Commit state

For DNS and CIDR at minimum:

1. run A;
2. Commit A;
3. change inputs;
4. run B;
5. verify committed card still contains A and reports working state changed;
6. Commit B;
7. verify export/copy now use B.

Repeat across rotation before and after Commit.

## 8. ODK/XLSForm

Canonical workbook:

```text
docs/example_odk_showcase_network_tools.xlsx
```

- [ ] workbook contains exactly one MethodMesh invocation;
- [ ] intent is on the group;
- [ ] no `methodmesh_return_namespace` is set;
- [ ] return leaves use unprefixed canonical names;
- [ ] `methodmesh_status` and `methodmesh_full_json` are present;
- [ ] all nine operation choices are selectable;
- [ ] operation-specific inputs use relevance rules;
- [ ] DNS call returns canonical beef/scalars/full JSON;
- [ ] CIDR call returns normalized CIDR/full JSON;
- [ ] `connection_status`, `wifi_info`, `wifi_scan` return clean unavailable diagnostics if Android permission/context is insufficient;
- [ ] ODK owns submission/persistence; MethodMesh creates no archive copy.

## 9. Suggested repository tests

Add focused tests under the normal app test tree for:

1. CIDR `/0`, `/24`, `/31`, `/32`;
2. invalid CIDR/host/port/timeout/hops;
3. output-key contract for every operation;
4. `network_value` non-empty for handled successful diagnostic outcomes;
5. `network_result_json` validity;
6. traceroute process helper timeout/output-bound logic where testable;
7. canonical XLSForm filename, single invocation, unprefixed leaves and `methodmesh_full_json`.
