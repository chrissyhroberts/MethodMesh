# NFC tag operations

Reads NDEF evidence from NFC tags and performs explicit, verified NDEF writes.

## Capabilities

### `nfc_tag_read`

Captures the first physical tag once, decodes its NDEF records, and returns canonical tag evidence.

### `nfc_tag_write`

Writes an NDEF record while keeping the RF field active, then reads the message back and verifies its SHA-256 hash.

Overwrite policies:

- `empty_only` — safe default; rejects tags containing data.
- `replace` — explicitly replaces existing NDEF content.
- `compare_and_replace` — replaces only when `expected_current_hash` matches.

The capability never makes a tag permanently read-only.

### `nfc.provision`

Provisions a study credential in one controlled operation:

1. writes the requested NDEF credential record;
2. reads the NDEF message back while the tag remains in the NFC field;
3. verifies the complete written-message SHA-256;
4. returns the tag UID, exact first NDEF payload bytes, and canonical
   `verification_evidence_hash` used by signed NFC attestations.

`credential_id` is the registry identifier associated with the physical tag. It
is not written to the tag unless the caller also chooses it as `value`.

## Android intent

```text
com.example.researchos.EXECUTE_METHOD(method_id='nfc_tag_read')
```

```text
com.example.researchos.EXECUTE_METHOD(method_id='nfc_tag_write',input_value='participant_P001',input_record_type='text/plain',input_overwrite_policy='replace')
```

```text
com.example.researchos.EXECUTE_METHOD(method_id='nfc.provision',input_credential_id='credential_001',input_value='random-secret-token',input_record_type='external',input_mime_type='researchos:credential',input_overwrite_policy='empty_only')
```

## Inputs

NFC read has no required input. NFC write requires `value` and `record_type`; it accepts `overwrite_policy`, `expected_current_hash`, `mime_type`, and `language_code`.

NFC provisioning requires:

| Input | Meaning |
|---|---|
| `credential_id` | Study registry identifier for the physical credential. |
| `value` | Secret or opaque token written into the NDEF record. |

Provisioning also accepts `record_type`, `mime_type`, `language_code`,
`overwrite_policy`, and `expected_current_hash`. The recommended credential
record is `record_type='external'` with
`mime_type='researchos:credential'`.

The default `empty_only` policy refuses to overwrite a non-blank tag.
`replace` must be explicit. For controlled replacement,
`compare_and_replace` additionally requires the SHA-256 of the current complete
NDEF message in `expected_current_hash`.

The user must keep the tag against the device until writing and read-back verification complete.

## Outputs

Read evidence includes tag UID in hexadecimal and decimal, technology list, NDEF support/writability/capacity, record count, decoded text/URI/MIME/external types, payload representations, records JSON, and tag summary.

Write adds `write_success`, `write_message`, `write_record_type`, `write_size_bytes`, `overwrite_policy`, `previous_message_hash`, `written_message_hash`, and `write_verified`.

Read, write, and provision results expose the generic dependency fields:

```text
verification_evidence_format = nfc_uid_ndef_payload_sha256_v1
verification_evidence_hash = <SHA-256>
```

The evidence hash is reproduced by:

1. normalizing `tag_uid_hex` to uppercase hexadecimal;
2. decoding `ndef_first_payload_hex` to its raw bytes and hashing those bytes
   with SHA-256, or using the literal `NONE` when no first payload exists;
3. joining:

   ```text
   uid_hex=<UID>
   ndef_payload_sha256=<digest-or-NONE>
   ```

4. hashing that exact UTF-8 string with SHA-256.

Provisioning additionally returns `credential_id`, `provision_success`,
`provision_message`, `provisioned_time_iso`, the verified write fields, and the
complete NFC read-back fields. A credential registry should retain at least
`credential_id`, `tag_uid_hex`, `ndef_first_payload_hex`,
`verification_evidence_format`, and `verification_evidence_hash`.

## ODK example

[`example_odk_nfc_tag_read.xlsx`](example_odk_nfc_tag_read.xlsx) demonstrates canonical tag capture.

[`example_odk_nfc_tag_write.xlsx`](example_odk_nfc_tag_write.xlsx) demonstrates policy-controlled writing with read-back verification.

[`example_odk_nfc.provision.xlsx`](example_odk_nfc.provision.xlsx) demonstrates
credential registration, safe writing, read-back verification, and capture of
the fields required to reproduce later NFC-attestation evidence.
