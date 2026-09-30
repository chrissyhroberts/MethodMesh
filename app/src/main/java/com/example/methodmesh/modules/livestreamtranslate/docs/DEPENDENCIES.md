# Dependencies and provider notes

Live Stream Translation v0.3.3 targets MethodMesh Master Book v1.29 and the current MethodMesh module metadata/runtime contracts.

## Host Gradle dependency

The host app must include:

```kotlin
implementation("com.google.mlkit:genai-speech-recognition:1.0.0-alpha1")
```

The existing MethodMesh host already uses ML Kit Translate (`com.google.mlkit:translate`).

## ML Kit Speech Recognition

Official documentation: https://developers.google.com/ml-kit/genai/speech-recognition/android

As of 2026-09-25:

- API status: alpha; backward-incompatible changes remain possible.
- Basic mode: on-device traditional recognizer, broadly available on Android API 31+.
- Advanced/GenAI mode: on-device Gemini-based recognizer on supported devices; current documented device support is Pixel 10 and Pixel 11.
- Input: microphone or supported PCM stream/file-descriptor source.
- Output: continuous stream of revisable partial text followed by final text.
- Runtime `checkStatus()` remains authoritative; the module does not hard-code a device whitelist as its availability decision.

The experimental `conversation.translate.live.streaming` capability is built specifically around this continuous partial/final stream.

## ML Kit Translate

Translation runs through the existing on-device ML Kit Translate API. Language packs may need downloading before first use. Once required models are present, streaming capability core execution does not require sending meeting audio/text to a cloud service.

## Android SpeechRecognizer

The fixed and automatic-language prototype modes retain Android SpeechRecognizer as an optional provider. Automatic-language mode uses Android 14+ language detection/switching where the installed recognition service supports it.

Android SpeechRecognizer is not offered by the new streaming capability because that method promises ML Kit continuous partial-result streaming semantics.

## Third-party/privacy summary

Provider: Google ML Kit / Android platform speech APIs.  
Core streaming audio leaves device: **No**. The module intentionally has no cloud speech provider.  
Credentials/API key: **None** for implemented providers.  
Initial connectivity: model/AICore preparation may require network access.  
Attribution/licence: governed by Android/Google ML Kit SDK terms; no third-party model files are redistributed inside this module.
