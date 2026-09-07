# Build notes — GameDeck v0.055

## Validation performed during this stabilization cycle

The capability has been through multiple review passes rather than a single patch pass.

Compiler-assisted checks were run against minimal API-shape stubs for:

- pure game/session/agent/simulator Kotlin;
- local stats persistence;
- extra-game Compose surfaces;
- Player Record and CPU Arena surfaces;
- the main GameDeck capability screen and MethodMesh capability contracts.

Those checks caught real defects during development, including a missing 15 Puzzle coordinate and a missing `GameResultPanel` session argument, which were corrected before packaging.

After the final UX iteration, an additional static regression pass checked:

- all Kotlin delimiter balance;
- the known 15 Puzzle, Memory, Reversi, Minesweeper and stats regressions;
- CPU-mode function/signature consistency;
- hidden-state and hidden-seed request sanitation;
- version-bound RNG commitments;
- absence of the old “skill evidence” terminology;
- launcher/help catalogue wiring;
- result/session wiring.

Static regression checks: **47 / 47 passed**.

## Not performed here

A full Android/Compose Gradle build against the actual MethodMesh repository is still authoritative and must be run in the real project.

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:assembleDebug
```

The UX should also be exercised on at least one short/narrow phone viewport because GameDeck deliberately uses fixed, non-scrolling play surfaces.


## v0.056 expansion validation

The v0.056 additions were reviewed statically after the v0.055 compiler-assisted stabilization cycle.

New checks covered:

- 2048 engine initialization, move/merge/spawn/terminal wiring;
- public Chance use for 2048 tile spawning;
- swipe plus visible-arrow controls;
- Dots & Boxes 20 horizontal + 20 vertical edge model;
- retained turn after completing one or more boxes;
- terminal scoring after forty claimed edges;
- enlarged logical edge tap targets;
- session semantics for solo 2048 and shared-table Dots & Boxes;
- play-exposure tags;
- typed capability game choices;
- rules/method version increments;
- Kotlin delimiter balance across all GameDeck source files;
- XLSForm game choices and formula-error scan.

A full real-project Gradle compile is still authoritative.


## v0.058 board-game validation

Static source review for the board expansion checked:

- Chess initial-state registration and session wiring.
- Pseudo-legal → own-king-safe legal move filtering.
- Castling rights/path/attack checks.
- En-passant target/capture handling.
- Automatic queen promotion.
- Checkmate/stalemate and 50-move termination.
- CPU move selection constrained to legal actions.
- Go group/liberty capture handling.
- Suicide rejection.
- Simple-ko recapture point handling.
- Two-pass termination and area scoring with komi.
- CPU/2P shared-session semantics.
- Game catalogue / typed XLSForm choices.
- Kotlin delimiter balance across all capability source files.
- XLSForm formula-error scan.

Known deliberate limitations are documented rather than hidden:

- Chess: no threefold-repetition or insufficient-material adjudication yet.
- Go: simple ko rather than positional superko; no separate dead-stone adjudication phase.

A real MethodMesh Gradle build and on-device smoke test remain authoritative.
