# Module review alignment — Media v0.4.0

Reviewed against canonical MethodMesh Master Book v1.07 dated 2026-09-08.

## Public methods

- `media.catalogue.search`
- `media.catalogue.import`
- `media.library.list`
- `media.library.state`
- `media.library.capture`
- `media.audio.identify`

## Review pass 1 — contract and parity

PASS: every public method is returned by `MediaModule.as100Methods()` and has one canonical `CapabilityScreenSpec`. All remain independently selectable for Presets/Protocols/ODK; the Media library and search screens are not dashboard-only implementations.

PASS: new personal-state functions use canonical `work_id`, not provider-specific account IDs.

PASS: audio identification does not depend on Spotify/current-media-session authentication and therefore satisfies the project no-sign-in decision.

## Review pass 2 — native UX

PASS: catalogue search remains tool-first and entitlement filters are visible.

PASS: selected catalogue titles can be added to Favourite or Watchlist without leaving search.

PASS: Media Library has explicit Favourites / Watchlist / Watched shelves plus provider and Included-only filters.

PASS: audio screen exposes a single purposive Listen and identify action, visible listening/identification states, result tap-to-copy, Favourite and Again actions.

PASS: no nested vertical scroll was introduced; host scrolling remains authoritative.

## Review pass 3 — working result / Commit

PASS: search and library selection return frozen canonical method results.

PASS: manual capture remains explicit Commit.

PASS: audio recognition freezes an `ExecutionResult`; automatic callers return after recognition, native callers retain Done/Again/Favourite actions.

PASS: the recognition result never silently changes after a later ambient sound.

## Review pass 4 — credentials and privacy

PASS: no end-user media account sign-in is implemented.

PASS: AudD uses the documented free public test token by default; an optional private token remains an operational service credential stored in private module preferences.

PASS: token is not passed to `context.onSettingsChanged`, request context, ODK, RIL, Presets, Protocols, results or exports.

PASS: recognition uploads only the intentionally recorded temporary clip; clip is deleted after the request attempt.

## Review pass 5 — persistence

PASS: personal state and provider availability are separate. A favourite survives catalogue/provider changes.

PASS: database bumped to schema v3 with `personal_state`; legacy v0.1 records migrate best-effort without deleting the old table.

PASS: provider/region replacement now requires both provider and region to be non-blank before destructive replacement.

## Review pass 6 — ODK

PASS by static workbook inspection: showcase workbook covers all six public method IDs and does not contain an audio service token field.

Interactive device return still requires host/device verification in ODK Collect.

## Build/test status

Current MethodMesh master public interfaces were inspected for `MethodMeshModule`, `As100Method`, `CapabilityScreenSpec`, `MethodSetting`, `RECORD_AUDIO` and Internet permissions.

A whole-app Gradle compile cannot be truthfully claimed from this isolated module handoff unless a full repository checkout is available in the execution environment. Keep Development status until `:app:compileDebugKotlin` and physical-device microphone/ODK checks pass.

## Remaining limitations

- catalogue source acquisition remains adapter/import based;
- no provider My List sync (intentional);
- no offline acoustic fingerprint database;
- no numeric match confidence is fabricated when the upstream service does not provide one;
- book/poster OCR remains composition with `mlkitvision`.

## v0.3.0 visual / interaction pass

The native screens were rebuilt around media-first interaction rather than generic result containers.

- Search results are the working surface; selecting a title expands actions in place.
- Successful capture replaces the editor with a clean committed title state rather than a generic result card.
- Audio identification uses a large tap-to-listen control and promotes the identified track/artist to the main screen hierarchy.
- Favourites/watchlist/watched are rendered as shelves with inline actions, not form-like state boxes.
- Catalogue import resolves to a simple catalogue-updated state.
- Repeated checkbox cards were replaced by quiet switch rows and section hierarchy.
- Raw JSON/provenance remains available through MethodMesh transport, but is deliberately absent from the everyday native screen.
- Canonical method IDs, result contracts, ODK transport, launch-origin completion behaviour and local persistence are unchanged.


## v0.4.0 publisher/dashboard pass

PASS: provider entry is no longer a primary free-text/comma-separated interaction; native search and library surfaces use searchable provider selection.

PASS: Catalogue Sources is publisher-first with TMDB/JustWatch and optional Watchmode adapters; manual files are demoted to Advanced.

PASS: TMDB/JustWatch attribution is visible in the Catalogue Sources surface and documented for host-app credits.

PASS: the TV/film search surface now presents local catalogue, favourites and watchlist status before search, while all canonical capabilities remain independently exposed for presets/protocols/ODK.


## v0.4.6 interaction/result alignment

- Catalogue direct-native interaction is browse-first (A–Z) while retaining `media.catalogue.search` as the unchanged canonical method contract for presets, protocols and ODK.
- Provider duplicates are consolidated only in native presentation; canonical result rows/provenance remain intact.
- Audio identification keeps all structured outputs, but orders the practical `media_value` headline before operational metadata so generic preset logs surface the song and artist.

## v0.4.7 audio field semantics

- `media.audio.identify` presents `media_title` as the song headline and `media_creator` as the artist.
- `media_album` is visibly labelled as album metadata and is never presented as the song title.
- Generic preset logging continues to receive `media_value = "Title — Artist"` first, with title/artist/album preserved as separate structured outputs.


## v0.4.8 live audio interaction alignment

- `media.audio.identify` keeps the working listen control and identified result in one continuous capability surface.
- The visible title is the direct tap-to-copy affordance and copies the practical `Title — Artist` value.
- Completion remains explicit for direct native use while preset immediate-submit behaviour is preserved.
