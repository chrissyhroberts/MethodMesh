## MethodMesh v2.2.0

This release promotes the new Plus Code capture capability to production.

### Added

- `plus_code.capture` production capability for field location capture using Google Plus Codes / Open Location Code.
- Local/offline Plus Code calculation with no Google API, what3words service, geocoder, or lookup database dependency.
- GPS averaging, selected-cell centroid, selected-cell bounds, GPS accuracy, timestamp, and audit JSON outputs.
- Full-screen map selector with:
  - street map tiles via OpenFreeMap;
  - satellite imagery via Esri World Imagery;
  - grid-only fallback;
  - fine and jump D-pads;
  - standalone centre-on-GPS control;
  - projection-aligned grid, tap selection, and GPS marker.
- Native preset flow that starts GPS acquisition and opens the selector directly, hiding setup controls during capture.
- Example ODK/XLSForm workbook for Plus Code capture.

### Changed

- Online street/satellite basemaps are now the default for Plus Code capture until offline tile-pack download is implemented.
- Plus Code preset settings now expose only real configuration: map mode, code length, grid width, and GPS averaging duration.
- Satellite mode falls back to street tiles at the tightest selector view where imagery may be unavailable.

### Verification

- Built successfully with `./gradlew :app:assembleDebug`.
