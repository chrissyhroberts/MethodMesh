# MethodMesh v2.1.4 Release Notes

MethodMesh v2.1.4 expands the platform's field-device and workflow capabilities. This release adds simple SMS sending, stronger Bluetooth device exploration, early Bluetooth printer support, offline NFC protocol administration, and new spatial geometry tools for field measurements.

## Highlights

### SMS sending

MethodMesh can now send a caller-supplied SMS message to a caller-supplied phone number.

This is designed for XLSForms and scheduled workflows where the form constructs the final message text, then passes only:

```text
input_sms_phone
input_sms_message
```

The SMS result returns send status, message hash, number of SMS parts, sent time, and any error.

### Bluetooth device inspection and experimental printer support

The Bluetooth inspector can now work more usefully with nearby and paired devices, exposing readable, writable, and notify-capable endpoints.

An experimental Bluetooth printer capability has also been added for Qutie-style mini label printers. This is currently marked as in development: device discovery and payload sending are present, but reliable label rendering still needs vendor-protocol confirmation.

### NFC protocol administration

This release adds NFC protocol administration tools for offline participant progress tracking.

Protocol NFC support now includes provisioning, checking, completion marking, reconstruction, and override workflows, making it possible to maintain a compact protocol state on a participant NFC card even without network sync.

### Spatial geometry tools

MethodMesh now includes early spatial measurement capabilities:

```text
tree_height_measurement
slope_inclination_measurement
geometry_distance_estimation
```

These use phone orientation sensors, camera-assisted targeting, and supplied field references to estimate height, slope, tilt, and distance, with formulas and sensor provenance returned for audit.

## Other Improvements

- Standalone module discovery is now generated from module-owned source files rather than relying on runtime dex scanning.
- The README now includes updated summaries of the current capability set.
- Bluetooth inspector documentation has been expanded.
- NFC protocol documentation has been expanded.
- New example XLSForms have been added for SMS, Bluetooth printing, and spatial geometry.
- Camera and sensor platform helpers were updated to support the new geometry workflows.

## Validation

Built and tested with:

```text
./gradlew testDebugUnitTest assembleDebug
```

Result:

```text
BUILD SUCCESSFUL
```

## Notes

Bluetooth printer support is intentionally experimental in this release. The Qutie printer can be detected and written to, but reliable label layout and feed behaviour require further protocol capture or vendor documentation.
