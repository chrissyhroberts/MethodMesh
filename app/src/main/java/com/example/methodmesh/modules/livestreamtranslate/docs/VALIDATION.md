# Validation — Live Stream Translation v0.3.3

Target: MethodMesh Master Book v1.29.

## Contract review

- Module ID preserved: `livestreamtranslate`.
- Established method IDs preserved:
  - `conversation.translate.live.fixed`
  - `conversation.translate.live.auto`
- Additive method:
  - `conversation.translate.live.streaming`
- All established non-speaker output fields are preserved. Draft speaker-tagging inputs/outputs were removed before deployment at the user's request; no deprecated compatibility fields are retained.
- Module version explicitly `0.3.3`.
- Maturity/connectivity metadata:
  - fixed: Development / ONLINE_OFFLINE
  - auto: Development / ONLINE_OFFLINE
  - streaming: Experimental / ONLINE_OFFLINE
- No capability-specific shared Home/registry edit is required by this module; v1.29 metadata is module-owned.

## Streaming review

The ML Kit speech provider already consumes `startRecognition(...).collect` and emits partial/final responses. the v0.3.x line adds a streaming translation projection above that provider:

- every partial source revision receives a monotonic revision number;
- translation submissions are conflated using `stream_response` delay;
- only the latest revision may update the visible translated working phrase;
- final recognizer responses invalidate any outstanding partial translation callback;
- final segments continue through the established canonical segment translation path;
- no partial/revisable text is written to committed transcript JSON.

This separation is intentional and testable: working UI may change rapidly; Commit payload may not.

`StreamingRevisionGate.kt` is pure Kotlin. Its monotonic-revision / stale-revision rejection / invalidation behaviour was compiled and executed independently during this handoff (`StreamingRevisionGate OK`).

## Provider/device expectations

- ML Kit Speech Recognition Basic: API 31+ potential availability; runtime model status is authoritative.
- ML Kit GenAI/Advanced: runtime `checkStatus()` authoritative. Current Google documentation lists Pixel 10 and Pixel 11; a Pixel 7a should therefore normally fall back to Basic when Automatic is selected.
- Explicit GenAI selection should surface unsupported/unavailable rather than silently switch provider.
- Streaming mode excludes Android SpeechRecognizer.
- Detect-language mode remains Android-only and requires Android 14+ language switching support.

## ODK/XLSForm review

Active canonical examples:

- `example_odk_showcase_conversation_translate_live_fixed.xlsx`
- `example_odk_showcase_conversation_translate_live_auto.xlsx`
- `example_odk_showcase_conversation_translate_live_streaming.xlsx`

Checks:

- exactly one MethodMesh invocation per workbook;
- canonical method IDs;
- no `methodmesh_return_namespace`;
- `methodmesh_status` captured;
- `methodmesh_full_json` captured;
- canonical unprefixed live-translation returns;
- fixed/auto historic `form_id` values retained after filename migration;
- streaming form has its own new `form_id`.

## Native UX review

- primary interaction remains the live meeting surface;
- streaming live translated phrase updates in place and is tap-to-copy;
- final feed cards remain tap-to-copy;
- no speaker attribution controls, fields or inferred identities;
- transcript ON/OFF changes only final-segment retention, not the live feed;
- pause/resume and End session remain on the live surface;
- final transcript/segments remain separate from technical/full JSON.

## Build/test limitations of isolated handoff

This module handoff does not contain the whole MethodMesh Gradle project. Standalone Kotlin syntax checking therefore reports unresolved Android/Compose/MethodMesh/ML Kit symbols by design. The handoff is additionally checked against current MethodMesh master interface definitions and Google ML Kit Speech Recognition documentation.

Before promotion of the new streaming method beyond Experimental, run in the target checkout:

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Then exercise on-device:

1. Pixel 7a / API 31+ class device: Streaming + Automatic should reject/fail GenAI runtime availability and fall back to ML Kit Basic.
2. Speak a long sentence without pausing: translated working phrase should revise before final recognition.
3. Rapid partial revisions must never cause an older translation to replace a newer one.
4. End session: committed transcript contains only final segments.
5. Pause/resume and Back to setup should invalidate outstanding partial translations.
6. Fixed and automatic-language prototype modes should retain their previous behaviours.
7. Direct, preset, protocol and each ODK showcase should resolve the same canonical method/output contracts.

## External documentation checked

- https://developers.google.com/ml-kit/genai/speech-recognition/android
- https://developers.google.com/ml-kit/language/translation/android
- https://developer.android.com/reference/android/speech/SpeechRecognizer

## Handoff packaging checks

- Module ZIP contains exactly one top-level `livestreamtranslate/` module root.
- ZIP compressed-data integrity check passed.
- All three canonical XLSX compressed-data integrity checks passed.
- Artifact-tool inspection confirmed one `body::intent` invocation per workbook, no return namespace, and presence of both `methodmesh_status` and `methodmesh_full_json`.
- Final spreadsheet formula/error scan returned no `#REF!`, `#DIV/0!`, `#VALUE!`, `#NAME?` or `#N/A` matches.
- Isolated Kotlin parse/structure scan found no parser or non-exhaustive-`when` diagnostics; Android/Compose/MethodMesh symbols cannot be resolved without the full host classpath.
