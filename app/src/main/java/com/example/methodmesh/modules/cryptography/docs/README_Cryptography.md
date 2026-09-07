# Cryptography capability

**Module ID:** `cryptography`  
**Version:** `0.3.1`  
**Dependency policy:** no new host or third-party dependencies

## Public methods

| Method | Purpose | Interoperable output |
|---|---|---|
| `crypto.password.generate` | Password/PIN/token/phrase generation | text |
| `crypto.hash` | SHA-256/SHA-512 | hexadecimal digest |
| `crypto.text.encrypt` | Password encrypt UTF-8 text | JWE Compact Serialization |
| `crypto.text.decrypt` | Password decrypt JWE text | UTF-8 text |
| `crypto.file.encrypt` | Password encrypt a file ≤24 MiB | `.jwe` |
| `crypto.file.decrypt` | Decrypt a JWE file | returned file URI |
| `crypto.key.generate` | Create/load device signing identity | public EC P-256 JWK + thumbprint |
| `crypto.identity.export` | Bind display name to public identity | `methodmesh.identity.jwk.v1` JSON |
| `crypto.sign` | Sign exact text/file bytes | detached ES256 JWS |
| `crypto.signature.verify` | Verify detached ES256 JWS | valid/failed + JWK thumbprint |
| `crypto.secret.split` | Split a secret M-of-N | `mms1:` Shamir shares |
| `crypto.secret.combine` | Reconstruct threshold secret | recovered bytes/text |
| `crypto.challenge.create` | Create expiring nonce challenge | JSON |
| `crypto.challenge.respond` | Sign current challenge | detached ES256 JWS |
| `crypto.challenge.verify` | Verify signer + expiry/audience | valid/failed |
| `otp.totp.import` | Store an `otpauth://` TOTP account | local biometric vault |
| `otp.totp.generate` | Generate RFC 6238 OTP | numeric code + lifetime |
| `otp.totp.delete` | Remove TOTP account | vault metadata only |
| `otp.totp.export` | Back up locker | password-protected `.jwe` |
| `crypto.dashboard` | Non-secret status snapshot | JSON |

## Portable encryption profile

The standalone implementation intentionally exposes one JWE profile only:

- `alg`: `PBES2-HS256+A128KW`
- `enc`: `A256GCM`
- PBES2 salt: random 128 bits
- default `p2c`: 210000
- content-encryption key: random 256 bits
- AES Key Wrap: RFC 3394
- AES-GCM IV: random 96 bits
- authentication tag: 128 bits

This is standard JWE, not a MethodMesh ciphertext container. A server-side JOSE implementation can decrypt it without MethodMesh.

File mode emits the ASCII JWE compact representation in a `.jwe` file. Because compact JWE is not streaming, this standalone build enforces a 24 MiB input limit rather than pretending it can safely handle arbitrarily large data.

## Signing identity

`crypto.key.generate` creates a P-256 key in Android Keystore. The private key is non-exported. The module returns:

- RFC 7517 public JWK;
- RFC 7638 SHA-256 JWK thumbprint;
- local alias.

`crypto.sign` produces a detached RFC 7515 JWS using ES256. The detached payload is reconstructed from the exact bytes when verifying.

## NFC / QR identity

`crypto.identity.export` returns public JSON containing:

- display name;
- public JWK;
- JWK thumbprint.

It contains no secret or private key material. An NFC or QR capability may carry this payload without Cryptography depending on that capability at compile time.

A verifier can therefore receive a file + detached JWS, scan a physical identity credential, and verify that the signature matches the scanned public key.

## Trusted timestamp composition

The existing trusted timestamp capability is **not copied into this folder**. When installed, workflows can compose the two public method families, e.g. sign/hash a result and then call the timestamp capability. Cryptography itself declares `dependencies() = emptyList()`.


## Native UX

The v0.3.1 native screens use goal-first guidance, progressive disclosure and misuse-prevention rails. See `UX_DESIGN_v0.3.1.md`.
