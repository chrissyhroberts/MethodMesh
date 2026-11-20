# Photo and document redaction

## Choose a source

| Tool | Source choices | Output |
| --- | --- | --- |
| **Redact photo** (`image.redact`) | **Take new photo** or **Choose saved photo** | New masked JPEG |
| **Redact document / PDF** (`pdf.redact`) | **Scan document to redact** or **Open PDF from phone** | New masked, image-only PDF |

These tools remove selected identifying features, labels, faces or other sensitive regions before export. The source is not overwritten or included as a result attachment. Review the complete redacted output before sharing; this is not secure deletion of the original from the phone.

Maturity: `image.redact` retains its existing Production status; `pdf.redact` is Development. The module's aggregate maturity reflects its Development member. Existing method IDs, image setting IDs/source tokens, output fields and image XLSForms are preserved.

## Zoom, pan and redact

Both photo and PDF editors use the signing viewer's fit-and-navigate approach, implemented locally without changing or importing the signing module. The full source starts fitted in view. Choose **Pan / zoom** to drag with one finger or pinch with two fingers; choose **Redact** to mark photo grid cells or draw PDF boxes. A two-finger gesture in Redact mode cancels the mark rather than treating a pinch as a redaction.

**+ / −** zoom from 1× to 16× in either mode. **Fit** resets only the view, never the masks. **Expand photo** and **Expand page** open full-screen editors with the same masks and navigation state. Zoom, normalised view centre, mode and an open expanded editor survive ordinary rotation. A newly loaded source or a different PDF page starts fitted; returning to a page preserves its masks, not a separate per-page navigation history. Committed PDF previews remain navigable but cannot add masks.

Hit testing and mask drawing use the same inverse view transform. Moving or zooming the viewport does not move masks on the source or change exported coordinates, hashes or output contracts. Pinch zoom stays anchored under the fingers; panning is bounded to the image/page. Zoom magnifies the existing preview, not the export resolution: photos retain the existing masked JPEG path and PDFs remain raster-only at 150 dpi. Review the exported result before sharing.

## Capabilities

### `pdf.redact` — Redact document / PDF

Added 21 October 2026; version `0.1.0`, Development, offline PDF editing/export. This is a separate capability in the existing module. Scanning uses the existing `documentscanner` module through the public `document.scan` contract, not a second scanner implementation.

1. Open **Redact document / PDF** and choose **Scan document to redact** for paper pages, or **Open PDF from phone** for a stored PDF. Finish the scanner to enter the same page editor; scanning alone does not redact or share anything.
2. Drag rectangular boxes over information to remove. **Expand page** opens a larger editor; **Previous/Next** changes page. Undo removes the last document-wide box; Clear page removes the current page's boxes.
3. Choose **Create redacted PDF**. This freezes a new output and its hash; the exported pages can be previewed in the same tool.
4. Use **Share PDF** to send only the new PDF attachment, **Save to Downloads**, or Done. Edit masks/new copy keeps the prior committed file unchanged and requires a fresh Create.

The original is never overwritten or returned as an output. Every page is rendered, marked regions are replaced with opaque black pixels (rounded outward), and a fresh image-only PDF is built from those masked pixels. No original text layer, editable form fields, annotations, links, embedded files, source metadata or digital signatures are copied. Visible unmasked information remains visible and can still be read/OCRed by a recipient: review every page and cover the whole sensitive region before sharing.

This deliberately trades searchable/editable/vector content for removal of the source PDF structure. The output uses 150 dpi page images with the original page dimensions/order. It is not a new valid digital signature or PDF accessibility/tagging preservation workflow. Password-protected/unreadable PDFs fail visibly. Current safety bounds: 64 MiB source, 1–50 pages, 12 million rendered pixels per page and 40 million in total; larger documents must be split first. A cache copy of the original is used privately during editing and removed on normal screen departure; Android may clear cache, and crash/process-death cleanup/durable draft recovery is not promised.

Masks/page selection and the private working-file reference survive configuration recreation. A committed execution retains the exact original envelope/FULL evidence; lost retained evidence requires a new explicit Create, never reconstruction of a historical envelope. File loading/rendering/export are background, composition-owned operations. Cancellation does not publish a partial output, and failures do not return/share the original. Downloads/provider performance and physical-device/real receiver acceptance still need testing; no production/security certification is implied by controlled fixtures.

#### PDF intent and input

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='pdf.redact',input_payload_mode='FULL',return_mode='flat')
```

The default interactive tool opens the phone PDF picker. To start with paper pages instead:

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='pdf.redact',input_document_source='document_scanner',input_payload_mode='FULL',return_mode='flat')
```

| Input | Values / meaning |
| --- | --- |
| `input_document_source` | `file_picker` (default) or `document_scanner`. |
| `input_pdf_uri` | Optional readable `content://…` URI for an existing PDF; caller must grant read access. |

The operator must still mark regions and choose Create. Native presets declared Ask when run wait for explicit source selection; fixed scan presets open the scanner. A fixed supplied PDF is not exposed as a runtime replacement field once loaded, but unreadable input leaves an explicit file-selection recovery path. Headless execution fails clearly rather than pretending an interactive redaction occurred. Presets/protocols use the same registered capability, not a dashboard-only implementation.

Scanning requests up to ten pages, full scanner mode and optional scanner gallery import, with OCR, searchable PDF, text-file return and timestamping disabled. Only a successful scanner PDF enters a private working copy. Raw scan URIs, page images and any scanner OCR sidecars are not forwarded into the redaction result; the final envelope records only the acquisition kind as `redaction_document_source`. Cancellation or failed/conflicting scanner status does not create a result or fall back to sharing the source. The existing scanner manages its own original scan cache; redaction does not erase that cache. Google Play services/ML Kit scanner availability and any first-use component download remain existing scanner dependencies. No cloud redaction or OCR service is added.

#### PDF outputs

| Field | Meaning |
| --- | --- |
| `pdf_redaction_status` | `succeeded` or `failed`. |
| `redacted_pdf_uri` | The new PDF attachment, never the original URI. |
| `redacted_pdf_name` | Generated neutral output filename. |
| `redacted_pdf_sha256` | SHA-256 of the final output bytes. |
| `pdf_redaction_page_count` | All output pages, in original order. |
| `pdf_redaction_region_count` | Number of marked regions across the document. |
| `pdf_redaction_regions_json` | Zero-based page indices and normalised, top-left-origin rectangle bounds. |
| `pdf_redaction_mode` | `raster_flattened`. |
| `pdf_redaction_resolution_dpi` | `150`. |
| `pdf_redaction_created_time_iso` | Output creation time, separate from execution provenance. |
| `pdf_redaction_error` | Failure explanation when applicable. |

The shared external return also includes `methodmesh_full_json` and read-granted attachment delivery. Exported evidence does not add the original PDF URI/path/name. The existing image XLSForms remain image-only; a dedicated PDF XLSForm and real Collect/Enketo/preset/protocol acceptance are still outstanding.

### `image.redact` — Redact photo

Choose **Take new photo** or **Choose saved photo**, mark grid cells to mask, then choose **Create redacted image**. Share/save/return uses the new JPEG, not the original. Camera capture requests camera permission when necessary; denial/cancellation stays visible and permits retry. Saved photos use the phone's image picker without broad storage access. Both workflows retain the original source file unchanged.

Flat `input_source='file_picker'` calls now remain file-picker calls after shared input normalisation; they no longer silently fall back to the camera. The legacy `input_source` setting ID and `camera`/`file_picker` tokens are unchanged. The recorded source is the source actually loaded, so cancelling a replacement camera attempt cannot relabel an already-loaded saved photo. Changing the grid clears the old cell selection for explicit re-selection.

Completed photo executions retain the exact original envelope/FULL JSON through configuration recreation instead of reconstructing historical evidence. Missing retained evidence requires a new explicit Create. This is not a durable draft/process-death guarantee; a picker grant/cache file may become unavailable.

Presets that keep a fixed photo source but ask for grid settings offer **Open image** after those controls; choosing runtime grid settings no longer leaves the acquisition action hidden.

## Android intent

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='image.redact',input_source='camera',input_grid_rows='10',input_grid_columns='10',input_redaction_style='black',input_payload_mode='FULL',return_mode='flat')
```

For ODK-style calls, `input_payload_mode='FULL'` returns the redacted image URI plus `methodmesh_full_json` audit metadata.

## Inputs

| Field | Description |
|---|---|
| `input_source` | `camera` or `file_picker`. |
| `input_grid_rows` | Number of mask-grid rows. |
| `input_grid_columns` | Number of mask-grid columns. |
| `input_redaction_style` | `black` or `white`. |

## Intent examples

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='image.redact',input_source='camera',input_grid_rows='10',input_grid_columns='10',input_redaction_style='black',input_payload_mode='FULL',return_mode='flat')
```

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='image.redact',input_source='file_picker',input_grid_rows='12',input_grid_columns='12',input_redaction_style='black',input_payload_mode='FULL',return_mode='flat')
```

## Outputs

| Field | Description |
|---|---|
| `image_redaction_status` | `succeeded` when the redacted image was created. |
| `redacted_image_uri` | URI for the redacted image attachment. |
| `redacted_image_name` | Suggested filename for ODK/file export. |
| `redacted_image_sha256` | SHA-256 of the final masked JPEG bytes. |
| `redaction_mask_json` | Selected cell IDs. |
| `redacted_cells` | Number of cells masked. |
| `redaction_grid_rows` | Grid rows used. |
| `redaction_grid_columns` | Grid columns used. |
| `redaction_style` | Mask colour used. |
| `redaction_input_source` | Camera or file picker. |
| `redaction_created_time_iso` | Creation time. |
| `methodmesh_full_json` | Full audit JSON when `input_payload_mode='FULL'`. |

The original image is used transiently for editing and is not returned as a capability output.

## ODK example

- `example_odk_image.redact.xlsx`

The example returns the redacted image URI as the main result and `methodmesh_full_json` as the auditable metadata field. Android also grants the returned image content URI to the calling app.

## Native use

When run inside MethodMesh, the clear result screen shows the redacted image as the main result. Sharing sends only the redacted image by default; the full JSON remains available through save/export or ODK full-payload return.

The default photo Share is a single read-granted `image/jpeg` attachment, not a hash/URI message or a mixed hash-text-plus-image share. The standard **Include full JSON / audit** toggle remains off by default. Enabling it keeps the same JPEG and adds canonical FULL JSON to the textual share payload and its matching text sidecar, never a separate JSON attachment. Save to Downloads remains file-oriented; external/ODK evidence and all declared hash/output fields are unchanged. No receiver-specific sharing logic is introduced.

## Validation boundary

Controlled Android tests exercise all four routes using the real redaction screen and synthetic camera/picker/scanner contract responses, including cancellation, failed scans, final output bytes, original preservation, read-granted sharing and recreation. They do not exercise a physical camera or Google ML Kit's scanner UI. Real-phone scanning/camera acceptance, actual share receivers, Collect/Enketo/preset/protocol roundtrips, low-memory behavior and whole-process recovery remain follow-up work. See `docs/handoffs/PDF_REDACTION_2026-10-21.md` in the repository for the recorded test results and PDF inspection.
