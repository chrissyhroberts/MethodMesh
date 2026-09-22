# MethodMesh XLSForm build report — example_trial_form

- Compiler: methodmesh-xlsform 0.1.0
- Source: `ordinary_trial_form.xlsx`
- Source SHA-256: `283846450ab2dda84c769e6fd99509389dd012aed35e5c0643243c132331d76c`
- Release: `ordinary_trial_form_methodmesh.xlsx`
- Release SHA-256: `b2a2895315b9b520b750b16feafbde69a6430481681114166a2a5c543d564707`
- Form version: `2026092201`
- Study ID: `EXAMPLE_STUDY`
- Timestamp policy: `preferred`

## Commitment summary

- Committed source fields: **6**
- Explicitly excluded fields/containers: **1**
- Automatically excluded structural/metadata/calculated fields: **2**

### Committed fields

| Order | Field | XLSForm type | Mode | Transform |
|---:|---|---|---|---|
| 1 | `participant_id` | `text` | `sha256` | `odk-lexical-utf8-sha256` |
| 2 | `visit_date` | `date` | `value` | `yyyy-mm-dd` |
| 3 | `systolic_bp` | `integer` | `value` | `odk-canonical-scalar` |
| 4 | `clinical_outcome` | `select_one outcome` | `value` | `stored-choice-name` |
| 5 | `symptoms` | `select_multiple symptoms` | `sha256` | `odk-selection-order-lexical-utf8-sha256` |
| 6 | `clinical_notes` | `text` | `sha256` | `odk-lexical-utf8-sha256` |

### Explicit exclusions

- `display_language`

## Build checks

- PASS — settings form_id/version present and preserved
- PASS — no reserved generated-name collisions
- PASS — source node names globally unique
- PASS — commitment recipe ordering matches generated canonical commitment
- PASS — NFC+PIN authentication wrapper injected
- PASS — source user-interface fields gated on authentication
- PASS — source editable fields become read-only after finalization
- PASS — frozen ODK commitment/hash + live integrity reconstruction injected
- PASS — prior NFC execution reuse attestation injected
- PASS — final required submission guard injected

## Warnings

- 1 field(s) are explicitly excluded from the attested commitment: display_language

## Known v0.1 limitations

- Repeat groups must be explicitly excluded (`mm_commit=exclude` on `begin_repeat`).
- Binary/media fields cannot yet be content-hashed by this compiler; explicitly exclude them for now.
- Global duplicate survey node names are rejected in v0.1.
- The compiler does not replace pyxform/ODK Validate. Upload/validate the generated release form before deployment.

## Release rule

Treat the ordinary source XLSForm as the authoring source of truth. Do not hand-edit the generated MethodMesh release workbook; rebuild it from source.
