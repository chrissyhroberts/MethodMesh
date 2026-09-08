# Earth science utilities

**Module:** `earthscience`  
**Version:** `0.1.0`  
**Status:** Development  
**Canonical folder:** `app/src/main/java/com/example/methodmesh/modules/earthscience/`

This module adds small, reusable field and calculation primitives for geography, geology and Earth-science workflows. It is deliberately **not** a GIS application. Each method is designed to work natively, from a preset/protocol, or from ODK/XLSForm through the standard MethodMesh intent boundary.

The module contains no third-party source code. Mathematical implementations are original Kotlin implementations based on published/standard definitions. The geological-timescale lookup includes a small derived table from the International Commission on Stratigraphy (ICS) chart and therefore carries explicit ICS attribution.

## Public methods

| Method ID | Purpose | Runtime type |
|---|---|---|
| `geodesy.distance_bearing` | WGS84 ellipsoidal distance and initial/final bearings between two coordinates | Calculation |
| `geodesy.destination` | WGS84 destination coordinate from start point, bearing and distance | Calculation |
| `geodesy.wgs84_to_utm` | WGS84 decimal degrees to UTM | Calculation |
| `geodesy.utm_to_wgs84` | UTM to WGS84 decimal degrees | Calculation |
| `gnss.average_position` | Collect repeated Android location fixes and return an averaged position with spread diagnostics | Device service |
| `structural.plane` | Normalise strike/dip, dip direction and plane pole | Calculation |
| `structural.plane_capture` | Development phone-orientation capture of strike/dip | Device service |
| `structural.line` | Normalise trend/plunge | Calculation |
| `structural.plane_intersection` | Calculate the line of intersection of two planes | Calculation |
| `geotime.lookup` | Numerical age (Ma) to ICS eon/era/period/subdivision hierarchy | Calculation |
| `soil.texture_usda` | USDA soil-texture class from sand/silt/clay percentages | Calculation |
| `sediment.grain_size` | Diameter/phi conversion and Wentworth size classification | Calculation |

All methods are **Development** in v0.1.0. Pure calculation code has local smoke tests; Android build, ODK round-trip and physical sensor validation remain required before Production.

---

## 1. Geodesy

### `geodesy.distance_bearing`

Calculates an ellipsoidal inverse geodesic on WGS84 using the Vincenty inverse iteration. For the rare nearly-antipodal case where Vincenty does not converge, the method returns a clearly labelled spherical fallback rather than inventing an ellipsoidal result.

Inputs:

- `latitude_1` — decimal degrees, -90 to 90;
- `longitude_1` — decimal degrees, -180 to 180;
- `latitude_2`;
- `longitude_2`.

Core outputs:

- `geodesy_distance_result` — compact native result;
- `geodesy_distance_m`;
- `geodesy_initial_bearing_deg`;
- `geodesy_final_bearing_deg`;
- `geodesy_distance_algorithm`.

Audit/error outputs:

- `geodesy_distance_audit_json`;
- `geodesy_distance_status`;
- `geodesy_distance_error`.

Example:

```text
com.example.methodmesh.EXECUTE_METHOD(
  method_id='geodesy.distance_bearing',
  input_latitude_1=${lat_a},
  input_longitude_1=${lon_a},
  input_latitude_2=${lat_b},
  input_longitude_2=${lon_b},
  input_payload_mode='FULL',
  methodmesh_return_namespace='distance',
  return_mode='flat'
)
```

### `geodesy.destination`

Inputs: `latitude`, `longitude`, `bearing_deg`, `distance_m`.

Outputs:

- `geodesy_destination_result`;
- `geodesy_destination_latitude`;
- `geodesy_destination_longitude`;
- `geodesy_destination_final_bearing_deg`;
- status/error/audit fields.

### `geodesy.wgs84_to_utm`

Converts WGS84 latitude/longitude to UTM using standard zone selection, including the Norway and Svalbard zone exceptions.

Inputs: `latitude`, `longitude`.

Outputs:

- `geodesy_utm_result`;
- `geodesy_utm_zone`;
- `geodesy_utm_hemisphere`;
- `geodesy_utm_easting_m`;
- `geodesy_utm_northing_m`;
- `geodesy_utm_datum=WGS84`;
- status/error/audit fields.

v0.1 supports ordinary UTM between 80°S and 84°N. UPS/polar coordinates, MGRS and arbitrary EPSG transforms are roadmap items rather than being silently approximated.

### `geodesy.utm_to_wgs84`

Inputs: `zone`, `hemisphere` (`N` or `S`), `easting_m`, `northing_m`.

Outputs:

- `geodesy_wgs84_result`;
- `geodesy_wgs84_latitude`;
- `geodesy_wgs84_longitude`;
- `geodesy_wgs84_datum=WGS84`;
- status/error/audit fields.

**Offline:** all geodesy methods are fully offline.

---

## 2. Averaged GNSS position

### `gnss.average_position`

The normal Android location workflow can return whichever fix happens to be current. This capability instead collects repeated fixes and reports both an averaged position and the spread of the accepted fixes.

Settings:

- `target_fix_count` — accepted fixes required, 2–500; default 20;
- `max_accuracy_m` — reject fixes whose Android-reported horizontal accuracy radius exceeds this value; default 10 m;
- `weighting` — `inverse_variance` or `equal`.

Core outputs:

- `gnss_average_result`;
- `gnss_average_latitude`;
- `gnss_average_longitude`;
- `gnss_average_altitude_m` when available;
- `gnss_average_accepted_fix_count`;
- `gnss_average_rejected_fix_count`;
- `gnss_average_mean_reported_accuracy_m`;
- `gnss_average_rms_spread_m`;
- `gnss_average_max_spread_m`;
- `gnss_average_weighting`.

Audit/error outputs:

- `gnss_average_captured_time_iso`;
- `gnss_average_audit_json`;
- `gnss_average_status`;
- `gnss_average_error`.

### Important accuracy wording

Android's `Location.accuracy` is an operating-system-reported horizontal accuracy radius. It is **not assumed to be a statistical standard deviation**. The optional `inverse_variance` mode uses `1 / accuracy²` only as a pragmatic quality weighting and records `accuracy_weighting_is_heuristic=true` in audit metadata. Scientific users should inspect the independent `rms_spread_m`, `max_spread_m`, fix count and rejection count.

Coordinates are averaged as unit vectors on the sphere so longitude wrap-around is handled correctly. Dispersion is then measured geodesically from the resulting mean position.

### Native workflow

1. Open **Average GNSS position**.
2. Choose accepted fix count, maximum reported uncertainty and weighting.
3. Grant location permission when required.
4. Press **Start collection** (intent/preset runs can start collection immediately).
5. Poor fixes are rejected and counted.
6. Collection finishes automatically at the target count, or the operator can finish early after at least two accepted fixes.
7. Share/return the compact averaged coordinate; detailed diagnostics remain structured outputs/audit metadata.

### ODK workflow

An ODK intent opens the live capture screen. ODK supplies the collection settings and MethodMesh returns after the target number of accepted fixes is reached.

```text
com.example.methodmesh.EXECUTE_METHOD(
  method_id='gnss.average_position',
  input_target_fix_count='20',
  input_max_accuracy_m='10',
  input_weighting='inverse_variance',
  input_payload_mode='FULL',
  methodmesh_return_namespace='gnss',
  return_mode='flat'
)
```

### Permission/service behaviour

Requires Android fine or coarse location permission. The implementation uses the same Google Play Services `FusedLocationProviderClient` API family already used by MethodMesh GPS navigation/Plus Code workflows. MethodMesh itself makes no web request, but Android's fused provider may combine GNSS, Wi-Fi, cellular and device assistance according to OS/device configuration. v0.1 therefore describes this as **Android location averaging**, not survey-grade GNSS or GNSS-only capture.

---

## 3. Structural geology

### `structural.plane`

Pure calculation for a geological plane.

Inputs:

- `strike_deg`;
- `dip_deg`, 0–90;
- `convention` — `right_hand_rule` or `explicit_dip_direction`;
- `dip_direction_deg` when explicit mode is selected.

Outputs:

- `structural_plane_result`;
- `structural_strike_deg`;
- `structural_dip_deg`;
- `structural_dip_direction_deg`;
- `structural_pole_trend_deg`;
- `structural_pole_plunge_deg`;
- audit/status/error fields.

### `structural.plane_capture`

**Development / physical validation required.**

v0.1 uses MethodMesh's shared `PhoneSensorRepository`; it does not introduce a second sensor stack. The intended field geometry is:

1. place the phone flat against the planar surface;
2. align the phone's top edge along the strike line;
3. keep that top edge level;
4. the magnetic heading of the top edge is interpreted as strike;
5. roll magnitude is interpreted as dip;
6. roll sign selects the dip side;
7. pitch magnitude is retained as the along-strike levelling error.

The capture button is disabled when pitch exceeds `max_level_error_deg` (default 5°).

Outputs use the same structural plane fields plus:

- `structural_north_reference=magnetic`;
- `structural_sensor_source=PhoneSensorRepository`;
- `structural_magnetometer_accuracy`;
- `structural_along_strike_level_error_deg`;
- `structural_captured_time_iso`.

The audit payload preserves raw heading, pitch, roll, the configured level tolerance and the capture-geometry statement.

**Do not promote this method to Production until the sign convention and mounting geometry have been physically checked against a real geological compass on known planes across multiple Android devices.** Nearby steel, magnetic cases and electronics can materially affect heading.

### `structural.line`

Inputs `trend_deg` and `plunge_deg`. Negative plunge is normalised to the equivalent downward-plunging line by reversing trend 180°.

Outputs: `structural_line_result`, `structural_trend_deg`, `structural_plunge_deg`, status/error/audit.

### `structural.plane_intersection`

Inputs: `strike_1_deg`, `dip_1_deg`, `strike_2_deg`, `dip_2_deg`, interpreted as right-hand-rule planes.

Returns `structural_intersection_trend_deg` and `structural_intersection_plunge_deg`. Parallel/nearly parallel planes fail explicitly rather than returning an unstable line.

**Offline:** structural methods are fully offline.

---

## 4. Geological time

### `geotime.lookup`

Input: `age_ma` from 0 to 4600 Ma.

Outputs:

- `geotime_result`;
- `geotime_age_ma`;
- `geotime_eon`;
- `geotime_era`;
- `geotime_period`;
- `geotime_subdivision` — rank-neutral subdivision (for example epoch/series or Carboniferous subsystem/subperiod);
- `geotime_source=International Commission on Stratigraphy`;
- `geotime_source_version=2026/06`;
- audit/status/error fields.

v0.1 bundles a compact hierarchy sufficient for the Phanerozoic eon/era/period/subdivision lookup plus broad pre-Phanerozoic eons. It is not a complete stage/age database. The bundled boundaries are derived from the official **ICS International Chronostratigraphic Chart 2026/06**.

Attribution/source:

- International Commission on Stratigraphy, International Chronostratigraphic Chart, version 2026/06.
- https://stratigraphy.org/supplementary
- authoritative chart data: https://github.com/i-c-stratigraphy/chart
- chart data copyright © International Commission on Stratigraphy, 2026; CC BY 4.0.

The source version is returned with every successful lookup so a future chart update does not silently alter the meaning of stored results.

**Offline:** fully offline; no runtime call to ICS.

---

## 5. Earth materials

### `soil.texture_usda`

Inputs:

- `sand_pct`;
- `silt_pct`;
- `clay_pct`;
- `sum_tolerance_pct`, default 1%.

The three fractions must total 100% within the configured tolerance. The capability does not silently repair materially inconsistent compositions.

Outputs:

- `soil_texture_result`;
- `soil_texture_class`;
- supplied percentages and total;
- `soil_texture_source=USDA-NRCS textural classes`;
- audit/status/error fields.

Reference:

- USDA Natural Resources Conservation Service Soil Texture Calculator: https://www.nrcs.usda.gov/resources/education-and-teaching-materials/soil-texture-calculator

### `sediment.grain_size`

Inputs:

- `input_mode` — `diameter_mm` or `phi`;
- `diameter_mm` when diameter mode is used;
- `phi` when phi mode is used.

Uses `phi = -log2(diameter_mm)` and returns:

- `sediment_grain_result`;
- `sediment_grain_diameter_mm`;
- `sediment_grain_phi`;
- `sediment_grain_wentworth_class`;
- `sediment_grain_broad_class` (`Gravel`, `Sand`, `Silt`, `Clay`);
- `sediment_grain_source=Wentworth grain-size scale`;
- audit/status/error fields.

Reference:

- USGS Wentworth nomenclature discussion: https://pubs.usgs.gov/of/2003/of03-001/htmldocs/nomenclature.htm

**Offline:** earth-material classification is fully offline.

---

## Presets and protocols

All user-configurable inputs are declared by `EarthScienceModule.capabilitySettings()`. Native preset runs therefore use the shared MethodMesh fixed/runtime-input contract; fixed settings are hidden by the module screens via `settingShouldBeShown(...)`.

Pure calculation methods are particularly useful as protocol steps because their ordinary input names can be supplied from previous output fields. Examples:

- averaged GNSS latitude/longitude → later geodesic calculation;
- captured structural plane → later reporting/export step;
- sample grain diameter → sediment classification;
- laboratory age estimate → geological-time lookup.

No method imports another capability's private implementation.

---

## ODK/XLSForm example

`docs/example_odk_EarthScience.xlsx` demonstrates all public methods. Each intent call is made through an XLSForm group and requests `input_payload_mode='FULL'`. Separate `methodmesh_return_namespace` values keep similarly named outputs from different methods distinct.

For normal forms, keep the compact result and whichever structured fields are analytically useful. Retain `methodmesh_full_json` (namespaced in the example) when an auditable MethodMesh execution envelope is required.

---

## Validation completed in this prototype

The pure Kotlin core has been compiled independently with `kotlinc` and a smoke suite covering:

- London → Paris WGS84 inverse geodesic range;
- inverse/direct geodesic consistency;
- London WGS84 → UTM zone 30 and UTM → WGS84 round trip;
- plane pole geometry;
- two vertical planes producing a vertical intersection;
- a representative USDA loam classification;
- 1 mm → very coarse sand and phi 0;
- 152 Ma → Phanerozoic / Mesozoic / Jurassic / Upper Jurassic;
- a three-fix GNSS average.

`docs/EarthScienceMathSmoke.kt` contains the executable smoke checks.

The MethodMesh-specific method layer was also syntax/type checked against lightweight stubs shaped to the current public `As100Method` contract. This does **not** substitute for a real Android/Gradle build.

---

## Production checklist / known limitations

Keep the whole module **Development** until:

1. `./gradlew :app:assembleDebug` passes in a complete current MethodMesh checkout.
2. Module discovery finds `EarthScienceModule` without any central registry edit.
3. Every pure calculation screen works from Dashboard.
4. Preset creation/runtime hides fixed settings and asks runtime inputs correctly.
5. `docs/example_odk_EarthScience.xlsx` is tested in ODK Collect for launch → return on all methods.
6. Main sharing exposes the compact `*_result` only.
7. Results survive device rotation.
8. GNSS permission denial/retry and unavailable-location states are tested.
9. GNSS capture is tested on several Android devices and compared with known reference positions.
10. Structural plane capture is physically validated against a geological compass across known strike/dip planes and several orientations/devices.
11. Magnetically contaminated environments/cases are tested and documented.
12. ICS lookup test cases are checked against the current official 2026/06 chart.
13. USDA class boundary test vectors are expanded beyond the smoke examples.
14. The repository-level `000_Roadmap.md` is updated using `docs/ROADMAP_NOTE.md`.

### Deliberately not in v0.1

- arbitrary EPSG/PROJ transforms;
- MGRS/UPS;
- geodesic polygon area/perimeter;
- stereonet plotting/density contours;
- true-north structural correction/declination;
- GPX track logging;
- external NMEA/RTK/NTRIP receivers;
- Macrostrat/DEM/earthquake web lookups;
- geological/mineral image classification.

These are better follow-ons once the primitive package has passed Android and field validation.

## Canonical delivery

Copy the complete folder:

```text
app/src/main/java/com/example/methodmesh/modules/earthscience/
```

The module requires no central registration edit. Its only non-code artifact is the nested ODK workbook in `docs/`, as required by the MethodMesh capability contract.
