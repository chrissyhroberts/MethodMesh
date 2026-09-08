# Arcade v0.02 — UX and reviewer checklist

## Shared chrome

- Launcher clearly explains that Snake is solo and Pong supports CPU / two-player.
- MENU is visible without knowing a three-dot convention.
- Opening MENU pauses the game.
- Tapping outside MENU dismisses it.
- How to Play pauses the game.
- Sound can be toggled.
- Restart creates a clean run.
- All Games returns to the Arcade shelf.
- Terminal overlay blocks input to the underlying court/board.
- Again and Games are obvious.
- Done is shown only where the MethodMesh live lifecycle exposes it.

## Snake Sprint

- Game does not begin before START.
- Swipe and arrow buttons both turn the snake.
- A direct 180° reversal is rejected.
- Two rapid turns between movement steps cannot create an indirect 180° reversal.
- Moving into the current tail is legal when the snake is not growing.
- Food never spawns on the body.
- Secure-random play does not collapse to the same deterministic food sequence.
- Fixed-seed play is reproducible.
- Score, best score and current length are visible.
- Wall and self collision produce terminal state.
- Filling the board produces `cleared`.

## Pong Table

- Setup clearly offers CPU versus 2 Players before START MATCH.
- Game does not start before the explicit Start action.
- Mode cannot change mid-match.
- CPU mode labels the near player YOU.
- Two-human mode rotates the far player rail.
- Tap and drag both move a paddle.
- Starting a drag in one half owns that paddle for the whole gesture.
- Crossing the centre during a drag does not switch paddles.
- CPU ignores human input to the far paddle.
- Point scoring is correct at both ends.
- There is a short reset pause after a point.
- First to seven terminates.
- Result wording distinguishes YOU / CPU from Player 1 / Player 2.

## Records

- A finished session is recorded once.
- Snake best score only increases.
- Snake longest length only increases.
- CPU Pong updates human W/L.
- Two-player Pong increments shared-match count instead of pretending P1 is the account holder.

## Architecture

- No runtime branch knows about Arcade.
- No GameDeck internal class is imported.
- Chance is called through the public method boundary.
- Rendering/animation does not determine outcomes.
- Full Android validation remains `compileDebugKotlin` + `assembleDebug`.
