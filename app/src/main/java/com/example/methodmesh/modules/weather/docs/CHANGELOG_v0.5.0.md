# MethodMesh Weather v0.5.0

## MethodMesh v1.29 UI/lifecycle refresh

This release updates Weather against `METHODMESH_MASTER_BOOK_v1.29` while preserving the canonical Weather capability architecture.

### Human-first dashboard

The dashboard has been redesigned as an operational weather surface rather than a concatenation of capability outputs. The default hierarchy is now:

1. **Now** — condition, temperature, feels-like, today's high/low, wind and sunset.
2. **Quick facts** — next rain, humidity/dew point, wind/gust, pressure, daylight/UV.
3. **Next 24 hours** — horizontally scannable hourly forecast cards.
4. **This week** — concise daily outlook.
5. **Radar** — compact operational radar surface.
6. **Rain detail** — progressive disclosure for quantitative precipitation.
7. **Meteorology & research** — progressive disclosure for meteogram, upper-atmosphere data, ensemble/model comparison and research snapshot.

This follows the v1.29 requirement that dashboards remain compact operational control centres rather than long scrolling substitutes for capability pages.

### Capability screens

All individual Weather capabilities now follow the v1.29 instrument hierarchy more closely:

- compact identity/mode;
- live working result before configuration chrome;
- Commit adjacent to the working result;
- frozen committed result and post-Commit actions on the same screen;
- settings/location collapsed once a useful result exists and available through **Adjust location & options**;
- preset authoring retained through the shared MethodMesh preset repository;
- fixed/runtime preset visibility rules retained;
- external/ODK launches retain host-owned scrolling and automatic-return behaviour.

### Dashboard execution

The dashboard again uses a coherent shared forecast/radar capture for the ordinary forecast panels instead of independently refetching the same provider payload for each panel. Atmosphere and ensemble comparison remain separate provider calls and are fetched concurrently.

### Radar GIF attachment

`weather.radar` extends its canonical output contract with:

- `weather_radar_gif_uri`
- `weather_radar_gif_sha256`

For external/ODK automatic-return runs, Weather attempts to render the recent RainViewer timeline as a transient animated GIF exposed through the app FileProvider. The shared MethodMesh transport remains responsible for URI grants/attachment import at the caller boundary.

The canonical radar XLSForm uses an attachment-compatible `image` field for `weather_radar_gif_uri`, retains the stable `form_id`, and uses zoom 5 by default.

## Contract compatibility

All eleven existing Weather method IDs and all pre-v0.5.0 inputs/outputs are preserved. The only canonical contract extension is the two radar GIF output fields above.
