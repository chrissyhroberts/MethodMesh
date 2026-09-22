# Enketo browser media bridge alpha

This example exercises the shared MethodMesh browser bridge with the existing
`visual.magnifier.capture` capability. Magnifier itself has no Enketo-specific
code.

## Workflow

1. An ordinary Enketo `url` question opens the normal `methodmesh://` browser route.
2. MethodMesh routes the request to `visual.magnifier.capture` using the canonical `method_id`/`input_*` contract.
3. The operator freezes a frame and chooses **Use this image**.
4. `input_browser_return=clipboard_media` tells the shared browser adapter to copy the projected result JSON and publish returned media.
5. The returned `content://` JPEG is copied byte-for-byte into Android MediaStore under `Pictures/MethodMesh`.
6. The transient MethodMesh task closes behind the existing browser task, revealing the exact live Enketo tab.
7. The operator pastes the JSON into a normal text field and attaches the newest MethodMesh image through the ordinary Enketo/Android picker.

The final attachment remains user-mediated. A native application cannot
programmatically populate an HTML `<input type="file">` on somebody else's web
page.

## Deep link used by the example

```text
intent://execute?method_id=visual.magnifier.capture&input_caller=enketo&input_browser_return=clipboard_media&input_request_ref=enketo_magnifier_alpha&input_camera_facing=rear&input_initial_zoom=2.0&input_default_filter=normal&input_allow_torch=true&input_allow_front_light=true&input_default_light=off#Intent;scheme=methodmesh;end
```

`clipboard_media` is explicit because MediaStore publication creates a persistent
public copy. Omitted `input_browser_return` and `clipboard` return structured data
only; `none` disables clipboard/media return while preserving the browser round
trip.

## Clipboard envelope

The version-1 JSON contains closeout status, canonical method IDs, optional request
reference, projected fields, media metadata and warnings. Published media entries
include display name, MIME type, relative path, published URI and SHA-256. For
Magnifier the transport hash should match `magnifier_image_sha256`, because the
MediaStore object is a byte-for-byte copy of the returned JPEG.

## Contract boundary

This path is browser-only. ODK Collect still receives the original `content://`
result through its Activity-result/ClipData/read-grant contract and does not gain
an extra MediaStore save. Browser MediaStore publication currently requires
Android 10/API 29 or later.
