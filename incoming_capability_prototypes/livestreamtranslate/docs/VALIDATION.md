# Validation checklist — Live Stream Translation v0.2.2

## Host build precondition

- [ ] `app/build.gradle.kts` contains `implementation("com.google.mlkit:genai-speech-recognition:1.0.0-alpha1")`.
- [ ] Run `:app:compileDebugKotlin` only after that shared host dependency has been added.

## Static/module wiring

- [ ] `LiveStreamTranslateCapabilityScreen.kt` calls the current shared scaffold with `capabilityId`, `context`, and `canGoBack`, and does not pass the retired scaffold `description` parameter.
- [ ] `LiveStreamTranslateModule` is registered by the MethodMesh module registry/discovery path.
- [ ] Both methods appear independently in Capabilities.
- [ ] Both methods are independently available to Presets and Protocols.
- [ ] RIL resolves fixed and automatic meeting-translation phrases to the intended method.
- [ ] The two module-owned XLSForms appear in ODK Forms.


## Speech providers and hot-switching

- [ ] Host app includes `com.google.mlkit:genai-speech-recognition:1.0.0-alpha1`.
- [ ] Fixed mode exposes Automatic, Android, ML Kit Basic and ML Kit GenAI.
- [ ] Detect-language mode exposes only Automatic and Android.
- [ ] Automatic fixed mode falls back GenAI → Basic → Android when a provider is unavailable.
- [ ] Switching engines while idle changes provider immediately without ending the meeting.
- [ ] Switching while someone is speaking waits for the current final utterance, then changes provider.
- [ ] The utterance immediately before a switch is emitted exactly once.
- [ ] Each stored segment contains `recognition_engine` and `recognition_engine_detail`.
- [ ] Session output contains requested engine, last active engine and switch count.
- [ ] ML Kit segments leave recognition confidence absent rather than inventing a value.
- [ ] Explicit ML Kit GenAI selection reports unsupported-device/model status visibly.
- [ ] ML Kit downloadable model preparation reports progress and begins listening after completion.

## Fixed-language mode

- [ ] Select French → English and start.
- [ ] Required ML Kit models are prepared before listening.
- [ ] Partial French speech appears in the Hearing card.
- [ ] Final source text appears in the feed.
- [ ] English translation appears without replacing/removing the source text.
- [ ] Recognition automatically restarts after each final utterance.
- [ ] Silence/no-match does not terminate the session.
- [ ] Pause stops recognition and Resume restarts it.
- [ ] End produces a working result ready for Commit.
- [ ] End during an in-flight translation stops recognition, shows Finishing translations…, and does not commit an empty/stale translation.
- [ ] Tap a feed card and confirm translated text is copied.

## Auto-language mode

Test on Android 14+ with a recognition service that supports language switching.

- [ ] Start with target English and likely languages `fr,es,de`.
- [ ] Speak French; UI shows French detection and English translation.
- [ ] Next speak Spanish; UI shows Spanish detection and English translation.
- [ ] Switching does not require reopening the session.
- [ ] `high_precision`, `balanced` and `quick_response` are passed through without crash.
- [ ] If the recognizer returns text without language metadata, the utterance is not silently assigned a guessed language.
- [ ] On Android <14, the capability refuses auto mode and directs the user to fixed-language mode.

## Speaker tags

- [ ] Speaker tags OFF removes speaker controls and stores blank speaker labels.
- [ ] Speaker tags ON shows the configured number of chips.
- [ ] Select Speaker 2; next completed utterance is labelled Speaker 2.
- [ ] Change to Speaker 3; subsequent utterance uses Speaker 3.
- [ ] `Speaker ?` is available when identity is uncertain.

## Transcript privacy semantics

- [ ] Start with transcript ON and speak one utterance.
- [ ] Turn transcript OFF and speak one utterance; it remains visible in the live feed.
- [ ] Turn transcript ON and speak another utterance.
- [ ] End and Commit.
- [ ] `live_translation_segments_json` includes only the two recorded utterances.
- [ ] `live_translation_transcript` excludes the unrecorded utterance and contains pause/resume markers.
- [ ] FULL JSON does not leak the unrecorded utterance through the method output fields.

## State and lifecycle

- [ ] Rotate/recompose during setup; settings remain.
- [ ] Rotate/recompose during a recorded session; recorded feed state remains.
- [ ] Leaving the immersive live surface via Back to setup stops the recognizer.
- [ ] Disposing the capability destroys `SpeechRecognizer` and closes ML Kit translators.
- [ ] Retry clears previous result and starts a clean working state.

## Offline behaviour

On Android 12+ with an installed on-device recognizer:

- [ ] Enable Prefer offline.
- [ ] Controller uses the on-device recognizer when advertised.
- [ ] Fixed-language recognition works for an installed offline speech model.
- [ ] Missing offline speech model fails visibly rather than silently pretending to be offline.
- [ ] Previously downloaded ML Kit translation models work without network.

## ODK round-trip

For each XLSForm:

- [ ] XLSForm validates.
- [ ] Launch intent opens the correct capability.
- [ ] Input settings arrive correctly.
- [ ] End/Commit returns `methodmesh_execution_id` and `methodmesh_status`.
- [ ] Transcript and segment count populate the expected fields.
- [ ] FULL JSON is returned.
- [ ] Auto form also returns `live_translation_last_detected_language`.

## Device matrix

Minimum recommended manual matrix:

1. Android 13 device — fixed mode; auto mode correctly blocked.
2. Android 14/15 device with Google speech service — fixed + auto.
3. Device with downloaded offline recognition model — offline fixed mode.
4. No-network test after translation/speech models are installed.
5. No-network test with missing model to verify useful failure messaging.
