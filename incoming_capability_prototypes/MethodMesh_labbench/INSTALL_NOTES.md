# MethodMesh Lab Bench module

This folder is laid out relative to the MethodMesh repository root.

Copy/merge the contained `app/` directory into the MethodMesh repository. The module is self-contained under:

`app/src/main/java/com/example/methodmesh/modules/labbench/`

Unit tests live under:

`app/src/test/java/com/example/methodmesh/modules/labbench/`

The module exposes 13 Development methods:

- `labbench.dashboard`
- `labbench.dilution`
- `labbench.molar_solution`
- `labbench.reconstitute`
- `labbench.serial_dilution`
- `labbench.master_mix`
- `labbench.centrifuge`
- `labbench.concentration`
- `labbench.nucleic_acid`
- `labbench.cell_dilution`
- `labbench.hemocytometer`
- `labbench.aliquot`
- `labbench.percent_solution`

No shared MethodMesh UI or central registry edit is intended; `LabBenchModule.kt` is designed for normal module discovery.

## Validation performed in this package build

- Pure calculation engine compiled with Kotlin against minimal compile stubs.
- Representative calculation runner executed successfully for dilution, molar solution, reconstitution, centrifuge conversion, cell dilution, hemocytometer, aliquot planning and percent solution.
- 13 XLSForm workbooks were opened with `artifact_tool`; all contain `survey`, `choices`, and `settings` sheets and have `settings.form_title` matching the canonical method ID.
- The repository JUnit test source contains tests across all 12 calculators and important invalid-input cases.

## Still required in the real MethodMesh checkout

Run:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Then test native dashboard, native preset, ODK round trip and protocol execution on Android before promoting any method from Development.
