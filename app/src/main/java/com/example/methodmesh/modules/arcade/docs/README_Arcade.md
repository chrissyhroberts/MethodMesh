# Arcade v0.035

Arcade is MethodMesh's lightweight real-time game capability.

It complements GameDeck rather than living inside it:

```text
PLAY
├── GameDeck   turn-based tabletop / puzzles / GameLab
└── Arcade     real-time touch games
```

Both use the generic MethodMesh capability runtime. The host UI does not know what an arcade game is.

## Current games

### Snake Sprint

A touch-first 18×30 Snake game that uses the portrait screen much more fully.

- Speed is a granular 3–16 cells/second slider and can be changed during play.
- Swipe the board to turn; there is no on-screen D-pad.
- Food is worth 10 points.
- Local best score and longest snake are retained.
- Collision logic allows entry into a tail cell that is vacating that step.
- Rapid input cannot create a hidden 180° reversal.
- Food placement uses the existing MethodMesh Chance capability.
- Normal play uses secure randomness; fixed seed is available for reproducible tests.

### Wall Break

A skill-based fixed-step Breakout-style game.

- Drag or tap to move the paddle.
- The outgoing ball angle depends on **where** it hits the paddle and **how the paddle is moving** at impact.
- Circle-vs-rectangle brick collision distinguishes side/top/bottom/corner contacts rather than forcing a rail-like Y reversal.
- Five wall layouts progress from a simple wall through checker, fortress, chevron and crown patterns; later layouts include two-hit bricks.
- Clearing a level grants a transition bonus and an extra life up to a cap of five.
- Local best score and final clear count are retained.

### Lane Dodge

A five-lane real-time avoidance game.

- Tap or drag horizontally to change lane.
- Falling blocks accelerate gradually.
- Passing a block scores one point.
- A collision ends the run.
- Hazard lanes use the existing MethodMesh Chance boundary, so fixed-seed runs are reproducible.

### Pong Table

Portrait tabletop Pong.

- First to seven.
- Choose **CPU** or **2 PLAYERS** before the match.
- Drag or tap your half of the court to move.
- In CPU mode the near player is labelled **YOU**.
- In two-player mode the far player rail rotates 180° for across-the-table use.
- Paddle ownership is fixed when a drag begins, so crossing the centre cannot switch player control.
- The ball pauses briefly after each point.
- Paddle impact position and paddle motion determine the return angle.
- CPU mode predicts the ball's reflected intercept position rather than simply following its current X coordinate.

## Shared UX

Both games have a visible **MENU** with:

- How to Play
- Sound
- Restart
- All Games
- Done, when the MethodMesh lifecycle exposes completion

Opening MENU or How to Play pauses the real-time simulation.

Game-over panels provide:

- Again
- Games
- Done

## Records

Arcade currently keeps a small local record on the device:

- total games;
- Snake best score;
- Snake longest length;
- Pong CPU wins/losses;
- shared two-player Pong matches.

This is intentionally capability-local for now. It should eventually migrate to a shared game-system player/record service rather than GameDeck and Arcade importing one another.

## Real-time architecture

`ArcadeFramework.kt` keeps rendering separate from simulation state.

The key rule is:

> Rendering cadence is not authoritative game state.

Pong advances in 16 ms fixed simulation steps. Snake uses 120 ms grid steps. UI delays may slow apparent playback, but physics/rules do not receive arbitrary frame-duration changes.

This architecture is intended to support later:

- replay/event streams;
- CPU controllers;
- deterministic simulation;
- P2P input adapters;
- more real-time games.

## Randomness

Arcade does not implement an independent RNG.

Snake food placement calls the public MethodMesh Chance method boundary:

```text
As100DiceSimulationMethod
```

This preserves the same distinction used elsewhere in MethodMesh:

- `secure_random` for ordinary play;
- `fixed_seed` for reproducibility.

## MethodMesh contract

Method:

```text
arcade.snapshot
```

Method version:

```text
0.0.7
```

Structured outputs include:

- game;
- full current state JSON;
- score;
- simulation tick;
- finished flag;
- winner;
- session ID;
- RNG mode;
- human-readable result.

Native dashboard/preset use remains live until the user explicitly chooses Done. External/ODK execution may return a snapshot immediately.

## Runtime invariant

Arcade declares:

```kotlin
CapabilityHostPresentation.Immersive
```

There must be no shared-runtime branch such as:

```kotlin
if (capabilityId == "arcade.snapshot") { ... }
```

The rule remains:

> Capabilities may describe how they want to be hosted; the host must not know why.


## v0.031 real-time timing correction

Continuous pointer updates no longer own the simulation clock. Pong and Brick Breaker therefore continue to simulate while a paddle is being dragged, and Lane Dodge continues while the player changes lane.

All real-time games now have explicit progression:

- Snake accelerates as food is collected.
- Pong accelerates through longer rallies.
- Brick Breaker accelerates as the wall is cleared.
- Lane Dodge advances through increasingly fast/dense levels.


## ODK / Kobo interactive roundtrip

The external game contract is interactive:

```text
ODK/Kobo
  → EXECUTE_METHOD(game=...)
  → MethodMesh opens that game
  → user plays
  → game reaches terminal state
  → MethodMesh records the session
  → final result is returned to the form
```

Opening the capability is never itself a result.

Returned fields include:

```text
arcade_result
arcade_score
arcade_finished
arcade_winner
arcade_player_stats_json
arcade_play_data_json
arcade_state_json
```

`arcade_player_stats_json` is the local player-record snapshot after the completed game.
`arcade_play_data_json` contains compact game-specific final metrics rather than pretending that every real-time frame is a meaningful move.


## ODK showcase configuration

The example XLSForm deliberately demonstrates pre-game configuration before handing control to the native Arcade UI.

Supported external inputs now include:

```text
game
snake_speed              legacy compatibility band
snake_speed_cps          3–16 cells/second; authoritative when supplied
pong_cpu_difficulty      casual | standard | sharp
breakout_start_level     1–5
dodge_difficulty         easy | normal | hard
rng_mode
seed
```

`snake_speed` remains in the XLSForm as the same `select_one arcade_snake_speeds` field used by earlier revisions. This preserves the existing ODK Central submission structure while allowing the newer granular `snake_speed_cps` setting to control actual Snake speed.

Difficulty settings change the requested starting conditions; the live game still owns play and returns only after its terminal state.
