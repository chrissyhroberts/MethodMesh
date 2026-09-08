# GPS target navigator

**Module:** `gpstargetnavigator`  
**Canonical method ID:** `gps_target_navigator`  
**Method version:** `0.4.1`  
**Lane/status:** Production capability migration to MethodMesh v1.05

GPS target navigator is an offline-first navigation instrument for guiding the user to a WGS84 target supplied either as decimal latitude/longitude or as a full Plus Code. The target vector is calculated locally. The phone shows a large instrument-style target compass first, then live distance/status with the current latitude/longitude, then the selected target with its latitude/longitude. Bearing, turn direction, GPS accuracy and arrival state remain available in context and technical details. A separate full-screen camera HUD is available when AR guidance is enabled.

The v1.05 interaction is deliberately navigation-dashboard styled. During active navigation the hierarchy is intentionally **target compass first, distance/current position second, target location third**, followed by compact controls and progressive technical details. The compass uses a dark instrument face with degree ticks, a red north needle and a stronger target vector so the navigation cue reads like a field instrument rather than a generic chart. The destination is set through a single **Set location** action offering latitude/longitude, full Plus Code, or an interactive map picker. Calculated values are tap-to-copy.

## Canonical capability and surface parity

`gps_target_navigator` is the single canonical capability. The dashboard, direct native launch, presets, protocol steps, widgets that launch a preset/protocol, and ODK/XLSForm all project the same method ID, settings and output field names.

- **Dashboard:** normal MethodMesh module discovery provides the module entry point; the module-owned capability screen is the navigation control centre rather than a generic settings/result form.
- **Direct native:** choose a Plus Code or coordinates, start navigation, watch the live working result, then **Commit** the current result.
- **Preset:** all declared settings remain individually available. Destination fields (`target_plus_code`, `target_name`, `target_latitude`, `target_longitude`) are seeded as **runtime inputs by default** when creating a new preset, because a destination is normally run-specific. The preset editor can still tick any/all of them as fixed to create a hard-coded destination preset. Fixed values are hidden at execution; runtime target fields are requested in the same production screen.
- **Protocol:** the capability remains independently selectable as `gps_target_navigator`; Commit returns the frozen payload to the protocol runner.
- **ODK/XLSForm:** ODK can supply either a full Plus Code or coordinates plus arrival radius. The normal route is interactive because physical navigation occurs in MethodMesh. Commit returns directly to ODK without the normal native share/save/Home flow.
- **Widget/schedule:** no module-specific widget implementation exists. Generic preset/protocol/widget or schedule launch uses the same capability contract and shared launch-origin closeout machinery.

The dashboard is a projection of the capability contract, not the implementation boundary.

## Native v1.05 lifecycle

1. The navigator opens as one continuous dashboard: target compass first, distance plus **CURRENT LOCATION latitude/longitude** second, then the **TARGET LOCATION latitude/longitude** card. Press **Set location** (or **Change location**) in that dashboard and choose **Lat / lon**, **Plus Code**, or **Pick from map**. Latitude/longitude and Plus Code entry expand inline beneath the target card; the map picker opens as a state-preserving full-screen overlay with a fixed centre crosshair, live candidate coordinates and a locally calculated full Plus Code, then **Use this location** returns directly to the same navigator dashboard.
2. **Start navigation.** Live GPS fixes update distance, bearing, relative bearing, accuracy and arrival state in place.
3. Optional **AR navigator** opens as a separate full-screen camera/HUD view. The target cue is based on GPS + device heading; the camera does not independently localise the target.
4. **Commit current result** at any time once a location fix exists. If the device is inside the arrival radius the primary action becomes **Commit arrival**.
5. Commit freezes the canonical payload. Native result actions are supplied by the shared MethodMesh result shell; ODK/protocol/widget closeout follows launch origin.

Stopping without Commit clears the active session and does not create a committed result.

## Tap-to-copy

Displayed result values are directly tappable. Clipboard payloads contain the useful value rather than labels, including:

- Plus Code;
- target coordinates;
- current/final coordinates;
- distance;
- bearing;
- heading/relative bearing;
- GPS accuracy;
- update/sample counts;
- committed arrival state.

## Settings / inputs

| Key | Type | Purpose |
|---|---|---|
| `target_plus_code` | text | Optional full Plus Code. Takes precedence over coordinates when non-blank. |
| `target_name` | text | Human-readable destination label. |
| `target_latitude` | float | WGS84 latitude, −90..90. Used when no Plus Code is supplied. |
| `target_longitude` | float | WGS84 longitude, −180..180. Used when no Plus Code is supplied. |
| `arrival_radius_m` | float | Arrival threshold, 1..500 m. |
| `show_current_location` | boolean | Display preference retained for compatibility. |
| `show_bearing` | boolean | Display preference retained for compatibility. |
| `show_distance` | boolean | Display preference retained for compatibility. |
| `show_ar_camera` | boolean | Opens the separate AR camera navigator when appropriate. |

Transport aliases retained for compatibility include `input_*`, `plus_code`, `latitude`/`lat`, `longitude`/`lon`/`lng`, and `arrival_radius` where previously accepted.

### Conditional target rule

A target is valid when either:

- `target_plus_code` contains a decodable full Plus Code; **or**
- both `target_latitude` and `target_longitude` are valid.

`arrival_radius_m` is the remaining required context field. The prior contract incorrectly modelled latitude and longitude as simultaneously mandatory even when a Plus Code was supplied; v0.4.0 corrected that while preserving the method ID and established field names.

### Preset destination policy

A newly created preset should not accidentally bake in the coordinates used while testing the capability. The capability therefore seeds `methodmesh_runtime_fields` with `target_plus_code,target_name,target_latitude,target_longitude` during normal configuration. In the generic preset editor these target rows start unchecked as **Ask at runtime / supplied by ODK**. The operator may explicitly check them to save a fixed target. Existing saved presets and external/ODK invocations are not rewritten.

## Output contract

Every field below is declared by the canonical method and is therefore available for generic projection to protocols/pipes and ODK. The ordinary UI shows the useful subset first.

### Live/current fields

| Field | Meaning | Normally shown |
|---|---|---:|
| `target_name` | Destination label | Yes |
| `target_plus_code` | Full Plus Code when used | Yes |
| `target_latitude`, `target_longitude` | Resolved target coordinates | Yes |
| `current_latitude`, `current_longitude` | Latest location fix | **Yes — explicit Current location latitude/longitude** |
| `accuracy_m` | Latest GPS accuracy | Yes |
| `distance_m` | Current distance to target | Yes |
| `bearing_deg` | Initial bearing from current position to target | Yes |
| `heading_deg` | Device/camera heading used for guidance | Technical details |
| `relative_bearing_deg` | Signed turn angle from device heading to target | Yes |
| `arrived` | Current distance is within `arrival_radius_m` | Yes |
| `timestamp_ms` | Latest navigation timestamp | Technical/audit |
| `update_count` | Number of live GPS updates | Technical details |
| `status` | Capability working/committed status | Yes |
| `arrival_radius_m` | Configured arrival threshold | Technical details |

### Committed outcome fields

| Field | Meaning |
|---|---|
| `capability` | Canonical method ID |
| `event_type` | `navigation_outcome` |
| `navigation_completed` | `true` when committed inside arrival radius |
| `arrival_latitude`, `arrival_longitude` | Position at Commit |
| `arrival_accuracy_m` | Accuracy at Commit |
| `final_distance_m` | Distance at Commit |
| `started_at_ms`, `ended_at_ms` | Navigation timing |
| `duration_seconds` | Active navigation duration |
| `sample_count` | Live GPS sample count |
| `first_fix_latitude`, `first_fix_longitude` | First live fix in the run |
| `last_fix_latitude`, `last_fix_longitude` | Last live fix in the run |
| `min_distance_m` | Minimum observed target distance |
| `mean_accuracy_m` | Mean accuracy across live fixes with accuracy data |
| `max_accuracy_m` | Maximum reported accuracy value across live fixes |

The committed payload also carries the live/current fields so older consumers that read `distance_m`, current coordinates, `arrived`, bearing or status continue to work.

`methodmesh_full_json`, execution IDs, transport status, namespace projection, ClipData and URI-grant handling are shared transport concerns and are not redefined by this module.

## ODK/XLSForm

[`example_odk_gps_target_navigator.xlsx`](example_odk_gps_target_navigator.xlsx) demonstrates:

- grouped intent invocation;
- Plus Code **or** coordinate input;
- `input_*` parameters;
- `input_payload_mode='FULL'`;
- flat return mode;
- `methodmesh_return_namespace='nav'`;
- the complete declared capability result set plus `nav_methodmesh_full_json`.

Typical intent shape:

```text
com.example.methodmesh.EXECUTE_METHOD(
  method_id='gps_target_navigator',
  input_target_name='Clinic',
  input_target_plus_code='6GCRPR6C+24',
  input_arrival_radius_m='50',
  input_payload_mode='FULL',
  return_mode='flat',
  methodmesh_return_namespace='nav'
)
```

When ODK supplies the target, MethodMesh does not force a second setup page. It opens the live navigator. Commit returns the canonical payload directly to ODK; MethodMesh does not create an extra archive copy merely because ODK invoked the capability.

## Persistence and state

Navigation is a long-running field operation, so active working state is module-locally persisted using `GpsTargetNavigatorStateStore`.

Persisted active-session state includes target identity, lifecycle, start time, last position, distance/bearing/heading state, update count and compact trace statistics. It is restored only when the stored target matches the current target. The store is cleared on Commit or Stop.

This is **active-session persistence**, not permanent result storage. Native Commit freezes the result in the execution/result state; it does not automatically record it in `ResearchRuntime.session`. The historical `recordNavigationOutcome(...)` helper remains available only for explicit callers that intentionally want session recording.

## Permissions

- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` — required for navigation.
- `CAMERA` — optional, only for the AR navigator. Refusing camera permission leaves compass/dashboard navigation usable.
- `INTERNET` — required only for the optional online map-picker basemap; core navigation does not require network access.

## Offline / online behaviour

Core navigation is offline-first:

- distance and bearing are calculated locally;
- Plus Code decoding is local and deterministic;
- device heading is local;
- no remote map, geocoding or navigation service is required for coordinate/Plus Code setup or navigation itself;
- **Pick from map** is an optional online convenience surface using OpenFreeMap tiles rendered with MapLibre GL JS in an Android WebView; if it is unavailable, latitude/longitude and Plus Code setup remain fully usable offline.

GPS itself may take longer to obtain a fix depending on device/environment. This module does not upload location to a remote provider.

## Dependencies

- MethodMesh AS1.00 method/runtime types;
- MethodMesh settings and workflow UI contracts;
- Android/Google Play Services Fused Location Provider;
- MethodMesh `PhoneSensorRepository`;
- MethodMesh `LiveCameraPreview`;
- `OpenLocationCode` decoder currently supplied by the `pluscodecapture` module.

No new shared-app capability-specific special case is required by this migration. The optional map picker uses the Android platform WebView and therefore does not introduce a new Gradle map SDK dependency. It loads MapLibre GL JS at runtime and OpenFreeMap map data when the user opens that picker.

## Validation status

See [`VALIDATION.md`](VALIDATION.md). The supplied handoff is a module-only archive, so a full Gradle app build cannot be executed in isolation. Static contract checks and XLSForm structure checks were performed; integration build/testing must be run after dropping the folder into the MethodMesh repository.
