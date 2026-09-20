# Star spectrum module — current internal design

**Design authority:** current behaviour for `star_spectrum` v0.2.25  
**Maturity:** `DEVELOPMENT`  
**Connectivity:** `OFFLINE`

This document is the concise internal source of truth for the current scientific and product design. Historical notes in the other README/validation files describe how the module evolved; where they conflict with this file, **this file describes the current design**.

## 1. Product intent

`star_spectrum` is an explainable, local astronomical slitless-spectroscopy tool for raster observations. It should behave like a scientific instrument rather than an opaque classifier.

The module has two independent capabilities:

- `astronomy.spectrum.reference.create` — create and persist a reusable wavelength reference from the user's own observation;
- `astronomy.spectrum.analyse` — extract and analyse a target spectrum, optionally using a saved reference.

The primary v1 direction is deliberately conventional and auditable: trace/extract the spectrum, calibrate wavelength from physical spectral structure, show QA, then detect candidate absorption/emission features. A neural network is **not** required for the primary path.

## 2. Scientific invariants

Do not casually change these without independent validation data:

- Horne-style optimal extraction remains the primary 1-D extraction path.
- The original unsmoothed extraction is canonical. Display smoothing is non-destructive.
- Raster/JPEG output is relative **instrumental intensity**, not calibrated photon flux.
- No camera/telescope/grating instrument-response correction is currently applied.
- Feature detection reports candidate absorption/emission structure; it does not automatically identify chemical species.
- Colour geometry is a weak wavelength prior, never a replacement for spectral-line evidence.
- Automatic wavelength calibration must remain explainable: observed troughs, proposed line matches, residuals, alternatives and ambiguity are visible.
- Manual calibration remains a review/fallback path, not the default path.
- Commit is the boundary that freezes the canonical result and provenance.

## 3. Reference-creation pipeline

The current reference workflow is:

1. Load the reference-star raster image.
2. Record reference name and optional star/spectral type.
3. Select RED and VIOLET spectrum endpoints.
4. Automatically propose R/Y, Y/G, G/B and B/V colour boundaries; allow full manual revision.
5. Refine/rectify the trace and run two-sided background estimation plus Horne-style optimal extraction.
6. Show extraction QA and the extracted spectrum before calibration.
7. Build the chromatic wavelength bootstrap from the photographed colour progression. This is approximate evidence only.
8. Build permissive, multi-scale **absorption-trough candidates** from the extracted spectrum.
9. Run the automatic consensus wavelength solver over many candidate trough ↔ Balmer-line assignments.
10. For a leading hypothesis that already has at least three independently detected Balmer members, predict the pixel neighbourhood of any missing member and run a targeted multi-scale trough search there.
11. Refit/rescore the hypothesis only if the predicted neighbourhood contains real multi-scale absorption evidence; the wavelength model alone cannot create a match.
12. Show the leading solution, direct/recovered matches, residuals, evidence, weak-prior agreement and ranked alternatives.
13. Allow **Use automatic solution** only when at least three lines were found independently before targeted completion.
14. Allow manual review/fine-tuning or fully manual fallback.
15. Fit the normal canonical wavelength model from the accepted/confirmed anchors and show linear/quadratic QA.
16. Commit, persist the reusable reference and emit the complete return package.

The reference does not become persistent before Commit.

## 4. Automatic consensus wavelength calibration

### 4.1 Current catalogue

The first validated catalogue scope is intentionally small and physically strong for A-type stars:

| Line | Wavelength |
|---|---:|
| Hδ | 410.17 nm |
| Hγ | 434.05 nm |
| Hβ | 486.13 nm |
| Hα | 656.28 nm |

The solver machinery is catalogue-agnostic, but adding stellar or telluric catalogues should wait for independent observations and explicit validation.

### 4.2 Candidate generation

The calibration solver does **not** reuse only the final target feature list. It independently creates permissive absorption-trough candidates from the extracted reference spectrum at several smoothing scales and can incorporate existing absorption evidence.

The candidate stage is intentionally permissive. False trough candidates are acceptable because the global pattern search, not a single local minimum, decides the wavelength solution.

### 4.3 Hypothesis generation

For many ordered pairs of observed trough candidates and many ordered pairs of known catalogue lines, solve the affine model:

```text
wavelength_nm = intercept_nm + slope_nm_per_px * distance_px
```

The internal trace coordinate runs RED → VIOLET, so acceptable hypotheses require a negative slope. The permitted dispersion range remains broad; the solver should not embed a hardcoded Seestar/grating dispersion.

Each two-point assignment only **seeds a hypothesis**. It is never sufficient evidence by itself.

### 4.4 Global consensus matching

Every seed hypothesis is projected across the full Balmer catalogue. The solver then performs one-to-one matching between predicted line positions and independent observed trough candidates using scale-aware tolerances.

A good calibration is therefore one simple pixel→wavelength mapping that explains several independent pieces of spectral structure simultaneously.


### 4.5 Targeted completion of a supported line family

Version 0.2.23 adds a conservative second pass for a missing Balmer member. It runs **only after the ordinary global search already supports the same affine solution with at least three independently detected catalogue lines**. For each missing catalogue member the current affine model predicts a pixel neighbourhood; MethodMesh then searches the observed residual locally across multiple smoothing scales. A recovered candidate must be a real negative trough with local prominence and multi-scale persistence.

This is deliberately generic. No Vega pixel coordinate, Seestar-specific dispersion, or line-specific hard-coded detector position is used. The predicted neighbourhood moves with the fitted intercept/dispersion. Recovered lines are tagged `targeted_recovery` and are distinguished from direct first-pass matches in the UI/provenance.

Targeted completion cannot bootstrap a two-line solution into apparent consensus. Direct first-pass match count remains the primary ranking evidence; recovery only completes an already-supported pattern.

### 4.6 Ranking and parsimony

Ranking is intentionally ordered as:

1. **direct first-pass supported-line count**;
2. lower description/parsimony cost;
3. total catalogue coverage after any conservative targeted recovery;
4. stronger aggregate trough support/evidence.

This prevents a mathematically exact two-point fit from beating a slightly noisier three- or four-line consensus.

The current description/parsimony cost combines wavelength residuals, unexplained catalogue structure, the fixed complexity of the simple affine model, and only a weak contribution from chromatic-prior disagreement. The score is a ranking/evidence mechanism, **not a posterior probability that the identification is physically correct**.

Nearby duplicate solutions are collapsed so the UI shows meaningfully different alternatives rather than tiny perturbations of the same mapping.

### 4.7 Weak colour prior

The existing spectrum-colour geometry is an excellent initial constraint because it is derived from the user's own image, but it must remain soft.

The prior can use:

- RED and VIOLET endpoint orientation;
- the continuous RGB/chromatic trajectory;
- R/Y, Y/G, G/B and B/V transition landmarks.

It may help reject aliases or rank otherwise similar line-pattern solutions. It **cannot create a spectral-line match**, rescue a solution with absent trough evidence, or be treated as an exact wavelength measurement.

### 4.8 Acceptance and ambiguity

The current automatic acceptance rule is deliberately conservative:

- fewer than three **direct first-pass** Balmer matches: never auto-accept;
- targeted recovery may complete a 3-line direct consensus to 4/4, but cannot supply the third independent line;
- small separation from the next distinct solution: mark ambiguous;
- weak overall evidence/confidence: mark ambiguous;
- three or four coherent line matches: automatic proposal may populate the ordinary canonical `CalibrationAnchor` objects.

Once accepted, there is no separate downstream “AI calibration” data type. The existing wavelength-fit machinery consumes the same anchor structure used by manual calibration.

### 4.9 Manual review/fallback

Manual Balmer controls remain first-class because they are needed for:

- inspecting the automatic matches;
- fine-tuning a proposed trough centre;
- replacing a bad automatic match;
- completing a reference when automatic consensus cannot find enough structure;
- validation/debugging of the automatic solver itself.

Provenance records one of:

- `automatic_consensus`;
- `automatic_consensus_reviewed`;
- `manual`.

## 5. Zero-order independence

The wavelength solver does not require the undispersed **zero-order star** as a stored wavelength origin. It calibrates from internal spectral structure and the fitted pixel-distance→wavelength relation.

This is important for the proposed higher-dispersion observing mode: a future observation may allow zero order to fall off the detector so that more of the first-order spectrum occupies the sensor. In principle, a sufficiently supported internal line pattern can still determine an affine wavelength mapping.

However, this is currently a **design property awaiting real-data validation**, not a claim that arbitrary 80/100 lines/mm Seestar observations are already validated. Before promoting this mode we need observations made with the same optical geometry across reference and target captures.

## 6. Target analysis relationship

`astronomy.spectrum.analyse` remains separate from reference creation.

A target run:

- performs the same trace/background/Horne extraction and QA;
- may select a saved wavelength reference;
- applies the persisted calibration/registration model;
- detects narrow and broad absorption/emission candidates;
- labels endpoint-guard, anchor-bracketed and extrapolated search zones;
- exports the instrumental spectrum and feature measurements.

The target analyser does **not** currently rerun the Balmer consensus solver to identify arbitrary stellar lines. Automatic consensus is presently a reference-creation calibration mechanism.

## 7. Smoothing and continuum semantics

Low-pass/smoothing controls are for inspection and calibration assistance only. The module must preserve the unsmoothed Horne extraction and use that canonical data for export.

The broad continuum shape in a raster observation is the convolution of stellar spectral energy distribution with the telescope/grating/camera response and image processing. It must not be interpreted as a response-corrected stellar continuum unless a future instrument-response correction has explicitly been applied.

## 8. Return/provenance requirements

Both capabilities follow the MethodMesh live-result → Commit lifecycle and return complete scientific packages.

Analysis Commit includes source image, annotated image, spectrum CSV, feature CSV/JSON, scientific metadata JSON, provenance JSON, hash manifest and complete ZIP.

Reference Commit includes source image, annotated reference image, calibrated reference-spectrum CSV, anchor CSV, reusable reference JSON, provenance JSON, hash manifest and complete ZIP.

Provenance for references must preserve at least:

- method/module version;
- source hash;
- extraction settings and QA;
- colour landmarks/chromatic-prior inputs;
- accepted calibration anchors;
- anchor source (`automatic_consensus`, reviewed, or manual);
- wavelength model/order/coefficients and RMS;
- explicit instrumental-response semantics.

Native Share, Save, presets/protocols and ODK must expose the same canonical committed result rather than bespoke surface-specific results.

## 9. Validation state / holding pattern

The automatic consensus design has passed synthetic smoke testing, but scientific tuning is now intentionally in a **holding pattern pending independent real observations**.

Do not keep changing candidate thresholds or ranking weights against the original Vega image alone. That would risk overfitting the algorithm to one photograph.

The next useful dataset should contain, where possible:

- several stars captured with the same Seestar/grating geometry;
- repeated exposures of at least one star;
- an A-type reference such as Vega/Sirius;
- cooler stars with materially different spectral structure;
- an emission-line object if available;
- native/full-resolution originals, preferably without saturation.

Validation should ask:

1. Does the reference wavelength solution reproduce on repeated captures?
2. Does automatic consensus recover the expected Balmer pattern without manual seeding?
3. Are alternative solutions appropriately separated/flagged as ambiguous?
4. Which structures recur at fixed wavelength across different stars (telluric/instrumental candidates)?
5. Which structures vary with stellar target?
6. Does a zero-order-free/high-dispersion reference produce a stable solution?
7. Does the deterministic solver already solve the problem well enough that a learned model is unnecessary?

## 10. Future extensions — order of preference

The preferred development order after validation data arrive is:

1. validate/tune the current deterministic Balmer consensus solver;
2. extend the catalogue carefully to additional stellar/telluric patterns if the data justify it;
3. test template/cross-correlation re-identification as a second deterministic route;
4. formalise zero-order-free/high-dispersion capture support;
5. only then consider a 1-D CNN or other learned feature proposer.

If a learned model is explored, it should operate on extracted/normalised 1-D spectra rather than raw photographs wherever possible. Ground truth must span instruments, resolutions, noise conditions and stellar types sufficiently to detect shortcut learning. Learned output should propose evidence, not silently replace the auditable wavelength solution.

## 11. External algorithmic provenance

The automatic calibration approach is conceptually informed by open astronomical wavelength-calibration methods that search feature-pattern hypotheses, including RANSAC-assisted strategies such as RASCAL. MethodMesh uses an independent Kotlin implementation and does not vendor or execute the Python packages.

See also:

- `README_StarSpectrumReference.md`
- `README_StarSpectrumAnalyse.md`
- `VALIDATION.md`
- `RETURN_CONTRACT.md`
- `THIRD_PARTY_NOTICES.md`


## v0.2.24 consensus propagation and continuum-relative recovery

A validated automatic consensus is the strongest provisional wavelength guide available to the manual-review surface. Once the automatic search has at least three independently detected Balmer members, the fitted linear model supersedes the chromatic bootstrap for predicted positions and review windows even before the operator explicitly accepts the proposal. The colour model remains provenance and weak alias-breaking evidence; it must not pull a missing-line review window away from the position implied by stronger line evidence.

Targeted missing-line recovery is continuum-relative. A broad absorption trough may sit on a positive part of the extracted stellar envelope, so recovery no longer requires the globally signed residual itself to be negative. Within the model-predicted neighbourhood, MethodMesh fits a straight baseline from the two shoulders and searches for a statistically credible downward excursion relative to that local baseline across multiple smoothing scales. This is generic: no Vega-specific pixel locations, Seestar-specific dispersion, or line-specific detector coordinate is encoded.

## v0.2.25 affine-consensus gate and alias rejection

Real-device testing exposed a failure mode that must not be hidden by model flexibility: a tightly clustered set of unrelated troughs can be assigned Balmer labels and then made to look excellent under a quadratic wavelength curve even though the same assignments are grossly inconsistent with a single linear objective-grating dispersion.

The calibration architecture therefore distinguishes **line identity** from **wavelength-model refinement**:

1. Balmer line identity is established under an affine (linear) pixel→wavelength model only.
2. Any hypothesis with three or more members is refitted to all assigned members before it is scored. The refitted pattern must have linear RMS ≤3 nm and normalized RMS ≤0.80.
3. When a chromatic wavelength bootstrap exists, its global slope is used only as a very broad geometric sanity prior. Candidate dispersion must have the same direction and remain within a 4× envelope of the photographic colour-derived scale. This is deliberately too broad to identify lines itself, but rejects order-of-magnitude aliases.
4. Targeted recovery keeps the missing line's identity fixed. A recovered trough is appended to the existing line family, the same affine model is refitted, and the new point is accepted only if the full set remains affine-plausible and does not materially degrade the global RMS.
5. Quadratic calibration is downstream refinement only. It cannot establish or rescue line identity. In the native reference UI it is disabled until four anchors already pass the linear identity check; Commit is blocked if the anchor set fails that check.

This is generic: there are no Vega pixel coordinates, Seestar-specific dispersions, or image-specific line positions. The colour geometry is used as a weak whole-spectrum scale/orientation constraint, while the observed absorption pattern provides the actual calibration evidence.
