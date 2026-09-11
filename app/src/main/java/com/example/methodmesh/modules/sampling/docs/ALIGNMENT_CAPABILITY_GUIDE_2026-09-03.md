# Capability-guide alignment — Sampling v0.1.1

Compared against the 2026-09-03 `CAPABILITY_WRITING_GUIDE.md` revision.

## Aligned

- Canonical handoff remains one self-contained `sampling/` module folder with nested docs and XLSForm.
- No central module registration or shared dashboard special case is required.
- Method ID remains stable at `sampling.run`; status remains `Development`.
- Canonical settings are declared in `SamplingModule.capabilitySettings()`.
- Enumerations use `ChoiceSetting`, booleans use `BooleanSetting`, counts use `IntSetting`, and numeric sequence inputs are declared as `FloatSetting`.
- `csv_uri` and `csv_text` are declared inputs rather than screen-only hidden state.
- Fixed native-preset settings are hidden with `settingShouldBeShown(...)`.
- Native preset runs no longer auto-execute before runtime inputs can be entered.
- External AutomaticReturn / ODK calls still execute without a native configuration gate.
- AutomaticReturn completion is owned by `CapabilityScreenScaffold`; Sampling does not directly double-submit the result.
- Result state, seed/settings, selected CSV URI and reconstructed result values use saveable state; CSV content is re-read from the persisted URI after recreation.
- `sampling_value` is a compact scalar/list main result; `sampling_result_uri` is the file result for CSV/JSON file workflows.
- Verbose provenance is carried by `sampling_audit_json` / FULL metadata rather than being the only useful result.
- ODK example uses an XLSForm group intent and explicitly includes the selected ID/value, capability audit JSON, provenance hash and shared FULL envelope.
- Protocol/schedule close-out and prior-step pipes are left to the shared runtime contract.
- Attestation is declared as a module dependency and consumed only through public `attestation.create`; Sampling does not copy TSA/signing internals.
- Sampling computation is offline/local. Optional TSA trusted timestamping belongs to the downstream attestation capability.
- Pure sampling regression tests pass under Kotlin/JVM 1.9.0.

## Remaining Development validation

These require the full Android project/device environment and are not claimed complete by this drop-in:

1. `./gradlew :app:assembleDebug` in the complete MethodMesh checkout.
2. Native run and orientation-change validation on Android.
3. Preset creation, fixed/runtime field selection and native preset execution.
4. ODK Collect round-trip using `example_odk_Sampling.xlsx`.
5. Sampling → `attestation.create` protocol chain with a real TSA token/timestamp.
6. Native **Share result** for genuine CSV/JSON output artefacts. The shared MethodMesh transport now treats returned `content://` files as typed attachments regardless of whether they are image/PDF/CSV/JSON; device validation is still required. This is distinct from optional FULL debug JSON, which is appended to Share text rather than introduced as a competing JSON stream.
7. Repository-level `000_Roadmap.md` update. `ROADMAP_NOTE.md` contains the exact module-owned handoff text because this drop-in does not modify files outside its canonical module folder.

Sampling should remain **Development** until these checks are closed.
