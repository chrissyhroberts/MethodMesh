# ODK integration — MethodMesh Paper Bridge

ODK/XLSForm is a first-class invocation surface for the same canonical Paper Bridge methods used by native MethodMesh, presets and protocols.

## Shared rules

- Canonical showcase workbooks contain exactly one MethodMesh invocation each.
- Use `com.example.methodmesh.EXECUTE_METHOD` with the canonical method ID.
- Canonical examples use unprefixed canonical return names and do not set `methodmesh_return_namespace`.
- Handled round-trips request `input_payload_mode='FULL'`, use `return_mode='flat'`, and capture `methodmesh_status` plus `methodmesh_full_json`.
- Interactive Commit returns directly to the calling form; Cancel returns cancellation.
- ODK owns form/submission persistence. Paper Bridge does not make an extra output-folder save merely because it was called by ODK.
- Binary outputs are attachment-compatible XLSForm fields and must arrive as caller-readable Android attachments, not as private paths.

## Integration Card — `paper.form.transcribe`

**Status tags:** Development · OFFLINE  
**Showcase:** `example_odk_showcase_paper_form_transcribe.xlsx`  
**Acquisition:** interactive camera/document scan or file selection; a caller may also supply `source_image_uri`.  
**Persistence:** working extraction is transient; ODK owns submission persistence. Native recent-activity tracking is not written for external round-trips.

**Intent**

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='paper.form.transcribe',input_paper_template_json=${paper_template_json},input_source_mode='camera',input_auto_accept_omr='true',input_auto_accept_ocr='false',input_payload_mode='FULL',return_mode='flat')
```

**Declared inputs / modifiers**

- `input_paper_template_json` — required versioned Paper Bridge manifest for the intended paper layout.
- `input_source_mode` — `camera` or `file_picker` when MethodMesh acquires the page.
- `input_source_image_uri` — optional caller-supplied page image.
- `input_auto_accept_omr` — allow schema-valid unambiguous OMR auto-acceptance.
- `input_auto_accept_ocr` — allow constrained numeric OCR auto-acceptance; free text remains review-first.
- `input_return_source_image`, `input_return_rectified_image` — accepted legacy compatibility inputs; not required by the canonical showcase call.

**Canonical stable returns**

- `paper_status` — text
- `paper_template_id` — text
- `paper_template_version` — text
- `paper_values_json` — text/JSON
- `paper_dynamic_fields_json` — text/JSON
- `paper_field_count` — integer
- `paper_auto_accepted_count` — integer
- `paper_reviewed_count` — integer
- `paper_unresolved_count` — integer
- `paper_source_image` — image attachment when MethodMesh acquired the source; blank for a caller-supplied source already owned by ODK
- `paper_rectified_image` — image attachment
- `paper_template_sha256` — text
- `paper_source_sha256` — text
- `paper_rectified_sha256` — text
- `paper_extraction_audit_json` — text/JSON
- `paper_manual_edits_json` — standalone manual edit / NA override event ledger
- `paper_manual_edit_count` — integer
- `paper_logic_checks_json` — per-field XLSForm relevance/required/constraint evaluation
- `paper_logic_violation_count` — integer
- `paper_logic_unknown_count` — integer; unsupported/unresolved expression checks
- `paper_na_fields_json` — JSON array of fields explicitly overridden to `na`
- `paper_attachment_metadata_json` — text/JSON, URI-free attachment metadata
- `paper_scan_time_iso` — text
- `paper_error` — text
- `methodmesh_status` — shared handled-roundtrip status
- `methodmesh_full_json` — shared complete structured/audit projection

**Dynamic canonical returns**

Every field declared by the versioned Paper Bridge manifest is also projected under its exact field name. The XLSForm must use the corresponding compatible question type. For example the bundled manifest returns `participant_id` as text, `visit_number` as integer, `temperature_c` as decimal and `site_sketch` as an image attachment.

**Return placement and runtime contract**

The showcase puts one `body::intent` invocation on the `scan_paper` begin-group row and places return fields as children of that group. MethodMesh owns acquisition, registration, recognition and review. Commit freezes and immediately returns the canonical payload to ODK.

For a manifest-defined dynamic image name such as `site_sketch`, generic MethodMesh transport must classify the returned `content://` value as a binary attachment even though the field name does not contain `image`, `file` or `_uri`. The required generic transport patch is documented in `SHARED_FRAMEWORK_DEPENDENCY.md`.


Paper Bridge templates generated from an imported XLSForm preserve field/group relevance, dynamic required expressions, raw constraints and constraint messages. Supported expressions are re-evaluated after extraction and after each manual edit. Unsupported JavaRosa/XPath constructs are returned as `logic_unknown` audit state rather than guessed. A confirmed **Set NA** operator action is represented explicitly in the manual-edit and NA audit outputs.

## Integration Card — `paper.form.design`

**Status tags:** Development · OFFLINE  
**Showcase:** `example_odk_showcase_paper_form_design.xlsx`  
**Acquisition:** interactive Paper Form Designer.  
**Persistence:** Save schema explicitly persists the reusable Paper Bridge schema; ODK separately owns its calling form/submission.

**Intent**

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='paper.form.design',input_payload_mode='FULL',return_mode='flat')
```

**Declared inputs / modifiers**

No capability-specific direct input is required; design is an interactive visual workflow.

**Canonical returns**

- `paper_design_status` — text
- `paper_template_json` — text/JSON
- `paper_template_id` — text
- `paper_template_version` — text
- `paper_template_json_uri` — file attachment
- `paper_template_yaml_uri` — file attachment
- `paper_template_bundle_uri` — file attachment
- `paper_design_template_image` — image attachment
- `paper_design_prepared_form_image` — image attachment
- `paper_design_markup_image` — image attachment
- `paper_design_field_count` — integer
- `paper_design_odk_field_count` — integer
- `paper_design_error_count` — integer
- `paper_design_warning_count` — integer
- `paper_design_source_type` — text
- `paper_design_error` — text
- `methodmesh_status` — shared handled-roundtrip status
- `methodmesh_full_json` — shared complete structured/audit projection

**Return placement and runtime contract**

The showcase puts one `body::intent` invocation on the `design_paper` begin-group row and places return fields as children. The visual lifecycle is SAVE SCHEMA → TEST EMPTY → TEST DATA → COMMIT. Commit returns directly to ODK; the native post-Commit Share/Save surface is not inserted into the round-trip.

### NA override and typed ODK fields

Paper Bridge records a confirmed irreconcilable value as the literal `na` in `paper_values_json` and identifies the affected variables in `paper_na_fields_json`. The corresponding flat dynamic return also carries `na`. An XLSForm target that is intrinsically numeric, select-only, image-only, or otherwise unable to represent that literal must therefore provide an explicit NA-compatible representation (for example an `na` choice or companion status field), or consume `paper_na_fields_json` rather than relying on coercion. Paper Bridge does not silently convert a confirmed NA override into a plausible numeric/choice value.
