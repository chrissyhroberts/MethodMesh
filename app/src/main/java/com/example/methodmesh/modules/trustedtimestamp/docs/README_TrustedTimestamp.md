# Trusted timestamp

## Capabilities

`integrity.trusted_timestamp` (**Development**) creates an RFC 3161 proof that exact bytes existed no later than a Timestamp Authority-certified time.

SHA-256 is computed locally. The source content itself is not sent to the TSA. The RFC 3161 request contains the message imprint and protocol metadata.

The portable evidence is one ordinary ZIP:

```text
Original_file_name_proof_of_existence.zip
```

Every proof ZIP includes:

```text
README.txt
proof.json
timestamp.tsq
timestamp.tsr
tsa-signing-cert.pem    # when available
tsa-root.pem            # when available
verify.sh
verify.ps1
```

Text input additionally includes the exact `timestamped-text.txt`.

The proof establishes existence of the exact bytes **no later than** the trusted timestamp. It does not establish authorship, original creation time, truth, photographic authenticity or legal validity.

### Native workflow

Choose a file or enter text, then press **Create proof of existence**. MethodMesh shows the proof ZIP as the main result with compact supporting fields. The ZIP is held in app cache only as the working copy. The result screen exposes three file operations which act on the ZIP bytes themselves:

- **Share proof ZIP** sends the ZIP as an `application/zip` attachment with URI permission;
- **Save proof ZIP…** uses Android Storage Access Framework and preserves the proof filename;
- **Export ZIP to Downloads** copies the ZIP itself to `Downloads/MethodMesh` on Android 10+, preserving the proof filename. Android 8/9 falls back to the Storage Access Framework rather than requesting broad legacy storage permission.

The cache URI is therefore an implementation transport, not the user-facing deliverable.

### Preset workflow

`tsa_url` and `timeout_ms` are configuration. `input_text` may be a fixed preset value or a runtime input. Fixed preset values are hidden with `CapabilityScreenContext.settingShouldBeShown()`.

### ODK / XLSForm workflow

ODK supplies `input_text` and the capability starts immediately. ODK does not depend on the native save dialog.

The returned media result is `trusted_timestamp_proof_uri`. The form may also store the proof filename, trusted time, SHA-256, authority, trust status and optional full JSON.

## Android intent

```text
com.example.methodmesh.EXECUTE_METHOD(method_id='integrity.trusted_timestamp',input_text=${input_text},input_include_full_json='true',return_mode='flat')
```

## Inputs

| Field | Class | Type | Required | Default | Policy |
|---|---|---|---:|---|---|
| `input_text` | runtime | string | no* | none | Exact UTF-8 text; no Unicode normalisation. |
| native file picker | runtime | file URI | no* | none | Any readable Android document URI. |
| `input_tsa_url` | configuration | string | no | `https://freetsa.org/tsr` | RFC 3161 endpoint. |
| `input_timeout_ms` | configuration | integer | no | `10000` | 1000–30000 ms. |
| `input_include_full_json` | configuration | boolean | no | `false` | Returns `trusted_timestamp_full_json`. |

\* One source is required.

## Outputs

### Main result

| Field | Meaning |
|---|---|
| `trusted_timestamp_proof_uri` | Shareable URI of the generated proof ZIP. |
| `trusted_timestamp_proof_filename` | Suggested proof ZIP filename. |

### Core supporting outputs

| Field | Meaning |
|---|---|
| `trusted_timestamp_time_iso` | TSA-certified timestamp. |
| `trusted_timestamp_sha256` | SHA-256 of the exact source bytes. |
| `trusted_timestamp_authority` | TSA identity. |

### Audit-priority outputs

| Field | Meaning |
|---|---|
| `trusted_timestamp_status` | `succeeded` or `failed`. |
| `trusted_timestamp_original_name` | Source name. |
| `trusted_timestamp_size_bytes` | Exact byte count. |
| `trusted_timestamp_serial` | RFC 3161 serial. |
| `trusted_timestamp_policy_oid` | TSA policy OID. |
| `trusted_timestamp_token_sha256` | SHA-256 of timestamp token. |
| `trusted_timestamp_trust_status` | Registry/trust evaluation. |
| `trusted_timestamp_error` | Failure explanation. |

### Full audit JSON

`trusted_timestamp_full_json` is populated only when `input_include_full_json=true`.

The `proof.json` inside the proof ZIP remains the richer portable evidence manifest.

## ODK example

`example_odk_integrity.trusted_timestamp.xlsx` is a real XLSForm containing `survey`, `choices` and `settings` sheets. Its `settings.form_title` is exactly `integrity.trusted_timestamp`.

The MethodMesh intent is on a `field-list` group. The workbook supplies `input_text` and stores the proof URI and filename, trusted timestamp, SHA-256, authority, trust status, status/error and full JSON.

## Permissions and services

Network access is required to obtain a new timestamp and retrieve configured certificate material.

File selection and explicit durable saving use Android Storage Access Framework. The generated proof ZIP uses the app's existing `FileProvider` for a shareable cache URI. No broad storage permission is required.

Default TSA: FreeTSA (`https://freetsa.org/tsr`).

## Online / offline behaviour

New trusted timestamps require an online independent TSA. Hashing is local. Existing proof ZIPs are standards-based and can be independently inspected or verified later without MethodMesh.

There is no fabricated offline timestamp fallback.

## Known limitations

- Development status: requires Android build and device validation before Production.
- This capability creates proofs; a dedicated in-app proof-verification method is not yet exposed.
- Custom TSA endpoints can be cryptographically consistent without being independently trusted.
- TSA certificate rotation requires registry maintenance.
- The proof concerns exact bytes and certified time, not the broader truth or legal meaning of the content.
