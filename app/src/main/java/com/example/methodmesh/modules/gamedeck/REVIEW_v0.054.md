# GameDeck v0.054 reviewer remediation

This release is deliberately a stabilization pass rather than a catalogue expansion.

## Correctness changes

- Reversi now has an explicit blocked-turn resolver for imported/headless states.
- Simulator outcomes distinguish wins, draws, aborts, invalid runs and unsupported games.
- Simulator failures are not folded into draw counts.
- Nim Casual / Standard / Sharp now represent distinct policies.
- Lights Out and 15 Puzzle generation cannot return an already-completed puzzle.
- Minesweeper refuses reveal on a flag and preserves pre-first-click flags across generation.
- Minesweeper `mine` is recorded as a personal loss for a solo player.

## Session semantics

`GameDeckSession` is now used by live gameplay:
- seat ownership is constructed once through `GameDeckSessions`;
- player statistics use session seats to identify a sole local human;
- two-local-human games remain shared-table records rather than pretending Player 1 is the account holder;
- snapshot audit includes public session metadata.

## Hidden information

The snapshot boundary now covers:
- Codebreaker secret;
- Minesweeper mine locations and generation seed;
- Memory card identities that are neither matched nor currently face-up.

For fixed-seed hidden games:
1. the live seed is withheld while the game is active;
2. SHA-256 commitment is recorded in audit metadata;
3. on terminal state the seed/state may be revealed for replay verification.

This is capability-local. No MethodMesh runtime branch knows that GameDeck or any specific game exists.

## Remaining deliberate limitations

- Internal state is still JSON rather than typed game-state classes.
- GameDeckCapabilityScreen remains large; further splitting should happen only after this stabilization build compiles in the real app.
- Player-domain counters remain exposure, not calibrated competence.
- CPU Arena is an initial deterministic simulator, not yet the full GameLab measurement engine.


## Self-review round 2

A second pass found and corrected:
- hidden state could still leak through native request settings (`state_json` / `input_state_json`);
- solved 15 Puzzle state was not terminal-locked;
- ordered stats migration initially discarded most legacy dedupe IDs;
- CPU Arena could leave the UI permanently in `RUNNING` if simulation raised;
- interactive Codebreaker controls did not become visibly terminal and did not reveal the code at game end;
- agent/match exceptions were not yet classified as simulator invalid runs.
