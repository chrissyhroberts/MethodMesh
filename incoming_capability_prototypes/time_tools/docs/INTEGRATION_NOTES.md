# Integration notes

1. Replace the previous `modules/time_tools/` folder rather than overlaying versions.
2. Merge `docs/ANDROID_MANIFEST_SNIPPET.xml` into the app manifest. Do not add a central module registration entry.
3. Android 13+ requires notification permission for visible notifications.
4. Android 12+ may deny exact-alarm access; `TimerAlarmScheduler` falls back to `setAndAllowWhileIdle` and reports approximate scheduling.
5. Notification-channel choices made by the user in Android settings override module requests for sound/vibration/light.
6. Long-range `time.until` defaults to a due notification only. Persistent shade/lock-screen display is opt-in.

7. Active countdown/stopwatch/interval notifications use the `methodmesh_time_active_v2` channel at DEFAULT importance but are explicitly silent. The v2 channel ID is deliberate because Android does not permit an app to raise the importance of an already-created notification channel.
8. The system notification chronometer is given wall-clock `Notification.when` values projected from the module's monotonic elapsed/remaining state; do not pass `SystemClock.elapsedRealtime()` directly to `setWhen()`.
