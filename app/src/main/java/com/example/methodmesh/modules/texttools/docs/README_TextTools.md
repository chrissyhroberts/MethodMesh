# MethodMesh Text Tools

**Module:** `texttools`  
**Lane:** Development  
**Icon key:** `tool`  
**Network:** none required  
**Persistence:** none by default

Text Tools is an offline-first MethodMesh module for deterministic manipulation and inspection of supplied text.

It deliberately exposes **independent methods**, rather than making a single dashboard screen the capability contract. Every method can therefore be invoked:

- directly from the MethodMesh capability list;
- as a saved preset;
- as a protocol or scheduled step;
- from a widget through a preset/protocol;
- from ODK/XLSForm through the standard Android-intent roundtrip.

A module-owned text screen is only the native interaction surface. The executable methods and their declared `MethodSetting` inputs remain the public contract.

## Public methods

| Method ID | Primary output | Purpose |
|---|---|---|
| `text.clean` | `text_cleaned` | Trim/normalize whitespace, lines and Unicode |
| `text.case` | `text_case_result` | Lower/upper/title/sentence/toggle case |
| `text.replace` | `text_replaced` | Literal or explicit regex find/replace |
| `text.lines` | `text_lines_result` | Sort, deduplicate, filter, number or subset lines |
| `text.split_join` | `text_split_join_result` | Delimited text ↔ one item per line |
| `text.count` | `text_count_summary` plus scalar counts | Character/word/line/paragraph/byte counts |
| `text.extract` | `text_extract_result` | Structural extraction or syntactic pattern extraction |
| `text.truncate` | `text_truncated` | Deterministic size restriction |
| `text.slug` | `text_slug` | Filename/identifier-friendly slug |
| `text.encode` | `text_encoded_result` | Base64/URL/hex/basic HTML entity encode/decode |

All methods also return MethodMesh status/operation/input-output size/time/error fields. The standard transport layer can provide `methodmesh_full_json`.

## Native UX

The native screen:

1. asks only for settings that are not fixed by a preset;
2. keeps the main text input large and editable;
3. runs deterministic processing locally;
4. shows the main result as selectable text;
5. passes a compact beef-first result preview to `CapabilityScreenScaffold`;
6. relies on the shared scaffold for generic copy/share/save/Done/closeout behaviour;
7. preserves input/configuration/result values through rotation using `rememberSaveable`.

Fixed preset settings are hidden using `CapabilityScreenContext.settingShouldBeShown(...)`.

When launched by an Android intent, or when the orchestrator marks the step as `startsImmediately`, the capability runs automatically rather than forcing the caller through a redundant native setup gate.

## Presets and protocols

Each public method has its own typed `capabilitySettings()` entry.

Typical preset:

**Unique lines**

- method: `text.lines`
- `operation = deduplicate_preserve_order` fixed
- `trim_lines = true` fixed
- `case_sensitive = true` fixed
- `text` runtime

A protocol can pipe prior scalar text into `text` using the generic MethodMesh previous-step output mechanism. No Text Tools code depends on another module's internals.

Examples:

```text
document.scan OCR text
→ text.clean
→ text.extract (pattern=email)
```

```text
API output
→ text.split_join
→ text.lines (deduplicate_preserve_order)
```

## ODK/XLSForm

ODK uses the standard multi-field external-app pattern:

- `begin_group`
- `appearance = field-list`
- `body::intent = com.example.methodmesh.EXECUTE_METHOD(...)`
- child fields are named for the MethodMesh return extras.

Example:

```text
com.example.methodmesh.EXECUTE_METHOD(
  method_id='text.replace',
  input_text=${source_text},
  input_search=';',
  input_replacement=',',
  input_mode='literal',
  input_scope='all',
  return_mode='flat'
)
```

The supplied workbook `example_odk_text_tools.xlsx` demonstrates grouped calls for all ten public methods.

Use `methodmesh_return_namespace` when several MethodMesh calls in one form could otherwise collide.

MethodMesh should not store ODK text merely because it processed it. ODK owns form persistence/submission.

## Inputs

All methods accept `input_text` from Android/ODK transport, while native/preset configuration uses the canonical setting key `text`.

Other inputs are method-specific and correspond directly to the `MethodSetting` declarations in `TextToolsModule.kt`.

## Pattern extraction caveat

Built-in email/URL/IP/number extractors are **syntactic**. A returned string matching an email pattern is not a claim that the address exists. IPv4 extraction similarly identifies IPv4-shaped tokens and does not by itself assert octets are within 0–255.

Custom regex mode is explicitly selected. Literal find/replace never silently interprets regex metacharacters.

## Encoding caveat

Base64, hexadecimal, URL encoding and HTML entity encoding are representations, **not encryption**.

## Privacy and permissions

All v0.1 processing is local.

- no network permission is required by this module;
- no remote service receives supplied text;
- no capability-owned repository or database is used;
- no internal file is automatically created;
- no credential handling is required.

## Dependencies / attribution

The implementation uses Kotlin/Java/Android platform APIs and the existing MethodMesh runtime. No new third-party library is introduced.

## Status

**Development.**

This prototype has been written against the MethodMesh `master` interfaces inspected on 2026-09-06, including:

- `MethodMeshModule`
- `As100Method`
- `MethodSetting`
- `CapabilityScreenSpec`
- `CapabilityScreenScaffold`
- `CapabilityScreenContext.settingShouldBeShown(...)`

It has not been admitted to `modules/` or promoted to Production until the Android build, native UX, preset flow and ODK roundtrip have been exercised.

Because this chat could not create a repository branch or run the full Android build, this folder should first be reviewed under `incoming_capability_prototypes/texttools/`.
