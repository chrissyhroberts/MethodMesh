# Spinner presentation and winner removal — 22 October 2026

Checkout: `/Users/icrucrob/AndroidStudioProjects/MethodMesh`; Master Book v1.31. This is the user's requested Chance diversion, not another general audit increment.

## Delivered

- Existing `chance.spinner.spin`, not a new ID/alias. Existing output names and meanings remain; spinner capability version is 0.3.0. App version/release metadata is unchanged.
- The native spinner requests the established immersive host, removes the old 300dp wheel cap, uses bold opaque segment colours and independently contrasting labels, shows a large copyable winner, and reflows wheel/controls side-by-side in landscape. Screen-awake state is temporary while the operational spinner is open. Long wheel labels may be abbreviated; full winning text remains available.
- Canonical BooleanSetting `remove_after_spin` defaults to false. With it enabled, landing removes exactly one selected segment from the next round; duplicate labels are not all removed together. The landed round remains visible while the next pool is already reduced. The last single label may be drawn. An exhausted pool cannot spin until explicitly restored. Restore all and Settings do not silently overwrite the saved/authored preset roster.
- New additive outputs: `spinner_remove_after_spin` and `spinner_remaining_items_json`. The latter describes the ordered next pool. Each external invocation remains one draw; callers explicitly carry remaining labels into a subsequent invocation. No cross-invocation hidden session is introduced. Explicit empty inputs fail instead of repopulating default labels.
- Each round generates one canonical execution before animation. The existing Activity-scoped retention helper preserves the exact object through recreation and Commit/Done; no envelope is reconstructed from saved output values. UI/session state is saveable. Lost execution state displays a warning and requires an explicit new session rather than claiming durable recovery.
- Native preset Commit delegates to the shared host when that host owns closeout; automatic callers return immediately. Ordinary native post-Commit Share/Copy/Save/Done uses the existing Chance/shared transport helpers, with audit opt-in. Weighted choice and other Chance UI behavior were not redesigned.

## Verification and limits

- `:app:assembleDebug` and `:app:assembleDebugAndroidTest` pass.
- 16 focused JVM tests pass: 11 spinner and 5 app registry/contract parity.
- Five actual-screen Android tests pass, covering complete removal/restoration, ordinary rotation, original execution through Commit/Done, fast animation and landscape, fixed native preset/host closeout, and one automatic external result. The landscape fixture waits for side-by-side geometry and a settled system rotation before capturing its screenshot.
- Palette pairs are tested at a minimum 4.5:1 text contrast. Pointer geometry is checked for every possible selected segment for counts 1–60. This is not a measured classroom/projector legibility test.
- No physical-phone install, real ODK/Collect/protocol roundtrip, sharing-receiver exercise, real OS process kill, NFC card or Sentinel/clinical execution was performed. The rest of the app's pre-existing audit queue remains open.
- No protected NFC/attestation/trusted-timestamp changes; the pre-existing signing diff is unchanged. No commit, push, tag or release.

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. Not installed on the user's phone.
