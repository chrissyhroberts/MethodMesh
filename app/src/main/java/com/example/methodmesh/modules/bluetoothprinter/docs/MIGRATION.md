# Migration to v2.12

## From v2.7–v2.10

Overlay the v2.12 `bluetoothprinter/` files. Existing settings remain compatible.

## From the experimental v2.11 package

Do **not** install `integration/methodmesh_module_owned_preset_editor.patch` from v2.11.

v2.12 contains no integration patch and does not use `capabilityPresetEditors()`.

If the v2.11 core patch was previously applied, revert those core changes separately so `MethodMeshModule`, `HomeScreen`, and the preset system return to the repository architecture. The v2.12 module itself requires no corresponding core change.

## Existing XLSForm example

Retain the repository's existing binary file:

`docs/example_odk_bluetooth_print.xlsx`

when overlaying this source package.

## Legacy BLE endpoint overrides

v2.12 treats FF00/FF02/FF01 as part of the Qutie driver definition rather than user configuration. Legacy `printer_service_uuid`, `printer_write_uuid`, and `printer_notify_uuid` inputs are therefore ignored by this driver. A printer that genuinely needs different endpoints should be implemented as a separate Bluetooth-printer capability/driver rather than changing this one at runtime.
