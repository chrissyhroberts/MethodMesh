# Fingerprint UX specification

## Product principles

The fingerprint capability should feel like a small instrument, not a settings-heavy application.

The user should normally perform one physical action at a time:

1. connect scanner;
2. place the requested finger;
3. hold briefly;
4. receive a clear success/failure cue;
5. continue automatically.

Numbers such as NFIQ and matcher scores are hidden in normal field mode. They remain available to debug/technical modes and to the calling application.

## 1. Standalone home

```text
┌──────────────────────────────┐
│         Fingerprints         │
│                              │
│  ◎  Register fingerprint     │
│                              │
│  ◎  Register fingerprint set │
│                              │
│  ✓  Verify fingerprint       │
│                              │
│  ⌕  Find matching print      │
│                              │
└──────────────────────────────┘
```

ODK/API launches bypass this screen.

## 2. Register one fingerprint

### Connecting

```text
          ◎

      Connecting scanner…
```

The fingerprint/scanner outline may pulse slowly.

### Ready

```text
       Right index finger

            ◎

     Place finger on scanner

          [ Scan ]
```

If `fast=true`, begin capture automatically on connection.

### Capturing

```text
       Right index finger

        [ridge animation]

       Keep finger still…
```

### Accepted

```text
              ✓

      Fingerprint captured

          Good quality
```

Normal ODK mode returns immediately after the short success transition.

### Poor quality

```text
              ~

          Try again

  Place the centre of your finger
       flat on the scanner
```

Do not frame routine poor capture as a fatal error.

## 3. Register fingerprint set

The calling workflow can specify requested finger slots.

Example:

```text
R_INDEX,L_INDEX,R_THUMB
```

Display a simple progress representation:

```text
Register fingerprints

RIGHT HAND                LEFT HAND

Thumb  ○                  ○  Thumb
Index  ●                  ○  Index
Middle ○                  ○  Middle

Right index
Scan 1 of 2

[ fingerprint capture area ]

Finger 1 of 3
```

A completed slot becomes a check.

### Repeated enrolment scans

Default: two accepted impressions per requested finger.

1. capture first impression;
2. capture second impression;
3. compare them;
4. if consistent, accept the finger;
5. if inconsistent, request a third impression.

The workflow should never say that the two fingerprints are "merged" unless a validated same-finger template-fusion algorithm is explicitly introduced. By default, select the best-quality accepted template or retain multiple impressions as separate templates.

## 4. Verify fingerprint

Verification compares a fresh scan against the fingerprint set already associated with the person.

### Ready

```text
Verify fingerprint

Registered fingers

✓ Right index
✓ Left index
✓ Right thumb

Use any registered finger
```

If finger-position metadata is unavailable:

```text
Place a registered finger
on the scanner
```

### Checking

```text
       [fingerprint]

          Checking…
```

### Verified

```text
              ✓

           Verified
```

One crisp success haptic.

### Weak/ambiguous

```text
              ~

       Try another finger

 We couldn't confirm this scan
```

### Clear non-match

Only show a binary failure if the calling workflow has explicitly requested threshold-based decision-making.

```text
              ×

    Fingerprint not verified

          [ Try again ]
```

Otherwise return the continuous match score to the caller.

## 5. Find matching fingerprint

Identification compares one fresh scan against templates supplied by the caller.

The UI need not expose the template list.

```text
Find matching fingerprint

            ◎

Place finger on scanner
```

Then:

```text
           Checking…
```

On completion, return the best candidate and score to the caller.

Standalone/debug mode may show best candidate, score and runner-up score. Normal ODK mode should return without exposing identifiers unnecessarily.

## 6. Recoverable versus fatal conditions

### Recoverable

- poor scan;
- no finger captured;
- scan timeout;
- scanner unplugged during capture;
- weak match;
- inconsistent repeat enrolment.

These stay within the workflow and invite retry.

### Fatal/configuration

- malformed input template;
- missing required templates for verification;
- unsupported scanner;
- invalid external Intent contract;
- impossible fingerprint-set schema version.

These may exit back to the caller with an explicit error.

## 7. Technical/debug mode

Optional debug display:

```text
Scanner: BioMini Slim 3
Template: ISO/IEC 19794-2
NFIQ: 2
Matcher: SourceAFIS
Best score: 132.7
Best template index: 2
```

Never show template bytes in normal UI.
