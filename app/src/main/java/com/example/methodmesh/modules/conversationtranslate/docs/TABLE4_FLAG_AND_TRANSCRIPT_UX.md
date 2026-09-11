# Four-seat, flag picker and transcript UX contract

## Design intent

The interface must remain usable when the people around the device do not share a language and cannot explain the controls to one another.

### Participant controls

Every participant-facing zone must expose the same functional elements, arranged so the participant's physical screen edge contains the action strip:

1. current flag + native language name;
2. Arabic variant selector when and only when Arabic is selected;
3. latest recognised/translated text;
4. compact `♀` / `♂` voice toggle + prominent localised Talk button + compact replay control.

Because the whole seat control rotates toward its chair, the final action strip must always land at that chair's physical device edge.

The flag/language selector must remain available during an active conversation.

### Flag picker

The flag grid is the default visual route. A participant should be able to identify a familiar flag without first reading English instructions. Its scroll position must remain stable while the participant opens a multilingual country and returns to the grid; ordinary recomposition must never reset it to the first row.

Country tiles show the English country name plus a locally recognisable country name when the two differ. Flags map to country/region entries. They do not directly imply a single language where the country is multilingual. Multilingual country entries must display a language choice using native language labels.

A generic `☾ العربية · Arabic` tile is pinned above the country grid. It represents Arabic as a transnational language rather than a country and opens the three Arabic speech-variant choices directly.

The A–Z language route is always available alongside the flag grid.

### Arabic

Arabic is `ar` for ML Kit Translation. Regional speech selection is separate.

The Arabic variant control must present exactly three primary choices:

- Levantine · الشامية
- Gulf · الخليجية
- North African · شمال أفريقيا

Country selection may preselect one of these but must not remove the participant's ability to change it.

### Four-seat translation order

Seat order is A → B → C → D clockwise.

For a spoken turn:

- show the original on all seats whose language equals the source language;
- traverse other seats clockwise;
- skip seats whose language equals the source language;
- translate once per distinct remaining language;
- reuse each translation for every seat using that target language;
- queue spoken translations in the same distinct-language order.

### Transcript

Transcript state is orthogonal to translation state.

When transcript is OFF:

- microphone capture continues;
- translation continues;
- screen output continues;
- TTS continues when enabled;
- turn text is not added to transcript persistence.

A pause marker is persisted when active transcript capture is stopped. A resume marker is persisted when capture restarts after an unrecorded interval. Starting transcript after earlier unrecorded conversation must explicitly state that earlier content was not transcribed.

### Shared-language turns

The translation graph is language-aware, not seat-aware.

If the speaker's canonical translation language is the same as another seat's language, that seat receives the original recognised text directly. No ML Kit translator is created, no translation model is downloaded for that source→same-language pair, and no TTS echo is generated merely to repeat speech in the same language.

Examples:

- `en / en / fr / ko`, English speaks → original is shared with both English seats; translate once to French and once to Korean.
- `fr / fr / fr / fr`, French speaks → no translation job is created.
- `en / en / en / fr`, English speaks → one French translation job only.

The transcript records the spoken source turn once and only records genuinely translated target-language text.

### Per-participant TTS voice preference

Each seat has a compact voice toggle directly beside its Talk button:

- `♀` — prefer a female-tagged installed TTS voice;
- `♂` — prefer a male-tagged installed TTS voice.

This preference belongs to the participant as the **speaker** and is independent of translation language and Arabic speech variant. When that participant speaks, all translated TTS output for that turn uses their voice preference while each target language still uses its own locale.

Android's `TextToSpeech.Voice` API does not define a standard gender field. MethodMesh therefore prefers explicitly tagged male/female voices when an engine exposes them, but the control must still have an audible effect on engines that do not. In that case it selects a stable alternative installed voice profile where possible and applies a restrained pitch offset as a final fallback. Seat identity is used as a voice-variant index, so participants sharing the same gender/profile should receive distinct voices when enough installed variants exist.

### Table control placement

The centre of the table is kept visually quiet.

- Transcript capture is a small global switch fixed to the physical bottom-right corner.
- End is available from the moment the table surface opens and is a compact control at the physical bottom-left.
- End never depends on a first recorded turn.
- The four-person surface is divided into top, left, right and bottom rectangular table zones.
- Each participant control group is anchored to that participant's physical outer edge.
- Their latest text sits immediately inward/above the Talk action strip.
- Replay is a compact side control rather than a second prominent button.
- The four zones use clearly different restrained tones and thin straight dividers; there is no diagonal centre intersection.

## v0.6 geometry

The diagonal-wedge experiment is retired. Rectangular zones are the canonical four-seat layout.


## v0.7 interaction refinements

Each rectangular participant tile is arranged in participant coordinates. The language selector sits against the internal/table boundary; the middle is reserved for recognised or translated text; and the outer screen edge carries a compact `voice | Talk | replay` strip. The Talk control is one line high.

Long text is scrollable. During TTS playback, character-range callbacks are mapped to the visible text and the scroll position advances proportionally. This is best-effort because Android TTS engines differ in range-callback support.

Speech is serialised across the four-seat table. The current speaker's voice preference and speaker-specific variant are resolved again immediately before each utterance, including replay.

The flag picker keeps a `LazyGridState` outside the dialog lifetime, with stable country keys, so returning from a multilingual country or reopening the picker retains position.
