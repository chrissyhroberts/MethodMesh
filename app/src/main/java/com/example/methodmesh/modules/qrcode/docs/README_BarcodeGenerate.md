# Barcode generate

**Canonical MethodMesh authority:** Master Book v1.25 (2026-09-22)
**Module ID:** `barcode`
**Method ID:** `barcode.generate`
**Method version:** `1.0.3`
**Maturity:** Development
**Connectivity:** Offline

`barcode.generate` turns an exact text payload into a scannable code. Presentation may change; the payload does not.

## Native UX

The generated symbol is the primary instrument. As the payload is typed or pasted, the code updates immediately. There is no separate Generate button. Swipe, previous/next and optional Cycle move only through symbologies that can encode the exact payload. A fixed preset format is not silently replaced: an incompatible fixed format shows a clear error and Commit remains disabled.

QR is the default. Generated QR presentation includes the canonical MethodMesh mark in a conservative central white clearance patch with high QR error correction. Arbitrary custom logos are not supported. Data Matrix, Aztec, PDF417 and linear barcodes remain unbranded.

Full-screen presentation raises screen brightness and keeps the screen awake while active, restoring prior window state on exit. **Commit** freezes payload + selected format + generation timestamp. Direct/native runs remain on the capability surface for Share image, Copy image, Save image, Home and Edit; automatic-return origins return on Commit.

The rendered barcode is the primary native artefact. Share sends the PNG first, Copy places the PNG on the Android clipboard, and Save writes the PNG to Downloads. The payload remains tap-to-copy as a secondary scalar result, but native Share/Copy/Save do not create a redundant payload text file. Optional/preset-requested audit metadata is carried in a JSON sidecar. The PNG remains a presentation/export artefact, not a declared canonical ODK output.

## Cross-surface exposure

`barcode.generate` is independently callable through direct native use, capability discovery/dashboard, presets, protocols, supported schedules/widgets and ODK/XLSForm. Fixed settings remain hidden; runtime inputs remain runtime. Preset `HOME`, `SHARE`, and `SAVE` completion policies are honoured, and preset `CORE`, `AUDIT`, and `FULL` payload modes control whether native Share/Save includes a JSON sidecar.

## Inputs/settings

| Key | Type | Required | Meaning |
|---|---|---:|---|
| `barcode_payload` | text | Yes | Exact payload to encode. |
| `barcode_format` | choice | No | Starting/fixed symbology; default `QR_CODE`. |
| `barcode_auto_cycle` | boolean | No | Start timed cycling through compatible formats. |

Supported presentation formats: `QR_CODE`, `CODE_128`, `DATA_MATRIX`, `AZTEC`, `PDF_417`, `CODE_39`, `EAN_13`, `EAN_8`, `UPC_A`, `UPC_E`.

The capability never truncates, pads, normalises or rewrites the payload to make it fit a symbology.

## Canonical outputs

| Output | Meaning |
|---|---|
| `barcode_payload` | Exact encoded payload; core result. |
| `barcode_payload_kind` | `text` or `url`. |
| `barcode_payload_url` | Safe absolute HTTP/HTTPS payload when applicable. |
| `barcode_format` | Committed presentation symbology. |
| `barcode_payload_sha256` | SHA-256 of exact payload. |
| `barcode_generated_time_iso` | Commit/generation timestamp. |
| `barcode_source` | Normally `methodmesh_generator`. |

## ODK Integration Card

### Capability

`barcode.generate`

### Intent call

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='barcode.generate',input_barcode_payload=${barcode_payload_input},input_barcode_format=${barcode_format_input},input_barcode_auto_cycle=${barcode_auto_cycle_input},input_payload_mode='FULL',return_mode='flat')
```

### Canonical returns

`barcode_payload`, `barcode_payload_kind`, `barcode_payload_url`, `barcode_format`, `barcode_payload_sha256`, `barcode_generated_time_iso`, `barcode_source`, plus shared `methodmesh_status` and `methodmesh_full_json`.

Canonical showcase: `docs/example_odk_showcase_barcode_generate.xlsx`. It contains exactly one MethodMesh call, uses canonical unprefixed returns and sets no return namespace.

ODK persists the returned scalar contract and metadata JSON. The native PNG share rendering is deliberately not invented as an XLSForm-only field.

## Permissions and persistence

No camera or network permission is required for generation. There is no barcode wallet/history. Reusable cards/tokens belong in presets rather than a private generator store.

## Capability-owned presets

The generator exposes **Save current setup as preset** in the top preamble, before the barcode canvas, so preset authoring is visible before data entry. Payload, starting format and automatic cycling can each be fixed or runtime. Payload defaults to **Ask when run** to avoid persisting an accidental one-off token; deliberately fixing it creates a reusable card/token preset. Running the preset reopens this same generator UI.
