# ResearchOS Migration Status

This file records the current state of the AS1.00 migration.

## Native ResearchOS methods

- NFC Read
- NFC Write
- Calibrated Scale
- GPS / Locate Target
- Verify Fingerprint / Device Credential

## Device services in use

- NFC device service
- Location device service
- Biometric device service

## Compatibility retained intentionally

- [x] Android package name: `com.example.researchos`
- [x] Android intent package/action strings using `com.example.researchos`
- Legacy `xlsformlab(...)` appearance parser alias

These remain for compatibility and should not be changed without a dedicated app identity migration.

## Cleanup status

- No source-level `Capability` terminology should remain.
- User-facing app name is ResearchOS.
- Theme name is ResearchOS.
- New provenance/output keys use ResearchOS naming.
- New method outputs should avoid XLSFormLab naming unless maintaining compatibility with existing data.

## Phase 1 closeout

All currently shipped interactive methods are now represented as native AS1.00 methods. Remaining cleanup should focus on deleting compatibility scaffolding only when the UI and transport layers no longer depend on the legacy `Method` interface.

## Suggested next work

1. Intent Language and API
2. Personal Protocol Timeline
3. QR / Barcode Method
4. Camera Measurement Method
5. BLE Scanner Method
