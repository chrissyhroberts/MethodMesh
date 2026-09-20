# Spectrum reference creator

**Module:** `star_spectrum`  
**Capability:** `astronomy.spectrum.reference.create`  
**Version:** `0.2.25`  
**Maturity:** `DEVELOPMENT`  
**Connectivity:** `OFFLINE`

## Purpose

Create a reusable wavelength calibration from the user's own reference-star observation, with A-type stars as the intended first-class workflow.

The reference is tied to the user's actual camera/grating/image geometry rather than pretending that a generic dispersion value is sufficient for every device. The current calibration policy is **automatic consensus first, manual review/fallback second**. See `DESIGN.md` for the authoritative current architecture.

## Native workflow

1. Load an A-star reference image.
2. Name the reference and optionally record star name/spectral type.
3. Select the RED and VIOLET ends of the dispersed spectrum. The shared colour-geometry picker automatically proposes R/Y, Y/G, G/B and B/V transitions; review/edit them only if needed.
4. Extract the reference spectrum using the same two-dimensional trace/background model as the analyser.
5. Inspect extraction QA and the chromatic wavelength bootstrap.
6. MethodMesh searches many possible Balmer-pattern assignments against permissive multi-scale absorption-trough candidates and ranks the resulting linear wavelength hypotheses.
7. Review the leading automatic consensus solution and its alternatives. At least three independently supported Balmer matches are required before the automatic proposal can populate the canonical anchors.
8. Fine-tune or replace any proposed anchor manually when necessary, then compare linear/quadratic fit QA.
9. **Commit reference**.
10. On Commit, MethodMesh freezes the canonical output and persists the reusable reference in the module-owned local reference library.

The reference does not become persistent before Commit.

## Built-in A-star anchor helpers

The UI exposes these convenience anchors:

| Label | Wavelength |
|---|---:|
| Hδ | 410.17 nm |
| Hγ | 434.05 nm |
| Hβ | 486.13 nm |
| Hα | 656.28 nm |

These are convenience values for the initial A-star workflow. The canonical data model stores arbitrary distance/wavelength anchors, so the module can later expose other calibration sources without changing the reference schema.

## Calibration model

The stored relation is:

```text
wavelength_nm = a0 + a1 * distance_px [+ a2 * distance_px^2]
```

The user chooses:

- linear (`order=1`) — minimum 2 anchors;
- quadratic (`order=2`) — minimum 3 anchors.

The final canonical fit is ordinary least squares over the **accepted/confirmed anchors**, regardless of whether those anchors came from automatic consensus, automatic consensus followed by manual review, or fully manual placement. The automatic solver itself searches simple linear hypotheses; once a proposal is accepted, the established canonical linear/quadratic fit and QA remain authoritative. Calibration RMS is stored in nanometres and is reused as one component of later target-observation calibration confidence.

## Persistent reference schema

A committed reference stores:

- stable reference UUID;
- reference name;
- star name;
- spectral type;
- creation time;
- source analysis dimensions;
- trace start/end convention;
- polynomial order and coefficients;
- calibration RMS;
- all calibration anchors;
- valid calibrated distance range.

The source astronomical image is **not** silently archived into the reference library. The reference library stores the calibration model and provenance required for reuse.

When this capability was invoked by ODK and MethodMesh itself acquired the source image, the source is returned to ODK as a conditional attachment, in accordance with MethodMesh source-return semantics.

## Settings / runtime inputs

| Key | Type | Default | Meaning |
|---|---|---:|---|
| `source_image_uri` | text | blank | Optional caller-supplied image; blank uses native picker. |
| `reference_name` | text | `A-star reference` | Human reference-library label. |
| `star_name` | text | blank | Reference-star name. |
| `spectral_type` | text | `A0V` | Recorded reference spectral type. |
| `polynomial_order` | choice | `2` | `1` linear or `2` quadratic. |
| `calibration_anchors_json` | text | blank | Advanced pre-supplied anchor array. Native UI normally proposes anchors by automatic Balmer consensus, with graphical review/fallback. |
| `ribbon_half_width_px` | int | 28 | Trace-search half-width. |
| `aperture_half_width_px` | int | 5 | Reference-star extraction half-width. |
| `background_gap_px` | int | 4 | Background-sideband gap. |

## Canonical outputs

| Field | Description |
|---|---|
| `spectrum_reference_status` | `succeeded` / `failed`. |
| `spectrum_reference_id` | Persistent reference UUID. |
| `spectrum_reference_source_image_uri` | Conditional source attachment when MethodMesh acquired the source on caller's behalf. |
| `spectrum_reference_source_image_sha256` | SHA-256 of the conditional source attachment. |
| `spectrum_reference_annotated_image_uri` | Reference image annotated with the fitted trace and confirmed Balmer anchors. |
| `spectrum_reference_spectrum_uri` | Calibrated reference-spectrum CSV with one row per extracted sample. |
| `spectrum_reference_anchors_uri` | Calibration-anchor CSV with known/fitted wavelengths and residuals. |
| `spectrum_reference_json_uri` | File form of the reusable reference JSON. |
| `spectrum_reference_provenance_uri` | Commit-time provenance JSON. |
| `spectrum_reference_manifest_uri` | JSON manifest with hashes for every bundle member. |
| `spectrum_reference_bundle_uri` | ZIP containing the complete reference package. |
| `spectrum_reference_annotated_image_sha256` / `spectrum_reference_spectrum_sha256` / `spectrum_reference_anchors_sha256` | SHA-256 hashes for the principal reference artefacts. |
| `spectrum_reference_json_sha256` / `spectrum_reference_provenance_sha256` / `spectrum_reference_manifest_sha256` / `spectrum_reference_bundle_sha256` | SHA-256 hashes for reference JSON, provenance, manifest and ZIP. |
| `spectrum_reference_name` | Human name. |
| `spectrum_reference_star_name` | Star name. |
| `spectrum_reference_spectral_type` | Recorded spectral type. |
| `spectrum_reference_anchor_count` | Number of wavelength anchors. |
| `spectrum_reference_anchor_source` | `automatic_consensus`, `automatic_consensus_reviewed`, or `manual`. |
| `spectrum_reference_polynomial_order` | 1 or 2. |
| `spectrum_reference_coefficients_json` | Polynomial coefficients `[a0,a1(,a2)]`. |
| `spectrum_reference_rms_nm` | Fit RMS in nm. |
| `spectrum_reference_dispersion_nm_per_px` | Polynomial derivative at the midpoint of the calibrated anchor range. |
| `spectrum_reference_wavelength_min_nm` / `spectrum_reference_wavelength_max_nm` | Wavelength range spanned by the calibration anchors/model. |
| `spectrum_reference_trace_quality` | Reference extraction trace-quality score. |
| `spectrum_reference_created_time_iso` | Commit time. |
| `spectrum_reference_json` | Complete reusable reference object. |
| `spectrum_reference_error` | Failure diagnostic. |

Shared FULL transport adds `methodmesh_full_json`.

## ODK Integration Card

### Method

`astronomy.spectrum.reference.create`

### Canonical showcase call

```text
com.example.methodmesh.EXECUTE_METHOD(
  method_id='astronomy.spectrum.reference.create',
  input_reference_name=${reference_name},
  input_star_name=${star_name},
  input_spectral_type=${spectral_type},
  input_payload_mode='FULL',
  return_mode='flat'
)
```

The operator selects the source image and completes calibration inside MethodMesh. The normal path is automatic Balmer-pattern consensus followed by review; manual anchor placement remains available as fallback. The ODK showcase therefore receives `spectrum_reference_source_image_uri` as the source attachment acquired on ODK's behalf.

### Return semantics

The showcase captures canonical reference fields, `methodmesh_status`, `methodmesh_full_json`, the conditional source image attachment, annotated reference image, calibrated spectrum CSV, anchor CSV, reference/provenance/manifest JSON files and the complete ZIP bundle.

### Workbook

`docs/example_odk_showcase_astronomy_spectrum_reference_create.xlsx`

Exactly one MethodMesh invocation is present. It uses unprefixed canonical return names and no return namespace.

## Presets / protocols

This capability remains independently selectable for presets and protocols.

A preset can fix an instrument-specific reference name, A-star spectral type, extraction aperture and polynomial order while leaving `source_image_uri` as a runtime input. A protocol can create/reference-calibrate before a later `astronomy.spectrum.analyse` run.

The persistent reference itself is referred to later by `spectrum_reference_id` / `reference_id`; the analysis capability does not need to know how the reference was created.

## Offline / privacy

Fully offline. Reference images, star names and calibration solutions remain on the device unless explicitly returned/shared by the invoking surface.

## Dependencies

No new third-party library. Uses the existing MethodMesh runtime, Android raster/file APIs, Jetpack Compose and module-owned JSON persistence.

## Limitations

- Automatic consensus currently searches only the built-in Hδ/Hγ/Hβ/Hα A-star catalogue; it is not a general stellar-line identifier.
- Automatic matching is a ranked, auditable calibration hypothesis, not proof that every visible trough is a Balmer line.
- Fewer than three independent supported Balmer matches cannot be accepted automatically.
- Manual review/fallback remains necessary when the spectrum is noisy, incomplete or ambiguous.
- A saved reference is only scientifically transferable while the relevant optical/camera geometry remains sufficiently stable.
- Zero-order-independent/high-dispersion use is architecturally supported by the line-based solver but remains pending independent real-data validation.

See `VALIDATION.md`.


### Balmer anchor placement UX

Placed Hα, Hβ, Hγ and Hδ anchors remain visible simultaneously on the spectrum chart. The active anchor can be reselected at any time. The chart supports horizontal pinch zoom/pan, press-and-hold magnified drag placement, and ±1/±5 sample nudging for fine control.

### Reference picker display

The calibration picker displays spectra in conventional VIOLET → RED order even though the internal trace coordinate remains RED-origin for stable storage and calibration. The default calibration view is continuum-normalised (`corrected signal / rolling-median continuum`) with a local vertical scale that expands as the user zooms. A raw extracted-signal view remains available. This is intended to make broad Balmer absorption troughs visible despite camera-response and continuum slope.

Endpoint placement uses a magnified candidate position. A coordinate is committed only after the candidate remains stationary for three seconds, so lifting the finger cannot jitter the stored point. Full-screen controls apply Android navigation-bar padding.

### v0.2 calibration-trace refinement

The image extractor no longer uses BT.709 perceptual luminance. JPEG/sRGB channel values are approximately linearised and the three linear-light channels are combined for relative spectral signal. This avoids imposing a strong green perceptual weighting on a spectrum whose hue varies systematically with wavelength.

The calibration display applies a small five-sample display smoothing, estimates a broad robust continuum, and masks regions where the local continuum is below 3% of the usable maximum rather than dividing near-zero image signal into large false spikes. These operations affect the calibration display only; anchor coordinates remain tied to the underlying extraction samples.

Endpoint placement now has explicit PAN / ZOOM and RED / VIOLET placement modes so navigation and long-press precision gestures do not compete. Movement within a three-image-pixel radius is treated as finger tremor and does not restart the three-second lock countdown.

### Calibration display signal

Balmer placement is now based on the **Horne-style optimally extracted 1-D signal**. If an optimal column is invalid, the simple background-subtracted boxcar value is used only as a fallback so the chart remains inspectable.

The calibration chart keeps three display modes:

- **Signed signal** — preserves the broad image-derived envelope *and* negative excursions below the fitted local background.
- **Line finder** — divides reliable positive-signal regions by a broad continuum estimate to make local absorption troughs easier to position.
- **Direct** — shows the unscaled signed optimally extracted signal without display normalisation.

The displayed amplitude remains image-derived relative signal for JPEG/PNG input; it is not a radiometrically calibrated stellar flux.


### Calibration chart display modes

The wavelength-anchor chart now opens in **Signed signal** mode. The Horne-optimal extraction is no longer clipped at zero for calibration display. Negative background-subtracted samples are preserved, the y-axis uses robust signed quantiles, and a horizontal zero line marks the fitted local-background reference. The broad smoothed continuum remains overlaid in white.

The predicted Balmer neighbourhood view also preserves signed structure. It estimates a straight local baseline from the shoulders of the colour-derived search window and plots the signed residual around zero. This avoids ratio blow-ups and prevents weak negative signal from being flattened into an artificial zero plateau. A genuine absorption trough should therefore appear as a downward excursion relative to the local zero line.

**Line finder** remains available as a continuum-normalised diagnostic for reliable positive-signal regions. **Direct** exposes the unscaled signed extraction.

The displayed amplitude is deliberately described as image-derived relative signal: a processed colour JPEG cannot provide a radiometrically calibrated stellar spectral energy distribution without camera-response calibration.

## Optimal extraction and visible QA

The primary 1-D science signal now comes from a Horne-style optimal extraction rather than the earlier experimental template-amplitude correction.

The native UI deliberately exposes the intermediate products used to construct that spectrum:

1. **Rectified trace** — fitted spectrum transformed to a horizontal dispersion axis.
2. **Two-sided background** — sigma-clipped local background estimated outside the target aperture.
3. **Background subtracted** — target ribbon after local-background removal.
4. **Spatial profile weights** — current unit-normalized empirical cross-dispersion profile.
5. **Fitted target model** — optimal 1-D flux multiplied by the current spatial profile.
6. **Data − fitted model residual** — signed raw mismatch.
7. **Standardized residual z-map** — mismatch divided by the local variance proxy; this is the rejection statistic.
8. **Cleaned optimal aperture** — rejected pixels highlighted and replaced only for the QA display.
9. **Boxcar versus optimal** — simple aperture sum compared with Horne-style optimal flux.

The spectrum CSV preserves both boxcar and optimal signals. For JPEG/PNG input, the variance used for optimal weighting is an empirical relative proxy because detector gain/read noise are not available. This limitation is explicit in metadata.

Balmer wavelength anchors are placed on the optimally extracted 1-D spectrum.


### Extraction residual QA

The extraction QA sequence now distinguishes the empirical unit-normalized spatial profile from the fitted 2-D target model. The residual panel shows `background-subtracted data − fitted target model` with a zero-centred greyscale and overlays rejected pixels in orange. The current iterative outlier rule is shown explicitly in the UI (`> 5.0σ residual` by default).


### Iterative profile refinement

The optimal extractor now alternates between flux extraction, standardized-residual rejection and **re-estimation of the empirical spatial profile from surviving pixels**. The profile is therefore not held fixed while the rejection loop proceeds.

At each pass MethodMesh:

1. extracts optimal 1-D flux with the current spatial profile;
2. calculates per-pixel standardized residuals;
3. rejects at most the strongest `|z| > 5σ` outlier in each dispersion column;
4. rebuilds the empirical spatial profile from the surviving pixels;
5. re-extracts using the revised profile.

The rejection threshold is deliberately not lowered merely because coherent residual structure remains. The QA panel exposes `% |z| > 3σ`, `% |z| > 5σ`, maximum `|z|` and the number of profile-update passes so model inadequacy remains visible rather than being converted automatically into contamination.


### Fixed-scale standardized-residual QA

The standardized-residual z-map uses a **fixed −5σ to +5σ greyscale**. Zero is always mid-grey, so a quiet residual field remains visually quiet rather than being stretched to black and white. Pixels with `|z| ≥ 3σ` are highlighted in yellow, pixels with `|z| ≥ 5σ` in magenta, and pixels actually rejected by the extractor remain orange.

The signed raw residual panel remains percentile auto-scaled because its purpose is to reveal subtle spatial structure. This revision changes QA rendering only; it does not alter trace fitting, background estimation, variance proxy, spatial-profile estimation, Horne extraction or rejection decisions.


### Reliable line finder and constrained Balmer placement

The calibration **Line finder** no longer divides every pixel by a rolling-median continuum.

It now:

1. starts from the Horne-optimally extracted 1-D signal;
2. applies only light display smoothing;
3. requires a minimum extracted signal and approximate signal-to-noise before a sample can participate;
4. estimates a broad **upper-envelope continuum** using a high rolling percentile, so broad absorption troughs are not fitted downward into the continuum;
5. leaves low-information / colour-channel-handover regions blank rather than dividing by near-zero values;
6. normalizes only reliable samples.

The UI reports the proportion of the spectrum that remains reliable for line finding.

Once at least **two Balmer anchors** have been placed, MethodMesh fits a provisional wavelength solution using the currently requested polynomial order where the number of anchors permits it. Unplaced Hα/Hβ/Hγ/Hδ positions are shown as **gold provisional markers**. The active predicted line also receives a dedicated local zoomed trough view with:

- predicted position in gold;
- an independent white placement cursor;
- tap/drag positioning;
- ±1 and ±5 sample nudging;
- an explicit **Set Hα/Hβ/Hγ/Hδ here** action.

Predicted positions are calibration aids only. They are never committed automatically and do not replace manual placement on the observed local absorption minimum.


### Historical v0.2.18 manual-first pattern assistance (superseded by v0.2.22)

The following section records the earlier manual-first design for development history. It is **not the current calibration policy**; see `DESIGN.md` and the v0.2.22 section below.

The reference workflow at that stage included a conservative pattern-assistance stage inspired by established astronomical wavelength-calibration workflows that match detected spectral features against known line lists and then refine a provisional solution interactively.

At that historical stage, MethodMesh did **not** auto-identify or auto-commit Balmer lines. It instead:

1. detects broad local minima only in reliable portions of the calibration line-finder signal;
2. pairs candidate troughs with pairs from the known Hα/Hβ/Hγ/Hδ line list;
3. derives provisional **linear** wavelength hypotheses;
4. predicts all four Balmer positions for each hypothesis;
5. scores how many predicted positions are supported by nearby observed troughs;
6. deduplicates and displays up to three ranked hypotheses;
7. requires the user to explicitly choose a hypothesis before its predicted positions are drawn as gold guides;
8. uses confirmed manual anchors to supersede the pattern hypothesis as soon as two lines have been placed.

The internal trace distance increases from RED toward VIOLET, so acceptable provisional dispersion solutions must have a negative wavelength-per-pixel slope.

For an unconfirmed predicted line, MethodMesh opens a dedicated local trough view. That view uses an independent shoulder-based local continuum rather than the whole-spectrum line-finder continuum, with a draggable white cursor, ±1/±5 sample nudging and an explicit **Set line here** action.

In that historical manual-first stage, a two-anchor wavelength solution was always treated as **preliminary guidance only**. Saving a reference required at least **three explicitly confirmed Balmer lines**, even when the final model was linear. Quadratic calibration inherently requires at least three anchors. A fourth confirmed line is recommended when observable.

Pattern score is an internal ranking heuristic based on trough proximity, trough depth and number of supported Balmer positions. It is not a probability that a hypothesis is physically correct.


### Global spectral-geometry plausibility

Pattern-assisted Balmer hypotheses are no longer ranked from local trough agreement alone.

Because the operator has already selected the **RED** and **VIOLET** ends of the visible spectrum, a candidate wavelength solution must also satisfy broad global geometry:

- Hα must lie toward the RED side of the selected trace;
- Hδ must lie toward the VIOLET side;
- Hα→Hδ must occupy a substantial fraction of the selected trace;
- the implied wavelength span of the complete selected trace must remain broadly compatible with a visible-spectrum selection.

Current hard gates are deliberately broad: Hα→Hδ must span 32–98% of the selected trace, Hα must fall within the RED-side 42%, Hδ within the VIOLET-side 42%, and the implied selected wavelength span must be 260–800 nm.

Surviving hypotheses receive an additional soft geometry score based on Balmer-series span, Hα/Hδ endpoint position and implied wavelength coverage. The final ranking combines local trough support and global geometry; local coincidences can no longer rescue a grossly implausible solution.

These are calibration priors for an operator-defined RED→VIOLET visible strip, not claims that the tapped endpoints correspond to exact physical wavelengths.


### Chromatic wavelength bootstrap

For processed colour photographs, MethodMesh now uses the photographed VIOLET → RED colour progression as a **coarse wavelength prior** before Balmer-line confirmation.

The bootstrap:

1. samples RGB colour along the optimised spectrum trace;
2. converts each sample to brightness-independent RGB chromaticity;
3. down-weights dark or weakly chromatic samples;
4. aligns the observed colour sequence monotonically against an approximate ideal visible spectral-colour locus;
5. searches several plausible violet/red endpoint ranges rather than assuming the user's taps are exact physical wavelengths;
6. returns a smooth monotonic wavelength-by-pixel prior;
7. uses that prior only to predict where Hα/Hβ/Hγ/Hδ should be inspected.

The UI shows the observed photographic strip, the aligned ideal-colour strip, coarse wavelength ticks, a chromatic fit score and the fraction of the trace carrying useful colour information.

This is **not** the final scientific wavelength calibration. Camera spectral response, white balance, demosaicing, JPEG processing and atmosphere can distort apparent colour. Confirmed Balmer absorption lines supersede the chromatic prior as soon as sufficient anchors are available. Two confirmed lines define a provisional wavelength solution; at least three confirmed Balmer anchors are required before a reference can be saved.


### Optional colour-transition boundaries

The RED and VIOLET endpoints remain mandatory. The reference workflow can now also collect up to four optional visual transition boundaries from the same source photograph:

- **RED / YELLOW** — soft prior centred at about **600 ± 22 nm**;
- **YELLOW / GREEN** — about **565 ± 18 nm**;
- **GREEN / BLUE** — about **500 ± 20 nm**;
- **BLUE / VIOLET** — about **445 ± 20 nm**.

These are intentionally broad priors. A human observer is usually more reproducible at locating a hue transition than at choosing an arbitrary colour midpoint, but camera response and display processing mean the boundary must not be treated as an exact wavelength calibration point.

Boundary selection uses the same full-screen precision interaction as the RED/VIOLET endpoint picker: pan/zoom mode, press-and-hold placement, magnifier, 3-image-pixel tremor deadband, three-second lock confirmation, manual x/y coordinate editing, and independent clearing/re-placement of each boundary.

The selected boundary is projected onto the optimised spectral trace and supplied to the monotonic chromatic fit as a **soft constraint**. The continuous RGB chromaticity trajectory still contributes to the fit, so the wavelength map remains a smooth nonlinear compromise rather than a piecewise interpolation through hard landmarks.

The chromatic QA panel shows the selected boundaries on both the observed strip and the aligned ideal-colour strip, reports **Boundary agreement**, and lists each approximate wavelength prior. Two or more mutually consistent boundaries can allow the chromatic prior to drive Balmer search windows even when the RGB-only colour fit is relatively weak. Poorly agreeing landmarks remain gated off.

Confirmed Balmer absorption anchors still supersede all colour-based priors.


### Uncertainty-aware Balmer search windows

The chromatic bootstrap now produces an explicit **search window** for each unconfirmed Balmer line rather than only a single predicted pixel.

The window width depends on how strongly the line is constrained by the colour-transition landmarks:

- **Hβ 486.13 nm** is normally the tightest window because it lies between the BLUE/VIOLET and GREEN/BLUE constraints;
- **Hγ 434.05 nm** is moderately constrained near the BLUE/VIOLET boundary;
- **Hδ 410.17 nm** uses a broader violet-end extrapolation window;
- **Hα 656.28 nm** uses a broader red-end extrapolation window beyond the RED/YELLOW landmark.

The current uncertainty model is deliberately conservative. Interior lines start at roughly 10–18 nm uncertainty depending on distance from the nearest colour boundary. Endpoint extrapolation expands progressively, capped at 30 nm on the violet side and 38 nm on the red side.

The chromatic QA strip overlays all four search windows directly on the observed photographic spectrum and lists each window's wavelength width. The Balmer chart shows the same windows as translucent bands.

The active line opens automatically in a local trough picker with the uncertainty band visible inside a wider contextual view. The user still places the white cursor on the observed absorption minimum and confirms the line explicitly.

The default confirmation sequence is now:

1. **Hβ**
2. **Hγ**
3. **Hα**
4. **Hδ**

After one confirmed line, the chromatic wavelength map receives a single global offset correction from that known anchor and the remaining search windows tighten modestly. After two confirmed Balmer anchors, the chromatic prior is superseded by the actual provisional Balmer wavelength solution. Three confirmed anchors are still required before the reference can be saved.


### Unified spectrum-geometry picker

Reference-star setup now uses **one full-screen precision step** for all image-space geometry.

The same surface contains:

- required **RED** endpoint;
- required **VIOLET** endpoint;
- optional **RED/YELLOW**, **YELLOW/GREEN**, **GREEN/BLUE** and **BLUE/VIOLET** transition boundaries.

All six targets use the same pan/zoom, press-and-hold, magnifier, 3-image-pixel tremor deadband, three-second lock confirmation and manual x/y coordinate editing. Every placed target remains visible at the same time and can be selected again for revision.

The interaction auto-advances from RED → VIOLET → the optional colour boundaries, but the colour-transition boundaries can be skipped. **Done** becomes available as soon as the two mandatory endpoints form a valid trace.

There is no longer a second colour-boundary dialog or a separate follow-up window. Reopening **Edit spectrum landmarks** returns to the same unified surface with all currently selected points restored.


### Signed calibration signal

Earlier builds clipped negative Horne/boxcar calibration samples with `coerceAtLeast(0.0)`. That produced long artificial zero plateaus precisely where a weak background-subtracted spectrum crossed below its fitted local background.

Version 0.2.11 removes that clipping from the calibration path. The extraction engine itself is unchanged; only the display/calibration projection now retains the signed values already produced by the Horne extractor.

Consequences:

- whole-spectrum calibration plots preserve negative excursions;
- robust signed y-scaling always includes the zero-background line;
- the local Balmer picker uses a shoulder-fitted additive baseline and plots signed residuals instead of dividing by a positive continuum;
- fallback boxcar samples remain signed if an optimal-extraction column is invalid;
- no signed value is silently replaced by zero merely for display.

The photographic colour strip and chromatic wavelength bootstrap are unchanged by this revision.


### Reference-calibration QA

Version 0.2.12 adds an explicit QA layer around the confirmed Balmer wavelength solution without changing the Horne extraction or chromatic-bootstrap mathematics.

#### Endpoint guard zones

The RED and VIOLET points continue to define the full trace geometry and the limits used by the chromatic bootstrap. They are **not** redefined as brightest-colour points.

For Balmer detection and local continuum fitting, MethodMesh now treats a short region at each end of the extracted trace as an endpoint guard zone. The guard is approximately 4% of the sampled trace at each end, bounded to 12–40 samples.

Guard-zone samples:

- remain visible in the signed spectrum;
- remain part of the stored trace geometry;
- remain available to the chromatic colour progression;
- are shaded in Balmer and committed-QA plots;
- are excluded from line-finder reliability and automatic Balmer-pattern trough detection;
- are excluded from the shoulder samples used by the local Balmer refinement view;
- cannot be selected by the Balmer chart's tap/drag/nudge controls.

This prevents detector/camera-response falloff or a hard photographic spectrum edge from masquerading as a Balmer absorption trough.

#### Per-anchor residuals

Once a wavelength model can be fitted, every confirmed Balmer anchor reports

`Δλ = fitted wavelength − known Balmer wavelength`.

Residuals are displayed beside the anchor in the working calibration view and again in the committed QA view.

- `|Δλ| ≤ 2 nm`: normal QA display;
- `2 nm < |Δλ| ≤ 3 nm`: **WARN**;
- `|Δλ| > 3 nm`: **OUTLIER**.

These thresholds are QA prompts, not automatic rejection rules.

#### Linear versus quadratic fit comparison

Whenever enough anchors are available, MethodMesh calculates **both** linear and quadratic RMS and displays them side by side. The user's selected model remains explicit and MethodMesh does **not** switch models automatically.

A three-anchor quadratic can fit almost exactly by construction, so the UI explicitly recommends a fourth Balmer line for independent validation. With four anchors, the quadratic solution is overdetermined and its residuals become a meaningful QA check.

#### Committed calibrated-spectrum view

Commit no longer collapses the reference workflow to a small summary only. The same-screen committed state retains a read-only calibrated spectrum with:

- the signed Horne-extracted signal;
- wavelength on the horizontal axis;
- all confirmed Balmer anchors overlaid as coloured vertical lines;
- RED/VIOLET endpoint guard zones shaded;
- linear RMS and quadratic RMS;
- the selected-model residual and QA status for every anchor.

The stored reference still contains the selected model coefficients and its selected RMS. Additional fit-comparison/max-residual information is written into the reference provenance notes and can also be recomputed from the stored anchors.


### Balmer search-window lock

When a Balmer line has an explicit predicted gold search window, the local refinement cursor is now **locked to that window by default**.

While locked:

- tap and drag cannot move the cursor outside the gold window;
- ±1 and ±5 nudges stop at the window boundaries;
- the Set action therefore cannot silently accept a feature outside the current prior.

An explicit **Expand search** control unlocks the cursor to the wider local context, still bounded by the RED/VIOLET endpoint guard. Expanded mode is visibly labelled and the confirmation button is marked as an expanded placement. **Relock to prediction** returns to the original gold search window and clamps the cursor back inside it.

This is an interaction safeguard, not an automatic scientific rejection rule: the user can still override the chromatic prior deliberately when the observed spectrum justifies it.


### Automatic colour-boundary suggestions

Version 0.2.19 adds the same optional machine-vision colour-boundary suggestion used by the target analyser. After RED and VIOLET are placed, **Auto-detect colour boundaries** estimates R/Y, Y/G, G/B and B/V from the photographed RGB progression using monotonic alignment to the approximate visible spectral-colour locus. Suggestions remain soft priors and must remain manually editable; they do not replace Balmer-line wavelength calibration.


### Default colour-boundary proposal

Version 0.2.19 changes the existing colour-boundary detector from an optional button-only aid into the default starting proposal. After RED and VIOLET are both defined, the shared picker automatically proposes R/Y, Y/G, G/B and B/V once for that endpoint geometry if no boundaries already exist. All four proposals remain soft priors and are fully editable; Balmer anchors remain the physical wavelength calibration.


### v0.2.20 YELLOW/GREEN auto-boundary refinement

Automatic Y/G placement now gives direct observed hue substantially more weight than the global RGB-to-wavelength DTW, which could place Y/G too far into photographic yellow. Manual editing remains authoritative.


## v0.2.21 complete reference return package

Reference Commit now freezes both the reusable calibration object and a complete scientific return package. The ZIP contains the original source image, an annotated reference image with Balmer anchors, calibrated reference-spectrum CSV, calibration-anchor CSV, reusable `reference.json`, provenance JSON and a SHA-256 manifest. All principal artefacts also have independent canonical URI/hash return fields, so ODK and protocol callers can consume either the individual files or the complete ZIP.

The reference provenance explicitly records that instrument-response correction has not been applied and that the saved wavelength solution itself does not require zero order as a stored anchor; the current calibration remains based on confirmed spectral features and the fitted pixel-distance → wavelength model.

## v0.2.22 automatic consensus wavelength calibration

Manual Balmer identification is no longer the primary path. After extraction and the chromatic bootstrap, MethodMesh now builds a permissive set of candidate absorption troughs across several smoothing scales and searches many possible assignments of those troughs to the established Hδ/Hγ/Hβ/Hα wavelengths.

The solver is deliberately explainable rather than learned. Every pair of candidate troughs can seed a linear pixel-distance → wavelength hypothesis. Each hypothesis is then tested against the remaining Balmer lines. Ranking gives priority to solutions supported by the largest number of independent lines and then applies a parsimony cost based on wavelength residuals, unexplained catalogue lines, the two-parameter linear model, and a deliberately weak chromatic-prior disagreement term. An exact two-point fit is therefore never allowed to outrank a coherent three- or four-line solution merely because two points define a line exactly.

The existing colour geometry is used as a **weak prior**, not as a wavelength measurement. The continuous chromatic bootstrap already incorporates RED/VIOLET orientation plus optional R/Y, Y/G, G/B and B/V transition landmarks. The automatic solver may use that approximate wavelength trajectory to break aliases, but the colour prior cannot manufacture a Balmer match or compensate for absent spectral evidence.

The leading automatic solution reports supported-line count, linear RMS, evidence score, weak-prior RMS, separation from the next solution, every proposed line↔trough match, and several ranked alternatives. A solution needs at least three independently supported Balmer lines before the UI enables **Use automatic solution**. Accepting the proposal writes ordinary `CalibrationAnchor` objects, so the established linear/quadratic wavelength-model QA, residuals, committed plots, persistence and downstream reference reuse remain unchanged.

Manual Balmer placement remains visible as **Manual review / fallback**. It can be used to inspect, fine-tune or replace an automatic proposal. Provenance records whether the final anchors were `automatic_consensus`, `automatic_consensus_reviewed`, or `manual`.

The implementation is conceptually informed by published/open wavelength-calibration strategies that search line-pattern hypotheses (notably RANSAC-assisted approaches such as RASCAL), but MethodMesh contains an independent Kotlin implementation and does not bundle or copy a third-party calibration runtime.


## v0.2.23 targeted missing-line recovery and colour-picker parity

The reference capability now guarantees the same automatic colour-boundary proposal path used by target analysis. Once RED and VIOLET are set, the shared picker proposes R/Y, Y/G, G/B and B/V boundaries; pressing Done also performs the same detector synchronously if the asynchronous proposal has not completed, so a fast user cannot accidentally bypass the automatic proposal. Manual editing remains available and the boundaries remain weak wavelength priors.

The automatic Balmer solver now has a conservative targeted-completion pass. It first runs the unchanged global candidate-pattern search. Only a hypothesis with **at least three direct first-pass Balmer matches** may predict the position of a missing catalogue line. MethodMesh then inspects that predicted neighbourhood in the observed 1-D residual at several smoothing scales and accepts a recovered line only when a real, locally prominent absorption trough persists across scales. Recovered lines are labelled `targeted_recovery`; they do not count toward the three-direct-line admission rule.

This change is deliberately not Vega-specific. No fixed Hβ pixel location or Seestar dispersion is encoded. The missing-line neighbourhood is computed by inverting the currently fitted affine wavelength solution, so the same logic moves with crop, orientation and dispersion.


## v0.2.24 automatic-consensus propagation

The first real-device v0.2.23 regression showed a three-line Hα/Hγ/Hδ consensus whose linear model correctly predicted the missing Hβ neighbourhood, while the manual review panel still displayed the older chromatic prediction. v0.2.24 fixes the architectural priority: a ≥3-direct-line automatic consensus now drives missing-line review positions immediately, even before the operator accepts the proposal.

Targeted recovery is also continuum-relative. Within the model-predicted neighbourhood MethodMesh fits a straight local baseline from the two shoulders and searches for a multi-scale downward excursion relative to that baseline. A Balmer trough therefore does not need the globally signed residual itself to be below zero. No Vega-specific pixel position, Seestar-specific dispersion, or Hβ-specific coordinate is encoded.

## v0.2.25 affine-first calibration safety

Automatic identification now has an explicit affine-consensus gate. Three-or-more-line proposals are refitted under one linear wavelength solution and rejected when that pattern is internally inconsistent. The existing colour bootstrap contributes only a broad scale/orientation sanity envelope, which prevents compact high-dispersion aliases without turning colour boundaries into exact wavelength anchors. Missing-line recovery preserves catalogue identity and must improve the same affine solution.

Quadratic calibration is now a downstream refinement: it unlocks only after four anchors already form a plausible linear calibration, and it cannot be used to rescue a bad line assignment. Commit is blocked when the anchor set fails the linear identity check. Linear is the default model.
