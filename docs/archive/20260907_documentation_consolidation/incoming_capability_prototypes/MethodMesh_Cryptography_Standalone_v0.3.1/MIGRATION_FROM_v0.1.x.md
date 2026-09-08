# Migration from v0.1.x

The v0.1.x packages are superseded.

They attempted to use PGPainless/OpenPGP and therefore required a Gradle dependency outside the capability folder. That violates the MethodMesh standalone-capability rule.

v0.2.1 removes:

- PGPainless;
- all OpenPGP source/imports;
- all Bouncy Castle assumptions;
- `HOST_PATCH/`;
- Gradle additions;
- recipient OpenPGP certificate settings;
- exportable OpenPGP secret keys.

Portable replacements:

- password encryption: JWE (`PBES2-HS256+A128KW` + `A256GCM`);
- signing: detached JWS (`ES256`);
- public identity: JWK + RFC 7638 thumbprint;
- private signing key: Android Keystore, device-bound/non-exported.

The public Method IDs are retained where the operation is semantically the same, but ciphertext/signature formats from v0.1.x are not compatible with v0.2.1.
