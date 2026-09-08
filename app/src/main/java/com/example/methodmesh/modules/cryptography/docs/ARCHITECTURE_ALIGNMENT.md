# Architecture alignment

- Module folder: `modules/cryptography/`.
- Module object: `CryptographyModule : MethodMeshModule`.
- Auto-discovery: no central registry edit.
- Canonical methods: exposed through `as100Methods()`.
- RIL: bindings are module-owned.
- UI: capability screens are module-owned.
- ODK: importable XLSForm examples are module-owned.
- Dashboard: persistent dashboard pattern; live native/preset runs withhold the current result from the generic scaffold until explicit Finish/Use snapshot.
- Dependencies: `dependencies() = emptyList()`.
- Gradle: no dependency additions or patches.
- Cross-capability behavior: NFC, QR and trusted timestamp are composed through payloads/public methods, never source imports.
- Retained state: Android Keystore signing key and encrypted TOTP vault only.
- Not retained: encryption passwords, plaintext, decrypted content, Shamir source secret, TOTP seeds in provenance.
