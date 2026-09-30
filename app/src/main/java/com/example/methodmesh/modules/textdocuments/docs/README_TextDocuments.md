# Text documents

**Module:** `textdocuments`
**Release:** v0.12
**Capability:** `document.text.open`
**Maturity:** **Development**
**Connectivity:** **Offline**
**Icon:** `document`

Text documents is MethodMesh's small local document toolkit for text, data, configuration, markup and source-code files. It creates, opens, edits, finds/replaces, saves, copies and shares documents without silently rewriting their contents. It can also save launch configurations as native MethodMesh presets/shortcuts.

Recognised filename forms include `.txt`, `.log`, `.md`, `.markdown`, `.json`, `.jsonl`, `.ndjson`, `.yaml`, `.yml`, `.toml`, `.xml`, `.html`, `.htm`, `.css`, `.js`, `.mjs`, `.cjs`, `.ts`, `.tsx`, `.py`, `.pyw`, `.kt`, `.kts`, `.java`, `.sh`, `.bash`, `.zsh`, `.sql`, `.csv`, `.tsv`, `.ini`, `.cfg`, `.conf`, `.properties`, `.env`, C/C++/C#/Go/Rust/Swift/Ruby/PHP/Lua/R/Perl/Dart/Groovy/Gradle and common component files. Unknown extensions remain editable as UTF-8 plain text.

## Interaction model

Normal launch from **Capabilities** is a standalone toolkit, not a capability transaction. The first screen is a small document library with **New**, **Open file**, **New from clipboard** and recent files. Opening or creating a file goes directly into the editor. There is no generic Commit/results window.

The editor is content-first and full-screen. Its main action strip exposes **Save**, **Copy** and **Share** directly rather than hiding normal document actions behind a menu. Native MethodMesh launches also expose **Preset**. The editor provides:

- visible Save / Save As behaviour;
- Share of the **current working buffer**, including unsaved edits;
- Copy all;
- Save as preset / shortcut for native MethodMesh use;
- Find / replace;
- Markdown Preview / Edit source;
- recent-file metadata without silently archiving document contents;
- unsaved-change protection on Library/Close/Back;
- keyboard/IME-safe controls;
- line/character count and clear saved/unsaved state.

When launched by another workflow, the same editor is used. The return control is origin-aware: ODK shows **Return to form**, protocol/sequence steps show **Continue**, and other external callers show **Return result**. A direct native preset behaves like a shortcut into the toolkit rather than presenting a Commit/results ceremony. MethodMesh Commit semantics remain the transport boundary internally, but are not exposed as a generic Commit window.

Android `VIEW` / `EDIT` launches use the same editor and return to the originating app on Close/Back.

## Canonical capability

`document.text.open` remains the single canonical contract for direct use, presets, protocols and ODK/XLSForm roundtrips. Android file opening through `DocumentActivity` is an additional launch origin, not a second MethodMesh capability.

### Inputs

| Key | Type | Required | Meaning |
|---|---|---:|---|
| `document_mode` | choice | no | `library`, `new`, or `edit`. Controls whether the toolkit opens at the library, starts a blank document, or opens supplied text directly. |
| `document_text` | text | no | Caller-supplied text. Used directly by `edit`; may be blank. |
| `document_title` | text | no | Suggested human-facing filename/title. |
| `document_format` | choice | no | `text`, `markdown`, `json`, `jsonl`, or a recognised text/data/source format. |

### Outputs

| Key | Type | Meaning |
|---|---|---|
| `document_status` | text | `succeeded` or `failed`. |
| `document_text` | text | Text returned when the caller completes the editor (**Return to form**, **Continue**, or **Return result**, depending on origin). |
| `document_title` | text | Human-facing title at return. |
| `document_format` | text | The detected or requested text/data/source format. |
| `document_error` | text | Failure diagnostic; blank on success. |

Shared transport adds `methodmesh_status` and, for the ODK/FULL projection, `methodmesh_full_json`.

## Preset semantics

**Save as preset** is not file persistence. It creates a normal MethodMesh `CapabilityPreset` for the canonical `document.text.open` method. The user can save one of three behaviours:

- **Open Text documents** — a pure shortcut to the toolkit/library;
- **New document** — jump directly into a blank document with the chosen title/format defaults;
- **Use current text as a template** — deliberately store the current text in the preset and open a fresh editable copy when run.

Direct native preset runs are treated as shortcuts. Protocol/sequence execution still uses canonical result-return semantics.

## File semantics

**Save** writes back to the selected URI when write access exists. Otherwise the UI uses **Save As…**. **Save a copy…** is always available for an already-writable document.

JSON and JSON Lines are deliberately lossless text surfaces. Opening, saving or sharing does not pretty-print, minify, sort keys, parse/re-emit or otherwise rewrite the body. The editor reads UTF-8 text; binary content is rejected with a clear message rather than being silently decoded as damaged text. Save As preserves an existing extension, including extensions the module does not recognise.

**Share** materialises the current buffer as a temporary typed file through the existing MethodMesh `FileProvider` and supplies the same text to `Intent.EXTRA_TEXT`. The share operation therefore reflects unsaved edits rather than accidentally sharing a stale on-disk version. Share-cache files are disposable and old entries are pruned.

Recent files store URI/title/MIME metadata only, and now contain only documents for which Android has granted **persistable read access**. Transient attachment URIs (for example many files opened directly from messaging apps) work for the current editing session but are deliberately not added to Recent files because Android cannot guarantee that they can be reopened later. Older dead transient entries are pruned automatically.

## Android external-open integration

The module accepts text-oriented files internally, using the filename extension where available and MIME type as a fallback. The in-app file picker deliberately asks for all files so providers that mislabel source/configuration files as generic binary can still offer them; the reader rejects binary content after inspection.

Common MIME types include:

- `text/plain`
- `text/markdown`
- `application/json`
- `text/json`
- `application/x-ndjson`
- `application/ndjson`
- `application/jsonl`
- `text/*`
- `application/xml`
- `application/yaml`
- `application/toml`
- `application/sql`
- `application/x-sh`
- `*/*` (the picker fallback; binary content is rejected)

Android resolver visibility is controlled by the app manifest, which is shared integration outside the canonical module folder. The host `DocumentActivity` intent filter must advertise the same MIME set. v0.09 is delivered with a complete replacement manifest separately from the module ZIP; the module tree itself remains Master-Book compliant and contains no copied shared app files.

# ODK INTEGRATION

## Capability

Text documents
`document.text.open`

## Tags

Maturity: **Development**
Connectivity: **Offline**

## ODK INPUTS

| Canonical input | ODK type | Required | Meaning |
|---|---|---:|---|
| `document_mode` | select/text | optional | `library`, `new`, or `edit`. Lets the form explicitly request selection, creation, or editing. |
| `document_text` | text | optional | Source text supplied by the form; may be blank when `document_mode=new` or `edit`. |
| `document_title` | text | optional | Suggested title/filename. |
| `document_format` | select/text | optional | `text`, `markdown`, `json`, `jsonl`, or a recognised text/data/source format. |

## INTENT CALL

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='document.text.open',input_document_mode=${source_mode},input_document_text=${source_text},input_document_title=${source_title},input_document_format=${source_format},input_payload_mode='FULL',return_mode='flat')
```

## CANONICAL RETURNS

| Canonical return | ODK type | Presence | Meaning |
|---|---|---|---|
| `methodmesh_status` | text | always | Shared MethodMesh execution status. |
| `document_status` | text | always | Capability status. |
| `document_text` | text | success | Returned edited text. |
| `document_title` | text | always | Returned title. |
| `document_format` | text | always | The detected or requested text/data/source format. |
| `document_error` | text | failure/blank on success | Failure diagnostic. |
| `methodmesh_full_json` | text/JSON | always on handled ODK roundtrip | Shared metadata/audit payload. |

Canonical showcase: `example_odk_showcase_document_text_open.xlsx`. It contains one MethodMesh invocation, includes the three explicit open modes, uses canonical unprefixed return keys and does not require `methodmesh_return_namespace`.

The non-interactive method succeeds only when `document_text` is supplied. Without text it fails clearly rather than pretending that an unattended edit occurred.

## Privacy and dependencies

Core editing is offline. No online provider receives document contents. Storage is through Android content URIs chosen or supplied by the user. The module uses existing Android/Compose, FileProvider and Markwon dependencies; it introduces no new dependency.

## Validation status

v0.12 adds broad text-file compatibility, preserves unknown filename extensions, rejects obvious binary content, and adds focused format/extension tests. Promotion remains **Development** until local build and representative device/ODK validation pass.
