# ResearchOS 2.1.1

ResearchOS 2.1.1 adds two major pieces of field-workflow infrastructure: direct ODK/Kobo form launching and a scheduler that can build on that launcher.

## ODK and Kobo form launcher

ResearchOS can now inspect locally available forms in ODK Collect and Kobo Collect, show projects and forms for selection, and open the selected form in the correct collection application. The launcher supports explicit project and form selection rather than assuming the currently active project.

## Scheduler

The scheduler creates recurring local workflows for ODK/Kobo forms, web forms, ResearchOS capabilities, and clipboard outputs. Schedules support cron timing, chained actions, configurable capability settings, notifications, retries, pause/resume switches, direct testing, background re-arming after boot or time changes, and individual or chain-level export/import. Chained clipboard outputs accumulate in execution order, and notification taps launch the scheduled action directly.

## Additional runtime improvements

- Calibrated-scale settings and saved device calibration are preserved for scheduled launches.
- QR/barcode scanning is protected against duplicate scanner re-entry.
- Capability modules publish typed scheduler configuration schemas.

## Validation

- `testDebugUnitTest` passed.
- `assembleDebug` passed.
