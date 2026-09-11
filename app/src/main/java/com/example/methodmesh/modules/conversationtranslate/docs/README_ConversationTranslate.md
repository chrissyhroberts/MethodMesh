# Conversation translator

The `conversationtranslate` module provides two live speech-translation capabilities:

- `conversation.translate` — two people sharing one device;
- `conversation.translate.table4` — up to four people around one device, with one participant surface facing each edge of the screen.

Both capabilities combine Android speech recognition, ML Kit on-device translation models and optional Android text-to-speech. Translation and transcript capture are intentionally independent: participants can keep translating while transcript recording is paused or switched off entirely.

## Two-person conversation

The existing two-person capability is preserved and extended.

Each participant panel now contains:

1. a high-contrast participant-facing language control;
2. when Arabic is selected, an additional Arabic speech-variant control;
3. a compact `♀` / `♂` speaker-voice toggle beside the Talk button;
4. a prominent **Press to speak** button localised in that participant's language;
5. a small replay control on the other side of the Talk button.

The initial participant-facing instruction is localised and explains that pressing the button will translate the participant's voice for the other person.

Participants may change languages during a conversation. The live language control opens the flag-first picker described below.

## Four-person table mode

`conversation.translate.table4` is designed for a device placed flat in the middle of a table.

The screen uses four clear table-facing rectangular zones: a full-width top seat, a split left/right middle row, and a full-width bottom seat:

- Seat A — bottom edge;
- Seat B — right edge;
- Seat C — top edge, rotated 180°;
- Seat D — left edge.

Each participant has a language/flag control and a large Talk button facing their chair. The latest recognised/translated text sits immediately above the action strip. The action strip is placed at that participant's physical outer edge, with the voice toggle and compact replay control beside Talk. Transcript and End remain compact global controls in the physical bottom corners.

When one participant speaks:

1. speech is recognised once in that participant's configured speech locale;
2. every seat using the same language receives the original text;
3. target seats are visited clockwise;
4. target languages are deduplicated;
5. each distinct target language is translated once, in sequence;
6. the resulting translation is reused for every seat using that target language;
7. optional text-to-speech is queued in the same target-language order.

Example: with `en / en / fr / ko`, an English speaker produces only two translation jobs: English → French and English → Korean. The second English seat sees the original and no redundant English translation is performed.

## Flag-first stranger workflow

The participant language control opens with a scrollable grid of large country/region flags. This is specifically intended for encounters where the operator and the other participant cannot yet explain the interface to one another. The flag-grid scroll position is retained while the participant drills into a multilingual country and returns, so the picker does not jump back to the top during browsing.

Every country tile shows its English name and, where different, the country's name in the primary local language used by that tile (for example `Germany · Deutschland`, `Spain · España`, `South Korea · 대한민국`, `Morocco · المغرب`).

A flag is **navigation**, not a language assertion.

- Countries with one relevant language can select it directly.
- Countries with several relevant ML Kit languages open a second, simple language choice.
- An A–Z language list remains available as an alternative route.
- A prominent `☾ العربية · Arabic` shortcut sits above the country grid for Arabic speakers who do not want to identify a country. It opens the same Levantine / Gulf / North African speech-variant choice used elsewhere.

Examples of deliberately multilingual flag entries include India, Switzerland, Belgium, Canada, Singapore and South Africa.

The flag catalogue provides at least one visual route to every one of the 59 ML Kit translation languages exposed by this module.

Country choice may also seed a regional Android speech locale without changing the ML Kit translation language. For example, Brazil can seed `pt-BR` while Portugal seeds `pt-PT`; both still use the ML Kit Portuguese model `pt`.

## Arabic speech variants

ML Kit translation exposes one Arabic translation language, `ar`. Spoken Arabic is therefore modelled separately from translation language.

Whenever Arabic is selected, the participant gets an additional visible control. Tapping it opens three large choices:

- **Levantine · الشامية**
- **Gulf · الخليجية**
- **North African · شمال أفريقيا**

The selected variant controls Android speech-recognition and TTS locale preference. It does **not** create a different ML Kit translation language.

Preferred locale families are:

- Levantine: `ar-LB`, `ar-JO`, `ar-SY`, `ar-PS`, `ar-IL`;
- Gulf: `ar-AE`, `ar-SA`, `ar-KW`, `ar-QA`, `ar-BH`, `ar-OM`;
- North African: `ar-MA`, `ar-DZ`, `ar-TN`, `ar-LY`, `ar-EG`.

Selecting an Arabic-speaking country's flag seeds the appropriate family. The participant can still override it with the three-button Arabic control.

Android speech availability is device/recogniser specific. If the preferred regional locale is not advertised, the module falls back to another advertised Arabic locale rather than treating the translation pack as unusable.

## Transcript control

Transcript recording has its own state and never controls whether translation is allowed.

The compact **Transcript ON/OFF** switch is fixed to the physical bottom-right corner of the four-seat table and can be changed at any time:

- ON → recognised speech and translations are appended to the transcript;
- OFF → translation, display and spoken output continue, but conversation content is not persisted;
- OFF → ON after unrecorded speech adds an explicit resume/start marker;
- ON → OFF adds an explicit pause marker.

This means the final transcript records where the tape was intentionally interrupted without fabricating content for the missing interval.

If a session starts with transcript disabled, nothing is recorded until the control is enabled. The preset/ODK setting `transcript_on_start` controls the initial state only; it never locks the in-session button.

## Language and speech wiring

Translation and speech are separate capabilities.

- The selectable translation-language list comes from ML Kit Translation.
- All 59 currently exposed ML Kit codes have explicit participant UI and Android speech/TTS routing in `ConversationLanguageSupport.kt`.
- Regional speech overrides are kept separate from translation codes.
- Important aliases include `tl` → `fil-PH`, `no` → `nb-NO`, `he` → `he-IL`/`iw-IL`, `id` → `id-ID`/`in-ID`, and Mandarin/Chinese tags for `zh`.
- **Prefer offline speech recognition** defaults to off because an installed ML Kit translation model does not imply an installed Android offline speech model.

See `LANGUAGE_PACK_AUDIT.md` for the complete language-pack audit.

## Presets and protocols

### `conversation.translate`

Configuration includes:

- `language_a`, `language_b`
- `arabic_variant_a`, `arabic_variant_b`
- `voice_a`, `voice_b`
- optional custom button labels `label_a`, `label_b`
- `spoken_output`
- `prefer_offline`
- `transcript_on_start`

### `conversation.translate.table4`

Configuration includes:

- `language_a` … `language_d`
- `arabic_variant_a` … `arabic_variant_d`
- `spoken_output`
- `prefer_offline`
- `transcript_on_start`

Participant flag choice and speech-locale routing remain changeable during the live session.

## ODK/XLSForms

Canonical XLSForm examples in this module are:

- `example_odk_conversation_translate.xlsx`
- `example_odk_showcase_conversation_translate.xlsx`
- `example_odk_conversation_translate_table4.xlsx`

The table form invokes `conversation.translate.table4`. The two-person forms invoke `conversation.translate`.

The XLSForms include Arabic speech-variant inputs, initial transcript state and FULL result capture. The final `conversation_transcript` contains explicit transcript pause/resume markers; detailed event/turn data remains available in `methodmesh_full_json`.

Generated XML snapshots were intentionally removed from this module so they cannot drift behind the XLSForm source. XLSX remains the module-owned design source of truth.

## Outputs

Two-person output additionally records:

- translation languages A/B;
- Arabic variants A/B where applicable;
- speaker voice preferences A/B;
- flag-seeded speech-locale overrides A/B where applicable;
- recorded turns JSON;
- transcript event JSON;
- whether transcript capture was enabled when the session ended.

Four-person output records equivalent A–D settings plus deduplicated multi-target turn JSON.

A turn is only written to transcript/turn JSON when transcript capture is ON. Live untranslated/translated text shown while transcript capture is OFF is intentionally ephemeral.

## Four-person table refinements

The table capability (`conversation.translate.table4`) deduplicates translation by canonical language. Same-language recipients receive the recognised source directly, so English→English, French→French and equivalent pairs never create translation jobs.

Each table participant has an independent `♀` / `♂` **speaker** TTS voice preference; translations of that participant's speech use their selected voice preference in each target-language locale. This is best-effort because Android does not expose standard voice-gender metadata; MethodMesh selects explicitly gender-tagged voices when the installed TTS engine provides them and otherwise retains the locale default voice.

The table's transcript switch is intentionally small and fixed at the physical bottom-right. `End` is available immediately from launch at the bottom-left. Transcript capture can still be paused/resumed without stopping recognition, translation or TTS.

## v0.4 UX refinements

- Two-person language selectors use a high-contrast surface treatment against the participant panels.
- `End conversation` is available immediately, including a zero-turn session.
- User-facing result preview suppresses blank optional metadata, so non-Arabic sessions do not show empty Arabic-variant or speech-locale rows. Full result JSON remains available when required.
- Two-person and four-person participant action strips use the same compact pattern: voice toggle, large Talk button, small replay control.
- In four-person mode the latest recognised/translated text is above the action strip and the action strip is the outermost control for every chair.
- Four-seat mode now uses rectangular table zones rather than diagonal wedges: top full-width, left/right split middle, bottom full-width. The zones use clearly different restrained surface tones and thin dividers.

## v0.5 picker refinements

- Flag-grid scroll state is owned by the picker dialog rather than the transient country-grid branch, so visiting a multilingual country and returning preserves the previous scroll position.
- Country tiles show English plus a locally recognisable country name when that differs from English.
- Arabic has a generic crescent flag tile (`☾`, `Arabic`, `العربية`) in the same grid and visual weight as country flags. It is independent of nationality; selecting it opens the three Arabic speech variants and keeps `ar` as the canonical ML Kit translation language.
- Generic Arabic selections use the crescent as the participant marker; choosing a specific Arabic-speaking country's flag continues to preserve that country's flag and regional speech seed.

## v0.6 table and voice refinements

- Replaced the diagonal four-seat geometry with a quieter rectangular table layout matching real seating positions.
- Two-person voice/replay controls use white foreground/borders on the green participant panels.
- Voice profile switching is now audible even where Android TTS exposes no gender metadata.
- Four-seat TTS uses source-seat identity to select distinct installed voice variants when available; four same-profile speakers can therefore receive four different voices if the engine provides enough suitable voices.
- Generic Arabic is a normal crescent flag tile rather than a full-width special button.


## v0.7 table reading, voice isolation and picker persistence

- Long translated text now occupies the central body of each four-seat tile and can scroll. When the installed TTS engine reports spoken character ranges (`UtteranceProgressListener.onRangeStart`), the text automatically advances in step with the spoken output. Manual scrolling remains available.
- Four-seat speech is serialised. Before every vocalisation and replay the module resolves the destination locale and the source participant's *current* male/female preference, then selects that speaker's stable voice variant. This prevents Android TTS's global voice/language state from leaking between queued turns.
- Speaker identity continues to seed voice selection, so multiple participants selecting the same gender receive different installed voices for a target locale when enough suitable voices exist.
- Participant tiles use a strict boundary/body/edge hierarchy: language selector at the table-facing boundary, translated text in the large central body, and a single-line Talk strip at the participant-facing outer edge with compact voice and replay controls.
- The flag picker uses a hoisted `LazyGridState` with stable keys. Scroll position therefore survives drill-in/back navigation and closing/reopening the picker for that participant instead of resetting to the A countries.
