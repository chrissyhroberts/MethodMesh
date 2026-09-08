# Astronomy v0.5.0

## Added

- Explicit dashboard site selector: GPS / Plus Code / latitude-longitude plus **◎ GPS** recenter.
- `astronomy.light_pollution` atomic capability and astronomy-owned regional cache/import format.
- Dashboard cached site-darkness card.
- `docs/build_light_pollution_region.py` for arbitrary-radius cache extraction from a World Atlas GeoTIFF.
- Polar-alignment AR sighting mode using rear-camera optical-axis azimuth/elevation.
- Image-scale sliders paired with exact numeric fields.
- Focus rear/front camera selection and rolling-median FWHM.
- Resumable astronomy sessions with Start/Pause/Resume/Stop/Discard.

## Changed

- Dashboard location source is strict: manual/Plus Code selection is not silently replaced by GPS.
- Dashboard best-window display explicitly continues to reuse `As100ImagingWindowMethod` calculation logic.
- Sky test is reframed as a relative point-source test against a saved clear-night reference; no absolute seeing/SQM claim.
- `astronomy.focus` is titled **Telescope focus assistant** in the native UI.

## Architecture

- No `HomeScreen`/Settings/core resource-registry changes. All v0.5 astronomy-specific persistent data remains inside the astronomy module.
- Public method IDs remain stable; one new method is added: `astronomy.light_pollution`.
