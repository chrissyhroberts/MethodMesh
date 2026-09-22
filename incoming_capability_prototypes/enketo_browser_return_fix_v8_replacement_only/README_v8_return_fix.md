# MethodMesh Enketo browser return fix — v8

This patch changes only `IntentRouterActivity.kt`.

## Why

The browser bridge, clipboard JSON and MediaStore publication were working in v7, but after completion `finishAndRemoveTask()` could cause Android to promote an older MethodMesh Home task instead of restoring the Chrome/Enketo task that launched the deep link.

## Fix

For browser/BROWSABLE launches, the router now:

1. calls `moveTaskToBack(true)` while the transient MethodMesh task is still alive;
2. Android therefore reveals the existing browser task and exact live Enketo tab underneath;
3. calls `finish()` to close the router after it is no longer foreground.

It does **not** launch Chrome, open a URL, or create a new tab. Android/ODK result handling is unchanged.

Replace the existing file at:

`app/src/main/java/com/example/methodmesh/transport/android/IntentRouterActivity.kt`
