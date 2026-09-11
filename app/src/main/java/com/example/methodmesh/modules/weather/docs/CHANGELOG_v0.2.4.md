# Weather v0.2.4 — Master Book preset/Commit alignment

## Presets

- Restores **Save current setup as preset** inside the immersive Weather dashboard and every individual Weather capability.
- Uses the shared MethodMesh `CapabilityPreset` and `ProtocolLibraryRepository`; Weather does not define a private preset format or store.
- Preset authoring exposes the current MethodMesh option set: fixed versus runtime settings, returned payload (`Core`, `Core + audit`, `Everything`), post-result action (`Home`, `Share`, `Save`), optional persistent Files log, and preset naming.
- Native preset runs with one or more `methodmesh_runtime_fields` now pause before acquisition and ask for those runtime fields. Fully fixed presets retain immediate execution.
- Fixed preset settings remain hidden during the native run through `CapabilityScreenContext.settingShouldBeShown(...)`.

## Native Commit lifecycle

- Direct/manual Weather use now freezes the canonical result on **Commit** instead of immediately handing it to the host.
- The committed payload remains distinct from subsequent working settings/results.
- Same-screen committed actions provide Copy, Share, Save, optional full JSON and Done.
- Recommit explicitly replaces the frozen payload.

## ODK / external contract

- No Weather method IDs, input keys, output keys or canonical XLSForms changed in v0.2.4.
- Automatic-return callers continue to use the existing canonical Weather result contract.
- The eleven canonical XLSForms remain single-invocation `FULL`/`flat` examples and retain `methodmesh_status` plus `methodmesh_full_json`.
- Preset authoring controls are native-only and are not shown to automatic-return callers such as ODK.
