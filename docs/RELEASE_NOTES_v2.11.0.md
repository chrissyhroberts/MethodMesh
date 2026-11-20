# MethodMesh v2.11.0 — Alpha

Released 2026-09-30. App version 2.11.0 (version code 12). The release includes a debug-signed APK for sideloading and controlled field evaluation.

## NFC credentials

- Enforces authenticated ROSC2 credential expiry, with inclusive calendar-date handling and a UTC-midnight exclusive expiry instant.
- Returns `valid_until_iso` in provisioning and verification results, including expiry failures where the credential can be decrypted.
- Produces explicit failed verification results with human-readable messages such as expired credential and incorrect PIN.
- Separates provisioning into card preparation, PIN creation, PIN confirmation, write, deliberate fresh-tap verification, and final result stages.
- Uses an on-screen PIN keypad and re-arms NFC reader mode between write and verification taps.
- Preserves issuer identity evidence without adding study or provisioner knowledge to MethodMesh; Sentinel remains responsible for later legitimacy reconciliation.

## ODK examples

- Reduces the canonical provisioning and verification examples to necessary inputs, confirmation outputs, issuer evidence, expiry, and one full JSON field.
- Removes the redundant NFC provisioning showcase form.
- Regenerates the packaged ODK template catalogue.

## Validation

- Focused NFC unit tests: passed.
- Kotlin compilation: passed.
- Debug APK build: passed.
- ODK catalogue generation: 415 templates, 0 generator errors.
- `git diff --check`: passed.

The APK is debug-signed and intended for development, testing and controlled field evaluation.
