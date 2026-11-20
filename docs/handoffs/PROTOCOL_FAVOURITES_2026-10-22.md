# Protocol favourites — 22 October 2026

User-requested continuation of preset favourites. Active checkout: `/Users/icrucrob/AndroidStudioProjects/MethodMesh`, branch `feature/esp-mesh-transport`; Master Book v1.31 remains authoritative.

## Behaviour

- Active protocols have accessible star controls in the Protocol library and dashboard search. Starred protocols appear beside preset/capability favourites on the dashboard, labelled `Protocol` with their step count.
- Clicking a star only changes the local shortcut preference. Clicking the dashboard shortcut uses the existing `RUN_PROTOCOL` dispatcher with the saved protocol ID, normal output handling and test mode disabled. It neither reconstructs the chain nor bypasses its existing runner.
- Protocol favourites use their own preference file and saved IDs, independently of preset/capability favourites. Renaming/editing retains a favourite while the protocol's normal version history remains intact. Removing an active protocol through `removeProtocol` clears only that ID's favourite reference.
- Only active protocols are projected into dashboard shortcuts. Archiving hides the shortcut without rewriting the archived protocol or its local preference. The existing “Unarchive as active copy” operation creates a new ID; that copy can be starred independently and is not silently conflated with the original/version history.
- Favourites do not alter steps, step order/output handling, preset configuration, protocol versions, export schemas or bundle hashes. They are local UI preferences, not additional fields in portable protocol bundles. Existing preset/capability favourites require no migration.
- No NFC/attestation/trusted-timestamp or signing production source, clinical-trial/Sentinel workflow, capability ID or release version changed. No commit, push, tag or release.

## Verification and limits

- Both debug APKs build; `git diff --check` passes. Ten focused JVM registry/contract and native-closeout routing checks pass.
- **15 focused Android cases verified across two runs:** the initial run passed 14, with the new protocol search fixture timing out when it tried to edit a search box scrolled off-screen. The shared native test helper now scrolls back to the editable field; the single-case rerun passes. No production workaround, dependency update or repeated full-suite rerun.
- Eight new protocol cases cover independent IDs across all three favourite types, unchanged chain/configuration, multiple protocol favourites, immutable snapshots, rename/edit/version retention and targeted removal, archive/restore identity, actual library stars through recreation, actual HomeScreen search/dashboard add/remove while preserving other favourites, existing dispatcher flags and persistence. The seven preset cases were included as regressions. The native UI harness is shared rather than duplicated.
- An additional explicit persistence probe wrote a protocol favourite, stopped the emulator app and read it from a fresh instrumentation process. Both phases pass, preserving the favourite, protocol name/ID, ordered step identity/output mode and linked preset settings. Pending preference writes were flushed first; abrupt power-loss durability is not claimed.
- All preference fixtures are test-namespaced and cleaned up. Testing used a disposable read-only API 37 Pixel 7a emulator, not the physical phone. Launch intents were captured; no real protocol/capability, clinical workflow, NFC card, Collect or Sentinel execution occurred. This is not full-suite or clinical acceptance certification.

Rebuilt APK: `app/build/outputs/apk/debug/app-debug.apk`. It is not installed on the user's phone.
