# Multi-counter

**Status:** Development prototype  
**Module:** `multicounter`  
**Method:** `counter.multientity`  
**Version:** `0.1.0`

`counter.multientity` is a compact multi-entity board for repeated counts, simple score tallies and lightweight per-entity timing. It is designed for situations where the operator needs to keep several values visible and tappable at once without opening separate capability instances.

The module is offline-only and keeps the active session in UI state. Nothing is automatically saved to MethodMesh storage. The operator explicitly finishes the session before a result is returned.

## Scope boundary

This is the generic multi-entity primitive.

It deliberately does **not** implement:

- sport-specific scoring rules, sets, innings, penalties or match logic — those belong in the scoring systems module;
- alarms, notifications, interval programmes, lap analysis or long-running background timers — those belong in the timer module;
- campaign/game character records or persistent score histories — those belong in a higher-level tabletop/game records module if required.

It does provide a minimal overlap where that is intrinsically useful: a score is a counter, and a per-entity stopwatch/countdown is useful alongside a counter.

## Native workflow

1. Choose `count`, `time` or `count_and_time`.
2. Set entity count and optional names, for example `Alice|Bob|Charlie`.
3. Configure count behaviour and/or timer behaviour.
4. Start the session.
5. Work from the compact board:
   - `−` / `+` changes that entity by the configured step;
   - Start/Pause controls a single entity timer;
   - Start all starts every idle timer;
   - a non-zero start stagger delays successive entities by the configured interval;
   - Pause all, Reset time and Reset values are global operational controls.
6. Tap **Finish session**. Running timers are frozen at that instant.
7. The MethodMesh result screen shows the compact result and the normal share/copy/save/Done actions.

The primary result is `counter_result`. It is intentionally human-readable and clipboard-friendly, for example:

```text
Alice 12 · 01:14.2
Bob 9 · 00:59.8
Total 21
Leader Alice (12)
```

JSON/event history is kept out of the primary native result surface.

## Settings and inputs

All settings are declared through `MethodSetting`.

| Input | Type | Meaning |
|---|---|---|
| `input_mode` | choice | `count`, `time`, `count_and_time` |
| `input_entity_count` | integer | 1–24 entities |
| `input_entity_names` | free text | pipe/newline/semicolon-separated labels |
| `input_counter_start` | integer | starting value for each entity |
| `input_step` | integer | increment/decrement size |
| `input_allow_negative` | boolean | allow values below zero |
| `input_show_total` | boolean | append total to the final result |
| `input_show_leader` | boolean | append leader/tie information |
| `input_timer_mode` | choice | `stopwatch` or `countdown` |
| `input_countdown_seconds` | integer | countdown length, 1 second to 7 days |
| `input_stagger_seconds` | integer | Start-all delay between successive entities; 0 = simultaneous |
| `input_timer_precision` | choice | `seconds` or `tenths` |

`entity_names` is genuine free text; fixed enumerations use `ChoiceSetting`/`BooleanSetting` rather than text boxes.

## Presets

Preset fixed settings are hidden using `CapabilityScreenContext.settingShouldBeShown(...)`.

Examples:

- save a two-player score counter with step 1 and no timers;
- save a four-lane stopwatch board with a five-second stagger;
- save a field tally where names are runtime inputs but count behaviour is fixed.

A native preset with no declared runtime inputs opens directly into the board. A preset with runtime inputs shows only those inputs before the board starts.

## ODK / XLSForm

This is an interactive capability. ODK may supply the board configuration through intent extras, after which MethodMesh opens directly into the counter board. It does not force the ODK caller through a second generic setup form. The operator works the board and taps **Finish session**; MethodMesh then returns the final snapshot.

Example intent:

```text
com.example.methodmesh.EXECUTE_METHOD(
  method_id='counter.multientity',
  input_mode='count_and_time',
  input_entity_count='3',
  input_entity_names='Control|Arm A|Arm B',
  input_counter_start='0',
  input_step='1',
  input_allow_negative='false',
  input_timer_mode='stopwatch',
  input_stagger_seconds='5',
  input_timer_precision='tenths',
  input_show_total='true',
  input_show_leader='true',
  input_payload_mode='FULL',
  return_mode='flat'
)
```

Static configuration belongs in `body::intent` as `input_*` arguments. Unprefixed group children are return placeholders, following the current MethodMesh XLSForm convention.

The included `example_odk_Multicounter.xlsx` demonstrates an interactive three-entity count+timer call and captures:

- `counter_result` — main human-readable result;
- `counter_entities_json` — structured final entity state;
- `counter_audit_json` — capability event/configuration audit;
- `methodmesh_full_json` — shared MethodMesh FULL envelope.

## Outputs

| Field | Role |
|---|---|
| `counter_result` | **core/beef** multiline result used for native copy/share |
| `counter_entities_json` | structured final state for external callers |
| `counter_audit_json` | configuration + event log + session timing |
| `counter_finished_time_iso` | completion time |
| `counter_status` | succeeded/failed |
| `counter_error` | failure detail |

`counter_result` is deliberately the only ordinary CORE field. Fields ending in `_json`, status/error and completion time remain off the default native result surface under the current `OutputFormatter` projection rules.

## Timer semantics

Timing uses Android monotonic elapsed realtime while the session is active. This avoids wall-clock edits changing an in-progress timer.

For staggered Start all:

- entity 1 starts immediately;
- entity 2 is scheduled at `+stagger_seconds`;
- entity 3 at `+2 × stagger_seconds`, etc.;
- scheduled rows remain individually cancellable;
- Pause all cancels pending starts and freezes running timers;
- countdown rows stop at zero.

The serialised UI state stores the monotonic anchors so portrait/landscape rotation can reconstruct running and scheduled timers without resetting them.

## Audit/event behaviour

The active session records state-changing events in memory, including:

- session start/finish;
- counter increment/decrement/reset;
- timer start/pause/reset;
- scheduled stagger starts/cancellations;
- countdown expiry.

Each event carries an offset from session start and a wall-clock ISO timestamp when the event is observed. The retained event log is capped at 5,000 events to avoid unbounded phone-memory growth. If that cap is exceeded, `counter_audit_json` records `dropped_event_count` and `event_log_truncated=true`; truncation is never silent.

This audit trail is useful provenance but is not presented as the main result.

## Permissions, dependencies and offline behaviour

- No dangerous Android permissions.
- No network access.
- No third-party API/service.
- No local database or automatic file save.
- Uses Android/Compose and the existing MethodMesh runtime/scaffold only.

## Protocols, schedules and widgets

The module returns a normal `ExecutionResult` and relies on MethodMesh's shared closeout contract. It does not add protocol/schedule special cases.

The capability is intrinsically interactive. A protocol or scheduled invocation should therefore be treated as an operator step: open the board, collect the session, finish, then continue. It should not fabricate a completed counter session without human interaction.

A saved preset can be launched from the normal generic preset/widget infrastructure without any core UI knowledge of counters.

## Rotation and state

Session configuration, serialised entity/timer state and final result fields use `rememberSaveable`. Running timers store monotonic anchors rather than a displayed tick value. Rotation therefore does not reset scores, elapsed times, pending stagger starts or a completed result.

## Error/failure behaviour

The state constructor clamps declared numeric limits to the module contract, normalises a negative starting value to zero when negative values are disabled, and generates fallback labels (`Counter 1`, `Counter 2`, …) when fewer names than entities are supplied. Malformed restored JSON causes the UI to show a clear restart action rather than inventing state.

## Development status

The source was written against the current MethodMesh `master` interfaces inspected on 2026-09-06, including `MethodMeshModule`, `MethodSetting`, `CapabilityScreenSpec`, `CapabilityScreenScaffold`, `As100Method` and current CORE/AUDIT/FULL output projection behaviour.

This packaging environment could inspect the live GitHub source but could not clone/build the Android repository. Under the MethodMesh contributor rules this folder therefore remains in `incoming_capability_prototypes/multicounter/` until Work-mode/repository review compiles it and exercises the production checklist.
