# Surveying v0.1.0 build / validation report

## Implemented

- 11 atomic surveying calculations.
- 2 persistent field-book dashboards.
- Shared pure-Kotlin calculation engine for atomic and dashboard paths.
- Capability-owned local traverse/levelling repository.
- Compact core outputs plus metadata JSON.
- Native-preset runtime-field hiding in atomic screens.
- Persistent dashboard semantics for native dashboard/native preset use.
- ODK/XLSForm examples under this module's `docs/` folder.

## Pure Kotlin validation completed

`SurveyCalculations.kt` compiles with `kotlinc`.

The smoke harness verifies:

- local-grid inverse and forward coordinate round trip;
- signed right-offset calculation and inverse chainage/offset;
- polygon area/centroid;
- Bowditch traverse adjustment closes on supplied control;
- levelling arithmetic identity;
- GPS repeated-fix averaging;
- bearing/bearing intersection.

Observed smoke result:

```text
SurveyCalculations smoke checks passed
```

## Dashboard contract implemented

Both field books:

- keep the latest real `ExecutionResult` in capability-owned state;
- keep `capturedResult = null` while native dashboard/native preset presentation should remain live;
- use explicit **Use this snapshot** / **Finish** confirmation;
- do not commit a graph/history result on each edit/refresh;
- use the same calculation methods as their atomic equivalents;
- retain `intent_test` as an interactive troubleshooting surface;
- allow genuine external/ODK execution to follow the normal single-shot result path.

## Not validated in this environment

A full Android checkout/build was not available in the local execution container. Before Production run at minimum:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.example.methodmesh.modules.surveying.*'
./gradlew :app:assembleDebug
```

Then verify on a device/emulator:

1. each atomic native run;
2. preset creation and runtime-field hiding;
3. preset result survival over orientation changes;
4. every XLSForm example in ODK Collect;
5. traverse field book in dashboard, native preset, `intent_test`, ODK/external and multi-step protocol contexts;
6. levelling field book in the same contexts;
7. local persistence, undo and new-job behaviour;
8. failure states for malformed legs/coordinates/level observations;
9. share/export projects only useful core values unless full details are explicitly requested.

Status remains **Development** until those integration checks pass.
