# Network tools validation

Status: **required before promotion from Development**

## Source/build checks

After placing the folder under the real module path:

```text
app/src/main/java/com/example/methodmesh/modules/networktools/
```

run:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

This handoff was produced outside the Android build environment, so a successful build is not claimed here.

## Architecture checks

- [ ] `NetworkToolsModule` is auto-discovered without central registration.
- [ ] No `HomeScreen` or other shared UI contains `network.tools` special casing.
- [ ] Module status remains `Development` until promotion is explicit.
- [ ] `iconKey="tool"` renders acceptably on generic launch surfaces.
- [ ] All settings are declared through `MethodSetting`.
- [ ] Fixed preset settings are hidden at runtime.
- [ ] Runtime preset fields remain visible and are requested before execution.
- [ ] External intents execute without a redundant native setup dialog.
- [ ] Protocol/schedule closeout uses the shared completion contract.

## Pure/function checks

### CIDR

Verify at minimum:

| Input | Expected network | Expected broadcast | Usable |
|---|---|---|---:|
| `192.168.10.15/24` | `192.168.10.0` | `192.168.10.255` | 254 |
| `10.0.0.7/32` | `10.0.0.7` | `10.0.0.7` | 1 |
| `10.0.0.6/31` | `10.0.0.6` | `10.0.0.7` | 2 |
| `0.0.0.0/0` | `0.0.0.0` | `255.255.255.255` | 4294967294 |

Reject malformed CIDRs and IPv6 CIDR input cleanly.

### Host validation

- [ ] `example.org` accepted.
- [ ] IPv4 literal accepted.
- [ ] IPv6 literal accepted for host operations.
- [ ] `https://example.org/path` rejected as a host input.
- [ ] whitespace/shell-like URL input rejected.
- [ ] traceroute does not use `sh -c` or another shell interpolation path.

### Bounds

- [ ] timeout accepts 100–30,000 ms and rejects values outside that range.
- [ ] TCP port clamps/validates to 1–65,535.
- [ ] traceroute hops clamp to 1–30.
- [ ] traceroute process is forcibly destroyed on wall-clock timeout.
- [ ] captured traceroute text is capped.

## Device checks

Exercise on at least one physical Android device and, if practical, a second manufacturer/Android version.

### Interface info

- [ ] returns without network permission crash;
- [ ] reports at least expected loopback/active interfaces;
- [ ] primary IP selection is sensible.

### DNS lookup

- [ ] known host resolves;
- [ ] nonexistent host returns a clean unresolved result;
- [ ] result includes structured JSON;
- [ ] native primary copy/share is the beef value, not the JSON blob.

### Reachability

- [ ] known reachable target gives plausible result where platform permits;
- [ ] unreachable target does not crash;
- [ ] UI says reachability probe, not guaranteed ICMP ping.

### TCP endpoint

- [ ] known open endpoint returns `network_tcp_open=true`;
- [ ] known closed/refused port returns `false` with detail;
- [ ] no port-range syntax is accepted.

### Traceroute

- [ ] supported device returns bounded output;
- [ ] unsupported device returns `unavailable` clearly;
- [ ] timeout does not leave the UI stuck.

### Wi-Fi info

- [ ] connected Wi-Fi state returns without crash;
- [ ] non-Wi-Fi active network returns a clean non-Wi-Fi result;
- [ ] SSID/BSSID redaction is handled cleanly;
- [ ] behaviour without `ACCESS_WIFI_STATE` is documented and acceptable;
- [ ] if `ACCESS_WIFI_STATE` is later added centrally, re-test Android permission/privacy behaviour.

## Native UX checks

- [ ] normal dashboard run waits for **Run diagnostic**;
- [ ] only relevant controls are shown;
- [ ] fixed native preset with no runtime inputs starts automatically;
- [ ] native preset with runtime input waits for that input;
- [ ] result survives portrait/landscape rotation sufficiently to reconstruct the result display;
- [ ] retry is available;
- [ ] Done/Home routing follows shared MethodMesh rules;
- [ ] copy/share defaults to `network_value` only;
- [ ] full JSON is opt-in;
- [ ] no automatic internal save occurs.

## ODK/XLSForm checks

Using `example_odk_network.tools.xlsx`:

- [ ] intent is attached to a group;
- [ ] input names do not collide with return field names;
- [ ] DNS operation returns `network_value`, host/IP/latency fields as available;
- [ ] CIDR operation returns the normalized range;
- [ ] `methodmesh_full_json` is populated with `input_payload_mode='FULL'`;
- [ ] blank return fields do not overwrite request inputs;
- [ ] ODK owns persistence/submission;
- [ ] no MethodMesh archive copy is created.

## Suggested focused tests after admission

Add repository tests at the normal `app/src/test/...` location for:

1. CIDR `/0`, `/24`, `/31`, `/32` calculations;
2. invalid host/CIDR rejection;
3. operation output-field contract;
4. `network_value` always present on successful operations;
5. `network_result_json` valid JSON;
6. timeout/port/max-hop bounds;
7. ODK workbook existence and group-intent contract.
