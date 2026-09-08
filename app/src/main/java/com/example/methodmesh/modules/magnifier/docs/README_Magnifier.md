# Magnifier

Capability ID: `visual.magnifier.capture`

Status: **Development**.

## Purpose

Use either the rear or front camera as a handheld visual magnifier. The operator can zoom the live camera, switch live between Normal, Contrast, Mono and Negative views, use a rear LED back light or display-based front light, hold centre focus, freeze a frame, continue comparing filters on that frozen frame, and return the chosen filtered image.

The module deliberately remains a visual-inspection primitive. It does **not** add OCR, measurement, object recognition, image libraries, remote vision services, or automatic interpretation.

## MethodMesh architecture

This folder is a self-contained MethodMesh capability module:

- `MagnifierModule.kt` owns discovery metadata, settings, RIL binding and icon hint.
- `MagnifierMethod.kt` owns the method/output contract.
- `MagnifierCapabilityScreen.kt` owns the interactive CameraX workflow.
- `MagnifierImageProcessing.kt` owns orientation correction and local image filters.
- `docs/` owns the implementation documentation and ODK example.

No capability-specific registration or `HomeScreen` branch is required. The module uses the existing MethodMesh `MethodMeshModule`, `CapabilityScreenSpec`, result scaffold, CameraX dependencies and `FileProvider` conventions.

## Native UX

The normal run is intentionally direct:

1. The configured camera opens.
2. Choose **Rear** or **Front** when `camera_facing` is not fixed by a preset.
3. Adjust zoom.
4. Optionally use **Back light** (rear camera flash LED) or **Front light** (bright display illumination), and hold/release centre focus.
5. Choose **Normal**, **Contrast**, **Mono** or **Negative** at any time; the live camera changes immediately.
6. Tap **Freeze frame**.
7. Pinch/drag the frozen image to inspect it; the same filter remains active and can still be changed.
7. Tap **Use this image**.
8. The standard MethodMesh result screen shows the image as the main result, with Share / Save to Downloads / Done / Retry behavior supplied by the shared scaffold.

For dedicated intent/preset presentation the module locally requests immersive system-bar hiding and restores the bars when it exits. It does not add camera-specific behavior to the shared host.

The frozen inspection zoom/pan is display-only. It does not crop or resample the returned file. The returned file is the full frozen frame with the selected filter applied.

## Presets

Preset configuration fields are:

| Setting | Type | Default | Meaning |
|---|---|---:|---|
| `camera_facing` | ChoiceSetting | `rear` | `rear` or `front`; selects the camera for live inspection and capture. |
| `initial_zoom` | FloatSetting | `2.0` | Starting digital zoom ratio. UI limit is 1×–10×; device limits still apply. |
| `default_filter` | ChoiceSetting | `normal` | Starting live filter. The same selected filter is used for live preview, frozen inspection and the returned JPEG. |
| `allow_torch` | BooleanSetting | `true` | Whether the rear LED **Back light** control is offered when the selected camera supports it. |
| `allow_front_light` | BooleanSetting | `true` | Whether the display-based **Front light** control is available. |
| `default_light` | ChoiceSetting | `off` | Starting light: `off`, `back`, or `front`. |
| `front_light_brightness` | FloatSetting | `1.0` | Display brightness (0.25–1.0) while Front light is active. |

These values configure the starting state. `camera_facing` is part of the canonical capability contract and may be fixed by a preset or marked as a runtime input. When it is fixed, the native preset run hides the camera selector in accordance with the shared preset rules. Zoom, filter and focus remain operator controls during an inspection.

## Android intent

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='visual.magnifier.capture',input_camera_facing='rear',input_initial_zoom='2.0',input_default_filter='normal',input_allow_torch='true',input_allow_front_light='true',input_default_light='off',input_front_light_brightness='1.0',input_payload_mode='FULL',return_mode='flat')
```

External/ODK execution is intentionally interactive: the operator must choose the frame to return.

## Inputs

| Intent field | Allowed values | Notes |
|---|---|---|
| `input_camera_facing` | `rear`, `front` | Selects the requested camera. If the requested camera cannot be bound, the capability reports the camera as unavailable rather than silently substituting another camera. |
| `input_initial_zoom` | `1.0`–`10.0` | CameraX/device min/max zoom still wins. |
| `input_default_filter` | `normal`, `high_contrast`, `monochrome`, `negative` | Starting live filter. The operator may change it interactively; the selected filter is also applied to the returned JPEG. |
| `input_allow_torch` | `true`, `false` | Enables the rear LED Back light control when the selected camera has a flash unit. |
| `input_allow_front_light` | `true`, `false` | Enables the display-based Front light control. |
| `input_default_light` | `off`, `back`, `front` | Starting illumination state. Unsupported Back light does not silently substitute Front light. |
| `input_front_light_brightness` | `0.25`–`1.0` | Screen brightness used while Front light is on. |
| `input_payload_mode` | normally `CORE` or `FULL` | `FULL` exposes `methodmesh_full_json` through shared transport. |
| `return_mode` | normally `flat` for ODK | Shared MethodMesh transport setting. |

## Output contract

### Core result — the beef

| Field | Description |
|---|---|
| `magnifier_image_uri` | `content://` URI for the final filtered JPEG. |

The main native result is the image. Default sharing/saving should therefore act on the image rather than on a block of metadata.

### Audit/detail fields

| Field | Description |
|---|---|
| `magnifier_status` | `succeeded` for a completed capture. |
| `magnifier_image_sha256` | SHA-256 of the exact final JPEG bytes. |
| `magnifier_camera_facing` | `rear` or `front`, recording the camera selected for the returned image. |
| `magnifier_filter_mode` | Filter applied to the returned JPEG. |
| `magnifier_zoom_requested_ratio` | Operator-requested zoom ratio. |
| `magnifier_zoom_actual_ratio` | Last CameraX zoom ratio observed after clamping/application. |
| `magnifier_torch_mode` | `on` or `off`, recording whether the rear LED Back light was active when the frame was frozen. |
| `magnifier_front_light_mode` | `on` or `off`, recording whether the display Front light was active when the frame was frozen. |
| `magnifier_focus_mode` | `held` or `continuous` at capture completion. |
| `magnifier_frozen_time_iso` | ISO-8601 time the inspection frame was frozen. |
| `magnifier_captured_time_iso` | ISO-8601 time the final filtered JPEG was created. |
| `magnifier_metadata_json` | Compact module-owned JSON containing the same result metadata. |
| `magnifier_error` | Failure diagnostic when applicable. |

The audit names intentionally follow the shared MethodMesh projection conventions (`status`, `sha`, `requested`/`actual`, `*_mode`, `*_time_iso`, `*_json`, `*_error`) so they stay out of the default CORE/native result without a magnifier-specific exception in shared code.

### Full JSON

For ODK or explicit full export, request:

```text
input_payload_mode='FULL'
```

Shared transport then adds:

- `methodmesh_full_json`

This is the complete auditable payload. It is not the main native display.

## Media / attachment behavior

The final JPEG is written to MethodMesh cache as the source artefact for `FileProvider` exposure. This is not an automatic archive save.

The capability returns the `content://` URI. Shared Android transport is responsible for adding returned binary URIs to `ClipData` and granting `FLAG_GRANT_READ_URI_PERMISSION` to external callers such as ODK Collect.

The temporary frozen source frame is cache-only. It is not returned, shared or saved to Downloads by the capability.

## ODK / XLSForm

`example_odk_showcase_visual_magnifier_capture.xlsx` demonstrates the current single-invocation showcase pattern:

- a `select_one` input for **Rear camera** / **Front camera**;
- a `select_one` input for the starting live filter: **Normal**, **Contrast**, **Mono** or **Negative**;
- a `select_one` input for starting light: **Off** / **Back light** / **Front light**;
- one `begin_group` with `appearance=field-list`;
- one MethodMesh action in `body::intent`, passing `input_camera_facing=${camera_facing}`, `input_default_light=${default_light}`, and `input_default_filter=${default_filter}`;
- an `image` child named `magnifier_image_uri` so ODK can import the returned media attachment;
- explicit `magnifier_status`, `magnifier_image_sha256`, `magnifier_camera_facing`, `magnifier_filter_mode`, `magnifier_torch_mode`, and `magnifier_front_light_mode` return fields;
- `methodmesh_full_json` for complete audit metadata.

The example requests `input_payload_mode='FULL'`.

## Permissions

Requires:

```text
android.permission.CAMERA
```

The module requests camera permission at runtime when required. A denial is shown clearly and the operator can retry the permission request.

No storage permission is required for the cache/FileProvider path.

## Offline / online behavior

**Fully offline.**

No network connection is required. No image, metadata, location or device identifier is sent off-device by this capability.

## Dependencies

Uses dependencies already present in MethodMesh:

- AndroidX CameraX (`camera2`, `lifecycle`, `view`);
- Jetpack Compose / Material 3;
- Android `ExifInterface`;
- existing MethodMesh `Digests`, `CapabilityScreenScaffold`, `OutputFormatter`, `SettingsState` and `FileProvider` infrastructure.

No new Maven dependency is required by the module as written.

## Camera behavior and limitations

- The UI requests zoom from 1× to 10×, but CameraX/device limits are authoritative. `magnifier_zoom_actual_ratio` records the last zoom ratio observed from CameraX after application.
- **Hold focus** starts a centre `FocusMeteringAction` with CameraX auto-cancel disabled. Camera HAL behavior differs between devices, so this is a practical focus hold rather than a claim that all hardware provides identical optical AF-lock semantics.
- Rear/front selection uses CameraX `DEFAULT_BACK_CAMERA` / `DEFAULT_FRONT_CAMERA`. MethodMesh does not silently fall back to the other camera when a requested camera cannot be bound.
- **Back light** uses CameraX torch control and therefore depends on the selected camera flash unit; front cameras commonly have no flash unit.
- **Front light** is independent of camera flash hardware: MethodMesh raises display brightness and provides a white illumination surround around the live preview. It is especially useful with the front camera for close inspection.
- Front light brightness is restored when the light is switched off or the capability exits. Both lights are shut off after a frame is successfully frozen. The output fields record which light was active at freeze time.
- Normal, Contrast, Mono and Negative are live inspection modes. The `PreviewView` runs in CameraX `COMPATIBLE` mode and the preview is composited through the same Android colour matrix used for the frozen frame and final JPEG. The selected filter therefore has one canonical definition across live inspection and returned media.
- High-contrast, monochrome and negative processing are simple local colour-matrix transforms, not diagnostic image enhancement.
- The module does not promise optical magnification beyond the phone camera hardware; digital zoom may reduce effective detail.

## Hash semantics

`magnifier_image_sha256` hashes the exact bytes of the final JPEG written to the cache artefact. It does **not** hash the URI string, bitmap object, metadata JSON or source frame.

## Attribution / licensing

See `THIRD_PARTY_NOTICES.md`.

## Validation status

See `VALIDATION.md`.
