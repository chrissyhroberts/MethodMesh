# Conversation translator

`conversation.translate` runs a bilingual, operator-controlled conversation translator.

It combines:

- Android speech recognition for microphone capture;
- ML Kit on-device translation for the configured language pair;
- Android text-to-speech for optional spoken translation output.

The capability is currently in development. It is intended as both a live translation aid and a way to document conversations as a bilingual transcript.

## Native workflow

1. Choose two languages.
2. Choose whether translated text should be spoken aloud.
3. Press either speaker button whenever that person speaks.
4. MethodMesh transcribes the spoken chunk, translates it, shows the translated text in large type, and optionally speaks it.
5. Repeat as needed. Either side can speak repeatedly; alternation is not enforced.
6. Press **End conversation** to produce the transcript result.

Native sharing sends only the transcript text.

## Offline behaviour

The translation step uses ML Kit language models. Offline translation requires the relevant language models to be downloaded before the session.

Speech recognition offline support depends on the Android recognizer and installed speech packs on the device. The capability requests offline recognition when configured, but Android may still depend on the recognizer available on the phone.

ML Kit language packs are shared across translation capabilities and managed from **Settings → Language packs**. Downloaded languages appear first; supported languages can be downloaded; languages not currently available in ML Kit are shown separately.

## Preset settings

The preset stores configuration only:

- `language_a`
- `language_b`
- `label_a`
- `label_b`
- `spoken_output`
- `prefer_offline`

Conversation content is never stored as a preset value.

## ODK/XLSForm intent

Place the MethodMesh intent on a `begin_group` row. The group children are return fields only; do not put the intent on the transcript text field itself.

Example intent:

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='conversation.translate',input_language_a=${language_a_input},input_language_b=${language_b_input},input_spoken_output=${spoken_output_input},input_prefer_offline=${prefer_offline_input},input_payload_mode='FULL',return_mode='flat')
```

The example workbook is [`example_odk_conversation.translate.xlsx`](example_odk_conversation.translate.xlsx). It returns:

- `conversation_transcript` as the main result;
- `methodmesh_full_json` for turn-level text, language settings, timestamps and status.

## Outputs

Main result:

- `conversation_transcript`

Background JSON fields:

- `conversation_turns_json`
- `conversation_language_a`
- `conversation_language_b`
- `conversation_label_a`
- `conversation_label_b`
- `conversation_spoken_output`
- `conversation_prefer_offline`
- `conversation_turn_count`
- `conversation_started_time_iso`
- `conversation_finished_time_iso`
- `conversation_status`
- `conversation_error`
