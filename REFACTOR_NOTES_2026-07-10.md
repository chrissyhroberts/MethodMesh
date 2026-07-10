# Direct intent and device-platform refactor

## Behaviour

- `qr.scan` now launches a real ZXing camera scanner immediately.
- Successful QR capture is converted by the existing AS100 QR method and returned without a second confirmation screen.
- Cancelling the scanner cancels the calling workflow.
- The final step of an externally invoked workflow now returns directly to the caller; the old redundant return-summary gate is bypassed.
- Existing NFC, biometric and device-credential implementations remain reusable dependencies rather than being duplicated.

## Platform foundations

- Added optional camera, USB host and BLE hardware declarations.
- Added Android 12+ Bluetooth scan/connect permissions plus legacy permissions for older devices.
- Added camera, audio and network permissions needed by future capability backends.
- Added a transport-neutral device service contract and runtime registry covering camera, NFC, BLE, classic Bluetooth, USB, Wi-Fi, Android sensors, location and audio.
- USB, BLE and Wi-Fi use Android platform APIs and therefore do not require third-party Gradle libraries at this stage. Protocol-specific adapters can register against the common service interface later.

## Dependency

- Added `com.journeyapps:zxing-android-embedded:4.3.0`.

## Cleanup

- Removed macOS archive metadata and the manual QR payload-entry screen.
- Retained legacy adapters that are still referenced by the current runtime; removal without migration would break registered capabilities.

## Validation

A Gradle build was attempted, but this execution environment could not download the configured Gradle distribution because outbound network access is unavailable. The project should sync dependencies normally in Android Studio.


## QR capability chaining into attestation

- Added a reusable `rememberQrCapabilityInvocation` boundary owned by the QR module.
- Signed event attestation now invokes that QR capability when `QR token` is selected.
- The camera opens directly; attestation no longer accepts manually typed QR payloads.
- The QR capability returns its canonical `ExecutionResult`; attestation consumes `qr_payload_hash` as verification evidence before signing.
- Cancellation and scanner errors return control to attestation without creating a signed record.
