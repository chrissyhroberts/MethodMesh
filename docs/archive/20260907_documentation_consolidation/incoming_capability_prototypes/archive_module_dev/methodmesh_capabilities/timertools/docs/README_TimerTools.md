# Timer tools

Status: **Development**

An immersive multi-timer workspace covering stopwatches, lap timing, countdowns, interval timers and simple session timers. It deliberately excludes background alarms/notifications.

## Capabilities

- `timer.tools` — run multiple simultaneous timers and return a structured timer snapshot.

Supported modes: `stopwatch`, `countdown`, `interval`, `session`. Stopwatch timers support laps. Interval timers can be one-shot or repeating. Countdown/session timers stop at zero.

## Android intent

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='timer.tools',input_default_mode='countdown',input_default_duration_seconds='120',return_mode='flat')
```

The normal external use is interactive capture: ODK opens the timer workspace, the operator runs the timer, then presses **Use this snapshot**.

## Inputs

Configuration inputs:

- `input_default_mode` — `stopwatch`, `countdown`, `interval`, or `session`.
- `input_default_duration_seconds` — positive integer, default `60`.
- `input_default_interval_seconds` — positive integer, default `30`.
- `input_repeat_interval` — Boolean.
- `input_interactive_capture` — Boolean, reserved for caller policy; v0.1.0 UI remains operator-driven.

Runtime/pipe input:

- `input_timers_json` — optional timer snapshot when the method is executed without the interactive screen.

## Outputs

Core outputs:

- `timer_timers_json`
- `timer_primary_name`
- `timer_primary_mode`
- `timer_primary_elapsed_ms`
- `timer_primary_remaining_ms`
- `timer_lap_count`

Audit-priority outputs:

- `timer_status`
- `timer_captured_time_iso`
- `timer_error`

Full metadata:

- `timer_metadata_json`

## ODK example

`example_odk_timer.tools.xlsx` demonstrates launching the interactive timer workspace from an ODK group and receiving the selected snapshot fields.

## Permissions and offline behaviour

No permission and no network access are required. Timing is local. Running timers derive elapsed time from the device wall clock so they continue sensibly across normal app backgrounding and orientation changes.

## Known limitations

- No alarm, notification, foreground service or guaranteed background wake-up behaviour is implemented.
- Wall-clock changes while a timer is running can affect elapsed time.
- v0.1.0 does not persist active timers across process death or device reboot.
