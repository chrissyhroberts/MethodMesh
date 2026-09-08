# ODK/Kobo template asset generator

The ODK Forms screen runs inside the APK. Module `docs/` folders are source files and are **not automatically packaged by Android**. Therefore module-owned XLSForms are projected into Android assets at build time.

## Source of truth

The generator scans:

```text
src/main/java/com/example/methodmesh/modules/<module>/docs/**/*.xlsx
```

It includes canonical `example_odk*.xlsx` workbooks and, as a safety net, other `.xlsx` files that structurally look like XLSForms. A module may own one form or many.

## Output packaged into the APK

```text
src/main/assets/methodmesh/odk_templates/index.json
src/main/assets/methodmesh/odk_templates/<module>/<form>.xlsx
```

`index.json` includes form metadata plus per-form validation findings and a batch validation summary.

## Validation

The generator always runs MethodMesh structural/naming lint. If `xls2xform` is available it also runs pyxform + ODK Validate for every form:

```bash
python3 -m pip install pyxform
```

ODK Validate coverage is optional but strongly recommended for the MethodMesh build machine. If it is unavailable, the Android Forms library explicitly says that validation is static-only.

## One-time build integration

From the **app-module root** (where `build.gradle.kts` lives):

```bash
python3 src/main/java/com/example/methodmesh/ui/integration/install_odk_form_build_hook.py
```

Equivalent manual line in `build.gradle.kts`:

```kotlin
apply(from = "src/main/java/com/example/methodmesh/ui/integration/odk-template-assets.gradle.kts")
```

Every app build then regenerates the catalogue before Android merges assets. You can force it with:

```bash
./gradlew generateMethodMeshOdkTemplateAssets
```

A successful task prints both discovery and validation counts.
