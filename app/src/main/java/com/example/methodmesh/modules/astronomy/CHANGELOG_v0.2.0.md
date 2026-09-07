# Astronomy v0.2.0 changes

- `astronomy.conditions`: native use now defaults to GPS + Open-Meteo weather and air-quality retrieval; piped data wins; manual fields are fallback only.
- `astronomy.dew_risk`: accepts AHT20 `sensor.read` fields (`temperature_c`, `relative_humidity_pct`) directly; otherwise uses piped weather, GPS + Open-Meteo, then manual fallback.
- `astronomy.imaging_window`: accepts piped `plus_code`, explicit coordinates, or GPS; automatically fetches the hourly astronomy forecast unless `forecast_json` is already piped.
- Added `AstronomyApiDefinitions.kt`: astronomy-specific Open-Meteo declarations are auto-installed in the existing MethodMesh API registry and executed via `api.get`.
- Added explicit module dependencies on `apiget`, `pluscodecapture`, and `sensorread`.
- `astronomy.sky_test`: flat-phone instructions now specify screen-down / rear-camera-up timed sampling.
- `astronomy.focus`: renamed/reframed as an optical-train focus assistant for a phone attached to an eyepiece/adapter; it is not a flat-phone sky test.
- `astronomy.exposure_limit`: UI now explicitly says it applies to an untracked camera/lens on a fixed tripod.
- Core calculation methods accept legacy `humidity_pct` as an alias, but the canonical sensor-compatible field is now `relative_humidity_pct`.
