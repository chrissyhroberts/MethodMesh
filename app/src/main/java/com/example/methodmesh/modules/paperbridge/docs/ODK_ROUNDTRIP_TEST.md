# Paper Bridge ODK round-trip test

Status: **required device validation before Production**.

## Canonical showcase files

- `example_odk_showcase_paper_form_transcribe.xlsx`
- `example_odk_showcase_paper_form_design.xlsx`

Each workbook must validate as an XLSForm, contain exactly one `com.example.methodmesh.EXECUTE_METHOD(...)` call, use the canonical method ID, request `input_payload_mode='FULL'`, use `return_mode='flat'`, omit `methodmesh_return_namespace`, and capture `methodmesh_status` plus `methodmesh_full_json`.

## Transcription round-trip

1. Install a MethodMesh build containing the reviewed Paper Bridge module and the generic content-URI attachment transport fix documented in `SHARED_FRAMEWORK_DEPENDENCY.md`.
2. Load the transcription showcase into ODK Collect (and, separately, a Central/Kobo test project where available).
3. Launch the Paper Bridge group.
4. Scan the bundled prepared AprilTag8 paper form using the normal camera/document-scanner route.
5. Resolve any review items.
6. Commit.
7. Verify control returns immediately to ODK without a native Share/Save detour.
8. Verify scalar returns are populated under their canonical group-child names.
9. Verify `methodmesh_status` and `methodmesh_full_json` are populated.
10. Verify `paper_rectified_image` imports into the ODK instance as a real image attachment.
11. Verify dynamic `site_sketch` imports as a real image attachment under the exact manifest field name.
12. Because MethodMesh acquired the camera source, verify `paper_source_image` is also a real attachment.
13. Save/send the ODK submission and verify the returned attachments are present in the submission media.

### Caller-supplied source case

Repeat with an XLSForm/caller path that supplies `input_source_image_uri`. After Commit:

- do not expect Paper Bridge to return a duplicate `paper_source_image` attachment for the already-owned source;
- do expect source hash/audit metadata;
- do expect newly produced derivatives such as `paper_rectified_image` and manifest image ROIs.

## Designer round-trip

1. Load `example_odk_showcase_paper_form_design.xlsx`.
2. Launch the designer group.
3. Import/select a blank paper source and survey/choices workbook.
4. Map fields and Save schema.
5. Pass TEST EMPTY.
6. Complete TEST DATA.
7. Commit.
8. Verify control returns directly to ODK.
9. Verify scalar design returns, `methodmesh_status` and `methodmesh_full_json`.
10. Verify each populated JSON/YAML/bundle output is a real `file` attachment and each populated design image is a real `image` attachment.

## Failure criteria

The round-trip fails if any returned binary output is merely an unreadable/private URI string, if Commit navigates through the dashboard/native result flow before returning, if ODK invocation causes an unrequested MethodMesh output-folder save, if the canonical field names differ from the method contract, or if a handled call omits the shared status/full-JSON fields.

## Logic / manual-edit roundtrip

1. Use an XLSForm-derived paper template with a direct `relevant` expression, a dynamic `required` expression, and at least one constraint.
2. Confirm a value entered for a non-relevant question is flagged before Commit.
3. Confirm a required blank or failed supported constraint is flagged and blocks Commit.
4. Manually edit a value and verify `paper_manual_edits_json` contains one event with candidate, previous value, new value and timestamp.
5. On an unresolved field choose **Set NA**, confirm the dialog, and verify the returned value is `na`, the field is listed in `paper_na_fields_json`, and the event is present in both `paper_manual_edits_json` and `paper_extraction_audit_json`.
6. Verify `paper_logic_checks_json`, `paper_logic_violation_count`, and `paper_logic_unknown_count` return to the calling group.
7. Exercise one unsupported expression and confirm it is marked unknown rather than silently passing or failing.
