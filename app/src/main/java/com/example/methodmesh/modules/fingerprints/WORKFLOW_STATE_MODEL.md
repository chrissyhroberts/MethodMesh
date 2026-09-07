# Workflow state model

The UI should be driven by explicit states rather than visibility toggles spread across fragments.

## Shared scanner states

```text
Unavailable
Connecting
Ready
Capturing
CaptureAccepted
CaptureRejected
Disconnected
FatalError
```

## Register one

```text
Connecting
   │
   ├── failure ───────────────→ FatalError
   ▼
Ready
   │ capture
   ▼
Capturing
   ├── no capture ────────────→ Ready
   ├── poor quality ──────────→ CaptureRejected ─→ Ready
   ▼
CaptureAccepted
   ▼
ReturnResult
```

If fast mode is enabled: `Connected → Capturing`.

## Register set

```text
PrepareSet
   ↓
PrepareSlot
   ↓
CaptureImpression
   ↓
EvaluateQuality
   ├── reject → CaptureImpression
   ↓
StoreAcceptedImpression
   ↓
NeedMoreImpressions?
   ├── yes → CaptureImpression
   ↓ no
EvaluateWithinFingerConsistency
   ├── inconsistent → CaptureAdditionalImpression
   ↓
AcceptFingerSlot
   ↓
MoreSlots?
   ├── yes → PrepareSlot
   ↓ no
ReturnFingerprintSet
```

Persist workflow state across ordinary Android configuration changes. Do not persist captured raw fingerprint images across process death unless resumable enrolment is explicitly designed.

## Verify

```text
ParseFingerprintSet
   ├── invalid → FatalError
   ↓
Connecting
   ↓
Ready
   ↓
Capturing
   ↓
MatchAgainstAllEligibleTemplates
   ↓
ReturnContinuousResult
```

Optional threshold-decision mode:

```text
Match result
   ├── score >= accept threshold → Verified
   ├── score in retry band       → Retry
   └── score < reject threshold  → NotVerified
```

Thresholds must not be hard-coded into generic UI components.

## Identify

```text
ParseCandidates
   ↓
Connecting
   ↓
Capture
   ↓
ScoreAgainstCandidates
   ↓
Rank
   ↓
Return best candidate + best score + runner-up score
```
