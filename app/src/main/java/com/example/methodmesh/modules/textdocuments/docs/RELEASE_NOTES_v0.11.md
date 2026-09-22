# Text documents v0.11

Toolkit/preset/ODK refinement over v0.10.

## Native toolkit

- adds a visible editor action strip for **Save**, **Copy** and **Share**;
- native MethodMesh launches also show **Preset** directly;
- Save As / Save a copy, Find/replace, Markdown preview/source and other secondary actions remain in the overflow menu;
- the same visible Save/Copy/Share strip appears when documents are opened from another Android app.

## Save as preset

- adds module-native **Save as preset** without routing through a generic result/Commit screen;
- presets use the canonical `document.text.open` capability and the shared `ProtocolLibraryRepository`;
- preset choices are: open the library, start a new blank document, or deliberately store the current text as a reusable template;
- direct native preset runs behave as shortcuts into the toolkit; protocol/sequence runs retain result-return semantics.

## ODK / caller behaviour

- adds canonical `document_mode` input with `library`, `new` and `edit`;
- ODK can now explicitly request a new blank document, edit supplied text (including blank text), or let the operator choose/open a document;
- ODK return action is labelled **Return to form**; protocol/sequence steps use **Continue**; other external callers use **Return result**;
- Save remains independent of return: the current working buffer can be returned to ODK without first writing a physical file.

## XLSForm

- showcase workbook updated to include `source_mode` and pass `input_document_mode`;
- canonical return fields and FULL transport payload are unchanged.

## Unchanged hardening

- only persistable document URIs enter Recent files; transient WhatsApp/provider attachments remain session-local;
- TXT/Markdown/JSON/JSONL stay lossless text surfaces;
- no shared platform code is introduced.
