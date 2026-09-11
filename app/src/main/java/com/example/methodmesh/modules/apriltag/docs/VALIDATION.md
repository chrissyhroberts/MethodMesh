# Validation plan and current status

## v0.2 interaction revision

- Fixed planar tap delivery by placing the Compose gesture surface above `PreviewView`.
- Camera analyzer now uses the latest `onFrame` callback, preventing calibration/tracking acquisition from retaining stale Compose state.
- Added common live HUD overlays with tag IDs/roles, current working values, point/path overlays and explicit reasons when Commit is unavailable.
- `apriltag.detect` now treats target ID `-1` as all-visible multi-tag capture while preserving a strongest selected tag for scalar fields.
- Relative pose and tracking support tap-to-assign tag roles.
- Tracking UI now explains reference-tag versus fixed-camera frames and live acquisition status.
- Default `quad_decimate` reduced from 2.0 to 1.0 for more robust oblique/small-tag detection, trading additional CPU for detection detail.


## Current handoff status

- Master Book v1.08 requirements reviewed for module ownership, canonical capabilities, live-working-result → Commit, direct/preset/protocol/ODK parity, canonical single-call XLSForms, tags and module-local documentation.
- Current MethodMesh public interfaces inspected for `MethodMeshModule`, `As100Method`, `CapabilityScreenSpec`, `MethodSetting`, shared result scaffold, CameraX dependencies and CAMERA permission.
- Pure geometry implementation is module-local and deterministic; the geometry/method/module core was smoke-compiled against lightweight stubs matching the inspected MethodMesh interfaces.
- Six canonical XLSForm showcases were generated with `artifact_tool` and structurally inspected; each contains one capability invocation and requests FULL payload mode. Pyxform/ODK device validation remains an integration check.
- Compose compatibility was cross-checked against the current repository, including avoiding the known invalid explicit `androidx.compose.foundation.layout.weight` import.
- No whole-app Gradle build is claimed in this packaging environment because the MethodMesh repository is not present locally.
- No physical-device AprilTag validation is claimed.
- The native AprilTag binary is intentionally not bundled; runtime fails closed until integrated.

Keep **Maturity: Experimental** until the checks below pass on the integrated app.

## Required integration/build checks

1. Integrate a pinned official AprilTag 3 revision as described in `../native/README.md`.
2. `./gradlew :app:compileDebugKotlin`
3. `./gradlew :app:testDebugUnitTest`
4. `./gradlew :app:assembleDebug`
5. Verify module auto-discovery, method-ID uniqueness and capability-screen-ID uniqueness.
6. Verify all six capabilities launch directly, from a native preset, as protocol steps and from their ODK showcase forms.
7. Verify ODK Commit returns directly to the same form and Cancel returns cleanly.
8. Verify `methodmesh_full_json` and every declared representable output are available through generic transport.

## Bench validation experiments

### Detection

Use known printed IDs under varied distance, rotation and illumination. Report false positives, missed detections, Hamming distribution and decision-margin distribution rather than inventing a universal margin cutoff.

### Printed tag geometry

Measure the **detection-edge** dimension with calipers/ruler. Printer “fit to page” must be disabled. Test several physical sizes. Record measured rather than nominal size in presets.

### Focal calibration

At known distances, compare `apriltag.calibrate_focal` profiles across repeated sessions. Evaluate coefficient of variation and distance prediction error at held-out ranges.

### Range/pose

Against a tape/rig or measured rail, test distance error by tag size, range, view angle and image location. Compare Android factory intrinsics, Android physical estimate and manual calibration. Establish empirical operating ranges for each study/device rather than hard-coding a global accuracy claim.

### Relative pose

Mount two tags at known separation/orientation on a rigid board. Test repeatability as the camera moves while the physical relative transform remains constant.

### Planar measurement

Use a printed/measured grid on the same plane as the tag. Tap known points and lengths across the field of view. Repeat with deliberately non-coplanar targets to demonstrate the expected failure mode.

### Tracking

Move a tagged carriage along a measured path. Compare fixed-camera and fixed-reference-tag modes. Check dropped detections, timing, path-length bias, speed spikes and the effect of sample interval.

## Acceptance principle

This module reports quality evidence and assumptions but does not label a result “survey grade”, “laboratory grade” or similar without empirical device/setup-specific validation.
