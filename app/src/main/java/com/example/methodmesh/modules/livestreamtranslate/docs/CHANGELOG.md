# Changelog

## v0.2.3

- Fixed runtime Compose crash when opening the live meeting surface.
- Removed the setup pane's nested `verticalScroll`; scrolling remains owned by the MethodMesh host.
- Preserved the bounded live-feed `LazyColumn` inside the full-screen meeting dialog.

## 0.2.2 — current MethodMesh scaffold contract fix

- Updated `CapabilityScreenScaffold` invocation to the current MethodMesh host contract.
- Removed obsolete scaffold-level `description` argument; descriptions remain owned by each `CapabilityScreenSpec`.
- Added required `capabilityId`, `context`, and `canGoBack = context.stepNumber > 1` arguments.
- No method IDs, settings, outputs, speech-provider behavior, hot-switch semantics, or ODK contracts changed.

## 0.2.1 — host build integration correction

- Explicitly marks the ML Kit GenAI speech artifact as a **required host-app Gradle change** when this module is installed.
- Documents the exact `app/build.gradle.kts` line required for `MlKitSpeechRecognitionProvider.kt` to compile.
- Clarifies that the returned module folder cannot modify the central app Gradle file under the MethodMesh module handoff rules.
- No method IDs, settings, output contracts, hot-switch semantics or ODK contracts changed.

## 0.2.0 — speech-provider hot-switching

- Added a provider abstraction underneath the continuous meeting session.
- Added Android, ML Kit Basic and ML Kit GenAI/Advanced recognition providers.
- Added `Automatic` routing for fixed-language mode: GenAI → Basic → Android, with visible fallback.
- Kept automatic-language mode on Android 14+ language detection/switching.
- Added live engine selector and safe utterance-boundary hot-switching.
- Added per-segment recognition-engine provenance and session-level requested/last-engine/switch-count outputs.
- Added ML Kit speech model status/download handling.
- Updated presets/protocol settings and both ODK XLSForms with speech-engine input/output fields.
- Kept manual speaker attribution; no recognizer is treated as a diarisation provider.

## 0.1.0 — initial live meeting translation

- Fixed source-language → target-language live translation.
- Android 14+ automatic source-language detection/switching mode.
- Live partial/final feed, transcript controls, speaker tags, Commit lifecycle and ODK round-trip examples.
