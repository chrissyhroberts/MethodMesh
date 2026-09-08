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
- zoom slider clamps to the device range without crashing;
- 1×, mid zoom and maximum available zoom visibly work;
- torch control appears only where supported/allowed;
- hold focus and release focus do not crash;
- Freeze frame produces the expected orientation;
- Normal / Contrast / Mono / Negative filters render correctly;
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
docs/example_odk_visual.magnifier.capture.xlsx
```

Confirm:

- grouped `body::intent` launches MethodMesh;
- the operator completes the interactive magnifier flow;
- `magnifier_image_uri` is imported as an ODK image attachment;
- `magnifier_status` returns `succeeded`;
- `magnifier_image_sha256` is populated;
- `methodmesh_full_json` is populated because the example requests `FULL`;
- blank return fields do not overwrite input settings;
- no extra MethodMesh archive copy is created for the ODK return.

### 7. Device-specific risk checks

Specifically test at least one device with:

- a low maximum digital zoom;
- no usable torch/flash on the selected camera if available;
- a camera HAL with noticeably different autofocus behavior.

## Promotion gate

Promote to Production only after the normal MethodMesh production checklist passes: build, native UX, preset UX, ODK example, rotation state, beef-first sharing, no golden-rule violation, permissions/offline/attribution review, and explicit production method status update.
