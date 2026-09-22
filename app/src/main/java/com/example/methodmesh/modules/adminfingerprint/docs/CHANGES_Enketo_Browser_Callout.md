# Enketo browser biometric call-out changes

## v1.1 / test v3

The browser capability now completes a practical stock-Enketo round trip.

- `admin_browser_biometric_callout` remains biometric-only and makes no person-identity claim.
- After a successful biometric event it issues a device-signed `MMBV1` verification token.
- The token is copied automatically to the Android clipboard.
- The XLSForm now includes a required `verification_token` field, so there is an explicit destination for the returned proof.
- The token uses a persistent Android-Keystore P-256 signing key; the MethodMesh result exposes the key ID and public key for later backend verification.
- The public Intent router uses an empty task affinity so the transient browser call-out closes back to the browser rather than exposing an existing MethodMesh main-screen task.
- ODK/Collect behaviour is unchanged; its existing `EXECUTE_METHOD` result extras remain available.

## Integration boundary

Stock Enketo does not expose an Android `ActivityResult` bridge to arbitrary companion applications. v3 therefore uses a clipboard hand-off rather than claiming automatic field population. No custom Enketo JavaScript or deployment modification is required.


## v4

- Browser call-out remains biometric-only.
- Clarifies that stock Enketo cannot accept a native Activity result or automatic native-to-field write.
- Successful proof remains copied to Android clipboard for explicit paste.
- Main app `IntentRouterActivity` now records the calling browser package and brings that browser back to the foreground after the transient workflow completes; this avoids falling through to an older MethodMesh Home activity.
