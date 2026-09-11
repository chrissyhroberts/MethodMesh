# MethodMesh Weather Module Specification

**Working module name:** Weather  
**Canonical namespace:** `weather`  
**Status:** implementation specification  
**Target:** MethodMesh current module architecture / Master Book lifecycle  
**Design ancestor:** `chrissyhroberts/weather_app_android` (“KeylessWeather”)  
**Design principle:** preserve the compact, instrument-like visual direction of KeylessWeather while rebuilding the module as a native, capability-driven MethodMesh module.

---

## 1. Purpose

Weather is a first-class MethodMesh module for obtaining, inspecting, visualising, capturing and returning meteorological data.

The module must serve two equally important use cases:

1. **Human weather instrument**
   - a beautiful, information-dense dashboard for quickly understanding current weather, rainfall, radar, forecast and meteorological detail;
   - suitable for everyday field use;
   - fast enough to glance at, but deep enough to inspect professionally.

2. **MethodMesh capability provider**
   - weather functions must be callable individually;
   - every canonical capability must work from direct native use, Presets, Protocols and ODK;
   - the dashboard must orchestrate capabilities, not replace them;
   - live values must remain working results until explicitly committed where the launch contract requires a result.

Weather therefore is **not** a conventional weather-app island inside MethodMesh. It is a coherent set of meteorological methods with a high-quality dashboard layered over them.

---

## 2. Source design to preserve and supersede

The KeylessWeather prototype establishes useful visual and behavioural direction:

- compact section hierarchy;
- strong headings;
- clear NOW / TODAY / NEXT HOURS / NEXT DAYS grouping;
- location-aware forecast;
- quantitative rainfall;
- animated observed radar;
- radar timestamp and manual frame controls;
- restrained, utilitarian presentation;
- explicit distinction between observed radar and future precipitation forecast.

These ideas should be retained.

The prototype architecture must **not** be retained. In particular, the MethodMesh module must not be implemented as:

- one monolithic Activity;
- direct ad-hoc HTTP calls from UI code;
- programmatically assembled legacy Android views;
- a WebView-hosted Leaflet weather application;
- hard-wired assumptions about one weather API;
- a dashboard with no independently callable canonical methods.

The MethodMesh module should be a clean native implementation using the same architectural, navigation, lifecycle, state and capability conventions as the rest of MethodMesh.

---

# 3. Non-negotiable MethodMesh rules

## 3.1 Dashboard is an orchestration surface

The Weather Dashboard is a top-level Weather surface.

It may display the outputs of many canonical capabilities together, but no capability may exist only inside the dashboard.

For example:

- rainfall shown in the dashboard is backed by `weather.precipitation`;
- the radar panel is backed by `weather.radar`;
- current meteorological values are backed by `weather.conditions`;
- detailed time-series inspection is backed by `weather.meteogram`;
- the daily forecast is backed by `weather.forecast`.

Those same capabilities must remain individually launchable and configurable elsewhere in MethodMesh.

## 3.2 Canonical capability parity

Each canonical capability must maintain parity across, where meaningful:

- Weather Dashboard
- Capabilities
- direct native launch
- Presets
- Protocols
- ODK/XLSForm roundtrip

There must not be parallel dashboard-only implementations with subtly different behaviour or output schemas.

## 3.3 Live working result -> Commit

Interactive weather surfaces produce a **live working result**.

Changing location, moving through time, selecting a radar frame, selecting a forecast time or changing variables updates that working result.

It must not silently overwrite or return data to the caller.

When a capability was launched in a result-returning context, **Commit** freezes and returns the current result.

The result must contain enough temporal and provenance metadata to remain interpretable later.

## 3.4 Launch-origin-aware completion

Completion behaviour must depend on launch origin.

Examples:

- launched from Weather Dashboard: remain within Weather unless the user explicitly navigates away;
- launched directly from Capabilities: normal native completion behaviour;
- launched from Preset/Protocol execution: return the committed result to that execution context;
- launched from ODK: return the committed typed result through the established ODK bridge;
- launched in inspection-only mode: no fabricated result-return action.

Android Back must follow MethodMesh navigation conventions and must not be repurposed as a hidden “save” or “commit” action.

## 3.5 Tap-to-copy

Human-readable scalar values should support tap-to-copy where that is useful, including:

- latitude / longitude;
- temperature;
- rainfall;
- pressure;
- humidity;
- dew point;
- wind speed;
- wind direction;
- gust;
- visibility;
- UV;
- timestamps;
- model/provider identifiers.

Copy affordance should be discoverable but visually quiet.

## 3.6 State preservation

Transient exploration state should survive normal UI transitions and configuration changes, including:

- selected location;
- selected dashboard time;
- selected radar frame;
- play/pause state where reasonable;
- selected meteorological variables;
- chart zoom/window;
- advanced-section expansion state;
- last successful data payload;
- freshness metadata.

The module should not repeatedly refetch identical data merely because the user navigated away and back.

---

# 4. Product character

The Weather module should feel like a **meteorological instrument**, not a consumer weather portal.

Desired character:

- luxurious but restrained;
- scientific rather than decorative;
- dense without feeling cramped;
- excellent typography for numerical data;
- generous hierarchy around the most important measurements;
- beautifully drawn weather symbols;
- precise charts;
- subtle animation only where it communicates time or state;
- no advertising-style panels;
- no generic “weather app” gradients unless they serve a real visual function;
- no emoji as the principal icon system.

The visual ancestry of KeylessWeather is appropriate: pale/quiet surfaces, explicit sectioning, strong labels, readable numbers and compact cards. The MethodMesh version should refine that language rather than replace it with standard Material-demo styling.

Dark mode should be supported using the host MethodMesh theme system.

---

# 5. Weather Dashboard

## 5.1 Dashboard structure

The dashboard should be vertically scrollable and composed from capability-backed panels.

Recommended order:

1. Location / freshness header
2. Current conditions hero
3. Immediate precipitation / wind / pressure strip
4. Precipitation timeline
5. Radar
6. Hourly forecast
7. Meteogram
8. Multi-day forecast
9. Detailed meteorology
10. Provenance / source status

The most important information must appear before scrolling far.

## 5.2 Location / freshness header

Display:

- resolved place name where available;
- latitude / longitude;
- location source:
  - GPS;
  - searched place;
  - map selection;
  - manually entered coordinates;
  - preset;
  - protocol/ODK supplied;
- last data update time;
- forecast model valid/run time when available;
- compact refresh control.

Do not silently fall back to London or any other arbitrary city.

When no location is available, present explicit location options.

## 5.3 Current conditions hero

Large primary values:

- weather symbol;
- temperature;
- condition text;
- apparent temperature;
- high / low for the local day.

Secondary values should be immediately visible but subordinate:

- precipitation now;
- humidity;
- dew point;
- wind speed/direction;
- gust;
- sea-level pressure;
- pressure tendency if derivable from valid recent values.

The hero must not misrepresent a model “current” estimate as a physical observation. A small source-state treatment should distinguish:

- OBSERVED;
- ANALYSIS;
- MODELLED CURRENT;
- FORECAST.

## 5.4 Immediate conditions strip

A narrow glanceable strip should answer practical questions such as:

- Is it raining now?
- How much?
- When is meaningful rain expected next?
- How strong is the wind?
- What is the current gust?
- Is pressure rising or falling?

This strip should be generated from canonical capability outputs rather than bespoke dashboard logic.

## 5.5 Precipitation timeline

A central visual element.

Default view:

- recent past where a suitable source exists;
- NOW marker;
- near-term forecast;
- approximately 24 hours visible, with horizontal exploration to a longer interval.

Display quantitative precipitation, preferably as mm per interval / mm·h⁻¹ as appropriate to source semantics.

Where available, show:

- rain;
- showers;
- snowfall / water equivalent;
- precipitation probability.

Observed/history, analysis, nowcast and forecast regions must be visually distinct.

The graph must retain exact values under cursor/tap.

Never imply continuity of source type where it does not exist.

## 5.6 Radar

Radar should be a native map panel, not a WebView application.

Required:

- location marker;
- pan;
- zoom;
- radar overlay;
- explicit radar timestamp;
- frame scrubber;
- previous / next frame;
- play / pause;
- clear coverage/unavailable state;
- attribution;
- current frame provenance.

The timeline must distinguish:

- observed radar frames;
- provider-supplied nowcast frames, if genuinely supplied;
- model precipitation forecast layers, if offered as a separate forecast layer.

### Critical semantic rule

**Modelled future precipitation imagery must never be labelled “radar”.**

If a provider supplies no future radar/nowcast, radar ends at the newest available observed frame.

A forecast precipitation layer may follow it only if explicitly labelled as model forecast.

The UI can make the transition elegant, but the underlying data classes and labels must remain honest.

## 5.7 Hourly forecast

Recommended default: next 24 hours with easy extension.

Each time step should make at least the following available:

- time;
- weather symbol;
- temperature;
- precipitation amount;
- precipitation probability where supported;
- wind;
- optional “feels like”.

Avoid using probability alone when quantitative rainfall is available.

## 5.8 Meteogram

The meteogram is the professional time-series inspection surface.

Default series:

- temperature;
- apparent temperature / dew point;
- precipitation;
- wind + gust;
- pressure;
- cloud cover.

Optional series:

- humidity;
- visibility;
- low / mid / high cloud;
- UV;
- solar radiation;
- CAPE/CIN;
- freezing level;
- wet-bulb temperature;
- soil temperature/moisture;
- other supported variables.

Requirements:

- common time cursor;
- exact value readout;
- pan/zoom or time-range selection;
- sensible auto-scaling;
- units always visible;
- variable visibility controls;
- touch selection must update the working result.

## 5.9 Multi-day forecast

Compact daily cards for approximately 7-10 days by default, extendable to provider-supported horizon.

Each day:

- day/date;
- condition;
- high/low;
- quantitative precipitation;
- precipitation probability where available;
- maximum wind/gust;
- optional UV.

Cards must remain readable on small screens without horizontal clipping.

## 5.10 Detailed meteorology

Expandable section or dedicated full-screen route exposing advanced values without overwhelming the dashboard.

Candidate variables:

### Near-surface
- temperature 2 m;
- apparent temperature;
- relative humidity;
- dew point;
- wet-bulb temperature;
- vapour pressure deficit;
- surface pressure;
- mean sea-level pressure;
- visibility;
- weather code.

### Wind
- speed;
- direction;
- gust;
- supported upper-level wind values.

### Cloud
- total cloud;
- low cloud;
- mid cloud;
- high cloud;
- fog/near-surface cloud where supported.

### Radiation / sun
- sunrise;
- sunset;
- daylight duration;
- sunshine duration;
- UV;
- shortwave radiation;
- direct/diffuse radiation where supported.

### Convection / atmospheric
- CAPE;
- CIN;
- lifted index;
- freezing level;
- boundary-layer height;
- total column water vapour.

### Land surface
- soil temperature;
- soil moisture;
- evapotranspiration;
- reference evapotranspiration.

### Pressure levels
Advanced mode may expose selected pressure-level values:

- temperature;
- relative humidity;
- wind;
- geopotential height;
- cloud.

This is an advanced meteorological feature and should not clutter the default dashboard.

---

# 6. Canonical capability inventory

The initial module should expose the following canonical methods.

## 6.1 `weather.conditions`

**Purpose:** obtain meteorological conditions for a location and valid time.

### Inputs

- location:
  - latitude;
  - longitude;
  - optional altitude;
- target time:
  - default NOW;
- variable set:
  - standard;
  - detailed;
  - explicit list;
- provider/model preference;
- unit preference.

### Working result

Current displayed/selected condition set.

### Commit output

```text
WeatherConditionsResult
  schema_version
  method_id = "weather.conditions"

  requested_location
  resolved_location
  location_source

  requested_time
  valid_time
  fetched_at

  data_class
  provider
  model
  model_run_time
  grid_metadata

  temperature_c
  apparent_temperature_c
  relative_humidity_pct
  dew_point_c
  wet_bulb_temperature_c?
  precipitation_mm
  rain_mm?
  snowfall_cm?
  pressure_msl_hpa?
  surface_pressure_hpa?
  visibility_m?
  cloud_cover_pct?
  wind_speed_ms
  wind_direction_deg
  wind_gust_ms?
  weather_code
  condition_label

  extra_variables {}
```

All optional fields must remain genuinely optional. Do not insert zero for unavailable values.

---

## 6.2 `weather.forecast`

**Purpose:** return an hourly and/or daily forecast time series.

### Inputs

- location;
- start time;
- horizon;
- temporal resolution;
- hourly/daily/both;
- variable set;
- provider/model;
- ensemble mode optional.

### Commit output

A typed time series plus provenance.

Each time point must carry or inherit:

- valid time;
- source class;
- provider;
- model/run;
- units.

---

## 6.3 `weather.precipitation`

**Purpose:** inspect and return precipitation timing, intensity and accumulation.

### Inputs

- location;
- interval;
- precipitation types;
- observed/history inclusion;
- forecast inclusion;
- threshold for “meaningful rain”;
- provider/model.

### UI

Dedicated precipitation graph.

### Derived values

Where valid:

- current precipitation;
- total expected precipitation over selected interval;
- next precipitation time;
- next threshold-exceedance time;
- maximum forecast intensity;
- duration above threshold.

### Commit output

`WeatherPrecipitationResult` containing selected interval, time series, summary values and provenance.

---

## 6.4 `weather.radar`

**Purpose:** inspect radar imagery/time and capture a radar state.

### Inputs

- location;
- zoom;
- history window;
- provider;
- optional colour scale;
- optional forecast-layer handoff.

### Working result

Current frame + viewport.

### Commit output

```text
WeatherRadarResult
  method_id
  location
  viewport
  frame_time
  frame_class
  provider
  provider_frame_id/path?
  generated_at?
  coverage_state
```

Do not attempt to return a raster image through normal scalar ODK roundtrip unless the wider MethodMesh file contract explicitly supports that route.

---

## 6.5 `weather.meteogram`

**Purpose:** visual inspection of detailed meteorological time series.

### Inputs

- location;
- date/time range;
- selected variables;
- provider/model.

### Working result

Selected cursor time and associated variable values.

### Commit output

- selected time;
- displayed range;
- selected variable set;
- scalar values at selected time;
- optional time-series bundle where caller supports structured output.

---

## 6.6 `weather.wind`

**Purpose:** wind-focused field instrument.

### Inputs

- location;
- valid time;
- altitude/height where supported;
- forecast horizon.

### UI

Prominent direction compass/arrow plus:

- direction degrees + compass point;
- sustained wind;
- gust;
- time series.

### Commit output

Typed wind result with units and provenance.

---

## 6.7 `weather.atmosphere`

**Purpose:** advanced meteorological inspection.

### Inputs

- location;
- valid time/range;
- selected atmospheric variables;
- optional pressure levels;
- provider/model.

### Outputs

Typed map of requested values plus complete variable/unit/provenance information.

This is intentionally advanced and should be marked accordingly in discovery.

---

## 6.8 `weather.sun`

**Purpose:** solar/daylight conditions.

### Inputs

- location;
- date.

### Outputs

- sunrise;
- sunset;
- daylight duration;
- sunshine duration where supported;
- UV maximum/current/forecast;
- radiation values where requested.

---

## 6.9 `weather.snapshot`

**Purpose:** create a coherent research-friendly meteorological snapshot for a location and timestamp.

This is a particularly important MethodMesh capability.

### Inputs

- latitude;
- longitude;
- timestamp;
- optional altitude;
- source policy:
  - best available;
  - model/reanalysis;
  - archived forecast;
  - explicit provider/model;
- requested variable profile.

### Output

A stable structured bundle designed for external data capture.

The snapshot must describe what kind of meteorological evidence produced each result.

Potential uses:

- environmental context for sample collection;
- exposure metadata;
- fieldwork context;
- linking interview/event timestamps to weather;
- ecological and epidemiological research workflows.

---

## 6.10 `weather.model_compare`

**Purpose:** compare forecast models and/or ensembles.

Advanced capability.

### Inputs

- location;
- time/horizon;
- variables;
- selected models;
- optional ensembles.

### UI

- aligned traces;
- median/mean where appropriate;
- spread/interval;
- model identity;
- initialization/run times.

### Output

Comparison bundle with no false impression that model spread is equivalent to a calibrated confidence interval.

---

# 7. Data-class and provenance model

Every external weather value must be classified.

Canonical data class:

```text
WeatherDataClass =
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

Do not collapse these into one generic “weather data” state.

Minimum provenance:

```text
WeatherProvenance
  provider_id
  provider_name
  model_id?
  model_name?
  model_run_time?
  valid_time
  fetched_at
  data_class
  source_resolution?
  grid_latitude?
  grid_longitude?
  elevation_used_m?
  licence_id?
  attribution_text?
  provider_metadata {}
```

Derived values must identify their inputs / derivation class where reasonable.

Example:

“pressure falling” may be a MethodMesh-derived interpretation of two or more modelled pressure values. It must not be recorded as a provider observation.

---

# 8. Provider architecture

The UI must not know how Open-Meteo or RainViewer works.

Use provider interfaces and repositories.

Suggested conceptual interfaces:

```kotlin
interface WeatherForecastProvider
interface WeatherHistoricalProvider
interface WeatherEnsembleProvider
interface WeatherRadarProvider
interface WeatherGeocodingProvider
```

Potential shared operations:

```kotlin
suspend fun getConditions(request): WeatherConditionsResult
suspend fun getForecast(request): WeatherForecastSeries
suspend fun getHistorical(request): WeatherTimeSeries
suspend fun getEnsemble(request): WeatherEnsembleSeries
suspend fun getRadarTimeline(request): RadarTimeline
```

A provider registry should report capabilities dynamically.

Example:

```text
ProviderFeatures
  conditions
  hourly_forecast
  daily_forecast
  historical
  historical_forecast
  ensemble
  radar
  radar_nowcast
  pressure_levels
  soil
  solar
```

The UI must gracefully hide/disable unsupported variables rather than crash or fabricate substitutes.

---

# 9. Default providers

## 9.1 Open-Meteo

Recommended default for:

- general forecast;
- current modelled conditions;
- detailed surface variables;
- historical/reanalysis access;
- historical forecasts;
- pressure-level data;
- model-specific forecasts;
- ensemble forecasts.

The implementation must keep attribution/licence metadata with provider configuration.

Provider response adapters should normalize Open-Meteo output to MethodMesh domain models immediately. Raw API field names should not spread through UI code.

## 9.2 RainViewer

Recommended initial radar provider.

Treat as:

- radar imagery;
- radar timeline metadata;
- optional provider-defined nowcast only when the live API actually supplies it.

Requirements:

- cache timeline metadata;
- cache tiles according to reasonable provider/client policy;
- retry conservatively;
- degrade cleanly when unavailable;
- show attribution;
- never claim an SLA;
- never synthesize “future radar” to fill gaps.

## 9.3 Provider extensibility

Future providers may include:

- national meteorological services;
- paid operational services;
- self-hosted weather backends;
- local station networks;
- research project endpoints.

Adding a provider should require an adapter, not dashboard rewrites.

---

# 10. Location handling

Weather location is a first-class input.

Supported sources:

1. current device location;
2. saved location;
3. map pick;
4. place search/geocoding;
5. manual coordinates;
6. Preset;
7. Protocol;
8. ODK-supplied coordinates.

No arbitrary fallback city.

If permission is denied:

- continue to operate;
- offer other selection mechanisms;
- explain that device location is unavailable without nagging.

Persist recently used locations using MethodMesh conventions.

Location display should support tap-to-copy.

When a provider evaluates a nearby model grid cell rather than the exact requested coordinate, preserve both requested and evaluated/grid locations where that information is available.

---

# 11. Time semantics

Weather is extremely sensitive to time semantics.

Always distinguish:

- request time;
- valid time;
- provider generation time;
- model initialization/run time;
- fetch time;
- local display time zone;
- UTC storage where appropriate.

Internally use unambiguous machine timestamps.

UI should generally display local time for the selected location, with UTC accessible in provenance/detail.

Never derive a data timestamp merely from the phone's current clock when the provider supplies a valid time.

---

# 12. Units

Domain results should use a stable canonical internal unit system.

Recommended canonical units:

- temperature: °C;
- precipitation: mm;
- wind: m/s;
- pressure: hPa;
- visibility: m;
- direction: degrees;
- radiation: provider-standard SI-compatible representation.

UI may display user-preferred units.

Returned contracts must explicitly define units and must not depend on UI locale.

Presets may include display-unit preferences but must not alter scientific meaning.

---

# 13. Caching and offline behaviour

Weather is network-dependent but must remain useful in weak connectivity.

Cache:

- last successful conditions payload;
- forecast series;
- radar timeline metadata;
- selected radar tiles within a bounded cache;
- geocoding results;
- provider capability metadata.

Every cached presentation must display age/freshness.

Examples:

- `Updated 8 min ago`
- `Cached · 3 h old`
- `Forecast fetched yesterday 18:20`

Never present cached weather as freshly updated.

Offline behaviour:

- dashboard shows last usable cached data;
- interactive charts remain usable;
- radar shows cached frames/tiles where available;
- unavailable uncached areas explain their state;
- Commit may return cached data if the user chooses to do so, but provenance must preserve original fetch time and an explicit stale/cache state.

Do not block the whole dashboard because one provider fails.

Radar failure must not remove forecast.
Geocoding failure must not prevent coordinate-based forecast.
Advanced-variable failure must not blank basic current conditions.

---

# 14. Refresh policy

Avoid uncontrolled API traffic.

Recommended pattern:

- refresh on explicit user action;
- refresh on entry when cached value is beyond a configured freshness threshold;
- de-duplicate concurrent identical requests;
- cancel obsolete requests when location changes;
- apply provider-specific retry/backoff;
- do not refetch every recomposition or navigation event.

Radar animation should fetch timeline metadata once per sensible refresh period, not for every frame transition.

---

# 15. Persistence model

Persist at least:

```text
WeatherSessionState
  selected_location
  selected_time
  selected_dashboard_range
  selected_radar_frame
  radar_playing
  radar_zoom
  radar_viewport
  selected_meteogram_variables
  expanded_sections
  last_provider_selection
  unit_preferences
```

Persist cache separately from ephemeral UI state.

Presets should store intentional configuration, not accidental transient UI state.

---

# 16. Presets

All suitable capabilities must support Presets.

Examples:

### “Current field weather”
- `weather.conditions`
- current GPS;
- standard variables;
- default provider;
- metric units.

### “Rain next 24 h”
- `weather.precipitation`
- current GPS;
- 24 h horizon;
- rainfall + probability;
- threshold 0.2 mm/h.

### “Sampling-site snapshot”
- `weather.snapshot`
- location supplied at launch;
- timestamp supplied at launch;
- temperature;
- humidity;
- rainfall;
- pressure;
- wind;
- provenance.

### “Detailed met”
- `weather.atmosphere`
- detailed surface variable profile;
- selected pressure levels.

Preset launch must resolve to the same canonical method as direct launch.

---

# 17. Protocols

Weather capabilities should be usable as normal protocol steps.

Examples:

```text
Get GPS
-> Weather Snapshot
-> Photo
-> Commit field record
```

or:

```text
Select study site
-> Rain next 48 h
-> Conditional fieldwork decision
```

Protocol execution must be able to pass typed location/time inputs directly without forcing the user to reselect them.

Committed output should be available to later steps through normal MethodMesh protocol data flow.

---

# 18. ODK / XLSForm roundtrip

Weather should ship with module-owned example forms.

Recommended forms:

```text
docs/example_odk_weather_snapshot.xlsx
docs/example_odk_rainfall_context.xlsx
docs/example_odk_weather_forecast.xlsx
```

The exact canonical directory should follow the host MethodMesh module convention.

## 18.1 Weather snapshot form

Inputs:

- GPS coordinate;
- event timestamp;
- optional altitude;
- requested profile/provider.

Possible returned fields:

```text
weather_valid_time
weather_fetched_at
weather_data_class
weather_provider
weather_model
weather_model_run

weather_temp_c
weather_apparent_temp_c
weather_dewpoint_c
weather_humidity_pct

weather_precip_mm
weather_rain_mm
weather_snowfall_cm

weather_pressure_msl_hpa
weather_surface_pressure_hpa

weather_wind_ms
weather_wind_dir_deg
weather_wind_gust_ms

weather_cloud_pct
weather_visibility_m
weather_uv

weather_code
weather_condition
```

Optional/unavailable values must remain blank/null according to the ODK bridge contract.

## 18.2 Rainfall context form

Inputs:

- location;
- current/event time;
- lookback;
- lookahead.

Returns:

- recent precipitation total;
- forecast precipitation total;
- next rain time;
- maximum forecast intensity;
- provider/model/provenance.

The form must not imply that model-derived historical rainfall is a gauge observation.

## 18.3 Forecast form

Inputs:

- GPS;
- horizon;
- optional threshold.

Returns a compact summary appropriate to XLSForm limitations, for example:

- high/low;
- total precipitation;
- maximum wind;
- threshold exceedance;
- next precipitation time;
- source metadata.

Where full series cannot be represented cleanly in ODK scalar fields, the module should return an explicitly defined summary rather than silently flattening or truncating data.

## 18.4 ODK lifecycle

ODK launch:

1. receive parameters;
2. instantiate canonical capability;
3. obtain/update live working result;
4. permit human inspection where the form contract expects it;
5. Commit;
6. return typed values;
7. resume form cleanly.

Direct API-only silent execution may be added only if the wider MethodMesh architecture explicitly supports non-interactive capability execution. It must not be improvised specifically for Weather.

---

# 19. Dashboard interactions and Commit behaviour

The dashboard itself is primarily an overview.

Tapping a panel should either:

- expand inline; or
- open the canonical capability surface with current dashboard location/context already supplied.

Examples:

- tap precipitation graph -> `weather.precipitation`;
- tap radar -> `weather.radar`;
- tap wind -> `weather.wind`;
- tap detailed data -> `weather.atmosphere`.

Returning from a capability should preserve dashboard scroll and state.

If the dashboard was itself launched in a result-required context, it may offer a purposeful “Weather snapshot” Commit action backed by `weather.snapshot`. It must not invent a dashboard-specific incompatible output schema.

---

# 20. Error and status language

Errors should be specific.

Good:

- `Forecast unavailable`
- `Radar unavailable · forecast still current`
- `Using cached forecast from 08:15`
- `No radar coverage at this location`
- `This provider does not supply CAPE here`
- `Location permission denied · choose a place or coordinates`

Bad:

- `Something went wrong`
- silently empty panels;
- zero-filled data;
- endless spinners;
- substituting one provider/data class without saying so.

---

# 21. Accessibility

Required:

- content descriptions for all weather glyphs;
- non-colour encoding of observed vs forecast;
- chart values accessible through touch/readout;
- adequate contrast;
- scalable text;
- minimum practical touch targets;
- radar playback controllable without relying on tiny icons;
- avoid animation that cannot be paused.

Weather symbols must not be the sole carrier of meaning; include concise condition text.

---

# 22. Suggested module architecture

The exact root/package path must follow the MethodMesh canonical module structure, but the Weather module should conceptually contain:

```text
weather/
├── README.md
├── docs/
│   ├── example_odk_weather_snapshot.xlsx
│   ├── example_odk_rainfall_context.xlsx
│   ├── example_odk_weather_forecast.xlsx
│   ├── provider_notes.md
│   └── attribution.md
│
├── domain/
│   ├── WeatherModels.kt
│   ├── WeatherRequests.kt
│   ├── WeatherResults.kt
│   ├── WeatherProvenance.kt
│   ├── WeatherUnits.kt
│   └── WeatherDataClass.kt
│
├── providers/
│   ├── WeatherProviderRegistry.kt
│   ├── WeatherForecastProvider.kt
│   ├── WeatherHistoricalProvider.kt
│   ├── WeatherEnsembleProvider.kt
│   ├── WeatherRadarProvider.kt
│   ├── openmeteo/
│   │   ├── OpenMeteoProvider.kt
│   │   ├── OpenMeteoApi.kt
│   │   ├── OpenMeteoDtos.kt
│   │   └── OpenMeteoMapper.kt
│   └── rainviewer/
│       ├── RainViewerRadarProvider.kt
│       ├── RainViewerApi.kt
│       ├── RainViewerDtos.kt
│       └── RainViewerMapper.kt
│
├── data/
│   ├── WeatherRepository.kt
│   ├── WeatherCache.kt
│   ├── RadarTileCache.kt
│   └── WeatherRefreshPolicy.kt
│
├── capabilities/
│   ├── WeatherCapabilityRegistry.kt
│   ├── conditions/
│   ├── forecast/
│   ├── precipitation/
│   ├── radar/
│   ├── meteogram/
│   ├── wind/
│   ├── atmosphere/
│   ├── sun/
│   ├── snapshot/
│   └── modelcompare/
│
├── dashboard/
│   ├── WeatherDashboardScreen.kt
│   ├── WeatherDashboardViewModel.kt
│   ├── WeatherHero.kt
│   ├── PrecipitationTimeline.kt
│   ├── RadarPanel.kt
│   ├── HourlyForecastStrip.kt
│   ├── WeatherMeteogram.kt
│   ├── DailyForecastStrip.kt
│   └── DetailedMeteorologyPanel.kt
│
├── location/
│   ├── WeatherLocationResolver.kt
│   ├── WeatherLocationPicker.kt
│   └── WeatherGeocoder.kt
│
├── integration/
│   ├── WeatherPresetAdapter.kt
│   ├── WeatherProtocolAdapter.kt
│   └── WeatherOdkAdapter.kt
│
└── test/
    ├── WeatherMapperTest.kt
    ├── WeatherProvenanceTest.kt
    ├── WeatherCapabilityContractTest.kt
    ├── WeatherCacheTest.kt
    ├── WeatherOdkRoundtripTest.kt
    └── RadarSemanticsTest.kt
```

Do not copy this tree mechanically if the Master Book mandates different package placement. The separation of concerns is normative; exact host-folder conventions are inherited from MethodMesh.

---

# 23. Domain-model requirements

Avoid exposing provider DTOs to capability/UI code.

Normalized models should support:

```text
WeatherPoint
WeatherTimeSeries<T>
WeatherVariable<T>
WeatherForecastPoint
WeatherDailyPoint
WeatherConditionsResult
WeatherPrecipitationResult
WeatherWindResult
WeatherRadarFrame
RadarTimeline
WeatherSnapshotResult
WeatherEnsembleResult
WeatherProvenance
```

A `WeatherVariable` should be able to carry:

- canonical variable ID;
- numeric/string value;
- unit;
- valid time;
- optional quality/status;
- provenance override if different from parent series.

This allows mixed-source bundles without losing provenance.

---

# 24. Variable IDs

Use stable canonical variable IDs independent of provider naming.

Examples:

```text
temperature_2m
apparent_temperature
relative_humidity_2m
dew_point_2m
wet_bulb_temperature_2m

precipitation
rain
showers
snowfall
precipitation_probability

pressure_msl
surface_pressure

cloud_cover
cloud_cover_low
cloud_cover_mid
cloud_cover_high
visibility

wind_speed_10m
wind_direction_10m
wind_gust_10m

uv_index
sunshine_duration
shortwave_radiation

cape
cin
lifted_index
freezing_level_height
boundary_layer_height

soil_temperature_0_7cm
soil_moisture_0_7cm
```

Provider mappings should convert external field names to canonical IDs.

Do not make Open-Meteo field strings the implicit MethodMesh public contract.

---

# 25. Research data integrity

Weather is especially likely to be used later as research metadata.

Therefore:

- never label reanalysis as observation;
- never label forecast as observation;
- never label modelled future precipitation as radar;
- preserve provider/model/run;
- preserve valid timestamp;
- preserve fetch timestamp;
- preserve requested coordinate;
- preserve provider/grid coordinate where available;
- preserve units;
- preserve missingness;
- preserve source licence/attribution information at module/provider level.

Where a historical timestamp is queried using reanalysis, the result should clearly state `REANALYSIS`.

Where a past archived forecast is intentionally requested, state `HISTORICAL_FORECAST`.

These are scientifically different data products.

---

# 26. Map architecture

Use MethodMesh's native map stack.

Weather should not introduce a second map framework merely for radar.

Map layers should conceptually include:

- base map;
- current location;
- radar;
- forecast precipitation layer;
- optional weather overlays later.

Layer metadata should identify source and valid time.

The radar colour legend must always be accessible.

The user should be able to tap the map to inspect coordinates and, where supported, use the selected location as the new weather target.

---

# 27. Radar timeline architecture

Represent radar time explicitly:

```text
RadarTimeline
  frames[]
  newest_observation_time
  nowcast_frames[]
  generated_at
  provider
```

Each frame:

```text
RadarFrame
  frame_id
  valid_time
  frame_class = RADAR_OBSERVATION | NOWCAST
  tile_template
  generated_at?
```

Playback order:

`old observed -> recent observed -> nowcast (only if provider supplied)`

If no nowcast frames exist:

`old observed -> recent observed -> STOP`

Forecast precipitation imagery is a separate layer/timeline class and never inserted into the radar frame array.

---

# 28. Forecast uncertainty

Weather Dashboard may default to a best-match deterministic forecast.

Advanced views should be capable of exposing uncertainty.

`weather.model_compare` and ensemble mode should show:

- model/member identity;
- central tendency where calculated;
- range/spread;
- run time;
- lead time.

Do not describe raw ensemble spread as a statistically calibrated confidence interval.

When models disagree strongly, the module may surface that fact descriptively.

---

# 29. Performance

Targets:

- cached dashboard should render immediately;
- network refresh must not block initial UI;
- first useful current-condition update should appear as soon as available;
- slower radar/advanced calls may populate afterward;
- charts should remain smooth for normal forecast horizons;
- radar animation should not cause uncontrolled memory growth;
- cancellation should occur when target location/time changes.

Compose state should be scoped carefully to avoid network calls or expensive transformations during recomposition.

---

# 30. Security and privacy

- GPS coordinates should not be sent anywhere except providers required for the requested weather operation.
- No account is required by the initial default provider set.
- API keys for future providers must use MethodMesh's established secret/config mechanism and must never be committed into module source.
- Provider URLs and request logging must avoid unnecessary persistence of precise location.
- Cached precise locations should follow MethodMesh privacy/storage expectations.

---

# 31. Attribution

The module should contain a consistent attribution surface accessible from the dashboard/detail screens.

Attribution must be provider-aware.

Do not hard-code one attribution footer if the result may come from another provider.

Attribution should be visible enough to satisfy provider requirements without dominating the instrument UI.

---

# 32. Testing

## 32.1 Contract tests

For every capability:

- stable method ID;
- input parsing;
- output typing;
- null/missing handling;
- units;
- provenance;
- Commit semantics;
- launch-origin completion.

## 32.2 Provider mapping tests

Use stored fixture responses.

Test:

- current values;
- hourly arrays;
- daily arrays;
- missing fields;
- unsupported variables;
- timezone offsets;
- model/run metadata;
- provider error payloads.

## 32.3 Radar tests

Must explicitly test:

- observed frames classified as radar observations;
- nowcast only classified when provider reports it;
- no synthetic forecast frames inserted into observed radar;
- empty radar coverage;
- timeline sorting;
- frame timestamps.

## 32.4 Cache tests

- fresh cache;
- stale cache;
- offline presentation;
- refresh replacement;
- cache separation by coordinate/provider/request.

## 32.5 ODK tests

Roundtrip for all shipped forms.

Check:

- input coordinate/time parsing;
- return types;
- blank optional fields;
- Commit;
- cancellation/back;
- provenance return;
- no accidental stale-value substitution.

## 32.6 UI tests

- small screen;
- large screen;
- portrait;
- dark mode;
- font scaling;
- no clipped forecast cards;
- radar controls accessible;
- state retained through navigation.

Do not add test-source dependencies that the host application does not provide. Match the project's established test framework and Gradle configuration.

---

# 33. Acceptance criteria

The Weather module is not complete until all of the following are true:

1. Weather Dashboard is polished and usable as a standalone weather instrument.
2. `weather.conditions` works independently.
3. `weather.forecast` works independently.
4. `weather.precipitation` works independently.
5. `weather.radar` works independently.
6. `weather.meteogram` works independently.
7. `weather.wind` works independently.
8. `weather.atmosphere` works independently.
9. `weather.sun` works independently.
10. `weather.snapshot` works independently.
11. `weather.model_compare` is either implemented or explicitly marked deferred without breaking registry parity.
12. Dashboard panels reuse canonical domain/repository logic.
13. Preset exposure exists for all applicable methods.
14. Protocol exposure exists for all applicable methods.
15. ODK roundtrip is implemented for the shipped forms.
16. Working-result -> Commit semantics are correct.
17. Launch-origin-aware completion is correct.
18. Tap-to-copy works on appropriate scalar values.
19. State survives normal navigation/configuration.
20. No London fallback exists.
21. No radar-as-future-forecast mislabelling exists.
22. Cached data show freshness.
23. Provider attribution is present.
24. Basic forecast remains usable when radar fails.
25. Build and existing host tests pass.
26. Module-specific tests pass.

---

# 34. Migration from KeylessWeather

Treat KeylessWeather as a reference implementation only.

## Preserve

- compact information density;
- quantitative precipitation;
- NOW/TODAY/hourly/daily conceptual hierarchy;
- radar playback idea;
- prominent timestamps;
- restrained visual style;
- fast glanceability.

## Replace

- `MainActivity.java` monolith;
- legacy view construction;
- direct `HttpURLConnection` calls from activity;
- WebView/Leaflet radar;
- London fallback;
- provider-specific data handling in UI;
- emoji-based main weather iconography;
- dashboard-only interaction;
- implicit/missing provenance;
- fixed 5-day / 13-hour assumptions.

No attempt should be made to embed the old app wholesale.

---

# 35. Recommended implementation sequence

## Phase A — canonical foundations

1. register module;
2. define canonical method IDs;
3. define domain models;
4. define provenance/data-class model;
5. implement provider interfaces;
6. implement Open-Meteo adapter;
7. implement repository/cache;
8. implement location handling.

## Phase B — essential capabilities

9. `weather.conditions`;
10. `weather.forecast`;
11. `weather.precipitation`;
12. `weather.wind`;
13. `weather.sun`;
14. `weather.snapshot`.

Establish Preset/Protocol/ODK parity before proceeding too far into dashboard polish.

## Phase C — dashboard

15. hero;
16. condition strip;
17. precipitation timeline;
18. hourly forecast;
19. daily forecast;
20. meteogram shell;
21. source/freshness treatment.

## Phase D — radar

22. RainViewer adapter;
23. native map overlay;
24. frame scrubber/playback;
25. cache/degradation;
26. strict observation/nowcast semantics.

## Phase E — advanced meteorology

27. `weather.meteogram`;
28. `weather.atmosphere`;
29. pressure-level selection;
30. `weather.model_compare`;
31. ensemble rendering.

## Phase F — compliance and polish

32. all ODK forms;
33. launch-origin matrix;
34. state restoration;
35. tap-to-copy;
36. accessibility;
37. dark mode;
38. attribution;
39. build/test;
40. module review against current Master Book.

---

# 36. Initial API/data notes

At specification time:

- Open-Meteo exposes a general forecast API with multi-day hourly forecast data and a broad variable set.
- Open-Meteo also exposes historical forecast, historical/reanalysis, model-specific and ensemble APIs.
- Its ensemble API exposes individual members and longer-range horizons for supported models.
- RainViewer's Weather Maps API exposes tiled radar history in short time steps and should be treated as a radar-specific source.
- Provider terms, attribution and availability may change; implementation must verify current provider documentation rather than freezing assumptions from this document into the codebase.

The module must therefore centralize provider capability/attribution metadata and make provider substitution possible.

---

# 37. Definition of the finished Weather experience

Opening Weather should immediately communicate:

**where am I, what is happening now, is rain coming, what has the radar been doing, what happens over the next hours, and how certain/detailed is the meteorology?**

A normal user should be able to answer those questions without understanding model terminology.

A field researcher should be able to tap any important value, inspect its time/source and commit it.

An advanced user should be able to open the meteogram or atmosphere capability and inspect the underlying meteorology.

An ODK form or MethodMesh Protocol should be able to call the same canonical machinery without depending on the dashboard.

That combination—**beautiful overview + honest meteorological provenance + independently composable capabilities**—is the defining requirement of the module.
