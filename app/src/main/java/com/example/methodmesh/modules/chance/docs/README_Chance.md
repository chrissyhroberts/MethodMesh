# Chance

**Module ID:** `chance`  
**MethodMesh standard:** v1.05  
**Lane:** Development

Chance is the MethodMesh module for local randomisation, concealed choice and familiar tabletop random mechanisms. It is an offline-first module and does not require network access or third-party services.

## Canonical capabilities

| Capability | Method ID | Primary result |
|---|---|---|
| Dice | `dice.simulate` | roll/result summary |
| Coin toss | `coin.toss` | toss summary |
| Draw cards | `chance.cards.draw` | drawn card(s)/hands |
| Pick one | `chance.pick.one` | value behind the human-selected concealed position |
| Spinner | `chance.spinner.spin` | selected segment |
| Weighted choice | `chance.weighted.choose` | selected weighted option |

The historical method IDs `dice.simulate` and `coin.toss` are intentionally preserved. The four established `chance.*` IDs are also preserved. No v1.05 migration alias or replacement ID is introduced.

## Surface parity

The module exposes all six methods through `ChanceModule.as100Methods()`, all six native screens through `capabilityScreens()`, and canonical `MethodSetting` metadata through `capabilitySettings()`. Consequently the same individual methods remain discoverable for direct native use, dashboard capability launch, preset authoring/execution and protocol composition. The module does not implement a dashboard-private version of any operation.

The shared MethodMesh dashboard supplies the module presence from module/method metadata; Chance does not require shared dashboard special-casing. `iconKey = random` supplies the broad generic visual family.

ODK/XLSForm uses the same method IDs and output fields. Every declared field remains projectable by the generic MethodMesh transport. Example XLSForms under `docs/` demonstrate every capability and request `input_payload_mode='FULL'`, including `methodmesh_full_json` as the generic full execution payload.

## v1.05 native lifecycle

Recorded native operations use:

`configure/interact -> current result -> Commit -> Share / Copy / Save / Done / New run`

Randomisation itself remains a deliberate domain action (`ROLL`, `TOSS`, `DEAL`, `SPIN`, etc.). The resulting working value stays on the capability screen. **Commit freezes that generated canonical payload; it does not navigate to a generic results page.** Inputs are not silently mutated after Commit; `New run` returns to editable working state.

Useful displayed scalar/text results are tap-to-copy. Post-Commit Share/Copy/Save is beef-first. JSON/audit inclusion is opt-in and technical JSON remains behind progressive disclosure.

`coin.toss` also retains the established single-coin **Just toss** convenience mode. It is deliberately ephemeral/non-recording: the user can repeatedly toss and leave without committing a stored/returned run. Recorded coin mode follows the normal Commit lifecycle.

## Launch-origin closeout

Chance never navigates to a hard-coded destination. Its screens call the supplied MethodMesh completion/cancel callbacks. The shared host therefore owns v1.05 closeout routing:

- app/direct or native preset -> MethodMesh dashboard;
- widget -> Android desktop;
- ODK/external -> canonical payload back to caller;
- protocol -> protocol runner;
- schedule -> schedule machinery.

For ODK/protocol automatic-return calls, non-interactive operations execute and return without native setup or post-Commit export UI. `chance.pick.one` is the exception when no `selected_position` is supplied: MethodMesh must show the concealed cards so the human can choose a position, then Commit returns the result to the caller.

## Randomness and reproducibility

Default randomness is local `java.security.SecureRandom`. Fixed-seed mode uses the existing versioned deterministic algorithm for replay/testing. Animation never generates or changes outcomes: the canonical result is generated first and animation reveals it.

Dice provenance records primitive draws and rule operations, including rerolls/explosions. Card, pick, spinner and weighted-choice provenance records the primitive bounded draws used by the selection engine.

## State and persistence

Working configuration, generated values, animation progress, current multiplayer turn, concealed-pick preparation and committed screen state use saveable UI state so relevant work survives ordinary Android recreation/orientation changes. Chance has no durable repository and does not automatically archive outputs on Commit. Explicit **Save** writes only when requested by the user. External/ODK execution does not create an extra Chance archive.

## Multiplayer

Dice and Draw cards retain their existing multiplayer behavior.

- Dice: 1-20 players with one shared configuration; take-turns view for any count and around-table view up to four players.
- Draw cards: 1-20 players from one shared shuffled deck; take-turns private hands or around-table visible hands up to four players.

## Documentation

- `README_Dice.md`
- `README_CoinToss.md`
- `README_DrawCards.md`
- `README_PickOne.md`
- `README_Spinner.md`
- `README_WeightedChoice.md`
- `VALIDATION.md`
- `ROADMAP_NOTE.md`
- one `example_odk_*.xlsx` workbook per capability

There are no third-party runtime dependencies requiring attribution or additional permissions.
