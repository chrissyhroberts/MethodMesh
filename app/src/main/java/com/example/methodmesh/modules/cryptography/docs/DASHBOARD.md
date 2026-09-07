# Cryptography dashboard

The dashboard follows the persistent-dashboard pattern: native dashboard and native preset runs retain the live view until the user explicitly finishes; external/protocol callers receive a normal result.

The dashboard shows only:

- supported portable formats;
- whether a local ES256 signing identity exists;
- whether the biometric TOTP vault is supported on this Android version;
- whether a TOTP vault ciphertext exists.

It intentionally does not show passwords, private keys, JWK-private material, TOTP seeds/codes, plaintext, Shamir shares or operational encryption controls.
