# GameDeck v0.057 — puzzle smoke test

## Sudoku

- Opening Sudoku produces a valid puzzle immediately.
- Difficulty cycles Easy → Medium → Hard → Expert only before the first entry.
- Changing difficulty generates a different puzzle.
- Given cells cannot be edited.
- Blank cells can be selected and filled from the visible keypad.
- Clear/backspace removes a non-given entry.
- Duplicate row/column/box entries highlight as conflicts.
- Solving the grid produces `winner=cleared`.
- Active exported/public state does not contain the `solution` array.
- Fixed-seed generation is reproducible.

## Takuzu

- Opening Takuzu produces a 6×6 puzzle.
- Difficulty cycles only before the first entry.
- Given cells cannot be edited.
- Blank cells cycle empty → 0 → 1 → empty.
- Three identical adjacent values are highlighted.
- Too many 0s/1s in a row or column are highlighted.
- Duplicate completed rows/columns are highlighted.
- Solving the generated solution produces `winner=cleared`.
- Active exported/public state does not contain the `solution` array.
- Fixed-seed generation is reproducible.
