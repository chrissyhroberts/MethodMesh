# MethodMesh Weather Module Specification v0.02

**Module:** Weather  
**Canonical namespace:** `weather`  
**Specification version:** v0.02  
**Date:** 2026-09-10  
**Normative project basis:** `docs/METHODMESH_MASTER_BOOK.md` — **v1.08 FINAL**, 2026-09-08  
**Reference implementation / visual ancestor:** `chrissyhroberts/weather_app_android` (“KeylessWeather”)  
**Canonical implementation root:** `app/src/main/java/com/example/methodmesh/modules/weather/`  
**Handoff rule:** return exactly one clean `weather/` module root; never a whole-app tree.

---

# 0. Authority and status of this document

This is a **module-local implementation specification** for MethodMesh Weather.

It does not override the MethodMesh Master Book. If this specification conflicts with the current canonical Master Book, the Master Book wins.

The authoritative project-wide source used for this revision is:

```text
MethodMesh/docs/METHODMESH_MASTER_BOOK.md
Version: v1.08
Status: FINAL
Last updated: 2026-09-08
```

This document is intended to live eventually under the Weather module's own `docs/` directory and to describe Weather-specific:

- method IDs;
- settings;
- runtime behaviour;
- outputs;
- UI;
- online providers;
- state;
- ODK integration;
- example XLSForms;
- tests;
- validation;
- implementation notes.

Project-wide doctrine must remain in the Master Book.

---

# 1. v0.02 changes from v0.01

v0.02 is a conformance rewrite rather than a cosmetic edit.

Key changes:

1. explicitly targets Master Book v1.08;
2. adds requirement-ID traceability;
3. adds maturity/connectivity tags to the module and every capability;
4. replaces the proposed parallel Weather networking engine with MethodMesh's existing declarative online-data infrastructure;
5. requires Weather-owned API definitions to execute through the generic online-data machinery;
6. applies the v1.08 location-privacy rule: third-party weather queries default to approximately 5 km rounded location disclosure where appropriate;
7. distinguishes:
   - exact requested device/user location;
   - remote disclosure/query location;
   - provider/model grid location;
8. adopts the current ODK Integration Card format;
9. adopts canonical intent syntax:
   `com.example.methodmesh.EXECUTE_METHOD(...)`;
10. requires `input_payload_mode='FULL'` and `return_mode='flat'` in canonical showcases;
11. renames canonical example XLSForms to `example_odk_showcase_<purpose>.xlsx`;
12. requires exactly one MethodMesh invocation per canonical showcase;
13. requires canonical unprefixed return keys;
14. forbids `methodmesh_return_namespace` in canonical showcases;
15. requires `methodmesh_status` and `methodmesh_full_json` on every handled ODK roundtrip;
16. separates disposable weather cache from intentionally downloaded future offline resources;
17. aligns Commit, Home/Done/Cancel and post-Commit editing with v1.08;
18. explicitly prevents weather-specific facts from leaking into shared UI/runtime code;
19. adds Master Book review/Definition-of-Done checks;
20. locks the canonical module handoff boundary to one `weather/` folder.

---

# 2. Purpose

Weather is a first-class MethodMesh module for obtaining, inspecting, visualising, contextualising, capturing and returning meteorological data.

It has two equal jobs.

## 2.1 Human weather instrument

Provide a beautiful, compact, information-dense dashboard that lets a person understand:

- what the weather is doing now;
- whether it is raining;
- what rain is approaching;
- what radar has been doing;
- what happens over the next hours;
- what the next several days look like;
- detailed meteorological conditions when wanted;
- source, age and provenance without burying the useful result.

## 2.2 MethodMesh capability provider

Expose meteorological operations as stable canonical methods that can be used through:

- direct native capability launch;
- the Weather Dashboard;
- Presets;
- Protocols;
- ODK/XLSForm;
- schedules/widgets where a specific capability is later declared eligible.

The dashboard must be an aggregation/orchestration surface over canonical methods, not the only place where Weather functionality exists.

---

# 3. Product rule

Weather must pass the MethodMesh taste test:

> Do stuff. Return the beef, keep the salad available when explicitly wanted.

For Weather, **beef** includes:

- temperature;
- rainfall;
- next rain time;
- wind;
- pressure;
- radar time/state;
- selected forecast values;
- a weather snapshot;
- selected time-series values.

**Salad** includes:

- provider;
- model;
- run time;
- query coordinate;
- grid coordinate;
- cache state;
- retrieval time;
- source class;
- licence/attribution;
- full structured JSON;
- diagnostics.

The native UI must be beef-first, but the canonical result contract must retain the salad.

---

# 4. Master Book conformance register

The implementation MUST be reviewed against at least the following current v1.08 requirements.

| Requirement | Weather interpretation |
|---|---|
| `MM-DOC-001` | Project-wide doctrine stays in the Master Book; Weather docs remain module-local. |
| `MM-CAP-001` | Each Weather method has one canonical contract projected through all applicable surfaces. |
| `MM-CAP-002` | Method IDs/input/output names/types/semantics are stable once admitted. |
| `MM-CAP-003` | Dashboard aggregation never becomes the only invocation path. |
| `MM-CAP-004` | Weather-specific methods, UI, settings, docs, examples and provider declarations stay Weather-owned. |
| `MM-CAP-005` | Exactly one maturity tag and one connectivity tag are declared for every admitted capability. |
| `MM-SURF-001` | Applicable Weather capabilities remain discoverable for direct use, Presets, Protocols and ODK. |
| `MM-SURF-002` | Back behaviour follows the canonical top-level/nested navigation rule. |
| `MM-UX-001` | Weather uses purpose-built meteorological UI. |
| `MM-UX-002` | Current working weather values update in place. |
| `MM-UX-003` | Useful scalar/text results are tap-to-copy. |
| `MM-UX-004` | Commit freezes the canonical payload. |
| `MM-UX-005` | Post-Commit exploration cannot silently mutate the committed payload. |
| `MM-UX-006` | Meaningful working/committed state survives ordinary lifecycle changes. |
| `MM-UX-007` | Closeout returns to the correct launch origin. |
| `MM-UX-008` | Weather stays on polished toolkit/capability surfaces; no arbitrary generic result screen. |
| `MM-ODK-001` | ODK invokes the same canonical Weather method as native/preset/protocol use. |
| `MM-ODK-002` | Every representable declared Weather output remains addressable by ODK. |
| `MM-ODK-003` | Interactive ODK launch returns directly to the calling form after Commit/Cancel. |
| `MM-ODK-005` | ODK owns returned study data; Weather does not silently archive a duplicate merely because a form called it. |
| `MM-ODK-006` | `methodmesh_full_json` remains the shared complete structured/audit return. |
| `MM-ODK-007` | Every handled Weather -> ODK roundtrip captures `methodmesh_full_json`. |
| `MM-ODK-009` | ODK/Kobo node portability is treated separately from generic JavaRosa validity. |
| `MM-ODK-010` | Weather exposes a standard ODK Integration Card per independently callable capability. |
| `MM-XLS-001` | Every active canonical Weather example contains exactly one MethodMesh invocation. |
| `MM-XLS-002` | New canonical forms use `example_odk_showcase_<purpose>.xlsx`. |
| `MM-XLS-003` | `form_id` is a stable external identity once deployed. |
| `MM-XLS-004` | Weather forms participate in batch structural/ODK/naming/provider validation. |
| `MM-XLS-006` | Showcases demonstrate the real declared capability contract and invent no synthetic fields. |
| `MM-XLS-007` | filename/title/version/form_id remain separate concepts. |
| `MM-XLS-008` | new/revised form versions prefer `YYYYMMDDrr`. |
| `MM-XLS-009` | active canonical examples remain single-invocation forms. |
| `MM-XLS-010` | showcases capture `methodmesh_status`, `methodmesh_full_json` and only declared Weather returns. |
| `MM-XLS-011` | canonical showcases use unprefixed canonical return keys and no `methodmesh_return_namespace`. |
| `MM-XLS-012` | return namespace remains only an advanced composite-form feature. |
| `MM-OUT-001` | Metadata may be visually secondary but cannot be removed from the contract. |
| `MM-OUT-002` | `methodmesh_full_json` remains independently available. |
| `MM-OUT-004` | Native Weather is beef-first; scalar/text results tap-to-copy. |
| `MM-OFF-001` | Weather works offline where reasonably possible and online dependencies are explicit. |
| `MM-API-001` | Weather APIs use declarative definitions consumed by generic MethodMesh online-data infrastructure. |
| `MM-API-002` | Provider responses remain structured until intentionally projected; do not prematurely flatten. |
| `MM-API-003` | Future provider credentials use secure references and are not exported/logged in plaintext. |
| `MM-API-004` | Remote location disclosure is minimized to required precision. |
| `MM-AND-001` | Weather publishes generic surface state/actions; shared Android infrastructure owns generic platform lifecycle/permissions. |
| `MM-AND-002` | Persistent/urgent Weather surfaces are quiet by default and require deliberate eligibility/configuration. |
| `MM-PROT-001` | Protocols invoke canonical Weather capabilities/presets and preserve step results/rails. |
| `MM-PROT-002` | Protocol Weather dataflow uses typed references/transforms, not arbitrary executable transforms. |
| `MM-SCHED-001` | Any later scheduled Weather action targets a canonical method and preserves closeout semantics. |
| `MM-MOD-001` | Handoff is exactly one clean `weather/` root. |
| `MM-MOD-002` | The new Weather prototype must not break discovery/admission before review. |
| `MM-MOD-003` | Status/connectivity metadata are module-owned and projected generically. |
| `MM-REV-001` | Review begins from the capability inventory/contracts. |
| `MM-REV-002` | Review verifies dashboard/direct/preset/protocol/ODK parity and schedules/widgets where applicable. |
| `MM-REV-003` | Supplied Weather XLSForms are part of module completeness. |
| `MM-REV-004` | Review starts from owner-declared ODK/runtime I/O and status tags, then reconciles implementation/UI/docs/forms. |
| `MM-TEST-001` | Contract-boundary/parity tests exist where feasible. |
| `MM-TEST-002` | Promotion requires representative native/preset/protocol/ODK validation. |

This table is not a substitute for reviewing the whole Master Book.

---

# 5. Module status

Initial module-level tags:

```text
Maturity: DEVELOPMENT
Connectivity: ONLINE_OFFLINE
```

Rationale:

- Weather is a new MethodMesh module and must not be labelled Production before build/review/promotion.
- live data are network-backed;
- the dashboard and several methods have meaningful supported cached-data behaviour offline;
- “ONLINE_OFFLINE” must not mean merely that the screen opens while offline.

Every capability below declares its own tags.

---

# 6. Visual ancestry and migration stance

The existing KeylessWeather prototype is a **design ancestor**, not a codebase to embed.

## Preserve

- compact hierarchy;
- NOW / TODAY / next-hours / next-days mental model;
- quantitative rainfall;
- observed radar playback;
- explicit timestamps;
- restrained, scientific presentation;
- glanceability;
- pale/quiet surfaces and strong labels;
- information density.

## Replace

- monolithic `MainActivity.java`;
- programmatic legacy Android view assembly;
- direct `HttpURLConnection` from the Activity;
- WebView/Leaflet radar;
- arbitrary London fallback;
- provider-specific parsing inside UI code;
- emoji as the principal icon language;
- fixed forecast-window assumptions;
- weak provenance;
- dashboard-only behaviour.

The new module should feel recognisably descended from KeylessWeather while being architecturally native to MethodMesh.

---

# 7. Weather Dashboard

## 7.1 Role

The Weather Dashboard is a first-class Weather surface and a polished meteorological instrument.

It aggregates canonical method outputs but does not own independent hidden business logic.

Panels should be backed by canonical methods or shared Weather domain functions used by those methods.

## 7.2 Recommended vertical hierarchy

1. location / freshness header;
2. current conditions hero;
3. immediate precipitation / wind / pressure strip;
4. precipitation timeline;
5. radar;
6. hourly forecast;
7. meteogram;
8. multi-day forecast;
9. detailed meteorology;
10. provenance / attribution / source status.

Important information should be useful before extensive scrolling.

## 7.3 Location / freshness header

Display:

- resolved place label, if available;
- exact local/device/request coordinate;
- location source:
  - device GPS;
  - supplied coordinates;
  - map pick;
  - place search;
  - saved location;
  - Preset;
  - Protocol;
  - ODK;
- latest useful data valid time;
- retrieval/update time;
- cache state;
- provider/model summary;
- refresh action.

A secondary technical view must distinguish:

- requested coordinate;
- remotely disclosed/query coordinate;
- provider/model grid coordinate.

Do not silently use London or another arbitrary fallback.

## 7.4 Current conditions hero

Primary:

- proper weather glyph;
- temperature;
- condition label;
- apparent temperature;
- local-day high/low.

Secondary:

- precipitation now;
- humidity;
- dew point;
- wind speed/direction;
- gust;
- mean sea-level pressure;
- optional pressure tendency.

The source class should be available without clutter:

```text
OBSERVED
RADAR OBSERVATION
ANALYSIS
REANALYSIS
MODELLED CURRENT
NOWCAST
FORECAST
ENSEMBLE FORECAST
HISTORICAL FORECAST
DERIVED
```

Do not call a modelled-current estimate an observation.

## 7.5 Immediate conditions strip

A narrow high-value strip should answer:

- raining now?
- current amount/intensity?
- next meaningful rain?
- wind/gust?
- pressure rising/falling?

Any threshold-based statement such as “rain in 38 min” must retain its threshold/source definition in technical details.

## 7.6 Precipitation timeline

This should be one of the defining Weather visuals.

Default:

- recent past when available;
- a clear NOW marker;
- near-term forecast;
- ~24 h visible;
- horizontal exploration beyond the default range.

Use quantitative precipitation:

- mm per interval; or
- mm/h where source semantics support intensity.

Optional companion probability may be shown but must not replace quantitative rainfall.

Visually distinguish:

- observation/history;
- analysis/reanalysis;
- nowcast;
- forecast.

Touch/cursor updates the live working result.

## 7.7 Radar

Native map panel, not a WebView app.

Required:

- location marker;
- pan;
- zoom;
- radar overlay;
- radar timestamp;
- timeline scrubber;
- previous/next;
- play/pause;
- legend;
- attribution;
- no-coverage state;
- cached state;
- frame provenance.

### Radar semantic invariant

Modelled future precipitation **must never be labelled radar**.

Radar timeline:

```text
older observed frames
    ->
newer observed frames
    ->
provider-supplied nowcast frames, only if genuinely supplied
    ->
STOP
```

If the user elects to view modelled precipitation after NOW, switch to an explicitly named **Forecast precipitation** layer/timeline.

Do not splice model forecast frames into `RadarTimeline.frames`.

## 7.8 Hourly forecast

Default approximately 24 h, extendable.

Each step should expose:

- local time;
- condition glyph/text;
- temperature;
- precipitation amount;
- precipitation probability where available;
- wind;
- optional apparent temperature.

The current KeylessWeather 2-hour-strip idea can inspire the glance view, but full hourly inspection must remain possible.

## 7.9 Meteogram

Purpose-built professional time-series view.

Default series:

- temperature;
- apparent temperature / dew point;
- precipitation;
- wind/gust;
- pressure;
- cloud.

Optional:

- humidity;
- visibility;
- low/mid/high cloud;
- UV;
- solar radiation;
- CAPE/CIN;
- freezing level;
- wet-bulb temperature;
- soil values;
- pressure-level variables.

Requirements:

- shared time cursor;
- exact values;
- unit labels;
- pan/zoom or time-range control;
- variable toggles;
- accessible readout;
- touch selection updates live working result.

## 7.10 Multi-day forecast

Default 7–10 days where provider supports it.

Card content:

- day/date;
- condition;
- high/low;
- quantitative precipitation;
- probability where available;
- max wind/gust;
- optional UV.

Must remain readable on small screens and at increased font scale.

## 7.11 Detailed meteorology

Expandable or dedicated full-screen route.

Candidate surface groups:

### Near surface
- temperature 2 m;
- apparent temperature;
- relative humidity;
- dew point;
- wet-bulb;
- vapour pressure deficit;
- MSL pressure;
- surface pressure;
- visibility;
- weather code.

### Wind
- speed;
- direction;
- gust;
- optional supported pressure-level winds.

### Cloud
- total;
- low;
- mid;
- high;
- fog/near-surface cloud where supported.

### Radiation / sun
- sunrise;
- sunset;
- daylight duration;
- sunshine duration;
- UV;
- shortwave radiation;
- direct/diffuse radiation.

### Convective / atmospheric
- CAPE;
- CIN;
- lifted index;
- freezing level;
- boundary-layer height;
- column water vapour.

### Land surface
- soil temperature;
- soil moisture;
- evapotranspiration;
- reference evapotranspiration.

### Pressure levels
Advanced selection of:

- temperature;
- humidity;
- wind;
- geopotential height;
- cloud.

Advanced values must not clutter the default dashboard.

---

# 8. Canonical capability inventory

Initial public capability set:

| Method ID | Human purpose | Maturity | Connectivity |
|---|---|---|---|
| `weather.conditions` | Current/selected-time weather conditions | DEVELOPMENT | ONLINE_OFFLINE |
| `weather.forecast` | Hourly/daily forecast | DEVELOPMENT | ONLINE_OFFLINE |
| `weather.precipitation` | Rain/snow timing, intensity and accumulation | DEVELOPMENT | ONLINE_OFFLINE |
| `weather.radar` | Observed radar / provider nowcast inspection | DEVELOPMENT | ONLINE_OFFLINE |
| `weather.meteogram` | Detailed meteorological time-series inspection | DEVELOPMENT | ONLINE_OFFLINE |
| `weather.wind` | Wind-focused field instrument | DEVELOPMENT | ONLINE_OFFLINE |
| `weather.atmosphere` | Advanced atmospheric/pressure-level data | EXPERIMENTAL | ONLINE_OFFLINE |
| `weather.sun` | Sunrise/sunset/daylight/UV/radiation | DEVELOPMENT | ONLINE_OFFLINE |
| `weather.snapshot` | Research-friendly meteorological snapshot | DEVELOPMENT | ONLINE_ONLY |
| `weather.model_compare` | Model/ensemble comparison | EXPERIMENTAL | ONLINE_ONLY |

### Connectivity interpretation

`ONLINE_OFFLINE` is justified only where the capability supports meaningful execution/inspection using a matching cached payload when network is unavailable.

A cached value must always expose its age.

`weather.snapshot` is initially `ONLINE_ONLY` because arbitrary timestamp/location research reconstruction must not pretend that an unrelated cache constitutes a valid offline source. If a future explicit downloadable meteorological resource/reanalysis pack supports genuine offline lookup, its connectivity tag may be revised through normal review.

`weather.model_compare` is initially `ONLINE_ONLY` for the same reason.

---

# 9. Capability contracts

All canonical methods declare once, in Weather-owned code/metadata:

- method ID;
- inputs;
- settings;
- runtime-input policy;
- outputs;
- result types;
- actions;
- closeout semantics;
- maturity;
- connectivity;
- ODK integration-card data.

No dashboard-specific duplicate contracts.

---

## 9.1 `weather.conditions`

### Purpose

Return meteorological conditions for a location and valid time.

### Input policy

#### Runtime inputs
- `input_latitude`
- `input_longitude`
- optional `input_altitude_m`
- optional `input_target_time_iso`

#### Fixed/preset configuration
- `input_variable_profile`
- `input_provider_id`
- `input_model_id`
- `input_units_profile`

#### Operational controls
- Refresh
- location acquisition/change
- technical details
- copy
- Commit

### Core outputs

Suggested stable canonical scalar keys:

```text
weather_conditions_result
weather_conditions_requested_latitude
weather_conditions_requested_longitude
weather_conditions_query_latitude
weather_conditions_query_longitude
weather_conditions_valid_time_iso
weather_conditions_retrieved_time_iso
weather_conditions_data_class
weather_conditions_provider
weather_conditions_model
weather_conditions_model_run_time_iso

weather_conditions_temperature_c
weather_conditions_apparent_temperature_c
weather_conditions_relative_humidity_pct
weather_conditions_dew_point_c
weather_conditions_wet_bulb_c
weather_conditions_precipitation_mm
weather_conditions_rain_mm
weather_conditions_snowfall_cm
weather_conditions_pressure_msl_hpa
weather_conditions_surface_pressure_hpa
weather_conditions_visibility_m
weather_conditions_cloud_cover_pct
weather_conditions_wind_speed_ms
weather_conditions_wind_direction_deg
weather_conditions_wind_gust_ms
weather_conditions_weather_code
weather_conditions_condition_label

weather_conditions_from_cache
weather_conditions_data_age_hours
weather_conditions_error
```

Only declare outputs actually implemented by the admitted contract.

Unavailable optional values remain absent/blank/null according to the host result/ODK projection. Never replace missing values with zero.

### Native beef

- temperature;
- condition;
- feels-like;
- rain;
- humidity;
- pressure;
- wind.

### Commit

Freeze the currently displayed valid-time/location/result set.

---

## 9.2 `weather.forecast`

### Purpose

Return hourly and/or daily forecast data.

### Inputs

- location;
- start time;
- horizon;
- hourly/daily/both;
- temporal resolution;
- variable profile;
- provider/model;
- optional ensemble mode.

### Outputs

The core native result is a readable forecast summary plus the selected time point.

The structured result retains full time-series structure.

Do not prematurely flatten provider arrays inside the core Weather data layer.

ODK canonical flat outputs should be limited to explicitly declared useful summary/scalar results plus shared full JSON.

Potential declared outputs:

```text
weather_forecast_result
weather_forecast_start_time_iso
weather_forecast_end_time_iso
weather_forecast_provider
weather_forecast_model
weather_forecast_model_run_time_iso
weather_forecast_selected_time_iso
weather_forecast_selected_temperature_c
weather_forecast_selected_precipitation_mm
weather_forecast_selected_precipitation_probability_pct
weather_forecast_selected_wind_speed_ms
weather_forecast_daily_high_c
weather_forecast_daily_low_c
weather_forecast_daily_precipitation_mm
weather_forecast_from_cache
weather_forecast_data_age_hours
weather_forecast_error
```

Full series remains available through the canonical structured payload / `methodmesh_full_json`.

---

## 9.3 `weather.precipitation`

### Purpose

Inspect and return precipitation timing/intensity/accumulation.

### Inputs

- location;
- start/end or lookback/lookahead;
- precipitation types;
- meaningful-rain threshold;
- source policy;
- provider/model.

### UI

Dedicated precipitation timeline.

### Derived outputs

Where supported:

```text
weather_precipitation_result
weather_precipitation_valid_from_iso
weather_precipitation_valid_to_iso
weather_precipitation_current_mm
weather_precipitation_total_mm
weather_precipitation_next_time_iso
weather_precipitation_threshold_mm_per_hour
weather_precipitation_next_threshold_time_iso
weather_precipitation_max_intensity_mm_per_hour
weather_precipitation_provider
weather_precipitation_model
weather_precipitation_data_class
weather_precipitation_from_cache
weather_precipitation_data_age_hours
weather_precipitation_error
```

“Next rain” must be algorithmically tied to a declared threshold/source rule.

---

## 9.4 `weather.radar`

### Purpose

Inspect observed radar frames and genuine provider-supplied nowcast frames.

### Inputs

- location;
- initial zoom;
- history window;
- provider;
- optional palette/opacity.

### Working result

Current frame and viewport.

### Core outputs

```text
weather_radar_result
weather_radar_requested_latitude
weather_radar_requested_longitude
weather_radar_frame_time_iso
weather_radar_frame_class
weather_radar_provider
weather_radar_coverage_state
weather_radar_zoom
weather_radar_viewport_json
weather_radar_from_cache
weather_radar_data_age_hours
weather_radar_error
```

Do not declare a fake future-radar output.

Raster/tile imagery is primarily a visual provider resource; do not invent an ODK file output unless a real caller-owned exported artefact is deliberately implemented.

---

## 9.5 `weather.meteogram`

### Purpose

Inspect a detailed meteorological time series and commit selected values/time.

### Inputs

- location;
- time range;
- variable list/profile;
- provider/model.

### Outputs

```text
weather_meteogram_result
weather_meteogram_selected_time_iso
weather_meteogram_variable_ids
weather_meteogram_values_json
weather_meteogram_provider
weather_meteogram_model
weather_meteogram_from_cache
weather_meteogram_data_age_hours
weather_meteogram_error
```

Selected scalar values may additionally be declared individually if stable and useful; do not invent dozens of XLSForm leaves solely to flatten every possible advanced variable.

---

## 9.6 `weather.wind`

### Purpose

Wind-focused live/forecast field instrument.

### Inputs

- location;
- time;
- height/pressure level where supported;
- forecast horizon.

### UI

- large direction treatment;
- degrees;
- compass point;
- sustained wind;
- gust;
- small time series.

### Outputs

```text
weather_wind_result
weather_wind_valid_time_iso
weather_wind_speed_ms
weather_wind_direction_deg
weather_wind_direction_compass
weather_wind_gust_ms
weather_wind_provider
weather_wind_model
weather_wind_data_class
weather_wind_from_cache
weather_wind_data_age_hours
weather_wind_error
```

---

## 9.7 `weather.atmosphere`

### Purpose

Advanced meteorological/atmospheric inspection.

### Maturity

`EXPERIMENTAL`

### Inputs

- location;
- valid time/range;
- selected variables;
- pressure levels;
- provider/model.

### Outputs

Beef:

- selected variable/time/value.

Structured output:

- selected atmospheric variable tree;
- units;
- levels;
- provenance.

Canonical scalar keys should remain compact; advanced multidimensional data belongs in structured output rather than hundreds of synthetic flat fields.

---

## 9.8 `weather.sun`

### Purpose

Solar/daylight/UV/radiation data.

### Inputs

- location;
- date/time;
- variable profile.

### Outputs

```text
weather_sun_result
weather_sun_date
weather_sun_sunrise_iso
weather_sun_sunset_iso
weather_sun_daylight_seconds
weather_sun_sunshine_seconds
weather_sun_uv_index
weather_sun_uv_index_max
weather_sun_shortwave_radiation_wm2
weather_sun_provider
weather_sun_from_cache
weather_sun_data_age_hours
weather_sun_error
```

Only declare fields available in the implemented contract.

---

## 9.9 `weather.snapshot`

### Purpose

Produce a coherent research-friendly meteorological snapshot for a supplied location and timestamp.

### Connectivity

`ONLINE_ONLY` initially.

### Inputs

- latitude;
- longitude;
- timestamp;
- optional altitude;
- source policy:
  - best available;
  - forecast;
  - historical forecast;
  - reanalysis;
  - explicit provider/model;
- variable profile.

### Scientific rule

The returned source class is part of the useful interpretation.

Examples:

- an event in 2022 reconstructed from reanalysis -> `REANALYSIS`;
- an archived forecast valid at that time -> `HISTORICAL_FORECAST`;
- present model estimate -> `MODELLED_CURRENT`;
- physical station feed if later supported -> `OBSERVATION`.

Do not erase this distinction.

### Outputs

A compact research-facing scalar set plus complete structured provenance.

Potential keys:

```text
weather_snapshot_result
weather_snapshot_requested_time_iso
weather_snapshot_valid_time_iso
weather_snapshot_requested_latitude
weather_snapshot_requested_longitude
weather_snapshot_query_latitude
weather_snapshot_query_longitude
weather_snapshot_data_class
weather_snapshot_provider
weather_snapshot_model
weather_snapshot_model_run_time_iso

weather_snapshot_temperature_c
weather_snapshot_apparent_temperature_c
weather_snapshot_relative_humidity_pct
weather_snapshot_dew_point_c
weather_snapshot_precipitation_mm
weather_snapshot_pressure_msl_hpa
weather_snapshot_wind_speed_ms
weather_snapshot_wind_direction_deg
weather_snapshot_wind_gust_ms
weather_snapshot_cloud_cover_pct
weather_snapshot_visibility_m
weather_snapshot_weather_code

weather_snapshot_retrieved_time_iso
weather_snapshot_error
```

---

## 9.10 `weather.model_compare`

### Purpose

Compare models and/or ensembles.

### Maturity/connectivity

```text
EXPERIMENTAL
ONLINE_ONLY
```

### Inputs

- location;
- valid time/horizon;
- variables;
- model list;
- ensemble choice.

### UI

- aligned model traces;
- model/run identifiers;
- mean/median only where defined;
- spread;
- member traces optionally;
- clear lead time.

### Semantic rule

Raw model/ensemble spread must not be described as a calibrated confidence interval unless the underlying source genuinely supplies one with that meaning.

---

# 10. Weather data classes and provenance

Canonical Weather source classes:

```text
OBSERVATION
RADAR_OBSERVATION
ANALYSIS
REANALYSIS
MODELLED_CURRENT
NOWCAST
FORECAST
ENSEMBLE_FORECAST
HISTORICAL_FORECAST
DERIVED
```

Recommended Weather provenance object:

```text
WeatherProvenance
  provider_id
  provider_name
  provider_product_id?
  model_id?
  model_name?
  model_run_time?
  valid_time
  retrieved_time
  data_class

  requested_latitude
  requested_longitude
  query_latitude?
  query_longitude?
  grid_latitude?
  grid_longitude?
  grid_elevation_m?
  source_resolution?

  from_cache
  data_age_seconds?

  attribution_text?
  documentation_url?
  licence_id?

  provider_metadata {}
```

The provenance model must support the privacy rule: exact requested coordinates may differ from the coordinates disclosed to the third party.

---

# 11. Location privacy

This is a mandatory v1.08 correction.

For declared third-party API providers, Weather should default to approximately **5 km rounded coordinate disclosure** where appropriate.

Therefore distinguish:

```text
requested location
  = exact user/device/form/protocol location used by MethodMesh

query/disclosed location
  = privacy-minimized coordinate actually sent to the remote provider

provider/grid location
  = provider/model evaluation coordinate where supplied
```

Rules:

1. exact GPS must not be silently disclosed when coarser precision is sufficient;
2. Weather API definitions should use `LocationDisclosureMode.ROUNDED`;
3. default radius: `5_000` m unless a justified task/provider exception is documented;
4. the user-facing result may still retain exact requested location because that is local MethodMesh/caller context;
5. provenance must record the query coordinate/precision or enough information to reconstruct the disclosure policy;
6. if an advanced workflow genuinely needs greater remote precision, that must be a deliberate declared mode, not a hidden bypass.

Radar/map tile requests may reveal viewport/tile geography by their nature. Their provider/privacy behaviour must be documented separately and minimized where feasible.

---

# 12. Online-data architecture

## 12.1 Do not build a second HTTP platform

Weather must use MethodMesh's existing online-data architecture.

Current repository patterns already include:

```text
com.example.methodmesh.core.onlinedata.ApiDefinition
ApiDefinitionRepository
CachePolicy
ApiPrivacy
LocationDisclosureMode
ApiAttribution
ResultTree
api.get / As100ApiGetMethod
```

The repository already bundles Open-Meteo definitions and the Astronomy module demonstrates module-owned Open-Meteo definitions executed through shared online-data machinery.

Weather should follow that architecture.

## 12.2 Weather-owned declarations

Weather-specific API knowledge should live under:

```text
app/src/main/java/com/example/methodmesh/modules/weather/
```

For example:

```text
WeatherApiDefinitions.kt
WeatherLiveData.kt
WeatherProviderMapping.kt
```

Possible declaration IDs:

```text
openmeteo.weather_current
openmeteo.weather_hourly
openmeteo.weather_daily
openmeteo.weather_detailed
openmeteo.weather_historical
openmeteo.weather_historical_forecast
openmeteo.weather_ensemble
openmeteo.weather_pressure_levels

rainviewer.weather_radar_timeline
```

These are provider-definition IDs, **not** public MethodMesh capability IDs.

## 12.3 Shared execution

API calls should be executed through generic MethodMesh infrastructure rather than bespoke shared UI/network code.

Weather may own:

- response mapping;
- provider-specific interpretation;
- Weather domain normalization;
- orchestration across several API definitions;
- radar tile rendering logic.

Weather must not require the shared app shell to know Open-Meteo/RainViewer field semantics.

## 12.4 Result structure

Provider responses remain structured through the online-data layer.

Mapping sequence:

```text
provider JSON / ResultTree
    ->
Weather provider mapper
    ->
canonical Weather domain model
    ->
capability result
    ->
native beef projection / flat ODK projection / full JSON
```

Do not flatten all provider data immediately into `Map<String,String>` and then rebuild scientific structure later.

---

# 13. Initial providers

## 13.1 Open-Meteo

Initial general meteorological provider for:

- current/modelled conditions;
- hourly forecast;
- daily forecast;
- detailed variables;
- historical/reanalysis where supported;
- historical forecast where supported;
- model-specific outputs;
- ensemble outputs;
- pressure-level fields.

Weather should reuse existing bundled definitions when they exactly satisfy the need and add Weather-owned definitions when the module requires a larger/different declared field set.

Every definition must include:

- cache policy;
- attribution;
- documentation URL;
- privacy configuration;
- expected response paths;
- declared inputs.

## 13.2 RainViewer

Initial radar provider.

The radar adapter must handle:

- provider timeline metadata;
- observed frames;
- genuine nowcast frames when present;
- host/tile templates;
- time;
- no-coverage/unavailable cases;
- attribution.

The radar provider is not the generic weather backend.

## 13.3 Future providers

Architecture should allow later adapters/declarations for:

- national meteorological services;
- station networks;
- paid operational services;
- project-specific endpoints;
- local/offline meteorological datasets.

Adding a provider must not require rewriting the Weather Dashboard.

---

# 14. Caching and offline behaviour

## 14.1 Disposable cache

Weather may maintain/use disposable cache through the shared online-data infrastructure for:

- current conditions;
- forecast;
- provider metadata;
- geocoding;
- radar timeline;
- bounded radar tiles;
- model comparison responses.

Cached presentations must expose age/freshness.

Examples:

```text
Updated 8 min ago
Cached · 3 h old
Forecast retrieved yesterday 18:20
```

Never display cached data as freshly retrieved.

## 14.2 Offline capability behaviour

For `ONLINE_OFFLINE` capabilities:

- a matching previously retrieved payload may be used;
- its age must be obvious;
- user can inspect it fully;
- Commit may use it if the capability contract permits;
- result provenance preserves the original retrieval time and cache state.

If no useful matching payload exists:

- say that live data require a network;
- do not substitute unrelated stale location/time data.

## 14.3 Downloaded offline resources

Do not label future intentionally downloaded meteorological resources as ordinary “cache”.

Potential future resources:

- offline forecast bundle;
- climate normals;
- reanalysis tiles;
- scientific rasters;
- offline base-map pack.

These are deliberate resources and should use MethodMesh's downloaded-resource concepts, separate from disposable cache.

## 14.4 Partial provider failure

Dashboard composition must degrade panel-by-panel.

Examples:

- radar unavailable -> forecast still works;
- advanced atmospheric definition fails -> basic conditions remain;
- geocoding fails -> coordinate-based forecast remains;
- ensemble fails -> deterministic forecast remains.

Do not make one provider failure blank the entire dashboard.

---

# 15. Refresh policy

Follow the shared cache/execution infrastructure where possible.

Weather-specific orchestration should:

- render matching cache immediately;
- prefer refresh from network when policy says fresh is required/preferred;
- avoid duplicate concurrent identical requests;
- cancel obsolete location/time requests;
- use provider-appropriate backoff;
- never refetch merely because Compose recomposed;
- never reload a radar metadata endpoint for every animation frame.

Refresh is an operational control, not a second capability.

---

# 16. Location acquisition

Supported input/acquisition paths:

1. device GPS;
2. supplied latitude/longitude;
3. map selection;
4. place search/geocoding;
5. saved/recent location;
6. Preset input;
7. Protocol input;
8. ODK form input.

If location permission is denied:

- capability remains usable with supplied/manual location;
- explain the unavailable acquisition mode;
- do not nag;
- do not silently use a fallback city.

Location scalar values are tap-to-copy.

---

# 17. Time semantics

Weather outputs must distinguish:

- requested time;
- valid time;
- model run/initialization time;
- provider generation time, if relevant;
- retrieval time;
- Commit time;
- displayed local timezone.

Internal timestamps must be unambiguous.

Display generally uses the target location's local timezone.

Technical details should expose UTC/offset-aware timestamps.

Do not stamp provider values with the phone's current time when the provider supplies a valid time.

---

# 18. Units

Use stable canonical scientific units in contracts.

Recommended:

```text
temperature              °C
precipitation             mm
precipitation intensity   mm/h where meaningful
wind                      m/s
pressure                  hPa
visibility                m
direction                 degrees
duration                  seconds
radiation                 W/m² where appropriate
```

UI may show user-preferred units.

Display-unit preference must not alter underlying scientific values/semantics returned by the canonical contract.

---

# 19. Working result, Commit and post-Commit editing

## 19.1 Live working result

Weather naturally changes as the user:

- moves the time cursor;
- changes location;
- scrubs radar;
- changes time range;
- changes variables;
- changes model/provider.

Those changes update the live working result **on the same surface**.

Do not make the user press a fake “Calculate” button merely to reveal values already available.

## 19.2 Commit

Commit means:

> this is the weather result I want to keep/return from this execution.

On Commit:

- freeze canonical payload;
- freeze selected time/location/provider/model state;
- construct final result;
- retain audit/provenance;
- enter committed state.

## 19.3 After Commit

Further exploration must not silently mutate the committed payload.

Acceptable patterns:

- lock result until user explicitly chooses Edit/New;
- start a new working result when the user changes parameters;
- visibly distinguish committed snapshot from subsequent exploration.

## 19.4 Persistence

Commit does **not** automatically create a hidden Weather archive.

Share/Save/Export, where offered, are explicit.

ODK owns its returned study data.

---

# 20. Origin-aware completion

Use the shared launch-origin machinery.

Required outcomes:

- normal app/direct/preset launch -> Home/Done returns to MethodMesh Dashboard;
- Protocol -> returns execution result to the protocol runtime;
- ODK interactive launch -> Commit/Cancel returns directly to the calling form;
- Widget -> if later supported, closeout follows widget-origin desktop behaviour;
- nested Weather screens -> Back unwinds nested state before top-level navigation.

Weather capability code must not fork into separate implementations for each origin.

---

# 21. Tap-to-copy

All useful scalar/text values should be directly tappable where sensible.

Examples:

- coordinates;
- temperature;
- precipitation amount;
- “next rain” time;
- humidity;
- dew point;
- pressure;
- wind speed;
- wind direction;
- gust;
- UV;
- visibility;
- provider/model ID;
- timestamps.

Copy the value, not the UI label/debug field name.

Example:

```text
18.4 °C
```

not:

```text
weather_conditions_temperature_c: 18.4
```

Provide subtle accessible confirmation.

---

# 22. State preservation

Meaningful working and committed state should survive ordinary lifecycle changes.

Persist/restore as appropriate:

```text
WeatherSessionState
  selected_location
  selected_location_source
  selected_time
  selected_range
  selected_radar_frame
  radar_playing
  radar_zoom
  radar_viewport
  selected_meteogram_variables
  expanded_sections
  provider/model selection
  unit display preferences
  working_result
  committed_result
  dashboard_scroll_anchor
```

Do not confuse:

- transient UI state;
- disposable API cache;
- committed execution result;
- Preset configuration;
- intentionally downloaded offline resources.

---

# 23. Presets

Each applicable capability must be preset-authorable through its canonical settings.

Examples:

## Current field weather

```text
method: weather.conditions
location: runtime GPS
variable_profile: standard
provider: default
```

## Rain next 24 hours

```text
method: weather.precipitation
location: runtime GPS
lookahead: 24 h
threshold: 0.2 mm/h
```

## Sampling-site weather snapshot

```text
method: weather.snapshot
location: runtime/supplied
timestamp: runtime/supplied
profile: field_research_standard
```

## Detailed meteorology

```text
method: weather.atmosphere
variables: detailed_surface
pressure_levels: selected
```

Fixed preset configuration should be hidden during native preset execution where the Master Book expects it; runtime inputs remain available.

---

# 24. Protocols

Weather capabilities must function as normal protocol steps.

Examples:

```text
GPS capture
-> weather.snapshot
-> photograph
-> commit field record
```

```text
select study site
-> weather.precipitation
-> operator decision
```

Protocol dataflow should be typed.

Example typed references:

```text
previous.location.latitude -> weather.snapshot.input_latitude
previous.location.longitude -> weather.snapshot.input_longitude
sample.collected_time_iso -> weather.snapshot.input_target_time_iso
```

Do not implement arbitrary code snippets as Weather transforms.

---

# 25. Schedules and widgets

No Weather capability is automatically urgent/persistent merely because weather changes.

Initial specification:

- no unsolicited persistent notification;
- no foreground service simply to “keep weather current”;
- no alerting by default.

Potential future eligibility:

- scheduled `weather.snapshot`;
- configured rain threshold check;
- compact weather widget.

These must use canonical method contracts and explicit opt-in configuration.

Do not implement this in the first admission unless specifically in scope.

---

# 26. ODK/XLSForm contract

ODK is a first-class surface.

Every independently callable Weather capability must have an ODK Integration Card, even if not every capability ships a showcase immediately.

Canonical ODK intent pattern:

```text
com.example.methodmesh.EXECUTE_METHOD(
    method_id='<canonical_method_id>',
    <declared input_* modifiers>,
    input_payload_mode='FULL',
    return_mode='flat'
)
```

Actual XLSForm `body::intent` syntax must follow the existing MethodMesh transport convention.

## 26.1 Canonical ODK return envelope

Every handled Weather roundtrip:

```text
methodmesh_status
+ capability-specific declared return fields
+ methodmesh_full_json
```

Do not invent per-capability universal success/time/JSON fields merely for uniformity.

Weather-specific retrieval/valid/model timestamps are legitimate only when they are actual declared Weather outputs.

## 26.2 Return names

Canonical showcases:

- use canonical unprefixed return names;
- do not use `methodmesh_return_namespace`;
- capture only fields declared by that capability;
- capture `methodmesh_status`;
- capture `methodmesh_full_json`.

## 26.3 Ownership

ODK owns returned submission/study data.

An ODK call must not silently add a duplicate copy to a Weather history/ledger merely because Commit occurred.

---

# 27. Canonical Weather XLSForms

Initial canonical showcase files:

```text
docs/example_odk_showcase_weather_conditions.xlsx
docs/example_odk_showcase_weather_forecast.xlsx
docs/example_odk_showcase_weather_precipitation.xlsx
docs/example_odk_showcase_weather_radar.xlsx
docs/example_odk_showcase_weather_wind.xlsx
docs/example_odk_showcase_weather_sun.xlsx
docs/example_odk_showcase_weather_snapshot.xlsx
```

Optional later showcases:

```text
docs/example_odk_showcase_weather_meteogram.xlsx
docs/example_odk_showcase_weather_atmosphere.xlsx
docs/example_odk_showcase_weather_model_compare.xlsx
```

Rules:

1. exactly one MethodMesh invocation per workbook;
2. stable `form_id`;
3. filename/title/version/form_id treated separately;
4. new version values prefer `YYYYMMDDrr`;
5. call on one intent group;
6. returned fields are children of that group;
7. use `input_payload_mode='FULL'`;
8. use `return_mode='flat'`;
9. no `methodmesh_return_namespace`;
10. capture `methodmesh_status`;
11. capture `methodmesh_full_json`;
12. capture all and only the capability-specific return fields intended by the showcase;
13. validate against JavaRosa/ODK;
14. assess Kobo portability separately;
15. deployment/access state is not conflated with workbook validity.

---

# 28. ODK Integration Cards

Weather documentation should contain one block per public capability in the same established project form.

Below are normative **specification templates**. Exact declared return lists must be reconciled to final code before handoff.

---

## Integration Card — `weather.conditions`

**Status tags:** Development · ONLINE_OFFLINE  
**Showcase:** `example_odk_showcase_weather_conditions.xlsx`  
**Acquisition:** supplied coordinates or native device-location acquisition.  
**Persistence:** no implicit Weather archive; ODK owns returned form data.

**Intent**

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='weather.conditions',input_payload_mode='FULL',return_mode='flat')
```

**Declared inputs / modifiers**

- `input_latitude`
- `input_longitude`
- `input_altitude_m`
- `input_target_time_iso`
- `input_variable_profile`
- `input_provider_id`
- `input_model_id`

Interactive native acquisition may supply location/time where omitted and allowed by the launch contract.

**Canonical returns**

Must match the final `weather.conditions` descriptor exactly, plus:

- `methodmesh_status`
- `methodmesh_full_json`

**Return placement and runtime contract**

The showcase places the call on one `begin group` row using `body::intent`; returned fields are children of that group. The canonical Weather capability owns any interactive acquisition, live result and Commit. External/ODK launches return directly to the calling form after Commit/Cancel and do not detour through a generic result screen.

---

## Integration Card — `weather.forecast`

**Status tags:** Development · ONLINE_OFFLINE  
**Showcase:** `example_odk_showcase_weather_forecast.xlsx`  
**Acquisition:** supplied/native location; configurable forecast horizon/profile.  
**Persistence:** no implicit persistence.

**Intent**

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='weather.forecast',input_payload_mode='FULL',return_mode='flat')
```

**Declared inputs / modifiers**

- location;
- forecast start/horizon;
- hourly/daily mode;
- variable profile;
- provider/model;
- optional ensemble flag if part of the admitted contract.

**Canonical returns**

Final declared scalar forecast outputs plus:

- `methodmesh_status`
- `methodmesh_full_json`

Full series remains available through structured output rather than an XLSForm-only synthetic flattening.

---

## Integration Card — `weather.precipitation`

**Status tags:** Development · ONLINE_OFFLINE  
**Showcase:** `example_odk_showcase_weather_precipitation.xlsx`  
**Acquisition:** supplied/native location and interval.  
**Persistence:** no implicit persistence.

**Intent**

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='weather.precipitation',input_payload_mode='FULL',return_mode='flat')
```

**Declared inputs / modifiers**

- location;
- lookback/lookahead or explicit interval;
- threshold;
- source policy;
- provider/model.

**Canonical returns**

Declared quantitative rainfall/next-rain/summary outputs plus shared fields.

---

## Integration Card — `weather.radar`

**Status tags:** Development · ONLINE_OFFLINE  
**Showcase:** `example_odk_showcase_weather_radar.xlsx`  
**Acquisition:** supplied/native location; interactive radar frame selection.  
**Persistence:** no implicit image archive.

**Intent**

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='weather.radar',input_payload_mode='FULL',return_mode='flat')
```

**Declared inputs / modifiers**

- location;
- initial zoom;
- history window;
- provider.

**Canonical returns**

Current selected radar state/frame metadata plus shared fields.

Do not invent a file return unless Weather deliberately implements a true exported raster/snapshot artefact.

---

## Integration Card — `weather.wind`

**Status tags:** Development · ONLINE_OFFLINE  
**Showcase:** `example_odk_showcase_weather_wind.xlsx`  
**Acquisition:** supplied/native location; current or selected time.  
**Persistence:** none.

**Intent**

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='weather.wind',input_payload_mode='FULL',return_mode='flat')
```

**Canonical returns**

Declared wind speed/direction/gust/time/provider fields plus shared fields.

---

## Integration Card — `weather.sun`

**Status tags:** Development · ONLINE_OFFLINE  
**Showcase:** `example_odk_showcase_weather_sun.xlsx`  
**Acquisition:** location + date/time.  
**Persistence:** none.

**Intent**

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='weather.sun',input_payload_mode='FULL',return_mode='flat')
```

**Canonical returns**

Declared sunrise/sunset/daylight/UV/radiation fields plus shared fields.

---

## Integration Card — `weather.snapshot`

**Status tags:** Development · ONLINE_ONLY  
**Showcase:** `example_odk_showcase_weather_snapshot.xlsx`  
**Acquisition:** supplied/native location plus supplied/event timestamp.  
**Persistence:** ODK owns returned study data; MethodMesh does not silently duplicate it.

**Intent**

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='weather.snapshot',input_payload_mode='FULL',return_mode='flat')
```

**Declared inputs / modifiers**

- latitude;
- longitude;
- timestamp;
- altitude optional;
- source policy;
- profile;
- provider/model optional.

**Canonical returns**

Declared research snapshot values and source/provenance scalars plus:

- `methodmesh_status`
- `methodmesh_full_json`

This is the principal canonical showcase for research capture.

---

# 29. Research data integrity

Weather may become study metadata, so semantics are non-negotiable.

Never:

- label reanalysis as an observation;
- label forecast as an observation;
- label modelled-current values as station measurements;
- label forecast precipitation imagery as radar;
- overwrite missing values with zero;
- discard model/run metadata where available;
- discard valid time;
- discard retrieval time;
- hide that the API query used rounded coordinates;
- present cache as fresh.

Derived claims must identify themselves as derived.

Example:

```text
pressure tendency = DERIVED
```

even if its input values are forecast/modelled pressure.

---

# 30. Radar domain model

Recommended:

```text
RadarTimeline
  provider
  retrieved_time
  frames[]
  newest_observation_time
  nowcast_frames[]
  from_cache
```

Each frame:

```text
RadarFrame
  id
  valid_time
  frame_class = RADAR_OBSERVATION | NOWCAST
  tile_template
  generated_time?
  provider_metadata
```

Forecast imagery is a different domain type:

```text
ForecastPrecipitationLayer
```

It is never inserted into `RadarTimeline.frames`.

---

# 31. Forecast uncertainty

Deterministic dashboard forecast can use an appropriate default/best-match model policy.

Advanced comparisons expose:

- model identity;
- run time;
- lead time;
- ensemble members;
- central tendency where defined;
- range/spread.

Use wording such as:

```text
model spread
ensemble range
member distribution
```

not “95% confidence interval” unless the provider/source actually defines that statistical object.

---

# 32. Map implementation

Radar and geographic weather layers should use the **current MethodMesh native/shared map architecture** where available.

Do not introduce WebView/Leaflet as an isolated second map product merely because KeylessWeather used it.

Weather-specific responsibilities:

- overlay/tile source;
- valid-time state;
- legend;
- provider attribution;
- layer semantics;
- radar timeline.

Shared platform responsibilities should remain shared.

Map tap may be used to select a Weather target location when consistent with the host map contract.

---

# 33. Accessibility

Required:

- proper content descriptions for condition glyphs;
- condition text as well as icon;
- source class not encoded by colour alone;
- current/forecast/radar distinctions not encoded by colour alone;
- accessible chart cursor/readout;
- adequate contrast;
- text scaling;
- practical touch targets;
- radar animation pause;
- keyboard/switch-access-compatible controls where host Compose supports them.

---

# 34. Performance

Targets:

- matching cached dashboard data renders immediately;
- network refresh is asynchronous;
- useful current conditions appear as soon as ready;
- radar/advanced data can populate later;
- location change cancels obsolete requests;
- no API calls from recomposition;
- no uncontrolled radar bitmap/tile memory growth;
- no repeated parsing of unchanged payloads merely because UI state changes.

---

# 35. Security / privacy

- no hard-coded provider secret;
- future secrets use MethodMesh credential/reference mechanisms;
- secrets must not appear in result JSON, exports or logs;
- exact GPS is not silently disclosed to third-party weather services;
- cache logging avoids unnecessary duplication of precise location;
- provider URLs/parameters are treated as potentially privacy-bearing metadata.

---

# 36. Attribution

Attribution is provider-aware.

At minimum each declaration should define:

- provider name;
- provider URL;
- required attribution text;
- documentation URL;
- relevant licence/product note if needed.

UI should make attribution accessible without turning the main dashboard into a legal footer.

Technical details may show full source metadata.

---

# 37. Proposed module tree

The canonical handoff root is:

```text
weather/
```

and is intended to sit at:

```text
app/src/main/java/com/example/methodmesh/modules/weather/
```

Suggested internal structure, adjusted to match existing source conventions rather than imposed mechanically:

```text
weather/
├── WeatherModule.kt
├── WeatherMethods.kt
├── WeatherCapabilityScreens.kt
├── WeatherDashboardScreen.kt
├── WeatherDashboardState.kt
│
├── WeatherDomain.kt
├── WeatherResults.kt
├── WeatherProvenance.kt
├── WeatherUnits.kt
├── WeatherSourceClass.kt
│
├── WeatherApiDefinitions.kt
├── WeatherLiveData.kt
├── WeatherProviderMapping.kt
├── WeatherRadarProvider.kt
│
├── WeatherCharts.kt
├── WeatherRadarSurface.kt
├── WeatherLocationSupport.kt
│
└── docs/
    ├── README_Weather.md
    ├── ARCHITECTURE.md
    ├── ODK_INTEGRATION.md
    ├── VALIDATION.md
    ├── PROVIDERS.md
    ├── ATTRIBUTION.md
    ├── MethodMesh_Weather_Module_Specification_v0.02.md
    ├── example_odk_showcase_weather_conditions.xlsx
    ├── example_odk_showcase_weather_forecast.xlsx
    ├── example_odk_showcase_weather_precipitation.xlsx
    ├── example_odk_showcase_weather_radar.xlsx
    ├── example_odk_showcase_weather_wind.xlsx
    ├── example_odk_showcase_weather_sun.xlsx
    └── example_odk_showcase_weather_snapshot.xlsx
```

Tests belong in the repository's existing test-source layout, not inside a fake `weather/test/` production subtree if that is not the host convention.

Do not hand back:

```text
app/
gradle/
settings.gradle
whole project tree
```

The module handoff is one clean `weather/` root.

---

# 38. Implementation sequence

## Phase A — admission-safe skeleton

1. create `modules/weather/`;
2. declare module metadata;
3. declare maturity/connectivity;
4. add method inventory/descriptors;
5. ensure discovery can tolerate incomplete Development methods;
6. add module docs skeleton.

## Phase B — data foundation

7. define Weather domain models;
8. define source/provenance classes;
9. implement Weather-owned `ApiDefinition` declarations;
10. execute them through shared online-data infrastructure;
11. map ResultTree/provider JSON into Weather domain models;
12. implement freshness/cache states;
13. implement location privacy/query-coordinate provenance.

## Phase C — essential methods

14. `weather.conditions`;
15. `weather.forecast`;
16. `weather.precipitation`;
17. `weather.wind`;
18. `weather.sun`;
19. `weather.snapshot`.

Establish direct/preset/protocol/ODK parity early.

## Phase D — dashboard

20. hero;
21. immediate strip;
22. precipitation timeline;
23. hourly strip;
24. daily forecast;
25. source/freshness treatment;
26. navigation into underlying capabilities;
27. state restoration.

## Phase E — radar

28. radar provider declaration/adapter;
29. native map overlay;
30. frame timeline;
31. scrub/play/pause;
32. cache;
33. no-coverage/failure states;
34. strict radar/forecast separation.

## Phase F — advanced met

35. `weather.meteogram`;
36. `weather.atmosphere`;
37. pressure levels;
38. `weather.model_compare`;
39. ensemble UI.

## Phase G — ODK/docs/review

40. Integration Cards;
41. canonical single-invocation XLSForms;
42. JavaRosa/ODK validation;
43. Kobo portability checks;
44. output parity tests;
45. lifecycle/Commit tests;
46. module docs;
47. final v1.08 review;
48. return one clean module root.

---

# 39. Testing

## 39.1 Contract tests

For every admitted method:

- stable ID;
- descriptor;
- tags;
- input parsing;
- setting categories;
- output keys/types;
- missing-value behaviour;
- units;
- structured output;
- Commit;
- post-Commit immutability;
- launch-origin closeout.

## 39.2 Surface parity tests

Representative method(s) must be exercised through:

- direct native;
- dashboard;
- Preset;
- Protocol;
- ODK.

Confirm identical canonical meaning.

## 39.3 Online-data tests

Use fixture/provider responses.

Verify:

- definition IDs;
- URL/query construction;
- rounded-location privacy;
- cache policy;
- attribution;
- structured response preservation;
- provider mapping;
- missing arrays/fields;
- timezone handling;
- model/run metadata.

## 39.4 Radar tests

Explicitly verify:

- observed frames -> `RADAR_OBSERVATION`;
- nowcast only when provider returns actual nowcast;
- model forecast is never inserted into radar timeline;
- sorting;
- no coverage;
- cached frames;
- timestamp accuracy;
- map state restoration.

## 39.5 Offline/cache tests

- fresh cached payload;
- stale cached payload;
- network failure with useful cache;
- network failure without matching cache;
- data-age labels;
- location mismatch never reuses wrong cache;
- `ONLINE_ONLY` methods refuse to fabricate new offline results.

## 39.6 ODK tests

For each shipped canonical showcase:

- exactly one `body::intent` MethodMesh call;
- canonical method ID;
- `input_payload_mode='FULL'`;
- `return_mode='flat'`;
- no `methodmesh_return_namespace`;
- `methodmesh_status`;
- `methodmesh_full_json`;
- only declared Weather return leaves;
- JavaRosa/ODK validity;
- Kobo portability finding recorded;
- Commit returns directly to calling form;
- Cancel returns directly to calling form;
- no silent MethodMesh duplicate persistence.

## 39.7 UI tests

- small phone;
- large phone/tablet where relevant;
- portrait;
- dark mode;
- large font;
- TalkBack/content descriptions;
- chart selection;
- radar controls;
- no clipped daily/hourly cards;
- rotation/state restoration;
- dashboard scroll restoration;
- tap-to-copy.

Use the repository's established test dependencies. Do not add JUnit/import assumptions inconsistent with the host Gradle configuration.

---

# 40. Definition of Done

Weather is not complete merely because the dashboard looks good.

A Development admission candidate is ready for final review when:

1. module is discoverable without breaking app startup;
2. every admitted method has stable metadata;
3. every admitted method has exactly one maturity and connectivity tag;
4. canonical methods remain individually accessible;
5. dashboard does not hide or replace individual capability access;
6. direct/preset/protocol/ODK parity is verified;
7. live results appear in-place;
8. Commit freezes the canonical payload;
9. post-Commit editing cannot silently mutate it;
10. closeout honours launch origin;
11. meaningful state survives normal lifecycle changes;
12. useful scalar/text outputs are tap-to-copy;
13. native execution stays on polished Weather surfaces;
14. Weather-specific logic remains module-owned;
15. shared online-data infrastructure is used rather than duplicated;
16. third-party location disclosure follows declared privacy policy;
17. cache age is visible;
18. online-only methods do not fabricate offline results;
19. radar observations and model forecasts remain semantically separate;
20. provider attribution exists;
21. all Weather ODK calls return `methodmesh_status` and `methodmesh_full_json`;
22. canonical showcases use the v1.08 naming/single-call/unprefixed/no-namespace policy;
23. forms validate or documented intentional findings remain;
24. ODK caller gets control back after Commit/Cancel;
25. no hidden Weather archive is created merely because a result was committed/returned;
26. docs agree with descriptors/runtime/forms;
27. representative tests pass;
28. build passes in target context where build access exists;
29. final module review is against the current Master Book, not an archived version;
30. handoff is one clean `weather/` folder.

---

# 41. Required final self-review checklist

Before handoff ask:

1. What are the canonical Weather methods?
2. Does each descriptor match runtime behaviour?
3. Do dashboard, direct, Preset, Protocol and ODK invoke the same contracts?
4. Are method IDs/keys stable?
5. Does every method have one maturity and one connectivity tag?
6. Are fixed settings, runtime inputs and operational controls correctly separated?
7. Does the useful current result appear on the same screen?
8. Does Commit freeze exactly what the user selected?
9. Can later exploration mutate a committed result accidentally?
10. Does Done/Cancel/Home go to the correct launch origin?
11. Does state survive rotation/navigation?
12. Can useful scalar/text results be tapped to copy?
13. Is there any generic result-screen detour?
14. Does every ODK call capture `methodmesh_status` and `methodmesh_full_json`?
15. Does each canonical XLSForm make exactly one MethodMesh call?
16. Are canonical showcase return keys unprefixed?
17. Is `methodmesh_return_namespace` absent?
18. Does each Integration Card agree with code and XLSForm?
19. Is ODK ownership respected?
20. Is exact GPS silently being sent to an API?
21. Are query coordinates rounded where required?
22. Is radar ever being used as a label for model forecast?
23. Does cache age remain visible?
24. Do `ONLINE_ONLY` methods fail honestly offline?
25. Did Weather add capability-specific facts to shared UI/framework code?
26. Does Weather reuse generic online-data infrastructure?
27. Are provider definitions/attribution/privacy declarative?
28. Are all supplied forms discovered/validated?
29. Does the module build?
30. Is the handoff exactly one clean `weather/` root?

---

# 42. Acceptance experience

Opening Weather should answer, very quickly:

- **Where am I?**
- **What is happening now?**
- **Is it raining?**
- **When is meaningful rain coming?**
- **What has the radar been doing?**
- **What happens over the next hours?**
- **What is the next week doing?**

A normal user should not need to understand model terminology to use the dashboard.

A field researcher should be able to tap a value, see its time/source and Commit it.

An advanced user should be able to open Meteogram/Atmosphere/Model Compare and inspect deeper meteorology.

An ODK form should be able to invoke the same machinery without depending on the dashboard.

The defining product combination is:

> **beautiful overview + honest meteorological provenance + canonical composable capabilities**

---

# 43. Provider notes for implementation review

The current MethodMesh repository already contains:

```text
BundledApiDefinitions.openMeteoCurrentWeather
BundledApiDefinitions.openMeteoDailyForecast
```

with:

- `CacheMode.FRESH_PREFERRED`;
- configured TTL;
- `ApiPrivacy`;
- `LocationDisclosureMode.ROUNDED`;
- `roundedLocationRadiusMeters = 5_000`;
- Open-Meteo attribution;
- documentation URL.

The Astronomy module also demonstrates module-owned Open-Meteo `ApiDefinition` declarations and execution through `As100ApiGetMethod`.

Weather should treat those implementations as current code-pattern evidence, while the Master Book remains normative.

Do not copy Astronomy's weather field list mechanically; define Weather's own declared products around the Weather capability contract.

---

# 44. Final implementation directive

Build a **new MethodMesh Weather module**, not a wrapped copy of KeylessWeather.

Use KeylessWeather for visual/interaction inspiration.

Use MethodMesh v1.08 for architecture and contracts.

Use existing MethodMesh online-data infrastructure for external weather data.

Make the dashboard exceptional.

Keep every underlying Weather method individually callable.

Preserve source class, time, location disclosure and model provenance.

Return useful values first.

Keep full structured/audit data available.

Make ODK first-class.

Do not fake radar, observations, freshness, or offline capability.

Return only the finished `weather/` module folder when implementation is handed back.
