# Capability screen base refactor

This patch formalises externally callable capabilities as focused workflow screens rather than dashboard panels.

## Added

- `transport/workflow/ui/CapabilityScreenContext`
- `transport/workflow/ui/CapabilityScreenSpec`
- `transport/workflow/ui/CapabilityScreenScaffold`

## Operational model

Each external workflow action is resolved to a `CapabilityScreenSpec`.

The workflow runner owns:

- action-chain sequencing
- back/cancel behaviour
- confirmed result recording into the ResearchOS graph
- final return summary
- Android result transport back to the caller

Each capability screen owns only:

- capability-specific capture UI
- retry/start capture behaviour
- previewing its current `ExecutionResult`
- confirming the result

## Current wired screens

- `nfc.read` / `nfc_tag_read`
- `identity.verify` / `admin_fingerprint_confirmation`
- `gps.navigate` / `gps_target_navigator`
- generic fallback for registered AS methods

## GPS note

GPS remains a target-navigation capability. This patch does not add a `gps.get_fix` capability.
