# Automatic code scanner

Camera-based automatic detection of QR, Data Matrix, PDF417, Aztec, and common 1D barcode formats through ZXing.

## Capabilities

### `qr.scan`

Opens the camera scanner, detects the code format, hashes the decoded payload, and returns canonical evidence. Despite the historical method identifier, format detection is not restricted to QR.

Other capabilities invoke this scanner through the generic dependency boundary rather than importing its implementation.

## Android intent

Automatic format detection:

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='qr.scan')
```

Restricted formats:

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='qr.scan',input_barcode_formats='DATA_MATRIX|CODE_128')
```

## Inputs

| Input | Required | Description |
|---|---:|---|
| `barcode_formats` | No | Pipe-delimited ZXing format identifiers. Omit for automatic all-format detection. |
| `study_id`, `operator_id` | No | Optional invocation context. |

## Outputs

`qr_payload`, `qr_payload_hash`, `barcode_format`, `qr_scan_time_iso`, and `qr_source`.

The payload remains read-only inside MethodMesh; its SHA-256 hash provides an integrity check.

## ODK example

[`example_odk_qr.scan.xlsx`](example_odk_qr.scan.xlsx) launches automatic scanning and stores the decoded payload, hash, detected format, capture time, and source.
