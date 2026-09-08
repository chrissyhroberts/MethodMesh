# Protocol provenance and attribution

This MethodMesh module is an independent Kotlin implementation. It does not embed a vendor SDK and does not copy code from unlicensed repositories.

The following public reverse-engineering work was used as protocol documentation and/or corroboration:

## ChiaraCannolee/thermal-pocket-printer-basic

Repository: https://github.com/ChiaraCannolee/thermal-pocket-printer-basic

The repository documents the C&Co 3128 / DP-L1S and the wider LuckPrinter family, including:

- BLE service `FF00`, write characteristic `FF02`, notify characteristic `FF01`;
- the `10 FF` control-command family;
- printer enable (`10 FF F1 03`) and stop (`10 FF F1 45`);
- density configuration (`10 FF 10 00 n`);
- label-positioning commands;
- use of uncompressed ESC/POS `GS v 0` raster data;
- a documented normal/label print sequence.

That repository is MIT-licensed. Its licence notice is reproduced in `THIRD_PARTY_NOTICES.md`. MethodMesh reimplements the documented protocol in Kotlin rather than incorporating its Python/HTML source code.

## thomashermine/makeid-labelprinter-l1-bluetooth

Repository: https://github.com/thomashermine/makeid-labelprinter-l1-bluetooth

Used as independent corroboration for the FF00/FF01/FF02 printer family and the practical value of write-without-response behaviour for BLE print streaming. The repository is MIT-licensed; its licence notice is reproduced in `THIRD_PARTY_NOTICES.md`.

## tomLadder/thermoprint

Repository: https://github.com/tomLadder/thermoprint

Used as corroborating reverse-engineering research for the broader observation that low-cost Bluetooth thermal printers are frequently rebadged, protocol-specific devices and benefit from explicit device profiles. The repository currently identifies itself as MIT-licensed. MethodMesh does not copy source code, text, or other copyrightable implementation material from this repository; it is cited for provenance and context only.

## Standards

The bitmap job uses the established ESC/POS `GS v 0` raster command format. Protocol command bytes and BLE UUIDs are interoperability facts; nevertheless, the reverse-engineering projects above are credited because they made the relevant device behaviour discoverable and testable.

## QR and barcode rendering (v2.6)

QR and Code 128 generation calls the ZXing API already used elsewhere in MethodMesh (`MultiFormatWriter` / `BarcodeFormat`). No ZXing source is copied into this replacement module and this update introduces no new binary dependency. ZXing licensing remains governed by the parent MethodMesh application's existing dependency and notice handling.


## Hardware validation in MethodMesh

The final Qutie-specific behaviours in v2.7-v2.8 were established empirically on the user's printer rather than inferred from third-party source: the tested device requires blank raster rows for reliable post-print paper advance, and approximately 150 px is the best calibrated head-to-exit eject distance for that unit.
