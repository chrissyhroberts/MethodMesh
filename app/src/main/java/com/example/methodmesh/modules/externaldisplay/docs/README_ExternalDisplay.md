# External display

Method ID: `external_display.present`

Status: Development · Offline (system casting may depend on device services)

This module provides the reusable external-display boundary for MethodMesh. It discovers Android presentation displays through `DisplayManager`, launches a clean MethodMesh presentation activity on a selected display with `ActivityOptions.setLaunchDisplayId`, and exposes blank/stop controls. A later capability can replace the generic presentation surface with an astronomy dashboard or another module-owned surface without changing this contract.

## Surface and hardware policy

The dashboard, direct Android intent, preset, protocol and ODK/XLSForm all address the same `external_display.present` method. The native screen is the controller surface; the external activity is the presentation surface. A persistent notification appears while a presentation is active when Android notification permission allows it.

MethodMesh does not implement USB video transport and cannot make unsupported hardware output video. An external display is reported only when Android exposes a suitable presentation display. Whole-device mirroring and third-party apps remain Android/system-managed; the `System casting` action opens the system handoff surface and reports that handoff rather than claiming to control it. No `MediaProjection` path is used by this capability.

## Capabilities

`external_display.present` discovers a suitable Android presentation display, launches the generic presentation surface, exposes blank/stop controls, or opens Android's system-managed casting controls.

## Android intent

Use the shared `com.example.methodmesh.EXECUTE_METHOD` action with `method_id=external_display.present` and optional `input_mode=present` or `input_mode=system_handoff`.

## Inputs

- `mode`: `present` or `system_handoff`.
- `blank_on_start`: reserved for the presentation surface lifecycle and defaults to false.

## Contractual outputs

`external_display_status`, `external_display_id`, `external_display_name`, `external_display_connection`, `external_display_native_video_capability`, `external_display_system_handoff`, `external_display_blanked`, `external_display_message`, and `external_display_error`.

`methodmesh_full_json` remains available through the shared MethodMesh transport. No capability-owned persistent data or ODK attachment is created.

## ODK example

`example_odk_showcase_external_display.xlsx` invokes exactly this capability through the standard MethodMesh intent and returns the shared status/full JSON fields plus the external-display result fields.

## Example intent

```text
com.example.methodmesh.EXECUTE_METHOD
method_id=external_display.present
input_mode=present
```

The same canonical call can be saved as a preset, used as a protocol step, scheduled where the launch policy permits an interactive surface, or invoked from an ODK form. ODK receives the standard status and full JSON return; unavailable displays are reported as a normal failed capability result with an explicit error.
