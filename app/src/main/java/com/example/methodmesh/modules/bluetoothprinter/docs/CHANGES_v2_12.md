# v2.12.0 — standalone capability refactor

- Removed the v2.11 module-owned preset-editor experiment completely.
- Removed every requirement for a `HomeScreen`, `MethodMeshModule`, registry, or preset-system patch.
- Restored the standard MethodMesh module contract: methods, RIL bindings, capability screen and typed capability settings only.
- Added `BluetoothPrinterConfig` as the single source of truth for defaults, parsing, validation, persistence, preview and print-job options.
- Moved GATT connection/write state out of the Compose screen into module-local `BluetoothPrinterBleClient`.
- Kept the hardware-confirmed blank-raster eject path with 150 px default.
- Text now centres across the print head by default.
- QR human-readable text retains independent font size, typeface, style and gap controls.
- FF00/FF02/FF01 UUIDs are now fixed by the Qutie driver profile rather than exposed as user settings; a different transport belongs in a different standalone driver.
- Stopped emitting the legacy `printer_label_height` as a setting; it remains accepted as input and returned as a compatibility output.
- Raw hexadecimal mode now explicitly bypasses label composition controls.
- No new external library or application dependency was added.
