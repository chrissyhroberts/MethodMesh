# ResearchOS RIL Conformance Foundation

This patch makes RIL the canonical internal transport path for externally launched workflows.

## Changes

- Added `RilTransportAdapter`.
- Legacy Android extras such as `actions`, `subject`, `returns` and `return_mode` are now compiled into a RIL request before execution.
- Direct `ril`, `request` and `researchos_request` extras continue to be parsed as native RIL.
- Added a visible request summary to the external workflow screen showing:
  - source
  - parsed actions
  - parsed return selectors
  - return format
  - parser warnings
- Added lightweight RIL parser conformance smoke checks as executable examples.
- Improved inline `format json` handling for compact one-line RIL requests.

## Canonical flow

```text
Android extras / ODK appearance / RIL text
        ↓
RilTransportAdapter
        ↓
RilRequestParser
        ↓
ParsedLaunchConfig
        ↓
ExternalWorkflowRequest
        ↓
Workflow runner
```

The legacy transport shape remains supported, but it is no longer a separate internal execution path.
