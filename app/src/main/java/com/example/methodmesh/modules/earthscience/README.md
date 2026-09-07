# MethodMesh Earth Science utilities v0.1.1

Drop-in Development module for MethodMesh.

## Install

Copy this complete `earthscience/` directory to:

```text
app/src/main/java/com/example/methodmesh/modules/earthscience/
```

Do **not** add manual registry entries. Current MethodMesh module discovery should find `EarthScienceModule.kt` automatically.

Then run:

```bash
./gradlew :app:assembleDebug
```

See `docs/README_EarthScience.md` for the capability contract, inputs/outputs, ODK examples, validation status and source attribution.

## Included methods

- `geodesy.distance_bearing`
- `geodesy.destination`
- `geodesy.wgs84_to_utm`
- `geodesy.utm_to_wgs84`
- `gnss.average_position`
- `structural.plane`
- `structural.plane_capture`
- `structural.line`
- `structural.plane_intersection`
- `geotime.lookup`
- `soil.texture_usda`
- `sediment.grain_size`

Status: **Development**. The pure calculation core is smoke-tested; Android Gradle/ODK/physical sensor validation remains required.


## v0.1.1 usability revision

- calculation failures are now shown instead of appearing as a blank result;
- UTM → WGS84 accepts the complete output line from WGS84 → UTM;
- geological time has a live age slider and segmented Earth-history display;
- GNSS averaging shows a live current mean and convergence/spread graphic;
- structural plane capture now explains strike/dip and phone placement in plain language.
