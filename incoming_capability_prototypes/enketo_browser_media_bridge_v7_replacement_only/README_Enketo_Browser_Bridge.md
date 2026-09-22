# Generic Enketo/browser bridge alpha v7

A stock Enketo form can launch a MethodMesh capability through a user-initiated
`methodmesh://` deep link without modifying the Enketo installation.

For browser launches the transport now automatically:

1. runs the requested MethodMesh capability;
2. copies the structured result JSON to the Android clipboard;
3. if the result contains `content://` media, publishes it to Android MediaStore;
4. removes the transient MethodMesh task so Android reveals the existing browser
   task/tab underneath rather than launching a new browser tab.

Explicit `input_browser_return=clipboard` and
`input_browser_return=clipboard_media` remain supported. `input_browser_return=none`
disables browser return processing. If no mode is supplied, media mode is inferred
when the result contains media.

Media locations on Android 10+:

- images: `Pictures/MethodMesh`
- video: `Movies/MethodMesh`
- audio: `Music/MethodMesh`
- other files: `Download/MethodMesh`

Stock Enketo still cannot accept an Android Activity result or allow a native app
to programmatically fill an HTML file input. The user therefore pastes the JSON
into a text question and selects returned media through the normal Android picker.
