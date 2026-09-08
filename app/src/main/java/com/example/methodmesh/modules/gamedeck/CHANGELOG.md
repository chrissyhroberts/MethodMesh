# Changelog

## v0.050

- Expanded from four games to twelve playable games/puzzles.
- Added Tic Tac Toe, Reversi, Nim, Memory Pairs, Lights Out, 15 Puzzle, Shut the Box and Codebreaker.
- Added game-agent profiles and first CPU adapters.
- Added CPU Arena for headless agent-vs-agent simulation.
- Added local player record with per-game counts and coarse cross-game skill evidence.
- Launcher now scrolls; active games remain fixed non-scrolling tabletop surfaces.
- New two-human games follow opposite-end player ownership and far-player rotation.
- Extra-game procedural setup uses the public MethodMesh Chance method boundary.
- Updated XLSForm game choices and version.

## v0.046

- Rebuilt active play around a measured fixed tabletop surface.
- Moved game management to an overlay menu.
- Strengthened opposite-end two-human presentation.

## v0.051
- Fixed Compose compiler error caused by reading `LocalContext.current` inside the non-composable `remember {}` calculation lambda.
- Fixed `BoxWithConstraintsScope.maxHeight` implicit-receiver conflict in `SoloFrame` by capturing the available height before nested layout receivers.

## v0.052
- Reviewer-remediation release.
- Fixed invalid 15 Puzzle generation: one blank, tiles 1–15, legal-move scrambling, solvability guaranteed.
- Memory mismatches now remain visibly face-up during a resolve phase before concealment/turn transfer.
- Reversi legal-move decoding now parses state once per operation.
- Added capability-local session/seat/public-private state contracts.
- Codebreaker secret is redacted from exported snapshot/audit state.
- CPU agents are now seat-aware and CPU profiles affect decisions.
- CPU Arena alternates seats, varies deterministic decision seeds, compares distinct agent profiles, and reports seat bias.
- Simulator work now runs off the Compose UI thread.
- Player records use durable session IDs and do not treat Player 1 as the account holder in shared-table games.
- Renamed provisional "skill evidence" to play/skill exposure; no competence inference is claimed.

## v0.053
- Fixed `ExtraGameScreen` parameter mismatch introduced during the v0.052 CPU/session refactor.
- Fixed Kotlin compatibility for bounded recorded-session persistence by converting the set to a list before `takeLast`.

## v0.054
- Multi-pass reviewer remediation.
- Session seats now drive personal/shared-table outcome semantics.
- Hidden-information redaction expanded to Minesweeper and Memory as well as Codebreaker.
- Fixed-seed hidden games use public SHA-256 commitment and withhold the seed until terminal state.
- Reversi blocked-turn resolution added; simulator failures are no longer counted as draws.
- CPU Arena reports completed/non-result runs separately and computes mean moves from completed matches.
- Nim Casual/Standard/Sharp policies are now distinct.
- Ordered session deduplication replaces reliance on unordered SharedPreferences StringSet chronology.
- Minesweeper preserves pre-generation flags, flagged cells cannot be revealed, and mine hits count as solo losses.
- Lights Out and 15 Puzzle generators reject accidental already-solved starts.
- Player Record terminology consistently uses play exposure rather than skill evidence.


## v0.055

### UX stabilization

- Rebuilt the launcher into compact **Tabletop**, **Puzzles** and **Game Lab** shelves rather than one long undifferentiated list.
- Added one capability-local game catalogue that drives launcher names, badges and on-demand **How to play** instructions.
- Replaced the cryptic three-dot tabletop control with a visible **MENU** and a modal menu containing How to play, sound, restart, all games and done.
- Added modal scrims so help, menus and end-of-game panels cannot accidentally pass input through to the board.
- End-of-game panels now provide clear **Again**, **Games** and **Done** actions.
- CPU games label the local player as **YOU**, explicitly show **CPU THINKING**, and display the current mode as `MODE: CPU` / `MODE: 2P`.
- Opponent mode changes are locked after the first move so a mistaken tap cannot silently destroy an in-progress game.
- Rebuilt Mancala into a compact traditional two-row board with side stores, reducing vertical crowding substantially.
- Minesweeper now has explicit **Reveal** and **Flag** modes, visible safe-cell/flag counts, selected presets and an adaptive short-screen layout; long-press remains available as a shortcut.
- Reversi legal moves are more visible and the active rail tells the user to tap a dot.
- 15 Puzzle visually distinguishes the tiles that can actually move.
- Memory gives stronger face-up/matched states and explicit turn/reveal messaging.
- Shut the Box now shows physical number tiles, dice, selected total and required total instead of relying on arithmetic inference.
- Codebreaker retains recent guesses with Exact/Near feedback and reveals the hidden code in the terminal result.
- Player Record is reorganised into personal results, games played, personal bests, play exposure and achievements.
- CPU Arena is now a guided three-step workflow with selected-state controls, agent descriptions, percentages and explicit non-result warnings.

### Reviewer hardening retained

- Session/seat semantics remain authoritative for personal versus shared-table results.
- Hidden-information snapshots remain redacted while live.
- Fixed-seed hidden games expose a version-bound SHA-256 commitment and withhold the seed until terminal state.
- Added commitment verification helper.
- Reversi blocked turns, simulator invalid/abort accounting, ordered stats dedupe, puzzle solvability and Minesweeper flag invariants remain enforced.

Method version: `0.0.7`.


## v0.056

- Added **Dots & Boxes** as a two-human shared-table game.
- Dots & Boxes uses a 5×5 dot lattice / 4×4 box field, large logical edge tap targets, retained turns after completing boxes and shared-table result semantics.
- Added **2048** as a solo puzzle.
- 2048 supports both swipe input and visible arrow controls, uses the public Chance boundary for 2/4 tile spawning, records score, and terminates on 2048 or no legal moves.
- Added both games to the centralized launcher/help catalogue and typed capability game choices.
- Added session semantics and play-exposure tags for both new games.
- Rules version bumped to `0.0.7`; method version bumped to `0.0.8`.


## v0.057 — procedural puzzle pack

- Added **Sudoku** with Easy / Medium / Hard / Expert generation.
- Sudoku generation starts from a valid randomized solution, removes clues and retains a removal only when the built-in solver still finds exactly one solution.
- Sudoku highlights row/column/box conflicts without revealing the private solution.
- Added **Takuzu** (6×6 binary puzzle) with the same generate → solve → uniqueness-check pipeline.
- Takuzu enforces balanced rows/columns, no triples and unique completed rows/columns.
- Both puzzle solutions are redacted from active public snapshots and protected by the existing hidden-information seed commitment boundary.
- Added difficulty cycling before the first move.
- Added puzzle-specific play-exposure tags and solo session semantics.
- Rules version bumped to `0.0.8`; method version bumped to `0.0.9`.


## v0.058 — board-game expansion

- Added **Chess** with legal move filtering, check/checkmate, castling, en passant, automatic queen promotion, stalemate and 50-move draw handling.
- Chess supports local 2-player or a lightweight CPU opponent. The current interactive CPU uses a compact material/check/centre heuristic rather than a heavyweight chess engine.
- Added **Go 9×9** with captures, liberties, suicide prevention, simple ko, pass, two-pass termination and area scoring with 6.5 komi.
- Go supports local 2-player or a lightweight heuristic CPU.
- Added dedicated board-game screens while reusing the existing shared-table rails and CPU/2P mode boundary.
- Added Chess and Go play-exposure tags and human-vs-CPU/shared-table session semantics.
- Rules version bumped to `0.0.9`; method version bumped to `0.0.10`.


## v0.059 — compile fix

- Replaced four invalid `GameDeckSound.turn(...)` calls in Chess/Go with the existing `GameDeckSound.move(...)` sound boundary.
- Fixed `puzzlePublic(...)`: `JSONObject.remove(...)` returns the removed value and therefore cannot be chained into `.put(...)`.
- Method version bumped to `0.0.11`.
