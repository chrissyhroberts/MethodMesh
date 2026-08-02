# NFC capabilities

The NFC module provides generic tag operations, portable credentials, and an
offline protocol-card workflow:

- `nfc_tag_read` — read any supported NFC/NDEF payload;
- `nfc_tag_write` — write any supported NFC/NDEF payload;
- `nfc_tag_wipe` — replace NDEF user content with a verified empty record;
- `nfc_credential_provisioning` — create a portable, PIN-protected credential;
- `nfc_credential_verification` — verify that credential and its PIN;
- `protocol_nfc_provision` — establish the initial protocol receipt during recruitment;
- `protocol_nfc_check` — decide whether a form step is currently eligible;
- `protocol_nfc_complete` — mark a completed step and verify the write;
- `protocol_nfc_reconstruct` — restore a lost/replacement card from an exported state payload;
- `protocol_nfc_override` — apply a justified manual flag or completion-bit change.

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

## Protocol NFC tracking

Protocol NFC tracking is an offline progress receipt for participant study
cards. It is separate from the scheduler: the card carries a compact protocol
state so ResearchOS can decide whether a form is currently allowed without a
network connection. ODK Collect, KoboCollect, or another study system remains
the canonical repository for form data. The operator's NFC/PIN/fingerprint
attestation remains the formal end-of-form attribution step; this capability
does not duplicate it.

Before a form, call `protocol_nfc_check`. After successful submission, call
`protocol_nfc_complete`. Completion checks the expected state, sets the step's
completion bit, preserves unrelated NDEF records (including credentials), and
verifies the write by reading the card back. A failed check or write does not
advance the card.

An unprovisioned card is never treated as an empty eligible protocol. The
recruitment workflow should first call `protocol_nfc_provision`, which writes
the initial receipt and reads it back. If a card is lost, export the last
trusted `protocol_state_payload` and its SHA-256 from a prior check/complete
result, then call `protocol_nfc_reconstruct` on the replacement card with a
human-readable `reconstruction_reason`. This preserves unrelated NDEF records
and requires verified read-back. `protocol_nfc_override` is the manual
exception path: it can set or clear active flags and completion bits, but
requires `override_justification` and returns `protocol_deviation=true`.

### Defining a protocol

Provisioning accepts either individual bit-count/initial-state settings or a
portable `protocol_definition_json` document. The standalone screen can load
that document from a file in device-accessible storage, or build it with
readable rows:

```text
Active flags:  bit | code | label | severity
0 | contraindication | Contraindication | BLOCKING
1 | failed_attempt   | Failed attempt   | WARNING

Protocol steps:  id | bits | label | required expression
consent | 01 | Consent |
form_2  | 02 | Form 2  | ALL(01)
```

The equivalent file is JSON:

```json
{"protocol_id":"study_x","protocol_version":"1","flag_bit_count":8,"completion_bit_count":8,"flags":[{"bit":0,"code":"contraindication","label":"Contraindication","severity":"BLOCKING"}],"steps":[{"id":"consent","bits":"01","label":"Consent","required_expression":""}]}
```

The definition is not written into the tag. The tag stores only the compact
state receipt; `protocol_definition_hash` identifies the exact definition used
for provisioning, so retain the original file with the study configuration.

## Protocol NFC Android intents

```text
com.example.researchos.EXECUTE_METHOD(method_id='protocol_nfc_check',input_protocol_id='study_x',input_protocol_version='1',input_step_id='baseline',input_required_bits='00',input_required_value='00',input_completion_bits='01',return_mode='flat')
```

```text
com.example.researchos.EXECUTE_METHOD(method_id='protocol_nfc_complete',input_protocol_id='study_x',input_protocol_version='1',input_step_id='baseline',input_required_bits='00',input_required_value='00',input_completion_bits='01',return_mode='flat')
```

Provisioning:

```text
com.example.researchos.EXECUTE_METHOD(method_id='protocol_nfc_provision',input_protocol_id='study_x',input_protocol_version='1',input_flag_bit_count='8',input_completion_bit_count='8',input_initial_flag_bits='00',input_initial_completion_bits='00',input_overwrite_policy='empty_only',return_mode='flat')
```

Replacement-card reconstruction:

```text
com.example.researchos.EXECUTE_METHOD(method_id='protocol_nfc_reconstruct',input_protocol_state_payload=${saved_state_payload},input_protocol_state_payload_hash=${saved_state_hash},input_reconstruction_reason='Card lost; replacement issued at visit 3',return_mode='flat')
```

Justified manual override:

```text
com.example.researchos.EXECUTE_METHOD(method_id='protocol_nfc_override',input_protocol_id='study_x',input_set_flag_bits='04',input_clear_completion_bits='0002',input_override_justification='Contraindication confirmed by investigator',return_mode='flat')
```

The protocol receipt is split into two configurable regions:

```text
[ active flag bits ][ completed-step bits ]
```

Set `input_flag_bit_count` and `input_completion_bit_count` once for a
protocol version. The card stores the widths and remains self-describing.
Nothing is hard-coded as “contraindication” or “form 2”: the study definition
assigns any names, labels, severities, or step meanings to the bit positions.
The debug settings accept compact definitions such as
`input_flag_definitions='0=contraindication;2=failed_attempt'` and
`input_step_definitions='0=consent;1=form_2;2=form_3'`. These names are labels
for the returned evidence; the card still stores only bits.

For a later step requiring bit `01` and setting bit `02`, use
`input_required_bits='01',input_required_value='01',input_completion_bits='02'`.
For Boolean requirements, use expressions such as
`input_required_expression='ALL(0001,0002)'`,
`input_required_expression='ANY(0002,0004)'`, or
`input_required_expression='NONE(0004)'`.

Protocol outputs include `protocol_allowed`, `protocol_reason`,
`active_flag_bits`, `completion_bits_state`, `protocol_state_bits`,
`protocol_state_version`, `protocol_state_hash`,
`protocol_updated_time_iso`, `protocol_write_verified`, `protocol_operation`,
`protocol_provisioned`, `protocol_state_payload`, `protocol_state_payload_hash`,
`protocol_state_source`, `protocol_definition_hash`, `reconstruction_reason`,
`override_justification`, `protocol_deviation`, `tag_uid_hex`, and
`ndef_message_sha256`. A normal NFC tag is not itself a
trusted issuer, so this receipt is not a signed credential. Bind it to the
formal end-of-form attestation when a stronger audit record is required.

## ODK example

`example_odk_protocol_nfc_check.xlsx` demonstrates a pre-form eligibility
check. `example_odk_protocol_nfc_complete.xlsx` demonstrates the post-submission
progress update. The same intent pattern can be used for provisioning,
reconstruction, and override; those administrative operations are primarily
intended to be run from the ResearchOS builder/file workflow because they carry
larger protocol configuration and audit justification. Replace the example
protocol ID, step IDs, and bit masks with the study's protocol map.

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
