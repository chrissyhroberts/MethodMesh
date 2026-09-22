# Changelog

## v0.4.0 — real-world metric distance calibration

- Added persistent device-level `distance_scale` calibration for all 3-D pose measurements.
- Calibration samples the uncorrected pose at a measured true camera-to-tag distance and computes `distance_scale = true / raw`.
- Corrected X/Y/Z, range, relative pose and tracking positions are scaled uniformly.
- HUDs expose the active adjustment; range/pose also shows the equivalent effective pose tag size.
- The calibration screen remains fullscreen/camera-first and shows raw distance, CV, proposed scale and corrected distance live.
- Calibration Commit stores the factor in a module-owned profile; presets/protocol/ODK can explicitly override it.
- Planar measurements deliberately continue to use the physical tag size and are not altered by the 3-D pose scale.
- Existing canonical method IDs remain unchanged; calibration outputs are extended with scale/raw/corrected audit fields.


## v0.3.2 — preset/external camera viewport

- Fixed CameraX preview startup when AprilTag capabilities are launched from a saved preset.
- The same fix applies to protocol and ODK/external launches because all use the external workflow surface.
- AprilTag now establishes its own finite screen-sized immersive viewport when launched from an intent, avoiding the unbounded-height constraint imposed by the external workflow scroll container.
- No HomeScreen, workflow-host, registry, or generic platform UI changes are required.
- Preset launch still uses the same canonical AprilTag capability and camera/HUD; saved settings only initialise the live instrument.

## 0.3.0 — immersive live HUD instruments

- All six AprilTag capability screens now use the generic immersive MethodMesh host.
- Camera preview is the primary working surface rather than a fixed-height component in a form.
- Common HUD grammar shows detected IDs/roles, working measurements, quality/readiness and capture guidance.
- Compact action rails remain on the live surface; reusable detector/intrinsics configuration is moved behind a Settings overlay.
- Planar measurement keeps a transparent Compose tap layer above `PreviewView` and shows captured points/paths live.
- Object tracking now exposes moving/reference roles, fixed-camera fallback, recording state, XYZ, samples, path, displacement and mean speed before Commit.
- Canonical IDs, outputs, presets/protocol/ODK contracts and the live-working-result → Commit lifecycle are unchanged.

## 0.2.1 — shared AprilTag platform boundary

- Removed the module-owned JNI call that caused `UnsatisfiedLinkError` after the shared native library moved to `platform.fiducial`.
- `AprilTagNativeBridge` is now a pure Kotlin adapter over the generic platform detector.
- Extended the generic platform detector with optional upstream AprilTag metric pose estimation while keeping it capability-agnostic.
- Preserved one-or-many tag detection, detector quality evidence, camera-first HUDs and explicit Commit semantics.
- Detector sessions are created once per active camera/settings configuration and closed with the camera lifecycle.

# AprilTag module changelog

## 0.2.0 — live HUD and capture rails

- unified camera-first live HUD across all six capabilities;
- reliable camera tapping through a Compose gesture layer above `PreviewView`;
- all detected tags labelled in the live image with target/reference/moving roles;
- `apriltag.detect` target `-1` now captures all visible tags, while retaining the strongest tag in scalar selected-tag fields;
- tap-to-target range/pose;
- tap-to-assign reference and target tags for relative pose;
- planar working points re-projected into the live image with line/polygon overlays, explicit next-step instructions and failed-tap explanations;
- tracking rewritten as an explicit moving-tag time-series workflow with recommended fixed-reference-tag mode, fixed-camera fallback, readiness status and live XYZ/path/sample HUD;
- fixed stale camera-analysis callback behaviour during calibration/tracking by using the latest Compose callback;
- changed default `quad_decimate` from `2.0` to `1.0` to favour robust oblique/small-tag detection over CPU economy;
- expanded capability documentation and XLSForm launch guidance.

Canonical method IDs and output field names are unchanged from 0.1.0.

## v0.3.1
- Restored **Save current setup as preset** as a capability-owned affordance on every immersive AprilTag screen.
- AprilTag now opens and owns its preset editor directly; no HomeScreen or generic immersive-host UI change is required.
- Presets use the generic `ProtocolLibraryRepository` for storage and retain canonical `apriltag.*` method IDs.
- The preset editor captures the live AprilTag settings, supports fixed versus runtime fields, payload mode, and post-run action.
- Preset creation is shown for direct dashboard capability use and hidden during external/ODK or existing native-preset runs.
