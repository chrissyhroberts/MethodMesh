# Enketo browser media bridge alpha

This example exercises the generic MethodMesh browser bridge with the existing
`visual.magnifier.capture` capability. It does not require changes to the
Enketo/Kobo/ODK web-form installation.

## Alpha workflow

1. An ordinary Enketo `url` question opens a `methodmesh://` deep link.
2. MethodMesh routes the request to `visual.magnifier.capture`.
3. The operator uses the existing Magnifier UI and chooses **Use this image**.
4. The generic browser return bridge sees `input_browser_return=clipboard_media`.
5. MethodMesh copies scalar/structured result data to the Android clipboard.
6. The returned `content://` JPEG is copied into Android MediaStore under
   `Pictures/MethodMesh`.
7. MethodMesh returns to the calling browser/Enketo form.
8. The operator pastes the result payload into a normal text field and uses the
   normal Enketo image question to attach the most recent MethodMesh image from
   Recent/Photos/Files.

The attachment step is intentionally user-mediated. A native application cannot
programmatically populate an HTML `<input type="file">` on somebody else's
web page; that browser restriction prevents silent exfiltration of local files.

## Deep link used by the example

```text
intent://execute?method_id=visual.magnifier.capture&input_caller=enketo&input_browser_return=clipboard_media&input_request_ref=enketo_magnifier_alpha&input_camera_facing=rear&input_initial_zoom=2.0&input_default_filter=normal&input_allow_torch=true&input_allow_front_light=true&input_default_light=off#Intent;scheme=methodmesh;end
```

`input_browser_return=clipboard_media` is transport-level rather than
magnifier-specific. Once validated, the same option can be used by other
MethodMesh capabilities that return media.

## Returned clipboard payload

The generic browser bridge copies JSON shaped approximately as follows:

```json
{
  "methodmesh_browser_return_v": 1,
  "status": "completed",
  "created_at": "...",
  "methods": ["visual.magnifier.capture"],
  "request_ref": "enketo_magnifier_alpha",
  "fields": {
    "magnifier_status": "succeeded",
    "magnifier_image_uri": "methodmesh-magnifier-....jpg",
    "magnifier_image_sha256": "..."
  },
  "media": [
    {
      "source_fields": ["magnifier_image_uri"],
      "display_name": "methodmesh-magnifier-....jpg",
      "mime_type": "image/jpeg",
      "relative_path": "Pictures/MethodMesh",
      "sha256": "..."
    }
  ],
  "warnings": []
}
```

The published-media SHA-256 is calculated while MethodMesh copies the bytes into
MediaStore. It can be compared with the capability's `magnifier_image_sha256`.
For this capability those hashes should match because the MediaStore copy is a
byte-for-byte copy of the returned JPEG.

## Android scope

The alpha MediaStore publication path requires Android 10 (API 29) or later.
No broad storage permission is requested on Android 10+.

The existing ODK Collect path is unchanged. Collect still receives the original
`content://` result through Android Activity result/URI grants; browser media
publication occurs only for a browser `methodmesh://` launch that explicitly
requests `clipboard_media`.

## What this establishes

The browser bridge now has a generic transport contract:

- `input_browser_return=clipboard` — copy structured outputs to clipboard;
- `input_browser_return=clipboard_media` — copy structured outputs and publish
  returned `content://` media into the relevant Android public media collection.

That means Enketo does not need a fingerprint-specific or magnifier-specific
integration. The deep link chooses the normal MethodMesh `method_id`; the bridge
handles the browser boundary after the capability finishes.
