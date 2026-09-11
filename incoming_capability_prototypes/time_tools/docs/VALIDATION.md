# Validation — Time & Alarms v0.6

## Contract

- [x] `TimeToolsModule` implements `MethodMeshModule` and is compatible with automatic `*Module.kt` indexing.
- [x] Individual methods remain independently exposed for direct capability use, presets, protocols and ODK.
- [x] `time.until` method ID preserved while the human label is now **Date & time countdown**.
- [x] Dashboard is additive and does not own execution.

## Functional checks

- [x] Monotonic countdown/stopwatch engine self-test retained.
- [x] Calendar-aware long-range formatter added.
- [x] Alarm schedule model supports once/daily/weekdays/weekends/weekly/custom days.
- [x] Reminder message, Done confirmation, configurable follow-ups and Snooze represented in durable timer state.
- [x] Long-range ongoing notification defaults off.
- [x] Lock-screen message privacy is independent from lock-screen timer visibility.
- [x] Notification actions defined for countdown and stopwatch.
- [x] Elapsed-time and duration-calculator capability screens are no longer generic/no-op surfaces.
- [x] Interval screen now starts and displays a real interval runtime.

## Host build status

The complete MethodMesh repository is not mounted in this chat runtime, so an Android Gradle build could not be executed here. Pure Kotlin timing/scheduling sources are syntax-tested separately. Before Production promotion run:

`./gradlew :app:testDebugUnitTest`

`./gradlew :app:assembleDebug`

and exercise notification actions on a physical Android device.

## v0.6.1 Compose canvas fix

- Hoisted `MaterialTheme.colorScheme` reads out of the `Canvas` `DrawScope` in `TimerHero`.
- `Canvas` now receives plain `Color` values captured in the surrounding `@Composable` scope.
- This removes the compiler error: `@Composable invocations can only happen from the context of a @Composable function`.


## v6.3 live timer regression

- Running countdown/stopwatch/interval/date-time screens now consume an explicit Compose `produceState` live projection of the durable timer state.
- The Time & Alarms dashboard has an explicit UI refresh tick; immutable durable timer records no longer leave the displayed remaining time stale.
- Module-dashboard tiles render their child capability with a dashboard/manual completion context rather than routing through the external automatic-return intent path.
- Date & time countdown no longer swaps immediately to a scheduled-result panel during a manual native run; its live countdown remains visible.


## v6.4 ongoing notification surface

- Countdown, stopwatch and interval timers use separate ongoing Android notifications by default.
- Active notifications are grouped under MethodMesh when more than one timer is running.
- Stopwatch and countdown clocks use Android's native notification chronometer rather than per-second notification republishing.
- Fixed a notification-clock bug: `Notification.when` now receives projected wall-clock epoch timestamps rather than `SystemClock.elapsedRealtime()` values. The monotonic runtime remains authoritative.
- Ongoing timer channel moved to `methodmesh_time_active_v2`, DEFAULT importance but silent, so active timers remain readily visible without generating an alert.
- Pause/Resume/Stop remain available from the shade; Stopwatch also exposes Lap and Countdown exposes +1:00.
- Completion cancels the ongoing timer notification before showing the due alert.
- Long-range `time.until` remains opt-in for ongoing notification display.
- Lock-screen text privacy remains independent of ongoing notification visibility.
