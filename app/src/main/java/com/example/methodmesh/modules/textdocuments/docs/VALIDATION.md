# Text documents v0.09 validation

## Static architecture review

- Exactly one canonical method: `document.text.open`.
- Exactly one module entry point: `TextDocumentsModule`.
- Dashboard/native, preset, protocol and ODK all address the same method ID.
- `DocumentActivity` is an Android file-launch origin only; it does not define a second MethodMesh method.
- Capability requests only the generic `CapabilityHostPresentation.Immersive` contract; no capability-specific shared-host branch is required.
- No `platform/documents` dependency or copied shared UI implementation is present.
- Module folder contains module-owned code/docs only.

## UX review

Expected direct-use path:

1. Open **Text documents** from Capabilities.
2. See New / Open file / New from clipboard / Recent files.
3. New/Open enters the editor directly — no Commit/result screen.
4. Edit text while the document remains the dominant surface.
5. Save/Save As and Share are available without leaving the editor.
6. Library returns to the toolkit start screen; unsaved changes are protected.

Expected caller path:

1. Preset/protocol/ODK supplies text or enters the same acquisition/library surface.
2. User edits in the same editor.
3. The origin-aware return action freezes and returns the canonical payload.
4. Automatic-return callers leave immediately; normal dashboard use never sees the return action.

## Device acceptance checks

- Create/save/reopen TXT.
- Create/save/reopen Markdown; switch Preview/Edit source.
- Open/edit/save JSON and confirm byte-for-byte text is unchanged except explicit edits.
- Open/edit/save `.jsonl` and `.ndjson` without automatic formatting.
- Open TXT/MD/JSON/JSONL from Android Files or another provider.
- Confirm read-only provider shows Save As rather than silently failing Save.
- Share an unsaved edit and confirm recipient receives the edited text/file, not the stale source file.
- Open keyboard and confirm editor, status/return action and floating menu remain reachable above IME.
- Back/Library with edits: Cancel, Discard and Save flows behave correctly.
- Rotate/recreate activity and confirm ordinary saved Compose state is retained rather than immediately reloading the original URI over it.
- Recent file reopens when URI access remains available and is removed after access becomes invalid.
- ODK showcase: supplied-text path and blank-text interactive path both return canonical unprefixed fields plus shared MethodMesh fields.

## Build status

Static source/API review completed against the supplied current `app/src/main` snapshot. A complete Gradle project was not available in this runtime; local `:app:compileDebugKotlin` and `:app:assembleDebug` remain required before promotion beyond Development.

## v0.10 hardening checks

- Recent repository now rejects non-persisted content URIs.
- Existing stale transient recent entries are pruned on read.
- External transient VIEW/EDIT URIs remain session-readable without being persisted into Recent files.
- Raw provider permission exception text is not surfaced in the library.

## v0.11 toolkit/preset/ODK checks

- Native editor exposes Save, Copy and Share as first-class visible actions.
- Save as preset writes a canonical `CapabilityPreset` using `ProtocolLibraryRepository`; it does not save the document file.
- Preset modes map to `document_mode=library|new|edit`; template mode is explicit and is the only preset path that stores document text.
- Direct native presets remain shortcut-like; protocol/sequence invocations still receive an execution result.
- ODK label is `Return to form`; file Save is not required before return.
- XLSForm showcase includes one canonical invocation with `input_document_mode` and canonical unprefixed returns.
