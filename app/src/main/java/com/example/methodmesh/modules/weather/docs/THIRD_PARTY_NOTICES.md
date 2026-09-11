# Weather third-party notices

Provider terms are external dependencies and can change independently of MethodMesh. Check them again before a public/commercial release. The MethodMesh MIT licence does not override provider/data licences or terms.

## Open-Meteo

Used for current/modelled weather, hourly/daily forecast, historical weather/reanalysis, historical-forecast reconstruction, GFS pressure-level data and ensemble data.

- Site: https://open-meteo.com/
- Terms: https://open-meteo.com/en/terms
- Licence: https://open-meteo.com/en/license
- Required module attribution: **Weather data by Open-Meteo**
- API data licence documented by Open-Meteo: CC BY 4.0.
- Open-Meteo's public Free API terms currently restrict free use to non-commercial use and publish request-volume limits. Commercial/production use must be checked against the provider's current plan/terms.

Open-Meteo requests that contain location use MethodMesh's declared rounded-location policy (approximately 5 km by default). Exact requested coordinates remain local to the MethodMesh Weather result; provider-grid coordinates returned by the product are separately recorded where available.

## RainViewer

Used for weather-radar timeline metadata and raster tiles.

- Site: https://www.rainviewer.com/
- API: https://www.rainviewer.com/api.html
- Required module attribution: **Radar data by RainViewer**

RainViewer currently describes its public Weather Maps API as intended for personal, educational and small-scale community use, without an SLA, and requires visible attribution. High-volume/commercial use requires checking current provider terms.

MethodMesh treats provider `past` frames as `RADAR_OBSERVATION`. A provider `nowcast` array is used only when it actually exists and contains frames. Model forecast precipitation is not inserted into the radar timeline and is never labelled radar.

## OpenFreeMap / OpenStreetMap

The native radar map uses OpenFreeMap as its default basemap style.

- Site: https://openfreemap.org/
- OpenFreeMap requires attribution and documents MapLibre as an attribution-aware client.
- Map data are derived from OpenStreetMap and retain the applicable OpenStreetMap/OpenMapTiles attribution requirements.

The MapLibre map leaves attribution enabled.
