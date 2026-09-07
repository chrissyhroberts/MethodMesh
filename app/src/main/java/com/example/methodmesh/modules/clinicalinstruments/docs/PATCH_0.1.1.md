# Clinical Instruments v0.1.1 launch-crash patch

## Symptom

The module compiled successfully, but opening the Clinical Instruments capability could crash immediately during Compose layout.

## Root cause

`ClinicalInstrumentsCapabilityScreen` wrapped its entire capability body in:

```kotlin
Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()))
```

MethodMesh already hosts capability screens inside vertically scrollable dashboard/external-workflow surfaces. An unconstrained vertically scrollable child inside an already vertically scrollable parent can be measured with an infinite vertical constraint and throw at runtime.

## Fix

The module no longer creates its own page-level vertical scroller:

```kotlin
Column(Modifier.fillMaxWidth())
```

Scrolling is delegated to the existing MethodMesh host. No capability behaviour, persistence, scoring, YAML definitions, ODK contract, or result fields changed.

## Review note

If a later sub-view needs independent scrolling (for example a long YAML editor or definition preview), constrain that sub-view to a finite height before adding `verticalScroll`, rather than scrolling the whole capability body.

## Validation required in main checkout

```bash
./gradlew :app:assembleDebug
```

Then open Clinical Instruments from the dashboard and test:

1. Library renders without crash.
2. Start qSOFA.
3. Navigate Back/Next.
4. Leave open, return to Active, resume.
5. Complete and view result.
6. Repeat from an ODK/external-app invocation.
