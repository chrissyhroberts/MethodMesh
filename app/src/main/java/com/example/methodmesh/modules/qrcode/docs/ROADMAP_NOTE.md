# Barcode module roadmap note

## Physical clone output

A committed `barcode.clone` result is intentionally sufficient for a future physical-output action: exact `barcode_payload` plus `barcode_clone_format`, with source/clone provenance retained separately.

A future Bluetooth/label-printer integration should be implemented through the appropriate printer/device capability or transport composition rather than by teaching `barcode.clone` printer-specific commands, paper widths or pairing state.

Before physical cloning is promoted, validate at least: printer resolution/dot density, quiet zones, minimum module/bar width, checksum behaviour where applicable, human-readable text policy, rotation/scaling, darkness/speed, and independent rescanning of the printed result. ZPL/CPCL/ESC/POS or vendor-native rendering decisions belong to the printer side of that boundary.
