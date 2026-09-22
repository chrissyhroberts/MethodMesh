# NFC offline issuer-trust validation — 2026-09-22c

## Architecture under test

MethodMesh performs no runtime communication with Sentinel or any other server.
ODK supplies a versioned study trust set to `nfc_credential_verification`.
MethodMesh verifies the embedded ECDSA signature, reconstructs the issuer public
key, hashes the X.509/SPKI public key with SHA-256, and compares the resulting
64-hex fingerprint with the supplied allow-list.

The 16-hex `issuer_key_id` remains part of ROSC1 and is still checked against the
first 16 hex characters of the recomputed fingerprint. It is retained as a
short identifier and legacy trust input, not the preferred production trust
anchor.

## Required cases

1. **Issuer identity export**
   - `nfc_issuer_identity` succeeds without NFC or network access.
   - `issuer_public_key_fingerprint_sha256` is exactly 64 hexadecimal characters.
   - `issuer_key_id` equals the first 16 characters of the full fingerprint.
   - repeated exports on the same installation return the same key/fingerprint.
   - the private key is never returned.

2. **Provisioning provenance**
   - provisioning `1.1.0` returns the same full fingerprint as issuer identity.
   - the on-card format remains `ROSC1`.
   - existing cards remain verifiable.

3. **Trusted full fingerprint**
   - ODK supplies the credential issuer fingerprint in
     `trusted_issuer_fingerprints_sha256`.
   - verification succeeds with `issuer_signature_valid=true`,
     `issuer_trust_status=trusted`, `issuer_trust_basis=public_key_sha256`, and
     `pin_verified=true` after the correct PIN.
   - supplied trust-set ID/version are returned unchanged and retained in
     `methodmesh_full_json`.

4. **Untrusted issuer**
   - ODK supplies a valid but different 64-hex fingerprint.
   - signature validation may succeed, but verification returns a failed result
     with `issuer_trust_status=untrusted` and
     `issuer_trust_basis=public_key_sha256`.
   - the failure does not consume a PIN-attempt counter because trust rejection
     occurs before PIN decryption.

5. **No trust set**
   - with no fingerprints or legacy short IDs supplied, cryptographic signature
     and PIN verification still work.
   - output is `issuer_trust_status=not_checked` and
     `issuer_trust_basis=not_checked`.

6. **Multiple issuers**
   - a comma/semicolon/whitespace-separated list of full fingerprints is
     accepted.
   - any exact fingerprint match is trusted.

7. **Malformed trust configuration**
   - a non-empty fingerprint that is not exactly 64 hexadecimal characters is
     rejected before credential scanning rather than silently ignored.

8. **Legacy compatibility**
   - `trusted_issuer_key_ids` remains accepted when no full fingerprint set is
     supplied.
   - output explicitly records `issuer_trust_basis=legacy_short_key_id`.
   - if full fingerprints are also supplied, the full fingerprint set takes
     precedence.

9. **Historical audit semantics**
   - changing an ODK trust set from version N to N+1 does not mutate an older
     MethodMesh sidecar.
   - every verification sidecar retains the issuer fingerprint plus the exact
     trust-set ID/version supplied for that execution.
