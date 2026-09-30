# MethodMesh v2.10.0 — Alpha

Released 2026-09-27. App version 2.10.0 (version code 11). The release includes a debug-signed APK for sideloading and field evaluation.

## A toolkit for field, lab and everyday work

- Expands MethodMesh as a practical toolkit for people collecting data and making observations in the field, in the lab and in the wider world.
- Makes the “many small apps in one place” use case more explicit: capabilities share a dashboard, artifact layer, presets, protocols, schedules and external/ODK contracts.
- Refreshes conversions, Weather, Signals, QR/barcode, live translation, NFC and device-facing workflows with clearer instrument surfaces, live results, validation notes and updated examples.

## Devices, transport and evidence

- Adds the first MethodMesh Devices implementation pieces, including display capabilities, ESP32-C3 firmware installation assets, device provisioning and ESP mesh join/provisioning flows.
- Extends ESP mesh transport diagnostics, coexistence handling, secure provisioning and BLE-related coverage.
- Adds NFC issuer identity and evidence-first credential assurance, including the canonical public-key fingerprint and public issuer bundle used for later provisioning-device registration.
- Adds quiet trusted-clock refresh scheduling and preserves policy-neutral time/provenance evidence across execution results.

## Widgets and practical UI

- Adds the widget bundle system with compact, frosted and themed shortcut surfaces, expanded bundle views, configuration migration and restart recovery.
- Improves calculator/conversion, weather radar, streaming translation and QR-burst interactions while keeping capability-owned workflows and live readouts coherent.
- Adds an early Display module with clock and timer examples.

## Forms, documentation and repository hygiene

- Regenerates the packaged ODK/XLSForm catalogue from module-owned examples, including new Display, live-translation and Signals examples.
- Updates the MethodMesh Master Book with Devices, the Devices ↔ Field Transport bridge, NFC issuer assurance, instrument dashboard guidance and current implementation boundaries.
- Consolidates the outstanding module sources, tests, firmware, prototype archives and the XLSForm compiler history on GitHub.
- Removes temporary local backups and superseded module-local copies where the current source or generated catalogue is authoritative.

## Validation

- `./gradlew test`: passed; **254 JVM tests**, no failures or skips.
- ESP mesh security invariant self-test: passed.
- Debug APK build: passed.
- ODK catalogue generation: **415 templates**, 0 generator errors. Advisory form-revision findings remain for 141 templates; they do not prevent packaging and are documented follow-up work.
- The APK is debug-signed. Interactive device, NFC, ODK round-trip, ESP hardware and production signing validation remain required before production deployment.

## Known alpha limits

- ESP mesh, physical devices, live translation, Signals, Weather radar and several instrument-style modules remain under active field validation.
- The packaged APK is intended for development, testing and controlled field evaluation, not as a production-signed distribution.
- Transport routing, heterogeneous device support, large-object transfer, issuer registry reconciliation and authoritative ODK validation remain partly implemented or roadmap work.
