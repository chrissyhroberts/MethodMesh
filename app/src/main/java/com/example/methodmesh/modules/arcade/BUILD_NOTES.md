# Build notes — Arcade v0.02

Arcade v0.02 was rebuilt from the earlier v0.01 capability and reviewed in several passes.

## Compiler-assisted validation performed

Using minimal API-shape stubs, the following compiled successfully with Kotlin 1.9:

1. **Core/runtime-independent layer**
   - `ArcadeFramework.kt`
   - `ArcadeEngine.kt`
   - `ArcadeRecords.kt`

2. **Compose/API-shape layer**
   - `ArcadeCapabilityScreen.kt`
   - `ArcadeChrome.kt`
   - `ArcadeSnakeScreen.kt`
   - `ArcadePongScreen.kt`
   - `ArcadeUxCatalog.kt`

3. **Method contract**
   - `ArcadeMethod.kt`

4. **Module/settings API shape**
   - `ArcadeModule.kt`

These are not substitutes for the real Android build, but they catch Kotlin syntax, visibility, function-signature and many Compose API-shape regressions.

The review itself caught and corrected:
- a cross-file `private` visibility error after UI modularisation;
- a missing `Alignment` import after splitting shared chrome;
- the earlier `Modifier.weight` pattern that had already caused MethodMesh compatibility problems elsewhere;
- Pong Restart initially reverting a 2-player match to CPU mode;
- setup-state Pong accepting hidden court input before START MATCH;
- modal result/help layers not explicitly consuming background taps.

## Static reviewer pass

The final source was also checked for:

- no GameDeck internal imports;
- no runtime capability-ID special cases;
- public Chance-method use only;
- no `Modifier.weight`;
- Snake queued-direction and tail-vacate invariants;
- Pong fixed drag ownership and serve pause;
- durable record deduplication;
- explicit MENU / How to Play / terminal actions;
- stable method ID and structured outputs;
- delimiter balance across all Kotlin files.

## Authoritative MethodMesh validation still required

Run in the real project:

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:assembleDebug
```

Then smoke-test both games on a short/narrow phone viewport.


## v0.03 expansion validation

The v0.03 changes were reviewed statically in this environment after the earlier v0.02 compiler-assisted cycle.

New checks covered:

- all four Snake speed presets and the Relaxed 235 ms default;
- speed persistence in Snake state and typed capability settings;
- Brick Breaker engine/screen/records/catalogue wiring;
- Lane Dodge engine/screen/records/catalogue wiring;
- public Chance use for Lane Dodge hazard generation;
- no cross-import from GameDeck;
- no capability-specific runtime branches;
- Kotlin delimiter balance across all Arcade source files;
- XLSForm game/speed choices and formula-error scan.

A full real-project Gradle compile is still authoritative.


## v0.033 focused review

Static checks cover:

- 18×30 Snake rules/render geometry and Chance-backed food selection across 540 cells;
- granular `snake_speed_cps` state/settings with old-preset compatibility;
- no on-screen Snake direction buttons;
- position + paddle-motion angular return model for Pong and Wall Break;
- predictive Pong CPU intercept using reflected side-wall trajectory;
- shared Wall Break rule/render brick geometry;
- circle-vs-rectangle brick collision;
- five level configurations including two-hit bricks;
- no GameDeck imports and no runtime capability special cases.

The real MethodMesh Gradle build and on-device play test remain authoritative.


## v0.034 ODK roundtrip checks

Static checks confirm:

- no external completion call remains in the startup effect;
- external execution keeps `capturedResult` null while play is in progress;
- completion is gated to one return per finished session;
- the final session is persisted before `arcade_player_stats_json` is built;
- final output includes score, player stats, play data and final state;
- example XLSForm uses play semantics and declares the returned fields.

A real MethodMesh Gradle build plus ODK Collect roundtrip remains authoritative.
