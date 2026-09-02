# Bluetooth printer module — Qutie family

Method ID: `bluetooth_print`

This module is a self-contained MethodMesh capability for the Qutie printer and compatible low-cost BLE thermal printers using the FF00/LuckPrinter-style transport.

## Architecture

Everything printer-specific is contained inside `modules/bluetoothprinter/`.

The module uses only MethodMesh contracts that already exist for every capability:

- `MethodMeshModule.as100Methods()`
- `MethodMeshModule.rilBindings()`
- `MethodMeshModule.capabilityScreens()`
- `MethodMeshModule.capabilitySettings()`
- `CapabilityScreenContext.onSettingsChanged()`
- the existing MethodMesh execution/result model

It does not require changes to `HomeScreen`, the module registry, preset UI, settings renderer, or any other capability.

## Current hardware defaults

- BLE service: `FF00`
- write characteristic: `FF02`
- notification characteristic: `FF01`
- print-head width: 96 px
- density: 1
- continuous paper mode
- blank-raster eject: 150 px
- text direction: along the label
- text alignment: centred across the label

The 150 px eject default is hardware-calibrated on the tested Qutie. Blank raster rows are used because post-raster `ESC J` feed was not effective on that firmware.

## Label composition

Text labels support up to two lines, font size, line spacing, Sans/Serif/Monospace, regular/bold/italic/bold-italic, along/across-label orientation, centring, offsets and automatic minimum-length growth.

QR codes can optionally print their payload as human-readable text after the QR. The readable text has its own font size, typeface, style and QR-to-text gap.

Code 128 and raw hexadecimal output are also supported.

## Presets and settings

The module exports typed `MethodSetting` metadata through `capabilitySettings()`. This is the same contract used by other standalone MethodMesh capabilities. The capability screen additionally provides dynamic controls that static settings metadata cannot provide, notably the paired-Bluetooth-device picker and live label preview.

No capability-specific preset implementation exists outside this folder.

## Installation

Overlay the `bluetoothprinter/` directory onto:

`app/src/main/java/com/example/methodmesh/modules/bluetoothprinter/`

Do not apply any core patch.

If your existing module folder contains `docs/example_odk_bluetooth_print.xlsx`, retain that file when overlaying this update. It is a binary example workbook from the existing repository and is not recreated by this source-only package.

## Licensing and attribution

See `ATTRIBUTION.md` and `THIRD_PARTY_NOTICES.md`.
