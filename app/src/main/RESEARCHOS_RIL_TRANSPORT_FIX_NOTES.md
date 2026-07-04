# RIL Transport Fix

Fixes the Android intent RIL binding so that sectioned RIL strings are recognised before the legacy `actions` fallback is used.

## Fixed

- `RilRequestParser.looksLikeRil()` now recognises payloads beginning with `WHAT`, `WHERE`, `HOW`, or `RESULT`, including semicolon-separated one-line payloads used in ADB and Android intent extras.
- RIL normalisation now converts semicolon-separated requests into line-oriented RIL sections.
- Compact one-line RIL requests are supported, for example:

```text
WHAT scan nfc WHERE participant/P001 RESULT return observation.nfc.uid as tag_uid format json
```

## Test command

```bash
adb -s '<device-id>' shell 'am start -W \
  -a com.example.researchos.EXECUTE_METHOD \
  --es ril "WHAT; scan nfc; verify identity fingerprint; WHERE; participant/P001; RESULT; return execution.id as execution_id; return observation.nfc.uid as tag_uid; return observation.identity.verified as verified; format json"'
```

Expected behaviour: NFC screen, then identity verification screen, then return summary.
