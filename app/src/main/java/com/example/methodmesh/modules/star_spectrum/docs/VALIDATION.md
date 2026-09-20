# Validation and self-review — Star spectrum analyser v0.2.25

Date: 2026-09-20

Status: **Development**. This handoff remains a Development module and must not be presented as a validated scientific pipeline.

`DESIGN.md` is the current design authority. Versioned sections below preserve historical implementation checks; older manual-first calibration notes are not the current v0.2.25 policy.

## MethodMesh contract review

- [x] One module entry point: `StarSpectrumModule`.
- [x] Two independently callable capabilities remain explicit:
  - `astronomy.spectrum.analyse`
  - `astronomy.spectrum.reference.create`
- [x] Both capabilities remain exposed through module-owned methods, screens, settings and RIL bindings.
- [x] Working results remain editable until Commit.
- [x] Commit remains the finalisation boundary.
- [x] Analysis exports annotated PNG, spectrum CSV, detected-feature CSV and metadata JSON.
- [x] Canonical ODK showcases remain single-invocation examples using FULL payload.
- [x] Reference persistence remains local/offline and occurs only at explicit reference Commit.

## Extraction-method revision

The original experimental template-amplitude extractor has been replaced as the primary science path.

The current engine follows the standard astronomical sequence used by Horne-style optimal-extraction packages:

1. refine the user-provided RED → VIOLET geometry with a robust quadratic trace fit;
2. rectify a 2-D ribbon around that fitted trace;
3. estimate a sigma-clipped two-sided local background independently at each dispersion sample;
4. retain a simple background-subtracted boxcar extraction;
5. estimate an empirical cross-dispersion spatial profile in dispersion bins and interpolate it smoothly;
6. run iterative variance-weighted Horne-style optimal extraction;
7. reject the strongest spatial-profile outlier in each column per iteration when it exceeds the residual threshold;
8. preserve rejection fractions, optimal variance proxy, boxcar flux and optimal flux in outputs.

For processed JPEG/PNG input, the uncertainty image cannot be detector-calibrated. The engine therefore uses an explicitly documented empirical relative variance proxy. The resulting spectrum is image-derived relative signal, not calibrated photon flux.

## Visual quality audit

Both native capabilities now expose the intermediate 2-D products after extraction:

1. rectified trace;
2. two-sided background model;
3. background-subtracted ribbon;
4. unit-normalized empirical spatial-profile weights;
5. fitted 2-D target model (`optimal flux × spatial profile`);
6. signed `data − fitted model` residual with rejected pixels overlaid;
7. cleaned/rejected-pixel view;
8. boxcar-versus-optimal 1-D comparison.

This is intended to make extraction failure visible rather than hiding all processing behind one final curve.

## Local implementation checks completed in this handoff

- [x] `SpectrumEngine.kt` and `SpectrumModels.kt` compile with a minimal Kotlin/JVM stub harness for Android Bitmap and JSON classes.
- [x] New extraction diagnostics are wired into the analysis model.
- [x] CSV export now preserves boxcar signal, optimal signal, optimal variance and per-column rejection fraction.
- [x] Metadata records extraction method, trace-fit RMS, rejected-pixel fraction, iteration count and variance semantics.
- [x] Reference calibration uses the Horne-optimally extracted 1-D spectrum as the primary Balmer placement surface.
- [x] Existing RED/VIOLET endpoint picker and horizontal photographic spectrum strip are retained.

## Required Android admission checks

A full Android Gradle build cannot be completed in this isolated handoff environment. Before merge or scientific use:

1. replace the module folder and run `./gradlew :app:compileDebugKotlin :app:assembleDebug`;
2. exercise reference extraction on the supplied stellar JPEG and inspect every QA stage;
3. confirm the rectified target is horizontal and centred;
4. confirm side-band stars are absent from the background model after clipping;
5. confirm rejected pixels correspond to visibly inconsistent crossings/hot pixels rather than the target core;
6. compare boxcar and optimal traces;
7. verify RED/VIOLET pan/zoom and three-second lock behaviour is unchanged;
8. run automatic Balmer-consensus calibration first; inspect supported matches, residuals, alternatives and ambiguity, then exercise manual review/fallback;
9. compare the accepted wavelength calibration against BASS or another trusted spectroscopy workflow;
10. repeat with an uncompressed/raw source before judging absolute spectral shape;
11. validate both XLSForms and ODK round trips;
12. only then consider promotion beyond Development.

## Deliberate boundaries

- no FITS/raw-linear decoder yet;
- no detector gain/read-noise calibration for JPEG/PNG;
- no automatic elemental/species identification;
- no atmospheric or instrument-response correction;
- no HST-style forward contamination model requiring direct imaging and instrument calibration files;
- no claim that processed raster intensity is calibrated stellar flux;
- no network dependency.

- [x] QA UI exposes the iterative residual rejection threshold used by the extractor.

## Iterative profile-refinement revision

- [x] Empirical spatial profile is re-estimated from surviving pixels during the Horne loop.
- [x] Rejection remains at the declared 5σ threshold; it is not loosened to force a cleaner-looking model.
- [x] QA exposes signed residual and standardized residual z-map separately.
- [x] QA reports fractions above 3σ and 5σ, maximum absolute standardized residual, and profile-update count.
- [x] Trace fitting, rectification and two-sided background logic were intentionally left unchanged in this revision.


## Local compile check for v0.2.18

- [x] `SpectrumModels.kt` + `SpectrumEngine.kt` compile successfully with Kotlin/JVM 1.9 using minimal Android Bitmap and JSON stubs after the iterative-profile revision.
- [x] No lowering of the 5σ rejection threshold was introduced.
- [x] Trace fitting, rectification and two-sided background code remain unchanged from the prior QA build.
- [ ] Full Android/Compose Gradle compilation still needs to be run in the MethodMesh app project.


## v0.2.18 fixed-scale z-map QA

- [x] Standardized residual image is displayed on a fixed −5σ to +5σ scale.
- [x] `|z| ≥ 3σ`, `|z| ≥ 5σ`, and actually rejected pixels use distinct overlays.
- [x] Raw signed residual remains auto-scaled for subtle-structure inspection.
- [x] No extraction or rejection mathematics changed in this revision.

## Reliable line-finder revision

- [x] Horne extraction engine is unchanged in this revision.
- [x] Low-signal samples are masked from continuum normalization rather than divided by near-zero values.
- [x] Line-finder continuum uses a broad high-percentile upper envelope.
- [x] Line-finder UI exposes reliable-sample coverage.
- [x] Two or more anchors generate provisional positions for unplaced Balmer lines.
- [x] Provisional positions are never committed automatically.
- [x] Active predicted Balmer lines receive a local zoomed trough picker with explicit commit.
- [ ] Full Android/Compose Gradle compilation remains to be run in the MethodMesh app project.

## Balmer pattern-assistance revision

- [x] Horne extraction, trace fit, background model and residual rejection are unchanged.
- [x] Candidate troughs are taken only from reliable line-finder samples.
- [x] Candidate pairs are matched against known Balmer-line pairs to generate provisional linear hypotheses.
- [x] Up to three deduplicated hypotheses are ranked by pattern support; none is auto-selected.
- [x] Hypothesis predictions are provisional gold guides only.
- [x] Two confirmed anchors supersede any selected pattern hypothesis.
- [x] Local predicted-line refinement uses an independent shoulder-based continuum.
- [x] Reference Commit requires at least three explicitly confirmed Balmer anchors.
- [x] Quadratic calibration still requires at least three anchors by construction.
- [ ] Full Android/Compose Gradle compilation remains to be run in the MethodMesh app project.

## Global spectral-geometry prior

- [x] Pattern hypotheses are rejected if the Balmer series is compressed into a small part of the selected visible trace.
- [x] Hα is constrained to the RED-side portion of the trace and Hδ to the VIOLET-side portion.
- [x] Implied whole-trace wavelength span is constrained by a deliberately broad visible-spectrum plausibility range.
- [x] Hypothesis ranking includes an explicit geometry score alongside local trough support.
- [x] Geometry diagnostics are shown to the user in each hypothesis card.
- [x] Horne extraction and line-finder normalization are unchanged in this revision.

## Chromatic wavelength bootstrap revision

- [x] RGB colour is used only as a coarse wavelength prior, never as the committed wavelength solution.
- [x] Chromaticity removes overall brightness before matching.
- [x] Dark/desaturated colour samples are down-weighted.
- [x] Observed VIOLET → RED colour is aligned monotonically to an approximate visible spectral-colour locus.
- [x] Candidate endpoint ranges are searched rather than assuming exact 400/700 nm endpoints.
- [x] The UI shows observed and ideal aligned colour strips plus approximate wavelength ticks.
- [x] Balmer local-refinement windows can be opened from chromatic predictions before any anchors are confirmed.
- [x] Two confirmed anchors supersede the chromatic prior for subsequent predictions.
- [x] Three confirmed Balmer anchors remain mandatory before Reference Commit.
- [x] Horne extraction and its QA diagnostics are unchanged in this revision.
- [ ] Full Android/Compose Gradle compilation remains to be run in the MethodMesh app project.

- [x] Automatic chromatic Balmer guide positions are gated off when chromatic fit quality is <25% or useful colour coverage is <15%; weak mappings remain visible for QA only.

## Colour-transition boundary revision

- [x] RED/VIOLET endpoint selection remains mandatory and unchanged.
- [x] Optional RED/YELLOW, YELLOW/GREEN, GREEN/BLUE and BLUE/VIOLET boundaries can be placed before extraction/calibration.
- [x] Boundary picker reuses the established full-screen hold/magnifier/deadband/manual-coordinate interaction.
- [x] Selected landmark coordinates are held in saveable working state and survive ordinary recomposition/rotation.
- [x] Landmark wavelength values are broad soft priors, not exact wavelength calibration points.
- [x] Landmarks are projected onto the optimised trace and incorporated into the monotonic chromatic DTW objective.
- [x] Chromatic QA displays observed/ideal boundary positions and reports boundary agreement.
- [x] Two or more consistent landmarks can rescue a weak RGB-only bootstrap; poor agreement remains gated.
- [x] Confirmed Balmer anchors supersede colour-derived guide positions.
- [x] Horne extraction engine and its QA diagnostics are unchanged.
- [ ] Full Android/Compose Gradle compilation remains to be run in the MethodMesh app project.

## Uncertainty-aware Balmer-window revision

- [x] Chromatic bootstrap produces a wavelength uncertainty window for every unconfirmed Balmer line.
- [x] Hβ/Hγ receive tighter interior windows; Hα/Hδ receive broader endpoint-extrapolation windows.
- [x] All four windows are displayed on the chromatic photographic strip and in the Balmer calibration chart.
- [x] The active local trough picker shows the chromatic uncertainty band inside a wider contextual view.
- [x] Reference workflow starts on Hβ and advances Hβ → Hγ → Hα → Hδ.
- [x] One confirmed anchor globally offset-corrects the chromatic prior and modestly narrows remaining windows.
- [x] Two confirmed anchors supersede the chromatic prior with the actual preliminary wavelength solution.
- [x] Three confirmed anchors remain mandatory before Reference Commit.
- [x] Horne extraction and all extraction QA mathematics are unchanged.
- [ ] Full Android/Compose Gradle compilation remains to be run in the MethodMesh app project.

## Unified spectrum-geometry picker revision

- [x] RED/VIOLET endpoints and optional colour-transition boundaries are collected in one full-screen picker.
- [x] All targets share the established magnifier, hold-lock, tremor-deadband and manual-coordinate interaction.
- [x] All selected points remain visible together and can be revised independently.
- [x] Optional boundary placement follows RED/VIOLET within the same dialog and can be skipped.
- [x] The obsolete second colour-boundary dialog and separate edit button were removed.
- [x] Target-spectrum analysis still exposes RED/VIOLET only; colour-boundary controls are enabled only for reference-star creation.
- [x] Horne extraction, chromatic bootstrap mathematics and Balmer calibration logic are unchanged.
- [ ] Full Android/Compose Gradle compilation remains to be run in the MethodMesh app project.

## Signed calibration-signal revision

- [x] Calibration base signal no longer clips negative Horne or fallback boxcar samples to zero.
- [x] Relative calibration display scales by robust absolute amplitude and preserves signed values.
- [x] Whole-spectrum calibration y-axis uses robust signed quantiles and an explicit zero-background baseline.
- [x] Immediate extracted-spectrum preview uses the same signed semantics.
- [x] Local Balmer refinement uses shoulder-fitted additive baseline subtraction rather than positive continuum division.
- [x] Local Balmer cursor reports signed residual and preserves finite negative samples.
- [x] Fine magnifier displays a zero baseline for signed modes.
- [x] Horne extraction engine, chromatic bootstrap and Balmer search-window predictions are unchanged.
- [ ] Full Android/Compose Gradle compilation remains to be run in the MethodMesh app project.

## Reference-calibration QA revision

- [x] RED/VIOLET remain trace/chromatic endpoints; they are not redefined as brightest-colour points.
- [x] Endpoint guard zones cover approximately 4% of each end, bounded to 12–40 samples.
- [x] Guard-zone samples remain visible but are excluded from line-finder reliability and automatic Balmer-pattern trough detection.
- [x] Local Balmer baseline fitting and manual Balmer placement are clamped to the endpoint-safe interior.
- [x] Balmer chart and committed calibrated-spectrum QA view shade the endpoint guards.
- [x] Selected-model Δλ residual is shown beside every confirmed Balmer anchor.
- [x] Residuals >2 nm show WARN; residuals >3 nm show OUTLIER.
- [x] Linear RMS and quadratic RMS are calculated and shown side by side whenever sufficient anchors exist.
- [x] Selected model remains user-controlled; RMS comparison does not cause automatic model switching.
- [x] UI explains why a three-anchor quadratic is not independent validation and why the fourth Balmer line matters.
- [x] Commit retains a read-only calibrated-spectrum QA chart with all Balmer anchors overlaid.
- [x] Reference provenance notes record linear RMS, quadratic RMS and maximum selected-model |residual|.
- [x] Horne extraction and chromatic-bootstrap mathematics are unchanged.
- [ ] Full Android/Compose Gradle compilation remains to be run in the MethodMesh app project.

## v0.2.18 compile fix

- [x] Committed reference QA captures the delegated `extraction` state into a stable local value before null-checking and passing it to `ReferenceCalibrationQaChart`.
- [x] Removes the Kotlin smart-cast failure caused by using a delegated Compose state property directly after a null check.

## v0.2.18 Balmer search-window lock

- [x] Active Balmer local picker is locked to an explicit predicted search window by default.
- [x] Tap, drag and ±1/±5 nudges cannot leave the gold window while locked.
- [x] Explicit Expand search override enables the wider endpoint-safe local context.
- [x] Expanded mode is visibly labelled and the Set action is marked as expanded.
- [x] Relock to prediction clamps the cursor back into the original gold window.
- [x] Endpoint guards remain active in both locked and expanded modes.
- [x] Extraction, chromatic bootstrap, wavelength fitting and MethodMesh/ODK contracts are unchanged.

## v0.2.18 reference selector

- [x] Analyser shows one Reference calibration control instead of one chip per saved profile.
- [x] Tapping the control opens a profile chooser even when only one reference exists.
- [x] Chooser includes Uncalibrated mode and every saved reference.
- [x] Each reference row shows name, star/type, model, anchor count, RMS, saved time where available and a short ID suffix.
- [x] Current selection is visually explicit.
- [x] Selecting a profile persists `reference_id`, invalidates stale working analysis and closes the chooser.
- [x] Selector refreshes the local reference repository when opened.
- [x] Fixed preset/protocol/ODK `reference_id` behavior remains unchanged.


## v0.2.18 target/reference preparation parity

- [x] Target analyser uses the same full-screen RED/VIOLET + optional four colour-boundary picker as reference creation.
- [x] Target colour boundaries remain visible and feed the same chromatic-bootstrap diagnostic.
- [x] Native stages 1–7 mirror the reference workflow.
- [x] Reference selection/registration is stage 8; feature detection/live result is stage 9.
- [x] Reference/detection changes preserve extraction but invalidate stale derived output until explicit refresh.
- [x] Horne extraction and saved-reference mathematics are unchanged in this pass.
- [ ] Same-image reference registration/orientation bug remains open for the next pass.


## v0.2.18 feature guard and colour-boundary autodetect

- [x] General emission/absorption detection excludes RED/VIOLET endpoint guard samples.
- [x] Endpoint-guard samples remain visible and exported.
- [x] Calibrated feature-search zones distinguish anchor-bracketed vs extrapolated wavelengths.
- [x] Extrapolated detections are confidence-down-weighted rather than silently treated as equally calibrated.
- [x] Live spectrum chart shades endpoint guards and extrapolated regions.
- [x] Feature rows, JSON, features CSV and spectrum CSV expose the feature-search zone.
- [x] Shared picker offers Auto-detect colour boundaries only after RED and VIOLET are present.
- [x] Auto-detection uses the existing chromaticity + monotonic spectral-colour alignment rather than a new unrelated colour heuristic.
- [x] Auto-detected R/Y, Y/G, G/B and B/V markers remain manually revisable soft priors.
- [x] On the supplied JPEG, an offline algorithm smoke check found 95% useful colour and ordered B/V → G/B → Y/G → R/Y suggestions along the visible spectrum.
- [ ] Full Android/Compose Gradle compilation remains to be run in the MethodMesh app project.


## v0.2.19 multi-scale features and default colour proposals

- [x] Existing narrow residual-extremum detector retained.
- [x] Added broad local-shoulder matched-response detector at nominal 7/13/21/33/49-sample widths when geometry permits.
- [x] Broad detector uses the same user sigma threshold; no global lowering of detection significance.
- [x] Broad response uncertainty combines propagated extraction noise with robust local response MAD.
- [x] Broad candidates use threshold stability and multi-scale support; a single-scale candidate needs full threshold stability and at least +0.5σ margin.
- [x] Coincident broad-scale candidates are clustered into one physical feature.
- [x] Coincident narrow/broad evidence is merged and labelled `narrow_and_broad`.
- [x] Feature model/JSON now includes detection method, strongest physical scale and supporting scales.
- [x] Native rows and CSV exports expose method, scale and FWHM.
- [x] Endpoint-guard exclusion remains common to narrow and broad detectors.
- [x] Extrapolated wavelength-region confidence penalty is unchanged.
- [x] RED/VIOLET completion now triggers one automatic colour-boundary proposal for a new endpoint geometry when boundaries are absent.
- [x] Auto-proposed R/Y, Y/G, G/B and B/V markers remain manually revisable.
- [x] Explicit re-detect remains available and a failed auto-proposal falls back to manual placement.

- [x] `SpectrumModels.kt` + `SpectrumEngine.kt` compiled successfully with `kotlinc` against minimal Android/JSON stubs after the v0.2.19 detector changes; this catches Kotlin/core-engine syntax/type errors independently of Compose.
- [ ] Full Android/Compose Gradle compilation remains to be run in the MethodMesh app project.


## v0.2.20 NaN regression and Y/G auto-boundary refinement

- [x] Broad-feature local prominence ignores non-finite response neighbours.
- [x] Residual broad candidates with non-finite derived metrics are dropped before feature creation.
- [x] Feature JSON cannot throw on a non-finite diagnostic value; such fields are omitted rather than aborting extraction.
- [x] YELLOW/GREEN auto-placement uses a direct photographic hue crossover cue (~90°) plus a loose wavelength prior.
- [x] R/Y, G/B and B/V retain the established chromatic-DTW placement.
- [x] v0.2.20 core engine compiles with `kotlinc` against the existing minimal Android/JSON stubs.
- [ ] Full Android/Compose Gradle compilation remains to be run in the MethodMesh app project.


## v0.2.21 transport, provenance and smoothing freeze

- [x] Scientific extraction/calibration/feature-detection algorithms are unchanged in this pass.
- [x] Analysis Commit creates provenance JSON, hash manifest and complete ZIP bundle.
- [x] Analysis bundle contains source image, annotated image, spectrum CSV, features CSV, features JSON, metadata JSON, provenance JSON and manifest JSON.
- [x] Analysis canonical contract exposes provenance/manifest/bundle URIs and SHA-256 hashes.
- [x] Reference Commit creates annotated image, calibrated reference-spectrum CSV, anchor CSV, file-form reference JSON, provenance, manifest and complete ZIP.
- [x] Reference canonical contract exposes each principal reference artefact plus hashes and bundle URI/hash.
- [x] Native Share routes through canonical MethodMesh `ResultShare` typed attachments.
- [x] Native Save uses shared `OutputExportRepository` and includes the complete ZIP.
- [x] Caller-owned source images are not gratuitously returned as duplicate top-level ODK attachments, but are retained inside the reproducibility ZIP.
- [x] Domain metadata/provenance explicitly state that spectra are not instrument-response corrected or absolute-flux calibrated.
- [x] Added non-destructive display-only low-pass windows; canonical extraction, calibration, detection and CSV remain unsmoothed.
- [x] ODK showcase forms updated for the new individual provenance/manifest/bundle return fields.
- [ ] Full Android/Compose Gradle compilation remains to be run in the MethodMesh app project.

### v0.2.21 implementation checks

- [x] `SpectrumModels.kt` + `SpectrumExport.kt` compile under `kotlinc` against minimal Android/JSON stubs after the packaging changes.
- [x] Kotlin parser pass found no `expecting` / `unexpected tokens` errors in the two capability screens or `SpectrumUi.kt`.
- [x] Both XLSForm showcase workbooks re-import successfully through `artifact_tool` and contain no spreadsheet formula errors.

## v0.2.22 automatic consensus calibration

- [x] Added an offline, deterministic multi-hypothesis wavelength-calibration solver for A-star references.
- [x] Solver generates candidate absorption troughs independently of the final scientific feature list using permissive multi-scale minima plus any existing absorption detections.
- [x] Pairwise trough/line assignments seed many linear pixel→wavelength hypotheses.
- [x] RED→VIOLET orientation is enforced while dispersion magnitude remains broadly unconstrained.
- [x] Every candidate hypothesis is tested against the full Hδ/Hγ/Hβ/Hα pattern with one-to-one matching.
- [x] Ranking prioritises independent line support before residual/parsimony cost so exact two-point fits cannot dominate a coherent 3–4 line solution.
- [x] Chromatic bootstrap/colour boundaries enter only as a weak alias-breaking prior.
- [x] UI presents the leading solution, matched lines, RMS, evidence, prior agreement and ranked alternatives.
- [x] Automatic acceptance requires at least three independent Balmer matches and populates the existing canonical anchor model.
- [x] Manual Balmer placement remains available as review/fallback and can revise an automatic proposal.
- [x] Stored reference/provenance records the final anchor source (`automatic_consensus`, `automatic_consensus_reviewed`, or `manual`).
- [x] Synthetic solver smoke test recovered all four injected Balmer troughs from an 800-sample spectrum with the correct dispersion to <0.001 nm/px and ~0.05 nm RMS.
- [x] `SpectrumModels.kt` + `SpectrumCalibrationSolver.kt` compile under `kotlinc` against minimal JSON stubs.
- [ ] Validation on independent real Seestar observations is intentionally pending the incoming dataset.
- [ ] Full Android/Compose Gradle compilation remains to be run in the MethodMesh app project.


## Current scientific hold / next dataset

The v0.2.23 automatic consensus solver, including the generic targeted-completion pass, is now deliberately held stable pending independent observations. Do **not** continue tuning candidate thresholds, ranking weights or colour-prior strength against the original Vega image alone.

The next validation dataset should preferably include repeated captures, multiple stellar types under the same Seestar/grating geometry, an A-type reference, and an emission-line object if available. Full/native-resolution unsaturated images are preferred.

The next admission questions are:

- does automatic consensus recover the Balmer wavelength solution reproducibly across independent A-star captures?
- do repeated observations give consistent dispersion/intercept and residuals?
- are ambiguous alternatives correctly surfaced rather than silently selected?
- which detected structures recur at fixed wavelength across different stars, suggesting telluric/instrumental origin?
- does the saved-reference transfer remain stable across different targets?
- can a deliberately zero-order-free/high-dispersion capture be calibrated from internal spectral structure alone?

Only after those checks should the line catalogue, score weights, telluric support, template matching or learned models be expanded. See `DESIGN.md`.


## v0.2.23 targeted-completion regression

The first real v0.2.22 Vega run provided a concrete regression case without introducing a Vega-specific rule:

- direct automatic consensus found Hα at ~150 px, Hγ at ~726 px and Hδ at ~786 px;
- the resulting affine model predicted Hβ at ~590.3 px;
- the local Hβ review showed an obvious absorption trough at that predicted neighbourhood;
- manually adding Hβ at 590 px produced 4/4 Balmer anchors with linear RMS ~0.250 nm (quadratic ~0.247 nm, so linear remained the parsimonious model).

v0.2.23 implements the generic algorithm exposed by that test: **global ≥3-line consensus → predict missing catalogue member → targeted multi-scale local trough recovery → refit/rescore**. No star-specific pixel positions are stored.

Implementation checks:

- [x] Targeted recovery cannot run from a two-line seed; at least three direct first-pass catalogue matches are required.
- [x] Direct match count remains the primary ranking evidence; recovered lines are secondary completion evidence.
- [x] Recovered troughs must be negative, locally prominent and persist across multiple smoothing scales including a broader scale.
- [x] Recovered matches are tagged `targeted_recovery` and surfaced distinctly in the UI.
- [x] Shared RED/VIOLET picker now retries automatic colour-boundary detection synchronously on Done if no proposal has yet returned.
- [x] `SpectrumCalibrationSolver.kt` compiles under standalone `kotlinc` stubs after the v0.2.23 changes.
- [x] Synthetic recovery smoke test: initial candidate generation deliberately sees only Hα/Hγ/Hδ; the ≥3-line hypothesis predicts Hβ, targeted multi-scale search recovers Hβ near the correct model position, and the final solution is 4/4.
- [x] Synthetic negative-control smoke test: with no Hβ trough present, the same 3-line hypothesis remains 3/4 and recovery adds nothing.
- [ ] Re-run the same Vega reference image: expected outcome is automatic 4/4 with Hβ recovered near the model-predicted neighbourhood, without manual placement.
- [ ] Validate on independent A-star observations before changing thresholds or claiming general performance.
- [ ] Full Android/Compose Gradle compilation remains to be run in the MethodMesh app project.


## v0.2.24 regression target

The motivating device run produced a three-line linear consensus (Hα/Hγ/Hδ) whose fitted model placed the missing Hβ near the observed trough, while the manual review panel remained locked to an older chromatic prediction. Required v0.2.24 behaviour is:

- ≥3 direct matches are still required before targeted completion can run;
- the fitted consensus, not the colour bootstrap, supplies missing-member review positions;
- a broad trough on a positive continuum can be recovered after local shoulder-baseline subtraction;
- absent local trough evidence must still leave the solution incomplete;
- no star- or device-specific pixel coordinate is permitted.

Standalone solver regression should include both a positive-continuum broad-trough case and a negative control with no missing trough.

### v0.2.24 local checks completed in this handoff

- [x] `SpectrumCalibrationSolver.kt` compiles under standalone `kotlinc` stubs.
- [x] Positive-continuum synthetic regression: the ordinary candidate pass sees only three direct Balmer members; continuum-relative targeted recovery finds the fourth member at the model-predicted position (`3 direct + 1 targeted_recovery`).
- [x] Negative control with no missing-line trough remains incomplete (`3 direct`, no recovered line); the recovery pass does not manufacture the fourth member.
- [x] Kotlin parser scan of the two modified Compose files reported no parser/syntax diagnostics; unresolved Android/Compose symbols are expected outside the full app classpath.
- [ ] Full Android/Compose Gradle build and on-device Vega regression remain to be run in the MethodMesh app project.

## v0.2.25 alias-rejection regression

The motivating device failure produced a calibration in which several assigned Balmer lines were crowded into a small part of an 837-pixel spectrum. The resulting four-anchor **linear RMS was 21.362 nm**, while a quadratic curve reported **0.035 nm** and therefore made a wrong identification look deceptively precise. v0.2.25 treats this as a line-identification failure, not a reason to prefer a higher-order polynomial.

Required behaviour:

- three-or-more-line automatic hypotheses are refitted and must pass an affine residual gate before targeted recovery;
- the photographic colour bootstrap may reject a dispersion alias only as a broad scale/orientation check (same sign, within a 4× envelope), never by creating a line match;
- targeted recovery preserves the missing catalogue member's identity and must strengthen the same affine solution;
- quadratic selection is disabled until four anchors already pass the linear line-identity check;
- Commit is blocked when ≥3 anchors have linear RMS >3 nm, irrespective of quadratic RMS;
- repeated edits to an accepted automatic proposal retain provenance as `automatic_consensus_reviewed` rather than incorrectly degrading to `manual` after the second edit.

Standalone solver regressions in this handoff cover: (1) three direct Balmer lines plus a broad Hβ trough recovered to 4/4; (2) a negative control with no Hβ remaining 3/4; and (3) strong false troughs forming a high-dispersion alias, which are rejected by the broad colour-geometry scale check while the true affine solution is retained.

Further threshold tuning remains on hold until independent observations are available.
