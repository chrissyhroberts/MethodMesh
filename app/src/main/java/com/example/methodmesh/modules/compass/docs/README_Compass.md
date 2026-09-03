# Compass

`compass.read` is a Development-stage MethodMesh navigation/orientation capability for reading a magnetic heading and sighting either magnetic North or a manually configured bearing.

**Status:** Development  
**Module:** `compass`  
**Method:** `compass.read`  
**Version:** `0.1.0`

## What it does

- shows a polished flat-phone compass with a large numeric heading and 16-point cardinal direction;
- uses the existing shared `PhoneSensorRepository` rather than implementing a second orientation stack;
- offers a vertical **Sight target** mode using the shared rear-camera optical-axis heading already used by GPS target navigation;
- optionally displays the shared `LiveCameraPreview` behind the sighting overlay;
- shows a thick central sighting ring that turns green when the phone is within a configurable angular tolerance of the target;
- targets either **magnetic North (0°)** or a manually entered bearing from 0–359.9°;
- captures a compact human-readable main result plus structured fields and audit JSON;
- runs fully offline.

## Design / reuse

The module intentionally does not copy the GPS navigator's private implementation. It consumes shared platform boundaries that already exist in MethodMesh:

- `PhoneSensorRepository.headingDegrees` for the normal flat-phone compass;
- `PhoneSensorRepository.rearCameraHeadingDegrees` for vertical sighting along the rear-camera optical axis;
- `PhoneSensorRepository.pitchDegrees` and `rollDegrees` for capture metadata;
- `PhoneSensorRepository.readings["magnetometer"]?.accuracy` for Android sensor-accuracy metadata;
- `LiveCameraPreview` for the optional rear-camera background.

The existing phone-sensor repository prefers Android's rotation-vector sensor and falls back to accelerometer + magnetometer orientation when required. This Compass module does not add another sensor listener or orientation algorithm.

## North reference

v0.1 reports **magnetic north**. It does not request location and does not apply magnetic declination.

This is deliberate: a basic compass should remain a sensor-only, offline capability and should not silently acquire precise location merely to label the result "true north". A future true-north option can consume an existing public location fix and Android geomagnetic declination without changing the meaning of v0.1 results.

Every result therefore contains:

```text
compass_north_reference = magnetic
```

## Native workflow

1. Open **Compass**.
2. Choose the target:
   - **North**; or
   - **Bearing**, then enter 0–359.9°.
3. Optionally adjust the green-zone tolerance. Default is ±5°.
4. Hold the phone flat to use the main compass display.
5. Press **Capture bearing** to save the flat-phone reading.
6. Or press **Sight target** and hold the phone vertically like a camera.
7. In sighting mode, put the central ring over the object/line you are sighting. The thick ring turns green when the rear-camera optical axis is within tolerance of the target bearing.
8. Press **Capture bearing** or **Capture aligned bearing**.

The camera background is optional. If camera permission is denied or camera display is disabled, the sighting reticle still works on a dark background because heading comes from the orientation sensors, not from image analysis.

## Preset workflow

The capability declares all configuration through `capabilitySettings()`:

- target mode;
- target bearing;
- alignment tolerance;
- whether to show the camera in sighting mode;
- whether to start directly in sighting mode.

When a native preset fixes a setting, the capability uses `settingShouldBeShown(...)` so that fixed values do not need to be re-entered. Runtime inputs remain available before capture.

Useful presets include:

- **Sight North** — target fixed to North, sighting mode starts automatically;
- **Transect 090°** — target fixed to 90°, tolerance fixed to ±3°;
- **Bearing capture** — target bearing left as runtime input.

## ODK / XLSForm workflow

ODK calls the capability through a group intent. The supplied `example_odk_Compass.xlsx` demonstrates both North and manual-bearing targets.

Example manual-bearing call:

```text
com.example.methodmesh.EXECUTE_METHOD(
  method_id='compass.read',
  input_target_mode='bearing',
  input_target_bearing_deg=${target_bearing},
  input_alignment_tolerance_deg=${tolerance_deg},
  input_show_camera_in_sight='true',
  input_start_in_sight_mode='true',
  input_payload_mode='FULL',
  return_mode='flat'
)
```

The user completes the live sighting/capture in MethodMesh and the selected CORE fields return to the XLSForm. The example also stores `methodmesh_full_json`, which is the standard MethodMesh FULL envelope containing background metadata including the capability audit JSON. The capability does not force ODK through an additional native configuration dialog.

## Inputs

| Input | Type | Meaning | Default |
|---|---|---|---|
| `target_mode` | choice | `north` or `bearing` | `north` |
| `target_bearing_deg` | float | Manual target bearing, 0–359.9° | `0` |
| `alignment_tolerance_deg` | float | Green-zone half-width in degrees | `5` |
| `show_camera_in_sight` | boolean | Show rear-camera preview behind reticle | `true` |
| `start_in_sight_mode` | boolean | Open sighting view immediately | `false` |

`target_bearing_deg` is ignored when `target_mode=north`; the effective target is 0°.

## Outputs

| Field | Role | Meaning |
|---|---|---|
| `compass_result` | **main/core** | Compact human-readable result, e.g. `091° E · 90.0° · On target`. |
| `compass_heading_deg` | core | Captured magnetic heading in degrees, normalised to [0,360). |
| `compass_cardinal` | core | 16-point direction such as `N`, `ENE`, `SW`. |
| `compass_target_mode` | core | `north` or `bearing`. |
| `compass_target_bearing_deg` | core | Effective target bearing; North is `0.0`. |
| `compass_error_deg` | core | Signed shortest error. Positive = turn right/clockwise; negative = turn left. |
| `compass_abs_error_deg` | core | Absolute angular error. |
| `compass_aligned` | core | `true` when absolute error is within tolerance. |
| `compass_tolerance_deg` | audit/core | Configured alignment tolerance. |
| `compass_view_mode` | audit | `flat` or `sight`. |
| `compass_heading_axis` | audit | `device_top_edge` or `rear_camera_optical_axis`. |
| `compass_north_reference` | audit/core | Always `magnetic` in v0.1. |
| `compass_pitch_deg` | audit | Phone pitch at capture when available. |
| `compass_roll_deg` | audit | Phone roll at capture when available. |
| `compass_magnetometer_accuracy` | audit | Raw Android sensor accuracy integer when available. |
| `compass_captured_time_iso` | audit | UTC ISO timestamp. |
| `compass_audit_json` | audit metadata | Complete capture metadata and versioning inside the raw execution result and therefore inside `methodmesh_full_json` when FULL payload mode is requested. |
| `compass_status` | status | `succeeded` or `failed`. |
| `compass_error` | error | Failure detail. |

## Main result

Native sharing should use `compass_result` only. Example:

```text
091° E · 90.0° · On target
```

The verbose sensor/posture/provenance information belongs in the raw `compass_audit_json` and the standard `methodmesh_full_json` envelope rather than the primary share action. The capability deliberately supplies only `compass_result` to the native result preview, so the shared **Share result** action remains compact without adding a compass-specific rule to the central UI.

## Audit JSON

`compass_audit_json` is a raw capability output. In the current MethodMesh transport contract, an XLSForm requesting `payload_mode=FULL` should retain `methodmesh_full_json` as the background audit field rather than expecting a separate flat `compass_audit_json` column.

The audit JSON records at least:

- method ID and version;
- alignment algorithm version;
- explicit magnetic-north reference;
- captured heading and cardinal direction;
- target mode and effective target bearing;
- signed and absolute angular error;
- alignment tolerance and aligned flag;
- flat vs sighting mode;
- heading axis used;
- pitch and roll where available;
- Android magnetometer accuracy value where available;
- capture timestamp;
- shared sensor boundary identifier;
- `network_used=false`;
- `location_used=false`.

## Permissions and services

### Orientation sensors

No runtime permission is required for the Android motion/magnetic sensors used through `PhoneSensorRepository`.

### Camera

`android.permission.CAMERA` is required only when the optional camera background is shown in sighting mode. The current MethodMesh host manifest already declares this permission, so the compass drop-in requires no manifest edit. The module owns the runtime permission request. Denial does not disable compass/sighting calculations; it falls back to a dark sighting background.

No location permission is requested in v0.1.

## Offline / online behaviour

**Fully offline.**

The capability performs no web request, cloud processing, telemetry upload or map lookup.

## Known limitations — Development

1. v0.1 reports magnetic rather than true north.
2. Phone magnetometers are vulnerable to nearby steel, magnets, speakers, cases and electrical equipment. The UI therefore displays Android's sensor-accuracy state but does not claim survey-grade accuracy.
3. Android device heading accuracy varies by hardware and calibration state.
4. The sighting reticle tests horizontal bearing only. It is not an inclinometer or full 3D aiming solution.
5. The rear-camera optical-axis heading becomes undefined when the camera points nearly straight up/down; the shared repository correctly returns no sight heading in that geometry.
6. Camera imagery is display-only and is not captured, persisted or analysed.
7. The complete Android `./gradlew :app:assembleDebug` build still needs to be run in a complete current MethodMesh checkout.
8. Physical-device testing should compare the flat and camera-axis modes against a known compass/bearing reference across several headings.
9. Native preset behaviour and orientation recreation need device validation.
10. The example XLSForm needs an ODK Collect round-trip test.

## Production checklist

Keep the capability **Development** until:

1. `./gradlew :app:assembleDebug` passes in the complete repository.
2. Flat compass and sighting mode are tested on multiple Android devices.
3. Heading behaviour is checked at 0/90/180/270° and intermediate bearings.
4. Sighting mode is verified with the phone held vertically in portrait and landscape where supported.
5. Camera permission denial and retry are tested.
6. Sensor-unavailable / unreliable states are tested.
7. Result state survives orientation changes.
8. Native preset fixed/runtime fields behave correctly.
9. ODK example completes a full launch/capture/return round trip.
10. Main share returns `compass_result` rather than the full audit JSON.

## Roadmap

Potential follow-ons:

- optional **true north** using an explicit public location fix + geomagnetic declination, never silent location acquisition;
- bearing lock / hold function;
- configurable haptic/audio cue when alignment enters the green zone;
- inclinometer / slope measurement as a separate method rather than overloading compass bearing;
- direct "bearing to current GPS target" composition through the public GPS navigation boundary.

## Canonical delivery folder

Copy the complete folder:

```text
app/src/main/java/com/example/methodmesh/modules/compass/
```

No central module registration or shared dashboard special case is required.
