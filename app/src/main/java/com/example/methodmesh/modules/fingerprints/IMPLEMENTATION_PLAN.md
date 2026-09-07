# Implementation plan

## Phase 1 — improve current presentation without API changes

- explicit UI state model;
- central fingerprint visual;
- connecting/ready/capturing/accepted/retry presentation;
- recoverable scanner-disconnect state;
- success haptics;
- reduced-motion support;
- preserve `fast=true` auto-capture.

## Phase 2 — richer multi-match result

Change internal result from only `score` to:

```text
bestScore
bestTemplateIndex
runnerUpScore
```

Optionally carry caller-supplied `templateId` and `fingerSlot`. Preserve old score-only output.

## Phase 3 — fingerprint-set model

Add:

```text
FingerprintSet
FingerprintTemplateRecord
FingerSlot
QualityMeasurement
```

Add legacy numbered-template adapter and JSON codec for `keppel.fingerprint-set/1`.

## Phase 4 — multi-finger registration

Add `SCAN_SET`, requested slot queue, repeat-impression enrolment and consistency checks.

## Phase 5 — standalone capability home

Add:

```text
Register fingerprint
Register fingerprint set
Verify fingerprint
Find matching fingerprint
```

ODK/API launches continue to skip home.

## Phase 6 — open scanner transport

Once BioMini HID reconnaissance succeeds, implement the open scanner behind the same scanner interface. No workflow/UI code should need to change.

## Suggested Android package boundaries

```text
fingerprints/
├── domain/
│   ├── FingerSlot
│   ├── FingerprintSet
│   ├── FingerprintTemplateRecord
│   ├── MatchCandidate
│   └── MatchResult
├── workflow/
│   ├── RegisterOneWorkflow
│   ├── RegisterSetWorkflow
│   ├── VerifyWorkflow
│   └── IdentifyWorkflow
├── external/
│   ├── OdkRequestParser
│   ├── OdkResultBuilder
│   └── FingerprintSetCodec
├── scanner/
│   └── FingerprintScanner
└── ui/
    ├── FingerprintCaptureView
    ├── FingerProgressView
    └── workflow screens
```
