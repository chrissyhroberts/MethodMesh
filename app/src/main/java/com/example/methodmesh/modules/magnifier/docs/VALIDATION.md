# Magnifier validation

Capability: `visual.magnifier.capture`

Status: **Development / admission candidate**

## Review performed for this handoff

The module was designed against the current MethodMesh module contract and the current camera patterns already used by MethodMesh. Static review covered:

- self-contained `MethodMeshModule` ownership;
- no central registration or `HomeScreen` special casing;
- `MethodSetting` types for all preset configuration;
- CameraX / `LifecycleCameraController` use;
- runtime camera permission request;
- cache-backed `FileProvider` result artefact;
- SHA-256 over final JPEG bytes;
- beef-first output naming compatible with the current generic `OutputFormatter` rules;
- orientation-persistent scalar/file-path state via `rememberSaveable`;
- grouped ODK intent example with media + full JSON return;
- no network dependency or automatic archive save.

## Build status

A full `./gradlew :app:testDebugUnitTest` / `./gradlew :app:assembleDebug` run was **not possible in the artifact-generation environment because the complete MethodMesh repository was not locally available to Gradle**. Do not promote this capability to Production on the strength of this handoff alone.

## Required admission checks

Run from the MethodMesh repository after placing this folder at:

```text
app/src/main/java/com/example/methodmesh/modules/magnifier/
```

### 1. Unit/build boundary

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Add/fix focused tests if the build exposes API drift.

### 2. Architecture

Confirm:

- module discovery finds `MagnifierModule` without a central list edit;
- method ID is unique: `visual.magnifier.capture`;
- capability screen ID is unique and matches the method ID;
- descriptor status remains `Development` until promotion;
- no shared UI acquires magnifier-specific logic.

### 3. Native run

On at least two physical Android devices where possible:

- first-run camera permission request works;
- denial is recoverable;
- rear-camera live preview opens;
- front-camera live preview opens;
- switching Rear ↔ Front rebinds cleanly without retaining torch/focus state from the previous camera;
- a fixed `camera_facing` preset hides the selector during the native preset run, while a runtime `camera_facing` setting remains selectable;
- zoom slider clamps to the device range without crashing;
- 1×, mid zoom and maximum available zoom visibly work;
- Back light appears only where the selected camera provides a flash unit and the setting allows it;
- Front light is available when allowed, raises screen brightness, produces a visible white illumination surround, and restores brightness when disabled/exiting;
- Back light and Front light are mutually exclusive in the native controls;
- a frozen capture records `magnifier_torch_mode` / `magnifier_front_light_mode` according to the light actually active at freeze time, then both lights shut off;
- hold focus and release focus do not crash;
- Freeze frame produces the expected orientation;
- Normal / Contrast / Mono / Negative filters change the live camera immediately;
- the frozen frame initially matches the live filter and can be switched between filters without recapture;
- the final JPEG matches the selected filter;
- frozen image pinch/drag works smoothly;
- `Use this image` produces a JPEG and standard result screen;
- Share shares the image by default;
- Save to Downloads saves the image;
- enabling full JSON adds metadata rather than replacing the image result;
- Retry resets the run cleanly;
- Done/Home routing follows the shared MethodMesh closeout rules.

### 4. Rotation/state

Test rotation:

- while live;
- after zoom/torch/focus changes;
- after Freeze frame;
- after changing the inspection filter;
- after `Use this image` on the result screen.

Expected: file-backed frozen/final results and scalar control state survive recreation without losing the selected result.

### 5. Byte-hash check

For one captured result:

1. Resolve/copy the bytes behind `magnifier_image_uri`.
2. Calculate SHA-256 independently.
3. Confirm it equals `magnifier_image_sha256`.

The commitment must be to final JPEG bytes, not URI text.

### 6. ODK/XLSForm

Import and exercise:

```text
docs/example_odk_showcase_visual_magnifier_capture.xlsx
```

Confirm:

- the ODK camera choice exposes both `rear` and `front`;
- the ODK light choice exposes `off`, `back`, and `front`;
- the ODK starting-filter choice exposes `normal`, `high_contrast`, `monochrome`, and `negative`;
- grouped `body::intent` launches MethodMesh and passes `input_camera_facing`, `input_default_light`, and `input_default_filter`;
- the operator completes the interactive magnifier flow;
- `magnifier_image_uri` is imported as an ODK image attachment;
- `magnifier_status` returns `succeeded`;
- `magnifier_image_sha256` is populated;
- `magnifier_camera_facing` returns the selected camera value;
- `magnifier_filter_mode` returns the filter actually selected for the returned image;
- `magnifier_torch_mode` and `magnifier_front_light_mode` describe the illumination used for the frozen frame;
- `methodmesh_full_json` is populated because the example requests `FULL`;
- blank return fields do not overwrite input settings;
- no extra MethodMesh archive copy is created for the ODK return.

### 7. Device-specific risk checks

Specifically test at least one device with:

- a low maximum digital zoom;
- front and rear cameras with different zoom ranges;
- no usable torch/flash on the selected camera if available;
- a camera HAL with noticeably different autofocus behavior.

## Promotion gate

Promote to Production only after the normal MethodMesh production checklist passes: build, native UX, preset UX, ODK example, rotation state, beef-first sharing, no golden-rule violation, permissions/offline/attribution review, and explicit production method status update.


- Verify Rear/Front remains visible during a native preset run even when `camera_facing` is fixed; the preset value is the starting camera, not a lockout.
- On at least one device with a rear flash LED, verify Back light toggles the physical torch after camera binding and after Front→Rear switching.
- Verify enabling Back light disables Front light, and enabling Front light disables Back light.

- Verify Rear/Front remains visible during direct use and native preset runs, including when `camera_facing` is fixed.
- Verify the preset/ODK `camera_facing` value still determines the starting camera.
- On a device with a rear flash LED, verify Back light toggles the physical torch after initial bind and after Front → Rear switching.
- Verify enabling Back light disables Front light and enabling Front light disables Back light.
