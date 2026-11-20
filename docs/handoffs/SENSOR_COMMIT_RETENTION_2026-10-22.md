# Audit continuation: sensor Commit retention — 22 October 2026

Active checkout: `/Users/icrucrob/AndroidStudioProjects/MethodMesh`, branch `feature/esp-mesh-transport`; Master Book v1.31 Commit/lifecycle rules remain authoritative. This is a bounded data-integrity correction, not an all-module certification.

## Confirmed and fixed

- **Compass (`compass.read`):** a committed execution was only remembered in the composition. Recreation rebuilt a new execution from saved scalar fields, changing its identity/timing/audit envelope while displaying old captured values. The screen now retains the actual execution using the existing Activity-scoped retention helper. The saved committed-value JSON and reconstruction helpers are removed; display, FULL JSON and Done all use the original execution.
- **Signals sensor scope (`signal.sensor.scope`):** Commit constructed one execution, but native display/export/Done constructed a second from saved value JSON. Recreation did so again; identical-value Recommit could also leave the JSON-keyed old envelope in place. Commit now constructs/stores one execution used by both native and automatic return. Explicit Recommit replaces it even when readings are identical. No committed-value JSON is saved in UI state.
- Both screens save a small committed-state marker alongside the retention helper's opaque key and ordinary UI settings. If the original execution is unavailable, they clear stale result-action state, show “Captured result unavailable” and require an explicit new live Commit. They do not fabricate a replacement or return the old capture under a new identity. Compass also closes a stale sight view in this recovery branch.
- The existing retention helper is reused unchanged. No method/output/setting IDs, scientific calculation, sensor calibration, public result schema, capability version, Share/Save policy or launch-origin contract is changed.

## Verification

- Debug app and instrumentation APKs build; `git diff --check` passes.
- **14/14 focused JVM checks pass:** four retained-store cases, five registry/contract parity and five native closeout routing cases.
- **13/13 Android cases pass in one focused run**, zero errors/skips: eight new actual-screen lifecycle cases plus the existing five Signals closeout regressions.
- Four cases per screen verify original object and byte-identical canonical FULL JSON through recreation and Done; explicit Recommit/new identity without mutating the previous capture; simulated missing-envelope recovery without confirmation until a fresh Commit; and automatic return using the retained object without duplicate delivery after recreation.
- The sensor-scope Recommit case injects a controlled repeated accelerometer reading so identical sampled values must still produce a new execution. Recreation then restarts the emulator sensor stream without changing the retained capture. Other cases use emulator sensors. The flat Compass Commit path is exercised; camera sight, real magnetic accuracy/calibration and physical-device measurement acceptance are not.
- Missing-envelope coverage forgets the retained slot then recreates the Activity. It is not a real OS process-kill test or a durable process-death recovery claim. Live trace/history, all working sensor UI state and external Collect/protocol/schedule roundtrips are not certified by these checks.
- Disposable read-only API 37 Pixel 7a emulator only; no physical-phone installation, real camera capture, transmitter/receiver session, NFC card or Sentinel/clinical execution. Protected NFC/attestation/trusted-timestamp source trees have no diff. Existing signing changes remain unchanged. No version bump, commit, push, tag or release.

## Confirmed remaining audit queue

1. **Other Signals screens:** 14 saved-JSON reconstruction declarations remain across Morse, FSK, QR, QR Burst, optical and surface transmit/receive screens (16 physical-signal capabilities because the FSK UIs serve audible/near-ultrasonic variants). Their working physical sessions, cancellation, Commit/recovery boundaries and exact evidence need a separate coherent pass. This sensor-scope correction is not applied or claimed for them.
2. **Paper Bridge design/transcription:** both still rebuild missing committed envelopes from saved value JSON. Barcode scan/generate/clone reconstruct with saved IDs/timing subsets; exact FULL preservation remains unqualified. No blanket retention fix is claimed.
3. **Share/Save conformity:** Signals/Weather/Network module-owned text dispatch, Signals audit controls and Paper Bridge committed exports versus workspace/template saves remain pending, as recorded in `MODULE_CLOSEOUT_2026-10-22.md`.
4. Other Astronomy/provider/runtime inputs, capability-specific Workbench policy, large state/log limits and real provider/receiver/Collect/Sentinel acceptance remain part of the wider app-wide review.

The rebuilt debug APK remains `app/build/outputs/apk/debug/app-debug.apk`; it has not been installed on the user's phone.
