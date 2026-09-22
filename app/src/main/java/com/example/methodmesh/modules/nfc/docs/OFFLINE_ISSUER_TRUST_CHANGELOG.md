# NFC offline issuer trust — 2026-09-22c

- ROSC1 on-card credential format is unchanged.
- `nfc_credential_provisioning` -> `1.1.0`.
- `nfc_credential_verification` -> `1.1.0`.
- new `nfc_issuer_identity` capability -> `1.0.0`.
- provisioning now returns `issuer_public_key_fingerprint_sha256`.
- verification recomputes that full fingerprint from the embedded public key.
- preferred trust input is `trusted_issuer_fingerprints_sha256`.
- ODK supplies optional `issuer_trust_set_id` and `issuer_trust_set_version`.
- verification returns `issuer_trust_status`, `issuer_trust_basis`, trust-set ID/version, and the full issuer fingerprint.
- legacy `trusted_issuer_key_ids` remains supported only for backwards compatibility.
- trust rejection is terminal and does not consume a PIN retry.
- no MethodMesh-to-Sentinel or MethodMesh-to-server communication is introduced.
- canonical and showcase provisioning/verification XLSX/XML examples are updated, and an issuer-identity XLSX/XML example is added.
