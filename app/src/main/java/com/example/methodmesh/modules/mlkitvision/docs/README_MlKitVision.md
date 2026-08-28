# ML Kit vision

Capability ID: `mlkit.vision.analyze`

Runs Google ML Kit on a camera-captured or selected image.

Supported modes:

- `ocr`: recognise Latin-script text
- `barcodes`: detect QR, Data Matrix, PDF417, Aztec, and common 1D barcodes
- `ocr_and_barcodes`: run both recognisers

Example intent:

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='mlkit.vision.analyze',input_mlkit_mode='ocr_and_barcodes',input_source='camera',return_mode='flat')
```

Returned fields include:

- `mlkit_text`
- `mlkit_text_block_count`
- `mlkit_barcodes_json`
- `mlkit_barcode_count`
- `mlkit_first_barcode_raw_value`
- `mlkit_first_barcode_format`
- `mlkit_image_uri`
- `mlkit_pdf_uri`
- `mlkit_text_file_uri`

When `input_return_pdf=true`, the capability creates a PDF attachment containing
the captured/selected image and, when OCR is enabled, a second text page with the
recognised text. When `input_return_text_file=true`, OCR text is also returned as
a plain `.txt` attachment. Attachment fields end in `_uri`, so they participate
in the normal MethodMesh export/ODK attachment handling.

The Latin OCR and barcode-scanning models are bundled by the app dependency and
therefore work on-device. This is separate from the existing ZXing live scanner;
ZXing remains useful for fast viewfinder-based scanning, while this capability
analyses a captured or selected still image and can return OCR at the same time.
