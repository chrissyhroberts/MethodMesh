# MethodMesh v2.2.3

MethodMesh v2.2.3 promotes the live conversation translator to production and applies the MethodMesh visual identity refresh across the Android app shell.

## Highlights

### Conversation translator promoted

`conversation.translate` is now marked as a production capability.

- Supports any ML Kit language pair available on the device.
- Uses two independent speaker buttons, so either participant can speak multiple times in a row.
- Captures a turn-by-turn transcript with original speech text, translated text and direction.
- Can optionally speak each translated line aloud.
- Native sharing sends the conversation transcript as the main result.
- ODK/XLSForm calls return the transcript plus `methodmesh_full_json` for audit metadata.
- Shared ML Kit language packs are managed from **Settings**.

### Visual identity refresh

- Applies the MethodMesh paper/ink/green palette to the Android app.
- Adds the MethodMesh mark to the home dashboard and navigation drawer.
- Simplifies capability runner and result surfaces.
- Tightens typography and spacing for a cleaner, more distinctive interface.
- Resizes the adaptive launcher icon foreground so it fits better inside circular Android launcher masks.

## Validation

Built and checked with:

```text
./gradlew testDebugUnitTest --tests com.example.methodmesh.modules.conversationtranslate.ConversationTranslateOdkFormContractTest --tests com.example.methodmesh.transport.OutputFormatterTest
./gradlew :app:assembleDebug
```

Result:

```text
BUILD SUCCESSFUL
```
