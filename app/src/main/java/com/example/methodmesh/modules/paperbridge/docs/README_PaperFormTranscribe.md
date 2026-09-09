# Paper Bridge — `paper.form.transcribe`

**Maturity:** Development  
**Connectivity:** Offline  
**Canonical methods:** `paper.form.transcribe`, `paper.form.design`

Paper Bridge converts a photographed, anchor-marked paper questionnaire into validated structured data and returns it through the normal MethodMesh execution contract. It is designed for paper-to-ODK workflows but the capability is not ODK-specific: the same method is available from the MethodMesh dashboard, direct native execution, presets, protocols and external Android/ODK invocation.


## Visual Paper Form Designer

`paper.form.design` is the normal study-setup path. Raw JSON remains an implementation/export format rather than the expected authoring interface.

The designer workflow is:

1. choose the blank paper questionnaire as a PDF or image;
2. import the matching XLSForm (optional but preferred);
3. Paper Bridge imports compatible ODK variables, types, required flags, simple numeric/regex constraints and select choices;
4. select a variable (and, for OMR, an individual return option) and drag a rectangle directly over the corresponding answer area;
5. resolve any design errors;
6. **Test with completed page** to run the actual rectification/extraction engine against a sample;
7. save the Paper Bridge form. The generated `methodmesh.paper.v1` JSON is created internally and the form becomes the active scanner template.

Supported XLSForm-to-paper mappings are `text`, `integer`, `decimal`, `select_one`, `select_multiple` and `barcode`. Unsupported survey types are skipped with a visible import warning rather than guessed. Choice values are imported from the XLSForm `choices` sheet, so a paper mark is mapped directly to the ODK return value. Complex ODK constraints that cannot be reduced safely are left in ODK and reported as designer warnings.

Designer validation currently detects: missing/unreadable blank source; missing four-corner anchors; invalid/duplicate/reserved variable names; missing ROIs; missing OMR return options; inverted/out-of-page ROIs; regions intruding into anchor safety zones; substantial region overlap; and very small/fragile regions. Saving is blocked while errors remain.

The blank design source is module-owned configuration, not participant data. Completed questionnaire test images still use the transient scanner/cache path and are not stored in the designer library.

### Bundled matched design fixture

The module includes a real matched pair for setup and testing:

- `example_paper_form_for_designer.pdf` — print-ready blank paper questionnaire with four bullseye registration targets;
- `example_paper_form_for_designer.png` — preview of the same paper page;
- `example_odk_showcase_paper_form_transcribe.xlsx` — importable XLSForm with matching variables/choices/constraints;
- `example_template_manifest.json` — the generated/reference manifest for the same layout.

The six shared variables are `participant_id`, `visit_number`, `eaten_today`, `symptoms`, `temperature_c` and `specimen_barcode`. This fixture exercises OCR text, OCR integer, OCR decimal, single-choice OMR, multiple-choice OMR and barcode capture.

## Native Paper Bridge dashboard

### Quick start

For normal native use, Paper Bridge is deliberately a four-step tool:

1. **Form in use** — check that the named paper questionnaire matches the sheet in your hand. The bundled demo is only for testing. Use **Change form** if needed.
2. **Scan page** — photograph the completed sheet with all four corner anchors visible, or choose an existing photo.
3. **Review** — Paper Bridge reads the page. Clear marks may pass automatically; anything ambiguous remains visibly unresolved until the operator confirms it.
4. **Commit answers** — once every field is resolved, Commit freezes the result. If ODK Collect launched Paper Bridge, the values return to the calling ODK form automatically.

Routine ODK use normally skips the dashboard entirely because the calling XLSForm supplies its paper-form definition and return fields. The dashboard is primarily for direct native scanning, testing, changing the active form, and module setup.


When `paper.form.transcribe` is opened from the MethodMesh dashboard, the capability now presents a purpose-built Paper Bridge control surface rather than dropping straight into a scanner. External/ODK launches and native preset runs still enter the acquisition workflow directly.

The dashboard provides:

- a dominant **Scan with camera** action plus **Use an existing image**;
- the active template with template ID/version and field-type counts;
- a module-owned form library plus the visual **Design a form** workflow; raw JSON import/edit remains an advanced escape hatch;
- review-policy controls for OMR and OCR;
- source/rectified image return controls;
- a clear ODK handoff summary showing what Commit will return;
- resume access to a still-open working scan;
- recent committed-scan metadata for operator orientation.

Dashboard persistence is intentionally narrow. `PaperBridgeWorkspace` stores imported template manifests, the active template choice, and recent activity metadata (template/time/field/review counts). It **never stores questionnaire answers, participant IDs or returned images**. ODK/calling workflows remain the authoritative data store.

The scanner/review surface remains the canonical live-working-result → **Commit** boundary. Returning to the Paper Bridge dashboard does not silently commit or submit anything.

## MethodMesh lifecycle

Paper Bridge follows the Master Book working-result lifecycle:

1. configure / receive a versioned paper manifest;
2. capture or select a page image;
3. find all four anchor symbols and rectify the page to canonical coordinates;
4. extract declared fields;
5. review anything ambiguous or policy-required;
6. **Commit**;
7. return/share/save according to launch origin.

Capture and extraction create a mutable working result. **Commit is the only finalisation boundary.** An ODK launch does not return merely because OCR/OMR has produced candidates.

## ODK ownership

Paper Bridge does **not** implement a parallel ODK Central submission client. In the canonical workflow ODK Collect invokes MethodMesh, Paper Bridge returns the committed field values and attachments to the calling form, and ODK owns the form instance, persistence, validation and Central submission.

This is intentional MethodMesh architecture, not a limitation of the scanner.

## Paper manifest

A paper template is a versioned JSON manifest. It binds physical page regions directly to canonical XLSForm field names. There is no intermediate `Q31/Q61/...` decode table.

Minimal shape:

```json
{
  "schema": "methodmesh.paper.v1",
  "template_id": "household_followup_v4",
  "version": "4",
  "page": {"width_px": 1600, "height_px": 2263},
  "anchors": {"search_fraction": 0.24},
  "fields": [
    {
      "name": "participant_id",
      "type": "ocr_text",
      "roi": [0.16, 0.085, 0.49, 0.13],
      "required": true,
      "regex": "[A-Za-z0-9_-]{1,24}",
      "auto_accept": false
    },
    {
      "name": "eaten_today",
      "type": "omr_single",
      "required": true,
      "threshold": 0.18,
      "min_separation": 0.06,
      "options": [
        {"value": "none", "roi": [0.16, 0.205, 0.19, 0.226]},
        {"value": "little", "roi": [0.16, 0.247, 0.19, 0.268]},
        {"value": "some", "roi": [0.16, 0.289, 0.19, 0.310]},
        {"value": "full", "roi": [0.16, 0.331, 0.19, 0.352]}
      ]
    }
  ]
}
```

Coordinates are normalised `[left, top, right, bottom]` positions on the canonical rectified page. Manifest field names must be safe XLSForm node names and cannot begin `paper_` or `methodmesh_`.

Supported field types in this Development implementation:

- `omr_single`
- `omr_multiple`
- `ocr_text`
- `ocr_integer`
- `ocr_decimal`
- `barcode`

## Anchors and rectification

The module preserves the important design idea from `OMR_LSHTM`: known edge/corner fiducials make the page a coordinate-defined instrument rather than an unconstrained document-understanding problem.

This rewrite is native Kotlin and does not copy the Python/OpenCV source. It locates a compact dark connected component in each reserved corner anchor zone, validates four-point page geometry, then uses Android's four-point projective `Matrix` transform to map the photograph to the manifest's canonical page dimensions.

The paper design must reserve quiet areas around all four anchors. If any anchor cannot be identified confidently, or the four points do not define a credible page, extraction fails closed and the operator must rescan/rotate.

## OMR decision rules

For each declared option ROI, Paper Bridge computes a mark score from mean darkness plus the fraction of sufficiently dark pixels relative to the page's estimated white level.

`omr_single` auto-accept requires all of:

- exactly one option above the declared threshold;
- the top score exceeds the runner-up by at least `min_separation`;
- the resulting value passes required/allowed constraints;
- the manifest/global policy permits automatic OMR acceptance.

A multimark is never resolved by choosing the darkest box.

`omr_multiple` selects options above threshold, but any option close to the threshold forces review. A template can require manual confirmation even for otherwise clean mark results.

## ML Kit text/barcode extraction

OCR is performed **after rectification** and only within the field ROI. The Latin ML Kit text model already bundled in MethodMesh is used on-device.

OCR is human-confirmed by default. `auto_accept_ocr=true` or field-level `"auto_accept": true` permits auto-accept only when the candidate passes declared type/range/regex constraints. This does not turn ML Kit confidence into a claim of zero transcription error; uncertainty remains a workflow state rather than a guessed value.

Normalisation is explicit and reproducible. Supported manifest policies are `none`, `trim`, `collapse_whitespace`, `uppercase_trim`, `lowercase_trim` and `digits_only`. Paper Bridge does not silently substitute visually similar characters such as `O→0`.

## Canonical outputs

Stable fields:

- `paper_status`
- `paper_template_id`
- `paper_template_version`
- `paper_values_json`
- `paper_dynamic_fields_json`
- `paper_field_count`
- `paper_auto_accepted_count`
- `paper_reviewed_count`
- `paper_unresolved_count`
- `paper_source_image`
- `paper_rectified_image`
- `paper_template_sha256`
- `paper_source_sha256`
- `paper_rectified_sha256`
- `paper_extraction_audit_json`
- `paper_scan_time_iso`
- `paper_error`

In addition, every manifest field is returned flat using its manifest `name`. These dynamic keys are the direct ODK/XLSForm projection of the stable `paper_values_json` object. They are not independently invented outputs.

Example committed values:

```text
participant_id = P-0041
visit_number = 3
eaten_today = little
symptoms = fever cough
temperature_c = 37.4
specimen_barcode = SPEC-0041
```

## Provenance

Commit freezes:

- template ID/version and SHA-256 of the canonicalised manifest;
- source-image SHA-256;
- rectified-image SHA-256;
- four detected anchors and scores;
- per-field candidate, final value, decision reason and raw OMR scores/OCR text;
- automatic-vs-reviewed status;
- scan timestamp.

`paper_extraction_audit_json` carries the extraction evidence. `input_payload_mode='FULL'` additionally returns the shared `methodmesh_full_json` execution envelope.

## Attachments

`paper_source_image` and `paper_rectified_image` are `content://` values backed by MethodMesh cache. They are not automatic MethodMesh archive saves. Shared Android return transport recognises them as binary image outputs, adds them to `ClipData` and grants temporary read permission to an external caller such as ODK Collect.

The XLSForm showcase uses `image` children with those exact names so ODK can take ownership of the returned media.

## ODK / XLSForm showcase

`example_odk_showcase_paper_form_transcribe.xlsx` contains exactly one MethodMesh invocation. It stores a demo paper manifest in a read-only `paper_template_json` field and launches:

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='paper.form.transcribe',input_paper_template_json=${paper_template_json},input_source_mode='camera',input_auto_accept_omr='true',input_auto_accept_ocr='false',input_return_source_image='true',input_return_rectified_image='true',input_payload_mode='FULL',return_mode='flat')
```

The `field-list` group includes:

- demo manifest-defined fields `participant_id`, `visit_number`, `eaten_today`, `symptoms`, `temperature_c`, `specimen_barcode`;
- canonical Paper Bridge metadata returns;
- source/rectified `image` returns;
- `methodmesh_full_json`.

A real study form replaces the demo manifest and declares child nodes whose names exactly match its paper manifest fields.

## Direct native / presets / protocols

The method ID is the same everywhere. Presets can fix the manifest and review policy while leaving acquisition as the runtime input. Protocols can compose `paper.form.transcribe` with downstream methods that consume the returned values.

The native scanner keeps the instrument hierarchy paper-first: rectified page, extraction/review summary, field review, acquisition controls, then Commit. The native dashboard keeps raw manifest JSON behind an Advanced editor while exposing template selection, scan policy, return policy, ODK handoff and recent non-identifying activity in one place.

## Offline behavior

The core operation is offline. Rectification, OMR, ML Kit OCR/barcode recognition, validation, review and result construction require no network access.

## Dependencies

No new project dependency is required. The module uses dependencies already present in MethodMesh:

- Jetpack Compose / Material 3;
- Android activity result APIs and `FileProvider`;
- bundled ML Kit text recognition;
- bundled ML Kit barcode scanning;
- existing MethodMesh method/runtime/return infrastructure.

## Current Development limits

- one photographed paper page per invocation;
- compact dark corner fiducials in reserved anchor zones;
- Latin-script ML Kit OCR model currently used;
- arbitrary cursive handwriting is not treated as unattended high-assurance extraction;
- direct Central API submission is deliberately outside this capability because ODK owns the record lifecycle;
- production promotion requires device validation against a labelled image corpus and ODK roundtrip testing described in `VALIDATION.md`.
