# GameDeck v0.055 — UX review and acceptance checklist

This pass treats GameDeck as a small physical-digital game shelf rather than a collection of demo screens.

## Design questions used in review

For every screen:

- Can a new player identify the primary action in a few seconds?
- Is the current player obvious without reading the rules?
- Are legal and illegal actions visually distinguishable?
- Does a core action depend on an undisclosed gesture?
- If the CPU is acting, does the interface visibly say so?
- Can a user recover after entering the menu/help/result state?
- Does the end of a game answer “what happened?” and “what can I do now?”
- Is important information still visible on a short phone screen?
- Does a two-human tabletop still make sense to the person sitting at the far end?

## Manual smoke test

### Shelf
- Open GameDeck and locate a two-player game, a solo puzzle and CPU Arena without scrolling through a single undifferentiated list.
- Confirm every card title/badge matches its How-to-play card.
- Confirm MethodMesh exit remains reachable.

### Common game chrome
- Open MENU, tap outside it, and confirm it dismisses without moving the board.
- Toggle sound.
- Open How to play and dismiss it.
- Restart a game.
- Return to All Games.
- Finish/Done where the MethodMesh host exposes completion.

### Tabletop
- Connect Four: both tap-column and drag-counter interactions work; full columns reject input.
- Snakes & Ladders: roll, move and snake/ladder transitions are visibly separated.
- Mancala: CPU and 2P modes are obvious before play; after move 1 the mode cannot be accidentally changed.
- Tic Tac Toe / Reversi / Nim: CPU mode labels the local player YOU; CPU THINKING is visible; 2P restores Player 1 / Player 2 orientation.
- Reversi: legal dots are obvious and a blocked turn passes correctly.
- Memory: a mismatch remains visible long enough to perceive before the turn passes.
- Shut the Box: selected numbers visibly total toward the dice result.

### Puzzles
- Minesweeper: a first-time user can discover flagging without knowing about long-press; flag mode and reveal mode do what they say.
- Minesweeper Dense: board and essential controls fit on a short phone viewport.
- Lights Out: lit versus unlit cells are unmistakable.
- 15 Puzzle: only tiles adjacent to the blank look actionable.
- Codebreaker: previous Exact/Near clues remain visible; final result reveals the code.

### End states
- Win/loss/draw overlays block input to the board.
- CPU games say YOU WIN / CPU WINS rather than Player 1 / Player 2 where seat ownership is known.
- Again starts a new session.
- Games returns to the shelf.
- Done returns the current structured result when available.

### System pages
- Player Record distinguishes personal W/L/D from shared-table sessions.
- Play exposure is not described as skill or competence.
- CPU Arena clearly shows selection state for game and both agents.
- A 100-match Arena result reports non-results separately from draws.

## Accessibility / device follow-up

The current pass improves discoverability and state signalling but is not a substitute for device testing. Before calling the capability release-ready, test:

- smallest supported Android viewport;
- large-font / display-size settings;
- light and dark appearance;
- touch targets with one-handed use;
- two people physically seated at opposite ends of a phone/tablet.

No accessibility semantics audit has yet been performed for screen readers.
