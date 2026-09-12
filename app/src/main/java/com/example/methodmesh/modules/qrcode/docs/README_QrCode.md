# Automatic code scanner

MethodMesh camera scanner for QR, Data Matrix, Aztec, PDF417 and common 1D barcode formats using JourneyApps/ZXing.

**Canonical MethodMesh authority:** Master Book v1.20 (2026-09-12)  
**Maturity:** Production  
**Connectivity:** Offline

The established public method ID is preserved:

- `barcode.scan`

Despite the historical identifier, the capability is not limited to linear barcodes or QR codes.

Current method implementation version: `1.1.2`.

## Canonical capability contract

`barcode.scan` is the single implementation boundary. Direct native use, dashboard discovery, presets, protocols, schedules/widgets and ODK/XLSForm all invoke the same method and declared output contract. No dashboard-only or ODK-only scanner implementation is introduced by this module.

`QrCodeModule` contributes the method, capability screen, typed `barcode_formats` setting, discovery/RIL bindings and module-owned documentation through the normal `MethodMeshModule` path.

## Native scanner UX

The production screen is scanner-first rather than a generated settings/result form.

1. The live camera is the primary instrument at the top of the capability.
2. Continuous decoding updates a mutable working result without leaving the screen.
3. The current payload appears in a floating HUD over the camera and is tap-to-copy.
4. If the payload is a safe absolute HTTP/HTTPS URL, **Open link** appears directly in that HUD beside **Commit**. Links are never opened automatically.
5. **Commit** freezes the canonical result for the execution.
6. Manual/native committed state remains on the same capability screen and exposes Share, Copy, Save to Downloads, optional full JSON/audit, Technical details, Home/Done and New scan.
7. Changing format configuration or starting a new scan explicitly leaves the previous working/committed state; a committed result is never silently mutated.

The code-format control is deliberately secondary. It is shown only when the setting is not fixed by the calling preset/context and expands on demand instead of occupying the scanner surface.

### Launch-origin closeout

- Direct app/dashboard: Commit freezes the result; Home returns through normal MethodMesh closeout.
- Native preset: fixed settings remain hidden; runtime settings remain available when configured as runtime.
- Widget preset: Done follows the existing launcher closeout path.
- Protocol/schedule: Commit returns the canonical result to the runner.
- ODK/external roundtrip: Commit returns the canonical payload to the caller; native post-commit export controls are not inserted into that automatic-return route.

The module does not persist scan history. Save occurs only through an explicit native Save action or an external/preset persistence policy.

## Settings and runtime inputs

| Key | Type | Required | Meaning |
|---|---|---:|---|
| `barcode_formats` | `MultiChoiceSetting` | No | Accepted ZXing formats. Empty means automatic/all scanner-supported formats. |

Declared choices are `QR_CODE`, `DATA_MATRIX`, `PDF_417`, `AZTEC`, `CODE_128`, `CODE_39`, `EAN_13`, `EAN_8`, `UPC_A`, and `UPC_E`.

External callers use the normal `input_` projection, for example `input_barcode_formats='DATA_MATRIX|CODE_128'`.

The method boundary also accepts established scanner/result context used by reusable scanner invocation:

| Input/context key | Purpose |
|---|---|
| `barcode_payload` | Exact decoded payload supplied by the scanner/dependency boundary. |
| `token` | Historical fallback alias for a supplied payload. |
| `barcode_format` | Detected format; defaults to `UNKNOWN`. |
| `barcode_source` | Capture source; native camera uses `camera_zxing`. |
| `barcode_scan_time_iso` | Capture time. |
| `operator_id` and invocation context | Optional attribution/context supplied by MethodMesh. |

Scanner-produced fields are not exposed as editable native text inputs.

## Outputs

| Output | Native presentation | Meaning |
|---|---|---|
| `barcode_payload` | Primary HUD/result | Exact decoded text; primary beef for copy/share/save. |
| `barcode_format` | HUD/committed result | Detected ZXing format. |
| `barcode_payload_kind` | Technical details | `text` or `url`. |
| `barcode_payload_url` | HUD action + technical detail when present | Exact payload only when it is a safe absolute HTTP/HTTPS URL. |
| `barcode_payload_sha256` | Technical details | SHA-256 of the exact decoded payload. |
| `verification_evidence_format` | Technical details | `barcode_payload_utf8_sha256_v1`. |
| `verification_evidence_hash` | Technical details | Evidence hash; currently the same payload SHA-256. |
| `barcode_scan_time_iso` | Technical details | ISO-8601 capture time. |
| `barcode_source` | Technical details | Scanner/source identifier. |

`methodmesh_full_json` is supplied by the shared MethodMesh FULL projection. It remains secondary/audit material in native UI and is always captured by the canonical handled ODK roundtrip example.

## State and persistence

Working and committed fields use saveable Compose state. Payload, format, source, scan time, execution/observation/transformation/relationship identifiers and system timestamp are preserved so ordinary activity recreation does not silently create a different committed execution.

No long-term scan repository is owned by this module.

## ODK Integration Card

### ODK INTEGRATION

**Capability**  
Automatic code scanner  
`barcode.scan`

**Tags**  
Maturity: Production  
Connectivity: Offline

### ODK INPUTS

| Canonical input key | ODK type | Required | Meaning |
|---|---|---:|---|
| `barcode_formats` | text / select_multiple projection | No | ZXing format names separated by spaces, pipes, commas or semicolons. Empty = automatic/all supported. |

**Interactive acquisition:** MethodMesh native camera scanner. The user frames a code and presses Commit to return the selected working result.

### INTENT CALL

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='barcode.scan',input_barcode_formats=${barcode_formats_input},input_payload_mode='FULL',return_mode='flat')
```

### MODIFIERS

| Modifier | Type | Semantics | Meaning |
|---|---|---|---|
| `input_payload_mode` | text | Canonical example uses `FULL` | Requests shared complete metadata/audit projection in addition to flat canonical returns. |
| `return_mode` | text | Canonical example uses `flat` | Projects canonical return fields into the calling group. |

The canonical showcase intentionally does **not** use `methodmesh_return_namespace`. User-authored composite forms may use the shared namespace mechanism when collision avoidance is genuinely required.

### CANONICAL RETURNS

| Canonical return key | ODK type | Availability | Meaning |
|---|---|---|---|
| `barcode_payload` | text | always on successful scan | Exact decoded payload. |
| `barcode_format` | text | always on successful scan | Detected format. |
| `barcode_payload_kind` | text | always on successful scan | `text` or `url`. |
| `barcode_payload_url` | text | conditional | Present only for safe absolute HTTP/HTTPS payloads. |
| `barcode_payload_sha256` | text | always on successful scan | Payload SHA-256. |
| `verification_evidence_format` | text | always on successful scan | Evidence recipe identifier. |
| `verification_evidence_hash` | text | always on successful scan | Evidence hash. |
| `barcode_scan_time_iso` | text | always on successful scan | Capture timestamp. |
| `barcode_source` | text | always on successful scan | Scanner/source identifier. |
| `methodmesh_status` | text | shared handled-roundtrip return | MethodMesh transport/execution status. |
| `methodmesh_full_json` | text / JSON | always on handled FULL roundtrip | Complete metadata/audit payload. |

### RETURN FIELD PLACEMENT

Canonical showcase: `docs/example_odk_showcase_barcode_scan.xlsx`

The workbook contains exactly one MethodMesh invocation and uses unprefixed canonical return leaves inside the invocation group:

```text
run_barcode_scan/barcode_payload
run_barcode_scan/barcode_format
run_barcode_scan/barcode_payload_kind
run_barcode_scan/barcode_payload_url
run_barcode_scan/barcode_payload_sha256
run_barcode_scan/verification_evidence_format
run_barcode_scan/verification_evidence_hash
run_barcode_scan/barcode_scan_time_iso
run_barcode_scan/barcode_source
run_barcode_scan/methodmesh_status
run_barcode_scan/methodmesh_full_json
```

The older `example_odk_barcode_scan.xlsx` filename is retained as a migration/compatibility example with its stable `form_id`; it now follows the same single-call, unprefixed return contract.

### FILE RETURN SEMANTICS

None. `barcode.scan` returns decoded text/evidence and no barcode-image/media attachment.

### RUNTIME

Inputs: optional `barcode_formats`; camera acquisition supplies payload/format/source/time.  
Primary output: `barcode_payload`.  
Working result: mutable until Commit.  
Commit: freezes the canonical execution result.  
Native export: payload first; full JSON/audit only when explicitly enabled.  
ODK persistence: owned by ODK; MethodMesh does not create a second stored copy simply because ODK invoked the capability.

## Permissions, dependencies and privacy

- Camera permission is required for native interactive scanning.
- Core decoding does not require network access and works offline.
- JourneyApps/ZXing provides `DecoratedBarcodeView`, `ScanContract` and decoding.
- Decoded content stays on-device in the scanning capability.
- HTTP/HTTPS payload classification is local.
- **Open link** is always an explicit user action using Android `ACTION_VIEW`; a scanned link is never auto-opened.

See `ATTRIBUTION.md` and `THIRD_PARTY_NOTICES.md`.

## Compatibility decisions

Preserved deliberately:

- module ID `barcode`;
- method ID `barcode.scan`;
- setting key `barcode_formats`;
- all established output names and meanings;
- `token` fallback input alias;
- evidence format `barcode_payload_utf8_sha256_v1`;
- stable ODK `form_id` values in the supplied workbooks.

The refresh changes presentation and documentation, not the canonical scientific/transport contract.

## Validation

See `VALIDATION.md` for the v1.20 review record and remaining repository/device checks.
