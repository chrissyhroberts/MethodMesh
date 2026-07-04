# External workflow runner patch

This patch separates the dashboard/debug UI from the production external-execution UI.

## New behaviour

External callers still launch:

`com.example.researchos.EXECUTE_METHOD`

The exported `IntentRouterActivity` now acts only as a result-preserving bridge. It starts `ExternalWorkflowActivity`, waits for its result, and relays the returned extras to the caller.

`ExternalWorkflowActivity` owns the generic external workflow:

1. parse caller request/context/action chain
2. show a focused screen for the current capability
3. capture data
4. show preview
5. allow retry or confirm
6. advance to next capability
7. show final return summary
8. return requested fields to caller as individual extras and/or JSON

## Supported action syntax

Single action:

`method=nfc.read`

Action chain:

`actions=nfc.read,identity.verify,gps.navigate_to_target`

Aliases currently map to canonical method IDs:

- `nfc.read` -> `nfc_tag_read`
- `identity.verify` / `fingerprint.verify` -> `admin_fingerprint_confirmation`
- `gps.navigate_to_target` / `gps.navigate` -> `gps_target_navigator`
- `scale.capture` -> `calibrated_scale`

## Return selection

The existing graph selector transport is preserved:

`returns=execution.id:execution_id,observation.nfc.uid:tag_uid`

The final return summary displays the fields before returning them to the calling app.

## Current implementation status

- NFC read has a focused capture screen: start capture, tap tag, retry, confirm.
- Fingerprint/device credential verification has a focused screen: start verification, retry, confirm.
- GPS target navigation uses the existing target-navigation UI inside a focused workflow step, then review/confirm.
- Other capabilities use a generic run/retry/confirm wrapper until they get dedicated capture screens.

## Next recommended refinement

Move each capability screen into a dedicated module-owned file and register it through a `CapabilityScreenRegistry`, so `ExternalWorkflowActivity` does not need a `when` block.
