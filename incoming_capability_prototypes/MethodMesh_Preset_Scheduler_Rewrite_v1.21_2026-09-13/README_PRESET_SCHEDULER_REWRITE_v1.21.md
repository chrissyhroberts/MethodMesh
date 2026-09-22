# MethodMesh preset + scheduler rewrite — v1.21

Date: 2026-09-13

This is a cumulative shared-framework rewrite for preset authoring/execution and
the vNext scheduler. It includes the scheduler fixes developed immediately before
this rewrite (new schedule types, exact-alarm support, transient scheduled launch,
and the corrected SchedulePlanRuntime comparison).

## Preset contract

`CapabilityPreset` now stores `launchMode` as first-class metadata:

- `AUTO` — use the capability's normal presentation.
- `INTERACTIVE` — explicitly show the capability UI.
- `BACKGROUND` — explicitly request a headless/unattended capability run.

Missing launch mode decodes as `AUTO`.

The preset/protocol export schema is now bundle version 2. The current importer
accepts and integrity-checks both v1 and v2. Imported v1 presets acquire
`launchMode=AUTO`.

## Preset authoring UI

The central Create preset dialog is reorganised as:

1. Preset — name + optional description
2. Configuration — Fixed vs Ask when run
3. Run behaviour — Auto / Show UI / Background
4. After completion — Return / Share / Save
5. Returned data — Result / + Audit / + Full JSON
6. Logging — explicit opt-in
7. Advanced configuration JSON — collapsed by default

The old second naming dialog is removed.

The Weather immersive preset authoring surface uses the same conceptual language
and stores the same first-class launch mode.

Internal compatibility tokens remain:

- `CORE`, `AUDIT`, `FULL`
- `HOME`, `SHARE`, `SAVE`

They are no longer the preferred UI labels.

## Scheduler integration

A scheduled preset action now stores a presentation override:

- Follow preset
- Show UI
- Background

Follow preset is the default for existing schedule-plan JSON.

Only an effective Background launch sets `input_methodmesh_headless=true`.
Interactive and Auto preserve the capability's normal UI.

The existing legacy scheduler's explicit `headless` schedule flag remains a
valid schedule-level override.

The vNext scheduler included here retains:

- Once / Interval / Daily / Weekly / Monthly / Day sequence / Cron
- immediate-first occurrence where authored
- elapsed interval anchors
- local-wall-clock calendar recurrence
- exact alarm permission support
- high-priority schedule notifications
- transient notification-launched tasks that return to the app/desktop underneath
- rolling occurrence generation for long/forever schedules

## Files intentionally changed/included

Shared preset/runtime:
- `core/protocols/ProtocolLibrary.kt`
- `ui/HomeScreen.kt`
- `modules/weather/WeatherPresetSupport.kt`

Scheduler:
- `core/scheduling/SchedulePlanModels.kt`
- `core/scheduling/SchedulePlanStore.kt`
- `core/scheduling/SchedulePlanCapabilityScreen.kt`
- `core/scheduling/SchedulePlanDispatchActivity.kt`
- `core/scheduling/SchedulerDispatchActivity.kt`
- cumulative vNext scheduler support/routing/runtime files
- legacy cron startup hardening (`nextOrNull`, guarded reschedule, save-time cron validation)
- main Android manifest exact-alarm/transient-task declarations

Documentation:
- `docs/METHODMESH_MASTER_BOOK.md` → v1.21

## Compatibility

- Stored presets without `launch_mode` remain valid and become Auto.
- Stored schedule-plan actions without `preset_launch_mode` remain valid and
  become Follow preset.
- v1 protocol/preset export bundles remain importable with their original hash
  verification.
- Stable method IDs and capability settings contracts are unchanged.

## Validation performed here

- Structural delimiter checks across the Kotlin files in this bundle.
- Pure Kotlin compilation of `SchedulePlanModels.kt` + `SchedulePlanEngine.kt`
  against the existing cron/timing model succeeded.
- Master Book v1.21 section/version/requirement sanity checks succeeded.

A full Android/Gradle build was not available in this runtime; build the bundle
in the MethodMesh project and report compiler output if the local tree has
diverged further.
