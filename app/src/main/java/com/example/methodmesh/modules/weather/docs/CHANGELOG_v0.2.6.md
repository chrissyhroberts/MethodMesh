# Weather v0.2.6 — radar camera and frame stabilisation

- Initialise MapLibre with the Weather target/zoom before first render using `MapLibreMapOptions`.
- Preserve the basemap when radar frames change; replace only the RainViewer raster layer/source.
- Preserve operator pan/zoom across radar playback and ordinary recomposition.
- Recenter only when requested coordinates or configured zoom genuinely change.
- Ignore stale asynchronous radar-frame callbacks.
- No canonical Weather contract or XLSForm changes.
