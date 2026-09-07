# ODK template library integration

The v1.05 UI now has a first-class **ODK forms** destination. The library is intentionally separate from the installed-form launcher: it is a catalogue of **module-owned XLSForm design templates** that can be searched, shared, or saved for reuse.

## Ownership rule

Each module continues to own its XLSForms under its own `docs/` directory, for example:

```text
modules/astronomy/docs/example_odk_Astronomy.xlsx
modules/music/docs/example_odk_Polyrhythm.xlsx
modules/music/docs/example_odk_TapTempo.xlsx
```

The source-of-truth file stays with the module. Do not create a second hand-maintained template library.

## Build-time projection

A generic build task should scan module folders for `docs/example_odk_*.xlsx`, copy those files into packaged assets, and generate one index:

```text
assets/methodmesh/odk_templates/index.json
assets/methodmesh/odk_templates/<module_id>/<template>.xlsx
```

Suggested index shape:

```json
{
  "schema": "methodmesh.odk_template_index.v1",
  "templates": [
    {
      "id": "astronomy.conditions.example",
      "moduleId": "astronomy",
      "moduleName": "Astronomy",
      "displayName": "Astronomy conditions",
      "description": "Example roundtrip form for astronomy conditions.",
      "sourceFileName": "example_odk_Astronomy.xlsx",
      "centralFormId": "astronomy_conditions_test",
      "assetPath": "methodmesh/odk_templates/astronomy/example_odk_Astronomy.xlsx",
      "capabilityIds": ["astronomy.conditions"],
      "tags": ["weather", "temperature", "humidity", "dew"]
    }
  ]
}
```

The generated index is a projection only. Module docs remain authoritative.

## UI behaviour

- ODK Forms mirrors Capabilities and Presets: module first, item second.
- Search covers display name, module, file name, capability IDs, description and tags.
- **Share** materialises the packaged XLSX to cache and sends it through Android Sharesheet.
- **Save** uses Android's document picker so the user owns the destination.
- Dashboard search may surface matching ODK forms, but the dashboard does not become an ODK library itself.

## Required framework integration

The full app build should add/confirm:

1. a build step generating the asset tree/index above;
2. a `FileProvider` authority of `${applicationId}.fileprovider` with cache-file access for Sharesheet;
3. a parity test that every generated index entry points to an existing XLSX;
4. a test that every module with ODK-plausible capabilities contributes at least one form unless explicitly exempted.


## Central test-deployment metadata

Where possible the generator should also populate `centralFormId` from the XLSForm's canonical `form_id`. If extracting that value at build time is inconvenient, generate a stable module/template ID. A stable value allows the testing UI to rediscover an already deployed form after local deployment metadata is lost.

See `ODK_CENTRAL_TEST_DEPLOYMENT.md` for the checkbox deployment semantics and API boundary.
