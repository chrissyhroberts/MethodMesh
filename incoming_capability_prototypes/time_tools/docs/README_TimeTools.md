# MethodMesh Time & Alarms

Status: **Development**  
Module ID: `time_tools`

Time & Alarms provides first-class MethodMesh capabilities for live and scheduled time work. The module dashboard is an additional management surface only; presets, protocols, widgets and ODK call the individual method IDs directly.

## Methods

- `time.dashboard` — Time & Alarms manager/launcher.
- `time.countdown` — duration countdown; native UI supports typed hours/minutes/seconds, date/time mode, pause and `+1:00`.
- `time.stopwatch` — live stopwatch with laps.
- `time.interval` — repeated labelled phases.
- `time.until` — **Date & time countdown**; stable historical method ID retained for compatibility. Supports absolute targets and anchor + calendar-day offset + local time.
- `time.alarm` — one-off or recurring alarm: daily, weekdays, weekends, weekly or custom weekdays.
- `time.elapsed` — difference between two date/times.
- `time.duration.calculate` — duration addition/subtraction.

## Reminder messages and confirmation

Countdowns, intervals, date/time countdowns and alarms accept a `message`. Example:

`Time to take your progesterone tablet`

They also support:

- `require_confirmation` — keep follow-up logic active until the user taps **Done**;
- `follow_up_count`;
- `follow_up_interval_minutes`;
- `snooze_minutes` (`0` disables Snooze);
- sound, vibration, notification light and priority requests.

Android notification-channel settings remain authoritative: a user can override sound/vibration/light behaviour at OS level.

## Privacy and long-range timers

`time.until` defaults to **no ongoing notification** and **no persistent lock-screen countdown**. This is deliberate for long/private reminders such as medication or HRT workflows. Users who want a visible event countdown (for example, time until a festival) can opt into the ongoing notification and lock-screen display.

Reminder message text is separately controlled by `show_message_on_lock_screen` and defaults off. A lock-screen notification can therefore say only `Reminder due` while the unlocked shade contains the full message.

Long-range native displays use calendar-aware months/days plus hours/minutes/seconds rather than pretending every month is a fixed number of seconds.

## Active timer notifications

Countdowns, stopwatches and interval timers use a compact **ongoing Android notification by default** while running. Android's native chronometer renders the live count-up/countdown, so the notification continues to move while MethodMesh is behind another app without MethodMesh republishing it every second. The notification provides direct timer controls (Pause/Resume, Stop, plus Lap for stopwatches and +1 minute for countdowns). Multiple active timers remain separate notifications and are grouped under MethodMesh.

A completion alert supersedes the running notification. Long-range `time.until` countdowns remain opt-in for ongoing display so private multi-day reminders do not sit persistently on the lock screen. Lock-screen visibility and exposure of free-text reminder content remain separate settings.

## Notifications

Active short timers can appear in the Android notification shade and lock screen:

- countdown: live count-down, `+1:00`, Pause/Resume, Stop;
- stopwatch: live count-up, Lap, Pause/Resume, Stop;
- interval: live countdown, Pause/Resume, Stop;
- long-range date/time countdown: optional concise persistent status.

Due notifications support **Done** and **Snooze**. Follow-up reminders are cancelled by Done. Recurring alarms keep their next normal occurrence when the current occurrence is confirmed.

## Example: cycle-day reminder

A preset can launch `time.until` from a widget on cycle day 1 with:

- target mode: `anchor_offset_local_time`;
- anchor: launch time;
- day offset: `14` when the anchor is labelled cycle day 1 and the target is labelled cycle day 15;
- local time: `21:00`;
- message: `Time to take your progesterone tablet`;
- ongoing notification: `false`;
- require confirmation: `true`;
- follow-up count/interval as desired.

The timer persists independently of the dashboard.

## ODK

Use the normal grouped MethodMesh intent contract:

`com.example.methodmesh.EXECUTE_METHOD(method_id='time.countdown',input_duration_ms=${input_duration_ms},input_message=${input_message},input_payload_mode='FULL',return_mode='flat')`

Inputs use `input_*` names. Returned fields are group children. The supplied `docs/example_odk_time_tools.xlsx` demonstrates countdown, date/time countdown, stopwatch, interval, alarm, elapsed time and duration calculation.

## Android integration

This folder includes `docs/ANDROID_MANIFEST_SNIPPET.xml`. The receivers must be merged into the app manifest when the module is admitted. This is an Android platform constraint: MethodMesh Kotlin module discovery cannot dynamically declare manifest receivers needed to wake a stopped process for alarms and notification actions.

No HomeScreen or central capability registration is required.
