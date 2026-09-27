# MethodMesh XLSForm compiler — v0.3.0

`mmxls` turns an ordinary ODK XLSForm into a MethodMesh-authenticated and attested release form.

The design goal is that form authors continue to write **normal ODK XLSForms**. Cryptographic commitment recipes, NFC/PIN plumbing, finalization logic and `attestation.create` calls are generated. **Every supported CLI compilation is now a Sentinel task event**: v0.3.0 runs the deterministic XLSForm transformation through a generic `form.compile` task wrapper, emits a machine-readable task receipt, creates a new self-contained release bundle and never overwrites an earlier release bundle.

## Authoring surface

### 1. Optional `mm_commit` column on `survey`

Most rows can be left blank (`auto`). Accepted values:

| value | meaning |
|---|---|
| blank / `auto` | compiler chooses the safe default for the XLSForm type |
| `value` | commit the canonical scalar value where the compiler knows this is unambiguous |
| `sha256` | commit SHA-256 of the ODK lexical string |
| `exclude` | submit the field normally but do not include it in the attested commitment |

Typical defaults:

- integer/decimal/date/range: value
- select_one: stored choice name
- text: SHA-256
- select_multiple: SHA-256 of ODK's stored selection-order representation
- dateTime/time/geopoint/geotrace/geoshape/barcode/rank: SHA-256 of the ODK lexical representation
- calculate: excluded unless explicitly `sha256`
- notes/groups/metadata: excluded
- media/file: build fails unless explicitly excluded in v0.3
- repeats: build fails unless the `begin_repeat` row is explicitly `mm_commit=exclude`

Exclusions are always visible in the generated manifests.

### 2. Recommended `methodmesh` sheet

| key | value |
|---|---|
| enabled | true |
| study_id | CIMC |
| description | Baseline clinical assessment form |
| authentication | nfc_credential |
| timestamp_policy | preferred |

`description` is optional and is used in the human-readable release manifest. `study_id` can instead be supplied on the command line.

## Install

On macOS, double-click `install.command`, or:

```bash
cd methodmesh_xlsform_compiler
python3 -m venv .venv
source .venv/bin/activate
pip install -e .
```

## Compile

Easiest on macOS: double-click `compile_form.command` and provide the ordinary `.xlsx` XLSForm.

CLI:

```bash
mmxls compile my_form.xlsx -o methodmesh_build
```

## Release bundles and provenance

Every compilation creates a **new uniquely named directory**. Previous release directories are never overwritten.

Example:

```text
methodmesh_build/
  example_trial_form__v20260922181542__build_20260922T181542__a1b2c3d4/
    SOURCE__ordinary_trial_form.xlsx
    example_trial_form__v20260922181542__methodmesh.xlsx
    MANIFEST.md
    commitment_manifest.json
    BUILD_REPORT.md
    sentinel_task_event.json
    CHECKSUMS.sha256
```

The folder name contains:

- deployed `form_id`;
- compiler-owned release version (`YYYYMMDDHHMMSS`);
- compile timestamp to the second;
- first eight characters of the exact source XLSForm SHA-256.

If an identical build identifier already exists, the compiler creates `__02`, `__03`, etc. It does **not** replace the previous release.

### Files in every bundle

- **`SOURCE__...xlsx`** — byte-for-byte copy of the exact authoring XLSForm supplied to the compiler.
- **`...__methodmesh.xlsx`** — MethodMesh-aware XLSForm to validate and deploy to ODK Central.
- **`MANIFEST.md`** — human-readable release/provenance manifest: what the form is, what the compiler added, commitment/exclusion policy, hashes, warnings, limitations and verification procedure.
- **`commitment_manifest.json`** — machine-readable commitment and provenance manifest for tooling/Sentinel.
- **`BUILD_REPORT.md`** — compiler checks and field-level decisions.
- **`sentinel_task_event.json`** — machine-readable Sentinel `form.compile` execution receipt containing request identity, timings, implementation identity, input hash, outputs and artifact hashes.
- **`CHECKSUMS.sha256`** — checksum inventory for the substantive files in the bundle, including the Sentinel task receipt.

On macOS/Linux, verify a preserved bundle with:

```bash
cd <release-folder>
shasum -a 256 -c CHECKSUMS.sha256
```

The checksum inventory is an integrity inventory, not by itself an external digital signature or trust anchor.

## Automatic release versioning

The compiler owns the deployed ODK form version. On every build it captures one local compile timestamp and generates:

```text
YYYYMMDDHHMMSS
```

The exact same value is written into:

- generated `settings.version`; and
- committed `mm_form_version`.

The source XLSForm `settings.version` is optional and, when present, is retained only as authoring provenance. Do not manually re-version or edit the generated release workbook; rebuild from the source form instead.

## What the compiler injects

- NFC credential + PIN MethodMesh call before protected form UI;
- authentication gate for source questions/groups;
- read-only locking after finalization;
- per-form UUID context;
- deterministic ordered commitment over study/form/authentication context and committed source fields;
- generated `methodmesh.commitment_recipe.v1`;
- SHA-256 freeze using `once(...)`;
- live reconstruction check against the frozen hash;
- `attestation.create` reusing the earlier NFC verification execution;
- namespaced `mm_auth_*` and `mm_att_*` return fields and FULL JSON evidence;
- final `READY TO SUBMIT` calculation;
- required submission guard blocking completion unless live data, frozen ODK hash and returned MethodMesh attestation agree.

## Sentinel task boundary

`mmxls compile` no longer calls the XLSForm transformer directly. It calls `run_form_compile_task(...)`, which is the first Sentinel task-engine entry point. The transformation primitive remains separate in `compile_xlsform(...)`; future desktop and VM/API front ends should invoke the same Sentinel task contract rather than duplicate compilation logic.

The task receipt schema is `methodmesh.sentinel.task_event.v1` with task type `form.compile` and task schema version `1`. In standalone CLI use the operator may be unknown; a future authenticated Sentinel VM service can populate the same contract with the authenticated operator and persist the event into the ODK trial sidecar project.

On compilation failure, a failed task event is written beneath `_sentinel_failed_tasks/` in the selected output directory before the error is re-raised.

## MethodMesh provenance context

Generated MethodMesh calls now pass the global provenance-envelope context that the compiler actually knows: `study_id`, `form_id`, `form_version`, and `form_instance_id`. The compiler-generated per-form workflow UUID is mapped to `form_instance_id`; it is **not** asserted to be a visit or event identifier. `visit_id` and `event_id` are left absent unless a future explicit study mapping supplies them.

The attestation call continues to pass the NFC-verified credential subject as `operator_id` and the earlier NFC execution as `input_verification_execution_id`. MethodMesh retains the distinction between request-context actor identity and the stronger NFC verification evidence.

## Machine-readable provenance

`commitment_manifest.json` v2 records, among other things:

- unique build ID and bundle filenames;
- compiler semantic version;
- SHA-256 fingerprint of the installed compiler Python implementation;
- exact source filename/hash and preserved source filename;
- compile timestamp;
- deployed form ID/version/study ID and optional human description;
- MethodMesh capability configuration;
- complete generated commitment recipe and recipe SHA-256;
- committed fields/transforms;
- explicit and automatic exclusions;
- generated release workbook SHA-256;
- warnings and known compiler limitations;
- the associated Sentinel task request ID and task-event filename.

`sentinel_task_event.json` records the execution of the compilation itself and is intended for direct sidecar ingestion by Sentinel.

## Human verification summary

`MANIFEST.md` describes how to verify both the release bundle and a resulting ODK submission. In summary, a submission verifier should reconcile the study/form/version, reconstruct the canonical commitment, check the ODK payload hash, confirm the MethodMesh attested hash matches it, verify the NFC-credential evidence and MethodMesh signature, and validate RFC 3161 evidence according to the release timestamp policy.

## Current fail-closed boundaries

The compiler intentionally fails rather than guessing when it cannot make a strong deterministic commitment:

- repeats must currently be explicitly excluded;
- binary/media content hashing is not implemented yet;
- globally duplicate survey node names are rejected;
- pyxform/ODK Validate are still required before deployment.

## Tests

```bash
python -m unittest discover -s tests -v
```

## Release notes

### v0.3.0

- MethodMesh invocation context upgraded for envelope v3: `study_id`, `form_id`, `form_version`, `form_instance_id`;
- removed the incorrect mapping of the workflow UUID to `visit_id`;
- compiler-owned ODK release versions now use second resolution (`YYYYMMDDHHMMSS`);
- added generic Sentinel task schema `methodmesh.sentinel.task_event.v1` / `form.compile`;
- CLI now runs compilation through the Sentinel task engine rather than directly calling the transformer;
- successful builds emit `sentinel_task_event.json`; failed tasks emit a machine-readable failure event under `_sentinel_failed_tasks/`;
- release checksum inventory covers the Sentinel task receipt;
- commitment manifest links the release to its Sentinel task request/event;
- added regression tests for provenance-envelope context and Sentinel task receipts.

### v0.2.0

- each compilation creates a uniquely named, non-overwriting release folder;
- exact source XLSForm bytes are preserved in the release bundle;
- added human-readable `MANIFEST.md` with form purpose, additions, commitment/exclusion summary and verification instructions;
- machine-readable commitment manifest upgraded to v2 with build identity, compiler implementation fingerprint and recipe SHA-256;
- added `CHECKSUMS.sha256` integrity inventory;
- added regression tests for bundle completeness, checksums and non-overwriting release behavior.

### v0.1.3

- release form versions generated automatically as local-time `YYYYMMDDHHMM`;
- same generated value written to `settings.version` and committed `mm_form_version`;
- source `settings.version` optional and retained only as provenance.

### v0.1.2

- generated commitment-recipe members always include the mandatory `type` property required by `attestation.create`.

## Evidence-first NFC authentication (v0.4)

Generated study forms do not carry or require a plain-text issuer whitelist.

The field authentication gate answers one bounded question: did MethodMesh verify a
well-formed NFC credential, its issuer signature, and its PIN? The generated form
captures the actual credential/issuer evidence returned by MethodMesh and binds the
following into the frozen ODK commitment:

- SHA-256 of `credential_id`;
- SHA-256 of `credential_subject_id`;
- the full SHA-256 issuer public-key fingerprint;
- the NFC verification-evidence hash.

Issuer/credential membership in a particular study is a separate central reconciliation
decision. A cryptographically valid credential from an unregistered MethodMesh issuer
can therefore produce a submission while preserving the evidence needed to classify it
centrally as an unknown issuer/credential.

All compiler-generated MethodMesh return rows receive both a label and hint even when
rendered with `hidden-answer`. This is deliberate XLSForm/pyxform compatibility and
prevents generated return nodes from being rejected for missing presentation metadata.

The v0.4 line is based on the Sentinel v0.3 compiler/task foundation, retaining immutable
release bundles, build manifests/checksums and Sentinel `form.compile` task receipts.

