# MethodMesh XLSForm Compiler v0.4.0 — change summary

This release restores the Sentinel v0.3 compiler/task foundation as the baseline and layers the current evidence-first NFC contract on top.

## Authentication

- No generated caller-editable issuer allow-list.
- Local field authentication requires credential verification, PIN verification and a valid issuer signature.
- Full issuer fingerprint and NFC verification-evidence hash are returned and committed.
- Study membership/authorisation is a later central reconciliation decision.

## ODK compatibility repair

Every generated `mm_auth_*` and `mm_att_*` return question now carries both a label and hint while remaining `hidden-answer`. This directly prevents the pyxform error that a generated survey element has no label or hint.

## Preserved v0.3 behaviour

- Sentinel `form.compile` task receipts.
- Immutable release/provenance folders.
- Second-resolution compiler-owned form versions.
- Correct `study_id`, `form_id`, `form_version`, and `form_instance_id` MethodMesh context.
- Prior NFC verification execution reused by `attestation.create`.
