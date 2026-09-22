# Generic Enketo/browser bridge alpha

Browser deep links already enter MethodMesh through `IntentRouterActivity`.
The return boundary is different from ODK Collect because stock Enketo is not
waiting for an Android Activity result.

For browser deep links, callers can opt into:

- `input_browser_return=clipboard`
- `input_browser_return=clipboard_media`

`clipboard` copies the capability result as JSON to the Android clipboard.
`clipboard_media` additionally discovers returned `content://` fields and
copies those bytes into MediaStore. Image output goes to `Pictures/MethodMesh`,
video to `Movies/MethodMesh`, audio to `Music/MethodMesh`, and other files to
`Downloads/MethodMesh`.

The user can then paste the structured payload and select the published media
from the ordinary Enketo attachment picker. This is intentionally generic: the
requested `method_id` remains the normal MethodMesh capability ID.

The bridge is opt-in and runs only for `methodmesh://` browser deep links, so it
does not change ODK/Collect Activity-result semantics.
