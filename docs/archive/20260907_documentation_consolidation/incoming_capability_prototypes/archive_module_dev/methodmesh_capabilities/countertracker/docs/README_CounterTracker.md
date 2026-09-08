# Counter / Tracker

Status: **Development**

A generic interactive state workspace for lightweight counters, HP/EXP/resource tracking and Boolean status flags. The module owns its persistence and immersive UI; it does not require capability-specific changes to the MethodMesh host.

## Capabilities

- `counter.workspace` — create/use multiple named counters and status flags, optionally persist the workspace/history, and return an explicit snapshot.

Native behaviour is interactive and dashboard-like. Native preset runs remain on the live workspace until **Finish with snapshot** is pressed. ODK/external callers can use interactive capture (`input_interactive_capture=true`) or supply `input_snapshot_json` and set interactive capture false for a single-shot return.

## Android intent

Interactive capture:

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='counter.workspace',input_interactive_capture='true',return_mode='flat')
```

Single-shot supplied snapshot:

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='counter.workspace',input_interactive_capture='false',input_snapshot_json='{"counters":[{"name":"Dose count","value":12,"kind":"tally"}],"flags":[]}',return_mode='flat')
```

## Inputs

Configuration inputs:

- `input_persist_state` — Boolean, default `true`; persist the native workspace in app-private storage.
- `input_persist_history` — Boolean, default `false`; retain up to 200 local change snapshots.
- `input_interactive_capture` — Boolean, default `true`; keep the workspace interactive when invoked externally.

Runtime input:

- `input_snapshot_json` — optional complete snapshot for non-interactive/single-shot execution.

Counter state supports `name`, integer `value`, positive integer `step`, optional `minimum`/`maximum`, and `kind` (`tally`, `hp`, `exp`, `resource`). Flags contain `name` and Boolean `value`.

## Outputs

Core outputs:

- `counter_snapshot_json` — complete selected workspace snapshot.
- `counter_primary_name`
- `counter_primary_value`
- `counter_count`
- `counter_active_flag_count`

Audit-priority outputs:

- `counter_status`
- `counter_captured_time_iso`
- `counter_error`

Full metadata:

- `counter_metadata_json`

Native sharing should favour the snapshot/main values rather than metadata.

## ODK example

`example_odk_counter.workspace.xlsx` demonstrates an ODK group intent that opens an interactive counter capture with persistence disabled for the form instance and receives the core/audit fields into child questions. The action is on a `begin_group` with `appearance=field-list` and all capability inputs are `input_` prefixed.

## Persistence, permissions and offline behaviour

No Android permission or network access is required. Persistence uses app-private `SharedPreferences`. History is deliberately opt-in because frequent counter changes can otherwise create unnecessary audit volume.

## Known limitations

- Counter values are integer-valued in v0.1.0.
- History records state changes, not MethodMesh graph results; only an explicit snapshot is returned through `onConfirmed(...)`.
- The workspace is not a background service and does not synchronise between devices.
