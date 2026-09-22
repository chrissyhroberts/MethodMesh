# FRDM PaperBridge PDF example pack

These are deliberately standalone one-page A4 forms derived from `FRDM_Progress_Record.xlsx`.

## Files
Each example has:
- `*_AUTHORING.pdf` - colour-coded PaperBridge training template.
- `*_BLANK_REGISTERED.pdf` - monochrome printable form with eight registration tags.
- `*_FILLED_EXAMPLE.pdf` - plausible completed example for scan/testing.

`FIELD_MAPPING.csv` links each paper field back to the XLSForm variable and type.

## Design principles applied
The layouts follow the supplied *Survey and Form Design* draft where compatible with PaperBridge:
- >= 12 pt-equivalent body interaction sizes where practical, clear sans-serif typography and left alignment.
- generous response areas instead of maximally compact forms.
- structured section headings and consistent alignment.
- square response marks for select-one and Likert-style questions.
- ISO-8601 `YYYY-MM-DD` date guides.
- lined natural-language boxes with enough space for ordinary handwriting.
- field-table organisation for repeated yes/no + date progress-meeting items.
- colour is never the sole carrier of meaning on the production form.

## PaperBridge authoring colours
- scalar/text/date: `#D81B60`
- select one (including Likert): `#00897B`
- select multiple: `#EF6C00`
- choice label text: `#1565C0`
- response geometry/question text: black

The authoring colours are design-time syntax. Use the monochrome registered PDFs for handwriting/OMR scan tests.

## Notes
- The XLSForm's `Research Degree Coordinator` is a `select_one_from_file DRDCs.csv`. The referenced CSV is not embedded in the workbook supplied here, so that field is intentionally omitted from these examples rather than inventing its choices.
- The forms are test examples, not a replacement production version of the FRDM record.

## Page split
The upgrade/review and progress-meeting content are intentionally separate A4 forms. The first draft tried to compress both onto one page; that produced exactly the cramped layout the handbook cautions against. The split version preserves normal handwriting space and larger OMR marks.
