# Automatic code scanner

Camera-based automatic detection of QR, Data Matrix, PDF417, Aztec, and common 1D barcode formats through ZXing.

## Capabilities

### `barcode.scan`

Opens the camera scanner, detects the code format, hashes the decoded payload, and returns canonical evidence. Despite the historical method identifier, format detection is not restricted to QR.

Other capabilities invoke this scanner through the generic dependency boundary rather than importing its implementation.

## Android intent

Automatic format detection:

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='barcode.scan',return_mode='flat')
```

Restricted formats:

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='barcode.scan',input_barcode_formats='DATA_MATRIX|CODE_128',return_mode='flat')
```

## Inputs

| Input | Required | Description |
|---|---:|---|
| `barcode_formats` | No | Pipe-delimited ZXing format identifiers. Omit for automatic all-format detection. |
| `study_id`, `operator_id` | No | Optional invocation context. |

## Outputs

`barcode_payload`, `barcode_payload_kind`, `barcode_payload_url`, `barcode_format`, `barcode_payload_sha256`, `verification_evidence_format`, `verification_evidence_hash`, `barcode_scan_time_iso`, and `barcode_source`.

The payload remains read-only inside MethodMesh; its SHA-256 hash provides an integrity check.

## ODK example

[`example_odk_barcode.scan.xlsx`](example_odk_barcode.scan.xlsx) launches automatic scanning and stores the decoded payload, detected format, capture time, and hidden audit/hash fields.
