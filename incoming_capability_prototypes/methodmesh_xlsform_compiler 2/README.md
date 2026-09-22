# MethodMesh XLSForm compiler — v0.1.0

`mmxls` turns an ordinary ODK XLSForm into a MethodMesh-authenticated and attested release form.

The design goal is that form authors continue to write **normal ODK XLSForms**. Cryptographic commitment recipes, NFC/PIN plumbing, finalization logic and `attestation.create` calls are generated.

## Authoring surface

### 1. Add an optional `mm_commit` column to `survey`

Most rows can be left blank (`auto`). Accepted values:

| value | meaning |
|---|---|
| blank / `auto` | compiler chooses the safe default for the XLSForm type |
| `value` | commit the canonical scalar value (only allowed where the compiler knows this is unambiguous) |
| `sha256` | commit SHA-256 of the ODK lexical string |
| `exclude` | submit the field normally but do not include it in the attested commitment |

Typical defaults:

- integer/decimal/date/range: value
- select_one: stored choice name
- text: SHA-256
- select_multiple: SHA-256 of ODK's stored space-separated selection-order representation
- dateTime/time/geopoint/geotrace/geoshape/barcode/rank: SHA-256 of the ODK lexical representation
- calculate: excluded unless explicitly `sha256`
- notes/groups/metadata: excluded
- media/file: build fails unless explicitly excluded in v0.1
- repeats: build fails unless the `begin_repeat` row is explicitly `mm_commit=exclude`

Exclusions are visible in the generated manifest and build report.

### 2. Add a `methodmesh` sheet (recommended)

| key | value |
|---|---|
| enabled | true |
| study_id | CIMC |
| authentication | nfc_credential |
| timestamp_policy | preferred |

`study_id` can instead be supplied on the command line.

## Install

```bash
cd methodmesh_xlsform_compiler
python3 -m venv .venv
source .venv/bin/activate
pip install -e .
```

## Compile

```bash
mmxls compile my_form.xlsx -o dist
```

or, without a `methodmesh` sheet:

```bash
mmxls compile my_form.xlsx -o dist --study-id CIMC
```

Outputs:

```text
dist/
  my_form_methodmesh.xlsx
  my_form_commitment_manifest.json
  my_form_build_report.md
```

## What the compiler injects

- one NFC credential + PIN MethodMesh call before the protected form UI;
- an authentication gate for source user-facing questions/groups;
- read-only locking of source editable questions after finalization;
- per-form UUID context;
- deterministic ordered commitment over study/form/authentication context plus committed source fields;
- generated `methodmesh.commitment_recipe.v1`;
- SHA-256 freeze using `once(...)`;
- live reconstruction check against the frozen hash;
- `attestation.create` call using `verification_method=NfcCredential` and the earlier MethodMesh execution ID;
- namespaced `mm_auth_*` and `mm_att_*` return fields plus both FULL JSON envelopes;
- final `READY TO SUBMIT` calculation;
- a **required submission guard** whose constraint prevents form completion unless the current data, frozen ODK hash and returned MethodMesh attestation agree.

The source XLSForm remains the authoring source of truth. Do not hand-edit generated release workbooks.

## Important v0.1 limitations

The compiler intentionally fails rather than guessing when it cannot make a strong deterministic commitment:

- repeats must currently be explicitly excluded;
- binary/media content hashing is not implemented yet;
- globally duplicate survey node names are rejected;
- pyxform/ODK Validate are still required before deployment.

These are deliberate fail-closed boundaries, not silent omissions.

## Why text is normally hashed

The canonical commitment uses `|` between members and `=` between key/value. Rather than inventing a fragile escaping language for arbitrary free text, v0.1 inserts `SHA-256(UTF-8 text)` into the canonical commitment. Simple scalar types and stable select-one choice names can be inserted directly.

## Select-multiple semantics

ODK stores multi-select responses as space-separated choice names in the order selected. v0.1 hashes that exact stored representation, so the commitment is reconstruction-stable from the ODK submission but intentionally selection-order-sensitive.

## Tests

```bash
python -m unittest discover -s tests -v
```

## Next planned extensions

1. repeat canonicalization;
2. binary attachment byte hashing;
3. richer provider validation/pyxform integration;
4. localized built-in wrapper strings;
5. a release manifest that Sentinel can ingest directly.
