# Signals committed execution retention — 23 October 2026

Active checkout: `/Users/icrucrob/AndroidStudioProjects/MethodMesh`, branch `feature/esp-mesh-transport`; Master Book v1.31. The user dropped the enrichment-variant proposals and resumed the strategic engineering review. This is a bounded evidence-integrity increment, not an all-module or physical-channel certification.

## Confirmed defect and correction

- Fourteen saved-value reconstruction declarations covered Morse transmit/receive, audible/high-band FSK transmit/receive, QR live transmit/receive, QR Burst transmit/receive, screen optical transmit/receive, torch transmit/receive and contact/surface transmit/receive. There are 16 public physical capabilities because FSK variants share two bodies.
- Commit constructed an execution for immediate caller return, while native result actions constructed a second execution from saved output JSON. Recreation reconstructed it again. A same-value Recommit could leave the JSON-keyed remembered prior execution in place. The displayed old payload could therefore acquire new execution identity/timing/audit or retain the wrong prior identity.
- A Signals-local `SignalCommitState` reuses the existing `rememberRetainedExecutionResult` framework helper. Each of the 15 deliberate Commit branches now constructs and retains one canonical execution. The second screen-optical receive branch preserves its established CRC-only interpretation; verification flags are not upgraded. All native fields, FULL JSON and Done derive from the retained execution. Canonical field enumeration comes from its single Signals observation, not an expanded generic transport projection, so existing module text exports retain their field set.
- Only a small committed marker and the retention helper's opaque key are saved at this boundary. The committed-value JSON copies are removed. The QR receiver's small auxiliary transfer-summary fields and existing working state are not redesigned. Existing Reset paths clear both the retained result and marker; working-only invalidation still preserves committed evidence where it did before.
- A missing retained execution shows an explicit warning and has no committed result actions. A deliberate subsequent Commit creates a new execution from current working state, not a reconstruction of the original. This is not durable restoration or a guarantee of new physical acquisition; physical working-state provenance/continuity remains a separate review item.
- Signal codecs, packet formats, timing/power profiles, capture/transmission operations, CRC/SHA-256 semantics, public IDs, output names, settings, capability versions and existing closeout/save policy are unchanged. The shared retention framework is unchanged. NFC credentials, attestation, trusted timestamps and signing are untouched.

## Verification

- Debug app and instrumentation APK builds pass; `git diff --check` passes.
- **21 focused JVM cases pass:** seven new boundary/wiring cases (the behavioral cases enumerate all 16 physical contracts), four retained-store, five app-wide registry/contract parity and five native preset closeout policy cases.
- **13 Android cases pass in one focused run:** eight new actual-screen cases plus five existing Signals closeout cases. For each of QR live and QR Burst transmit, the screen completes a real screen-only cycle before Commit. Cases check identical object and byte-identical FULL evidence through recreation and Done; deliberate same-value Recommit with a new identity; simulated lost-envelope warning/no return followed by a new explicit cycle/Commit; and immediate return at Commit without duplicate delivery or restarted transmission after recreation.
- The fixture uses a read-only API 37 Pixel 7a emulator and no physical phone. It does not use a real receiving device, camera, microphone, torch, contact sensor or external caller. Missing-envelope tests forget the retained slot then recreate the Activity; they do not kill the OS process. Other physical screen bodies are source/wiring/contract/build checked, not individually exercised against hardware.
- Existing closeout failure tests retain Save-before-confirm ordering and reject failed storage or module-owned Home navigation. These do not certify real storage-provider/receiver behavior.
- No full-suite rerun/promotion, app/capability version bump, physical-phone installation, commit, push, tag or release.

## Remaining review queue

1. Paper Bridge design/transcription still rebuild missing committed envelopes; Barcode scan/generate/clone retain saved identity/timing subsets rather than qualified exact FULL retention.
2. Signals working physical-session interruption/cancellation, large saved frame arrays and hardware pairing remain open. A retained completed execution does not make a live camera/audio/emitter session survive recreation.
3. Module Share/Save conformity remains open, including Signals audit controls and text sharing, Weather/Network dispatch and Paper Bridge workspace saves versus canonical committed Downloads exports.
4. Other Astronomy/provider/runtime inputs, capability-specific Workbench policy, large log/payload limits and real caller/Collect/Sentinel acceptance remain part of the app-wide review.

Rebuilt APK: `app/build/outputs/apk/debug/app-debug.apk`; not installed on the user's phone. Existing spinner functionality remains in this build; none of the proposed enrichment variants was implemented.
