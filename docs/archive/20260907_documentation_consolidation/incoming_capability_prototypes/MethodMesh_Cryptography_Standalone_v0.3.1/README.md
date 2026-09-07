# MethodMesh Cryptography — standalone v0.3.1

This delivery supersedes the experimental OpenPGP builds v0.1.x.

## Golden rule

The `cryptography/` folder is self-contained under the MethodMesh module contract:

> Adding or removing the folder must not require an edit to Gradle, the application registry, or another capability.

This version adds **no Maven/Gradle dependency**. It uses MethodMesh's existing module/UI contract plus Android/JDK platform APIs only.

## What is implemented

- secure password / PIN / token / human-enterable phrase generation;
- SHA-256 / SHA-512 hashing;
- password-protected JWE text encryption/decryption;
- password-protected JWE file encryption/decryption for files up to 24 MiB;
- device-bound P-256 signing identity in Android Keystore;
- public JWK export and RFC 7638 JWK thumbprints;
- detached ES256 JWS signing and verification;
- NFC/QR-safe public identity token (payload only; transport remains the NFC/QR capability's job);
- expiring challenge-response using detached JWS;
- RFC 6238 TOTP generation and otpauth provisioning;
- Android 11+ strong-biometric TOTP vault and password-JWE backup;
- Shamir threshold secret sharing;
- a deliberately small persistent dashboard showing non-secret status only.

See `cryptography/docs/README_Cryptography.md` for the public method contract.
