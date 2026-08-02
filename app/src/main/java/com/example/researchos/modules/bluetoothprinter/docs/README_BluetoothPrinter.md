# Bluetooth printer

## Capabilities

`bluetooth_print` sends a text or raw hexadecimal payload to a paired Bluetooth thermal printer. It is intended for small labels, identifiers, and scheduler-triggered print jobs. The first profile targets the common BLE thermal-printer layout exposed by Qutie-like devices: service `FF00` and writable characteristic `FF02`.

## Android intent

```text
com.example.researchos.EXECUTE_METHOD(method_id='bluetooth_print',input_printer_payload='ResearchOS test label',input_printer_payload_format='text',return_mode='flat')
```

The current prototype is primarily interactive so the operator can select and inspect the paired printer. The endpoint UUIDs remain editable because printer firmware variants may differ.

## Inputs

- `input_printer_payload`: text or hexadecimal bytes to send.
- `input_printer_payload_format`: `text` or `hex`.
- `input_printer_device_address`: optional paired-device address.
- `input_printer_service_uuid`: optional GATT service UUID; defaults to the Qutie-style `FF00` service.
- `input_printer_write_uuid`: optional writable characteristic UUID; defaults to `FF02`.

## Outputs

- selected printer name and address;
- service and write characteristic UUIDs;
- payload format and byte count;
- `printer_status` and print time.

## ODK example

Open `example_odk_bluetooth_print.xlsx` and install it in ODK Collect. The workbook demonstrates an explicit `bluetooth_print` intent and fields for the payload and returned status.

## Notes

The endpoint layout is not, by itself, proof that the Qutie firmware accepts ESC/POS commands. The prototype sends a conservative generic thermal-text payload; reliable label graphics, barcodes, and cutter control will require confirmation against the Qutie hardware.
