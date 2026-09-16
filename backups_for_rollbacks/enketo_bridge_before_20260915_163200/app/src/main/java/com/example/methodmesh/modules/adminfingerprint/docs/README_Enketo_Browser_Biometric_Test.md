# Enketo browser biometric call-out test — v4

This test exercises a stock Enketo → MethodMesh → Android biometric → stock Enketo round trip without custom JavaScript or changes to the Enketo deployment.

## What v4 does

1. Enketo renders an ordinary `text` question with `appearance=url`.
2. A user tap launches the existing `methodmesh://` BROWSABLE deep-link route.
3. MethodMesh immediately opens `admin_browser_biometric_callout`.
4. Android performs biometric authentication only; device credential fallback is disabled.
5. On success MethodMesh creates a device-signed `MMBV1` proof token and copies it to the clipboard.
6. The MethodMesh router explicitly brings the calling browser back to the foreground, rather than allowing an older MethodMesh Home activity to become visible.
7. The user pastes the token into the ordinary `verification_token` text field in Enketo.

Stock Enketo cannot receive an Android `Activity` result or allow a different Android app to write directly into a live form control. The clipboard is therefore the zero-Enketo-modification return channel for this version. The `verification_token` field will **not** populate automatically: the user must paste the copied token.

## Files

- `example_enketo_browser_biometric_callout.xlsx` — upload to Kobo/ODK Central and open in Enketo on Android Chrome.
- `BrowserBiometricVerificationToken.kt` — Android-Keystore signing for the proof token.

## Browser URI used by the XLSForm

```text
intent://execute?method_id=admin_browser_biometric_callout&input_caller=enketo&input_confirmation_reason=enketo_browser_test#Intent;scheme=methodmesh;end
```

Chrome resolves this to MethodMesh's existing `methodmesh://` VIEW/BROWSABLE route. The ODK `com.example.methodmesh.EXECUTE_METHOD` route remains separate and does not need to be BROWSABLE.

## Expected behaviour

1. Install a MethodMesh build containing this v4 module and the v4 `IntentRouterActivity` browser-return logic.
2. Upload the example XLSForm and open it in Enketo on the Android device.
3. Tap **Verify with MethodMesh**.
4. MethodMesh should open directly to the Android biometric prompt.
5. Authenticate successfully.
6. MethodMesh should copy a token beginning `MMBV1.`.
7. The calling browser should be brought back to the foreground; MethodMesh Home should not appear.
8. Enketo should still contain its previous answers.
9. **Manually paste** the clipboard value into **Biometric verification token**. Automatic population is not expected in stock Enketo.

## Token semantics

The token format is:

```text
MMBV1.<base64url-json-payload>.<base64url-ecdsa-signature>.<key-id>
```

The payload records successful local-device biometric verification, the caller, request reference (when supplied), reason, authentication method, timestamp and a unique nonce. It explicitly records `identity_claimed=false`.

The signature is ES256-style ECDSA using a persistent P-256 key generated in Android Keystore. The private key never leaves the device. The capability also exposes the key ID and SPKI public key in its MethodMesh result so a study/backend can register the device key and later verify pasted tokens.

A token therefore means **MethodMesh on the signing device recorded successful local biometric verification**. It does not establish which enrolled human biometric matched.

## Android task behaviour

The public `IntentRouterActivity` should use an empty task affinity and be excluded from Recents. This prevents a browser call-out from returning to a previously open MethodMesh `MainActivity` when the transient workflow finishes:

```xml
<activity
    android:name=".transport.android.IntentRouterActivity"
    android:exported="true"
    android:excludeFromRecents="true"
    android:taskAffinity=""
    android:label="MethodMesh Intent Router"
    android:theme="@style/Theme.MethodMesh">
```

This does not change the ODK result path: an ODK `startActivityForResult` launch can still receive the normal MethodMesh Intent extras.
