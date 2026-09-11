# MethodMesh v2.7.0

MethodMesh v2.7.0 is an alpha release that packages the current fieldwork, Paper Bridge, communication, translation and device work into one build. It is intended for practical testing and development, not production deployment.

## Highlights

- Added a native, offline AprilTag detection boundary with CameraX analysis support, all pinned tag families, Android instrumentation coverage, and vendored sources for reproducible Android builds.
- Expanded **Paper Bridge** into a fuller paper-form workflow: template authoring and import, tagged registration, field extraction, review and audit metadata, persistent result bundles, and ODK-facing source/registered-image attachments.
- Added the four-person table conversation translator, language/voice support, and its focused ODK example.
- Expanded **Signals** with optical screen and torch transfer, AprilTag payload support, visual transport helpers, and refreshed focused ODK examples.
- Added the Weather module and its generated ODK catalogue entries.
- Refined ESP mesh gateway provisioning and connected-device support for bench testing with ESP32-C3 hardware.
- Made result sharing consistent across native capabilities, protocol/scheduler closeout, and saved exports: beef is shared as text, media is de-duplicated, and full provenance JSON is an optional sidecar. ODK/external returns always retain full JSON and available media URIs.

## Build and platform changes

- Added the Android NDK/CMake configuration and required foreground-service permission for the native fiducial and connected-device work.
- Added OpenCV for Paper Bridge registration and AprilTag-related image processing.
- Regenerated the module-owned ODK/XLSForm catalogue from the current module sources.
- Updated the Master Book with the native fiducial boundary, artifact/export contract, and current build requirements.

## Validation

- `:app:assembleDebug` passes with the release sources.
- Focused result, external-return, and scheduler unit tests pass.
- The generated XLSForm catalogue is included in the APK. Its structural warnings remain advisory migration work; they do not silently change form IDs or source workbooks.

## Known alpha limits

- Paper Bridge, ESP mesh, Signals, Weather, native fiducials, and the table translator remain under active field validation.
- ESP mesh support is suitable for controlled bench tests; production provisioning, encrypted payloads, route discovery, and larger-payload fragmentation are still follow-up work.
- Sharing behaviour ultimately depends on the receiving Android application. MethodMesh supplies a standard text payload and explicitly grants every selected attachment URI.
