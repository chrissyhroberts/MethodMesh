# ODK template library integration

The v1.05 UI has a first-class **ODK forms** destination. The library is intentionally separate from the installed-form launcher: it is a catalogue of **module-owned XLSForm design templates** that can be searched, shared, saved, and optionally deployed to a selected ODK Central test App User.

## Ownership rule

Each module continues to own every XLSForm it provides under that module's `docs/` directory.

A module may own **one form or many forms**. There is no one-form-per-module assumption.

Examples:

```text
modules/compass/docs/
└── example_odk_Compass.xlsx

modules/calibratedscale/docs/
├── example_odk_calibrated_scale.xlsx
├── example_odk_calibrated_scale_MinMax.xlsx
├── example_odk_calibrated_scale_Range.xlsx
└── example_odk_calibrated_scale_Vertical.xlsx

modules/attestation/docs/
├── example_odk_attestation.anchor_bundle.xlsx
├── example_odk_attestation.create_Fingerprint.xlsx
├── example_odk_attestation.create_Nfc.xlsx
├── example_odk_attestation.create_Password.xlsx
├── example_odk_attestation.create_Pin.xlsx
└── example_odk_attestation.create_Qr.xlsx
```

The source-of-truth file stays with the module. Do not create a second hand-maintained form library.

## Generator included in this UI package

`ui/integration/generate_odk_template_assets.py` performs the projection.

From the app-module root:

```bash
python3 src/main/java/com/example/methodmesh/ui/integration/generate_odk_template_assets.py
```

It scans every canonical:

```text
src/main/java/com/example/methodmesh/modules/*/docs/example_odk*.xlsx
```

and also picks up framework-owned `docs/example_odk*.xlsx` examples outside `modules/` (for example the scheduler).

For each XLSForm it:

1. creates a distinct catalogue entry;
2. copies the XLSX into the packaged ODK-template asset tree;
3. calculates SHA-256;
4. reads `form_title`, `form_id` and `version` from the XLSForm `settings` sheet where available;
5. records the original module-relative source path.

The generator uses only the Python standard library and does not require `openpyxl`.

## Generated projection

```text
src/main/assets/methodmesh/odk_templates/index.json
src/main/assets/methodmesh/odk_templates/<module_id>/<template>.xlsx
```

Index schema v2 example:

```json
{
  "schema": "methodmesh.odk_template_index.v2",
  "templateCount": 2,
  "moduleCount": 1,
  "templates": [
    {
      "id": "calibratedscale.example_odk_calibrated_scale_MinMax",
      "moduleId": "calibratedscale",
      "moduleName": "Calibratedscale",
      "displayName": "Calibrated scale — min/max",
      "description": "",
      "sourceFileName": "example_odk_calibrated_scale_MinMax.xlsx",
      "sourceRelativePath": "modules/calibratedscale/docs/example_odk_calibrated_scale_MinMax.xlsx",
      "centralFormId": "calibrated_scale_minmax",
      "formVersion": "20260907",
      "assetPath": "methodmesh/odk_templates/calibratedscale/example_odk_calibrated_scale_MinMax.xlsx",
      "sha256": "...",
      "capabilityIds": [],
      "tags": []
    }
  ]
}
```

`moduleName` in the generated JSON is only a fallback. At runtime the UI resolves `moduleId` against `MethodMeshModuleRegistry` and uses the module's canonical `displayName` whenever possible.

## UI behaviour

- ODK Forms mirrors Capabilities and Presets: **module first, form second**.
- A module section shows the actual count of forms it owns.
- Search covers form title, module, filename, form ID, version, source path, capability IDs, description and tags.
- **Share** materialises the packaged XLSX to cache and sends it through Android Sharesheet.
- **Save** uses Android's document picker so the user owns the destination.
- Dashboard search may surface matching ODK forms, but the dashboard does not become an ODK library itself.
- With ODK Central configured, checking a form deploys/updates/publishes it and grants the selected App User access; unchecking revokes only that App User's access.

## Required full-repository integration

The current UI-folder handoff cannot edit the app-module `build.gradle.kts` itself, but it now includes `ui/integration/odk-template-assets.gradle.kts`. Apply it once from the app module:

```kotlin
apply(from = "src/main/java/com/example/methodmesh/ui/integration/odk-template-assets.gradle.kts")
```

After that, `preBuild` regenerates the catalogue from every `**/docs/example_odk*.xlsx`, so a module with several forms is automatically projected without registration.

Also confirm:

1. a `FileProvider` authority of `${applicationId}.fileprovider` with cache-file access for Sharesheet;
2. a parity test that every generated index entry points to an existing XLSX;
3. a test that multiple XLSForms in the same module all appear as distinct entries;
4. a test that `settings.form_id`, `settings.form_title` and `settings.version` are projected when present;
5. a test that every ODK-plausible module contributes at least one example form unless explicitly exempted.

See `ODK_CENTRAL_TEST_DEPLOYMENT.md` for Central checkbox/deployment semantics.

## Dual-server rapid testing

The same generated form catalogue now feeds two independent deployment routes:

- **ODK Central**: selected Project + selected App User;
- **KoboToolbox**: configured Kobo account / API token.

Each form row can therefore be enabled for ODK, Kobo, both, or neither. The local XLSForm remains the source of truth; remote deployments are projections for testing.

See `XLSFORM_SERVER_PROVIDERS.md` for provider-specific semantics and future adapter boundaries.
