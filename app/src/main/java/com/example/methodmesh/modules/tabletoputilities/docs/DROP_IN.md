# Drop-in installation

Copy this entire `tabletoputilities` folder to:

```text
app/src/main/java/com/example/methodmesh/modules/tabletoputilities/
```

Do not scatter the files elsewhere.

No central module registration edit is required. `TabletopUtilitiesModule.kt` follows the generated module-discovery convention.

No Android manifest change is required for this prototype.

No new Gradle dependency is required. Persistence uses application-private JSON/JSONL files and `org.json` already available to Android code.

The module declares a public dependency on:

```text
dicesimulator
```

for `dice.simulate`. Install the separate Dice Simulator drop-in if it is not already present. Tabletop Utilities does not copy its internals.

After copying:

```bash
./gradlew :app:assembleDebug
```

Recommended Development checks:

1. create `D&D house rules` with HP, temp HP, EXP, effects, death saves, initiative, characters and sessions;
2. add a 52-HP character and apply `-15` HP;
3. close/reopen the app and confirm 37 HP remains;
4. add a 3-round effect and confirm round advancement decrements it;
5. check `↶ Undo` creates a reversal while the original audit row remains;
6. create a scoring workspace, add players, adjust scores and record scores twice; verify personal bests;
7. start/note/finish a session and inspect History;
8. launch `🎲 Dice`, roll through Dice Simulator and confirm a `dice_roll_linked` audit event;
9. rotate during configuration and after selecting a workspace;
10. create a native preset for one `counter_adjust` and confirm fixed settings disappear;
11. import `docs/example_odk_TabletopUtilities.xlsx` into ODK Central/Collect and test snapshot and counter adjustment;
12. confirm normal sharing exposes `tabletop_result`, not the full JSON history.

`docs/ROADMAP_NOTE.md` contains the suggested repository roadmap entry because the canonical drop-in deliberately does not edit files outside its module folder.
