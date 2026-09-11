# Weather v0.1.0

Initial MethodMesh Weather module implementation.

- Added canonical `weather.dashboard`, `weather.conditions`, `weather.forecast`, `weather.precipitation`, `weather.radar`, `weather.meteogram`, `weather.wind`, `weather.atmosphere`, `weather.sun`, `weather.snapshot` and `weather.model_compare` capabilities.
- Added purpose-built Weather dashboard with current conditions, precipitation, radar timeline, hourly trend, daily forecast and meteogram surfaces.
- Added native MapLibre RainViewer radar map and frame scrubber.
- Added Open-Meteo forecast, historical/reanalysis, historical-forecast, pressure-level and ensemble provider definitions through MethodMesh generic online-data execution.
- Added MethodMesh 5 km rounded third-party location disclosure with requested/query/grid provenance separation.
- Added headless/direct execution path for ODK, Presets and Protocols with supplied coordinates.
- Added live working-result -> Commit behaviour and tap-to-copy native values.
- Added eleven canonical single-invocation XLSForm showcase workbooks and structural audit.
- Explicitly retained reanalysis/model/radar/nowcast source-class distinctions; model precipitation is never labelled radar.
- Final hardening invalidates stale working results when coordinates change, makes dashboard/tool headers narrow-screen-safe, and consolidates dashboard meteorological retrieval before canonical panel projection.
- Final compile-oriented host-interface type-check passed after correcting callback, public-type, nullable-description and MapLibre import integration defects; real Gradle/Android build remains a receiving-repository gate.
