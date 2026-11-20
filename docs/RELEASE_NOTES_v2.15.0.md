# MethodMesh v2.15.0 — Alpha

Released 2026-10-16. App version 2.15.0 (version code 16). The release includes a debug-signed APK for sideloading and controlled field evaluation.

## Individual SMS to multiple recipients

- Allows `sms.send` to accept multiple phone numbers separated by commas, semicolons, or new lines.
- Sends one identical SMS separately to each de-duplicated recipient; it does not create a group message.
- Reports overall `sent`, `partial`, or `failed` status.
- Returns the recipient list, recipient count, and per-recipient status/error JSON for audit and workflow use.
- Preserves the existing single-recipient behaviour and message hashing.

## Documentation and validation

- Updates the SMS capability documentation and dashboard labels for multiple recipients.
- Adds unit coverage for recipient separators, blank entries, and duplicate removal.
- Android focused SMS unit test: passed.

The APK is debug-signed and intended for development, testing and controlled field evaluation.
