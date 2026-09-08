# Arcade v0.033 smoke checklist

## Pong — shot control

- Hold paddle still and hit near centre: return should be relatively steep/central.
- Hold paddle still and hit near either edge: return should angle strongly toward that side.
- Repeat the same approximate contact while moving the paddle left/right: outgoing horizontal trajectory should visibly change with paddle motion.
- A receiver should need to move to the actual projected intercept; the ball should not return along a fixed rail.
- In CPU mode, the CPU should move toward the projected intercept after side-wall reflection rather than simply tracking current ball X.
- Continuous dragging must not stop the simulation clock.

## Wall Break — skill physics

- Centre, edge and moving-paddle hits should produce visibly different outgoing trajectories.
- Side impact on a brick should reverse horizontal travel; top/bottom impact should reverse vertical travel; corner contacts should not always force a Y bounce.
- Two-hit bricks survive the first hit and disappear on the second.
- Clearing each layout advances automatically after a short serve pause.
- Verify all five layouts: Wall, Checker, Fortress, Chevron, Crown.
- Level transition preserves score and grants one life up to the five-life cap.
- Final level clear ends the run as `winner=cleared`.

## Snake — touch-first layout

- Grid visibly contains 18 columns and 30 rows.
- The board uses most of a portrait screen rather than a small square region.
- There are no arrow/WASD direction controls.
- Swipe up/right/down/left queues the corresponding legal turn.
- Opposite-direction swipe cannot produce a hidden 180-degree reversal.
- Speed slider exposes every integer rate from 3 to 16 cells/second.
- Speed can be changed while the run is active.
- Food can appear anywhere in the 540-cell board and never inside the snake.
