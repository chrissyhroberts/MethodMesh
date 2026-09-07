# Weighted choice

**Method ID:** `chance.weighted.choose`  
**Module:** Chance (`chance`)  
**Lane:** Development  
**MethodMesh standard:** v1.05

## Purpose

Choose from labelled options in proportion to arbitrary positive numeric weights. Weights do not have to sum to 1 or 100.

## Canonical surface parity

This method is implemented once by the Chance module and is independently exposed for direct native launch, the generic dashboard capability surface, preset creation/execution and protocol composition. ODK/XLSForm invokes the same method ID and receives fields from the same declared output contract. There is no dashboard-only or ODK-only implementation.

## Native UX and Commit

The user supplies `Label:weight` options, deliberately chooses, watches a weighted wheel whose segment width corresponds to weight, sees the current result, then commits it. The selected result is tap-to-copy.

Recorded working results remain mutable until **Commit**. Commit freezes the already-generated canonical payload and reveals beef-first Share/Copy/Save/Done/New run actions on the same screen. Audit/JSON inclusion is opt-in. Useful displayed scalar/text results are tappable to copy their useful value.

## Presets and protocols

All canonical settings are declared through `MethodSetting`. Fixed preset values are hidden on native preset execution; declared runtime fields remain visible. A fully fixed native preset can start the operation directly where appropriate. Protocols call `chance.weighted.choose` as an independent step and receive the same canonical result; completion is returned to the protocol runner through the supplied MethodMesh callback.

## ODK/XLSForm

Non-interactive when ODK supplies weighted options/settings; executes and returns immediately. Inputs use the standard `input_*` projection. `input_payload_mode='FULL'` makes the generic transport return `methodmesh_full_json` in addition to requested flat fields. Return namespace projection, ClipData/URI grants (not used by this non-media capability) and caller closeout are owned by the shared transport rather than Chance.

Example: `example_odk_chance_weighted_choose.xlsx`.

## Settings / runtime inputs

- `weighted_items` - `Label:weight`, newline/`|`/`;` separated
- `rng_mode`
- `seed`
- `animation_mode`

Preset authoring may mark applicable settings as fixed or runtime. ODK may supply the same values with `input_` prefixes.

## Outputs

**Primary beef:** `weighted_result` / `weighted_selected_value`

Canonical option probabilities, selected index/weight/probability, total weight and RNG metadata remain available.

| Field | Role |
|---|---|
| `weighted_status` | status/diagnostic |
| `weighted_result` | primary |
| `weighted_options_json` | secondary |
| `weighted_selected_index` | secondary |
| `weighted_selected_value` | primary |
| `weighted_selected_weight` | secondary |
| `weighted_selected_probability` | secondary |
| `weighted_total_weight` | secondary |
| `weighted_rng_mode` | secondary |
| `weighted_seed` | audit/provenance |
| `weighted_seed_sha256` | audit/provenance |
| `weighted_rng_algorithm` | audit/provenance |
| `weighted_rng_algorithm_version` | audit/provenance |
| `weighted_generated_time_iso` | audit/provenance |
| `weighted_audit_json` | audit/provenance |
| `weighted_error` | status/diagnostic |

`weighted_audit_json`, RNG/seed fields, algorithm version and generated time.

`methodmesh_full_json` is a generic FULL-payload transport field rather than a Chance descriptor field; it contains the complete projected execution when requested.

## State, offline behavior and storage

The operation is local/offline. Relevant configuration, working result, animation/turn progress and committed screen state are saveable across ordinary activity recreation/orientation changes. Chance does not automatically create durable records or Downloads copies. Save is explicit. ODK owns its surrounding form persistence.

## Permissions and dependencies

No capability-specific Android permission, network service or third-party runtime dependency is required.

## Validation status

See `VALIDATION.md`. The v1.05 migration is retained in the Development lane until it is built and exercised in the full MethodMesh app.
