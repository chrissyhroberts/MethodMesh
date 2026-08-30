# Document scanner

Capability: `document.scan`

This module opens ML Kit Document Scanner to capture paper pages, crop and align them, then returns PDF and OCR outputs through the normal MethodMesh return/attachment pathway.

## Intent examples

Scan pages and return OCR/searchable outputs:

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='document.scan',input_page_limit='10',input_scanner_mode='full',input_run_ocr='true',input_return_searchable_pdf='true',return_mode='flat')
```

Quick PDF-only scan:

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='document.scan',input_page_limit='5',input_run_ocr='false',input_return_searchable_pdf='false',return_mode='flat')
```

## Settings

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

## Notes

ML Kit Document Scanner supplies the capture, crop, alignment and scanner PDF. MethodMesh copies page images into its own FileProvider cache before returning them. When searchable PDF output is enabled and creation succeeds, MethodMesh returns/exports the searchable PDF in preference to the original scanner PDF, so callers receive one PDF rather than duplicate PDF attachments. When OCR is enabled, the searchable PDF includes page images plus embedded OCR text and an OCR appendix.
