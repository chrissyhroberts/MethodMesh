# Text Tools validation

Status: **prototype / Development**

## Required admission checks

Before moving this module from `incoming_capability_prototypes/texttools/` to the auto-discovered `modules/texttools/` path:

### Build

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Do not admit the module if either fails.

## Pure processing checks

### `text.clean`

- CRLF and CR normalize to LF when requested.
- repeated blank lines collapse to one blank line.
- remove-blank-lines and collapse-blank-lines are distinct.
- Unicode NFC/NFD/NFKC/NFKD modes behave deterministically.
- blank text succeeds and returns blank text.

### `text.case`

- lower and upper case work.
- title and sentence case do not destroy punctuation.
- toggle case is reversible for ordinary cased characters.
- ROOT locale is the reproducible default.
- device-locale mode is explicit rather than implicit.

### `text.replace`

- literal punctuation is treated literally.
- regex is used only in regex mode.
- invalid regex fails with `text_error`.
- replace-first and replace-all report the correct replacement count.
- replacement strings containing `$` or `\` are preserved in literal mode.

### `text.lines`

- `deduplicate_preserve_order` is stable.
- case-insensitive deduplication preserves the first encountered spelling.
- blank line counts are correct.
- first/last N obey N=0.
- line numbering starts at 1.

### `text.split_join`

- comma, comma-space, semicolon, tab, pipe, space, newline and custom delimiters work.
- empty custom delimiter fails explicitly.
- trim/discard-empty settings are respected.
- split returns one item per line.
- join consumes line-oriented input.

### `text.count`

Check exact expected scalar values for:

- ASCII;
- emoji / surrogate pairs;
- accented Unicode;
- blank text;
- text with trailing newline;
- multiple paragraphs.

### `text.extract`

- no match returns blank result and count 0.
- multiple matches return newline-separated beef plus JSON match list.
- custom regex parse errors fail explicitly.
- marker operations do not invent text when markers are absent.
- email/URL/IP extractors are documented as syntactic rather than semantic validation.

### `text.truncate`

- input below limit is unchanged.
- exact-limit input is unchanged.
- suffix is included within the configured limit.
- Unicode characters are not split mid-code-point.
- UTF-8 byte truncation never splits a code point.
- retain-start and retain-end both obey the limit.

### `text.slug`

- repeated separators collapse.
- leading/trailing separators are removed.
- NFKD diacritic removal behaves as documented.
- ASCII-only and Unicode-preserving modes differ as expected.

### `text.encode`

Round-trip:

- Base64;
- URL;
- hex;
- basic HTML entities.

Malformed decode input must fail explicitly.

## Native UX

For every method:

- direct Capability run works;
- fixed preset configuration disappears at runtime;
- runtime `text` remains visible when not fixed/supplied;
- fully supplied preset/protocol runs automatically when `startsImmediately`;
- rotation retains input/settings/result;
- primary result is prominent/selectable;
- share/copy/save default to the useful result, not verbose JSON;
- Done routes through shared MethodMesh closeout;
- retry does not retain stale result state.

## Protocol/pipes

Test at least:

1. `text.clean` → `text.count`
2. `text.split_join` → `text.lines`
3. prior capability text output → `text.clean`
4. `text.extract` → later text capability

Verify declared scalar outputs are addressable without parsing `methodmesh_full_json`.

## ODK

Import `docs/example_odk_text_tools.xlsx` into an XLSForm-compatible server and test on current ODK Collect.

For each method verify:

- launch is via a `field-list` group `body::intent`;
- `input_*` values arrive correctly;
- MethodMesh does not display a redundant native setup dialog;
- main result field returns;
- supplementary fields return;
- `methodmesh_full_json` returns where requested;
- blank return fields do not overwrite request parameters;
- namespaced return works when `methodmesh_return_namespace` is supplied;
- no MethodMesh archive copy is created.

## Golden-rule check

No change should be required to:

- `HomeScreen.kt`;
- central capability registration;
- capability-independent preset field switches;
- ODK transport;
- protocol engine.

If a core change is needed only to make Text Tools work, stop and reassess the module design.
