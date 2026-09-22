# NFC credential overwrite validation

Expected behaviours for `nfc_credential_provisioning` v1.0.1:

1. `empty_only` + empty writable NDEF tag -> provisioning may proceed.
2. `empty_only` + existing meaningful NDEF -> block before PIN/write and do not alter the tag.
3. `replace` + existing meaningful writable NDEF -> accept the card and overwrite it after explicit confirmation.
4. `replace` does not bypass writability or capacity checks.
5. Result must report `overwrite_policy=replace` and retain `previous_message_hash`.
6. ODK/external `input_overwrite_policy='replace'` is displayed and honoured as supplied.
7. Unknown non-blank overwrite policies are rejected; they do not silently become `empty_only`.
