# MethodMesh XLSForm build report — example_trial_form

- Build ID: `example_trial_form__v20260922215713__build_20260922T215713__28384645`
- Compiler: methodmesh-xlsform 0.3.0
- Compiler implementation SHA-256: `70cdac923e4089626c2943585f0571a98c55f26104e3fdd69a84a4c92901829d`
- Source: `ordinary_trial_form.xlsx`
- Preserved source: `SOURCE__ordinary_trial_form.xlsx`
- Source SHA-256: `283846450ab2dda84c769e6fd99509389dd012aed35e5c0643243c132331d76c`
- Release: `example_trial_form__v20260922215713__methodmesh.xlsx`
- Release SHA-256: `75e662ab3f1e106675c463bee20bc28d1f9ec2ccac3dd49d11bf368695ddd00a`
- Release form version: `20260922215713` (compiler-generated `YYYYMMDDHHMMSS`)
- Compiled at: `2026-09-22T21:57:13+00:00`
- Source authoring version: `2026092201`
- Study ID: `EXAMPLE_STUDY`
- Timestamp policy: `preferred`
- Commitment recipe SHA-256: `e5917846d9854c59444358e05104e35b1b07b62644a9c6de7ffbd512fea8b1e2`

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

- PASS — exact source XLSForm copied into release bundle and source SHA-256 preserved
- PASS — settings form_id preserved and release version generated once at compile time
- PASS — no reserved generated-name collisions
- PASS — source node names globally unique
- PASS — commitment recipe ordering matches generated canonical commitment
- PASS — NFC+PIN authentication wrapper injected
- PASS — source user-interface fields gated on authentication
- PASS — source editable fields become read-only after finalization
- PASS — frozen ODK commitment/hash + live integrity reconstruction injected
- PASS — prior NFC execution reuse attestation injected
- PASS — final required submission guard injected
- PASS — release bundle receives an integrity checksum inventory

## Warnings

- 1 field(s) are explicitly excluded from the attested commitment: display_language

## Known v0.3 limitations

- Repeat groups must be explicitly excluded (`mm_commit=exclude` on `begin_repeat`).
- Binary/media fields cannot yet be content-hashed by this compiler; explicitly exclude them for now.
- Global duplicate survey node names are rejected.
- The compiler does not replace pyxform/ODK Validate. Upload/validate the generated release form before deployment.

## Release rule

Treat the preserved ordinary source XLSForm as the authoring source of truth. Do not hand-edit the generated MethodMesh release workbook; rebuild from source.
The compiler owns the deployed form version: every build writes one local-time `YYYYMMDDHHMMSS` value into both `settings.version` and the attested `mm_form_version`.
Every build is placed in a new release folder. Existing release folders are never overwritten.
