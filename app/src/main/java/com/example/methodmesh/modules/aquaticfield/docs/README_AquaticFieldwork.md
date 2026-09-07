# Aquatic Fieldwork — MethodMesh module

Module ID: `aquaticfield`

This module provides an offline-first collection of oceanography, limnology and aquatic fieldwork capabilities.  It is deliberately structured as atomic MethodMesh methods plus a local persistent station dashboard, rather than a monolithic field application.

## Public method IDs

| Method ID | Purpose |
|---|---|
| `aquatic.salinity.convert` | Conductivity unit conversion, PSS-78 Practical Salinity, specific conductance and empirical TDS |
| `aquatic.pressure_depth.convert` | Pressure/depth conversion plus explicit sensor/wire corrections |
| `aquatic.secchi.measure` | Guided Secchi observation with optional Carlson TSI(SD) |
| `aquatic.station.visit` | Persistent station + visit record |
| `aquatic.ctd.cast` | CTD / multiparameter-sonde cast provenance |
| `aquatic.sample_id.generate` | Configurable sample identifier |
| `aquatic.sample.record` | Discrete water sample provenance |
| `aquatic.rosette.fire` | Rosette / water-sampler firing event |
| `aquatic.profile.summarise` | Processed vertical-profile summary |
| `aquatic.depth_plan.generate` | Sampling-depth planning |
| `aquatic.instrument.check` | Calibration / verification / service record |
| `aquatic.field_qc.run` | Non-destructive completeness and plausibility QC |
| `aquatic.transect.record` | Transect record with geodesic start/end distance |
| `aquatic.mooring.record` | Mooring deployment / service / recovery |
| `aquatic.sediment.record` | Sediment core / grab record |
| `aquatic.station.dashboard` | Persistent station snapshot |

All methods are initially classified **Development**.

## Scientific boundary

The implementation is conservative by design.

### Practical Salinity

`aquatic.salinity.convert` implements the PSS-78 Practical Salinity polynomial used by TEOS-10/GSW for the Practical Salinity variable.  Conductivity is normalised internally to mS/cm.

Low-salinity results below SP=2 are flagged for validation.

### Absolute Salinity

The module does **not** relabel Reference Salinity as Absolute Salinity.

True TEOS-10 Absolute Salinity normally requires the SAAR atlas/anomaly data as well as SP, pressure and location.  Until a validated offline SAAR implementation/data pack is bundled, requesting `mode=absolute_salinity` returns a clear failure.

This is intentional.

### Pressure / depth

The first implementation uses the UNESCO 1983 / Saunders pressure-depth polynomial and reports that algorithm explicitly.  It does not claim that result is `gsw_z_from_p`.

### CTD processing

The module records CTD/sonde cast provenance and can summarise **processed** profiles.  It deliberately does not replace instrument-specific raw CTD processing workflows.

### Profile interpretation

Thermocline, halocline, pycnocline and oxycline values are gradient-based candidates and the criterion is included in metadata. Mixed-layer depth is threshold-based and configurable.

Schmidt stability and whole-lake heat content remain blank until an explicit hypsographic/geometry integration is implemented. The module does not substitute a single-profile shortcut and call it a whole-lake metric.

## Local field repository

Native aquatic records may be stored in a module-owned `SharedPreferences` repository.

This local durable state is separate from the MethodMesh execution graph:

- recording a sample can update the active station counters;
- dashboard Refresh reads that local state;
- Refresh does not intentionally create repeated graph results;
- `Finish station` returns the current dashboard snapshot as a MethodMesh execution result.

External/automatic invocation remains a normal single-shot result.

## Station / visit distinction

A **station** is a persistent geographic sampling location.

A **visit** is one occupation of that station on a particular date/time.

Do not collapse these into one identifier in project data design.

## ODK / Android intent pattern

Each method accepts settings either as its ordinary name or with `input_` prefix.

Example concept:

```text
action = aquatic.secchi.measure
input_disappearance_depth_m = 4.8
input_reappearance_depth_m = 5.2
input_water_depth_m = 31
input_calculate_carlson_tsi = true
```

Useful returned fields include:

```text
aquatic_result
aquatic_status
aquatic_algorithm
aquatic_warnings_json
aquatic_metadata_json
aquatic_error
```

plus method-specific fields such as `aquatic_secchi_depth_m`.

See `example_odk_aquaticfield.xlsx`.

## Profile input

`aquatic.profile.summarise` currently accepts pasted/intent-supplied simple processed CSV, TSV or semicolon-delimited data with a header row.

Example:

```csv
depth,temperature,salinity,oxygen,density
0,24.8,0.12,8.4,997.2
2,24.6,0.12,8.2,997.3
5,23.9,0.13,7.9,997.6
8,20.2,0.14,6.1,998.5
10,17.3,0.14,4.8,999.1
15,16.1,0.15,3.7,999.4
```

The parser intentionally does not guess complex quoted CSV dialects. A clean processed table should be supplied.

## Sample ID templates

Available tokens:

```text
{project}
{campaign}
{station}
{visit}
{cast}
{depth}
{bottle}
{type}
{replicate}
{sequence}
{date}
```

Default:

```text
{project}-{station}-{cast}-{depth}M-{type}-{replicate}
```

The current MethodMesh `barcode` module is a scanner rather than a code generator. This aquatic module therefore returns the sample ID cleanly but does not duplicate barcode/QR encoding. If a generic code-generation capability is added later, the sample ID should pipe into that public capability.

## Build / review checklist

Before Production promotion:

- test all native methods;
- verify runtime/preset field hiding;
- test external intent/ODK return fields;
- test protocol close-out;
- test `intent_test`;
- test orientation state;
- validate PSS-78 against authoritative GSW reference vectors;
- validate pressure-depth reference vectors;
- add/validate the low-salinity Hill correction if required for intended use;
- add a validated offline TEOS-10 SAAR implementation before enabling Absolute Salinity;
- add file-picker support for profile/CTD URIs if required;
- validate dashboard Finish/Refresh graph behaviour;
- verify all field QC rules;
- run `./gradlew :app:assembleDebug`.

## References

Implementation documentation should cite the authoritative standards used during scientific validation:

- TEOS-10 / Gibbs SeaWater: https://www.teos-10.org/
- IOC, SCOR and IAPSO (2010), TEOS-10 Manual
- UNESCO (1983), Algorithms for computation of fundamental properties of seawater
- Carlson trophic-state index literature for `TSI(SD)`

