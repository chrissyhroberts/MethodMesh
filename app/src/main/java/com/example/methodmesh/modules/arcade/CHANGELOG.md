# Changelog

## v0.02

- Restored Arcade as a separate MethodMesh capability alongside GameDeck.
- Retained stable method ID `arcade.snapshot`; method version is now `0.0.2`.
- Rebuilt Snake Sprint and Pong Table around the GameDeck v0.055 UX principles.
- Added visible `MENU`, How to Play, Sound, Restart, All Games and Done controls.
- Menus/help pause real-time simulation and use modal scrims so input cannot leak to the game.
- Added clear Again / Games / Done terminal actions.
- Snake now waits for an explicit START rather than moving before the player is ready.
- Snake supports both swipe and visible arrow controls.
- Snake turn input is queued against the committed direction, preventing rapid hidden 180° reversals.
- Fixed Snake collision semantics so moving into a vacating tail cell is legal.
- Snake food generation now uses the public Chance boundary with `secure_random` or reproducible `fixed_seed`.
- Added full-board completion handling for Snake.
- Pong now has an explicit pre-match setup card with CPU / 2-player selection and START MATCH.
- Pong pauses briefly after each point.
- Pong drag ownership is fixed at drag-start, so crossing the centre line cannot switch paddles mid-gesture.
- Pong supports both tap and drag positioning.
- CPU mode labels the local player `YOU`; two-human mode rotates the far player rail 180°.
- Added small local Arcade records: Snake best score/length and Pong CPU W/L / shared matches.
- Expanded structured snapshot outputs with tick, finished state, winner, session ID and RNG mode.
- Added centralized `ArcadeUxCatalog.kt` for launcher/help copy.
- Split orchestration, shared chrome, Snake UI and Pong UI into separate Kotlin files for reviewability.
- Pong Restart / Again preserves the selected CPU versus two-player mode.
- No GameDeck internals are imported; future shared player/record services remain a separate architecture step.

## v0.01

- Initial fixed-step Arcade framework.
- Snake Sprint prototype.
- Pong Table prototype with CPU / two-player foundations.
- Generic immersive host declaration.


## v0.021 startup crash fix

- Fixed the Arcade `ExecutionResult` wrapper to use the same MethodMesh completion
  pattern as the working GameDeck/Chance capabilities, including an `Entity`,
  shared provenance, status-aware transformation result and diagnostics.
- Native opening of the Arcade launcher no longer constructs a MethodMesh graph
  result merely to display the shelf. External immediate-submit behaviour is unchanged.
- Local record reads are defensive so stale/corrupt development preferences cannot
  crash the Arcade launcher.
- Method version bumped to `0.0.3`.


## v0.03

- Added four Snake speed presets: Relaxed, Normal, Fast and Turbo.
- Changed the default Snake speed from the original 120 ms step to a much more forgiving 235 ms Relaxed step.
- Snake speed can be chosen before START and is also exposed as the typed `snake_speed` capability setting.
- Added **Brick Breaker**, a solo fixed-step paddle/brick game with three lives and local best-score/clear records.
- Added **Lane Dodge**, a solo five-lane avoidance game with gradually increasing spawn pressure and Chance-backed hazard lanes.
- Added local best-score records for Brick Breaker and Lane Dodge.
- Added launcher/help/result copy for the new games.
- Method version bumped to `0.0.4`.


## v0.031 — real-time clock and collision fix

- Fixed Pong and Brick Breaker pausing whenever a paddle drag updated state.
- Fixed the same architectural issue for Lane Dodge and made Snake use the same persistent simulation-clock pattern.
- Real-time loops now use a persistent `LaunchedEffect` keyed only to run/pause/terminal state and `rememberUpdatedState(onStep)` to consume the newest authoritative transition callback.
- Lane Dodge collision now uses actual player/hazard rectangle overlap; moving into the visible wake of a passed block is safe.
- Lane Dodge now advances through explicit levels that increase both fall speed and spawn pressure.
- Pong now accelerates noticeably with rally length and resets pace after a point.
- Brick Breaker accelerates modestly on paddle/brick contact with a bounded maximum speed.
- Snake now accelerates gradually from the selected starting preset as food is collected.
- Added visible progression cues: Pong rally count, Brick Breaker pace and Lane Dodge level.
- Method version bumped to `0.0.5`.


## v0.032 — Compose State delegate compile fix

- Replaced delegated `rememberUpdatedState` reads in Snake, Pong, Brick Breaker and Lane Dodge with explicit `State.value` access.
- This removes the need for the Compose `getValue` delegate import and fixes the `State has no method getValue` / cascading `latestOnStep` compiler errors.
- Method version bumped to `0.0.6`.


## v0.033 — skill physics, Wall Break levels and full-height Snake

- Reworked Pong and Wall Break paddle physics using established open-source arcade-game behaviour: impact position aims the outgoing ball and paddle motion adds horizontal "english".
- Paddle returns use bounded angular deflection (about ±64°), minimum vertical velocity and speed caps rather than rail-like component flipping.
- Pong CPU now predicts the ball intercept after side-wall reflections and moves toward that target.
- Wall Break brick collision now uses circle-vs-rectangle closest-point contact and contact-side reflection.
- Wall Break now has five distinct levels: Wall, Checker, Fortress, Chevron and Crown; later layouts include two-hit bricks.
- Snake expanded from 10×10 to an 18×30 portrait grid.
- Removed the on-screen Snake D-pad; swipe is the primary steering interaction.
- Replaced four coarse Snake speed presets with a granular 3–16 cells/second slider that can be adjusted during play.
- Updated typed capability setting to `snake_speed_cps` with compatibility for older preset strings.
- Method version bumped to `0.0.7`.


## v0.034 — interactive ODK roundtrip

- Corrected the external/ODK lifecycle: opening Arcade no longer immediately returns an initial/historical-looking snapshot.
- An external `arcade.snapshot` request now opens the requested live game and remains in MethodMesh until the game reaches a terminal state.
- The finished session is recorded before the result is built, so returned player statistics include the game just played.
- External completion is emitted once per session after a short terminal-frame delay.
- Added `arcade_player_stats_json`.
- Added `arcade_play_data_json` with game-specific terminal play metrics.
- Updated the example XLSForm from “Get snapshot” semantics to “Play game” semantics.
- Method version bumped to `0.0.8`.


## v0.035 — Central-compatible ODK showcase controls

- Restored the legacy `snake_speed` select-one field in the example XLSForm so existing ODK Central form structure is preserved.
- Kept `snake_speed_cps` as the authoritative granular Snake control (3–16 cells/second).
- Added ODK-configurable Pong CPU difficulty: `casual`, `standard`, `sharp`.
- Added ODK-configurable Wall Break starting level: 1–5.
- Added ODK-configurable Lane Dodge difficulty: `easy`, `normal`, `hard`.
- Pong CPU difficulty changes reaction rate and prediction error while preserving the same ball/paddle physics.
- Lane Dodge difficulty changes starting pressure while normal in-game level progression remains active.
- Wall Break can start directly on any of the five wall layouts and continues through later levels.
- Returned play-data JSON now includes the selected difficulty/start-level metadata.
- Method version bumped to `0.0.9`.
