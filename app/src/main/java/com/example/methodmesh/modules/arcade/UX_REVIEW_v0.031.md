# Arcade v0.031 smoke test

## Continuous input regression

### Pong
- Start a match.
- Hold a continuous drag for several seconds.
- Ball must continue moving while the paddle follows the finger.
- Rally count should rise after paddle returns.
- Long rallies should become visibly faster.
- Scoring a point resets the rally pace.

### Brick Breaker
- Hold a continuous paddle drag.
- Ball must continue moving throughout the drag.
- Pace indicator should rise as bricks are removed.
- Speed remains bounded and the game remains controllable.

### Lane Dodge
- Let a red block pass the green player.
- Move into that lane immediately after the block is visibly below the player.
- The game must continue.
- Collision should occur only while the red rectangle actually overlaps the green rectangle.
- LEVEL should rise during a longer run and both fall speed and spawn pressure should become clearly harder.

### Snake
- Relaxed remains the default.
- Collect several foods.
- Movement should gradually accelerate from the selected base preset.
- Direction taps/swipes must not reset or postpone the movement clock.
