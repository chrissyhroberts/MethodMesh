# AprilTag — AprilTag metrology and tracking

**Module ID:** `apriltag`  
**Display name:** AprilTag  
**Version:** 0.2.0  
**Maturity:** Experimental  
**Connectivity:** Offline  
**MethodMesh target:** Master Book v1.08

This module turns printed AprilTags into physical experimental references. The public capabilities are **experimental operations**, not a generic AprilTag demo. Each operation is independently callable from direct native use, presets, protocols and ODK/XLSForm; the module dashboard is only an aggregate projection.

## Canonical capabilities

| Method ID | Purpose | Primary beef |
|---|---|---|
| `apriltag.detect` | Detect/identify tags and image-space geometry | ID, pixel geometry, detector evidence |
| `apriltag.calibrate_focal` | Approximate pinhole focal calibration from a known-distance tag | `fx/fy/cx/cy` calibration profile |
| `apriltag.range_pose` | Known-size tag range + 6-DoF pose | distance, XYZ, yaw/pitch/roll |
| `apriltag.relative_pose` | Target tag relative to fixed reference tag | relative XYZ/rotation |
| `apriltag.planar_measure` | Point/distance/area/manual trajectory on a tagged plane | millimetre coordinates/derived measures |
| `apriltag.track_pose` | Time-series motion of a tagged rigid object | trajectory, path, displacement, speed |

## Common experimental lifecycle

1. The camera is the primary instrument. Every camera capability now uses the same **live HUD → working result → Commit** model.
2. Visible AprilTags are outlined and labelled directly in the image. Where roles matter the HUD labels **TARGET**, **REF** and **MOVING** tags.
3. The HUD shows the values that would be committed now, plus the reason capture is unavailable when geometry is incomplete.
4. Camera taps are handled by a Compose overlay above `PreviewView`, so tag selection and planar point capture are reliable.
5. Settings sit below the instrument and may be fixed by presets or exposed as runtime fields.
6. **Commit** freezes a canonical payload. Live camera changes never silently mutate committed data.
7. Direct/dashboard use then exposes MethodMesh result actions on the same capability flow; external/ODK launches return after Commit.

### Interaction rails

- `apriltag.detect`: Target ID `-1` means **all visible tags**. The full set is returned in `apriltag_tag_ids` and `apriltag_detections_json`; tapping a detected tag switches to single-target mode.
- `apriltag.range_pose`: tap any visible tag to target it. Live range, XYZ, yaw/pitch/roll and detector quality stay in the HUD.
- `apriltag.relative_pose`: explicitly choose **Set reference** or **Set target**, then tap the corresponding visible tag. Both roles and the current relative transform are shown live.
- `apriltag.planar_measure`: tap directly on the live image. Captured points, lines/polygon and working metric result are re-projected into the HUD whenever the reference tag remains visible.
- `apriltag.track_pose`: choose a moving tag and preferably a fixed reference tag by tapping them. The HUD shows readiness, live XYZ, recording state, sample count and path. Fixed-camera mode is available but visibly warns that the phone must not move.

The detector default `quad_decimate` is now `1.0` for robustness with smaller and more oblique tags. Higher decimation remains available where speed matters more than detection margin.

## Scientific validity model

The UI deliberately exposes geometry assumptions and quality evidence rather than presenting camera-derived millimetres as intrinsically authoritative.

- Metric pose requires a known AprilTag **detection-edge** size plus camera intrinsics.
- `tag_size_mm` means the physical distance between the four AprilTag detection corners, **not** the outside edge of the printed paper.
- Automatic camera intrinsics use Android factory metadata where available and are scaled to the CameraX analysis frame. The returned `apriltag_intrinsics_source` and warnings remain part of the result.
- The fallback physical-sensor estimate is explicitly labelled approximate.
- `apriltag.calibrate_focal` is a pragmatic field calibration, not a full lens-distortion calibration.
- Planar measurements are valid only when the tapped object lies on the same physical plane as the reference tag/layout.
- Camera-frame tracking requires the phone/camera to remain physically fixed. A simultaneously visible reference tag is preferable because it gives a stable external frame.
- AprilTag's pose error, Hamming distance, decision margin and camera-model source are retained as audit/quality evidence.

## Example experimental setups

### Snail/animal arena

Place one or more fixed tags on the arena plane. `apriltag.planar_measure` in `trajectory` mode lets the operator tap the animal repeatedly; the committed result contains timestamped millimetre coordinates, path length, duration and mean speed. An optional planar layout maps different visible tag IDs into one shared arena coordinate frame.

### Range target

Print a tag with a measured detection edge (for example 100.00 mm), calibrate/provide camera intrinsics, and use `apriltag.range_pose`. Range, camera-frame XYZ and tag orientation are returned together with camera-model and detector evidence.

### Moving apparatus

Attach a tag to a rigid moving object. Prefer a second fixed reference tag in view. `apriltag.track_pose` records the moving tag in the reference coordinate frame and returns the full time series plus derived path/displacement/speeds.

### Mapping fixed landmarks

`apriltag.relative_pose` measures one tag relative to another when both are visible. Repeated pairwise observations are useful building blocks for room/bench/arena landmark networks. Global graph optimisation / bundle adjustment is intentionally **not** claimed in v0.2; see `ROADMAP_NOTE.md`.

## Native detector dependency

MethodMesh currently provides CameraX but no AprilTag detector. `AprilTagNativeBridge.kt` therefore attempts to load `libmethodmesh_apriltag.so`; if absent, every camera screen fails closed. `native/apriltag_jni.cpp`, `native/CMakeLists.txt.example` and `native/README.md` define the integration boundary without modifying shared app files in this module handoff.

Default family is `tagStandard41h12`; supported families are configurable.

## Permissions and connectivity

- Camera: `android.permission.CAMERA` (already a MethodMesh app permission).
- Network: none.
- Location: none.
- Storage: no automatic persistent study-data storage.

Returned data remain transient unless the user explicitly saves/exports or the caller (for example ODK) owns persistence.

## Module contents

- `AprilTagModule.kt` — module declaration, RIL bindings and settings.
- `AprilTagMethods.kt` — six canonical method contracts and result construction.
- `AprilTagCapabilityScreens.kt` — task-specific live/Commit UI.
- `AprilTagCamera.kt` — CameraX analysis surface and camera model resolution.
- `AprilTagGeometry.kt` — pure geometry, transforms and measurement calculations.
- `AprilTagNativeBridge.kt` — fail-closed JNI boundary.
- `native/` — AprilTag JNI integration material.
- `docs/` — capability-addressable documentation and one-call XLSForm showcases.
