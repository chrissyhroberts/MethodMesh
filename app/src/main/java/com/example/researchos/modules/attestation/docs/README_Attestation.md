# Signed event attestation

Cryptographically signs a caller-supplied event hash with the Android Keystore key, links the event into the device attestation chain, and optionally obtains an RFC 3161 trusted timestamp.

## Capabilities

### `attestation.create`

Creates one signed event attestation. QR and NFC verification are invoked through the generic capability-dependency boundary; this module contains no scanner or NFC-reader implementation.

### `attestation.anchor_bundle`

Returns a signed bundle describing the current chain head and unanchored records for submission to an external system such as ODK Central.

## Android intent

Every signed-event call needs the method, the caller-computed SHA-256 hash, a
verification method, and a timestamp policy. The compact fingerprint form is:

```text
com.example.researchos.EXECUTE_METHOD(method_id='attestation.create',event_payload_hash=${form_payload_hash},verification_method='Fingerprint',trusted_timestamp='preferred',return_mode='flat')
```

Use the same call with one of these verification controls:

```text
verification_method='Fingerprint'
verification_method='Pin'
verification_method='Qr'
verification_method='Nfc'
verification_method='Password',verification_evidence=${study_evidence_token}
```

`Pin` delegates to Android device credential, which may be a PIN, pattern, or
password. `Qr` and `Nfc` invoke the installed scanner/reader capabilities through
the generic dependency boundary. `Password` hashes the supplied study token
transiently; the token itself is not placed in the execution result or graph.

Timestamp policy accepts:

- `disabled` — local signature and chain only;
- `preferred` — try RFC 3161 and continue with the local attestation if unavailable;
- `required` — fail without adding a record unless RFC 3161 succeeds.

Create a chain anchor with:

```text
com.example.researchos.EXECUTE_METHOD(method_id='attestation.anchor_bundle',study_id='my_study',operator_id='operator_001',return_mode='flat')
```

## Inputs

| Input | Required | Description |
|---|---:|---|
| `event_payload_hash` | Yes | 64-character hexadecimal SHA-256 digest. It is signed directly and never re-hashed. |
| `verification_method` | Yes | Verification dependency or Android authenticator. |
| `trusted_timestamp` | No | `disabled`, `preferred`, or `required`. |
| `verification_evidence` | Password only | Evidence token used by the Password method. |
| `study_id`, `operator_id`, `subject_ref`, `event_type` | No | Optional signed metadata. Values protected inside the form hash need not be duplicated. |

The manual/debug screen supplies a clearly labelled deterministic placeholder hash. External calls never receive that placeholder.

## Outputs

Schema version 4 includes `attestation_id`, `event_payload_hash`,
`event_payload_mode`, `verification_method`, `verification_evidence_format`,
`verification_evidence_hash`, device time and monotonic counter,
`previous_attestation_hash`, `attestation_hash`, the public key and key
identifier, ECDSA signature fields, timestamp policy/status, and complete RFC
3161 evidence when obtained.

Raw QR, NFC and study-token credentials are never included in the attestation
record or caller-facing return.

### Verification evidence formats

| Method | `verification_evidence_format` | Reproduction rule |
|---|---|---|
| QR | `qr_payload_utf8_sha256_v1` | SHA-256 of the decoded QR payload as UTF-8 bytes. |
| NFC | `nfc_uid_ndef_payload_sha256_v1` | Normalize UID to uppercase hexadecimal; SHA-256 the first raw NDEF payload bytes (or use `NONE`); then SHA-256 `uid_hex=<UID>\nndef_payload_sha256=<digest-or-NONE>`. |
| Fingerprint | `android_biometric_result_sha256_v1` | SHA-256 of the successful Android biometric result label. |
| PIN/pattern/password | `android_device_credential_result_sha256_v1` | SHA-256 of the successful Android device-credential result label. |
| Study token | `study_token_utf8_sha256_v1` | SHA-256 of the supplied token as UTF-8 bytes. |

The signed canonical attestation contains both the caller's
`event_payload_hash` and `verification_evidence_hash`. A verifier independently
reconstructs both and then verifies the device signature. There is no need to
concatenate the credential with the complete form payload.

A static QR code or ordinary NFC tag remains bearer evidence: it proves that the
token was scanned, not that a named person was physically present. Studies that
need stronger identity assurance should combine token evidence with a biometric
or use a challenge-response credential.

The anchor capability returns the chain head, previous anchor, record count, bundle hash, public key, bundle signature, and device creation time.

## ODK example

The signed-event examples are based on the field-tested fingerprint form. Each
uses the same ODK-side SHA-256 calculation and complete return contract, with a
different verification dependency:

- [`example_odk_AttestationFingerprint.xlsx`](example_odk_AttestationFingerprint.xlsx)
- [`example_odk_AttestationPin.xlsx`](example_odk_AttestationPin.xlsx) — Android PIN, pattern, or password
- [`example_odk_AttestationQr.xlsx`](example_odk_AttestationQr.xlsx)
- [`example_odk_AttestationNfc.xlsx`](example_odk_AttestationNfc.xlsx)
- [`example_odk_AttestationPassword.xlsx`](example_odk_AttestationPassword.xlsx) — caller-supplied study evidence token

Each form lets the tester choose `disabled`, `preferred`, or `required`
timestamping. [`example_odk_AttestationAnchor.xlsx`](example_odk_AttestationAnchor.xlsx)
creates and returns an anchor bundle for the current device chain.

### Important group-intent rule

ODK Collect sends the text and numeric fields inside a `field-list` intent group
as input extras as well as using them as return targets. If a child field has the
same name as a parameter in `body::intent`, Collect overrides the explicit
parameter with the child field's current value. A blank return placeholder can
therefore erase a valid request value before ResearchOS is launched.

The examples deliberately keep these request-only names out of the return group:

```text
study_id
event_type
event_payload_hash
verification_method
```

The ODK-calculated hash remains stored outside the return group as
`event_payload_hash`, while the
signed result returns the non-duplicative evidence fields needed to verify it:
`verification_evidence_format`, `verification_evidence_hash`, `attestation_hash`,
`previous_attestation_hash`, public-key/signature fields, and optional RFC 3161
evidence. Failed executions populate `diagnostic_reason`.
