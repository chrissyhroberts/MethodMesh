# Speech transcription

Capability: `speech.transcribe`

This module opens Android speech recognition and returns recognised text to MethodMesh, ODK, or the scheduler.

## Intent example

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='speech.transcribe',input_speech_language='en-GB',input_speech_prompt='Please speak now',input_speech_prefer_offline='true',return_mode='flat')
```

## Outputs

- `speech_language`
- `speech_prompt`
- `speech_prefer_offline`
- `speech_text`
- `speech_alternatives_json`
- `speech_status`
- `speech_error`
- `speech_transcribed_time_iso`

## Notes

This uses the Android speech recognizer. Offline transcription depends on the speech engine and offline language packs installed on the device.
