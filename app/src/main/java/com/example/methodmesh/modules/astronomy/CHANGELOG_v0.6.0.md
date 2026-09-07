# Astronomy v0.6.0

## Light pollution becomes a live map capability

- `astronomy.light_pollution` now downloads public NASA GIBS VIIRS Day/Night Band nighttime-radiance imagery for an arbitrary site and radius.
- The downloaded WMS image is stored as an astronomy-owned georeferenced regional raster cache, with dataset/date/bounds/source metadata.
- A MapLibre map surface follows the same local-map pattern already used by `plus_code.capture`: OpenFreeMap street map, Esri satellite map, or a dark blank background.
- The NASA raster is re-coloured locally into a semi-transparent heat layer and georeferenced over the basemap with MapLibre `ImageSource` + `RasterLayer`.
- The selected site is sampled from the same cached raster. GIBS' published greyscale colour map is inverted approximately to report nighttime radiance in `nW/(cm² sr)`.
- The result is explicitly labelled a **night-light / light-pollution proxy**. It is not reported as Bortle class, SQM, or measured zenith sky brightness.
- Download requests walk backwards across recent dates until NASA supplies usable data at the selected site.
- Overlapping raster caches are tried newest-first; a transparent/no-data pixel no longer hides an older usable cache.
- Existing v0.5 point-grid JSON import remains available only as a backwards-compatible fallback.

## Dashboard

- The dashboard **Site darkness** card reads the same astronomy-owned NASA raster cache.
- If the selected dashboard site has cached coverage, the card shows a compact transparent heatmap plus the sampled VIIRS nighttime-radiance proxy.
- If no raster covers the site, **Download 50 km heatmap** fetches and caches one directly from the dashboard.
- GPS, Plus Code, and explicit lat/lon dashboard sites all use the same cache lookup.

## Boundaries

- No `HomeScreen`, Settings, widget, or other MethodMesh core-UI files are changed.
- The older Falchi World Atlas preprocessing helper remains documented for legacy imported sky-brightness grids, but it is no longer the primary native workflow.
