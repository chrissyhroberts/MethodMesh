# Drop-in instructions

Canonical delivery folder:

```text
fieldstats/
```

Copy that folder to:

```text
app/src/main/java/com/example/methodmesh/modules/fieldstats/
```

Do not add a central module registry entry. MethodMesh discovers `FieldStatsModule.kt` through the existing generated module-index mechanism.

Then run:

```bash
./gradlew :app:assembleDebug
```

Keep status at **Development** until the checks in `VALIDATION.md` are complete. Copy the text in `ROADMAP_NOTE.md` into the repository-level `000_Roadmap.md`.
