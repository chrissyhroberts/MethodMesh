# Paper Bridge — `paper.form.transcribe`

**Maturity:** Development  
**Connectivity:** Offline  
**Canonical method:** `paper.form.transcribe`

Paper Bridge turns a photographed registered paper questionnaire into the same named values that an XLSForm/ODK workflow expects. The capability is the single implementation used by Dashboard/direct native use, Presets, Protocols and ODK/external round-trips.

## Operator workflow

1. Select a Paper Bridge schema or receive one from the caller/preset.
2. Acquire a page from camera/document scan, image picker, or caller-supplied image.
3. Register the page into the schema's canonical coordinate system.
4. Extract the schema's declared ROIs.
5. Re-run imported XLSForm relevance, dynamic-required and supported constraint logic across the whole extracted record before accepting it.
6. Auto-accept only values that satisfy the schema's confidence/constraint rules; false relevance with a populated value, a required blank, or a failed supported constraint becomes a review item.
6. Review every unresolved field on the same capability surface.
7. **Commit** when the transcription is correct.

Before Commit the working result is mutable. Commit freezes the canonical return payload. Commit itself does **not** save an archive or write an output copy.

## Registration

New schemas use `APRILTAG8` with the `tag36h11` family:

- tag 0 — top left;
- tag 1 — top centre;
- tag 2 — top right;
- tag 3 — left centre;
- tag 4 — right centre;
- tag 5 — bottom left;
- tag 6 — bottom centre;
- tag 7 — bottom right.

All detected tag corners are used as geometric correspondences. Paper Bridge estimates a robust page homography, validates spatial distribution and reprojection error, rectifies the acquired page once into the canonical page coordinate frame, and only then applies saved ROIs. Missing tags are tolerated only when the remaining geometry is sufficient for trustworthy registration. Legacy `QR4` and `BULLSEYE4` schemas remain readable.

The technical panel exposes registration mode, detected tags/points and reprojection diagnostics. ROI coordinates are never individually nudged to compensate for bad registration.

## Inputs

| Input | Meaning |
| --- | --- |
| `paper_template_json` | Versioned Paper Bridge template/manifest. |
| `source_mode` | `camera` or `file_picker` for native acquisition. |
| `source_image_uri` | Optional caller-supplied image. |
| `auto_accept_omr` | Permit unambiguous OMR auto-acceptance. |
| `auto_accept_ocr` | Permit constrained numeric OCR auto-acceptance. Free text still requires review. |
| `return_source_image` | Legacy compatibility input; retained for stable contract compatibility. |
| `return_rectified_image` | Legacy compatibility input; retained for stable contract compatibility. |

The canonical showcase call uses only the inputs relevant to the demonstrated workflow.

## Canonical stable returns

`paper_status`, `paper_template_id`, `paper_template_version`, `paper_values_json`, `paper_dynamic_fields_json`, `paper_field_count`, `paper_auto_accepted_count`, `paper_reviewed_count`, `paper_unresolved_count`, `paper_source_image`, `paper_rectified_image`, `paper_template_sha256`, `paper_source_sha256`, `paper_rectified_sha256`, `paper_extraction_audit_json`, `paper_manual_edits_json`, `paper_manual_edit_count`, `paper_logic_checks_json`, `paper_logic_violation_count`, `paper_logic_unknown_count`, `paper_na_fields_json`, `paper_attachment_metadata_json`, `paper_scan_time_iso`, `paper_error`.

The versioned paper manifest additionally declares **dynamic flat return keys**. For example a manifest field named `participant_id` returns as `participant_id`; a manifest image field named `site_sketch` returns as `site_sketch`. The complete structured equivalents remain available in `paper_values_json`, `paper_dynamic_fields_json` and the shared full JSON projection.

## Native committed state

After Commit, native use remains on the Paper Bridge capability screen and exposes:

- every scalar/text result as tap-to-copy;
- image returns as meaningful attachment cards rather than raw URI text;
- **Share**;
- **Save** to the shared MethodMesh **Files** surface;
- optional **Include full JSON / audit**;
- **Done** / origin-aware closeout;
- **Edit** to reopen the working result;
- explicit copy of the full JSON audit payload.

Share and Save default to one human-readable transcription plus the human-facing media products: the registered/canonical completed form and any image/file fields cropped from that form. The raw acquisition/source page remains in the committed audit/ODK contract but is not duplicated into native Share/Save. When media are shared, the transcription remains ordinary Android share text (`EXTRA_TEXT`); only the form image and cropped image/file fields are attachments. Save copies the same frozen set into the shared MethodMesh Files/ArtifactStore as one collection so Files can preview members and export/share the collection as a bundle. Full JSON is optional. Commit does not implicitly persist a duplicate output package.

## Evidence/media semantics

The registered analysis page is a derived output. Manifest-defined image ROIs are media outputs. The source scan is also available to native use.

For ODK/external round-trips:

- when MethodMesh acquires the source image on the caller's behalf, the source image is returned as an attachment;
- when the caller supplied `source_image_uri`, Paper Bridge does not gratuitously return a duplicate of that same source attachment;
- the registered page and manifest-defined image outputs are returned when produced;
- hashes remain available even when an already-owned source attachment is not returned again.

`paper_attachment_metadata_json` contains attachment roles, hashes and registration metadata without embedding private/local URI strings.

## ODK/external behaviour

ODK owns its form instance and submission persistence. An ODK launch uses the normal polished Paper Bridge acquisition/review surface. **Commit returns immediately to the calling form** through the canonical MethodMesh transport; native Share/Save controls are suppressed in that round-trip mode. Paper Bridge does not create an extra MethodMesh output-folder copy merely because ODK invoked it.

See `ODK_INTEGRATION.md` and `example_odk_showcase_paper_form_transcribe.xlsx`.

Dynamic image-field names require the generic MethodMesh return transport to recognise returned `content://` values as binary attachments independently of the field name. The required shared-framework patch is documented in `SHARED_FRAMEWORK_DEPENDENCY.md`; Paper Bridge deliberately does not rename XLSForm fields to work around shared transport.

## State and parity

Working acquisition/review state and the committed payload are distinct. The committed payload is serialised into saveable UI state so normal Activity recreation does not silently mutate or lose it. Configuration settings are projected through the normal capability settings contract, so the same canonical method remains available to direct native use, presets, protocols and ODK.

## Status

The module remains **Development** until representative-device ODK/Kobo attachment round-trips and the target-app integration build have been exercised. The AprilTag8 registration pipeline itself is implemented and bundled examples are intended as deterministic smoke fixtures, not as a Production claim.

## XLSForm logic checks and NA override

When a paper template was designed from an XLSForm, Paper Bridge retains the raw per-question `relevant`, dynamic `required`, `constraint` and `constraint_message` expressions in the paper manifest. Group relevance is inherited by child questions. After extraction, and again after every operator edit, Paper Bridge evaluates the common deterministic XLSForm subset used in field forms: `${field}` references, `.`, comparisons, `and`/`or`/`not`, `selected()`, `count-selected()`, `regex()`, `string-length()`, `contains()`, `starts-with()`, `ends-with()`, `number()`, and boolean literals.

A false relevance with a populated value, a dynamically/static required blank, or a false supported constraint is a review-blocking logic violation. A false relevance with no value is treated as not applicable and does not fail requiredness. Expressions that depend on unsupported JavaRosa/XPath functions, calculated nodes that are not mapped to paper fields, repeats requiring instance context, or other unsupported constructs are marked **logic check incomplete** and written to the audit; they are never silently treated as valid logic.

Every relevant field can be revisited even after automatic acceptance: scalar/choice/code fields expose **Edit**, and fields expose **Set NA** when an operator cannot reconcile the paper value. **Set NA** requires an explicit confirmation dialog, sets the canonical Paper Bridge value to the literal `na`, bypasses the normal required/constraint check for that field, and records the override. A confirmed NA can be replaced again by restoring/editing the detected value where available.

Every actual operator value change is appended to the standalone `paper_manual_edits_json` event ledger. Each event records timestamp, field, original candidate, previous/new value, previous/new reason, relevance/required/constraint state, logic violation/unknown state, NA state and operator confirmation. Confirming an unchanged candidate is a review action but not a manual edit. The same ledger is embedded under `manual_edits` in `paper_extraction_audit_json`; `paper_logic_checks_json`, violation/unknown counts and `paper_na_fields_json` provide standalone machine-readable audit outputs.


### Android share payload

For a committed transcription, native Share uses one logical payload:

- one human-readable beef value, associated with the first shared item only;
- the registered form image and each dynamic media field once;
- `metadata.json` once when **Include full JSON / audit** is enabled.

For `ACTION_SEND_MULTIPLE`, Paper Bridge uses the Android-supported `EXTRA_TEXT`
array form rather than one scalar caption. This prevents receivers that apply a
scalar caption to every stream from duplicating the beef. Mixed media + JSON
shares use a `*/*` envelope and also advertise the concrete member MIME types.
Receiving applications that do not support heterogeneous Android shares may
still choose to reject a member; that is receiver behaviour rather than a
Paper Bridge persistence change.


### Native Share transport — v0.5.5

This revision deliberately concentrates on the reliable **beef + pictures**
handoff.

The payload follows Magnifier/shared-scaffold semantics:

- the transcription is one scalar `Intent.EXTRA_TEXT`;
- one media item uses `Intent.EXTRA_STREAM`;
- several media items use `ACTION_SEND_MULTIPLE` plus an `EXTRA_STREAM` list.

Paper Bridge also supplies the same media URIs in URI-only `ClipData` and grants
read permission to resolved receiving packages. That extra grant propagation is
needed because Paper Bridge commonly shares several `content://` media items.
The beef is never placed in `ClipData`, so Paper Bridge itself supplies it only
once.

The optional provenance-JSON path is retained but is not the focus of this
revision.
