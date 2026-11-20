# MethodMesh v2.13.0 — Alpha

Released 2026-10-02. App version 2.13.0 (version code 14). The release includes a debug-signed APK for sideloading and controlled field evaluation.

## External display and presentation

- Adds the reusable `external_display.present` capability.
- Discovers Android presentation displays through `DisplayManager`.
- Launches a clean MethodMesh presentation surface on a selected secondary display using Android display targeting.
- Adds handset-side refresh, blank and stop controls, plus persistent presentation notification controls.
- Reports when no suitable display is available and explains that USB video depends on device hardware, adapter and display support.
- Provides an explicit handoff to Android system casting for whole-device mirroring and third-party apps such as YouTube.
- Does not invent a USB-video transport or use a software screen-capture path.
- Adds module-owned documentation and a canonical ODK showcase workbook.

## ESP mesh controls

- Adds a clear Settings switch for enabling or disabling ESP mesh communications.
- Disabling mesh stops the service, BLE maintenance and active voice transmission.
- Disabling mesh removes the persistent foreground notification and hides the dashboard Talk/Listen control.
- Prevents a disabled mesh service from recreating its foreground notification during startup.

## Limitations

- External display support depends on Android exposing a presentation display. A USB-C port without DisplayPort Alt Mode cannot be made to output video by MethodMesh.
- The initial external presentation surface is generic; capability-specific dashboards such as astronomy remain a follow-up integration.
- Whole-device mirroring remains controlled by Android and the device manufacturer.
- This is an alpha release for development, testing and controlled field evaluation.

## Validation

- Debug APK build: passed.
- Focused ESP mesh and module-discovery tests: passed.
- External-display documentation/ODK architecture checks: passed.
- The full unit suite retains unrelated pre-existing module-boundary failures in Home/ESP mesh and NFC code.

The APK is debug-signed and intended for development, testing and controlled field evaluation.
