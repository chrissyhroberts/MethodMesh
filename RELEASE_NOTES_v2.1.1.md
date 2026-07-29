# ResearchOS 2.1.1

This patch release contains the scheduler and direct-capability execution work from the current release cycle.

## Highlights

- Configurable scheduled chains for ODK/Kobo forms, web forms, ResearchOS capabilities, and clipboard outputs.
- Typed capability settings in scheduler action cards.
- Running/paused schedule state with chain-level switches.
- Direct test execution, chain export/import, retries, background alarm recovery, and boot/time-change re-arming.
- Notification taps dispatch the scheduled action directly.
- Chained clipboard outputs accumulate in execution order.
- Calibrated-scale settings and device calibration are preserved for scheduled launches.
- QR/barcode scanning is protected against duplicate scanner re-entry.
- Capability modules publish their scheduler configuration schemas.

## Validation

- `testDebugUnitTest` passed.
- `assembleDebug` passed.
