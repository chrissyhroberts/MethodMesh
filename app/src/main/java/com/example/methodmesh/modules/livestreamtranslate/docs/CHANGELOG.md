# Changelog

## 0.3.3 — 2026-09-25 — clean transcript, no speaker attribution

- Removed manual speaker tagging and speaker-slot controls completely.
- Removed speaker inputs, outputs and per-segment speaker fields; no deprecated speaker compatibility fields are retained because they were never used in a deployed contract.
- Simplified the readable committed transcript to chronological translated text, with only explicit transcript recording pause/resume markers.
- Recognition/language/provenance detail remains in structured segment/full JSON.
- Preserved all three established method IDs and the streaming follow-tail interaction.

## 0.3.2 — 2026-09-25

- Replaced the streaming mode card stack with one simple top-to-bottom accumulating transcript.
- Final translated segments append in chronological order; the revisable working translation is an inline final row rather than a separate large card.
- Added follow-tail autoscroll so the newest transcript remains in view while speech/translation revisions arrive.
- A deliberate vertical swipe suspends follow-tail, preventing automatic scrolling from fighting the user; **Follow live ↓** explicitly resumes live following.
- Kept per-line tap-to-copy while removing recognition-engine/language/provenance clutter from the transcript surface.
- No method IDs, setting keys, output keys, ODK `form_id`s or Commit semantics changed.

## 0.3.1 — 2026-09-24

- Rebased ML Kit language-catalog imports onto the v1.29 shared platform API (`com.example.methodmesh.platform.translation`).
- Corrected the Compose `rememberSaveable` import in the ODK Integration Card.
- Normalised `supportedCodes()` to `List<String>` at language-selector boundaries after the shared catalogue API changed to return a set.
- No method IDs, setting keys, output keys, ODK `form_id`s or streaming semantics changed.

## 0.3.0 — 2026-09-24

- Reviewed/migrated module against MethodMesh Master Book v1.29.
- Added explicit module version/maturity and canonical capability maturity/connectivity metadata.
- Existing `conversation.translate.live.fixed` and `conversation.translate.live.auto` IDs preserved; both now honestly tagged Development.
- Added experimental `conversation.translate.live.streaming` capability.
- Streaming mode uses ML Kit Speech Recognition continuous partial/final output rather than waiting for utterance completion to update translation.
- Added a three-stage streaming path: recognizer stream -> conflating translation worker -> revision arbiter.
- Stale asynchronous partial translations are dropped and cannot overwrite newer phrases.
- Partial hypotheses remain working-only; only final recognizer segments enter the committed transcript/segment JSON.
- Added `stream_response` policy (`fast`, `balanced`, `stable`).
- Streaming Automatic recognition routes GenAI -> Basic and intentionally excludes Android SpeechRecognizer.
- Restricted streaming source choices conservatively to ML Kit Basic-supported languages so fallback remains viable on devices without GenAI, including Pixel 7a-class devices.
- Added module-owned ODK integration card.
- Migrated active XLSForms to v1.29 `example_odk_showcase_*` naming and added a streaming single-invocation showcase; existing fixed/auto `form_id` identities preserved.
- Removed legacy active XLSForm filenames from the handoff to avoid duplicate form-library records.

## 0.2.3 — 2026-09-11

- Removed nested vertical scrolling that could crash Compose with infinite maximum-height constraints when the live meeting dialog opened.

## 0.2.2 — 2026-09-11

- Updated `CapabilityScreenScaffold` invocation for the then-current shared scaffold signature.

## 0.2.1 — 2026-09-11

- Documented required host dependency for ML Kit GenAI Speech Recognition.

## 0.2.0 — 2026-09-11

- Added Android / ML Kit Basic / ML Kit GenAI recognition-provider abstraction and safe provider hot-switching.
