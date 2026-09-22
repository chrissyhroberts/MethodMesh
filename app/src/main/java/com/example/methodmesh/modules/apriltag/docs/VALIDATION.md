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
- Native AprilTag support is provided by the shared MethodMesh `platform.fiducial` layer; the module contains no module-specific JNI implementation.

Keep **Maturity: Experimental** until the checks below pass on the integrated app.

## Required integration/build checks

1. Verify the shared `platform.fiducial` AprilTag library builds for the app ABIs and `libmethodmesh_apriltag.so` is packaged.
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


## v0.2.1 platform-boundary repair

The failing v0.2 build loaded `libmethodmesh_apriltag.so` successfully but called a stale module-specific JNI symbol (`modules.apriltag.AprilTagNativeBridge.nativeDetect`) that the shared library deliberately does not export. v0.2.1 removes that JNI declaration. The module now consumes `com.example.methodmesh.platform.fiducial.AprilTagDetector`.

Static checks performed in the supplied `main/` tree:

- no `external fun` or `System.loadLibrary` remains in the AprilTag module;
- the only JNI symbols are generic `platform.fiducial.AprilTagDetector` symbols;
- shared native pose estimation uses upstream `estimate_tag_pose`;
- the module's six `apriltag.*` method IDs remain unchanged;
- the main/shared platform contains no imports of `modules.apriltag` and no `apriltag.*` capability IDs.

A full Gradle/Android-device build is still required in the complete repository because the supplied archive is the `app/src/main` tree rather than a complete Gradle checkout.


## v0.3.0 immersive UI validation notes

The module requests only the generic `CapabilityHostPresentation.Immersive` host; no shared host branch names or knows AprilTag. Live camera state remains capability-owned. The committed `ExecutionResult` is still created only after explicit Commit, after which the normal shared result/automatic-return lifecycle is used. Physical-device validation remains required for camera aspect/rotation, touch alignment, one-handed bottom-rail ergonomics and long tracking sessions.

## v0.3.2 launch-origin camera parity

Manual validation matrix for every canonical AprilTag capability:

| Launch origin | Expected optical surface |
| --- | --- |
| Dashboard/direct | Immersive live camera/HUD; current settings; explicit Commit |
| Saved preset | Same live camera/HUD; preset values preloaded; runtime fields remain operator-controlled; explicit Commit |
| Protocol step | Same live camera/HUD in a finite viewport; step completion occurs after Commit |
| ODK/external intent | Same live camera/HUD in a finite viewport; caller return occurs after Commit |

The module must not gate `AprilTagCameraSurface` on `isNativePresetRun`, and preset execution must not call a headless AprilTag method instead of the capability screen.


## v0.4.0 metric calibration validation

- With calibration camera settings forced to distance scale 1.0, verify collected samples are raw/unadjusted.
- At a known true distance D, verify committed factor is D / mean(raw range).
- Verify range/pose X, Y, Z and Euclidean range are multiplied by the same factor.
- Verify relative pose and tracking positions/path/speed inherit the same factor.
- Verify planar point/distance/area calculations are unchanged by distance scale.
- Verify direct dashboard launches load the persisted device factor.
- Verify saved presets retain their explicit factor and can override the device default.
- Verify ODK/protocol launches with no valid `distance_scale` fall back to the persisted calibration.
- Recalibration should be required after changing camera/lens, zoom, intrinsics model or the tag-size definition.
