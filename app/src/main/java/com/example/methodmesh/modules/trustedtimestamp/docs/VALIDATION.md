# Trusted timestamp validation

Review date: 2026-09-08

## Contract checks

- canonical method ID preserved: `integrity.trusted_timestamp`;
- maturity declared: Development;
- connectivity declared: Online only;
- ODK direct text supported;
- ODK direct file input declared/supported through the external transport value;
- ODK interactive file picker/text entry supported when no source is supplied;
- duplicate source is not returned when ODK supplied it;
- MethodMesh-acquired file/text has a conditional return field;
- proof ZIP remains the primary produced artefact;
- ODK examples capture `methodmesh_status` and `methodmesh_full_json`;
- file returns use attachment-compatible XLSForm `file` questions;
- native UI remains on the capability dashboard through result creation and Commit;
- displayed scalar/text runtime outputs are tap-to-copy;
- native file results expose Share/Save/Export actions;
- capability-owned JSON does not include a private cache URI/path.

## XLSForm checks

Both module-owned `.xlsx` workbooks contain `survey`, `choices` and `settings` sheets and preserve their stable `form_id` values.

Intent groups use the canonical action and method ID. Repeated return leaf names are deliberately reused in separate groups; group paths provide the XLSForm namespace. `methodmesh_return_namespace` remains optional for flat projections.

`pyxform` / ODK Validate were unavailable in the review environment, so JavaRosa compilation remains a downstream validation step.

## Android build

Not run: the supplied handoff is a module-only ZIP and does not contain the full MethodMesh Gradle project/shared runtime.
