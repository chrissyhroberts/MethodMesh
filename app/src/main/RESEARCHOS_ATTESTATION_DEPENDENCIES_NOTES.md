# Attestation dependency integration

This patch applies the rule that existing capabilities should be reused as dependencies rather than rewritten inside a dependent module.

## Changes

- `modules/attestation` now consumes NFC evidence through the existing NFC module path:
  - `NfcDeviceServiceEffect`
  - `As100NfcReadMethod.readBundle(...)`
- PIN/pattern/phone-password attestation now uses Android device credential through `BiometricAuthHelper.authenticateDeviceCredential(...)`.
- Fingerprint/biometric attestation now requests biometric-only authentication rather than silently accepting device credential fallback.
- Added a standalone `modules/qrcode` capability:
  - `qr.scan`
  - `QrCodeModule`
  - `QrScanCapabilityScreen`
- `AttestationModule.dependencies()` documents dependencies on NFC, QR and Android device credential.
- `ResearchOSModuleManifest` registers `QrCodeModule` explicitly.

## Current QR scanner boundary

The QR capability is deliberately separate from attestation. The current implementation captures QR token payload from the capability UI or an external scanner handoff and emits a canonical QR evidence observation. Camera decoding can now be added inside `modules/qrcode` without touching the attestation module.
