# ResearchOS RIL Transport Notes

This build adds a first Android/ODK text binding for the ResearchOS Intent Language (RIL) v0.03.

The RIL specification remains authoritative. This implementation does not redefine RIL; it provides a minimal transport binding that maps RIL sections into the existing Android workflow runtime:

- `WHAT` -> ordered capability/action chain
- `WHERE` -> invocation subject/context
- `HOW` -> execution settings and policies
- `RESULT` -> graph selectors and return mode

## Android intent extra

External callers may now pass a single RIL request using one of these extras:

- `ril`
- `request`
- `researchos_request`

Existing extras such as `actions`, `subject`, `returns`, and `return_mode` remain supported as compatibility bindings.

## Example

```bash
adb shell am start -W \
  -a com.example.researchos.EXECUTE_METHOD \
  --es ril "WHAT; scan nfc; verify identity fingerprint; WHERE; participant/P001; RESULT; return execution.id as execution_id; return observation.nfc.uid as tag_uid; return observation.identity.verified as verified; format json"
```

Semicolons are accepted as line separators for Android/ODK convenience. The same request may be written in canonical section form:

```text
WHAT
scan nfc
verify identity fingerprint
WHERE
participant/P001
RESULT
return execution.id as execution_id
return observation.nfc.uid as tag_uid
return observation.identity.verified as verified
format json
```

## Shorthand

A compact shorthand is also accepted:

```text
execute nfc.read for participant/P001
return observation.nfc.uid as tag_uid
return execution.id as execution_id
```

This is interpreted as a RIL request and mapped through the same workflow runner.

## Implementation files

- `transport/ril/RilRequestParser.kt`
- `transport/LaunchConfigParser.kt`
- `transport/android/AndroidIntentRequestReader.kt`

## Current scope

This is a first implementation binding. It supports ordered action chains, subject context, simple HOW settings, return selectors, return mode, and provenance/metadata/diagnostics flags as settings. It does not yet implement scheduled WHEN triggers or complex WHERE spatial constraints.
