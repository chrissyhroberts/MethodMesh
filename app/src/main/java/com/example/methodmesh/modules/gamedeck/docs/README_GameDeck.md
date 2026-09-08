# GameDeck v0.058

GameDeck is MethodMesh's lightweight tabletop, puzzle and GameLab capability. It uses the normal MethodMesh module/method/screen discovery model: the runtime does not know what GameDeck is.

## Presentation invariants

- Active games use the generic `CapabilityHostPresentation.Immersive` host contract.
- Gameplay surfaces do not scroll. The launcher may scroll as the shelf grows.
- Two humans sharing one device own opposite ends of the table. Player 2's player-facing rail is rotated 180 degrees; Player 1's is upright.
- CPU opponents use the far rail but remain upright, because nobody is physically sitting opposite the device.
- The board remains stable unless a game's mechanics genuinely require rotation.
- Game-management actions stay behind the small tabletop menu rather than consuming board space.
- Winner/result state is explicit and overlaid rather than appended below the board.

## Playable games

### Original shelf

- Connect Four — 2P tabletop
- Snakes & Ladders — 2P, Chance-backed D6, manual token movement
- Mancala / Kalah — 1P CPU or 2P
- Minesweeper / GameLab — procedural validated boards

### v0.050 expansion

- Tic Tac Toe — 1P CPU or 2P
- Reversi — 1P CPU or 2P
- Nim — 1P CPU or 2P
- Memory Pairs — 2P
- Lights Out — solo puzzle
- 15 Puzzle — solvable shuffled solo puzzle
- Shut the Box — Chance-backed dice puzzle
- Codebreaker — four-symbol deduction puzzle

## CPU framework

`GameDeckAgents.kt` introduces named CPU profiles and a shared game-agent boundary. The first adapters cover Tic Tac Toe, Reversi and Nim. Agents choose actions; they do not own game state. The rules engine remains authoritative.

This is deliberately the same separation required for future remote/P2P play: human input, CPU input and peer input should all become ordered actions applied by the same deterministic rules layer.

## CPU Arena / simulator

`GameDeckSimulator.kt` runs headless matches using the same rules functions used by the visible games. The launcher exposes **CPU Arena**, currently supporting Tic Tac Toe, Nim and Reversi. A 100-match series reports P1/P2 wins, draws and mean move count.

This is the first executable slice of GameLab's broader simulation model:

1. generate or select a state/ruleset;
2. assign agents;
3. simulate;
4. measure outcomes and decision structure;
5. reject/accept generated scenarios or compare rule variants.

## Player record

`GameDeckStatsStore` provides a small local-first record using Android `SharedPreferences`. Finishing games records games/wins/losses/draws and per-game play counts. It also accumulates coarse cross-game play exposure across domains such as planning, tactics, spatial reasoning, deduction, memory and resource management. These counters are exposure, not calibrated competence estimates.

This is intentionally evidence, not a hidden difficulty score. The roadmap is to add uncertainty/confidence, difficulty-adjusted evidence, high scores, achievements and explicit player profiles.

## Randomness

GameDeck does not duplicate MethodMesh's Chance engine. Dice and seeded/shuffled setup call the public Chance method boundary (`As100DiceSimulationMethod`). Secure runtime randomness and fixed-seed reproducibility remain distinct concepts.

## External snapshot contract

Method: `gamedeck.snapshot`

Native dashboard/preset use remains live until the user explicitly finishes. External/ODK use returns a structured public snapshot and audit metadata. Perfect-information games can expose their full state; active hidden-information games expose only public state and withhold fixed seeds until the terminal/reveal boundary. See `example_odk_GameDeck.xlsx`.

## Architecture rule

> Games own mechanics. GameDeck owns identity, persistence, input, animation, randomness, records and presentation.

And the MethodMesh golden rule remains intact:

> Capabilities may describe how they want to be hosted; the host must not know why.


## Hidden-information snapshots

GameDeck keeps complete runtime state inside the capability, but snapshot/audit output is public-state only while a hidden-information game is active. This currently applies to Codebreaker, Minesweeper and Memory Pairs. For fixed-seed hidden games the audit records a SHA-256 commitment while withholding the seed; terminal snapshots may reveal the seed and private state for replay verification.

The commitment proves consistency with a later-revealed seed. It is not a secrecy guarantee for a weak or guessable seed.


## UX model

The shelf is grouped into **Tabletop**, **Puzzles** and **Game Lab** so a player can choose by intent rather than scan a long list. Every game has an on-demand **How to play** card generated from the same catalogue metadata used by the launcher.

During play:

- active rails say whose turn it is and, where useful, what to do next;
- CPU games use **YOU** and **CPU** rather than making the user infer seat numbers;
- the central board stays fixed while the far human rail rotates for across-the-table play;
- the visible **MENU** contains help, sound, restart, all games and MethodMesh completion;
- end-state overlays offer **Again**, **Games** and **Done**;
- system pages may scroll, but active game boards do not.

Minesweeper deliberately provides a visible Flag/Reveal mode in addition to long-press. This reflects the broader GameDeck rule that a hidden gesture may be a shortcut, but should not be the only discoverable path to a core action.


### Chess

Local two-player or lightweight-CPU chess.

- Legal-move filtering and check/checkmate.
- Castling and en passant.
- Automatic queen promotion.
- Stalemate and 50-move draw handling.
- No heavyweight external engine.

Current limitation: threefold-repetition and insufficient-material draw adjudication are not yet implemented.

### Go 9×9

Compact Go for local two-player or lightweight CPU play.

- Captures and liberties.
- Suicide prevention.
- Simple ko.
- Pass / two-pass termination.
- Area scoring with 6.5 komi.

Current limitation: scoring uses the board as played; there is no separate dead-stone adjudication phase or positional-superko history yet.

### Dots & Boxes

Two-player pass-and-play on a 5×5 dot lattice.

- Tap an unclaimed edge.
- Completing a box claims it and keeps the turn.
- Otherwise the turn passes.
- The far player rail retains the 180-degree shared-table orientation.

### Sudoku

Procedurally generated 9×9 Sudoku with Easy, Medium, Hard and Expert modes.

- Every accepted puzzle is checked for exactly one solution.
- Given cells are locked.
- Duplicate row/column/box entries are highlighted.
- The solution is private while play is active.

### Takuzu

Procedurally generated 6×6 binary logic puzzle.

- Equal 0/1 balance per row and column.
- No runs of three identical values.
- No duplicate completed rows or columns.
- Every accepted puzzle is solver-checked for uniqueness.

### 2048

Solo sliding-number puzzle.

- Swipe or use visible arrow controls.
- Equal tiles merge once per move.
- A new 2, or occasional 4, is generated after each successful move using the public Chance boundary.
- The run ends when 2048 is reached or no legal moves remain.



## ODK / Kobo interactive roundtrip

GameDeck external invocation is a play request, not a snapshot request:

```text
ODK/Kobo
  → EXECUTE_METHOD(game=chess)
  → MethodMesh opens Chess
  → player(s) play
  → terminal outcome
  → session/player record updated
  → final result returned to ODK
```

The same lifecycle applies to every playable GameDeck game.

Returned fields include:

```text
gamedeck_result
gamedeck_winner
gamedeck_move_count
gamedeck_score
gamedeck_player_stats_json
gamedeck_move_data_json
gamedeck_state_json
gamedeck_audit_json
```

`gamedeck_move_data_json` is game-aware. Chess and Go have explicit chronological move logs. Codebreaker returns its guess history. Engines that predate the explicit event-stream contract return their terminal public state plus move count until they are migrated to first-class event logs.

This preserves the important rule that ODK receives the result of the game that was actually played, never the state that existed when the interface opened.
