# Star spectrum analyser

**Module:** `star_spectrum`  
**Capability:** `astronomy.spectrum.analyse`  
**Version:** `0.2.25`  
**Maturity:** `DEVELOPMENT`  
**Connectivity:** `OFFLINE`

## Purpose

`astronomy.spectrum.analyse` turns a raster astronomical image containing a slitless stellar spectrum into a traced, background-corrected and contamination-aware one-dimensional spectrum.

The operator selects only an approximate line over the dispersed spectrum. MethodMesh then searches within a ribbon around that line, optimises the spatial trace, estimates local background from sidebands, models the target's cross-dispersion profile, identifies likely point-source/field-star contamination, extracts corrected intensity, optionally applies a previously saved wavelength solution, detects emission and/or absorption features, and exports the result.

The capability is intentionally an **analysis instrument**, not an automatic stellar-classification or elemental-identification system. Saved references may now originate from the automatic Balmer-consensus reference workflow; target analysis consumes the persisted canonical wavelength model and does not need to know whether its anchors were automatic, reviewed, or manual. See `DESIGN.md` for the current module-wide calibration architecture.

## Canonical surfaces

The capability contract is declared once in `StarSpectrumAnalyseMethod.kt` and `StarSpectrumModule.kt`.

It remains independently addressable through:

- direct native capability use;
- the generic MethodMesh dashboard presence;
- presets;
- protocols and protocol pipes;
- schedules/widgets when authored to show this interactive capability;
- ODK/XLSForm through the normal MethodMesh Android-intent roundtrip.

There is no dashboard-only implementation and no shared-shell special case.

## Native workflow

The native screen is one continuous tool surface.

1. Choose a spectrum image, or accept an image URI supplied by the caller.
2. Select an optional saved wavelength reference.
3. Pinch/zoom and pan the image.
4. Tap **Select trace** and tap the origin and far end of the dispersed spectrum.
5. MethodMesh displays the trace-search ribbon.
6. Tap **Analyse**.
7. The same screen displays the optimised trace, likely contaminants, extracted spectrum, quality metrics and detected features.
8. Adjust extraction/detection settings and re-run while the result is still working state.
9. Tap **Commit analysis** to freeze the canonical payload.
10. In committed state, Share, Save, copy the feature list, optionally include full JSON, then Done.

The screen deliberately does not navigate to a generic result page after analysis or Commit.

## Image interaction

The image surface supports:

- pinch zoom up to 8×;
- pan by drag when not selecting a trace;
- a dedicated select/reselect mode so one-finger trace placement is unambiguous;
- reset view;
- display of the user's original line;
- display of the trace-search ribbon;
- display of the optimised trace after analysis;
- orange spatial-contamination markers;
- yellow detected-feature markers.

Trace coordinates are stored in source-image pixel coordinates, not screen coordinates, so zoom/pan do not alter the scientific geometry.

## 2-D extraction model

The analyser now follows an established Horne-style optimal-extraction sequence rather than the earlier experimental template-amplitude method.

For each observation it:

1. uses the RED → VIOLET endpoints as an initial trace geometry;
2. refines that geometry with a robust quadratic fit to cross-dispersion centroids;
3. rectifies a 2-D ribbon around the fitted trace so dispersion is horizontal;
4. fits a sigma-clipped **two-sided local background** at each dispersion sample;
5. preserves a simple background-subtracted **boxcar extraction**;
6. estimates a smoothly varying empirical cross-dispersion spatial profile in dispersion bins;
7. performs iterative variance-weighted **Horne-style optimal extraction**;
8. rejects pixels that deviate strongly from the fitted target spatial profile and recomputes the optimal flux.

This is the standard conceptual architecture used by astronomical extraction packages such as Astropy `specreduce`. For processed JPEG/PNG input, MethodMesh must use an empirical relative variance proxy because detector gain/read-noise data are unavailable.

The field-star failure mode is therefore addressed spatially before collapse to 1-D: a crossing source that does not follow the target's cross-dispersion profile can be rejected as a deviant pixel/cluster rather than being silently absorbed into the target spectrum.

## Intensity semantics

The exported signal is **relative detector/image intensity**.

JPEG/PNG/WebP pixel values must not be described as calibrated photon counts. Compression, gamma/tone curves, demosaicing, white balance and other camera processing can alter proportionality between incident photons and stored pixel values.

A future FITS/raw-linear path can support stronger photometric semantics. The v0.2 interface therefore labels its amplitude generically as signal/intensity and records this limitation in `spectrum_intensity_semantics` and metadata JSON.

## Background and contamination outputs

Every row of the spectrum CSV retains:

- raw aperture signal;
- estimated background signal;
- contamination-corrected target signal;
- fitted continuum;
- continuum residual;
- local noise estimate;
- local S/N;
- contamination fraction;
- contamination probability;
- validity flag.

Downstream R analysis can therefore choose whether to exclude, down-weight or model contaminated points rather than receiving only a cosmetically cleaned curve.

## Wavelength calibration

A run may use a reference created by `astronomy.spectrum.reference.create`.

The stored reference contains the user's own pixel-to-wavelength polynomial, accepted calibration anchors, source image dimensions, calibration RMS and anchor-source provenance. References created by v0.2.22 normally begin with automatic Balmer-pattern consensus and may then be manually reviewed; target application uses the committed model identically in either case. The target run applies the stored solution after accounting for image-dimension scaling and the per-observation `registration_offset_px`.

If the target/reference aspect ratio differs materially, MethodMesh reduces calibration confidence and exposes a warning rather than silently treating the reference as exact.

A blank `reference_id` is valid. The spectrum then remains in distance/pixel coordinates.

## Feature detection

Feature detection is performed on the continuum-subtracted spectrum.

- continuum: rolling robust median;
- local spectral noise: rolling MAD × 1.4826, combined conservatively with extraction-sideband noise;
- candidate type: local emission maxima and/or absorption minima;
- threshold: configurable local sigma threshold;
- stability: candidate persistence across `threshold - 0.5σ`, `threshold`, and `threshold + 0.5σ`;
- confidence: weighted combination of S/N, prominence, multi-threshold stability and low contamination.

`confidence` is **not** a literal ROC probability and is not an elemental-identification probability. It is a bounded feature-stability/evidence score.

No automatic atomic/molecular identification is performed in v0.2.

## Settings / runtime inputs

| Key | Type | Default | Meaning |
|---|---|---:|---|
| `source_image_uri` | text | blank | Optional caller-supplied image URI. Blank opens the native picker. |
| `reference_id` | text | blank | Saved wavelength reference; blank means pixel coordinates. |
| `registration_offset_px` | float | 0 | Per-observation shift applied to the saved reference solution. |
| `ribbon_half_width_px` | int | 28 | Half-width of trace search ribbon. |
| `aperture_half_width_px` | int | 5 | Half-width of central extraction aperture. |
| `background_gap_px` | int | 4 | Gap before background sidebands. |
| `continuum_window` | int | 51 | Robust continuum window in samples. |
| `detection_sigma` | float | 3.5 | Feature significance threshold. |
| `detection_mode` | choice | `both` | `both`, `emission`, or `absorption`. |

Preset-fixed settings are not presented as editable runtime configuration. Operational image manipulation and trace placement remain part of the interactive measurement.

## Canonical outputs

### Primary beef

| Field | Normally shown | Description |
|---|---:|---|
| `spectrum_annotated_image_uri` | yes | Marked-up source image with user trace, optimised trace, contamination markers and detected feature markers. |
| `spectrum_data_uri` | yes | Analysis-ready CSV containing one row per spectral sample. |
| `spectrum_features_uri` | yes | CSV containing one row per detected feature. |
| `spectrum_features_text` | yes | Human-readable detected-feature list. |
| `spectrum_feature_count` | yes | Number of detected features. |

### Contractually available detail

| Field | Description |
|---|---|
| `spectrum_source_image_uri` | Conditional source attachment. Populated only when MethodMesh acquired the source image rather than receiving it from the caller. |
| `spectrum_source_image_sha256` | SHA-256 of the conditional returned source attachment. |
| `spectrum_metadata_uri` | Domain metadata JSON artefact for the analysis. |
| `spectrum_provenance_uri` | Commit-time provenance JSON describing method/version, extraction/calibration settings, source hash, instrument-response semantics and colour-landmark inputs. |
| `spectrum_manifest_uri` | JSON manifest listing every file in the scientific bundle with role, MIME type, byte size and SHA-256. |
| `spectrum_bundle_uri` | ZIP containing the complete scientific package: source image, annotated image, spectrum CSV, feature CSV/JSON, metadata, provenance and manifest. |
| `spectrum_annotated_image_sha256` | SHA-256 of the annotated PNG bytes. |
| `spectrum_data_sha256` | SHA-256 of the spectrum CSV bytes. |
| `spectrum_features_sha256` | SHA-256 of the feature CSV bytes. |
| `spectrum_metadata_sha256` | SHA-256 of the domain metadata JSON. |
| `spectrum_provenance_sha256` | SHA-256 of the commit-time provenance JSON. |
| `spectrum_manifest_sha256` | SHA-256 of the bundle manifest. |
| `spectrum_bundle_sha256` | SHA-256 of the complete ZIP bundle. |
| `spectrum_features_json` | Structured detected-feature array. |
| `spectrum_reference_id` | Saved reference ID used for calibration, if any. |
| `spectrum_reference_name` | Human name of reference used. |
| `spectrum_calibration_rms_nm` | Stored reference calibration RMS. |
| `spectrum_calibration_confidence` | Target/reference geometry + RMS confidence score. |
| `spectrum_calibration_warning` | Geometry/rescaling warning if applicable. |
| `spectrum_wavelength_min_nm` / `spectrum_wavelength_max_nm` | Calibrated wavelength range; blank if uncalibrated. |
| `spectrum_trace_quality` | Bounded quality score for trace geometry/signal support. |
| `spectrum_trace_mean_correction_px` | Mean distance between user estimate and refined trace. |
| `spectrum_mean_snr` | Mean local residual S/N diagnostic. |
| `spectrum_contaminated_fraction` | Fraction of spectral samples with contamination probability ≥ 0.5. |
| `spectrum_source_width_px` / `spectrum_source_height_px` | Decoded analysis dimensions. |
| `spectrum_intensity_semantics` | Explicit instrumental relative-intensity caveat. |
| `spectrum_instrument_response_corrected` | `false` for the current raster/JPEG workflow. |
| `spectrum_continuum_semantics` | Explicit statement that the broad continuum includes the source SED convolved with camera/telescope/grating response. |
| `spectrum_metadata_json` | Module-owned structured metadata. |
| `spectrum_status` | `succeeded` / `failed`. |
| `spectrum_error` | Failure diagnostic. |

Shared transport adds `methodmesh_full_json` when FULL output is requested.

## CSV schema

`spectrum_data_uri` contains:

```text
index,distance_px,trace_x_px,trace_y_px,wavelength_nm,
raw_signal,background_signal,boxcar_signal,optimal_signal,optimal_variance,
continuum,residual,local_noise,local_snr,rejected_fraction,
contamination_probability,valid,is_feature,feature_id
```

`spectrum_features_uri` contains:

```text
feature_id,index,distance_px,wavelength_nm,type,signal,continuum,
amplitude,prominence,fwhm_px,fwhm_nm,snr,stability,
contamination_probability,confidence
```

Both are ordinary UTF-8 CSVs designed for direct import into R/Python.

## ODK Integration Card

### Method

`astronomy.spectrum.analyse`

### Canonical call

The supplied showcase intentionally asks MethodMesh to acquire the source file interactively:

```text
com.example.methodmesh.EXECUTE_METHOD(
  method_id='astronomy.spectrum.analyse',
  input_payload_mode='FULL',
  return_mode='flat'
)
```

A user-authored form may instead supply any declared setting through the normal `input_<key>` mapping.

### Return semantics

The showcase captures:

- `methodmesh_status`;
- `spectrum_status`;
- `spectrum_source_image_uri` as an image attachment because MethodMesh acquires it in this showcase;
- `spectrum_annotated_image_uri` as an image attachment;
- `spectrum_data_uri` as a file attachment;
- `spectrum_features_uri` as a file attachment;
- `spectrum_metadata_uri` as a file attachment;
- `spectrum_provenance_uri` and `spectrum_manifest_uri` as JSON file attachments;
- `spectrum_bundle_uri` as a ZIP file attachment containing the complete scientific package, including a copy of the source image even when the caller originally supplied it;
- feature/calibration/quality scalar returns and SHA-256 values;
- `methodmesh_full_json`.

If ODK supplied the source image itself, `spectrum_source_image_uri` remains blank so MethodMesh does not return a gratuitous duplicate.

### Workbook

`docs/example_odk_showcase_astronomy_spectrum_analyse.xlsx`

The workbook contains exactly one MethodMesh invocation, uses canonical unprefixed return fields and does not use `methodmesh_return_namespace`.

## Persistence

Analysis runs are transient by default.

Generated source-copy/annotated/CSV/JSON/ZIP files live in MethodMesh cache as transport sources. Commit creates a manifest-addressed scientific bundle with hashes for every member. Explicit Save persists the individual artefacts plus the ZIP to Downloads; Share uses MethodMesh's canonical typed-attachment transport. ODK can receive the individual files and the ZIP through the same return contract.

The analysis capability does not silently create its own results database.

## Offline / privacy

Fully offline. No astronomical image, pixel data, spectrum, reference name or derived feature is sent to a network service.

## Dependencies

Uses Android/Jetpack components already present in MethodMesh plus Kotlin/Java standard APIs:

- Jetpack Compose / Material 3;
- Android raster bitmap APIs;
- Android Storage Access Framework;
- existing MethodMesh `FileProvider`, `Digests`, canonical runtime and output/export infrastructure.

No new Maven library is introduced by v0.2.

## v0.2 limitations

- Raster image import only; FITS/raw-linear import is future work.
- Calibration reuse assumes substantially stable camera/grating geometry and a repeatable trace origin; `registration_offset_px` handles simple longitudinal shifts, not arbitrary affine/distortion changes.
- Optimal extraction rejects profile-inconsistent pixels but is not a full multi-source PSF deblender.
- No atmospheric extinction correction, instrumental response correction, heliocentric/barycentric velocity correction, redshift fitting or line identification.
- Feature confidence is an internal evidence/stability score rather than a calibrated posterior probability.

See `VALIDATION.md` for current validation status.


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


### Reference calibration selector

The analyser no longer renders every saved reference as a horizontal chip. That made profiles with the same display name appear as duplicate buttons and did not provide enough information to distinguish them.

Version 0.2.15 exposes one **Reference calibration** control. Tapping it opens an explicit selector containing:

- **Uncalibrated** / pixel-axis mode;
- every saved spectrum reference;
- reference name;
- star name and spectral type;
- linear/quadratic model;
- confirmed Balmer anchor count;
- RMS;
- save timestamp when available;
- a short reference-ID suffix to distinguish otherwise identically named profiles;
- current selected state.

The selector is shown even when only one saved reference exists. Profile choice is never automatic. When the `reference_id` setting is fixed by a preset/protocol/ODK invocation, the chooser remains hidden and the fixed reference is displayed instead.


### Target/reference preparation parity

Version 0.2.17 aligns the target analyser's first seven native stages with Create spectrum reference: target observation; spectrum colour geometry; extraction; extraction QA; extracted photographic appearance; chromatic wavelength bootstrap; and extraction-only 1-D trace. The target now uses the same full-screen RED/VIOLET plus optional four colour-transition boundary picker. Only after those common preparation stages does the workflow diverge into saved-reference registration and feature detection.

Changing the selected reference, registration offset or feature-detection controls retains the inspected extraction but invalidates stale derived output until the user explicitly reapplies/refreshes it. This pass deliberately does not claim to fix the same-image reference orientation/registration bug; the added target chromatic geometry is preparation for that next correction.


### Feature-search QA zones

Version 0.2.19 applies the same endpoint-edge discipline used during Balmer calibration to the general spectral-feature detector.

- The first and last approximately 4% of trace samples (bounded to 12–40 samples) remain visible and exported but are excluded from automatic emission/absorption feature detection.
- With a saved wavelength reference, the region between the outer confirmed Balmer anchors is labelled `anchor_bracketed`.
- Calibrated samples beyond the outer confirmed anchors are labelled `extrapolated`; detections remain possible there but their confidence is multiplied by 0.72.
- Endpoint-guard samples are labelled `endpoint_guard` in the full spectrum CSV and can never become automatic feature calls.
- Uncalibrated runs use `uncalibrated`.

The live spectrum chart shades endpoint guards and the extrapolated wavelength regions. Feature rows and JSON/CSV exports carry the search-zone label.

### Automatic colour-boundary suggestions

The shared RED/VIOLET + colour-boundary picker now provides **Auto-detect colour boundaries** after RED and VIOLET are set. It is available in both Create spectrum reference and Star spectrum analyser.

The detector samples RGB chromaticity along the selected spectrum corridor, lightly smooths the colour trajectory, and monotonically aligns it to the same approximate visible spectral-colour locus already used by the chromatic wavelength bootstrap. It then proposes RED/YELLOW, YELLOW/GREEN, GREEN/BLUE and BLUE/VIOLET at the nearest matched positions to their existing soft wavelength priors.

These are **suggestions, not accepted calibration points**. The picker reports chromatic-fit/useful-colour percentages and leaves every suggested marker visible and manually revisable before Done.


### Multi-scale spectral feature detection

Version 0.2.19 keeps the existing narrow residual-extremum detector and adds a second detector for coherent broad structure. The user-facing sigma threshold is **not** globally lowered.

The broad detector evaluates several nominal core widths (7, 13, 21, 33 and 49 samples where the trace is long enough). At each width it compares the mean target signal in a central core with symmetric left/right shoulder windows. A linear local slope therefore largely cancels, while a broad absorption trough or emission hump produces a signed matched response.

For each scale:

- only endpoint-safe, valid, non-strongly-contaminated samples are eligible;
- the response uncertainty combines propagated local extraction noise with a robust local MAD of the matched response;
- the same base sigma threshold used by the narrow detector is applied;
- threshold stability is checked at `sigma-0.5`, `sigma`, and `sigma+0.5`;
- ordinary candidates require support at more than one physical scale. A single-scale candidate is retained only when it is stable across all three threshold probes and exceeds the base threshold by at least 0.5σ.

Candidates representing the same physical feature across scales are clustered. Broad and narrow detections that overlap are merged into a single feature with `detection_method = narrow_and_broad`; otherwise the method is `narrow` or `broad_multiscale`.

Each feature now carries:

- `detection_method`;
- `detection_scale_px` for the strongest scale;
- `supporting_scales_px`;
- `fwhm_px` and, when calibrated, `fwhm_nm`;
- existing S/N, stability, contamination, confidence and feature-search-zone fields.

These fields are shown in native feature rows and exported in JSON, the feature CSV, and the full spectrum CSV. Endpoint guards still cannot generate features, and extrapolated calibrated features retain the existing confidence penalty.

The purpose is to recover broad photographic absorption/emission structure without re-admitting the RED/VIOLET edge cliffs that were eliminated in v0.2.18.

### Default machine-vision colour-boundary proposal

Once RED and VIOLET have both been placed in the shared geometry picker, MethodMesh now automatically runs the existing monotonic chromaticity-to-visible-spectrum alignment **once** for that endpoint geometry when no colour boundaries are already present.

The resulting R/Y, Y/G, G/B and B/V points are proposals only. They are immediately shown on the same full-screen image and can be selected, fine-positioned, manually edited or replaced. **Re-detect colour boundaries** remains available explicitly. If the automatic fit is not stable enough, the picker reports that and falls back to manual boundary placement.

Changing RED or VIOLET creates a new endpoint geometry and permits a fresh automatic proposal. Existing manually reviewed boundaries are never silently overwritten merely by opening the picker.


### v0.2.20 extraction NaN guard and Y/G colour-boundary refinement

The v0.2.19 broad multi-scale detector could propagate `NaN` through local prominence when a broad-response neighbourhood contained non-finite edge or invalid samples. Android `JSONObject` rejects non-finite numbers, producing `Forbidden numeric value: NaN` during extraction/export. v0.2.20 excludes non-finite neighbours from prominence, drops any residual non-finite broad candidate, and hardens feature JSON so a non-finite diagnostic field is omitted rather than aborting extraction.

Automatic YELLOW/GREEN placement now uses the observed photographic hue crossover (centred on ~90° between canonical yellow and green) as the dominant machine-vision cue, with the wavelength prior retained as a loose constraint. R/Y, G/B and B/V retain the previous chromatic-DTW placement.


## v0.2.21 return package and instrumental-spectrum semantics

Version 0.2.21 freezes the scientific algorithm while strengthening transport, provenance and inspection. Commit now creates a complete self-describing ZIP package. The package contains the original source image, annotated spectrum image, full per-sample spectrum CSV, feature CSV, feature JSON, domain metadata JSON, provenance JSON and a manifest with MIME types, sizes and SHA-256 hashes. The ZIP itself also has a canonical URI and SHA-256 return field.

Native **Share** uses the shared MethodMesh `ResultShare` transport with typed attachments. Native **Save** persists the individual artefacts and ZIP through the shared output repository. The same canonical URI fields are available to ODK/presets/protocols; `methodmesh_full_json` remains the optional shared FULL payload rather than a replacement for domain files.

The UI now offers a non-destructive low-pass display view (`Raw`, 5, 11, 21 or 41 sample windows). This changes only the plotted curve. Feature detection, calibration and exported CSV values remain based on the canonical unsmoothed Horne extraction.

The metadata/provenance now explicitly labels the result as an **instrumental spectrum**: background subtraction and optimal extraction are applied, but no camera/grating/telescope response correction or absolute flux calibration is performed. Consequently the broad continuum is the stellar energy distribution convolved with the complete instrument response and must not be interpreted as a response-corrected stellar continuum.


## v0.2.23 shared colour-picker reliability

The shared RED/VIOLET geometry picker now repeats automatic colour-boundary detection synchronously when Done is pressed if no asynchronous proposal has yet been returned. This preserves analyser/reference parity and prevents fast confirmation from accidentally returning endpoints without the normal R/Y, Y/G, G/B and B/V proposals. Scientific target extraction/detection logic is otherwise unchanged in v0.2.23.


## v0.2.24 shared calibration guidance

The analyser science path is unchanged. Shared calibration UI now understands automatic-consensus guide anchors so that, where this UI is reused, a ≥3-line fitted wavelength solution takes priority over the earlier chromatic bootstrap for predicted line positions.

## v0.2.25 shared reference-calibration safety

The analyser science path is unchanged. The shared module version advances because reference creation now enforces affine-first Balmer identity, broad colour-geometry alias rejection and quadratic-as-refinement-only semantics. Existing saved references remain readable.
