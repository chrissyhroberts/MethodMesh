# Paper Bridge colour-authored templates

Paper Bridge can be authored with an ordinary `.xlsx` workbook and a deliberately annotated **QR-free** paper template. ODK is optional: the workbook only needs the standard XLSForm-style `survey` and `choices` sheets and the fixed subset below.

## Workbook contract

`survey` must contain `type`, `name`, and `label`; `required` and `constraint` are optional. Supported `type` values are:

- `text`
- `integer`
- `decimal`
- `barcode`
- `image`
- `select_one <list_name>`
- `select_multiple <list_name>`

`choices` must contain `list_name`, `name`, and `label` for choice lists. `settings` is optional; when present, Paper Bridge reads `form_id`, `form_title`, and `version`.

Question matching is performed against `survey.label`, not the variable name. Choice matching is performed against `choices.label`; the corresponding `name` is the value returned at runtime.

## Fixed colour syntax

The authoring template uses sRGB colours as a design-time annotation language:

| Meaning | Hex |
| --- | --- |
| Scalar question envelope (`text`, `integer`, `decimal`) | `#D81B60` |
| `select_one` question envelope | `#00897B` |
| `select_multiple` question envelope | `#EF6C00` |
| Barcode/QR question envelope | `#5E35B1` |
| Select option/answer label text | `#1565C0` |
| Question text | `#000000` |
| Runtime response boxes / mark geometry | `#000000` |

The preferred authoring form uses a **solid coloured question panel** containing the complete question: its black question stem and black response region(s). For select questions, answer-option text is blue while the actual tick boxes remain black. Legacy coloured-outline envelopes are also accepted, but solid panels are preferred because they are visually obvious to the author and easier to segment reliably.

Bold text is recommended in the authoring template because it improves design-time OCR. The production paper form does **not** need bold text or these colours. It may use any visual style, including colour, because runtime Paper Bridge reads only the saved ROI geometry after registration.

## Automatic authoring pass

When both the registered paper template and workbook are present, the designer can run the colour analysis automatically. It:

1. finds the fixed-colour question panels/envelopes;
2. classifies each panel by question type;
3. OCRs black question text inside each panel;
4. compares that text with compatible `survey.label` values;
5. detects black response rectangles inside the panel;
6. for select questions, OCRs blue option text and compares it with `choices.label`;
7. wires high-confidence question/choice matches to the corresponding response ROI;
8. leaves ambiguous or unmatched regions available for manual linking.

The authoring colour is never consulted during completed-form extraction.

## Runtime and handwritten text

Completed forms are acquired through ML Kit and then geometrically registered with **APRILTAG8**. Paper Bridge adds eight unique `tag36h11` markers to a dedicated printable border, uses up to 32 tag-corner correspondences to estimate one robust homography, crops the result back to exact canonical dimensions, and only then applies saved ROIs. Legacy QR4 and bullseye4 schemas remain readable.

Free text (`text`) always requires operator confirmation before Commit. The review card shows the cropped paper ROI beside the editable OCR proposal. Integer and decimal OCR can be eligible for auto-accept only when explicitly enabled and the candidate passes the declared constraints.

## Portable backup

Saving a design produces:

- `*.paperbridge.json` — canonical machine definition;
- `*.paperbridge.yaml` — fixed-profile YAML mirror containing the canonical restore payload;
- `*.template.png` — canonical target-centre authoring image;
- `*.prepared-form.png` — printable form with eight unique AprilTag36h11 registration markers;
- `*.mapping.png` — annotated field/choice mapping;
- `*.paperbridge.zip` — portable bundle containing the schema artifacts.

The JSON, fixed-profile YAML, or portable ZIP can be copied to another device and imported. The bundle contains no participant response data. The original authoring artwork is preserved and is expected to contain **no Paper Bridge QR fiducials**; QR fiducials are generated only on the prepared output rather than destructively written into the imported source.

## Media/code regions

Paper Bridge supports two visually similar single-ROI field classes with different runtime behaviour:

- `barcode` in the survey workbook: the ROI is passed to ML Kit barcode scanning and the field returns the decoded code text.
- `image` in the survey workbook: the ROI is cropped from the registered completed form and the field returns a shareable image attachment URI.

The fixed authoring palette uses violet `#5E35B1` for barcode envelopes and lime green `#7CB342` for image envelopes. Both contain a black inner response/capture rectangle. `barcode` returns decoded code text; `image` returns the cropped ROI as media. The colours are design-time hints only; production forms may use any colour scheme because runtime reads only the stored ROI geometry.

The built-in tutorial therefore includes both a specimen barcode region and a site-sketch/image region.


## APRILTAG8 geometry contract

Current Paper Bridge schemas use eight unique AprilTag `tag36h11` markers, IDs 0–7, in semantic order TL, TC, TR, LC, RC, BL, BC, BR. The printable prepared form places these in a dedicated registration border outside the canonical form artwork. Runtime detection uses the four outer corners of every accepted tag (up to 32 correspondences), rejects duplicate/weak IDs, requires at least four well-distributed tags, fits a robust projective homography, reports RMS and maximum reprojection error, and fails closed when registration quality is not trustworthy. The registered bitmap is then cropped back to the exact canonical form dimensions. Saved field ROIs are never individually shifted or rescaled to compensate for scan geometry. QR4 and bullseye4 are legacy-compatible only.
