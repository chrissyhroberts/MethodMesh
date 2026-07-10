# Result visibility and attestation evidence integrity

- Capability cards and legacy method sections now start collapsed, including all DCE capability runners.
- Truncated result previews are interactive: the “more fields” row expands to show every returned field and can be collapsed again.
- QR-backed attestation now consumes the QR capability's raw payload and SHA-256 hash, checks the dependency result before signing, and returns both values.
- The attestation signature binds the evidence hash. A modified payload fails verification when its SHA-256 digest is compared with the signed `verification_evidence_hash`; changing the hash also invalidates the ECDSA signature.
- Returned evidence is rendered as read-only result text. ResearchOS does not provide an editor for captured QR evidence.
