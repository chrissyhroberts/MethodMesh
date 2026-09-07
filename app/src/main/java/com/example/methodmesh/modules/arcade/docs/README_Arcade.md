# Arcade v0.031

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

A touch-first 10×10 Snake game.

- Choose **Relaxed**, **Normal**, **Fast** or **Turbo**, then press **START**.
- Swipe the board or use visible arrow controls.
- Food is worth 10 points.
- Local best score and longest snake are retained.
- Collision logic allows entry into a tail cell that is vacating that step.
- Rapid input cannot create a hidden 180° reversal.
- Food placement uses the existing MethodMesh Chance capability.
- Normal play uses secure randomness; fixed seed is available for reproducible tests.

### Brick Breaker

A compact fixed-step breakout game.

- Three lives.
- Drag or tap to move the paddle.
- Clear all thirty bricks to win.
- Ball/paddle physics are deterministic state transitions; rendering does not determine collisions.
- Local best score and clear count are retained.

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
0.0.5
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
