# Display v0.4.0 validation notes

## Capability surface

The module exposes exactly four intentional capabilities:

- `display.show` — general visual content;
- `display.timer` — display-oriented countdown;
- `display.debate_timer` — colour-phase speaking/debate countdown;
- `display.clock` — display-oriented local clock.

The old unused `display.static`, `display.marquee`, `display.board`, `display.code` and `display.sequence` IDs remain absent. Static text, marquee, board-like multiline text and giant codes remain options/inputs of `display.show` rather than returning as separate methods.



## v0.4 debate timer

- Added canonical `display.debate_timer`; no existing method IDs were changed.
- Defaults: 300 seconds total, yellow warning at 60 seconds remaining.
- Visual phase is derived only from remaining time: green above the warning threshold, yellow from the threshold through one second, red at zero.
- Total and warning thresholds use the same native Android H/M/S number-wheel editor as `display.timer`.
- Runtime remains based on an absolute monotonic `SystemClock.elapsedRealtime()` deadline.
- The warning sound is one bounded short beep when the yellow phase is first entered. `warningPlayed` is saveable and does not reset on pause/resume, recomposition or orientation.
- The zero sound reuses the existing finite alarm cadence (three short beeps, pause, three short beeps).
- Restart clears warning/alarm latches and creates a new deadline. Pause preserves remaining milliseconds and therefore preserves the displayed phase.
- STOP commits `remaining_seconds`, phase and completion reason; Back cancels without Commit.
- Bottom controls retain navigation-bar padding plus the existing extra clearance.
- Added a dedicated module-owned ODK example for `display.debate_timer`.

## v0.3.1 timer input/alarm refinement

- Duration entry uses three native Android `NumberPicker` wheels (hours/minutes/seconds); there are no free-text duration boxes.
- Wheel values are zero-padded, bounded to 00–99 hours and 00–59 minutes/seconds, and remain represented canonically as `duration_seconds`.
- The due sound is now a finite alarm-like cadence rather than one isolated beep.

## v0.3 additions

### Countdown

- Native duration editor exposes hours/minutes/seconds but maps to one canonical `duration_seconds` setting.
- Countdown state is based on a monotonic `SystemClock.elapsedRealtime()` deadline; UI ticks recompute remaining time from that deadline rather than decrementing state.
- Pause stores the precise remaining milliseconds; Resume creates a new monotonic deadline from that remainder.
- Restart creates a new deadline from the original configured duration.
- A bounded alarm cadence is emitted on the Android alarm stream when zero is reached: three short beeps, a pause, then three short beeps; the `ToneGenerator` is then stopped and released.
- The countdown remains on `00:00` after the finite alarm cadence until Restart/STOP/Back rather than turning into an ongoing alarm.
- Full-screen controls auto-hide while running, reappear on tap, and are forced visible at zero.
- Bottom controls use navigation-bar padding plus 18 dp additional clearance.
- STOP commits. Back cancels without Commit.

### Clock

- Supports `HH:MM` and `HH:MM:SS` only, as requested.
- Optional date is shown above the time.
- Uses device local time zone and locale.
- Updates are aligned to the next second/minute boundary rather than an arbitrary repeating delay from composition time.
- Time/date text use measured single-line auto-fit and share the same renderer between preview and full-screen view.
- Full-screen controls use the same inset-safe, auto-hiding presentation behaviour as the rest of Display.

## Existing `display.show` behaviour retained

- One general sign method rather than separate static/marquee/board/code methods.
- Text/emoji and local image/GIF/animated-WebP support.
- Still/scroll/flash/pulse motion.
- Flash remains hard-capped at 2 Hz.
- Non-scrolling text never breaks inside a word.
- Scrolling uses a complete unconstrained single-line text layout drawn on the clipping viewport canvas.
- Marquee speed remains fixed linear dp/second and therefore does not change with phone rotation, viewport width, text length or font size.
- Text size and motion speed use sliders.
- Preview and full-screen presentation share rendering logic.
- Bottom controls remain clear of Android navigation/gesture controls.

## Architecture review

`display.timer` is intentionally a simple screen-as-timer presentation. It does not replace or copy the persistent scheduling/notification responsibilities of the separate Time Tools module. The Display implementation has no notification service, exact alarm, interval-programme or background-timer behaviour.

`display.clock` similarly presents device-local current time; it does not create a second scheduling subsystem.

All four capabilities remain independently callable through the standard MethodMesh method/screen/settings contracts.

Four module-owned XLSForm examples are supplied, one per public method, each using a grouped `body::intent` call and the canonical `methodmesh_full_json` return field.

## Build status

The source was revised against the current public MethodMesh `MethodMeshModule`, `MethodSetting`, `CapabilityScreenContext` and `CapabilityScreenScaffold` interfaces visible in the repository. A complete local Android project/SDK is not mounted in this execution environment, so the full app Gradle build could not be run here.

Run after drop-in:

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest
```

Recommended device smoke test:

1. existing `display.show` still/scroll behaviour in portrait and landscape;
2. ordinary 5-second countdown reaches zero accurately and emits the bounded alarm cadence;
3. ordinary timer Pause/Resume and Restart remain accurate;
4. debate timer starts green at 5:00, becomes yellow at 1:00, emits exactly one warning beep, then becomes red at 00:00 and emits the zero alarm cadence;
5. pause/resume and rotate the debate timer after the warning beep and confirm it does not replay;
6. restart the debate timer and confirm the warning beep is available again on the next threshold crossing;
7. timer/debate controls remain clear of Android gesture/3-button navigation;
8. clock `HH:MM` with date off and `HH:MM:SS` with date on;
9. rotate clock portrait/landscape and confirm auto-fit;
10. Preset with fixed debate-timer settings starts immediately;
11. external/ODK result return after STOP for all public methods.
