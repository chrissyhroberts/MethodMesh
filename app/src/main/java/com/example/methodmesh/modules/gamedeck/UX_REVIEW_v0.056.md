# GameDeck v0.056 smoke checklist

## 2048

- Fresh board contains exactly two non-zero tiles.
- Swipe up/down/left/right works.
- Visible arrow controls perform the same moves.
- A line such as 2,2,2,2 becomes 4,4 rather than 8.
- An unsuccessful move does not spawn a tile or increment moves.
- A successful move spawns exactly one 2 or 4.
- Fixed-seed runs are reproducible.
- 2048 terminates as cleared.
- A full board with no equal neighbours terminates as stuck.

## Dots & Boxes

- All forty edges begin unclaimed.
- Entire logical edge cell is tappable, not just the thin rendered line.
- Claiming an edge normally passes the turn.
- Completing one box keeps the turn.
- One edge can complete two boxes and awards both.
- Claimed boxes show the owning player's colour/symbol.
- Final result occurs after forty edges.
- Shared-table result is not silently attributed to Player 1.
