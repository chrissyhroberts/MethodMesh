# Writing-guide alignment

Aligned against `CAPABILITY_WRITING_GUIDE.md` supplied 2026-09-03.

This revision corrects the previous malformed Kotlin patch and follows the current MethodMesh patterns for:

- Development status;
- module-owned settings;
- `input_text` runtime input naming;
- preset visibility through `settingShouldBeShown`;
- intent/ODK immediate execution when text is supplied;
- proof ZIP as the primary media result;
- transient `FileProvider` cache URI;
- explicit durable saving rather than automatic native saving;
- compact native result preview;
- optional full audit JSON;
- state strings/URIs persisted with `rememberSaveable`;
- module-owned README and XLSForm example.

Repository-level `000_Roadmap.md` bookkeeping is intentionally not included because the requested canonical handoff is exactly the module folder.


## File-delivery correction

The proof URI is now explicitly treated as an internal shareable transport rather than as the deliverable itself. The native screen keeps its own post-result controls visible and shares/saves/exports the actual `application/zip` bytes. This avoids the generic text-result presentation treating an otherwise valid FileProvider URI as plain text.
