# Integration notes

1. Replace the previous `modules/time_tools/` folder rather than overlaying versions.
2. Merge `docs/ANDROID_MANIFEST_SNIPPET.xml` into the app manifest. Do not add a central module registration entry.
3. Android 13+ requires notification permission for visible notifications.
4. Android 12+ may deny exact-alarm access; `TimerAlarmScheduler` falls back to `setAndAllowWhileIdle` and reports approximate scheduling.
5. Notification-channel choices made by the user in Android settings override module requests for sound/vibration/light.
6. Long-range `time.until` defaults to a due notification only. Persistent shade/lock-screen display is opt-in.
