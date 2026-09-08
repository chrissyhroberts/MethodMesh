MethodMesh v2.2.4

Highlights
- Polishes native preset result handling with configurable after-result actions.
- Adds dashboard search for capabilities and presets.
- Adds active schedules on the dashboard with pause/resume controls.
- Promotes and refines the conversation translator full-screen two-person layout.
- Adds missing-language handling for conversation translation with direct language-pack download shortcuts.
- Restores ML Kit language-pack downloads to the known-good RemoteModelManager contract.
- Adds language-pack diagnostics: elapsed time, callback log, installed pack list, network validation state, and Google Play Services visibility.
- Adds Google ML Kit attribution on translation/language-pack screens.

Validation
- Built successfully with ./gradlew :app:assembleDebug.

Known issue
- Native preset Home action still does not reliably return to the MethodMesh dashboard in all flows; continue from this next.
