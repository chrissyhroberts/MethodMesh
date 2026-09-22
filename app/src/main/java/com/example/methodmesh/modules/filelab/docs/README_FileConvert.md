# Convert file

`file.convert` converts only through routes present in the executable `ConversionGraph`. The source file is never modified.

**Version:** `0.7.0`
**Maturity:** Development
**Connectivity:** Offline

## Native lifecycle

1. choose/receive a source file;
2. File Lab identifies it by content and shows only executable targets;
3. adjust route-specific controls;
4. **Convert** creates a live working artefact;
5. File Lab reopens that artefact and verifies its content signature matches the requested target;
6. inspect filename, size and SHA-256, then optionally **Open**, **Save file** or **Share** it;
7. **Commit** freezes only the converted-output payload;
8. shared launch-origin handling returns to the caller when appropriate.

Changing the source, target or raster settings invalidates the previous working output.

## Executable routes

| Source | Target | Notes |
|---|---|---|
| CBZ | PDF | image pages become PDF pages; bounded extraction |
| PDF | CBZ | each PDF page is rasterised to JPEG |
| TXT / Markdown / CSV / TSV / JSON / GeoJSON / XML / YAML / TOML / INI / logs / source / SQL and supported text field formats | PDF | paginated monospaced text rendering; 16 MiB input bound |
| PNG / JPEG / WebP / BMP | PDF | image rendered to one PDF page |
| GIF | PDF | first frame rendered to one PDF page |

Recognising a format does **not** automatically create a conversion route. In particular CBR/RAR, CB7/7-Zip, EPUB/MOBI, audio transcoding and scientific binary-format conversion are not advertised unless an engine exists.

## Inputs

| Canonical input | Type | Required | Meaning |
|---|---|---:|---|
| `source_uri` | Android URI / ODK attachment projection | no | Source file. Native UI asks for one when absent. |
| `source_name` | text | no | Optional display-name hint. |
| `target_format` | choice | yes for fixed/headless setup | Current target family is `pdf` or `cbz`; unsupported source→target pairs fail closed. |
| `max_dimension` | integer | no | Maximum rendered dimension for PDF→CBZ and image→PDF; 256–12000 px, default 2400. |
| `jpeg_quality` | integer | no | PDF→CBZ JPEG quality 1–100, default 92. |

## Canonical returns

| Key | ODK type | Meaning |
|---|---|---|
| `filelab_conversion_summary` | text | Route/size summary. |
| `filelab_source_name` | text | Source display name. |
| `filelab_source_format` | text | Content-detected source format. |
| `filelab_target_format` | text | Executed target format. |
| `filelab_output_name` | text | Converted output filename. |
| `filelab_output_uri` | file | Converted file as a shareable `content://` URI. |
| `filelab_output_mime_type` | text | Output MIME type. |
| `filelab_output_size_bytes` | integer/text | Final bytes written. |
| `filelab_output_sha256` | text | SHA-256 of final converted bytes. |
| `filelab_conversion_warnings_json` | text/JSON | Lossiness/route notes. |
| `filelab_converted_time_iso` | text | Conversion time. |
| `filelab_convert_status` | text | `succeeded` or `failed`. |
| `filelab_convert_error` | text | Failure detail. |

`filelab_source_uri` from v0.04 is intentionally no longer a declared conversion return. Returning the input URI caused the shared exporter to treat the original file as an output attachment. The selected input remains available to the calling workflow as its original input.

The shared transport additionally returns `methodmesh_status` and `methodmesh_full_json` on handled ODK FULL roundtrips.

## Safety and verification

CBZ→PDF enforces page-count, per-page extracted-byte, total extracted-byte and decoded-pixel limits. PDF→CBZ enforces page count, dimensions and JPEG quality. Image→PDF bounds source bytes and decoded dimensions. Text→PDF is bounded to 16 MiB. Failed conversions delete partial output files.

After every successful route, File Lab inspects the generated file itself and rejects it unless the detected format equals the requested target. This is a second-line check against accidental copies, wrong extensions or incomplete dispatch.

## ODK Integration Card

### ODK INTEGRATION

**Capability:** Convert file
**Method ID:** `file.convert`
**Maturity:** Development
**Connectivity:** Offline

### ODK INPUTS

The canonical showcase supplies `source_file`, target format and raster controls.

### INTENT CALL

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='file.convert',input_source_uri=${source_file},input_target_format=${target_format_input},input_max_dimension=${max_dimension_input},input_jpeg_quality=${jpeg_quality_input},input_payload_mode='FULL',return_mode='flat')
```

The showcase contains exactly one MethodMesh invocation, uses no canonical return namespace, and captures every declared return plus `methodmesh_status` and `methodmesh_full_json`.

### FILE RETURN SEMANTICS

`filelab_output_uri` is the sole conversion file attachment. It is the primary/core useful return and should enter returned Intent ClipData/read-grant handling so ODK imports the converted bytes. The original input URI is not returned as a second result attachment.
