# MethodMesh v2.2.5

This release refreshes the public project README and packages the recent preset/runtime, UI, language-service and sensor-read work into a coherent release.

## Highlights

- Rewrote the top-level GitHub README as a current MethodMesh overview rather than the older architecture-heavy manifesto.
- Clarified the native toolbox model: show and share the main result by default, with full JSON metadata available for ODK/XLSForm and audit use.
- Documented the current production capability set and the four production checks used during capability review.
- Reinforced the core architecture rule that the shared UI renders capability metadata generically and should not know about individual capability behaviour.

## Presets and capability runtime

- Moved preset creation into the configure/test workflow.
- Added a “Save current setup as preset” flow that reuses the capability’s own setting metadata.
- Added fixed-vs-runtime setting handling so native preset runs hide fixed values and only ask for runtime inputs.
- Added final preset naming rather than silently using generic `capability...` names.
- Added confirmation after preset creation.
- Added a Home path to capability/configuration flows and continued work on making preset completion return to the dashboard.
- Added multi-choice setting support so capabilities can render checkbox groups generically.
- Updated barcode format preset configuration to use checkboxes instead of free-text format strings.

## Production capability polish

- Continued the production pass for barcode scanner, calibrated scale, document scanner, GPS target navigator, Plus Code capture, image redaction, local device authentication and conversation translator.
- Preserved capability results across orientation changes in reviewed production flows.
- Standardised native sharing around the main useful result rather than verbose metadata.
- Kept ODK/XLSForm behaviour focused on main fields plus optional JSON metadata.

## ML Kit and language services

- Renamed the shared device-services area toward Settings.
- Added shared ML Kit language-pack management with visible installed/downloadable states and diagnostics.
- Hardened language-pack download/remove feedback so long-running operations report whether callbacks are still pending.
- Restored ML Kit language-pack download handling used by translation flows.
- Improved conversation translation handling for Chinese speech recognition by using a more specific speech locale path.
- Clarified that Android speech recognition availability and ML Kit translation packs are separate services.

## Conversation translator

- Promoted conversation translation into the production capability set.
- Added a full-screen two-person conversation mode designed to be held between speakers.
- Added operator-view switching so both text panels can face the main operator when needed.
- Added replay/end controls and a shareable transcript model.

## Bluetooth printer and workbench work

- Added Qutie-family Bluetooth printer implementation pieces and documentation.
- Improved native preset handling for printer settings, while keeping operational controls such as device selection visible.
- Kept printer/device tooling aligned with the workbench/development separation.

## ESP32 sensor and LD2410C radar fixes

- Integrated the LD2410C fresh-sample/live diagnostic update from the external ZIP bundle without keeping the bundle itself as release content.
- Fixed the build failure caused by local function ordering in the generated sensor-read screen changes.
- Live sensor reads now request a fresh `sample` command before reading when the firmware exposes the command characteristic.
- LD2410C firmware wiring is updated for the current physical wiring:
  - ESP32 GPIO5 TX → LD2410C RX
  - ESP32 GPIO4 RX ← LD2410C TX
- Sensor firmware version is now `methodmesh-sensor-0.1.7`.
- Added bounded radar diagnostics for live/decode troubleshooting:
  - `radar_frame_sequence`
  - `radar_frame_length`
  - `radar_decode_offset`
  - `radar_prefix_hex`

## Documentation

- Refreshed the root README around:
  - app structure;
  - production capabilities;
  - presets and runtime inputs;
  - ODK/XLSForm integration;
  - offline-first behaviour;
  - module ownership;
  - repository layout;
  - attribution and third-party services.
- Added live sensor read diagnostic notes for the LD2410C update.

## Validation

- `./gradlew :app:assembleDebug` passes.

## Notes

- ESP32 sensor firmware changes require rebuilding and reflashing the relevant sensor image before the wiring/live-sample fixes affect a physical node.
- Online map tiles remain optional presentation aids; Plus Code encoding itself remains local and offline-capable.

