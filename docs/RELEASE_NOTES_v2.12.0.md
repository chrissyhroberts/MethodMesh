# MethodMesh v2.12.0 — Alpha

Released 2026-10-01. App version 2.12.0 (version code 13). The release includes a debug-signed APK for sideloading and controlled field evaluation.

## NFC credential and attestation workflow

- Keeps NFC verification focused on identifying and authenticating the staff operator.
- Carries credential expiry, verification evidence, and the complete MethodMesh JSON result into ODK.
- Freezes study data before attestation and retains the ordered commitment recipe needed to independently recreate the hash.
- Carries the NFC verification evidence into attestation without coupling MethodMesh to study or provisioner legitimacy; Sentinel remains responsible for that reconciliation.
- Keeps attestation method and schema versions explicit and fail-closed.

## In-app ODK compiler

- Adds the NFC XLSForm compiler as a MethodMesh capability.
- Ports the standalone compiler commitment policy, including `mm_commit` modes, repeat handling, media exclusions, select-list validation, date normalization, and field hashing.
- Removes compiler-only `mm_commit` metadata from released forms.
- Creates the standard `choices/mm_yes` list when required and preserves a single full JSON output field.
- Detects duplicate and reserved survey names before injecting MethodMesh fields.

## Validation

- Focused NFC compiler and contract unit tests: passed.
- Full debug APK build: passed.
- `git diff --check`: passed.

The APK is debug-signed and intended for development, testing and controlled field evaluation.
