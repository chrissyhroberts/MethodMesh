# Live stream translation

Module ID: `livestreamtranslate`  
Module version: `0.3.3`  
Reviewed against: MethodMesh Master Book v1.29 (2026-09-23)

## Purpose

Live Stream Translation is a meeting-oriented language instrument. It keeps the useful translated feed on the live working surface, separates revisable working hypotheses from committed transcript data, and preserves the same canonical methods across direct native runs, presets, protocols and ODK.

## Capabilities

| Method ID | Native name | Maturity | Connectivity | Purpose |
|---|---|---|---|---|
| `conversation.translate.live.fixed` | Live translation · fixed language | Development | ONLINE_OFFLINE | Robust known-source-language meeting translation; final recognition segments are translated. |
| `conversation.translate.live.auto` | Live translation · detect language | Development | ONLINE_OFFLINE | Android 14+ language detection/switching followed by translation of final recognition segments. |
| `conversation.translate.live.streaming` | Live translation · streaming | Experimental | ONLINE_OFFLINE | ML Kit continuous partial-result speech stream translated while speech is still in progress; final recognition segments remain the Commit boundary. |

The two established method IDs are unchanged. `conversation.translate.live.streaming` was added in v0.3.0 and is retained unchanged in v0.3.3.

## Streaming architecture

The experimental streaming capability uses three logical processors:

1. **Speech stream** — ML Kit Speech Recognition consumes microphone audio and emits a continuously revised partial transcript plus final responses.
2. **Translation worker** — the newest source-text revision is translated with on-device ML Kit Translate. Revisions are conflated for a short response window so the translator is not asked to process every tiny character-level change.
3. **Projection arbiter** — every source revision has a monotonically increasing revision number. Only a translation callback matching the newest revision may update the visible live phrase. Slow/stale callbacks are discarded.

This means the display can update before an utterance is final without allowing an older asynchronous translation to overwrite a newer phrase.

### Working state versus committed data

Partial speech hypotheses and their translations are **working/current results only**. They are deliberately not written to `live_translation_segments_json` or the committed transcript. A final recognizer response creates the canonical segment; the normal final-segment translation path then supplies the committed transcript. Commit therefore freezes final speech segments rather than transient hypotheses.

This is the MethodMesh v1.29 `configure/interact -> live current result -> Commit` model rather than a second streaming-specific execution contract.

## Streaming response setting

`stream_response` controls the conflation delay before the newest partial hypothesis is translated:

- `fast` — approximately 120 ms;
- `balanced` — approximately 240 ms (default);
- `stable` — approximately 450 ms.

This setting does not claim statistical confidence. The current on-device ML Kit Speech Recognition alpha API exposes revisable partial/final text but does not expose a calibrated stability/confidence contract for partial phrases. The UI therefore describes the displayed text as a **revisable working phrase**, not a confidence-scored “most likely phrase”.

## Speech providers

### Fixed mode

- Automatic: ML Kit GenAI -> ML Kit Basic -> Android.
- Android.
- ML Kit Basic.
- ML Kit GenAI.

Explicit provider selection does not silently fall back. Automatic mode may fall back when runtime model/device checks fail.

### Detect-language mode

Uses Android SpeechRecognizer language detection/switching. ML Kit Speech Recognition currently requires a fixed locale, so it is not offered for this mode.

### Streaming mode

The live streaming surface is a plain accumulating transcript rather than a stack of result cards. Final translated segments append from top to bottom. The current revisable translation is shown inline at the bottom. The transcript follows the newest text automatically; a deliberate vertical swipe suspends follow-tail until **Follow live ↓** is selected.


- Automatic: ML Kit GenAI -> ML Kit Basic.
- ML Kit Basic.
- ML Kit GenAI.

Android SpeechRecognizer is intentionally excluded from this capability so that `conversation.translate.live.streaming` has a clear contract: its primary working feed is driven by ML Kit's continuous partial-result stream rather than utterance chaining.

The source-language selector is conservatively restricted to locales documented for ML Kit Basic so Automatic remains able to fall back from GenAI to Basic. On a Pixel 7a, for example, GenAI/Advanced is not currently supported but Basic is available on API 31+ devices.

## Inputs

### `conversation.translate.live.fixed`

- `source_language`
- `target_language`
- `speech_engine`: `auto`, `android`, `mlkit_basic`, `mlkit_genai`
- `prefer_offline`
- `transcript_on_start`

### `conversation.translate.live.auto`

- `target_language`
- `allowed_languages`
- `switch_sensitivity`: `high_precision`, `balanced`, `quick_response`
- `speech_engine`: `auto`, `android`
- `prefer_offline`
- `transcript_on_start`

### `conversation.translate.live.streaming`

- `source_language`
- `target_language`
- `speech_engine`: `auto`, `mlkit_basic`, `mlkit_genai`
- `stream_response`: `fast`, `balanced`, `stable`
- `transcript_on_start`

## Canonical outputs

All three methods preserve the established output schema:

- `live_translation_transcript` — primary beef; clean chronological translated text from final recorded segments only.
- `live_translation_segments_json` — structured final recorded segments with recognition-provider provenance.
- `live_translation_mode`
- `live_translation_source_language`
- `live_translation_target_language`
- `live_translation_auto_allowed_languages`
- `live_translation_switch_sensitivity`
- `live_translation_speech_engine_requested`
- `live_translation_last_speech_engine`
- `live_translation_engine_switch_count`
- `live_translation_prefer_offline`
- `live_translation_transcript_enabled_at_end`
- `live_translation_last_detected_language`
- `live_translation_segment_count`
- `live_translation_started_time_iso`
- `live_translation_finished_time_iso`
- `live_translation_status`
- `live_translation_error`

`methodmesh_full_json` is provided by the shared MethodMesh transport/envelope and is not redefined as a module-specific field.

## Native UX and Commit

The live meeting view is the primary working surface. It shows:

- active recognition provider;
- current language direction;
- current partial speech;
- in streaming mode, the revisable live translated phrase;
- accumulated final translated segments;
- transcript recording state;
- pause/resume and End session.

Final segment cards are tap-to-copy. The streaming live phrase is also tap-to-copy when a translation is available. Ending the session creates the working canonical result; MethodMesh Commit/automatic-return semantics then govern closeout.

## ODK / XLSForm

Active canonical showcase workbooks are:

- `example_odk_showcase_conversation_translate_live_fixed.xlsx`
- `example_odk_showcase_conversation_translate_live_auto.xlsx`
- `example_odk_showcase_conversation_translate_live_streaming.xlsx`

Each contains exactly one MethodMesh invocation, uses canonical unprefixed return fields, does not set `methodmesh_return_namespace`, and captures both `methodmesh_status` and `methodmesh_full_json`.

The historical fixed/auto `form_id` values are preserved in their renamed showcase workbooks to avoid silently breaking deployed identities.

The capability setup screen also exposes a module-owned **ODK integration** card with the exact method ID, inputs, `body::intent` pattern and canonical returns.

## Presets and protocols

All three methods are independently registered canonical capabilities. Fixed preset settings remain fixed; declared runtime inputs remain runtime inputs. Protocols invoke the same method IDs and result schema as direct native/ODK execution.

The streaming partial phrase is UI working state and is not a hidden protocol-only or dashboard-only output.

## Permissions and persistence

- `RECORD_AUDIO` is required for microphone recognition.
- Working transcript/feed state uses Compose saveable state where practical.
- No session transcript is silently written to a module-private datastore.
- Only the normal MethodMesh Commit / explicit save/share/export paths persist output.

## Dependencies and privacy

See `DEPENDENCIES.md`.

Core streaming recognition and translation are designed to run on-device after required models are present. Google ML Kit GenAI Speech Recognition is currently an alpha API. The module has no cloud speech provider, cloud credential path or meeting-audio upload path.

## Sources for provider behaviour

- ML Kit GenAI Speech Recognition Android: https://developers.google.com/ml-kit/genai/speech-recognition/android
- ML Kit on-device translation: https://developers.google.com/ml-kit/language/translation/android
- Android SpeechRecognizer / RecognizerIntent: https://developer.android.com/reference/android/speech/SpeechRecognizer
