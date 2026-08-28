# MethodMesh v2.0.0

MethodMesh v2.0.0 is the first cohesive field-capability release of the Android reference application. It establishes independently owned capability modules, direct intent execution from ODK, and cryptographically verifiable field evidence.

## Highlights

- **Capability modules are self-contained.** Each module owns its methods, screens, RIL bindings, documentation, and example ODK XLSForms. The core runtime discovers modules without capability-specific wiring.
- **ODK intent execution is direct.** An externally invoked capability starts its operation immediately and returns to the calling form on completion; the dashboard remains a manual testing environment.
- **Signed event attestation is ready for field testing.** Attestations bind an ODK-supplied SHA-256 form hash to local device verification, an ECDSA signature, a device-wide hash chain, and—when requested—RFC 3161 trusted time evidence.
- **NFC credentials are portable and PIN-protected.** Provisioning writes a signed, encrypted credential to an NFC card; verification checks the card payload, issuer signature, and the holder's 4- or 6-digit PIN on any compatible device.

## Included capabilities

- Calibrated scales, including horizontal, vertical, range, and min/max variants.
- QR, barcode, and Data Matrix scanning.
- NFC tag read, controlled write, wipe, credential provisioning, and credential verification.
- GPS target navigation with live camera-assisted AR guidance.
- Device biometric and credential access control.
- Signed event attestation and chain-anchor export.
- Discrete choice experiments: pairwise comparison, MaxDiff, ranking, points allocation, and conjoint selection.

## NFC improvements

- Supports semantic blank-tag detection, explicit replacement policies, expected-current-content checks, and copied write hashes.
- Adds NDEF wipe using an empty NDEF record; it removes NDEF user content but does not claim forensic erasure of physical memory.
- Adds robust post-write verification. Where a tag resets after writing, MethodMesh requests one confirmation tap and verifies the exact stored content before reporting success.
- Credential envelopes use Argon2id PIN derivation, AES-256-GCM encryption, and P-256 / SHA256withECDSA issuer signatures. PINs and credential secrets are never returned to ODK.

## Compatibility and migration

- The legacy NFC provisioning capability has been replaced by `nfc_credential_provisioning` and `nfc_credential_verification`.
- The compatibility runtime layer has been removed. New integrations should use canonical method IDs and the documented `com.example.methodmesh.EXECUTE_METHOD` intent contract.
- Every public capability ships with module-local implementation documentation and a named example ODK workbook.

## Verification

This release was built and tested with the Android unit-test suite and a debug APK build. NFC credential provisioning and verification were exercised on a physical NFC tag, including confirmation-tap verification after a post-write tag reset.

## Known scope

MethodMesh v2.0.0 is a reference implementation for field testing. Study-specific issuer trust lists, server-side registries, and broader protocol-management workflows remain configuration and deployment work for individual studies.
