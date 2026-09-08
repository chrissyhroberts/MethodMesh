# Document scanner

Capability: `document.scan`

This module opens ML Kit Document Scanner to capture paper pages, crop and align them, then returns PDF and OCR outputs through the normal MethodMesh return/attachment pathway.

## Capabilities

### `document.scan`

Capture paper pages, crop/align them, optionally OCR them, and return a document attachment. When searchable PDF output is enabled and succeeds, MethodMesh returns the searchable PDF by preference rather than returning both the scanner PDF and searchable PDF.

## Android intent

Scan pages and return OCR/searchable outputs:

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='document.scan',input_page_limit='10',input_scanner_mode='full',input_allow_gallery_import='true',input_run_ocr='true',input_return_searchable_pdf='true',input_return_text_file='true',input_payload_mode='FULL',return_mode='flat')
```

## Intent examples

Quick PDF-only scan:

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='document.scan',input_page_limit='5',input_run_ocr='false',input_return_searchable_pdf='false',return_mode='flat')
```

## Inputs

- `input_page_limit`: maximum pages to scan.
- `input_scanner_mode`: `full`, `base_with_filter`, or `base`.
- `input_allow_gallery_import`: allow existing page images to be imported instead of capturing every page with the camera.
- `input_run_ocr`: read printed text from each scanned page on device.
- `input_return_searchable_pdf`: create a PDF attachment containing the scanned pages and OCR text.
- `input_return_text_file`: also attach OCR text as a plain `.txt` file.

## Outputs

Core return:

- `document_scan_searchable_pdf_uri`
- `document_scan_ocr_text`
- `document_scan_pdf_uri` when searchable PDF output is disabled or unavailable

Audit/full JSON return additionally includes:

- `document_scan_status`
- `document_scan_page_count`
- `document_scan_page_image_uris_json`
- `document_scan_pdf_uri`
- `document_scan_ocr_text_file_uri`
- `document_scan_ocr_page_count`
- `document_scan_mode`
- `document_scan_gallery_import_allowed`
- `document_scan_page_limit`
- `document_scan_time_iso`
- `document_scan_error`
- `methodmesh_full_json` when `input_payload_mode='FULL'`

## ODK example

- `example_odk_document.scan.xlsx`

The production example returns the searchable PDF URI and OCR text as the main useful values, plus `methodmesh_full_json` for background metadata/audit storage. Android also grants returned document content URIs to the calling app.

## Notes

ML Kit Document Scanner supplies the capture, crop, alignment and scanner PDF. MethodMesh copies page images into its own FileProvider cache before returning them. When searchable PDF output is enabled and creation succeeds, MethodMesh returns/exports the searchable PDF in preference to the original scanner PDF, so callers receive one PDF rather than duplicate PDF attachments. When OCR is enabled, the searchable PDF includes page images plus embedded OCR text and an OCR appendix.

Native preset/intent runs inherit saved scan settings, open the scanner immediately, and hide the setup controls. The completed payload is restored after Android orientation changes.
