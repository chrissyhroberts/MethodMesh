# Validation

Validation date: 2026-09-11.

## Interface audit

The module was checked against the current MethodMesh `master` public interfaces for:

- `MethodMeshModule`
- `As100Method`
- `CapabilityScreenSpec` / `CapabilityScreenContext`
- `MethodSetting`

The module exposes two distinct method IDs and two distinct capability screens:

- `conversation.translate`
- `conversation.translate.table4`

## Source checks

- Kotlin compiler parsing was run over the isolated module. No Kotlin syntax/parser errors were reported. Full compilation cannot succeed in isolation because Android, Compose, ML Kit, MethodMesh host classes and `org.json` are supplied by the containing app rather than this module folder.
- Flag catalogue coverage was checked against the module's 59 ML Kit translation-language codes: 59/59 have at least one flag/visual route.
- Arabic regional candidate groups are explicit and separate from ML Kit translation code `ar`.
- Four-seat target selection is language-deduplicated after clockwise seat ordering.
- Transcript persistence is independent of live translation state and contains explicit pause/resume markers.

## XLSForms

The three XLSForms were inspected after generation:

- `example_odk_conversation_translate.xlsx`
- `example_odk_showcase_conversation_translate.xlsx`
- `example_odk_conversation_translate_table4.xlsx`

Checks performed:

- Arabic variant choice list present;
- `transcript_on_start` present;
- two-person intent targets `conversation.translate`;
- four-person intent targets `conversation.translate.table4`;
- all 59 translation-language choices present;
- no spreadsheet formula-error tokens detected.

Generated XML snapshots from the previous module were removed deliberately. The module-owned XLSX files are the canonical ODK design source; retaining old compiled XML after changing the intents/settings would create a false second source of truth.

## Build limitation

A whole-app Gradle compile was not performed because the supplied artifact is a module-only archive, not the MethodMesh application tree. No claim of whole-app build success is made.

## v0.4 UX checks

- Same-language targets are filtered before ML Kit translator construction (`source == target` never translates).
- Duplicate target languages are collapsed to one translation job and the result is reused by seats sharing that language.
- Two-person same-language mode also bypasses ML Kit and does not TTS-echo the source as a fake translation.
- Two-person and four-person participants persist independent female/male TTS voice preferences.
- Voice selection prefers installed voices whose engine metadata explicitly hints the requested gender. When metadata is absent, stable alternate voice selection plus a restrained pitch fallback makes the toggle audibly effective.
- Transcript is a compact bottom-right switch and does not block the centre.
- End is enabled from table launch and no longer depends on `conversationHasOccurred`.
- Side controls are narrower and offset away from the centre; latest text is above the edge action strip; triangle contrast/dividers are stronger, especially between left and right.
- Two-person language selection uses a higher-contrast button treatment.
- Two-person End is available before the first turn; a zero-turn session can be closed cleanly.
- Result previews filter blank optional fields, preventing irrelevant Arabic/speech-locale artefacts on non-Arabic confirmation screens.
- Replay is a compact side control and the voice toggle sits beside Talk.
- The previous invalid `matchParentSize` import/use is removed; the background canvas uses `fillMaxSize()`.


## v0.5 picker validation

- Picker scroll state is hoisted to `ParticipantLanguageDialog`, so switching from the flag grid into a multilingual country and back reuses the same `ScrollState`.
- Country labels are generated from the primary country speech locale and displayed beneath the English country name when different; India uses an explicit `भारत` override because its first configured route is English.
- Generic Arabic uses `☾` rather than an arbitrary national flag and routes to explicit Levantine (`ar-LB`), Gulf (`ar-SA`) and North African (`ar-MA`) speech seeds while retaining ML Kit language `ar`.

## v0.6 validation

- Four-seat layout source no longer uses `Canvas`, `Path`, diagonal geometry or `matchParentSize`; it is composed from top/middle/bottom rectangular surfaces.
- Source-speaker seat IDs are carried into TTS selection and replay so multiple participants can receive distinct voice variants.
- Generic Arabic is rendered through the same 108dp flag-tile component as country entries.
- Two-person voice/replay outline controls explicitly use `onPrimary` foreground/borders on primary-coloured participant panels.


## v0.7 validation

Source checks added for the v0.7 changes:

- four-seat TTS uses one active utterance at a time and re-applies locale/voice immediately before `speak`;
- replay resolves the originating speaker's current voice preference rather than relying on stale stored TTS state;
- speaker keys remain `a`/`b`/`c`/`d`, preserving deterministic per-speaker voice distribution;
- `onRangeStart` drives per-language character progress for auto-scrolling long translated text;
- participant controls use full rectangular seat bounds with language at the inner boundary and Talk at the outer edge;
- flag navigation uses `LazyVerticalGrid`, stable item keys and a hoisted `LazyGridState`;
- Kotlin parser-oriented compile check reports no syntax, redeclaration, argument-shape or missing-parameter errors in the isolated module.

A full Android/Gradle compile still requires the enclosing MethodMesh application and its Android/Compose dependencies.
