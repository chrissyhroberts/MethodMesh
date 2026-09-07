# Draw cards

**Method ID:** `chance.cards.draw`  
**Module:** Chance (`chance`)  
**Lane:** Development  
**MethodMesh standard:** v1.05

## Purpose

Draw/deal cards without replacement from a standard 52-card deck, optionally including jokers, including shared-deck multiplayer dealing.

## Canonical surface parity

This method is implemented once by the Chance module and is independently exposed for direct native launch, the generic dashboard capability surface, preset creation/execution and protocol composition. ODK/XLSForm invokes the same method ID and receives fields from the same declared output contract. There is no dashboard-only or ODK-only implementation.

## Native UX and Commit

The user sets cards per player and player presentation, deals, watches the stylised deck/card reveal, sees the current hand/deal, then commits. Cards wrap to available width. Take-turns multiplayer uses Hide & pass between private hands; around-table mode supports up to four visible hands. Face-up cards and hand text are tap-to-copy.

Recorded working results remain mutable until **Commit**. Commit freezes the already-generated canonical payload and reveals beef-first Share/Copy/Save/Done/New run actions on the same screen. Audit/JSON inclusion is opt-in. Useful displayed scalar/text results are tappable to copy their useful value.

## Presets and protocols

All canonical settings are declared through `MethodSetting`. Fixed preset values are hidden on native preset execution; declared runtime fields remain visible. A fully fixed native preset can start the operation directly where appropriate. Protocols call `chance.cards.draw` as an independent step and receive the same canonical result; completion is returned to the protocol runner through the supplied MethodMesh callback.

## ODK/XLSForm

Non-interactive when ODK supplies draw/player settings. A multiplayer call is one shared-deck round-robin deal, not independent decks. Inputs use the standard `input_*` projection. `input_payload_mode='FULL'` makes the generic transport return `methodmesh_full_json` in addition to requested flat fields. Return namespace projection, ClipData/URI grants (not used by this non-media capability) and caller closeout are owned by the shared transport rather than Chance.

Example: `example_odk_chance_cards_draw.xlsx`.

## Settings / runtime inputs

- `draw_count` - cards per player (one-player: cards to draw)
- `player_count` - 1-20
- `player_mode` - take turns/around table
- `include_jokers`
- `rng_mode`
- `seed`
- `animation_mode`

Preset authoring may mark applicable settings as fixed or runtime. ODK may supply the same values with `input_` prefixes.

## Outputs

**Primary beef:** `card_result`

Flat cards, structured card JSON, player hands, deck configuration and RNG metadata remain available.

| Field | Role |
|---|---|
| `card_status` | status/diagnostic |
| `card_result` | primary |
| `card_cards_csv` | secondary |
| `card_cards_json` | secondary |
| `card_draw_count` | secondary |
| `card_player_count` | secondary |
| `card_cards_per_player` | secondary |
| `card_player_hands_json` | secondary |
| `card_include_jokers` | secondary |
| `card_rng_mode` | secondary |
| `card_seed` | audit/provenance |
| `card_seed_sha256` | audit/provenance |
| `card_rng_algorithm` | audit/provenance |
| `card_rng_algorithm_version` | audit/provenance |
| `card_generated_time_iso` | audit/provenance |
| `card_audit_json` | audit/provenance |
| `card_error` | status/diagnostic |

`card_audit_json`, RNG/seed fields, algorithm version and generated time.

`methodmesh_full_json` is a generic FULL-payload transport field rather than a Chance descriptor field; it contains the complete projected execution when requested.

## State, offline behavior and storage

The operation is local/offline. Relevant configuration, working result, animation/turn progress and committed screen state are saveable across ordinary activity recreation/orientation changes. Chance does not automatically create durable records or Downloads copies. Save is explicit. ODK owns its surrounding form persistence.

## Permissions and dependencies

No capability-specific Android permission, network service or third-party runtime dependency is required.

## Validation status

See `VALIDATION.md`. The v1.05 migration is retained in the Development lane until it is built and exercised in the full MethodMesh app.
