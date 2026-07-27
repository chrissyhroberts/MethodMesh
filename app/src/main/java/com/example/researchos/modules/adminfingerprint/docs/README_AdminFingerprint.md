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
com.example.researchos.EXECUTE_METHOD(method_id='admin_fingerprint_confirmation',input_authentication_method='device_credential')
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
