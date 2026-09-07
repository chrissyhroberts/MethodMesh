# Fingerprints capability

A lightweight fingerprint-registration, verification, and identification capability for the app.

This folder defines the product/UX contract independently of the scanner transport. The transport may initially be the existing Keppel scanner adapters and later the open BioMini Slim 3 driver.

## Design goals

- preserve existing Keppel / ODK Collect compatibility;
- remain stateless by default;
- support one-finger and multi-finger enrolment;
- support verification of one fresh scan against a registered fingerprint set;
- support identification against multiple supplied templates;
- never fuse different fingers into one synthetic biometric template;
- keep biometric policy such as match thresholds explicit and externally configurable;
- provide pleasant but non-essential UI animation;
- keep raw fingerprint images ephemeral unless an explicitly authorised workflow requires them.

## User-facing operations

1. **Register fingerprint**
2. **Register fingerprint set**
3. **Verify fingerprint**
4. **Find matching fingerprint**

When launched from ODK Collect, the calling Intent bypasses the standalone menu and opens the required workflow directly.

## Files

- `UX_SPEC.md` — screen and interaction specification.
- `WORKFLOW_STATE_MODEL.md` — implementation state machines.
- `ODK_CONTRACT.md` — backwards-compatible external Intent contract.
- `FINGERPRINT_SET_SCHEMA.md` — versioned multi-finger data model.
- `fingerprint-set.schema.json` — machine-readable schema.
- `MATCHING_POLICY.md` — matching, repeats, thresholds and ambiguity.
- `ANIMATION_SPEC.md` — lightweight visual/haptic behaviour.
- `ACCESSIBILITY_PRIVACY.md` — field usability, accessibility and data-handling constraints.
- `IMPLEMENTATION_PLAN.md` — suggested implementation order.

## Relationship to Keppel

Existing Keppel operations remain valid:

- `uk.ac.lshtm.keppel.android.SCAN`
- `uk.ac.lshtm.keppel.android.MATCH`
- `uk.ac.lshtm.keppel.android.MULTI_MATCH`

The richer capability should extend those contracts rather than break them.

## Relationship to open scanner transport

Do not couple UI logic to BioMini, Mantra, Aratek, USB HID, or any vendor SDK.

The UX should consume a scanner abstraction which can eventually be implemented by the open BioMini driver:

```text
FingerprintScanner
    connect()
    capture()
    stopCapture()
    disconnect()
```

A successful capture should expose at least:

```text
isoTemplate
quality
```

Raw images, where available, remain a lower-level optional capability.
