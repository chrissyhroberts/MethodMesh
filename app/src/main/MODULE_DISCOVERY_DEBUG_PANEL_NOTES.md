# Module discovery and debug panel update

This patch keeps the module model specification-driven and implementation-oriented:

- the runtime discovers `ResearchOSModule` objects from `com.example.researchos.modules.*`
- module-owned RIL bindings remain the route by which external intents can address capabilities
- module-owned examples and capability summaries are now available to the debug dashboard
- the dashboard no longer depends only on legacy `MethodRegistry` categories, so AS-only modules such as discrete choice experiments are visible

## Debug dashboard

The main debug screen now includes an **Installed capability modules** panel. For each discovered module it shows:

- module id and display name
- short module summary
- declared AS capabilities
- whether each capability has an external workflow screen
- graph outputs and output field counts
- RIL bindings owned by the module
- example RIL requests owned by the module

## Choice experiment module

`modules/choiceexperiment` now contributes its own dashboard-facing metadata and examples for:

- `dce.pairwise`
- `dce.maxdiff`
- `dce.ranking`
- `dce.points`
- `dce.conjoint`

No choice-experiment-specific UI code was added to the dashboard. The dashboard renders generic module metadata exposed through the module contract.
