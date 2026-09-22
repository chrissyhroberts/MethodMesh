# Network Tools v0.3.0 review report

Basis: MethodMesh Master Book v1.22 plus the current `master` runtime interfaces.

## Crash root cause

The v0.2 dashboard added its own root `verticalScroll(rememberScrollState())` while the Standard MethodMesh capability host already owns the vertical scroll surface. In Compose this can place a vertically scrollable child under unbounded vertical measurement and crash with the infinite-height nested-scroll failure seen elsewhere in MethodMesh.

**Fix:** both dashboard and focused capability now participate as ordinary content in the Standard host. Neither owns an unconditional root vertical scroller.

## Additional runtime defects found and fixed

1. **Unsafe Wi-Fi/network reads during dashboard composition**
   Initial system-service and scan-result reads were performed synchronously while Compose was constructing the screen. The dashboard now mounts with a neutral checking state and performs Android network/Wi-Fi reads on `Dispatchers.IO` from effects/actions.

2. **Permission/OEM Wi-Fi exceptions could escape dashboard acquisition**
   `NetworkSnapshotRepository.current()` and `wifiEnvironment()` are now no-throw boundaries. Security/OEM/API failures become explicit unavailable/redacted states.

3. **Incorrect metered state with no active network**
   Null `NetworkCapabilities` was previously interpreted as “not unmetered”, causing no-network state to report Metered = Yes. Metering is now true only when capabilities exist and lack `NET_CAPABILITY_NOT_METERED`.

4. **Wrong Wi-Fi standard constant owner**
   Wi-Fi standards use `ScanResult.WIFI_STANDARD_*`, with Wi-Fi 7 guarded to Android 13+.

5. **Traceroute process pipe deadlock risk**
   The previous implementation could wait for process exit before draining stdout. A sufficiently verbose child could block on a full pipe. Output is now drained concurrently, retained text is bounded, and timeout forcibly terminates the process.

6. **JSON compile/type defect**
   Nullable Wi-Fi strings were being supplied through `String.ifBlank { JSONObject.NULL }`, whose lambda cannot return `JSONObject.NULL`. JSON now writes `JSONObject.NULL` through explicit branches.

7. **Dashboard-only functional logic**
   Connection-status capture and nearby-Wi-Fi capture are now canonical `network.tools` operations (`connection_status`, `wifi_scan`) rather than UI-only behaviour. They are selectable through presets/protocols and ODK using the same `operation` input.

8. **Lifecycle mismatch**
   The older run flow effectively treated acquisition as final. v0.3.0 keeps a working result on the capability screen and freezes result + producing settings only at Commit. Copy/share/save/Done operate on the committed payload.

9. **Missing ODK Integration Card / stale workbook convention**
   Added a module-owned ODK Integration Card and renamed/updated the canonical workbook to `example_odk_showcase_network_tools.xlsx`. It contains one MethodMesh invocation and unprefixed canonical returns including `methodmesh_full_json`.

## Static safety/architecture review

- no capability-specific `HomeScreen` branch;
- no central registration edit;
- no shell interpolation (`sh -c`) for host input;
- TCP is one explicit port only;
- no subnet/LAN sweep;
- timeouts and traceroute hop counts are bounded;
- Wi-Fi/API failures degrade to diagnostics;
- dashboard values and working/committed scalar text are tap-to-copy;
- `network_value` remains the beef-first output;
- full JSON remains secondary in native UI and captured in canonical ODK flow;
- status metadata is Development + Online/Offline;
- module remains self-contained except for the documented two-line app-manifest Wi-Fi scan permission integration.

## Validation not claimed here

A full Gradle/Android build and physical-device run cannot be executed from the standalone module packaging workspace. Before Production promotion run:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Then exercise `VALIDATION.md` on a physical Android device and with ODK Collect.
