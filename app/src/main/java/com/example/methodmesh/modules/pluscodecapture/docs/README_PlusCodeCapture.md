# Plus Code capture

`plus_code.capture` captures a full Google Plus Code / Open Location Code (OLC) by combining GPS with a user-selected grid cell.

The capability is intended for field data collection where GPS gives an approximate position, but the enumerator needs to select the actual house, compound, facility, sampling point, or landmark on a map.

## Core behaviour

1. Acquire and average Android GPS fixes.
2. Centre the selector on the GPS-derived OLC cell.
3. Show a locally generated Plus Code grid over a map.
4. Let the user tap the intended cell, or move the map with fine/coarse D-pads.
5. Return the full, globally self-contained Plus Code.

OLC encoding/decoding is calculated locally. No Google Maps API, Plus Codes API, what3words service, geocoder, or lookup database is required to generate the code.

## Map modes

- `auto` — online street map tiles using OpenFreeMap.
- `satellite` — online satellite imagery using Esri World Imagery.
- `blank` — grid only.

Street and satellite tiles are contextual only. They help the user identify the intended cell, but the selected Plus Code is derived from latitude/longitude locally.

The selector currently defaults to online street/satellite use because offline tile-pack download is not yet implemented. The grid-only mode remains available as a no-basemap fallback.

At the tightest `9×9` view, satellite mode falls back to street tiles where imagery is unavailable or overly sparse.

## Preset and native app behaviour

When run directly from the capability screen, the user can configure:

- map mode;
- Plus Code length;
- initial grid width;
- GPS averaging duration.

When run from a saved preset, the configuration panel is hidden. The capability:

1. requests location permission if needed;
2. starts GPS acquisition;
3. opens the full-screen selector;
4. submits the selected cell when the user presses **Use this cell**.

## ODK/XLSForm behaviour

ODK calls should pass fixed configuration values and receive the selected location fields.

Example intent:

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='plus_code.capture',input_code_length='10',input_gps_average_seconds='10',input_basemap_mode='auto',input_grid_span_cells='129',return_mode='flat')
```

The example workbook is [`example_odk_plus_code.capture.xlsx`](example_odk_plus_code.capture.xlsx).

## Outputs

Main result:

- `plus_code`

Useful location fields:

- `plus_code_centroid_latitude`
- `plus_code_centroid_longitude`
- `plus_code_gps_latitude`
- `plus_code_gps_longitude`
- `plus_code_gps_accuracy_m`
- `plus_code_gps_fix_count`
- `plus_code_selected_time_iso`

Audit/configuration fields:

- `plus_code_status`
- `plus_code_length`
- `plus_code_basemap_mode`
- `plus_code_basemap_actual_source`
- `plus_code_audit_json`
- `plus_code_error`

The audit JSON contains the selected cell bounding box, selected centroid, GPS fix metadata, basemap mode/source, timestamp, and status.

## Precision

The initial default is a 10-character full Plus Code. This is suitable for small-site or building-scale selection in many field contexts. The grid width changes how much surrounding context is visible; it does not reduce the stored Plus Code precision.

## Offline design note

The important offline guarantee is the OLC calculation itself. Basemap tiles are optional context. Future work can add offline tile-pack download/cache management without changing the Plus Code calculation or output schema.
