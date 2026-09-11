# MethodMesh Live Stream Translation

## Purpose

`livestreamtranslate` is a meeting-oriented translation module derived from the existing `conversationtranslate` architecture.

It is for the situation: **I am in a meeting, I do not understand every language being spoken, and I want a continuously updating translated text feed.**

The module exposes two canonical MethodMesh capabilities rather than hiding materially different recognition behaviour behind one method ID:

- `conversation.translate.live.fixed` — known source language → target language;
- `conversation.translate.live.auto` — detect/switch the current spoken language → target language.

Both capabilities remain individually addressable from native capability launch, Presets, Protocols, RIL and ODK/XLSForm.

## User experience

The live surface is designed as a meeting feed, not as a two-person push-to-talk conversation.

At the top it shows the active translation route and listening state. While someone is speaking, partial recognition is shown in a temporary **Hearing…** card. Completed utterances are appended to a scrollable feed. Each feed card shows:

- optional speaker label;
- detected/selected source language;
- target language;
- source text;
- translated text;
- timestamp.

Tap any completed feed card to copy its translated text.

The bottom controls are always available:

- transcript ON/OFF;
- pause/resume listening;
- end session;
- back to setup.

## Mode 1 — fixed source language

Method ID: `conversation.translate.live.fixed`

This is the **robust** mode when the meeting language is known.

The user selects:

- source language;
- target language;
- optional offline speech preference;
- transcript state;
- speaker-tag settings.

The module explicitly configures Android speech recognition for the selected source locale and does not ask it to infer the language.

Before listening begins, the module prepares the required ML Kit translation models. Translation is on-device once those models are available.

## Mode 2 — automatic language detection/switching

Method ID: `conversation.translate.live.auto`

This mode requires **Android 14 / API 34 or later** and a speech-recognition service that implements Android language detection/switching.

The module requests:

- `RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION`;
- `RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH`;
- optional detection/switch allow-lists;
- one of Android's `high_precision`, `balanced` or `quick_response` switch sensitivities.

A user can optionally provide a comma-separated likely-language set such as:

`en,fr,es`

Restricting the candidate set can improve recognition-service language detection when the likely meeting languages are known.

The module **does not silently invent a language** if the recognizer returns speech text but no language-detection callback. That utterance remains visible as source text and is marked as untranslated with a clear explanation. The user can switch to fixed-language mode if their recognizer does not implement automatic switching.

Android reference:

- https://developer.android.com/reference/android/speech/RecognizerIntent
- https://developer.android.com/reference/android/speech/SpeechRecognizer
- https://developer.android.com/reference/android/speech/RecognitionListener

## Continuous listening model

Android documents `SpeechRecognizer` as session/utterance based and notes that it is not designed as an unlimited continuous-recognition API.

MethodMesh therefore implements a controlled **restart loop**:

1. start recognition;
2. show partial speech while the utterance is in progress;
3. receive the final utterance;
4. translate it;
5. restart recognition after a short settling delay;
6. recover automatically from silence/no-match and selected transient recognizer errors.

This produces meeting-style continuous behaviour while remaining inside Android's supported recognizer boundary.

There can still be a small gap between recognition sessions. The module does not claim sample-perfect, zero-gap transcription.

### Why not use the new ML Kit GenAI speech recognizer by default?

As of September 2026, ML Kit also exposes an **alpha** GenAI Speech Recognition API with genuine continuous streaming from the microphone. It is promising, but Basic mode currently documents a much smaller locale set than MethodMesh translation supports, Advanced mode is limited to selected devices, and the published API does not document speaker diarisation or automatic language switching. Introducing an alpha dependency would therefore make the default capability less portable rather than more robust. It is a good future optional recognition provider, not the baseline implementation.

Reference: https://developers.google.com/ml-kit/genai/speech-recognition/android

## Translation

Translation uses the same ML Kit translation family already used by `conversationtranslate`.

The module prepares source/target language models before fixed-language listening. In automatic mode it prepares the target model first and downloads a newly detected source model on first use if required.

ML Kit reference:

- https://developers.google.com/ml-kit/language/translation/android
- https://developers.google.com/ml-kit/language/translation/translation-language-support

## Speaker separation / diarisation

Android's `SpeechRecognizer` language APIs do **not** expose speaker diarisation or speaker IDs.

This module therefore does not guess speaker identity using unreliable audio heuristics.

Instead, when **Speaker tags** are enabled, the live screen provides compact `?`, `1`, `2`, `3`… speaker chips. The selected label is attached to the next completed utterance and remains active until changed. This is designed so a meeting participant can rapidly mark speaker changes without interrupting the live feed.

Automatic diarisation is deliberately kept as a future provider boundary rather than being faked. A cloud STT provider such as Google Cloud Speech-to-Text can return diarised speaker tags, but that introduces network, credential, cost and data-governance requirements that are inappropriate to smuggle into the default MethodMesh capability.

See `SPEAKER_SEPARATION.md`.

## Transcript semantics

Live translation and transcript capture are separate states.

- Translation continues while transcript capture is OFF.
- Feed items generated while transcript capture is OFF remain visible during the live session.
- They are excluded from committed `live_translation_segments_json` and from `live_translation_transcript`.
- Transcript pause/resume markers are preserved in the committed transcript.

This follows the privacy semantics of the existing conversation translator: turning recording off must not merely hide content while still returning it in FULL JSON.

## Live working result → Commit

The live feed is a working state.

Ending the session stops speech capture immediately. If one or more completed utterances are still translating, the UI enters a short **Finishing translations…** state and only constructs the MethodMesh result after those pending translation callbacks have completed. Direct native use then presents the result through `CapabilityScreenScaffold` for Commit. Preset/protocol/ODK invocation can use the normal `submitsImmediately` path.

Retry clears the previous session and starts a new live working result.

## Presets and protocols

### `conversation.translate.live.fixed`

Settings:

- `source_language`
- `target_language`
- `prefer_offline`
- `transcript_on_start`
- `speaker_tagging`
- `speaker_slots`

### `conversation.translate.live.auto`

Settings:

- `target_language`
- `allowed_languages`
- `switch_sensitivity`
- `prefer_offline`
- `transcript_on_start`
- `speaker_tagging`
- `speaker_slots`

All settings are surfaced through `context.onSettingsChanged` and respect fixed/native preset handling.

## ODK/XLSForm

Module-owned XLSForms:

- `example_odk_live_stream_translate_fixed.xlsx`
- `example_odk_live_stream_translate_auto.xlsx`

The fixed form calls:

`conversation.translate.live.fixed`

The automatic form calls:

`conversation.translate.live.auto`

Both request FULL payload capture and expose the flat fields required for normal ODK round-trip use.

## Outputs

Common fields:

- `live_translation_transcript`
- `live_translation_segments_json`
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
- `live_translation_speaker_tagging`
- `live_translation_speaker_slots`
- `live_translation_last_detected_language`
- `live_translation_segment_count`
- `live_translation_started_time_iso`
- `live_translation_finished_time_iso`
- `live_translation_status`
- `live_translation_error`

`live_translation_segments_json` contains only utterances for which transcript capture was ON.

## Language routing and dependencies

The module carries a small module-local speech-locale bridge derived from the audited routing table in the existing Conversation Translator. This preserves its concrete speech locales (for example `en-GB`, `fr-FR`, `ar-SA`, `fil-PH`) without creating a compile-time dependency on another feature module.

It still uses the shared MethodMesh ML Kit language catalogue and the ML Kit Translation dependency already used by the existing translation capabilities. No separate language-ID library is required: automatic mode uses Android 14+ speech-recognizer language detection/switching so that recognition and detected language stay coupled.

## Permissions

The capability requires Android `RECORD_AUDIO` permission. It uses the existing runtime permission pattern and does not add a new app-level permission contract.

## Speech-recognition providers and hot-switching

Version 0.2.0 separates the meeting session from the speech-recognition provider. The fixed-language capability can use:

- `auto` — try ML Kit GenAI/Advanced, then ML Kit Basic, then Android, with visible fallback;
- `android` — platform `SpeechRecognizer`;
- `mlkit_basic` — ML Kit continuous microphone streaming, Basic model;
- `mlkit_genai` — ML Kit continuous microphone streaming, Advanced/GenAI model.

Detect-language mode exposes `auto` and `android` only because Android 14+ provides the language detection/switching callbacks used to determine the source language. The current ML Kit alpha API takes a configured locale and does not provide equivalent automatic language switching.

The active provider can be changed while the meeting is live. If nobody is currently speaking, the provider is replaced immediately. If an utterance is in progress, the switch is queued until that utterance emits its final result. The meeting feed, transcript state, speaker tag, translation engine and Commit lifecycle are not restarted.

Each segment records `recognition_engine` and `recognition_engine_detail`; session outputs also record requested engine, last active engine and engine-switch count. ML Kit alpha currently does not expose recognition confidence equivalent to Android's confidence array, so confidence is left absent rather than inferred.

See `DEPENDENCIES.md` for the required ML Kit speech dependency and current device/API constraints.

## Known limits

- Android recognition quality and offline-language availability are recognizer/device specific.
- `EXTRA_PREFER_OFFLINE` is a preference on generic recognizers; on Android 12+ the controller uses `createOnDeviceSpeechRecognizer` when offline mode is requested and the device advertises an on-device recognizer.
- Auto language switching is recognizer-service dependent even on Android 14+.
- Android explicitly says `SpeechRecognizer` is not intended for indefinite continuous recognition; MethodMesh chains utterance sessions and may have short restart gaps.
- Neither the Android recognizer nor the current ML Kit speech alpha exposes a speaker-diarisation contract used by this module; speaker labels remain explicit/manual.
- ML Kit speech model preparation can delay first recognition for a newly selected provider/locale; the UI reports this state rather than silently dropping back.
- Automatic source-language translation-model download can delay the first translated utterance for a newly encountered language.

## Version

MethodMesh live-stream translation module: `0.2.0`.
