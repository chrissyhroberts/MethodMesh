# Live Stream Translation — required host dependency

Version 0.2.1 uses Google ML Kit streaming speech recognition for the optional **ML Kit Basic** and **ML Kit GenAI** providers. Because MethodMesh modules are source folders compiled inside the main `:app`, this library must be declared in the host app Gradle dependencies. A module-local documentation file cannot add a Gradle dependency to the host build.

## Required host-app change

Add this line to the `dependencies { ... }` block in `app/build.gradle.kts`:

```kotlin
implementation("com.google.mlkit:genai-speech-recognition:1.0.0-alpha1")
```

A sensible placement in the current MethodMesh file is immediately after the existing ML Kit translation dependency:

```kotlin
implementation("com.google.mlkit:translate:17.0.3")
implementation("com.google.mlkit:genai-speech-recognition:1.0.0-alpha1")
```

**Without that host-app change, `:app:compileDebugKotlin` will fail on the `com.google.mlkit.genai...` imports in `MlKitSpeechRecognitionProvider.kt`.**

No extra coroutines dependency is required for the current MethodMesh app: the app already compiles code using `CoroutineScope`, `SupervisorJob` and `Dispatchers.Main`.

## Provider requirements

- **Android**: platform `SpeechRecognizer`; automatic language detection/switching requires Android 14+ and recognition-service support.
- **ML Kit Basic**: microphone streaming requires Android 12/API 31+ even though the API library itself supports lower Android levels for non-microphone inputs.
- **ML Kit GenAI / Advanced**: device-limited; availability is checked at runtime with `checkStatus()` and required features are downloaded when Google reports them as downloadable.
- **Automatic**: in fixed-language mode, MethodMesh tries GenAI → Basic → Android and falls back visibly. In detect-language mode it selects Android because the ML Kit alpha API is configured with a fixed locale and does not expose Android-style language switching.

The ML Kit Speech Recognition API is alpha. Its public contract may change, so the provider is isolated behind `LiveSpeechRecognitionProvider` rather than leaking ML Kit types into MethodMesh method contracts.
