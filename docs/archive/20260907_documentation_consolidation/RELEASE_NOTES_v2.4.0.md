# MethodMesh v2.4.0

MethodMesh v2.4.0 consolidates the project-wide documentation model and the module-owned ODK/XLSForm capability library.

## Highlights

- MethodMesh Master Book v1.06 remains the sole normative project-wide documentation source.
- Installed and aligned the reviewed module-owned XLSForm library across 66 modules.
- Reviewed baseline contains 423 XLSForms:
  - 276 focused capability showcase forms
  - 147 broader, workflow-oriented or legacy examples
- Established focused capability showcase naming using `example_odk_showcase_<capability_method>.xlsx`.
- Retained `example_odk_<purpose>.xlsx` for broader, workflow, dashboard and historical examples.
- Preserved deployed `form_id` values as stable external identities.
- Clarified that filename, title, version and `form_id` are separate concerns.
- Added `YYYYMMDDrr` as the preferred version convention for new forms and intentional revisions.
- Tightened XLSForm settings metadata and human-readable title requirements.
- Clarified that XLSForms demonstrate the MethodMesh runtime contract rather than defining a second schema.
- Prevented XLSForms from inventing success fields, timestamps, JSON fields or other outputs not actually exposed by the runtime.
- `methodmesh_full_json` remains the shared complete structured/audit projection.
- Capability-specific scalar, status, time, JSON and media returns are included only where actually declared.
- Module handoff documentation now distinguishes focused capability showcases from broader examples.
- Documentation-hygiene rules reduce future architectural drift.

## XLSForm migration

The reviewed XLSForm installation processed 423 canonical forms:

- 331 installed as new module-owned XLSForms
- 22 replaced older reviewed copies
- 70 were already identical
- 0 canonical reviewed targets were skipped because of local modifications

A subsequent identity-based cleanup identified 118 additional legacy filename variants.

Of these:

- 112 were verified as legacy duplicates by matching `settings.form_id` to exactly one reviewed canonical workbook in the same module and were removed after backup.
- 6 locally modified legacy workbooks were deliberately protected from automatic removal and remain pending module-specific reconciliation.

The protected workbooks are associated with active Arcade, Calibrated Scale, Digital Signing and GameDeck development. They are not part of the canonical reviewed 423-form baseline.

## ODK/XLSForm contract

Module-owned XLSForms remain first-class MethodMesh resources.

Focused capability showcases demonstrate independently callable MethodMesh methods using their existing canonical contracts.

A showcase does not create a parallel API. It uses:

- the canonical method ID;
- declared capability inputs;
- the requested return namespace;
- declared capability/shared transport outputs;
- `methodmesh_full_json` where complete structured/audit return is required;
- Android URI/media transport where appropriate.

Where the shared runtime does not yet expose a desirable universal scalar success/time/JSON envelope, that is recorded as a shared MethodMesh contract gap rather than hidden by inventing XLSForm-specific fields.

## Documentation consolidation

Project-wide MethodMesh doctrine is maintained in:

`docs/METHODMESH_MASTER_BOOK.md`

Module-specific documentation and XLSForms remain owned by each module.

Generated website pages, packaged ODK templates and other projections are not alternative architectural authorities.

Historical project-wide documentation and release notes remain available under `docs/archive`.

## Compatibility

This release deliberately preserves stable MethodMesh method IDs and deployed ODK `form_id` values.

The XLSForm migration is a contract-alignment and documentation-quality exercise, not a wholesale external-identity renaming exercise.

Existing broader examples may coexist with focused capability showcases where both provide useful coverage.

## Production status

Presence in the source tree or XLSForm library does not by itself promote a module to Production.

Production readiness continues to depend on the complete MethodMesh review contract across native execution, presets, protocols, ODK roundtrip, lifecycle behaviour, outputs, state preservation, documentation and relevant device testing.
