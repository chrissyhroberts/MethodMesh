# MethodMesh ODK flat-return fix — 2026-09-22

This patch fixes the transport mismatch exposed by the NFC credential study
forms.

## Problem

`methodmesh_full_json` correctly contained identifiers, hashes and timestamps,
but `OutputFormatter` intentionally omits many of those fields from the compact
CORE/FULL flat projection. ODK therefore received blank values for declared
group children such as:

- `credential_id`
- `credential_subject_id`
- `credential_issued_time_iso`
- `credential_envelope_hash`
- `credential_secret_hash`
- `issuer_key_id`
- `credential_verified_time_iso`
- `verification_evidence_hash`

## Fix

The Android/ODK transport now recognises XLSForm group-child names as explicit
caller-declared return placeholders. Canonical MethodMesh inputs remain
namespaced as `input_*` / `input64_*`.

At return time, only caller-declared names that actually exist in the canonical
MethodMesh result (or an explicit selector result) are copied back as flat
extras. This means:

- no NFC-specific exception is added to `OutputFormatter`;
- CORE/native result presentation stays compact;
- the FULL JSON sidecar remains unchanged;
- unknown form fields cannot create outputs;
- repeat runs still refresh previously populated output questions;
- the same behaviour also reaches the existing browser/Enketo bridge because
  it consumes the already-built external flat-return map.

No Master Book version bump is needed: this restores the existing grouped
XLSForm roundtrip contract rather than introducing a new architecture.

## Install

```bash
cd ~/Downloads/MethodMesh_ODK_Flat_Return_Fix_20260922
./INSTALL.sh /Users/icrucrob/AndroidStudioProjects/MethodMesh
```

Then run the two commands printed by the installer.
