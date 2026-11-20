# Preset favourites — 22 October 2026

User-reported core discovery gap: only capabilities could be favourited. Presets had no star control and were excluded from the dashboard favourites list.

Active checkout: `/Users/icrucrob/AndroidStudioProjects/MethodMesh`, branch `feature/esp-mesh-transport`; Master Book v1.31. This implements the requested shortcut function, not a shell redesign or a preset-contract migration.

## Behaviour

- The expanded Preset library module groups now expose a star beside every preset. Dashboard preset search results expose the same control.
- Starring immediately adds a dashboard shortcut, alongside existing capability favourites. Preset shortcuts show the saved name and a `Preset`/module subtitle; tapping one uses the existing `RUN_PRESET` dispatcher with the exact saved preset ID, transient-run and output flags.
- Star controls have labelled Add/Remove accessibility actions and standard IconButton touch targets. Clicking a star does not launch the preset. Removing a favourite is reversible and does not delete the preset.
- Local preference storage uses preset IDs, independently of capability favourite IDs. Renaming/updating a preset preserves its favourite. Multiple setups for one capability can be starred separately. Removing a preset through the existing confirmed deletion path removes only its own favourite reference.
- Preset names/settings, launch mode, result action, payload mode, versions and portable bundle schemas are unchanged by starring. Favourites remain a local UI preference, not new fields in exported presets/protocols.
- Existing capability favourites are neither migrated nor rewritten. No capability/module IDs, output contracts, version numbers, NFC/attestation/trusted-timestamp sources, signing sources or clinical-trial/Sentinel behaviour changed in this feature.

## Verification

- Debug app and instrumentation APKs build; `git diff --check` passes.
- **10 focused JVM checks pass:** five registry/contract parity and five native closeout routing cases.
- **Seven focused Android test cases verified.** Repository checks cover independent capability/preset IDs, multiple setups, unchanged configuration, immutable snapshots, rename retention and targeted deletion cleanup. Native UI checks exercise actual library stars through recreation, the actual HomeScreen search/dashboard add/remove flow while preserving a capability favourite, and correct dispatcher intent/settings with no real preset execution.
- The native seven-case run passed six; its remaining assertion mistakenly expected the capability screen title `Create code` rather than canonical catalogue name `Code generator`. The assertion now uses the canonical descriptor, and the single-case rerun passes. The earlier Compose-rule attempt hit the existing Espresso/API 37 `InputManager.getInstance` incompatibility before screen checks; tests use the already working native accessibility harness instead. No test-library upgrade or production workaround was introduced.
- An additional explicit cold-start probe ran its write phase, stopped the emulator app, then ran its read phase in a new instrumentation process. Both phases pass; the stored favourite, preset ID, name, settings and launch mode remain linked. Pending preference writes were flushed before stopping, so this is not a power-loss/immediate-kill durability claim.
- Tests namespace every preference file and clean up their own fixtures. A disposable read-only API 37 Pixel 7a emulator was used; the physical phone was untouched. No messages, real capability executions, Collect/Sentinel workflows or clinical acceptance tests were performed. No full-suite rerun or new all-module certification is claimed.

No commit, push, tag or release. The rebuilt debug APK is `app/build/outputs/apk/debug/app-debug.apk`; it has not been installed on the user's phone.
