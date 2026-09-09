# Paper Bridge visual designer — `paper.form.design`

The designer is the canonical authoring surface for paper templates. Study teams should not normally hand-edit `methodmesh.paper.v1` JSON.

## Fast path

1. Open **Paper Bridge → Design a form**.
2. Choose the blank questionnaire PDF/image.
3. Import the corresponding XLSForm.
4. Use **Place box** and drag once over an answer region.
5. The new box remains selected; choose the ODK field (and return option for OMR) and press **Link to ODK**.
6. Use **Edit boxes** to drag a box to move it or drag one of its four corner handles to resize it. Existing boxes are never silently replaced.
7. Fix all red design errors, then use **Test with completed page**.
8. **Save Paper Bridge form**. The designer saves both the JSON definition and a marked-up PNG showing every linked bounding box and its ODK mapping.

The saved form is immediately available to `paper.form.transcribe` and becomes the active native Paper Bridge form.

## What XLSForm import provides

For compatible rows the designer imports:

- variable name and label;
- paper recognition type;
- required state;
- `select_one` / `select_multiple` choice values and labels;
- simple `regex(., '…')` constraints;
- simple numeric lower/upper bounds.

Compatible ODK types are `text`, `integer`, `decimal`, `select_one`, `select_multiple` and `barcode`. Other rows are skipped with a warning. The XLSForm remains authoritative for constraints that the designer cannot safely reduce.

## Error detection

Save is blocked for structural errors: missing registration targets, unlinked placed boxes, missing field/choice mappings, invalid or duplicate variable names, reserved `paper_`/`methodmesh_` names, out-of-page/inverted boxes, target-zone intrusion and substantial overlaps. Very small regions are warnings because they are likely to be fragile in real photographs.

Testing uses the actual Paper Bridge rectification + ML Kit/OMR extraction path; it is not a mock preview.

## Bundled fixture

Use `example_paper_form_for_designer.pdf` together with `example_odk_showcase_paper_form_transcribe.xlsx`. They share six fields and cover every currently supported recognition family.

### Pan and zoom

The designer opens in a full-screen dialog and uses the same document-viewport discipline as MethodMesh Digital Signing: the page opens fitted at 1×, **Navigate** supports drag-to-pan and pinch-to-zoom up to 16×, and **Fit** restores the whole page. **Place box** is always available when a page is loaded. Every box is stored in normalized page coordinates after inverting the active viewport transform, so geometry is invariant to zoom/pan. Linked boxes remain visible with `variable` or `variable = return_value` labels. In **Edit boxes**, drag inside a box to move it and drag a corner handle to resize it. There is no redraw operation: unlink/delete is explicit.

## Built-in example

Paper Bridge ships with a ready-to-use example form. The canonical source files remain in this module's `docs/` directory:

- `example_paper_form_for_designer.pdf` — anchored blank paper questionnaire;
- `example_odk_showcase_paper_form_transcribe.xlsx` — matching XLSForm;
- `example_template_manifest.json` — generated Paper Bridge mapping;
- `example_paper_form_for_designer.png` — documentation preview.

The Android runtime does not assume `docs/` is an APK asset directory. Therefore the exact PDF/XLSX bytes are embedded in `PaperBridgeBuiltInExamples.kt` and materialised into app-private storage on first use. The example appears in **Form setup** automatically and is the fallback active form on a clean install. Opening it in Designer automatically loads both its paper PDF and ODK schema. The built-in example is immutable: Designer opens it as an editable copy with a new form ID so experimental changes cannot silently replace the shipped fixture.


## Designer outputs

Saving `paper.form.design` creates durable app-private design artefacts and returns both of them to the caller:

- `paper_template_json` — the canonical JSON definition as text;
- `paper_template_json_uri` — a shareable URI for the saved `.paperbridge.json` file;
- `paper_design_markup_image` — a shareable PNG URI containing the blank page with every linked bounding box and mapping label burned into the image.

These are design/configuration artefacts only; they contain no completed questionnaire values.

## Registration targets

The bundled example now uses bullseye-style registration targets (dark centre, light annulus, dark outer ring) rather than plain black dots. The detector scores this target structure explicitly while retaining compatibility with older solid-dot forms. This is intended to reduce accidental matches on ordinary corner content.
