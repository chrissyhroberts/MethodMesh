# ResearchOS 2.1.3

ResearchOS 2.1.3 adds public Android-app inspection, Bluetooth endpoint inspection, richer scheduling and direct capability execution, protocol-aware NFC support, native image/SVG selection tools, and portable direct-run output export.

## Android app inspector

The new `android_app_inspector` capability provides a safe, user-directed way to explore installed Android applications:

- lists installed launchable applications;
- inspects exported activities, services, receivers, and providers;
- probes common public actions, categories, and URI schemes;
- targets exported activities explicitly;
- sends an action, optional URI, and simple string extras;
- captures result codes, returned URIs, and returned extras;
- saves a tested package/component/action/URI/extras combination as a local integration definition.

This makes it possible to investigate applications such as Peek Acuity Pro and test exported entry points such as visual-acuity activities. The inspector does not bypass non-exported components, permissions, authentication, or private implementation details. It is a discovery and test harness, not a universal reverse-engineering tool.

## Runtime and scheduler refinements

- The scheduler is collapsed by default when the ResearchOS app opens.
- Scheduler state remains available through the expanded central scheduler view.
- Public launcher resolution is more robust for applications whose package-level `MAIN` probe does not resolve directly.
- Schedules can launch ODK/Kobo forms, web forms, or direct capabilities and can chain actions with cron timing, retries, notifications, pause/resume, and import/export.
- Direct capability results can be exported as timestamped JSON with linked attachment files to the default ResearchOS output folder or a user-selected folder.
- Returned image attachments are delivered to ODK/Kobo image questions rather than only exposing internal content URIs.

## New field capabilities

- `bluetooth_device_inspector` discovers nearby BLE devices, enumerates GATT services and characteristics, reads endpoints, samples notification streams, and saves reusable device profiles.
- `protocol_nfc_check` and `protocol_nfc_complete` maintain compact offline protocol-progress state on participant NFC cards while preserving unrelated NDEF records.
- `svg.select` is a native, zoomable SVG polygon selector with single, multiple, and strict ordered-sequence modes, timestamped audit events, reset/backstep controls, and app-private SVG storage.
- `scaled_photo.capture` captures a ruler-calibrated original image, provides configurable macro/orientation/HUD/grid controls, and returns original and annotated attachments plus separate grid-selection data.
- NFC tag read/write/wipe and credential workflows include stronger read-back verification and clearer portable-credential evidence.

## Documentation

- Added module-local app-inspector implementation documentation.
- Added an ODK example workbook for `android_app_inspector`.
- Added module-local documentation and example workbooks for Bluetooth inspection, protocol NFC, scaled-photo capture, and SVG selection.
- Updated the repository README and Android developer guide.
- Documented the limits of generic intent-filter discovery and the expected workflow for combining inspection with application documentation or source review.

## Verification

The release candidate was validated with:

```text
./gradlew testDebugUnitTest assembleDebug
```

Both the unit-test suite and debug APK build passed.
