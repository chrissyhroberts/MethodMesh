# Barcode clone

**Canonical MethodMesh authority:** Master Book v1.25 (2026-09-22)
**Module ID:** `barcode`
**Method ID:** `barcode.clone`
**Method version:** `1.0.3`
**Maturity:** Development
**Connectivity:** Offline

`barcode.clone` is the camera-to-code bridge: point the windowed camera at a supported barcode/2D code, capture its exact decoded payload, immediately re-present that payload, then swipe through other compatible symbologies and share/save/return the current clone.

## Native UX

Clone opens as a normal MethodMesh capability surface so the shared preamble and preset machinery remain available. The scanner is embedded at the top of the capability rather than launching the separate full-screen ZXing capture activity. The camera view follows the device orientation and the screen-relative aiming/horizon line always runs left-to-right in both portrait and landscape.

The scanner is live on entry. The first successful decode pauses the windowed camera and immediately displays the cloned code below it. **Scan another** clears the working clone and resumes the same embedded scanner. This avoids a constantly changing payload while the operator is swiping formats or preparing to share.

`SOURCE` starts with the detected source symbology when ZXing can encode the exact payload in that format. Swipe, previous/next and optional Cycle move only through compatible formats. A fixed incompatible clone format is never silently replaced; export/return actions remain disabled until the exact payload can be represented.

There is deliberately **no visible Commit button**. Clone still preserves MethodMesh's working-result/finalized-result distinction, but finalization happens atomically when the user selects an action. **Share image**, **Copy image**, **Save image**, **Return clone**, or preset closeout snapshots the current payload + selected symbology into one canonical execution and one PNG. Repeating actions without changing the live state reuses that snapshot. Rescanning, changing the clone format, or changing the text-return policy invalidates it so the next action creates a new canonical snapshot.

The clone renderer deliberately leaves even QR symbols pristine: branding belongs to `barcode.generate`; cloning prioritises scanner and future print fidelity. The rendered barcode PNG is the primary artefact everywhere. `barcode_clone_image_uri` is a canonical output, not merely a native presentation detail. **Share image** sends the PNG first; **Copy image** places the PNG on the Android clipboard; **Save image** writes the PNG to Downloads.

Native Share/Copy default to image-only. A small **Include payload text in Share / Copy** switch can add the exact decoded text without creating a redundant `.txt` file. Native Share/Save can independently include a JSON metadata sidecar.

`barcode_return_text_payload` controls whether the canonical scalar text payload is returned alongside the image. It defaults to `true` for useful ODK roundtrip behaviour, and can be fixed to `false` by a preset/protocol/ODK caller when the cloned PNG is sufficient. The payload is still used internally to render the code; suppressing its return never changes the encoded barcode.

ODK/external roundtrips receive `barcode_clone_image_uri` as a caller-readable PNG attachment. They stay on the same windowed interactive screen until the operator chooses **Return clone**, which is the external-roundtrip finalization boundary. The canonical showcase requests both the PNG and text payload plus `methodmesh_full_json`, because a clone workflow normally wants the artefact; a text-only workflow should use `barcode.scan` instead.

## Cross-surface exposure

`barcode.clone` is a first-class capability, independently addressable through direct native use, capability discovery/dashboard, presets, protocols, supported schedules/widgets and ODK/XLSForm. It composes the established scanner boundary for acquisition but returns its own canonical clone observation.

### Preset and closeout contract

Fixed clone-format/cycle settings stay fixed and are hidden at runtime; runtime fields remain editable. Preset authoring remains reachable because Clone uses the standard host presentation rather than taking over the whole screen. Preset result actions are honoured as first-class policies: `HOME` closes normally, `SHARE` snapshots/shares the current barcode image and then closes, and `SAVE` snapshots/saves the current barcode image and then closes. Preset payload modes `CORE`, `AUDIT`, and `FULL` determine whether a JSON metadata sidecar accompanies Share/Save. Widget/launcher-origin closeout returns to the launcher; protocol/schedule/external origins return through their owning runner/transport.

## Inputs/settings

| Key | Type | Required | Meaning |
|---|---|---:|---|
| `barcode_clone_format` | choice | No | `SOURCE` or a specific target symbology. |
| `barcode_auto_cycle` | boolean | No | Start timed cycling through compatible target formats. |
| `barcode_return_text_payload` | boolean | No | Also return the exact decoded payload as scalar text. Defaults to `true`; PNG is always returned. |

Target values: `SOURCE`, `QR_CODE`, `CODE_128`, `DATA_MATRIX`, `AZTEC`, `PDF_417`, `CODE_39`, `EAN_13`, `EAN_8`, `UPC_A`, `UPC_E`.

Camera acquisition supplies the exact payload, source format and scan timestamp. The payload is never truncated, padded or rewritten during conversion.

## Canonical outputs

| Output | Meaning |
|---|---|
| `barcode_clone_image_uri` | Caller-readable `content://` URI for the committed PNG; primary/core result and ODK image attachment. |
| `barcode_payload` | Exact scanned/re-presented payload when `barcode_return_text_payload=true`; optional scalar return. |
| `barcode_payload_kind` | `text` or `url`. |
| `barcode_payload_url` | Safe absolute HTTP/HTTPS payload when applicable. |
| `barcode_source_format` | Symbology detected during acquisition. |
| `barcode_clone_format` | Symbology committed for the clone. |
| `barcode_payload_sha256` | SHA-256 of the exact payload. |
| `verification_evidence_format` | `barcode_payload_utf8_sha256_v1`. |
| `verification_evidence_hash` | Evidence hash. |
| `barcode_scan_time_iso` | Source scan timestamp. |
| `barcode_clone_time_iso` | Clone commit timestamp. |
| `barcode_source` | Normally `camera_zxing_clone`. |

## ODK Integration Card

### Capability

`barcode.clone`

### Intent call

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='barcode.clone',input_barcode_clone_format=${barcode_clone_format_input},input_barcode_auto_cycle=${barcode_clone_auto_cycle_input},input_barcode_return_text_payload=${barcode_return_text_payload_input},input_payload_mode='FULL',return_mode='flat')
```

### Canonical returns

`barcode_clone_image_uri` (ODK `image` attachment) is always returned on success. When `barcode_return_text_payload=true`, `barcode_payload`, `barcode_payload_kind`, and `barcode_payload_url` are also returned. Audit/scientific fields remain `barcode_source_format`, `barcode_clone_format`, `barcode_payload_sha256`, `verification_evidence_format`, `verification_evidence_hash`, `barcode_scan_time_iso`, `barcode_clone_time_iso`, and `barcode_source`, plus shared `methodmesh_status` and `methodmesh_full_json`.

Canonical showcase: `docs/example_odk_showcase_barcode_clone.xlsx`. It contains one MethodMesh call, uses canonical unprefixed return fields and sets no return namespace. Camera acquisition is interactive; **Return clone** snapshots the current live clone and returns the contract to ODK.

The canonical form defaults `barcode_return_text_payload` to `true`, so it demonstrates the normal clone roundtrip: PNG attachment + exact text payload + `methodmesh_full_json`. Set it to `false` when the caller wants the cloned barcode image without a separate scalar payload return. The PNG is returned as an actual attachment through the shared binary transport rather than merely as URI text.

## Permissions, persistence and roadmap

Camera permission is required; core operation is offline. The capability owns no history repository.

Physical clone printing is a roadmap transport/export path, not current behaviour. A future Bluetooth/label-printer action should consume the committed exact payload + clone format and preserve printer-specific concerns outside this capability contract. See `ROADMAP_NOTE.md`.

## Capability-owned presets

Clone exposes **Save current setup as preset** alongside the windowed scanner. Starting clone format, automatic cycling and text-payload return can each be fixed or runtime. Running the preset reopens this same windowed Clone instrument with the camera ready; it does not divert to a generic settings page or the external ZXing capture activity.
