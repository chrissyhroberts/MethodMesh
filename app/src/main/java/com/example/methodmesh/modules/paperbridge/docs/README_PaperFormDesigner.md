# Paper Bridge — `paper.form.design`

**Maturity:** Development  
**Connectivity:** Offline  
**Canonical method:** `paper.form.design`

The Paper Form Designer creates and manages versioned Paper Bridge schemas from a blank paper questionnaire plus an XLSForm-style survey/choices workbook. It owns the visual mapping task; the same method is exposed to direct native use, presets, protocols and ODK/external round-trips.

## Lifecycle

The persistent design lifecycle is:

**SAVE SCHEMA → TEST EMPTY → TEST DATA → COMMIT**

- **Save schema** explicitly persists the current named schema and its bound blank-form source. It does not close the editor.
- **Test empty** verifies that an unfilled prepared form does not generate false responses.
- **Test data** exercises the same registration/extraction pipeline using a completed page.
- Editing the design after a test invalidates that test for the changed design.
- **Commit** is enabled only when the current saved design has passed the required tests. Commit freezes the canonical design-result payload.

Because designing a Paper Bridge form is itself a persistence task, Save schema is an explicit durable action. This is distinct from the result lifecycle: Commit does not silently create unrelated output/archive copies.

## Registration produced by the designer

New schemas default to eight unique `tag36h11` AprilTags around the page perimeter. The prepared printable form embeds the required tags and the schema stores their IDs/canonical positions. QR4 and bullseye4 remain supported for legacy schemas.

## Authoring workflow

A normal design run is:

1. open an existing schema or create a new schema;
2. choose/import the blank paper form;
3. import the survey/choices workbook;
4. use colour-authoring envelopes and/or automatic region detection where appropriate;
5. review field/choice mappings and constraints;
6. Save schema;
7. run TEST EMPTY;
8. run TEST DATA;
9. Commit.

The designer returns a portable template JSON plus generated design artefacts. It can be used without the colour authoring convention; colour authoring is an accelerator, not a runtime dependency.

## Canonical returns

`paper_design_status`, `paper_template_json`, `paper_template_id`, `paper_template_version`, `paper_template_json_uri`, `paper_template_yaml_uri`, `paper_template_bundle_uri`, `paper_design_template_image`, `paper_design_prepared_form_image`, `paper_design_markup_image`, `paper_design_field_count`, `paper_design_odk_field_count`, `paper_design_error_count`, `paper_design_warning_count`, `paper_design_source_type`, `paper_design_error`.

The JSON/YAML/bundle and design images are real file/media outputs, not clipboard-oriented URI strings.

## Native committed state

After Commit the native designer remains on its committed surface and exposes:

- tap-to-copy scalar identifiers/counts;
- **Share** of the useful summary plus generated design artefacts;
- **Save** to the shared MethodMesh **Files** surface;
- optional **Include full JSON / audit**;
- **Done** with launch-origin-aware closeout;
- **Edit design**;
- **Copy template JSON**.

## ODK/external behaviour

An external/ODK launch opens the same visual designer. Commit returns the canonical design payload directly to the calling form and finishes the MethodMesh round-trip. It does not detour through the native share/save surface. ODK owns the calling form/submission; Paper Bridge owns its explicitly saved reusable schema.

The canonical example is `example_odk_showcase_paper_form_design.xlsx`. See `ODK_INTEGRATION.md`.

## Deletion

Deleting a saved Paper Bridge schema is a destructive action and requires confirmation. The UI states that deletion removes the saved schema/bound blank-form source; it does not claim to delete completed ODK submissions.

## Lifecycle restoration

Meaningful in-progress editor state is encoded in saveable state: selected source identity, imported ODK schema, field mappings, loose regions, selection/editing controls, test state and registration configuration. Large bitmaps are reloaded/rederived from the persistent source identity after ordinary Activity recreation rather than serialised into the state bundle. Committed design values are separately frozen and saveable.

## Status

The module remains **Development** pending target-app integration build and representative ODK/Kobo device round-trip validation. Documentation and bundled XLSForms describe implemented behaviour rather than implying Production readiness.
