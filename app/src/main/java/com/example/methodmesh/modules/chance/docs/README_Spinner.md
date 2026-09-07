# Spinner

**Method ID:** `chance.spinner.spin`  
**Module:** Chance (`chance`)  
**Lane:** Development  
**MethodMesh standard:** v1.05

## Purpose

Choose uniformly from labelled wheel segments and reveal the result with a spinner animation.

## Canonical surface parity

This method is implemented once by the Chance module and is independently exposed for direct native launch, the generic dashboard capability surface, preset creation/execution and protocol composition. ODK/XLSForm invokes the same method ID and receives fields from the same declared output contract. There is no dashboard-only or ODK-only implementation.

## Native UX and Commit

The user supplies labels, deliberately spins, watches the wheel decelerate to the already-generated outcome, sees the current result, then commits it. The selected result is tap-to-copy.

Recorded working results remain mutable until **Commit**. Commit freezes the already-generated canonical payload and reveals beef-first Share/Copy/Save/Done/New run actions on the same screen. Audit/JSON inclusion is opt-in. Useful displayed scalar/text results are tappable to copy their useful value.

## Presets and protocols

All canonical settings are declared through `MethodSetting`. Fixed preset values are hidden on native preset execution; declared runtime fields remain visible. A fully fixed native preset can start the operation directly where appropriate. Protocols call `chance.spinner.spin` as an independent step and receive the same canonical result; completion is returned to the protocol runner through the supplied MethodMesh callback.

## ODK/XLSForm

Non-interactive when ODK supplies the labels/settings; executes and returns immediately. Inputs use the standard `input_*` projection. `input_payload_mode='FULL'` makes the generic transport return `methodmesh_full_json` in addition to requested flat fields. Return namespace projection, ClipData/URI grants (not used by this non-media capability) and caller closeout are owned by the shared transport rather than Chance.

Example: `example_odk_chance_spinner_spin.xlsx`.

## Settings / runtime inputs

- `items` - 2-60 labels separated by newline, `|` or `;`
- `rng_mode`
- `seed`
- `animation_mode`

Preset authoring may mark applicable settings as fixed or runtime. ODK may supply the same values with `input_` prefixes.

## Outputs

**Primary beef:** `spinner_result` / `spinner_selected_value`

Segment list, selected index and RNG metadata remain available.

| Field | Role |
|---|---|
| `spinner_status` | status/diagnostic |
| `spinner_result` | primary |
| `spinner_items_json` | secondary |
| `spinner_selected_index` | secondary |
| `spinner_selected_value` | primary |
| `spinner_rng_mode` | secondary |
| `spinner_seed` | audit/provenance |
| `spinner_seed_sha256` | audit/provenance |
| `spinner_rng_algorithm` | audit/provenance |
| `spinner_rng_algorithm_version` | audit/provenance |
| `spinner_generated_time_iso` | audit/provenance |
| `spinner_audit_json` | audit/provenance |
| `spinner_error` | status/diagnostic |

`spinner_audit_json`, RNG/seed fields, algorithm version and generated time.

`methodmesh_full_json` is a generic FULL-payload transport field rather than a Chance descriptor field; it contains the complete projected execution when requested.

## State, offline behavior and storage

The operation is local/offline. Relevant configuration, working result, animation/turn progress and committed screen state are saveable across ordinary activity recreation/orientation changes. Chance does not automatically create durable records or Downloads copies. Save is explicit. ODK owns its surrounding form persistence.

## Permissions and dependencies

No capability-specific Android permission, network service or third-party runtime dependency is required.

## Validation status

See `VALIDATION.md`. The v1.05 migration is retained in the Development lane until it is built and exercised in the full MethodMesh app.
