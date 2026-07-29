# ResearchOS 2.1.0

## Scheduler and direct capability execution

- Added configurable scheduled chains for ODK/Kobo forms, web forms, ResearchOS capabilities, and clipboard outputs.
- Added capability configuration cards so scheduled calls inherit the same typed settings used by the debug runtime.
- Added running/paused visual state and per-chain enable/disable switches.
- Added direct test execution for scheduled chains and chain-level export/import support.
- Chained clipboard outputs now accumulate in execution order instead of overwriting one another.
- Added background-safe alarm re-arming, boot/time-change recovery, retry handling, and stale chain-alarm cleanup.
- Notification taps now dispatch the scheduled action directly.

## Capability and runtime fixes

- External and scheduled calibrated-scale launches now load the saved device calibration before presenting the scale.
- Capability settings are passed through the canonical `input_*` runtime namespace.
- QR/barcode launches are guarded against duplicate scanner re-entry after a scan result.
- Duplicate workflow result delivery is suppressed.
- Capability modules now publish their typed scheduler configuration schemas, including calibrated scale, QR, NFC, attestation, authentication, choice experiments, GPS navigation, and ODK form launch.

## Compatibility

- Existing schedules and persisted scheduler bundles remain readable.
- Existing standalone capability and ODK/Kobo invocation paths are unchanged.

## Validation

- `testDebugUnitTest` passed.
- `assembleDebug` passed.
