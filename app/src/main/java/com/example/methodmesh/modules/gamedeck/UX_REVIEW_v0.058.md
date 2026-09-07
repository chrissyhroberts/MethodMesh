# GameDeck v0.058 — board-game smoke test

## Chess

- Initial position has the expected pieces and White/Player 1 moves first.
- A selected piece shows only legal destinations.
- A move that exposes the player's own king is not offered.
- Pawn one-step/two-step opening moves work.
- Captures work.
- Castling works only with rights/path/check constraints satisfied.
- En passant works immediately after an eligible double pawn move.
- Promotion on the last rank becomes a queen.
- Check is visibly reported in the relevant player rail.
- Checkmate produces the correct winner.
- Stalemate produces draw.
- 50 halfmoves without pawn move/capture produces draw.
- MODE: CPU / MODE: 2P is available only before the first move.
- CPU replies without blocking the UI indefinitely.

## Go 9×9

- Black/Player 1 moves first.
- Stone placement occurs only on empty legal intersections.
- Captured zero-liberty groups disappear.
- Suicide moves are unavailable.
- Immediate simple-ko recapture is unavailable.
- PASS changes turn.
- Two consecutive passes end and score the game.
- Area score includes 6.5 komi for White.
- MODE: CPU / MODE: 2P is available only before the first move.
- CPU can place stones and can pass late in a settled game.
