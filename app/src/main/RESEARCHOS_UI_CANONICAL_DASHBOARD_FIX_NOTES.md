# ResearchOS UI canonical dashboard fix

## Problem addressed

The dashboard had drifted away from the architecture/specification boundary:

- canonical AS methods were shown in the module/debug inventory;
- legacy method cards were also shown as primary dashboard items;
- capabilities with focused AS screens, especially AS-only DCE capabilities, were described but not runnable from the dashboard;
- there were two competing `CapabilityScreenSpec` contracts under `transport.workflow` and `transport.workflow.ui`.

This produced duplicate presentation for migrated capabilities and loss of usable UI for capabilities that had no legacy `MethodCard`.

## Changes

- Rebuilt `HomeScreen` around the canonical AS method registry.
- Added a single `Capabilities` section with one card per canonical AS method.
- Capability cards now expose metadata, RIL bindings, examples and a dashboard runner.
- If a capability has a focused screen, the dashboard renders that screen directly.
- If a capability has no focused screen, the dashboard uses a generic AS method runner.
- Confirmed dashboard results are recorded back into the `ResearchRuntime` graph.
- Added a `Knowledge graph state` section for graph counts and recent observations.
- Grouped calibration and device signals under a single `Device services` section.
- Moved old legacy `MethodCard` UI into a collapsed `Legacy UI shells` compatibility section.
- Removed the obsolete duplicate `transport.workflow.CapabilityScreenSpec` contract; the single canonical UI contract is now `transport.workflow.ui.CapabilityScreenSpec`.

## Architectural intent

The dashboard is now aligned with the spec principle that modules register Methods, Device Services and presentation components through stable architectural interfaces, without dashboard-specific capability code. Legacy UI remains available during migration but no longer competes with canonical capabilities as the primary UI.
