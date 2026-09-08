# Accessibility, field use and privacy

## Accessibility

- large touch targets;
- text plus icon/shape for all key states;
- colour only as supplementary information;
- non-essential animations;
- Android font scaling;
- high outdoor contrast;
- screen-reader labels;
- always state the finger name in text, not only on a hand diagram.

## Field use assumptions

Assume glare, noisy environments, intermittent USB connection, one-handed tablet use, minimal training, and repeated enrolments.

Therefore do not rely on sound, recover automatically from scanner reconnection where possible, preserve recoverable workflow state, and auto-continue after successful capture.

## Privacy

Fingerprint templates are biometric data.

Default rules:

- no template contents in logs;
- no raw fingerprint image in logs;
- no raw fingerprint preview in normal UI;
- raw image remains transient;
- no silent local biometric registry;
- caller owns persistence;
- wipe transient buffers where practical;
- never include biometric bytes in analytics;
- error reports contain state/error codes, never templates.

Normal capture screens show a generic fingerprint illustration rather than the participant's actual fingerprint image.

## Statelessness

```text
ODK / host app
    supplies template(s)
        ↓
fingerprint capability
    performs capture/match
        ↓
ODK / host app
    receives result
```
