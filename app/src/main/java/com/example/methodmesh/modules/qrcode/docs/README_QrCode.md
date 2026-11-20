# Barcode scan

**Canonical MethodMesh authority:** Master Book v1.31 (2026-09-25)
**Module ID:** `barcode`  
**Method ID:** `barcode.scan`  
**Method version:** `1.1.2`  
**Maturity:** Production  
**Connectivity:** Offline

`barcode.scan` is the established scanner capability. It remains unchanged in purpose and contract while the module also exposes the sibling capabilities `barcode.generate` and `barcode.clone`.

## Purpose and native UX

The camera is the primary instrument. Continuous ZXing decoding produces a live working result on the scanner surface. The current payload is tap-to-copy. A safe absolute HTTP/HTTPS payload exposes **Open link** but is never opened automatically. **Commit** freezes the canonical result. Direct/native runs then remain on the capability surface for Share, Copy, Save, optional audit/full JSON, Home/Done and New scan; ODK/protocol/schedule origins return the committed result to their caller.

The scanner has no history repository and does not upload decoded content. Camera permission is required only for scanning/cloning.

## Execution retention — 23 October 2026

The original decoded working execution and committed execution are retained across ordinary Activity recreation, not rebuilt from saved values/IDs. A missing working or committed record requires explicit **Start new scan**; no stale result actions or camera restart occur behind recovery. Real camera/process-death acceptance remains separate from source/build checks.

See repository handoff `docs/handoffs/PAPER_BARCODE_RETENTION_2026-10-23.md` for exact tests and limitations.

## Cross-surface exposure

The same `barcode.scan` contract is exposed through direct native use, capability discovery/dashboard, presets, protocols, supported schedules/widgets and ODK/XLSForm. The dashboard is a projection, not an implementation boundary.

Fixed preset settings stay hidden through the shared `settingShouldBeShown` contract. Runtime-selected format filters remain editable where configured as runtime inputs.

## Inputs/settings

| Key | Type | Required | Meaning |
|---|---|---:|---|
| `barcode_formats` | multi-choice/text projection | No | Accepted ZXing format names. Empty means automatic/all supported scanner formats. |

Supported names: `QR_CODE`, `DATA_MATRIX`, `PDF_417`, `AZTEC`, `CODE_128`, `CODE_39`, `EAN_13`, `EAN_8`, `UPC_A`, `UPC_E`.

## Canonical outputs

| Output | Meaning |
|---|---|
| `barcode_payload` | Exact decoded payload; core result. |
| `barcode_payload_kind` | `text` or `url`. |
| `barcode_payload_url` | Exact payload only when it is a safe absolute HTTP/HTTPS URL. |
| `barcode_format` | Detected ZXing symbology. |
| `barcode_payload_sha256` | SHA-256 of the exact payload. |
| `verification_evidence_format` | `barcode_payload_utf8_sha256_v1`. |
| `verification_evidence_hash` | Evidence hash. |
| `barcode_scan_time_iso` | Capture timestamp. |
| `barcode_source` | Capture source; native camera uses `camera_zxing`. |

`methodmesh_full_json` is the shared complete audit/metadata projection. It is secondary in native UI and mandatory on handled ODK roundtrips.

## ODK Integration Card

### Capability

`barcode.scan`

### Intent call

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='barcode.scan',input_barcode_formats=${barcode_formats_input},input_payload_mode='FULL',return_mode='flat')
```

### Canonical returns

`barcode_payload`, `barcode_payload_kind`, `barcode_payload_url`, `barcode_format`, `barcode_payload_sha256`, `verification_evidence_format`, `verification_evidence_hash`, `barcode_scan_time_iso`, `barcode_source`, plus shared `methodmesh_status` and `methodmesh_full_json`.

Canonical showcase: `docs/example_odk_showcase_barcode_scan.xlsx`.

The historical `docs/example_odk_barcode_scan.xlsx` remains active for compatibility with stable `form_id=barcode_scan`. The canonical showcase retains `form_id=qrcode_barcode_scan`. Both are single-invocation forms, use unprefixed canonical return fields and do not set `methodmesh_return_namespace`.

ODK owns submission persistence. MethodMesh does not create a second stored scan simply because ODK invoked it.

## Dependencies and attribution

Scanning uses JourneyApps ZXing Android Embedded / ZXing locally on-device. See `ATTRIBUTION.md` and `THIRD_PARTY_NOTICES.md`.

## Capability-owned presets

The scanner screen exposes **Save current setup as preset** during ordinary native use. The preset editor can keep the accepted-format filter fixed or ask for it each run, and independently selects returned-data scope and completion behaviour. Running the preset reopens this same scanner capability UI; fixed settings are hidden and runtime settings remain editable.


## 28 October 2026 — committed action closeout

The scanner keeps its module-owned committed payload/format/link/technical display, while common result actions supply consistent Share/Copy/Save/Done and execution-keyed optional audit. Only a final native preset applies Share-and-finish or Save-and-finish, and the operation must succeed before original-result confirmation. Direct/intermediate Done delegates without final exports or private Home navigation. Manual Share failures remain visible, not uncaught. Downloads runs off the UI thread; New scan is disabled and handler-gated during an active export or pending completion.

Decoded content is explicitly text, not an attachment inferred from its spelling: a content:// payload stays literal text. Native default text remains only barcode_payload; complete canonical evidence remains available through FULL/ODK. Formats, capture/hash algorithms, generator/clone siblings and automatic Commit returns are unchanged. The corrected shared file transport is untouched.

Both builds/37 focused JVM cases pass; exact 44-case multi-run Android coverage is recorded in docs/handoffs/BARCODE_COMPASS_RESULT_ACTIONS_2026-10-28.md. New scanner result-action tests use canonical synthetic decoded records and do not certify physical camera scanning, interruption/permissions, real OS process death or Collect/Sentinel workflows.
