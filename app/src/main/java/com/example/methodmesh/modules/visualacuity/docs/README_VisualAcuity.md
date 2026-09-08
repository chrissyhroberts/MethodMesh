# Visual acuity

`visual_acuity.measure` is a Development-stage MethodMesh measurement capability for rapid visual-acuity testing with a calibrated single tumbling-E optotype.

**Status:** Development  
**Module:** `visualacuity`  
**Method:** `visual_acuity.measure`  
**Version:** `0.1.4`

The implementation follows the published design principles used by Peek Acuity and the V@home/WHOeyes lineage, but it is an independent MethodMesh implementation. It does not contain WHOeyes or Peek application code or branded assets, and this version does **not** claim clinical equivalence, regulatory approval, or diagnostic use.

## What it does

- renders a 5×5-grid tumbling E in four random orientations;
- uses the global MethodMesh physical screen calibration rather than trusting Android's nominal DPI alone;
- renders ETDRS-style crowding bars around the single optotype;
- runs a rapid 4-of-5 staircase from 1.0 to 0.0 logMAR;
- supports the validated nominal distances of **2 m** for distance VA and **40 cm** for near VA;
- records operator swipes without showing correctness feedback during testing;
- returns logMAR, metric Snellen, imperial Snellen and decimal acuity;
- records the full stimulus/response path and calibration metadata in audit JSON;
- runs locally and does not require a network connection.

## Scientific basis

### Peek Acuity design

The original Peek Acuity validation describes:

- a **5×5 tumbling E**;
- four orientations: 0°, 90°, 180° and 270°;
- one optotype at a time;
- participant indication of the E-arm direction, entered by the tester as a swipe;
- no correctness feedback to the tester during the sequence;
- a crowding boundary with bar thickness equal to one optotype limb and a clear separation equal to half the total E size;
- screen brightness set to 100%;
- a rapid stair-casing approach;
- output in logMAR and metric/imperial Snellen notation.

Reference:

Bastawrous A, Rono HK, Livingstone IAT, et al. Development and Validation of a Smartphone-Based Visual Acuity Test (Peek Acuity) for Clinical Practice and Community-Based Fieldwork. *JAMA Ophthalmology*. 2015;133(8):930-937. doi:10.1001/jamaophthalmol.2015.1468.  
https://pmc.ncbi.nlm.nih.gov/articles/PMC5321502/

Peek later documented that some Android devices misreported pixel density, causing incorrect physical optotype sizing. Its mitigation was a ruler-based physical calibration check. MethodMesh already has a global ruler-calibration mechanism, so this capability consumes that existing calibration rather than implementing a second device calibration store.

Reference:  
https://peekvision.org/solutions/peek-acuity/peek-acuity-calibration/

### V@home / WHOeyes staircase

WHOeyes is described by WHO as a near- and distance-visual-acuity app using a tumbling E. WHO's information sheet specifies target distances of **2 m** for distance testing and **40 cm** for near testing. WHOeyes was developed as an upgraded version of the validated V@home application.

References:

- WHOeyes: https://www.who.int/teams/noncommunicable-diseases/sensory-functions-disability-and-rehabilitation/whoeyes
- WHOeyes information sheet: https://cdn.who.int/media/docs/default-source/blindness-and-visual-impairment/whoeyes-info-sheet-en.pdf
- Wu Y, Keel S, Carneiro VLA, et al. Real-world application of a smartphone-based visual acuity test (WHOeyes) with automatic distance calibration. *Br J Ophthalmol*. 2024;108(11):1613-1620. doi:10.1136/bjo-2023-324913.

The published V@home staircase gives the most explicit presentation-count rules available in this lineage:

1. start at **1.0 logMAR**;
2. present **five** randomly oriented optotypes at a tested level;
3. pass the level if at least **4 of 5** are correct;
4. initial coarse levels are **1.0, 0.8, 0.5, 0.2 and 0.0 logMAR**;
5. a failure at 0.8 after passing 1.0 causes 0.9 to be tested;
6. continue until the smallest level meeting the 4-of-5 criterion is identified, or 0.0 is reached;
7. acuity poorer than 1.0 logMAR is recorded as **<6/60**.

Reference:

Han X, Scheetz J, Keel S, et al. Development and Validation of a Smartphone-Based Visual Acuity Test (Vision at Home). *Transl Vis Sci Technol*. 2019;8(4):27. doi:10.1167/tvst.8.4.27.  
https://pmc.ncbi.nlm.nih.gov/articles/PMC6701871/

### MethodMesh-specific staircase refinement

The publication states the coarse levels and gives the 1.0/0.8/0.9 example, but it does not publish every internal branch needed to reconstruct the application source code exactly.

MethodMesh therefore makes the missing rule explicit and versioned:

```text
algorithm_id      = methodmesh.vahome_fast_staircase
algorithm_version = 1.0.0
```

After a coarse failure, MethodMesh fills the known pass/fail bracket in **0.1-logMAR steps** until the smallest passing level is identified. For example:

```text
pass 1.0 -> fail 0.8 -> test 0.9
pass 0.8 -> fail 0.5 -> test 0.7, then 0.6 if 0.7 passes
```

Every tested level uses all five presentations. This refinement is a transparent MethodMesh implementation decision, not a claim about unpublished WHOeyes or Peek source code. It must not be changed silently under algorithm version `1.0.0`.

## Physical optotype geometry

For logMAR value `L`:

```text
MAR arcmin = 10^L
full E angular size = 5 × MAR arcmin
```

For test distance `d` in millimetres:

```text
θ = (5 × 10^L) × π / (180 × 60)
E_mm = 2 × d × tan(θ / 2)
stroke_mm = E_mm / 5
```

The E is drawn geometrically as a 5×5 grid rather than by using a font. This avoids font-metric substitution.

The crowding bars are implemented literally from the Peek publication:

```text
bar thickness = 1 optotype limb = E / 5
clear gap      = E / 2
outer crowding-box side = E + 2 × (E/5 + E/2) = 2.4E
```

If the calibrated stimulus cannot fit at its true physical size, MethodMesh tells the operator to rotate the device rather than shrinking the stimulus.

### Physical sizes at 2 m

| logMAR | E size (mm) | limb (mm) | crowding outer side (mm) | metric Snellen | imperial Snellen |
|---:|---:|---:|---:|---:|---:|
| 0.0 | 2.909 | 0.582 | 6.981 | 6/6 | 20/20 |
| 0.1 | 3.662 | 0.732 | 8.789 | 6/7.5 | 20/25 |
| 0.2 | 4.610 | 0.922 | 11.065 | 6/9.5 | 20/32 |
| 0.3 | 5.804 | 1.161 | 13.930 | 6/12 | 20/40 |
| 0.4 | 7.307 | 1.461 | 17.536 | 6/15 | 20/50 |
| 0.5 | 9.199 | 1.840 | 22.077 | 6/19 | 20/63 |
| 0.6 | 11.581 | 2.316 | 27.793 | 6/24 | 20/80 |
| 0.7 | 14.579 | 2.916 | 34.990 | 6/30 | 20/100 |
| 0.8 | 18.354 | 3.671 | 44.049 | 6/38 | 20/125 |
| 0.9 | 23.106 | 4.621 | 55.455 | 6/48 | 20/160 |
| 1.0 | 29.089 | 5.818 | 69.814 | 6/60 | 20/200 |

Snellen-equivalent labels follow the conventional 0.1-logMAR sequence (for example 0.5 = 6/19 = 20/63 and 0.8 = 6/38 = 20/125):  
https://www.ncbi.nlm.nih.gov/books/NBK11509/table/ch25kallspatial.T1/

At 40 cm the linear dimensions are approximately one fifth of the 2 m dimensions. The module checks that the 0.0-logMAR limb is at least one calibrated physical pixel on the current device. This one-pixel minimum is a MethodMesh engineering safeguard; it is **not** itself a clinically validated acceptance threshold. The Peek near-vision paper likewise notes that very small optotypes may need to be excluded when smartphone pixel size cannot portray them accurately.

Near-test reference:

Livingstone IAT, et al. Development and Validation of a Digital (Peek) Near Visual Acuity Test for Clinical Practice, Community-Based Survey, and Research. *Ophthalmology Science*. 2023.  
https://pmc.ncbi.nlm.nih.gov/articles/PMC9807182/

## Screen calibration

MethodMesh currently stores global physical screen calibration as:

```text
CalibrationRepository.current().dpPerMm
CalibrationRepository.current().calibrated
```

The calibrated render length is therefore:

```text
optotype_dp = optotype_mm × dpPerMm
```

Compose subsequently maps dp to physical screen pixels. The audit payload also records the current Compose `px/dp` density and therefore:

```text
effective_pixels_per_mm = dpPerMm × display_density_px_per_dp
```

A visual-acuity run is blocked unless the global calibration has been explicitly confirmed. There is deliberately no fallback to Android's nominal DPI because incorrect Android pixel-density reporting has previously been shown to cause inaccurate Peek optotype sizing.

## Lighting and brightness

During the active stimulus sequence the capability applies a reversible **100% per-window brightness override**, matching the Peek validation implementation. It does not modify the device-wide brightness setting.

When the phone has an ambient-light sensor, the capability records lux measurements and by default blocks the start if the current reading is above **1000 lux**. Peek's original study reported poor agreement in conditions above 1000 lux and used an in-app warning at that threshold. The study's controlled clinic testing was performed at 80–300 lux.

If no light sensor exists, testing remains possible and the audit JSON records that lighting measurement was unavailable.

## Native workflow

1. Confirm MethodMesh screen calibration with a physical ruler.
2. Select distance or near testing.
3. Select eye and correction status.
4. Measure the required testing distance from the screen to the participant's eyes.
5. Start the test.
6. The participant indicates the direction in which the E arms point.
7. The operator swipes the same direction on the screen. Use **Not seen / cannot identify** when appropriate.
8. MethodMesh completes five presentations at each tested level and follows the staircase.
9. The result screen displays logMAR and Snellen results; full trial data remain in audit metadata.

For the V@home validation configuration, distance VA was monocular at 2 m and near VA was binocular at 40 cm. The native UI defaults to right-eye distance testing and changes a newly selected near test to binocular unless the operator subsequently chooses another eye. Non-standard eye/distance combinations should be regarded as protocol variants rather than validated equivalence claims.

## Preset workflow

All normal settings are declared through `capabilitySettings()`:

- `test_mode`: `distance` or `near`;
- `eye`: `right`, `left` or `binocular`;
- `correction`: `habitual`, `none` or `not_recorded`;
- `ambient_light_warning`: boolean.

Fixed preset values are hidden with `CapabilityScreenContext.settingShouldBeShown(...)`. Runtime inputs remain visible. The physical calibration itself is global device state and is never copied into the preset.

## ODK / XLSForm workflow

ODK launches the same operator-facing measurement screen using a group Android intent. The caller supplies settings and MethodMesh returns the result into child fields.

Example intent:

```text
com.example.methodmesh.EXECUTE_METHOD(
  method_id='visual_acuity.measure',
  input_test_mode='distance',
  input_eye='right',
  input_correction='habitual',
  input_ambient_light_warning='true',
  input_payload_mode='FULL',
  return_mode='flat'
)
```

Use the supplied `docs/example_odk_VisualAcuity.xlsx` as the canonical example.

## Outputs

| Field | Role | Meaning |
|---|---|---|
| `visual_acuity_result` | main/core | Human-readable combined result, e.g. `6/12 · 20/40 · 0.30 logMAR`. |
| `visual_acuity_logmar` | core | logMAR threshold, or `>1.0`. |
| `visual_acuity_snellen_metric` | core | Metric Snellen label. |
| `visual_acuity_snellen_imperial` | core | Imperial Snellen label. |
| `visual_acuity_decimal` | core | Decimal acuity. |
| `visual_acuity_status` | audit/status | `succeeded` on a completed sequence. |
| `visual_acuity_audit_json` | audit | Full protocol, calibration, lighting, staircase and trial trace. |
| `visual_acuity_error` | error | Failure detail where applicable. |

For a result below the current test floor, v0.1 returns:

```text
visual_acuity_logmar            = >1.0
visual_acuity_snellen_metric    = <6/60
visual_acuity_snellen_imperial  = <20/200
visual_acuity_decimal           = <0.1
```

Peek's separate count-fingers, hand-movement and light-perception modes are intentionally **not** implemented in this first drop-in.

## Audit JSON

The audit JSON records at least:

- method and algorithm versions;
- test mode and nominal testing distance;
- eye and correction status;
- calibrated `dp/mm`;
- current `px/dp` and effective calibrated pixels/mm;
- calibration-confirmed flag;
- brightness policy;
- ambient-light sensor availability and mean lux;
- optotype and crowding geometry;
- 4-of-5 pass rule;
- full trial count;
- for every presentation: sequence number, logMAR level, item number, target orientation, operator response, correctness, physical E size in mm, E size in dp, calibrated limb size in pixels, and timestamp;
- final threshold and start/finish times.

This is deliberately sufficient to audit the actual presentation path rather than retaining only a final `6/x` value.

## Offline / online behaviour

The capability is fully local. It performs no network request and transmits no participant or stimulus data. ODK/MethodMesh transport remains local Android intent transport unless a wider protocol explicitly adds another networked capability.

## Known limitations and validation status

This module must remain **Development** until the following are closed:

1. **Physical ruler validation on real devices.** Check several logMAR sizes after global calibration, not only the calibration reference itself.
2. **Crowding geometry verification.** The v0.1 geometry is a literal implementation of the dimensions described in the Peek paper. Compare it against a validated Peek/ETDRS reference implementation before claiming equivalence.
3. **Full Android build.** Run `./gradlew :app:assembleDebug` in the complete MethodMesh checkout.
4. **Orientation testing.** Confirm an in-progress staircase and final result survive portrait/landscape recreation; landscape is likely required for the largest 2 m crowding stimulus on narrow phones.
5. **Preset testing.** Confirm fixed settings disappear and runtime settings remain available.
6. **ODK round trip.** Import the example XLSForm into ODK Central/Collect and verify every return field.
7. **Prospective measurement validation.** A software implementation derived from published geometry is not automatically equivalent to WHOeyes, Peek Acuity, or ETDRS. Clinical/research use as a validated outcome requires an appropriate equivalence/repeatability study.
8. **Medical-device/regulatory assessment.** Intended use determines whether local medical-device software requirements apply. This Development module makes no regulatory claim.
9. **Distance enforcement.** Android v0.1 instructs the operator to measure 2 m/40 cm; it does not implement WHOeyes iOS automatic distance calibration.
10. **Near-vision notation.** v0.1 returns angular acuity in logMAR/Snellen-equivalent notation. It does not yet return the Peek near test's N notation series.
11. **Low-vision extensions.** Count-fingers, hand-movement and light-perception workflows are not included.
12. **Age/use population.** WHO's screening guidance describes WHOeyes as suitable for people aged 8 years and above. MethodMesh does not infer or enforce participant age in this version.

## Validation performed in this drop-in environment

The pure Kotlin geometry/staircase engine was compiled with the locally installed Kotlin compiler and smoke-tested for:

- 0.0 and 1.0 logMAR physical-size calculations at 2 m;
- all-coarse-level pass path: 25 presentations, threshold 0.0;
- failure at the initial 1.0 level: 5 presentations, result poorer than 1.0;
- pass 1.0 / fail 0.8 / pass 0.9: threshold 0.9;
- pass 1.0 / pass 0.8 / fail 0.5 / pass 0.7 / pass 0.6: threshold 0.6.

The complete Android project and SDK were not mounted in this packaging environment, so an Android Gradle build is not claimed here.

## Canonical delivery folder

Copy the complete folder:

```text
app/src/main/java/com/example/methodmesh/modules/visualacuity/
```

No central module registration is required.


## v0.1.1 screen-fit repair

The original v0.1.0 screen incorrectly treated failure of the complete crowding frame to fit as failure of the optotype itself. At 1.0 logMAR and 2 m the full crowding frame is approximately 69.8 mm across, which exceeds the usable short dimension of many phones. This caused the optotype not to be drawn and, because the gesture detector was inside the same conditional branch, swipes were also disabled.

v0.1.1 separates the two constraints. The calibrated E is drawn whenever the E itself fits and swipe capture remains active. If only the full crowding frame fails to fit, the bars are omitted for that presentation rather than scaling the E. The trial audit records `crowding_mode=omitted_screen_constraint`. This is a development fallback, not a claim of ETDRS/Peek equivalence. A future version should implement a validated low-vision distance-transition strategy.


## v0.1.2 stimulus-only test surface

During an active visual-acuity test the capability now bypasses `CapabilityScreenScaffold` entirely and uses a full-screen white gesture surface. The only visible elements are the calibrated tumbling-E stimulus and its crowding bars when they physically fit. Titles, instructions, progress, cancel controls, ambient-light messages, result UI, margins, cards and correctness feedback are absent during measurement. Android status/navigation chrome is hidden using immersive-sticky flags and restored when the test ends. The entire display is the directional swipe target. Ambient light can still prevent a test from starting and remains recorded, but a change in lux during an active run no longer silently disables gestures.


## v0.1.3 true fullscreen test window

The v0.1.2 stimulus surface used `fillMaxSize()`, but the capability was still hosted inside the MethodMesh Home/preset content slot. Consequently the optotype and gesture detector occupied only that small parent region.

v0.1.3 presents the active test in a non-dismissible Compose `Dialog` with `usePlatformDefaultWidth=false` and `decorFitsSystemWindows=false`. The dialog creates a separate full-display window above the MethodMesh UI. Android status/navigation chrome is hidden on the dialog window itself, and the gesture detector is attached to the full-screen white surface. During testing the only rendered content is the calibrated optotype/crowding stimulus. The normal MethodMesh UI becomes visible again after the staircase completes.



## v0.1.4 non-repeating optotype orientation

Successive optotypes can no longer have the same orientation. After each recorded response, the next tumbling-E direction is selected uniformly at random from the three orientations other than the one just shown. This avoids visually ambiguous transitions such as `UP → UP` or `LEFT → LEFT`, where an operator could reasonably wonder whether the swipe was registered.

The first orientation of a new test remains uniformly random across all four directions. This change affects presentation only; the five-trial-per-level staircase and acuity scoring are unchanged.
