# Matching and enrolment policy

## Different fingers

Different fingers are separate biometric sources. Do not combine their minutiae into one ISO template.

## Repeat captures of the same finger

Recommended default enrolment:

```text
capture A
capture B

if both have acceptable quality:
    compare A ↔ B

if consistent:
    accept finger
else:
    capture C
```

The exact consistency threshold must be empirically validated for the chosen template producer and matcher.

### Storage strategy A — preferred lightweight default

After consistency is demonstrated:

1. choose the better-quality accepted impression;
2. return/store that one template;
3. discard other capture templates from transient memory.

### Storage strategy B — higher robustness

Retain two accepted impressions and compare future scans against both.

## Verification against a fingerprint set

Return at least:

```text
bestScore
bestTemplateIndex
bestTemplateId
bestFingerSlot
```

Prefer also `runnerUpScore`.

## Thresholds

The generic capability should return continuous scores. Threshold policy should be decided by the caller or explicitly supplied to the fingerprint capability.

If threshold mode is requested, support three bands: `ACCEPT`, `RETRY / INDETERMINATE`, `REJECT`.

## Template-producer migration

If template extraction changes, repeat validation of same-finger and different-finger score distributions, interoperability, thresholds, and enrolment consistency.
