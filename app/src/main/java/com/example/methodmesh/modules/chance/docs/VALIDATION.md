# Chance validation

**Migration target:** MethodMesh Master Book v1.05  
**Status:** Development; focused spinner validation below does not promote the whole module

## Spinner update — 22 October 2026

The spinner v0.3.0 update follows Master Book v1.31 and adds an immersive high-contrast wheel, adaptive portrait/landscape layout and opt-in winner removal. Other Chance capability interfaces and RNG algorithms remain unchanged.

- Debug app and instrumentation APK builds pass.
- 11 focused JVM spinner cases pass: unchanged deterministic/default choice, ordered pool removal, duplicate labels, complete depletion including a single final label, explicit empty/oversized input failures, input-prefix parity, canonical settings/outputs/version, minimum 4.5:1 label contrast, cyclic segment separation and pointer/outcome alignment.
- Five registry/contract parity cases pass across the actual app module inventory.
- Five focused Android actual-screen cases pass: removal through exhaustion/restoration and recreation; standard mode/Commit/Done returning the exact retained execution; animated landing once with landscape recreation; fixed native preset and host-owned closeout; and one immediate external automatic return. Portrait and landscape screenshots are inspected separately.
- These checks use an API 37 Pixel 7a emulator, not a physical phone or a real ODK/protocol caller. No physical back-of-room legibility claim, durable process-death recovery, real sharing receiver acceptance or all-module promotion is implied. Missing-envelope recovery requires an explicit new session; it is not tested with an actual OS process kill.
- No NFC, credential, attestation, trusted-timestamp or signing source was changed for this work. App release version remains unchanged; spinner capability metadata advances to 0.3.0 for the additive contract.

The earlier v1.05 migration record below is historical, not the current spinner build result.

## Contract inventory

| Method | Direct screen | Preset metadata | Protocol method | ODK example | Declared outputs |
|---|---:|---:|---:|---:|---:|
| `dice.simulate` | yes | yes | yes | yes | 24 |
| `coin.toss` | yes | yes | yes | yes | 18 |
| `chance.cards.draw` | yes | yes | yes | yes | 17 |
| `chance.pick.one` | yes | yes | yes | yes | 16 |
| `chance.spinner.spin` | yes | yes | yes | yes | 15 |
| `chance.weighted.choose` | yes | yes | yes | yes | 17 |

All six methods are returned by `ChanceModule.as100Methods()`, all six native screens by `capabilityScreens()`, and all six settings lists by `capabilitySettings()`. No central registration or shared capability-specific UI change is required.

## v1.05 interaction review

- Working random outcomes remain on the capability screen.
- Recorded operations expose **Commit** as the finalisation boundary.
- Commit freezes the saved canonical result rather than regenerating it.
- Post-Commit actions remain on the same capability screen.
- Share/Copy/Save are beef-first with optional JSON/audit inclusion.
- Useful displayed scalar/text results are tap-to-copy.
- Fixed native-preset fields use `settingShouldBeShown(...)`; fully fixed presets may start immediately.
- Non-interactive automatic-return callers execute without native setup/post-result export UI.
- Interactive `chance.pick.one` retains human position selection when no external `selected_position` is supplied.
- Working and committed UI state uses `rememberSaveable` where applicable.
- No automatic archive/output persistence is performed.
- Completion is delegated through `onConfirmed`/`onCancel`; Chance does not hard-code dashboard/desktop/ODK/protocol/schedule navigation.

## Random-engine checks

The migration preserves the established secure and deterministic engines rather than redesigning them. Local smoke checks cover:

- deterministic fixed-seed replay;
- card draws without replacement;
- concealed-pick permutation and position bounds;
- spinner selection bounds;
- weighted-choice positive-weight selection;
- dice parser/execution vectors, including rerolls and cascading explosions.

## ODK review

Each capability has a grouped-intent XLSForm example under `docs/`. Examples use canonical method IDs, `input_*` parameters, useful flat return fields and `methodmesh_full_json` with FULL payload mode. All additional descriptor fields remain requestable/projectable through MethodMesh generic transport; examples are not allow-lists.

No Chance capability returns binary media, so Chance needs no module-specific ClipData or URI-grant code.

## Historical migration build status — 6 September 2026

A full `./gradlew :app:testDebugUnitTest ./gradlew :app:assembleDebug` could not be run in the available container because the environment could not resolve `github.com` when obtaining the MethodMesh repository/dependencies. This is an environment limitation, not a claimed build pass.

Best-effort migration checks performed locally on 2026-09-06:

- Kotlin source delimiter scans on every module Kotlin file passed;
- `DiceSimulatorEngine.kt` and `ChanceSelectionEngine.kt` compiled with `kotlinc` against minimal `org.json` stubs;
- the pure-engine smoke suite passed fixed-seed dice replay, explosion parsing, 20-card uniqueness without replacement, fixed-seed concealed-pick replay/permutation, spinner bounds, and weighted-choice probability/weight checks;
- static parity enumeration confirmed the six established method IDs are present in module methods, screens, settings and RIL exposure;
- all six per-capability XLSForms were imported and structurally inspected with `artifact_tool`; grouped intents use the preserved canonical method IDs and FULL payload mode;
- delivery-clutter and Kotlin delimiter scans passed.

Before promotion from Development, integrate this `chance/` folder into the current MethodMesh repository and run the required Gradle tests/build plus device checks for direct launch, native preset, protocol, ODK, rotation, Commit, tap-to-copy and origin-aware Done routing.
