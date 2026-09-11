# Weather ODK / XLSForm integration

This document is module-specific implementation guidance. Project-wide ODK behaviour is governed by the MethodMesh Master Book v1.08 FINAL.

## Contract

ODK invokes the same canonical Weather methods used by native MethodMesh, Presets and Protocols. The showcase workbooks use the current MethodMesh single-invocation pattern:

**v0.2.4 preset-authoring alignment:** no Weather method ID, canonical input, canonical output or XLSForm changed. The restored Save-as-preset UI is a native/manual surface concern and is not exposed during automatic-return ODK execution.

**v0.2.5 launch hardening:** ODK remains contractually unchanged. Intent-launched Weather screens now rely on `ExternalWorkflowActivity` for vertical scrolling instead of nesting a second root scroller. This is a host-surface stability fix only; method IDs, inputs, outputs, `methodmesh_status`, `methodmesh_full_json`, FULL/flat return mode and the eleven showcase XLSForms are unchanged.

- one `begin_group` row with `appearance=field-list`;
- the Android action in that group's `body::intent`;
- caller values passed as `input_*` extras;
- direct group-child canonical return names;
- `input_payload_mode='FULL'`;
- `return_mode='flat'`;
- no `methodmesh_return_namespace`;
- shared `methodmesh_status` and `methodmesh_full_json` return questions.

Latitude and longitude are placed before the intent group so the sample call can execute headlessly and return without requiring MethodMesh-side GPS interaction.

## Canonical showcase workbooks

| Workbook | Method |
|---|---|
| `example_odk_showcase_weather_dashboard.xlsx` | `weather.dashboard` |
| `example_odk_showcase_weather_conditions.xlsx` | `weather.conditions` |
| `example_odk_showcase_weather_forecast.xlsx` | `weather.forecast` |
| `example_odk_showcase_weather_precipitation.xlsx` | `weather.precipitation` |
| `example_odk_showcase_weather_radar.xlsx` | `weather.radar` |
| `example_odk_showcase_weather_meteogram.xlsx` | `weather.meteogram` |
| `example_odk_showcase_weather_wind.xlsx` | `weather.wind` |
| `example_odk_showcase_weather_atmosphere.xlsx` | `weather.atmosphere` |
| `example_odk_showcase_weather_sun.xlsx` | `weather.sun` |
| `example_odk_showcase_weather_snapshot.xlsx` | `weather.snapshot` |
| `example_odk_showcase_weather_model_compare.xlsx` | `weather.model_compare` |

The workbooks deliberately expose all canonical return fields for their method, even when an everyday form would only retain a small subset. A production study form can retain only the fields required by its protocol plus `methodmesh_status`/`methodmesh_full_json` as appropriate to the study's audit design.

## Example intent

The Conditions showcase uses the following pattern:

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='weather.conditions',input_latitude=${req_latitude},input_longitude=${req_longitude},input_target_time_iso=${req_target_time_iso},input_offline_only=${req_offline_only},input_payload_mode='FULL',return_mode='flat')
```

The exact input list differs by capability. Use the shipped workbook rather than copying this example blindly.

## Research snapshot

`weather.snapshot` is the preferred Weather capability when an XLSForm needs environmental context tied to an event timestamp.

Inputs:

- `input_latitude`
- `input_longitude`
- `input_target_time_iso`
- `input_source_policy`

Supported source policies:

- `best_available`
- `reanalysis`
- `historical_forecast`
- `forecast`

The output includes requested/query/provider-grid coordinates, requested and resolved valid times, source class, meteorological scalars, provider and audit JSON.

Important interpretation rule: `REANALYSIS`, `HISTORICAL_FORECAST` and `FORECAST` are model-derived evidence classes. They are not silently relabelled as station observations.

## Radar

`weather.radar` returns a selected provider radar frame plus timeline metadata. Its principal fields include:

- `weather_radar_frame_time_iso`
- `weather_radar_frame_class`
- `weather_radar_tile_template`
- observed and provider-nowcast frame counts
- `weather_radar_timeline_json`
- freshness/provider/audit fields.

If RainViewer supplies no `nowcast` frames, the nowcast count is zero. MethodMesh does not manufacture future radar imagery from point precipitation forecasts.

## Flat + full JSON

All showcase forms request both flat values and the full MethodMesh payload:

- flat fields make common Weather values directly usable in form logic and exports;
- `methodmesh_full_json` preserves the wider canonical execution/provenance object for auditing or downstream processing.

Do not add an XLSForm-only Weather return field that is absent from the method descriptor. If a new canonical Weather field is needed, update the method contract first and regenerate/review the workbook.

## ODK validation status

The canonical workbooks were initially generated in v0.1.0 and are unchanged in v0.2.0 because no method IDs, inputs or return contracts changed. They were re-imported with `artifact_tool`; structural audit confirmed for all 11 workbooks:

- exactly one MethodMesh intent;
- correct canonical method ID;
- `input_payload_mode='FULL'`;
- `return_mode='flat'`;
- no return namespace;
- all declared capability output fields present;
- `methodmesh_status` present;
- `methodmesh_full_json` present;
- no duplicate survey node names;
- no spreadsheet formula-error tokens.

`pyxform` is not installed in the packaging environment, so JavaRosa/ODK Validate, ODK Central upload and Kobo upload remain receiving-repository checks rather than claimed validation here.


**v0.2.6 radar presentation patch:** no ODK contract change. Radar camera initialisation and frame rendering are native UI internals only; all eleven XLSForms remain unchanged.
