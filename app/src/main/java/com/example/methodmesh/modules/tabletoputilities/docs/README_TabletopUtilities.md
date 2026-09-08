# Tabletop Utilities

**Build-fix revision:** v0.1.2 removes an invalid Compose `weight` import and removes experimental `FlowRow` usage; see `BUILD_FIX_v0.1.2.md`.


**MethodMesh module:** `tabletoputilities`  
**Primary method:** `tabletop.state.manage`  
**Status:** Development  
**Module version:** 0.1.2

## What this capability does

Tabletop Utilities is a lightweight, offline, **stateful** MethodMesh package for tabletop RPGs and other games.

Its core abstraction is a persistent **game workspace**. A user can create separate workspaces such as:

- `D&D house rules`;
- `Friday Pathfinder`;
- `Family Scrabble`;
- `Warhammer campaign`.

Each workspace owns its own configuration and live state. The native game screen shows only utilities enabled for that game.

The v0.1 prototype includes:

- HP counters;
- temporary HP;
- EXP counters with larger quick modifiers;
- arbitrary resources and generic `+ / -` counters;
- timed or indefinite buffs/effects;
- round ticker;
- death-save counters;
- initiative order and current turn;
- player records and score counters;
- high-score records / personal best display;
- character cards;
- campaign/session start, notes and completion;
- append-only audit history;
- undo as an audited reversal;
- Dice Simulator launch through the public `dice.simulate` capability boundary.

The underlying model is deliberately game-system agnostic. It does not hard-code D&D attributes, Pathfinder rules, Scrabble scoring rules, or another specific ruleset into the core data structures.

## Architecture and module ownership

All capability-specific implementation lives in:

```text
app/src/main/java/com/example/methodmesh/modules/tabletoputilities/
```

The module does **not** require:

- a central module registry edit;
- shared dashboard special cases;
- a manifest change;
- a Room/SQLite Gradle dependency;
- a network service.

`TabletopUtilitiesModule.kt` exposes the module through the normal generated MethodMesh module index.

The module declares a dependency on:

```text
dicesimulator
```

and launches the public method:

```text
dice.simulate
```

It does not copy Dice Simulator parsing, RNG, animation or expression-engine internals.

## Persistent workspace folders

The user-facing “game folder” concept is also reflected in local storage.

Each workspace is stored under the application files directory as:

```text
files/tabletoputilities/workspaces/<workspace-id>/
├── workspace.json
└── audit.jsonl
```

`workspace.json` is the current renderable state snapshot.

`audit.jsonl` is an append-only sequence of state-change events. Ordinary undo does not remove an event; it appends a new event with `source=undo` and `reverses_event_id` pointing to the event being reversed.

A SHA-256 digest of the resulting workspace snapshot is recorded in each audit event. This is useful provenance but is **not** presented as a cryptographic attestation or tamper-proof ledger.

## Native workflow

### Workspace library

Opening **Tabletop utilities** normally shows a library of game cards.

The user can create a workspace, give it a name/ruleset label, and choose which utilities are enabled:

- Hit points
- Temporary HP
- Experience
- Resources
- Buffs / effects
- Death saves
- Initiative
- Generic counters
- Character cards
- Player scores
- Campaign / sessions

Disabled features do not occupy space on the play screen.

### Play screen

The workspace dashboard is intended to keep ordinary play on one screen.

Typical actions are one or two taps:

- `-1 / +1` HP;
- `-100 / -10 / +10 / +100` EXP;
- arbitrary resource/counter changes;
- death-save success/failure increments;
- effect on/off toggle;
- effect duration `- / +`;
- initiative `Next turn`;
- `Next round`;
- add session note;
- record player scores;
- launch Dice Simulator.

The current game state is persisted after every accepted state mutation.

### Round/effect interaction

Effects may be indefinite or have an integer duration in rounds.

When initiative wraps from the last entry to the first entry, the round increments automatically. `Next round` also increments the round explicitly.

At a round increment, an active round-based effect configured with automatic ticking changes:

```text
3 rounds -> 2 rounds
2 rounds -> 1 round
1 round  -> 0 rounds / inactive
```

The resulting workspace state is audited. The prototype currently represents the automatic effect changes through the resulting round-transition state rather than emitting one separate event per individual effect tick; separate per-effect auto-tick events are a Production follow-on.

### Undo

The dashboard exposes `↶ Undo`.

Undo does not erase history. Reversible actions currently include:

- counter adjust/set;
- effect toggle;
- effect duration change;
- death-save change;
- initiative score adjustment.

The repository locates the newest unreversed reversible event and applies its stored inverse operation. The reversal is itself appended to the audit log.

### Dice Simulator integration

The `🎲 Dice` action launches MethodMesh through its public Android capability intent with:

```text
method_id = dice.simulate
input_expression = d20
input_roll_count = 1
input_history_output = true
input_rng_mode = secure_random
input_animation_mode = fast
input_payload_mode = FULL
return_mode = flat
```

The Dice Simulator screen can then be used normally. When it returns `dice_result`, Tabletop Utilities appends a `dice_roll_linked` audit event. If `dice_audit_json` is returned it is preserved as dependency provenance in that audit event.

The default `d20` is simply the entry point into Dice Simulator, not a restriction on what can subsequently be rolled.

## Character cards

Characters are generic records containing:

- stable ID;
- name;
- optional linked player;
- notes.

State such as HP, temporary HP and EXP is held in linked counters rather than duplicated inside the character record. This avoids competing sources of truth.

When a character is created, enabled standard counters are created automatically:

- HP: starts at the supplied maximum;
- temporary HP: starts at 0;
- EXP: starts at 0.

## Players, scores and high scores

Players are distinct from characters.

If scoring is enabled, adding a player creates a linked score counter. `Record scores` appends immutable score records containing player, score, time and active session ID where available.

The dashboard displays a player's recorded personal best and the highest recorded scores.

No game-specific scoring algorithm is assumed.

## Sessions / campaign records

When enabled, a workspace can:

1. start a session;
2. append session notes;
3. finish the session.

The session record stores its own stable ID, start/end timestamps and notes. Audit events occurring during the session include the active session ID.

Because counter, initiative, effect, dice and score operations are audited, useful session provenance exists even when the user writes few or no free-text notes.

## Method: `tabletop.state.manage`

The native dashboard and external state actions share one stable public method boundary.

### Operations

| `operation` | Purpose |
|---|---|
| `dashboard` | Normal native workspace-library/play-screen workflow. |
| `snapshot` | Return current state without mutating it. |
| `counter_adjust` | Add signed `value` to counter `target_id`. |
| `counter_set` | Set counter `target_id` to integer `value`. |
| `session_start` | Start a session; `note` is used as its name, or a default name is generated. |
| `session_note` | Append `note` to the active session. |
| `session_finish` | Finish the active session. |
| `initiative_next` | Advance one initiative turn; wrapping advances the round. |
| `round_next` | Advance the round explicitly. |
| `score_record` | Store immutable score records for configured player score counters. |

The richer native UI can perform additional module-owned mutations such as adding characters/effects/initiative entries. Those are intentionally not all promoted to external settings in v0.1.

## Input settings

All settings are declared by `TabletopUtilitiesModule.capabilitySettings()`.

| Setting | Type | Default | Meaning |
|---|---|---:|---|
| `operation` | choice | `dashboard` | Dashboard or one supported external/preset operation. |
| `workspace_id` | text | blank | Stable workspace ID. Preferred for presets/ODK. |
| `workspace_name` | text | blank | Optional case-insensitive name lookup when ID is unavailable. |
| `target_id` | text | blank | Counter/entity ID for target operations. |
| `value` | integer | 0 | Signed delta or set value. |
| `note` | text | blank | Session name or note depending on operation. |

Fixed preset values are hidden with `CapabilityScreenContext.settingShouldBeShown(...)`. Runtime values remain editable in the native preset panel.

## Output fields

| Field | Role | Meaning |
|---|---|---|
| `tabletop_result` | **main/core** | Compact human-readable workspace summary or mutation summary. |
| `tabletop_workspace_id` | core | Stable workspace ID. |
| `tabletop_workspace_name` | core | Game/campaign name. |
| `tabletop_operation` | core | Operation performed. |
| `tabletop_round` | core | Current round. |
| `tabletop_current_turn` | core | Current initiative entry when available. |
| `tabletop_active_session` | core | Current session name when one is active. |
| `tabletop_event_id` | audit/core | Event created by a mutation, blank for a pure snapshot. |
| `tabletop_state_json` | structured | Complete current workspace snapshot. |
| `tabletop_audit_json` | **audit/full** | Method/version metadata, current event and recent audit events. |
| `tabletop_status` | status | `succeeded` or `failed`. |
| `tabletop_error` | error | Human-readable failure reason. |

## Main result and sharing

The main native result is:

```text
tabletop_result
```

Examples:

```text
D&D house rules · Session 18 · Round 7 · 4 characters
```

or:

```text
HP: 37 → 33
```

The long state/audit JSON fields remain secondary detail. The module does not make JSON the only useful output.

The normal share action therefore shares the compact core result rather than dumping the full game history.

The capability does not auto-export a result file. Persistent workspace state is the requested function of this stateful capability and is stored locally; explicit MethodMesh output export remains separate.

## Native preset workflow

Tabletop Utilities uses MethodMesh's generic preset machinery.

Useful preset examples include:

- **Current campaign state** — fixed `operation=snapshot`, fixed `workspace_id`;
- **Take 1 HP** — fixed `operation=counter_adjust`, fixed HP `target_id`, fixed `value=-1`;
- **Award EXP** — fixed operation/workspace/EXP counter with `value` kept as a runtime input;
- **Next initiative turn** — fixed `operation=initiative_next`, fixed workspace;
- **Add session note** — fixed `operation=session_note`, runtime `note`.

During native preset execution, values fixed in the preset are hidden and runtime fields remain editable.

## ODK / XLSForm workflow

The module includes:

```text
docs/example_odk_TabletopUtilities.xlsx
```

ODK calls are made through XLSForm **groups**, not individual questions.

Representative snapshot call:

```text
com.example.methodmesh.EXECUTE_METHOD(
  method_id='tabletop.state.manage',
  input_operation='snapshot',
  input_workspace_id=${workspace_id},
  input_payload_mode='FULL',
  return_mode='flat'
)
```

Representative counter adjustment:

```text
com.example.methodmesh.EXECUTE_METHOD(
  method_id='tabletop.state.manage',
  input_operation='counter_adjust',
  input_workspace_id=${workspace_id},
  input_target_id=${counter_id},
  input_value=${delta},
  input_payload_mode='FULL',
  return_mode='flat'
)
```

The example workbook receives both compact return fields and full state/audit JSON.

ODK does not depend on the visual game dashboard.

## Audit model

Each state mutation records at least:

- event ID;
- workspace ID;
- active session ID where available;
- timestamp;
- event type;
- compact summary;
- target entity ID where relevant;
- source (`native`, preset/external transport, `undo`, or dependency);
- before/after value where relevant;
- inverse mutation JSON for reversible events;
- reversed-event ID when undoing;
- optional external reference;
- SHA-256 digest of the resulting workspace snapshot.

Examples include:

```text
workspace_created
counter_created
counter_adjusted
counter_set
player_created
character_created
effect_created
effect_enabled
effect_disabled
effect_duration_changed
death_save_changed
initiative_entry_added
initiative_score_changed
initiative_started
turn_advanced
round_advanced
session_started
session_note
session_finished
scores_recorded
dice_roll_linked
workspace_archived
```

## Permissions and Android services

**Runtime permissions:** none.

The module uses:

- local application-private files;
- ordinary Android activity result handling to invoke the public Dice Simulator boundary.

No location, camera, microphone, Bluetooth, NFC or storage permission is requested.

## Offline / online behaviour

**Fully offline.**

Tabletop game state is not transmitted to a cloud service. The module itself performs no network request or telemetry upload.

Dice Simulator is also expected to run locally through its own public capability contract.

## State and orientation

Persistent game state lives in module-owned files and therefore survives activity/app recreation.

Native navigation state such as selected workspace, open dialogs, history view and encoded result values uses `rememberSaveable` where appropriate.

The primary `ExecutionResult` is reconstructed from saved string fields after configuration change instead of requiring the complex object itself to be saveable.

Device/emulator rotation still requires full MethodMesh validation before Production.

## Pure-logic validation performed in this handoff

The following files were compiled independently with Kotlin/JVM 1.9.0:

```text
TabletopUtilitiesModels.kt
TabletopUtilitiesStateEngine.kt
```

A smoke test verified:

1. creating a character with HP maximum 52;
2. applying `-15` HP gives 37;
3. adding a 3-round `Bless` effect;
4. adding two initiative entries;
5. starting initiative;
6. advancing through the last entry back to the first;
7. round changes from 1 to 2;
8. the effect automatically changes from 3 to 2 rounds.

Observed smoke-test result:

```text
HP=37; round=2; bless=2
```

This is not a substitute for an Android build or device testing.

## Known limitations — Development

1. The complete Android `./gradlew :app:assembleDebug` build has not been run in this packaging environment.
2. Android/Compose compile compatibility must be checked against the exact checkout receiving this drop-in.
3. Native workspace/library/dashboard behaviour needs device/emulator testing in portrait and landscape.
4. Native preset creation and fixed/runtime field hiding need end-to-end validation.
5. `example_odk_TabletopUtilities.xlsx` needs an ODK Central/Collect round trip.
6. The Dice Simulator launch/return path needs device validation with the separate `dicesimulator` drop-in installed.
7. Effect duration is round-based in v0.1; turn-start/turn-end/custom tick points are not yet configurable.
8. Round-driven effect changes are represented in the resulting round event/state; separate per-effect `effect_auto_tick` audit events are not yet emitted.
9. Character/player editing, removal and re-ordering are intentionally minimal in the prototype.
10. Initiative entries can be added and scores adjusted, but v0.1 has no drag-and-drop ordering UI.
11. Scoring records high scores but does not yet provide game-specific tie-break logic or separate named leaderboard variants.
12. Workspace archive hides a game from the normal library but v0.1 does not include an archive-browser/restore screen.
13. State snapshot and audit JSON are stored as ordinary local files; this is auditable application history, not tamper-proof attestation.
14. Snapshot replacement and audit append are two local file operations; sudden process/device failure between them is not currently handled as a transactional journal commit.
15. Full history export/import and multi-device synchronisation are not included.
16. The current MethodMesh module interface has no explicit application-context initialisation hook. The repository is initialised by the capability screen. Direct headless invocation of `As100TabletopUtilitiesMethod.execute()` before that boundary fails explicitly rather than silently inventing state. Validate scheduled/headless runtime behaviour before Production.

## Production checklist

Keep this capability **Development** until at least:

1. `./gradlew :app:assembleDebug` passes in the complete current MethodMesh repository.
2. Focused repository unit tests are added for state transitions, bounds, round wrapping, effect ticks, JSON round trips and undo selection.
3. Workspace creation/reopen survives full app restart.
4. Portrait/landscape recreation preserves selected workspace and result state.
5. HP/temp-HP/EXP/resource/generic counter min/max behaviour is verified.
6. Death-save 0-3 bounds are verified.
7. Initiative sorting, current turn, wrap and round increments are verified.
8. Timed effects decrement/deactivate correctly.
9. Undo appends a reversal and never deletes the original event.
10. Session start/note/finish and score-history behaviour are verified.
11. Native preset fixed/runtime settings behave correctly.
12. ODK Collect round-trips the example form and return fields.
13. Main sharing sends `tabletop_result` rather than the verbose state/audit JSON.
14. Dice Simulator integration uses `dice.simulate` and returned provenance is linked correctly.
15. Archive behaviour and audit retention are verified.
16. Repository `000_Roadmap.md` is updated from the supplied module-owned roadmap note.

## Canonical delivery folder

Copy exactly:

```text
app/src/main/java/com/example/methodmesh/modules/tabletoputilities/
```

No central module registration or shared dashboard modification should be added.
