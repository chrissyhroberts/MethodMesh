# Arcade physics research notes — v0.033

The v0.033 Pong and Wall Break behaviour was redesigned after reviewing open-source implementations and design notes. MethodMesh does **not** copy an external engine; it implements the same established gameplay principles inside Arcade's deterministic state-transition boundary.

## Sources reviewed

### foslock/breakout-roguelite

https://github.com/foslock/breakout-roguelite

Apache-2.0. Its documented physics uses circle/rectangle closest-point collision for bricks and maps normalized paddle impact position to a bounded ±60° deflection, with minimum vertical speed. Its level system also separates generation/configuration from rendering.

### SRombauts/pong-sdl3-cpp

https://github.com/SRombauts/pong-sdl3-cpp

The project roadmap explicitly describes Pong paddle reflection based on hit offset, a bounded maximum reflection angle, moving the ball clear of the paddle after contact, speed increase per paddle hit and a maximum speed cap.

### ppatel56/Pygame-Pong

https://github.com/ppatel56/Pygame-Pong

Its documentation describes using displacement from the paddle centre to determine the returned ball trajectory rather than merely reversing one velocity component.

## MethodMesh implementation

Arcade v0.033 applies those principles as its own compact rules:

```text
hitOffset = (ballX - paddleX) / paddleHalfWidth
paddleEnglish = recentPaddleVelocity × transferFactor
aim = clamp(hitOffset + paddleEnglish, -1, +1)
angle = aim × 64 degrees
outgoing velocity = speed × [sin(angle), ±cos(angle)]
```

The speed is bounded and a minimum vertical component prevents nearly-horizontal dead rallies.

For Wall Break, brick collision uses a circle/rectangle closest-point test. The previous ball position identifies whether the ball approached from a side, top or bottom; corner contacts fall back to the dominant contact normal.

Pong CPU play predicts where the current trajectory will reach the CPU paddle after side-wall reflections. It is therefore responding to the player's actual shot rather than following the ball's current X coordinate.
