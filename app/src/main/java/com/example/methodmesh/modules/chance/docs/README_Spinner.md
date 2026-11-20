# Spinner

**Method ID:** `chance.spinner.spin`  
**Module:** Chance (`chance`)  
**Lane:** Development  
**Capability version:** 0.3.0
**MethodMesh standard:** v1.31

## Purpose

Choose uniformly from labelled wheel segments and reveal the result on a full-screen, high-contrast wheel. Optionally remove each landed winner from subsequent draws.

## Canonical surface parity

This method is implemented once by the Chance module and is independently exposed for direct native launch, the generic dashboard capability surface, preset creation/execution and protocol composition. ODK/XLSForm invokes the same method ID and receives fields from the same declared output contract. There is no dashboard-only or ODK-only implementation.

## Native UX and Commit

The user supplies labels, opens the spinner, deliberately spins, watches the wheel decelerate to the already-generated outcome, sees the current result, then optionally commits it. The selected result is large and tap-to-copy.

The spinner requests the existing immersive capability host. It has no 300dp wheel-size cap, uses opaque bold segment colours with separately selected light/dark labels, and reflows side-by-side in landscape. Segment label/background pairs are regression-tested at a minimum 4.5:1 contrast ratio. The current winner is shown separately in large text; wheel labels may be abbreviated and dense wheels use segment numbers. The screen stays awake while the operational spinner is open and restores the previous keep-awake state when closed.

**Remove winner after each spin** is off by default. When enabled, the next pool loses exactly the selected segment when the animation lands, not when Commit is pressed. The landed wheel/result remain visible until the next spin. Duplicate labels are distinct segments, so landing on one removes only that occurrence. A final single label can be drawn; an exhausted pool disables further spins. **Restore all** explicitly starts the whole roster again, and **Settings** starts a fresh editable session. Depleting a session never overwrites the authored roster or saved preset.

Recorded working results remain mutable until **Commit**. Commit freezes the already-generated canonical payload and reveals beef-first Share/Copy/Save/Done/New run actions on the same screen. Audit/JSON inclusion is opt-in. Useful displayed scalar/text results are tappable to copy their useful value.

## Presets and protocols

All canonical settings are declared through `MethodSetting`. Fixed preset values are hidden on native preset execution; declared runtime fields remain visible. A fully fixed native preset can start the operation directly where appropriate. Protocols call `chance.spinner.spin` as an independent step and receive the same canonical result; completion is returned to the protocol runner through the supplied MethodMesh callback.

## ODK/XLSForm

Non-interactive when ODK supplies the labels/settings; executes and returns immediately. Inputs use the standard `input_*` projection. `input_payload_mode='FULL'` makes the generic transport return `methodmesh_full_json` in addition to requested flat fields. Return namespace projection, ClipData/URI grants (not used by this non-media capability) and caller closeout are owned by the shared transport rather than Chance.

Each invocation is one canonical draw, not a hidden persistent session. With `input_remove_after_spin='true'`, `spinner_remaining_items_json` returns the ordered pool for a caller's next invocation. The caller must supply those remaining labels as `input_items` for that next call. Empty explicit input fails rather than silently restoring the default Yes/No wheel. External automatic-return calls do not display or wait for native animation/Commit.

Example: `example_odk_chance_spinner_spin.xlsx`.

## Settings / runtime inputs

- `items` - 2-60 labels to start, separated by newline, `|` or `;`; one remaining label is accepted in removal mode
- `remove_after_spin` - boolean, default `false`
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
| `spinner_remove_after_spin` | secondary; whether this draw removes its winner |
| `spinner_remaining_items_json` | secondary; ordered labels for the next draw |
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

The operation is local/offline. Configuration, remaining pool, animation progress and screen state are saveable across ordinary activity recreation/orientation changes. The exact generated execution is retained in the existing Activity-scoped store, so Commit, audit and Done use the same execution through rotation. If that execution is lost after restart, the screen requires an explicit new draw/session instead of fabricating a replacement envelope from saved values. This is not durable process-death recovery. Chance does not automatically create durable records or Downloads copies. Save is explicit. ODK owns its surrounding form persistence.

## Permissions and dependencies

No capability-specific Android permission, network service or third-party runtime dependency is required.

## Validation status

See `VALIDATION.md`. The v1.05 migration is retained in the Development lane until it is built and exercised in the full MethodMesh app.
