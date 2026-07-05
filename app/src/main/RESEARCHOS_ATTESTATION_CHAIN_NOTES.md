# ResearchOS attestation chain

This patch turns attestation from a plain `verified` flag into a signed, traceable event record.

## Model

- The phone creates a non-exportable ECDSA signing key in Android Keystore.
- The public key is exportable and can be shared with the trial data manager.
- Every attested event hashes the event payload and declared verification evidence.
- Each event includes the previous attestation hash, creating a local hash chain.
- The canonical attestation payload is signed with the phone private key.

## Offline and online proof

Offline events can prove order, integrity, device key and local verification method. They cannot independently prove wall-clock time while disconnected.

The nightly ODK anchor bundle exports:

- public key ID and public key
- last anchor hash
- current chain head hash
- first/last unanchored event time claimed by the device
- bundle hash
- bundle signature

Submitting this via ODK Central gives the independent server receipt timestamp: by that server time, the signed chain head already existed.

## New capabilities

- `attestation.create` — create a signed event attestation.
- `attestation.anchor_bundle` — create nightly ODK chain-anchor fields.
