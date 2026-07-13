# Attestation v3 supplied-hash fix

- `attestation.create` now reads `event_payload_hash` from the invocation context.
- A supplied hash must be a 64-character hexadecimal SHA-256 digest.
- Valid supplied hashes are signed directly and are not hashed again.
- The legacy `event_payload` route remains available and is hashed once.
- `event_payload_hash` takes precedence when both inputs are present.
- Missing or malformed payload inputs fail without appending a record to the chain.
- The signed canonical record and caller return now include:
  - `attestation_schema_version = 3`
  - `event_payload_mode = supplied_hash | calculated`
- The capability screen preserves externally supplied hash input rather than replacing it with its local demo payload.
