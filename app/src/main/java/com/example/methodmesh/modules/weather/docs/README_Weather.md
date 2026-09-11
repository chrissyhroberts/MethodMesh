# MethodMesh Weather

**Module version:** 0.2.6  
**Normative project authority:** MethodMesh Master Book v1.08 FINAL, 2026-09-08

Weather is a self-contained MethodMesh meteorological module. It combines a high-information native dashboard with independently invokable canonical weather capabilities for direct use, Presets, Protocols and ODK/XLSForm.

The dashboard is an aggregation surface, not a parallel implementation. Every useful operation below remains independently callable.

## Public capability inventory

| Method | Maturity | Connectivity | Purpose |
|---|---|---|---|
| `weather.dashboard` | Development | ONLINE_OFFLINE | Integrated live weather dashboard and committable dashboard snapshot |
| `weather.conditions` | Development | ONLINE_OFFLINE | Current or selected-time point conditions |
| `weather.forecast` | Development | ONLINE_OFFLINE | Hourly forecast plus compact daily forecast series |
| `weather.precipitation` | Development | ONLINE_OFFLINE | Quantitative precipitation timing, thresholds and accumulation |
| `weather.radar` | Development | ONLINE_OFFLINE | Observed radar history and genuine provider nowcast frames |
| `weather.meteogram` | Development | ONLINE_OFFLINE | Detailed meteorological time-series inspection |
| `weather.wind` | Development | ONLINE_OFFLINE | Wind direction, sustained speed and gusts |
| `weather.atmosphere` | Experimental | ONLINE_OFFLINE | GFS pressure-level and convective meteorology |
| `weather.sun` | Development | ONLINE_OFFLINE | Sunrise, sunset, daylight, sunshine and UV |
| `weather.snapshot` | Development | ONLINE_ONLY | Timestamped research weather context with explicit source class |
| `weather.model_compare` | Experimental | ONLINE_ONLY | Ensemble spread inspection; not a calibrated confidence interval |

## Native Weather dashboard

`weather.dashboard` is designed as a meteorological instrument rather than a generic consumer-weather screen. It provides:

- GPS or manual latitude/longitude directly on the dashboard;
- current condition hero with proper drawn weather glyphs;
- compact two-column **Now / Today** summary containing feels-like, high/low, rain now/today, humidity, dew point, pressure, wind and gust without repeating these later;
- quantitative next-24-hour precipitation bars with labelled time/value axes and next meaningful-rain time;
- native MapLibre radar with explicit page-scroll vs **Move map** interaction mode, observed/nowcast timeline, Play/Stop animation and in-map time/source overlay;
- clearly labelled **Hourly temperature** chart with numeric y axis and time x axis;
- horizontally scrollable multi-day forecast cards;
- labelled 72-hour meteogram charts for temperature, pressure and wind;
- dashboard panels for Sun & daylight, detailed atmosphere, ensemble/model comparison and the current research Weather Snapshot;
- explicit cache/freshness and provider state;
- an explicit **Commit dashboard snapshot** boundary.

The radar timeline displays RainViewer `past` frames as `RADAR_OBSERVATION`. Provider `nowcast` frames are displayed as `NOWCAST` only when the provider actually supplies them. Model forecast precipitation remains a separate product and is never labelled radar.


## v0.2.1 radar context patch

The default radar camera now opens at approximately **zoom 5.0** rather than 6.0. This gives a national-scale starting view around the selected location (roughly whole-UK context on a typical phone panel) while preserving normal pan/zoom after the operator enters **Move map** mode. The same default is used by the dashboard, standalone radar capability, Presets and headless/ODK execution unless an explicit `input_zoom` overrides it. No canonical method ID, input name or output contract changed.

## v0.2.0 dashboard and snapshot refresh

The v0.2.0 UI pass responds to field testing of the first dashboard build. It removes the repeated Today summary and the redundant selected-hour rain/wind table, tightens the headline metrics into a two-column grid, labels chart axes, makes radar/page gestures explicit, adds animated radar playback with a timestamp overlay, and promotes the strongest advanced Weather capabilities into the dashboard.

`weather.snapshot` continues to accept a typed ISO-8601 timestamp, and its native surface now also provides a calendar + time picker. Picker selections are interpreted in the device timezone and stored as an unambiguous UTC ISO timestamp before execution.

## Working result and Commit

Native Weather screens distinguish the live working result from the committed result:

1. changing location, time, horizon, source policy or radar frame creates/refreshes a working result;
2. the operator can inspect and tap displayed useful values to copy them;
3. **Commit result** freezes the currently resolved canonical payload and the settings used to produce it;
4. the committed result remains visible even if the working settings/result subsequently change;
5. Copy, Share, Save, optional full JSON and Done operate on that frozen payload;
6. **Recommit** explicitly replaces the frozen payload;
7. external automatic-return origins continue to return the canonical result through the existing MethodMesh transport without a generic result-screen detour.

For radar, moving the timeline scrubber re-resolves the canonical frame before Commit. The displayed frame and returned `weather_radar_frame_time_iso` therefore refer to the same provider frame.

## Preset authoring

Weather uses immersive, capability-owned screens, so it cannot rely on the Standard host wrapper to inject preset controls. v0.2.4 restores the Master Book preset workflow directly in the polished Weather surface while still using MethodMesh's shared preset contract and repository.

**Save current setup as preset** is available on the Weather dashboard and every individual Weather capability during direct/manual use. The authoring dialog lets the operator:

- choose which declared settings are **Fixed** and which are **Ask when run** runtime inputs;
- choose returned payload: Core, Core + audit or Everything;
- choose post-result behaviour: Home, Share or Save;
- optionally keep runs in a persistent Files log;
- name and save the preset.

Runtime fields are serialized using the standard `methodmesh_runtime_fields` mechanism. When a native preset contains runtime fields, Weather now pauses before acquisition and asks for those fields; fully fixed presets retain immediate execution. Fixed fields remain hidden during the native preset run through `CapabilityScreenContext.settingShouldBeShown(...)`.

## Location and privacy

The module preserves three different location concepts where available:

- **requested location** — the coordinate supplied by the caller or captured from GPS;
- **query location** — the coordinate disclosed to the online provider after MethodMesh privacy transformation;
- **provider grid location** — the grid coordinate returned by the weather product.

Open-Meteo definitions use MethodMesh `LocationDisclosureMode.ROUNDED` with `roundedLocationRadiusMeters = 5000`. The module also reports the rounded query coordinate in the canonical result/audit record rather than implying that the exact requested GPS fix was sent upstream.

RainViewer radar timeline metadata do not require a location query. Location is used locally to centre the radar map.

## Online-data architecture

Weather does not implement a parallel network stack. Provider calls use the existing generic MethodMesh online-data primitives:

- `ApiDefinition`;
- `ApiGetExecutor`;
- `ApiGetRequest`;
- `HttpUrlConnectionOnlineHttpClient`;
- `SharedApiResultCache`;
- `ApiPrivacy` / `LocationDisclosureMode`;
- `roundLocationForDisclosure`;
- structured `ResultTree` responses.

The module owns weather-specific provider declarations and mapping logic. Shared MethodMesh UI/runtime code does not branch on Weather semantics.

### Provider definitions

Open-Meteo definitions cover:

- current/hourly/daily forecast;
- historical weather/reanalysis;
- historical-forecast reconstruction;
- GFS pressure-level fields;
- ensemble fields.

RainViewer provides radar timeline metadata and raster tiles. OpenFreeMap provides the default basemap used by the native radar map.

See `THIRD_PARTY_NOTICES.md` for attribution and provider-use constraints.

## Evidence/source classes

Weather distinguishes the provenance class of values rather than collapsing them into “weather data”:

- `MODELLED_CURRENT`
- `FORECAST`
- `REANALYSIS`
- `HISTORICAL_FORECAST`
- `RADAR_OBSERVATION`
- `NOWCAST`
- `ENSEMBLE_FORECAST`
- `DERIVED`

A historical model reconstruction is not described as a station observation. `weather.snapshot` is deliberately explicit about the selected source policy and valid time.

### Historical forecast caveat

Open-Meteo's Historical Forecast API provides archived model-derived time series and is useful for reconstructing recent model conditions. The module does **not** claim that this is necessarily the exact forecast a user saw at a particular issuance time/lead time. That distinction belongs in downstream research interpretation.

## ODK / XLSForm

`docs/` contains one canonical single-invocation showcase workbook for every public Weather method:

- `example_odk_showcase_weather_dashboard.xlsx`
- `example_odk_showcase_weather_conditions.xlsx`
- `example_odk_showcase_weather_forecast.xlsx`
- `example_odk_showcase_weather_precipitation.xlsx`
- `example_odk_showcase_weather_radar.xlsx`
- `example_odk_showcase_weather_meteogram.xlsx`
- `example_odk_showcase_weather_wind.xlsx`
- `example_odk_showcase_weather_atmosphere.xlsx`
- `example_odk_showcase_weather_sun.xlsx`
- `example_odk_showcase_weather_snapshot.xlsx`
- `example_odk_showcase_weather_model_compare.xlsx`

Each workbook uses exactly one grouped `body::intent` invocation, `input_payload_mode='FULL'`, `return_mode='flat'`, canonical unprefixed Weather return fields, `methodmesh_status` and `methodmesh_full_json`, and no `methodmesh_return_namespace`.

v0.2.4 changes no Weather method ID, canonical input or canonical output. Preset authoring is native-only and is suppressed for automatic-return callers such as ODK, so the eleven existing showcase contracts remain valid.

See `ODK_INTEGRATION.md`.

## Direct/headless execution

The canonical methods can execute without opening their native screen when the runtime supplies sufficient inputs, notably latitude and longitude. `WeatherMethodBase.execute()` resolves the same provider/data mapping path used by the native surface and returns the same declared output contract.

This is the intended route for direct ODK, Preset and Protocol execution. The native screen remains available when location acquisition or interactive inspection/Commit is required.

## Offline behaviour

Development capabilities tagged `ONLINE_OFFLINE` support MethodMesh's cache-only/fallback execution mode using the shared online-data cache. Cached results are explicitly labelled via the `*_from_cache` and `*_data_age_hours` fields.

A cached result is never silently presented as current. The current shared cache is disposable runtime cache, not a downloaded durable weather archive; callers needing defensible historical research context should use `weather.snapshot` while online and persist the returned research record in their own workflow.

## Installation / handoff

Place this complete folder at:

```text
app/src/main/java/com/example/methodmesh/modules/weather/
```

Do not split its UI, provider definitions or documentation into shared app folders. MethodMesh module discovery/index generation and repository-level asset generation remain host-repository responsibilities.

No shared-framework Weather special case is required.
