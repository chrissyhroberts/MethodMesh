# Arcade implementation notes — v0.02

## Capability boundary

Arcade is a separate MethodMesh capability:

```text
arcade/
```

It is discovered through its normal `MethodMeshModule`. No MethodMesh runtime code should branch on `arcade`, `Arcade`, Snake, Pong, or any game type.

Arcade requests the generic:

```kotlin
CapabilityHostPresentation.Immersive
```

The host knows only the presentation contract.

Arcade also does **not** import GameDeck persistence or rules internals. GameDeck and Arcade should eventually share player identity / record / replay services through a public game-system boundary rather than coupling capability folders.

## Real-time rule

Rendering cadence is not authoritative game state.

Arcade advances gameplay through explicit fixed steps:

```text
input → state transition → fixed step → new state → render
```

A delayed UI frame can make a run appear slower, but it must not silently change the magnitude of a physics step.

Current fixed steps:

- Pong: 16 ms simulation step.
- Brick Breaker: 16 ms simulation step.
- Lane Dodge: 16 ms simulation step.
- Snake: 235 / 175 / 130 / 95 ms for Relaxed / Normal / Fast / Turbo.

## Modal pause rule

Opening:

- MENU
- How to Play

pauses real-time state advancement. Closing the modal resumes from the same state.

A modal must also intercept touches so paddle/snake input cannot pass through it.

## Interaction rule

A hidden gesture may be a shortcut, but not the only route to a core action.

Snake therefore supports:

- swipe;
- visible arrow controls.

Pong supports:

- drag;
- tap positioning.

## Snake invariants

- `dir` is the committed direction.
- `queued_dir` is applied on the next movement step.
- Requested direction is validated against committed direction, preventing two rapid intermediate turns from producing a hidden 180° reversal.
- The current tail cell is not treated as occupied when that tail will vacate on a non-growth step.
- Food placement uses MethodMesh Chance through `As100DiceSimulationMethod`.
- `secure_random` is normal play.
- `fixed_seed` is reproducible testing/demo mode.
- Full-board occupancy terminates as `cleared`.

## Pong invariants

- The local/near paddle is P1.
- The far paddle is P2 or CPU.
- In two-human mode the far rail rotates 180°.
- In CPU mode the far rail remains upright.
- A drag chooses its owning paddle at drag start; crossing the centre line cannot transfer control.
- Match mode may be changed only before START MATCH.
- First to seven wins.
- A short centre serve pause follows each point.

## Local records

`ArcadeRecordStore` is intentionally small:

- total finished games;
- Snake best score;
- Snake longest length;
- Pong CPU wins/losses;
- shared two-player Pong matches.

This is not the final cross-capability player service. When that service exists, Arcade should migrate through its public interface.

## Validation

The authoritative project checks remain:

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:assembleDebug
```


## Source layout

The real-time UI is deliberately split so Arcade does not repeat GameDeck's early monolithic-screen problem:

```text
ArcadeCapabilityScreen.kt   lifecycle/orchestration
ArcadeChrome.kt             launcher/menu/help/results
ArcadeSnakeScreen.kt        Snake presentation/input
ArcadePongScreen.kt         Pong presentation/input
ArcadeEngine.kt             authoritative state transitions
ArcadeFramework.kt          generic fixed-step contracts
ArcadeRecords.kt            provisional local records
ArcadeUxCatalog.kt          launcher/help copy
```


## v0.03 additions

### Snake speed

Snake speed is an explicit game setting rather than an incidental timing constant:

```text
relaxed  235 ms
normal   175 ms
fast     130 ms
turbo     95 ms
```

The selected speed is stored in Snake state and exposed as the typed `snake_speed` capability setting.

### Brick Breaker

Brick Breaker keeps the same fixed-step rule as Pong. The state owns:

- ball position/velocity;
- paddle position;
- 30 brick states;
- score;
- lives;
- serve pause;
- terminal outcome.

### Lane Dodge

Lane Dodge uses five discrete lanes so touch ownership remains obvious on narrow phones. The state owns:

- player lane;
- falling hazards;
- spawn index;
- score;
- tick;
- terminal outcome.

Hazard lane generation goes through `As100DiceSimulationMethod`; no independent RNG is introduced.


## v0.031 persistent simulation clock

The original v0.03 real-time screens used:

```kotlin
LaunchedEffect(stateJson, paused) {
    delay(STEP_MS)
    onStep()
}
```

That is incorrect for continuous-input games because dragging a paddle changes
`stateJson`, cancelling/restarting the delay before the physics step can fire.

v0.031 uses:

```kotlin
val latestOnStep by rememberUpdatedState(onStep)

LaunchedEffect(started, finished, paused) {
    while (started && !finished && !paused) {
        delay(STEP_MS)
        latestOnStep()
    }
}
```

This separates the simulation clock from input-state mutations while still calling
the latest authoritative state-transition closure.

### Lane Dodge collision

UI geometry and rules geometry now match:

```text
player top     0.880
player bottom  0.925
hazard height  0.055
```

Collision requires actual vertical overlap plus the same lane.

### Progression

- Snake: selected base speed, then 12 ms faster every three foods, floor 70 ms.
- Pong: rally contact raises vertical/horizontal pace, bounded to playable maxima.
- Brick Breaker: paddle and brick contacts progressively increase ball speed.
- Lane Dodge: level increases roughly every 500 fixed ticks and affects both fall speed and spawn interval.
