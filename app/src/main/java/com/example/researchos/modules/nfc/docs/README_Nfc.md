# NFC capabilities

The NFC module provides four independent capabilities:

- `nfc_tag_read` — read any supported NFC/NDEF payload;
- `nfc_tag_write` — write any supported NFC/NDEF payload;
- `nfc_tag_wipe` — replace NDEF user content with a verified empty record;
- `nfc_credential_provisioning` — create a portable, PIN-protected credential;
- `nfc_credential_verification` — verify that credential and its PIN.

The credential capabilities do not replace generic read/write. They add a
defined cryptographic format and a guided human workflow.

## Capabilities

### `nfc_credential_provisioning`

Provisioning is suitable for field-team identity cards and other portable
study credentials.

The workflow is:

1. tap the card so ResearchOS can inspect it;
2. enter and confirm a 4- or 6-digit PIN inside ResearchOS;
3. tap the same card again;
4. ResearchOS writes the encrypted credential and verifies the read-back.

The PIN is never stored in ODK, returned by the capability, or written to the
card. ResearchOS derives an AES-256 key from the PIN with Argon2id and a random
salt. The encrypted credential is authenticated with AES-GCM and signed by the
provisioning device's issuer key.

### `nfc_credential_verification`

Verification works on another compatible phone without copying the PIN or a
credential registry to that phone:

1. tap the credential;
2. ResearchOS verifies the embedded issuer signature;
3. enter the cardholder's PIN inside ResearchOS;
4. successful AES-GCM decryption proves knowledge of the PIN and returns the
   credential subject.

### `nfc_tag_read`, `nfc_tag_write`, and `nfc_tag_wipe`

These retain the generic ability to read or write caller-supplied NDEF data
without applying the portable-credential format. Wipe removes NDEF user
content; it does not claim forensic erasure of physical chip memory.

## Android intent

Provisioning:

```text
com.example.researchos.EXECUTE_METHOD(method_id='nfc_credential_provisioning',input_credential_subject_id='operator_001',input_pin_length='6',input_overwrite_policy='empty_only',return_mode='flat')
```

Verification:

```text
com.example.researchos.EXECUTE_METHOD(method_id='nfc_credential_verification',return_mode='flat')
```

Generic read and write:

```text
com.example.researchos.EXECUTE_METHOD(method_id='nfc_tag_read')
```

```text
com.example.researchos.EXECUTE_METHOD(method_id='nfc_tag_write',input_value='participant_P001',input_record_type='application/x-participantid',input_overwrite_policy='replace')
```

```text
com.example.researchos.EXECUTE_METHOD(method_id='nfc_tag_wipe',return_mode='flat')
```

## Inputs

Provisioning inputs:

| Input | Required | Meaning |
|---|---:|---|
| `credential_subject_id` | yes | Identifier recovered only after successful PIN verification. |
| `credential_id` | no | Credential identifier; ResearchOS generates one when omitted. |
| `pin_length` | no | `4` or `6`; default `6`. |
| `overwrite_policy` | no | `empty_only` (default), `replace`, or `compare_and_replace`. |
| `expected_current_hash` | conditional | Required for `compare_and_replace`. |

## Outputs

Provisioning outputs include:

```text
credential_id
credential_subject_id
pin_length
credential_format_version
key_derivation
credential_issued_time_iso
credential_envelope_hash
credential_secret_hash
issuer_key_id
issuer_public_key_base64
issuer_signature_algorithm
provision_success
written_message_hash
write_verified
tag_uid_hex
verification_evidence_hash
```

The encrypted credential envelope and PIN are deliberately omitted from the
default return.

An optional issuer allow-list can be supplied:

```text
com.example.researchos.EXECUTE_METHOD(method_id='nfc_credential_verification',input_trusted_issuer_key_ids='8303b9580fc54502,another_key_id',return_mode='flat')
```

Verification outputs include:

```text
credential_verified
credential_verification_message
credential_id
credential_subject_id
pin_verified
issuer_signature_valid
issuer_trust_status
credential_envelope_hash
credential_secret_hash
issuer_key_id
credential_verified_time_iso
tag_uid_hex
verification_evidence_hash
```

`issuer_signature_valid=true` proves that the credential has not been altered
since it was signed. `issuer_trust_status=trusted` additionally proves that the
issuer key ID matched the caller's allow-list. When no allow-list is supplied,
the status is `not_checked`; the signature is still cryptographically checked,
but study governance has not independently vouched for that issuer key.

Verification allows five PIN attempts per scan session. It never returns the
PIN, random credential secret, decryption key, or encrypted envelope.

## Portable credential format

The on-card record is an NDEF external record of type:

```text
researchos:portable-credential
```

The `ROSC1` envelope contains:

- credential ID and PIN length;
- random Argon2id salt;
- AES-GCM nonce and ciphertext;
- compressed P-256 issuer public key and issuer key ID;
- ECDSA issuer signature.

The encrypted content contains the credential subject ID, a random 256-bit
credential secret, and issue time. A wrong PIN fails authenticated decryption.
Changing any signed envelope component invalidates the issuer signature.

The compact envelope is designed to fit a typical 492-byte NDEF credential
tag when identifiers are reasonably short. ResearchOS reports the required and
available sizes if a card is still too small.

## Generic NFC read and write

Overwrite policies:

- `empty_only` rejects meaningful existing content but accepts a
  factory-formatted empty NDEF/text record;
- `replace` explicitly replaces existing NDEF content;
- `compare_and_replace` writes only when `expected_current_hash` matches.

The capability never makes a tag permanently read-only. A generic write keeps
the RF field active, reads the message back, and compares the complete NDEF
message hash. In the debug screen, hold the written-message hash to copy it.

Wipe returns `wipe_success`, `wipe_message`, `wiped_time_iso`,
`previous_message_hash`, the verified empty-message hash, and tag UID.

## ODK example

- [`example_odk_nfc_tag_read.xlsx`](example_odk_nfc_tag_read.xlsx)
- [`example_odk_nfc_tag_write.xlsx`](example_odk_nfc_tag_write.xlsx)
- [`example_odk_nfc_tag_wipe.xlsx`](example_odk_nfc_tag_wipe.xlsx)
- [`example_odk_nfc_credential_provisioning.xlsx`](example_odk_nfc_credential_provisioning.xlsx)
- [`example_odk_nfc_credential_verification.xlsx`](example_odk_nfc_credential_verification.xlsx)

Each example's filename and `form_title` use the underlying capability name.
