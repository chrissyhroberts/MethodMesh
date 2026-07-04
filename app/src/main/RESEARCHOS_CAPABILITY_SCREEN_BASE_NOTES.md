# ResearchOS capability screen base refactor

This patch introduces a common production execution surface for externally-called ResearchOS capabilities.

## Added

- `transport/workflow/ui/CapabilityScreen.kt`
  - `CapabilityScreenSpec`
  - `CapabilityScreenContext`
  - `CapabilityScreenScaffold`

## Intent

The dashboard remains a developer/debug interface. External calls now use a consistent capability execution model:

1. Workflow runner receives an external request/action chain.
2. Each action is shown as a focused capability screen.
3. The capability body performs capture or review.
4. The shared scaffold presents:
   - step number
   - subject/entity context
   - capability id
   - result preview
   - Back / Start-Retry / Confirm / Cancel controls
5. Confirmed results are recorded to the ResearchOS graph.
6. Final summary resolves requested graph selectors and returns values to the caller.

## Current capability screens using the scaffold

- `nfc.read` / `nfc_tag_read`
- `identity.verify` / `admin_fingerprint_confirmation`
- `gps.navigate` / `gps_target_navigator`
- generic AS method fallback

## GPS note

GPS is still treated as target navigation, not a generic `get fix` action. A future `gps.get_fix` capability should be a separate screen and AS method.
