# GPS target navigation

Tracks the device relative to a configured geographic target and records navigation evidence.

## Capabilities

### `gps_target_navigator`

Uses the Android location device service and orientation sensors to calculate
distance, bearing, relative bearing, and arrival state. By default, these are
overlaid on the live rear-camera view as a lightweight, offline AR navigator.
The camera does not estimate the target position independently: GPS accuracy is
always displayed alongside the target cue. A compass-only view remains
available on devices without camera permission. The manual screen permits
direct coordinate entry and a **Quick target: use current position** action.

External calls start live navigation as soon as location permission is
available. Interim fixes, including `arrived=false`, are display state and are
never returned as completed results. After the target first enters the arrival
radius, high-accuracy updates continue refining the distance, position, and
accuracy until the user selects **Save navigation result**. Saving returns
automatically to the calling app. Manual/debug launches retain the separate
**Use result** confirmation.

## Android intent

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='gps_target_navigator',input_target_name='Clinic',input_target_plus_code='6GCRPR6C+24',input_arrival_radius_m='50',input_show_ar_camera='true')
```

## Inputs

| Input | Required | Description |
|---|---:|---|
| `target_plus_code` | Conditional | Full Plus Code destination. Use this instead of coordinates when available. |
| `target_latitude` | Conditional | Decimal latitude from −90 to 90. Required when no Plus Code is supplied. |
| `target_longitude` | Conditional | Decimal longitude from −180 to 180. Required when no Plus Code is supplied. |
| `arrival_radius_m` | Yes | Arrival threshold in metres. |
| `target_name` | No | User-facing target label. |
| `show_current_location`, `show_bearing`, `show_distance` | No | Display controls. |
| `show_ar_camera` | No | Shows the live camera with target, turn, distance, and accuracy overlays. Defaults to `true`; set to `false` for compass-only navigation. |

Location and camera permissions are requested through Android when necessary.
Refusing camera permission does not prevent compass navigation.

## Outputs

`target_name`, target and current coordinates, `accuracy_m`, `distance_m`, `bearing_deg`, `heading_deg`, `relative_bearing_deg`, `arrived`, `timestamp_ms`, `update_count`, and `status`. Saved outcomes also include navigation timing and trace evidence.

## ODK example

[`example_odk_gps_target_navigator.xlsx`](example_odk_gps_target_navigator.xlsx) accepts either a full Plus Code or target coordinates plus an arrival radius, launches navigation, and receives the resulting location fields.
