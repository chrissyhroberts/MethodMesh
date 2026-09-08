# Automatic barcode scanner

Camera-based automatic detection of QR, Data Matrix, PDF417, Aztec, and common 1D barcode formats through ZXing.

## Capabilities

### `barcode.scan`

Canonical capability. Opens the camera scanner, detects the symbology, preserves the exact decoded text, classifies safe HTTP(S) payloads as links, and returns canonical barcode evidence.

### `qr.scan` (deprecated)

Compatibility contract for existing forms. It retains the historical `qr_payload`, `qr_payload_hash`, `qr_scan_time_iso`, and `qr_source` fields and the `qr_payload_utf8_sha256_v1` evidence format. New integrations must use `barcode.scan`.

Other capabilities invoke this scanner through the generic dependency boundary rather than importing its implementation.

## Android intent

Automatic format detection:

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='barcode.scan')
```

Restricted formats:

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='barcode.scan',input_barcode_formats='DATA_MATRIX|CODE_128')
```

## Inputs

| Input | Required | Description |
|---|---:|---|
| `barcode_formats` | No | Pipe-delimited ZXing format identifiers. Omit for automatic all-format detection. |
| `study_id`, `operator_id` | No | Optional invocation context. |

## Outputs

The primary result is `barcode_payload`, containing the exact decoded string. Other fields are:

| Output | Meaning |
|---|---|
| `barcode_payload_kind` | `text` or `url`. |
| `barcode_payload_url` | Present only when the exact payload is an absolute HTTP(S) URL with a host. |
| `barcode_format` | Detected ZXing symbology identifier. |
| `barcode_payload_sha256` | SHA-256 of the exact UTF-8 payload. |
| `verification_evidence_format` | `barcode_payload_utf8_sha256_v1`. |
| `verification_evidence_hash` | Same digest as `barcode_payload_sha256`. |
| `barcode_scan_time_iso` | ISO-8601 capture time. |
| `barcode_source` | Capture provider. |

The scanner does not trim, numerically coerce, or otherwise rewrite payloads. EAN/UPC values therefore retain leading zeros. Non-HTTP schemes and malformed links remain ordinary text. Link opening is always initiated by the user.

Successful scans return one observation. Cancellation returns no result. Empty decoded content returns `Unsupported`; capture failures return a diagnostic without inventing a payload.

## ODK example

[`example_odk_barcode.scan.xlsx`](example_odk_barcode.scan.xlsx) demonstrates the canonical method and stores the payload, payload kind, optional HTTP(S) link, detected format, and payload hash.

[`example_odk_qr.scan.xlsx`](example_odk_qr.scan.xlsx) remains bundled only for compatibility testing of the deprecated contract.
