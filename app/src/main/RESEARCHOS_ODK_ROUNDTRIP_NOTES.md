# ResearchOS ODK round-trip refactor

This version moves the Android/ODK intent route onto the AS/ResearchOS execution path.

## What changed

- `IntentRouterActivity` now resolves methods through `As100MethodRegistry` rather than the legacy `MethodRegistry`.
- ODK / Android extras are parsed into an `InvocationContext`.
- The invocation context is passed into the `ExecutionRequest`.
- The returned `ExecutionResult` is enriched with invocation context and recorded into `ResearchRuntime.session`.
- The caller receives a flat return payload derived from the AS `ExecutionResult` rather than from the legacy `Observation` artifact.
- `OutputFormatter` now supports `ExecutionResult` directly.
- `AndroidManifest.xml` now exposes `IntentRouterActivity` for:
  - `researchos://...` deep links
  - `xlsformlab://...` deep links
  - `com.example.researchos.EXECUTE_METHOD` explicit intent action

## Example deep link

```text
researchos://execute?method=calibrated_scale&participant_id=P001&visit_id=V01&form_id=demo&operator_id=OP01&return=json&value=73&minimum=0&maximum=100&use_range=false
```

Expected behaviour:

1. ResearchOS creates/uses `participant/P001` as the invocation subject.
2. The calibrated scale method executes through the AS runtime.
3. The execution result is recorded into the in-memory ResearchOS graph.
4. A flat JSON result is returned to the caller.
5. The Graph panel can show the resulting observation/transformation under `participant/P001`.

## Example explicit Android extras

```text
Action: com.example.researchos.EXECUTE_METHOD
Extras:
  method = calibrated_scale
  participant_id = P001
  visit_id = V01
  form_id = demo
  operator_id = OP01
  return = json
  value = 73
  minimum = 0
  maximum = 100
  use_range = false
```

## Current limitation

Live hardware methods such as NFC still require the Android NFC device-service path to supply a live tag signal. The intent path now records context and returns graph-shaped execution results, but it cannot fabricate a live Android `Tag` handle from a plain ODK launch string.
