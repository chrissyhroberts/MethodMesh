# MethodMesh v2.14.0 — Alpha

Released 2026-10-16. App version 2.14.0 (version code 15). The release includes a debug-signed APK for sideloading and controlled field evaluation.

## Document scanner timestamp proof

- Adds `disabled`, `preferred`, and `required` TSA policies to `document.scan`.
- Creates an RFC 3161 proof ZIP for the final returned PDF when enabled.
- Hashes the PDF locally and sends only the SHA-256 digest to FreeTSA; PDF contents remain on-device.
- Returns proof URI, filename, authority, trusted time, subject hash, and failure diagnostics.
- Keeps the scan successful under `preferred` when the TSA is unavailable; `required` fails without proof.
- Moves timestamp acquisition off the UI thread.
- Ensures shared/exported scan delivery contains one canonical PDF rather than page-image or duplicate-PDF attachments.

## ODK mapping

- Updates the bundled document-scanner showcase workbook to request preferred timestamp proof.
- Maps timestamp status, proof URI, authority, time, hash, and error fields.
- Documents the ODK input and output contract.

## Validation

- Android debug Kotlin compile: passed.
- Document-scanner ODK contract test: passed.
- Result-share attachment tests: passed.

The APK is debug-signed and intended for development, testing and controlled field evaluation.
