# Beat pads

**Method ID:** `music.beat_pads`  
**Lane/status:** Development  
**Module implementation:** 0.3.0  
**MethodMesh standard:** v1.05

## Purpose

Finger-drum with synthesized one-shot percussion pads and optionally capture the hit sequence.

## Canonical capability and surfaces

This capability is defined once by `As100BeatPadsMethod`. The module dashboard (where applicable), direct native use, preset execution, protocol execution and ODK/XLSForm are projections over that same method contract. The dashboard is not the implementation boundary. The capability remains independently discoverable through `MusicModule.as100Methods()`, `capabilityScreens()` and `capabilitySettings()`.

## Native v1.05 interaction

Large finger-drum pads synthesize local percussion and optionally capture the hit sequence.

Current values are **working results**. Displayed useful scalar/text results are tappable and copy only the useful value. **Commit** freezes the canonical payload on the same screen. Native post-Commit controls expose beef-first Share/Copy, an opt-in full JSON/audit export, Technical details, Done and Edit/new run. Editing does not silently mutate the committed payload.

Completion is launch-origin aware through the shared `onConfirmed`/external completion contract: app and in-app preset runs return to MethodMesh navigation; widget/protocol/schedule/external origins are returned to their owning runner by shared orchestration. ODK/external automatic-return executions suppress the ordinary native post-Commit flow.

## Inputs

- `hits_json`

## Outputs

The primary beef field is `music_beat_pads_result`. All fields below remain contractually available even when the compact native UI does not normally show them:

- `music_beat_pads_result`
- `music_beat_pads_hit_count`
- `music_beat_pads_hits_json`
- `music_beat_pads_duration_ms`
- `music_beat_pads_status`
- `music_beat_pads_audit_json`
- `music_beat_pads_error`
- `methodmesh_full_json` — transport-level complete payload when requested by ODK/external transport.

`music_beat_pads_audit_json` is the module audit payload (`methodmesh.music.audit.v1`) and remains secondary to the main result.

## Presets

This capability remains individually selectable for presets. Fixed preset configuration is hidden at runtime through `settingShouldBeShown`; runtime/operational values remain interactive where needed. The preset launches the same capability screen and Commit lifecycle rather than a second result implementation.

## Protocols and schedules/widgets

The stable method ID `music.beat_pads` remains an independent protocol step and uses the same declared inputs/outputs. Schedule/widget launches do not create a separate implementation; closeout is delegated to the shared launch-origin contract.

## ODK/XLSForm

ODK can either supply canonical `input_*` values for a direct non-interactive execution, or launch the same native interaction when user manipulation is required; Commit returns directly to the external caller.

`docs/example_odk_BeatPads.xlsx` uses a grouped `body::intent`, distinct `req_*` request fields, canonical `input_*` intent extras, every declared output field, and `methodmesh_full_json`. Namespace projection remains a central transport feature and does not alter these canonical field names. ODK owns form persistence/submission; this module does not create an archive merely because ODK invoked it.

## Persistence

Working and committed UI state uses saveable Compose state across ordinary configuration change; no permanent record is created merely by Commit.

## Offline / permissions / dependencies

No dangerous permission. Percussion is synthesized locally with Android audio APIs. All calculations and audio generation in this module are offline-first and do not send music data to a remote service. The module uses Android/Compose APIs plus its own Kotlin music/rhythm algorithms; it does not bundle third-party music samples.

## Compatibility

No method ID, input key or established output key was renamed during the v1.05 migration.

## Validation

See `docs/VALIDATION.md`. The pure Kotlin music/creation engines compile and pass focused smoke tests in the migration environment. A full MethodMesh Gradle build was not available in this sandbox, so the capability remains **Development** pending target-repository build and device UX/audio validation where applicable.
