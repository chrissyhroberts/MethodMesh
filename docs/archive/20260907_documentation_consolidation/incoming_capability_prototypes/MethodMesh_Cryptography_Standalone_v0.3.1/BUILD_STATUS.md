# Build status — MethodMesh Cryptography Standalone v0.3.1

## Scope

v0.3.1 is a native-UX redesign of the dependency-free cryptography capability. Public Method IDs remain stable; the native presentation is now goal-first and uses progressive disclosure for cryptographic detail.

## Golden rule

PASS by static inspection:

- no Maven/Gradle dependency declaration inside the capability;
- no PGPainless or Bouncy Castle dependency/import;
- no host `build.gradle(.kts)`, `settings.gradle(.kts)` or manifest patch;
- all new UI components live inside `modules/cryptography/`;
- deleting the folder removes the capability without requiring a host edit.

## Executable tests in this build environment

PASS:

- SHA/hash primitives from the existing core self-test;
- RFC 3394 AES key-wrap known-answer test;
- JWE encrypt/decrypt round-trip;
- ES256 JWS sign/verify and JWK thumbprint self-test;
- RFC 6238 TOTP known-answer test;
- randomized Shamir recovery tests;
- Kotlin parser pass for all changed UI files: no `expecting`, `unexpected tokens`, or leaked undefined `identity` references;
- standalone dependency scan.

## UX review

Seven design/review passes are documented in `cryptography/docs/UX_DESIGN_v0.3.1.md`.

The normal native path now:

- begins with real-world outcomes rather than algorithm names;
- explains `You need`, `You get`, and `Important` boundaries before actions;
- hides interoperability parameters under `Expert options`;
- requires password confirmation for encryption/backups;
- accepts MethodMesh public identity cards directly during verification;
- distinguishes valid key signatures from verified human identity;
- gives manual human-readable TOTP setup rather than requiring an `otpauth://` URI;
- restores original filenames when opening `.jwe` files;
- retains no secrets on the crypto dashboard.

## Android project compile

Not executed in this environment because the complete Android/Gradle project is not mounted and container networking is disabled. The next host-side validation remains:

```bash
./gradlew :app:compileDebugKotlin
```

No host file should be edited to make v0.3.1 compile.
