# v0.3.1 — guided cryptography UX

This release changes the native interaction model while retaining the standalone architecture and public Method IDs.

Key changes:

- goal-first cryptography guide replacing the status-first dashboard;
- shared guidance, route and status cards using existing Material 3 only;
- password/passphrase/PIN/token choices instead of free-text generator mode;
- password confirmation for text/file encryption and TOTP backups;
- expert-only algorithm/key-alias/interoperability controls;
- public identity tokens accepted directly in signature and live-proof verification;
- clearer distinction between encryption, signatures, identity, hashes and challenge-response;
- manual human-readable authenticator account setup;
- TOTP misuse/recovery warnings;
- clearer Shamir threshold language and share-distribution warnings;
- decrypted `.jwe` files restore the original filename rather than adding `.decrypted`;
- no external libraries or host build changes.
