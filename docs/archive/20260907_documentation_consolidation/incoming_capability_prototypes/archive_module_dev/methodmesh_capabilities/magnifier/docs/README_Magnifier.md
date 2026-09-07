# Visual inspection / Magnifier

Status: **Development**

An immersive camera inspection tool for cases where a phone is useful as a handheld magnifier.

## Capabilities

- `visual.magnifier.capture` — zoomed camera inspection with torch, autofocus/focus hold, freeze-frame, post-freeze inspection filters, and capture of the inspected image.

The capability uses MethodMesh's existing CameraX stack and requests immersive hosting locally. It does not add camera-specific branches to the shared host.

## Android intent

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='visual.magnifier.capture',input_initial_zoom='2.0',input_default_filter='normal',return_mode='flat')
```

External/ODK execution is intentionally interactive because the operator must inspect and choose a frame.

## Inputs

Configuration inputs:

- `input_initial_zoom` — initial digital zoom ratio; UI range 1×–10× and CameraX/device limits still apply.
- `input_default_filter` — `normal`, `high_contrast`, `monochrome`, or `negative`.
- `input_allow_torch` — Boolean.

Runtime action is camera/operator driven rather than a stored text input.

## Outputs

Core output:

- `magnifier_image_uri` — captured filtered image URI.

Audit-priority outputs:

- `magnifier_status`
- `magnifier_filter`
- `magnifier_zoom_ratio`
- `magnifier_torch`
- `magnifier_focus_locked`
- `magnifier_captured_time_iso`
- `magnifier_error`

Full metadata:

- `magnifier_metadata_json`

## ODK example

`example_odk_visual.magnifier.capture.xlsx` demonstrates an ODK group intent that launches the camera workflow and receives the image URI plus inspection metadata.

## Permissions and offline behaviour

Requires `android.permission.CAMERA`, already present in the MethodMesh manifest. No network connection is required. Captures are written to app cache and exposed through the existing MethodMesh `FileProvider`.

## Known limitations

- Camera hardware controls vary: the requested zoom may be clamped/rejected by a particular device.
- CameraX continuously autofocuses by default. **Hold focus** starts a centre focus/metering action without auto-cancel; it is a practical focus hold, not a claim of identical optical AF-lock semantics on every camera HAL.
- High contrast, monochrome and negative filters are applied to the frozen/captured frame. The live preview remains unfiltered in v0.1.0; this avoids device-specific GPU/render-effect requirements while still capturing the inspected filtered image.
- No OCR, measurement, object recognition or background image library is included.
