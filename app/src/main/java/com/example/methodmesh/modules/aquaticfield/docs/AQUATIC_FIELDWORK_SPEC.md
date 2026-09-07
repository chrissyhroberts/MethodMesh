# MethodMesh Aquatic Fieldwork Capability Package

**Working module name:** `aquaticfield`  
**Canonical module path:** `app/src/main/java/com/example/methodmesh/modules/aquaticfield/`  
**Initial status:** Development  
**Scope:** Oceanography, limnology and general aquatic field sampling  
**Design goal:** A compact, offline-first electronic aquatic field notebook built from reusable MethodMesh primitives, with a persistent station dashboard layered over the same engines and repositories.

---

## 1. Summary

This package expands the original ideas of salinity conversion, Secchi depth, sampling-station logs, CTD/sample records and depth correction into a coherent MethodMesh domain module.

It should not be built as one monolithic “oceanography app”. The module should expose a family of atomic methods that can be used:

- directly in MethodMesh;
- from saved presets;
- as protocol or schedule steps;
- from ODK/XLSForm through Android intents;
- from the module’s own persistent station dashboard.

The package should cover:

1. salinity, conductivity and water-property conversions;
2. pressure/depth and instrument-geometry corrections;
3. guided Secchi observations;
4. station and visit records;
5. CTD / multiparameter-sonde cast records;
6. discrete water-sample records;
7. sample-ID generation;
8. rosette / water-sampler firing logs;
9. processed vertical-profile summaries;
10. thermocline, halocline, oxycline and stratification metrics;
11. depth-plan generation;
12. instrument calibration / field-check records;
13. field completeness and plausibility QC;
14. transect records;
15. mooring deployment, recovery and service records;
16. sediment core / grab records;
17. a persistent station dashboard that composes all of the above.

The module should remain useful without network connectivity. Scientific calculations should be deterministic and versioned. No primary calculation should depend on a remote API.

---

## 2. MethodMesh architectural constraints

The implementation must follow the MethodMesh capability-writing rules.

### 2.1 Module ownership

All capability-specific files live under:

```text
app/src/main/java/com/example/methodmesh/modules/aquaticfield/
```

Suggested package structure:

```text
aquaticfield/
├── docs/
│   ├── README_AquaticFieldwork.md
│   ├── SCIENTIFIC_METHODS.md
│   ├── example_odk_aquaticfield.xlsx
│   └── example_profile.csv
│
├── AquaticFieldModule.kt
│
├── calculation/
│   ├── SalinityEngine.kt
│   ├── PressureDepthEngine.kt
│   ├── SecchiEngine.kt
│   ├── ProfileEngine.kt
│   ├── StratificationEngine.kt
│   ├── DepthPlanEngine.kt
│   └── FieldQcEngine.kt
│
├── model/
│   ├── AquaticModels.kt
│   ├── Units.kt
│   ├── StationModels.kt
│   ├── InstrumentModels.kt
│   └── SampleModels.kt
│
├── repository/
│   └── AquaticFieldRepository.kt
│
├── methods/
│   ├── SalinityMethod.kt
│   ├── PressureDepthMethod.kt
│   ├── SecchiMethod.kt
│   ├── StationVisitMethod.kt
│   ├── CtdCastMethod.kt
│   ├── SampleIdMethod.kt
│   ├── SampleRecordMethod.kt
│   ├── RosetteFireMethod.kt
│   ├── ProfileSummaryMethod.kt
│   ├── DepthPlanMethod.kt
│   ├── InstrumentCheckMethod.kt
│   ├── FieldQcMethod.kt
│   ├── TransectMethod.kt
│   ├── MooringMethod.kt
│   ├── SedimentMethod.kt
│   └── StationDashboardMethod.kt
│
└── screens/
    ├── ...
    └── StationDashboardCapabilityScreen.kt
```

The exact use of subfolders should follow the current repository conventions, but the whole package remains inside the one canonical module folder.

### 2.2 No shared-UI special cases

Do not add aquatic-specific logic to `HomeScreen`, the generic dashboard, generic result screens or protocol machinery.

The module owns its:

- method IDs;
- descriptors;
- settings;
- UI screens;
- scientific calculations;
- persistence;
- permissions;
- field validation;
- output formatting;
- audit metadata;
- documentation;
- example XLSForm.

MethodMesh shared code should remain capability-agnostic.

### 2.3 Main result first, audit second

Every method should expose a compact useful result plus structured audit metadata.

Examples:

```text
Secchi depth: 2.35 m
```

rather than a wall of JSON.

The same execution may additionally return:

```text
secchi_disappearance_depth_m
secchi_reappearance_depth_m
secchi_tsi_sd
secchi_flags
secchi_metadata_json
```

ODK and protocol callers can consume the individual fields or full audit JSON.

### 2.4 Development status

All methods begin as:

```text
category = Development
status = Development
```

Promotion to Production requires native, preset, ODK, protocol, orientation-state, error-state, documentation and build validation.

---

# 3. Shared scientific and field data model

The package should use shared domain objects rather than inventing separate representations in each screen.

## 3.1 Core entities

### Project / campaign

Optional parent context for a field programme.

Suggested fields:

- project ID;
- campaign / cruise ID;
- project name;
- lead organisation;
- default operator;
- default timezone;
- default sample-ID template;
- notes.

### Station

A persistent geographical sampling location.

Suggested fields:

- `station_id`;
- station name;
- latitude;
- longitude;
- optional Plus Code;
- waterbody;
- nominal water depth;
- station type;
- notes.

A station is not the same as a visit.

### Visit

One occupation of a station on a particular date/time.

Suggested fields:

- `visit_id`;
- parent `station_id`;
- arrival timestamp;
- departure timestamp;
- operator(s);
- platform / vessel;
- observed water depth;
- weather;
- wind;
- sea/lake state;
- tidal state if applicable;
- ice state if applicable;
- visit purpose;
- field notes;
- visit status: open / completed / abandoned.

### Instrument

Reusable local instrument identity.

Suggested fields:

- instrument ID;
- instrument class;
- manufacturer;
- model;
- serial number;
- owner/project;
- calibration date;
- calibration expiry / due date if defined;
- firmware/configuration identifier;
- notes.

### Cast

A profiling operation associated with a station visit.

### Sample

A discrete physical sample linked to station, visit and optionally cast/bottle.

### Rosette firing

A discrete bottle-firing event associated with a cast.

### Instrument check

A calibration, verification, cleaning or pre/post-deployment check.

### Transect

A start-to-end field operation containing waypoints, stations, observations and/or samples.

### Mooring event

A deployment, service or recovery event.

### Sediment event

A core or grab operation and resulting sample set.

---

# 4. Unit handling

The package should normalise scientific values internally while accepting common field units.

At minimum:

### Length / depth

- m;
- cm;
- mm.

### Pressure

- dbar;
- bar;
- kPa;
- Pa;
- optional psi for imported field records.

Internal oceanographic pressure should normally be dbar.

### Temperature

- °C.

### Conductivity

- S/m;
- mS/cm;
- µS/cm.

### Salinity

- Practical Salinity `SP`: dimensionless;
- Absolute Salinity `SA`: g/kg.

“PSU” may be accepted as a familiar input/display alias for Practical Salinity where useful, but the audit record should preserve the formal variable and unit convention.

### Dissolved oxygen

For imported processed profiles, support declared units rather than guessing:

- mg/L;
- µmol/L;
- µmol/kg;
- % saturation.

Conversions that require density, pressure, salinity or temperature must only be attempted when the required inputs exist.

### General rule

Never infer units silently.

Every imported numeric column used scientifically must have either:

- a declared unit;
- a strongly defined parser mapping;
- or an explicit “unit unknown” state that prevents unsafe conversion.

---

# 5. Capability catalogue

## 5.1 `aquatic.salinity.convert`

### Purpose

Convert conductivity/salinity variables and optionally derive standard seawater thermodynamic properties.

### Core modes

1. conductivity unit conversion;
2. conductivity + temperature + pressure → Practical Salinity;
3. Practical Salinity + pressure + longitude + latitude → Absolute Salinity;
4. in-situ temperature → Conservative Temperature where sufficient variables exist;
5. salinity/temperature/pressure → density and selected sigma values;
6. freshwater/brackish specific-conductance normalisation when the user supplies the appropriate temperature-compensation parameters;
7. conductivity → estimated TDS using a user-supplied empirical coefficient.

### Scientific implementation

Marine calculations should be implemented against TEOS-10 / Gibbs SeaWater (GSW) definitions and validated against official reference outputs.

Do not invent simplified “salinity = conductivity × constant” formulas for marine salinity.

For freshwater TDS estimation, the multiplier is empirical and waterbody/instrument dependent. The user must select or enter the coefficient, and the output must be explicitly labelled an estimate.

### Inputs

Depending on mode:

- conductivity value;
- conductivity unit;
- in-situ temperature;
- pressure;
- latitude;
- longitude;
- Practical Salinity;
- Absolute Salinity;
- TDS coefficient;
- temperature compensation coefficient if specific conductance is calculated.

### Main outputs

Suggested fields:

```text
aquatic_salinity_status
aquatic_practical_salinity
aquatic_absolute_salinity_g_kg
aquatic_conductivity_ms_cm
aquatic_specific_conductance_25
aquatic_conservative_temperature_c
aquatic_density_kg_m3
aquatic_sigma0_kg_m3
aquatic_estimated_tds_mg_l
aquatic_salinity_metadata_json
aquatic_salinity_error
```

Only fields supported by the supplied inputs should be populated.

### Native UX

The result card should emphasise the requested target variable, for example:

```text
Practical salinity
34.72
```

Optional detail:

```text
Absolute salinity: 34.89 g/kg
Density: 1026.3 kg/m³
```

### Audit priorities

Record:

- original values and units;
- normalised values;
- calculation mode;
- algorithm / implementation version;
- coordinates if used;
- pressure;
- warnings;
- empirical coefficients where used.

---

## 5.2 `aquatic.pressure_depth.convert`

### Purpose

Convert pressure and depth, correct sensor geometry, and provide explicitly approximate wire-out calculations.

### Modes

1. pressure → depth;
2. depth → expected pressure;
3. pressure-zero correction;
4. sensor vertical-offset correction;
5. bottle/intake depth from CTD pressure-sensor depth;
6. wire-out + cable angle → approximate vertical depth;
7. apply a user-defined mechanical correction factor.

### Scientific rules

Pressure-to-depth should use the TEOS-10/GSW pressure-height relationship where appropriate, including latitude rather than assuming exactly 1 dbar = 1 m.

Distinguish:

- sea pressure;
- absolute pressure;
- depth below surface;
- geometric height.

Never silently mix them.

For cable geometry, explicitly define the angle convention:

```text
angle_from_vertical
```

or

```text
angle_from_horizontal
```

and store the convention in metadata.

Wire-out calculations are estimates and should be labelled as such.

### Outputs

```text
aquatic_depth_status
aquatic_depth_m
aquatic_pressure_dbar
aquatic_depth_correction_m
aquatic_depth_is_estimated
aquatic_depth_metadata_json
aquatic_depth_error
```

---

## 5.3 `aquatic.secchi.measure`

### Purpose

Guide and document a repeatable Secchi-disk observation.

### Runtime workflow

Record:

- disk diameter / type;
- disappearance depth on lowering;
- reappearance depth on raising;
- whether disk reached bottom before disappearing;
- local water depth;
- observer;
- shaded / unshaded reading;
- sun/glare;
- cloud;
- wave / surface state;
- optional water colour;
- optional turbidity / unusual-condition note.

Calculate the reported Secchi depth from the configured method, with the default guided method using the mean of disappearance and reappearance depths when both are valid.

### Optional derived outputs

#### Carlson TSI(SD)

For freshwater lake use, allow an optional Carlson trophic-state index derived from Secchi depth.

It must be labelled as an index derived from transparency, not a direct nutrient or chlorophyll measurement.

#### Estimated euphotic depth

May be offered only as an optional empirical estimate.

Requirements:

- not enabled by default;
- user selects or supplies the multiplier/model;
- output visibly labelled `estimated`;
- audit record stores the coefficient/source;
- do not imply a universal Secchi:euphotic-depth relationship.

### Outputs

```text
aquatic_secchi_status
aquatic_secchi_depth_m
aquatic_secchi_disappearance_m
aquatic_secchi_reappearance_m
aquatic_secchi_tsi_sd
aquatic_secchi_estimated_euphotic_depth_m
aquatic_secchi_flags
aquatic_secchi_metadata_json
aquatic_secchi_error
```

### Useful QC flags

- disappearance < reappearance by an implausibly large amount;
- zero/negative depth;
- Secchi depth > recorded water depth;
- bottom reached before disappearance;
- only one valid directional observation;
- heavy glare / severe wave condition;
- missing disk specification.

---

## 5.4 `aquatic.station.visit`

### Purpose

Open or record one field occupation of a station.

### Station vs visit

The module must retain the distinction:

```text
Station = persistent geographical sampling location
Visit   = one occupation of that station
```

This is essential for repeat limnological surveillance and repeat oceanographic stations.

### Inputs

- existing/new station;
- station ID;
- visit ID;
- GPS;
- optional Plus Code;
- timestamp;
- waterbody;
- platform/vessel;
- operator(s);
- observed water depth;
- weather;
- wind;
- sea/lake state;
- tide if relevant;
- ice if relevant;
- sampling purpose;
- notes.

### Location

Prefer the existing MethodMesh public location / Plus Code boundary where available.

Do not copy private Plus Code logic into this module.

Do not transmit precise location to any third party.

### Outputs

```text
aquatic_station_status
aquatic_station_id
aquatic_visit_id
aquatic_station_latitude
aquatic_station_longitude
aquatic_station_plus_code
aquatic_station_water_depth_m
aquatic_station_timestamp_utc
aquatic_station_metadata_json
aquatic_station_error
```

---

## 5.5 `aquatic.ctd.cast`

### Purpose

Record the provenance and field operation of a CTD or multiparameter-sonde cast.

It is deliberately not a generic raw-CTD processing package.

### Record

- cast ID;
- station / visit;
- instrument ID;
- instrument make/model/serial;
- configuration identifier;
- calibration date;
- cast start;
- cast end;
- surface-soak note/time;
- direction: downcast/upcast/both;
- maximum pressure;
- maximum depth;
- nominal descent/ascent rate if known;
- operator;
- number of bottle fires;
- raw data filename/URI;
- processed data filename/URI;
- optional SHA-256 file hash;
- field notes;
- cast status / problem flags.

### Outputs

```text
aquatic_ctd_status
aquatic_ctd_cast_id
aquatic_ctd_instrument_id
aquatic_ctd_max_pressure_dbar
aquatic_ctd_max_depth_m
aquatic_ctd_start_utc
aquatic_ctd_end_utc
aquatic_ctd_raw_file_uri
aquatic_ctd_processed_file_uri
aquatic_ctd_file_sha256
aquatic_ctd_flags
aquatic_ctd_metadata_json
aquatic_ctd_error
```

### Explicit boundary

v0.1 should not attempt manufacturer-specific raw CTD processing such as arbitrary:

- hexadecimal conversion;
- sensor-response alignment;
- conductivity-cell thermal-mass correction;
- ship-heave loop editing;
- proprietary calibration application;
- arbitrary raw oxygen sensor processing.

Those procedures are instrument and configuration dependent and are already handled by specialist tooling.

MethodMesh may import **processed** profile data for summarisation.

---

## 5.6 `aquatic.sample_id.generate`

### Purpose

Generate stable, human-readable sample IDs without forcing a full sample-record workflow.

### Configurable template components

Possible tokens:

- project;
- cruise/campaign;
- station;
- visit;
- cast;
- target depth;
- bottle number;
- sample type;
- replicate;
- sequence number;
- date.

Example:

```text
CR26-ST017-CT03-025M-NUT-01
```

The exact template must be configurable rather than hard-coded.

### Requirements

- deterministic formatting;
- collision check against the local repository;
- optional automatic sequence increment;
- preview before commit;
- preserve the template and component values in audit metadata.

### Handoff to labels

If QR/barcode encoding is requested, call the existing MethodMesh QR/barcode capability through its public boundary.

Do not implement a second QR encoder here.

### Outputs

```text
aquatic_sample_id_status
aquatic_sample_id
aquatic_sample_id_template
aquatic_sample_id_metadata_json
aquatic_sample_id_error
```

---

## 5.7 `aquatic.sample.record`

### Purpose

Record one discrete water sample and its provenance.

### Inputs

- sample ID;
- station;
- visit;
- cast;
- rosette/bottle number;
- target depth;
- actual pressure/depth;
- sample timestamp;
- sample matrix/type;
- intended analyte or purpose;
- sample volume;
- container;
- filtration yes/no;
- filter ID;
- filter material;
- pore size;
- preservative;
- preservation timestamp;
- storage condition;
- replicate ID;
- blank type;
- operator;
- notes.

### Suggested sample categories

Use a choice setting rather than free text for common categories, with an “other” value:

- chemistry;
- nutrients;
- chlorophyll;
- microbiology;
- eDNA;
- isotope;
- particulate;
- phytoplankton;
- zooplankton;
- general archive;
- other.

Do not over-standardise analyte names in v0.1; allow declared free-text analyte/purpose alongside the broad sample category.

### Outputs

```text
aquatic_sample_status
aquatic_sample_id
aquatic_sample_station_id
aquatic_sample_visit_id
aquatic_sample_cast_id
aquatic_sample_bottle_number
aquatic_sample_target_depth_m
aquatic_sample_actual_depth_m
aquatic_sample_pressure_dbar
aquatic_sample_type
aquatic_sample_timestamp_utc
aquatic_sample_metadata_json
aquatic_sample_error
```

---

## 5.8 `aquatic.rosette.fire`

### Purpose

Record individual water-sampler / rosette firing events and connect CTD pressure/depth to resulting samples.

### Inputs

- cast ID;
- rosette/sampler ID;
- bottle number;
- firing sequence;
- firing timestamp;
- pressure;
- derived depth;
- bottle status;
- successful fire / misfire / refire;
- linked sample IDs;
- notes.

### Outputs

```text
aquatic_rosette_status
aquatic_rosette_cast_id
aquatic_rosette_bottle_number
aquatic_rosette_fire_time_utc
aquatic_rosette_pressure_dbar
aquatic_rosette_depth_m
aquatic_rosette_sample_ids_json
aquatic_rosette_flags
aquatic_rosette_metadata_json
aquatic_rosette_error
```

A cast summary should be able to report which bottles fired and whether any planned depth was missed.

---

## 5.9 `aquatic.profile.summarise`

### Purpose

Summarise an already processed vertical profile.

### Input formats

Initial support:

- CSV;
- TSV;
- simple processed CNV-style ASCII if a robust parser can be implemented.

The user maps columns to variables and declares units.

Do not guess ambiguous columns.

### Supported profile variables

Where present:

- pressure;
- depth;
- temperature;
- conductivity;
- Practical Salinity;
- Absolute Salinity;
- density;
- dissolved oxygen;
- pH;
- chlorophyll fluorescence;
- turbidity;
- PAR/light;
- other numeric variables as generic series.

### General summary

Return:

- number of valid observations;
- profile depth range;
- surface value;
- bottom/deepest value;
- minimum;
- maximum;
- depth of minimum/maximum;
- optional mean/median;
- values interpolated at requested depths;
- depth-bin summaries.

### Vertical gradients

Where supported, derive:

- strongest temperature gradient;
- strongest salinity gradient;
- strongest density gradient;
- strongest oxygen gradient;
- thermocline candidate;
- halocline candidate;
- pycnocline candidate;
- oxycline candidate.

The criterion must always be explicit.

Possible selectable methods include:

```text
maximum_absolute_gradient
gradient_threshold
user_defined_depth_window
```

### Mixed-layer depth

Support explicit methods rather than a black-box answer, for example:

- temperature-difference threshold from a chosen reference depth;
- density-difference threshold from a chosen reference depth;
- user-defined method parameters.

Store the selected criterion and threshold.

### Outputs

```text
aquatic_profile_status
aquatic_profile_max_depth_m
aquatic_profile_thermocline_depth_m
aquatic_profile_halocline_depth_m
aquatic_profile_pycnocline_depth_m
aquatic_profile_oxycline_depth_m
aquatic_profile_mixed_layer_depth_m
aquatic_profile_summary
aquatic_profile_summary_json
aquatic_profile_error
```

---

## 5.10 Stratification and limnological metrics

These can initially live behind `aquatic.profile.summarise` and shared `StratificationEngine`, with the option to expose a separate method later if the UI becomes too crowded.

### Metrics to support

Where adequate data exist:

- epilimnion / metalimnion / hypolimnion boundaries;
- temperature gradient;
- density gradient;
- buoyancy frequency `N²`;
- Schmidt stability;
- water-column heat content;
- selected layer means;
- layer thicknesses.

### Important input requirements

Do not calculate whole-lake metrics from a single profile if additional lake geometry is required.

In particular:

- Schmidt stability requires appropriate hypsographic/bathymetric information;
- whole-lake heat content requires geometry/volume information;
- a per-unit-area water-column heat-content quantity may be possible from a single vertical profile but must be labelled accordingly.

If the required geometry is absent:

```text
metric = unavailable
reason = missing_hypsography
```

Do not substitute an unstated approximation.

---

## 5.11 `aquatic.depth_plan.generate`

### Purpose

Generate a planned set of sampling depths before or during a station visit.

### Strategies

- explicit fixed list;
- fixed vertical interval;
- surface / mid / bottom;
- equal proportional depths;
- surface plus near-bottom offset;
- standard project template;
- stratification-targeted;
- custom hybrid plan.

### Stratification-targeted mode

If a valid processed profile is available, allow a plan such as:

```text
surface
above thermocline
thermocline
below thermocline
near bottom
```

or equivalent density/oxygen targets.

The generated plan is a planning output, not a claim that bottles were actually fired at those depths.

### Safety / plausibility

- never generate depth below the known water depth;
- support a configurable near-bottom safety clearance;
- warn when water depth is unknown;
- keep requested and clipped/adjusted depths distinct.

### Outputs

```text
aquatic_depth_plan_status
aquatic_depth_plan_count
aquatic_depth_plan_text
aquatic_depth_plan_json
aquatic_depth_plan_metadata_json
aquatic_depth_plan_error
```

---

## 5.12 `aquatic.instrument.check`

### Purpose

Record calibration, verification and operational checks for field instruments.

### Check types

- pre-deployment;
- post-deployment;
- calibration;
- field verification;
- blank/standard check;
- cleaning;
- service;
- battery/power;
- clock synchronisation;
- sensor-zero check;
- other.

### Record

- instrument ID;
- check type;
- timestamp;
- operator;
- reference material/instrument;
- expected value;
- observed value;
- tolerance;
- pass/fail;
- corrective action;
- next calibration due;
- notes.

### Rules

Pass/fail tolerances should be user/project defined unless an instrument-specific standard is explicitly configured.

Never invent a manufacturer tolerance.

### Outputs

```text
aquatic_instrument_check_status
aquatic_instrument_id
aquatic_instrument_check_type
aquatic_instrument_check_result
aquatic_instrument_check_timestamp_utc
aquatic_instrument_check_metadata_json
aquatic_instrument_check_error
```

---

## 5.13 `aquatic.field_qc.run`

### Purpose

Run completeness and plausibility checks before leaving a station or closing a visit.

This is operational QC, not post-hoc scientific data cleaning.

### Example completeness checks

- planned samples vs recorded samples;
- required blank missing;
- required duplicate missing;
- planned depth not sampled;
- CTD expected but not recorded;
- Secchi expected but not recorded;
- sample lacks preservation data;
- sample lacks timestamp;
- sample has no operator;
- bottle firing has no linked sample;
- cast file/hash missing when required;
- calibration/check overdue;
- station visit still missing GPS.

### Example plausibility checks

- negative depth;
- sample depth > recorded station water depth;
- Secchi depth > water depth;
- CTD maximum depth > water depth beyond tolerance;
- target depth differs greatly from actual depth;
- sample timestamp before station arrival;
- sample timestamp before cast start when linked to a cast;
- duplicate sample ID;
- duplicate cast ID;
- impossible latitude/longitude;
- departure before arrival;
- preservation before sampling;
- bottle number fired twice without explicit refire;
- recovery before mooring deployment.

### QC behaviour

QC findings are flags.

Do not silently modify scientific records.

Each finding should include:

- severity;
- rule ID;
- affected record;
- human-readable message;
- suggested resolution where possible.

### Outputs

```text
aquatic_qc_status
aquatic_qc_issue_count
aquatic_qc_warning_count
aquatic_qc_error_count
aquatic_qc_summary
aquatic_qc_json
aquatic_qc_error
```

Suggested status:

```text
PASS
PASS_WITH_WARNINGS
INCOMPLETE
BLOCKING_ERRORS
```

Projects may decide whether blocking errors actually prevent a dashboard Finish action. The default should favour visibility over destructive enforcement.

---

## 5.14 `aquatic.transect.record`

### Purpose

Record a linear or waypoint-based aquatic survey operation.

### Record

- transect ID;
- project/campaign;
- start timestamp/GPS;
- end timestamp/GPS;
- planned route/reference;
- waypoints;
- stations/visits encountered;
- samples;
- instruments;
- platform/vessel;
- nominal speed;
- notes;
- status.

### Optional derived values

- straight-line start/end distance;
- cumulative waypoint distance;
- elapsed time;
- mean transit speed if valid timestamps/positions exist.

Derived geographic distance should use a documented geodesic calculation rather than treating latitude/longitude as Cartesian coordinates.

### Outputs

```text
aquatic_transect_status
aquatic_transect_id
aquatic_transect_start_utc
aquatic_transect_end_utc
aquatic_transect_distance_m
aquatic_transect_station_count
aquatic_transect_metadata_json
aquatic_transect_error
```

---

## 5.15 `aquatic.mooring.record`

### Purpose

Record deployment, service and recovery of a mooring or fixed aquatic instrument platform.

### Event types

- deploy;
- recover;
- service;
- inspect;
- replace instrument;
- other.

### Record

- mooring ID;
- event type;
- timestamp;
- GPS;
- water depth;
- anchor/attachment notes;
- line length;
- target sensor depths;
- instrument IDs and serials;
- logger start/stop state;
- sampling interval;
- expected recovery date;
- actual recovery date;
- fouling/damage condition;
- file names/hashes on recovery;
- operator;
- notes.

### Outputs

```text
aquatic_mooring_status
aquatic_mooring_id
aquatic_mooring_event
aquatic_mooring_timestamp_utc
aquatic_mooring_latitude
aquatic_mooring_longitude
aquatic_mooring_instrument_count
aquatic_mooring_metadata_json
aquatic_mooring_error
```

---

## 5.16 `aquatic.sediment.record`

### Purpose

Record sediment coring or grab sampling.

### Sampling modes

- gravity core;
- piston core;
- box core;
- hand core;
- Ekman grab;
- Van Veen grab;
- Ponar grab;
- other.

### Record

- event/sample ID;
- station/visit;
- sampler type;
- deployment timestamp;
- recovery timestamp;
- water depth;
- target penetration;
- observed penetration;
- recovered core length;
- core diameter if known;
- overlying water retained yes/no;
- sample integrity;
- sediment description;
- photograph URI if used;
- subsample IDs;
- preservation/storage;
- operator;
- notes.

### Outputs

```text
aquatic_sediment_status
aquatic_sediment_id
aquatic_sediment_method
aquatic_sediment_water_depth_m
aquatic_sediment_recovery_length_cm
aquatic_sediment_subsample_count
aquatic_sediment_metadata_json
aquatic_sediment_error
```

---

# 6. Persistent station dashboard

## 6.1 `aquatic.station.dashboard`

The dashboard is the package’s field-oriented composition layer.

It should use the MethodMesh persistent-dashboard pattern rather than behaving like a normal one-shot result screen.

### Example native view

```text
STN-017 · Lake Kivu
Visit VK-2026-09-04-017
GPS ✓     14:32 UTC
Water depth 84.3 m

SECCHI
6.4 m ✓

CTD
Cast 028
Max depth 80.1 m ✓

SAMPLES
8 / 10 completed

0 m      ✓
5 m      ✓
10 m     ✓
20 m     ✓
30 m     ✓
40 m     ✓
60 m     ✓
80 m     ✓
Blank    ✗
Duplicate ✗

INSTRUMENTS
CTD-02 calibration current ✓
DO probe field check due ⚠

QC
2 outstanding items

[ Add sample ]
[ Record bottle fire ]
[ CTD cast ]
[ Secchi ]
[ Instrument check ]
[ Run QC ]

[ Finish station ]
```

### Dashboard state

The dashboard should aggregate shared engines/repository data.

It must **not** launch other capability screens merely to scrape their results.

Preferred architecture:

```text
atomic method/screen ─┐
                      ├── shared engines + repository
station dashboard  ───┘
```

Dashboard capture actions may use shared module logic directly.

### Persistent-dashboard result contract

The latest valid station snapshot must be held separately from the result passed to the generic scaffold.

Conceptually:

```kotlin
val keepLiveDashboard =
    context.presentationMode == CapabilityPresentationMode.Dashboard ||
    context.isNativePresetRun

val scaffoldResult =
    if (keepLiveDashboard) null else result
```

Native use:

```text
refresh/update
    ↓
dashboard remains visible
    ↓
user explicitly presses Finish / Use this snapshot
    ↓
onConfirmed(latestResult)
```

External/ODK use:

```text
calculate/read current snapshot
    ↓
normal structured result
    ↓
automatic return
```

A genuine external caller must never be trapped waiting for a native **Finish** button.

### Refresh semantics

Refreshing or updating dashboard cards must not create a new MethodMesh graph result every time.

However, local repository changes may be written durably as the operator records them so that field data are not lost if the app closes.

This distinction is important:

```text
local durable field state ≠ committed MethodMesh execution result
```

The final snapshot is returned/committed only through the proper execution close-out.

### Protocol behaviour

Protocols and scheduled workflows must retain the normal MethodMesh close-out contract.

Test separately that `aquatic.station.dashboard` cannot trap a multi-step protocol.

### `intent_test`

Do not assume every intent-style launch is a genuine external machine caller.

Internal `intent_test` behaviour must remain interactive when necessary.

---

# 7. Dashboard snapshot outputs

Suggested compact dashboard outputs:

```text
aquatic_dashboard_status
aquatic_dashboard_station_id
aquatic_dashboard_visit_id
aquatic_dashboard_visit_status
aquatic_dashboard_sample_planned_count
aquatic_dashboard_sample_completed_count
aquatic_dashboard_cast_count
aquatic_dashboard_secchi_depth_m
aquatic_dashboard_qc_issue_count
aquatic_dashboard_summary
aquatic_dashboard_snapshot_json
aquatic_dashboard_error
```

Example main result:

```text
STN-017 complete — 10/10 samples, 1 CTD cast, Secchi 6.4 m, QC passed
```

The full snapshot JSON may include identifiers and current record summaries but should not be the primary native result.

---

# 8. Local persistence

A repository is justified because station work is multi-step and may last minutes to hours.

## 8.1 Requirements

The repository should support:

- active project/campaign;
- station definitions;
- visits;
- casts;
- samples;
- bottle fires;
- instrument checks;
- depth plans;
- transects;
- mooring events;
- sediment events;
- QC state.

## 8.2 Offline first

All field capture and calculation functions should work offline.

No operation should require remote authentication to save a station/sample record.

## 8.3 Amendment behaviour

Do not silently overwrite scientifically relevant values.

Where practical, preserve:

- record creation time;
- modification time;
- prior value or amendment event;
- operator;
- reason for correction when supplied.

A lightweight amendment trail is preferable to pretending an edited value was always the original observation.

## 8.4 Export

The package should eventually support explicit export of field records as:

- JSON;
- CSV tables;
- a human-readable station summary.

Export is an explicit user action, not an automatic file dump after every native calculation.

---

# 9. ALCOA / provenance priorities

For scientific and operational records, audit metadata should capture enough information to reconstruct what MethodMesh did.

Where relevant:

- method ID;
- method version;
- calculation-engine version;
- event timestamp;
- UTC timestamp;
- original timezone/offset;
- operator identifier if provided;
- project/campaign;
- station/visit;
- original input values and units;
- normalised values;
- calculation criteria;
- empirical coefficients;
- location and location accuracy;
- instrument ID/model/serial;
- calibration/check date;
- file URI;
- file hash;
- warnings;
- QC flags;
- edit/amendment information.

Do not expose all of this on the primary result screen.

---

# 10. Presets

Presets are particularly valuable for repetitive field programmes.

Examples:

### Lake monitoring Secchi preset

Fixed:

- disk diameter;
- calculation method;
- required observation fields.

Runtime:

- station;
- disappearance depth;
- reappearance depth;
- conditions.

### Nutrient sample preset

Fixed:

- sample category = nutrients;
- bottle/container;
- filtration;
- preservative;
- volume.

Runtime:

- station;
- depth;
- bottle number;
- sample ID.

### CTD cast preset

Fixed:

- instrument ID;
- configuration ID;
- default cast type.

Runtime:

- station/visit;
- cast number;
- start/end;
- maximum pressure;
- file.

Fixed preset values must disappear during the preset run; only runtime inputs and genuinely necessary operational controls remain visible.

---

# 11. ODK / XLSForm integration

The module should ship with:

```text
docs/example_odk_aquaticfield.xlsx
```

The workbook should demonstrate grouped Android-intent calls for at least:

- salinity conversion;
- pressure/depth conversion;
- Secchi;
- station visit;
- CTD cast;
- sample ID;
- sample record;
- field QC;
- dashboard snapshot.

ODK should supply its own values as intent extras and receive:

- compact core fields;
- declared secondary fields;
- metadata/audit JSON;
- media/file URI where applicable.

ODK must not depend on native MethodMesh dialogs.

A full implementation can use one example workbook with clearly separated groups for each method, provided every method’s field mapping is documented in the module README.

---

# 12. Permissions and Android boundaries

## Location

Needed for station, transect and mooring geolocation.

The module should:

- request permission at the capability boundary;
- preserve GPS accuracy if available;
- support manual coordinate entry when location permission/fix is unavailable;
- never silently transmit precise coordinates externally.

## Files

Profile import and CTD file attachment should use Android file/URI mechanisms compatible with current MethodMesh patterns.

Avoid unnecessary broad storage permission.

Persist selected URI strings using orientation-safe state.

## Camera

Only needed if a future sediment/sample workflow captures photographs directly.

If existing MethodMesh camera/document capabilities can supply media through a public boundary, reuse them rather than duplicating private implementation.

## Network

No core method requires network access.

Optional map display or later external lookup functions must disclose providers, cache sensibly and degrade gracefully offline.

---

# 13. Field QC philosophy

The package should distinguish three different things:

```text
measurement
calculation
quality flag
```

A quality check must not mutate the original field record.

Example:

```text
Observed sample depth: 86.2 m
Recorded station depth: 84.3 m
QC: ERROR — sample depth exceeds station depth by 1.9 m
```

MethodMesh should not quietly replace `86.2` with `84.3`.

Likewise:

```text
Secchi disappearance: 5.8 m
Reappearance: 7.2 m
QC: WARNING — directional observations differ by 1.4 m
```

The operator can then review or amend the record explicitly.

---

# 14. Scientific algorithms and transparency

Every derived scientific value should be reproducible.

For each algorithm, document:

- formula or upstream standard;
- input variables;
- units;
- domain of applicability;
- default parameters;
- configurable parameters;
- failure conditions;
- warnings;
- implementation version;
- validation/reference cases.

Particular care is needed for:

- TEOS-10 salinity/density functions;
- freshwater specific-conductance corrections;
- empirical conductivity/TDS factors;
- Secchi-derived trophic/euphotic estimates;
- pressure-depth conversion;
- mixed-layer definitions;
- thermocline/halocline/oxycline detection;
- Schmidt stability;
- heat-content calculations.

A visually plausible number is not sufficient.

---

# 15. Scientific validation strategy

Pure calculations should have unit tests using published or independently generated reference values.

At minimum test:

## Salinity

- conductivity unit conversions;
- representative oceanic `C,T,p` → `SP`;
- `SP,p,lon,lat` → `SA`;
- low-salinity behaviour supported by the chosen GSW implementation;
- invalid/missing coordinates when `SA` needs them;
- empirical TDS output includes estimate flag and coefficient.

## Pressure/depth

- equatorial and high-latitude cases;
- surface pressure;
- deep-water pressure;
- sensor offset sign convention;
- cable angle conventions;
- impossible angle/value handling.

## Secchi

- mean disappearance/reappearance;
- one-sided observation;
- bottom-limited observation;
- Carlson TSI reference values;
- optional euphotic estimate visibly flagged.

## Profile

- monotonic synthetic profile;
- known maximum-gradient profile;
- duplicated depths;
- missing values;
- unsorted depths;
- irregular depth spacing;
- mixed units;
- shallow profile with no defensible thermocline;
- oxygen profile with an artificial oxycline;
- stratified lake profile;
- absent hypsography for a geometry-dependent metric.

## QC

- every rule independently;
- warnings vs errors;
- no silent data changes;
- duplicate IDs;
- timestamp ordering;
- missing expected samples.

---

# 16. Native UX principles

The field UI should work on a wet boat in poor conditions.

Prefer:

- large touch targets;
- short forms;
- strong numeric entry;
- explicit units beside fields;
- sensible defaults from active visit/project;
- one-tap reuse of station/instrument context;
- immediate validation;
- obvious success state;
- minimal scrolling for repeated sample entry;
- offline operation;
- dark-mode compatibility;
- state retention through rotation/backgrounding.

Do not bury the main value under metadata.

For repetitive tasks, support:

```text
Save + next sample
```

or equivalent module-owned workflow where compatible with the normal MethodMesh completion contract.

---

# 17. Suggested module dependencies

Use existing public MethodMesh boundaries when available.

Potential dependencies:

- location / Plus Code capture;
- QR/barcode generation;
- camera/photo capture;
- file hashing;
- generic export/share.

Do not copy another module’s private classes or logic.

If there is no suitable generic public boundary, keep the aquatic implementation local rather than introducing capability-specific hacks into shared UI.

---

# 18. Out of scope for the first implementation

The package should deliberately not become a replacement for specialist oceanographic processing suites.

Do not include in the first implementation:

- arbitrary raw manufacturer-specific CTD processing;
- full Sea-Bird replacement processing;
- ADCP processing;
- tidal prediction;
- full carbonate-system chemistry;
- nutrient chemistry modelling;
- remote-sensing processing;
- bathymetric GIS;
- cable catenary/towing dynamics beyond clearly labelled simple geometry;
- automatic ecological-status classification;
- automatic regulatory compliance claims;
- LIMS functionality;
- cloud synchronisation as a requirement.

These can later be added as separate, scientifically bounded methods if there is a strong use case.

---

# 19. Recommended implementation order

## Phase 1 — high-value field core

1. shared units/models/repository;
2. `aquatic.pressure_depth.convert`;
3. `aquatic.salinity.convert`;
4. `aquatic.secchi.measure`;
5. `aquatic.station.visit`;
6. `aquatic.sample_id.generate`;
7. `aquatic.sample.record`;
8. `aquatic.ctd.cast`;
9. `aquatic.field_qc.run`;
10. `aquatic.station.dashboard`.

This produces the first genuinely useful “electronic aquatic field notebook”.

## Phase 2 — sampling-system integration

11. `aquatic.rosette.fire`;
12. `aquatic.depth_plan.generate`;
13. `aquatic.instrument.check`;
14. processed `aquatic.profile.summarise`.

## Phase 3 — extended field methods

15. `aquatic.transect.record`;
16. `aquatic.mooring.record`;
17. `aquatic.sediment.record`;
18. advanced stratification metrics.

All are part of the planned package even if delivered incrementally.

---

# 20. Production checklist

Before any method is promoted from Development:

- [ ] Native run works.
- [ ] Main result is concise and useful.
- [ ] Native preset creation works.
- [ ] Fixed preset fields disappear at runtime.
- [ ] Runtime fields remain available.
- [ ] ODK/XLSForm example returns declared fields.
- [ ] Protocol execution closes cleanly.
- [ ] Scheduled execution closes cleanly where applicable.
- [ ] Main share action shares the useful result rather than metadata.
- [ ] Audit JSON is available.
- [ ] State survives orientation change.
- [ ] File URI state survives configuration changes where applicable.
- [ ] Permission-denial states are clear.
- [ ] Offline behaviour is documented.
- [ ] Calculation unit tests pass.
- [ ] Scientific reference cases pass.
- [ ] Dashboard does not create graph results on refresh.
- [ ] Dashboard native preset remains on dashboard until Finish.
- [ ] Dashboard external call automatically returns a snapshot.
- [ ] `intent_test` behaviour is tested.
- [ ] No aquatic special cases were added to generic shared UI.
- [ ] Module README and XLSForm are present.
- [ ] `000_Roadmap.md` is updated.
- [ ] `./gradlew :app:assembleDebug` passes.

---

# 21. Roadmap entry

Suggested addition to `000_Roadmap.md`:

```text
## Aquatic fieldwork / oceanography / limnology

Planned Development module: `aquaticfield`.

Scope:
- TEOS-10-based salinity/water-property conversions
- conductivity/specific-conductance/TDS-estimate utilities
- pressure/depth and sensor/cable corrections
- guided Secchi measurements
- reusable station + visit records
- CTD/sonde cast provenance
- sample ID generation
- discrete sample records
- rosette firing logs
- processed vertical-profile summaries
- thermocline/halocline/pycnocline/oxycline and mixed-layer calculations
- limnological stratification metrics where sufficient geometry/data exist
- sample depth-plan generation
- instrument calibration/check logs
- field QC/completeness
- transects
- mooring deployment/recovery/service
- sediment core/grab records
- persistent station dashboard

Architecture:
- one self-contained `aquaticfield` module
- atomic methods + shared calculation/data engines
- local repository for active field state
- dashboard composes shared engines/repository
- native dashboard remains persistent until explicit Finish
- external/ODK calls remain structured and single-shot
- no core calculation requires network access

Known exclusions for initial implementation:
- raw manufacturer-specific CTD processing
- ADCP processing
- tide prediction
- full carbonate chemistry
- full LIMS/cloud-sync behaviour
```

---

# 22. Contributor / Work-mode handoff brief

> Build a self-contained MethodMesh module under `app/src/main/java/com/example/methodmesh/modules/aquaticfield/`.
>
> The module is an offline-first oceanography/limnology field toolkit. Implement the atomic methods and shared domain engines described in `README_AquaticFieldwork.md`, beginning with pressure/depth, salinity, Secchi, station visits, sample IDs, sample records, CTD cast records, field QC and the persistent station dashboard.
>
> Keep all capability-specific code, repositories, docs and examples inside the module folder. Do not add aquatic special cases to the shared MethodMesh UI and do not add a manual central module registry entry.
>
> Every method must have a stable AS100 method ID, declared `MethodSetting` inputs, compact core outputs, audit JSON and clean native/preset/ODK/protocol behaviour. Start all methods in Development.
>
> Calculations must be deterministic, unit-explicit, documented and tested against authoritative reference values. Marine salinity/density work should use TEOS-10/GSW definitions. Empirical freshwater conversions must record their coefficients and be visibly labelled as estimates.
>
> The dashboard must implement the persistent-dashboard pattern: keep the latest true `ExecutionResult` locally, pass `null` to `CapabilityScreenScaffold` while native dashboard/preset presentation should remain live, and only call `onConfirmed(result)` on explicit Finish/Use-this-snapshot. External/ODK invocation must still return automatically.
>
> Dashboard refreshes must not create MethodMesh graph records. Local station/sample data may nevertheless be persisted durably as the operator records them. Atomic screens and the dashboard must share calculation engines and repositories rather than invoking screens to scrape values.
>
> Provide module-owned documentation and `docs/example_odk_aquaticfield.xlsx`, update `000_Roadmap.md`, add focused tests for the calculation/QC/state logic, and run `./gradlew :app:assembleDebug`.

---

# 23. Technical references for implementation

These should be checked against the current authoritative versions during implementation.

## TEOS-10 / Gibbs SeaWater

Use the official TEOS-10 Gibbs SeaWater documentation for:

- Practical Salinity from conductivity;
- Absolute Salinity from Practical Salinity, pressure and position;
- Conservative Temperature;
- density;
- pressure/height relationships.

Official project: `https://www.teos-10.org/`

## Secchi / trophic-state calculations

Carlson trophic-state calculations derived from Secchi depth are established freshwater indices. The implementation should document the exact formula and domain of interpretation rather than treating it as a universal ecological classification.

A useful US EPA technical source contains the Carlson TSI(SD) formula.

## CTD processing boundary

Sea-Bird Scientific documentation illustrates why raw CTD processing is deliberately out of scope for this first MethodMesh package: profiling workflows may require conversion, filtering, temporal alignment, conductivity-cell thermal-mass correction, loop editing, derivation and bin averaging.

The MethodMesh package should therefore record CTD operations and import processed data rather than pretending a generic raw processor can safely replace instrument-specific workflows.

---

# 24. End state

The intended result is a low-weight but unusually capable field package:

```text
Aquatic Fieldwork
│
├── calculate
│   ├── salinity / water properties
│   ├── pressure / depth
│   ├── profile / stratification
│   └── depth plan
│
├── observe
│   └── Secchi
│
├── operate
│   ├── station / visit
│   ├── CTD cast
│   ├── rosette firing
│   ├── sample
│   ├── instrument check
│   ├── transect
│   ├── mooring
│   └── sediment
│
├── assure
│   └── field QC
│
└── compose
    └── persistent station dashboard
```

The design should feel less like a collection of calculators and more like a **small, transparent, offline electronic aquatic field notebook** that still obeys MethodMesh’s core principle: each useful operation remains a reusable capability with clean inputs, outputs and provenance.
