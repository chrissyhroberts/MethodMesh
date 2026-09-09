# Paper Bridge validation plan

`paper.form.transcribe` and `paper.form.design` are **Development** until the image/extraction and ODK roundtrip validation below have been completed on representative devices and paper stock.

## Contract / architecture checks

- [x] One canonical method: `paper.form.transcribe`.
- [x] Module entry point is self-contained and auto-discoverable by the existing `*Module.kt` index rule.
- [x] No shared UI special case or central registration change required.
- [x] Existing MethodMesh ML Kit and Android dependencies are sufficient.
- [x] Dashboard/direct/preset/protocol/ODK all use the same method contract.
- [x] Working scan is mutable; Commit is the finalisation boundary.
- [x] External/ODK invocation returns only after Commit.
- [x] Dynamic paper keys are validated manifest field names and are the flat projection of `paper_values_json`.
- [x] ODK owns persistence and Central submission.
- [x] No automatic MethodMesh archive save is performed during ODK roundtrip.
- [x] Scalar results are tap-to-copy in native committed/working surfaces.
- [x] Native MethodMesh dashboard launch opens the Paper Bridge control surface; external/ODK/preset launch still enters the canonical scan workflow directly.
- [x] Dashboard template/activity workspace stores no questionnaire values, participant identifiers or page images.
- [x] A working scan can be left for the dashboard and resumed without creating a Commit.

## Designer validation

Using the bundled matched PDF/XLSForm pair:

1. open **Design a form** from the Paper Bridge dashboard;
2. import `example_paper_form_for_designer.pdf` and verify all four bullseye registration targets are detected and report strong target confidence;
3. import `example_odk_showcase_paper_form_transcribe.xlsx`;
4. verify six compatible fields are imported with exact ODK names and choice return values;
5. verify `visit_number` imports the 1-12 bounds, `temperature_c` imports 34-43, and `participant_id` imports its regex;
6. place boxes independently, link each to its ODK field/option, and confirm Save remains disabled while any box is unlinked or required mapping is missing;
7. deliberately overlap two boxes by >35% and verify a blocking error;
8. draw a field over a corner safety zone and verify a blocking anchor-zone error;
9. create duplicate/reserved/invalid manual variable names and verify they are rejected;
10. test a completed page and verify the actual extraction engine reports ready-vs-review fields;
11. save and verify the generated form becomes the active Paper Bridge scanner form;
12. reopen the app and verify the generated manifest persists; the designer source may persist as configuration, but no completed-page data is written to the designer store.

XLSForm reader regression cases should include inline strings, shared strings, multilingual label columns, missing choices sheets, unsupported survey types and complex constraints. Unsupported constructs must produce warnings/skip behaviour rather than silent remapping.

## Manifest negative tests

Expect hard rejection for:

1. blank manifest;
2. unsupported schema;
3. missing `template_id`;
4. empty `fields`;
5. duplicate field names;
6. invalid XLSForm node name;
7. field name beginning `paper_` or `methodmesh_`;
8. ROI outside `[0,1]` or inverted ROI;
9. OMR field without options;
10. OCR/barcode field without ROI;
11. duplicate OMR option values;
12. malformed regex;
13. page dimensions outside declared bounds.

## Anchor / rectification corpus

Create a labelled corpus for each supported paper layout with at minimum:

- flat scanner image;
- hand-held portrait photographs at ±5°, ±10°, ±20° perspective;
- portrait/landscape device rotations;
- uneven indoor light;
- strong side shadow;
- low contrast photocopy;
- slightly cropped page but all anchors present;
- each single missing anchor;
- one false dark object placed in each corner zone;
- wrinkled page;
- old `OMR_LSHTM`-style anchor form where compatible.

For each image record expected anchor centres in canonical source pixels and compute rectification error at known control points. Production acceptance should be defined in pixels/mm before promotion; do not promote on visual impression alone.

Any missing/implausible anchor set must stop extraction rather than continue with guessed geometry.

## OMR labelled tests

For every mark ROI capture labelled examples of:

- empty box;
- tick;
- cross;
- filled box;
- faint pencil;
- partially erased mark;
- pen outside box;
- two marks in a single-select question;
- mark close to threshold;
- photocopy artefacts.

Estimate false-positive and false-negative rates separately. Tune manifest thresholds against held-out pages, not the same pages used to choose thresholds.

Required invariant: a single-select multimark must enter review; code must never pick the darkest of two marked responses.

## OCR validation

For each intended OCR field type test:

- clean printed text;
- block capitals;
- digits;
- decimal values;
- common ambiguous glyph pairs (`O/0`, `I/1`, `S/5`);
- invalid range;
- invalid regex;
- blank required field;
- multiple lines within ROI;
- text crossing ROI edge.

Because OCR is manual-review by default, measure both candidate accuracy and final post-review accuracy. If any field/template enables `auto_accept_ocr`, validate that combination independently and document its acceptable error bound.

## State / lifecycle tests

- native dashboard opens with the active template and does not trigger camera/file acquisition until requested;
- camera/file-picker cancellation returns cleanly to the dashboard when no working scan exists;
- import a valid manifest, switch templates, relaunch Paper Bridge and verify the active native template persists;
- reject an invalid imported/edited manifest without replacing the current active template;
- remove a custom template and verify the bundled demo remains available;
- open a working scan, return to dashboard, resume it and verify review state remains intact;
- after Commit, recent activity contains only template/time/count metadata and no field values/images/participant IDs;
- clearing recent activity does not delete ODK/MethodMesh committed outputs or templates;
- rotate Android device after capture: source URI survives and extraction can be reconstructed;
- rotate after manual corrections: override values survive and are re-applied;
- rescan clears manual overrides;
- manual Rotate 90° clears overrides and recomputes extraction;
- Cancel from ODK returns cancellation without committed data;
- Commit is disabled while any required/review field is unresolved;
- after Commit, external origin immediately returns; native origin shows committed state/actions.

## ODK roundtrip

Using `example_odk_showcase_paper_form_transcribe.xlsx`:

1. validate XLSForm;
2. install in ODK Collect;
3. open group and launch MethodMesh;
4. scan the matching demo paper layout;
5. review forced OCR fields;
6. Commit;
7. verify `participant_id`, `visit_number`, `eaten_today`, `symptoms`, `temperature_c` and `specimen_barcode` are populated with exact values;
8. verify `paper_values_json` matches those flat fields;
9. verify `paper_source_image` and `paper_rectified_image` become real ODK media attachments rather than unusable URI text;
10. verify `paper_source_sha256` and `paper_rectified_sha256` hash the attachment bytes;
11. verify `methodmesh_full_json` exists when FULL payload mode is requested;
12. save/finalise/send via normal ODK workflow and confirm Central contains both data and media.

## Batch truth-set recommendation

Before Production, build a study-neutral benchmark with at least several hundred independently double-entered paper pages across multiple Android camera models. The truth set should include intentional edge cases and preserve raw images. Report field-level sensitivity/specificity for OMR, exact-match for OCR candidates, review rate, final post-review disagreement rate, anchor failure rate and rescan rate.

The production claim should be based on those observed rates. “No margin for error” is implemented operationally as fail-closed ambiguity plus review, not as an untestable claim that computer vision cannot be wrong.

## Host scrolling regression guard

- Paper Bridge capability roots MUST NOT apply `verticalScroll`/`LazyColumn` as an outer viewport.
- MethodMesh dashboard and external workflow hosts already own vertical scrolling; nesting another vertical scroll at the capability root can throw an infinite-height Compose measurement exception at runtime.
- Verify both dashboard and scan/review surfaces by launching from MethodMesh Dashboard and from an external/ODK intent.

## Designer viewport / gesture regression

- Blank-form canvas opens fitted at 1×.
- NAV mode supports one-finger pan and two-finger pinch zoom from 1× to 48×; + / − controls must reach the same bounded zoom state.
- Pan is clamped so the page cannot be lost completely beyond the viewport.
- Fit resets zoom to 1× and pan to the page centre.
- **Place box** is enabled whenever a source page is loaded; it does not depend on first selecting an ODK field.
- Placed rectangles are converted through the current viewport transform into normalized page coordinates; zoom/pan must never alter saved ROI geometry.
- Newly placed boxes are unlinked and remain visible until explicitly linked or deleted.
- A tap on a region selects it without requiring drag slop; where regions overlap, the smallest region containing the tap is selected. In **EDIT**, dragging inside the selected box moves it without changing size; dragging any of the four corner handles resizes it.
- Repositioning/resizing must update the existing region rather than creating duplicate ROIs.
- Every linked region remains visibly annotated on the page with its ODK variable and, for OMR, return value.
- Drawing equivalent boxes at different zoom levels must produce the same normalized coordinates within pointer-placement tolerance.

## Built-in example availability

1. Clear Paper Bridge workspace preferences / install cleanly.
2. Open the Paper Bridge dashboard without importing any files.
3. Confirm **Paper Bridge designer demo** is present, labelled **BUILT-IN EXAMPLE**, and active by default.
4. Choose **Explore built-in example in Designer**.
5. Confirm the blank paper PDF renders without a file picker.
6. Confirm the matching XLSForm schema loads without an XLSForm picker and reports six compatible fields.
7. Confirm the example opens as an editable copy (`paperbridge_designer_demo_copy`) and saving it does not alter/remove the shipped example.
8. Return to Form setup and confirm the original built-in example is still available.
9. Verify the materialised runtime PDF and XLSX hashes match the canonical `docs/` copies recorded in `PaperBridgeBuiltInExamples.kt`.

### Built-in example visibility

- [ ] On a clean app install, the Paper Bridge dashboard visibly contains a **Built-in examples** section without opening Form setup.
- [ ] The built-in demo card exposes **Open in Designer** and **Use for scan** actions.
- [ ] Launching `paper.form.design` directly opens the built-in example as an editable copy, with its bundled PDF and XLSForm loaded automatically.
- [ ] The built-in example remains available after custom forms are created or removed.


## Designer full-screen / export regression

- `paper.form.design` opens as a platform-width full-screen dialog and is not constrained by the dashboard capability card.
- The full-screen dialog uses a fixed split: paper viewport = 2/3 of available workspace, ODK linkage = 1/3. Only the lower linkage pane scrolls; the full-screen root and paper viewport do not.
- The navigation rail is confined to the paper viewport and contains NAV, + BOX, EDIT, +, − and FIT controls.
- ODK linkage content is confined to the lower pane and never paints over the paper viewport.
- Selecting a mapped box shows variable name, Paper Bridge/ODK-compatible type, required state and the imported regex/range or select choice return values.
- Saving writes a `.paperbridge.json` and marked-up `.mapping.png` under app-private design exports.
- `paper_template_json_uri` resolves through MethodMesh FileProvider and byte content matches `paper_template_json`.
- `paper_design_markup_image` resolves to a PNG showing all linked boxes and mapping labels at source-page coordinates.
- No completed-page image or participant response may be written to the design export directory.

## Registration target regression

- Bundled PDF uses bullseye targets rather than plain dots.
- Target scoring checks dark centre + light annulus + dark outer ring.
- Existing solid-dot paper forms remain detectable for backwards compatibility.
- Benchmark false-positive corner objects (text blocks, checkboxes, logos, dark blobs) against target marks before Production promotion.
