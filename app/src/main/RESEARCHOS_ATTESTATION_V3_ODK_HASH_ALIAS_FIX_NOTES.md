# ResearchOS attestation v3 ODK hash alias fix

ODK group intents send child question values under their XLSForm field names. Existing forms may still name the precomputed SHA-256 field `event_payload`, even though the canonical v3 input is `event_payload_hash`.

The attestation repository now handles inputs as follows:

1. `event_payload_hash` containing 64 hexadecimal characters: use directly.
2. Legacy `event_payload` containing exactly 64 hexadecimal characters: treat as the supplied hash and use directly.
3. Any other non-empty `event_payload`: hash once for backward compatibility.
4. Missing or malformed explicit `event_payload_hash`: fail without appending to the chain.

This prevents a hexadecimal ODK digest from being hashed a second time while retaining compatibility with older forms.
