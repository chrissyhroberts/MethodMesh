# Standalone capability architecture

Version 2.12 deliberately restores the normal MethodMesh module boundary.

## Files

- `BluetoothPrinterModule.kt` — standard module registration only.
- `BluetoothPrinterMethod.kt` — AS100 method and result construction.
- `BluetoothPrinterConfig.kt` — field names, defaults, validation, typed settings metadata and the single configuration model.
- `BluetoothPrinterCapabilityScreen.kt` — all operator-facing printer UI, paired-device selection and live preview.
- `BluetoothPrinterBleClient.kt` — Android BLE/GATT boundary.
- `BluetoothPrinterProtocol.kt` — FF00-family command generation, bitmap composition and raster encoding.

## Boundary rule

Nothing outside `modules/bluetoothprinter/` needs to know that a Qutie printer exists.

The module consumes existing generic MethodMesh interfaces and Android/platform libraries already present in the application. It does not introduce a new registry, renderer, preset editor API, dependency, manifest requirement, or dashboard branch.

## Settings ownership

`BluetoothPrinterConfig` is the canonical configuration model. Incoming capability settings are parsed once into this model. The screen, preview and print-job generator all derive from the same values.

`BluetoothPrinterSettings.schema` is the only settings metadata exported to MethodMesh. FF00/FF02/FF01 endpoint UUIDs are fixed by this driver profile and are not settings. A printer using a different BLE protocol should be implemented as a separate driver/capability.

The legacy `printer_label_height` key is accepted on input and returned as a compatibility output, but new settings use `printer_sticker_length_px` only.
