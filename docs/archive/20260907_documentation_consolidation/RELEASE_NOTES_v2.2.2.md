# MethodMesh v2.2.2

MethodMesh v2.2.2 promotes Plus Code capture to production. This release hardens the offline-calculable Open Location Code workflow for field use: GPS starts automatically, the map/grid selector is usable from presets, native sharing returns only the selected Plus Code, and ODK/XLSForm integrations receive the main code plus background audit JSON.

## Highlights

### Plus Code capture promoted

`plus_code.capture` is now marked as a production capability.

- Preset setup now uses typed choices for grid precision, GPS averaging, map view, starting zoom, and online map tiles.
- Preset runs now open directly into the map selector and start GPS acquisition without an extra press.
- The map selector includes a dedicated **Refresh GPS** control for updating the fix while selecting the intended cell.
- Captured Plus Code results survive Android orientation changes instead of returning to the trigger screen.
- Native sharing sends only the selected full Plus Code.
- ODK/XLSForm examples return `plus_code` as the main result plus `methodmesh_full_json` for metadata and audit.

### Output contract tightened

- Plus Code core output now contains only the field-facing identifier: `plus_code`.
- Centroid, GPS accuracy, bounding box, basemap, timestamp, and other metadata remain available through the full JSON payload when requested.
- The example XLSForm no longer exposes legacy separate audit fields such as `plus_code_audit_json` or separate GPS latitude/longitude columns.

## Validation

Built and checked with:

```text
./gradlew testDebugUnitTest --tests com.example.methodmesh.modules.pluscodecapture.PlusCodeCaptureOdkFormContractTest --tests com.example.methodmesh.transport.OutputFormatterTest
./gradlew :app:assembleDebug
```

Result:

```text
BUILD SUCCESSFUL
```
