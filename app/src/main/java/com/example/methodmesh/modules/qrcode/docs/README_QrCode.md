# Automatic code scanner

MethodMesh module for local camera capture of QR, Data Matrix, Aztec, PDF417 and common 1D barcode formats through ZXing/JourneyApps.

**Lane/status:** Production method metadata; migrated to MethodMesh Master Book v1.05 on 2026-09-06.

The historical public method ID is intentionally preserved:

- `barcode.scan`

Despite that identifier, the capability is not restricted to linear barcodes or QR codes.

Current method implementation version: `1.1.1` (embedded rotation-aware scanner UI; canonical method/output contract unchanged).

## Canonical capability contract

`barcode.scan` is the single implementation boundary. The dashboard, direct native launch, presets, protocols, schedules/widgets and ODK/XLSForm all invoke this same method and receive the same declared outputs. The dashboard is a projection/control surface, not a private scanner implementation.

The module does not require central capability-specific registration or shared-UI special cases. `QrCodeModule` exposes the method, native screen and typed `MethodSetting` metadata through the normal `MethodMeshModule` contract.

## v1.05 native lifecycle

The production screen behaves like a scanner rather than a generated settings form:

1. Choose accepted formats when that setting is not fixed by a preset.
2. The camera opens inside a bounded scanner window on the capability screen.
3. The scanner follows device rotation; the viewfinder/scan line remains horizontal relative to the held orientation rather than being portrait-locked.
4. Continuous decoding keeps the latest detected payload immediately beneath the scanner window. A different code replaces the mutable working result until Commit.
5. Tap the payload or other useful displayed values to copy the value itself.
6. When the payload is a safe absolute HTTP/HTTPS URL, an **Open link** action appears. Links are never opened automatically.
7. Press **Commit** to freeze the canonical result for this execution.
8. Native/manual runs reveal Share, Copy, Save to Downloads, optional full JSON, Home/Done and Edit/new scan on the same screen.

Editing/new scanning after Commit explicitly leaves the committed state; it does not silently mutate the frozen result.

### Launch-origin closeout

- Direct app/dashboard: Commit freezes the result; Home returns to MethodMesh.
- Native app preset: fixed settings stay hidden; runtime format input is requested when configured as runtime; Home returns to MethodMesh.
- Widget preset: Done returns through the existing launcher closeout path to the Android desktop.
- Protocol/schedule: Commit returns the canonical result to the runner so it can continue.
- ODK/external roundtrip: the scanner opens when interaction is required; Commit returns the canonical payload directly to the caller. Native Share/Save/Home controls are not shown in this route.

The module itself does not create archive/output copies on Commit. Storage occurs only through an explicit Save action or an explicitly configured preset save policy.

## Settings and runtime inputs

| Key | Type | Required | Meaning |
|---|---|---:|---|
| `barcode_formats` | `MultiChoiceSetting` | No | Accepted ZXing formats. Empty means automatic/all scanner-supported formats. |

Declared choices are `QR_CODE`, `DATA_MATRIX`, `PDF_417`, `AZTEC`, `CODE_128`, `CODE_39`, `EAN_13`, `EAN_8`, `UPC_A`, and `UPC_E`.

ODK/external callers use the normal `input_` projection, for example `input_barcode_formats='DATA_MATRIX|CODE_128'`.

The execution context also accepts the established scan/result inputs used by the method boundary:

| Input/context key | Purpose |
|---|---|
| `barcode_payload` | Decoded payload supplied by the scanner/dependency boundary. |
| `token` | Historical fallback alias for a supplied payload. |
| `barcode_format` | Detected format; defaults to `UNKNOWN`. |
| `barcode_source` | Capture source; native camera uses `camera_zxing`. |
| `barcode_scan_time_iso` | Capture time. |
| `operator_id` and invocation-context fields | Optional attribution/context supplied by MethodMesh/ODK. |

The normal native UI does not ask users to type scanner-produced fields.

## Outputs

All fields below are contractually available to protocols, pipes and ODK projection even when hidden from the everyday native view.

| Output | Normal native presentation | Meaning |
|---|---|---|
| `barcode_payload` | Primary result | Exact decoded text; beef-first share/copy value. |
| `barcode_format` | Shown | Detected ZXing format. |
| `barcode_payload_kind` | Technical details | `text` or `url`. |
| `barcode_payload_url` | Shown when present | Exact payload only when it is a safe absolute HTTP/HTTPS URL. |
| `barcode_payload_sha256` | Technical details | SHA-256 of the exact decoded payload bytes used by the method. |
| `verification_evidence_format` | Technical details | `barcode_payload_utf8_sha256_v1`. |
| `verification_evidence_hash` | Technical details | Evidence hash; currently the same payload SHA-256. |
| `barcode_scan_time_iso` | Technical details | ISO-8601 capture time. |
| `barcode_source` | Technical details | Scanner/source identifier. |

### Full JSON and audit

`methodmesh_full_json` is supplied by the generic MethodMesh FULL payload projection. It contains the complete result/audit representation, including execution/provenance material available from the canonical `ExecutionResult`.

Native runs share/copy/save only the decoded payload by default. Enabling **Include full JSON** adds the audit JSON to that explicit export action.

### Media

This capability returns decoded text/evidence, not a barcode image. `setBarcodeImageEnabled(false)` is intentional, so there is no module-declared media URI and no module-specific ClipData handling. Generic MethodMesh transport remains responsible for ClipData/read grants for capabilities that do return binary artefacts.

## State and persistence

Working scan state and committed state use saveable Compose state. Payload, format, source, scan time and the execution/observation/transformation/relationship identifiers required to reconstruct the result are preserved across ordinary activity recreation/rotation. Reconstructed committed results retain the same architectural IDs and system timestamp rather than silently becoming a different execution.

The module deliberately has no long-term repository: a barcode scan is a short one-shot operation. Committed results are not automatically persisted to module storage.

## Dashboard, presets and protocols

- **Dashboard:** generic module/capability discovery can present `barcode.scan` from module metadata; the scanner remains independently invokable.
- **Direct native:** purpose-built camera/scanner screen with working result and Commit.
- **Presets:** `barcode.scan` remains individually selectable. `barcode_formats` may be fixed or runtime. Fixed values are hidden during the preset run.
- **Protocols:** `barcode.scan` remains an independent protocol step and returns the same output contract.
- **Schedules/widgets:** invoke the same method/preset contract; closeout is controlled by the existing launch-origin framework flags.

## ODK/XLSForm

ODK is a first-class interactive route because camera framing is required.

Typical grouped intent:

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='barcode.scan',input_barcode_formats=${barcode_formats_input},input_payload_mode='FULL',input_methodmesh_return_namespace='scan',return_mode='flat')
```

The supplied workbook demonstrates:

- grouped intent invocation;
- `input_barcode_formats`;
- FULL payload mode;
- return namespace `scan`;
- every declared scanner output;
- `scan_methodmesh_full_json`.

The example is not an allow-list: the generic ODK projector exposes all declared capability outputs. Namespace projection prefixes the returned keys without changing their canonical names or meanings.

ODK owns form persistence/submission. The module does not save an extra MethodMesh output merely because ODK invoked it.

## Permissions and dependencies

- Camera access is required. The production screen requests runtime camera permission for its embedded JourneyApps/ZXing scanner window.
- No network permission is required by the module for decoding.
- Core scanning works offline.
- Host app dependencies provide JourneyApps/ZXing embedded scanning (`DecoratedBarcodeView`) plus the existing `ScanContract` / `ScanOptions` dependency-invocation path.

See `ATTRIBUTION.md` and `THIRD_PARTY_NOTICES.md`.

## Privacy and network behaviour

Decoded code content stays on-device within this capability. A payload that is a safe absolute HTTP/HTTPS URL is classified locally. The module never opens it automatically; the user may explicitly press **Open link**, which hands the URL to Android via `ACTION_VIEW`. Decoding itself does not transmit the payload.

## Compatibility decisions in this migration

Preserved deliberately:

- method ID `barcode.scan`;
- module ID `barcode`;
- setting key `barcode_formats`;
- every existing output field name and semantic meaning;
- `token` as a method-level fallback input alias;
- SHA-256 evidence format `barcode_payload_utf8_sha256_v1`.

`PDF_417` and `AZTEC` were added to the typed setting choices because the existing module documentation and ODK example already exposed those supported formats. This resolves a configuration-surface mismatch without changing the method contract.

## Validation status

Static migration review and workbook contract checks are documented in `VALIDATION.md`. A full Android Gradle build was not possible from the supplied standalone module archive alone; the receiving repository should run the standard MethodMesh test/build commands before release admission.
