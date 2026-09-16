# Local device authentication

Local, unsigned access authorisation through Android. This capability confirms that Android accepted an enrolled authenticator on the device. It does **not** identify which enrolled person authenticated and is not a substitute for a signed event attestation.

## Capabilities

### `admin_fingerprint_confirmation`

Opens Android authentication and returns a transient local-access result. Supported policies are:

- `biometric`
- `device_credential` — Android PIN, pattern, or password
- `biometric_or_device_credential`

Android reports PIN, pattern, and password collectively as `device_credential`.

## Android intent

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='admin_fingerprint_confirmation',input_authentication_method='device_credential')
```

External calls open the requested Android prompt immediately and return automatically. Manual/debug execution requires **Use result**.

## Inputs

| Input | Required | Description |
|---|---:|---|
| `authentication_method` | No | Defaults to `biometric`; accepts the three policies above. |
| `prompt_title` | No | Android prompt title. |
| `prompt_subtitle` | No | Android prompt subtitle. |
| `prompt_description` | No | Explanation displayed in the Android prompt. |
| `confirmation_reason` | No | Caller-defined reason; defaults to `local_access_authorisation`. |
| `confirmation_required` | No | Whether Android should require explicit confirmation. |

## Outputs

`confirmed`, `verification_status`, `auth_method`, `authentication_policy`, `assurance_scope`, `identity_claimed`, `timestamp_ms`, `timestamp_iso`, `reason`, `message`, `biometric_device_service`, `biometric_signal_type`, `biometric_execution_id`, and `biometric_provenance_json`.

`assurance_scope` is `local_device_access` and `identity_claimed` is always `false`.

## ODK example

[`example_odk_admin_fingerprint_confirmation.xlsx`](example_odk_admin_fingerprint_confirmation.xlsx) demonstrates biometric-only, device-credential-only, and either-method access calls.

## `admin_browser_biometric_callout`

Experimental biometric-only entry point intended for a browser or unmodified Enketo form.

The capability deliberately does **not** claim that a stock Enketo deployment can receive an Android activity result. Its purpose is the narrower interaction:

1. the respondent taps a link in Enketo;
2. Android opens MethodMesh;
3. MethodMesh requests an enrolled biometric;
4. the respondent returns to the still-open browser form.

Inputs:

- `caller` — defaults to `browser`; use `enketo` for the example form;
- `request_ref` — optional caller-generated reference for diagnostics/audit correlation;
- `confirmation_reason` — defaults to `browser_biometric_callout`.

The authentication method is fixed to biometric-only. Device PIN/pattern/password fallback is intentionally excluded from this capability.

### Browser dispatch requirement

The Android activity that receives `com.example.methodmesh.EXECUTE_METHOD` must be browser-addressable (`android.intent.category.BROWSABLE`) for Chrome to launch it from an `intent:` link. This is an app-shell/manifest concern rather than an Enketo change. Chrome requires a user gesture and a resolvable BROWSABLE activity for external-app launch.

See `enketo_browser_callout_manifest_snippet.xml` and `example_enketo_browser_biometric_callout.xlsx`.
