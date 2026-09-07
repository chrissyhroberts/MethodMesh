# Time Tools validation

Before promotion to Production, verify at minimum:

1. Each of the six method IDs is auto-discovered independently.
2. Each can be launched without entering the Time Tools dashboard.
3. Native countdown, stopwatch and interval state survives rotation.
4. Countdown and stopwatch remain correct when the device civil clock/timezone changes mid-run.
5. Pause time is excluded from measured elapsed time.
6. Lap totals are monotonic and lap durations sum consistently.
7. Countdown completion is derived from monotonic elapsed time and does not drift with UI tick delays.
8. `time.until` handles midnight, timezone and DST transitions with `java.time` semantics.
9. Cancellation returns shared closeout cancellation, not a zero-duration completed payload.
10. Preset-fixed values are not redundantly requested at runtime.
11. A protocol can execute each method through generic rails and continue after clean closeout.
12. ODK grouped intent calls return declared scalar fields and optional `methodmesh_full_json`.
13. Namespace projection is preserved centrally.
14. Copy/share defaults to the primary formatted duration rather than JSON.
15. No capability-specific logic is added to shared Home/Dashboard UI.
16. Active background timers remain accurate through screen lock and activity recreation after foreground-service integration.
17. `./gradlew :app:testDebugUnitTest` passes.
18. `./gradlew :app:assembleDebug` passes.
