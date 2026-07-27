# GPS target navigation

Tracks the device relative to a configured geographic target and records navigation evidence.

## Capabilities

### `gps_target_navigator`

Uses the Android location device service and compass sensors to calculate distance, bearing, relative bearing, and arrival state. The manual screen permits direct coordinate entry and a **Quick target: use current position** action.

## Android intent

```text
com.example.researchos.EXECUTE_METHOD(method_id='gps_target_navigator',target_name='Clinic',target_latitude='-1.28',target_longitude='36.81',arrival_radius_m='50')
```

## Inputs

| Input | Required | Description |
|---|---:|---|
| `target_latitude` | Yes | Decimal latitude from −90 to 90. |
| `target_longitude` | Yes | Decimal longitude from −180 to 180. |
| `arrival_radius_m` | Yes | Arrival threshold in metres. |
| `target_name` | No | User-facing target label. |
| `show_current_location`, `show_bearing`, `show_distance` | No | Display controls. |

Location permission is requested through Android when necessary.

## Outputs

`target_name`, target and current coordinates, `accuracy_m`, `distance_m`, `bearing_deg`, `heading_deg`, `relative_bearing_deg`, `arrived`, `timestamp_ms`, `update_count`, and `status`. Saved outcomes also include navigation timing and trace evidence.

## ODK example

[`example_odk_GpsTargetNavigator.xlsx`](example_odk_GpsTargetNavigator.xlsx) accepts target coordinates and an arrival radius, launches navigation, and receives the resulting location fields.
