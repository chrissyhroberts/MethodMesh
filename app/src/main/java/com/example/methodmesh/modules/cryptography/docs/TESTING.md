# Testing

`tests/CoreSelfTest.kt` is JVM-executable and tests the platform-independent cryptographic primitives.

Current checks:

1. RFC 3394 AES Key Wrap known-answer vector.
2. JWE password encrypt/decrypt round-trip.
3. JWE wrong-password authentication rejection.
4. JWE header/profile inspection.
5. RFC 6238 SHA-1 known-answer vector at T=59.
6. Password/PIN/token invariants.
7. 100 randomized Shamir 3-of-5 reconstructions.
8. ES256/JWK/JWS sign/verify round-trip.
9. JWS tamper rejection.
10. Independent Python `cryptography` decryption/verification of emitted JWE/JWS data.

Android-device tests still required after integration:

- Android Keystore identity creation and persistence;
- content URI file round-trip;
- FileProvider handoff;
- Android 11+ biometric TOTP vault import/unlock/delete/backup;
- ODK Collect return/import behavior.
