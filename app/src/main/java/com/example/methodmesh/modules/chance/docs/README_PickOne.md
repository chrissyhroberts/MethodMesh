# Pick one

**Method ID:** `chance.pick.one`  
**Module:** Chance (`chance`)  
**Lane:** Development  
**MethodMesh standard:** v1.05

## Purpose

Randomise a concealed arrangement, then let the human choose which face-down position to reveal. The system randomises the hidden mapping; it does not choose the user's position.

## Canonical surface parity

This method is implemented once by the Chance module and is independently exposed for direct native launch, the generic dashboard capability surface, preset creation/execution and protocol composition. ODK/XLSForm invokes the same method ID and receives fields from the same declared output contract. There is no dashboard-only or ODK-only implementation.

## Native UX and Commit

Chance lays out shuffled face-down cards. The user taps a position, that card flips, the selected value becomes the current result, and Commit freezes/returns it. Optional Reveal remaining changes only presentation because the complete concealed arrangement is already in the canonical payload. Built-ins include numbers, colours, suits, abstract marks and classic cards plus custom text.

Recorded working results remain mutable until **Commit**. Commit freezes the already-generated canonical payload and reveals beef-first Share/Copy/Save/Done/New run actions on the same screen. Audit/JSON inclusion is opt-in. Useful displayed scalar/text results are tappable to copy their useful value.

## Presets and protocols

All canonical settings are declared through `MethodSetting`. Fixed preset values are hidden on native preset execution; declared runtime fields remain visible. A fully fixed native preset can start the operation directly where appropriate. Protocols call `chance.pick.one` as an independent step and receive the same canonical result; completion is returned to the protocol runner through the supplied MethodMesh callback.

## ODK/XLSForm

Two canonical routes: supply `input_selected_position` for non-interactive execution, or omit it to launch the same polished concealed-card interaction; the human picks and Commit returns directly to ODK/protocol. Inputs use the standard `input_*` projection. `input_payload_mode='FULL'` makes the generic transport return `methodmesh_full_json` in addition to requested flat fields. Return namespace projection, ClipData/URI grants (not used by this non-media capability) and caller closeout are owned by the shared transport rather than Chance.

Example: `example_odk_chance_pick_one.xlsx`.

## Settings / runtime inputs

- `set_type`
- `item_count`
- `custom_items`
- `selected_position` - transport/runtime position; 0 means interactive human choice
- `reveal_remaining`
- `rng_mode`
- `seed`
- `animation_mode`

Preset authoring may mark applicable settings as fixed or runtime. ODK may supply the same values with `input_` prefixes.

## Outputs

**Primary beef:** `pick_result` / `pick_selected_value`

The full concealed arrangement, selected position, set metadata and RNG metadata remain available.

| Field | Role |
|---|---|
| `pick_status` | status/diagnostic |
| `pick_result` | primary |
| `pick_set_type` | secondary |
| `pick_item_count` | secondary |
| `pick_selected_position` | secondary |
| `pick_selected_value` | primary |
| `pick_arrangement_json` | secondary |
| `pick_reveal_remaining` | secondary |
| `pick_prepared_time_iso` | audit/provenance |
| `pick_rng_mode` | secondary |
| `pick_seed` | audit/provenance |
| `pick_seed_sha256` | audit/provenance |
| `pick_rng_algorithm` | audit/provenance |
| `pick_rng_algorithm_version` | audit/provenance |
| `pick_audit_json` | audit/provenance |
| `pick_error` | status/diagnostic |

`pick_audit_json`, prepared time, RNG/seed fields and algorithm version.

`methodmesh_full_json` is a generic FULL-payload transport field rather than a Chance descriptor field; it contains the complete projected execution when requested.

## State, offline behavior and storage

The operation is local/offline. Relevant configuration, working result, animation/turn progress and committed screen state are saveable across ordinary activity recreation/orientation changes. Chance does not automatically create durable records or Downloads copies. Save is explicit. ODK owns its surrounding form persistence.

## Permissions and dependencies

No capability-specific Android permission, network service or third-party runtime dependency is required.

## Validation status

See `VALIDATION.md`. The v1.05 migration is retained in the Development lane until it is built and exercised in the full MethodMesh app.
