# Enketo browser biometric bridge alpha

This example exercises the shared stock-browser/Enketo bridge with the
`admin_browser_biometric_callout` capability. It requires no custom JavaScript or
changes to the Enketo deployment.

## Workflow

1. Enketo renders an ordinary `text` question with `appearance=url`.
2. A user tap opens MethodMesh through the existing `methodmesh://` BROWSABLE route.
3. MethodMesh runs `admin_browser_biometric_callout` and requests a biometric only.
4. On successful Commit the capability returns its normal canonical fields, including a device-signed `MMBV1` verification token.
5. The shared browser bridge copies the projected result as versioned JSON to the Android clipboard.
6. The transient MethodMesh task moves behind the existing browser task and closes, revealing the same live Enketo tab.
7. The user pastes the JSON into the ordinary `methodmesh_browser_result` field.

Stock Enketo cannot receive an Android `Activity` result or permit another native
application to write directly into a live form control. Paste is therefore
user-mediated. The fingerprint capability does not own clipboard or browser-task
behaviour; those are shared transport concerns.

## Example URI

```text
intent://execute?method_id=admin_browser_biometric_callout&input_caller=enketo&input_confirmation_reason=enketo_browser_test&input_browser_return=clipboard#Intent;scheme=methodmesh;end
```

The browser route is separate from the ODK/Collect
`com.example.methodmesh.EXECUTE_METHOD` Activity-result path.

## Expected clipboard result

The pasted JSON begins with `methodmesh_browser_return_v: 1`. Its `fields` object
contains the projected capability returns, including `verification_token`,
`verification_key_id` and `verification_public_key_spki_base64url` on successful
verification.

The token format is:

```text
MMBV1.<base64url-json-payload>.<base64url-ecdsa-signature>.<key-id>
```

The token means MethodMesh on the signing device recorded successful local
biometric verification for that call. It explicitly carries
`identity_claimed=false`; it does not identify which enrolled human biometric
matched.

## Contract checks

- the exact existing Enketo tab returns after completion; MethodMesh Home must not appear;
- no new browser tab is opened;
- `input_browser_return=none` suppresses clipboard return while still returning to the browser;
- the ODK result path remains unchanged and continues to use Intent extras/ClipData;
- browser task-navigation flags are stripped only at the internal browser workflow boundary, while non-browser callers retain their original Intent contract.
