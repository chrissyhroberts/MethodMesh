# Star Spectrum Return Contract

Version: 0.2.25

This module follows the MethodMesh Commit lifecycle: working analysis remains editable; **Commit** freezes the canonical payload and creates stable transport artefacts. Direct native use remains on the capability screen after Commit, with Share, Save, Copy, Done and Revise actions. ODK/preset/protocol callers receive the same canonical fields through the shared transport layer.

## `astronomy.spectrum.analyse`

The committed scientific package contains:

- original source image;
- annotated spectrum image;
- `spectrum.csv` — one row per extracted sample, including trace coordinates, wavelength when calibrated, boxcar/optimal signal, variance proxy, continuum/residual/noise, contamination and feature-search QA;
- `features.csv` — one row per detected feature;
- `features.json` — structured feature array;
- `metadata.json` — module-owned scientific metadata;
- `provenance.json` — method/version, settings, source hash, colour landmarks, extraction/calibration provenance and instrumental-response semantics;
- `manifest.json` — role, filename, MIME type, byte size and SHA-256 for every package member;
- `star_spectrum_bundle.zip` — all of the above.

The source image is always included inside the ZIP for reproducibility. `spectrum_source_image_uri` is populated only when MethodMesh acquired the image on the caller's behalf, so an ODK-supplied image is not returned as a gratuitous duplicate attachment.

Primary file returns are `spectrum_annotated_image_uri`, `spectrum_data_uri`, `spectrum_features_uri`, `spectrum_metadata_uri`, `spectrum_provenance_uri`, `spectrum_manifest_uri` and `spectrum_bundle_uri`. The principal artefacts have SHA-256 companion fields. `methodmesh_full_json` remains the shared FULL transport payload when requested.

The current raster workflow returns an **instrumental spectrum**. `spectrum_instrument_response_corrected=false`; no camera/grating/telescope response correction or absolute flux calibration is implied. Display smoothing is non-destructive and never replaces canonical unsmoothed data.

## `astronomy.spectrum.reference.create`

Reference Commit persists the reusable wavelength solution and creates:

- original reference-star image;
- annotated reference image with fitted trace and confirmed Balmer anchors;
- calibrated `reference_spectrum.csv`;
- `calibration_anchors.csv` with known/fitted wavelengths and residuals;
- reusable `reference.json`;
- `provenance.json`;
- `manifest.json`;
- `star_spectrum_reference_bundle.zip` containing the complete package.

These have independent canonical URI/hash fields in addition to the inline reusable `spectrum_reference_json` value. `spectrum_reference_anchor_source` records whether the committed anchors came from automatic consensus, automatic consensus followed by manual review, or fully manual placement. Reference provenance must also preserve the colour-prior inputs and accepted anchors so the automatic proposal remains auditable rather than becoming an opaque classification result. The caller can therefore use either the compact scalar/model returns, individual typed files, or the full ZIP.

## Native Share / Save

**Share** routes through MethodMesh `ResultShare`, preserving useful human-readable text plus typed attachments. The complete ZIP is included alongside the principal individual files so receivers can use whichever representation they support.

**Save** routes through the shared `OutputExportRepository`, writing the individual artefacts and ZIP into the MethodMesh Downloads package. FULL MethodMesh JSON remains optional rather than being substituted for the domain artefacts.

## ODK

The two module-owned XLSForms in `docs/` expose canonical unprefixed return names. Both use one MethodMesh invocation and `input_payload_mode='FULL'`. Analysis captures provenance/manifest/bundle files, bundle hashes and instrumental-spectrum semantics. Reference creation captures the annotated image, reference spectrum/anchors data, reference/provenance/manifest JSON and ZIP bundle.


## v0.2.23 provenance note

No return-field names change in v0.2.23. Method version advances to `0.2.23`; reference provenance/notes may additionally record that one or more Balmer members were obtained by `targeted_recovery` after a ≥3-line direct automatic consensus. The committed `anchor_source` remains `automatic_consensus`, `automatic_consensus_reviewed`, or `manual`.


## v0.2.24 provenance note

No return-field names change. Method version advances to `0.2.24`. Existing candidate-source provenance continues to distinguish direct detector/multiscale evidence from `targeted_recovery`; the scientific meaning of `anchor_source` remains `automatic_consensus`, `automatic_consensus_reviewed`, or `manual`.

## v0.2.25 provenance note

No return-field names change. Method version advances to `0.2.25`. A manually adjusted automatic proposal continues to report `automatic_consensus_reviewed` across repeated edits. Quadratic fitting cannot be committed unless the same anchor set first passes the linear line-identity sanity check.
