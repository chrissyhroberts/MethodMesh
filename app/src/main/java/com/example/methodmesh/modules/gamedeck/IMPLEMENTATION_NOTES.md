# GameDeck implementation notes — v0.050

## Generic runtime only

GameDeck requests `CapabilityHostPresentation.Immersive`. There must be no `gamedeck` ID/name branch in shared MethodMesh UI. The runtime knows only generic host presentation semantics.

## Shared tabletop contract

Two-human local play is approximately 180-degree rotationally symmetric in player-facing UI. Player rails, local controls and status belong to the player at that end of the device. The central board is stable.

The current original games use `TabletopFrame`; the v0.050 compact games use the equivalent `ExtraTabletop`. These should converge into one public capability-local component after the interaction designs settle.

## Game state

All compact new games use JSON state with common fields:

- `game`
- `turn`
- `winner`
- `moves`

Game-specific state is additional. UI is a projection of state; CPU agents and simulators call the same state-transition functions.

## Agent and simulator direction

The CPU boundary is intentionally action-oriented. Future adapters should expose legal actions and evaluation separately so RANDOM, GREEDY, HEURISTIC, BOUNDED, OPTIMAL and NOISY-EXPERT agents can share one harness.

`GameDeckSimulator` is deliberately headless and bounded. It must never use rendering or animation state to determine outcomes.

## Stats direction

Current stats are local-only `SharedPreferences`. This is an initial implementation rather than the final cross-capability player identity service. Arcade and future game capabilities should eventually use a public game-system method/repository contract rather than import GameDeck internals.

## Chance

Random outcome generation stays behind the existing Chance public method boundary. Presentation animation never determines outcomes.

## Validation

The capability still requires validation in the real MethodMesh Android project with:

```bash
./gradlew :app:compileDebugKotlin
```

and then `:app:assembleDebug` before release.


## v0.052 reviewer invariants

- A hidden-information game's internal state is not necessarily exportable state.
- A simulator agent must always be told which seat it controls.
- A CPU profile must alter policy; profile labels must not be decorative.
- A simulation series alternates seats and varies decision seeds so repeated matches are not duplicated trajectories.
- Two-human hot-seat results are shared-table records unless an explicit player identity/seat mapping exists.
- Game counts by cognitive domain are exposure, not calibrated skill estimates.
- Puzzle generators must guarantee their declared state invariants (15 Puzzle: exactly one blank, tiles 1–15 exactly once, reachable from solved state).


## v0.055 UX contract

GameDeck should be usable without reading repository documentation. The interface follows these capability-local rules:

1. **The active player is obvious.** The active rail is highlighted and carries the immediate action cue.
2. **The next action is named.** Buttons say what happens (`ROLL DICE`, `TRY CODE`, `FLAG`, `REVEAL`) rather than exposing internal state names.
3. **Legal actions look actionable.** Reversi dots, 15 Puzzle adjacency, selected Shut-the-Box tiles and Minesweeper modes expose the permitted interaction before the tap.
4. **CPU state is explicit.** CPU games distinguish `YOU`, `CPU`, `CPU THINKING` and the current opponent mode.
5. **Shared-table orientation is preserved.** The far human rail rotates 180 degrees; CPU rails stay upright; the central board remains stable.
6. **Help is available but not compulsory.** Every shelf item has a short How-to-play card reachable from the in-game menu; there is no blocking tutorial.
7. **End state has an obvious exit.** Result overlays offer Again, Games and, where the MethodMesh lifecycle permits it, Done.
8. **Hidden gestures have visible alternatives.** Minesweeper retains long-press-to-flag but also exposes an explicit Flag mode.
9. **Destructive configuration is constrained.** CPU/2P mode changes are disabled once play has started.
10. **System pages may scroll; active games do not.** Gameplay remains a stable physical-digital surface.

The launcher/help copy is centralized in `GameDeckUxCatalog.kt` so naming, objectives and instructions do not drift independently.

## Mancala presentation

The original vertically stacked six-row representation was too tall for a phone tabletop. v0.055 uses a conventional two-row board with side stores:

- far/CPU/P2 pits: `12 downTo 7`;
- near/P1 pits: `0..5`;
- far store: `13`;
- near store: `6`.

This is presentation only; the existing Kalah state-transition engine remains authoritative.


## v0.056 game additions

### Dots & Boxes

State uses:

- 20 horizontal edges;
- 20 vertical edges;
- 16 owned boxes;
- two scores;
- turn / winner / moves.

A completed box retains the player's turn. Two-local-human session semantics mean the result is recorded as shared-table play rather than silently assigning Player 1 to the device owner.

### 2048

State uses a 4×4 integer grid plus score/moves. A successful move:

1. compresses each line;
2. merges equal neighbours once;
3. writes the transformed grid;
4. spawns a new 2 or occasional 4 through the public Chance method boundary;
5. checks for 2048 or a no-move terminal state.

Swipe is supported, but visible arrows remain available so the primary action is not gesture-only.


## v0.057 procedural puzzle architecture

`GameDeckPuzzleEngine.kt` owns generation/verification for logic puzzles rather than embedding solver logic in Compose screens.

### Sudoku

Generation:

```text
randomized valid 9×9 solution
→ deterministic-random clue-removal order
→ remove candidate clue
→ count solutions (limit 2)
→ retain removal only when solution count == 1
→ stop near selected clue target
```

Difficulty targets are deliberately simple and inspectable:

```text
Easy    ~42 clues
Medium  ~36 clues
Hard    ~31 clues
Expert  ~28 clues
```

Actual clue count is retained in state because uniqueness can make a candidate clue non-removable.

### Takuzu

The 6×6 generator starts from a valid complete binary grid, applies symmetry/complement transformations, then removes clues with a bounded uniqueness solver.

Rules checked during solving:

- exactly three 0s and three 1s in each completed row/column;
- no run of three identical values;
- completed rows are unique;
- completed columns are unique.

### Hidden state

Both games keep `solution` in internal state but active public snapshots remove it. The existing hidden-game commitment mechanism therefore applies to fixed-seed puzzle generation as it already does to Codebreaker, Minesweeper and Memory.


## v0.058 board-game engine

`GameDeckBoardEngine.kt` keeps perfect-information board rules separate from Compose presentation.

### Chess

State includes:

- 64-square board;
- side to move;
- castling rights;
- en-passant target;
- halfmove clock;
- last move;
- winner / move count.

Legal moves are generated in two stages:

```text
pseudo-legal move
→ apply to copied position
→ reject if own king is attacked
```

Implemented special rules:

- castling through unattacked empty squares;
- en passant;
- promotion (currently automatic queen promotion);
- check/checkmate;
- stalemate;
- 50-move draw.

Threefold-repetition and insufficient-material adjudication are not yet implemented.

The CPU is intentionally compact:

- Casual: random legal move through the public Chance boundary.
- Standard: material + immediate tactical/centre evaluation.
- Sharp engine path: shallow reply-aware evaluation.

The interactive v0.058 screen currently uses Standard.

### Go 9×9

State includes:

- 81 intersections;
- side to move;
- simple-ko point;
- consecutive passes;
- captures;
- 6.5 komi;
- last move;
- terminal area scores.

A move:

```text
place stone
→ capture adjacent opponent groups with zero liberties
→ reject suicide
→ calculate simple-ko recapture point
→ pass turn
```

Two consecutive passes end the game. Scoring uses area scoring on the board as played:

```text
stones + exclusively surrounded empty territory
```

White receives 6.5 komi.

There is not yet a separate dead-stone adjudication phase or positional-superko history. The current implementation is deliberately compact for 9×9 casual play.

### CPU principle

Neither Chess nor Go introduces a heavyweight external engine. CPU choice remains a policy over the same legal action/state-transition boundary used by human play.
