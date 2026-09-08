# Drop-in instructions — Astronomy v0.6.0

Copy this whole `astronomy/` directory to:

`app/src/main/java/com/example/methodmesh/modules/astronomy/`

The generated MethodMesh module index should discover `AstronomyModule` automatically.

## Important boundary

v0.6.0 is deliberately self-contained. **Do not replace or edit `HomeScreen.kt` for this release.** Light-pollution cache files, clear-night calibration and resumable active-session state are owned by the astronomy module. A future shared offline-resource registry can migrate those resources later without changing the public astronomy method IDs.

## Existing module dependencies

- `apiget` — Open-Meteo weather, air quality, hourly forecast and GFS upper-air calls use the shared API machinery.
- `pluscodecapture` — Plus Code decoding/location interoperability.

`sensorread` is not an astronomy dependency. Dew-risk can accept canonical `temperature_c` / `relative_humidity_pct` fields only when they genuinely arrive from an existing multi-step workflow; standalone use remains GPS + Open-Meteo then manual fallback.

## Light-pollution heatmaps

Native `astronomy.light_pollution` now downloads a recent public NASA GIBS VIIRS nighttime-radiance image for the selected GPS / Plus Code / lat-lon site and radius. The PNG plus geographic metadata are cached entirely inside the astronomy module. The capability renders the cache as a transparent MapLibre heatmap and the dashboard reads the same cache.

No separate data-file installation is required for the primary workflow. Internet access is needed only when downloading/refreshing a region; already-cached heatmaps remain available afterwards (the street/satellite basemap itself may still require online tiles unless the blank background is selected).

The old Falchi/World Atlas JSON workflow remains available only for backwards compatibility:

```bash
python docs/build_light_pollution_region.py World_Atlas_2015.tif site.json \
  --lat 53.4084 --lon -2.1491 --radius-km 75 --name "Home +75 km"
```

## Validation

Run the normal Android build/architecture tests after dropping in the folder. Camera, GPS, AR orientation, session persistence and regional-cache behaviour still require physical-device validation; see `docs/VALIDATION.md`.
