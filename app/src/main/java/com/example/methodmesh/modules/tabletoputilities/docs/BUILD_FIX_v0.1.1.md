# Tabletop Utilities v0.1.1 build-fix note

This revision replaces the original v0.1.0 prototype drop-in.

## Compile-surface fixes

- Removed the explicit `androidx.compose.foundation.layout.weight` import. `Modifier.weight(...)` is a `RowScope`/`ColumnScope` member extension and existing MethodMesh Compose screens use it without that import.
- Removed `FlowRow` and the file-level `ExperimentalLayoutApi` opt-in. The dashboard now uses ordinary `Row`/`Column` layouts already used throughout MethodMesh.
- Kept the module self-contained: no Gradle dependency, manifest, shared dashboard, registry or generated-index edits are required.
- Method version bumped to `0.1.1`.

## Validation status

The pure state engine remains independently compilable and smoke-tested. A complete `./gradlew :app:assembleDebug` cannot be executed in this packaging runtime because the full MethodMesh checkout and Android dependency resolution are not available here. Under the capability-writing guide this therefore remains Development until the build is run in the real repository.

If the complete Android build still reports a compiler error, preserve the first compiler error and file/line: the next correction should be based on that concrete repository build output rather than inferred API compatibility.
