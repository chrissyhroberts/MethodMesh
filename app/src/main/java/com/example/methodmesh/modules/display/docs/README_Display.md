# MethodMesh Display

**Module ID:** `display`  
**Module version:** 0.3.1  
**Maturity:** Development

Display turns the Android device into an across-the-room visual surface. The module now has three deliberately distinct capabilities:

- `display.show` — general signs, text/emoji, images and animated media;
- `display.timer` — a large countdown display with a short alarm-like local beep pattern at zero;
- `display.clock` — a large local 24-hour clock, optionally with the date above it.

The earlier proliferation of separate static/marquee/board/code methods remains removed. Those are still presentation options of `display.show`, not separate capabilities. Timer and clock are separate because they have genuinely different live time semantics rather than merely different styling.

## `display.show`

`display.show` accepts text/emoji or local image/GIF/animated-WebP content. Text presentation supports:

- `motion=still|scroll|flash|pulse`;
- white-on-black, black-on-white, black-on-yellow, neon, rainbow, pride and fabulous themes;
- start/centre/end alignment;
- content rotation 0/90/180/270;
- continuous text-size control;
- orientation-independent linear marquee speed;
- left/right scrolling;
- optional high brightness.

Scrolling text is always one unbroken line. The line is measured at its complete unconstrained width and drawn inside a clipping viewport; font size is fitted from available height, so long text remains large and traverses fully from off-screen to off-screen. Non-scrolling text wraps only at whitespace or explicit newlines and never splits an individual word.

The configuration preview and full-screen presentation share the same renderer. Live controls respect Android navigation/gesture insets, auto-hide and reappear on tap. `STOP` commits; Android Back cancels the uncommitted presentation.

## `display.timer`

`display.timer` is intentionally simple: configure a duration, press **START**, and the device becomes a large countdown display.

Duration entry uses native Android number-picker wheels for hours, minutes and seconds rather than free-text fields.

Settings:

- `duration_seconds` — 1 to 359999 seconds (native UI presents this as hours/minutes/seconds);
- `beep` — bounded alarm-stream beep pattern when the countdown reaches zero;
- `theme` — `white_on_black`, `black_on_white` or `black_on_yellow`;
- `high_brightness` — temporarily request maximum display brightness.

Runtime behaviour:

- the countdown starts immediately when the full-screen presentation opens;
- Pause/Resume preserves exact remaining duration;
- Restart begins again from the configured duration;
- the live value is derived from an absolute `SystemClock.elapsedRealtime()` deadline rather than decrementing a counter, avoiding drift and surviving ordinary recomposition/orientation changes;
- reaching zero produces a bounded two-burst alarm cadence (three short beeps, pause, three short beeps) and leaves `00:00` visible until the operator stops or restarts the timer;
- controls auto-hide during the countdown but are forced visible when zero is reached;
- `STOP` commits the final remaining duration and records `countdown_finished` when zero has been reached, otherwise `stopped_by_operator`;
- Android Back cancels without Commit.

This is a display-oriented timer. MethodMesh Time Tools remains the appropriate subsystem for persistent alarms, notification-shade timers, interval programmes and scheduling behaviour.

Canonical timer outputs include:

- `display_timer_status`
- `display_timer_result`
- `display_timer_duration_seconds`
- `display_timer_remaining_seconds`
- `display_timer_beep`
- `display_timer_theme`
- `display_timer_high_brightness`
- `display_timer_started_time_iso`
- `display_timer_stopped_time_iso`
- `display_timer_elapsed_ms`
- `display_timer_completion_reason`
- `display_timer_audit_json`
- `display_timer_error`

## `display.clock`

`display.clock` is a deliberately minimal local-time display.

Settings:

- `clock_format=HH:MM|HH:MM:SS`;
- `show_date=true|false` — when enabled, the date is shown above the time;
- `theme=white_on_black|black_on_white|black_on_yellow`;
- `high_brightness=true|false`.

The clock uses the device local time zone and locale. `HH:MM:SS` updates on second boundaries; `HH:MM` updates on minute boundaries. Time and date are independently auto-fitted so the clock remains maximally legible in portrait and landscape. The date uses the device locale with a form equivalent to `EEE d MMM yyyy`.

Canonical clock outputs include:

- `display_clock_status`
- `display_clock_result`
- `display_clock_time`
- `display_clock_date`
- `display_clock_format`
- `display_clock_show_date`
- `display_clock_zone_id`
- `display_clock_theme`
- `display_clock_high_brightness`
- `display_clock_started_time_iso`
- `display_clock_stopped_time_iso`
- `display_clock_elapsed_ms`
- `display_clock_completion_reason`
- `display_clock_audit_json`
- `display_clock_error`

## Presets, protocols, ODK and external invocation

All three capabilities are independently registered MethodMesh methods and therefore remain available through direct native use, Presets, Protocols, RIL and external/ODK invocation.

Representative method calls are:

- `display.show` with content/presentation settings;
- `display.timer` with `duration_seconds`, `beep`, `theme` and `high_brightness`;
- `display.clock` with `clock_format`, `show_date`, `theme` and `high_brightness`.

Module-owned XLSForm examples are included separately so each grouped intent can use the canonical `methodmesh_full_json` return field without name collisions:

- `docs/example_odk_Display.xlsx` — `display.show`;
- `docs/example_odk_Display_Timer.xlsx` — `display.timer`;
- `docs/example_odk_Display_Clock.xlsx` — `display.clock`.

Fixed Preset/external settings may start the presentation immediately through the normal `CapabilityScreenContext.startsImmediately` lifecycle. Canonical results continue through the shared MethodMesh Commit/automatic-return machinery.

## Display target

All v0.3 capabilities render to the current local Android screen. The broader display renderer/target architecture remains available for future external displays without multiplying sign methods merely because transport/rendering technology changes.
